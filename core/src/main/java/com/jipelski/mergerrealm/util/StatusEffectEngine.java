package com.jipelski.mergerrealm.util;

import com.jipelski.mergerrealm.model.ActiveStatusEffect;
import com.jipelski.mergerrealm.model.Combatant;
import com.jipelski.mergerrealm.util.StatusEffectManager.StatusEffectData;

import java.util.Iterator;

/**
 * Pure resolution logic for Raid V3 status effects — applies, ticks, and
 * reads the multiplier/gate helpers RaidManager's combat loop needs.
 * Deliberately zero Gdx coupling (same bar as WeightedRoll/TextUtil/
 * PrinceLevelConfig/GameTypes) so it's directly headless-unit-testable with
 * a plain `new StatusEffectEngine(manager)` and hand-built Combatants —
 * no EventManager, no HeadlessApplication required.
 *
 * RaidManager owns all the "what triggers what" wiring (onHit/self/aura
 * sourcing, death handling, event emission); this class only knows how to
 * apply/tick/read effects already decided on, given a target and an effect
 * id resolved from StatusEffectManager's catalog.
 */
public class StatusEffectEngine {

    private final StatusEffectManager manager;

    public StatusEffectEngine(StatusEffectManager manager) {
        this.manager = manager;
    }

    /** Callback so RaidManager can push CombatEvents/log lines without this class depending on RaidState. */
    public interface EffectEventSink {
        void onDamage(Combatant target, int amount, String effectId);
        void onHeal(Combatant target, int amount, String effectId);
    }

    // ══════════════════════════════════════════════════════════════
    // APPLY
    // ══════════════════════════════════════════════════════════════

    public void applyEffect(Combatant target, String effectId, String sourceId, boolean aura) {
        applyEffect(target, effectId, sourceId, aura, null, null);
    }

    /**
     * @param aura               true for RaidManager.recomputeAuras()-sourced instances,
     *                           which are always applied fresh (that sweep clears every
     *                           aura-flagged instance before reapplying, so no dedup/stack
     *                           check is needed here for aura instances).
     * @param magnitudeOverride  non-null for enchanted-set-driven effects whose numbers
     *                           come from EnchantedSetManager.ActiveSetBonuses rather than
     *                           this catalog entry's own fixed magnitude.
     * @param tickIntervalOverride same idea, for Holy Radiance's set-driven heal cadence.
     */
    public void applyEffect(Combatant target, String effectId, String sourceId, boolean aura,
                            Float magnitudeOverride, Float tickIntervalOverride) {
        if (target == null || !target.isAlive()) return;
        StatusEffectData def = manager.getEffect(effectId);
        if (def == null) return;

        // Immunity blocks new negative effects only — never blocks buffs.
        if (!def.beneficial && isImmune(target)) return;

        if (def.isInstant()) {
            resolveInstant(target, def, magnitudeOverride);
            return;
        }

        if (!aura) {
            ActiveStatusEffect existing = findInstance(target, effectId);
            if (existing != null) {
                if (def.refreshOnReapply) {
                    existing.remaining = def.isPermanent() ? -1f : def.duration;
                    existing.tickTimer = 0f;
                }
                if (existing.stacks < Math.max(1, def.maxStacks)) existing.stacks++;
                existing.sourceId = sourceId;
                existing.magnitudeOverride = magnitudeOverride;
                existing.tickIntervalOverride = tickIntervalOverride;
                return;
            }
        }

        ActiveStatusEffect inst = new ActiveStatusEffect();
        inst.effectId = effectId;
        inst.remaining = def.isPermanent() ? -1f : def.duration;
        inst.tickTimer = 0f;
        inst.stacks = 1;
        inst.sourceId = sourceId;
        inst.aura = aura;
        inst.magnitudeOverride = magnitudeOverride;
        inst.tickIntervalOverride = tickIntervalOverride;
        target.effects.add(inst);
    }

    private ActiveStatusEffect findInstance(Combatant target, String effectId) {
        for (ActiveStatusEffect e : target.effects) {
            if (!e.aura && effectId.equals(e.effectId)) return e;
        }
        return null;
    }

    private void resolveInstant(Combatant target, StatusEffectData def, Float magnitudeOverride) {
        float mag = magnitudeOverride != null ? magnitudeOverride : def.magnitude;
        switch (def.kind) {
            case StatusEffectManager.KIND_SHIELD:
                target.shield += resolveFlatAmount(target, mag, def.magnitudeType);
                break;
            case StatusEffectManager.KIND_CLEANSE:
                target.effects.removeIf(e -> {
                    StatusEffectData d = manager.getEffect(e.effectId);
                    return d != null && !d.beneficial;
                });
                break;
            case StatusEffectManager.KIND_EXECUTE:
                if (target.maxHp > 0 && (float) target.hp / target.maxHp <= mag) {
                    target.hp = 0;
                }
                break;
            default:
                break;
        }
    }

    private int resolveFlatAmount(Combatant target, float magnitude, String magnitudeType) {
        if (StatusEffectManager.MAG_PERCENT_MAX_HP.equals(magnitudeType)) {
            return Math.round(target.maxHp * magnitude);
        }
        return Math.round(magnitude);
    }

    /** Strips every aura-tagged instance — RaidManager.recomputeAuras() calls this before reapplying. */
    public void clearAuras(Combatant c) {
        if (c != null) c.effects.removeIf(e -> e.aura);
    }

    // ══════════════════════════════════════════════════════════════
    // TICK — dot/hot damage-over-time, expiry
    // ══════════════════════════════════════════════════════════════

    public void tick(Combatant c, float delta, EffectEventSink sink) {
        if (c == null || c.effects.isEmpty()) return;

        Iterator<ActiveStatusEffect> it = c.effects.iterator();
        while (it.hasNext()) {
            ActiveStatusEffect inst = it.next();
            StatusEffectData def = manager.getEffect(inst.effectId);
            if (def == null) { it.remove(); continue; }

            if (StatusEffectManager.KIND_DOT.equals(def.kind)
                || StatusEffectManager.KIND_HOT.equals(def.kind)) {
                inst.tickTimer += delta;
                float interval = inst.tickIntervalOverride != null
                    ? inst.tickIntervalOverride : def.tickInterval;
                if (interval <= 0f) interval = 1f;

                while (inst.tickTimer >= interval && c.hp > 0) {
                    inst.tickTimer -= interval;
                    float mag = inst.magnitudeOverride != null ? inst.magnitudeOverride : def.magnitude;
                    int perStack = resolveFlatAmount(c, mag, def.magnitudeType);
                    int amount = perStack * Math.max(1, inst.stacks);

                    if (StatusEffectManager.KIND_DOT.equals(def.kind)) {
                        amount = Math.min(amount, c.hp);
                        c.hp = Math.max(0, c.hp - amount);
                        if (sink != null && amount > 0) sink.onDamage(c, amount, def.id);
                    } else {
                        int healed = Math.min(amount, c.maxHp - c.hp);
                        c.hp = Math.min(c.maxHp, c.hp + amount);
                        if (sink != null && healed > 0) sink.onHeal(c, healed, def.id);
                    }
                }
            }

            if (!inst.isPermanent()) {
                inst.remaining -= delta;
                if (inst.remaining <= 0f) it.remove();
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // READ HELPERS — combat-loop multiplier chain
    // ══════════════════════════════════════════════════════════════

    public boolean isStunned(Combatant c) {
        return hasKind(c, StatusEffectManager.KIND_STUN);
    }

    public boolean isImmune(Combatant c) {
        return hasKind(c, StatusEffectManager.KIND_IMMUNITY);
    }

    private boolean hasKind(Combatant c, String kind) {
        if (c == null) return false;
        for (ActiveStatusEffect inst : c.effects) {
            StatusEffectData def = manager.getEffect(inst.effectId);
            if (def != null && kind.equals(def.kind)) return true;
        }
        return false;
    }

    /** Product of every active attack_speed modifier's factor — see resolveFactor(). */
    public float getAttackIntervalMultiplier(Combatant c) {
        return productOfFactors(c, StatusEffectManager.TARGET_ATTACK_SPEED);
    }

    /** Product of every active damage_dealt modifier's factor (attacker side). */
    public float getDamageDealtMultiplier(Combatant c) {
        return productOfFactors(c, StatusEffectManager.TARGET_DAMAGE_DEALT);
    }

    /** Product of every active damage_taken modifier's factor (defender side). */
    public float getDamageTakenMultiplier(Combatant c) {
        return productOfFactors(c, StatusEffectManager.TARGET_DAMAGE_TAKEN);
    }

    private float productOfFactors(Combatant c, String target) {
        if (c == null) return 1f;
        float mult = 1f;
        for (ActiveStatusEffect inst : c.effects) {
            StatusEffectData def = manager.getEffect(inst.effectId);
            if (def == null || !StatusEffectManager.KIND_MODIFIER.equals(def.kind)
                || !target.equals(def.target)) continue;
            float mag = inst.magnitudeOverride != null ? inst.magnitudeOverride : def.magnitude;
            mult *= resolveFactor(mag, def.magnitudeType);
        }
        return mult;
    }

    private float resolveFactor(float magnitude, String magnitudeType) {
        return StatusEffectManager.MAG_MULTIPLIER.equals(magnitudeType) ? magnitude : (1f + magnitude);
    }

    /** Sum of every active reflect modifier's percentage (each is an independent payout, not a chain). */
    public float getReflectPercent(Combatant c) {
        if (c == null) return 0f;
        float sum = 0f;
        for (ActiveStatusEffect inst : c.effects) {
            StatusEffectData def = manager.getEffect(inst.effectId);
            if (def == null || !StatusEffectManager.KIND_MODIFIER.equals(def.kind)
                || !StatusEffectManager.TARGET_REFLECT.equals(def.target)) continue;
            sum += inst.magnitudeOverride != null ? inst.magnitudeOverride : def.magnitude;
        }
        return sum;
    }

    /** Soaks dmg into the combatant's shield first; returns the remainder to apply to hp. */
    public int absorbWithShield(Combatant c, int dmg) {
        if (c == null || c.shield <= 0 || dmg <= 0) return dmg;
        int absorbed = Math.min(c.shield, dmg);
        c.shield -= absorbed;
        return dmg - absorbed;
    }

    /**
     * If the combatant holds a "revive" effect, consumes it and revives at
     * the effect's magnitude fraction of max HP instead of dying — the
     * data-driven replacement for eternal_phoenix's old partyTypes[i]=
     * "phoenix_revived" one-shot hack. Call BEFORE marking a 0-hp combatant
     * dead; returns true if a revive fired.
     */
    public boolean tryConsumeRevive(Combatant c) {
        if (c == null) return false;
        Iterator<ActiveStatusEffect> it = c.effects.iterator();
        while (it.hasNext()) {
            ActiveStatusEffect inst = it.next();
            StatusEffectData def = manager.getEffect(inst.effectId);
            if (def != null && StatusEffectManager.KIND_REVIVE.equals(def.kind)) {
                it.remove();
                float mag = inst.magnitudeOverride != null ? inst.magnitudeOverride : def.magnitude;
                c.hp = Math.max(1, resolveFlatAmount(c, mag, def.magnitudeType));
                c.dead = false;
                return true;
            }
        }
        return false;
    }
}
