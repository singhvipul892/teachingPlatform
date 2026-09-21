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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordResetService {

    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 5;
    private static final int RESEND_COOLDOWN_SECONDS = 60;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetOtpRepository otpRepository,
                                PasswordEncoder passwordEncoder,
                                EmailService emailService) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    @Transactional
    public void forgotPassword(String email) {
        var userOpt = userRepository.findByEmail(normalize(email));
        if (userOpt.isEmpty()) {
            // Return without error to avoid user enumeration
            return;
        }
        var user = userOpt.get();

        // Repeat taps within the cooldown send nothing and keep the code already emailed valid.
        var latest = otpRepository.findTopByUserIdOrderByCreatedAtDesc(user.getId());
        if (latest.isPresent()
                && latest.get().getCreatedAt().isAfter(Instant.now().minusSeconds(RESEND_COOLDOWN_SECONDS))) {
            return;
        }

        List<PasswordResetOtp> existing = otpRepository.findAllByUserIdAndUsedFalse(user.getId());
        existing.forEach(PasswordResetOtp::markUsed);
        otpRepository.saveAll(existing);

        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        Instant expiresAt = Instant.now().plus(OTP_EXPIRY_MINUTES, ChronoUnit.MINUTES);
        otpRepository.save(new PasswordResetOtp(
                user.getId(), user.getMobileNumber(), passwordEncoder.encode(otp), expiresAt));

        // After commit, so the email never carries a code whose row was rolled back.
        String to = user.getEmail();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                emailService.sendPasswordResetOtp(to, otp);
            }
        });
    }

    // noRollbackFor: a wrong guess must still persist its attempt count when we reject it.
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void resetPassword(String email, String otp, String newPassword) {
        var user = userRepository.findByEmail(normalize(email))
                .orElseThrow(PasswordResetService::noActiveOtp);

        PasswordResetOtp resetOtp = otpRepository
                .findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId())
                .orElseThrow(PasswordResetService::noActiveOtp);

        if (Instant.now().isAfter(resetOtp.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP has expired. Please request a new one.");
        }

        if (!passwordEncoder.matches(otp, resetOtp.getOtpHash())) {
            resetOtp.recordFailedAttempt();
            if (resetOtp.getAttempts() >= MAX_ATTEMPTS) {
                resetOtp.markUsed();
                otpRepository.save(resetOtp);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Too many wrong attempts. Please request a new OTP.");
            }
            otpRepository.save(resetOtp);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OTP.");
        }

        resetOtp.markUsed();
        otpRepository.save(resetOtp);
        userRepository.updatePasswordHash(user.getId(), passwordEncoder.encode(newPassword));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }

    private static ResponseStatusException noActiveOtp() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active OTP found. Please request a new one.");
    }
}
