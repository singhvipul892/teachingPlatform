package com.maths.teacher.catalog.service;

import com.maths.teacher.catalog.repository.ChapterRepository;
import com.maths.teacher.catalog.repository.VideoPdfRepository;
import com.maths.teacher.catalog.repository.VideoRepository;
import com.maths.teacher.catalog.web.dto.ChapterResponse;
import com.maths.teacher.catalog.web.dto.VideoResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VideoCatalogService {

    private static final Logger logger = LoggerFactory.getLogger(VideoCatalogService.class);

    private final VideoRepository videoRepository;
    private final VideoPdfRepository videoPdfRepository;
    private final ChapterRepository chapterRepository;
    private final CourseAccessGuard courseAccessGuard;

    public VideoCatalogService(
            VideoRepository videoRepository,
            VideoPdfRepository videoPdfRepository,
            ChapterRepository chapterRepository,
            CourseAccessGuard courseAccessGuard
    ) {
        this.videoRepository = videoRepository;
        this.videoPdfRepository = videoPdfRepository;
        this.chapterRepository = chapterRepository;
        this.courseAccessGuard = courseAccessGuard;
    }

    /**
     * Flat list in course order (chapter by chapter). Kept for app builds that
     * predate chapters; newer builds use {@link #getChaptersByCourse}.
     */
    public List<VideoResponse> getVideosByCourse(Long courseId, Long userId) {
        logger.info("Fetching videos for course {} by user {}", courseId, userId);
        courseAccessGuard.requireAccess(userId, courseId);

        var videos = videoRepository.findByCourseIdInChapterOrder(courseId);
        var pdfsByVideoId = VideoResponses.loadPdfsByVideoId(videoPdfRepository, videos);

        return videos.stream()
                .map(video -> VideoResponses.toVideoResponse(video, pdfsByVideoId))
                .toList();
    }

    /** Chapters with their classes, in order. Chapters with no classes yet are left out. */
    public List<ChapterResponse> getChaptersByCourse(Long courseId, Long userId) {
        logger.info("Fetching chapters for course {} by user {}", courseId, userId);
        courseAccessGuard.requireAccess(userId, courseId);

        var chapters = chapterRepository.findByCourseIdOrderByDisplayOrderAscIdAsc(courseId);
        var videos = videoRepository.findByCourseIdInChapterOrder(courseId);
        var pdfsByVideoId = VideoResponses.loadPdfsByVideoId(videoPdfRepository, videos);

        return VideoResponses.toChapterResponses(chapters, videos, pdfsByVideoId, false);
    }
}
