package com.maths.teacher.payment.repository;

import com.maths.teacher.payment.domain.Purchase;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Untagging a student stamps {@code unenrolled_at} instead of deleting the row,
 * so the table holds enrolments that are over as well as ones that are live.
 * Every lookup here hides the ones that are over — the two methods that do not
 * say so in their names. Anything that grants access should therefore be safe by
 * default, and a query that wants a removed student has to ask for it.
 */
public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    @Query("select count(p) > 0 from Purchase p where p.userId = :userId and p.unenrolledAt is null")
    boolean existsByUserId(@Param("userId") Long userId);

    @Query("select count(p) > 0 from Purchase p where p.userId = :userId "
            + "and p.courseId = :courseId and p.unenrolledAt is null")
    boolean existsByUserIdAndCourseId(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /**
     * The student's live enrolment, expired or not — {@link Purchase#isActive}
     * decides that part. Empty once an admin has removed them.
     */
    @Query("select p from Purchase p where p.userId = :userId "
            + "and p.courseId = :courseId and p.unenrolledAt is null")
    Optional<Purchase> findEnrolled(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /**
     * The row whether or not the student was removed. Only for enrolling someone:
     * the unique index allows one row per (user, course), so tagging or paying
     * again has to find and reuse the row a previous enrolment left behind.
     * Never use this to decide access.
     */
    @Query("select p from Purchase p where p.userId = :userId and p.courseId = :courseId")
    Optional<Purchase> findIncludingUnenrolled(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Query("select p from Purchase p where p.userId = :userId and p.unenrolledAt is null")
    List<Purchase> findByUserId(@Param("userId") Long userId);

    @Query("select p from Purchase p where p.courseId = :courseId and p.unenrolledAt is null")
    List<Purchase> findByCourseId(@Param("courseId") Long courseId);

    /**
     * The roster with removed students included, so an admin can see a mistaken
     * removal and undo it. Only for showing the list — never for access.
     */
    @Query("select p from Purchase p where p.courseId = :courseId")
    List<Purchase> findByCourseIdIncludingUnenrolled(@Param("courseId") Long courseId);

    @Query("select count(p) from Purchase p where p.courseId = :courseId and p.unenrolledAt is null")
    long countByCourseId(@Param("courseId") Long courseId);

    /** Students whose access has not lapsed. Lifetime purchases always count. */
    @Query("select count(p) from Purchase p where p.courseId = :courseId and p.unenrolledAt is null "
            + "and (p.expiresAt is null or p.expiresAt > :now)")
    long countActiveByCourseId(@Param("courseId") Long courseId, @Param("now") Instant now);

    /**
     * Looked up by payment to make verification idempotent, so this deliberately
     * does not filter on enrolment.
     *
     * <p>Scoped by course because a payment reference is only unique within one:
     * an admin may enter the same UPI reference for a student who paid once for
     * two courses. Gateway payment ids are globally unique anyway, so this is
     * strictly narrower and never misses one.
     */
    Optional<Purchase> findByCourseIdAndRazorpayPaymentId(Long courseId, String razorpayPaymentId);
}
