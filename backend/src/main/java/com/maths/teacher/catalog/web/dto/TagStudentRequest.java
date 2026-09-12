package com.maths.teacher.catalog.web.dto;

public class TagStudentRequest {

    private Long userId;
    private String razorpayTransactionId;
    /**
     * Whether money actually changed hands. True records an offline payment at
     * the course price; false grants access and records nothing payable — a free
     * seat, or undoing a removal.
     *
     * <p>Deliberately a Boolean and not a boolean: null means the caller did not
     * say, which the service refuses for a student who was removed, because
     * guessing there is how a correction becomes a phantom sale.
     */
    private Boolean recordPayment;

    public TagStudentRequest() {}

    public Long getUserId() { return userId; }
    public String getRazorpayTransactionId() { return razorpayTransactionId; }
    public Boolean getRecordPayment() { return recordPayment; }
}
