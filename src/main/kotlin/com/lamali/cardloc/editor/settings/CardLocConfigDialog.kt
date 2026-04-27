package com.lamali.cardloc.editor.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.lamali.cardloc.data.CardLocConfig
import com.lamali.cardloc.data.CardLocPreset
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.ui.JBSplitter
import java.awt.*
import java.io.File
import javax.swing.*

class CardLocConfigDialog(
    project: Project,
    private val initialConfigFile: File
) : DialogWrapper(project) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val presetListModel = DefaultListModel<CardLocPreset>()

    private val settingsPanel = GlobalConfigPanel(project)
    private var selectorPanel: PresetSidebarPanel
    private val editorPanel = PresetDetailPanel(project)

    private var lastSelectedIndex: Int = -1
    private var isSaving = false

    init {
        title = "CardLoc Configuration - ${initialConfigFile.parentFile.name}"

        selectorPanel = PresetSidebarPanel(
            model = presetListModel,
            onSelectionChanged = { preset, newIndex -> handleSelectionChange(preset, newIndex) },
            onCreate = { createNewPreset() },
            onDuplicate = { preset -> duplicatePreset(preset) },
            onDelete = { index -> deletePreset(index) }
        )

        loadCurrentConfig() // This now uses initialConfigFile
        init()

        if (!presetListModel.isEmpty) {
            selectorPanel.setSelectedIndex(0)
        }
    }

    private fun handleSelectionChange(newPreset: CardLocPreset?, newIndex: Int) {
        if (isSaving) return
        if (lastSelectedIndex >= 0 && lastSelectedIndex < presetListModel.size()) {
            try {
                isSaving = true
                val updated = editorPanel.getCurrentPresetState()
                presetListModel.setElementAt(updated, lastSelectedIndex)
            } finally {
                isSaving = false
            }
        }

        lastSelectedIndex = newIndex
        editorPanel.loadPreset(newPreset)
    }

    private fun saveCurrentEditorStateToList() {
        if (isSaving) return
        if (lastSelectedIndex >= 0 && lastSelectedIndex < presetListModel.size()) {
            try {
                isSaving = true
                val updated = editorPanel.getCurrentPresetState()
                presetListModel.setElementAt(updated, lastSelectedIndex)
            } finally {
                isSaving = false
            }
        }
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout(0, 10)).apply {
            preferredSize = JBUI.size(1100, 800)
            border = JBUI.Borders.empty(10)
        }

        val topSection = JPanel(BorderLayout()).apply {
            val titleLabel = JBLabel("Project settings").apply {
                font = font.deriveFont(Font.BOLD, 14f)
                border = JBUI.Borders.emptyBottom(5)
            }
            add(titleLabel, BorderLayout.NORTH)
            add(settingsPanel, BorderLayout.CENTER)
            add(JSeparator().apply { border = JBUI.Borders.empty(10, 0) }, BorderLayout.SOUTH)
        }

        val presetSection = JPanel(BorderLayout()).apply {
            val titleLabel = JBLabel("Preset management").apply {
                font = font.deriveFont(Font.BOLD, 14f)
                border = JBUI.Borders.emptyBottom(5)
            }

            val splitSection = JBSplitter(false, 0.25f).apply {
                firstComponent = selectorPanel
                secondComponent = editorPanel
                dividerWidth = 3
                border = JBUI.Borders.empty(5, 0)
            }

            add(titleLabel, BorderLayout.NORTH)
            add(splitSection, BorderLayout.CENTER)
        }

        mainPanel.add(topSection, BorderLayout.NORTH)
        mainPanel.add(presetSection, BorderLayout.CENTER)

        return mainPanel
    }

    override fun doOKAction() {
        saveCurrentEditorStateToList()
        try {
            val config = CardLocConfig(
                projectId = settingsPanel.getProjectId(),
                localizationBase = settingsPanel.getLocBase(),
                presets = (0 until presetListModel.size()).map { presetListModel.getElementAt(it) }
            )
            initialConfigFile.writeText(gson.toJson(config))
            super.doOKAction()
        } catch (e: Exception) {
            showError("Save failed: ${e.message}")
        }
    }

    private fun loadCurrentConfig() {
        if (initialConfigFile.exists()) {
            try {
                val json = initialConfigFile.readText().trimStart('\uFEFF')
                val config = gson.fromJson(json, CardLocConfig::class.java)
                settingsPanel.setState(config.projectId, config.localizationBase)
                presetListModel.clear()
                config.presets.forEach { presetListModel.addElement(it) }
            } catch (e: Exception) {
                showError("Error loading config: ${e.message}")
            }
        }
    }

    private fun createNewPreset() {
        val newPreset = CardLocPreset("new_preset", emptyList(), emptyList())
        presetListModel.addElement(newPreset)
        selectorPanel.setSelectedIndex(presetListModel.size() - 1)
    }

    private fun duplicatePreset(preset: CardLocPreset) {
        val copy = preset.copy(id = "${preset.id}_copy")
        presetListModel.addElement(copy)
        selectorPanel.setSelectedIndex(presetListModel.size() - 1)
    }

    private fun deletePreset(index: Int) {
        if (index >= 0 && JOptionPane.showConfirmDialog(contentPanel, "Delete preset?", "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            if (index == lastSelectedIndex) {
                lastSelectedIndex = -1
                editorPanel.loadPreset(null)
            } else if (index < lastSelectedIndex) {
                lastSelectedIndex--
            }
            presetListModel.remove(index)
        }
    }

    private fun showError(msg: String) = JOptionPane.showMessageDialog(contentPanel, msg, "Error", JOptionPane.ERROR_MESSAGE)
}