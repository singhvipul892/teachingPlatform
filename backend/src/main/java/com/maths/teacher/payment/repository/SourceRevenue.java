package com.maths.teacher.payment.repository;

/** Takings split by how the money arrived, so online and offline are legible apart. */
public record SourceRevenue(String source, long amountPaise, long paymentCount) {
}
