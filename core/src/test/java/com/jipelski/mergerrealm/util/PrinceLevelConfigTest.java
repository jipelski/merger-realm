package com.jipelski.mergerrealm.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrinceLevelConfigTest {

    @Test
    void gridSizeAtLevel_exactMatchOnly() {
        assertArrayEquals(new int[]{3, 4}, PrinceLevelConfig.getGridSizeAtLevel(1));
        assertArrayEquals(new int[]{6, 8}, PrinceLevelConfig.getGridSizeAtLevel(35));
        // 12 isn't one of the level keys that expands the grid — exact lookup is null,
        // unlike getGridSizeForLevel below which returns the largest earned so far.
        assertNull(PrinceLevelConfig.getGridSizeAtLevel(12));
    }

    @Test
    void gridSizeForLevel_returnsLargestEarnedSoFar() {
        assertArrayEquals(new int[]{3, 4}, PrinceLevelConfig.getGridSizeForLevel(1));
        // Between the level-10 (4x5, area 20) and level-15 (5x5, area 25) expansions —
        // should hold at the level-10 size, not null and not the next tier.
        assertArrayEquals(new int[]{4, 5}, PrinceLevelConfig.getGridSizeForLevel(12));
        assertArrayEquals(new int[]{6, 8}, PrinceLevelConfig.getGridSizeForLevel(35));
        // Past the final defined expansion — should hold at the max, not null.
        assertArrayEquals(new int[]{6, 8}, PrinceLevelConfig.getGridSizeForLevel(99));
    }

    @Test
    void hasGridExpansion_level1IsFalseDespiteBeingInTheMap() {
        // Easy edge case to miss: level 1 IS a key in GRID_EXPANSIONS (the
        // starting size), but hasGridExpansion() has an explicit level > 1
        // guard, so it reports false for the starting level.
        assertFalse(PrinceLevelConfig.hasGridExpansion(1));
        assertTrue(PrinceLevelConfig.hasGridExpansion(5));
        assertFalse(PrinceLevelConfig.hasGridExpansion(2)); // not a key at all
    }

    @Test
    void xpRequired_scalesByBaseAndExponent() {
        // BASE_XP=50, XP_SCALE=1.5f: 50 * 1.5^(level-1), truncated to int.
        assertEquals(50, PrinceLevelConfig.getXpRequired(1));
        assertEquals(75, PrinceLevelConfig.getXpRequired(2));
        assertEquals(112, PrinceLevelConfig.getXpRequired(3)); // 112.5 truncated, not rounded
    }

    @Test
    void facilityUnlockAtLevel_exactMatchOnly() {
        assertEquals("silo", PrinceLevelConfig.getFacilityUnlockAtLevel(2));
        assertEquals("barracks", PrinceLevelConfig.getFacilityUnlockAtLevel(6));
        assertNull(PrinceLevelConfig.getFacilityUnlockAtLevel(7)); // no unlock at level 7
    }

    @Test
    void isFacilityUnlocked_boundaryLevels() {
        assertFalse(PrinceLevelConfig.isFacilityUnlocked("archeryrange", 8));
        assertTrue(PrinceLevelConfig.isFacilityUnlocked("archeryrange", 9));
        assertTrue(PrinceLevelConfig.isFacilityUnlocked("archeryrange", 20));
    }

    @Test
    void unlockedFacilities_skipsLevelsWithNoUnlock() {
        // Levels 1-9 unlock at 1,2,3,4,5,6,8,9 (level 7 has none) — 8 entries by level 9.
        assertEquals(8, PrinceLevelConfig.getUnlockedFacilities(9).size());
        assertTrue(PrinceLevelConfig.getUnlockedFacilities(9).contains("archeryrange"));
        assertFalse(PrinceLevelConfig.getUnlockedFacilities(6).contains("archeryrange"));
    }

    @Test
    void rewardChest_storageTypesHaveNoRewardChest() {
        // Only FACILITIES have an unlock-reward chest defined — STORAGE
        // types (silo/timberyard/ironvault) deliberately don't, since they're
        // unlocked without a chest-gated build-token requirement.
        assertEquals("nail_chest", PrinceLevelConfig.getRewardChest("homestead"));
        assertEquals("relic_chest", PrinceLevelConfig.getRewardChest("dragonslair"));
        assertNull(PrinceLevelConfig.getRewardChest("silo"));
        assertNull(PrinceLevelConfig.getRewardChest("timberyard"));
    }
}
