package com.maths.teacher.catalog.service;

import com.maths.teacher.catalog.exception.ErrorMessages;
import com.maths.teacher.catalog.repository.VideoPdfRepository;
import com.maths.teacher.catalog.web.dto.PdfDownloadResponse;
import com.maths.teacher.storage.S3PresignedUrlService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PdfDownloadService {

    private final VideoPdfRepository videoPdfRepository;
    private final S3PresignedUrlService presignedUrlService;

    public PdfDownloadService(
            VideoPdfRepository videoPdfRepository,
            S3PresignedUrlService presignedUrlService
    ) {
        this.videoPdfRepository = videoPdfRepository;
        this.presignedUrlService = presignedUrlService;
    }

    /**
     * Returns a presigned download URL for the given PDF. No authorization required;
     * all users may download PDFs tagged to a video.
     */
    public PdfDownloadResponse getDownloadUrl(Long videoId, Long pdfId) {
        var pdf = videoPdfRepository.findByIdAndVideo_Id(pdfId, videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorMessages.PDF_NOT_FOUND));

        var presigned = presignedUrlService.createPresignedDownloadUrl(pdf.getFileUrl());
        return new PdfDownloadResponse(presigned.url(), presigned.expiresInSeconds());
    }
}
