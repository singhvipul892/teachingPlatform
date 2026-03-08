package com.maths.teacher.payment.service;

import com.maths.teacher.payment.config.RazorpayProperties;
import com.maths.teacher.payment.domain.Course;
import com.maths.teacher.payment.domain.PaymentOrder;
import com.maths.teacher.payment.domain.Purchase;
import com.maths.teacher.payment.repository.CourseRepository;
import com.maths.teacher.payment.repository.PaymentOrderRepository;
import com.maths.teacher.payment.repository.PurchaseRepository;
import com.maths.teacher.payment.web.CourseResponse;
import com.maths.teacher.payment.web.CreateOrderResponse;
import com.maths.teacher.payment.web.UserCoursesResponse;
import com.maths.teacher.payment.web.VerifyPaymentResponse;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
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

    public PaymentService(
            RazorpayClient razorpayClient,
            RazorpayProperties razorpayProperties,
            CourseRepository courseRepository,
            PaymentOrderRepository paymentOrderRepository,
            PurchaseRepository purchaseRepository
    ) {
        this.razorpayClient = razorpayClient;
        this.razorpayProperties = razorpayProperties;
        this.courseRepository = courseRepository;
        this.paymentOrderRepository = paymentOrderRepository;
        this.purchaseRepository = purchaseRepository;
    }

    /** Returns all active courses (public endpoint). */
    public List<CourseResponse> listCourses() {
        return courseRepository.findByActiveTrue().stream()
                .map(c -> new CourseResponse(c.getId(), c.getTitle(), c.getDescription(), c.getPricePaise(), c.getCurrency(), c.getThumbnailUrl()))
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

        if (purchaseRepository.existsByUserIdAndCourseId(userId, courseId)) {
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
     */
    @Transactional
    public VerifyPaymentResponse verifyPayment(
            Long userId,
            String razorpayOrderId,
            String razorpayPaymentId,
            String razorpaySignature
    ) {
        HashMap<String, String> attributes = new HashMap<>();
        attributes.put("razorpay_order_id", razorpayOrderId);
        attributes.put("razorpay_payment_id", razorpayPaymentId);
        attributes.put("razorpay_signature", razorpaySignature);

        try {
            Utils.verifyPaymentSignature(new JSONObject(attributes), razorpayProperties.getKeySecret());
        } catch (RazorpayException e) {
            logger.warn("Invalid payment signature for order {} from user {}", razorpayOrderId, userId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment signature verification failed.");
        }

        PaymentOrder paymentOrder = paymentOrderRepository.findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found."));

        if (!paymentOrder.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Order does not belong to this user.");
        }

        if (PaymentOrder.Status.PAID.name().equals(paymentOrder.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order already paid.");
        }

        paymentOrder.markPaid();
        paymentOrderRepository.save(paymentOrder);

        Purchase purchase = new Purchase(
                userId,
                paymentOrder.getCourseId(),
                razorpayOrderId,
                razorpayPaymentId,
                paymentOrder.getAmountPaise(),
                paymentOrder.getCurrency()
        );
        purchaseRepository.save(purchase);

        logger.info("Payment verified for user {}, order {}, payment {}", userId, razorpayOrderId, razorpayPaymentId);
        return new VerifyPaymentResponse(true, "Payment verified successfully.");
    }

    /** Returns full course details for all courses the user has purchased. */
    public UserCoursesResponse getUserCourses(Long userId) {
        List<Purchase> purchases = purchaseRepository.findByUserId(userId);
        List<CourseResponse> courses = purchases.stream()
                .map(p -> courseRepository.findById(p.getCourseId()).orElse(null))
                .filter(c -> c != null)
                .map(c -> new CourseResponse(c.getId(), c.getTitle(), c.getDescription(), c.getPricePaise(), c.getCurrency(), c.getThumbnailUrl()))
                .toList();
        return new UserCoursesResponse(courses);
    }

    /** Returns whether the user has at least one verified purchase. */
    public boolean hasActivePurchase(Long userId) {
        return purchaseRepository.existsByUserId(userId);
    }
}
