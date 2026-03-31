package com.jipelski.mergerrealm.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents one unit currently out on exploration.
 *
 * When a unit is sent exploring:
 *   1. It's removed from the grid
 *   2. Its data is copied into this slot
 *   3. Events are processed based on elapsed time
 *   4. On return, loot is delivered and unit is placed back on grid
 *
 * The slot stores everything needed to reconstruct the unit on return,
 * even if the app was closed and reopened.
 */
public class ExplorationSlot {

    // ── Unit snapshot (copied when exploration starts) ──
    private String unitId;
    private String unitType;
    private int unitLevel;
    private int unitMaxLvl;
    private int unitMaxHp;
    private int unitCurrentHp;
    private int unitDamage;
    private int unitGenRate;
    private String unitResource;
    private String equippedItemId;

    // ── Exploration state ──
    private String zone;              // "forest", "mountain", "mines", "ruins", "wastes"
    private long startTimeMs;         // System.currentTimeMillis() when exploration began
    private long lastEventTimeMs;     // timestamp of last processed event (relative to start)
    private boolean returning;        // unit is heading home
    private long recallTimeMs;        // System.currentTimeMillis() when recall was initiated
    private boolean dead;             // unit died during exploration
    private int cursedEncounters;     // remaining encounters with curse debuff (ruins)

    // ── Log and loot ──
    private List<ExplorationEvent> log;
    private List<ExplorationLoot> loot;

    public ExplorationSlot() {
        this.log = new ArrayList<>();
        this.loot = new ArrayList<>();
        this.cursedEncounters = 0;
    }

    // ── Unit snapshot getters/setters ──

    public String getUnitId() { return unitId; }
    public void setUnitId(String unitId) { this.unitId = unitId; }

    public String getUnitType() { return unitType; }
    public void setUnitType(String unitType) { this.unitType = unitType; }

    public int getUnitLevel() { return unitLevel; }
    public void setUnitLevel(int unitLevel) { this.unitLevel = unitLevel; }

    public int getUnitMaxLvl() { return unitMaxLvl; }
    public void setUnitMaxLvl(int unitMaxLvl) { this.unitMaxLvl = unitMaxLvl; }

    public int getUnitMaxHp() { return unitMaxHp; }
    public void setUnitMaxHp(int unitMaxHp) { this.unitMaxHp = unitMaxHp; }

    public int getUnitCurrentHp() { return unitCurrentHp; }
    public void setUnitCurrentHp(int hp) { this.unitCurrentHp = hp; }

    public int getUnitDamage() { return unitDamage; }
    public void setUnitDamage(int unitDamage) { this.unitDamage = unitDamage; }

    public int getUnitGenRate() { return unitGenRate; }
    public void setUnitGenRate(int unitGenRate) { this.unitGenRate = unitGenRate; }

    public String getUnitResource() { return unitResource; }
    public void setUnitResource(String unitResource) { this.unitResource = unitResource; }

    public String getEquippedItemId() { return equippedItemId; }
    public void setEquippedItemId(String equippedItemId) { this.equippedItemId = equippedItemId; }

    // ── Exploration state getters/setters ──

    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }

    public long getStartTimeMs() { return startTimeMs; }
    public void setStartTimeMs(long startTimeMs) { this.startTimeMs = startTimeMs; }

    public long getLastEventTimeMs() { return lastEventTimeMs; }
    public void setLastEventTimeMs(long lastEventTimeMs) { this.lastEventTimeMs = lastEventTimeMs; }

    public boolean isReturning() { return returning; }
    public void setReturning(boolean returning) { this.returning = returning; }

    public long getRecallTimeMs() { return recallTimeMs; }
    public void setRecallTimeMs(long recallTimeMs) { this.recallTimeMs = recallTimeMs; }

    public boolean isDead() { return dead; }
    public void setDead(boolean dead) { this.dead = dead; }

    public int getCursedEncounters() { return cursedEncounters; }
    public void setCursedEncounters(int cursedEncounters) { this.cursedEncounters = cursedEncounters; }

    // ── Log and loot ──

    public List<ExplorationEvent> getLog() { return log; }
    public void setLog(List<ExplorationEvent> log) { this.log = log; }

    public List<ExplorationLoot> getLoot() { return loot; }
    public void setLoot(List<ExplorationLoot> loot) { this.loot = loot; }

    public void addEvent(long relativeTimeMs, String text, String type) {
        log.add(new ExplorationEvent(relativeTimeMs, text, type));
    }

    public void addLoot(ExplorationLoot l) {
        loot.add(l);
    }

    // ── Computed properties ──

    /**
     * Total time the unit has been exploring (milliseconds).
     */
    public long getElapsedMs() {
        return System.currentTimeMillis() - startTimeMs;
    }

    /**
     * Time remaining before the unit returns home (milliseconds).
     * Returns 0 if not returning or already arrived.
     */
    public long getReturnRemainingMs() {
        if (!returning) return -1;
        long exploreTime = recallTimeMs - startTimeMs;
        long returnDuration = exploreTime / 2;
        long elapsed = System.currentTimeMillis() - recallTimeMs;
        return Math.max(0, returnDuration - elapsed);
    }

    /**
     * Returns true if the unit has arrived home after being recalled.
     */
    public boolean hasArrived() {
        return returning && getReturnRemainingMs() <= 0;
    }

    /**
     * Returns true if this is a max-level unit (survives death).
     */
    public boolean isMaxLevel() {
        return unitLevel >= unitMaxLvl;
    }

    /**
     * Calculates exploration damage: base damage + (level * 10).
     * Applies curse debuff if active.
     */
    public int getExplorationDamage() {
        int baseDmg = unitDamage + (unitLevel * 10);
        if (cursedEncounters > 0) {
            baseDmg = (int)(baseDmg * 0.8f); // 20% reduction from curse
        }
        return baseDmg;
    }

    /**
     * Apply damage to the exploring unit. Returns true if unit died.
     * Max-level units survive at 1 HP instead of dying.
     */
    public boolean takeDamage(int damage) {
        unitCurrentHp -= damage;
        if (unitCurrentHp <= 0) {
            if (isMaxLevel()) {
                unitCurrentHp = 1;
                return false; // max level units survive
            }
            unitCurrentHp = 0;
            dead = true;
            return true;
        }
        return false;
    }

    /**
     * Heal by amount, capped at max HP.
     */
    public void heal(int amount) {
        unitCurrentHp = Math.min(unitMaxHp, unitCurrentHp + amount);
    }

    /**
     * Heal by percentage of max HP.
     */
    public void healPercent(int percent) {
        int amount = unitMaxHp * percent / 100;
        heal(Math.max(1, amount));
    }

    /**
     * Use a Phoenix Feather — revive at 50% HP mid-exploration.
     * Only works if unit is dead.
     */
    public boolean usePhoenixFeather() {
        if (!dead) return false;
        dead = false;
        unitCurrentHp = unitMaxHp / 2;
        return true;
    }
}
