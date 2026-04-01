package com.maths.teacher.payment.web;

public class PaymentStatusResponse {

    private final boolean verified;  // Is payment signature verified?
    private final boolean courseAccessible;  // Does user have access to course?
    private final String message;  // User-friendly message
    private final String debugMessage;  // Technical details for debugging

    public PaymentStatusResponse(
            boolean verified,
            boolean courseAccessible,
            String message,
            String debugMessage
    ) {
        this.verified = verified;
        this.courseAccessible = courseAccessible;
        this.message = message;
        this.debugMessage = debugMessage;
    }

    public boolean isVerified() { return verified; }
    public boolean isCourseAccessible() { return courseAccessible; }
    public String getMessage() { return message; }
    public String getDebugMessage() { return debugMessage; }
}
