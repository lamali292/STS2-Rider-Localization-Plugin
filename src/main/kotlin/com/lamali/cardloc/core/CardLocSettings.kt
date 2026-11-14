package com.lamali.cardloc.core

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import com.lamali.cardloc.data.CardLocConfig
import com.lamali.cardloc.data.CardLocPreset
import com.lamali.cardloc.data.CustomTag
import com.lamali.cardloc.data.FieldDef
import com.lamali.cardloc.data.PinnedColor
import com.lamali.cardloc.data.withJsonSuffix

/**
 * Per-project CardLoc configuration, persisted by the IDE to `.idea/cardloc.xml`.
 * Replaces the deprecated `cardloc-presets.json` (see [CardLocRegistry] for the one-time import).
 *
 * A solution can hold several mods, so configs are stored per "module": each is keyed by the
 * directory (relative to the project root, "" = root) that its localization base resolves against.
 *
 * The state classes below are plain beans so the platform writes real, diff-friendly XML.
 */
@Service(Service.Level.PROJECT)
@State(name = "CardLocSettings", storages = [Storage("cardloc.xml")])
class CardLocSettings : PersistentStateComponent<CardLocSettings.State> {

    // Compact XML: values are attributes, lists are plain child elements, markers one comma list.
    /** One localization file and the fields stored in it, e.g. `title,description,selectionScreenPrompt?` (`?` = optional). */
    @Tag("file")
    class FileState {
        @Attribute var name: String = ""
        @Attribute var fields: String = ""
    }

    @Tag("preset")
    class PresetState {
        @Attribute var id: String = ""

        /** Id of the project-default preset this one extends (then files/markers are additions). */
        @Attribute var base: String = ""

        /** Comma-separated marker class names. */
        @Attribute var markers: String = ""
        @XCollection(style = XCollection.Style.v2) var files: MutableList<FileState> = mutableListOf()
    }

    @Tag("color")
    class PinnedColorState {
        @Attribute var tag: String = ""
        @Attribute var hex: String = ""
        @Attribute var label: String = ""
    }

    @Tag("customTag")
    class CustomTagState {
        @Attribute var tag: String = ""
        @Attribute var label: String = ""
        @Attribute var color: String = ""
        @Attribute var bold: Boolean = false
        @Attribute var italic: Boolean = false
    }

    @Tag("module")
    class ModuleState {
        @Attribute var baseDir: String = ""
        @Attribute var localizationBase: String = ""
        @Attribute var keyPrefix: String = ""

        /** Legacy: only used to derive the key prefix when [keyPrefix] is empty. */
        @Attribute var projectId: String = ""
        @XCollection(style = XCollection.Style.v2, propertyElementName = "presets")
        var presets: MutableList<PresetState> = mutableListOf()
        @XCollection(style = XCollection.Style.v2, propertyElementName = "pinnedColors")
        var pinnedColors: MutableList<PinnedColorState> = mutableListOf()
        @XCollection(style = XCollection.Style.v2, propertyElementName = "customTags")
        var customTags: MutableList<CustomTagState> = mutableListOf()
    }

    class State {
        /** Project-default key prefix / localization base (relative to the project root); empty = unset. */
        @Attribute var defaultKeyPrefix: String = ""
        @Attribute var defaultLocalizationBase: String = ""

        /** Project-wide default presets; empty means "use the bundled ones". */
        @XCollection(style = XCollection.Style.v2, propertyElementName = "defaults")
        var defaults: MutableList<PresetState> = mutableListOf()

        @XCollection(style = XCollection.Style.v2, propertyElementName = "modules")
        var modules: MutableList<ModuleState> = mutableListOf()
    }

    private var state = State()
    private val cache = HashMap<String, CardLocConfig>()

    @Synchronized
    override fun getState(): State = state

    @Synchronized
    override fun loadState(state: State) {
        this.state = state
        cache.clear()
    }

    @Synchronized
    fun get(baseDir: String): CardLocConfig? {
        cache[baseDir]?.let { return it }
        val module = state.modules.firstOrNull { it.baseDir == baseDir } ?: return null
        return toConfig(module).also { cache[baseDir] = it }
    }

    /** Base directories (relative to the project root) of every stored module. */
    @Synchronized
    fun baseDirs(): List<String> = state.modules.map { it.baseDir }

    @Synchronized
    fun remove(baseDir: String) {
        state.modules.removeAll { it.baseDir == baseDir }
        cache.remove(baseDir)
    }

    @Synchronized
    fun has(baseDir: String): Boolean = state.modules.any { it.baseDir == baseDir }

    @Synchronized
    fun put(baseDir: String, config: CardLocConfig) {
        val module = state.modules.firstOrNull { it.baseDir == baseDir }
            ?: ModuleState().also { it.baseDir = baseDir; state.modules.add(it) }
        fill(module, config)
        cache[baseDir] = config
    }

    // --- conversion between the domain model and the XML beans ---

    private fun fill(module: ModuleState, config: CardLocConfig) {
        module.localizationBase = config.localizationBase ?: ""
        module.projectId = config.projectId ?: ""
        module.keyPrefix = config.keyPrefix ?: ""
        module.presets = config.presets.map { presetToState(it) }.toMutableList()
        module.pinnedColors = (config.pinnedColors ?: emptyList()).map { c ->
            PinnedColorState().also { it.tag = c.tag ?: ""; it.hex = c.hex ?: ""; it.label = c.label ?: "" }
        }.toMutableList()
        module.customTags = (config.customTags ?: emptyList()).map { t ->
            CustomTagState().also {
                it.tag = t.tag; it.label = t.label; it.color = t.color; it.bold = t.bold; it.italic = t.italic
            }
        }.toMutableList()
    }

    private fun toConfig(module: ModuleState) = CardLocConfig(
        localizationBase = module.localizationBase,
        projectId = module.projectId,
        keyPrefix = module.keyPrefix.ifBlank { null },
        presets = module.presets.map { presetFromState(it) },
        pinnedColors = module.pinnedColors.map {
            PinnedColor(tag = it.tag.ifBlank { null }, hex = it.hex.ifBlank { null }, label = it.label.ifBlank { null })
        },
        customTags = module.customTags.map { CustomTag(it.tag, it.label, it.color, it.bold, it.italic) }
    )

    private fun presetToState(p: CardLocPreset) = PresetState().also { s ->
        s.id = p.id
        s.base = p.base ?: ""
        s.markers = p.markers.joinToString(",")
        s.files = p.fields.groupBy { it.file }.map { (file, fields) ->
            FileState().also { f ->
                f.name = file.removeSuffix(".json") // ".json" is implied
                f.fields = fields.joinToString(",") { it.name + if (it.optional) "?" else "" }
            }
        }.toMutableList()
    }

    private fun presetFromState(p: PresetState) = CardLocPreset(
        id = p.id,
        markers = p.markers.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        fields = p.files.flatMap { f ->
            f.fields.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map {
                FieldDef(name = it.removeSuffix("?"), file = f.name.withJsonSuffix(), optional = it.endsWith("?"))
            }
        },
        base = p.base.ifBlank { null }
    )

    @Synchronized
    fun defaultKeyPrefix(): String = state.defaultKeyPrefix

    @Synchronized
    fun defaultLocalizationBase(): String = state.defaultLocalizationBase

    @Synchronized
    fun putDefaultValues(keyPrefix: String, localizationBase: String) {
        state.defaultKeyPrefix = keyPrefix
        state.defaultLocalizationBase = localizationBase
    }

    /** The stored project defaults, or null when none were stored (use the bundled ones). */
    @Synchronized
    fun defaults(): List<CardLocPreset>? =
        state.defaults.takeIf { it.isNotEmpty() }?.map { presetFromState(it) }

    @Synchronized
    fun putDefaults(presets: List<CardLocPreset>) {
        state.defaults = presets.map { presetToState(it) }.toMutableList()
    }
}
