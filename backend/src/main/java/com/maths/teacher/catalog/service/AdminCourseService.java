package com.maths.teacher.catalog.service;

import com.maths.teacher.auth.domain.User;
import com.maths.teacher.auth.repository.UserRepository;
import com.maths.teacher.catalog.web.dto.AdminCourseResponse;
import com.maths.teacher.catalog.web.dto.CreateCourseRequest;
import com.maths.teacher.catalog.web.dto.StudentResponse;
import com.maths.teacher.catalog.web.dto.TagStudentRequest;
import com.maths.teacher.catalog.web.dto.UpdateCourseRequest;
import com.maths.teacher.catalog.web.dto.UpdateStudentExpiryRequest;
import com.maths.teacher.payment.domain.AccessExpiry;
import com.maths.teacher.payment.domain.Course;
import com.maths.teacher.payment.domain.PaymentOrder;
import com.maths.teacher.payment.domain.Purchase;
import com.maths.teacher.payment.repository.CourseRepository;
import com.maths.teacher.payment.repository.PaymentOrderRepository;
import com.maths.teacher.payment.repository.StudentPayments;
import com.maths.teacher.payment.repository.PurchaseRepository;
import com.maths.teacher.storage.S3StorageService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final PaymentOrderRepository paymentOrderRepository;
    private final UserRepository userRepository;
    private final S3StorageService storageService;

    public AdminCourseService(
            CourseRepository courseRepository,
            PurchaseRepository purchaseRepository,
            PaymentOrderRepository paymentOrderRepository,
            UserRepository userRepository,
            S3StorageService storageService
    ) {
        this.courseRepository = courseRepository;
        this.purchaseRepository = purchaseRepository;
        this.paymentOrderRepository = paymentOrderRepository;
        this.userRepository = userRepository;
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
                request.getActive(),
                request.getValidityDays() == null ? 0 : request.getValidityDays()
        );
        course.setEnrolmentClosesOn(request.getEnrolmentClosesOn());

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
        if (request.getValidityDays() != null) {
            // Deliberately does not touch existing purchases: students who already
            // bought keep the expiry they were sold.
            course.setValidityDays(request.getValidityDays());
        }
        if (request.getEnrolmentClosesOn() != null) {
            // Closing enrolment never touches who is already in: it only stops
            // new people joining from that day on.
            course.setEnrolmentClosesOn(request.getEnrolmentClosesOn());
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
    public List<StudentResponse> getEnrolledStudents(Long courseId, boolean includeRemoved) {
        logger.info("Fetching students for course: {} (includeRemoved={})", courseId, includeRemoved);

        // Verify course exists
        courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));

        // Fetch purchases for this course
        List<Purchase> purchases = includeRemoved
                ? purchaseRepository.findByCourseIdIncludingUnenrolled(courseId)
                : purchaseRepository.findByCourseId(courseId);
        Instant now = Instant.now();

        // One grouped query for the whole roster rather than a ledger lookup per
        // student. Absent from the map means they never paid — a free seat.
        Map<Long, StudentPayments> payments = paymentOrderRepository.summarisePayersOn(courseId).stream()
                .collect(Collectors.toMap(StudentPayments::userId, Function.identity()));

        return purchases.stream()
                .map(purchase -> {
                    User user = purchase.getUser();
                    StudentPayments paid = payments.get(user.getId());
                    return new StudentResponse(
                            user.getId(),
                            user.getFirstName(),
                            user.getLastName(),
                            user.getEmail(),
                            user.getMobileNumber(),
                            purchase.getPurchasedAt(),
                            AccessExpiry.lastDay(purchase.getExpiresAt()),
                            purchase.isExpired(now),
                            AccessExpiry.dayOf(purchase.getUnenrolledAt()),
                            paid == null ? 0 : paid.paymentCount(),
                            paid == null ? null : AccessExpiry.dayOf(paid.firstPaidAt())
                    );
                })
                .toList();
    }

    /**
     * Undoes a removal. This is the "I clicked the wrong row" path, so unlike
     * tagging it records no payment: nobody paid anything, the student simply
     * gets back the enrolment they already had, expiry and all.
     *
     * <p>Only a removed student can be restored. Someone whose access merely
     * lapsed has to be tagged again, because that is a real second payment.
     */
    @Transactional
    public StudentResponse restoreStudent(Long courseId, Long userId) {
        courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));

        Purchase purchase = purchaseRepository.findIncludingUnenrolled(userId, courseId)
                .filter(p -> !p.isEnrolled())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No removed enrolment to restore for this student"));

        purchase.restore();
        purchaseRepository.save(purchase);
        logger.info("Admin restored student {} to course {} (expiry unchanged: {})",
                userId, courseId, purchase.getExpiresAt());

        User user = purchase.getUser();
        return new StudentResponse(user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getMobileNumber(), purchase.getPurchasedAt(),
                AccessExpiry.lastDay(purchase.getExpiresAt()), purchase.isExpired(Instant.now()));
    }

    /**
     * Manually tags (enrolls) a student in a course.
     * Used for direct/offline payments. Works without a Razorpay payment flow.
     */
    @Transactional
    public StudentResponse tagStudent(Long courseId, TagStudentRequest request) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));

        // Deliberately NOT blocked when the course is off sale or its enrolment
        // window has closed. Students are stopped at createOrder; an admin is
        // recording something that already happened, and a student who paid before
        // the course closed must not be stranded because the paperwork came late.
        // The panel warns instead.
        if (!course.isEnrolmentOpen(java.time.LocalDate.now(AccessExpiry.ZONE))) {
            logger.info("Admin tagging into a closed course {} (active={}, closes={})",
                    courseId, course.isActive(), course.getEnrolmentClosesOn());
        }

        Long userId = request.getUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Instant now = Instant.now();
        // Deliberately looks through unenrolment: a student who was removed has a
        // row that has to be reused, because only one may exist per (user, course).
        Purchase existing = purchaseRepository.findIncludingUnenrolled(userId, courseId).orElse(null);
        if (existing != null && existing.isEnrolled() && existing.isActive(now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Student is already enrolled in this course");
        }

        // Was this a payment? A tag usually means the student paid off the books -
        // cash, UPI, a bank transfer - but it is also how a free seat is granted and
        // how a mistaken removal gets undone by hand. Only the admin knows which,
        // so for a student who was removed we refuse to guess: guessing is how a
        // correction turns into a sale that never happened.
        boolean paid;
        if (request.getRecordPayment() != null) {
            paid = request.getRecordPayment();
        } else if (existing != null && !existing.isEnrolled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This student was removed from the course. Say whether they paid again "
                            + "(recordPayment true) or use Restore to undo the removal.");
        } else {
            // A first-time tag with nothing said is the ordinary offline sale.
            paid = true;
        }

        PaymentOrder.Source source = paid ? PaymentOrder.Source.OFFLINE : PaymentOrder.Source.COMPLIMENTARY;

        // The reference the admin typed, if any. Entering the same one twice for
        // one course is one payment being recorded as two, which would overstate
        // revenue; the same reference on another course is a bundle, and fine.
        String reference = (request.getRazorpayTransactionId() != null
                && !request.getRazorpayTransactionId().isBlank())
                ? request.getRazorpayTransactionId().trim()
                : null;
        if (paid && reference != null && paymentOrderRepository.referenceUsedOn(courseId, reference)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payment reference " + reference + " is already recorded against this course. "
                            + "Use a different reference, or Restore if you meant to undo a removal.");
        }

        // The money is real, so each paid tag records its own payment, stamped with
        // the moment it happened. Tagging the same student again after untagging
        // means they paid a second time: that has to append to the history, not land
        // on the id the first payment already took.
        long paidAt = now.toEpochMilli();
        String orderId = "ADMIN-ORDER-" + userId + "-" + courseId + "-" + paidAt;
        String txnId = reference != null ? reference : "ADMIN-" + userId + "-" + courseId + "-" + paidAt;

        // What the student actually handed over: the course price as it stands
        // today, so offline sales show up in the books at their real value instead
        // of as zero. A free seat is worth exactly nothing and says so.
        int amountPaise = paid ? course.getPricePaise() : 0;

        // The row is written either way - purchases.razorpay_order_id is NOT NULL
        // and references this table, so an enrolment cannot exist without one. A
        // COMPLIMENTARY order at zero is how "access granted, nobody paid" is
        // recorded: it adds nothing to revenue and still leaves the audit trail.
        PaymentOrder adminOrder = new PaymentOrder(orderId, userId, courseId, amountPaise,
                course.getCurrency(), source);
        adminOrder.setReference(reference);
        adminOrder.markPaid();
        paymentOrderRepository.save(adminOrder);

        Purchase purchase;
        if (existing != null) {
            // A row is already here because the student was removed, or because
            // their access simply lapsed. Either way the unique (user, course)
            // index means this enrolment has to be restarted in place; the count
            // of how many times they paid lives in payment_orders, which the
            // order above just added to.
            existing.renew(orderId, txnId, amountPaise, course.getCurrency(), course.getValidityDays());
            purchase = purchaseRepository.save(existing);
            logger.info("Admin re-enrolled student {} on course {} until {} (order {}, {})",
                    userId, courseId, purchase.getExpiresAt(), orderId, source);
        } else {
            purchase = new Purchase(userId, courseId, orderId, txnId, amountPaise,
                    course.getCurrency(), course.getValidityDays());
            purchase.setUser(user);
            purchase = purchaseRepository.save(purchase);
            logger.info("Admin tagged student {} to course {} until {} (order {}, {})",
                    userId, courseId, purchase.getExpiresAt(), orderId, source);
        }

        return new StudentResponse(user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getMobileNumber(), purchase.getPurchasedAt(),
                AccessExpiry.lastDay(purchase.getExpiresAt()), purchase.isExpired(now));
    }

    /**
     * Untags (removes) a student from a course.
     * Works for both admin-tagged and Razorpay-paid enrollments.
     */
    @Transactional
    public void untagStudent(Long courseId, Long userId) {
        courseRepository.findById(courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found"));

        Purchase purchase = purchaseRepository.findEnrolled(userId, courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student is not enrolled in this course"));

        // Stamped, not deleted: the enrolment stays on record, and the payments
        // behind it stay in payment_orders where they can still be counted.
        purchase.unenrol(Instant.now());
        purchaseRepository.save(purchase);
        logger.info("Admin untagged student {} from course {}", userId, courseId);
    }

    /**
     * Overrides one student's expiry for a course. A null date grants lifetime
     * access; otherwise access runs to the end of the given day, Indian time, the
     * same way a purchase-time expiry does.
     */
    @Transactional
    public StudentResponse updateStudentExpiry(Long courseId, Long userId, UpdateStudentExpiryRequest request) {
        Purchase purchase = purchaseRepository.findEnrolled(userId, courseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student is not enrolled in this course"));

        Instant expiresAt = request.getExpiryDate() == null
                ? null
                : AccessExpiry.endOfDay(request.getExpiryDate());
        purchase.setExpiresAt(expiresAt);
        purchaseRepository.save(purchase);

        logger.info("Admin set expiry for student {} on course {} to {}", userId, courseId, expiresAt);

        User user = purchase.getUser();
        return new StudentResponse(user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), user.getMobileNumber(), purchase.getPurchasedAt(),
                AccessExpiry.lastDay(purchase.getExpiresAt()), purchase.isExpired(Instant.now()));
    }

    /**
     * Converts a Course entity to AdminCourseResponse DTO.
     */
    private AdminCourseResponse toAdminResponse(Course course) {
        long studentCount = purchaseRepository.countByCourseId(course.getId());
        long activeStudentCount = purchaseRepository.countActiveByCourseId(course.getId(), Instant.now());
        return new AdminCourseResponse(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                course.getPricePaise(),
                course.getCurrency(),
                course.getThumbnailUrl(),
                course.isActive(),
                course.getValidityDays(),
                course.getEnrolmentClosesOn(),
                course.isEnrolmentOpen(java.time.LocalDate.now(AccessExpiry.ZONE)),
                (int) studentCount,
                (int) activeStudentCount,
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
