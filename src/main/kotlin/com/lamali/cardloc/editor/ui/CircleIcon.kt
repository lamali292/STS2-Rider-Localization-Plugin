package com.lamali.cardloc.editor.ui

import java.awt.*
import javax.swing.Icon

class CircleIcon(private val color: Color, private val size: Int = 12) : Icon {
    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Fill
        g2.color = color
        g2.fillOval(x, y, size, size)

        // Optional: Subtle outline so white circles don't vanish
        if (color == Color.WHITE) {
            g2.color = Color(255, 255, 255, 100)
            g2.drawOval(x, y, size, size)
        }

        g2.dispose()
    }
    override fun getIconWidth() = size
    override fun getIconHeight() = size
}