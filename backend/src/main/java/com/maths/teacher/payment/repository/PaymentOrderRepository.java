package com.maths.teacher.payment.repository;

import com.maths.teacher.payment.domain.PaymentOrder;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The ledger. One row per payment, appended and never rewritten, which is what
 * makes it the only honest source for revenue: an enrolment row is current state
 * and forgets everything before the last renewal.
 *
 * <p>Every reporting query below counts PAID orders only — CREATED means the
 * student opened a checkout and never finished, and must not read as income.
 */
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByRazorpayOrderId(String razorpayOrderId);

    /**
     * Whether this reference has already been recorded against this course. The
     * same reference on a DIFFERENT course is fine and deliberate — one bank
     * transfer can pay for two courses — so this is scoped to the one.
     */
    @Query("select count(o) > 0 from PaymentOrder o where o.courseId = :courseId "
            + "and o.reference = :reference")
    boolean referenceUsedOn(@Param("courseId") Long courseId, @Param("reference") String reference);

    /** Every payment for one student on one course, oldest first. */
    @Query("select o from PaymentOrder o where o.userId = :userId and o.courseId = :courseId "
            + "and o.status = 'PAID' order by o.createdAt asc")
    List<PaymentOrder> findPaidFor(@Param("userId") Long userId, @Param("courseId") Long courseId);

    /**
     * Payment counts and first-payment dates for everyone on a course, in one
     * query so the roster does not fan out into one lookup per student.
     */
    @Query("select new com.maths.teacher.payment.repository.StudentPayments("
            + "o.userId, count(o), min(o.createdAt)) "
            + "from PaymentOrder o where o.courseId = :courseId and o.status = 'PAID' "
            + "group by o.userId")
    List<StudentPayments> summarisePayersOn(@Param("courseId") Long courseId);

    /** Total taken, split by how it arrived. Complimentary seats sum to zero. */
    @Query("select new com.maths.teacher.payment.repository.SourceRevenue("
            + "o.source, coalesce(sum(o.amountPaise), 0), count(o)) "
            + "from PaymentOrder o where o.status = 'PAID' group by o.source")
    List<SourceRevenue> revenueBySource();

    /**
     * Offline enrolments recorded with no amount against them. These are the tags
     * written before the amount was taken from the course price, and their real
     * value is not recoverable — so they are counted and shown rather than guessed
     * at, and the revenue total simply does not include them.
     */
    @Query("select count(o) from PaymentOrder o where o.status = 'PAID' "
            + "and o.source = 'OFFLINE' and o.amountPaise = 0")
    long countOfflineWithoutAmount();

    /**
     * Per-course takings. payerCount is distinct students, which differs from
     * paymentCount whenever somebody renewed or paid again after being removed.
     */
    @Query("select new com.maths.teacher.payment.repository.CourseRevenue("
            + "o.courseId, coalesce(sum(o.amountPaise), 0), count(o), count(distinct o.userId)) "
            + "from PaymentOrder o where o.status = 'PAID' group by o.courseId")
    List<CourseRevenue> revenueByCourse();
}
