package com.lamali.cardloc.core

import com.intellij.util.ui.UIUtil
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants
import javax.swing.text.StyledDocument

class TagDeserializer(private val handlers: List<TagHandler>) {

    fun parse(doc: StyledDocument, raw: String?) {
        doc.remove(0, doc.length)
        if (raw.isNullOrEmpty()) return

        val tokens = Regex("""\[/?[^]]+]|[^\[]+""").findAll(raw)
        val attr = SimpleAttributeSet().apply {
            StyleConstants.setForeground(this, UIUtil.getTextFieldForeground())
        }
        val state = ParseState(colorStack = mutableListOf(UIUtil.getTextFieldForeground()))

        tokens.forEach { match ->
            val token = match.value
            if (token.startsWith("[") && token.endsWith("]") && token.length >= 3) {
                val isClosing = token.startsWith("[/")
                val tagName = if (isClosing) token.substring(2, token.length - 1)
                else token.substring(1, token.length - 1)

                val handler = handlers.firstOrNull { it.handles(tagName, isClosing) }
                if (handler != null && (!isClosing || handler.acceptsClose(tagName, state))) {
                    if (isClosing) handler.onClose(tagName, attr, state)
                    else handler.onOpen(tagName, attr, state)
                    return@forEach // handled — don't insert as text
                }
            }
            doc.insertString(doc.length, token, attr)
        }
    }
}