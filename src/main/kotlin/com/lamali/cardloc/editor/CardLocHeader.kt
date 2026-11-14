package com.lamali.cardloc.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBLabel
import com.lamali.cardloc.editor.ui.UI
import java.awt.*
import javax.swing.*

class CardLocHeader(
    private val onReload: () -> Unit,
    private val onAdd: () -> Unit,
    private val onSave: () -> Unit,
    private val onSettings: () -> Unit,
    private val onClassPicked: (String) -> Unit = {},
    private val onLanguagePicked: (String) -> Unit = {},
    /** Whether the shown card has an entry in the given language (asked when the popup opens). */
    private val hasEntry: (String) -> Boolean = { true }
) : JPanel(BorderLayout()) {

    private val keyLabel = JLabel("No card selected").apply {
        foreground = UI.text
        font = font.deriveFont(Font.BOLD, 12f)
    }

    // Card classes of the open file; with more than one, the key label doubles as a dropdown.
    private var classNames: List<String> = emptyList()
    private var selectedClass: String = ""

    private fun pickerEnabled() = classNames.size > 1

    // Language of the shown localization; a dropdown when the project has more than one.
    private var languages: List<String> = emptyList()
    private var selectedLanguage: String = ""
    private val languageLabel = JBLabel().apply {
        isVisible = false
        horizontalTextPosition = SwingConstants.LEFT
        iconTextGap = 2
        font = font.deriveFont(Font.BOLD, 11f)
        foreground = UI.text
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = "Language"
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) = showLanguagePopup()
        })
    }

    init {
        background = UI.bgAlt
        // A subtle bottom border to separate the global header from the editor/toolbar
        border = BorderFactory.createMatteBorder(0, 0, 1, 0, UI.border)
        keyLabel.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) = showClassPopup()
        })
        updateOrientation(false)
    }

    fun setKey(key: String) {
        keyLabel.text = key
    }

    fun setClasses(names: List<String>, selected: String) {
        classNames = names
        selectedClass = selected
        val picker = pickerEnabled()
        keyLabel.icon = if (picker) AllIcons.General.ArrowDown else null
        keyLabel.horizontalTextPosition = SwingConstants.LEFT
        keyLabel.iconTextGap = 4
        keyLabel.cursor = Cursor.getPredefinedCursor(if (picker) Cursor.HAND_CURSOR else Cursor.DEFAULT_CURSOR)
        keyLabel.toolTipText = if (picker) "Choose a card class in this file" else null
    }

    fun setLanguages(names: List<String>, selected: String, selectedHasEntry: Boolean = true) {
        languages = names
        selectedLanguage = selected
        languageLabel.text = selected.uppercase() + if (selectedHasEntry) "" else " (no entry)"
        languageLabel.icon = AllIcons.General.ArrowDown
        languageLabel.isVisible = names.size > 1
        revalidate()
    }

    private fun showLanguagePopup() {
        if (languages.size < 2) return
        // Languages that have this card first (keeping their order), the ones without an entry after, greyed out.
        val (withEntry, without) = languages.partition { it == selectedLanguage || hasEntry(it) }
        val missing = without.toSet()
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(withEntry + without)
            .setSelectedValue(selectedLanguage, true)
            .setRenderer(object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
                ): Component {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    val name = value as? String ?: return this
                    if (name in missing) {
                        text = "$name   (no entry)"
                        if (!isSelected) foreground = UIManager.getColor("Label.disabledForeground") ?: foreground
                    }
                    return this
                }
            })
            .setItemChosenCallback { name -> if (name != selectedLanguage) onLanguagePicked(name) }
            .createPopup()
            .showUnderneathOf(languageLabel)
    }

    private fun showClassPopup() {
        if (!pickerEnabled()) return
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(classNames)
            .setSelectedValue(selectedClass, true)
            .setItemChosenCallback { name -> if (name != selectedClass) onClassPicked(name) }
            .createPopup()
            .showUnderneathOf(keyLabel)
    }

    fun updateOrientation(vertical: Boolean) {
        removeAll()

        if (vertical) {
            // SIDEBAR: Stacked Vertically
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createMatteBorder(0, 0, 0, 1, UI.border)

            add(Box.createVerticalStrut(10))
            keyLabel.horizontalAlignment = SwingConstants.CENTER
            keyLabel.alignmentX = Component.CENTER_ALIGNMENT
            add(keyLabel)

            add(Box.createVerticalStrut(10))
            add(createActionGroup(FlowLayout.CENTER))

            preferredSize = Dimension(120, 0)
        } else {
            // BOTTOM: Single horizontal line
            layout = BorderLayout()
            border = BorderFactory.createMatteBorder(0, 0, 1, 0, UI.border)

            val content = JPanel(BorderLayout()).apply {
                background = UI.bgAlt
                border = BorderFactory.createEmptyBorder(4, 12, 4, 8)
                add(keyLabel, BorderLayout.WEST)
                add(createActionGroup(FlowLayout.RIGHT), BorderLayout.EAST)
            }
            add(content, BorderLayout.CENTER)
            preferredSize = Dimension(0, 40)
        }

        revalidate()
        repaint()
    }

    private fun createActionGroup(alignment: Int) = JPanel(FlowLayout(alignment, 4, 0)).apply {
        background = UI.bgAlt
        add(languageLabel)
        add(createAction(AllIcons.Actions.Refresh, "Reload") { onReload() })
        add(createAction(AllIcons.General.Add, "Add Field") { onAdd() })
        add(createAction(AllIcons.Actions.MenuSaveall, "Save All") { onSave() })
        add(createAction(AllIcons.General.GearPlain, "Settings") { onSettings() })
    }

    private fun createAction(icon: Icon, tip: String, action: () -> Unit) =
        JBLabel(icon, SwingConstants.CENTER).apply {
            preferredSize = Dimension(28, 26)
            toolTipText = tip
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mousePressed(e: java.awt.event.MouseEvent) = action()
                override fun mouseEntered(e: java.awt.event.MouseEvent) {
                    isOpaque = true
                    background = UI.border
                    repaint()
                }
                override fun mouseExited(e: java.awt.event.MouseEvent) {
                    isOpaque = false
                    repaint()
                }
            })
        }
}