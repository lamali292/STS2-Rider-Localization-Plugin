package com.lamali.cardloc.core

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.lamali.cardloc.CardLocConstants

/**
 * Listens for VFS events to detect when the config file is saved.
 */
class CardLocConfigWatcher(private val project: Project) : BulkFileListener {

    override fun after(events: List<VFileEvent>) {
        val needsReload = events.any { it.path.endsWith(CardLocConstants.CONFIG_FILENAME) }
        if (needsReload) {
            val registry = project.getService(CardLocRegistry::class.java)
            registry.reload()
            println("CardLoc [${project.name}]: Config file change detected. Registry reloaded.")
        }
    }
}