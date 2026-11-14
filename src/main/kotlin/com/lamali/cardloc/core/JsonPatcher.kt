package com.lamali.cardloc.core

/**
 * Edits a flat JSON object *in place*, as text: only the touched string values / keys / entries
 * change. Everything else - indentation, key order, spacing, comments-free whitespace, line
 * endings, BOM, ` ` escapes vs raw non-breaking spaces, unrelated values - stays byte-for-byte.
 *
 * Only what localization files need is supported: a top-level object whose values are strings
 * (other value types are skipped over untouched).
 */
class JsonPatcher(private val text: String) {

    private class Entry(
        val key: String,
        val keyStart: Int, val keyEnd: Int,       // span of the quoted key literal
        val valueStart: Int, val valueEnd: Int,   // span of the value literal
        val value: String?,                       // decoded value if it is a string
        val commaAfter: Int                       // index of the comma following the entry, or -1
    )

    private class Edit(val start: Int, val end: Int, val replacement: String)

    class ParseException(message: String) : Exception(message)

    private val entries = ArrayList<Entry>()
    private var openBrace = -1
    private var closeBrace = -1
    private val edits = ArrayList<Edit>()
    private val appended = LinkedHashMap<String, String>()
    private val removed = HashSet<String>()

    init {
        parse()
    }

    // ---------------- reading ----------------

    fun get(key: String): String? = entries.firstOrNull { it.key == key }?.value
    fun has(key: String) = (entries.any { it.key == key && it.key !in removed }) || appended.containsKey(key)

    // ---------------- editing ----------------

    /** Sets a string value: replaces just that literal, or queues a new entry at the end. */
    fun set(key: String, value: String) {
        removed.remove(key)
        val e = entries.firstOrNull { it.key == key }
        if (e == null) {
            // Never create an entry just to hold an empty value: only filled-in text is written.
            if (value.isNotEmpty()) appended[key] = value
            return
        }
        edits.removeAll { it.start == e.valueStart }
        if (e.value == value) return // unchanged: keep the original literal (and its escaping) as is
        edits.add(Edit(e.valueStart, e.valueEnd, quote(value)))
    }

    /** Deletes an entry (its line and the comma that kept the JSON valid). No-op if the key is absent. */
    fun remove(key: String) {
        appended.remove(key)
        val e = entries.firstOrNull { it.key == key } ?: return
        removed.add(key)
        edits.removeAll { it.start >= e.keyStart && it.start < e.valueEnd }
    }

    /** Renames a key in place (position and value untouched). No-op if [old] is missing or [new] exists. */
    fun rename(old: String, new: String): Boolean {
        if (old == new || has(new) || old in removed) return false
        val e = entries.firstOrNull { it.key == old } ?: return false
        edits.removeAll { it.start == e.keyStart }
        edits.add(Edit(e.keyStart, e.keyEnd, quote(new)))
        return true
    }

    /** The patched text. */
    fun result(): String {
        val kept = entries.filter { it.key !in removed }
        if (kept.isEmpty() && appended.isNotEmpty()) {
            // Everything was removed and something new is added: rebuild the object's interior.
            val nl = if (text.contains("\r\n")) "\r\n" else "\n"
            val body = appended.entries.joinToString(",$nl") { "  ${quote(it.key)}: ${quote(it.value)}" }
            return text.substring(0, openBrace + 1) + nl + body + nl + text.substring(closeBrace)
        }

        val all = ArrayList(edits)
        for (e in entries) if (e.key in removed) all.add(removalEdit(e))
        // Entries removed from the end leave a dangling comma after the last kept one.
        val lastKept = kept.lastOrNull()
        if (lastKept != null && lastKept.commaAfter >= 0 && lastKept !== entries.last()) {
            all.add(Edit(lastKept.commaAfter, lastKept.commaAfter + 1, ""))
        }
        if (appended.isNotEmpty()) all.add(appendEdit(lastKept))
        if (all.isEmpty()) return text
        val sb = StringBuilder(text)
        for (edit in all.sortedByDescending { it.start }) sb.replace(edit.start, edit.end, edit.replacement)
        return sb.toString()
    }

    private fun removalEdit(e: Entry): Edit {
        val lineStart = text.lastIndexOf('\n', e.keyStart - 1) + 1
        val ownLine = text.substring(lineStart, e.keyStart).isBlank() && lineStart > openBrace
        val end = if (e.commaAfter >= 0) e.commaAfter + 1 else e.valueEnd

        var tail = end
        while (tail < text.length && (text[tail] == ' ' || text[tail] == '\t')) tail++
        val restOfLineBlank = tail >= text.length || text[tail] == '\r' || text[tail] == '\n'
        if (ownLine && restOfLineBlank) {
            // the entry has its own line: remove the whole line, line break included
            if (tail < text.length && text[tail] == '\r') tail++
            if (tail < text.length && text[tail] == '\n') tail++
            return Edit(lineStart, tail, "")
        }
        // entries sharing a line: remove the entry and the whitespace after it
        var t = end
        while (t < text.length && (text[t] == ' ' || text[t] == '\t' || text[t] == '\r' || text[t] == '\n')) t++
        return Edit(e.keyStart, t, "")
    }

    private fun appendEdit(anchor: Entry?): Edit {
        val nl = if (text.contains("\r\n")) "\r\n" else "\n"
        val last = entries.lastOrNull()
        if (last == null || anchor == null) {
            // empty object: "{}" / "{ }" -> multi-line with two-space indent
            val body = appended.entries.joinToString(",$nl") { "  ${quote(it.key)}: ${quote(it.value)}" }
            return Edit(openBrace + 1, closeBrace, "$nl$body$nl")
        }
        // Mirror the layout of the last entry: its indentation and the text between key and value.
        val lineStart = text.lastIndexOf('\n', last.keyStart - 1) + 1
        val before = text.substring(lineStart, last.keyStart)
        val multiLine = before.isBlank() && lineStart > openBrace
        val indent = if (multiLine) before else ""
        val sep = text.substring(last.keyEnd, last.valueStart)
        val body = appended.entries.joinToString(",") {
            (if (multiLine) nl + indent else " ") + quote(it.key) + sep + quote(it.value)
        }
        return Edit(anchor.valueEnd, anchor.valueEnd, ",$body")
    }

    // ---------------- parsing ----------------

    private fun parse() {
        var i = 0
        if (text.startsWith("﻿")) i = 1
        i = skipWs(i)
        if (i >= text.length || text[i] != '{') throw ParseException("not a JSON object")
        openBrace = i
        i = skipWs(i + 1)

        while (i < text.length && text[i] != '}') {
            if (text[i] != '"') throw ParseException("expected a key at $i")
            val keyStart = i
            val keyEnd = stringEnd(i)
            val key = unquote(text.substring(keyStart, keyEnd))
            i = skipWs(keyEnd)
            if (i >= text.length || text[i] != ':') throw ParseException("expected ':' at $i")
            val afterColon = i + 1
            i = skipWs(afterColon)
            val valueStart = i
            val valueEnd = valueEnd(i)
            val decoded = if (text[valueStart] == '"') unquote(text.substring(valueStart, valueEnd)) else null
            i = skipWs(valueEnd)
            val comma = if (i < text.length && text[i] == ',') i else -1
            entries.add(Entry(key, keyStart, keyEnd, valueStart, valueEnd, decoded, comma))
            if (comma >= 0) i = skipWs(i + 1)
            else if (i < text.length && text[i] != '}') throw ParseException("expected ',' or '}' at $i")
        }
        if (i >= text.length) throw ParseException("unterminated object")
        closeBrace = i
    }

    private fun skipWs(from: Int): Int {
        var i = from
        while (i < text.length && (text[i] == ' ' || text[i] == '\t' || text[i] == '\n' || text[i] == '\r')) i++
        return i
    }

    /** Index just past the string literal starting at [start]. */
    private fun stringEnd(start: Int): Int {
        var i = start + 1
        while (i < text.length) {
            when (text[i]) {
                '\\' -> i++
                '"' -> return i + 1
            }
            i++
        }
        throw ParseException("unterminated string at $start")
    }

    /** Index just past the JSON value starting at [start] (string, object/array, or scalar). */
    private fun valueEnd(start: Int): Int {
        if (start >= text.length) throw ParseException("missing value")
        return when (text[start]) {
            '"' -> stringEnd(start)
            '{', '[' -> {
                var depth = 0
                var i = start
                while (i < text.length) {
                    when (text[i]) {
                        '"' -> { i = stringEnd(i); continue }
                        '{', '[' -> depth++
                        '}', ']' -> { depth--; if (depth == 0) return i + 1 }
                    }
                    i++
                }
                throw ParseException("unterminated value at $start")
            }
            else -> {
                var i = start
                while (i < text.length && text[i] !in ",}] \t\r\n") i++
                i
            }
        }
    }

    // ---------------- string literals ----------------

    private fun unquote(literal: String): String {
        val sb = StringBuilder()
        var i = 1
        while (i < literal.length - 1) {
            val c = literal[i]
            if (c != '\\') { sb.append(c); i++; continue }
            i++
            when (val e = literal[i]) {
                'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                'u' -> { sb.append(literal.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }
                else -> sb.append(e) // \" \\ \/
            }
            i++
        }
        return sb.toString()
    }

    /** Quotes [s]; only what JSON requires is escaped, so non-breaking spaces and other text stay raw. */
    private fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' || c == ' ' || c == ' ' -> sb.append(String.format("\\u%04x", c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }
}
