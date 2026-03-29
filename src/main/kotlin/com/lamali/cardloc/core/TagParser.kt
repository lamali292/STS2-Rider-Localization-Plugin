package com.lamali.cardloc.core

import com.lamali.cardloc.editor.ui.TagDefs
import java.awt.Color
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument

object TagParser {

    private val colorToTag = TagDefs.color.associateBy { it.color }

    fun toGameString(doc: StyledDocument): String {
        val sb = StringBuilder()
        val len = doc.length
        val activeStyles = mutableListOf<String>()

        var i = 0
        while (i < len) {
            val element = doc.getCharacterElement(i)
            val attr = element.attributes
            val start = element.startOffset
            val end = element.endOffset
            val text = doc.getText(start, (end - start).coerceAtMost(len - start))

            // 1. Determine which "Formatting" tags should be active
            val targetStyles = mutableListOf<String>()
            if (StyleConstants.isBold(attr)) targetStyles.add("b")
            if (StyleConstants.isItalic(attr)) targetStyles.add("i")
            if (StyleConstants.isUnderline(attr)) targetStyles.add("u")

            val fg = StyleConstants.getForeground(attr)
            colorToTag[fg]?.let { targetStyles.add(it.tag) }

            // 2. Close styles that changed (LIFO order)
            val toClose = activeStyles.filter { it !in targetStyles }.reversed()
            for (tag in toClose) {
                sb.append("[/$tag]")
                activeStyles.remove(tag)
            }

            // 3. Open new styles
            val toOpen = targetStyles.filter { it !in activeStyles }
            for (tag in toOpen) {
                sb.append("[$tag]")
                activeStyles.add(tag)
            }

            // 4. Append text (this includes [jitter] if it was inserted as text)
            sb.append(text)
            i = end
        }

        // Close remaining formatting
        activeStyles.reversed().forEach { sb.append("[/$it]") }

        return sb.toString()
    }

    fun parseAndSet(doc: StyledDocument, raw: String?) {
        doc.remove(0, doc.length)
        if (raw.isNullOrEmpty()) return

        // Regex matches [tags] or plain text
        val tokens = Regex("""\[/?[^]]+]|[^\[]+""").findAll(raw)

        val currentAttr = SimpleAttributeSet().apply {
            StyleConstants.setForeground(this, Color.WHITE)
        }
        val colorStack = mutableListOf(Color.WHITE)

        tokens.forEach { match ->
            val token = match.value

            // Logic for Formatting Tags ONLY
            if (token.startsWith("[") && token.endsWith("]") && token.length >= 3) {
                val isClosing = token.startsWith("[/")
                val tagName = if (isClosing) token.substring(2, token.length - 1) else token.substring(1, token.length - 1)
                val def = TagDefs.map[tagName]

                // We ONLY process it as a style if it is a Color or a standard Format (B, I, U)
                val isFormatting = def != null && (def.isFormat || def.color != null) && !def.animated

                if (isFormatting) {
                    if (isClosing) {
                        when {
                            tagName == "b" || def.bold -> StyleConstants.setBold(currentAttr, false)
                            tagName == "i" || def.italic -> StyleConstants.setItalic(currentAttr, false)
                            tagName == "u" -> StyleConstants.setUnderline(currentAttr, false)
                            def.color != null -> {
                                if (colorStack.size > 1) colorStack.removeAt(colorStack.size - 1)
                                StyleConstants.setForeground(currentAttr, colorStack.last())
                            }
                        }
                    } else {
                        when {
                            tagName == "b" || def.bold -> StyleConstants.setBold(currentAttr, true)
                            tagName == "i" || def.italic -> StyleConstants.setItalic(currentAttr, true)
                            tagName == "u" -> StyleConstants.setUnderline(currentAttr, true)
                            def.color != null -> {
                                colorStack.add(def.color)
                                StyleConstants.setForeground(currentAttr, def.color)
                            }
                        }
                    }
                    return@forEach // Style applied, don't insert the tag as text
                }
            }
            doc.insertString(doc.length, token, currentAttr)
        }
    }
}