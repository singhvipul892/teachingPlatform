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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
     * @param section Section/chapter name
     * @param duration Video duration (e.g., "12:45")
     * @param displayOrder Display order within section
     * @param notesPdf Notes PDF (optional)
     * @param solvedPracticeSetPdf Solved Practice Set PDF (optional)
     * @param annotatedPracticeSetPdf Annotated Practice Set PDF (optional)
     * @return Created video with PDFs
     */
    @Transactional
    public VideoResponse createVideoLesson(
            String youtubeVideoLink,
            String title,
            String section,
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
        var video = new Video(null, videoId, title, section, thumbnailUrl, duration, displayOrder);
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
}
