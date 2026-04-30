package com.lamali.cardloc.editor.ui

import com.lamali.cardloc.data.CardLocContext
import java.awt.Color

class TagDefs private constructor(
    val color: List<TagDef>,
    val anim: List<TagDef>,
    val format: List<TagDef>,
    val map: Map<String, TagDef>
) {
    data class TagDef(
        val tag: String,
        val label: String,
        val color: Color? = null,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val animated: Boolean = false,
        val isFormat: Boolean = false
    )

    companion object {
        // Base tags that are always available
        private val baseColor = listOf(
            TagDef("gold", "Gold", Color(0xEFC851)),
            TagDef("blue", "Blue", Color(0x87CEEB)),
            TagDef("aqua", "Aqua", Color(0x2AEBBE)),
            TagDef("green", "Green", Color(0x7FFF00)),
            TagDef("orange", "Orange", Color(0xFFA518)),
            TagDef("pink", "Pink", Color(0xFF78A0)),
            TagDef("purple", "Purple", Color(0xEE82EE)),
            TagDef("red", "Red", Color(0xFF5555)),
        )

        private val baseAnim = listOf(
            TagDef("jitter", "~Jitter", Color(0xA0D0FF), animated = true),
            TagDef("sine", "~Sine", Color(0xA0D0FF), animated = true),
            TagDef("fade_in", "~FadeIn", Color(0xA0D0FF), animated = true),
            TagDef("fly_in", "~FlyIn", Color(0xA0D0FF), animated = true),
            TagDef("thinky_dots", "~Dots", Color(0xA0D0FF), animated = true),
        )

        private val baseFormat = listOf(
            TagDef("b", "B", bold = true, isFormat = true),
            TagDef("i", "I", italic = true, isFormat = true),
            TagDef("u", "U", isFormat = true),
        )

        // Factory method - pull tags from context
        fun create(context: CardLocContext?): TagDefs {
            val customTags = context?.config?.customTags?.map { custom ->
                TagDef(
                    tag = custom.tag,
                    label = custom.label,
                    color = runCatching { Color.decode(custom.color) }.getOrNull(),
                    bold = custom.bold,
                    italic = custom.italic
                )
            } ?: emptyList()

            val allColor = baseColor + customTags
            val allTags = allColor + baseAnim + baseFormat

            return TagDefs(
                color = allColor,
                anim = baseAnim,
                format = baseFormat,
                map = allTags.associateBy { it.tag }
            )
        }
    }
}