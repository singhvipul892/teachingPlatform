package com.maths.teacher.catalog.service;

import com.maths.teacher.catalog.domain.Chapter;
import com.maths.teacher.catalog.domain.Video;
import com.maths.teacher.catalog.domain.VideoPdf;
import com.maths.teacher.catalog.repository.VideoPdfRepository;
import com.maths.teacher.catalog.repository.VideoRepository;
import com.maths.teacher.catalog.util.YouTubeUrlExtractor;
import com.maths.teacher.catalog.web.dto.PdfResponse;
import com.maths.teacher.catalog.web.dto.VideoResponse;
import com.maths.teacher.catalog.web.dto.YouTubeInfoResponse;
import com.maths.teacher.storage.S3StorageService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminService {

    private static final String PDF_TYPE_NOTES = "Notes";
    private static final String PDF_TYPE_SOLVED_PRACTICE_SET = "Solved Practice Set";
    private static final String PDF_TYPE_ANNOTATED_PRACTICE_SET = "Annotated Practice Set";

    private final VideoRepository videoRepository;
    private final VideoPdfRepository videoPdfRepository;
    private final S3StorageService storageService;
    private final YouTubeUrlExtractor youtubeUrlExtractor;
    private final ChapterService chapterService;
    private final YouTubeOEmbedClient youTubeOEmbedClient;

    public AdminService(
            VideoRepository videoRepository,
            VideoPdfRepository videoPdfRepository,
            S3StorageService storageService,
            YouTubeUrlExtractor youtubeUrlExtractor,
            ChapterService chapterService,
            YouTubeOEmbedClient youTubeOEmbedClient
    ) {
        this.videoRepository = videoRepository;
        this.videoPdfRepository = videoPdfRepository;
        this.storageService = storageService;
        this.youtubeUrlExtractor = youtubeUrlExtractor;
        this.chapterService = chapterService;
        this.youTubeOEmbedClient = youTubeOEmbedClient;
    }

    /**
     * Video id, thumbnail and (when YouTube answers) title for a pasted link, so
     * the panel can fill the title in. A bad link is a 400; YouTube being slow or
     * the video being private just means no title.
     */
    public YouTubeInfoResponse getYouTubeInfo(String youtubeVideoLink) {
        String videoId = extractVideoIdOrBadRequest(youtubeVideoLink);
        return new YouTubeInfoResponse(
                videoId,
                youTubeOEmbedClient.fetchTitle(videoId),
                youtubeUrlExtractor.generateThumbnailUrl(videoId)
        );
    }

    private String extractVideoIdOrBadRequest(String youtubeVideoLink) {
        try {
            return youtubeUrlExtractor.extractVideoId(youtubeVideoLink);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Creates a video lesson from YouTube link with optional PDFs.
     * Extracts video ID from YouTube URL and generates thumbnail URL automatically.
     *
     * @param youtubeVideoLink YouTube video URL (any format)
     * @param title Video title
     * @param courseId Course ID (optional when chapterId is given)
     * @param chapterId Chapter to add the video to; without it, the course's first chapter
     * @param duration Video duration (e.g., "12:45")
     * @param displayOrder Position within the chapter; omitted = appended at the end
     * @param notesPdf Notes PDF (optional)
     * @param solvedPracticeSetPdf Solved Practice Set PDF (optional)
     * @param annotatedPracticeSetPdf Annotated Practice Set PDF (optional)
     * @return Created video with PDFs
     */
    @Transactional
    public VideoResponse createVideoLesson(
            String youtubeVideoLink,
            String title,
            Long courseId,
            Long chapterId,
            String duration,
            Integer displayOrder,
            MultipartFile notesPdf,
            MultipartFile solvedPracticeSetPdf,
            MultipartFile annotatedPracticeSetPdf
    ) {
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a title for the class");
        }
        if (displayOrder != null && displayOrder < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayOrder must be >= 1");
        }
        String videoId = extractVideoIdOrBadRequest(youtubeVideoLink);
        String thumbnailUrl = youtubeUrlExtractor.generateThumbnailUrl(videoId);

        Chapter chapter = chapterService.resolveChapterForNewVideo(courseId, chapterId);
        int order = displayOrder != null
                ? displayOrder
                : videoRepository.findMaxDisplayOrderInChapter(chapter.getId()) + 1;

        var video = new Video(null, videoId, title.trim(), chapter.getCourseId(), chapter.getId(),
                thumbnailUrl, duration, order);
        var savedVideo = videoRepository.save(video);

        // Process PDFs: upload to S3 first, then batch save to DB
        var pdfsToSave = new ArrayList<VideoPdf>();
        int displayOrderCounter = 1;

        if (notesPdf != null && !notesPdf.isEmpty()) {
            pdfsToSave.add(createPdfEntity(savedVideo, notesPdf, PDF_TYPE_NOTES, displayOrderCounter++));
        }

        if (solvedPracticeSetPdf != null && !solvedPracticeSetPdf.isEmpty()) {
            pdfsToSave.add(createPdfEntity(savedVideo, solvedPracticeSetPdf, PDF_TYPE_SOLVED_PRACTICE_SET, displayOrderCounter++));
        }

        if (annotatedPracticeSetPdf != null && !annotatedPracticeSetPdf.isEmpty()) {
            pdfsToSave.add(createPdfEntity(savedVideo, annotatedPracticeSetPdf, PDF_TYPE_ANNOTATED_PRACTICE_SET, displayOrderCounter++));
        }

        // Batch save all PDFs in a single DB call
        var savedPdfs = pdfsToSave.isEmpty() 
                ? new ArrayList<VideoPdf>() 
                : videoPdfRepository.saveAll(pdfsToSave);

        // Convert to response DTOs
        var pdfResponses = savedPdfs.stream()
                .map(pdf -> new PdfResponse(
                        pdf.getId(),
                        pdf.getTitle(),
                        pdf.getPdfType(),
                        pdf.getFileUrl(),
                        pdf.getDisplayOrder()
                ))
                .toList();

        return new VideoResponse(
                savedVideo.getId(),
                savedVideo.getVideoId(),
                savedVideo.getTitle(),
                savedVideo.getThumbnailUrl(),
                savedVideo.getDuration(),
                savedVideo.getDisplayOrder(),
                pdfResponses
        );
    }

    /**
     * Creates a PDF entity after uploading to S3.
     * Does not save to DB - use batch saveAll() for multiple PDFs.
     */
    private VideoPdf createPdfEntity(
            Video video,
            MultipartFile file,
            String pdfType,
            int displayOrder
    ) {
        String storageUrl = storageService.uploadPdf(video.getId(), file);
        String pdfTitle = generatePdfTitle(video.getTitle(), pdfType);

        return new VideoPdf(
                null,
                video,
                pdfTitle,
                pdfType,
                storageUrl,
                displayOrder
        );
    }

    private String generatePdfTitle(String videoTitle, String pdfType) {
        return videoTitle + " - " + pdfType;
    }

    public List<VideoResponse> getVideosForCourse(Long courseId) {
        List<Video> videos = videoRepository.findByCourseIdInChapterOrder(courseId);
        if (videos.isEmpty()) {
            return List.of();
        }
        List<Long> videoIds = videos.stream().map(Video::getId).toList();
        List<VideoPdf> allPdfs = videoPdfRepository.findByVideo_IdInOrderByDisplayOrderAsc(videoIds);

        return videos.stream().map(video -> {
            List<PdfResponse> pdfResponses = allPdfs.stream()
                    .filter(pdf -> pdf.getVideo().getId().equals(video.getId()))
                    .map(pdf -> new PdfResponse(
                            pdf.getId(),
                            pdf.getTitle(),
                            pdf.getPdfType(),
                            pdf.getFileUrl(),
                            pdf.getDisplayOrder()
                    ))
                    .toList();
            return new VideoResponse(
                    video.getId(),
                    video.getVideoId(),
                    video.getTitle(),
                    video.getThumbnailUrl(),
                    video.getDuration(),
                    video.getDisplayOrder(),
                    pdfResponses
            );
        }).toList();
    }

    @Transactional
    public void deleteVideo(Long videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found"));
        List<VideoPdf> pdfs = videoPdfRepository.findByVideo_IdInOrderByDisplayOrderAsc(List.of(videoId));
        for (VideoPdf pdf : pdfs) {
            storageService.deleteByStorageUrl(pdf.getFileUrl());
        }
        videoRepository.deleteById(videoId);
    }

    @Transactional
    public VideoResponse updateVideo(Long videoId, String title, String duration, Integer displayOrder) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Video not found"));

        if (title != null && !title.isBlank()) video.setTitle(title);
        if (duration != null && !duration.isBlank()) video.setDuration(duration);
        if (displayOrder != null) {
            if (displayOrder < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "displayOrder must be >= 1");
            video.setDisplayOrder(displayOrder);
        }
        Video saved = videoRepository.save(video);

        List<PdfResponse> pdfResponses = saved.getPdfs().stream()
                .map(pdf -> new PdfResponse(
                        pdf.getId(),
                        pdf.getTitle(),
                        pdf.getPdfType(),
                        pdf.getFileUrl(),
                        pdf.getDisplayOrder()
                ))
                .toList();

        return new VideoResponse(
                saved.getId(),
                saved.getVideoId(),
                saved.getTitle(),
                saved.getThumbnailUrl(),
                saved.getDuration(),
                saved.getDisplayOrder(),
                pdfResponses
        );
    }
}
