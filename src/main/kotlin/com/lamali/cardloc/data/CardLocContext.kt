package com.lamali.cardloc.data

import java.io.File
import java.util.regex.Pattern

/**
 * This class holds everything needed for a specific sub-project.
 */
data class CardLocContext(
    val config: CardLocConfig,
    val baseDir: File,
    val configFile: File
) {
    // This turns "MyCoolCard" into "MY_COOL_CARD-"
    val keyPrefix: String? by lazy {
        val id = config.projectId
        if (id.isBlank()) return@lazy null
        Pattern.compile("(?<!^)([A-Z])")
            .matcher(id)
            .replaceAll("_$1")
            .uppercase() + "-"
    }

    /**
     * This is the function you were looking for!
     * It analyzes the source code to find the class name and match it to a Preset.
     */
    fun detectWithClass(source: String): Pair<CardLocPreset, String>? {
        val classMatch = Regex("""public\s+(?:sealed\s+|abstract\s+)?class\s+(\w+)\s*(?:\([^)]*\))?\s*:\s*(.+)""")
            .find(source) ?: return null

        val className = classMatch.groupValues[1].trim()
        val inheritance = classMatch.groupValues[2].trim()

        val preset = config.presets.firstOrNull { preset ->
            preset.markers.any { marker -> inheritance.contains(marker) }
        } ?: return null

        return preset to className
    }
}