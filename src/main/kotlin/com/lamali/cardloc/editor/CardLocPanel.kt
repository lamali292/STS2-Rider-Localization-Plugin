package com.lamali.cardloc.editor

import com.lamali.cardloc.data.CardLocPreset
import com.lamali.cardloc.core.CardLocService
import com.lamali.cardloc.editor.ui.UI
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.*

/**
 * The main Orchestrator for the Card Localization Editor.
 * Adapts its layout based on whether it is docked at the bottom or the side.
 */
class CardLocPanel : JPanel(BorderLayout()) {
    private var projectRef: Any? = null
    private var currentKey = ""
    private var currentPreset: CardLocPreset? = null

    private val header = CardLocHeader(::reload, ::promptAddField, ::save)
    private val preview = CardLocPreview()
    private val fieldsPanel = JPanel(GridBagLayout()).apply { background = UI.bg }
    private val rows = mutableListOf<FieldRow>()

    private val scrollFields = JScrollPane(fieldsPanel).apply {
        border = null
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }

    private val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollFields, preview).apply {
        isContinuousLayout = true
        border = null
        dividerSize = 8
        setResizeWeight(0.7)
    }

    init {
        background = UI.bg
        add(splitPane, BorderLayout.CENTER)
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                applyAdaptiveLayout()
            }
        })
    }

    private fun applyAdaptiveLayout() {
        val isWide = width > height && width > 600
        remove(header)

        if (isWide) {
            // BOTTOM DOCK: Toolbar on the left, everything else left-to-right
            header.updateOrientation(vertical = true)
            add(header, BorderLayout.WEST)
            splitPane.orientation = JSplitPane.HORIZONTAL_SPLIT
            if (splitPane.dividerLocation < 100 || splitPane.dividerLocation > width - 100) {
                splitPane.dividerLocation = (width * 0.7).toInt()
            }
        } else {
            // SIDE DOCK: Toolbar on top, everything else top-to-bottom
            header.updateOrientation(vertical = false)
            add(header, BorderLayout.NORTH)
            splitPane.orientation = JSplitPane.VERTICAL_SPLIT
            if (splitPane.dividerLocation < 100 || splitPane.dividerLocation > height - 100) {
                splitPane.dividerLocation = (height * 0.7).toInt()
            }
        }

        revalidate()
        repaint()
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
            gridx = 0
            gridy = rows.size
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.NORTH
            insets = Insets(0, 0, 2, 0)
        }
        fieldsPanel.add(row, gbc)
    }

    private fun refreshUI() {
        val glueGbc = GridBagConstraints().apply {
            gridx = 0
            gridy = 999
            weightx = 1.0
            weighty = 1.0
            fill = GridBagConstraints.BOTH
        }
        fieldsPanel.add(Box.createVerticalGlue(), glueGbc)

        preview.update(rows)
        revalidate()
        repaint()
    }

    private fun save() {
        val project = projectRef ?: return
        val preset = currentPreset ?: return
        val data = rows.associate { "$currentKey.${it.fieldName}" to it.text }

        runCatching { CardLocService.save(project, currentKey, data, preset) }
            .onSuccess {
                JOptionPane.showMessageDialog(this, "Saved successfully!")
            }
            .onFailure { e ->
                JOptionPane.showMessageDialog(this, "Error saving: ${e.message}")
            }
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