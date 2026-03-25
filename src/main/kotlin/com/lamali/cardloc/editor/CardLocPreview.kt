package com.lamali.cardloc.editor

import com.lamali.cardloc.editor.ui.UI
import java.awt.BorderLayout
import java.awt.Font
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

class CardLocPreview : JPanel(BorderLayout()) {
    private val textArea = JTextArea().apply {
        isEditable = false
        background = UI.inputBg
        foreground = UI.subtleText
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        margin = Insets(10, 10, 10, 10)
        lineWrap = true
        wrapStyleWord = true
    }

    init {
        background = UI.bgAlt
        border = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(UI.border), "LIVE JSON PREVIEW",
            0, 0, null, UI.subtleText
        )
        add(JScrollPane(textArea).apply { border = null }, BorderLayout.CENTER)
    }

    fun update(rows: List<FieldRow>) {
        val sb = StringBuilder()
        rows.forEach { row ->
            sb.append("${row.fieldName.uppercase()}:\n")
            sb.append("${row.text.replace("\n", "\\n")}\n\n")
        }
        textArea.text = sb.toString()
    }
}