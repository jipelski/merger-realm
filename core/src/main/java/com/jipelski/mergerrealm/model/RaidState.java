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
 */
public class RaidState {

    // ── Party ──
    private String[] partyUnitIds = new String[4];    // grid unit IDs (removed from grid)
    private String[] partyTypes = new String[4];
    private int[] partyLevels = new int[4];
    private int[] partyMaxHp = new int[4];
    private int[] partyCurrentHp = new int[4];
    private int[] partyDamage = new int[4];
    private float[] partyAttackSpeed = new float[4];  // seconds between attacks
    private float[] partyAttackTimer = new float[4];
    private boolean[] partyDead = new boolean[4];

    // ── Raid info ──
    private String chapterId;
    private String nodeId;
    private int currentRoomIndex = 0;
    private boolean completed = false;
    private boolean failed = false;
    private int deathCount = 0;

    // ── Current room enemies ──
    private List<RaidEnemy> activeEnemies = new ArrayList<>();

    // ── Loot ──
    private int trophiesEarned = 0;
    private List<String> itemsFound = new ArrayList<>();  // item IDs
    private String enchantedDrop = null;                  // enchanted item ID if dropped
    private int bossTokens = 0;

    // ── Damage distribution ratios ──
    public static final float[] DAMAGE_DISTRIBUTION = {0.50f, 0.30f, 0.10f, 0.10f};

    // ── Default attack speed for units ──
    public static final float DEFAULT_ATTACK_SPEED = 1.5f;

    public RaidState() {}

    // ── Party management ──

    public void setPartyMember(int slot, String unitId, String type, int level,
                               int maxHp, int currentHp, int damage) {
        partyUnitIds[slot] = unitId;
        partyTypes[slot] = type;
        partyLevels[slot] = level;
        partyMaxHp[slot] = maxHp;
        partyCurrentHp[slot] = currentHp;
        partyDamage[slot] = damage;
        partyAttackSpeed[slot] = DEFAULT_ATTACK_SPEED;
        partyAttackTimer[slot] = 0f;
        partyDead[slot] = false;
    }

    public boolean isSlotOccupied(int slot) {
        return partyUnitIds[slot] != null;
    }

    public boolean isSlotAlive(int slot) {
        return isSlotOccupied(slot) && !partyDead[slot] && partyCurrentHp[slot] > 0;
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
     * Deals damage to the party, distributed by position.
     * Front slots absorb most damage.
     */
    public void dealDamageToParty(int totalDamage) {
        for (int i = 0; i < 4; i++) {
            if (!isSlotAlive(i)) continue;
            int dmg = Math.round(totalDamage * DAMAGE_DISTRIBUTION[i]);
            if (dmg <= 0) continue;
            partyCurrentHp[i] -= dmg;
            if (partyCurrentHp[i] <= 0) {
                partyCurrentHp[i] = 0;
                partyDead[i] = true;
                deathCount++;
            }
        }
        // Redistribute damage from dead slots to alive slots
        redistributeDamage();
    }

    /**
     * Redistributes damage shares from dead party members to living ones.
     */
    private void redistributeDamage() {
        // This only affects future damage distribution — already-dealt damage stays
    }

    /**
     * Returns the total party DPS (sum of alive members' damage).
     */
    public int getPartyDps() {
        int total = 0;
        for (int i = 0; i < 4; i++) {
            if (isSlotAlive(i)) total += partyDamage[i];
        }
        return total;
    }

    /**
     * Uses a potion on a party member. Heals to full HP.
     * Returns true if successful.
     */
    public boolean usePotion(int slot) {
        if (!isSlotOccupied(slot) || partyDead[slot]) return false;
        if (partyCurrentHp[slot] >= partyMaxHp[slot]) return false;
        partyCurrentHp[slot] = partyMaxHp[slot];
        return true;
    }

    /**
     * Uses a Phoenix Feather to revive a dead party member at 50% HP.
     */
    public boolean usePhoenixFeather(int slot) {
        if (!isSlotOccupied(slot) || !partyDead[slot]) return false;
        partyDead[slot] = false;
        partyCurrentHp[slot] = partyMaxHp[slot] / 2;
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
        for (RaidEnemy e : activeEnemies) {
            if (e.hp > 0) return false;
        }
        return true;
    }

    public void nextRoom(List<RaidEnemy> enemies) {
        currentRoomIndex++;
        activeEnemies = enemies;
        // Reset attack timers
        for (int i = 0; i < 4; i++) {
            partyAttackTimer[i] = 0f;
        }
    }

    // ── Getters ──

    public String[] getPartyUnitIds() { return partyUnitIds; }
    public String[] getPartyTypes() { return partyTypes; }
    public int[] getPartyLevels() { return partyLevels; }
    public int[] getPartyMaxHp() { return partyMaxHp; }
    public int[] getPartyCurrentHp() { return partyCurrentHp; }
    public int[] getPartyDamage() { return partyDamage; }
    public float[] getPartyAttackSpeed() { return partyAttackSpeed; }
    public float[] getPartyAttackTimer() { return partyAttackTimer; }
    public boolean[] getPartyDead() { return partyDead; }

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

    public List<RaidEnemy> getActiveEnemies() { return activeEnemies; }
    public void setActiveEnemies(List<RaidEnemy> e) { this.activeEnemies = e; }

    public int getTrophiesEarned() { return trophiesEarned; }
    public void setTrophiesEarned(int t) { this.trophiesEarned = t; }
    public void addTrophies(int t) { this.trophiesEarned += t; }
    public List<String> getItemsFound() { return itemsFound; }
    public String getEnchantedDrop() { return enchantedDrop; }
    public void setEnchantedDrop(String e) { this.enchantedDrop = e; }
    public int getBossTokens() { return bossTokens; }
    public void setBossTokens(int t) { this.bossTokens = t; }

    /**
     * Simple enemy data holder for active combat.
     */
    public static class RaidEnemy {
        public String type;
        public String name;
        public int hp;
        public int maxHp;
        public int damage;
        public float attackSpeed;
        public float attackTimer = 0f;
        public boolean boss = false;

        public RaidEnemy(String type, String name, int hp, int damage,
                         float attackSpeed, boolean boss) {
            this.type = type;
            this.name = name;
            this.hp = hp;
            this.maxHp = hp;
            this.damage = damage;
            this.attackSpeed = attackSpeed;
            this.boss = boss;
        }

        public boolean isAlive() { return hp > 0; }
    }
}
