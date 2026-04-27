package com.lamali.cardloc.editor.ui

import com.lamali.cardloc.core.CardLocRegistry
import com.lamali.cardloc.data.CardLocContext
import com.lamali.cardloc.data.PinnedColor
import java.awt.Color
import javax.swing.*
import javax.swing.text.*

sealed class ToolbarAction {
    abstract val tooltip: String

    // Updated signature to include context
    abstract fun execute(editor: JTextPane, parent: JComponent, context: CardLocContext?)

    // Updated signature to include context
    open fun contextMenu(registry: CardLocRegistry, context: CardLocContext?, onChanged: () -> Unit): JPopupMenu? = null

    data class ClearFormatting(
        override val tooltip: String = "Clear Formatting"
    ) : ToolbarAction() {
        override fun execute(editor: JTextPane, parent: JComponent, context: CardLocContext?) {
            val attr = SimpleAttributeSet().apply {
                StyleConstants.setForeground(this, Color.WHITE)
                StyleConstants.setBold(this, false)
                StyleConstants.setItalic(this, false)
                StyleConstants.setUnderline(this, false)
            }
            editor.setCharacterAttributes(attr, false)
        }
    }

    data class NamedColor(
        val color: Color,
        val tag: String,
        override val tooltip: String
    ) : ToolbarAction() {
        override fun execute(editor: JTextPane, parent: JComponent, context: CardLocContext?) {
            val attr = SimpleAttributeSet().apply { StyleConstants.setForeground(this, color) }
            editor.setCharacterAttributes(attr, false)
        }

        override fun contextMenu(registry: CardLocRegistry, context: CardLocContext?, onChanged: () -> Unit) = JPopupMenu().apply {
            val ctx = context ?: return@apply // Context is required for unpinning
            add(JMenuItem("Unpin").apply {
                addActionListener {
                    registry.unpinColor(ctx, PinnedColor(tag = tag))
                    onChanged()
                }
            })
        }
    }

    data class CustomNamedColor(
        val color: Color,
        val hex: String,
        override val tooltip: String
    ) : ToolbarAction() {
        override fun execute(editor: JTextPane, parent: JComponent, context: CardLocContext?) {
            val attr = SimpleAttributeSet().apply { StyleConstants.setForeground(this, color) }
            editor.setCharacterAttributes(attr, false)
        }

        override fun contextMenu(registry: CardLocRegistry, context: CardLocContext?, onChanged: () -> Unit) = JPopupMenu().apply {
            val ctx = context ?: return@apply
            add(JMenuItem("Unpin").apply {
                addActionListener {
                    registry.unpinColor(ctx, PinnedColor(hex = hex))
                    onChanged()
                }
            })
        }
    }

    data class MoreColors(
        val extra: List<TagDefs.TagDef>,
        val registry: CardLocRegistry,
        val isVertical: Boolean = false,
        override val tooltip: String = "More Colors"
    ) : ToolbarAction() {
        override fun execute(editor: JTextPane, parent: JComponent, context: CardLocContext?) {
            val ctx = context ?: return

            // Logic moved here to ensure it's called
            val popup = ColorOverflowPopup(editor, extra, registry, ctx, isVertical) {
                // This triggers the UI refresh in the toolbar
                parent.firePropertyChange("rebuildToolbar", false, true)
            }

            if (isVertical) popup.show(parent, parent.width, 0)
            else            popup.show(parent, 0, parent.height)
        }
    }

    data class ToggleFormat(
        val tag: String,
        val label: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        override val tooltip: String
    ) : ToolbarAction() {
        override fun execute(editor: JTextPane, parent: JComponent, context: CardLocContext?) {
            val current = editor.styledDocument.getCharacterElement(editor.selectionStart).attributes
            val next = SimpleAttributeSet()
            if (bold)      StyleConstants.setBold(next, !StyleConstants.isBold(current))
            if (italic)    StyleConstants.setItalic(next, !StyleConstants.isItalic(current))
            if (underline) StyleConstants.setUnderline(next, !StyleConstants.isUnderline(current))
            editor.setCharacterAttributes(next, false)
        }
    }

    data class WrapTag(
        val tag: String,
        val label: String,
        override val tooltip: String
    ) : ToolbarAction() {
        override fun execute(editor: JTextPane, parent: JComponent, context: CardLocContext?) =
            wrapSelection(editor, tag)
    }

    protected fun wrapSelection(editor: JTextPane, openTag: String, closeTag: String = openTag) {
        val start = editor.selectionStart
        val selected = editor.selectedText ?: ""
        editor.replaceSelection("[$openTag]$selected[/$closeTag]")
        editor.caretPosition = if (selected.isEmpty()) start + openTag.length + 2
        else start + openTag.length + 2 + selected.length
        editor.requestFocusInWindow()
    }
}