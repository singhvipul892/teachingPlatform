package com.maths.teacher.app.util

import java.text.SimpleDateFormat
import java.util.Locale

/** How close to the end we start warning the student rather than just stating the date. */
const val EXPIRY_WARNING_DAYS = 7

/**
 * Turns the API's yyyy-MM-dd expiry into something a student reads, e.g. "10 Mar 2027".
 *
 * The app targets API 24, so java.time is unavailable here — the parsing the
 * backend already did is deliberately not repeated, only formatted.
 */
fun formatExpiryDate(isoDate: String): String {
    return try {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(isoDate)
        if (parsed == null) isoDate else SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(parsed)
    } catch (e: Exception) {
        // Never let a formatting problem hide the course itself.
        isoDate
    }
}
