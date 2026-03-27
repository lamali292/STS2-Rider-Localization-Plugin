package com.lamali.cardloc.editor.settings

import com.lamali.cardloc.data.CardLocPreset
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

class PresetSidebarPanel(
    private val model: DefaultListModel<CardLocPreset>,
    private val onSelectionChanged: (CardLocPreset?, Int) -> Unit,
    private val onCreate: () -> Unit,
    private val onDuplicate: (CardLocPreset) -> Unit,
    private val onDelete: (Int) -> Unit
) : JPanel(BorderLayout()) {

    private val list = JList(model).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(l: JList<*>?, v: Any?, i: Int, s: Boolean, f: Boolean): Component {
                val p = v as? CardLocPreset
                text = p?.let { "${it.id} (${it.markers.size} markers)" } ?: ""
                return super.getListCellRendererComponent(l, text, i, s, f)
            }
        }

        // Selection Logic
        addListSelectionListener {
            if (!it.valueIsAdjusting) {
                // Pass BOTH the value and the index
                onSelectionChanged(selectedValue, selectedIndex)
            }
        }

        // Context Menu Logic
        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) { if (e.isPopupTrigger) showMenu(e) }
            override fun mouseReleased(e: MouseEvent) { if (e.isPopupTrigger) showMenu(e) }
        })
    }

    init {
        border = BorderFactory.createTitledBorder("Presets")
        add(JScrollPane(list), BorderLayout.CENTER)
    }

    private fun showMenu(e: MouseEvent) {
        val index = list.locationToIndex(e.point)
        val isClickOnItem = index != -1 && list.getCellBounds(index, index)?.contains(e.point) == true

        val menu = JPopupMenu()

        val newItem = JMenuItem("New Preset").apply {
            addActionListener { onCreate() }
        }
        if (isClickOnItem) {
            // Right-click on a specific Preset
            list.selectedIndex = index // Force selection of the right-clicked item

            val duplicateItem = JMenuItem("Duplicate").apply {
                addActionListener { list.selectedValue?.let { onDuplicate(it) } }
            }
            val deleteItem = JMenuItem("Delete").apply {
                addActionListener { onDelete(list.selectedIndex) }
            }

            menu.add(duplicateItem)
            menu.add(deleteItem)
            menu.addSeparator()
        }
        menu.add(newItem)
        menu.show(list, e.x, e.y)
    }

    fun getSelectedIndex() = list.selectedIndex
    fun setSelectedIndex(i: Int) { list.selectedIndex = i }
}