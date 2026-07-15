package com.jipelski.mergerrealm.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A single participant in raid combat — player unit OR enemy. Raid V3
 * unifies what used to be 11 parallel `[4]` party arrays on RaidState plus a
 * separate RaidEnemy class into this one shape, so both sides of combat
 * (and the status-effect system) can be driven by identical code instead of
 * two asymmetric representations.
 *
 * Plain public fields, no getters — same convention the old RaidEnemy used
 * (and DeadPartySnapshot/CombatEvent still use), since this is a Gson-
 * serialized data holder read/written directly by RaidManager, not an
 * encapsulated domain object.
 */
public class Combatant {

    // ── Identity ──
    public String id;      // party: grid unit id; enemy: enemy key (e.g. "goblin_grunt")
    public String type;    // party: unit type; enemy: same as id (RaidEnemy.type kept this redundancy)
    public String name;    // enemy display name; null for party (display derives from type, as before)
    public String sprite;
    public boolean party;  // true = player unit, false = enemy
    public boolean boss;   // enemy-only

    // ── Stats ──
    public int level;
    public int maxHp;
    public int hp;
    public int baseDamage;
    public int shield;     // absorbs incoming damage before hp (Raid V3 status effect)

    // ── Timing ──
    public float baseAttackSpeed;  // seconds between attacks
    public float attackTimer;      // accumulator

    // ── State ──
    public boolean dead;

    // ── Status effects (Raid V3) ──
    public List<ActiveStatusEffect> effects = new ArrayList<>();

    // Effect id applied to whatever this combatant successfully hits — cached
    // here at creation (from UnitData for party members, from raid_enemies.json
    // for enemies) so RaidManager doesn't re-resolve static content on every
    // single attack. "none"/null = no on-hit effect.
    public String onHitEffect;

    public Combatant() {}

    public boolean isAlive() {
        return !dead && hp > 0;
    }
}
