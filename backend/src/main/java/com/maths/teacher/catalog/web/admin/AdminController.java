package com.maths.teacher.catalog.web.admin;

import com.maths.teacher.catalog.service.AdminService;
import com.maths.teacher.catalog.web.admin.request.CreateVideoLessonRequest;
import com.maths.teacher.catalog.web.dto.VideoResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/videos")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * Creates a video lesson with YouTube link and optional PDFs.
     * 
     * @param request Request containing YouTube link, video details, and optional PDFs
     * @return Created video with PDFs
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public VideoResponse createVideoLesson(@ModelAttribute CreateVideoLessonRequest request) {
        return adminService.createVideoLesson(
                request.getYoutubeVideoLink(),
                request.getTitle(),
                request.getSection(),
                request.getDuration(),
                request.getDisplayOrder(),
                request.getNotesPdf(),
                request.getSolvedPracticeSetPdf(),
                request.getAnnotatedPracticeSetPdf()
        );
    }
}
