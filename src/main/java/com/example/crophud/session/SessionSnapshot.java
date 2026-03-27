package com.example.crophud.session;

import com.example.crophud.hud.HudAnchor;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record SessionSnapshot(long activeMillis, int harvestedUnits, BigDecimal totalValue, HudAnchor hudAnchor, boolean active, String currentCropId) {
    private static final long MIN_ACTIVE_MILLIS_FOR_RATE = 30_000L;

    /** Returns the estimated hourly value, or {@code null} when active time is under 30 seconds. */
    public BigDecimal valuePerHour() {
        if (activeMillis < MIN_ACTIVE_MILLIS_FOR_RATE) {
            return null;
        }
        return totalValue.multiply(BigDecimal.valueOf(3_600_000L))
                .divide(BigDecimal.valueOf(activeMillis), 2, RoundingMode.HALF_UP);
    }
}
