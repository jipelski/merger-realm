package com.jipelski.mergerrealm.util;

import com.badlogic.gdx.Gdx;

import com.jipelski.mergerrealm.model.Item;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Manages the Prince Prestige system: a permanent currency ("Crowns") earned
 * by resetting the board at PrinceLevelConfig.MAX_LEVEL_FOR_PRESTIGE (or
 * later), spent on permanent ECONOMY/IDLE upgrades — never raid/combat power.
 * Multipliers are read at point-of-use by their respective systems
 * (ResourceManager, BuildMenu, Facility), mirroring exactly how RuneSystem's
 * getBoosted*() getters work: this class never threads itself through those
 * call sites' internals, it just answers "what's the current multiplier" on
 * demand.
 *
 * Upgrade ids: gen_rate, build_cost, spawn_odds, start_level, equipment_slots.
 * Tier costs/effects are tunable constants below (mirrors RuneSystem.
 * BOOST_STANDARD-style single-constant tunability) — balance freely without
 * touching any call site.
 */
public class PrestigeManager {

    private static final String TAG = "PrestigeManager";

    // ── Upgrade ids ──
    public static final String GEN_RATE = "gen_rate";
    public static final String BUILD_COST = "build_cost";
    public static final String SPAWN_ODDS = "spawn_odds";
    public static final String START_LEVEL = "start_level";
    public static final String EQUIPMENT_SLOTS = "equipment_slots";

    // ── Tunable effect-per-tier constants ──
    private static final float GEN_PER_TIER = 0.06f;
    private static final float COST_PER_TIER = 0.05f;
    private static final float SPAWN_PER_TIER = 0.10f;
    private static final int[] START_LEVEL_BY_TIER = {1, 5, 10, 15, 20}; // index = tier

    // ── Max purchasable tier per upgrade ──
    private static final int GEN_MAX_TIER = 5;
    private static final int COST_MAX_TIER = 5;
    private static final int SPAWN_MAX_TIER = 4;
    private static final int START_MAX_TIER = 4;
    private static final int EQUIP_MAX_TIER = 4; // +1 free base slot = 5 total max

    // ── Crown cost for tier N (1-indexed; costs[0] = cost of tier 1) ──
    private static final int[] GEN_COSTS   = {1, 2, 4, 7, 11};
    private static final int[] COST_COSTS  = {1, 2, 4, 7, 11};
    private static final int[] SPAWN_COSTS = {2, 4, 8, 14};
    private static final int[] START_COSTS = {3, 6, 12, 20};
    private static final int[] EQUIP_COSTS = {2, 5, 10, 18};

    // ── Crown earning ──
    public static final int BASE_CROWNS = 3;
    public static final int CROWNS_PER_EXTRA_LEVEL = 1;

    // ── State ──
    private int crowns = 0;
    private int prestigeCount = 0;
    private final Map<String, Integer> upgradeTiers = new HashMap<>();
    private final Set<String> protectedItemIds = new HashSet<>();

    private final EventManager eventManager;

    public PrestigeManager(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    // ══════════════════════════════════════════════════════════════
    // BALANCE
    // ══════════════════════════════════════════════════════════════

    public int getCrowns() { return crowns; }
    public int getPrestigeCount() { return prestigeCount; }

    public boolean canAfford(int amount) { return crowns >= amount; }

    // ══════════════════════════════════════════════════════════════
    // GATE + EARNING
    // ══════════════════════════════════════════════════════════════

    /**
     * True if the player is eligible to prestige right now: at or above the
     * level gate, AND no units are deployed off-board (raid/exploration).
     * Without the deployment guard, a player could park units away on a raid
     * or exploration, reset the board, and get those units back afterward —
     * trivializing the reset.
     */
    public boolean canPrestige() {
        BattleFieldManager bfm = eventManager.getBattleFieldManager();
        RaidManager raidManager = eventManager.getRaidManager();
        ExplorationManager explorationManager = eventManager.getExplorationManager();

        return bfm.getLevel() >= PrinceLevelConfig.MAX_LEVEL_FOR_PRESTIGE
            && !raidManager.isRaidActive()
            && explorationManager.getActiveSlots().isEmpty();
    }

    public int earnedCrowns(int currentLevel) {
        int extra = Math.max(0, currentLevel - PrinceLevelConfig.MAX_LEVEL_FOR_PRESTIGE);
        return BASE_CROWNS + extra * CROWNS_PER_EXTRA_LEVEL;
    }

    /** Called once by EventManager.performPrestigeReset() — credits Crowns and bumps the counter. */
    public void creditPrestige(int currentLevel) {
        int earned = earnedCrowns(currentLevel);
        crowns += earned;
        prestigeCount++;
        Gdx.app.log(TAG, "Prestige #" + prestigeCount + " — earned " + earned
            + " Crowns (total: " + crowns + ")");
    }

    // ══════════════════════════════════════════════════════════════
    // UPGRADES
    // ══════════════════════════════════════════════════════════════

    public int getTier(String upgradeId) {
        return upgradeTiers.getOrDefault(upgradeId, 0);
    }

    private int getMaxTier(String upgradeId) {
        switch (upgradeId) {
            case GEN_RATE:        return GEN_MAX_TIER;
            case BUILD_COST:      return COST_MAX_TIER;
            case SPAWN_ODDS:      return SPAWN_MAX_TIER;
            case START_LEVEL:     return START_MAX_TIER;
            case EQUIPMENT_SLOTS: return EQUIP_MAX_TIER;
            default: return 0;
        }
    }

    /** Crown cost to buy the NEXT tier, or -1 if already at max tier / unknown id. */
    public int getNextTierCost(String upgradeId) {
        int nextTier = getTier(upgradeId) + 1;
        if (nextTier > getMaxTier(upgradeId)) return -1;

        int[] costs;
        switch (upgradeId) {
            case GEN_RATE:        costs = GEN_COSTS;   break;
            case BUILD_COST:      costs = COST_COSTS;  break;
            case SPAWN_ODDS:      costs = SPAWN_COSTS; break;
            case START_LEVEL:     costs = START_COSTS; break;
            case EQUIPMENT_SLOTS: costs = EQUIP_COSTS; break;
            default: return -1;
        }
        return costs[nextTier - 1];
    }

    /** Attempts to purchase the next tier of the given upgrade. Returns true on success. */
    public boolean purchase(String upgradeId) {
        int cost = getNextTierCost(upgradeId);
        if (cost < 0) {
            Gdx.app.log(TAG, "purchase: " + upgradeId + " already at max tier");
            return false;
        }
        if (!canAfford(cost)) {
            Gdx.app.log(TAG, "purchase: not enough Crowns for " + upgradeId
                + " (need " + cost + ", have " + crowns + ")");
            return false;
        }

        crowns -= cost;
        upgradeTiers.put(upgradeId, getTier(upgradeId) + 1);
        Gdx.app.log(TAG, "Purchased " + upgradeId + " tier " + getTier(upgradeId)
            + " for " + cost + " Crowns");
        return true;
    }

    // ── Point-of-use multiplier getters (mirrors RuneSystem.getBoosted*()) ──

    public float getResourceGenMultiplier() {
        return 1f + getTier(GEN_RATE) * GEN_PER_TIER;
    }

    public int getDiscountedBuildCost(int baseCost) {
        float multiplier = 1f - getTier(BUILD_COST) * COST_PER_TIER;
        return Math.round(baseCost * multiplier);
    }

    /** Exposed so BuildMenu can fold this into its price-cache staleness check. */
    public int getBuildCostTier() {
        return getTier(BUILD_COST);
    }

    public float getSpawnBias() {
        return getTier(SPAWN_ODDS) * SPAWN_PER_TIER;
    }

    public int getStartLevel() {
        return START_LEVEL_BY_TIER[getTier(START_LEVEL)];
    }

    public int getEquipmentSlotCount() {
        return 1 + getTier(EQUIPMENT_SLOTS); // 1 free base slot
    }

    // ══════════════════════════════════════════════════════════════
    // HEIRLOOM VAULT (protected equipment)
    // ══════════════════════════════════════════════════════════════

    public Set<String> getProtectedItemIds() { return protectedItemIds; }

    public boolean isProtected(String itemId) {
        return protectedItemIds.contains(itemId);
    }

    /**
     * Toggles whether an item is protected. Returns true if the toggle
     * succeeded (added or removed); false if adding was blocked (item
     * missing, consumable, or the slot count is already full).
     */
    public boolean toggleProtected(String itemId) {
        if (protectedItemIds.contains(itemId)) {
            protectedItemIds.remove(itemId);
            return true;
        }

        Item item = eventManager.getInventory().getItem(itemId);
        if (item == null) {
            Gdx.app.log(TAG, "toggleProtected: item not found id=" + itemId);
            return false;
        }
        if (item.isConsumable()) {
            Gdx.app.log(TAG, "toggleProtected: cannot protect consumable " + item.getName());
            return false;
        }
        if (protectedItemIds.size() >= getEquipmentSlotCount()) {
            Gdx.app.log(TAG, "toggleProtected: no free Heirloom Vault slots");
            return false;
        }

        protectedItemIds.add(itemId);
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    // SAVE / LOAD
    // ══════════════════════════════════════════════════════════════

    public int[] getSaveState() { return new int[]{ crowns, prestigeCount }; }

    public void setSaveState(int[] state) {
        if (state != null && state.length >= 2) {
            crowns = state[0];
            prestigeCount = state[1];
        }
    }

    public Map<String, Integer> getUpgradeTiers() { return upgradeTiers; }

    public void setUpgradeTiers(Map<String, Integer> tiers) {
        upgradeTiers.clear();
        if (tiers != null) upgradeTiers.putAll(tiers);
    }

    public void setProtectedItemIds(Set<String> ids) {
        protectedItemIds.clear();
        if (ids != null) protectedItemIds.addAll(ids);
    }
}
