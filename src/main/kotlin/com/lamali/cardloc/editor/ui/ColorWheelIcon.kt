package com.lamali.cardloc.editor.ui

import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon

class ColorWheelIcon(private val size: Int = 14) : Icon {
    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        val cx = x + size / 2f
        val cy = y + size / 2f
        val r = size / 2f

        // Draw rainbow segments
        val segments = 12
        for (i in 0 until segments) {
            val startAngle = (i * 360f / segments)
            val hue = i.toFloat() / segments
            g2.color = Color.getHSBColor(hue, 1f, 1f)
            g2.fill(java.awt.geom.Arc2D.Float(
                x.toFloat(), y.toFloat(), size.toFloat(), size.toFloat(),
                startAngle, 360f / segments, java.awt.geom.Arc2D.PIE
            ))
        }

        // White center circle to make it look like a wheel
        val innerR = r * 0.45f
        g2.color = if (c != null) c.background else Color.DARK_GRAY
        g2.fill(java.awt.geom.Ellipse2D.Float(cx - innerR, cy - innerR, innerR * 2, innerR * 2))

        g2.dispose()
    }

    override fun getIconWidth() = size
    override fun getIconHeight() = size
}