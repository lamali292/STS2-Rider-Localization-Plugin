package com.lamali.cardloc.editor.settings

import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBTextField
import com.lamali.cardloc.core.CardLocService
import java.awt.*
import java.io.File
import javax.swing.*

/** Key prefix and localization base of the folder currently selected in the dialog. */
class GlobalConfigPanel : JPanel(GridBagLayout()) {

    /** The localization base is resolved against this folder (only used by Browse). */
    var baseDir: File = File(".")

    /** Where Detect searches for localization folders. */
    var projectRoot: File = File(".")

    private val keyPrefixField = createSecureTextField(20).apply {
        toolTipText = "Prefix of every key, e.g. DOWNFALL- (a trailing - is added if missing)"
    }
    private val localizationBaseField = createSecureTextField(30)

    init {
        val gbc = GridBagConstraints().apply {
            insets = Insets(5, 5, 5, 5)
            anchor = GridBagConstraints.WEST
        }

        add(JLabel("Key prefix:"), gbc.apply { gridx = 0; gridy = 0; weightx = 0.0 })
        add(keyPrefixField, gbc.apply { gridx = 1; gridy = 0; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL })

        add(JLabel("Localization Base:"), gbc.apply { gridx = 0; gridy = 1; weightx = 0.0 })
        add(localizationBaseField, gbc.apply { gridx = 1; gridy = 1; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL })

        add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(JButton("Browse").apply {
                toolTipText = "Pick the localization folder (or a language folder inside it)"
                addActionListener { browseLocalization() }
            })
            add(JButton("Detect").apply {
                toolTipText = "Find localization/<language> folders in the project"
                addActionListener { detectLocalization() }
            })
        }, gbc.apply { gridx = 2; gridy = 1; weightx = 0.0; fill = GridBagConstraints.NONE })
    }

    private fun createSecureTextField(columns: Int): JBTextField {
        return JBTextField(columns).apply {
            putClientProperty("ActionMap.LocalEventHandling", true)
        }
    }

    /**
     * In folder mode a blank value falls back to the project default; in defaults mode the
     * values apply to every folder that sets none of its own.
     */
    fun setFolderMode(folderMode: Boolean) {
        val hint = if (folderMode) "(project default)" else "(optional)"
        keyPrefixField.emptyText.text = hint
        localizationBaseField.emptyText.text = hint
    }

    fun setState(prefix: String, base: String) {
        keyPrefixField.text = prefix
        localizationBaseField.text = base
    }

    fun getKeyPrefix(): String = keyPrefixField.text.trim()
    fun getLocBase(): String = localizationBaseField.text.trim()

    private fun browseLocalization() {
        val chooser = JFileChooser(baseDir).apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            setBaseFrom(CardLocService.primaryLanguageDir(chooser.selectedFile))
        }
    }

    private fun setBaseFrom(dir: File) {
        val relPath = baseDir.canonicalFile.toPath().relativize(dir.canonicalFile.toPath())
        localizationBaseField.text = relPath.toString().replace("\\", "/")
    }

    private fun detectLocalization() {
        val found = CardLocService.findLocalizationDirs(projectRoot)
        when {
            found.isEmpty() -> JOptionPane.showMessageDialog(
                this, "No localization/<language> folder with json files found in the project.", "Detect", JOptionPane.INFORMATION_MESSAGE
            )
            found.size == 1 -> setBaseFrom(found.first())
            else -> JBPopupFactory.getInstance()
                .createPopupChooserBuilder(found)
                .setRenderer(object : DefaultListCellRenderer() {
                    override fun getListCellRendererComponent(
                        list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
                    ): Component {
                        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                        (value as? File)?.let {
                            text = projectRoot.canonicalFile.toPath().relativize(it.canonicalFile.toPath())
                                .toString().replace(File.separatorChar, '/')
                        }
                        return this
                    }
                })
                .setItemChosenCallback { setBaseFrom(it) }
                .createPopup()
                .showUnderneathOf(localizationBaseField)
        }
    }
}
