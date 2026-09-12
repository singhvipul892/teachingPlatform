package com.maths.teacher.payment.repository;

/** One course's takings, summed from paid orders rather than from a head count. */
public record CourseRevenue(Long courseId, long amountPaise, long paymentCount, long payerCount) {
}
