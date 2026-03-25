package com.lamali.cardloc.core

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class CardLocStartupActivity : ProjectActivity {

    /**
     * ProjectActivity runs asynchronously when a project is opened.
     * We use this to trigger the first-time load of the cardloc-presets.json.
     */
    override suspend fun execute(project: Project) {
        // 1. Fetch the project-specific registry service
        // .service<T>() is a helpful Kotlin extension for project.getService(T::class.java)
        val registry = project.service<CardLocRegistry>()

        // 2. Run the initialization
        // The service already knows its own project.basePath
        registry.initialize()

        // 3. Optional: Log completion for debugging
        if (registry.isInitialized()) {
            println("CardLoc [${project.name}]: StartupActivity completed — Registry is ready.")
        } else {
            println("CardLoc [${project.name}]: StartupActivity finished — No config file found.")
        }
    }
}