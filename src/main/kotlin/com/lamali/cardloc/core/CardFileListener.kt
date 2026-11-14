package com.lamali.cardloc.core

import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener

class CardFileListener : FileEditorManagerListener {

    override fun selectionChanged(event: FileEditorManagerEvent) {
        val file = event.newFile ?: return
        if (file.extension != "cs" && file.extension != "kt") return

        val project = event.manager.project
        val editor = event.manager.selectedTextEditor?.takeIf {
            com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getFile(it.document) == file
        }

        // Prefer the live document (unsaved edits) and the caret position when we have an editor.
        val source = editor?.document?.text ?: runCatching { String(file.contentsToByteArray()) }.getOrNull() ?: return
        val target = editor?.let { CardLocSelector.Target.Offset(it.caretModel.offset) } ?: CardLocSelector.Target.First

        CardLocSelector.show(project, file, source, target, force = true)
    }
}
