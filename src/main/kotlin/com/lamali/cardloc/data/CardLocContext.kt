package com.lamali.cardloc.data

import java.io.File
import java.util.regex.Pattern

/**
 * This class holds everything needed for a specific sub-project.
 */
data class CardLocContext(
    val config: CardLocConfig,
    val baseDir: File,
    /** Project-wide default presets that presets with a `base` build on. */
    val defaults: List<CardLocPreset> = emptyList(),
    /** Project-default key prefix, used when this folder sets none. */
    val defaultKeyPrefix: String = "",
    /** Project-default localization base, already made relative to [baseDir]. */
    val defaultLocalizationBase: String = ""
) {
    /** The effective presets (defaults merged in). */
    val presets: List<CardLocPreset> by lazy { config.presets.map { it.resolve(defaults) } }

    /** The folder's own prefix (explicit, or derived from the legacy mod id); null if it sets none. */
    val ownKeyPrefix: String? by lazy {
        normalizePrefix(config.keyPrefix)?.let { return@lazy it }
        // This turns "MyCoolCard" into "MY_COOL_CARD-"
        val id = config.projectId
        if (id.isNullOrBlank()) return@lazy null
        Pattern.compile("(?<!^)([A-Z])")
            .matcher(id)
            .replaceAll("_$1")
            .uppercase() + "-"
    }

    /** The prefix in effect: the folder's own, else the project default. */
    val keyPrefix: String? by lazy { ownKeyPrefix ?: normalizePrefix(defaultKeyPrefix) }

    /** The localization base in effect (relative to [baseDir]): the folder's own, else the project default. */
    val localizationBase: String
        get() = config.localizationBase?.takeIf { it.isNotBlank() } ?: defaultLocalizationBase

    private fun normalizePrefix(p: String?): String? =
        p?.trim()?.takeIf { it.isNotEmpty() }?.let { if (it.endsWith("-")) it else "$it-" }

    /** A recognised card class: its preset, name and the source span of the declaration plus body. */
    data class ClassMatch(val preset: CardLocPreset, val className: String, val range: IntRange)

    /** Every concrete class in [source] whose base list contains a marker of one of the presets. */
    fun detectAll(source: String): List<ClassMatch> {
        val result = ArrayList<ClassMatch>()
        for (m in CLASS_REGEX.findAll(source)) {
            val inheritanceGroup = m.groups[2] ?: continue
            val preset = presetFor(m.groupValues[2].substringBefore('{')) ?: continue

            val open = source.indexOf('{', inheritanceGroup.range.first)
            val end = if (open < 0) source.length - 1 else closingBrace(source, open)
            result.add(ClassMatch(preset, m.groupValues[1].trim(), m.range.first..end))
        }
        return result
    }

    /** The first preset with a marker contained in a class's base list. */
    private fun presetFor(inheritance: String): CardLocPreset? {
        for (preset in presets) {
            for (marker in preset.markers) {
                if (inheritance.contains(marker)) return preset
            }
        }
        return null
    }

    /** The innermost recognised class whose declaration or body contains [offset]. */
    fun detectAt(source: String, offset: Int): ClassMatch? =
        detectAll(source).filter { offset in it.range }.maxByOrNull { it.range.first }

    /** First recognised class in the file. */
    fun detectWithClass(source: String): Pair<CardLocPreset, String>? =
        detectAll(source).firstOrNull()?.let { it.preset to it.className }

    /** Index of the brace closing the one at [open], skipping comments, strings and chars. */
    private fun closingBrace(src: String, open: Int): Int {
        var depth = 0
        var i = open
        while (i < src.length) {
            val c = src[i]
            when {
                src.startsWith("//", i) -> {
                    val nl = src.indexOf('\n', i)
                    i = if (nl < 0) src.length else nl
                }
                src.startsWith("/*", i) -> {
                    val end = src.indexOf("*/", i + 2)
                    i = if (end < 0) src.length else end + 1
                }
                // verbatim strings: @"..." / $@"..." / @$"..." ("" is an escaped quote)
                c == '@' || c == '$' -> {
                    val q = verbatimQuote(src, i)
                    if (q >= 0) {
                        var j = q + 1
                        while (j < src.length) {
                            if (src[j] == '"') {
                                if (src.startsWith("\"\"", j)) j++ else break
                            }
                            j++
                        }
                        i = j
                    }
                }
                c == '"' || c == '\'' -> {
                    var j = i + 1
                    while (j < src.length && src[j] != c && src[j] != '\n') {
                        if (src[j] == '\\') j++
                        j++
                    }
                    i = j
                }
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return src.length - 1
    }

    /** If a verbatim string prefix (@" $@" @$") starts at [i], the index of its opening quote, else -1. */
    private fun verbatimQuote(src: String, i: Int): Int = when {
        src.startsWith("@\"", i) -> i + 1
        src.startsWith("$@\"", i) || src.startsWith("@$\"", i) -> i + 2
        else -> -1
    }

    private companion object {
        val CLASS_REGEX = Regex("""public\s+(?:(?:sealed|partial)\s+)*class\s+(\w+)\s*(?:\([^)]*\))?\s*:\s*(.+)""")
    }
}
