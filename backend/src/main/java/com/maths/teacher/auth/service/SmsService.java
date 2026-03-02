package com.maths.teacher.auth.service;

public interface SmsService {

    void sendOtp(String mobileNumber, String otp);
}
