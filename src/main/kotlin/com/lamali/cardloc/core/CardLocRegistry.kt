package com.lamali.cardloc.core

import com.lamali.cardloc.data.CardLocConfig
import com.lamali.cardloc.data.CardLocPreset
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.regex.Pattern

object CardLocRegistry {

    private var config: CardLocConfig? = null
    private val gson = Gson()

    val localizationBase: String
        get() = config?.localizationBase
            ?: error("CardLocRegistry not initialized — call initialize() first")

    val keyPrefix: String
        get() {
            val id = config?.projectId
                ?: error("CardLocRegistry not initialized — call initialize() first")
            return CAMEL_TO_SNAKE.matcher(id).replaceAll("_$1").uppercase() + "-"
        }

    private val CAMEL_TO_SNAKE = Pattern.compile("(?<!^)([A-Z])")

    private val DEFAULTS_JSON = """
    {
      "localizationBase": "Downfall/localization/eng",
      "presets": [
        {
          "id": "card",
          "markers": ["CustomCardModel"],
          "fields": [
            { "name": "title",          "file": "cards.json",          "optional": false },
            { "name": "description",    "file": "cards.json",          "optional": false }
          ]
        },
        {
          "id": "power",
          "markers": ["CustomPowerModel"],
          "fields": [
            { "name": "title",            "file": "powers.json", "optional": false },
            { "name": "description",      "file": "powers.json", "optional": false },
            { "name": "smartDescription", "file": "powers.json", "optional": true  }
          ]
        }
      ]
    }
    """.trimIndent()

    fun initialize(projectBasePath: String? = null) {
        val path = projectBasePath ?: return
        val projectFile = File(path, "cardloc-presets.json")

        if (projectFile.exists()) {
            val json = projectFile.readText()
            loadFromJson(json)
        } else {
            // If the user deletes their custom config, revert to defaults
            loadFromJson(DEFAULTS_JSON)
        }
    }

    private fun loadFromJson(json: String) {
        val clean = json.trimStart('\uFEFF')
        runCatching {
            val type = object : TypeToken<CardLocConfig>() {}.type
            config = gson.fromJson(clean, type)
            println("CardLoc: Initialized — base='${config!!.localizationBase}', " +
                    "presets=${config!!.presets.map { it.id }}")
        }.onFailure {
            println("CardLoc: Failed to parse config, falling back to defaults: ${it.message}")
            if (json != DEFAULTS_JSON) loadFromJson(DEFAULTS_JSON)
        }
    }

    fun detect(source: String): CardLocPreset? {
        val classLine = Regex("""public\s+(?:sealed\s+|abstract\s+)?class\s+\w+\s*(?:\([^)]*\))?\s*:\s*(.+)""")
            .find(source)?.groupValues?.get(1) ?: return null

        return config?.presets?.firstOrNull { preset ->
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

    fun isInitialized(): Boolean = config != null
}