package com.lamali.cardloc.core

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.lamali.cardloc.data.CardLocConfig
import com.lamali.cardloc.data.CardLocPreset
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.lamali.cardloc.CardLocConstants
import com.lamali.cardloc.data.PinnedColor
import java.io.File
import java.util.regex.Pattern

@Service(Service.Level.PROJECT)
class CardLocRegistry(private val project: Project) {

    private var config: CardLocConfig? = null
    private val gson = Gson()
    private val CAMEL_TO_SNAKE = Pattern.compile("(?<!^)([A-Z])")

    val pinnedColors: List<PinnedColor>
        get() = config?.pinnedColors ?: defaultPinnedColors

    fun pinColor(color: PinnedColor) {
        val current = config ?: return
        if (pinnedColors.any { it.tag == color.tag && it.hex == color.hex }) return
        config = current.copy(pinnedColors = pinnedColors + color)
        persist()
    }

    fun unpinColor(color: PinnedColor) {
        val current = config ?: return
        config = current.copy(pinnedColors = current.pinnedColors.filter { pinned ->
            when {
                color.tag != null -> pinned.tag != color.tag
                color.hex != null -> pinned.hex != color.hex
                else -> true
            }
        })
        persist()
    }

    fun isInitialized(): Boolean = config != null

    val localizationBase: String?
        get() = config?.localizationBase

    val keyPrefix: String?
        get() {
            val id = config?.projectId ?: return null
            return CAMEL_TO_SNAKE.matcher(id).replaceAll("_$1").uppercase() + "-"
        }

    fun initialize() {
        val path = project.basePath ?: return
        val projectFile = File(path, CardLocConstants.CONFIG_FILENAME)
        if (projectFile.exists()) {
            loadFromJson(projectFile.readText())
        } else {
            config = null
            println("CardLoc: No config file found at $path")
        }
    }

    fun reload() = initialize()

    private fun loadFromJson(json: String) {
        val clean = json.trimStart('\uFEFF')
        runCatching {
            val type = object : TypeToken<CardLocConfig>() {}.type
            var newConfig: CardLocConfig = gson.fromJson(clean, type)

            // Gson bypasses Kotlin constructors — pinnedColors can be null on old configs
            if (newConfig.pinnedColors.isEmpty()) {
                newConfig = newConfig.copy(pinnedColors = defaultPinnedColors)
            }

            if (newConfig.projectId.isNotBlank()) {
                config = newConfig
            }
        }.onFailure {
            config = null
            println("CardLoc: Failed to parse config: ${it.message}")
        }
    }

    private fun persist() {
        val basePath = project.basePath ?: return
        val file = File(basePath, CardLocConstants.CONFIG_FILENAME)
        val snapshot = config ?: return
        Thread {
            runCatching {
                val json = GsonBuilder().setPrettyPrinting().create()
                    .toJson(snapshot, CardLocConfig::class.java)
                file.writeText(json)
                println("CardLoc: Persisted config with ${snapshot.pinnedColors.size} pinned colors")
            }.onFailure {
                println("CardLoc: Failed to persist: ${it.message}")
            }
        }.start()
    }

    fun detect(source: String): CardLocPreset? {
        val currentConfig = config ?: return null
        val classLine = Regex("""public\s+(?:sealed\s+|abstract\s+)?class\s+\w+\s*(?:\([^)]*\))?\s*:\s*(.+)""")
            .find(source)?.groupValues?.get(1) ?: return null
        return currentConfig.presets.firstOrNull { preset ->
            preset.markers.any { classLine.contains(it) }
        }
    }

    fun detectWithClass(source: String): Pair<CardLocPreset, String>? {
        val preset = detect(source) ?: return null
        val className = Regex("""public\s+(?:sealed\s+|abstract\s+)?class\s+(\w+)""")
            .find(source)?.groupValues?.get(1) ?: return null
        return preset to className
    }

    fun all(): List<CardLocPreset> = config?.presets.orEmpty()

    companion object {
        val defaultPinnedColors = listOf(
            PinnedColor(tag = "gold"),
            PinnedColor(tag = "blue")
        )
    }
}