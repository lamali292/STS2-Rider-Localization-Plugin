package com.lamali.cardloc.editor

import com.intellij.openapi.project.Project
import com.intellij.ui.JBSplitter
import com.lamali.cardloc.core.CardLocRegistry
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
    private var activeRegistry: CardLocRegistry = project.getService(CardLocRegistry::class.java)

    private val autoSaveTimer = Timer(2000) { performAutoSave() }.apply { isRepeats = false }
    private var lastChangeTime = 0L
    private val autoSaveDelay = 1500L

    // UI Components
    private val header = CardLocHeader(::reload, ::promptAddField, ::save, ::openPresetEditor)
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
        val row = FieldRow(suffix, value) {
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
        val context = currentContext ?: return
        val preset = currentPreset ?: return
        val data = rows.associate { "$currentKey.${it.fieldName}" to it.text }
        runCatching { CardLocService.save(context, data, preset) }
    }

    private fun refreshUI() {
        fieldsPanel.add(Box.createVerticalGlue(), GridBagConstraints().apply {
            gridx = 0; gridy = 999; weightx = 1.0; weighty = 1.0; fill = GridBagConstraints.BOTH
        })
        preview.update(rows)
        revalidate(); repaint()
    }

    private fun save() {
        val context = currentContext ?: return
        val preset = currentPreset ?: return
        val data = rows.associate { "$currentKey.${it.fieldName}" to it.text }
        runCatching { CardLocService.save(context, data, preset) }
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
        val context = currentContext ?: return
        val dialog = CardLocConfigDialog(project, context.configFile)

        if (dialog.showAndGet()) {
            val updatedContext = activeRegistry.loadContext(context.configFile) ?: return

            currentPreset?.let { p ->
                val newPreset = updatedContext.config.presets.find { it.id == p.id }
                    ?: updatedContext.config.presets.firstOrNull()

                if (newPreset != null) {
                    val data = CardLocService.load(updatedContext, currentKey, newPreset)
                    load(updatedContext, currentKey, data, newPreset)
                }
            }
        }
    }

    private fun reload() {
        val context = currentContext ?: return
        val preset = currentPreset ?: return
        runCatching { CardLocService.load(context, currentKey, preset) }
            .onSuccess { load(context, currentKey, it, preset) }
    }
}