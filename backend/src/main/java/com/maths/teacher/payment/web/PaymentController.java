package com.maths.teacher.payment.web;

import com.maths.teacher.payment.service.PaymentService;
import com.maths.teacher.security.AuthService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private final PaymentService paymentService;
    private final AuthService authService;

    public PaymentController(PaymentService paymentService, AuthService authService) {
        this.paymentService = paymentService;
        this.authService = authService;
    }

    /** Public — lists all active purchasable courses. */
    @GetMapping("/api/courses")
    public ResponseEntity<List<CourseResponse>> listCourses() {
        return ResponseEntity.ok(paymentService.listCourses());
    }

    /** JWT — returns the list of courses the logged-in user has purchased. */
    @GetMapping("/api/user/courses")
    public ResponseEntity<UserCoursesResponse> getUserCourses(
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = Long.parseLong(authService.requireUserId(authHeader));
        return ResponseEntity.ok(paymentService.getUserCourses(userId));
    }

    /** JWT — creates a Razorpay order for the given course. */
    @PostMapping("/api/payment/create-order")
    public ResponseEntity<CreateOrderResponse> createOrder(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        Long userId = Long.parseLong(authService.requireUserId(authHeader));
        return ResponseEntity.ok(paymentService.createOrder(userId, request.getCourseId()));
    }

    /** JWT — verifies Razorpay payment signature and records the purchase. */
    @PostMapping("/api/payment/verify")
    public ResponseEntity<VerifyPaymentResponse> verifyPayment(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody VerifyPaymentRequest request
    ) {
        Long userId = Long.parseLong(authService.requireUserId(authHeader));
        VerifyPaymentResponse response = paymentService.verifyPayment(
                userId,
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature()
        );
        return ResponseEntity.ok(response);
    }

    /** JWT — returns whether the user has any active purchase. */
    @GetMapping("/api/payment/status")
    public ResponseEntity<PurchaseStatusResponse> getPurchaseStatus(
            @RequestHeader("Authorization") String authHeader
    ) {
        Long userId = Long.parseLong(authService.requireUserId(authHeader));
        return ResponseEntity.ok(new PurchaseStatusResponse(paymentService.hasActivePurchase(userId)));
    }
}
