package com.lamali.cardloc.core

import com.intellij.util.ui.UIUtil
import com.lamali.cardloc.editor.ui.TagDefs
import java.awt.Color
import javax.swing.text.AttributeSet
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

object BoldHandler : TagHandler {
    override fun handles(tagName: String, isClosing: Boolean) = tagName == "b"
    override fun onOpen(tagName: String, attr: SimpleAttributeSet, state: ParseState) = StyleConstants.setBold(attr, true)
    override fun onClose(tagName: String, attr: SimpleAttributeSet, state: ParseState) = StyleConstants.setBold(attr, false)
    override fun activeTag(attr: AttributeSet) = if (StyleConstants.isBold(attr)) "b" else null
}

object ItalicHandler : TagHandler {
    override fun handles(tagName: String, isClosing: Boolean) = tagName == "i"
    override fun onOpen(tagName: String, attr: SimpleAttributeSet, state: ParseState) = StyleConstants.setItalic(attr, true)
    override fun onClose(tagName: String, attr: SimpleAttributeSet, state: ParseState) = StyleConstants.setItalic(attr, false)
    override fun activeTag(attr: AttributeSet) = if (StyleConstants.isItalic(attr)) "i" else null
}

object UnderlineHandler : TagHandler {
    override fun handles(tagName: String, isClosing: Boolean) = tagName == "u"
    override fun onOpen(tagName: String, attr: SimpleAttributeSet, state: ParseState) = StyleConstants.setUnderline(attr, true)
    override fun onClose(tagName: String, attr: SimpleAttributeSet, state: ParseState) = StyleConstants.setUnderline(attr, false)
    override fun activeTag(attr: AttributeSet) = if (StyleConstants.isUnderline(attr)) "u" else null
}

class NamedColorHandler(tags: List<TagDefs.TagDef>) : TagHandler {
    private val byTag   = tags.filter { it.color != null }.associateBy { it.tag }
    private val byColor = tags.filter { it.color != null }.associateBy { it.color!! }

    override fun handles(tagName: String, isClosing: Boolean) = byTag.containsKey(tagName)

    override fun onOpen(tagName: String, attr: SimpleAttributeSet, state: ParseState) {
        val color = byTag[tagName]?.color ?: return
        state.colorStack.add(color)
        StyleConstants.setForeground(attr, color)
    }

    override fun onClose(tagName: String, attr: SimpleAttributeSet, state: ParseState) {
        if (state.colorStack.size > 1) state.colorStack.removeAt(state.colorStack.size - 1)
        StyleConstants.setForeground(attr, state.colorStack.last())
    }

    override fun activeTag(attr: AttributeSet) = byColor[StyleConstants.getForeground(attr)]?.tag
}

object HexColorHandler : TagHandler {
    private var currentTagDefs: TagDefs? = null

    fun setTagDefs(tagDefs: TagDefs) { currentTagDefs = tagDefs }

    override fun handles(tagName: String, isClosing: Boolean) =
        (!isClosing && tagName.startsWith("color=#")) || (isClosing && tagName == "color")

    override fun onOpen(tagName: String, attr: SimpleAttributeSet, state: ParseState) {
        runCatching { Color.decode(tagName.substring(6)) }.onSuccess { color ->
            state.colorStack.add(color)
            state.hexColorStack.add(color)
            StyleConstants.setForeground(attr, color)
        }
    }

    override fun onClose(tagName: String, attr: SimpleAttributeSet, state: ParseState) {
        if (state.colorStack.size > 1) state.colorStack.removeAt(state.colorStack.size - 1)
        if (state.hexColorStack.isNotEmpty()) state.hexColorStack.removeAt(state.hexColorStack.size - 1)
        StyleConstants.setForeground(attr, state.colorStack.last())
    }

    override fun activeTag(attr: AttributeSet): String? {
        val fg = StyleConstants.getForeground(attr)
        if (fg == Color.WHITE) return null
        if (fg == UIUtil.getTextFieldForeground()) return null
        if (currentTagDefs?.color?.any { it.color == fg } == true) return null
        return "color=#%02X%02X%02X".format(fg.red, fg.green, fg.blue)
    }

    override fun closeTagFor(openTag: String) = "color"

    override fun acceptsClose(tagName: String, state: ParseState) = state.hexColorStack.isNotEmpty()
}