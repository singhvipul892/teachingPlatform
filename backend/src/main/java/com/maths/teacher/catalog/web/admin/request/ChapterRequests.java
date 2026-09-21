package com.maths.teacher.catalog.web.admin.request;

import java.util.List;

/** JSON bodies for the chapter endpoints. */
public final class ChapterRequests {

    private ChapterRequests() {
    }

    /** One name, or a pasted list, appended in this order. */
    public record AddChapters(List<String> titles) {
    }

    public record RenameChapter(String title) {
    }

    /** Every chapter of the course, in the new order. */
    public record ChapterOrder(List<Long> chapterIds) {
    }

    /** Every class that should be in the chapter, in order — including ones just dragged in. */
    public record VideoOrder(List<Long> videoIds) {
    }
}
