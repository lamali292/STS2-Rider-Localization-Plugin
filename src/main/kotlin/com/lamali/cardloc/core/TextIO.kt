package com.lamali.cardloc.core

import java.io.File

/** Write text files without changing their existing line-ending / BOM style. */
object TextIO {

    data class Style(val crlf: Boolean, val bom: Boolean)

    /** Capture a file's style. [defaultCrlf] applies only when the file is new/empty. */
    fun styleOf(file: File, defaultCrlf: Boolean = false): Style {
        if (!file.exists() || file.length() == 0L) return Style(defaultCrlf, false)
        val raw = file.readText()
        return Style(crlf = raw.contains("\r\n"), bom = raw.startsWith('\uFEFF'))
    }

    /** Re-apply [style] to freshly generated [text] (assumed LF from Gson). */
    fun apply(text: String, style: Style): String {
        var out = text.replace("\r\n", "\n")            // normalise first, avoid \r\r\n
        if (style.crlf) out = out.replace("\n", "\r\n")  // LF -> CRLF
        if (style.bom && !out.startsWith('\uFEFF')) out = "\uFEFF$out"
        return out
    }
}