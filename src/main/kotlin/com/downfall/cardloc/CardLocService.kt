package com.downfall.cardloc

import com.downfall.cardloc.data.CardLocPreset
import com.downfall.cardloc.data.FieldDef
import com.google.gson.*
import java.io.File
import java.util.regex.Pattern

object CardLocService {

    private val CAMEL_TO_SNAKE = Pattern.compile("(?<!^)([A-Z])")

    fun toKey(className: String): String =
        CardLocRegistry.keyPrefix + CAMEL_TO_SNAKE.matcher(className).replaceAll("_$1").uppercase()

    private fun getBasePath(project: Any): String? = try {
        project.javaClass.getMethod("getBasePath").invoke(project) as? String
    } catch (e: Exception) { null }

    private fun resolveFieldFile(basePath: String, fieldDef: FieldDef): File =
        File(basePath, "${CardLocRegistry.localizationBase}/${fieldDef.file}")

    fun load(project: Any, keyPrefix: String, preset: CardLocPreset): LinkedHashMap<String, String> {
        val basePath = getBasePath(project) ?: return LinkedHashMap()
        val result = LinkedHashMap<String, String>()

        preset.fields
            .groupBy { it.file }
            .forEach { (_, fieldsInFile) ->
                val file = resolveFieldFile(basePath, fieldsInFile.first())
                if (!file.exists()) return@forEach

                val obj = runCatching {
                    JsonParser.parseString(file.readText()).asJsonObject
                }.getOrNull() ?: return@forEach

                fieldsInFile.forEach { fieldDef ->
                    val fullKey = "$keyPrefix.${fieldDef.name}"
                    obj[fullKey]?.let { result[fullKey] = it.asString }
                }
            }

        return result
    }

    fun save(project: Any, keyPrefix: String, values: Map<String, String>, preset: CardLocPreset) {
        val basePath = getBasePath(project) ?: run {
            println("CardLoc Error: Could not resolve project base path.")
            return
        }

        val byFile = mutableMapOf<String, MutableMap<String, String>>()
        values.forEach { (fullKey, value) ->
            val suffix = fullKey.removePrefix("$keyPrefix.")
            val fieldDef = preset.fields.find { it.name == suffix } ?: run {
                println("CardLoc Warning: No field def found for '$suffix', skipping.")
                return@forEach
            }
            byFile.getOrPut(fieldDef.file) { mutableMapOf() }[fullKey] = value
        }

        byFile.forEach { (_, entries) ->
            val suffix = entries.keys.first().removePrefix("$keyPrefix.")
            val fieldDef = preset.fields.find { it.name == suffix } ?: return@forEach
            val file = resolveFieldFile(basePath, fieldDef)
            saveToFile(file, entries)
        }
    }

    fun saveToFile(file: File, values: Map<String, String>) {
        file.parentFile?.mkdirs()

        val obj: JsonObject = runCatching {
            if (file.exists() && file.length() > 0)
                JsonParser.parseString(file.readText()).asJsonObject
            else JsonObject()
        }.getOrElse {
            println("CardLoc: Bad JSON in ${file.name}, starting fresh.")
            JsonObject()
        }

        values.forEach { (k, v) -> obj.addProperty(k, v) }

        runCatching {
            val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
            file.writeText(gson.toJson(obj))
        }.onFailure {

            it.printStackTrace()
        }
    }
}