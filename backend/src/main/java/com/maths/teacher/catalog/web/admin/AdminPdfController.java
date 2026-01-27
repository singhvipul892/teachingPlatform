package com.maths.teacher.catalog.web.admin;

import com.maths.teacher.catalog.service.AdminPdfService;
import com.maths.teacher.catalog.web.dto.PdfResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/videos/{videoId}/pdfs")
public class AdminPdfController {

    private final AdminPdfService adminService;

    public AdminPdfController(AdminPdfService adminService) {
        this.adminService = adminService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PdfResponse addPdf(
            @PathVariable Long videoId,
            @RequestParam String title,
            @RequestParam String pdfType,
            @RequestParam Integer displayOrder,
            @RequestParam MultipartFile file
    ) {
        return adminService.addPdf(videoId, title, pdfType, displayOrder, file);
    }

    @PutMapping("/{pdfId}")
    public PdfResponse updatePdf(
            @PathVariable Long videoId,
            @PathVariable Long pdfId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String pdfType,
            @RequestParam(required = false) Integer displayOrder,
            @RequestParam(required = false) MultipartFile file
    ) {
        return adminService.updatePdf(videoId, pdfId, title, pdfType, displayOrder, file);
    }

    @DeleteMapping("/{pdfId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePdf(@PathVariable Long videoId, @PathVariable Long pdfId) {
        adminService.deletePdf(videoId, pdfId);
    }
}
