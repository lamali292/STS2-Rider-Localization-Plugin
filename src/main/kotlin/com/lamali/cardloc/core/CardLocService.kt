package com.lamali.cardloc.core

import com.intellij.openapi.project.Project
import com.lamali.cardloc.data.CardLocPreset
import com.lamali.cardloc.data.FieldDef
import com.google.gson.*
import java.io.File
import java.util.regex.Pattern

object CardLocService {

    private val CAMEL_TO_SNAKE = Pattern.compile("(?<!^)([A-Z])")
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    /**
     * Now requires the Project instance to fetch the project-specific Registry.
     */
    fun toKey(project: Project, className: String): String? {
        val registry = project.getService(CardLocRegistry::class.java)
        val prefix = registry.keyPrefix ?: return null
        val snake = CAMEL_TO_SNAKE.matcher(className).replaceAll("_$1").uppercase()
        return "$prefix$snake"
    }

    /**
     * Resolved directly from the project instance safely.
     */
    private fun resolveFieldFile(project: Project, fieldDef: FieldDef): File? {
        val basePath = project.basePath ?: return null
        val registry = project.getService(CardLocRegistry::class.java)
        val locBase = registry.localizationBase ?: return null
        return File(basePath, "$locBase/${fieldDef.file}")
    }

    fun load(project: Project, keyPrefix: String, preset: CardLocPreset): LinkedHashMap<String, String> {
        val result = LinkedHashMap<String, String>()

        preset.fields
            .groupBy { it.file }
            .forEach { (_, fieldsInFile) ->
                val file = resolveFieldFile(project, fieldsInFile.first()) ?: return@forEach
                if (!file.exists()) return@forEach

                val jsonText = file.readText().trimStart('\uFEFF')
                val obj = runCatching {
                    JsonParser.parseString(jsonText).asJsonObject
                }.getOrNull() ?: return@forEach

                fieldsInFile.forEach { fieldDef ->
                    val fullKey = "$keyPrefix.${fieldDef.name}"
                    obj[fullKey]?.let { result[fullKey] = it.asString }
                }
            }

        return result
    }

    fun save(project: Project, keyPrefix: String, values: Map<String, String>, preset: CardLocPreset) {
        val byFile = mutableMapOf<String, MutableMap<String, String>>()

        values.forEach { (fullKey, value) ->
            val fieldName = fullKey.substringAfterLast(".")
            val fieldDef = preset.fields.find { it.name == fieldName } ?: return@forEach
            byFile.getOrPut(fieldDef.file) { mutableMapOf() }[fullKey] = value
        }

        byFile.forEach { (fileName, entries) ->
            val fieldDef = preset.fields.first { it.file == fileName }
            val file = resolveFieldFile(project, fieldDef) ?: return@forEach
            saveToFile(file, entries)
        }
    }

    private fun saveToFile(file: File, values: Map<String, String>) {
        file.parentFile?.mkdirs()

        val obj: JsonObject = runCatching {
            if (file.exists() && file.length() > 0) {
                JsonParser.parseString(file.readText().trimStart('\uFEFF')).asJsonObject
            } else JsonObject()
        }.getOrElse { JsonObject() }

        // Merge new values into existing JSON object
        values.forEach { (k, v) -> obj.addProperty(k, v) }

        runCatching {
            file.writeText(gson.toJson(obj))
        }.onFailure { it.printStackTrace() }
    }
}