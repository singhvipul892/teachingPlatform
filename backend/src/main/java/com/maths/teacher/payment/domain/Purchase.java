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

    protected Purchase() {
        // for JPA
    }

    public Purchase(Long userId, Long courseId, String razorpayOrderId, String razorpayPaymentId, int amountPaise, String currency) {
        this.userId = userId;
        this.courseId = courseId;
        this.razorpayOrderId = razorpayOrderId;
        this.razorpayPaymentId = razorpayPaymentId;
        this.amountPaise = amountPaise;
        this.currency = currency;
        this.purchasedAt = Instant.now();
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
}
