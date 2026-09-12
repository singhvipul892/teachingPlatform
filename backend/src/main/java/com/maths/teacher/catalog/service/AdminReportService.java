package com.maths.teacher.catalog.service;

import com.maths.teacher.catalog.web.dto.PaymentRecordResponse;
import com.maths.teacher.catalog.web.dto.RevenueReportResponse;
import com.maths.teacher.payment.domain.Course;
import com.maths.teacher.payment.domain.PaymentOrder;
import com.maths.teacher.payment.repository.CourseRepository;
import com.maths.teacher.payment.repository.CourseRevenue;
import com.maths.teacher.payment.repository.PaymentOrderRepository;
import com.maths.teacher.payment.repository.SourceRevenue;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reporting read from the ledger.
 *
 * <p>The dashboard used to work revenue out in the browser as
 * {@code course price × student count}. That was wrong in four ways at once:
 * editing a price rewrote every past sale, removing a student erased a sale that
 * really happened, a student who paid twice counted once, and an admin-tagged
 * student counted at full list price whether or not any money arrived. None of
 * those are edge cases — they are ordinary days.
 *
 * <p>Everything here sums paid orders instead, which is the only record of what
 * was actually received.
 */
@Service
public class AdminReportService {

    private static final Logger logger = LoggerFactory.getLogger(AdminReportService.class);

    private final PaymentOrderRepository paymentOrderRepository;
    private final CourseRepository courseRepository;

    public AdminReportService(
            PaymentOrderRepository paymentOrderRepository,
            CourseRepository courseRepository
    ) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.courseRepository = courseRepository;
    }

    public RevenueReportResponse getRevenueReport() {
        logger.info("Building revenue report from payment ledger");

        List<SourceRevenue> bySource = paymentOrderRepository.revenueBySource();
        List<CourseRevenue> byCourse = paymentOrderRepository.revenueByCourse();

        Map<Long, Course> courses = courseRepository.findAll().stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));

        long total = bySource.stream().mapToLong(SourceRevenue::amountPaise).sum();
        long payments = bySource.stream().mapToLong(SourceRevenue::paymentCount).sum();
        long complimentary = bySource.stream()
                .filter(line -> PaymentOrder.Source.COMPLIMENTARY.name().equals(line.source()))
                .mapToLong(SourceRevenue::paymentCount)
                .sum();

        List<RevenueReportResponse.SourceLine> sourceLines = bySource.stream()
                .map(line -> new RevenueReportResponse.SourceLine(
                        line.source(), line.amountPaise(), line.paymentCount()))
                .sorted(Comparator.comparing(RevenueReportResponse.SourceLine::source))
                .toList();

        // A course deleted outright would leave orders behind; keep the money
        // visible and say so rather than dropping the line.
        List<RevenueReportResponse.CourseLine> courseLines = byCourse.stream()
                .map(line -> {
                    Course course = courses.get(line.courseId());
                    return new RevenueReportResponse.CourseLine(
                            line.courseId(),
                            course != null ? course.getTitle() : "(deleted course)",
                            course != null && course.isActive(),
                            line.amountPaise(),
                            line.paymentCount(),
                            line.payerCount());
                })
                .sorted(Comparator.comparingLong(RevenueReportResponse.CourseLine::amountPaise).reversed())
                .toList();

        return new RevenueReportResponse(total, payments, complimentary,
                paymentOrderRepository.countOfflineWithoutAmount(), sourceLines, courseLines);
    }

    /**
     * Every payment one student made for one course, oldest first — the answer to
     * "how many times did they actually buy this?", which the enrolment row cannot
     * give because there is only ever one of it.
     */
    public List<PaymentRecordResponse> getStudentPayments(Long courseId, Long userId) {
        return paymentOrderRepository.findPaidFor(userId, courseId).stream()
                .map(order -> new PaymentRecordResponse(
                        order.getCreatedAt(),
                        order.getAmountPaise(),
                        order.getCurrency(),
                        order.getSource(),
                        // The order id is synthetic for offline sales and means
                        // nothing to a person; fall back to it only if there is
                        // genuinely no reference.
                        order.getReference() != null ? order.getReference() : order.getRazorpayOrderId()))
                .toList();
    }
}
