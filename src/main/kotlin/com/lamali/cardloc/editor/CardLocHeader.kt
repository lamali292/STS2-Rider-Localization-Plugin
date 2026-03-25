package com.lamali.cardloc.editor

import com.lamali.cardloc.editor.ui.TagToolbar
import com.lamali.cardloc.editor.ui.UI
import java.awt.*
import javax.swing.*

class CardLocHeader(
    private val onReload: () -> Unit,
    private val onAdd: () -> Unit,
    private val onSave: () -> Unit
) : JPanel() {

    private val keyLabel = JLabel("No card selected").apply {
        foreground = UI.text
        font = font.deriveFont(Font.BOLD, 12f)
        alignmentX = Component.CENTER_ALIGNMENT
    }

    val toolbar = TagToolbar()

    private val horizontalView = JPanel().apply { background = UI.bgAlt }
    private val verticalView = JPanel(BorderLayout()).apply {
        background = UI.bgAlt
        border = BorderFactory.createMatteBorder(0, 0, 0, 1, UI.border)
    }

    init {
        background = UI.bgAlt
        updateOrientation(vertical = false)
    }

    fun setKey(key: String) {
        keyLabel.text = key
        revalidate()
        repaint()
    }

    fun updateOrientation(vertical: Boolean) {
        removeAll()
        layout = BorderLayout()

        if (vertical) {
            setupVerticalView()
            add(verticalView, BorderLayout.CENTER)
            preferredSize = Dimension(100, 0)
        } else {
            setupHorizontalView()
            add(horizontalView, BorderLayout.CENTER)
            preferredSize = null
        }

        revalidate()
        repaint()
    }

    private fun setupHorizontalView() {
        horizontalView.removeAll()
        horizontalView.layout = BoxLayout(horizontalView, BoxLayout.Y_AXIS)

        val topRow = JPanel(BorderLayout()).apply {
            background = UI.bgAlt
            border = BorderFactory.createEmptyBorder(6, 12, 6, 8)
            add(keyLabel, BorderLayout.WEST)
            add(createActionGroup(FlowLayout.RIGHT), BorderLayout.EAST)
        }

        horizontalView.add(topRow)
        horizontalView.add(toolbar)
        toolbar.updateOrientation(false)
    }

    private fun setupVerticalView() {
        verticalView.removeAll()

        val topColumn = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = UI.bgAlt
            border = BorderFactory.createEmptyBorder(8, 2, 8, 2)

            keyLabel.horizontalAlignment = SwingConstants.CENTER
            add(keyLabel)
            add(Box.createVerticalStrut(6))

            val actions = createActionGroup(FlowLayout.CENTER).apply {
                maximumSize = Dimension(100, 100)
            }
            add(actions)

            add(Box.createVerticalStrut(8))
            add(JSeparator().apply {
                foreground = UI.border
                maximumSize = Dimension(Int.MAX_VALUE, 1)
            })
        }

        verticalView.add(topColumn, BorderLayout.NORTH)
        verticalView.add(toolbar, BorderLayout.CENTER)

        toolbar.updateOrientation(true)
    }

    private fun createActionGroup(alignment: Int) = JPanel(FlowLayout(alignment, 2, 2)).apply {
        background = UI.bgAlt
        add(createAction("⟳", "Reload") { onReload() })
        add(createAction("+", "Add Field") { onAdd() })
        add(createAction("💾", "Save") { onSave() })
    }

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