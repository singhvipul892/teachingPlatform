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
                @Index(name = "idx_purchases_user_course", columnList = "user_id, course_id", unique = true),
                // Per course, not global: one offline payment reference can cover
                // two courses, and only a genuine duplicate on one is refused.
                @Index(name = "idx_purchases_course_payment", columnList = "course_id, razorpay_payment_id", unique = true)
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

    @Column(name = "razorpay_payment_id", nullable = false, length = 100)
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

    /**
     * When an admin removed this student from the course. Null means they are
     * still enrolled, which is every row that has never been untagged. Untagging
     * stamps this rather than deleting the row, so the enrolment is never
     * destroyed and re-tagging reuses the row the unique (user, course) index
     * allows only one of.
     *
     * <p>Repository lookups filter this out, so an unenrolled row is only ever
     * reachable through the methods that name it explicitly.
     */
    @Column(name = "unenrolled_at")
    private Instant unenrolledAt;

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
    public Instant getUnenrolledAt() { return unenrolledAt; }

    public void setUser(User user) { this.user = user; }

    /** False once an admin has removed the student from the course. */
    public boolean isEnrolled() {
        return unenrolledAt == null;
    }

    /**
     * Removes the student from the course without destroying the record of the
     * enrolment. The expiry is left exactly as it was: what they were sold stays
     * on the row, and {@link #isEnrolled()} is what decides access.
     */
    public void unenrol(Instant when) {
        this.unenrolledAt = when;
    }

    /**
     * Undoes a removal, and only that. No payment happened — an admin clicked
     * the wrong row — so the expiry, the amount and the payment this enrolment
     * points at all stay exactly as they were. A student whose access had
     * already lapsed before the removal comes back lapsed.
     */
    public void restore() {
        this.unenrolledAt = null;
    }

    /**
     * True while the student still has access, judged on expiry alone — callers
     * reach a Purchase through lookups that have already excluded unenrolled
     * rows. Lifetime purchases are always active.
     */
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
        // Paying again re-enrols someone who had been removed.
        this.unenrolledAt = null;
    }

    /** Admin override of a single student's expiry. Null grants lifetime access. */
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
