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
        var i = 0
        val len = doc.length

        try {
            while (i < len) {
                val element = doc.getCharacterElement(i)
                val attr = element.attributes
                val start = element.startOffset
                val end = element.endOffset
                val partText = doc.getText(start, (end - start).coerceAtMost(len - start))

                // Determine active tags for this entire block
                val activeTags = mutableListOf<String>()

                // 1. Format Tags
                if (StyleConstants.isBold(attr)) activeTags.add("b")
                if (StyleConstants.isItalic(attr)) activeTags.add("i")
                if (StyleConstants.isUnderline(attr)) activeTags.add("u")

                // 2. Color Tags
                val color = StyleConstants.getForeground(attr)
                colorToTag[color]?.let { activeTags.add(it.tag) }

                // Note: Animation tags are currently handled as text inserts in Toolbar,

                // Construct the block
                val openTags = activeTags.joinToString("") { "[$it]" }
                val closeTags = activeTags.asReversed().joinToString("") { "[/$it]" }

                // Newlines should NOT be wrapped in tags in many game engines
                if (partText == "\n") {
                    sb.append("\n")
                } else {
                    sb.append(openTags).append(partText).append(closeTags)
                }

                i = end
            }
        } catch (e: Exception) {
            return ""
        }

        // Final cleanup: Merge adjacent identical tags, e.g., [/b][b] -> ""
        var result = sb.toString()
        val allPossibleTags = TagDefs.map.keys + listOf("b", "i", "u")
        allPossibleTags.forEach { tag ->
            result = result.replace("[/$tag][$tag]", "")
        }

        return result
    }

    fun parseAndSet(doc: StyledDocument, raw: String) {
        doc.remove(0, doc.length)
        // Convert escaped newlines for the editor
        val processed = raw.replace("\\n", "\n")

        // Regex to find [tags], [/tags], or plain text
        val tokens = Regex("""\[/?[^]]+]|[^\[]+""").findAll(processed)

        // Stack to track nested styles
        val styleStack = mutableListOf(SimpleAttributeSet().apply {
            StyleConstants.setForeground(this, Color.WHITE)
        })

        tokens.forEach { match ->
            val token = match.value
            when {
                token.startsWith("[/") -> {
                    if (styleStack.size > 1) styleStack.removeAt(styleStack.size - 1)
                }
                token.startsWith("[") -> {
                    val tagName = token.substring(1, token.length - 1)
                    val newStyle = SimpleAttributeSet(styleStack.last())

                    TagDefs.map[tagName]?.let { def ->
                        if (def.color != null) StyleConstants.setForeground(newStyle, def.color)
                        if (def.bold || tagName == "b") StyleConstants.setBold(newStyle, true)
                        if (def.italic || tagName == "i") StyleConstants.setItalic(newStyle, true)
                        if (tagName == "u") StyleConstants.setUnderline(newStyle, true)
                    }
                    styleStack.add(newStyle)
                }
                else -> {
                    doc.insertString(doc.length, token, styleStack.last())
                }
            }
        }
    }
}