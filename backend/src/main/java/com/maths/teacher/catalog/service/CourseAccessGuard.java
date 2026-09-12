package com.maths.teacher.catalog.service;

import com.maths.teacher.payment.domain.Purchase;
import com.maths.teacher.payment.repository.PurchaseRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * The single rule for "may this student open this course's material".
 *
 * <p>It lives on its own because it has to answer the same way for every kind of
 * content. Videos enforced it and PDF downloads did not, which meant an expired
 * or removed student kept every set of notes they could guess the id of — the
 * listing hid the PDFs, nothing stopped the download.
 *
 * <p>Anything that serves paid material goes through here.
 */
@Component
public class CourseAccessGuard {

    private static final Logger logger = LoggerFactory.getLogger(CourseAccessGuard.class);

    private final PurchaseRepository purchaseRepository;

    public CourseAccessGuard(PurchaseRepository purchaseRepository) {
        this.purchaseRepository = purchaseRepository;
    }

    /**
     * Throws unless the student currently has access to the course.
     *
     * @return their enrolment, for callers that need the expiry
     * @throws ResponseStatusException 403 if they never bought it, an admin removed
     *                                 them, or their access has lapsed
     */
    public Purchase requireAccess(Long userId, Long courseId) {
        // findEnrolled already excludes students an admin removed.
        Purchase purchase = purchaseRepository.findEnrolled(userId, courseId)
                .orElseThrow(() -> {
                    logger.info("Access denied: user {} has no live enrolment on course {}", userId, courseId);
                    return new ResponseStatusException(HttpStatus.FORBIDDEN, "You have not purchased this course.");
                });

        if (purchase.isExpired(Instant.now())) {
            logger.info("Access expired for user {} on course {} (expired {})",
                    userId, courseId, purchase.getExpiresAt());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your access to this course has expired.");
        }

        return purchase;
    }
}
