package com.lamali.cardloc.idea

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.lamali.cardloc.core.CardLocRegistry
import com.lamali.cardloc.editor.CardLocPanel

/**
 * Factory responsible for creating and managing the CardLoc Editor tab.
 * Implements DumbAware so the window is available even during index updates.
 */
class CardLocToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // 1. Initialize the Registry Service for this specific project
        val registry = project.getService(CardLocRegistry::class.java)
        registry.initialize()

        // 2. Create the UI Panel and pass the project context
        // This ensures the panel knows which Registry/Service to talk to
        val editorPanel = CardLocPanel(project)

        // 3. Create the Content tab
        // displayName is empty "" because the ToolWindow ID provides the title
        val content = ContentFactory.getInstance().createContent(editorPanel, "", false)

        // 4. Add the content to the manager
        // IntelliJ handles the "Stop/Close" logic: if the user closes the tab,
        // the panel is disposed. If they hide it, the state is preserved.
        toolWindow.contentManager.addContent(content)
    }

    /**
     * Optional: Decides if the ToolWindow should be visible by default.
     * You can check if cardloc-presets.json exists before showing the icon.
     */
    override fun shouldBeAvailable(project: Project): Boolean {
        return true
    }
}