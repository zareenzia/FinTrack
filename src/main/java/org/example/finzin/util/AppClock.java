package org.example.finzin.util;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Single source of truth for "what day/month is it right now" on the server.
 *
 * The app only serves Bangladesh users, but the server host's own clock is effectively UTC (no
 * {@code app.timezone}/{@code TZ} is configured). Using the bare, zone-less {@code LocalDate.now()}
 * or {@code YearMonth.now()} therefore follows the *host's* UTC calendar day, which can be a full
 * day behind Asia/Dhaka (UTC+6, no DST) for the first six hours of every Dhaka day — "due today",
 * "this month so far", and similar boundaries would then look stale to every user until the
 * server's UTC date catches up. Call {@link #today()} instead everywhere server-side "today"/
 * "this month" matters.
 */
public final class AppClock {

    public static final ZoneId ZONE = ZoneId.of("Asia/Dhaka");

    private AppClock() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }
}
