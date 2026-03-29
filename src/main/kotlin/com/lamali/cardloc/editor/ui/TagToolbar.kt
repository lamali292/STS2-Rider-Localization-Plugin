package com.lamali.cardloc.editor.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.text.*

class TagToolbar : JPanel() {

    private var activeEditor: JTextPane? = null

    init {
        background = JBColor.namedColor("ActionButton.hoverBackground", UIUtil.getPanelBackground())
        updateOrientation(false)
    }

    fun setActiveEditor(editor: JTextPane) {
        this.activeEditor = editor
    }

    fun updateOrientation(vertical: Boolean) {
        removeAll()
        val borderColor = JBColor.namedColor("Borders.color", JBColor(0xC9C9C9, 0x646464))

        if (vertical) {
            layout = FlowLayout(FlowLayout.CENTER, 4, 4)
            preferredSize = JBUI.size(36, 0)
            border = JBUI.Borders.customLine(borderColor, 0, 0, 0, 1)
        } else {
            layout = FlowLayout(FlowLayout.LEFT, 4, 2)
            preferredSize = JBUI.size(0, 36)
            border = JBUI.Borders.customLine(borderColor, 0, 0, 1, 0)
        }

        buildUnifiedUI()
        revalidate()
        repaint()
    }

    private fun buildUnifiedUI() {
        add(createToolbarButton(AllIcons.Actions.ClearCash, "Clear Formatting") { resetFormatting() })
        TagDefs.color.forEach { add(createColorButton(it)) }
        TagDefs.format.forEach { tag ->
            add(createToolbarButton(tag.label, "Toggle ${tag.label}") {
                toggleStyle(tag.bold, tag.italic, tag.tag == "u")
            }.apply {
                if (tag.bold) font = font.deriveFont(Font.BOLD)
                if (tag.italic) font = font.deriveFont(Font.ITALIC)
            })
        }

        TagDefs.anim.forEach { tag ->
            add(createToolbarButton("≈", "Apply ${tag.label}") { applyAnimationTag(tag.tag) })
        }
    }

    private fun resetFormatting() {
        val attr = SimpleAttributeSet().apply {
            StyleConstants.setForeground(this, Color.WHITE)
            StyleConstants.setBold(this, false)
            StyleConstants.setItalic(this, false)
            StyleConstants.setUnderline(this, false)
        }
        activeEditor?.setCharacterAttributes(attr, false)
    }

    private fun toggleStyle(b: Boolean = false, i: Boolean = false, u: Boolean = false) {
        val editor = activeEditor ?: return
        val current = editor.styledDocument.getCharacterElement(editor.selectionStart).attributes
        val next = SimpleAttributeSet()
        if (b) StyleConstants.setBold(next, !StyleConstants.isBold(current))
        if (i) StyleConstants.setItalic(next, !StyleConstants.isItalic(current))
        if (u) StyleConstants.setUnderline(next, !StyleConstants.isUnderline(current))
        editor.setCharacterAttributes(next, false)
    }

    private fun applyAnimationTag(tagName: String) {
        val editor = activeEditor ?: return
        val start = editor.selectionStart

        val selectedText = editor.selectedText ?: ""
        val replacement = "[$tagName]$selectedText[/$tagName]"
        editor.replaceSelection(replacement)
        if (selectedText.isEmpty()) {
            editor.caretPosition = start + tagName.length + 2
        } else {
            editor.select(start + tagName.length + 2, start + tagName.length + 2 + selectedText.length)
        }
        editor.requestFocusInWindow()
    }

    private fun createToolbarButton(iconOrLabel: Any, tip: String, action: () -> Unit) =
        ActionButton(iconOrLabel, tip, 28, 28, action)

    private fun createColorButton(tag: TagDefs.TagDef) =
        ActionButton(CircleIcon(tag.color ?: Color.WHITE, 12), tag.label, 28, 28) {
            val attr = SimpleAttributeSet()
            StyleConstants.setForeground(attr, tag.color ?: Color.WHITE)
            activeEditor?.setCharacterAttributes(attr, false)
        }

    private inner class ActionButton(iconObj: Any, tip: String, w: Int, h: Int, val action: () -> Unit) : JLabel() {
        init {
            if (iconObj is Icon) icon = iconObj else text = iconObj.toString()
            horizontalAlignment = CENTER
            preferredSize = JBUI.size(w, h)
            toolTipText = tip
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            font = JBUI.Fonts.label(12f).asBold()
            foreground = UIUtil.getLabelForeground()

            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) = action()
                override fun mouseEntered(e: MouseEvent) {
                    isOpaque = true
                    background = JBColor.namedColor("ActionButton.hoverBackground", Color(0, 0, 0, 20))
                    repaint()
                }
                override fun mouseExited(e: MouseEvent) { isOpaque = false; repaint() }
            })
        }
    }
}