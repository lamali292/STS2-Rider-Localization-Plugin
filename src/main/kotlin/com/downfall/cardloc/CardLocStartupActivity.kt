package com.downfall.cardloc

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class CardLocStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        CardLocRegistry.initialize(project.basePath)
    }
}