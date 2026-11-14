package com.lamali.cardloc.core

import com.google.gson.*
import com.lamali.cardloc.data.CardLocContext
import com.lamali.cardloc.data.CardLocPreset
import java.io.File
import java.util.regex.Pattern

object CardLocService {
    private val CAMEL_TO_SNAKE = Pattern.compile("(?<!^)([A-Z])")

    fun toKey(context: CardLocContext, className: String): String? {
        val prefix = context.keyPrefix ?: return null
        val snake = CAMEL_TO_SNAKE.matcher(className).replaceAll("_$1").uppercase()
        return "$prefix$snake"
    }

    /**
     * The directory of one language. The configured localization base is the primary language
     * (e.g. `.../localization/eng`); the others are its sibling directories (`deu`, `fra`, ...).
     * A null [language] means the primary one.
     */
    fun localizationDir(context: CardLocContext, language: String?): File {
        val primary = File(context.baseDir, context.localizationBase)
        return if (language == null || language == primary.name) primary
        else File(primary.parentFile, language)
    }

    private val SKIPPED_DIRS = setOf("bin", "obj", "node_modules", "build", "out")

    private fun hasJson(dir: File) = dir.listFiles { f -> f.isFile && f.extension == "json" }?.isNotEmpty() == true

    /**
     * Resolves a picked directory to a language directory: a directory with json files is one itself;
     * for a `localization` root the `eng` subdirectory is preferred, else the first language with json.
     */
    fun primaryLanguageDir(dir: File): File {
        if (hasJson(dir)) return dir
        val langs = dir.listFiles { f -> f.isDirectory && hasJson(f) }?.sortedBy { it.name } ?: return dir
        return langs.firstOrNull { it.name == "eng" } ?: langs.firstOrNull() ?: dir
    }

    /** Finds `localization/<lang>` directories below [root] (primary language of each `localization` folder). */
    fun findLocalizationDirs(root: File, maxDepth: Int = 6): List<File> {
        val found = mutableListOf<File>()
        fun walk(dir: File, depth: Int) {
            if (depth > maxDepth || found.size >= 50) return
            val children = dir.listFiles { f -> f.isDirectory } ?: return
            for (child in children.sortedBy { it.name }) {
                if (child.name.startsWith(".") || child.name in SKIPPED_DIRS) continue
                if (child.name.equals("localization", ignoreCase = true)) {
                    val primary = primaryLanguageDir(child)
                    if (primary != child) found.add(primary)
                } else {
                    walk(child, depth + 1)
                }
            }
        }
        walk(root, 0)
        return found
    }

    /** Languages of the project, primary first, then the sibling directories that hold json files. */
    fun languages(context: CardLocContext): List<String> {
        if (context.localizationBase.isBlank()) return emptyList()
        val primary = File(context.baseDir, context.localizationBase)
        val siblings = primary.parentFile?.listFiles { f ->
            f.isDirectory && f.name != primary.name && f.listFiles { j -> j.extension == "json" }?.isNotEmpty() == true
        }?.map { it.name }?.sorted() ?: emptyList()
        return listOf(primary.name) + siblings
    }

    fun load(
        context: CardLocContext, keyPrefix: String, preset: CardLocPreset, language: String? = null
    ): LinkedHashMap<String, String> {
        val result = LinkedHashMap<String, String>()
        val dir = localizationDir(context, language)

        preset.fields.groupBy { it.file }.forEach { (fileName, fieldsInFile) ->
            val jsonText = LocFileIO.read(File(dir, fileName))?.trimStart('\uFEFF') ?: return@forEach
            val obj = runCatching { JsonParser.parseString(jsonText).asJsonObject }.getOrNull() ?: return@forEach

            fieldsInFile.forEach { fieldDef ->
                val fullKey = "$keyPrefix.${fieldDef.name}"
                obj[fullKey]?.let { result[fullKey] = it.asString }
            }
        }
        return result
    }

    /** Keys that were not written because the file changed under them (see [save]). */
    data class SaveResult(val conflicts: Set<String>)

    /**
     * Writes [values] (an empty value removes the key).
     *
     * @param expected what each key held when the caller loaded it. If a key has a different value
     * on disk now (edited elsewhere in the meantime) it is *not* overwritten and is reported as a
     * conflict - unless [force] is set. Keys missing from [expected] are never treated as conflicts.
     */
    fun save(
        context: CardLocContext, values: Map<String, String>, preset: CardLocPreset, language: String? = null,
        expected: Map<String, String>? = null, force: Boolean = false
    ): SaveResult {
        val dir = localizationDir(context, language)
        val byFile = mutableMapOf<String, MutableMap<String, String>>()

        values.forEach { (fullKey, value) ->
            // Field names may contain dots (e.g. "banter.dead.endTurnPing"), so match on the key's suffix.
            val fieldDef = preset.fields.find { fullKey.endsWith(".${it.name}") } ?: return@forEach
            byFile.getOrPut(fieldDef.file) { mutableMapOf() }[fullKey] = value
        }

        val conflicts = mutableSetOf<String>()
        byFile.forEach { (fileName, entries) ->
            conflicts += saveToFile(File(dir, fileName), entries, expected, force)
        }
        return SaveResult(conflicts)
    }

    data class RenameResult(val movedFields: Int, val languages: List<String>)

    /**
     * Moves every "<oldKey>.<field>" entry to "<newKey>.<field>", keeping its value,
     * across every language directory under the localization root (not just eng).
     * Never overwrites an entry that already exists under newKey.
     */
    fun renameKey(context: CardLocContext, oldKey: String, newKey: String, preset: CardLocPreset): RenameResult {
        if (oldKey == newKey) return RenameResult(0, emptyList())

        val engDir = File(context.baseDir, context.localizationBase)
        val langRoot = engDir.parentFile ?: return RenameResult(0, emptyList())
        val langDirs = langRoot.listFiles { f -> f.isDirectory } ?: return RenameResult(0, emptyList())

        var movedFields = 0
        val touchedLangs = mutableListOf<String>()

        preset.fields.groupBy { it.file }.forEach { (fileName, fieldsInFile) ->
            langDirs.forEach { langDir ->
                val file = File(langDir, fileName)
                val original = LocFileIO.read(file) ?: return@forEach
                val patcher = runCatching { JsonPatcher(original) }.getOrNull() ?: return@forEach

                var changed = false
                fieldsInFile.forEach { fieldDef ->
                    if (patcher.rename("$oldKey.${fieldDef.name}", "$newKey.${fieldDef.name}")) {
                        changed = true
                        movedFields++
                    }
                }

                if (changed) {
                    val updated = patcher.result()
                    val renamedOk = runCatching {
                        verifyRename(original, updated, oldKey, newKey, fieldsInFile.map { it.name })
                        LocFileIO.write(file, updated)
                    }.onFailure { it.printStackTrace() }.isSuccess
                    if (renamedOk && langDir.name !in touchedLangs) touchedLangs.add(langDir.name)
                }
            }
        }

        return RenameResult(movedFields, touchedLangs)
    }

    /**
     * Writes [values] into [file] by patching the text in place, so formatting, key order and all
     * untouched entries survive. Safety rails:
     *  - a file that is not a parseable JSON object is left alone (an exception is thrown);
     *  - keys that changed on disk since the caller loaded them are skipped and returned as conflicts;
     *  - the patched text is parsed and compared with "old content + intended changes" before anything
     *    is written, so a bug in the patcher can never corrupt a file;
     *  - the write goes through the IDE (undoable, in sync with open editors) or atomically.
     * Returns the conflicting keys.
     */
    private fun saveToFile(
        file: File, values: Map<String, String>, expected: Map<String, String>?, force: Boolean
    ): Set<String> {
        val original = LocFileIO.read(file)?.takeIf { it.isNotBlank() } ?: "{}"
        val patcher = JsonPatcher(original)

        val conflicts = mutableSetOf<String>()
        val applied = LinkedHashMap<String, String>()
        values.forEach { (key, value) ->
            if (!force && expected != null && expected.containsKey(key) && (patcher.get(key) ?: "") != expected[key]) {
                conflicts.add(key) // edited elsewhere since it was loaded: don't overwrite blindly
            } else {
                applied[key] = value
            }
        }
        // Filled text is written; an emptied field removes its key (nothing is stored for empty text).
        applied.forEach { (k, v) -> if (v.isEmpty()) patcher.remove(k) else patcher.set(k, v) }

        val updated = patcher.result()
        if (updated != original) {
            verifySave(original, updated, applied)
            LocFileIO.write(file, updated)
        }
        return conflicts
    }

    private fun parseObject(text: String): JsonObject =
        JsonParser.parseString(text.trimStart('\uFEFF')).asJsonObject

    /** Throws unless [updated] is exactly [original] plus [applied] (empty value = removed). */
    private fun verifySave(original: String, updated: String, applied: Map<String, String>) {
        val expected = parseObject(original)
        applied.forEach { (k, v) -> if (v.isEmpty()) expected.remove(k) else expected.addProperty(k, v) }
        check(parseObject(updated) == expected) { "Refusing to write: the result did not match the intended change" }
    }

    /** Throws unless [updated] is [original] with only the given field keys renamed. */
    private fun verifyRename(original: String, updated: String, oldKey: String, newKey: String, fields: List<String>) {
        val expected = parseObject(original)
        for (f in fields) {
            val old = "$oldKey.$f"
            val new = "$newKey.$f"
            if (expected.has(old) && !expected.has(new)) expected.add(new, expected.remove(old))
        }
        check(parseObject(updated) == expected) { "Refusing to write: the result did not match the intended rename" }
    }
}
