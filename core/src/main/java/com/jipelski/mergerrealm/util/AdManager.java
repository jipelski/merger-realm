package com.jipelski.mergerrealm.util;

import com.badlogic.gdx.Gdx;

import java.util.Arrays;

/**
 * Tracks daily "watch an ad" usage for the 4 gameplay actions that also have
 * an existing Gold-cost path (see GoldManager: fillResources/
 * reviveRaidParty/instantPeriodicSpawn/speedUpExploration). Each action gets
 * its own MAX_PER_DAY-count budget that resets on a rolling 24h window — the
 * same idiom TrophyShop uses for its daily stock refresh and
 * DailyLoginManager uses for its claim window (NOT a calendar/UTC-midnight
 * reset).
 *
 * There is no real ad SDK integrated yet — watchAd() grants the reward
 * immediately, as a placeholder. See the TODO in watchAd() for where a real
 * rewarded-ad callback belongs; ui/AdRewardPopup.java is the matching UI-side
 * "Watch Ad" button that calls this method.
 *
 * A watch is only ever counted against the daily budget if the underlying
 * action actually succeeds — mirrors how the Gold paths refund instead of
 * charging for a no-op (e.g. reviving with no active raid). See watchAd().
 */
public class AdManager {

    private static final String TAG = "AdManager";

    public static final int MAX_PER_DAY = 5;
    public static final long DAY_MS = 24L * 60 * 60 * 1000;

    public enum AdAction { FILL_RESOURCES, REVIVE_PARTY, INSTANT_SPAWN, SPEED_EXPLORATION }

    private static final int ACTION_COUNT = AdAction.values().length;

    // ── State ──
    private long windowStartMs = 0; // 0 = window not yet started
    private final int[] counts = new int[ACTION_COUNT];

    private final EventManager eventManager;

    public AdManager(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    // ══════════════════════════════════════════════════════════════
    // DAILY WINDOW
    // ══════════════════════════════════════════════════════════════

    /**
     * Rolls the window forward and zeroes all counts once 24h have elapsed
     * since it started. Cheap no-op within the window, so canWatch()/
     * getRemaining()/watchAd() all call this lazily — callers never need to
     * remember to reset anything themselves. MergerRealmGame also calls this
     * explicitly on cold launch/resume (belt-and-suspenders, matching the
     * processOfflineProgress()/checkDailyLoginPopup() convention).
     */
    public void checkDailyReset() {
        long now = eventManager.getServerTimeManager().getTrustedTimeMillis();
        if (windowStartMs == 0) {
            windowStartMs = now;
            return;
        }
        if (now - windowStartMs >= DAY_MS) {
            windowStartMs = now;
            Arrays.fill(counts, 0);
            Gdx.app.log(TAG, "Daily ad-watch budget reset");
        }
    }

    public boolean canWatch(AdAction action) {
        checkDailyReset();
        return counts[action.ordinal()] < MAX_PER_DAY;
    }

    public int getRemaining(AdAction action) {
        checkDailyReset();
        return Math.max(0, MAX_PER_DAY - counts[action.ordinal()]);
    }

    // ══════════════════════════════════════════════════════════════
    // WATCH
    // ══════════════════════════════════════════════════════════════

    /**
     * Grants the given action's effect for free, as if a rewarded ad had
     * just been watched. Returns true if the daily budget allowed it AND the
     * underlying effect actually succeeded — a watch is never counted
     * against the budget for a no-op (reviving with no active raid, speeding
     * up a non-returning slot, instant-spawning a facility that no longer
     * exists).
     *
     * slotIndex is used by SPEED_EXPLORATION only; facilityId by
     * INSTANT_SPAWN only. Pass -1 / null for the other actions.
     */
    public boolean watchAd(AdAction action, int slotIndex, String facilityId) {
        if (!canWatch(action)) return false;

        // TODO: a real rewarded-ad SDK call belongs here (play the ad, only
        // proceed on its "reward earned" callback). This placeholder grants
        // immediately — see ui/AdRewardPopup.java's Watch Ad button, which
        // calls this method right where the ad-complete callback will go.
        GoldManager gm = eventManager.getGoldManager();
        boolean granted;
        switch (action) {
            case FILL_RESOURCES:
                gm.doFillResources();
                granted = true; // always succeeds, nothing to validate
                break;
            case REVIVE_PARTY:
                granted = gm.doReviveRaidParty() > 0;
                break;
            case INSTANT_SPAWN:
                granted = gm.doInstantPeriodicSpawn(facilityId);
                break;
            case SPEED_EXPLORATION:
                granted = gm.doSpeedUpExploration(slotIndex);
                break;
            default:
                granted = false;
        }

        if (granted) {
            counts[action.ordinal()]++;
            Gdx.app.log(TAG, "Ad watched for " + action
                + " (" + counts[action.ordinal()] + "/" + MAX_PER_DAY + " today)");
        }
        return granted;
    }

    // ══════════════════════════════════════════════════════════════
    // SAVE / LOAD
    // ══════════════════════════════════════════════════════════════

    /**
     * { windowStartHigh32, windowStartLow32, count0, count1, count2, count3 }
     * — long-into-two-ints packing, same convention as daily_login_state.
     */
    public int[] getSaveState() {
        int[] state = new int[2 + ACTION_COUNT];
        state[0] = (int) (windowStartMs >>> 32);
        state[1] = (int) (windowStartMs);
        System.arraycopy(counts, 0, state, 2, ACTION_COUNT);
        return state;
    }

    public void setSaveState(int[] state) {
        if (state == null || state.length < 2 + ACTION_COUNT) return;
        windowStartMs = ((long) state[0] << 32) | (state[1] & 0xFFFFFFFFL);
        System.arraycopy(state, 2, counts, 0, ACTION_COUNT);
    }
}
