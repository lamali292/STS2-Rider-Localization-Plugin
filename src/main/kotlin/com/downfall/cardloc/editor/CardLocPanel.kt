package com.downfall.cardloc.editor

import com.downfall.cardloc.data.CardLocPreset
import com.downfall.cardloc.CardLocService
import com.downfall.cardloc.FieldRow
import java.awt.*
import javax.swing.*

class CardLocPanel : JPanel(BorderLayout()) {

    private var projectRef: Any? = null
    private var currentKey = ""
    private var currentPreset: CardLocPreset? = null
    private val globalToolbar = TagToolbar()

    private val fieldsPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = UI.bg
        alignmentX = LEFT_ALIGNMENT
    }

    private val previewArea = JTextArea().apply {
        isEditable = false
        background = UI.inputBg
        foreground = UI.subtleText
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        margin = Insets(10, 10, 10, 10)
        lineWrap = true
        wrapStyleWord = true
    }

    private val rows = mutableListOf<FieldRow>()
    private val keyLabel = JLabel("No card selected").apply {
        foreground = UI.text
        font = font.deriveFont(Font.BOLD, 13f)
    }

    init {
        background = UI.bg
        val header = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = UI.bgAlt
            add(buildTopBar())
            add(globalToolbar)
            add(JSeparator().apply { maximumSize = Dimension(Int.MAX_VALUE, 1) })
        }
        add(header, BorderLayout.NORTH)
        val mainContent = JPanel(BorderLayout()).apply {
            background = UI.bg
            add(JScrollPane(fieldsPanel).apply { border = null }, BorderLayout.CENTER)

            val footer = JPanel(BorderLayout()).apply {
                background = UI.bgAlt
                border = BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(UI.border), "LIVE JSON PREVIEW",
                    0, 0, null, UI.subtleText
                )
                preferredSize = Dimension(Int.MAX_VALUE, 220)
                add(JScrollPane(previewArea).apply { border = null }, BorderLayout.CENTER)
            }
            add(footer, BorderLayout.SOUTH)
        }
        add(mainContent, BorderLayout.CENTER)
    }

    private fun buildTopBar() = JPanel(BorderLayout()).apply {
        background = UI.bgAlt
        border = BorderFactory.createEmptyBorder(8, 10, 8, 10)
        add(keyLabel, BorderLayout.CENTER)
        add(JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0)).apply {
            background = UI.bgAlt
            add(JButton("\uD83D\uDD04").apply { addActionListener { reload() } })
            add(JButton("+").apply { addActionListener { promptAddField() } })
            add(JButton("\uD83D\uDCBE").apply { addActionListener { save() } })
        }, BorderLayout.EAST)
    }

    private fun updateGlobalPreview() {
        val sb = StringBuilder()
        rows.forEach { row ->
            sb.append("${row.fieldName.uppercase()}:\n")
            val visualText = row.text.replace("\n", "\\n")
            sb.append("$visualText\n\n")
        }
        previewArea.text = sb.toString()
    }

    fun load(project: Any?, keyPrefix: String, existing: Map<String, String>, preset: CardLocPreset) {
        this.projectRef = project
        this.currentKey = keyPrefix
        this.currentPreset = preset
        keyLabel.text = keyPrefix

        fieldsPanel.removeAll()
        rows.clear()

        // Required fields first, then any optional ones already present in existing
        val requiredFields = preset.fields.filter { !it.optional }.map { it.name }
        val optionalPresent = existing.keys
            .map { it.removePrefix("$keyPrefix.") }
            .filter { suffix -> preset.fields.any { it.name == suffix && it.optional } }
        val keys = (requiredFields + optionalPresent).distinct()

        keys.forEach { suffix ->
            val row = FieldRow(suffix, existing["$keyPrefix.$suffix"] ?: "") {
                updateGlobalPreview()
            }
            row.connectToolbar(globalToolbar)
            rows.add(row)
            fieldsPanel.add(row)
        }

        updateGlobalPreview()
        revalidate()
        repaint()
    }

    private fun promptAddField() {
        val preset = currentPreset
        val existingSuffixes = rows.map { it.fieldName }

        // Suggest optional fields from the preset that aren't already shown
        val suggestions = preset?.fields
            ?.filter { it.optional && it.name !in existingSuffixes }
            ?.map { it.name }
            ?.toTypedArray()
            ?: emptyArray()

        val comboBox = JComboBox(suggestions).apply {
            isEditable = true
            selectedIndex = if (suggestions.isNotEmpty()) 0 else -1
        }

        val panel = JPanel(GridLayout(0, 1))
        panel.add(JLabel("Select or type a new suffix:"))
        panel.add(comboBox)

        val result = JOptionPane.showConfirmDialog(
            this, panel, "Add New Field",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        )

        if (result == JOptionPane.OK_OPTION) {
            val input = comboBox.selectedItem?.toString()?.trim()
            if (!input.isNullOrBlank()) {
                if (rows.any { it.fieldName == input }) {
                    JOptionPane.showMessageDialog(this, "Field '$input' already exists.")
                    return
                }
                val row = FieldRow(input, "") { updateGlobalPreview() }
                row.connectToolbar(globalToolbar)
                rows.add(row)
                fieldsPanel.add(row)
                updateGlobalPreview()
                revalidate()
                repaint()
            }
        }
    }

    private fun reload() {
        val project = projectRef ?: run {
            JOptionPane.showMessageDialog(this, "No project — running in preview mode.")
            return
        }
        val preset = currentPreset ?: return
        if (currentKey.isBlank()) return

        val existing = CardLocService.load(project, currentKey, preset)
        load(project, currentKey, existing, preset)
    }

    private fun save() {
        val project = projectRef ?: run {
            JOptionPane.showMessageDialog(this, "No project — running in preview mode.")
            return
        }
        val preset = currentPreset ?: return
        if (currentKey.isBlank()) return

        val data = rows.associate { "$currentKey.${it.fieldName}" to it.text }

        runCatching { CardLocService.save(project, currentKey, data, preset) }
            .onSuccess { JOptionPane.showMessageDialog(this, "Saved successfully!") }
            .onFailure {
                it.printStackTrace()
                JOptionPane.showMessageDialog(this, "Error saving: ${it.message}")
            }
    }
}