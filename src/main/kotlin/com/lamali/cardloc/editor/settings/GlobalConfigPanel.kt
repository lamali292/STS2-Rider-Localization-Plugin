package com.lamali.cardloc.editor.settings

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTextField
import java.awt.*
import java.io.File
import javax.swing.*

class GlobalConfigPanel(private val project: Project) : JPanel(GridBagLayout()) {

    private val projectIdField = createSecureTextField(20)
    private val localizationBaseField = createSecureTextField(30)

    init {
        val gbc = GridBagConstraints().apply {
            insets = Insets(5, 5, 5, 5)
            anchor = GridBagConstraints.WEST
        }

        add(JLabel("Mod ID:"), gbc.apply { gridx = 0; gridy = 0; weightx = 0.0 })
        add(projectIdField, gbc.apply { gridx = 1; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL })

        add(JLabel("Localization Base:"), gbc.apply { gridx = 0; gridy = 1; weightx = 0.0 })
        add(localizationBaseField, gbc.apply { gridx = 1; gridy = 1; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL })

        add(JButton("Browse").apply {
            addActionListener { browse() }
        }, gbc.apply { gridx = 2; gridy = 1; weightx = 0.0 })
    }

    private fun createSecureTextField(columns: Int): JBTextField {
        return JBTextField(columns).apply {
            putClientProperty("ActionMap.LocalEventHandling", true)
        }
    }

    fun setState(id: String, base: String) {
        projectIdField.text = id
        localizationBaseField.text = base
    }

    fun getProjectId(): String = projectIdField.text.trim()
    fun getLocBase(): String = localizationBaseField.text.trim()

    private fun browse() {
        val chooser = JFileChooser(project.basePath).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val relPath = File(project.basePath!!).toPath().relativize(chooser.selectedFile.toPath())
            localizationBaseField.text = relPath.toString().replace("\\", "/")
        }
    }
}