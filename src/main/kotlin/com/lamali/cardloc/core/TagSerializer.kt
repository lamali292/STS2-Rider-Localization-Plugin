package com.lamali.cardloc.core

import javax.swing.text.StyledDocument

class TagSerializer(private val handlers: List<TagHandler>) {

    fun serialize(doc: StyledDocument): String {
        val sb = StringBuilder()
        val len = doc.length

        val stack = mutableListOf<Pair<String, TagHandler>>()

        var i = 0
        while (i < len) {
            val element = doc.getCharacterElement(i)
            val attr = element.attributes
            val start = element.startOffset
            val end = element.endOffset
            val text = doc.getText(start, (end - start).coerceAtMost(len - start))

            val target = handlers.mapNotNull { h -> h.activeTag(attr)?.let { tag -> tag to h } }
            val targetTags = target.map { it.first }.toSet()

            val diverge = stack.indexOfFirst { (tag, _) -> tag !in targetTags }
                .let { if (it == -1) stack.size else it }

            for (level in stack.size - 1 downTo diverge) {
                val (tag, handler) = stack[level]
                sb.append("[/${handler.closeTagFor(tag)}]")
            }
            while (stack.size > diverge) stack.removeAt(stack.size - 1)

            val onStack = stack.map { it.first }.toSet()
            target.filter { it.first !in onStack }
                .sortedByDescending { (tag, handler) -> styleEnd(doc, start, handler, tag, len) }
                .forEach { (tag, handler) ->
                    sb.append("[$tag]")
                    stack.add(tag to handler)
                }

            sb.append(text)
            i = end
        }

        // Close whatever is still open, top of the stack first.
        for (level in stack.size - 1 downTo 0) {
            val (tag, handler) = stack[level]
            sb.append("[/${handler.closeTagFor(tag)}]")
        }

        return sb.toString()
    }

    /** How far the given style stays continuously active starting at [from]. */
    private fun styleEnd(doc: StyledDocument, from: Int, handler: TagHandler, tag: String, len: Int): Int {
        var pos = from
        while (pos < len) {
            val el = doc.getCharacterElement(pos)
            if (handler.activeTag(el.attributes) != tag) break
            pos = el.endOffset
        }
        return pos
    }
}