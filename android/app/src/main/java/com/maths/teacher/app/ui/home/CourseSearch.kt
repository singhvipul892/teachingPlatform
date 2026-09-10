package com.maths.teacher.app.ui.home

import com.maths.teacher.app.domain.model.CourseWithVideos

/**
 * One course that matched a search, carrying only the classes that matched.
 *
 * When [matchedByName] is true the student searched for the course itself, so [course] keeps all
 * of its classes and [totalVideos] equals `course.videos.size`.
 */
data class CourseSearchResult(
    val course: CourseWithVideos,
    val totalVideos: Int,
    val matchedByName: Boolean
)

/**
 * Searches courses by name and classes by title, entirely in memory.
 *
 * A course whose *name* matches is returned whole — the student asked for the course, so they get
 * every class in it. Otherwise the course is returned only if some of its class titles match, and
 * then it carries just those classes.
 *
 * Returns an empty list for a blank or punctuation-only query.
 */
fun searchCourses(
    courses: List<CourseWithVideos>,
    query: String
): List<CourseSearchResult> {
    val trimmed = query.trim()
    val queryTokens = tokenize(trimmed)
    if (queryTokens.isEmpty()) return emptyList()

    // Course-name matches outrank class-only matches, whatever their raw scores.
    val nameMatchBonus = 100

    return courses
        .mapNotNull { course ->
            val nameScore = scoreTitle(course.name, queryTokens, trimmed)
            if (nameScore != null) {
                return@mapNotNull ScoredCourse(
                    result = CourseSearchResult(
                        course = course,
                        totalVideos = course.videos.size,
                        matchedByName = true
                    ),
                    score = nameScore + nameMatchBonus
                )
            }

            // sortedByDescending is stable, so equally-scoring classes keep their displayOrder.
            val matches = course.videos
                .mapNotNull { video ->
                    scoreTitle(video.title, queryTokens, trimmed)?.let { score -> score to video }
                }
                .sortedByDescending { it.first }

            if (matches.isEmpty()) return@mapNotNull null

            ScoredCourse(
                result = CourseSearchResult(
                    course = course.copy(videos = matches.map { it.second }),
                    totalVideos = course.videos.size,
                    matchedByName = false
                ),
                score = matches.first().first
            )
        }
        .sortedByDescending { it.score }
        .map { it.result }
}

private data class ScoredCourse(val result: CourseSearchResult, val score: Int)

private val TOKEN_DELIMITER = Regex("[^\\p{L}\\p{N}]+")

private fun tokenize(text: String): List<String> =
    text.lowercase().split(TOKEN_DELIMITER).filter { it.isNotEmpty() }

/**
 * Scores [title] against already-tokenized query terms, or returns null if it does not match.
 *
 * Every query token has to match somewhere (AND semantics), which is what keeps "class 5" from
 * dragging in every class. Better kinds of match score higher, so "class 5" ranks `DPB CLASS-5`
 * (exact token) above `DPB CLASS-50` (token prefix) above `DPB CLASS-15` (bare substring).
 */
private fun scoreTitle(title: String, queryTokens: List<String>, rawQuery: String): Int? {
    val normalizedTitle = title.lowercase()
    val titleTokens = tokenize(title)

    var score = 0
    for (token in queryTokens) {
        score += when {
            titleTokens.any { it == token } -> 3
            titleTokens.any { it.startsWith(token) } -> 2
            normalizedTitle.contains(token) -> 1
            else -> return null
        }
    }

    val normalizedQuery = rawQuery.lowercase()
    if (normalizedTitle == normalizedQuery) score += 5
    if (normalizedTitle.startsWith(normalizedQuery)) score += 2

    return score
}
