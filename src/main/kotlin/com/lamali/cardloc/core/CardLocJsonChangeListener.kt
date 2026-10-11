package com.lamali.cardloc.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.lamali.cardloc.editor.CardLocPanel

/** Refreshes the open editor panel when a localization .json is modified outside of it. */
class CardLocJsonChangeListener : BulkFileListener {

    override fun after(events: MutableList<out VFileEvent>) {
        val paths = events.filterIsInstance<VFileContentChangeEvent>()
            .filter { it.file.extension == "json" }
            .map { it.file.path }
        if (paths.isEmpty()) return

        ApplicationManager.getApplication().invokeLater {
            ProjectManager.getInstance().openProjects.forEach { project ->
                val tw = ToolWindowManager.getInstance(project).getToolWindow("StS2 Localization Editor") ?: return@forEach
                val panel = tw.contentManager.selectedContent?.component as? CardLocPanel ?: return@forEach
                paths.forEach { panel.reloadIfChangedOnDisk(it) }
            }
        }
    }
}
