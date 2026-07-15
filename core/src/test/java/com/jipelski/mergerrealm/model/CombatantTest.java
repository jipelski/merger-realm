package com.jipelski.mergerrealm.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Combatant is the Raid V3 unified party/enemy shape (replaces the old 11
 * parallel RaidState party arrays + the separate RaidEnemy class) — zero
 * Gdx coupling, so no bootstrap needed here, same bar as WeightedRoll/
 * TextUtil/PrinceLevelConfig/GameTypes.
 */
class CombatantTest {

    @Test
    void isAlive_falseByDefault_zeroHp() {
        Combatant c = new Combatant();
        assertFalse(c.isAlive()); // hp defaults to 0
    }

    @Test
    void isAlive_trueWhenHpPositiveAndNotDead() {
        Combatant c = new Combatant();
        c.hp = 100;
        assertTrue(c.isAlive());
    }

    @Test
    void isAlive_falseWhenDeadFlagSetEvenWithPositiveHp() {
        // RaidManager sets dead=true and hp=0 together on a real kill, but
        // isAlive() must not trust hp alone — a stray path that sets one
        // without the other (e.g. a future revive bug) shouldn't silently
        // treat the combatant as alive.
        Combatant c = new Combatant();
        c.hp = 50;
        c.dead = true;
        assertFalse(c.isAlive());
    }

    @Test
    void isAlive_falseWhenHpZeroEvenIfNotMarkedDead() {
        Combatant c = new Combatant();
        c.hp = 0;
        c.dead = false;
        assertFalse(c.isAlive());
    }

    @Test
    void effectsList_startsEmptyAndMutable() {
        Combatant c = new Combatant();
        assertNotNull(c.effects);
        assertTrue(c.effects.isEmpty());

        c.effects.add(new ActiveStatusEffect("poison", 5f));
        assertTrue(c.effects.size() == 1);
    }

    @Test
    void newCombatant_defaultsShieldToZeroAndOnHitEffectToNull() {
        Combatant c = new Combatant();
        assertTrue(c.shield == 0);
        assertTrue(c.onHitEffect == null); // "no effect" — RaidManager treats null same as "none"
    }
}
