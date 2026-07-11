package com.jipelski.mergerrealm.util;

/**
 * Shared text/formatting helpers. Consolidates copies that were duplicated
 * (private, byte-identical) across ~8 files — see CLAUDE.md's cleanup pass.
 */
public final class TextUtil {

    private TextUtil() {} // static-only

    /**
     * Capitalizes the first letter of a string. Returns the input unchanged
     * if null or empty.
     */
    public static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    /**
     * Formats a millisecond duration as "1h 23m" / "5m 3s" / "42s" (from
     * ExplorePanel — full h/m/s tiers, no zero-guard: 0ms -> "0s").
     */
    public static String formatDurationMs(long ms) {
        long totalSec = ms / 1000;
        long hours = totalSec / 3600;
        long min = (totalSec % 3600) / 60;
        long sec = totalSec % 60;
        if (hours > 0) return hours + "h " + min + "m";
        if (min > 0) return min + "m " + sec + "s";
        return sec + "s";
    }

    /**
     * Formats a millisecond duration as "1h 23m" / "23m" / "now" (from
     * TrophyShopPanel — coarser than {@link #formatDurationMs}: never shows a
     * seconds component, and guards ms&lt;=0 as "now". NOT interchangeable
     * with formatDurationMs — they disagree on output for the same input.
     */
    public static String formatDurationMsShort(long ms) {
        if (ms <= 0) return "now";
        long totalSec = ms / 1000;
        long hours = totalSec / 3600;
        long min = (totalSec % 3600) / 60;
        if (hours > 0) return hours + "h " + min + "m";
        return min + "m";
    }

    /**
     * Formats a SECOND duration (not ms) as "Xm Ys" (from ExplorationManager).
     */
    public static String formatDurationSeconds(long seconds) {
        long min = seconds / 60;
        long sec = seconds % 60;
        return String.format("%dm %ds", min, sec);
    }
}
