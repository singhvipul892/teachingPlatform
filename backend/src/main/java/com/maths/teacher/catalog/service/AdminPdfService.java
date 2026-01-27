package com.maths.teacher.catalog.service;

import com.maths.teacher.catalog.domain.VideoPdf;
import com.maths.teacher.catalog.exception.ErrorMessages;
import com.maths.teacher.catalog.repository.VideoPdfRepository;
import com.maths.teacher.catalog.repository.VideoRepository;
import com.maths.teacher.catalog.web.dto.PdfResponse;
import com.maths.teacher.storage.S3StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminPdfService {

    private final VideoRepository videoRepository;
    private final VideoPdfRepository videoPdfRepository;
    private final S3StorageService storageService;

    public AdminPdfService(
            VideoRepository videoRepository,
            VideoPdfRepository videoPdfRepository,
            S3StorageService storageService
    ) {
        this.videoRepository = videoRepository;
        this.videoPdfRepository = videoPdfRepository;
        this.storageService = storageService;
    }

    public PdfResponse addPdf(
            Long videoId,
            String title,
            String pdfType,
            Integer displayOrder,
            MultipartFile file
    ) {
        var video = videoRepository.findById(videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorMessages.VIDEO_NOT_FOUND));

        // Check if PDF with this type already exists for this video
        videoPdfRepository.findByVideo_IdAndPdfType(videoId, pdfType)
                .ifPresent(existingPdf -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            ErrorMessages.pdfTypeAlreadyExists(pdfType)
                    );
                });

        var storageUrl = storageService.uploadPdf(videoId, file);

        var pdf = new VideoPdf(null, video, title, pdfType, storageUrl, displayOrder);
        var saved = videoPdfRepository.save(pdf);
        return new PdfResponse(saved.getId(), saved.getTitle(), saved.getPdfType(), saved.getFileUrl(), saved.getDisplayOrder());
    }

    public PdfResponse updatePdf(
            Long videoId,
            Long pdfId,
            String title,
            String pdfType,
            Integer displayOrder,
            MultipartFile file
    ) {
        var pdf = videoPdfRepository.findByIdAndVideo_Id(pdfId, videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorMessages.PDF_NOT_FOUND));

        if (title != null && !title.isBlank()) {
            pdf.setTitle(title);
        }
        if (pdfType != null && !pdfType.isBlank()) {
            pdf.setPdfType(pdfType);
        }
        if (displayOrder != null) {
            pdf.setDisplayOrder(displayOrder);
        }
        if (file != null && !file.isEmpty()) {
            storageService.deleteByStorageUrl(pdf.getFileUrl());
            var storageUrl = storageService.uploadPdf(videoId, file);
            pdf.setFileUrl(storageUrl);
        }

        var saved = videoPdfRepository.save(pdf);
        return new PdfResponse(saved.getId(), saved.getTitle(), saved.getPdfType(), saved.getFileUrl(), saved.getDisplayOrder());
    }

    public void deletePdf(Long videoId, Long pdfId) {
        var pdf = videoPdfRepository.findByIdAndVideo_Id(pdfId, videoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ErrorMessages.PDF_NOT_FOUND));
        storageService.deleteByStorageUrl(pdf.getFileUrl());
        videoPdfRepository.delete(pdf);
    }
}
