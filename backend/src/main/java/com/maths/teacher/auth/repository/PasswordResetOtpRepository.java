package com.maths.teacher.auth.repository;

import com.maths.teacher.auth.domain.PasswordResetOtp;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {

    Optional<PasswordResetOtp> findTopByMobileNumberAndUsedFalseOrderByCreatedAtDesc(String mobileNumber);

    List<PasswordResetOtp> findAllByMobileNumberAndUsedFalse(String mobileNumber);
}
