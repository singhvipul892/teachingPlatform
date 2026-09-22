package com.maths.teacher.catalog.web.admin;

import com.maths.teacher.catalog.service.ChapterService;
import com.maths.teacher.catalog.web.admin.request.ChapterRequests;
import com.maths.teacher.catalog.web.dto.ChapterResponse;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chapter-level admin endpoints. Course-level ones (list, add, reorder) live on
 * AdminCourseController. Each returns the course's full content afterwards.
 */
@RestController
@RequestMapping("/api/admin/chapters")
@PreAuthorize("hasRole('ADMIN')")
public class AdminChapterController {

    private final ChapterService chapterService;

    public AdminChapterController(ChapterService chapterService) {
        this.chapterService = chapterService;
    }

    @PatchMapping(value = "/{chapterId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public List<ChapterResponse> renameChapter(
            @PathVariable Long chapterId,
            @RequestBody ChapterRequests.RenameChapter request
    ) {
        return chapterService.renameChapter(chapterId, request.title());
    }

    /** Deletes the chapter together with its classes and their PDFs. Cannot be undone. */
    @DeleteMapping("/{chapterId}")
    public List<ChapterResponse> deleteChapter(@PathVariable Long chapterId) {
        return chapterService.deleteChapter(chapterId);
    }

    /** Sets the chapter's classes and their order; classes listed from another chapter move here. */
    @PutMapping(value = "/{chapterId}/videos/order", consumes = MediaType.APPLICATION_JSON_VALUE)
    public List<ChapterResponse> setChapterVideos(
            @PathVariable Long chapterId,
            @RequestBody ChapterRequests.VideoOrder request
    ) {
        return chapterService.setChapterVideos(chapterId, request.videoIds());
    }
}
