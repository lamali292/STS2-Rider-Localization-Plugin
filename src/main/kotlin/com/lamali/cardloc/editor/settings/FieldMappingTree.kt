package com.lamali.cardloc.editor.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.ui.treeStructure.Tree
import com.lamali.cardloc.data.FieldDef
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
// CRITICAL IMPORTS FOR THE MODEL
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class FieldMappingTree(private val project: Project) : JPanel(BorderLayout()) {

    private val root = DefaultMutableTreeNode("Fields")

    /** * We explicitly use the full path here just in case your IDE is
     * confused by AbstractButton's internal 'model' property.
     */
    private val treeModel: DefaultTreeModel = DefaultTreeModel(root)

    private val tree = Tree(treeModel).apply {
        isRootVisible = false
        showsRootHandles = true
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        cellRenderer = FieldTreeCellHandler()
    }

    init {
        add(JScrollPane(tree), BorderLayout.CENTER)

        val inputMap = tree.getInputMap(WHEN_FOCUSED)
        val actionMap = tree.actionMap

        // Action: Delete
        inputMap.put(KeyStroke.getKeyStroke("DELETE"), "deleteNode")
        actionMap.put("deleteNode", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                removeSelected()
            }
        })

        // Action: Rename (F2)
        inputMap.put(KeyStroke.getKeyStroke("F2"), "renameNode")
        actionMap.put("renameNode", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                (tree.lastSelectedPathComponent as? FieldMutableNode)?.let { renameNode(it) }
            }
        })

        // Action: Toggle Optional (Spacebar)
        inputMap.put(KeyStroke.getKeyStroke("SPACE"), "toggleOptional")
        actionMap.put("toggleOptional", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                val node = tree.lastSelectedPathComponent as? FieldMutableNode
                if (node != null && !node.isGroupNode) {
                    node.updateData(node.field.name, !node.field.optional)
                    treeModel.nodeChanged(node) // This is a DefaultTreeModel method
                }
            }
        })

        tree.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) { if (e.isPopupTrigger) showPopupMenu(e) }
            override fun mouseReleased(e: MouseEvent) { if (e.isPopupTrigger) showPopupMenu(e) }
        })
    }

    private fun showPopupMenu(e: MouseEvent) {
        val path = tree.getPathForLocation(e.x, e.y)
        val menu = JPopupMenu()

        if (path == null) {
            menu.add(JMenuItem("Add New File Group", AllIcons.General.Add).apply {
                addActionListener { addNewFileGroup() }
            })
        } else {
            tree.selectionPath = path
            val node = path.lastPathComponent as? FieldMutableNode ?: return

            menu.add(JMenuItem("Rename", AllIcons.Actions.Edit).apply {
                addActionListener { renameNode(node) }
            })

            if (!node.isGroupNode) {
                menu.add(JCheckBoxMenuItem("Optional", node.field.optional).apply {
                    addActionListener {
                        node.updateData(node.field.name, isSelected)
                        treeModel.nodeChanged(node)
                    }
                })
            }

            menu.addSeparator()

            if (node.isGroupNode) {
                menu.add(JMenuItem("Add Field", AllIcons.General.Add).apply {
                    addActionListener { addNewFieldToSelected() }
                })
                menu.addSeparator()
            }

            menu.add(JMenuItem("Delete", AllIcons.Actions.GC).apply {
                addActionListener { removeSelected() }
            })
        }
        menu.show(tree, e.x, e.y)
    }

    private fun renameNode(node: FieldMutableNode) {
        val currentName = if (node.isGroupNode) node.field.file else node.field.name
        val typeName = if (node.isGroupNode) "File" else "Field"

        val newName = JOptionPane.showInputDialog(
            this, "Enter new $typeName name:", "Rename $typeName",
            JOptionPane.PLAIN_MESSAGE, null, null, currentName
        ) as? String

        if (!newName.isNullOrBlank() && newName != currentName) {
            node.updateData(newName, node.field.optional)
            treeModel.nodeChanged(node)
        }
    }

    private fun addNewFileGroup() {
        val fileName = JOptionPane.showInputDialog(this, "JSON filename (e.g. ui.json):")
        if (!fileName.isNullOrBlank()) {
            val newNode = FieldMutableNode(FieldDef("", fileName, false), isGroupNode = true)
            treeModel.insertNodeInto(newNode, root, root.childCount)
            tree.scrollPathToVisible(TreePath(newNode.path))
        }
    }

    private fun addNewFieldToSelected() {
        val selectedNode = tree.lastSelectedPathComponent as? FieldMutableNode ?: return
        val targetFileNode = if (selectedNode.isGroupNode) selectedNode else selectedNode.parent as? FieldMutableNode

        if (targetFileNode != null && targetFileNode.isGroupNode) {
            val fieldName = JOptionPane.showInputDialog(this, "Field name:")
            if (!fieldName.isNullOrBlank()) {
                val newNode = FieldMutableNode(FieldDef(fieldName, targetFileNode.field.file, false))
                treeModel.insertNodeInto(newNode, targetFileNode, targetFileNode.childCount)
                tree.expandPath(TreePath(targetFileNode.path))
                tree.scrollPathToVisible(TreePath(newNode.path))
            }
        }
    }

    private fun removeSelected() {
        val selectedNode = tree.lastSelectedPathComponent as? FieldMutableNode ?: return
        val nodeName = if (selectedNode.isGroupNode) selectedNode.field.file else selectedNode.field.name

        val confirm = JOptionPane.showConfirmDialog(
            this, "Delete '$nodeName'?", "Confirm Delete",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
        )
        if (confirm == JOptionPane.YES_OPTION) {
            treeModel.removeNodeFromParent(selectedNode)
        }
    }

    fun loadFields(fields: List<FieldDef>) {
        root.removeAllChildren()
        fields.groupBy { it.file }.forEach { (fileName, fileFields) ->
            val fileNode = FieldMutableNode(FieldDef("", fileName, false), isGroupNode = true)
            root.add(fileNode)
            fileFields.forEach { fileNode.add(FieldMutableNode(it)) }
        }
        treeModel.reload()
    }

    fun getFlatFields(): List<FieldDef> {
        val result = mutableListOf<FieldDef>()
        for (i in 0 until root.childCount) {
            val fileNode = root.getChildAt(i) as? FieldMutableNode ?: continue
            for (j in 0 until fileNode.childCount) {
                val fieldNode = fileNode.getChildAt(j) as? FieldMutableNode ?: continue
                result.add(fieldNode.field.copy(file = fileNode.field.file))
            }
        }
        return result
    }
}