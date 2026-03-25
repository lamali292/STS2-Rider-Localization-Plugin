package com.lamali.cardloc.idea

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.lamali.cardloc.core.CardLocRegistry
import com.lamali.cardloc.editor.CardLocPanel

class CardLocToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        CardLocRegistry.initialize(project.basePath)
        val panel = CardLocPanel()
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}