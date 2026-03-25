package com.lamali.cardloc.editor

import com.lamali.cardloc.editor.logic.TagParser
import com.lamali.cardloc.editor.ui.*
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.StyledEditorKit

class FieldRow(
    val fieldName: String,
    initialValue: String,
    private val onUpdate: () -> Unit
) : JPanel() {

    val editor = AutoScalingEditor()
    val text: String get() = TagParser.toGameString(editor.styledDocument)

    init {
        layout = BorderLayout()
        background = UI.panel
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        // Title Label
        add(JLabel(fieldName.uppercase()).apply {
            foreground = UI.subtleText
            font = font.deriveFont(Font.BOLD, 10f)
            border = BorderFactory.createEmptyBorder(0, 0, 5, 0)
        }, BorderLayout.NORTH)

        // Editor Setup
        editor.editorKit = object : StyledEditorKit() {
            override fun getViewFactory() = SymbolViewFactory(super.getViewFactory())
        }

        setupEditorStyles()
        TagParser.parseAndSet(editor.styledDocument, initialValue)

        // ScrollPane wrapping
        val scroll = JScrollPane(editor).apply {
            border = BorderFactory.createLineBorder(UI.border)
            viewport.background = UI.previewBg
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
        }
        add(scroll, BorderLayout.CENTER)

        // Change Listener
        editor.styledDocument.addDocumentListener(object : DocumentListener {
            fun trigger() {
                editor.revalidate()
                this@FieldRow.revalidate()
                onUpdate()
            }
            override fun insertUpdate(e: DocumentEvent) = trigger()
            override fun removeUpdate(e: DocumentEvent) = trigger()
            override fun changedUpdate(e: DocumentEvent) = trigger()
        })
    }

    private fun setupEditorStyles() {
        editor.apply {
            background = UI.previewBg
            foreground = UI.text
            caretColor = Color.WHITE
            font = Font("Segoe UI", Font.PLAIN, 14)
            margin = Insets(8, 8, 8, 8)
        }
    }

    fun connectToolbar(toolbar: TagToolbar) {
        editor.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) = toolbar.setActiveEditor(editor)
        })
    }
}