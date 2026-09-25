package com.lamali.cardloc.core

import com.google.gson.*
import com.lamali.cardloc.data.CardLocContext
import com.lamali.cardloc.data.CardLocPreset
import java.io.File
import java.util.regex.Pattern

object CardLocService {
    private val CAMEL_TO_SNAKE = Pattern.compile("(?<!^)([A-Z])")
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    fun toKey(context: CardLocContext, className: String): String? {
        val prefix = context.keyPrefix ?: return null
        val snake = CAMEL_TO_SNAKE.matcher(className).replaceAll("_$1").uppercase()
        return "$prefix$snake"
    }

    fun load(context: CardLocContext, keyPrefix: String, preset: CardLocPreset): LinkedHashMap<String, String> {
        val result = LinkedHashMap<String, String>()
        val locBase = context.config.localizationBase

        preset.fields.groupBy { it.file }.forEach { (fileName, fieldsInFile) ->
            val file = File(context.baseDir, "$locBase/$fileName")
            if (!file.exists()) return@forEach

            val jsonText = file.readText().trimStart('\uFEFF')
            val obj = runCatching { JsonParser.parseString(jsonText).asJsonObject }.getOrNull() ?: return@forEach

            fieldsInFile.forEach { fieldDef ->
                val fullKey = "$keyPrefix.${fieldDef.name}"
                obj[fullKey]?.let { result[fullKey] = it.asString }
            }
        }
        return result
    }

    fun save(context: CardLocContext, values: Map<String, String>, preset: CardLocPreset) {
        val locBase = context.config.localizationBase
        val byFile = mutableMapOf<String, MutableMap<String, String>>()

        values.forEach { (fullKey, value) ->
            val fieldName = fullKey.substringAfterLast(".")
            val fieldDef = preset.fields.find { it.name == fieldName } ?: return@forEach
            byFile.getOrPut(fieldDef.file) { mutableMapOf() }[fullKey] = value
        }

        byFile.forEach { (fileName, entries) ->
            val file = File(context.baseDir, "$locBase/$fileName")
            saveToFile(file, entries)
        }
    }

    data class RenameResult(val movedFields: Int, val languages: List<String>)

    /**
     * Moves every "<oldKey>.<field>" entry to "<newKey>.<field>", keeping its value,
     * across every language directory under the localization root (not just eng).
     * Never overwrites an entry that already exists under newKey.
     */
    fun renameKey(context: CardLocContext, oldKey: String, newKey: String, preset: CardLocPreset): RenameResult {
        if (oldKey == newKey) return RenameResult(0, emptyList())

        val engDir = File(context.baseDir, context.config.localizationBase)
        val langRoot = engDir.parentFile ?: return RenameResult(0, emptyList())
        val langDirs = langRoot.listFiles { f -> f.isDirectory } ?: return RenameResult(0, emptyList())

        var movedFields = 0
        val touchedLangs = mutableListOf<String>()

        preset.fields.groupBy { it.file }.forEach { (fileName, fieldsInFile) ->
            langDirs.forEach { langDir ->
                val file = File(langDir, fileName)
                if (!file.exists()) return@forEach

                val jsonText = file.readText().trimStart('﻿')
                val obj = runCatching { JsonParser.parseString(jsonText).asJsonObject }.getOrNull() ?: return@forEach

                var changed = false
                fieldsInFile.forEach { fieldDef ->
                    val oldFullKey = "$oldKey.${fieldDef.name}"
                    val newFullKey = "$newKey.${fieldDef.name}"
                    if (obj.has(newFullKey)) return@forEach
                    val value = obj.remove(oldFullKey) ?: return@forEach
                    obj.add(newFullKey, value)
                    changed = true
                    movedFields++
                }

                if (changed) {
                    val style = TextIO.styleOf(file)
                    runCatching { file.writeText(TextIO.apply(gson.toJson(obj), style)) }
                        .onFailure { it.printStackTrace() }
                    if (langDir.name !in touchedLangs) touchedLangs.add(langDir.name)
                }
            }
        }

        return RenameResult(movedFields, touchedLangs)
    }

    private fun saveToFile(file: File, values: Map<String, String>) {
        file.parentFile?.mkdirs()

        val style = TextIO.styleOf(file)

        val obj: JsonObject = runCatching {
            if (file.exists() && file.length() > 0) {
                JsonParser.parseString(file.readText().trimStart('\uFEFF')).asJsonObject
            } else JsonObject()
        }.getOrElse { JsonObject() }

        values.forEach { (k, v) -> obj.addProperty(k, v) }

        runCatching {
            file.writeText(TextIO.apply(gson.toJson(obj), style))
        }.onFailure { it.printStackTrace() }
    }
}