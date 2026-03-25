package com.lamali.cardloc.editor

import com.intellij.openapi.project.Project
import com.lamali.cardloc.core.CardLocRegistry
import com.lamali.cardloc.core.CardLocService
import com.lamali.cardloc.data.CardLocPreset
import com.lamali.cardloc.editor.ui.UI
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.*

/**
 * The main Orchestrator for the Card Localization Editor.
 * Now Project-aware to support the Service-based Registry.
 */
class CardLocPanel(private val project: Project) : JPanel(BorderLayout()) {

    private var currentKey = ""
    private var currentPreset: CardLocPreset? = null

    // Fetch the project-specific registry
    private val registry = project.getService(CardLocRegistry::class.java)

    private val header = CardLocHeader(::reload, ::promptAddField, ::save)
    private val preview = CardLocPreview()
    private val fieldsPanel = JPanel(GridBagLayout()).apply { background = UI.bg }
    private val rows = mutableListOf<FieldRow>()

    private val scrollFields = JScrollPane(fieldsPanel).apply {
        border = null
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
    }

    private val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollFields, preview).apply {
        isContinuousLayout = true
        border = null
        dividerSize = 8
        setResizeWeight(0.6)
    }

    init {
        background = UI.bg
        add(splitPane, BorderLayout.CENTER)

        // Listen for resize to switch between Side-dock (Vertical) and Bottom-dock (Horizontal)
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                applyAdaptiveLayout()
            }
        })
    }

    private fun applyAdaptiveLayout() {
        val isWide = width > height && width > 600

        // Temporarily remove to re-add in correct position
        remove(header)

        if (isWide) {
            // BOTTOM DOCK: Toolbar on the left, horizontal split
            header.updateOrientation(vertical = true)
            add(header, BorderLayout.WEST)
            if (splitPane.orientation != JSplitPane.HORIZONTAL_SPLIT) {
                splitPane.orientation = JSplitPane.HORIZONTAL_SPLIT
                splitPane.dividerLocation = (width * 0.6).toInt()
            }
        } else {
            // SIDE DOCK: Toolbar on top, vertical split
            header.updateOrientation(vertical = false)
            add(header, BorderLayout.NORTH)
            if (splitPane.orientation != JSplitPane.VERTICAL_SPLIT) {
                splitPane.orientation = JSplitPane.VERTICAL_SPLIT
                splitPane.dividerLocation = (height * 0.6).toInt()
            }
        }

        revalidate()
        repaint()
    }

    /**
     * Call this to populate the editor with data.
     */
    fun load(keyPrefix: String, existing: Map<String, String>, preset: CardLocPreset) {
        this.currentKey = keyPrefix
        this.currentPreset = preset
        header.setKey(keyPrefix)

        fieldsPanel.removeAll()
        rows.clear()

        // Group fields: mandatory ones from preset + any existing custom ones
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
        // Add a spacer at the bottom so rows stay pinned to the top
        fieldsPanel.add(Box.createVerticalGlue(), GridBagConstraints().apply {
            gridx = 0; gridy = 999; weightx = 1.0; weighty = 1.0; fill = GridBagConstraints.BOTH
        })

        preview.update(rows)
        revalidate()
        repaint()
    }

    private fun save() {
        val preset = currentPreset ?: return
        if (!registry.isInitialized()) return

        val data = rows.associate { "$currentKey.${it.fieldName}" to it.text }

        runCatching { CardLocService.save(project, currentKey, data, preset) }
            .onSuccess {
                // Consider a non-blocking toast or status bar message here instead of a dialog
                println("CardLoc: Saved $currentKey")
            }
            .onFailure { e ->
                JOptionPane.showMessageDialog(this, "Error saving: ${e.message}", "Save Error", JOptionPane.ERROR_MESSAGE)
            }
    }

    private fun promptAddField() {
        val preset = currentPreset ?: return
        val existingSuffixes = rows.map { it.fieldName }.toSet()
        val suggestions = preset.fields
            .filter { it.optional && it.name !in existingSuffixes }
            .map { it.name }
            .toTypedArray()

        val comboBox = JComboBox(suggestions).apply { isEditable = true }
        val panel = JPanel(GridLayout(0, 1, 0, 5)).apply {
            add(JLabel("Select or type a new suffix:"))
            add(comboBox)
        }

        val result = JOptionPane.showConfirmDialog(this, panel, "Add Field", JOptionPane.OK_CANCEL_OPTION)

        if (result == JOptionPane.OK_OPTION) {
            val input = comboBox.selectedItem?.toString()?.trim() ?: ""
            if (input.isNotBlank() && rows.none { it.fieldName == input }) {
                addRow(input, "")
                refreshUI()
            }
        }
    }

    private fun reload() {
        val preset = currentPreset ?: return
        if (currentKey.isBlank()) return

        runCatching {
            CardLocService.load(project, currentKey, preset)
        }.onSuccess { existingData ->
            load(currentKey, existingData, preset)
        }.onFailure { e ->
            JOptionPane.showMessageDialog(this, "Reload failed: ${e.message}")
        }
    }
}