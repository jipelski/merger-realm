package com.jipelski.mergerrealm.util;

import com.jipelski.mergerrealm.database.JsonManager;
import com.jipelski.mergerrealm.model.ActiveStatusEffect;
import com.jipelski.mergerrealm.model.Combatant;
import com.jipelski.mergerrealm.testutil.GdxTestSupport;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises StatusEffectEngine against the REAL bundled status_effects.json
 * (via a real JsonManager/StatusEffectManager, headless-backed like
 * WeightedRollTest/RuneSystemTest — no full EventManager needed, since
 * StatusEffectEngine only depends on the catalog, not any other manager).
 * Using the real catalog rather than a hand-built fake one means these
 * tests also catch a broken status_effects.json entry, not just engine bugs.
 */
class StatusEffectEngineTest {

    @BeforeAll
    static void setUpGdx() {
        GdxTestSupport.ensureInitialized();
    }

    private StatusEffectEngine engine;

    @BeforeEach
    void setUp() {
        StatusEffectManager manager = new StatusEffectManager(new JsonManager());
        engine = new StatusEffectEngine(manager);
    }

    private Combatant combatant(int hp, int maxHp) {
        Combatant c = new Combatant();
        c.id = "test";
        c.type = "test_type";
        c.hp = hp;
        c.maxHp = maxHp;
        c.baseDamage = 10;
        c.baseAttackSpeed = 2.8f;
        return c;
    }

    // ── Apply / stacking / immunity ──

    @Test
    void applyEffect_dot_addsSingleInstance() {
        Combatant target = combatant(1000, 1000);
        engine.applyEffect(target, "poison", "src", false);
        assertEquals(1, target.effects.size());
        assertEquals("poison", target.effects.get(0).effectId);
        assertEquals(1, target.effects.get(0).stacks);
    }

    @Test
    void applyEffect_reapplyingBelowMaxStacks_incrementsStacksAndRefreshesDuration() {
        Combatant target = combatant(1000, 1000);
        engine.applyEffect(target, "poison", "src", false); // max_stacks=3
        engine.tick(target, 5.9f, null); // let duration (6s) nearly expire
        engine.applyEffect(target, "poison", "src", false); // refresh + stack 2
        assertEquals(1, target.effects.size()); // still one instance, not a duplicate
        ActiveStatusEffect inst = target.effects.get(0);
        assertEquals(2, inst.stacks);
        assertTrue(inst.remaining > 5f); // refreshed back to ~6, not left at ~0.1
    }

    @Test
    void applyEffect_reapplyingAtMaxStacks_stopsIncrementing() {
        Combatant target = combatant(1000, 1000);
        for (int i = 0; i < 6; i++) engine.applyEffect(target, "poison", "src", false);
        assertEquals(1, target.effects.size());
        assertEquals(3, target.effects.get(0).stacks); // poison's max_stacks = 3
    }

    @Test
    void applyEffect_negativeEffectOnImmuneTarget_isBlocked() {
        Combatant target = combatant(1000, 1000);
        engine.applyEffect(target, "status_immunity", "src", false);
        assertTrue(engine.isImmune(target));

        engine.applyEffect(target, "poison", "attacker", false); // beneficial=false
        assertTrue(target.effects.stream().noneMatch(e -> "poison".equals(e.effectId)));
    }

    @Test
    void applyEffect_beneficialEffectOnImmuneTarget_isNotBlocked() {
        Combatant target = combatant(1000, 1000);
        engine.applyEffect(target, "status_immunity", "src", false);

        engine.applyEffect(target, "regen", "healer", false); // beneficial=true
        assertTrue(target.effects.stream().anyMatch(e -> "regen".equals(e.effectId)));
    }

    @Test
    void applyEffect_auraInstances_areNotDeduped() {
        // recomputeAuras() clears every aura=true instance each frame before
        // reapplying, so within a single frame's sweep aura application never
        // needs the stacking/refresh dedup non-aura effects use — pinning
        // that down here so it doesn't silently regress.
        Combatant target = combatant(1000, 1000);
        engine.applyEffect(target, "fortify", "holder_a", true, null, null);
        engine.applyEffect(target, "fortify", "holder_b", true, null, null);
        assertEquals(2, target.effects.size());
    }

    // ── DOT / HOT ticking ──

    @Test
    void tick_dot_dealsDamageEveryInterval() {
        Combatant target = combatant(1000, 1000);
        engine.applyEffect(target, "poison", "src", false); // 0.03 * 1000 = 30/tick, every 1s
        engine.tick(target, 1f, null);
        assertEquals(970, target.hp);
        engine.tick(target, 1f, null);
        assertEquals(940, target.hp);
    }

    @Test
    void tick_dot_neverDropsBelowZero() {
        Combatant target = combatant(10, 1000); // 30/tick would go negative
        engine.applyEffect(target, "poison", "src", false);
        engine.tick(target, 1f, null);
        assertEquals(0, target.hp);
    }

    @Test
    void tick_hot_healsButCapsAtMaxHp() {
        Combatant target = combatant(990, 1000);
        engine.applyEffect(target, "regen", "src", false); // 0.03 * 1000 = 30/tick, every 2s
        engine.tick(target, 2f, null);
        assertEquals(1000, target.hp); // capped, not 1020
    }

    @Test
    void tick_expiredEffect_isRemoved() {
        Combatant target = combatant(1000, 1000);
        engine.applyEffect(target, "stun", "src", false); // duration 1.5s
        engine.tick(target, 2f, null);
        assertTrue(target.effects.isEmpty());
    }

    @Test
    void tick_permanentEffect_neverExpires() {
        Combatant target = combatant(1000, 1000);
        engine.applyEffect(target, "ironclad_reflect", "self", false); // duration -1
        engine.tick(target, 999f, null);
        assertEquals(1, target.effects.size());
    }

    // ── Read helpers ──

    @Test
    void isStunned_reflectsActiveStunEffect() {
        Combatant target = combatant(1000, 1000);
        assertFalse(engine.isStunned(target));
        engine.applyEffect(target, "stun", "src", false);
        assertTrue(engine.isStunned(target));
    }

    @Test
    void getDamageDealtMultiplier_multipliesAcrossFactors() {
        Combatant attacker = combatant(1000, 1000);
        engine.applyEffect(attacker, "empower", "self", false, 0.20f, null); // ×1.20
        engine.applyEffect(attacker, "shadowbow_power", "self", false); // multiplier ×2.0
        assertEquals(2.4f, engine.getDamageDealtMultiplier(attacker), 0.001f);
    }

    @Test
    void getAttackIntervalMultiplier_haste_speedsUpAttacks() {
        Combatant c = combatant(1000, 1000);
        engine.applyEffect(c, "haste", "src", false); // -0.3 -> ×0.7
        assertEquals(0.7f, engine.getAttackIntervalMultiplier(c), 0.001f);
    }

    @Test
    void getReflectPercent_sumsMultipleSources() {
        Combatant c = combatant(1000, 1000);
        engine.applyEffect(c, "ironclad_reflect", "self", false);       // 0.10
        engine.applyEffect(c, "infernal_reflect", "set_infernal", true); // 0.25 (aura)
        assertEquals(0.35f, engine.getReflectPercent(c), 0.001f);
    }

    @Test
    void absorbWithShield_soaksThenOverflows() {
        Combatant c = combatant(1000, 1000);
        engine.applyEffect(c, "shield", "src", false); // 0.15 * 1000 = 150 shield
        assertEquals(150, c.shield);

        int remainder1 = engine.absorbWithShield(c, 100);
        assertEquals(0, remainder1);
        assertEquals(50, c.shield);

        int remainder2 = engine.absorbWithShield(c, 100);
        assertEquals(50, remainder2); // 50 absorbed, 50 overflows through
        assertEquals(0, c.shield);
    }

    @Test
    void resolveInstant_execute_killsAtOrBelowThreshold() {
        Combatant atThreshold = combatant(150, 1000); // exactly 15%
        engine.applyEffect(atThreshold, "execute", "src", false);
        assertEquals(0, atThreshold.hp);

        Combatant aboveThreshold = combatant(151, 1000); // 15.1%
        engine.applyEffect(aboveThreshold, "execute", "src", false);
        assertEquals(151, aboveThreshold.hp); // unchanged
    }

    @Test
    void resolveInstant_cleanse_stripsOnlyNegativeEffects() {
        Combatant c = combatant(1000, 1000);
        engine.applyEffect(c, "poison", "src", false); // beneficial=false
        engine.applyEffect(c, "regen", "src", false);  // beneficial=true
        engine.applyEffect(c, "cleanse", "src", false);

        assertTrue(c.effects.stream().noneMatch(e -> "poison".equals(e.effectId)));
        assertTrue(c.effects.stream().anyMatch(e -> "regen".equals(e.effectId)));
    }

    @Test
    void tryConsumeRevive_revivesOnceThenIsGone() {
        Combatant c = combatant(500, 1000);
        engine.applyEffect(c, "phoenix_revive", "self", false); // 0.5 * maxHp
        c.hp = 0;

        assertTrue(engine.tryConsumeRevive(c));
        assertEquals(500, c.hp);
        assertFalse(c.dead);

        c.hp = 0; // died again, no revive charge left
        assertFalse(engine.tryConsumeRevive(c));
    }

    @Test
    void clearAuras_removesOnlyAuraTaggedInstances() {
        Combatant c = combatant(1000, 1000);
        engine.applyEffect(c, "fortify", "holder", true);       // aura
        engine.applyEffect(c, "ironclad_reflect", "self", false); // not aura
        engine.clearAuras(c);

        assertEquals(1, c.effects.size());
        assertEquals("ironclad_reflect", c.effects.get(0).effectId);
    }

    @Test
    void applyEffect_unknownEffectId_isNoOp() {
        Combatant c = combatant(1000, 1000);
        engine.applyEffect(c, "not_a_real_effect", "src", false);
        assertTrue(c.effects.isEmpty());
    }

    @Test
    void getEffect_unknownId_returnsNull() {
        assertNull(new StatusEffectManager(new JsonManager()).getEffect("bogus"));
    }
}
