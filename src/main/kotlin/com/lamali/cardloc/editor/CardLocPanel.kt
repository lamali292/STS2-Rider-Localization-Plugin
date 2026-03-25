package com.lamali.cardloc.editor

import com.lamali.cardloc.data.CardLocPreset
import com.lamali.cardloc.core.CardLocService
import com.lamali.cardloc.editor.ui.UI
import java.awt.*
import javax.swing.*


class CardLocPanel : JPanel(BorderLayout()) {
    private var projectRef: Any? = null
    private var currentKey = ""
    private var currentPreset: CardLocPreset? = null

    private val header = CardLocHeader(::reload, ::promptAddField, ::save)
    private val preview = CardLocPreview()
    private val fieldsPanel = JPanel(GridBagLayout()).apply { background = UI.bg }
    private val rows = mutableListOf<FieldRow>()

    init {
        background = UI.bg
        add(header, BorderLayout.NORTH)

        val scrollFields = JScrollPane(fieldsPanel).apply {
            border = null
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }

        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollFields, preview).apply {
            isContinuousLayout = true
            border = null
            dividerSize = 8
            setResizeWeight(0.7)
            dividerLocation = 400
        }
        add(splitPane, BorderLayout.CENTER)
    }

    fun load(project: Any?, keyPrefix: String, existing: Map<String, String>, preset: CardLocPreset) {
        this.projectRef = project
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
        val row = FieldRow(suffix, value) { preview.update(rows) }
        row.connectToolbar(header.toolbar)
        rows.add(row)

        val gbc = GridBagConstraints().apply {
            gridx = 0; gridy = rows.size; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; anchor = GridBagConstraints.NORTH
        }
        fieldsPanel.add(row, gbc)
    }

    private fun refreshUI() {
        val glueGbc = GridBagConstraints().apply { gridx = 0; gridy = 999; weighty = 1.0; fill = GridBagConstraints.BOTH }
        fieldsPanel.add(Box.createVerticalGlue(), glueGbc)

        preview.update(rows)
        revalidate(); repaint()
    }

    private fun save() {
        val project = projectRef ?: return
        val preset = currentPreset ?: return
        val data = rows.associate { "$currentKey.${it.fieldName}" to it.text }
        runCatching { CardLocService.save(project, currentKey, data, preset) }
            .onSuccess { JOptionPane.showMessageDialog(this, "Saved!") }
    }

    private fun promptAddField() {
        val preset = currentPreset ?: return
        val existingSuffixes = rows.map { it.fieldName }.toSet()
        val suggestions = preset.fields
            .filter { it.optional && it.name !in existingSuffixes }
            .map { it.name }
            .toTypedArray()

        val comboBox = JComboBox(suggestions).apply {
            isEditable = true
            selectedIndex = if (suggestions.isNotEmpty()) 0 else -1
        }

        val panel = JPanel(GridLayout(0, 1, 0, 5)).apply {
            add(JLabel("Select or type a new suffix:"))
            add(comboBox)
        }

        val result = JOptionPane.showConfirmDialog(
            this, panel, "Add Optional Field",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        )

        if (result == JOptionPane.OK_OPTION) {
            val input = comboBox.selectedItem?.toString()?.trim()
            if (!input.isNullOrBlank()) {
                if (rows.any { it.fieldName == input }) {
                    JOptionPane.showMessageDialog(this, "Field '$input' already exists.")
                    return
                }
                addRow(input, "")
                refreshUI()
            }
        }
    }

    private fun reload() {
        val project = projectRef ?: run {
            JOptionPane.showMessageDialog(this, "No project active — cannot reload.")
            return
        }
        val preset = currentPreset ?: return
        if (currentKey.isBlank()) return

        runCatching {
            CardLocService.load(project, currentKey, preset)
        }.onSuccess { existingData ->
            load(project, currentKey, existingData, preset)
        }.onFailure { e ->
            JOptionPane.showMessageDialog(this, "Failed to reload: ${e.message}")
        }
    }

}
