package com.maths.teacher.payment.repository;

import com.maths.teacher.payment.domain.Purchase;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    boolean existsByUserId(Long userId);

    boolean existsByUserIdAndCourseId(Long userId, Long courseId);

    /**
     * There is at most one row per (user, course) — a renewal reuses it — so this
     * is the enrolment, expired or not. Callers decide using {@link Purchase#isActive}.
     */
    Optional<Purchase> findByUserIdAndCourseId(Long userId, Long courseId);

    List<Purchase> findByUserId(Long userId);

    List<Purchase> findByCourseId(Long courseId);

    long countByCourseId(Long courseId);

    /** Students whose access has not lapsed. Lifetime purchases always count. */
    @Query("select count(p) from Purchase p where p.courseId = :courseId "
            + "and (p.expiresAt is null or p.expiresAt > :now)")
    long countActiveByCourseId(@Param("courseId") Long courseId, @Param("now") Instant now);

    Optional<Purchase> findByRazorpayPaymentId(String razorpayPaymentId);

    void deleteByUserIdAndCourseId(Long userId, Long courseId);
}
