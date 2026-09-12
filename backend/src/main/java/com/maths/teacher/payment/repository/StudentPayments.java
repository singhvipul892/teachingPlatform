package com.maths.teacher.payment.repository;

import java.time.Instant;

/**
 * How many times one student paid for one course, and when they first did.
 *
 * <p>First payment has to come from here rather than from purchases.purchased_at,
 * which moves forward every time the enrolment is renewed or re-tagged.
 */
public record StudentPayments(Long userId, long paymentCount, Instant firstPaidAt) {
}
