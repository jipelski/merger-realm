package com.jipelski.mergerrealm.model;

/**
 * A live instance of a status effect on a {@link Combatant}. Lean by design —
 * the actual definition (kind/target/magnitude/duration/etc.) lives in
 * StatusEffectManager's catalog and is re-resolved by {@link #effectId} at
 * use time, the same "recompute from static content on demand" pattern
 * RaidManager already uses for setBonuses on resume — a definition edited or
 * removed from status_effects.json later just null-guards to "drop the
 * effect" instead of needing a save migration.
 */
public class ActiveStatusEffect {

    public String effectId;

    // Seconds left. -1 = permanent for the raid (self/aura-granted buffs that
    // don't expire on a timer — e.g. shadowbow's double-damage, or a party
    // aura that gets torn down and rebuilt every frame by
    // RaidManager.recomputeAuras() instead). Never decremented while -1.
    public float remaining;

    public float tickTimer;   // dot/hot cadence accumulator
    public int stacks = 1;
    public String sourceId;   // combatant id that applied this (for aura teardown / attribution)

    // True for aura-origin instances (legendary auraEffect, enchanted-set
    // bonuses folded into the pipeline) — RaidManager.recomputeAuras() strips
    // and rebuilds ONLY these every frame, gated on the source holder still
    // being alive. Self-granted and onHit-granted effects are never touched
    // by that sweep.
    public boolean aura;

    // Enchanted-set-driven auras (Dragonscale/Shadowsteel/Holy Radiance/
    // Infernal) source their magnitude from EnchantedSetManager.
    // ActiveSetBonuses (already data-driven from enchanted_sets.json) rather
    // than duplicating those numbers into status_effects.json — when set,
    // this overrides the catalog def's own magnitude for this instance only.
    public Float magnitudeOverride;

    // Holy Radiance's heal-tick cadence is also set-driven
    // (healIntervalSeconds), not a status_effects.json constant — same
    // override pattern as magnitudeOverride, only needed by hot/dot kinds.
    public Float tickIntervalOverride;

    public ActiveStatusEffect() {}

    public ActiveStatusEffect(String effectId, float duration) {
        this.effectId = effectId;
        this.remaining = duration;
    }

    public boolean isPermanent() {
        return remaining < 0f;
    }
}
