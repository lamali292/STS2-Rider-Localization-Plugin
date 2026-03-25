package com.lamali.cardloc.core

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

/**
 * Listens for VFS (Virtual File System) events to detect when the config file is saved.
 * IntelliJ automatically passes the correct 'project' instance via the constructor.
 */
class CardLocConfigWatcher(private val project: Project) : BulkFileListener {

    override fun after(events: List<VFileEvent>) {
        // Check if any of the affected files is our config file
        // We use .endsWith for a simple check, but you could also check the full path
        val needsReload = events.any { it.path.endsWith("cardloc-presets.json") }

        if (needsReload) {
            // 1. Fetch the project-specific service instance
            val registry = project.getService(CardLocRegistry::class.java)

            // 2. Trigger the reload logic
            registry.reload()

            println("CardLoc [${project.name}]: Config file change detected. Registry reloaded.")

            // 3. Optional: Trigger a UI refresh if a file is currently open
            // You could call a refresh method on your active CardLocPanel here
            // if you store a reference to it.
        }
    }
}