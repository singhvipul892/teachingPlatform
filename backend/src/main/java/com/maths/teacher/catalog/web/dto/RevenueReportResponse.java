package com.maths.teacher.catalog.web.dto;

import java.util.List;

/**
 * Revenue as recorded, not as estimated.
 *
 * <p>Every figure is summed from paid orders in payment_orders, so editing a
 * course price cannot rewrite history, removing a student cannot erase a sale
 * that happened, a student who paid twice counts twice, and a free seat counts
 * nothing.
 */
public record RevenueReportResponse(
        long totalPaise,
        long paymentCount,
        long complimentaryCount,
        /**
         * Offline enrolments from before amounts were tracked. Not part of
         * totalPaise — shown so the total reads as incomplete rather than wrong.
         */
        long untrackedOfflineCount,
        List<SourceLine> bySource,
        List<CourseLine> byCourse
) {
    /** How the money arrived: RAZORPAY online, OFFLINE by hand, COMPLIMENTARY free. */
    public record SourceLine(String source, long amountPaise, long paymentCount) {
    }

    /**
     * @param payerCount distinct students; lower than paymentCount wherever
     *                   somebody renewed or paid again after being removed
     */
    public record CourseLine(
            Long courseId,
            String title,
            boolean active,
            long amountPaise,
            long paymentCount,
            long payerCount
    ) {
    }
}
