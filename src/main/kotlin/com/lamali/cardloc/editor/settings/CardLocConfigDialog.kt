package com.lamali.cardloc.editor.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.lamali.cardloc.core.CardLocRegistry
import com.intellij.openapi.ui.ComboBox
import com.lamali.cardloc.data.CardLocConfig
import com.lamali.cardloc.data.CardLocContext
import com.lamali.cardloc.data.CardLocPreset
import com.intellij.ui.JBSplitter
import java.awt.*
import java.io.File
import javax.swing.*

/**
 * Edits the settings of every configured folder (mod) of the project. A dropdown at the top
 * switches between folders; all changes are written when the dialog is confirmed.
 */
class CardLocConfigDialog(
    private val project: Project,
    private val initialContext: CardLocContext,
    private val registry: CardLocRegistry
) : DialogWrapper(project) {

    /** Working copy of one folder's config. */
    private class Draft(
        val baseDir: File,
        var config: CardLocConfig,
        var keyPrefix: String,
        var locBase: String,
        var presets: List<CardLocPreset>,
        val existedBefore: Boolean,
        val isDefaults: Boolean = false
    )

    /** The context (of the folder the dialog was opened for) that was stored on confirm. */
    var savedContext: CardLocContext? = null
        private set

    private val projectRoot = File(project.basePath ?: ".").canonicalFile
    private val drafts = mutableListOf<Draft>()

    /** Pseudo-entry that edits the presets shared by all folders. */
    private val initialDefaults = registry.projectDefaults()
    private val initialDefaultPrefix = registry.defaultKeyPrefix()
    private val initialDefaultBase = registry.defaultLocalizationBase()
    private val defaultsDraft = Draft(
        baseDir = projectRoot,
        config = CardLocConfig(localizationBase = "", projectId = "", presets = emptyList()),
        keyPrefix = registry.defaultKeyPrefix(),
        locBase = registry.defaultLocalizationBase(),
        presets = initialDefaults,
        existedBefore = true,
        isDefaults = true
    )
    private val removed = mutableListOf<File>()
    private var currentDraft: Draft? = null
    private var switching = false

    private val folderCombo = ComboBox<Draft>()
    private val presetListModel = DefaultListModel<CardLocPreset>()

    private val settingsPanel = GlobalConfigPanel().also { it.projectRoot = projectRoot }
    private var selectorPanel: PresetSidebarPanel
    private val editorPanel = PresetDetailPanel(project)

    private var lastSelectedIndex: Int = -1
    private var isSaving = false

    init {
        title = "CardLoc Configuration"

        selectorPanel = PresetSidebarPanel(
            model = presetListModel,
            onSelectionChanged = { preset, newIndex -> handleSelectionChange(preset, newIndex) },
            onCreate = { createNewPreset() },
            onDuplicate = { preset -> duplicatePreset(preset) },
            onDelete = { index -> deletePreset(index) }
        )

        registry.allContexts().forEach { drafts.add(draftOf(it, existedBefore = true)) }
        val initialDir = initialContext.baseDir.canonicalFile
        // A project without any folder entry is the single-mod case: edit the project defaults.
        val startOnDefaults = initialDir == projectRoot && drafts.none { it.baseDir == initialDir }
        if (!startOnDefaults && drafts.none { it.baseDir == initialDir }) {
            drafts.add(draftOf(initialContext.copy(baseDir = initialDir), existedBefore = false))
        }
        drafts.sortBy { it.baseDir.path }

        folderCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                (value as? Draft)?.let { text = label(it) }
                return this
            }
        }
        folderCombo.addItem(defaultsDraft)
        drafts.forEach { folderCombo.addItem(it) }
        editorPanel.defaultsLookup = { defaultsDraft.presets }
        folderCombo.addActionListener {
            if (!switching) (folderCombo.selectedItem as? Draft)?.let { switchTo(it) }
        }

        init()

        val start = if (startOnDefaults) defaultsDraft else drafts.first { it.baseDir == initialDir }
        folderCombo.selectedItem = start
        if (currentDraft == null) switchTo(start)
    }

    private fun draftOf(context: CardLocContext, existedBefore: Boolean) = Draft(
        baseDir = context.baseDir,
        config = context.config,
        keyPrefix = context.ownKeyPrefix ?: "",
        locBase = context.config.localizationBase ?: "",
        presets = context.config.presets,
        existedBefore = existedBefore
    )

    private fun label(draft: Draft): String {
        if (draft.isDefaults) return "Project defaults (shared by all folders)"
        val rel = projectRoot.toPath().relativize(draft.baseDir.toPath()).toString().replace(File.separatorChar, '/')
        val name = rel.ifEmpty { "(project root)" }
        return if (draft.existedBefore) name else "$name  (not configured)"
    }

    /** New folders start by referencing every project default and adding nothing. */
    private fun referencePresets() =
        defaultsDraft.presets.map { CardLocPreset(it.id, emptyList(), emptyList(), base = it.id) }

    /** Writes the editor's current state back into [currentDraft]. */
    private fun stashCurrent() {
        val draft = currentDraft ?: return
        saveCurrentEditorStateToList()
        draft.keyPrefix = settingsPanel.getKeyPrefix()
        draft.locBase = settingsPanel.getLocBase()
        draft.presets = (0 until presetListModel.size()).map { presetListModel.getElementAt(it) }
    }

    private fun switchTo(draft: Draft) {
        if (draft === currentDraft) return
        stashCurrent()
        currentDraft = draft

        lastSelectedIndex = -1
        editorPanel.loadPreset(null)
        presetListModel.clear()
        settingsPanel.baseDir = draft.baseDir
        settingsPanel.setState(draft.keyPrefix, draft.locBase)
        settingsPanel.setFolderMode(!draft.isDefaults)
        editorPanel.defaultsMode = draft.isDefaults
        draft.presets.forEach { presetListModel.addElement(it) }
        if (!presetListModel.isEmpty) selectorPanel.setSelectedIndex(0)
    }

    private fun addFolder() {
        val chooser = JFileChooser(projectRoot).apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
        if (chooser.showOpenDialog(contentPanel) != JFileChooser.APPROVE_OPTION) return
        val dir = chooser.selectedFile.canonicalFile
        if (!dir.path.startsWith(projectRoot.path)) {
            showError("The folder must be inside the project.")
            return
        }
        val draft = drafts.firstOrNull { it.baseDir == dir } ?: Draft(
            baseDir = dir,
            config = CardLocConfig(localizationBase = "", projectId = "", presets = emptyList()),
            keyPrefix = "",
            locBase = "",
            presets = referencePresets(),
            existedBefore = false
        ).also {
            drafts.add(it)
            folderCombo.addItem(it)
        }
        folderCombo.selectedItem = draft
    }

    /** Creates (or overwrites) settings for another folder as a copy of the selected folder's settings. */
    private fun copyToFolder() {
        val source = currentDraft ?: return
        if (source.isDefaults) {
            showError("Select a folder to copy from. New folders already inherit the project defaults.")
            return
        }
        stashCurrent()
        val chooser = JFileChooser(projectRoot).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Copy settings of ${label(source)} to folder"
        }
        if (chooser.showOpenDialog(contentPanel) != JFileChooser.APPROVE_OPTION) return
        val dir = chooser.selectedFile.canonicalFile
        if (!dir.path.startsWith(projectRoot.path)) {
            showError("The folder must be inside the project.")
            return
        }
        if (dir == source.baseDir) return

        val existing = drafts.firstOrNull { it.baseDir == dir }
        if (existing != null) {
            val overwrite = JOptionPane.showConfirmDialog(
                contentPanel, "${label(existing)} already has settings. Overwrite them?", "Confirm", JOptionPane.YES_NO_OPTION
            )
            if (overwrite != JOptionPane.YES_OPTION) return
            existing.config = source.config
            existing.keyPrefix = source.keyPrefix
            existing.locBase = source.locBase
            existing.presets = source.presets
            // Reload the editor from the overwritten draft.
            currentDraft = null
            folderCombo.selectedItem = existing
            switchTo(existing)
        } else {
            val copy = Draft(dir, source.config, source.keyPrefix, source.locBase, source.presets, existedBefore = false)
            drafts.add(copy)
            folderCombo.addItem(copy)
            folderCombo.selectedItem = copy
        }
    }

    private fun removeFolder() {
        val draft = currentDraft ?: return
        if (draft.isDefaults) {
            showError("The project defaults cannot be removed.")
            return
        }
        if (JOptionPane.showConfirmDialog(
                contentPanel, "Remove the CardLoc settings of ${label(draft)}?", "Confirm", JOptionPane.YES_NO_OPTION
            ) != JOptionPane.YES_OPTION
        ) return
        if (draft.existedBefore) removed.add(draft.baseDir)
        val index = drafts.indexOf(draft)
        drafts.remove(draft)
        currentDraft = null // nothing to stash: the draft is gone

        switching = true
        folderCombo.removeItem(draft)
        switching = false

        if (drafts.isEmpty()) {
            // Always keep one entry to edit: an unconfigured one for the folder the dialog opened for.
            val fresh = draftOf(
                initialContext.copy(
                    config = CardLocConfig(localizationBase = "", projectId = "", presets = emptyList())
                ).let { it },
                existedBefore = false
            ).also { it.presets = referencePresets() }
            drafts.add(fresh)
            folderCombo.addItem(fresh)
        }
        folderCombo.selectedItem = drafts[index.coerceAtMost(drafts.size - 1)]
        switchTo(folderCombo.selectedItem as Draft)
    }

    private fun handleSelectionChange(newPreset: CardLocPreset?, newIndex: Int) {
        if (isSaving) return
        if (lastSelectedIndex >= 0 && lastSelectedIndex < presetListModel.size()) {
            try {
                isSaving = true
                val updated = editorPanel.getCurrentPresetState()
                presetListModel.setElementAt(updated, lastSelectedIndex)
            } finally {
                isSaving = false
            }
        }

        lastSelectedIndex = newIndex
        editorPanel.loadPreset(newPreset)
    }

    private fun saveCurrentEditorStateToList() {
        if (isSaving) return
        if (lastSelectedIndex >= 0 && lastSelectedIndex < presetListModel.size()) {
            try {
                isSaving = true
                val updated = editorPanel.getCurrentPresetState()
                presetListModel.setElementAt(updated, lastSelectedIndex)
            } finally {
                isSaving = false
            }
        }
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout(0, 10)).apply {
            preferredSize = JBUI.size(1100, 800)
            border = JBUI.Borders.empty(10)
        }

        val topSection = JPanel(BorderLayout()).apply {
            val titleLabel = JBLabel("Folder settings").apply {
                font = font.deriveFont(Font.BOLD, 14f)
                border = JBUI.Borders.emptyBottom(5)
            }
            val folderRow = JPanel(BorderLayout(6, 0)).apply {
                add(JLabel("Folder:"), BorderLayout.WEST)
                add(folderCombo, BorderLayout.CENTER)
                add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                    add(JButton("Add folder...").apply { addActionListener { addFolder() } })
                    add(JButton("Copy to...").apply {
                        toolTipText = "Copy the selected folder's settings to another folder"
                        addActionListener { copyToFolder() }
                    })
                    add(JButton("Remove").apply { addActionListener { removeFolder() } })
                }, BorderLayout.EAST)
                border = JBUI.Borders.empty(0, 5, 5, 0)
            }
            val north = JPanel(BorderLayout()).apply {
                add(titleLabel, BorderLayout.NORTH)
                add(folderRow, BorderLayout.CENTER)
            }
            add(north, BorderLayout.NORTH)
            add(settingsPanel, BorderLayout.CENTER)
            add(JSeparator().apply { border = JBUI.Borders.empty(10, 0) }, BorderLayout.SOUTH)
        }

        val presetSection = JPanel(BorderLayout()).apply {
            val titleLabel = JBLabel("Preset management").apply {
                font = font.deriveFont(Font.BOLD, 14f)
                border = JBUI.Borders.emptyBottom(5)
            }

            val splitSection = JBSplitter(false, 0.25f).apply {
                firstComponent = selectorPanel
                secondComponent = editorPanel
                dividerWidth = 3
                border = JBUI.Borders.empty(5, 0)
            }

            add(titleLabel, BorderLayout.NORTH)
            add(splitSection, BorderLayout.CENTER)
        }

        mainPanel.add(topSection, BorderLayout.NORTH)
        mainPanel.add(presetSection, BorderLayout.CENTER)

        return mainPanel
    }

    override fun doOKAction() {
        stashCurrent()
        try {
            if (defaultsDraft.presets != initialDefaults || defaultsDraft.keyPrefix != initialDefaultPrefix ||
                defaultsDraft.locBase != initialDefaultBase
            ) {
                registry.saveProjectDefaults(defaultsDraft.presets, defaultsDraft.keyPrefix, defaultsDraft.locBase)
            }
            removed.forEach { registry.removeContext(it) }
            val initialDir = initialContext.baseDir.canonicalFile
            drafts.forEach { d ->
                // Skip untouched, never-configured entries so empty configs are not stored: a new folder
                // is only kept once it sets something of its own (a prefix, a base or extra presets).
                val addsPresets = d.presets.any { it.base == null || it.markers.isNotEmpty() || it.fields.isNotEmpty() }
                if (!d.existedBefore && d.keyPrefix.isBlank() && d.locBase.isBlank() && !addsPresets) return@forEach
                registry.saveContext(
                    CardLocContext(
                        config = d.config.copy(keyPrefix = d.keyPrefix, localizationBase = d.locBase, presets = d.presets),
                        baseDir = d.baseDir
                    )
                )
            }
            // Re-read so the returned context has the project defaults merged in.
            savedContext = registry.contextForDir(initialDir)
            super.doOKAction()
        } catch (e: Exception) {
            showError("Save failed: ${e.message}")
        }
    }

    private fun createNewPreset() {
        val newPreset = CardLocPreset("new_preset", emptyList(), emptyList())
        presetListModel.addElement(newPreset)
        selectorPanel.setSelectedIndex(presetListModel.size() - 1)
    }

    private fun duplicatePreset(preset: CardLocPreset) {
        val copy = preset.copy(id = "${preset.id}_copy")
        presetListModel.addElement(copy)
        selectorPanel.setSelectedIndex(presetListModel.size() - 1)
    }

    private fun deletePreset(index: Int) {
        if (index >= 0 && JOptionPane.showConfirmDialog(contentPanel, "Delete preset?", "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            if (index == lastSelectedIndex) {
                lastSelectedIndex = -1
                editorPanel.loadPreset(null)
            } else if (index < lastSelectedIndex) {
                lastSelectedIndex--
            }
            presetListModel.remove(index)
        }
    }

    private fun showError(msg: String) = JOptionPane.showMessageDialog(contentPanel, msg, "Error", JOptionPane.ERROR_MESSAGE)
}