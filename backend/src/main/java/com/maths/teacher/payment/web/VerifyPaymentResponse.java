package com.maths.teacher.payment.web;

public class VerifyPaymentResponse {

    private final boolean success;
    private final String message;

    public VerifyPaymentResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
}
