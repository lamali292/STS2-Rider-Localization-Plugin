package com.lamali.cardloc.core

import com.google.gson.Gson
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.lamali.cardloc.CardLocConstants
import com.lamali.cardloc.data.CardLocConfig
import com.lamali.cardloc.data.CardLocContext
import com.lamali.cardloc.data.CardLocPreset
import com.lamali.cardloc.data.PinnedColor
import com.lamali.cardloc.data.withJsonSuffix
import com.lamali.cardloc.editor.settings.CardLocConfigDialog
import java.io.File

@Service(Service.Level.PROJECT)
class CardLocRegistry(private val project: Project) : Disposable {

    /** "<file>#<class>" of the card last pushed to the panel, so caret moves inside one card are no-ops. */
    @Volatile
    var lastShown: String? = null

    override fun dispose() {}

    private val gson = Gson()
    private var notifiedUnconfigured = false

    private val settings: CardLocSettings
        get() = project.getService(CardLocSettings::class.java)

    /**
     * Resolves the context for [sourceFile] by walking up from its directory to the project root.
     * The nearest directory that has stored settings wins. If it has none but holds a legacy
     * `cardloc-presets.json`, that file is imported once into the settings.
     * With nothing found, an unconfigured context (bundled presets, blank mod id) rooted at the
     * project is returned; saving it creates the root entry.
     */
    @Synchronized
    fun getContextForFile(sourceFile: VirtualFile): CardLocContext? {
        val root = projectRoot() ?: return null
        var dir: File? = File(sourceFile.path).parentFile?.canonicalFile

        while (dir != null && dir.path.startsWith(root.path)) {
            val rel = relativeKey(root, dir)
            settings.get(rel)?.let { return contextOf(dir!!, it) }

            val legacy = File(dir, CardLocConstants.LEGACY_CONFIG_FILENAME)
            if (legacy.exists()) {
                readLegacy(legacy)?.let {
                    settings.put(rel, it)
                    notifyMigrated(legacy)
                    return contextOf(dir!!, it)
                }
            }
            dir = dir.parentFile
        }
        return contextOf(root, defaultConfig())
    }

    /**
     * Context for whatever file is open in the editor, else the project root. Lets the settings
     * dialog open before any card class has been recognised (i.e. while still unconfigured).
     */
    fun contextForActiveFile(): CardLocContext? {
        val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        return file?.let { getContextForFile(it) } ?: projectRoot()?.let { contextForDir(it) }
    }

    /** Every folder that has stored settings. */
    fun allContexts(): List<CardLocContext> {
        val root = projectRoot() ?: return emptyList()
        return settings.baseDirs().mapNotNull { rel ->
            settings.get(rel)?.let { contextOf(File(root, rel).canonicalFile, it) }
        }
    }

    /** Deletes the stored settings of [baseDir]. */
    fun removeContext(baseDir: File) {
        val root = projectRoot() ?: return
        settings.remove(relativeKey(root, baseDir.canonicalFile))
    }

    /** True if settings are stored for exactly [baseDir]. */
    fun isConfigured(baseDir: File): Boolean {
        val root = projectRoot() ?: return false
        return settings.has(relativeKey(root, baseDir.canonicalFile))
    }

    /** Re-reads the stored context for [baseDir] (e.g. after the config dialog saved it). */
    fun contextForDir(baseDir: File): CardLocContext? {
        val root = projectRoot() ?: return null
        val dir = baseDir.canonicalFile
        return contextOf(dir, settings.get(relativeKey(root, dir)) ?: defaultConfig())
    }

    /** Persists [context]'s config into the project settings. */
    fun saveContext(context: CardLocContext) {
        val root = projectRoot() ?: return
        settings.put(relativeKey(root, context.baseDir.canonicalFile), context.config)
    }

    /** One-off balloon with a shortcut to the config dialog, for projects without a mod id yet. */
    fun notifyUnconfigured(context: CardLocContext) {
        if (notifiedUnconfigured) return
        notifiedUnconfigured = true
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("CardLoc")
            .createNotification(
                "CardLoc is not configured",
                "Set your mod ID and localization folder to edit localization for this class.",
                NotificationType.INFORMATION
            )
        notification.addAction(NotificationAction.createSimpleExpiring("Configure") {
            CardLocConfigDialog(project, context, this).show()
        })
        notification.notify(project)
    }

    private fun projectRoot(): File? = project.basePath?.let { File(it).canonicalFile }

    private fun relativeKey(root: File, dir: File): String =
        root.toPath().relativize(dir.toPath()).toString().replace(File.separatorChar, '/')

    private fun contextOf(dir: File, config: CardLocConfig): CardLocContext {
        // The default localization base is relative to the project root; express it relative to [dir].
        val defaultBase = settings.defaultLocalizationBase()
        val root = projectRoot()
        val relativeBase = if (defaultBase.isBlank() || root == null) "" else
            dir.toPath().relativize(File(root, defaultBase).toPath()).toString().replace(File.separatorChar, '/')
        return CardLocContext(
            config = withColorDefaults(config),
            baseDir = dir,
            defaults = projectDefaults(),
            defaultKeyPrefix = settings.defaultKeyPrefix(),
            defaultLocalizationBase = relativeBase
        )
    }

    fun defaultKeyPrefix(): String = settings.defaultKeyPrefix()
    fun defaultLocalizationBase(): String = settings.defaultLocalizationBase()

    fun pinColor(context: CardLocContext, color: PinnedColor): CardLocContext {
        val safeList = context.config.pinnedColors ?: emptyList()
        if (safeList.any { it.tag == color.tag && it.hex == color.hex }) return context
        val newContext = context.copy(config = context.config.copy(pinnedColors = safeList + color))
        saveContext(newContext)
        return newContext
    }

    fun unpinColor(context: CardLocContext, color: PinnedColor): CardLocContext {
        val safeList = context.config.pinnedColors ?: emptyList()
        val updatedList = safeList.filterNot { pinned ->
            (color.tag != null && pinned.tag == color.tag) ||
                    (color.hex != null && pinned.hex == color.hex)
        }
        val newContext = context.copy(config = context.config.copy(pinnedColors = updatedList))
        saveContext(newContext)
        return newContext
    }

    // --- Legacy cardloc-presets.json migration ---

    private fun readLegacy(file: File): CardLocConfig? = runCatching {
        val json = file.readText().trimStart('﻿')
        val config = gson.fromJson(json, CardLocConfig::class.java)
        // Gson bypasses Kotlin null-safety, so tolerate files that omit fields.
        @Suppress("USELESS_ELVIS", "UNNECESSARY_SAFE_CALL")
        config.copy(
            localizationBase = config.localizationBase ?: "",
            projectId = config.projectId ?: "",
            presets = (config.presets?.takeIf { it.isNotEmpty() } ?: referencePresets()).map { p ->
                p.copy(fields = p.fields.map { it.copy(file = it.file.withJsonSuffix()) })
            }
        )
    }.onFailure {
        println("CardLoc: Failed to import ${file.path}: ${it.message}")
    }.getOrNull()

    private fun notifyMigrated(legacy: File) {
        ApplicationManager.getApplication().invokeLater {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("CardLoc")
                .createNotification(
                    "CardLoc settings imported",
                    "Imported ${legacy.parentFile.name}/${legacy.name} into the project settings (.idea/cardloc.xml). " +
                            "${legacy.name} is deprecated and no longer read - you can delete it.",
                    NotificationType.INFORMATION
                )
                .notify(project)
        }
    }

    /** Presets shared by all folders: the stored project defaults, else the bundled ones. */
    fun projectDefaults(): List<CardLocPreset> = settings.defaults() ?: defaultPresets

    /** Saves the shared presets (storing exactly the bundled ones clears the override) and prefix / localization base. */
    fun saveProjectDefaults(presets: List<CardLocPreset>, keyPrefix: String, localizationBase: String) {
        settings.putDefaults(if (presets == defaultPresets) emptyList() else presets)
        settings.putDefaultValues(keyPrefix, localizationBase)
    }

    /** Presets that just reference every project default (and add nothing). */
    fun referencePresets(): List<CardLocPreset> =
        projectDefaults().map { CardLocPreset(it.id, emptyList(), emptyList(), base = it.id) }

    private fun defaultConfig() = CardLocConfig(
        localizationBase = "",
        projectId = "",
        presets = referencePresets(),
    )

    private fun withColorDefaults(config: CardLocConfig): CardLocConfig =
        if (config.pinnedColors.isNullOrEmpty()) config.copy(pinnedColors = defaultPinnedColors) else config

    private data class DefaultPresets(val presets: List<CardLocPreset>)

    companion object {
        val defaultPinnedColors = listOf(
            PinnedColor(tag = "gold"),
            PinnedColor(tag = "blue")
        )

        /** Presets for BaseLib's custom model classes, bundled with the plugin. */
        val defaultPresets: List<CardLocPreset> by lazy {
            val stream = CardLocRegistry::class.java.getResourceAsStream(CardLocConstants.DEFAULT_PRESETS_RESOURCE)
                ?: return@lazy emptyList()
            stream.use {
                val json = it.readBytes().toString(Charsets.UTF_8).trimStart('﻿')
                Gson().fromJson(json, DefaultPresets::class.java).presets
            }
        }
    }
}
