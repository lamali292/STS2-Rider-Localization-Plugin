package com.lamali.cardloc.core

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class CardLocStartupActivity : ProjectActivity {

    // ProjectActivity is the modern replacement for StartupActivity.
    // It runs 'suspend', which is why it doesn't block the UI thread.
    override suspend fun execute(project: Project) {
        val basePath = project.basePath
        if (basePath != null) {
            // Initialize the registry with the project path
            CardLocRegistry.initialize(basePath)
            println("CardLoc: ProjectActivity initialized — base='$basePath'")
        }
    }
}