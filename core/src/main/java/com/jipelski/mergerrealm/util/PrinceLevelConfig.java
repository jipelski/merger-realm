package com.jipelski.mergerrealm.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Defines what happens at each Prince level:
 * - Grid expansions
 * - Facility unlocks
 * - Reward chests to spawn on unlock
 *
 * Centralized here so balancing is easy — just edit this class.
 */
public class PrinceLevelConfig {

    // ── Grid sizes by prince level ──
    // Only levels that change the grid are listed
    private static final Map<Integer, int[]> GRID_EXPANSIONS = new HashMap<>();
    static {
        //              level → {width, height}
        GRID_EXPANSIONS.put(1,  new int[]{3, 4});  // start
        GRID_EXPANSIONS.put(5, new int[]{4, 4});  // +1 col
        GRID_EXPANSIONS.put(10,  new int[]{4, 5});  // +1 row
        GRID_EXPANSIONS.put(15, new int[]{5, 5});  // +1 col
        GRID_EXPANSIONS.put(20, new int[]{5, 6});  // +1 row
        GRID_EXPANSIONS.put(25, new int[]{5, 7});  // +1 row
        GRID_EXPANSIONS.put(30, new int[]{6, 7});  // +1 col
        GRID_EXPANSIONS.put(35, new int[]{6, 8});  // +1 row (final)
    }

    // ── Facility unlocks by prince level ──
    // Maps level → facility type that unlocks
    private static final Map<Integer, String> FACILITY_UNLOCKS = new HashMap<>();
    static {
        FACILITY_UNLOCKS.put(1,  "homestead");
        FACILITY_UNLOCKS.put(2, "silo");
        FACILITY_UNLOCKS.put(3,  "lodge");
        FACILITY_UNLOCKS.put(4,  "tavernboard");
        FACILITY_UNLOCKS.put(5, "timberyard");
        FACILITY_UNLOCKS.put(6,  "barracks");
        FACILITY_UNLOCKS.put(8, "ironvault");
        FACILITY_UNLOCKS.put(9,  "archeryrange");
        FACILITY_UNLOCKS.put(15, "forge");
        FACILITY_UNLOCKS.put(20, "griffinnest");
        FACILITY_UNLOCKS.put(25, "monastery");
        FACILITY_UNLOCKS.put(30, "dragonslair");
    }

    // ── Reward chests spawned when a facility unlocks ──
    // Maps facility type → chest type that contains the tokens needed to build it
    private static final Map<String, String> UNLOCK_REWARD_CHEST = new HashMap<>();
    static {

        UNLOCK_REWARD_CHEST.put("homestead", "nail_chest");
        UNLOCK_REWARD_CHEST.put("lodge",      "nail_chest");
        UNLOCK_REWARD_CHEST.put("tavernboard",    "slate_chest");
        UNLOCK_REWARD_CHEST.put("barracks",       "ingot_chest");
        UNLOCK_REWARD_CHEST.put("archeryrange",     "ingot_chest");
        UNLOCK_REWARD_CHEST.put("forge",       "ingot_chest");
        UNLOCK_REWARD_CHEST.put("dragonslair", "relic_chest");
        UNLOCK_REWARD_CHEST.put("griffinnest",  "nail_chest");
        UNLOCK_REWARD_CHEST.put("monastery",    "relic_chest");
    }

    // ── XP scaling ──
    private static final int BASE_XP = 50;
    private static final float XP_SCALE = 1.5f;

    /**
     * Returns the grid size for the given prince level,
     * or null if no expansion happens at this level.
     */
    public static int[] getGridSizeAtLevel(int level) {
        return GRID_EXPANSIONS.get(level);
    }

    /**
     * Returns the largest grid size the prince has earned
     * up to and including the given level.
     */
    public static int[] getGridSizeForLevel(int level) {
        int[] best = GRID_EXPANSIONS.get(1); // default 4x4
        for (Map.Entry<Integer, int[]> entry : GRID_EXPANSIONS.entrySet()) {
            if (entry.getKey() <= level) {
                int[] size = entry.getValue();
                if (size[0] * size[1] > best[0] * best[1]) {
                    best = size;
                }
            }
        }
        return best;
    }

    /**
     * Returns the facility type that unlocks at this level,
     * or null if nothing unlocks.
     */
    public static String getFacilityUnlockAtLevel(int level) {
        return FACILITY_UNLOCKS.get(level);
    }

    /**
     * Returns the chest type to spawn when a facility is unlocked,
     * or null if no chest is defined.
     */
    public static String getRewardChest(String facilityType) {
        return UNLOCK_REWARD_CHEST.get(facilityType);
    }

    /**
     * Returns all facility types that should be unlocked
     * at or below the given prince level.
     */
    public static java.util.List<String> getUnlockedFacilities(int level) {
        java.util.List<String> unlocked = new java.util.ArrayList<>();
        for (Map.Entry<Integer, String> entry : FACILITY_UNLOCKS.entrySet()) {
            if (entry.getKey() <= level) {
                unlocked.add(entry.getValue());
            }
        }
        return unlocked;
    }

    /**
     * Returns true if the given facility type is unlocked at the given level.
     */
    public static boolean isFacilityUnlocked(String facilityType, int princeLvl) {
        for (Map.Entry<Integer, String> entry : FACILITY_UNLOCKS.entrySet()) {
            if (entry.getValue().equals(facilityType) && entry.getKey() <= princeLvl) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the XP required to reach the given level.
     */
    public static int getXpRequired(int level) {
        return (int)(BASE_XP * Math.pow(XP_SCALE, level - 1));
    }

    /**
     * Returns true if this level has a grid expansion.
     */
    public static boolean hasGridExpansion(int level) {
        return GRID_EXPANSIONS.containsKey(level) && level > 1;
    }
}
