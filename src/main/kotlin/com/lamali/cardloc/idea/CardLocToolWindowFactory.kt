package com.lamali.cardloc.idea

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.lamali.cardloc.core.CardLocRegistry
import com.lamali.cardloc.editor.CardLocPanel

class CardLocToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val registry = project.getService(CardLocRegistry::class.java)
        registry.initialize()
        val editorPanel = CardLocPanel(project)
        val content = ContentFactory.getInstance().createContent(editorPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        return true
    }
}