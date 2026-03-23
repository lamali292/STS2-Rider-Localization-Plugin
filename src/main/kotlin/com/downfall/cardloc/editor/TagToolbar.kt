package com.downfall.cardloc.editor

import java.awt.*
import javax.swing.*
import javax.swing.text.*

class TagToolbar : JPanel() {

    private var activeEditor: JTextPane? = null

    init {
        // Reduced gap from 5 to 2
        layout = FlowLayout(FlowLayout.LEFT, 2, 2)
        background = UI.panel
        maximumSize = Dimension(Int.MAX_VALUE, 32)

        add(resetColorButton())
        add(Box.createHorizontalStrut(2))
        add(separator())
        add(Box.createHorizontalStrut(2))

        TagDefs.color.forEach { add(colorCircleButton(it)) }

        add(Box.createHorizontalStrut(4))
        add(separator())
        add(Box.createHorizontalStrut(4))

        add(formatButton("B", isBold = true))
        add(formatButton("U", isUnderline = true))
    }

    fun setActiveEditor(editor: JTextPane) {
        this.activeEditor = editor
    }

    private fun colorCircleButton(tag: TagDefs.TagDef) = JButton(CircleIcon(tag.color ?: Color.WHITE)).apply {
        preferredSize = Dimension(22, 22) // Smaller, tighter buttons
        isContentAreaFilled = false
        isBorderPainted = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = tag.label

        addActionListener {
            val editor = activeEditor ?: return@addActionListener
            val doc = editor.styledDocument
            val attr = doc.getCharacterElement(editor.selectionStart).attributes
            val currentColor = StyleConstants.getForeground(attr)

            val newAttr = SimpleAttributeSet()
            val targetColor = if (currentColor == tag.color) Color.WHITE else tag.color!!
            StyleConstants.setForeground(newAttr, targetColor)
            editor.setCharacterAttributes(newAttr, false)
        }
    }

    private fun resetColorButton() = JButton(CircleIcon(Color.WHITE)).apply {
        preferredSize = Dimension(22, 22)
        isContentAreaFilled = false
        isBorderPainted = true
        border = BorderFactory.createLineBorder(Color.GRAY, 1)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = "Clear Color"

        addActionListener {
            val editor = activeEditor ?: return@addActionListener
            val attr = SimpleAttributeSet()
            StyleConstants.setForeground(attr, Color.WHITE)
            editor.setCharacterAttributes(attr, false)
        }
    }

    private fun formatButton(label: String, isBold: Boolean = false, isUnderline: Boolean = false) = JButton(label).apply {
        preferredSize = Dimension(24, 22)
        margin = Insets(0,0,0,0)
        font = font.deriveFont(10f)
        addActionListener {
            val editor = activeEditor ?: return@addActionListener
            val attr = editor.styledDocument.getCharacterElement(editor.selectionStart).attributes
            val newAttr = SimpleAttributeSet()
            if (isBold) StyleConstants.setBold(newAttr, !StyleConstants.isBold(attr))
            if (isUnderline) StyleConstants.setUnderline(newAttr, !StyleConstants.isUnderline(attr))
            editor.setCharacterAttributes(newAttr, false)
        }
    }

    private fun separator() = JSeparator(JSeparator.VERTICAL).apply {
        preferredSize = Dimension(1, 16)
        foreground = UI.border
    }
}