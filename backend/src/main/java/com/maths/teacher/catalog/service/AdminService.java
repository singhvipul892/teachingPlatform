package com.maths.teacher.catalog.service;

import com.maths.teacher.catalog.domain.Video;
import com.maths.teacher.catalog.domain.VideoPdf;
import com.maths.teacher.catalog.repository.VideoPdfRepository;
import com.maths.teacher.catalog.repository.VideoRepository;
import com.maths.teacher.catalog.util.YouTubeUrlExtractor;
import com.maths.teacher.catalog.web.dto.PdfResponse;
import com.maths.teacher.catalog.web.dto.VideoResponse;
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

    public AdminService(
            VideoRepository videoRepository,
            VideoPdfRepository videoPdfRepository,
            S3StorageService storageService,
            YouTubeUrlExtractor youtubeUrlExtractor
    ) {
        this.videoRepository = videoRepository;
        this.videoPdfRepository = videoPdfRepository;
        this.storageService = storageService;
        this.youtubeUrlExtractor = youtubeUrlExtractor;
    }

    /**
     * Creates a video lesson from YouTube link with optional PDFs.
     * Extracts video ID from YouTube URL and generates thumbnail URL automatically.
     *
     * @param youtubeVideoLink YouTube video URL (any format)
     * @param title Video title
     * @param courseId Course ID to associate video with
     * @param duration Video duration (e.g., "12:45")
     * @param displayOrder Display order within course
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
            String duration,
            Integer displayOrder,
            MultipartFile notesPdf,
            MultipartFile solvedPracticeSetPdf,
            MultipartFile annotatedPracticeSetPdf
    ) {
        // Extract video ID from YouTube URL
        String videoId = youtubeUrlExtractor.extractVideoId(youtubeVideoLink);
        String thumbnailUrl = youtubeUrlExtractor.generateThumbnailUrl(videoId);

        // Create video entity
        var video = new Video(null, videoId, title, courseId, thumbnailUrl, duration, displayOrder);
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
        List<Video> videos = videoRepository.findByCourseIdOrderByDisplayOrderAsc(courseId);
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
