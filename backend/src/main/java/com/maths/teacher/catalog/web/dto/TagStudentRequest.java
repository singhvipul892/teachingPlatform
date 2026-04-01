package com.maths.teacher.catalog.web.dto;

public class TagStudentRequest {

    private Long userId;
    private String razorpayTransactionId;

    public TagStudentRequest() {}

    public Long getUserId() { return userId; }
    public String getRazorpayTransactionId() { return razorpayTransactionId; }
}
