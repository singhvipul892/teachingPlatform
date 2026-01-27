package com.maths.teacher.catalog.service;

import com.maths.teacher.catalog.exception.ErrorMessages;
import com.maths.teacher.catalog.repository.VideoPdfRepository;
import com.maths.teacher.catalog.web.dto.PdfDownloadResponse;
import com.maths.teacher.security.VideoAccessService;
import com.maths.teacher.storage.S3PresignedUrlService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PdfDownloadService {

    private final VideoPdfRepository videoPdfRepository;
    private final VideoAccessService videoAccessService;
    private final S3PresignedUrlService presignedUrlService;

    public PdfDownloadService(
            VideoPdfRepository videoPdfRepository,
            VideoAccessService videoAccessService,
            S3PresignedUrlService presignedUrlService
    ) {
        this.videoPdfRepository = videoPdfRepository;
        this.videoAccessService = videoAccessService;
        this.presignedUrlService = presignedUrlService;
    }

    public PdfDownloadResponse getDownloadUrl(Long videoId, Long pdfId, String userId) {
        if (!videoAccessService.hasAccess(userId, videoId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ErrorMessages.ACCESS_DENIED);
        }

        var pdf = videoPdfRepository.findByIdAndVideo_Id(pdfId, videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorMessages.PDF_NOT_FOUND));

        var presigned = presignedUrlService.createPresignedDownloadUrl(pdf.getFileUrl());
        return new PdfDownloadResponse(presigned.url(), presigned.expiresInSeconds());
    }
}
