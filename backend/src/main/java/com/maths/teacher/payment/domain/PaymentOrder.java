package com.maths.teacher.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
        name = "payment_orders",
        indexes = {
                @Index(name = "idx_payment_orders_user_id", columnList = "user_id"),
                @Index(name = "idx_payment_orders_razorpay_order_id", columnList = "razorpay_order_id", unique = true),
                @Index(name = "idx_payment_orders_course_id", columnList = "course_id")
        }
)
public class PaymentOrder {

    public enum Status { CREATED, PAID, FAILED }

    /**
     * How the money arrived. Revenue sums amounts across all of these, so a
     * COMPLIMENTARY order — an admin granting access with no payment — is always
     * zero and contributes nothing, while still leaving a record that access was
     * granted and when.
     */
    public enum Source { RAZORPAY, OFFLINE, COMPLIMENTARY }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "razorpay_order_id", nullable = false, unique = true, length = 100)
    private String razorpayOrderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "amount_paise", nullable = false)
    private int amountPaise;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    /**
     * What a person would quote when asked which payment this was: the UPI or
     * bank reference an admin typed in, or the gateway's payment id. Null for a
     * free seat, which has no payment to reference.
     */
    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PaymentOrder() {
        // for JPA
    }

    /** Online purchases; the gateway is the default way money arrives. */
    public PaymentOrder(String razorpayOrderId, Long userId, Long courseId, int amountPaise, String currency) {
        this(razorpayOrderId, userId, courseId, amountPaise, currency, Source.RAZORPAY);
    }

    public PaymentOrder(String razorpayOrderId, Long userId, Long courseId, int amountPaise,
                        String currency, Source source) {
        this.razorpayOrderId = razorpayOrderId;
        this.userId = userId;
        this.courseId = courseId;
        this.amountPaise = amountPaise;
        this.currency = currency;
        this.status = Status.CREATED.name();
        this.source = source.name();
        this.createdAt = Instant.now();
    }

    public void markPaid() {
        this.status = Status.PAID.name();
    }

    public Long getId() { return id; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public Long getUserId() { return userId; }
    public Long getCourseId() { return courseId; }
    public int getAmountPaise() { return amountPaise; }
    public String getCurrency() { return currency; }
    public String getStatus() { return status; }
    public String getSource() { return source; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    /** True when money actually changed hands, online or off. */
    public boolean isPayment() { return !Source.COMPLIMENTARY.name().equals(source); }
    public Instant getCreatedAt() { return createdAt; }
}
