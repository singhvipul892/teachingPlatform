package com.maths.teacher.payment.domain;

import com.maths.teacher.auth.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
        name = "purchases",
        indexes = {
                @Index(name = "idx_purchases_user_id", columnList = "user_id"),
                @Index(name = "idx_purchases_course_id", columnList = "course_id"),
                @Index(name = "idx_purchases_user_course", columnList = "user_id, course_id", unique = true)
        }
)
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_purchases_user_id"))
    private User user;

    @Column(name = "user_id", nullable = false, insertable = false, updatable = false)
    private Long userId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "razorpay_order_id", nullable = false, unique = true, length = 100)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", nullable = false, unique = true, length = 100)
    private String razorpayPaymentId;

    @Column(name = "amount_paise", nullable = false)
    private int amountPaise;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "purchased_at", nullable = false)
    private Instant purchasedAt;

    /**
     * When access ends. Null means it never does — which is every purchase made
     * before course validity existed, and every purchase of a course whose
     * validity is 0. Fixed at purchase time and never recalculated, so editing
     * a course's validity cannot change what a student already paid for.
     */
    @Column(name = "expires_at")
    private Instant expiresAt;

    protected Purchase() {
        // for JPA
    }

    public Purchase(Long userId, Long courseId, String razorpayOrderId, String razorpayPaymentId, int amountPaise, String currency) {
        this(userId, courseId, razorpayOrderId, razorpayPaymentId, amountPaise, currency, 0);
    }

    public Purchase(Long userId, Long courseId, String razorpayOrderId, String razorpayPaymentId, int amountPaise, String currency, int validityDays) {
        this.userId = userId;
        this.courseId = courseId;
        this.razorpayOrderId = razorpayOrderId;
        this.razorpayPaymentId = razorpayPaymentId;
        this.amountPaise = amountPaise;
        this.currency = currency;
        this.purchasedAt = Instant.now();
        this.expiresAt = AccessExpiry.from(this.purchasedAt, validityDays);
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Long getUserId() { return userId; }
    public Long getCourseId() { return courseId; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public String getRazorpayPaymentId() { return razorpayPaymentId; }
    public int getAmountPaise() { return amountPaise; }
    public String getCurrency() { return currency; }
    public Instant getPurchasedAt() { return purchasedAt; }
    public Instant getExpiresAt() { return expiresAt; }

    public void setUser(User user) { this.user = user; }

    /** True while the student still has access. Lifetime purchases are always active. */
    public boolean isActive(Instant now) {
        return expiresAt == null || expiresAt.isAfter(now);
    }

    public boolean isExpired(Instant now) {
        return !isActive(now);
    }

    /**
     * Restarts access from today for a fresh payment. Used when an expired
     * student buys the course again — the row is reused so there stays one
     * enrolment per student per course; the full payment history lives in
     * payment_orders.
     */
    public void renew(String razorpayOrderId, String razorpayPaymentId, int amountPaise, String currency, int validityDays) {
        this.razorpayOrderId = razorpayOrderId;
        this.razorpayPaymentId = razorpayPaymentId;
        this.amountPaise = amountPaise;
        this.currency = currency;
        this.purchasedAt = Instant.now();
        this.expiresAt = AccessExpiry.from(this.purchasedAt, validityDays);
    }

    /** Admin override of a single student's expiry. Null grants lifetime access. */
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
