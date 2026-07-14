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
 *   - Daily login streak (DailyLoginManager): 2-15 Gold, escalating over 7 days
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
    public static final int EARN_PRINCE_MILESTONE = 20;
    public static final int EARN_FIRST_3STAR = 10;
    public static final int EARN_DISMISS_LEGENDARY = 10;
    public static final int EARN_EXPLORE_FIND_MIN = 1;
    public static final int EARN_EXPLORE_FIND_MAX = 3;

    // First-clear raid gold by node type
    public static final int EARN_RAID_MAIN_NODE = 5;
    public static final int EARN_RAID_SIDE_NODE = 8;
    public static final int EARN_RAID_BOSS_NODE = 15;
    public static final int EARN_RAID_CHALLENGE_NODE = 25;

    // Equipment salvage Gold by item level (index 0 = level 1)
    public static final int[] SALVAGE_GOLD_PER_LEVEL = {1, 2, 3, 5, 8};

    // ── Tracking one-time rewards ──
    // Stores keys like "raid:gw_boss", "milestone:10", "3star:gw_outpost"
    private Set<String> claimedRewards = new HashSet<>();

    // ── Extra exploration slots purchased ──
    private int extraExploreSlots = 0;

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
            case "boss":      amount = EARN_RAID_BOSS_NODE; break;
            case "side":      amount = EARN_RAID_SIDE_NODE; break;
            case "challenge": amount = EARN_RAID_CHALLENGE_NODE; break;
            default:          amount = EARN_RAID_MAIN_NODE; break;
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

    /** Gold value of salvaging one unequipped sword/shield/amulet at the given level. */
    public int getSalvageValue(int level) {
        int idx = level - 1;
        if (idx < 0 || idx >= SALVAGE_GOLD_PER_LEVEL.length) return 0;
        return SALVAGE_GOLD_PER_LEVEL[idx];
    }

    public void onSalvageEquipment(int totalGold) {
        addGold(totalGold, "salvage_equipment");
    }

    // ══════════════════════════════════════════════════════════════
    // SPENDING — ACTIONS
    //
    // Each of these has a package-private "do*" effect-only core (no Gold
    // charge) shared with AdManager's free-via-ad path — see AdManager.
    // Exception: instantPeriodicSpawn() below is left untouched and has its
    // own separate doInstantPeriodicSpawn() core rather than a shared
    // extraction, so its documented spend-first/no-refund-on-missing-
    // facility quirk (asserted by GoldManagerTest) can't be disturbed.
    // ══════════════════════════════════════════════════════════════

    /**
     * Fills all resources (food, wood, iron) to their max capacity.
     */
    public boolean fillResources() {
        if (!spendGold(COST_FILL_RESOURCES, "fill_resources")) return false;
        doFillResources();
        return true;
    }

    /** Free effect core for fillResources — no Gold charge. */
    void doFillResources() {
        ResourceManager rm = eventManager.getResourceManager();
        rm.fillToMax("food");
        rm.fillToMax("wood");
        rm.fillToMax("iron");
        Gdx.app.log(TAG, "All resources filled to max");
    }

    /**
     * Speeds up a unit's trip home by halving its remaining return time.
     * Does NOT affect exploration itself — only usable once a unit has
     * been recalled and is en route back.
     *
     * @param slotIndex the exploration slot to speed up
     */
    public boolean speedUpExploration(int slotIndex) {
        com.jipelski.mergerrealm.model.ExplorationSlot slot = getSpeedupEligibleSlot(slotIndex);
        if (slot == null) return false;

        if (!spendGold(COST_SPEED_EXPLORATION, "speed_exploration")) return false;

        applySpeedup(slotIndex, slot);
        return true;
    }

    /**
     * Free effect core for speedUpExploration — no Gold charge. Returns
     * true if the slot was eligible and got sped up.
     */
    boolean doSpeedUpExploration(int slotIndex) {
        com.jipelski.mergerrealm.model.ExplorationSlot slot = getSpeedupEligibleSlot(slotIndex);
        if (slot == null) return false;
        applySpeedup(slotIndex, slot);
        return true;
    }

    private com.jipelski.mergerrealm.model.ExplorationSlot getSpeedupEligibleSlot(int slotIndex) {
        ExplorationManager em = eventManager.getExplorationManager();
        if (em == null) return null;

        java.util.List<com.jipelski.mergerrealm.model.ExplorationSlot> slots =
            em.getActiveSlots();
        if (slotIndex < 0 || slotIndex >= slots.size()) return null;

        com.jipelski.mergerrealm.model.ExplorationSlot slot = slots.get(slotIndex);
        long trustedNow = eventManager.getServerTimeManager().getTrustedTimeMillis();
        if (slot.isDead() || slot.hasArrived(trustedNow) || !slot.isReturning()) return null;
        return slot;
    }

    private void applySpeedup(int slotIndex, com.jipelski.mergerrealm.model.ExplorationSlot slot) {
        long remaining = slot.getReturnRemainingMs(eventManager.getServerTimeManager().getTrustedTimeMillis());
        slot.setReturnSpeedupMs(slot.getReturnSpeedupMs() + remaining / 2);
        Gdx.app.log(TAG, "Exploration slot " + slotIndex + " return trip sped up (remaining time halved)");
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
     * Free effect core for the ad-watch path only. Deliberately a SEPARATE
     * implementation from instantPeriodicSpawn() above (not extracted from
     * it) — see the SPENDING section header comment for why.
     */
    boolean doInstantPeriodicSpawn(String facilityId) {
        com.jipelski.mergerrealm.model.GameObject obj =
            eventManager.getGRID_OBJECT_MANAGER().getObject(facilityId);
        if (obj == null) return false;

        if (!EventManager.PERIODIC_FACILITIES.contains(obj.getType())) {
            Gdx.app.log(TAG, "Not a periodic facility: " + obj.getType());
            return false;
        }

        com.jipelski.mergerrealm.model.Facility facility =
            (com.jipelski.mergerrealm.model.Facility) obj;

        com.jipelski.mergerrealm.data.FacilityData data =
            (com.jipelski.mergerrealm.data.FacilityData)
                eventManager.getGameDataLoader().getGameData(facility.getType(), facility.getLvl());
        if (data != null && data.getTimeCost() > 0) {
            facility.setSpawnTimer(data.getTimeCost());
        }

        Gdx.app.log(TAG, "Instant spawn triggered for " + facility.getType() + " (ad)");
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

        int revived = doReviveRaidParty();
        return revived > 0;
    }

    /**
     * Free effect core for reviveRaidParty — no Gold charge. Includes its
     * own raid-active guard (returns 0 rather than relying on the caller),
     * so it's safe to call directly. Returns the number of members revived.
     */
    int doReviveRaidParty() {
        RaidManager rm = eventManager.getRaidManager();
        if (rm == null || !rm.isRaidActive()) return 0;

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

        Gdx.app.log(TAG, "Revived " + revived + " party members");
        return revived;
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
}
