package com.lamali.cardloc.editor

import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.ui.JBSplitter
import com.lamali.cardloc.core.CardLocRegistry
import com.lamali.cardloc.core.CardLocSelector
import com.lamali.cardloc.core.CardLocService
import com.lamali.cardloc.data.CardLocContext
import com.lamali.cardloc.data.CardLocPreset
import com.lamali.cardloc.editor.settings.CardLocConfigDialog
import com.lamali.cardloc.editor.ui.TagToolbar
import com.lamali.cardloc.editor.ui.UI
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.*

class CardLocPanel(private val project: Project) : JPanel(BorderLayout()) {

    // --- State Management ---
    private var currentKey = ""
    private var currentPreset: CardLocPreset? = null
    private var currentContext: CardLocContext? = null
    /** Selected language directory name; null = the primary one (the configured localization base). */
    private var language: String? = null
    private var currentFile: com.intellij.openapi.vfs.VirtualFile? = null
    private var knownDisk: Map<String, String> = emptyMap()
    private var activeRegistry: CardLocRegistry = project.getService(CardLocRegistry::class.java)

    private val autoSaveTimer = Timer(2000) { performAutoSave() }.apply { isRepeats = false }
    private var lastChangeTime = 0L
    private val autoSaveDelay = 1500L

    // UI Components
    private val header = CardLocHeader(::reload, ::promptAddField, ::save, ::openPresetEditor, ::pickClass, ::pickLanguage, ::languageHasEntry)
    private val preview = CardLocPreview()
    private val tagToolbar = TagToolbar(activeRegistry)
    private val fieldsPanel = JPanel(GridBagLayout()).apply { background = UI.bg }
    private val rows = mutableListOf<FieldRow>()
    private val editorWrapper = JPanel(BorderLayout()).apply { background = UI.bg }

    private val scrollFields = JScrollPane(fieldsPanel).apply {
        border = null
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
    }

    private val splitPane = JBSplitter(true, 0.6f).apply {
        dividerWidth = 5
    }

    init {
        background = UI.bg
        editorWrapper.add(scrollFields, BorderLayout.CENTER)
        splitPane.firstComponent = editorWrapper
        splitPane.secondComponent = preview
        add(header, BorderLayout.NORTH)
        add(splitPane, BorderLayout.CENTER)

        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                applyAdaptiveLayout()
            }
        })
    }

    private fun applyAdaptiveLayout() {
        val isWide = width > height && width > 600
        editorWrapper.remove(tagToolbar)
        if (!isWide) {
            tagToolbar.updateOrientation(vertical = true)
            editorWrapper.add(tagToolbar, BorderLayout.WEST)
            splitPane.orientation = true
        } else {
            tagToolbar.updateOrientation(vertical = false)
            editorWrapper.add(tagToolbar, BorderLayout.NORTH)
            splitPane.orientation = false
        }
        editorWrapper.revalidate(); editorWrapper.repaint()
        revalidate(); repaint()
    }

    // --- Updated Load Method ---
    fun load(context: CardLocContext, key: String, existing: Map<String, String>, preset: CardLocPreset) {
        this.currentContext = context
        this.currentKey = key
        this.currentPreset = preset
        tagToolbar.setContext(context)

        // The selector hands us the primary language's values; for another language read that one.
        val languages = CardLocService.languages(context)
        if (language != null && language !in languages) language = null
        header.setLanguages(languages, language ?: languages.firstOrNull() ?: "")  // refined below once the values are known
        val existing = if (language != null) CardLocService.load(context, key, preset, language) else existing
        this.knownDisk = existing.toMap()
        header.setLanguages(languages, language ?: languages.firstOrNull() ?: "", selectedHasEntry = existing.isNotEmpty())

        header.setKey(key)
        fieldsPanel.removeAll()
        rows.clear()

        val keys = (preset.fields.filter { !it.optional }.map { it.name } +
                existing.keys.map { it.removePrefix("$key.") }
                    .filter { suffix -> preset.fields.any { it.name == suffix } }).distinct()

        keys.forEach { addRow(it, existing["$key.$it"] ?: "") }
        refreshUI()
    }

    private fun addRow(suffix: String, value: String) {
        val row = FieldRow(suffix, value, currentContext) {
            preview.update(rows)
            scheduleAutoSave()
        }
        row.connectToolbar(tagToolbar)
        rows.add(row)

        val gbc = GridBagConstraints().apply {
            gridx = 0; gridy = rows.size; weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL; anchor = GridBagConstraints.NORTH
            insets = Insets(0, 0, 2, 0)
        }
        fieldsPanel.add(row, gbc)
    }

    /** Shows the card classes of [file] in the header dropdown, with [selected] chosen. */
    fun setClassChoices(file: com.intellij.openapi.vfs.VirtualFile, names: List<String>, selected: String) {
        currentFile = file
        header.setClasses(names, selected)
    }

    /** Whether the shown card has any value in [lang]; used to sort / grey out the language list. */
    private fun languageHasEntry(lang: String): Boolean {
        val context = currentContext ?: return true
        val preset = currentPreset ?: return true
        val primary = CardLocService.languages(context).firstOrNull()
        return runCatching {
            CardLocService.load(context, currentKey, preset, if (lang == primary) null else lang).isNotEmpty()
        }.getOrDefault(true)
    }

    private fun pickLanguage(name: String) {
        val context = currentContext ?: return
        saveCurrentContent() // flush edits to the language we are leaving
        val primary = CardLocService.languages(context).firstOrNull()
        language = if (name == primary) null else name
        reload()
    }

    private fun pickClass(name: String) {
        val file = currentFile ?: return
        val source = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(file)?.text ?: return
        CardLocSelector.show(project, file, source, CardLocSelector.Target.ClassName(name), force = true)
    }

    fun saveCurrentContent() {
        if (currentKey.isNotEmpty() && currentPreset != null) {
            autoSave()
        }
    }

    private fun scheduleAutoSave() {
        lastChangeTime = System.currentTimeMillis()
        if (!autoSaveTimer.isRunning) autoSaveTimer.start() else autoSaveTimer.restart()
    }

    private fun performAutoSave() {
        if (System.currentTimeMillis() - lastChangeTime >= autoSaveDelay) {
            SwingUtilities.invokeLater { autoSave() }
        } else autoSaveTimer.restart()
    }

    /** The rows whose text differs from what is on disk (a missing entry counts as ""). */
    private fun changedRows(): Map<String, String> =
        rows.associate { "$currentKey.${it.fieldName}" to it.text }
            .filter { (key, text) -> text != (knownDisk[key] ?: "") }

    private fun autoSave() {
        val changed = changedRows()
        if (changed.isEmpty()) return // nothing edited: never write (or create empty entries) just by looking
        persist(changed, force = false, interactive = false)
    }

    /**
     * Writes [changed] to disk. Keys that were modified elsewhere since this card was loaded are not
     * overwritten; the user is told and can choose which version to keep.
     */
    private fun persist(changed: Map<String, String>, force: Boolean, interactive: Boolean) {
        val context = currentContext ?: return
        val preset = currentPreset ?: return
        val expected = changed.keys.associateWith { knownDisk[it] ?: "" }

        runCatching { CardLocService.save(context, changed, preset, language, expected, force) }
            .onSuccess { result ->
                val written = changed.filterKeys { it !in result.conflicts }
                knownDisk = (knownDisk + written).filterValues { it.isNotEmpty() }
                if (result.conflicts.isNotEmpty()) notifyConflict(changed.filterKeys { it in result.conflicts }, interactive)
                else lastConflict = emptySet()
            }
            .onFailure { e ->
                if (interactive) JOptionPane.showMessageDialog(this, "Error: ${e.message}")
                else notifyProblem("Could not save: ${e.message}")
            }
    }

    private var lastConflict: Set<String> = emptySet()
    private var lastProblem: String? = null

    private fun notifyConflict(mine: Map<String, String>, interactive: Boolean) {
        if (!interactive && mine.keys == lastConflict) return // already told the user about exactly these
        lastConflict = mine.keys
        val n = mine.size
        NotificationGroupManager.getInstance().getNotificationGroup("CardLoc")
            .createNotification(
                "CardLoc: changed on disk",
                "$n field(s) of $currentKey were edited elsewhere since they were loaded, so your edit was not written.",
                NotificationType.WARNING
            )
            .addAction(NotificationAction.createSimpleExpiring("Overwrite with mine") {
                persist(mine, force = true, interactive = true)
            })
            .addAction(NotificationAction.createSimpleExpiring("Use the file's version") { reload() })
            .notify(project)
    }

    private fun notifyProblem(message: String) {
        if (message == lastProblem) return
        lastProblem = message
        NotificationGroupManager.getInstance().getNotificationGroup("CardLoc")
            .createNotification("CardLoc", message, NotificationType.WARNING)
            .notify(project)
    }

    private fun refreshUI() {
        fieldsPanel.add(Box.createVerticalGlue(), GridBagConstraints().apply {
            gridx = 0; gridy = 999; weightx = 1.0; weighty = 1.0; fill = GridBagConstraints.BOTH
        })
        preview.update(rows)
        revalidate(); repaint()
    }

    private fun save() {
        val changed = changedRows()
        if (changed.isEmpty()) return
        persist(changed, force = false, interactive = true)
    }

    private fun promptAddField() {
        val preset = currentPreset ?: return
        val existingSuffixes = rows.map { it.fieldName }.toSet()
        val suggestions = preset.fields.filter { it.optional && it.name !in existingSuffixes }.map { it.name }.toTypedArray()
        val comboBox = JComboBox(suggestions).apply { isEditable = true }
        val result = JOptionPane.showConfirmDialog(this, comboBox, "Add Field", JOptionPane.OK_CANCEL_OPTION)
        if (result == JOptionPane.OK_OPTION) {
            val input = comboBox.selectedItem?.toString()?.trim() ?: ""
            if (input.isNotBlank() && rows.none { it.fieldName == input }) {
                addRow(input, "")
                refreshUI()
            }
        }
    }

    private fun openPresetEditor() {
        val context = currentContext ?: activeRegistry.contextForActiveFile() ?: return
        val dialog = CardLocConfigDialog(project, context, activeRegistry)

        if (dialog.showAndGet()) {
            val updatedContext = dialog.savedContext ?: return

            currentPreset?.let { p ->
                val newPreset = updatedContext.presets.find { it.id == p.id }
                    ?: updatedContext.presets.firstOrNull()

                if (newPreset != null) {
                    val data = CardLocService.load(updatedContext, currentKey, newPreset, language)
                    load(updatedContext, currentKey, data, newPreset)
                }
            }
        }
    }

    fun renameCurrentKeyIfMatches(context: CardLocContext, oldKey: String, newKey: String, preset: CardLocPreset) {
        if (currentContext?.baseDir != context.baseDir || currentKey != oldKey) return
        val data = CardLocService.load(context, newKey, preset, language)
        load(context, newKey, data, preset)
    }

    /**
     * Called when a localization json changed on disk (e.g. edited outside this panel).
     * Reloads the shown card if its values differ from what we last loaded/saved, unless
     * the user has unsaved edits pending - those win and get written by the next autosave.
     */
    fun reloadIfChangedOnDisk(changedPath: String) {
        val context = currentContext ?: return
        val preset = currentPreset ?: return
        if (currentKey.isEmpty() || autoSaveTimer.isRunning) return
        val locDir = java.io.File(context.baseDir, context.localizationBase).parentFile ?: return
        if (!java.io.File(changedPath).canonicalPath.startsWith(locDir.canonicalPath)) return

        val disk = runCatching { CardLocService.load(context, currentKey, preset, language) }.getOrNull() ?: return
        if (disk == knownDisk) return
        load(context, currentKey, disk, preset)
    }

    private fun reload() {
        val context = currentContext ?: return
        val preset = currentPreset ?: return
        runCatching { CardLocService.load(context, currentKey, preset, language) }
            .onSuccess { load(context, currentKey, it, preset) }
    }
}