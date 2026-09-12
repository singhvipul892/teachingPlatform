package com.maths.teacher.catalog.web.admin;

import com.maths.teacher.catalog.service.AdminCourseService;
import com.maths.teacher.catalog.service.AdminReportService;
import com.maths.teacher.catalog.service.AdminService;
import com.maths.teacher.catalog.web.dto.AdminCourseResponse;
import com.maths.teacher.catalog.web.dto.CreateCourseRequest;
import com.maths.teacher.catalog.web.dto.PaymentRecordResponse;
import com.maths.teacher.catalog.web.dto.StudentResponse;
import com.maths.teacher.catalog.web.dto.TagStudentRequest;
import com.maths.teacher.catalog.web.dto.UpdateCourseRequest;
import com.maths.teacher.catalog.web.dto.UpdateStudentExpiryRequest;
import com.maths.teacher.catalog.web.dto.VideoResponse;
import java.util.List;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin API endpoints for course management.
 * All endpoints require ADMIN role.
 */
@RestController
@RequestMapping("/api/admin/courses")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCourseController {

    private final AdminCourseService adminCourseService;
    private final AdminService adminService;
    private final AdminReportService adminReportService;

    public AdminCourseController(AdminCourseService adminCourseService, AdminService adminService,
                                 AdminReportService adminReportService) {
        this.adminCourseService = adminCourseService;
        this.adminService = adminService;
        this.adminReportService = adminReportService;
    }

    /**
     * Creates a new course with an optional thumbnail image.
     *
     * @param title       course title (required)
     * @param description course description
     * @param pricePaise  price in paise (required)
     * @param currency    currency code (default: INR)
     * @param active      whether course is active (default: true)
     * @param validityDays days of access a purchase gets; 0 (default) = lifetime
     * @param enrolmentClosesOn last day a NEW student may join (optional, yyyy-MM-dd);
     *                    unrelated to validityDays, which is how long access lasts
     * @param thumbnail   thumbnail image file (optional, JPEG/PNG max 5MB)
     * @return created course
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AdminCourseResponse createCourse(
            @RequestParam String title,
            @RequestParam(required = false, defaultValue = "") String description,
            @RequestParam int pricePaise,
            @RequestParam(required = false, defaultValue = "INR") String currency,
            @RequestParam(required = false, defaultValue = "true") boolean active,
            @RequestParam(required = false, defaultValue = "0") int validityDays,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate enrolmentClosesOn,
            @RequestPart(required = false) MultipartFile thumbnail
    ) {
        CreateCourseRequest request = new CreateCourseRequest(
                title, description, pricePaise, currency, active, validityDays
        );
        request.setEnrolmentClosesOn(enrolmentClosesOn);
        return adminCourseService.createCourse(request, thumbnail);
    }

    /**
     * Updates an existing course.
     *
     * @param courseId    course ID
     * @param title       new title (optional)
     * @param description new description (optional)
     * @param pricePaise  new price in paise (optional)
     * @param currency    new currency (optional)
     * @param active      new active status (optional)
     * @param validityDays new validity in days (optional); applies to future purchases only
     * @param enrolmentClosesOn new last day for NEW students to join (optional)
     * @param thumbnail   new thumbnail image (optional)
     * @return updated course
     */
    @PutMapping(value = "/{courseId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminCourseResponse updateCourse(
            @PathVariable Long courseId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Integer pricePaise,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer validityDays,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate enrolmentClosesOn,
            @RequestPart(required = false) MultipartFile thumbnail
    ) {
        UpdateCourseRequest request = new UpdateCourseRequest(
                title, description, pricePaise, currency, active, validityDays
        );
        request.setEnrolmentClosesOn(enrolmentClosesOn);
        return adminCourseService.updateCourse(courseId, request, thumbnail);
    }

    /**
     * Partially updates a course's non-file fields (title, description, pricePaise, currency, active).
     * Use PUT when updating the thumbnail image.
     *
     * @param courseId course ID
     * @param request  fields to update (all optional)
     * @return updated course
     */
    @PatchMapping(value = "/{courseId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AdminCourseResponse patchCourse(
            @PathVariable Long courseId,
            @RequestBody UpdateCourseRequest request
    ) {
        return adminCourseService.updateCourse(courseId, request, null);
    }

    /**
     * Deletes (soft delete) a course.
     *
     * @param courseId course ID
     */
    @DeleteMapping("/{courseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCourse(@PathVariable Long courseId) {
        adminCourseService.deleteCourse(courseId);
    }

    /**
     * Gets all courses (with admin-specific fields).
     *
     * @return list of all courses
     */
    @GetMapping
    public List<AdminCourseResponse> getAllCourses() {
        return adminCourseService.getAllCourses();
    }

    /**
     * Gets a specific course by ID.
     *
     * @param courseId course ID
     * @return course details
     */
    @GetMapping("/{courseId}")
    public AdminCourseResponse getCourseById(@PathVariable Long courseId) {
        return adminCourseService.getCourseById(courseId);
    }

    /**
     * Gets all students enrolled in a course.
     *
     * @param courseId course ID
     * @return list of enrolled students with purchase details
     */
    @GetMapping("/{courseId}/students")
    public List<StudentResponse> getEnrolledStudents(
            @PathVariable Long courseId,
            @RequestParam(defaultValue = "false") boolean includeRemoved
    ) {
        return adminCourseService.getEnrolledStudents(courseId, includeRemoved);
    }

    /**
     * Every payment this student made for this course, oldest first. Answers
     * "how many times did they buy it?", which the single enrolment row cannot.
     */
    @GetMapping("/{courseId}/students/{userId}/payments")
    public List<PaymentRecordResponse> getStudentPayments(
            @PathVariable Long courseId,
            @PathVariable Long userId
    ) {
        return adminReportService.getStudentPayments(courseId, userId);
    }

    /**
     * Undoes a removal, for when a student was untagged by mistake. Records no
     * payment — see AdminCourseService.restoreStudent.
     */
    @PostMapping("/{courseId}/students/{userId}/restore")
    public StudentResponse restoreStudent(@PathVariable Long courseId, @PathVariable Long userId) {
        return adminCourseService.restoreStudent(courseId, userId);
    }

    /**
     * Manually tags (enrolls) a student in a course.
     * Used for direct/offline payments. Optionally accepts a Razorpay transaction ID.
     */
    @PostMapping("/{courseId}/students")
    @ResponseStatus(HttpStatus.CREATED)
    public StudentResponse tagStudent(
            @PathVariable Long courseId,
            @RequestBody TagStudentRequest request
    ) {
        return adminCourseService.tagStudent(courseId, request);
    }

    /**
     * Overrides one student's expiry for a course. Send {"expiryDate": null} to
     * grant lifetime access; a date means access runs to the end of that day.
     */
    @PutMapping(value = "/{courseId}/students/{userId}/expiry", consumes = MediaType.APPLICATION_JSON_VALUE)
    public StudentResponse updateStudentExpiry(
            @PathVariable Long courseId,
            @PathVariable Long userId,
            @RequestBody UpdateStudentExpiryRequest request
    ) {
        return adminCourseService.updateStudentExpiry(courseId, userId, request);
    }

    /**
     * Untags (removes) a student from a course.
     * Works for both admin-tagged and Razorpay-paid enrollments.
     */
    @DeleteMapping("/{courseId}/students/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void untagStudent(@PathVariable Long courseId, @PathVariable Long userId) {
        adminCourseService.untagStudent(courseId, userId);
    }

    @GetMapping("/{courseId}/videos")
    public List<VideoResponse> getVideosForCourse(@PathVariable Long courseId) {
        return adminService.getVideosForCourse(courseId);
    }
}
