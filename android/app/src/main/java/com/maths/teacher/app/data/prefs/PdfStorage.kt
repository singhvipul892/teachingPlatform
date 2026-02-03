package com.maths.teacher.app.data.prefs

import android.content.Context

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
