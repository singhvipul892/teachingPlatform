package com.maths.teacher.payment.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Owns the single rule that turns a course's validity into a student's expiry
 * instant, so the website, the app and the admin panel can never disagree about
 * when access ends.
 *
 * Access runs to the END of the day validityDays after purchase, in Indian
 * time: a 180-day course bought on 10 Sep is usable through all of 9 Mar,
 * rather than lapsing at whatever hour of the afternoon the payment happened to
 * go through. Ending on a whole day is deliberate — it is what a student reads
 * "valid till 9 Mar" to mean.
 */
public final class AccessExpiry {

    /** Students are in India; expiry days are theirs, not the server's. */
    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private AccessExpiry() {
    }

    /**
     * @param validityDays the course's validity at the time of purchase; 0 or
     *                     less means lifetime access
     * @return the instant access ends, or null for lifetime access
     */
    public static Instant from(Instant purchasedAt, int validityDays) {
        if (validityDays <= 0) {
            return null;
        }
        return endOfDay(purchasedAt.atZone(ZONE).toLocalDate().plusDays(validityDays));
    }

    /**
     * Access runs to the end of the given Indian day.
     *
     * 23:59:59 rather than LocalTime.MAX: Postgres timestamps hold microseconds,
     * so MAX's .999999999 rounds UP to 00:00:00 the next day on the way into the
     * column — which silently reports every expiry a day late. Whole seconds are
     * storable exactly, and the second we give up is not one anyone can notice.
     */
    public static Instant endOfDay(LocalDate lastDay) {
        return lastDay.atTime(LocalTime.of(23, 59, 59)).atZone(ZONE).toInstant();
    }

    /**
     * The last day access is usable, in Indian time. Null for lifetime access.
     * Clients get this rather than a raw instant so nobody has to redo the
     * timezone arithmetic — the Android app in particular cannot, since it runs
     * on API 24 without java.time.
     */
    public static LocalDate lastDay(Instant expiresAt) {
        return expiresAt == null ? null : expiresAt.atZone(ZONE).toLocalDate();
    }

    /**
     * Whole days from today until access ends, in Indian time. 0 means access
     * ends tonight; negative means it already has; null means lifetime access.
     */
    public static Integer daysRemaining(Instant expiresAt, Instant now) {
        if (expiresAt == null) {
            return null;
        }
        LocalDate today = now.atZone(ZONE).toLocalDate();
        return (int) ChronoUnit.DAYS.between(today, lastDay(expiresAt));
    }
}
