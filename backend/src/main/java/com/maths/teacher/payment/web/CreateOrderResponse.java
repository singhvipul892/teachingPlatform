package com.maths.teacher.payment.web;

public class CreateOrderResponse {

    private final String razorpayOrderId;
    private final int amountPaise;
    private final String currency;
    private final String keyId;

    public CreateOrderResponse(String razorpayOrderId, int amountPaise, String currency, String keyId) {
        this.razorpayOrderId = razorpayOrderId;
        this.amountPaise = amountPaise;
        this.currency = currency;
        this.keyId = keyId;
    }

    public String getRazorpayOrderId() { return razorpayOrderId; }
    public int getAmountPaise() { return amountPaise; }
    public String getCurrency() { return currency; }
    public String getKeyId() { return keyId; }
}
