package com.maths.teacher.catalog.service;

import com.maths.teacher.auth.domain.User;
import com.maths.teacher.catalog.web.dto.AdminCourseResponse;
import com.maths.teacher.catalog.web.dto.CreateCourseRequest;
import com.maths.teacher.catalog.web.dto.StudentResponse;
import com.maths.teacher.catalog.web.dto.UpdateCourseRequest;
import com.maths.teacher.payment.domain.Course;
import com.maths.teacher.payment.domain.Purchase;
import com.maths.teacher.payment.repository.CourseRepository;
import com.maths.teacher.payment.repository.PurchaseRepository;
import com.maths.teacher.storage.S3StorageService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service for admin course management operations.
 * All methods require ADMIN role.
 */
@Service
public class AdminCourseService {

    private static final Logger logger = LoggerFactory.getLogger(AdminCourseService.class);

    private final CourseRepository courseRepository;
    private final PurchaseRepository purchaseRepository;
    private final S3StorageService storageService;

    public AdminCourseService(
            CourseRepository courseRepository,
            PurchaseRepository purchaseRepository,
            S3StorageService storageService
    ) {
        this.courseRepository = courseRepository;
        this.purchaseRepository = purchaseRepository;
        this.storageService = storageService;
    }

    /**
     * Creates a new course with an optional thumbnail image.
     */
    @Transactional
    public AdminCourseResponse createCourse(
            CreateCourseRequest request,
            MultipartFile thumbnail
    ) {
        logger.info("Creating new course: {}", request.getTitle());

        // Create course entity
        Course course = new Course(
                request.getTitle(),
                request.getDescription(),
                request.getPricePaise(),
                request.getCurrency(),
                "",  // thumbnailUrl will be set after S3 upload if file provided
                request.getActive()
        );

        // Upload thumbnail if provided
        if (thumbnail != null && !thumbnail.isEmpty()) {
            validateImageFile(thumbnail);
            String thumbnailUrl = storageService.uploadCourseThumbnail(null, thumbnail);
            course.setThumbnailUrl(thumbnailUrl);
        }

        // Save course to database
        Course saved = courseRepository.save(course);

        logger.info("Course created with ID: {}", saved.getId());

        return toAdminResponse(saved);
    }

    /**
     * Updates an existing course.
     */
    @Transactional
    public AdminCourseResponse updateCourse(
            Long courseId,
            UpdateCourseRequest request,
            MultipartFile thumbnail
    ) {
        logger.info("Updating course: {}", courseId);

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));

        // Update fields if provided in request
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            course.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            course.setDescription(request.getDescription());
        }
        if (request.getPricePaise() != null) {
            course.setPricePaise(request.getPricePaise());
        }
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            course.setCurrency(request.getCurrency());
        }
        if (request.getActive() != null) {
            course.setActive(request.getActive());
        }

        // Handle thumbnail update
        if (thumbnail != null && !thumbnail.isEmpty()) {
            validateImageFile(thumbnail);
            // Delete old thumbnail if exists
            if (course.getThumbnailUrl() != null && !course.getThumbnailUrl().isEmpty()) {
                try {
                    storageService.deleteByStorageUrl(course.getThumbnailUrl());
                } catch (Exception e) {
                    logger.warn("Failed to delete old thumbnail: {}", e.getMessage());
                }
            }
            String newThumbnailUrl = storageService.uploadCourseThumbnail(courseId, thumbnail);
            course.setThumbnailUrl(newThumbnailUrl);
        }

        // Save changes
        Course updated = courseRepository.save(course);

        logger.info("Course updated: {}", courseId);

        return toAdminResponse(updated);
    }

    /**
     * Soft deletes a course (sets active = false).
     */
    @Transactional
    public void deleteCourse(Long courseId) {
        logger.info("Deleting course: {}", courseId);

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));

        course.setActive(false);
        courseRepository.save(course);

        logger.info("Course soft-deleted: {}", courseId);
    }

    /**
     * Gets all courses (for admin listing).
     */
    public List<AdminCourseResponse> getAllCourses() {
        logger.info("Fetching all courses for admin");
        List<Course> courses = courseRepository.findAll();
        return courses.stream()
                .map(this::toAdminResponse)
                .toList();
    }

    /**
     * Gets a specific course by ID.
     */
    public AdminCourseResponse getCourseById(Long courseId) {
        logger.info("Fetching course: {}", courseId);
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));
        return toAdminResponse(course);
    }

    /**
     * Gets all students enrolled in a course.
     */
    public List<StudentResponse> getEnrolledStudents(Long courseId) {
        logger.info("Fetching enrolled students for course: {}", courseId);

        // Verify course exists
        courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));

        // Fetch purchases for this course
        List<Purchase> purchases = purchaseRepository.findByCourseId(courseId);

        return purchases.stream()
                .map(purchase -> {
                    User user = purchase.getUser();
                    return new StudentResponse(
                            user.getId(),
                            user.getFirstName(),
                            user.getLastName(),
                            user.getEmail(),
                            user.getMobileNumber(),
                            purchase.getPurchasedAt()
                    );
                })
                .toList();
    }

    /**
     * Converts a Course entity to AdminCourseResponse DTO.
     */
    private AdminCourseResponse toAdminResponse(Course course) {
        long studentCount = purchaseRepository.countByCourseId(course.getId());
        return new AdminCourseResponse(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getPricePaise(),
                course.getCurrency(),
                course.getThumbnailUrl(),
                course.isActive(),
                (int) studentCount,
                course.getCreatedAt()
        );
    }

    /**
     * Validates that a file is a valid image.
     */
    private void validateImageFile(MultipartFile file) {
        // Check file size (max 5MB)
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file size must not exceed 5MB");
        }

        // Check content type
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("image/jpeg") && !contentType.startsWith("image/png"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only JPEG and PNG images are allowed");
        }
    }
}
