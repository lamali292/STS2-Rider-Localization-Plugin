package com.lamali.cardloc.core

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.lamali.cardloc.data.CardLocConfig
import com.lamali.cardloc.data.CardLocPreset
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lamali.cardloc.CardLocConstants
import java.io.File
import java.util.regex.Pattern

@Service(Service.Level.PROJECT)
class CardLocRegistry(private val project: Project) {

    private var config: CardLocConfig? = null
    private val gson = Gson()
    private val CAMEL_TO_SNAKE = Pattern.compile("(?<!^)([A-Z])")

    /**
     * Returns true only if a valid config has been loaded.
     */
    fun isInitialized(): Boolean = config != null

    /**
     * Returns the base path for localization files.
     */
    val localizationBase: String?
        get() = config?.localizationBase

    /**
     * Generates a key prefix (e.g., "MY_PROJECT-") from the projectId.
     */
    val keyPrefix: String?
        get() {
            val id = config?.projectId ?: return null
            return CAMEL_TO_SNAKE.matcher(id).replaceAll("_$1").uppercase() + "-"
        }
    /**
     * Entry point.
     */
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

    /**
     * Helper to reload from the current project path.
     */
    fun reload() = initialize()

    private fun loadFromJson(json: String) {
        val clean = json.trimStart('\uFEFF')
        runCatching {
            val type = object : TypeToken<CardLocConfig>() {}.type
            val newConfig: CardLocConfig = gson.fromJson(clean, type)

            if (newConfig.projectId.isNotBlank()) {
                config = newConfig
                println("CardLoc [${project.name}]: Successfully loaded config.")
            }
        }.onFailure {
            config = null
            println("CardLoc [${project.name}]: Failed to parse config: ${it.message}")
        }
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
}