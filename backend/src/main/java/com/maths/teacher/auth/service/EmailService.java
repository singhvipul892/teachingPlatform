package com.maths.teacher.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final boolean mockEnabled;
    private final String from;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.mock:false}") boolean mockEnabled,
                        @Value("${app.mail.from:}") String from) {
        this.mailSender = mailSender;
        this.mockEnabled = mockEnabled;
        this.from = from;
    }

    public void sendPasswordResetOtp(String email, String otp) {
        String subject = "Singh Sir - password reset code";
        String body = "Your Singh Sir password reset code is: " + otp + "\n\n"
                + "It is valid for 10 minutes. Do not share it with anyone.\n\n"
                + "If you did not ask to reset your password, you can ignore this email.";

        if (mockEnabled) {
            log.info("[EMAIL MOCK] To: {} | Subject: {} | Body: {}", email, subject, body);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("Singh Sir <" + from + ">");
        message.setTo(email);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
