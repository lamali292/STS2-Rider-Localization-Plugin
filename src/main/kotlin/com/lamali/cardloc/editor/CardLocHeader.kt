package com.lamali.cardloc.editor

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.lamali.cardloc.editor.ui.UI
import java.awt.*
import javax.swing.*

class CardLocHeader(
    private val onReload: () -> Unit,
    private val onAdd: () -> Unit,
    private val onSave: () -> Unit,
    private val onSettings: () -> Unit
) : JPanel(BorderLayout()) {

    private val keyLabel = JLabel("No card selected").apply {
        foreground = UI.text
        font = font.deriveFont(Font.BOLD, 12f)
    }

    init {
        background = UI.bgAlt
        // A subtle bottom border to separate the global header from the editor/toolbar
        border = BorderFactory.createMatteBorder(0, 0, 1, 0, UI.border)
        updateOrientation(false)
    }

    fun setKey(key: String) {
        keyLabel.text = key
    }

    fun updateOrientation(vertical: Boolean) {
        removeAll()

        if (vertical) {
            // SIDEBAR: Stacked Vertically
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createMatteBorder(0, 0, 0, 1, UI.border)

            add(Box.createVerticalStrut(10))
            keyLabel.horizontalAlignment = SwingConstants.CENTER
            keyLabel.alignmentX = Component.CENTER_ALIGNMENT
            add(keyLabel)

            add(Box.createVerticalStrut(10))
            add(createActionGroup(FlowLayout.CENTER))

            preferredSize = Dimension(120, 0)
        } else {
            // BOTTOM: Single horizontal line
            layout = BorderLayout()
            border = BorderFactory.createMatteBorder(0, 0, 1, 0, UI.border)

            val content = JPanel(BorderLayout()).apply {
                background = UI.bgAlt
                border = BorderFactory.createEmptyBorder(4, 12, 4, 8)
                add(keyLabel, BorderLayout.WEST)
                add(createActionGroup(FlowLayout.RIGHT), BorderLayout.EAST)
            }
            add(content, BorderLayout.CENTER)
            preferredSize = Dimension(0, 40)
        }

        revalidate()
        repaint()
    }

    private fun createActionGroup(alignment: Int) = JPanel(FlowLayout(alignment, 4, 0)).apply {
        background = UI.bgAlt
        add(createAction(AllIcons.Actions.Refresh, "Reload") { onReload() })
        add(createAction(AllIcons.General.Add, "Add Field") { onAdd() })
        add(createAction(AllIcons.Actions.MenuSaveall, "Save All") { onSave() })
        add(createAction(AllIcons.General.GearPlain, "Settings") { onSettings() })
    }

    private fun createAction(icon: Icon, tip: String, action: () -> Unit) =
        JBLabel(icon, SwingConstants.CENTER).apply {
            preferredSize = Dimension(28, 26)
            toolTipText = tip
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mousePressed(e: java.awt.event.MouseEvent) = action()
                override fun mouseEntered(e: java.awt.event.MouseEvent) {
                    isOpaque = true
                    background = UI.border
                    repaint()
                }
                override fun mouseExited(e: java.awt.event.MouseEvent) {
                    isOpaque = false
                    repaint()
                }
            })
        }
}