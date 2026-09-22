package com.maths.teacher.catalog.web.dto;

/** What the admin panel pre-fills from a pasted YouTube link. title is null when YouTube didn't say. */
public class YouTubeInfoResponse {

    private final String videoId;
    private final String title;
    private final String thumbnailUrl;

    public YouTubeInfoResponse(String videoId, String title, String thumbnailUrl) {
        this.videoId = videoId;
        this.title = title;
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getVideoId() {
        return videoId;
    }

    public String getTitle() {
        return title;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }
}
