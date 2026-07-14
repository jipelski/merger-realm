package com.jipelski.mergerrealm.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GameTypes is the single source of truth for type-membership across the
 * codebase (see its own class javadoc — it replaced duplicated switch/case
 * lists that had drifted, most notably a bug where legendaries were missing
 * from a unit check). These tests exist specifically to catch that class of
 * drift: if a type is added to a set but a sibling set/predicate is
 * forgotten, or a set's membership count silently changes, a test here
 * should fail.
 */
class GameTypesTest {

    @Test
    void baseUnits_areUnitsButNotLegendary() {
        for (String type : GameTypes.BASE_UNITS) {
            assertTrue(GameTypes.isUnit(type), type + " should be a unit");
            assertTrue(GameTypes.isBaseUnit(type), type + " should be a base unit");
            assertFalse(GameTypes.isLegendary(type), type + " should not be legendary");
        }
    }

    @Test
    void legendaryUnits_areUnitsButNotBase() {
        for (String type : GameTypes.LEGENDARY_UNITS) {
            assertTrue(GameTypes.isUnit(type), type + " should be a unit");
            assertTrue(GameTypes.isLegendary(type), type + " should be legendary");
            assertFalse(GameTypes.isBaseUnit(type), type + " should not be a base unit");
        }
    }

    @Test
    void unitsIsExactlyTheUnionOfBaseAndLegendary() {
        assertEquals(GameTypes.BASE_UNITS.size() + GameTypes.LEGENDARY_UNITS.size(),
            GameTypes.UNITS.size(),
            "UNITS should be the disjoint union of BASE_UNITS and LEGENDARY_UNITS");
    }

    @Test
    void periodicFacilities_areASubsetOfFacilities() {
        for (String type : GameTypes.PERIODIC_FACILITIES) {
            assertTrue(GameTypes.isFacility(type), type + " should be a facility");
            assertTrue(GameTypes.isPeriodicFacility(type));
        }
        // Not every facility is periodic — tap-based ones (e.g. homestead) exist too.
        assertTrue(GameTypes.FACILITIES.size() > GameTypes.PERIODIC_FACILITIES.size());
    }

    @Test
    void nonUnitTypesAreNotClassifiedAsUnits() {
        for (String type : GameTypes.FACILITIES) assertFalse(GameTypes.isUnit(type));
        for (String type : GameTypes.STORAGE) assertFalse(GameTypes.isUnit(type));
        for (String type : GameTypes.MONSTERS) assertFalse(GameTypes.isUnit(type));
        for (String type : GameTypes.CHESTS) assertFalse(GameTypes.isUnit(type));
    }

    @Test
    void predicatesAreNullSafe() {
        assertFalse(GameTypes.isUnit(null));
        assertFalse(GameTypes.isBaseUnit(null));
        assertFalse(GameTypes.isLegendary(null));
        assertFalse(GameTypes.isFacility(null));
        assertFalse(GameTypes.isPeriodicFacility(null));
        assertFalse(GameTypes.isStorage(null));
        assertFalse(GameTypes.isMonster(null));
        assertFalse(GameTypes.isChest(null));
        assertFalse(GameTypes.isToken(null));
        assertFalse(GameTypes.isResourcePouch(null));
    }

    @Test
    void unknownTypeMatchesNoPredicate() {
        String bogus = "definitely_not_a_real_type";
        assertFalse(GameTypes.isUnit(bogus));
        assertFalse(GameTypes.isFacility(bogus));
        assertFalse(GameTypes.isStorage(bogus));
        assertFalse(GameTypes.isMonster(bogus));
        assertFalse(GameTypes.isChest(bogus));
        assertFalse(GameTypes.isToken(bogus));
        assertFalse(GameTypes.isResourcePouch(bogus));
    }

    // Documented set sizes — a change here should be a deliberate content
    // edit, not a silent accident (e.g. a copy-paste that dropped an entry).
    @Test
    void documentedSetSizes() {
        assertEquals(17, GameTypes.BASE_UNITS.size());
        assertEquals(17, GameTypes.LEGENDARY_UNITS.size());
        assertEquals(34, GameTypes.UNITS.size());
        assertEquals(9, GameTypes.FACILITIES.size());
        assertEquals(3, GameTypes.PERIODIC_FACILITIES.size());
        assertEquals(3, GameTypes.STORAGE.size());
        assertEquals(5, GameTypes.MONSTERS.size());
        assertEquals(4, GameTypes.CHESTS.size());
        assertEquals(4, GameTypes.TOKENS.size());
        assertEquals(3, GameTypes.RESOURCE_POUCHES.size());
    }

    @Test
    void specificKnownTypesClassifyCorrectly() {
        // Spot-check real type names rather than only iterating the sets
        // themselves, so a bug in the set definition itself would still
        // be caught (iterating BASE_UNITS to test isBaseUnit is circular
        // for that specific failure mode).
        assertTrue(GameTypes.isBaseUnit("villager"));
        assertTrue(GameTypes.isLegendary("eternal_phoenix"));
        assertTrue(GameTypes.isFacility("dragonslair"));
        assertTrue(GameTypes.isPeriodicFacility("griffinnest"));
        assertFalse(GameTypes.isPeriodicFacility("homestead"));
        assertTrue(GameTypes.isStorage("silo"));
        assertTrue(GameTypes.isMonster("gremlin"));
        assertTrue(GameTypes.isChest("relic_chest"));
        assertTrue(GameTypes.isToken("nail_token"));
        assertTrue(GameTypes.isResourcePouch("food_pouch"));
    }
}
