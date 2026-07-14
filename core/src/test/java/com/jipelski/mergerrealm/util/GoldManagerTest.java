package com.jipelski.mergerrealm.util;

import com.jipelski.mergerrealm.testutil.EventManagerTestSupport;
import com.jipelski.mergerrealm.testutil.GdxTestSupport;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The outer class covers GoldManager's methods that never dereference
 * `eventManager` (confirmed by reading the class in full: addGold/
 * spendGold/canAfford, the one-time-reward earn triggers, salvage math, and
 * the extraExploreSlots/claimedRewards accessors) — GoldManager is
 * constructed with a null EventManager, safe HERE specifically because the
 * constructor only stores the reference without dereferencing it, and none
 * of these methods ever touch `this.eventManager`.
 *
 * The nested WithRealEventManager class covers the reach-through methods
 * (fillResources/speedUpExploration/instantPeriodicSpawn/reviveRaidParty/
 * getTotalExploreSlots), which DO need a real EventManager
 * (EventManagerTestSupport.freshEventManager()) — kept in a separate
 * @Nested class specifically so the outer class's cheap null-backed tests
 * don't all pay for constructing a full manager graph before every test.
 */
class GoldManagerTest {

    @BeforeAll
    static void setUpGdx() {
        GdxTestSupport.ensureInitialized();
    }

    private GoldManager gold;

    @BeforeEach
    void setUp() {
        gold = new GoldManager(null);
    }

    @Test
    void addGold_increasesBalance() {
        gold.addGold(10, "test");
        assertEquals(10, gold.getGold());
        gold.addGold(5, "test");
        assertEquals(15, gold.getGold());
    }

    @Test
    void addGold_nonPositiveAmount_isNoOp() {
        gold.addGold(0, "test");
        gold.addGold(-5, "test");
        assertEquals(0, gold.getGold());
    }

    @Test
    void spendGold_sufficientBalance_succeedsAndDeducts() {
        gold.addGold(20, "seed");
        assertTrue(gold.spendGold(15, "purchase"));
        assertEquals(5, gold.getGold());
    }

    @Test
    void spendGold_insufficientBalance_failsAndLeavesBalanceUnchanged() {
        gold.addGold(5, "seed");
        assertFalse(gold.spendGold(10, "purchase"));
        assertEquals(5, gold.getGold());
    }

    @Test
    void spendGold_nonPositiveAmount_fails() {
        gold.addGold(10, "seed");
        assertFalse(gold.spendGold(0, "purchase"));
        assertFalse(gold.spendGold(-5, "purchase"));
        assertEquals(10, gold.getGold());
    }

    @Test
    void canAfford_matchesBalanceExactly() {
        gold.addGold(10, "seed");
        assertTrue(gold.canAfford(10));
        assertTrue(gold.canAfford(5));
        assertFalse(gold.canAfford(11));
    }

    @Test
    void onRaidNodeFirstClear_amountVariesByNodeType() {
        gold.onRaidNodeFirstClear("ch1", "main1", "main");
        assertEquals(GoldManager.EARN_RAID_MAIN_NODE, gold.getGold());

        gold.onRaidNodeFirstClear("ch1", "side1", "side");
        assertEquals(GoldManager.EARN_RAID_MAIN_NODE + GoldManager.EARN_RAID_SIDE_NODE, gold.getGold());

        gold.onRaidNodeFirstClear("ch1", "boss1", "boss");
        assertEquals(GoldManager.EARN_RAID_MAIN_NODE + GoldManager.EARN_RAID_SIDE_NODE
            + GoldManager.EARN_RAID_BOSS_NODE, gold.getGold());

        gold.onRaidNodeFirstClear("ch1", "challenge1", "challenge");
        assertEquals(GoldManager.EARN_RAID_MAIN_NODE + GoldManager.EARN_RAID_SIDE_NODE
            + GoldManager.EARN_RAID_BOSS_NODE + GoldManager.EARN_RAID_CHALLENGE_NODE, gold.getGold());
    }

    @Test
    void onRaidNodeFirstClear_unknownNodeType_fallsBackToMainAmount() {
        gold.onRaidNodeFirstClear("ch1", "node1", "some_future_type");
        assertEquals(GoldManager.EARN_RAID_MAIN_NODE, gold.getGold());
    }

    @Test
    void onRaidNodeFirstClear_sameNodeTwice_onlyAwardsOnce() {
        gold.onRaidNodeFirstClear("ch1", "boss1", "boss");
        gold.onRaidNodeFirstClear("ch1", "boss1", "boss");
        assertEquals(GoldManager.EARN_RAID_BOSS_NODE, gold.getGold());
        assertTrue(gold.getClaimedRewards().contains("raid:ch1:boss1"));
    }

    @Test
    void onRaidFirst3Star_onlyAwardsOncePerNode() {
        gold.onRaidFirst3Star("ch1", "node1");
        gold.onRaidFirst3Star("ch1", "node1");
        assertEquals(GoldManager.EARN_FIRST_3STAR, gold.getGold());
    }

    @Test
    void onPrinceLevelUp_onlyMultiplesOfFiveTrigger() {
        gold.onPrinceLevelUp(4);
        assertEquals(0, gold.getGold());
        gold.onPrinceLevelUp(5);
        assertEquals(GoldManager.EARN_PRINCE_MILESTONE, gold.getGold());
    }

    @Test
    void onPrinceLevelUp_sameMilestoneTwice_onlyAwardsOnce() {
        gold.onPrinceLevelUp(10);
        gold.onPrinceLevelUp(10);
        assertEquals(GoldManager.EARN_PRINCE_MILESTONE, gold.getGold());
    }

    @Test
    void onExplorationGoldFind_awardsWithinDocumentedRange() {
        gold.onExplorationGoldFind();
        assertTrue(gold.getGold() >= GoldManager.EARN_EXPLORE_FIND_MIN
            && gold.getGold() <= GoldManager.EARN_EXPLORE_FIND_MAX);
    }

    @Test
    void onDismissLegendary_awardsFlatAmount() {
        gold.onDismissLegendary();
        assertEquals(GoldManager.EARN_DISMISS_LEGENDARY, gold.getGold());
    }

    @Test
    void getSalvageValue_matchesTableForValidLevels() {
        assertEquals(1, gold.getSalvageValue(1));
        assertEquals(2, gold.getSalvageValue(2));
        assertEquals(3, gold.getSalvageValue(3));
        assertEquals(5, gold.getSalvageValue(4));
        assertEquals(8, gold.getSalvageValue(5));
    }

    @Test
    void getSalvageValue_outOfRangeLevels_clampToZero() {
        assertEquals(0, gold.getSalvageValue(0));
        assertEquals(0, gold.getSalvageValue(-1));
        assertEquals(0, gold.getSalvageValue(6));
        assertEquals(0, gold.getSalvageValue(100));
    }

    @Test
    void onSalvageEquipment_delegatesToAddGold() {
        gold.onSalvageEquipment(42);
        assertEquals(42, gold.getGold());
    }

    @Test
    void extraExploreSlots_getSet() {
        assertEquals(0, gold.getExtraExploreSlots());
        gold.setExtraExploreSlots(3);
        assertEquals(3, gold.getExtraExploreSlots());
    }

    @Test
    void claimedRewards_getSet() {
        assertTrue(gold.getClaimedRewards().isEmpty());
        java.util.Set<String> restored = new java.util.HashSet<>();
        restored.add("raid:ch1:boss1");
        gold.setClaimedRewards(restored);
        assertTrue(gold.getClaimedRewards().contains("raid:ch1:boss1"));
    }

    /**
     * Scoped to the guard-clause/rejection paths that don't need a live raid
     * or an in-progress exploration slot already set up (a running raid, an
     * exploration slot in the returning state) — those "happy path" scenarios
     * need considerably more fixture state and are explicitly out of scope
     * for this pass, same as the outer class's own doc comment states for
     * these methods as a whole.
     */
    @Nested
    class WithRealEventManager {

        private EventManager eventManager;
        private GoldManager realGold;

        @BeforeEach
        void setUpRealEventManager() {
            eventManager = EventManagerTestSupport.freshEventManager();
            realGold = eventManager.getGoldManager();
        }

        @Test
        void fillResources_success_fillsAllThreeToMax() {
            realGold.addGold(GoldManager.COST_FILL_RESOURCES, "seed");
            assertTrue(realGold.fillResources());

            ResourceManager rm = eventManager.getResourceManager();
            assertEquals(rm.getPoolSize("food"), rm.getAmount("food"));
            assertEquals(rm.getPoolSize("wood"), rm.getAmount("wood"));
            assertEquals(rm.getPoolSize("iron"), rm.getAmount("iron"));
        }

        @Test
        void fillResources_insufficientGold_fails() {
            assertFalse(realGold.fillResources()); // fresh manager starts at 0 Gold
        }

        @Test
        void getTotalExploreSlots_freshManager_isBaseSlotPlusPurchased() {
            // Fresh EventManager starts at Prince level 1 — only the
            // level-1 threshold in ExplorationManager's SLOT_UNLOCK_LEVELS
            // {1,5,10,...} qualifies, so 1 base slot.
            assertEquals(1, realGold.getTotalExploreSlots());
            realGold.setExtraExploreSlots(2);
            assertEquals(3, realGold.getTotalExploreSlots());
        }

        @Test
        void speedUpExploration_noActiveSlots_fails() {
            assertFalse(realGold.speedUpExploration(0));
        }

        @Test
        void instantPeriodicSpawn_insufficientGold_failsWithoutDeducting() {
            assertFalse(realGold.instantPeriodicSpawn("bogus_id"));
            assertEquals(0, realGold.getGold());
        }

        @Test
        void instantPeriodicSpawn_nonexistentFacility_failsButStillConsumesGold() {
            // Documents current behavior as-is, not necessarily ideal: the
            // obj==null rejection path has no refund (unlike the "not a
            // periodic facility" branch just below it in the source, which
            // does refund), so Gold is spent even though nothing happened.
            // Flagging via this test rather than silently "fixing" it —
            // that's outside this pass's scope (writing tests, not patching
            // behavior found along the way).
            realGold.addGold(GoldManager.COST_INSTANT_SPAWN, "seed");
            assertFalse(realGold.instantPeriodicSpawn("does_not_exist"));
            assertEquals(0, realGold.getGold());
        }

        @Test
        void reviveRaidParty_noActiveRaid_failsAndRefunds() {
            realGold.addGold(GoldManager.COST_RAID_REVIVE_ALL, "seed");
            assertFalse(realGold.reviveRaidParty());
            assertEquals(GoldManager.COST_RAID_REVIVE_ALL, realGold.getGold()); // refunded
        }

        @Test
        void reviveRaidParty_insufficientGold_fails() {
            assertFalse(realGold.reviveRaidParty());
            assertEquals(0, realGold.getGold());
        }
    }
}
