package com.lamali.cardloc.core

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.lamali.cardloc.editor.CardLocPanel

/**
 * Follows Rider's own C# rename refactor: when it renames a card's .cs file to match
 * a renamed class, cascades the same rename to every "<OLD_KEY>.field" -> "<NEW_KEY>.field"
 * entry across all localization languages, keeping translated values in place so
 * translators don't lose work on a pure rename.
 */
class CardRenameListener : BulkFileListener {

    override fun after(events: MutableList<out VFileEvent>) {
        events.forEach { event ->
            if (event !is VFilePropertyChangeEvent) return@forEach
            if (event.propertyName != VirtualFile.PROP_NAME) return@forEach

            val file = event.file
            if (file.extension != "cs") return@forEach

            val oldClassName = (event.oldValue as? String)?.removeSuffix(".cs") ?: return@forEach
            val newClassName = (event.newValue as? String)?.removeSuffix(".cs") ?: return@forEach
            if (oldClassName == newClassName) return@forEach

            ApplicationManager.getApplication().executeOnPooledThread {
                handleRename(file, oldClassName, newClassName)
            }
        }
    }

    private fun handleRename(file: VirtualFile, oldClassName: String, newClassName: String) {
        val project = ProjectManager.getInstance().openProjects.firstOrNull { p ->
            val base = p.basePath ?: return@firstOrNull false
            file.path.startsWith(base)
        } ?: return

        val registry = project.getService(CardLocRegistry::class.java)
        val context = registry.getContextForFile(file) ?: return

        val source = runCatching { String(file.contentsToByteArray()) }.getOrNull() ?: return
        val (preset, detectedClassName) = context.detectWithClass(source) ?: return
        // File got renamed but its body doesn't match the new name (e.g. unrelated file move) - skip.
        if (detectedClassName != newClassName) return

        val oldKey = CardLocService.toKey(context, oldClassName) ?: return
        val newKey = CardLocService.toKey(context, newClassName) ?: return
        if (oldKey == newKey) return

        val result = CardLocService.renameKey(context, oldKey, newKey, preset)
        if (result.movedFields == 0) return

        ApplicationManager.getApplication().invokeLater {
            val tw = ToolWindowManager.getInstance(project).getToolWindow("StS2 Localization Editor")
            val panel = tw?.contentManager?.selectedContent?.component as? CardLocPanel
            panel?.renameCurrentKeyIfMatches(context, oldKey, newKey, preset)

            NotificationGroupManager.getInstance()
                .getNotificationGroup("CardLoc")
                .createNotification(
                    "CardLoc: $oldKey -> $newKey",
                    "Moved ${result.movedFields} field(s) across ${result.languages.size} language file(s): ${result.languages.joinToString()}",
                    NotificationType.INFORMATION
                )
                .notify(project)
        }
    }
}
