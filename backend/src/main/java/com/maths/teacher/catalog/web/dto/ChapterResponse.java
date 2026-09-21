package com.maths.teacher.catalog.web.dto;

import java.util.List;

/** One chapter of a course with its classes, both in display order. */
public class ChapterResponse {

    private final Long id;
    private final String title;
    private final Integer displayOrder;
    private final List<VideoResponse> videos;

    public ChapterResponse(Long id, String title, Integer displayOrder, List<VideoResponse> videos) {
        this.id = id;
        this.title = title;
        this.displayOrder = displayOrder;
        this.videos = videos;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public List<VideoResponse> getVideos() {
        return videos;
    }
}
