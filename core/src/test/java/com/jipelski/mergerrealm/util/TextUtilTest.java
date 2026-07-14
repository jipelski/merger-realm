package com.jipelski.mergerrealm.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TextUtilTest {

    @Test
    void capitalize_normalWord() {
        assertEquals("Villager", TextUtil.capitalize("villager"));
    }

    @Test
    void capitalize_alreadyCapitalized() {
        assertEquals("Villager", TextUtil.capitalize("Villager"));
    }

    @Test
    void capitalize_singleChar() {
        assertEquals("A", TextUtil.capitalize("a"));
    }

    @Test
    void capitalize_emptyString_returnsUnchanged() {
        assertEquals("", TextUtil.capitalize(""));
    }

    @Test
    void capitalize_null_returnsNull() {
        assertNull(TextUtil.capitalize(null));
    }

    @Test
    void formatDurationMs_zero_isZeroSeconds() {
        assertEquals("0s", TextUtil.formatDurationMs(0));
    }

    @Test
    void formatDurationMs_secondsOnly() {
        assertEquals("42s", TextUtil.formatDurationMs(42_000));
    }

    @Test
    void formatDurationMs_minutesAndSeconds() {
        assertEquals("1m 30s", TextUtil.formatDurationMs(90_000));
    }

    @Test
    void formatDurationMs_hoursAndMinutes_dropsSeconds() {
        assertEquals("1h 23m", TextUtil.formatDurationMs((3600 + 23 * 60 + 45) * 1000L));
    }

    @Test
    void formatDurationMsShort_zeroOrNegative_isNow() {
        assertEquals("now", TextUtil.formatDurationMsShort(0));
        assertEquals("now", TextUtil.formatDurationMsShort(-500));
    }

    @Test
    void formatDurationMsShort_neverShowsSeconds() {
        // Same 90s input as formatDurationMs_minutesAndSeconds above —
        // deliberately asserted here too, so the two formatters' documented
        // disagreement on identical input is pinned down explicitly rather
        // than assumed. See TextUtil's own class javadoc: these are NOT
        // interchangeable.
        assertEquals("1m", TextUtil.formatDurationMsShort(90_000));
    }

    @Test
    void formatDurationMsShort_hoursAndMinutes() {
        assertEquals("1h 23m", TextUtil.formatDurationMsShort((3600 + 23 * 60 + 45) * 1000L));
    }

    @Test
    void theTwoMsFormatters_disagreeOnZero_byDesign() {
        // formatDurationMs(0) == "0s", formatDurationMsShort(0) == "now" —
        // a regression guard against someone "fixing" these into agreement.
        assertEquals("0s", TextUtil.formatDurationMs(0));
        assertEquals("now", TextUtil.formatDurationMsShort(0));
    }

    @Test
    void formatDurationSeconds_basic() {
        assertEquals("2m 5s", TextUtil.formatDurationSeconds(125));
    }

    @Test
    void formatDurationSeconds_zero() {
        assertEquals("0m 0s", TextUtil.formatDurationSeconds(0));
    }
}
