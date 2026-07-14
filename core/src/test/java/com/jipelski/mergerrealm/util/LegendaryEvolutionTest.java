package com.jipelski.mergerrealm.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegendaryEvolutionTest {

    @Test
    void getLegendaryType_knownBaseType() {
        assertEquals("eternal_phoenix", LegendaryEvolution.getLegendaryType("phoenix"));
    }

    @Test
    void getLegendaryType_unknownBaseType_returnsNull() {
        assertNull(LegendaryEvolution.getLegendaryType("not_a_real_unit"));
    }

    @Test
    void canEvolve_matchesEvolutionMapMembership() {
        assertTrue(LegendaryEvolution.canEvolve("villager"));
        assertFalse(LegendaryEvolution.canEvolve("eternal_phoenix")); // legendary, not a base type
        assertFalse(LegendaryEvolution.canEvolve("not_a_real_unit"));
    }

    @Test
    void isLegendary_trueOnlyForEvolvedTypes() {
        assertTrue(LegendaryEvolution.isLegendary("eternal_phoenix"));
        assertFalse(LegendaryEvolution.isLegendary("phoenix")); // base type, not legendary
        assertFalse(LegendaryEvolution.isLegendary("not_a_real_unit"));
    }

    @Test
    void getBaseType_isTheInverseOfGetLegendaryType() {
        assertEquals("phoenix", LegendaryEvolution.getBaseType("eternal_phoenix"));
        assertNull(LegendaryEvolution.getBaseType("phoenix")); // phoenix is a base type, not a legendary
        assertNull(LegendaryEvolution.getBaseType("not_a_real_unit"));
    }

    @Test
    void getTrait_returnsUniqueIdentifierPerLegendary() {
        assertEquals("auto_revive_raid", LegendaryEvolution.getTrait("eternal_phoenix"));
        assertEquals("dual_resource_food_wood", LegendaryEvolution.getTrait("elder_villager"));
        assertNull(LegendaryEvolution.getTrait("not_a_real_legendary"));
    }

    @Test
    void everyLegendaryType_hasATrait() {
        // Every value in EVOLUTION_MAP should have a corresponding TRAIT_MAP
        // entry — a missing trait would silently do nothing in combat/raid/
        // exploration code that checks for it by name.
        for (String legendaryType : LegendaryEvolution.getAllLegendaryTypes()) {
            assertNotNullTrait(legendaryType);
        }
    }

    private void assertNotNullTrait(String legendaryType) {
        String trait = LegendaryEvolution.getTrait(legendaryType);
        org.junit.jupiter.api.Assertions.assertNotNull(trait,
            legendaryType + " has no trait defined");
    }

    @Test
    void getAllLegendaryTypes_matchesDocumentedCount() {
        assertEquals(17, LegendaryEvolution.getAllLegendaryTypes().length);
    }

    @Test
    void legendaryTypeSet_matchesGameTypesExactly() {
        // Two independently-maintained lists of the same 17 legendary type
        // names (LegendaryEvolution's EVOLUTION_MAP values here, GameTypes.
        // LEGENDARY_UNITS there) — exactly the class of drift GameTypes
        // itself was built to eliminate (see its own class javadoc). If a
        // new legendary is ever added to one but not the other, this test
        // should fail.
        Set<String> fromEvolutionMap = new HashSet<>(Arrays.asList(LegendaryEvolution.getAllLegendaryTypes()));
        assertEquals(GameTypes.LEGENDARY_UNITS, fromEvolutionMap);
    }
}
