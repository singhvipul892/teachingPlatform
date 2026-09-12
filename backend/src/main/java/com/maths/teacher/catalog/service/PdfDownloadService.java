package com.maths.teacher.catalog.service;

import com.maths.teacher.catalog.exception.ErrorMessages;
import com.maths.teacher.catalog.repository.VideoPdfRepository;
import com.maths.teacher.catalog.repository.VideoRepository;
import com.maths.teacher.catalog.web.dto.PdfDownloadResponse;
import com.maths.teacher.storage.S3PresignedUrlService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PdfDownloadService {

    private final VideoPdfRepository videoPdfRepository;
    private final VideoRepository videoRepository;
    private final CourseAccessGuard courseAccessGuard;
    private final S3PresignedUrlService presignedUrlService;

    public PdfDownloadService(
            VideoPdfRepository videoPdfRepository,
            VideoRepository videoRepository,
            CourseAccessGuard courseAccessGuard,
            S3PresignedUrlService presignedUrlService
    ) {
        this.videoPdfRepository = videoPdfRepository;
        this.videoRepository = videoRepository;
        this.courseAccessGuard = courseAccessGuard;
        this.presignedUrlService = presignedUrlService;
    }

    /**
     * Returns a presigned download URL for the given PDF, provided the student
     * currently has access to the course the PDF's video belongs to.
     *
     * <p>The access check comes first on purpose: a presigned URL is a bearer
     * token for the file, so it must not be minted before the caller has been
     * shown to be entitled to it.
     */
    public PdfDownloadResponse getDownloadUrl(Long videoId, Long pdfId, Long userId) {
        var pdf = videoPdfRepository.findByIdAndVideo_Id(pdfId, videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorMessages.PDF_NOT_FOUND));

        var video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorMessages.PDF_NOT_FOUND));

        courseAccessGuard.requireAccess(userId, video.getCourseId());

        var presigned = presignedUrlService.createPresignedDownloadUrl(pdf.getFileUrl());
        return new PdfDownloadResponse(presigned.url(), presigned.expiresInSeconds());
    }
}
