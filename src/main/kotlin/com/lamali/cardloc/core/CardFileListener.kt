package com.lamali.cardloc.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.wm.ToolWindowManager
import com.lamali.cardloc.editor.CardLocPanel

class CardFileListener : FileEditorManagerListener {

    override fun selectionChanged(event: FileEditorManagerEvent) {
        val file = event.newFile ?: return
        if (file.extension != "cs" && file.extension != "kt") return

        val project = event.manager.project
        val registry = project.getService(CardLocRegistry::class.java)

        ApplicationManager.getApplication().executeOnPooledThread {
            if (!registry.isInitialized()) return@executeOnPooledThread
            val source = runCatching {
                String(file.contentsToByteArray())
            }.getOrNull() ?: return@executeOnPooledThread
            val result = registry.detectWithClass(source) ?: return@executeOnPooledThread
            val (preset, className) = result
            val key = CardLocService.toKey(project, className) ?: return@executeOnPooledThread
            val existing = CardLocService.load(project, key, preset)
            ApplicationManager.getApplication().invokeLater {
                val tw = ToolWindowManager.getInstance(project).getToolWindow("StS2 Localization Editor") ?: return@invokeLater
                if (tw.isVisible) {
                    val panel = tw.contentManager.selectedContent?.component as? CardLocPanel ?: return@invokeLater
                    panel.load(key, existing, preset)
                }
            }
        }
    }
}