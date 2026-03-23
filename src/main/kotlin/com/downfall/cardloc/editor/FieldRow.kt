package com.downfall.cardloc

import com.downfall.cardloc.editor.TagDefs
import com.downfall.cardloc.editor.TagToolbar
import com.downfall.cardloc.editor.UI
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.event.*
import javax.swing.text.*

class FieldRow(
    val fieldName: String,
    initialValue: String,
    val onUpdate: () -> Unit
) : JPanel() {

    val editor = JTextPane()
    val text: String get() = generateGameString()

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = UI.panel
        alignmentX = LEFT_ALIGNMENT
        border = BorderFactory.createEmptyBorder(8, 0, 0, 0)

        add(label(fieldName.uppercase()))
        add(Box.createVerticalStrut(5))
        add(autoHeightScroll(editor))

        setupEditor(initialValue)

        editor.styledDocument.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = onUpdate()
            override fun removeUpdate(e: DocumentEvent) = onUpdate()
            override fun changedUpdate(e: DocumentEvent) = onUpdate()
        })
    }

    private fun setupEditor(value: String) {
        editor.apply {
            background = UI.previewBg
            foreground = UI.text
            caretColor = Color.WHITE
            font = Font("Segoe UI", Font.PLAIN, 14)
            margin = Insets(8, 8, 8, 8)

            // Handle the "Enter" key to reset formatting to white
            addKeyListener(object : KeyAdapter() {
                override fun keyReleased(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER) {
                        val blank = SimpleAttributeSet()
                        StyleConstants.setForeground(blank, Color.WHITE)
                        StyleConstants.setBold(blank, false)
                        StyleConstants.setUnderline(blank, false)

                        // Reset the typing attributes for the new line
                        inputAttributes.removeAttributes(inputAttributes)
                        inputAttributes.addAttributes(blank)
                        setCharacterAttributes(blank, false)
                    }
                }
            })
        }
        parseAndSetText(value)
    }

    private fun generateGameString(): String {
        val doc = editor.styledDocument
        val sb = StringBuilder()
        val colorToTag = TagDefs.color.associateBy { it.color }

        try {
            var i = 0
            while (i < doc.length) {
                val element = doc.getCharacterElement(i)
                val attr = element.attributes
                val start = element.startOffset
                val end = element.endOffset
                val partText = doc.getText(start, (end - start).coerceAtMost(doc.length - start))

                val color = StyleConstants.getForeground(attr)
                val isUnderlined = StyleConstants.isUnderline(attr)
                val isBold = StyleConstants.isBold(attr)
                val tagDef = colorToTag[color]

                val activeTags = mutableListOf<String>()
                if (isBold) activeTags.add("b")
                if (isUnderlined) activeTags.add("u")
                if (tagDef != null && color != Color.WHITE && color != UI.text) {
                    activeTags.add(tagDef.tag)
                }

                partText.forEach { char ->
                    if (char == '\n') {
                        sb.append("\n")
                    } else {
                        activeTags.forEach { sb.append("[$it]") }
                        sb.append(char)
                        activeTags.asReversed().forEach { sb.append("[/$it]") }
                    }
                }
                i = end
            }
        } catch (e: Exception) {
            return editor.text.replace("\n", "\\n")
        }

        var result = sb.toString()
        TagDefs.map.keys.plus(listOf("b", "u")).forEach { tag ->
            result = result.replace("[/$tag][$tag]", "")
        }
        return result
    }

    private fun parseAndSetText(raw: String) {
        val processed = raw.replace("\\n", "\n")
        val doc = editor.styledDocument
        doc.remove(0, doc.length)
        val tokens = Regex("""\[/?[^]]+]|[^\[]+""").findAll(processed)
        val styleStack = mutableListOf(SimpleAttributeSet())
        tokens.forEach { match ->
            val token = match.value
            if (token.startsWith("[/")) {
                if (styleStack.size > 1) styleStack.removeAt(styleStack.size - 1)
            } else if (token.startsWith("[")) {
                val tagName = token.substring(1, token.length - 1)
                val newStyle = SimpleAttributeSet(styleStack.last())
                val def = TagDefs.map[tagName]
                if (def != null) {
                    if (def.color != null) StyleConstants.setForeground(newStyle, def.color)
                    if (def.bold) StyleConstants.setBold(newStyle, true)
                    if (tagName == "u") StyleConstants.setUnderline(newStyle, true)
                }
                styleStack.add(newStyle)
            } else {
                doc.insertString(doc.length, token, styleStack.last())
            }
        }
    }

    private fun label(text: String) = JLabel(text).apply {
        foreground = UI.subtleText
        font = font.deriveFont(Font.BOLD, 10f)
        alignmentX = LEFT_ALIGNMENT
    }

    private fun autoHeightScroll(c: JComponent) = JScrollPane(c).apply {
        border = BorderFactory.createLineBorder(UI.border)
        viewport.background = c.background
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
        alignmentX = LEFT_ALIGNMENT
    }

    fun connectToolbar(toolbar: TagToolbar) {
        editor.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) = toolbar.setActiveEditor(editor)
        })
    }
}