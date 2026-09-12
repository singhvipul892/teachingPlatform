package com.maths.teacher.payment.service;

import com.maths.teacher.auth.domain.User;
import com.maths.teacher.auth.repository.UserRepository;
import com.maths.teacher.payment.config.RazorpayProperties;
import com.maths.teacher.payment.domain.AccessExpiry;
import com.maths.teacher.payment.domain.Course;
import com.maths.teacher.payment.domain.PaymentOrder;
import com.maths.teacher.payment.domain.Purchase;
import com.maths.teacher.payment.repository.CourseRepository;
import com.maths.teacher.payment.repository.PaymentOrderRepository;
import com.maths.teacher.payment.repository.PurchaseRepository;
import com.maths.teacher.payment.web.CourseResponse;
import com.maths.teacher.payment.web.CreateOrderResponse;
import com.maths.teacher.payment.web.PaymentStatusResponse;
import com.maths.teacher.payment.web.UserCoursesResponse;
import com.maths.teacher.payment.web.VerifyPaymentResponse;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private final RazorpayClient razorpayClient;
    private final RazorpayProperties razorpayProperties;
    private final CourseRepository courseRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PurchaseRepository purchaseRepository;
    private final UserRepository userRepository;

    public PaymentService(
            RazorpayClient razorpayClient,
            RazorpayProperties razorpayProperties,
            CourseRepository courseRepository,
            PaymentOrderRepository paymentOrderRepository,
            PurchaseRepository purchaseRepository,
            UserRepository userRepository
    ) {
        this.razorpayClient = razorpayClient;
        this.razorpayProperties = razorpayProperties;
        this.courseRepository = courseRepository;
        this.paymentOrderRepository = paymentOrderRepository;
        this.purchaseRepository = purchaseRepository;
        this.userRepository = userRepository;
    }

    /** Returns all active courses (public endpoint). */
    public List<CourseResponse> listCourses() {
        return courseRepository.findByActiveTrue().stream()
                .map(c -> new CourseResponse(c.getId(), c.getTitle(), c.getDescription(), c.getPricePaise(),
                        c.getCurrency(), c.getThumbnailUrl(), c.getValidityDays()))
                .toList();
    }

    /**
     * Creates a Razorpay order for the given course.
     * Price comes from the course record — the client cannot influence it.
     */
    @Transactional
    public CreateOrderResponse createOrder(Long userId, Long courseId) {
        Course course = courseRepository.findById(courseId)
                .filter(Course::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Course not found."));

        // A dated batch stops taking new people after its last enrolment day.
        // Students already in it are untouched — this gate is only about joining.
        if (!course.isEnrolmentOpen(LocalDate.now(AccessExpiry.ZONE))) {
            logger.info("Enrolment closed for course {} (closed {})", courseId, course.getEnrolmentClosesOn());
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Enrolment for this course closed on "
                            + course.getEnrolmentClosesOn() + ".");
        }

        // An expired enrolment is allowed to buy again, and so is a student an
        // admin removed — neither has access. Only a live one is blocked.
        boolean hasLiveAccess = purchaseRepository.findEnrolled(userId, courseId)
                .map(p -> p.isActive(Instant.now()))
                .orElse(false);
        if (hasLiveAccess) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already purchased this course.");
        }

        int amountPaise = course.getPricePaise();
        String currency = course.getCurrency();

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amountPaise);
        orderRequest.put("currency", currency);
        orderRequest.put("receipt", "rcpt_u" + userId + "_c" + courseId);

        Order razorpayOrder;
        try {
            razorpayOrder = razorpayClient.orders.create(orderRequest);
        } catch (RazorpayException e) {
            logger.error("Failed to create Razorpay order for user {} course {}: {}", userId, courseId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Payment provider error. Please try again.");
        }

        String razorpayOrderId = razorpayOrder.get("id");
        PaymentOrder paymentOrder = new PaymentOrder(razorpayOrderId, userId, courseId, amountPaise, currency);
        paymentOrderRepository.save(paymentOrder);

        logger.info("Created Razorpay order {} for user {} course {}", razorpayOrderId, userId, courseId);
        return new CreateOrderResponse(razorpayOrderId, amountPaise, currency, razorpayProperties.getKeyId());
    }

    /**
     * Verifies signature, marks order paid, records purchase.
     * Idempotent: Multiple calls with same razorpayPaymentId will succeed.
     */
    @Transactional
    public VerifyPaymentResponse verifyPayment(
            Long userId,
            String razorpayOrderId,
            String razorpayPaymentId,
            String razorpaySignature
    ) {
        logger.info("Verifying payment: userId={}, razorpayOrderId={}, razorpayPaymentId={}", userId, razorpayOrderId, razorpayPaymentId);

        HashMap<String, String> attributes = new HashMap<>();
        attributes.put("razorpay_order_id", razorpayOrderId);
        attributes.put("razorpay_payment_id", razorpayPaymentId);
        attributes.put("razorpay_signature", razorpaySignature);

        try {
            Utils.verifyPaymentSignature(new JSONObject(attributes), razorpayProperties.getKeySecret());
            logger.info("Signature verified successfully for payment {}", razorpayPaymentId);
        } catch (RazorpayException e) {
            logger.error("Signature verification failed for payment {} in order {}, userId={}: {}",
                    razorpayPaymentId, razorpayOrderId, userId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment signature verification failed.");
        }

        PaymentOrder paymentOrder = paymentOrderRepository.findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> {
                    logger.error("PaymentOrder not found for razorpayOrderId={}, userId={}", razorpayOrderId, userId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found.");
                });

        if (!paymentOrder.getUserId().equals(userId)) {
            logger.error("OrderId mismatch: order belongs to userId={}, but verified by userId={}",
                    paymentOrder.getUserId(), userId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Order does not belong to this user.");
        }

        if (PaymentOrder.Status.PAID.name().equals(paymentOrder.getStatus())) {
            logger.warn("Order already marked PAID: razorpayOrderId={}, userId={}", razorpayOrderId, userId);
            // Check if Purchase already exists for idempotency
            var existingPurchase = purchaseRepository
                    .findByCourseIdAndRazorpayPaymentId(paymentOrder.getCourseId(), razorpayPaymentId);
            if (existingPurchase.isPresent()) {
                logger.info("Purchase already exists for payment {} (idempotent success)", razorpayPaymentId);
                return new VerifyPaymentResponse(true, "Payment already verified successfully.");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order already paid but purchase record missing. Contact support.");
        }

        // Idempotency check: if Purchase already exists for this payment ID, return success
        var existingPurchase = purchaseRepository
                .findByCourseIdAndRazorpayPaymentId(paymentOrder.getCourseId(), razorpayPaymentId);
        if (existingPurchase.isPresent()) {
            logger.info("Purchase already exists for payment {} (idempotent success)", razorpayPaymentId);
            // Ensure order is marked as paid
            if (!PaymentOrder.Status.PAID.name().equals(paymentOrder.getStatus())) {
                paymentOrder.markPaid();
                paymentOrderRepository.save(paymentOrder);
                logger.info("Updated existing PaymentOrder to PAID status for idempotent call");
            }
            return new VerifyPaymentResponse(true, "Payment already verified successfully.");
        }

        paymentOrder.markPaid();
        // The gateway's payment id is what a person quotes for an online sale.
        paymentOrder.setReference(razorpayPaymentId);
        paymentOrderRepository.save(paymentOrder);
        logger.info("Marked PaymentOrder as PAID: razorpayOrderId={}, userId={}", razorpayOrderId, userId);

        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> {
                        logger.error("User not found for enrollment: userId={}, payment={}", userId, razorpayPaymentId);
                        return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found for enrollment.");
                    });

            Long courseId = paymentOrder.getCourseId();
            // Validity is read once, here, and baked into the purchase. Editing the
            // course later must never move a student's expiry.
            int validityDays = courseRepository.findById(courseId)
                    .map(Course::getValidityDays)
                    .orElse(0);

            Purchase purchase = purchaseRepository.findIncludingUnenrolled(userId, courseId)
                    .orElse(null);
            if (purchase != null) {
                // Expired student buying again, or one an admin removed paying their
                // way back in: reuse the enrolment row so there stays one per student
                // per course. payment_orders keeps the payment history.
                purchase.renew(razorpayOrderId, razorpayPaymentId, paymentOrder.getAmountPaise(),
                        paymentOrder.getCurrency(), validityDays);
                logger.info("Purchase renewed: userId={}, courseId={}, payment={}, expiresAt={}",
                        userId, courseId, razorpayPaymentId, purchase.getExpiresAt());
            } else {
                purchase = new Purchase(
                        userId,
                        courseId,
                        razorpayOrderId,
                        razorpayPaymentId,
                        paymentOrder.getAmountPaise(),
                        paymentOrder.getCurrency(),
                        validityDays
                );
                logger.info("Purchase record created successfully: userId={}, courseId={}, payment={}, expiresAt={}",
                        userId, courseId, razorpayPaymentId, purchase.getExpiresAt());
            }
            purchase.setUser(user);
            purchaseRepository.save(purchase);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to create Purchase record: userId={}, courseId={}, payment={}, error={}",
                    userId, paymentOrder.getCourseId(), razorpayPaymentId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Payment verified but enrollment failed. Contact support with payment ID: " + razorpayPaymentId);
        }

        logger.info("Payment verification complete: userId={}, razorpayOrderId={}, razorpayPaymentId={}",
                userId, razorpayOrderId, razorpayPaymentId);
        return new VerifyPaymentResponse(true, "Payment verified successfully.");
    }

    /**
     * Returns full course details for all courses the user has purchased,
     * expired ones included — the app shows those as expired rather than hiding
     * them, so the student can renew.
     */
    public UserCoursesResponse getUserCourses(Long userId) {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(AccessExpiry.ZONE);
        List<Purchase> purchases = purchaseRepository.findByUserId(userId);
        List<CourseResponse> courses = purchases.stream()
                .map(p -> courseRepository.findById(p.getCourseId())
                        .map(c -> new CourseResponse(c.getId(), c.getTitle(), c.getDescription(), c.getPricePaise(),
                                c.getCurrency(), c.getThumbnailUrl(), c.getValidityDays(),
                                AccessExpiry.lastDay(p.getExpiresAt()), p.isExpired(now),
                                AccessExpiry.daysRemaining(p.getExpiresAt(), now),
                                // Retired and closed courses stay listed so the student
                                // can see what they had; they just cannot be bought again.
                                c.isEnrolmentOpen(today)))
                        .orElse(null))
                .filter(c -> c != null)
                .toList();
        return new UserCoursesResponse(courses);
    }

    /** Returns whether the user has at least one verified purchase. */
    public boolean hasActivePurchase(Long userId) {
        return purchaseRepository.existsByUserId(userId);
    }

    /**
     * Checks if a payment was verified. Does not throw errors.
     * Used when frontend needs to query status after verify failures.
     */
    public PaymentStatusResponse checkPaymentStatus(
            Long userId,
            String razorpayOrderId,
            String razorpayPaymentId
    ) {
        logger.info("Checking payment status: userId={}, razorpayOrderId={}, razorpayPaymentId={}", userId, razorpayOrderId, razorpayPaymentId);

        // Check if Purchase exists (payment was verified). The order names the
        // course, and a payment reference is only unique within one.
        var paymentOrder = paymentOrderRepository.findByRazorpayOrderId(razorpayOrderId);
        var purchase = paymentOrder
                .flatMap(o -> purchaseRepository.findByCourseIdAndRazorpayPaymentId(
                        o.getCourseId(), razorpayPaymentId));
        if (purchase.isPresent()) {
            Purchase p = purchase.get();
            if (p.getUserId().equals(userId)) {
                logger.info("Payment verified: userId={}, razorpayPaymentId={}", userId, razorpayPaymentId);
                return new PaymentStatusResponse(
                        true,
                        true,
                        "Payment verified. Course access granted.",
                        "Purchase record found with matching user."
                );
            } else {
                logger.warn("Payment found but belongs to different user: expected={}, found={}, payment={}",
                        userId, p.getUserId(), razorpayPaymentId);
                return new PaymentStatusResponse(
                        false,
                        false,
                        "Payment recorded for different user.",
                        "Security concern: payment belongs to different user."
                );
            }
        }

        // Check if PaymentOrder exists but not verified
        if (paymentOrder.isPresent()) {
            PaymentOrder order = paymentOrder.get();
            if (!order.getUserId().equals(userId)) {
                logger.warn("PaymentOrder found but belongs to different user: expected={}, found={}, order={}",
                        userId, order.getUserId(), razorpayOrderId);
                return new PaymentStatusResponse(
                        false,
                        false,
                        "Order belongs to different user.",
                        "Security concern: order belongs to different user."
                );
            }

            if (PaymentOrder.Status.PAID.name().equals(order.getStatus())) {
                logger.warn("PaymentOrder marked PAID but Purchase not found: order={}, payment={}",
                        razorpayOrderId, razorpayPaymentId);
                return new PaymentStatusResponse(
                        false,
                        false,
                        "Payment recorded but enrollment failed. Contact support.",
                        "PaymentOrder status is PAID but Purchase record missing. Support ID: " + razorpayPaymentId
                );
            } else {
                logger.info("PaymentOrder exists but not paid: order={}, status={}", razorpayOrderId, order.getStatus());
                return new PaymentStatusResponse(
                        false,
                        false,
                        "Payment not yet verified. Please complete the Razorpay checkout.",
                        "PaymentOrder status: " + order.getStatus()
                );
            }
        }

        // Neither PaymentOrder nor Purchase exists
        logger.warn("No payment record found: razorpayOrderId={}, razorpayPaymentId={}, userId={}",
                razorpayOrderId, razorpayPaymentId, userId);
        return new PaymentStatusResponse(
                false,
                false,
                "Payment not found. Please create an order first.",
                "No PaymentOrder or Purchase record found."
        );
    }
}
