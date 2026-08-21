package com.lamali.cardloc.core

import javax.swing.text.StyledDocument

class TagSerializer(private val handlers: List<TagHandler>) {

    fun serialize(doc: StyledDocument): String {
        val sb = StringBuilder()
        val len = doc.length

        // Open tags, outermost first. Strict stack: last opened is first closed.
        val stack = mutableListOf<Pair<String, TagHandler>>()

        var i = 0
        while (i < len) {
            val element = doc.getCharacterElement(i)
            val attr = element.attributes
            val start = element.startOffset
            val end = element.endOffset
            val runText = doc.getText(start, (end - start).coerceAtMost(len - start))

            // Styles wanted across this run (before newline splitting).
            val runTarget = handlers.mapNotNull { h -> h.activeTag(attr)?.let { tag -> tag to h } }

            // Split the run at newlines. A newline is always emitted with every tag
            // closed, so no tag (colour, bold, italic, underline, ...) ever spans a
            // line break: "[color]a[/color]\n[color]b[/color]", never "[color]a\nb[/color]".
            var segStart = 0
            while (segStart < runText.length) {
                val newlineSeg = runText[segStart] == '\n'
                var segEnd = segStart
                while (segEnd < runText.length && (runText[segEnd] == '\n') == newlineSeg) segEnd++

                val segText = runText.substring(segStart, segEnd)
                val target = if (newlineSeg) emptyList() else runTarget
                emit(sb, stack, doc, target, segText, start + segStart, len)

                segStart = segEnd
            }

            i = end
        }

        for (level in stack.size - 1 downTo 0) {
            val (tag, handler) = stack[level]
            sb.append("[/${handler.closeTagFor(tag)}]")
        }

        return sb.toString()
    }

    /** Reconcile the open-tag stack to [target], then append [text]. */
    private fun emit(
        sb: StringBuilder,
        stack: MutableList<Pair<String, TagHandler>>,
        doc: StyledDocument,
        target: List<Pair<String, TagHandler>>,
        text: String,
        segStart: Int,
        len: Int,
    ) {
        val targetTags = target.map { it.first }.toSet()

        // Close from the top down to the first divergence to keep nesting well-formed;
        // anything still wanted is reopened below.
        val diverge = stack.indexOfFirst { (tag, _) -> tag !in targetTags }
            .let { if (it == -1) stack.size else it }
        for (level in stack.size - 1 downTo diverge) {
            val (tag, handler) = stack[level]
            sb.append("[/${handler.closeTagFor(tag)}]")
        }
        while (stack.size > diverge) stack.removeAt(stack.size - 1)

        // Open wanted tags not already open. The style that stays active longest from
        // here opens first (outermost), so e.g. [u] wraps the colours that break inside it.
        val onStack = stack.map { it.first }.toSet()
        target.filter { it.first !in onStack }
            .sortedByDescending { (tag, handler) -> styleEnd(doc, segStart, handler, tag, len) }
            .forEach { (tag, handler) ->
                sb.append("[$tag]")
                stack.add(tag to handler)
            }

        sb.append(text)
    }

    /** How far the given style stays continuously active from [from], stopping at a newline. */
    private fun styleEnd(doc: StyledDocument, from: Int, handler: TagHandler, tag: String, len: Int): Int {
        var pos = from
        while (pos < len) {
            val el = doc.getCharacterElement(pos)
            if (handler.activeTag(el.attributes) != tag) break
            val runEnd = el.endOffset.coerceAtMost(len)
            val nl = doc.getText(pos, runEnd - pos).indexOf('\n')
            if (nl >= 0) return pos + nl   // a newline breaks every span
            pos = runEnd
        }
        return pos
    }
}