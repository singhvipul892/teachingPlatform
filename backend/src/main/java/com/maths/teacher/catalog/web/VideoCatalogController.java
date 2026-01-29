package com.maths.teacher.catalog.web;

import com.maths.teacher.catalog.service.PdfDownloadService;
import com.maths.teacher.catalog.service.VideoCatalogService;
import com.maths.teacher.catalog.web.dto.PdfDownloadResponse;
import com.maths.teacher.catalog.web.dto.SectionResponse;
import com.maths.teacher.catalog.web.dto.VideoResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class VideoCatalogController {

    private final VideoCatalogService videoCatalogService;
    private final PdfDownloadService pdfDownloadService;

    public VideoCatalogController(
            VideoCatalogService videoCatalogService,
            PdfDownloadService pdfDownloadService
    ) {
        this.videoCatalogService = videoCatalogService;
        this.pdfDownloadService = pdfDownloadService;
    }

    @GetMapping("/sections")
    public List<SectionResponse> getSections() {
        return videoCatalogService.getSections();
    }

    @GetMapping("/sections/{section}/videos")
    public List<VideoResponse> getVideosBySection(@PathVariable String section) {
        return videoCatalogService.getVideosBySection(section);
    }

    @GetMapping("/videos/{videoId}/pdfs/{pdfId}/download")
    public PdfDownloadResponse downloadPdf(
            @PathVariable Long videoId,
            @PathVariable Long pdfId
    ) {
        return pdfDownloadService.getDownloadUrl(videoId, pdfId);
    }
}
