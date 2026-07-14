package com.jipelski.mergerrealm.util;

import com.jipelski.mergerrealm.testutil.GdxTestSupport;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers AdManager's pure logic — the daily-window reset, per-action count
 * budget, and save-state packing — all of which never dereference
 * `eventManager` (confirmed by reading the class: only watchAd() touches it,
 * to reach GoldManager's do* effect cores). AdManager is constructed with a
 * null EventManager here, safe specifically because none of the methods
 * under test ever call watchAd(). Mirrors GoldManagerTest's outer-class
 * null-backed style.
 */
class AdManagerTest {

    @BeforeAll
    static void setUpGdx() {
        GdxTestSupport.ensureInitialized();
    }

    private AdManager ads;

    @BeforeEach
    void setUp() {
        ads = new AdManager(null);
    }

    @Test
    void freshManager_allowsFivePerActionPerDay() {
        for (AdManager.AdAction action : AdManager.AdAction.values()) {
            assertEquals(AdManager.MAX_PER_DAY, ads.getRemaining(action));
            assertTrue(ads.canWatch(action));
        }
    }

    @Test
    void canWatch_falseAfterBudgetExhausted() {
        // Craft a save state as if FILL_RESOURCES had already been watched
        // 5 times today: { windowHigh32, windowLow32, count0, count1, count2, count3 }.
        long now = System.currentTimeMillis();
        int[] state = {
            (int) (now >>> 32), (int) now,
            AdManager.MAX_PER_DAY, 0, 0, 0
        };
        ads.setSaveState(state);

        assertFalse(ads.canWatch(AdManager.AdAction.FILL_RESOURCES));
        assertEquals(0, ads.getRemaining(AdManager.AdAction.FILL_RESOURCES));

        // Other actions are independent budgets, untouched.
        assertTrue(ads.canWatch(AdManager.AdAction.REVIVE_PARTY));
        assertEquals(AdManager.MAX_PER_DAY, ads.getRemaining(AdManager.AdAction.REVIVE_PARTY));
    }

    @Test
    void checkDailyReset_restoresBudgetAfterWindowElapses() {
        long overADayAgo = System.currentTimeMillis() - AdManager.DAY_MS - 1000L;
        int[] state = {
            (int) (overADayAgo >>> 32), (int) overADayAgo,
            AdManager.MAX_PER_DAY, AdManager.MAX_PER_DAY, AdManager.MAX_PER_DAY, AdManager.MAX_PER_DAY
        };
        ads.setSaveState(state);

        // canWatch()/getRemaining() both trigger checkDailyReset() lazily.
        for (AdManager.AdAction action : AdManager.AdAction.values()) {
            assertTrue(ads.canWatch(action));
            assertEquals(AdManager.MAX_PER_DAY, ads.getRemaining(action));
        }
    }

    @Test
    void checkDailyReset_keepsBudgetWithinWindow() {
        long recentlyStarted = System.currentTimeMillis() - 1000L; // 1s ago, well within 24h
        int[] state = {
            (int) (recentlyStarted >>> 32), (int) recentlyStarted,
            3, 0, 0, 0
        };
        ads.setSaveState(state);

        assertEquals(2, ads.getRemaining(AdManager.AdAction.FILL_RESOURCES));
    }

    @Test
    void saveState_roundTrips() {
        long ms = System.currentTimeMillis();
        int[] original = {
            (int) (ms >>> 32), (int) ms,
            1, 2, 3, 4
        };
        ads.setSaveState(original);
        assertArrayEquals(original, ads.getSaveState());
    }

    @Test
    void setSaveState_nullOrShortArray_isNoOp() {
        ads.setSaveState(null);
        ads.setSaveState(new int[]{1, 2, 3}); // too short (needs 6)

        for (AdManager.AdAction action : AdManager.AdAction.values()) {
            assertEquals(AdManager.MAX_PER_DAY, ads.getRemaining(action));
        }
    }
}
