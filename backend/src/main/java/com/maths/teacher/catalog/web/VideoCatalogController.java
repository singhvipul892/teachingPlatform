package com.maths.teacher.catalog.web;

import com.maths.teacher.catalog.service.PdfDownloadService;
import com.maths.teacher.catalog.service.VideoCatalogService;
import com.maths.teacher.catalog.web.dto.PdfDownloadResponse;
import com.maths.teacher.catalog.web.dto.VideoResponse;
import com.maths.teacher.security.AuthService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class VideoCatalogController {

    private final VideoCatalogService videoCatalogService;
    private final PdfDownloadService pdfDownloadService;
    private final AuthService authService;

    public VideoCatalogController(
            VideoCatalogService videoCatalogService,
            PdfDownloadService pdfDownloadService,
            AuthService authService
    ) {
        this.videoCatalogService = videoCatalogService;
        this.pdfDownloadService = pdfDownloadService;
        this.authService = authService;
    }

    /**
     * Returns all videos for a purchased course.
     * JWT required — user must have purchased this course to access its videos.
     */
    @GetMapping("/courses/{courseId}/videos")
    public List<VideoResponse> getVideosByCourse(
            @PathVariable Long courseId,
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = Long.parseLong(authService.requireUserId(authHeader));
        return videoCatalogService.getVideosByCourse(courseId, userId);
    }

    /**
     * Returns a presigned S3 URL for downloading a PDF.
     * Can be called with or without JWT (optional access control).
     */
    @GetMapping("/videos/{videoId}/pdfs/{pdfId}/download")
    public PdfDownloadResponse downloadPdf(
            @PathVariable Long videoId,
            @PathVariable Long pdfId
    ) {
        return pdfDownloadService.getDownloadUrl(videoId, pdfId);
    }
}
