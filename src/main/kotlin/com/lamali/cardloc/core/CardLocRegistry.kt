package com.lamali.cardloc.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.lamali.cardloc.CardLocConstants
import com.lamali.cardloc.data.CardLocConfig
import com.lamali.cardloc.data.CardLocContext
import com.lamali.cardloc.data.PinnedColor
import java.io.File

@Service(Service.Level.PROJECT)
class CardLocRegistry(private val project: Project) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Resolves the nearest configuration file by walking up the directory tree
     * starting from the provided [sourceFile].
     */
    fun getContextForFile(sourceFile: VirtualFile): CardLocContext? {
        val solutionRootPath = project.basePath ?: return null
        val solutionRoot = File(solutionRootPath)
        var currentDir: File? = File(sourceFile.path).parentFile

        while (currentDir != null && currentDir.canonicalPath.startsWith(solutionRoot.canonicalPath)) {
            val configFile = File(currentDir, CardLocConstants.CONFIG_FILENAME)
            if (configFile.exists()) {
                return loadContext(configFile)
            }
            currentDir = currentDir.parentFile
        }
        return null
    }

    /**
     * Loads a specific config file into a Context object.
     */
    fun loadContext(configFile: File): CardLocContext? {
        return runCatching {
            val json = configFile.readText().trimStart('\uFEFF')
            val config = gson.fromJson(json, CardLocConfig::class.java)

            // Ensure we have default colors if the config is empty
            val colorsFromDisk: List<PinnedColor>? = config.pinnedColors

            val finalConfig = if (colorsFromDisk == null || colorsFromDisk.isEmpty()) {
                config.copy(pinnedColors = defaultPinnedColors)
            } else config

            CardLocContext(
                config = finalConfig,
                baseDir = configFile.parentFile,
                configFile = configFile
            )
        }.onFailure {
            println("CardLoc: Failed to load context from ${configFile.path}: ${it.message}")
        }.getOrNull()
    }

    /**
     * Persists a context back to its specific file.
     * Uses a background thread to prevent UI freezing.
     */
    fun saveContext(context: CardLocContext) {
        val file = context.configFile
        val json = gson.toJson(context.config)

        Thread {
            runCatching {
                file.writeText(json)
                println("CardLoc: Saved config to ${file.absolutePath}")
            }.onFailure {
                it.printStackTrace()
            }
        }.start()
    }

    /**
     * Helper to update pinned colors within a specific context.
     */
    fun pinColor(context: CardLocContext, color: PinnedColor): CardLocContext {
        val existingColors: List<PinnedColor>? = context.config.pinnedColors
        val safeList = existingColors ?: emptyList()
        if (safeList.any { it.tag == color.tag && it.hex == color.hex }) return context
        val updatedConfig = context.config.copy(
            pinnedColors = safeList + color
        )
        val newContext = context.copy(config = updatedConfig)
        saveContext(newContext)
        return newContext
    }

    fun unpinColor(context: CardLocContext, color: PinnedColor): CardLocContext {
        val currentColors: List<PinnedColor>? = context.config.pinnedColors
        val safeList = currentColors ?: emptyList()
        val updatedList = safeList.filterNot { pinned ->
            (color.tag != null && pinned.tag == color.tag) ||
                    (color.hex != null && pinned.hex == color.hex)
        }

        val updatedConfig = context.config.copy(pinnedColors = updatedList)
        val newContext = context.copy(config = updatedConfig)

        saveContext(newContext)
        return newContext
    }

    companion object {
        val defaultPinnedColors = listOf(
            PinnedColor(tag = "gold"),
            PinnedColor(tag = "blue")
        )
    }
}