package com.lamali.cardloc.core

import java.awt.Color
import javax.swing.text.AttributeSet
import javax.swing.text.SimpleAttributeSet

data class ParseState(
    val colorStack: MutableList<Color> = mutableListOf(Color.WHITE)
)

interface TagHandler {
    /** Deserialization: can this handler process this tag? */
    fun handles(tagName: String, isClosing: Boolean): Boolean

    fun onOpen(tagName: String, attr: SimpleAttributeSet, state: ParseState)
    fun onClose(tagName: String, attr: SimpleAttributeSet, state: ParseState)

    /** Serialization: returns the open-tag string if active for these attrs, else null */
    fun activeTag(attr: AttributeSet): String?

    /** Given an open-tag string produced by this handler, returns the close-tag name */
    fun closeTagFor(openTag: String): String = openTag
}