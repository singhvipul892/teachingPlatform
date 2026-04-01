package com.maths.teacher.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Map;

@Service
public class SnsSmsSService implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(SnsSmsSService.class);

    private final SnsClient snsClient;
    private final boolean mockEnabled;

    public SnsSmsSService(SnsClient snsClient,
                          @Value("${app.sms.mock:false}") boolean mockEnabled) {
        this.snsClient = snsClient;
        this.mockEnabled = mockEnabled;
    }

    @Override
    public void sendOtp(String mobileNumber, String otp) {
        String e164Number = toE164(mobileNumber);
        String message = "Your Singh Sir password reset OTP is: " + otp + ". Valid for 10 minutes. Do not share with anyone.";

        if (mockEnabled) {
            log.info("[SMS MOCK] To: {} | Message: {}", e164Number, message);
            return;
        }

        PublishRequest request = PublishRequest.builder()
                .phoneNumber(e164Number)
                .message(message)
                .messageAttributes(Map.of(
                        "AWS.SNS.SMS.SMSType",
                        MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue("Transactional")
                                .build()
                ))
                .build();

        snsClient.publish(request);
    }

    private String toE164(String mobileNumber) {
        String digits = mobileNumber.replaceAll("[^0-9]", "");
        if (digits.startsWith("91") && digits.length() == 12) {
            return "+" + digits;
        }
        if (digits.length() == 10) {
            return "+91" + digits;
        }
        // Already in E.164 or unknown format — return as-is with leading +
        return mobileNumber.startsWith("+") ? mobileNumber : "+" + digits;
    }
}
