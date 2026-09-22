package com.maths.teacher.catalog.web.admin;

import com.maths.teacher.catalog.service.AdminService;
import com.maths.teacher.catalog.web.dto.VideoResponse;
import com.maths.teacher.catalog.web.dto.YouTubeInfoResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/admin/videos")
@PreAuthorize("hasRole('ADMIN')")
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
            @RequestParam(required = false) @Schema(description = "Course ID (optional when chapterId is given)") Long courseId,
            @RequestParam(required = false) @Schema(description = "Chapter to add the video to; omitted = the course's first chapter") Long chapterId,
            @RequestParam(required = false) @Schema(description = "Duration (e.g. 12:45)", requiredMode = Schema.RequiredMode.NOT_REQUIRED) String duration,
            @RequestParam(required = false) @Schema(description = "Position in the chapter; omitted = appended at the end") Integer displayOrder,
            @RequestPart(required = false) @Schema(description = "Notes PDF file", type = "string", format = "binary") MultipartFile notesPdf,
            @RequestPart(required = false) @Schema(description = "Solved practice set PDF", type = "string", format = "binary") MultipartFile solvedPracticeSetPdf,
            @RequestPart(required = false) @Schema(description = "Annotated practice set PDF", type = "string", format = "binary") MultipartFile annotatedPracticeSetPdf
    ) {
        return adminService.createVideoLesson(
                youtubeVideoLink,
                title,
                courseId,
                chapterId,
                duration,
                displayOrder,
                notesPdf,
                solvedPracticeSetPdf,
                annotatedPracticeSetPdf
        );
    }

    /** Video id, thumbnail and title for a pasted link — the panel uses it to fill the title in. */
    @GetMapping("/youtube-info")
    public YouTubeInfoResponse getYouTubeInfo(@RequestParam String url) {
        return adminService.getYouTubeInfo(url);
    }

    @DeleteMapping("/{videoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVideo(@PathVariable Long videoId) {
        adminService.deleteVideo(videoId);
    }

    @PatchMapping(value = "/{videoId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public VideoResponse updateVideo(
            @PathVariable Long videoId,
            @RequestBody Map<String, Object> body
    ) {
        String title = body.containsKey("title") ? (String) body.get("title") : null;
        String duration = body.containsKey("duration") ? (String) body.get("duration") : null;
        Integer displayOrder = body.containsKey("displayOrder")
                ? ((Number) body.get("displayOrder")).intValue()
                : null;
        return adminService.updateVideo(videoId, title, duration, displayOrder);
    }
}
