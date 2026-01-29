package com.maths.teacher.app.data.prefs

import android.content.Context

private const val PREFS_NAME = "pdf_downloads"
private const val KEY_PREFIX = "pdf_"

fun savePdfPath(context: Context, videoId: Long, pdfId: Long, path: String) {
    context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString("$KEY_PREFIX${videoId}_$pdfId", path)
        .apply()
}

fun getPdfPath(context: Context, videoId: Long, pdfId: Long): String? {
    return context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString("$KEY_PREFIX${videoId}_$pdfId", null)
}
