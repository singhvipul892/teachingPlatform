package com.maths.teacher.payment.repository;

import com.maths.teacher.payment.domain.PaymentOrder;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByRazorpayOrderId(String razorpayOrderId);
}
