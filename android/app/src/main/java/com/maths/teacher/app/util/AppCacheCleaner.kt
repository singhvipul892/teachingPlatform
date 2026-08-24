package com.maths.teacher.app.util

import android.content.Context
import android.os.Environment
import com.maths.teacher.app.data.prefs.clearAllPdfPaths
import java.io.File

/**
 * Wipes all locally cached app data:
 *  - downloaded PDF files (external + internal download dirs)
 *  - the app cache directories
 *  - the saved PDF-path preference entries
 *
 * Session/token clearing is handled separately by [com.maths.teacher.app.data.prefs.SessionManager.clearSession].
 *
 * Performs file IO, so call from a background dispatcher.
 */
fun clearAllCachedData(context: Context) {
    val appContext = context.applicationContext

    // Downloaded PDFs live in the app's external Downloads dir (falling back to filesDir).
    appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.deleteContents()
    appContext.filesDir.listFiles()?.forEach { child ->
        if (child.isFile && child.name.endsWith(".pdf", ignoreCase = true)) child.delete()
    }

    // App cache directories.
    appContext.cacheDir.deleteContents()
    appContext.externalCacheDir?.deleteContents()

    // Saved PDF-path preferences.
    clearAllPdfPaths(appContext)
}

private fun File.deleteContents() {
    listFiles()?.forEach { child ->
        if (child.isDirectory) child.deleteRecursively() else child.delete()
    }
}
