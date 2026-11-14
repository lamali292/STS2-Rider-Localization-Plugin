package com.lamali.cardloc.core

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Reads and writes localization files through the IDE where possible, so that
 *  - unsaved edits in an open editor tab are what we read and patch (never the stale disk copy),
 *  - our edit lands in that document as one small, undoable change instead of fighting the editor,
 *  - the IDE and the disk stay in sync (no "file changed on disk" conflicts).
 * Files the IDE does not know fall back to an atomic write (temp file + rename).
 */
object LocFileIO {

    /** Current text of [file]: the document's (possibly unsaved) text if the IDE has it, else the disk. Null if absent. */
    fun read(file: File): String? {
        val vf = LocalFileSystem.getInstance().findFileByIoFile(file)
        if (vf != null && vf.isValid && !vf.isDirectory) {
            val text = ReadAction.compute<String?, RuntimeException> {
                FileDocumentManager.getInstance().getDocument(vf)?.text
            }
            if (text != null) return text
        }
        return if (file.exists()) file.readText() else null
    }

    /** Replaces the content of [file] with [newText] (creating it if needed). */
    fun write(file: File, newText: String) {
        runOnEdt { writeOnEdt(file, newText) }
    }

    private fun writeOnEdt(file: File, newText: String) {
        // Pick up changes made outside the IDE before deciding what to write onto.
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
        val docManager = FileDocumentManager.getInstance()
        val doc = vf?.takeIf { it.isValid && !it.isDirectory }?.let { docManager.getDocument(it) }

        if (doc != null) {
            // Documents hold LF text and no BOM; the IDE restores the file's own separator / BOM on save.
            val target = newText.removePrefix("﻿").replace("\r\n", "\n")
            val current = doc.text
            if (current != target) {
                val wasUnsaved = docManager.isDocumentUnsaved(doc)
                var start = 0
                val maxPrefix = minOf(current.length, target.length)
                while (start < maxPrefix && current[start] == target[start]) start++
                var endCur = current.length
                var endNew = target.length
                while (endCur > start && endNew > start && current[endCur - 1] == target[endNew - 1]) { endCur--; endNew-- }

                CommandProcessor.getInstance().executeCommand(null, {
                    ApplicationManager.getApplication().runWriteAction {
                        doc.replaceString(start, endCur, target.substring(start, endNew))
                    }
                }, "CardLoc: update localization", null)

                // Don't write the user's own unsaved edits to disk behind their back.
                if (!wasUnsaved) docManager.saveDocument(doc)
            }
            return
        }

        atomicWrite(file, newText)
        LocalFileSystem.getInstance().refreshIoFiles(listOf(file))
    }

    private fun atomicWrite(file: File, text: String) {
        file.parentFile?.mkdirs()
        val tmp = File.createTempFile("cardloc", ".tmp", file.parentFile)
        try {
            tmp.writeText(text)
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    private fun runOnEdt(block: () -> Unit) {
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) block() else app.invokeAndWait(block)
    }
}
