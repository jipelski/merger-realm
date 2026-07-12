package com.jipelski.mergerrealm.util;

import com.badlogic.gdx.Gdx;

import com.jipelski.mergerrealm.data.GenData;
import com.jipelski.mergerrealm.model.FacilitySpawnConfiguration;

import java.util.ArrayList;

/**
 * Manages the 7-day escalating login streak (days 1-6 roll a common table,
 * day 7 is a guaranteed unit scaled to Prince level). Folds in the Gold
 * bonus that used to live standalone in GoldManager.onDailyLogin() — this is
 * now the single daily system.
 *
 * Streak state is derived from two persisted fields (lastClaimMs,
 * lastClaimDay) rather than storing "claimed today" directly — see
 * getDayToClaim()/isClaimAvailable().
 *
 * Reward tables (assets/data/daily_login_config.json, keys daily_common/
 * daily_day7) are rolled via the shared WeightedRoll utility, exactly like
 * Chest.spawn() rolls chest_config.json. Grid-object rewards are delivered
 * via the same "spawn at closest empty cell, else queue via Wall Gate"
 * pattern ExplorationManager.collectResults() already uses for exploration
 * finds — a full grid never loses a reward.
 */
public class DailyLoginManager {

    private static final String TAG = "DailyLoginManager";

    public static final long DAY_MS = 24L * 60 * 60 * 1000;
    public static final int STREAK_LENGTH = 7;

    // Gold granted per streak day (index 0 = day 1); day 7 is the largest.
    private static final int[] DAY_GOLD = {2, 2, 3, 3, 4, 5, 15};

    // Day-7 reward level scales with Prince level: 1 + level/DIVISOR, capped.
    private static final int DAY7_LEVEL_DIVISOR = 10;
    private static final int DAY7_LEVEL_CAP = 3;

    // ── State ──
    private long lastClaimMs = 0;
    private int lastClaimDay = 0; // 0 = never claimed

    private final EventManager eventManager;

    public DailyLoginManager(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    // ══════════════════════════════════════════════════════════════
    // STREAK STATE
    // ══════════════════════════════════════════════════════════════

    /** True if never claimed, or 24h+ have elapsed since the last claim. */
    public boolean isClaimAvailable() {
        if (lastClaimMs <= 0) return true;
        return System.currentTimeMillis() - lastClaimMs >= DAY_MS;
    }

    public boolean isTodayClaimed() {
        return !isClaimAvailable();
    }

    /**
     * Which streak day (1-7) would be granted if the player claimed right
     * now. Continues the streak within the 24h-48h window, resets to day 1
     * once the streak has lapsed (48h+) or on a first-ever claim.
     */
    public int getDayToClaim() {
        if (lastClaimMs <= 0) return 1;

        long elapsed = System.currentTimeMillis() - lastClaimMs;
        if (elapsed < DAY_MS) return lastClaimDay; // already claimed today
        if (elapsed < 2 * DAY_MS) return (lastClaimDay % STREAK_LENGTH) + 1;
        return 1; // streak lapsed
    }

    public int getLastClaimDay() { return lastClaimDay; }

    // ══════════════════════════════════════════════════════════════
    // CLAIM
    // ══════════════════════════════════════════════════════════════

    /** Result of a single claim() call, for the UI to display. */
    public static class DailyReward {
        public final int day;
        public final int gold;
        public final String rewardType;
        public final int rewardLevel;
        public final boolean queued;

        DailyReward(int day, int gold, String rewardType, int rewardLevel, boolean queued) {
            this.day = day;
            this.gold = gold;
            this.rewardType = rewardType;
            this.rewardLevel = rewardLevel;
            this.queued = queued;
        }
    }

    /**
     * Claims today's reward. Returns null if isClaimAvailable() is false —
     * callers must check first (mirrors GoldManager.spendGold()'s
     * guard-then-act convention).
     */
    public DailyReward claim() {
        if (!isClaimAvailable()) return null;

        int day = getDayToClaim();

        int gold = DAY_GOLD[day - 1];
        eventManager.getGoldManager().addGold(gold, "daily_login_day" + day);

        String configKey = (day == STREAK_LENGTH) ? "daily_day7" : "daily_common";
        ArrayList<FacilitySpawnConfiguration> table =
            eventManager.getGameDataLoader().getSpawnConfiguration(configKey);

        String rewardType = null;
        int rewardLevel = 1;
        boolean queued = false;

        if (table != null && !table.isEmpty()) {
            WeightedRoll.validateProbabilitiesSumToOne(table, FacilitySpawnConfiguration::getSpawnProbability);
            FacilitySpawnConfiguration picked =
                WeightedRoll.weightedPick(table, FacilitySpawnConfiguration::getSpawnProbability);

            rewardType = picked.getUnitType();
            rewardLevel = (day == STREAK_LENGTH)
                ? Math.min(1 + eventManager.getBattleFieldManager().getLevel() / DAY7_LEVEL_DIVISOR, DAY7_LEVEL_CAP)
                : picked.getUnitLVL();

            int[] cell = eventManager.getGridInstance().getClosestEmptyCell(0, 0);
            if (cell != null) {
                eventManager.spawnObject(rewardType, rewardLevel, cell[0], cell[1]);
            } else {
                eventManager.getBattleFieldManager().addToQueue(
                    new GenData(rewardType, "Daily login reward", rewardLevel));
                queued = true;
            }
        } else {
            Gdx.app.log(TAG, "claim: no reward table for key=" + configKey);
        }

        lastClaimDay = day;
        lastClaimMs = System.currentTimeMillis();

        Gdx.app.log(TAG, "Daily login day " + day + " claimed: +" + gold + " Gold"
            + (rewardType != null ? ", " + rewardType + " lv" + rewardLevel
                + (queued ? " (queued)" : "") : ""));

        return new DailyReward(day, gold, rewardType, rewardLevel, queued);
    }

    // ══════════════════════════════════════════════════════════════
    // SAVE / LOAD
    // ══════════════════════════════════════════════════════════════

    public long getLastClaimMs() { return lastClaimMs; }
    public void setLastClaimMs(long ms) { this.lastClaimMs = ms; }

    public int getSavedLastClaimDay() { return lastClaimDay; }
    public void setLastClaimDay(int day) { this.lastClaimDay = day; }
}
