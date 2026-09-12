package com.maths.teacher.catalog.web.dto;

import java.time.Instant;

/**
 * One payment, as it will be shown next to a student's name.
 *
 * @param source    RAZORPAY, OFFLINE or COMPLIMENTARY
 * @param reference the gateway order id, or the transaction id an admin typed in
 */
public record PaymentRecordResponse(
        Instant paidAt,
        long amountPaise,
        String currency,
        String source,
        String reference
) {
}
