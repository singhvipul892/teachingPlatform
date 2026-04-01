package com.maths.teacher.payment.repository;

import com.maths.teacher.payment.domain.Purchase;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    boolean existsByUserId(Long userId);

    boolean existsByUserIdAndCourseId(Long userId, Long courseId);

    List<Purchase> findByUserId(Long userId);

    List<Purchase> findByCourseId(Long courseId);

    long countByCourseId(Long courseId);

    Optional<Purchase> findByRazorpayPaymentId(String razorpayPaymentId);

    void deleteByUserIdAndCourseId(Long userId, Long courseId);
}
