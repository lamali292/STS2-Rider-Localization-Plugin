package com.lamali.cardloc.editor

import com.lamali.cardloc.editor.ui.TagToolbar
import com.lamali.cardloc.editor.ui.UI
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.SwingConstants

class CardLocHeader(
    private val onReload: () -> Unit,
    private val onAdd: () -> Unit,
    private val onSave: () -> Unit
) : JPanel() {
    private val keyLabel = JLabel("No card selected").apply {
        foreground = UI.text
        font = font.deriveFont(Font.BOLD, 12f)
    }
    val toolbar = TagToolbar()

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = UI.bgAlt

        val topBar = JPanel(BorderLayout()).apply {
            background = UI.bgAlt
            border = BorderFactory.createEmptyBorder(6, 12, 6, 8)
            add(keyLabel, BorderLayout.WEST)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
                background = UI.bgAlt
                add(createAction("⟳", "Reload") { onReload() })
                add(Box.createHorizontalStrut(4))
                add(createAction("+", "Add Field") { onAdd() })
                add(Box.createHorizontalStrut(4))
                add(createAction("💾", "Save") { onSave() })
            }, BorderLayout.EAST)
        }

        add(topBar)
        add(toolbar)
        add(JSeparator().apply { maximumSize = Dimension(Int.MAX_VALUE, 1) })
    }

    fun setKey(key: String) { keyLabel.text = key }

    private fun createAction(symbol: String, tip: String, action: () -> Unit) =
        JLabel(symbol, SwingConstants.CENTER).apply {
            preferredSize = Dimension(28, 24)
            font = font.deriveFont(if (symbol == "+") 18f else 14f)
            foreground = UI.subtleText
            toolTipText = tip
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mousePressed(e: java.awt.event.MouseEvent) = action()
                override fun mouseEntered(e: java.awt.event.MouseEvent) {
                    foreground = UI.text; isOpaque = true; background = UI.border; repaint()
                }
                override fun mouseExited(e: java.awt.event.MouseEvent) {
                    foreground = UI.subtleText; isOpaque = false; repaint()
                }
            })
        }
}