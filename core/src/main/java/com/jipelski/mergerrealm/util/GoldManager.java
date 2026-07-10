package com.jipelski.mergerrealm.util;

import com.badlogic.gdx.Gdx;

import java.util.HashSet;
import java.util.Set;

/**
 * Manages the Gold premium currency system.
 *
 * Gold is earned through gameplay milestones and rare events.
 * Gold buys convenience and time-skips — never direct power.
 *
 * Earning sources:
 *   - First-clear raid nodes: 5-15 Gold
 *   - First 3-star raid clears: 10 Gold
 *   - Prince level milestones (every 5 levels): 20 Gold
 *   - Daily login bonus: 2-5 Gold
 *   - Exploration rare find (~1% in Ruins/Wastes): 1-3 Gold
 *   - Dismiss max-level legendary unit: 10 Gold
 *
 * Spending:
 *   - Fill all resources to max: 15 Gold
 *   - Speed up exploration (halve remaining time): 10 Gold
 *   - Instant periodic facility spawn (skip timer): 5 Gold
 *   - Revive entire raid party mid-raid: 30 Gold
 *   - Unlock extra exploration slot (permanent): 40 Gold
 *
 * Designed for optional real-money purchase integration later.
 * addGold() is the single entry point for all Gold additions,
 * making it easy to hook up an IAP callback.
 */
public class GoldManager {

    private static final String TAG = "GoldManager";

    // ── Balance ──
    private int gold = 0;

    // ── Spending costs ──
    public static final int COST_FILL_RESOURCES = 15;
    public static final int COST_SPEED_EXPLORATION = 10;
    public static final int COST_INSTANT_SPAWN = 5;
    public static final int COST_RAID_REVIVE_ALL = 30;
    public static final int COST_EXTRA_EXPLORE_SLOT = 40;

    // ── Earning amounts ──
    public static final int EARN_DAILY_LOGIN_MIN = 2;
    public static final int EARN_DAILY_LOGIN_MAX = 5;
    public static final int EARN_PRINCE_MILESTONE = 20;
    public static final int EARN_FIRST_3STAR = 10;
    public static final int EARN_DISMISS_LEGENDARY = 10;
    public static final int EARN_EXPLORE_FIND_MIN = 1;
    public static final int EARN_EXPLORE_FIND_MAX = 3;

    // First-clear raid gold by node type
    public static final int EARN_RAID_MAIN_NODE = 5;
    public static final int EARN_RAID_SIDE_NODE = 8;
    public static final int EARN_RAID_BOSS_NODE = 15;

    // ── Tracking one-time rewards ──
    // Stores keys like "raid:gw_boss", "milestone:10", "3star:gw_outpost"
    private Set<String> claimedRewards = new HashSet<>();

    // ── Extra exploration slots purchased ──
    private int extraExploreSlots = 0;

    // ── Daily login tracking ──
    private long lastDailyLoginMs = 0;
    private static final long DAILY_MS = 24L * 60 * 60 * 1000;

    // ── Reference ──
    private final EventManager eventManager;

    public GoldManager(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    // ══════════════════════════════════════════════════════════════
    // BALANCE
    // ══════════════════════════════════════════════════════════════

    public int getGold() { return gold; }
    public void setGold(int gold) { this.gold = gold; }

    /**
     * Adds Gold to the player's balance.
     * This is the SINGLE entry point for all Gold additions.
     * Hook IAP callbacks into this method.
     *
     * @param amount amount to add
     * @param source description for logging (e.g. "raid_first_clear", "iap_purchase")
     */
    public void addGold(int amount, String source) {
        if (amount <= 0) return;
        gold += amount;
        Gdx.app.log(TAG, "+" + amount + " Gold (" + source + ") — total: " + gold);
    }

    /**
     * Attempts to spend Gold. Returns true if successful.
     */
    public boolean spendGold(int amount, String purpose) {
        if (amount <= 0) return false;
        if (gold < amount) {
            Gdx.app.log(TAG, "Not enough Gold: have " + gold + ", need " + amount
                + " for " + purpose);
            return false;
        }
        gold -= amount;
        Gdx.app.log(TAG, "-" + amount + " Gold (" + purpose + ") — remaining: " + gold);
        return true;
    }

    public boolean canAfford(int amount) {
        return gold >= amount;
    }

    // ══════════════════════════════════════════════════════════════
    // EARNING — GAMEPLAY SOURCES
    // ══════════════════════════════════════════════════════════════

    /**
     * Awards Gold for first-clearing a raid node.
     * Only awards once per node (tracked in claimedRewards).
     */
    public void onRaidNodeFirstClear(String chapterId, String nodeId, String nodeType) {
        String key = "raid:" + chapterId + ":" + nodeId;
        if (claimedRewards.contains(key)) return;

        int amount;
        switch (nodeType) {
            case "boss": amount = EARN_RAID_BOSS_NODE; break;
            case "side": amount = EARN_RAID_SIDE_NODE; break;
            default:     amount = EARN_RAID_MAIN_NODE; break;
        }

        claimedRewards.add(key);
        addGold(amount, "raid_first_clear_" + nodeId);
    }

    /**
     * Awards Gold for first 3-star clear of a raid node.
     */
    public void onRaidFirst3Star(String chapterId, String nodeId) {
        String key = "3star:" + chapterId + ":" + nodeId;
        if (claimedRewards.contains(key)) return;

        claimedRewards.add(key);
        addGold(EARN_FIRST_3STAR, "first_3star_" + nodeId);
    }

    /**
     * Awards Gold for reaching a prince level milestone.
     * Triggers at levels 5, 10, 15, 20, 25, 30, 35.
     */
    public void onPrinceLevelUp(int newLevel) {
        if (newLevel % 5 != 0) return;

        String key = "milestone:" + newLevel;
        if (claimedRewards.contains(key)) return;

        claimedRewards.add(key);
        addGold(EARN_PRINCE_MILESTONE, "prince_milestone_" + newLevel);
    }

    /**
     * Awards daily login Gold. Call once when the game opens.
     * Returns the amount awarded, or 0 if already claimed today.
     */
    public int onDailyLogin() {
        long now = System.currentTimeMillis();
        if (lastDailyLoginMs > 0 && now - lastDailyLoginMs < DAILY_MS) {
            return 0; // already claimed today
        }

        lastDailyLoginMs = now;
        int amount = EARN_DAILY_LOGIN_MIN
            + (int)(Math.random() * (EARN_DAILY_LOGIN_MAX - EARN_DAILY_LOGIN_MIN + 1));
        addGold(amount, "daily_login");
        return amount;
    }

    /**
     * Awards Gold for a rare exploration find.
     * Called by ExplorationManager during event processing.
     */
    public void onExplorationGoldFind() {
        int amount = EARN_EXPLORE_FIND_MIN
            + (int)(Math.random() * (EARN_EXPLORE_FIND_MAX - EARN_EXPLORE_FIND_MIN + 1));
        addGold(amount, "exploration_find");
    }

    /**
     * Awards Gold for dismissing a max-level legendary unit.
     */
    public void onDismissLegendary() {
        addGold(EARN_DISMISS_LEGENDARY, "dismiss_legendary");
    }

    // ══════════════════════════════════════════════════════════════
    // SPENDING — ACTIONS
    // ══════════════════════════════════════════════════════════════

    /**
     * Fills all resources (food, wood, iron) to their max capacity.
     */
    public boolean fillResources() {
        if (!spendGold(COST_FILL_RESOURCES, "fill_resources")) return false;

        ResourceManager rm = eventManager.getResourceManager();
        rm.fillToMax("food");
        rm.fillToMax("wood");
        rm.fillToMax("iron");

        Gdx.app.log(TAG, "All resources filled to max");
        return true;
    }

    /**
     * Speeds up a unit's trip home by halving its remaining return time.
     * Does NOT affect exploration itself — only usable once a unit has
     * been recalled and is en route back.
     *
     * @param slotIndex the exploration slot to speed up
     */
    public boolean speedUpExploration(int slotIndex) {
        ExplorationManager em = eventManager.getExplorationManager();
        if (em == null) return false;

        java.util.List<com.jipelski.mergerrealm.model.ExplorationSlot> slots =
            em.getActiveSlots();
        if (slotIndex < 0 || slotIndex >= slots.size()) return false;

        com.jipelski.mergerrealm.model.ExplorationSlot slot = slots.get(slotIndex);
        if (slot.isDead() || slot.hasArrived() || !slot.isReturning()) return false;

        if (!spendGold(COST_SPEED_EXPLORATION, "speed_exploration")) return false;

        // Halve the remaining return time
        long remaining = slot.getReturnRemainingMs();
        slot.setReturnSpeedupMs(slot.getReturnSpeedupMs() + remaining / 2);

        Gdx.app.log(TAG, "Exploration slot " + slotIndex + " return trip sped up (remaining time halved)");
        return true;
    }

    /**
     * Instantly triggers a periodic facility spawn, skipping the timer.
     *
     * @param facilityId the facility to trigger
     */
    public boolean instantPeriodicSpawn(String facilityId) {
        if (!spendGold(COST_INSTANT_SPAWN, "instant_spawn")) return false;

        com.jipelski.mergerrealm.model.GameObject obj =
            eventManager.getGRID_OBJECT_MANAGER().getObject(facilityId);
        if (obj == null) return false;

        if (!EventManager.PERIODIC_FACILITIES.contains(obj.getType())) {
            Gdx.app.log(TAG, "Not a periodic facility: " + obj.getType());
            // Refund
            gold += COST_INSTANT_SPAWN;
            return false;
        }

        com.jipelski.mergerrealm.model.Facility facility =
            (com.jipelski.mergerrealm.model.Facility) obj;

        // Force timer to trigger
        com.jipelski.mergerrealm.data.FacilityData data =
            (com.jipelski.mergerrealm.data.FacilityData)
                eventManager.getGameDataLoader().getGameData(facility.getType(), facility.getLvl());
        if (data != null && data.getTimeCost() > 0) {
            facility.setSpawnTimer(data.getTimeCost());
        }

        Gdx.app.log(TAG, "Instant spawn triggered for " + facility.getType());
        return true;
    }

    /**
     * Revives all dead party members in the current raid at 50% HP.
     */
    public boolean reviveRaidParty() {
        if (!spendGold(COST_RAID_REVIVE_ALL, "raid_revive_all")) return false;

        RaidManager rm = eventManager.getRaidManager();
        if (rm == null || !rm.isRaidActive()) {
            gold += COST_RAID_REVIVE_ALL; // refund
            return false;
        }

        com.jipelski.mergerrealm.model.RaidState raid = rm.getActiveRaid();
        boolean[] dead = raid.getPartyDead();
        int[] hp = raid.getPartyCurrentHp();
        int[] maxHp = raid.getPartyMaxHp();

        int revived = 0;
        for (int i = 0; i < 4; i++) {
            if (raid.isSlotOccupied(i) && dead[i]) {
                dead[i] = false;
                hp[i] = maxHp[i] / 2;
                raid.setDeathCount(Math.max(0, raid.getDeathCount() - 1));
                revived++;
            }
        }

        Gdx.app.log(TAG, "Revived " + revived + " party members via Gold");
        return revived > 0;
    }

    /**
     * Purchases a permanent extra exploration slot.
     * Stacks with prince-level slots.
     */
    public boolean purchaseExtraExploreSlot() {
        if (!spendGold(COST_EXTRA_EXPLORE_SLOT, "extra_explore_slot")) return false;

        extraExploreSlots++;
        Gdx.app.log(TAG, "Extra exploration slot purchased (total: " + extraExploreSlots + ")");
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    // EXTRA SLOTS
    // ══════════════════════════════════════════════════════════════

    public int getExtraExploreSlots() { return extraExploreSlots; }
    public void setExtraExploreSlots(int slots) { this.extraExploreSlots = slots; }

    /**
     * Returns total exploration slots available
     * (prince level slots + purchased slots).
     */
    public int getTotalExploreSlots() {
        int princeLvl = eventManager.getBattleFieldManager().getLevel();
        return ExplorationManager.getSlotsForLevel(princeLvl) + extraExploreSlots;
    }

    // ══════════════════════════════════════════════════════════════
    // SAVE / LOAD
    // ══════════════════════════════════════════════════════════════

    public Set<String> getClaimedRewards() { return claimedRewards; }
    public void setClaimedRewards(Set<String> rewards) { this.claimedRewards = rewards; }

    public long getLastDailyLoginMs() { return lastDailyLoginMs; }
    public void setLastDailyLoginMs(long ms) { this.lastDailyLoginMs = ms; }
}
