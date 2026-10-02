package com.capstone.dsaplatform.support;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** The DB stores UTC LocalDateTime; the API sends ISO-8601 with "Z". This is the one bridge. */
public final class Times {

    private Times() {
    }

    // Seconds precision: the contract format is "2026-10-02T09:15:00Z", and sub-second noise only clutters JSON.
    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
    }

    // Instant serialises with a trailing "Z", which is what the contract requires.
    public static Instant toInstant(LocalDateTime utc) {
        return utc == null ? null : utc.toInstant(ZoneOffset.UTC);
    }
}
