package com.lamali.cardloc.editor

import com.intellij.openapi.project.Project
import com.intellij.ui.JBSplitter
import com.lamali.cardloc.core.CardLocRegistry
import com.lamali.cardloc.core.CardLocService
import com.lamali.cardloc.data.CardLocPreset
import com.lamali.cardloc.editor.settings.CardLocConfigDialog
import com.lamali.cardloc.editor.ui.TagToolbar
import com.lamali.cardloc.editor.ui.UI
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.*

class CardLocPanel(private val project: Project) : JPanel(BorderLayout()) {

    private var currentKey = ""
    private var currentPreset: CardLocPreset? = null

    private val autoSaveTimer = Timer(2000) { performAutoSave() }.apply { isRepeats = false }
    private var lastChangeTime = 0L
    private val autoSaveDelay = 1500L

    private val registry = project.getService(CardLocRegistry::class.java)

    // UI Components
    private val header = CardLocHeader(::reload, ::promptAddField, ::save, ::openPresetEditor)
    private val preview = CardLocPreview()
    private val tagToolbar = TagToolbar(registry)
    private val fieldsPanel = JPanel(GridBagLayout()).apply { background = UI.bg }
    private val rows = mutableListOf<FieldRow>()

    // The wrapper that "glues" the toolbar to the fields
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

        // Setup Editor Area
        editorWrapper.add(scrollFields, BorderLayout.CENTER)

        // Setup Splitter
        splitPane.firstComponent = editorWrapper
        splitPane.secondComponent = preview

        // Main Layout
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

        // Remove toolbar to re-position it inside the wrapper
        editorWrapper.remove(tagToolbar)

        if (!isWide) {
            // SIDEBAR MODE (Narrow)
            tagToolbar.updateOrientation(vertical = true)
            editorWrapper.add(tagToolbar, BorderLayout.WEST)

            // Splitter stays vertical (Preview at bottom)
            if (!splitPane.orientation) {
                splitPane.orientation = true
            }
        } else {
            // BOTTOM BAR MODE (Wide)
            tagToolbar.updateOrientation(vertical = false)
            editorWrapper.add(tagToolbar, BorderLayout.NORTH)

            // Splitter stays horizontal (Preview at right)
            if (splitPane.orientation) {
                splitPane.orientation = false
            }
        }

        editorWrapper.revalidate()
        editorWrapper.repaint()
        revalidate()
        repaint()
    }

    fun load(keyPrefix: String, existing: Map<String, String>, preset: CardLocPreset) {
        this.currentKey = keyPrefix
        this.currentPreset = preset
        header.setKey(keyPrefix)

        fieldsPanel.removeAll()
        rows.clear()

        val keys = (preset.fields.filter { !it.optional }.map { it.name } +
                existing.keys.map { it.removePrefix("$keyPrefix.") }
                    .filter { suffix -> preset.fields.any { it.name == suffix } }).distinct()

        keys.forEach { addRow(it, existing["$keyPrefix.$it"] ?: "") }
        refreshUI()
    }

    private fun addRow(suffix: String, value: String) {
        val row = FieldRow(suffix, value) {
            preview.update(rows)
            scheduleAutoSave()
        }
        // Connect row to our local toolbar
        row.connectToolbar(tagToolbar)
        rows.add(row)

        val gbc = GridBagConstraints().apply {
            gridx = 0; gridy = rows.size; weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL; anchor = GridBagConstraints.NORTH
            insets = Insets(0, 0, 2, 0)
        }
        fieldsPanel.add(row, gbc)
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

    private fun autoSave() {
        val preset = currentPreset ?: return
        if (!registry.isInitialized()) return
        val data = rows.associate { "$currentKey.${it.fieldName}" to it.text }
        runCatching { CardLocService.save(project, currentKey, data, preset) }
    }

    private fun refreshUI() {
        fieldsPanel.add(Box.createVerticalGlue(), GridBagConstraints().apply {
            gridx = 0; gridy = 999; weightx = 1.0; weighty = 1.0; fill = GridBagConstraints.BOTH
        })
        preview.update(rows)
        revalidate(); repaint()
    }

    private fun save() {
        val preset = currentPreset ?: return
        val data = rows.associate { "$currentKey.${it.fieldName}" to it.text }
        runCatching { CardLocService.save(project, currentKey, data, preset) }
            .onFailure { e -> JOptionPane.showMessageDialog(this, "Error: ${e.message}") }
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
        if (CardLocConfigDialog(project, registry).showAndGet()) {
            registry.reload()
            currentPreset?.let { p -> registry.all().find { it.id == p.id }?.let { load(currentKey, emptyMap(), it) } }
        }
    }

    private fun reload() {
        val preset = currentPreset ?: return
        runCatching { CardLocService.load(project, currentKey, preset) }
            .onSuccess { load(currentKey, it, preset) }
    }
}