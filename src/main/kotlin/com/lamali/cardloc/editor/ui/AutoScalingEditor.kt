package com.lamali.cardloc.editor.ui

import java.awt.*
import javax.swing.JTextPane
import javax.swing.text.*

class AutoScalingEditor : JTextPane() {
    init {
        isOpaque = false
        putClientProperty("JEditorPane.honorDisplayProperties", true)
    }

    override fun getScrollableTracksViewportWidth(): Boolean = true
    override fun getScrollableTracksViewportHeight(): Boolean = false

    override fun getPreferredSize(): Dimension {
        val parentWidth = parent?.width ?: 0
        if (parentWidth > 0) setSize(parentWidth, Int.MAX_VALUE)
        val d = super.getPreferredSize()
        return Dimension(10, d.height.coerceAtLeast(45))
    }
}

class SymbolViewFactory(private val delegate: ViewFactory) : ViewFactory {
    override fun create(elem: Element): View {
        return if (elem.name == AbstractDocument.ParagraphElementName) {
            NewlineSymbolParagraphView(elem)
        } else delegate.create(elem)
    }
}

class NewlineSymbolParagraphView(elem: Element) : ParagraphView(elem) {
    override fun paint(g: Graphics, allocation: Shape) {
        super.paint(g, allocation)
        val doc = document
        val lastCharIdx = endOffset - 1
        if (lastCharIdx >= 0 && lastCharIdx < doc.length - 1) {
            try {
                if (doc.getText(lastCharIdx, 1) == "\n") {
                    val shape = modelToView(lastCharIdx, allocation, Position.Bias.Forward) ?: return
                    val r = shape.bounds
                    val g2d = g as Graphics2D
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2d.color = UI.subtleText.darker()
                    g2d.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
                    g2d.drawString("¬", r.x + 2, r.y + r.height - 5)
                }
            } catch (_: Exception) {}
        }
    }
}