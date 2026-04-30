package com.lamali.cardloc.editor.ui

import com.lamali.cardloc.core.CardLocRegistry
import com.lamali.cardloc.data.CardLocContext
import com.lamali.cardloc.data.PinnedColor
import java.awt.Color

object ToolbarActionProvider {

    /**
     * Now accepts an optional CardLocContext to resolve project-specific colors.
     */
    fun getActions(registry: CardLocRegistry, context: CardLocContext?, isVertical: Boolean = false): List<ToolbarAction> {
        // Get dynamic tags from context
        val tagDefs = TagDefs.create(context)

        // Get pinned colors from the specific context, or fall back to defaults if no context/config
        val pinnedList = context?.config?.pinnedColors ?: CardLocRegistry.defaultPinnedColors

        val pinnedActions = pinnedList.mapNotNull { it.toToolbarAction(tagDefs) }
        val pinnedTags = pinnedList.mapNotNull { it.tag }.toSet()

        return buildList {
            // 1. Basic Formatting
            add(ToolbarAction.ClearFormatting())

            // 2. Add Pinned Colors
            addAll(pinnedActions)

            // 3. More Colors Button (passing registry and filtered tags)
            add(ToolbarAction.MoreColors(
                extra      = tagDefs.color.filter { it.tag !in pinnedTags }, // Use dynamic tagDefs
                registry   = registry,
                isVertical = isVertical
            ))

            // 4. Style Tags (Bold, Italic, etc.)
            tagDefs.format.forEach { // Use dynamic tagDefs
                add(ToolbarAction.ToggleFormat(
                    tag       = it.tag,
                    label     = it.label,
                    bold      = it.bold,
                    italic    = it.italic,
                    underline = it.tag == "u",
                    tooltip   = "Toggle ${it.label}"
                ))
            }

            // 5. Animation Tags
            tagDefs.anim.forEach { // Use dynamic tagDefs
                add(ToolbarAction.WrapTag(it.tag, "≈", "Apply ${it.label}"))
            }
        }
    }

    /**
     * Extension to convert data model to UI Action
     */
    private fun PinnedColor.toToolbarAction(tagDefs: TagDefs): ToolbarAction? { // Pass tagDefs
        return when {
            tag != null -> {
                val def = tagDefs.map[tag] ?: return null // Use dynamic tagDefs
                ToolbarAction.NamedColor(def.color ?: Color.WHITE, def.tag, def.label)
            }
            hex != null -> {
                val color = runCatching { Color.decode(hex) }.getOrNull() ?: return null
                ToolbarAction.CustomNamedColor(color, hex, label ?: hex)
            }
            else -> null
        }
    }
}