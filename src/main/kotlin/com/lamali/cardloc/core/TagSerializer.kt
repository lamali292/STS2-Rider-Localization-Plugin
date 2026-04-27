package com.lamali.cardloc.core

import javax.swing.text.StyledDocument

class TagSerializer(private val handlers: List<TagHandler>) {

    fun serialize(doc: StyledDocument): String {
        val sb = StringBuilder()
        val len = doc.length
        val activeStyles = mutableListOf<Pair<String, TagHandler>>()

        var i = 0
        while (i < len) {
            val element = doc.getCharacterElement(i)
            val attr = element.attributes
            val start = element.startOffset
            val end = element.endOffset
            val text = doc.getText(start, (end - start).coerceAtMost(len - start))

            val targetStyles = handlers.mapNotNull { h -> h.activeTag(attr)?.let { h to it } }
            val targetTags = targetStyles.map { it.second }

            activeStyles.filter { (tag, _) -> tag !in targetTags }
                .reversed()
                .forEach { (tag, handler) ->
                    sb.append("[/${handler.closeTagFor(tag)}]")
                    activeStyles.remove(tag to handler)
                }

            targetStyles.filter { (_, tag) -> tag !in activeStyles.map { it.first } }
                .forEach { (handler, tag) ->
                    sb.append("[$tag]")
                    activeStyles.add(tag to handler)
                }

            sb.append(text)
            i = end
        }

        // Close remaining
        activeStyles.reversed().forEach { (tag, handler) ->
            sb.append("[/${handler.closeTagFor(tag)}]")
        }

        return sb.toString()
    }
}