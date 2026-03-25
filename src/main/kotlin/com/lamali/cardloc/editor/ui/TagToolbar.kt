package com.lamali.cardloc.editor.ui

import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.text.*

class TagToolbar : JPanel() {

    private var activeEditor: JTextPane? = null

    init {
        background = UI.bgAlt
        updateOrientation(false)
    }

    fun setActiveEditor(editor: JTextPane) {
        this.activeEditor = editor
    }

    fun updateOrientation(vertical: Boolean) {
        removeAll()
        if (vertical) {
            layout = FlowLayout(FlowLayout.CENTER, 2, 2)
            preferredSize = Dimension(95, 0)
            buildSidebarUI()
        } else {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            preferredSize = Dimension(Int.MAX_VALUE, 64)
            buildHorizontalUI()
        }
        revalidate()
        repaint()
    }

    private fun buildHorizontalUI() {
        // 1. CLEAR SECTION - Now specifically sized to fill the height
        val clearPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
            // We give the Clear button a larger preferred size for the horizontal view
            val bigClear = createIconButton("⊘", "Clear All", width = 36, height = 56) { resetAll() }.apply {
                font = font.deriveFont(18f) // Make the icon itself look bigger
            }
            add(bigClear, BorderLayout.CENTER)
        }
        add(clearPanel)
        add(createSeparator())

        // 2. COLORS
        val colorPanel = createGridColumn(2)
        TagDefs.color.forEach { colorPanel.add(createColorButton(it)) }
        add(colorPanel)
        add(createSeparator())

        // 3. FORMAT
        val formatPanel = createGridColumn(2)
        TagDefs.format.forEach { tag ->
            formatPanel.add(createIconButton(tag.label, "Toggle ${tag.label}") {
                toggleStyle(tag.bold, tag.italic, tag.tag == "u")
            })
        }
        add(formatPanel)
        add(createSeparator())

        // 4. ANIMATIONS
        val animPanel = createGridColumn(2)
        TagDefs.anim.forEach { tag ->
            animPanel.add(createIconButton("≈", tag.label) { applyTag(tag.tag) })
        }
        add(animPanel)

        add(Box.createHorizontalGlue())
    }

    private fun createGridColumn(rows: Int) = JPanel(GridLayout(rows, 0, 2, 2)).apply {
        isOpaque = false
        border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
    }

    private fun createSeparator() = JSeparator(JSeparator.VERTICAL).apply {
        foreground = UI.border
        maximumSize = Dimension(1, 50)
        border = BorderFactory.createEmptyBorder(8, 2, 8, 2)
    }

    private fun buildSidebarUI() {
        add(createIconButton("⊘", "Reset All") { resetAll() })
        (TagDefs.format + TagDefs.color + TagDefs.anim).forEach { tag ->
            if (tag.color != null && !tag.animated) add(createColorButton(tag))
            else add(createIconButton(if(tag.animated) "≈" else tag.label, tag.label) {
                if(tag.isFormat) toggleStyle(tag.bold, tag.italic, tag.tag == "u")
                else applyTag(tag.tag)
            })
        }
    }

    // --- LOGIC ---

    private fun resetAll() {
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

    private fun applyTag(tagName: String) {
        val text = activeEditor?.selectedText ?: ""
        activeEditor?.replaceSelection("[$tagName]$text[/$tagName]")
    }

    /**
     * Optimized Icon Button with better hit detection
     */
    private fun createIconButton(label: String, tip: String, width: Int = 28, height: Int = 28, action: () -> Unit) =
        JLabel(label, SwingConstants.CENTER).apply {
            preferredSize = Dimension(width, height)
            font = font.deriveFont(Font.BOLD, 12f)
            foreground = UI.text
            toolTipText = tip
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

            // Fix: Background must be set but usually same as parent until hover
            isOpaque = false
            background = UI.border

            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) = action()
                override fun mouseEntered(e: MouseEvent) {
                    isOpaque = true
                    repaint()
                }
                override fun mouseExited(e: MouseEvent) {
                    isOpaque = false
                    repaint()
                }
            })
        }

    private fun createColorButton(tag: TagDefs.TagDef) = JPanel(GridBagLayout()).apply {
        preferredSize = Dimension(28, 28)
        isOpaque = false
        background = UI.border
        toolTipText = tag.label
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        add(JLabel(CircleIcon(tag.color ?: Color.WHITE, 14)))
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                val attr = SimpleAttributeSet()
                StyleConstants.setForeground(attr, tag.color ?: Color.WHITE)
                activeEditor?.setCharacterAttributes(attr, false)
            }
            override fun mouseEntered(e: MouseEvent) { isOpaque = true; repaint() }
            override fun mouseExited(e: MouseEvent) { isOpaque = false; repaint() }
        })
    }
}