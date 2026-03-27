package com.lamali.cardloc.editor.settings

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*
import javax.swing.tree.TreeCellRenderer

class FieldTreeCellHandler : JPanel(BorderLayout(10, 0)), TreeCellRenderer {

    private val label = JLabel()
    private val statusLabel = JLabel()

    init {
        isOpaque = false
        add(label, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.EAST)
        statusLabel.border = JBUI.Borders.emptyRight(10)
    }

    override fun getTreeCellRendererComponent(
        tree: JTree, value: Any?, selected: Boolean,
        expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean
    ): Component {
        val node = value as? FieldMutableNode ?: return JLabel()
        val f = node.field

        val foreground = if (selected)
            JBColor.namedColor("Tree.selectionForeground", Color.WHITE)
        else
            JBColor.namedColor("Tree.foreground", Color.BLACK)

        label.iconTextGap = 5
        if (node.isGroupNode) {
            label.text = f.file
            label.icon = AllIcons.Nodes.PpLibFolder
            label.font = tree.font.deriveFont(Font.BOLD)
            // Use blueish color for files when not selected
            label.foreground = if (selected) foreground else JBColor(Color(0, 100, 200), Color(88, 157, 246))
            statusLabel.text = ""
        } else {
            label.text = f.name
            label.icon = AllIcons.Nodes.Property
            label.font = tree.font
            label.foreground = foreground

            if (f.optional) {
                statusLabel.text = "optional"
                statusLabel.font = tree.font.deriveFont(Font.ITALIC, 11f)
                statusLabel.foreground = if (selected) foreground else JBColor.GRAY
            } else {
                statusLabel.text = ""
            }
        }

        isOpaque = selected
        if (selected) {
            background = JBColor.namedColor("Tree.selectionBackground", Color(13, 41, 62))
        }

        return this
    }
}