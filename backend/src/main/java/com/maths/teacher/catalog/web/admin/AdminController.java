package com.maths.teacher.catalog.web.admin;

import com.maths.teacher.catalog.service.AdminService;
import com.maths.teacher.catalog.web.dto.VideoResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/videos")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * Creates a video lesson with YouTube link and optional PDFs.
     * Use form fields for text values and file parts for PDFs (multipart/form-data).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public VideoResponse createVideoLesson(
            @RequestParam @Schema(description = "YouTube video URL", example = "https://www.youtube.com/watch?v=dQw4w9WgXcQ", requiredMode = Schema.RequiredMode.REQUIRED) String youtubeVideoLink,
            @RequestParam @Schema(description = "Video title", requiredMode = Schema.RequiredMode.REQUIRED) String title,
            @RequestParam @Schema(description = "Section/chapter name", requiredMode = Schema.RequiredMode.REQUIRED) String section,
            @RequestParam @Schema(description = "Duration (e.g. 12:45)", requiredMode = Schema.RequiredMode.REQUIRED) String duration,
            @RequestParam @Schema(description = "Display order within section", requiredMode = Schema.RequiredMode.REQUIRED) Integer displayOrder,
            @RequestPart(required = false) @Schema(description = "Notes PDF file", type = "string", format = "binary") MultipartFile notesPdf,
            @RequestPart(required = false) @Schema(description = "Solved practice set PDF", type = "string", format = "binary") MultipartFile solvedPracticeSetPdf,
            @RequestPart(required = false) @Schema(description = "Annotated practice set PDF", type = "string", format = "binary") MultipartFile annotatedPracticeSetPdf
    ) {
        return adminService.createVideoLesson(
                youtubeVideoLink,
                title,
                section,
                duration,
                displayOrder,
                notesPdf,
                solvedPracticeSetPdf,
                annotatedPracticeSetPdf
        );
    }
}
