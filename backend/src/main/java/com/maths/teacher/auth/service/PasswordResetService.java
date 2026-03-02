package com.maths.teacher.auth.service;

import com.maths.teacher.auth.domain.PasswordResetOtp;
import com.maths.teacher.auth.repository.PasswordResetOtpRepository;
import com.maths.teacher.auth.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordResetService {

    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsService smsService;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetOtpRepository otpRepository,
                                PasswordEncoder passwordEncoder,
                                SmsService smsService) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
        this.smsService = smsService;
    }

    @Transactional
    public void forgotPassword(String mobileNumber) {
        String mobile = mobileNumber.trim();

        // Look up user — silently succeed even if not found (don't reveal existence)
        var userOpt = userRepository.findByMobileNumber(mobile);
        if (userOpt.isEmpty()) {
            // Return without error to avoid user enumeration
            return;
        }
        var user = userOpt.get();

        // Invalidate any existing unused OTPs for this mobile
        List<PasswordResetOtp> existing = otpRepository.findAllByMobileNumberAndUsedFalse(mobile);
        existing.forEach(PasswordResetOtp::markUsed);
        otpRepository.saveAll(existing);

        // Generate 6-digit OTP
        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        String otpHash = passwordEncoder.encode(otp);

        Instant expiresAt = Instant.now().plus(OTP_EXPIRY_MINUTES, ChronoUnit.MINUTES);
        PasswordResetOtp resetOtp = new PasswordResetOtp(user.getId(), mobile, otpHash, expiresAt);
        otpRepository.save(resetOtp);

        smsService.sendOtp(mobile, otp);
    }

    @Transactional
    public void resetPassword(String mobileNumber, String otp, String newPassword) {
        String mobile = mobileNumber.trim();

        PasswordResetOtp resetOtp = otpRepository
                .findTopByMobileNumberAndUsedFalseOrderByCreatedAtDesc(mobile)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "No active OTP found. Please request a new one."));

        if (Instant.now().isAfter(resetOtp.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP has expired. Please request a new one.");
        }

        if (!passwordEncoder.matches(otp, resetOtp.getOtpHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OTP.");
        }

        resetOtp.markUsed();
        otpRepository.save(resetOtp);

        var user = userRepository.findByMobileNumber(mobile)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

        String newHash = passwordEncoder.encode(newPassword);
        userRepository.updatePasswordHash(user.getId(), newHash);
    }
}
