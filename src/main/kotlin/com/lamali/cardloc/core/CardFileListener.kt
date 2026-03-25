package com.lamali.cardloc.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.wm.ToolWindowManager
import com.lamali.cardloc.editor.CardLocPanel

class CardFileListener : FileEditorManagerListener {

    override fun selectionChanged(event: FileEditorManagerEvent) {
        val file = event.newFile ?: return
        // Ensure we only process relevant files (e.g., C# or Kotlin/Java)
        if (file.extension != "cs" && file.extension != "kt") return

        val project = event.manager.project

        // Fetch the project-specific registry service
        val registry = project.getService(CardLocRegistry::class.java)

        ApplicationManager.getApplication().executeOnPooledThread {
            // 1. Silent Guard: if the project isn't initialized yet, do nothing
            if (!registry.isInitialized()) return@executeOnPooledThread

            val source = runCatching {
                String(file.contentsToByteArray())
            }.getOrNull() ?: return@executeOnPooledThread

            // 2. Detect if this file matches a Card/Power preset
            val result = registry.detectWithClass(source) ?: return@executeOnPooledThread
            val (preset, className) = result

            // 3. Generate the key using the project instance
            val key = CardLocService.toKey(project, className) ?: return@executeOnPooledThread

            // 4. Load the current values from the JSON files
            val existing = CardLocService.load(project, key, preset)

            ApplicationManager.getApplication().invokeLater {
                // Use the ID defined in your plugin.xml (usually "CardLoc Editor")
                val tw = ToolWindowManager.getInstance(project).getToolWindow("StS2 Localization Editor") ?: return@invokeLater

                // If the tool window exists, load the data into the panel
                if (tw.isVisible) {
                    val panel = tw.contentManager.selectedContent?.component as? CardLocPanel ?: return@invokeLater
                    panel.load(key, existing, preset)
                }
            }
        }
    }
}