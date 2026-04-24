package com.lamali.cardloc.editor.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.lamali.cardloc.core.CardLocRegistry
import com.lamali.cardloc.data.PinnedColor
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

class ColorOverflowPopup(
    private val editor: JTextPane,
    tags: List<TagDefs.TagDef>,
    private val registry: CardLocRegistry,
    isVertical: Boolean = false,
    private val onPinChanged: () -> Unit
) : JPopupMenu() {

    private val savedStart = editor.selectionStart
    private val savedEnd   = editor.selectionEnd

    private val chipSize = 28
    private val cols = if (isVertical) 1 else (tags.size + 1).coerceAtMost(6)

    init {
        border = JBUI.Borders.empty(6)
        layout = GridLayout(0, cols, 4, 4)

        tags.forEach { tag -> add(colorChip(CircleIcon(tag.color ?: Color.WHITE, 14), tag.label,
            onClick = { applyNamed(tag) },
            onPin   = { registry.pinColor(PinnedColor(tag = tag.tag)); onPinChanged(); isVisible = false }
        ))}

        add(colorChip(ColorWheelIcon(14), "Custom color…",
            onClick = { pickColor() },
            onPin   = null
        ))

        pack()
    }

    private fun colorChip(icon: Icon, tip: String, onClick: () -> Unit, onPin: (() -> Unit)?) = JLabel().apply {
        this.icon = icon
        toolTipText = tip
        preferredSize = JBUI.size(chipSize, chipSize)
        horizontalAlignment = SwingConstants.CENTER
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                when {
                    SwingUtilities.isRightMouseButton(e) && onPin != null -> {
                        SwingUtilities.invokeLater {
                            JPopupMenu().apply {
                                add(JMenuItem("Pin to toolbar").apply { addActionListener { onPin() } })
                            }.show(editor,
                                SwingUtilities.convertPoint(this@apply, e.x, e.y, editor).x,
                                SwingUtilities.convertPoint(this@apply, e.x, e.y, editor).y
                            )
                        }
                    }
                    SwingUtilities.isLeftMouseButton(e) -> onClick()
                }
            }
            override fun mouseEntered(e: MouseEvent) {
                isOpaque = true
                background = JBColor.namedColor("ActionButton.hoverBackground", Color(0, 0, 0, 20))
                repaint()
            }
            override fun mouseExited(e: MouseEvent) { isOpaque = false; repaint() }
        })
    }

    private fun applyNamed(tag: TagDefs.TagDef) {
        isVisible = false
        val attr = SimpleAttributeSet().apply { StyleConstants.setForeground(this, tag.color ?: Color.WHITE) }
        editor.select(savedStart, savedEnd)
        editor.setCharacterAttributes(attr, false)
        editor.caretPosition = savedEnd
        editor.requestFocusInWindow()
    }

    private fun pickColor() {
        isVisible = false
        val initial = StyleConstants.getForeground(
            editor.styledDocument.getCharacterElement(savedStart).attributes
        )
        val picked = JColorChooser.showDialog(editor, "Choose Color", initial) ?: return
        val attr = SimpleAttributeSet().apply { StyleConstants.setForeground(this, picked) }
        editor.select(savedStart, savedEnd)
        editor.setCharacterAttributes(attr, false)
        editor.caretPosition = savedEnd

        val hex = "#%02X%02X%02X".format(picked.red, picked.green, picked.blue)
        val label = JOptionPane.showInputDialog(editor, "Pin this color? Enter a label (or cancel to skip)", hex)
        if (label != null) {
            registry.pinColor(PinnedColor(hex = hex, label = label))
            onPinChanged()
        }
        editor.requestFocusInWindow()
    }
}