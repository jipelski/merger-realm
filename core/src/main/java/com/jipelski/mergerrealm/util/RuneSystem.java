package com.jipelski.mergerrealm.util;

import com.badlogic.gdx.Gdx;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages the rune system — permanent percentage-based stat boosts applied to units.
 *
 * Flow:
 *   1. Player finds Rune Fragments during exploration
 *   2. Collect 5 of the same type → craft a Rune
 *   3. Apply the Rune to a unit → permanent +3% stat boost (or +5% for swiftness)
 *   4. Each stat type capped at 5 applications per unit (15% max, or 15% for swiftness at 3)
 *
 * Rune types:
 *   might     — +3% damage per application, max 5 (15%)
 *   vitality  — +3% HP per application, max 5 (15%)
 *   fortune   — +3% resource gen per application, max 5 (15%)
 *   swiftness — +5% exploration speed per application, max 3 (15%)
 *
 * Applied runes are stored per unit ID in a nested map:
 *   unitId → { "might": 3, "vitality": 2, "fortune": 0, "swiftness": 1 }
 *
 * Fragment counts are tracked globally (not per unit):
 *   { "might": 3, "vitality": 1, "fortune": 4, "swiftness": 0 }
 */
public class RuneSystem {

    private static final String TAG = "RuneSystem";

    // Rune type constants
    public static final String MIGHT = "might";
    public static final String VITALITY = "vitality";
    public static final String FORTUNE = "fortune";
    public static final String SWIFTNESS = "swiftness";

    public static final String[] RUNE_TYPES = { MIGHT, VITALITY, FORTUNE, SWIFTNESS };

    // Fragment item type prefix → rune type mapping
    // "rune_fragment_might" → "might"
    public static final String FRAGMENT_PREFIX = "rune_fragment_";

    // Crafting and application constants
    public static final int FRAGMENTS_PER_RUNE = 5;
    public static final int MAX_APPLICATIONS_STANDARD = 5;   // might, vitality, fortune
    public static final int MAX_APPLICATIONS_SWIFTNESS = 3;  // swiftness

    // Boost percentages
    public static final float BOOST_STANDARD = 0.03f;   // 3% per rune
    public static final float BOOST_SWIFTNESS = 0.05f;  // 5% per rune

    // ── State ──

    // Fragment counts: runeType → count (0-4, crafted at 5)
    private Map<String, Integer> fragmentCounts;

    // Applied runes per unit: unitId → { runeType → applicationCount }
    private Map<String, Map<String, Integer>> appliedRunes;

    // Crafted runes available to apply (not yet used on a unit)
    private Map<String, Integer> craftedRunes;

    public RuneSystem() {
        this.fragmentCounts = new HashMap<>();
        this.appliedRunes = new HashMap<>();
        this.craftedRunes = new HashMap<>();

        // Initialize fragment counts to 0
        for (String type : RUNE_TYPES) {
            fragmentCounts.put(type, 0);
            craftedRunes.put(type, 0);
        }
    }

    // ── Getters / Setters for save/load ──

    public Map<String, Integer> getFragmentCounts() { return fragmentCounts; }
    public void setFragmentCounts(Map<String, Integer> counts) { this.fragmentCounts = counts; }

    public Map<String, Map<String, Integer>> getAppliedRunes() { return appliedRunes; }
    public void setAppliedRunes(Map<String, Map<String, Integer>> applied) { this.appliedRunes = applied; }

    public Map<String, Integer> getCraftedRunes() { return craftedRunes; }
    public void setCraftedRunes(Map<String, Integer> crafted) { this.craftedRunes = crafted; }

    // ══════════════════════════════════════════════════════════════
    // FRAGMENTS
    // ══════════════════════════════════════════════════════════════

    /**
     * Adds a fragment of the given rune type.
     * Called when collecting exploration loot that contains rune fragments.
     *
     * @param runeType "might", "vitality", "fortune", or "swiftness"
     */
    public void addFragment(String runeType) {
        if (!isValidRuneType(runeType)) return;
        int current = fragmentCounts.getOrDefault(runeType, 0);
        fragmentCounts.put(runeType, current + 1);
        Gdx.app.log(TAG, "Fragment added: " + runeType
            + " (" + (current + 1) + "/" + FRAGMENTS_PER_RUNE + ")");
    }

    /**
     * Extracts the rune type from a fragment item type string.
     * "rune_fragment_might" → "might"
     * Returns null if the string doesn't match the expected format.
     */
    public static String getRuneTypeFromFragmentType(String fragmentItemType) {
        if (fragmentItemType == null || !fragmentItemType.startsWith(FRAGMENT_PREFIX)) {
            return null;
        }
        String runeType = fragmentItemType.substring(FRAGMENT_PREFIX.length());
        for (String valid : RUNE_TYPES) {
            if (valid.equals(runeType)) return runeType;
        }
        return null;
    }

    /**
     * Returns the fragment count for the given rune type.
     */
    public int getFragmentCount(String runeType) {
        return fragmentCounts.getOrDefault(runeType, 0);
    }

    /**
     * Returns true if the player has enough fragments to craft a rune of this type.
     */
    public boolean canCraft(String runeType) {
        return getFragmentCount(runeType) >= FRAGMENTS_PER_RUNE;
    }

    // ══════════════════════════════════════════════════════════════
    // CRAFTING
    // ══════════════════════════════════════════════════════════════

    /**
     * Crafts a rune from 5 fragments. Consumes the fragments and
     * adds one crafted rune of that type.
     *
     * @return true if crafting was successful
     */
    public boolean craftRune(String runeType) {
        if (!isValidRuneType(runeType)) return false;

        int fragments = getFragmentCount(runeType);
        if (fragments < FRAGMENTS_PER_RUNE) {
            Gdx.app.log(TAG, "Not enough fragments to craft " + runeType
                + " (" + fragments + "/" + FRAGMENTS_PER_RUNE + ")");
            return false;
        }

        fragmentCounts.put(runeType, fragments - FRAGMENTS_PER_RUNE);
        int crafted = craftedRunes.getOrDefault(runeType, 0);
        craftedRunes.put(runeType, crafted + 1);

        Gdx.app.log(TAG, "Crafted Rune of " + capitalize(runeType)
            + "! (fragments remaining: " + (fragments - FRAGMENTS_PER_RUNE) + ")");
        return true;
    }

    /**
     * Returns the number of crafted runes available for the given type.
     */
    public int getCraftedCount(String runeType) {
        return craftedRunes.getOrDefault(runeType, 0);
    }

    /**
     * Returns true if the player has a crafted rune of this type available.
     */
    public boolean hasCraftedRune(String runeType) {
        return getCraftedCount(runeType) > 0;
    }

    // ══════════════════════════════════════════════════════════════
    // APPLICATION
    // ══════════════════════════════════════════════════════════════

    /**
     * Applies a crafted rune to a unit. Consumes the crafted rune
     * and permanently increases the unit's stat boost count.
     *
     * @param unitId   the unit to apply the rune to
     * @param runeType the rune type to apply
     * @return true if the rune was applied successfully
     */
    public boolean applyRune(String unitId, String runeType) {
        if (!isValidRuneType(runeType)) return false;

        // Check we have a crafted rune
        if (!hasCraftedRune(runeType)) {
            Gdx.app.log(TAG, "No crafted " + runeType + " rune available");
            return false;
        }

        // Check cap
        int currentApplications = getApplicationCount(unitId, runeType);
        int max = getMaxApplications(runeType);
        if (currentApplications >= max) {
            Gdx.app.log(TAG, "Unit " + unitId + " already at max " + runeType
                + " runes (" + currentApplications + "/" + max + ")");
            return false;
        }

        // Consume the crafted rune
        craftedRunes.put(runeType, getCraftedCount(runeType) - 1);

        // Apply to unit
        Map<String, Integer> unitRunes = appliedRunes.computeIfAbsent(
            unitId, k -> new HashMap<>());
        unitRunes.put(runeType, currentApplications + 1);

        Gdx.app.log(TAG, "Applied Rune of " + capitalize(runeType) + " to " + unitId
            + " (" + (currentApplications + 1) + "/" + max + ")");
        return true;
    }

    /**
     * Returns how many times a rune type has been applied to a unit.
     */
    public int getApplicationCount(String unitId, String runeType) {
        Map<String, Integer> unitRunes = appliedRunes.get(unitId);
        if (unitRunes == null) return 0;
        return unitRunes.getOrDefault(runeType, 0);
    }

    /**
     * Returns the max number of applications for a rune type.
     */
    public int getMaxApplications(String runeType) {
        return SWIFTNESS.equals(runeType)
            ? MAX_APPLICATIONS_SWIFTNESS
            : MAX_APPLICATIONS_STANDARD;
    }

    /**
     * Returns true if the unit can receive another rune of this type.
     */
    public boolean canApplyRune(String unitId, String runeType) {
        return getApplicationCount(unitId, runeType) < getMaxApplications(runeType);
    }

    /**
     * Returns true if the unit has any runes applied at all.
     */
    public boolean hasAnyRunes(String unitId) {
        Map<String, Integer> unitRunes = appliedRunes.get(unitId);
        if (unitRunes == null) return false;
        for (int count : unitRunes.values()) {
            if (count > 0) return true;
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════
    // STAT CALCULATIONS
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns the damage multiplier for a unit (1.0 + rune bonus).
     * Example: 2 Might runes = 1.0 + 0.06 = 1.06 (6% boost)
     */
    public float getDamageMultiplier(String unitId) {
        int mightCount = getApplicationCount(unitId, MIGHT);
        return 1.0f + (mightCount * BOOST_STANDARD);
    }

    /**
     * Returns the HP multiplier for a unit.
     */
    public float getHpMultiplier(String unitId) {
        int vitalityCount = getApplicationCount(unitId, VITALITY);
        return 1.0f + (vitalityCount * BOOST_STANDARD);
    }

    /**
     * Returns the resource gen multiplier for a unit.
     */
    public float getGenRateMultiplier(String unitId) {
        int fortuneCount = getApplicationCount(unitId, FORTUNE);
        return 1.0f + (fortuneCount * BOOST_STANDARD);
    }

    /**
     * Returns the exploration speed multiplier for a unit.
     * Applied as a reduction to event interval (faster events).
     */
    public float getExplorationSpeedMultiplier(String unitId) {
        int swiftnessCount = getApplicationCount(unitId, SWIFTNESS);
        return 1.0f + (swiftnessCount * BOOST_SWIFTNESS);
    }

    /**
     * Calculates boosted damage for a unit: baseDamage * (1 + runeBonus).
     */
    public int getBoostedDamage(String unitId, int baseDamage) {
        return Math.round(baseDamage * getDamageMultiplier(unitId));
    }

    /**
     * Calculates boosted max HP for a unit: baseHp * (1 + runeBonus).
     */
    public int getBoostedMaxHp(String unitId, int baseHp) {
        return Math.round(baseHp * getHpMultiplier(unitId));
    }

    /**
     * Calculates boosted gen rate for a unit: baseRate * (1 + runeBonus).
     */
    public int getBoostedGenRate(String unitId, int baseRate) {
        return Math.round(baseRate * getGenRateMultiplier(unitId));
    }

    /**
     * Cleans up rune data when a unit is permanently removed (death, not dismiss).
     * For dismissal, runes stay — they're tied to the unit ID which won't be reused.
     */
    public void onUnitPermanentlyLost(String unitId) {
        if (appliedRunes.remove(unitId) != null) {
            Gdx.app.log(TAG, "Rune data cleared for lost unit " + unitId);
        }
    }

    /**
     * Returns a summary string for display: "Might ×3, Vitality ×2"
     */
    public String getRuneSummary(String unitId) {
        Map<String, Integer> unitRunes = appliedRunes.get(unitId);
        if (unitRunes == null) return null;

        StringBuilder sb = new StringBuilder();
        for (String type : RUNE_TYPES) {
            int count = unitRunes.getOrDefault(type, 0);
            if (count > 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(capitalize(type)).append(" x").append(count);
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    private boolean isValidRuneType(String runeType) {
        for (String valid : RUNE_TYPES) {
            if (valid.equals(runeType)) return true;
        }
        Gdx.app.log(TAG, "Invalid rune type: " + runeType);
        return false;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
