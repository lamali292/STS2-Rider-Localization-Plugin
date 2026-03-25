package com.lamali.cardloc.core

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

// IntelliJ will automatically pass the correct 'project' instance here
class CardLocConfigWatcher(private val project: Project) : BulkFileListener {

    override fun after(events: List<VFileEvent>) {
        val needsReload = events.any { it.path.endsWith("cardloc-presets.json") }
        if (needsReload) {
            CardLocRegistry.initialize(project.basePath)
            println("CardLoc: reload json: ${project.basePath}")
        }
    }
}