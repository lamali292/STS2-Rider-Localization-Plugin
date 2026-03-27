package com.lamali.cardloc.editor.settings

import com.lamali.cardloc.data.FieldDef
import javax.swing.tree.DefaultMutableTreeNode

/**
 * A specialized Tree Node that holds a FieldDef and manages its data.
 * @param isGroupNode If true, this node represents a JSON file (the "folder").
 */
class FieldMutableNode(
    var field: FieldDef,
    val isGroupNode: Boolean = false
) : DefaultMutableTreeNode(if (isGroupNode) field.file else field.name) {

    /**
     * Updates the underlying FieldDef. If this is a group node,
     * it renames the file for all its children as well.
     */
    fun updateData(newName: String, newOptional: Boolean) {
        field = field.copy(
            name = if (isGroupNode) field.name else newName, // Don't rename field if group
            file = if (isGroupNode) newName else field.file, // Rename file if group
            optional = newOptional
        )
        // Refresh the node's display text
        userObject = if (isGroupNode) field.file else field.name
    }
}