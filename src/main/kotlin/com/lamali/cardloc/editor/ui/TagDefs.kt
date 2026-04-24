package com.lamali.cardloc.editor.ui

import java.awt.Color

object TagDefs {

    data class TagDef(
        val tag: String,
        val label: String,
        val color: Color? = null,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val animated: Boolean = false,
        val isFormat: Boolean = false
    )

    val color = listOf(
        TagDef("gold",   "Gold",   Color(0xEFC851)),
        TagDef("blue",   "Blue",   Color(0x87CEEB)),
        TagDef("aqua",   "Aqua",   Color(0x2AEBBE)),
        TagDef("green",  "Green",  Color(0x7FFF00)),
        TagDef("orange", "Orange", Color(0xFFA518)),
        TagDef("pink",   "Pink",   Color(0xFF78A0)),
        TagDef("purple", "Purple", Color(0xEE82EE)),
        TagDef("red",    "Red",    Color(0xFF5555)),
    )

    val anim = listOf(
        TagDef("jitter",      "~Jitter",   Color(0xA0D0FF), animated = true),
        TagDef("sine",        "~Sine",     Color(0xA0D0FF), animated = true),
        TagDef("fade_in",     "~FadeIn",   Color(0xA0D0FF), animated = true),
        TagDef("fly_in",      "~FlyIn",    Color(0xA0D0FF), animated = true),
        TagDef("thinky_dots", "~Dots",     Color(0xA0D0FF), animated = true),
    )

    val format = listOf(
        TagDef("b", "B", bold = true,   isFormat = true),
        TagDef("i", "I", italic = true, isFormat = true),
        TagDef("u", "U",                isFormat = true),
    )

    val map: Map<String, TagDef> =
        (color + anim + format).associateBy { it.tag }
}