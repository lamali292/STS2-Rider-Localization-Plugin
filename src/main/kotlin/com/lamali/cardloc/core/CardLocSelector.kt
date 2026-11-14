package com.lamali.cardloc.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.lamali.cardloc.editor.CardLocPanel

/**
 * Decides which card class of a file is shown in the editor panel and loads it.
 * A file can hold several cards; the target says which one.
 */
object CardLocSelector {

    sealed interface Target {
        /** The card whose class contains this caret offset. */
        data class Offset(val offset: Int) : Target

        /** The card with this class name (from the panel's dropdown). */
        data class ClassName(val name: String) : Target

        /** The first card in the file. */
        data object First : Target
    }

    const val TOOL_WINDOW_ID = "StS2 Localization Editor"

    /**
     * @param force reload even if this card is already shown, and for an [Target.Offset] outside
     * any card fall back to the first card instead of leaving the panel untouched.
     */
    fun show(project: Project, file: VirtualFile, source: String, target: Target, force: Boolean) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val registry = project.getService(CardLocRegistry::class.java)
            val context = registry.getContextForFile(file) ?: return@executeOnPooledThread

            val matches = context.detectAll(source)
            val match = when (target) {
                is Target.Offset -> context.detectAt(source, target.offset)
                    ?: if (force) matches.firstOrNull() else null
                is Target.ClassName -> matches.firstOrNull { it.className == target.name }
                Target.First -> matches.firstOrNull()
            } ?: return@executeOnPooledThread

            val id = "${file.path}#${match.className}"
            if (!force && id == registry.lastShown) return@executeOnPooledThread
            registry.lastShown = id

            val key = CardLocService.toKey(context, match.className) ?: run {
                ApplicationManager.getApplication().invokeLater { registry.notifyUnconfigured(context) }
                return@executeOnPooledThread
            }
            val existing = CardLocService.load(context, key, match.preset)
            val classNames = matches.map { it.className }

            ApplicationManager.getApplication().invokeLater {
                val tw = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
                    ?: return@invokeLater
                if (!tw.isVisible) return@invokeLater
                val panel = tw.contentManager.selectedContent?.component as? CardLocPanel
                    ?: return@invokeLater

                panel.saveCurrentContent()
                panel.load(context, key, existing, match.preset)
                panel.setClassChoices(file, classNames, match.className)
            }
        }
    }
}
