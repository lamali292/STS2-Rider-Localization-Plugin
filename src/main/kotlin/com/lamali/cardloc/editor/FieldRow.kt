package com.lamali.cardloc.editor

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.lamali.cardloc.core.TagParser
import com.lamali.cardloc.data.CardLocContext
import com.lamali.cardloc.editor.ui.*
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.event.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyledEditorKit

class FieldRow(
    val fieldName: String,
    initialValue: String,
    private val context: CardLocContext?,
    private val onUpdate: () -> Unit
) : JPanel(BorderLayout()) {

    val editor = AutoScalingEditor()
    val text: String get() = TagParser.toGameString(editor.styledDocument, context)

    init {
        background = UIUtil.getPanelBackground()
        border = JBUI.Borders.empty(8, 12)
        val titleLabel = JBLabel(fieldName.uppercase()).apply {
            foreground = UIUtil.getContextHelpForeground()
            font = JBUI.Fonts.label(11f).asBold()
            border = JBUI.Borders.emptyBottom(4)
        }
        add(titleLabel, BorderLayout.NORTH)

        editor.editorKit = object : StyledEditorKit() {
            override fun getViewFactory() = SymbolViewFactory(super.getViewFactory())
        }

        editor.putClientProperty("ActionMap.LocalEventHandling", true)

        setupEditorStyles()
        editor.transferHandler = object : TransferHandler() {
            override fun canImport(support: TransferSupport) =
                support.isDataFlavorSupported(DataFlavor.stringFlavor)

            override fun importData(support: TransferSupport): Boolean {
                if (!canImport(support)) return false
                if (support.isDrop && support.component == editor) {
                    return super.importData(support)
                }
                val text = runCatching {
                    support.transferable.getTransferData(DataFlavor.stringFlavor) as? String
                }.getOrNull() ?: return false

                val doc = editor.styledDocument
                val caretAttr = SimpleAttributeSet(
                    doc.getCharacterElement(editor.caretPosition).attributes
                )
                // In FieldRow.kt - update the TransferHandler section:
                val fg = StyleConstants.getForeground(caretAttr)
                val tagDefs = TagDefs.create(context) // Get current tags
                if (fg != Color.WHITE && fg != UIUtil.getTextFieldForeground()
                    && tagDefs.color.none { it.color == fg }) {
                    StyleConstants.setForeground(caretAttr, UIUtil.getTextFieldForeground())
                }
                editor.replaceSelection("")
                doc.insertString(editor.caretPosition, text, caretAttr)
                return true
            }

            override fun createTransferable(c: JComponent) = super.createTransferable(c)
            override fun getSourceActions(c: JComponent) = COPY_OR_MOVE
        }

        TagParser.parseAndSet(editor.styledDocument, initialValue, context)

        val scroll = JScrollPane(editor).apply {
            val borderColor = JBColor.namedColor("Component.borderColor", JBColor(0xC9C9C9, 0x646464))
            border = JBUI.Borders.customLine(borderColor, 1)

            viewport.background = UIUtil.getTextFieldBackground()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
        }
        add(scroll, BorderLayout.CENTER)

        editor.styledDocument.addDocumentListener(object : DocumentListener {
            private fun trigger() {
                // Use invokeLater to avoid potential mutation race conditions
                SwingUtilities.invokeLater {
                    editor.revalidate()
                    this@FieldRow.revalidate()
                    onUpdate()
                }
            }
            override fun insertUpdate(e: DocumentEvent) = trigger()
            override fun removeUpdate(e: DocumentEvent) = trigger()
            override fun changedUpdate(e: DocumentEvent) = trigger()
        })
    }

    private fun setupEditorStyles() {
        editor.apply {
            background = UIUtil.getTextFieldBackground()
            caretColor = UIUtil.getTextFieldForeground()
            font = UIUtil.getLabelFont()
            margin = JBUI.insets(6)
        }
        val defaultAttr = SimpleAttributeSet().apply {
            StyleConstants.setForeground(this, UIUtil.getTextFieldForeground())
        }
        editor.styledDocument.setParagraphAttributes(0, editor.styledDocument.length, defaultAttr, false)
    }

    fun connectToolbar(toolbar: TagToolbar) {
        editor.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) {
                toolbar.setActiveEditor(editor)
                editor.requestFocusInWindow()
            }
        })
    }
}