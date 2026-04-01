package com.maths.teacher.app.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/**
 * Copies a PDF from app-private storage into the public Downloads folder.
 * Returns true on success, false on failure.
 */
fun exportPdfToDownloads(context: Context, sourceFile: File, displayName: String): Boolean {
    return try {
        val safeName = if (displayName.endsWith(".pdf", ignoreCase = true)) displayName else "$displayName.pdf"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloadsDir.mkdirs()
            val destFile = File(downloadsDir, safeName)
            FileOutputStream(destFile).use { out ->
                sourceFile.inputStream().use { it.copyTo(out) }
            }
        }
        true
    } catch (e: Exception) {
        false
    }
}
