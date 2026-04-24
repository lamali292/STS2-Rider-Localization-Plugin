package com.lamali.cardloc.editor.ui

import com.lamali.cardloc.core.CardLocRegistry
import com.lamali.cardloc.data.PinnedColor
import java.awt.Color

object ToolbarActionProvider {

    fun defaultActions(registry: CardLocRegistry, isVertical: Boolean = false): List<ToolbarAction> {
        val pinned = registry.pinnedColors.mapNotNull { it.toToolbarAction() }.toSet()
        val pinnedTags = registry.pinnedColors.mapNotNull { it.tag }.toSet()

        return buildList {
            add(ToolbarAction.ClearFormatting())
            addAll(pinned)
            add(ToolbarAction.MoreColors(
                extra      = TagDefs.color.filter { it.tag !in pinnedTags },
                registry   = registry,
                isVertical = isVertical
            ))
            TagDefs.format.forEach {
                add(ToolbarAction.ToggleFormat(
                    tag       = it.tag,
                    label     = it.label,
                    bold      = it.bold,
                    italic    = it.italic,
                    underline = it.tag == "u",
                    tooltip   = "Toggle ${it.label}"
                ))
            }
            TagDefs.anim.forEach {
                add(ToolbarAction.WrapTag(it.tag, "≈", "Apply ${it.label}"))
            }
        }
    }

    private fun PinnedColor.toToolbarAction(): ToolbarAction? {
        return when {
            tag != null -> {
                val def = TagDefs.map[tag] ?: return null
                ToolbarAction.NamedColor(def.color!!, def.tag, def.label)
            }
            hex != null -> {
                val color = runCatching { Color.decode(hex) }.getOrNull() ?: return null
                ToolbarAction.CustomNamedColor(color, hex, label ?: hex)
            }
            else -> null
        }
    }
}