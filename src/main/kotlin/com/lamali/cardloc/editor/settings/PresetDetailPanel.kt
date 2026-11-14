package com.lamali.cardloc.editor.settings

import com.intellij.openapi.project.Project
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.lamali.cardloc.data.CardLocPreset
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

class PresetDetailPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val idField = JBTextField(20).apply {
        putClientProperty("ActionMap.LocalEventHandling", true)
    }

    private val inheritBox = JCheckBox("Inherit from project default").apply {
        toolTipText = "Build on the project-wide preset with this ID: below, list only what this folder adds"
    }
    private val inheritInfo = JBLabel().apply { foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND }

    /** Supplies the project-default presets (set by the dialog). */
    var defaultsLookup: () -> List<CardLocPreset> = { emptyList() }

    /** True while the project defaults themselves are edited: no inheritance there. */
    var defaultsMode: Boolean = false
        set(value) {
            field = value
            inheritBox.isVisible = !value
            inheritInfo.isVisible = !value
        }

    private val markersModel = DefaultListModel<String>()
    private val fieldsTree = FieldMappingTree(project)

    init {
        val topPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 10)).apply {
            add(JBLabel("Preset ID:"))
            add(idField)
            add(inheritBox)
            add(inheritInfo)
        }
        inheritBox.addActionListener { toggleInherit() }

        val splitter = JBSplitter(true, 0.3f).apply {
            firstComponent = createMarkersSection()
            secondComponent = createFieldsSection()
            dividerWidth = 3 // Single pixel divider
            // No more setResizeWeight needed!
        }

        add(topPanel, BorderLayout.NORTH)
        add(splitter, BorderLayout.CENTER)
        border = JBUI.Borders.empty(5)
    }

    fun loadPreset(preset: CardLocPreset?) {
        idField.text = preset?.id ?: ""
        idField.isEnabled = preset != null

        markersModel.clear()
        preset?.markers?.forEach { markersModel.addElement(it) }

        fieldsTree.loadFields(preset?.fields ?: emptyList())

        inheritBox.isSelected = preset?.base != null
        inheritBox.isEnabled = preset != null
        updateInheritInfo()
    }

    /** Switching on keeps only this folder's additions; switching off copies the inherited parts in. */
    private fun toggleInherit() {
        val cur = getCurrentPresetState()
        val d = defaultsLookup().firstOrNull { it.id == cur.id }
        if (d == null) {
            updateInheritInfo()
            return
        }
        val next = if (inheritBox.isSelected) {
            cur.copy(
                markers = cur.markers - d.markers.toSet(),
                fields = cur.fields.filterNot { f -> d.fields.any { it.file == f.file && it.name == f.name } },
                base = d.id
            )
        } else {
            cur.copy(base = d.id).resolve(defaultsLookup()).copy(base = null)
        }
        loadPreset(next)
    }

    private fun updateInheritInfo() {
        val id = idField.text.trim()
        val d = defaultsLookup().firstOrNull { it.id == id }
        inheritInfo.text = when {
            !inheritBox.isSelected -> ""
            d == null -> "No project default named '$id' - nothing is inherited"
            else -> "Inherits ${d.markers.size} marker(s) and ${d.fields.size} field(s); the lists below are additions only"
        }
        inheritInfo.toolTipText = if (inheritBox.isSelected && d != null) {
            "<html>Inherited markers: ${d.markers.joinToString(", ")}<br>" +
                d.fields.groupBy { it.file }.entries.joinToString("<br>") { (file, fs) ->
                    "$file: " + fs.joinToString(", ") { it.name + if (it.optional) "?" else "" }
                } + "</html>"
        } else null
    }

    fun getCurrentPresetState(): CardLocPreset {
        return CardLocPreset(
            id = idField.text.trim(),
            markers = markersModel.elements().toList(),
            fields = fieldsTree.getFlatFields(),
            base = if (inheritBox.isSelected && !defaultsMode) idField.text.trim() else null
        )
    }

    private fun createMarkersSection(): JPanel {
        val panel = JPanel(BorderLayout())

        val header = JBLabel("Markers").apply {
            font = font.deriveFont(Font.BOLD)
            border = JBUI.Borders.empty(2, 0, 5, 0)
        }

        val markerList = JList(markersModel)
        markerList.selectionMode = ListSelectionModel.SINGLE_SELECTION

        markerList.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) { if (e.isPopupTrigger) showMarkerMenu(e, markerList) }
            override fun mouseReleased(e: MouseEvent) { if (e.isPopupTrigger) showMarkerMenu(e, markerList) }
        })

        markerList.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DELETE"), "deleteMarker")
        markerList.actionMap.put("deleteMarker", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (markerList.selectedIndex != -1) {
                    markersModel.remove(markerList.selectedIndex)
                }
            }
        })

        panel.add(header, BorderLayout.NORTH)
        panel.add(JScrollPane(markerList), BorderLayout.CENTER)

        return panel
    }

    private fun showMarkerMenu(e: MouseEvent, list: JList<String>) {
        val index = list.locationToIndex(e.point)
        val isClickOnItem = index != -1 && list.getCellBounds(index, index)?.contains(e.point) == true
        val menu = JPopupMenu()

        menu.add(JMenuItem("Add Marker").apply {
            addActionListener {
                val m = JOptionPane.showInputDialog(this@PresetDetailPanel, "Marker class:")
                if (!m.isNullOrBlank()) {
                    markersModel.addElement(m)
                }
            }
        })

        if (isClickOnItem) {
            list.selectedIndex = index
            menu.addSeparator()
            menu.add(JMenuItem("Remove Marker").apply {
                addActionListener { markersModel.remove(index) }
            })
        }
        menu.show(list, e.x, e.y)
    }

    private fun createFieldsSection() = JPanel(BorderLayout()).apply {
        border = BorderFactory.createTitledBorder("Fields")
        add(fieldsTree, BorderLayout.CENTER)
    }
}