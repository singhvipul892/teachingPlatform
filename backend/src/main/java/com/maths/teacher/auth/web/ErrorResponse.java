package com.maths.teacher.auth.web;

/**
 * Standard error body for 401/4xx responses so the client can show the exact message.
 */
public class ErrorResponse {

    private String message;

    public ErrorResponse() {
    }

    public ErrorResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
