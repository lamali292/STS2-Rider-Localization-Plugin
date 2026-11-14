package com.lamali.cardloc.core

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import javax.swing.Timer

/**
 * Follows the caret in code editors so that, in a file with several cards, the localization
 * panel shows the card the caret is in.
 */
class CardLocCaretTracker : ProjectActivity {

    override suspend fun execute(project: Project) {
        val registry = project.getService(CardLocRegistry::class.java)
        var pending: Editor? = null

        val timer = Timer(150) {
            val editor = pending ?: return@Timer
            handle(project, editor)
        }.apply { isRepeats = false }

        EditorFactory.getInstance().eventMulticaster.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                if (event.editor.project != project) return
                pending = event.editor
                timer.restart()
            }
        }, registry)
    }

    private fun handle(project: Project, editor: Editor) {
        if (editor.isDisposed) return
        if (FileEditorManager.getInstance(project).selectedTextEditor !== editor) return

        val tw = ToolWindowManager.getInstance(project).getToolWindow(CardLocSelector.TOOL_WINDOW_ID)
        if (tw == null || !tw.isVisible) return

        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        if (file.extension != "cs" && file.extension != "kt") return

        CardLocSelector.show(
            project, file, editor.document.text,
            CardLocSelector.Target.Offset(editor.caretModel.offset), force = false
        )
    }
}
