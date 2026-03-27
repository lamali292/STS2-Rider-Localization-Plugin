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

    private val markersModel = DefaultListModel<String>()
    private val fieldsTree = FieldMappingTree(project)

    init {
        val topPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 10)).apply {
            add(JBLabel("Preset ID:"))
            add(idField)
        }

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
    }

    fun getCurrentPresetState(): CardLocPreset {
        return CardLocPreset(
            id = idField.text.trim(),
            markers = markersModel.elements().toList(),
            fields = fieldsTree.getFlatFields()
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