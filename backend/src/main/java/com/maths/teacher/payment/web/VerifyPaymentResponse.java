package com.maths.teacher.payment.web;

public class VerifyPaymentResponse {

    private final boolean success;
    private final String message;
    private final String errorCode;  // SIGNATURE_INVALID, ORDER_NOT_FOUND, ALREADY_PAID, DB_ERROR, SUCCESS, null
    private final String supportPaymentId;  // Payment ID for support team

    public VerifyPaymentResponse(boolean success, String message) {
        this(success, message, null, null);
    }

    public VerifyPaymentResponse(boolean success, String message, String errorCode, String supportPaymentId) {
        this.success = success;
        this.message = message;
        this.errorCode = errorCode;
        this.supportPaymentId = supportPaymentId;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getErrorCode() { return errorCode; }
    public String getSupportPaymentId() { return supportPaymentId; }
}
