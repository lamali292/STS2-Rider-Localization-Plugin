package com.lamali.cardloc.core

import com.lamali.cardloc.data.CardLocContext
import com.lamali.cardloc.editor.ui.TagDefs
import javax.swing.text.StyledDocument

// In TagParser
object TagParser {
    fun createHandlers(context: CardLocContext?): List<TagHandler> {
        val tagDefs = TagDefs.create(context)
        HexColorHandler.setTagDefs(tagDefs) // Set the current tagDefs

        return listOf(
            NamedColorHandler(tagDefs.color),
            HexColorHandler,
            BoldHandler,
            ItalicHandler,
            UnderlineHandler,
        )
    }

    fun toGameString(doc: StyledDocument, context: CardLocContext?): String {
        return TagSerializer(createHandlers(context)).serialize(doc)
    }

    fun parseAndSet(doc: StyledDocument, raw: String?, context: CardLocContext?) {
        TagDeserializer(createHandlers(context)).parse(doc, raw)
    }
}