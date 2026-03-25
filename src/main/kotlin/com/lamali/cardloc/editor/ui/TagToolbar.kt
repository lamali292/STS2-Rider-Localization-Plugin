package com.lamali.cardloc.editor.ui

import com.lamali.cardloc.editor.ui.UI
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.text.*

class TagToolbar : JPanel() {

    private var activeEditor: JTextPane? = null

    init {
        layout = FlowLayout(FlowLayout.LEFT, 4, 4)
        background = UI.bgAlt
        maximumSize = Dimension(Int.MAX_VALUE, 32)
        border = BorderFactory.createEmptyBorder(0, 5, 0, 5)
        add(createIconButton("⊘", "Reset Formatting") { resetAll() })
        add(separator())
        TagDefs.color.forEach { tag ->
            add(createColorButton(tag))
        }
        add(separator())
        add(createIconButton("B", "Bold") { toggleStyle(isBold = true) })
        add(createIconButton("U", "Underline") { toggleStyle(isUnderline = true) })
    }

    fun setActiveEditor(editor: JTextPane) {
        this.activeEditor = editor
    }

    private fun resetAll() {
        val editor = activeEditor ?: return
        val attr = SimpleAttributeSet()
        StyleConstants.setForeground(attr, Color.WHITE)
        StyleConstants.setBold(attr, false)
        StyleConstants.setUnderline(attr, false)
        editor.setCharacterAttributes(attr, false)
    }

    private fun toggleStyle(isBold: Boolean = false, isUnderline: Boolean = false) {
        val editor = activeEditor ?: return
        val currentAttr = editor.styledDocument.getCharacterElement(editor.selectionStart).attributes
        val newAttr = SimpleAttributeSet()
        if (isBold) StyleConstants.setBold(newAttr, !StyleConstants.isBold(currentAttr))
        if (isUnderline) StyleConstants.setUnderline(newAttr, !StyleConstants.isUnderline(currentAttr))
        editor.setCharacterAttributes(newAttr, false)
    }

    private fun createIconButton(label: String, tooltip: String, action: () -> Unit): JComponent {
        val btn = JLabel(label, SwingConstants.CENTER).apply {
            preferredSize = Dimension(24, 24)
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = UI.text
            isOpaque = false
            toolTipText = tooltip
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }

        btn.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) = action()
            override fun mouseEntered(e: MouseEvent) {
                btn.isOpaque = true
                btn.background = UI.border // Subtle highlight
                btn.repaint()
            }
            override fun mouseExited(e: MouseEvent) {
                btn.isOpaque = false
                btn.repaint()
            }
        })
        return btn
    }

    private fun createColorButton(tag: TagDefs.TagDef): JComponent {
        val container = JPanel(GridBagLayout()).apply {
            preferredSize = Dimension(24, 24)
            background = UI.bgAlt
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = tag.label
        }

        val dot = JLabel(CircleIcon(tag.color ?: Color.WHITE, 12))
        container.add(dot)

        container.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                val editor = activeEditor ?: return
                val attr = SimpleAttributeSet()
                StyleConstants.setForeground(attr, tag.color ?: Color.WHITE)
                editor.setCharacterAttributes(attr, false)
            }
            override fun mouseEntered(e: MouseEvent) {
                container.isOpaque = true
                container.background = UI.border
                container.repaint()
            }
            override fun mouseExited(e: MouseEvent) {
                container.isOpaque = false
                container.repaint()
            }
        })
        return container
    }

    private fun separator() = JLabel("|").apply {
        foreground = UI.border
        font = font.deriveFont(10f)
        border = BorderFactory.createEmptyBorder(0, 4, 0, 4)
    }
}