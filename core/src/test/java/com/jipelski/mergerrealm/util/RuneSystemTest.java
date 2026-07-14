package com.jipelski.mergerrealm.util;

import com.jipelski.mergerrealm.testutil.GdxTestSupport;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuneSystemTest {

    @BeforeAll
    static void setUpGdx() {
        // Nearly every method here logs via Gdx.app — needs a live Gdx.app.
        GdxTestSupport.ensureInitialized();
    }

    private RuneSystem runes;

    @BeforeEach
    void setUp() {
        runes = new RuneSystem();
    }

    @Test
    void addFragment_incrementsCount() {
        runes.addFragment(RuneSystem.MIGHT);
        assertEquals(1, runes.getFragmentCount(RuneSystem.MIGHT));
        runes.addFragment(RuneSystem.MIGHT);
        assertEquals(2, runes.getFragmentCount(RuneSystem.MIGHT));
    }

    @Test
    void addFragment_invalidType_isSilentlyIgnored() {
        runes.addFragment("not_a_real_rune_type");
        assertEquals(0, runes.getFragmentCount("not_a_real_rune_type"));
    }

    @Test
    void getRuneTypeFromFragmentType_parsesPrefix() {
        assertEquals("might", RuneSystem.getRuneTypeFromFragmentType("rune_fragment_might"));
        assertNull(RuneSystem.getRuneTypeFromFragmentType("rune_fragment_bogus"));
        assertNull(RuneSystem.getRuneTypeFromFragmentType("not_a_fragment"));
        assertNull(RuneSystem.getRuneTypeFromFragmentType(null));
    }

    @Test
    void craftRune_belowThreshold_fails() {
        for (int i = 0; i < 4; i++) runes.addFragment(RuneSystem.VITALITY);
        assertFalse(runes.canCraft(RuneSystem.VITALITY));
        assertFalse(runes.craftRune(RuneSystem.VITALITY));
        assertEquals(4, runes.getFragmentCount(RuneSystem.VITALITY)); // unchanged
        assertEquals(0, runes.getCraftedCount(RuneSystem.VITALITY));
    }

    @Test
    void craftRune_atThreshold_succeedsAndConsumesFragments() {
        for (int i = 0; i < RuneSystem.FRAGMENTS_PER_RUNE; i++) runes.addFragment(RuneSystem.VITALITY);
        assertTrue(runes.canCraft(RuneSystem.VITALITY));
        assertTrue(runes.craftRune(RuneSystem.VITALITY));
        assertEquals(0, runes.getFragmentCount(RuneSystem.VITALITY));
        assertEquals(1, runes.getCraftedCount(RuneSystem.VITALITY));
    }

    @Test
    void applyRune_noCraftedRune_fails() {
        assertFalse(runes.applyRune("unit1", RuneSystem.MIGHT));
        assertEquals(0, runes.getApplicationCount("unit1", RuneSystem.MIGHT));
    }

    @Test
    void applyRune_consumesCraftedRuneAndIncrementsApplication() {
        setCrafted(RuneSystem.MIGHT, 1);
        assertTrue(runes.applyRune("unit1", RuneSystem.MIGHT));
        assertEquals(1, runes.getApplicationCount("unit1", RuneSystem.MIGHT));
        assertEquals(0, runes.getCraftedCount(RuneSystem.MIGHT));
    }

    @Test
    void applyRune_standardCap_isFiveThenRejects() {
        setCrafted(RuneSystem.MIGHT, RuneSystem.MAX_APPLICATIONS_STANDARD + 1);
        for (int i = 0; i < RuneSystem.MAX_APPLICATIONS_STANDARD; i++) {
            assertTrue(runes.applyRune("unit1", RuneSystem.MIGHT), "application #" + (i + 1) + " should succeed");
        }
        assertFalse(runes.applyRune("unit1", RuneSystem.MIGHT), "6th application should be rejected at the cap");
        assertEquals(RuneSystem.MAX_APPLICATIONS_STANDARD, runes.getApplicationCount("unit1", RuneSystem.MIGHT));
    }

    @Test
    void applyRune_swiftnessCap_isThreeNotFive() {
        setCrafted(RuneSystem.SWIFTNESS, RuneSystem.MAX_APPLICATIONS_SWIFTNESS + 1);
        for (int i = 0; i < RuneSystem.MAX_APPLICATIONS_SWIFTNESS; i++) {
            assertTrue(runes.applyRune("unit1", RuneSystem.SWIFTNESS));
        }
        assertFalse(runes.applyRune("unit1", RuneSystem.SWIFTNESS));
        assertEquals(3, runes.getMaxApplications(RuneSystem.SWIFTNESS));
        assertEquals(5, runes.getMaxApplications(RuneSystem.MIGHT)); // standard, for contrast
    }

    @Test
    void multipliers_scaleLinearlyWithApplications() {
        setCrafted(RuneSystem.MIGHT, 2);
        assertEquals(1.0f, runes.getDamageMultiplier("unit1")); // no applications yet
        runes.applyRune("unit1", RuneSystem.MIGHT);
        runes.applyRune("unit1", RuneSystem.MIGHT);
        assertEquals(1.06f, runes.getDamageMultiplier("unit1"), 0.0001f); // 1.0 + 2*0.03
        assertEquals(106, runes.getBoostedDamage("unit1", 100));
    }

    @Test
    void explorationSpeedMultiplier_usesSwiftnessBoostRate() {
        setCrafted(RuneSystem.SWIFTNESS, 1);
        runes.applyRune("unit1", RuneSystem.SWIFTNESS);
        assertEquals(1.05f, runes.getExplorationSpeedMultiplier("unit1"), 0.0001f); // +5%, not +3%
    }

    @Test
    void getRuneSummary_noRunes_isNull() {
        assertNull(runes.getRuneSummary("unit1"));
    }

    @Test
    void getRuneSummary_formatsAppliedRunesInRuneTypeOrder() {
        setCrafted(RuneSystem.VITALITY, 1);
        setCrafted(RuneSystem.MIGHT, 2);
        runes.applyRune("unit1", RuneSystem.VITALITY);
        runes.applyRune("unit1", RuneSystem.MIGHT);
        runes.applyRune("unit1", RuneSystem.MIGHT);
        // RUNE_TYPES order is {might, vitality, fortune, swiftness} regardless
        // of application order, so Might should be listed before Vitality.
        assertEquals("Might x2, Vitality x1", runes.getRuneSummary("unit1"));
    }

    @Test
    void onUnitPermanentlyLost_clearsAppliedRunes() {
        setCrafted(RuneSystem.MIGHT, 1);
        runes.applyRune("unit1", RuneSystem.MIGHT);
        assertTrue(runes.hasAnyRunes("unit1"));

        runes.onUnitPermanentlyLost("unit1");

        assertFalse(runes.hasAnyRunes("unit1"));
        assertEquals(0, runes.getApplicationCount("unit1", RuneSystem.MIGHT));
    }

    /** Test-only shortcut: injects crafted-rune count directly via the save/load setter, bypassing the fragment-accumulation dance for tests that aren't specifically about crafting. */
    private void setCrafted(String runeType, int count) {
        Map<String, Integer> crafted = new HashMap<>(runes.getCraftedRunes());
        crafted.put(runeType, count);
        runes.setCraftedRunes(crafted);
    }
}
