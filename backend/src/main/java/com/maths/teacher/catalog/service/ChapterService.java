package com.maths.teacher.catalog.service;

import com.maths.teacher.catalog.domain.Chapter;
import com.maths.teacher.catalog.domain.Video;
import com.maths.teacher.catalog.domain.VideoPdf;
import com.maths.teacher.catalog.repository.ChapterRepository;
import com.maths.teacher.catalog.repository.VideoPdfRepository;
import com.maths.teacher.catalog.repository.VideoRepository;
import com.maths.teacher.catalog.web.dto.ChapterResponse;
import com.maths.teacher.payment.repository.CourseRepository;
import com.maths.teacher.storage.S3StorageService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin-side chapter management. Order is never typed by the teacher: new
 * chapters and classes go to the end, and the panel sends the full order after
 * a drag, which is written back as 1..n.
 *
 * Every mutation returns the course's whole content (chapters incl. empty ones,
 * each with its classes) so the panel re-renders from what was actually saved.
 */
@Service
public class ChapterService {

    static final String DEFAULT_CHAPTER_TITLE = "All Classes";
    private static final int MAX_TITLE_LENGTH = 200;
    private static final String STALE_MESSAGE =
            "This course was changed somewhere else. Reload and try again.";

    private final ChapterRepository chapterRepository;
    private final VideoRepository videoRepository;
    private final VideoPdfRepository videoPdfRepository;
    private final CourseRepository courseRepository;
    private final S3StorageService storageService;

    public ChapterService(
            ChapterRepository chapterRepository,
            VideoRepository videoRepository,
            VideoPdfRepository videoPdfRepository,
            CourseRepository courseRepository,
            S3StorageService storageService
    ) {
        this.chapterRepository = chapterRepository;
        this.videoRepository = videoRepository;
        this.videoPdfRepository = videoPdfRepository;
        this.courseRepository = courseRepository;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public List<ChapterResponse> getContent(Long courseId) {
        requireCourse(courseId);
        var chapters = chapterRepository.findByCourseIdOrderByDisplayOrderAscIdAsc(courseId);
        var videos = videoRepository.findByCourseIdInChapterOrder(courseId);
        var pdfs = VideoResponses.loadPdfsByVideoId(videoPdfRepository, videos);
        return VideoResponses.toChapterResponses(chapters, videos, pdfs, true);
    }

    /** Appends chapters in the given order. One title or a pasted list both come through here. */
    @Transactional
    public List<ChapterResponse> addChapters(Long courseId, List<String> titles) {
        requireCourse(courseId);
        List<String> cleaned = titles == null ? List.of() : titles.stream()
                .map(t -> t == null ? "" : t.trim())
                .filter(t -> !t.isEmpty())
                .toList();
        if (cleaned.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a chapter name");
        }
        cleaned.forEach(ChapterService::requireTitleLength);

        int order = chapterRepository.findMaxDisplayOrder(courseId);
        var toSave = new ArrayList<Chapter>();
        for (String title : cleaned) {
            toSave.add(new Chapter(courseId, title, ++order));
        }
        chapterRepository.saveAll(toSave);
        return getContent(courseId);
    }

    @Transactional
    public List<ChapterResponse> renameChapter(Long chapterId, String title) {
        Chapter chapter = requireChapter(chapterId);
        String cleaned = title == null ? "" : title.trim();
        if (cleaned.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chapter name can't be empty");
        }
        requireTitleLength(cleaned);
        chapter.setTitle(cleaned);
        return getContent(chapter.getCourseId());
    }

    /** chapterIds must be exactly the course's chapters, in their new order. */
    @Transactional
    public List<ChapterResponse> reorderChapters(Long courseId, List<Long> chapterIds) {
        requireCourse(courseId);
        var chapters = chapterRepository.findByCourseIdOrderByDisplayOrderAscIdAsc(courseId);
        Map<Long, Chapter> byId = chapters.stream().collect(Collectors.toMap(Chapter::getId, Function.identity()));
        if (chapterIds == null
                || chapterIds.size() != chapters.size()
                || !new HashSet<>(chapterIds).equals(byId.keySet())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, STALE_MESSAGE);
        }
        for (int i = 0; i < chapterIds.size(); i++) {
            byId.get(chapterIds.get(i)).setDisplayOrder(i + 1);
        }
        return getContent(courseId);
    }

    /**
     * Sets a chapter's classes to exactly videoIds, in that order. Handles both
     * reordering inside the chapter and moving classes in from another chapter
     * of the same course (the chapters they left are renumbered).
     */
    @Transactional
    public List<ChapterResponse> setChapterVideos(Long chapterId, List<Long> videoIds) {
        Chapter chapter = requireChapter(chapterId);
        List<Long> ids = videoIds == null ? List.of() : videoIds;
        if (new LinkedHashSet<>(ids).size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A class is listed twice");
        }

        Map<Long, Video> byId = videoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Video::getId, Function.identity()));
        if (byId.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, STALE_MESSAGE);
        }
        for (Video v : byId.values()) {
            if (!v.getCourseId().equals(chapter.getCourseId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Classes can only be moved between chapters of the same course");
            }
        }
        // Everything already in the chapter must still be listed; otherwise the
        // panel is showing an old state and one class would end up unordered.
        for (Video current : videoRepository.findByChapterIdOrderByDisplayOrderAscIdAsc(chapterId)) {
            if (!byId.containsKey(current.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, STALE_MESSAGE);
            }
        }

        Set<Long> sourceChapterIds = new HashSet<>();
        for (int i = 0; i < ids.size(); i++) {
            Video v = byId.get(ids.get(i));
            if (!chapterId.equals(v.getChapterId())) {
                sourceChapterIds.add(v.getChapterId());
                v.setChapterId(chapterId);
            }
            v.setDisplayOrder(i + 1);
        }
        videoRepository.flush();
        sourceChapterIds.forEach(this::renumberVideos);
        return getContent(chapter.getCourseId());
    }

    /** Deletes the chapter and every class in it, including their PDFs in S3. */
    @Transactional
    public List<ChapterResponse> deleteChapter(Long chapterId) {
        Chapter chapter = requireChapter(chapterId);
        Long courseId = chapter.getCourseId();

        var videos = videoRepository.findByChapterIdOrderByDisplayOrderAscIdAsc(chapterId);
        if (!videos.isEmpty()) {
            List<Long> videoIds = videos.stream().map(Video::getId).toList();
            for (VideoPdf pdf : videoPdfRepository.findByVideo_IdInOrderByDisplayOrderAsc(videoIds)) {
                storageService.deleteByStorageUrl(pdf.getFileUrl());
            }
            videoRepository.deleteAll(videos);
        }
        chapterRepository.delete(chapter);
        chapterRepository.flush();

        var remaining = chapterRepository.findByCourseIdOrderByDisplayOrderAscIdAsc(courseId);
        for (int i = 0; i < remaining.size(); i++) {
            remaining.get(i).setDisplayOrder(i + 1);
        }
        return getContent(courseId);
    }

    /**
     * The chapter a new class goes into. With a chapterId it must belong to the
     * course (when one is given). Without one — older callers that only send
     * courseId — it is the course's first chapter, created if the course has none.
     */
    @Transactional
    public Chapter resolveChapterForNewVideo(Long courseId, Long chapterId) {
        if (chapterId != null) {
            Chapter chapter = requireChapter(chapterId);
            if (courseId != null && !courseId.equals(chapter.getCourseId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chapter does not belong to this course");
            }
            return chapter;
        }
        if (courseId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chapterId or courseId is required");
        }
        requireCourse(courseId);
        var chapters = chapterRepository.findByCourseIdOrderByDisplayOrderAscIdAsc(courseId);
        if (!chapters.isEmpty()) {
            return chapters.get(0);
        }
        return chapterRepository.save(new Chapter(courseId, DEFAULT_CHAPTER_TITLE, 1));
    }

    private void renumberVideos(Long chapterId) {
        var videos = videoRepository.findByChapterIdOrderByDisplayOrderAscIdAsc(chapterId);
        for (int i = 0; i < videos.size(); i++) {
            videos.get(i).setDisplayOrder(i + 1);
        }
    }

    private void requireCourse(Long courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found");
        }
    }

    private Chapter requireChapter(Long chapterId) {
        return chapterRepository.findById(chapterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chapter not found"));
    }

    private static void requireTitleLength(String title) {
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Chapter name is too long (max " + MAX_TITLE_LENGTH + " characters)");
        }
    }
}
