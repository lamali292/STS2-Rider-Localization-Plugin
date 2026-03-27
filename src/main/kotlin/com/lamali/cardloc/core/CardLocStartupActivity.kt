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
        val registry = project.service<CardLocRegistry>()
        registry.initialize()
    }
}