package com.lamali.cardloc.editor

import com.lamali.cardloc.editor.ui.TagDefs
import com.lamali.cardloc.editor.ui.TagToolbar
import com.lamali.cardloc.editor.ui.UI
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.event.*
import javax.swing.text.*

class AutoScalingEditor : JTextPane() {
    init {
        isOpaque = false
        putClientProperty("JEditorPane.honorDisplayProperties", true)
    }

    override fun getScrollableTracksViewportWidth(): Boolean = true
    override fun getScrollableTracksViewportHeight(): Boolean = false

    override fun getPreferredSize(): Dimension {
        val parentWidth = parent?.width ?: 0
        if (parentWidth > 0) {
            setSize(parentWidth, Int.MAX_VALUE)
        }
        val d = super.getPreferredSize()
        return Dimension(10, d.height.coerceAtLeast(45))
    }
}
class SymbolViewFactory(val delegate: ViewFactory) : ViewFactory {
    override fun create(elem: Element): View {
        return if (elem.name == AbstractDocument.ParagraphElementName) {
            NewlineSymbolParagraphView(elem)
        } else delegate.create(elem)
    }
}

class NewlineSymbolParagraphView(elem: Element) : ParagraphView(elem) {
    override fun paint(g: Graphics, allocation: Shape) {
        super.paint(g, allocation)
        val doc = document
        val lastCharIdx = endOffset - 1
        if (lastCharIdx >= 0 && lastCharIdx < doc.length - 1) {
            try {
                if (doc.getText(lastCharIdx, 1) == "\n") {
                    val shape = modelToView(lastCharIdx, allocation, Position.Bias.Forward) ?: return
                    val r = shape.bounds
                    val g2d = g as Graphics2D

                    val symbolFont = Font(Font.MONOSPACED, Font.PLAIN, 12)

                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                    g2d.color = UI.subtleText.darker()
                    g2d.font = symbolFont
                    val x = r.x + 2
                    val y = r.y + r.height - 5

                    g2d.drawString("¬", x, y)
                }
            } catch (_: Exception) {
            }
        }
    }
}

class FieldRow(
    val fieldName: String,
    initialValue: String,
    val onUpdate: () -> Unit
) : JPanel() {

    val editor = AutoScalingEditor()
    val text: String get() = generateGameString()

    override fun getMinimumSize(): Dimension {
        return Dimension(50, super.getMinimumSize().height)
    }
    init {
        layout = BorderLayout()
        background = UI.panel
        alignmentX = LEFT_ALIGNMENT
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        add(JLabel(fieldName.uppercase()).apply {
            foreground = UI.subtleText
            font = font.deriveFont(Font.BOLD, 10f)
            border = BorderFactory.createEmptyBorder(0, 0, 5, 0)
        }, BorderLayout.NORTH)

        // IMPORTANT: Set Kit before parsing text
        editor.editorKit = object : StyledEditorKit() {
            override fun getViewFactory(): ViewFactory = SymbolViewFactory(super.getViewFactory())
        }

        setupEditor(initialValue)

        val scroll = JScrollPane(editor).apply {
            border = BorderFactory.createLineBorder(UI.border)
            viewport.background = UI.previewBg
            // Force the scrollpane to not allow horizontal scrolling
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
        }
        add(scroll, BorderLayout.CENTER)

        editor.styledDocument.addDocumentListener(object : DocumentListener {
            private fun trigger() {
                // Revalidate the row AND the container so it grows vertically
                editor.revalidate()
                this@FieldRow.revalidate()
                parent?.revalidate()
                onUpdate()
            }
            override fun insertUpdate(e: DocumentEvent) = trigger()
            override fun removeUpdate(e: DocumentEvent) = trigger()
            override fun changedUpdate(e: DocumentEvent) = trigger()
        })
    }

    private fun setupEditor(value: String) {
        editor.apply {
            background = UI.previewBg
            foreground = UI.text
            caretColor = Color.WHITE
            font = Font("Segoe UI", Font.PLAIN, 14)
            margin = Insets(8, 8, 8, 8)

            addKeyListener(object : KeyAdapter() {
                override fun keyReleased(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER) {
                        val blank = SimpleAttributeSet().apply {
                            StyleConstants.setForeground(this, Color.WHITE)
                        }
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
                    if (char == '\n') sb.append("\n")
                    else {
                        activeTags.forEach { sb.append("[$it]") }
                        sb.append(char)
                        activeTags.asReversed().forEach { sb.append("[/$it]") }
                    }
                }
                i = end
            }
        } catch (_: Exception) {
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
                TagDefs.map[tagName]?.let { def ->
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

    fun connectToolbar(toolbar: TagToolbar) {
        editor.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent) = toolbar.setActiveEditor(editor)
        })
    }
}