package com.maths.teacher.catalog.exception;

/**
 * Centralized error messages for the application.
 * Messages are organized by domain/feature for easy maintenance.
 */
public final class ErrorMessages {

    private ErrorMessages() {
        // Utility class - prevent instantiation
    }

    // ==================== Video-related Errors ====================

    public static final String VIDEO_NOT_FOUND = "Video not found";

    // ==================== PDF-related Errors ====================

    public static final String PDF_NOT_FOUND = "PDF not found";

    public static final String PDF_TYPE_ALREADY_EXISTS_TEMPLATE = 
            "A PDF with type '%s' already exists for this video. Please use the update endpoint to modify it.";

    // ==================== Authentication/Authorization Errors ====================

    public static final String MISSING_AUTHORIZATION_HEADER = "Missing Authorization header";

    public static final String ACCESS_DENIED = "Access denied";

    /** Shown when login identifier (email or mobile) does not match any account. */
    public static final String NO_ACCOUNT_FOUND_EMAIL_OR_MOBILE = "No account found with this email or mobile number.";

    /** Shown when the password is wrong for an existing account. */
    public static final String PASSWORD_INVALID = "Password is invalid.";

    public static final String EMAIL_ALREADY_REGISTERED = "An account with this email is already registered.";

    public static final String MOBILE_ALREADY_REGISTERED = "An account with this mobile number is already registered.";

    // ==================== YouTube URL Errors ====================

    public static final String YOUTUBE_URL_CANNOT_BE_NULL_OR_EMPTY = 
            "YouTube URL cannot be null or empty";

    public static final String INVALID_YOUTUBE_URL_FORMAT = 
            "Invalid YouTube URL format. Supported formats: " +
            "https://www.youtube.com/watch?v=VIDEO_ID, " +
            "https://youtu.be/VIDEO_ID, " +
            "https://www.youtube.com/embed/VIDEO_ID";

    // ==================== S3/Storage Configuration Errors ====================

    public static final String S3_REGION_REQUIRED = 
            "S3 region is required (app.storage.s3.region).";

    public static final String S3_BUCKET_REQUIRED = 
            "S3 bucket is required (app.storage.s3.bucket).";

    // ==================== S3/Storage URL Errors ====================

    public static final String STORAGE_URL_REQUIRED = "storageUrl is required";

    public static final String INVALID_S3_URL_TEMPLATE = "Invalid s3 url: %s";

    public static final String UNSUPPORTED_S3_URL_TEMPLATE = "Unsupported S3 URL: %s";

    // ==================== File Upload Errors ====================

    public static final String FAILED_TO_READ_PDF_FILE = "Failed to read PDF file";

    // ==================== Helper Methods ====================

    /**
     * Formats the PDF type already exists error message with the given PDF type.
     *
     * @param pdfType The PDF type that already exists
     * @return Formatted error message
     */
    public static String pdfTypeAlreadyExists(String pdfType) {
        return String.format(PDF_TYPE_ALREADY_EXISTS_TEMPLATE, pdfType);
    }

    /**
     * Formats the invalid S3 URL error message with the given URL.
     *
     * @param storageUrl The invalid storage URL
     * @return Formatted error message
     */
    public static String invalidS3Url(String storageUrl) {
        return String.format(INVALID_S3_URL_TEMPLATE, storageUrl);
    }

    /**
     * Formats the unsupported S3 URL error message with the given URL.
     *
     * @param storageUrl The unsupported storage URL
     * @return Formatted error message
     */
    public static String unsupportedS3Url(String storageUrl) {
        return String.format(UNSUPPORTED_S3_URL_TEMPLATE, storageUrl);
    }
}
