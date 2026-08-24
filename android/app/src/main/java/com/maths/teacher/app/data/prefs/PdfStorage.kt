package com.maths.teacher.app.data.prefs

import android.content.Context
import java.io.File

private const val PREFS_NAME_PREFIX = "pdf_downloads_"
private const val KEY_PREFIX = "pdf_"

fun savePdfPath(context: Context, userId: Long?, videoId: Long, pdfId: Long, path: String) {
    if (userId == null) return
    context.applicationContext
        .getSharedPreferences("$PREFS_NAME_PREFIX$userId", Context.MODE_PRIVATE)
        .edit()
        .putString("$KEY_PREFIX${videoId}_$pdfId", path)
        .apply()
}

fun getPdfPath(context: Context, userId: Long?, videoId: Long, pdfId: Long): String? {
    if (userId == null) return null
    return context.applicationContext
        .getSharedPreferences("$PREFS_NAME_PREFIX$userId", Context.MODE_PRIVATE)
        .getString("$KEY_PREFIX${videoId}_$pdfId", null)
}

/**
 * Remove every saved PDF path for all users. Used on force-logout / full cache wipe
 * so no stale download references survive into the next session.
 */
fun clearAllPdfPaths(context: Context) {
    val appContext = context.applicationContext
    val prefsDir = File(appContext.applicationInfo.dataDir, "shared_prefs")
    prefsDir.listFiles()?.forEach { file ->
        val name = file.name.removeSuffix(".xml")
        if (name.startsWith(PREFS_NAME_PREFIX)) {
            appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
        }
    }
}
