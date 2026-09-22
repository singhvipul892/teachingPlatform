package com.maths.teacher.catalog.service;

import com.maths.teacher.catalog.domain.Chapter;
import com.maths.teacher.catalog.domain.Video;
import com.maths.teacher.catalog.repository.VideoPdfRepository;
import com.maths.teacher.catalog.web.dto.ChapterResponse;
import com.maths.teacher.catalog.web.dto.PdfResponse;
import com.maths.teacher.catalog.web.dto.VideoResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Entity → DTO mapping shared by the student and admin video listings. */
final class VideoResponses {

    private VideoResponses() {
    }

    /** All PDFs for these videos in one query, grouped by video id. */
    static Map<Long, List<PdfResponse>> loadPdfsByVideoId(VideoPdfRepository videoPdfRepository, List<Video> videos) {
        var videoIds = videos.stream().map(Video::getId).toList();
        if (videoIds.isEmpty()) {
            return Map.of();
        }
        return videoPdfRepository.findByVideo_IdInOrderByDisplayOrderAsc(videoIds)
                .stream()
                .collect(Collectors.groupingBy(
                        pdf -> pdf.getVideo().getId(),
                        Collectors.mapping(
                                pdf -> new PdfResponse(
                                        pdf.getId(),
                                        pdf.getTitle(),
                                        pdf.getPdfType(),
                                        pdf.getFileUrl(),
                                        pdf.getDisplayOrder()
                                ),
                                Collectors.toList()
                        )
                ));
    }

    static VideoResponse toVideoResponse(Video video, Map<Long, List<PdfResponse>> pdfsByVideoId) {
        return new VideoResponse(
                video.getId(),
                video.getVideoId(),
                video.getTitle(),
                video.getThumbnailUrl(),
                video.getDuration(),
                video.getDisplayOrder(),
                pdfsByVideoId.getOrDefault(video.getId(), List.of())
        );
    }

    /**
     * Groups videos (already in course order) under their chapters, keeping the
     * chapters' order. includeEmpty=false drops chapters with no classes, which
     * is what students get: the teacher can outline a syllabus before filling it.
     */
    static List<ChapterResponse> toChapterResponses(
            List<Chapter> chapters,
            List<Video> videosInOrder,
            Map<Long, List<PdfResponse>> pdfsByVideoId,
            boolean includeEmpty
    ) {
        Map<Long, List<VideoResponse>> videosByChapter = videosInOrder.stream()
                .collect(Collectors.groupingBy(
                        Video::getChapterId,
                        Collectors.mapping(v -> toVideoResponse(v, pdfsByVideoId), Collectors.toList())
                ));
        return chapters.stream()
                .map(c -> new ChapterResponse(
                        c.getId(), c.getTitle(), c.getDisplayOrder(),
                        videosByChapter.getOrDefault(c.getId(), List.of())))
                .filter(c -> includeEmpty || !c.getVideos().isEmpty())
                .toList();
    }
}
