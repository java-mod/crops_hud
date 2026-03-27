package com.example.crophud.client;

import com.example.crophud.hud.HudAnchor;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ClientSessionState {
    private static volatile long activeMillis;
    private static volatile int harvestedUnits;
    private static volatile BigDecimal totalValue = BigDecimal.ZERO;
    private static volatile HudAnchor hudAnchor = HudAnchor.TOP_RIGHT;
    private static volatile boolean active;
    private static volatile String currentCropId = "minecraft:wheat";
    private static volatile String lockedCropId  = "";
    private static volatile int specialDrops;

    private ClientSessionState() {
    }

    public static void update(long activeMillis, int harvestedUnits, BigDecimal totalValue,
                              HudAnchor hudAnchor, boolean active, String currentCropId,
                              int specialDrops, String lockedCropId) {
        ClientSessionState.activeMillis    = activeMillis;
        ClientSessionState.harvestedUnits  = harvestedUnits;
        ClientSessionState.totalValue      = totalValue == null ? BigDecimal.ZERO : totalValue;
        ClientSessionState.hudAnchor       = hudAnchor;
        ClientSessionState.active          = active;
        ClientSessionState.currentCropId   = currentCropId;
        ClientSessionState.specialDrops    = specialDrops;
        ClientSessionState.lockedCropId    = lockedCropId == null ? "" : lockedCropId;
    }

    public static long activeMillis()    { return activeMillis; }
    public static int harvestedUnits()   { return harvestedUnits; }
    public static BigDecimal totalValue(){ return totalValue; }
    public static HudAnchor hudAnchor()  { return hudAnchor; }
    public static boolean active()       { return active; }
    public static String currentCropId() { return currentCropId; }
    public static String lockedCropId()  { return lockedCropId; }
    public static int specialDrops()     { return specialDrops; }

    public static void reset() {
        update(0L, 0, BigDecimal.ZERO, HudAnchor.TOP_RIGHT, false, "", 0, "");
    }

    // Minimum active time before the hourly rate is meaningful.
    // Require at least 1 full minute so the per-minute floor always gives a non-zero denominator.
    private static final long MIN_ACTIVE_MILLIS_FOR_RATE = 60_000L;

    /**
     * Returns the estimated hourly value rounded to the nearest integer, or {@code null}
     * if less than 1 full minute has elapsed.
     *
     * <p>The active time is floored to whole minutes before dividing so the displayed
     * value only updates once per minute, preventing constant width changes that cause
     * the HUD background to jitter.
     */
    public static BigDecimal valuePerHour() {
        long minuteMillis = (activeMillis / 60_000L) * 60_000L;
        if (minuteMillis < MIN_ACTIVE_MILLIS_FOR_RATE) {
            return null;
        }
        return totalValue.multiply(BigDecimal.valueOf(3_600_000L))
                .divide(BigDecimal.valueOf(minuteMillis), 0, RoundingMode.HALF_UP);
    }
}
