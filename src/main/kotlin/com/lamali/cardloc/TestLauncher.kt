package com.lamali.cardloc

import com.lamali.cardloc.editor.CardLocPanel
import javax.swing.*
import java.awt.Dimension

fun main() {
    SwingUtilities.invokeLater {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (e: Exception) {
            // Fallback to default if system theme fails
        }

        val testBase = System.getProperty("user.dir") + "/src/main/resources"
        CardLocRegistry.initialize(testBase)

        val frame = JFrame("STS2 Localization Preview")
        frame.defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE

        val panel = CardLocPanel()

        val preset = CardLocRegistry.all().first { it.id == "power" }
        val fakeData = mapOf(
            "myCard.title" to "[gold]Strike[/gold]",
            "myCard.description" to "Deal 6 damage. [b]Innate.[/b]"
        )
        panel.load(null, "myCard", fakeData, preset)

        frame.add(panel)
        frame.minimumSize = Dimension(400, 500)
        frame.pack()
        frame.setSize(500, 800)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true

        println("Preview Window is now visible.")
    }
}