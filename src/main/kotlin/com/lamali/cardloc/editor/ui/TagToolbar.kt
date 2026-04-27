package com.lamali.cardloc.editor.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.lamali.cardloc.core.CardLocRegistry
import com.lamali.cardloc.data.CardLocContext
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

class TagToolbar(private val registry: CardLocRegistry) : JPanel() {

    private var activeEditor: JTextPane? = null
    private var isVertical = false
    private var currentContext: CardLocContext? = null // Added property

    init {
        background = JBColor.namedColor("ActionButton.hoverBackground", UIUtil.getPanelBackground())
        updateOrientation(false)
    }

    fun setActiveEditor(editor: JTextPane) {
        this.activeEditor = editor
    }
    /**
     * Called by CardLocPanel whenever a new file/context is loaded.
     */
    fun setContext(context: CardLocContext) {
        this.currentContext = context
        rebuild()
    }

    fun updateOrientation(vertical: Boolean) {
        isVertical = vertical
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

        ToolbarActionProvider.getActions(registry, currentContext, isVertical).forEach { add(buildButton(it)) }

        revalidate()
        repaint()
    }

    private fun rebuild() = updateOrientation(isVertical)

    private fun buildButton(action: ToolbarAction): JComponent {
        val icon: Any = when (action) {
            is ToolbarAction.ClearFormatting  -> AllIcons.Actions.ClearCash
            is ToolbarAction.NamedColor       -> CircleIcon(action.color, 12)
            is ToolbarAction.CustomNamedColor -> CircleIcon(action.color, 12)
            is ToolbarAction.MoreColors       -> ColorWheelIcon(14)
            is ToolbarAction.ToggleFormat     -> action.label
            is ToolbarAction.WrapTag          -> action.label
        }

        return ActionButton(icon, action.tooltip, 28, 28) {
            activeEditor?.let { editor ->
                when (action) {
                    is ToolbarAction.MoreColors -> {
                        val context = currentContext ?: return@let // Safety check
                        // FIXED: Added currentContext to the constructor
                        val popup = ColorOverflowPopup(editor, action.extra, action.registry, context, isVertical) {
                            rebuild()
                        }
                        if (isVertical) popup.show(this, width, 0)
                        else            popup.show(this, 0, height)
                    }
                    else -> {
                        // Pass context to execute if the action needs it (like pinning)
                        action.execute(editor, this, currentContext)
                    }
                }
            }
        }.apply {
            if (action is ToolbarAction.ToggleFormat) {
                if (action.bold)   font = font.deriveFont(Font.BOLD)
                if (action.italic) font = font.deriveFont(Font.ITALIC)
            }

            // Pass context to menu so unpinning works for the correct project
            action.contextMenu(registry, currentContext) { rebuild() }?.let { menu ->
                addMouseListener(object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent) {
                        if (SwingUtilities.isRightMouseButton(e)) menu.show(this@apply, e.x, e.y)
                    }
                })
            }
        }
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
                override fun mousePressed(e: MouseEvent) {
                    if (SwingUtilities.isLeftMouseButton(e)) action()
                }
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