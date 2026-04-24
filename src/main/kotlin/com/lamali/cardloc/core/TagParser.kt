package com.lamali.cardloc.core

import com.lamali.cardloc.editor.ui.TagDefs
import javax.swing.text.StyledDocument

object TagParser {

    private val handlers = listOf(
        NamedColorHandler(TagDefs.color), // must come before HexColorHandler
        HexColorHandler,
        BoldHandler,
        ItalicHandler,
        UnderlineHandler,
    )

    private val serializer   = TagSerializer(handlers)
    private val deserializer = TagDeserializer(handlers)

    fun toGameString(doc: StyledDocument): String = serializer.serialize(doc)
    fun parseAndSet(doc: StyledDocument, raw: String?)  = deserializer.parse(doc, raw)
}