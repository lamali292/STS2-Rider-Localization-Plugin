package com.downfall.cardloc

import com.downfall.cardloc.editor.CardLocPanel
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.wm.ToolWindowManager

class CardFileListener : FileEditorManagerListener {

    override fun selectionChanged(event: FileEditorManagerEvent) {
        val file = event.newFile ?: return
        if (file.extension != "cs") return

        val project = event.manager.project

        com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
            val source = try {
                String(file.contentsToByteArray())
            } catch (e: Exception) {
                println("CardLoc: Failed to read source — ${e.message}"); return@executeOnPooledThread
            }

            val result = CardLocRegistry.detectWithClass(source) ?: return@executeOnPooledThread
            val (preset, className) = result

            val key = CardLocService.toKey(className)
            val existing = CardLocService.load(project, key, preset)

            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                val tw = ToolWindowManager.getInstance(project).getToolWindow("STS2 Localization") ?: run {
                    println("CardLoc: Tool window 'STS2 Localization' not found!"); return@invokeLater
                }
                tw.activate {
                    val panel = tw.contentManager.selectedContent?.component as? CardLocPanel ?: run {
                        println("CardLoc: Selected content is not a CardLocPanel — got: ${tw.contentManager.selectedContent?.component?.javaClass?.name}")
                        return@activate
                    }
                    panel.load(project, key, existing, preset)
                }
            }
        }
    }
}