package com.capstone.dsaplatform.support;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rounding happens only when building responses; stored and intermediate values keep full
 * precision so rounding errors never compound (e.g. a gap computed from two rounded numbers).
 */
public final class Rounding {

    private Rounding() {
    }

    // 0-100 scale values (readiness, gap) are shown to 1 decimal (CONTRACT.md section 0).
    public static Double oneDecimal(Double value) {
        return value == null ? null : round(value, 1);
    }

    // 0-1 values (retention, share, score) need 2 decimals to be meaningful.
    public static Double twoDecimals(Double value) {
        return value == null ? null : round(value, 2);
    }

    private static double round(double value, int places) {
        return BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_UP).doubleValue();
    }
}
