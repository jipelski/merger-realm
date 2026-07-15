package com.jipelski.mergerrealm.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks the state of an active raid instance.
 *
 * Party slots:
 *   [0] FRONT_1 — takes 50% of incoming damage
 *   [1] FRONT_2 — takes 30% of incoming damage
 *   [2] BACK_1  — takes 10% of incoming damage
 *   [3] BACK_2  — takes 10% of incoming damage
 *
 * Raid V3: the party is now 4 {@link Combatant} slots (null = empty) instead
 * of 11 parallel arrays, and activeEnemies is a List&lt;Combatant&gt; instead
 * of the old RaidEnemy type — player units and enemies share one shape, so
 * status effects (see ActiveStatusEffect) attach symmetrically to both.
 */
public class RaidState {

    // ── Party ──
    private Combatant[] party = new Combatant[4];

    // ── Raid info ──
    private String chapterId;
    private String nodeId;
    private int currentRoomIndex = 0;
    private boolean completed = false;
    private boolean failed = false;
    private int deathCount = 0;

    // ── Current room enemies ──
    private List<Combatant> activeEnemies = new ArrayList<>();

    // ── Loot ──
    private int trophiesEarned = 0;
    private List<String> itemsFound = new ArrayList<>();  // item IDs
    private String enchantedDrop = null;                  // enchanted item ID if dropped
    private int bossTokens = 0;

    // ── Fury (crit charge) system ──
    public enum FuryPhase { CHARGING, READY, SELECTING, ACTIVE }
    private FuryPhase furyPhase = FuryPhase.CHARGING;
    private float furyMeter = 0f;          // 0..1
    private float furyMultiplier = 1.5f;   // current (SELECTING) or locked (ACTIVE)
    private float furyActiveTimer = 0f;

    public FuryPhase getFuryPhase() { return furyPhase; }
    public void setFuryPhase(FuryPhase p) { this.furyPhase = p; }
    public float getFuryMeter() { return furyMeter; }
    public void setFuryMeter(float m) { this.furyMeter = m; }
    public float getFuryMultiplier() { return furyMultiplier; }
    public void setFuryMultiplier(float m) { this.furyMultiplier = m; }
    public float getFuryActiveTimer() { return furyActiveTimer; }
    public void setFuryActiveTimer(float t) { this.furyActiveTimer = t; }

    // ── Room transition state ──
    private boolean inTransition = false;
    private float transitionTimer = 0f;
    public boolean isInTransition() { return inTransition; }
    public void setInTransition(boolean t) { this.inTransition = t; }
    public float getTransitionTimer() { return transitionTimer; }
    public void setTransitionTimer(float t) { this.transitionTimer = t; }

    // ── Endless mode (procedural rooms, extract-or-die) ──
    // currentRoomIndex above IS the depth counter here too — no separate
    // field needed, same "0-based room currently being fought" meaning
    // RaidManager.loadRoom already gives it for story nodes.
    private boolean endless = false;
    private long seed = 0L;                 // seeds RaidManager.generateEndlessRoom deterministically
    private boolean awaitingExtractDecision = false; // paused at a checkpoint — see RaidManager.isCheckpointDepth
    private int pendingTrophies = 0;        // banked only on extractEndless(); forfeited on a wipe
    private int pendingTokens = 0;
    private List<Item> pendingLootItems = new ArrayList<>(); // zombie-encounter item retrieval — see Phase 2
    private String currentZombiePoolId = null; // links the current room to a DeadPartySnapshot, if zombified

    public boolean isEndless() { return endless; }
    public void setEndless(boolean e) { this.endless = e; }
    public long getSeed() { return seed; }
    public void setSeed(long s) { this.seed = s; }
    public boolean isAwaitingExtractDecision() { return awaitingExtractDecision; }
    public void setAwaitingExtractDecision(boolean a) { this.awaitingExtractDecision = a; }
    public int getPendingTrophies() { return pendingTrophies; }
    public void addPendingTrophies(int t) { this.pendingTrophies += t; }
    public int getPendingTokens() { return pendingTokens; }
    public void addPendingTokens(int t) { this.pendingTokens += t; }
    public List<Item> getPendingLootItems() { return pendingLootItems; }
    public String getCurrentZombiePoolId() { return currentZombiePoolId; }
    public void setCurrentZombiePoolId(String id) { this.currentZombiePoolId = id; }

    // ── Damage distribution ratios ──
    public static final float[] DAMAGE_DISTRIBUTION = {0.50f, 0.30f, 0.10f, 0.10f};

    // ── Default attack speed for units ──
    public static final float DEFAULT_ATTACK_SPEED = 2.8f;

    public RaidState() {}

    // ── Party management ──

    public void setPartyMember(int slot, String unitId, String type, int level,
                               int maxHp, int currentHp, int damage, String sprite) {
        Combatant c = new Combatant();
        c.id = unitId;
        c.type = type;
        c.level = level;
        c.maxHp = maxHp;
        c.hp = currentHp;
        c.baseDamage = damage;
        c.baseAttackSpeed = DEFAULT_ATTACK_SPEED;
        c.attackTimer = 0f;
        c.dead = false;
        c.sprite = sprite;
        c.party = true;
        party[slot] = c;
    }

    /** Returns the Combatant in the given party slot, or null if empty. */
    public Combatant getMember(int slot) { return party[slot]; }

    /** Returns the enemy at index i in the current room's activeEnemies. */
    public Combatant getEnemy(int i) { return activeEnemies.get(i); }

    public boolean isSlotOccupied(int slot) {
        return party[slot] != null;
    }

    public boolean isSlotAlive(int slot) {
        return party[slot] != null && party[slot].isAlive();
    }

    public int getAliveCount() {
        int count = 0;
        for (int i = 0; i < 4; i++) {
            if (isSlotAlive(i)) count++;
        }
        return count;
    }

    public boolean isPartyWiped() {
        return getAliveCount() == 0;
    }

    /**
     * Returns the total party DPS (sum of alive members' damage).
     */
    public int getPartyDps() {
        int total = 0;
        for (int i = 0; i < 4; i++) {
            if (isSlotAlive(i)) total += party[i].baseDamage;
        }
        return total;
    }

    /**
     * Uses a potion on a party member. Heals to full HP.
     * Returns true if successful.
     */
    public boolean usePotion(int slot) {
        Combatant c = party[slot];
        if (c == null || c.dead) return false;
        if (c.hp >= c.maxHp) return false;
        c.hp = c.maxHp;
        return true;
    }

    /**
     * Uses a Phoenix Feather to revive a dead party member at 50% HP.
     */
    public boolean usePhoenixFeather(int slot) {
        Combatant c = party[slot];
        if (c == null || !c.dead) return false;
        c.dead = false;
        c.hp = c.maxHp / 2;
        deathCount--; // undo the death for star rating
        return true;
    }

    /**
     * Returns star rating: 3 = no deaths, 2 = 1 death, 1 = 2+ deaths
     */
    public int getStarRating() {
        if (deathCount == 0) return 3;
        if (deathCount == 1) return 2;
        return 1;
    }

    // ── Room/enemy management ──

    public boolean isRoomCleared() {
        for (Combatant e : activeEnemies) {
            if (e.hp > 0) return false;
        }
        return true;
    }

    // ── Getters ──

    /**
     * Builds a fresh String[4] of party unit ids (null for empty slots) —
     * used only by the occasional caller that needs the classic array shape
     * (EnchantedSetManager.checkSetBonuses on resume). Not called per-frame,
     * so allocating here is fine — contrast with the removed per-frame
     * array getters below.
     */
    public String[] getPartyUnitIdsArray() {
        String[] ids = new String[4];
        for (int i = 0; i < 4; i++) ids[i] = party[i] != null ? party[i].id : null;
        return ids;
    }

    public String getChapterId() { return chapterId; }
    public void setChapterId(String id) { this.chapterId = id; }
    public String getNodeId() { return nodeId; }
    public void setNodeId(String id) { this.nodeId = id; }
    public int getCurrentRoomIndex() { return currentRoomIndex; }
    public void setCurrentRoomIndex(int i) { this.currentRoomIndex = i; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean c) { this.completed = c; }
    public boolean isFailed() { return failed; }
    public void setFailed(boolean f) { this.failed = f; }
    public int getDeathCount() { return deathCount; }
    public void setDeathCount(int d) { this.deathCount = d; }

    public List<Combatant> getActiveEnemies() { return activeEnemies; }
    public void setActiveEnemies(List<Combatant> e) { this.activeEnemies = e; }

    public int getTrophiesEarned() { return trophiesEarned; }
    public void setTrophiesEarned(int t) { this.trophiesEarned = t; }
    public void addTrophies(int t) { this.trophiesEarned += t; }
    public List<String> getItemsFound() { return itemsFound; }
    public String getEnchantedDrop() { return enchantedDrop; }
    public void setEnchantedDrop(String e) { this.enchantedDrop = e; }
    public int getBossTokens() { return bossTokens; }
    public void setBossTokens(int t) { this.bossTokens = t; }

    /** Presentation event pushed by RaidManager, consumed by RaidArenaRenderer. */
    public static class CombatEvent {
        public String kind;          // "attack", "heal", "room_clear", "room_start", "fury", "status_apply", "status_tick"
        public boolean actorIsParty;
        public int actorIdx;         // party slot or enemy index (or room index for room_start)
        public boolean targetIsParty;
        public int targetIdx;
        public int damage;           // dmg dealt / heal amount / mult*10 for fury
        public boolean crit;
        public String effectId;      // set for status_apply/status_tick — renderer resolves color/name via StatusEffectManager
    }

    private final java.util.List<CombatEvent> pendingEvents = new java.util.ArrayList<>();

    public void pushEvent(CombatEvent ev) { pendingEvents.add(ev); }

    /** Returns and clears all pending events. Called once per frame by the renderer. */
    public java.util.List<CombatEvent> drainEvents() {
        java.util.List<CombatEvent> out = new java.util.ArrayList<>(pendingEvents);
        pendingEvents.clear();
        return out;
    }
}
