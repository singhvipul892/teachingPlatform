package com.maths.teacher.catalog.util;

import com.maths.teacher.catalog.exception.ErrorMessages;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Utility to extract YouTube video ID from various YouTube URL formats.
 * Supports:
 * - https://www.youtube.com/watch?v=VIDEO_ID
 * - https://youtu.be/VIDEO_ID
 * - https://www.youtube.com/embed/VIDEO_ID
 * - https://m.youtube.com/watch?v=VIDEO_ID
 */
@Component
public class YouTubeUrlExtractor {

    private static final Pattern[] PATTERNS = {
            // Standard watch URL: https://www.youtube.com/watch?v=VIDEO_ID
            Pattern.compile("(?:youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/embed/)([a-zA-Z0-9_-]{11})"),
            // Short URL: https://youtu.be/VIDEO_ID
            Pattern.compile("youtu\\.be/([a-zA-Z0-9_-]{11})"),
            // Embed URL: https://www.youtube.com/embed/VIDEO_ID
            Pattern.compile("youtube\\.com/embed/([a-zA-Z0-9_-]{11})")
    };

    /**
     * Extracts YouTube video ID from a YouTube URL.
     *
     * @param youtubeUrl YouTube URL in any supported format
     * @return YouTube video ID (11 characters)
     * @throws IllegalArgumentException if URL format is invalid or video ID cannot be extracted
     */
    public String extractVideoId(String youtubeUrl) {
        if (youtubeUrl == null || youtubeUrl.trim().isEmpty()) {
            throw new IllegalArgumentException(ErrorMessages.YOUTUBE_URL_CANNOT_BE_NULL_OR_EMPTY);
        }

        String trimmedUrl = youtubeUrl.trim();

        // If it's already just a video ID (11 characters), return as-is
        if (trimmedUrl.matches("^[a-zA-Z0-9_-]{11}$")) {
            return trimmedUrl;
        }

        // Try each pattern
        for (Pattern pattern : PATTERNS) {
            Matcher matcher = pattern.matcher(trimmedUrl);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }

        throw new IllegalArgumentException(ErrorMessages.INVALID_YOUTUBE_URL_FORMAT);
    }

    /**
     * Generates standard YouTube thumbnail URL from video ID.
     *
     * @param videoId YouTube video ID
     * @return Thumbnail URL
     */
    public String generateThumbnailUrl(String videoId) {
        return "https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg";
    }
}
