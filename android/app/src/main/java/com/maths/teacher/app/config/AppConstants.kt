package com.maths.teacher.app.config

object AppConstants {
    // For Android Emulator, 10.0.2.2 maps to localhost of the dev machine.
    const val BASE_URL = "http://13.205.19.207:8080/"

    const val YOUTUBE_APP_URI_PREFIX = "vnd.youtube:"
    const val YOUTUBE_WEB_URL_PREFIX = "https://www.youtube.com/watch?v="
    const val YOUTUBE_EMBED_URL_PREFIX = "https://www.youtube.com/embed/"
    const val YOUTUBE_NOCOOKIE_EMBED_URL_PREFIX = "https://www.youtube-nocookie.com/embed/"

    /**
     * Builds a YouTube embed URL with autoplay enabled
     * @param videoId The YouTube video ID
     * @param useNoCookieDomain Whether to use youtube-nocookie.com (more permissive for WebView)
     * @return The embed URL with autoplay and required parameters
     */
    fun buildYouTubeEmbedUrl(videoId: String, useNoCookieDomain: Boolean = true): String {
        // Clean the video ID (remove any whitespace or invalid characters)
        val cleanVideoId = videoId.trim()
        
        // Validate video ID format (YouTube IDs are typically 11 characters)
        if (cleanVideoId.isEmpty()) {
            throw IllegalArgumentException("Video ID cannot be empty")
        }
        
        // Use nocookie domain for better WebView compatibility
        val baseUrl = if (useNoCookieDomain) {
            YOUTUBE_NOCOOKIE_EMBED_URL_PREFIX
        } else {
            YOUTUBE_EMBED_URL_PREFIX
        }
        
        // Build URL with required parameters for embedding
        // Note: Removed autoplay=1 as YouTube often blocks it in WebView
        // enablejsapi=1: Enables JavaScript API (helps with embedding)
        // rel=0: Don't show related videos
        // modestbranding=1: Hide YouTube logo
        // playsinline=1: Play inline on mobile devices
        // controls=1: Show video controls
        return "${baseUrl}${cleanVideoId}?enablejsapi=1&rel=0&modestbranding=1&playsinline=1&controls=1"
    }
}
