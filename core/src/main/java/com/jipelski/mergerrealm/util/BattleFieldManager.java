package com.jipelski.mergerrealm.util;

import com.badlogic.gdx.Gdx;

import com.jipelski.mergerrealm.data.GenData;
import com.jipelski.mergerrealm.database.JsonManager;
import com.jipelski.mergerrealm.grid.Grid;

import java.util.LinkedList;
import java.util.Map;

public class BattleFieldManager {

    private static final String TAG = "BattleFieldManager";

    private int level;
    private int current_xp;
    private int xp_required;

    private LinkedList<GenData>  rewardQueue;
    private Map<String, Boolean> lockedStatus;
    private Map<String, int[]>   globalCounter;

    private final EventManager eventManager;
    private final Grid         grid;
    private final GridObjectManager gridObjectManager;

    public Map<String, int[]> getGlobalCounter() { return globalCounter; }

    public BattleFieldManager(EventManager eventManager, JsonManager jsonManager,
                              Grid grid, GridObjectManager gridObjectManager) {
        this.eventManager = eventManager;
        this.grid         = grid;
        this.gridObjectManager = gridObjectManager;
        initialiseQueue(jsonManager);
        initialiseProgression(jsonManager);
        initialiseLockedStatus(jsonManager);
        initialiseGlobalCounter(jsonManager);
    }
    private void initialiseQueue(JsonManager jsonManager) {
        rewardQueue = jsonManager.loadRewardQueue("reward_queue");
        if (rewardQueue == null) {
            Gdx.app.log(TAG, "reward_queue missing — starting with empty queue");
            rewardQueue = new LinkedList<>();
        }
    }

    private void initialiseProgression(JsonManager jsonManager) {
        int[] progressionArr = jsonManager.loadArray("progression");
        if (progressionArr == null || progressionArr.length < 3) {
            Gdx.app.log(TAG, "progression data missing or corrupt — defaulting to level 1");
            this.level       = 1;
            this.current_xp  = 0;
            this.xp_required = 100;
        } else {
            this.level       = progressionArr[0];
            this.current_xp  = progressionArr[1];
            this.xp_required = progressionArr[2];
        }
    }

    private void initialiseLockedStatus(JsonManager jsonManager) {
        lockedStatus = jsonManager.loadStatus("status");
        if (lockedStatus == null) {
            Gdx.app.log(TAG, "status data missing — all objects will appear unlocked");
            lockedStatus = new java.util.HashMap<>();
        }
    }

    private void initialiseGlobalCounter(JsonManager jsonManager) {
        globalCounter = jsonManager.loadGlobalCounterMap("counter_map");
        if (globalCounter == null) {
            Gdx.app.log(TAG, "counter_map missing — spawn counters will not function");
            globalCounter = new java.util.HashMap<>();
        }
    }

    // GETTERS

    public int getLevel()       { return level; }
    public int getCurrent_xp()  { return current_xp; }
    public int getXp_required() { return xp_required; }
    public int getWidth()       { return grid.getWidth(); }
    public int getHeight()      { return grid.getHeight(); }

    public LinkedList<GenData>  getRewardQueue()  { return rewardQueue; }
    public Map<String, Boolean> getLockedStatus() { return lockedStatus; }

    // SETTERS

    public void setLevel(int level)             { this.level = level; }
    public void setCurrent_xp(int current_xp)   { this.current_xp = current_xp; }
    public void setXp_required(int xp_required) { this.xp_required = xp_required; }
    public void setRewardQueue(LinkedList<GenData> rewardQueue) { this.rewardQueue = rewardQueue; }
    public void setLockedStatus(Map<String, Boolean> lockedStatus) { this.lockedStatus = lockedStatus; }

    // METHODS

    public void addToQueue(GenData obj) {
        rewardQueue.addLast(obj);
    }

    /**
     * Removes and returns the next reward from the queue.
     * Returns null if the queue is empty — callers must check.
     */
    public GenData getReward() {
        if (rewardQueue.isEmpty()) {
            Gdx.app.log(TAG, "getReward() called on empty queue");
            return null;
        }
        return rewardQueue.removeFirst();
    }

    /**
     * Awards XP and levels up if the threshold is reached.
     * Carries over any excess XP rather than discarding it.
     */
    public void increaseXP(int xp) {
        current_xp += xp;
        if (current_xp >= xp_required) {
            current_xp -= xp_required; // carry over excess instead of resetting to 0
            level      += 1;
            Gdx.app.log(TAG, "Level up! Now level " + level + " with " + current_xp + " carry-over XP");
        }
    }

    public boolean checkStatus(String type) {
        return Boolean.TRUE.equals(lockedStatus.get(type));
    }

    public void unlock(String type) {
        lockedStatus.replace(type, false);
    }

    /**
     * Increments the spawn counter for the given monster type.
     * Counter is frozen while a monster of that type is already on the board.
     * When the counter reaches its threshold, the monster spawns and resets.
     */
    public void increaseCounter(String type, int amount) {
        // Don't build counter if this monster type is already on the board
        if (gridObjectManager.hasObjectOfType(type)) {
            Gdx.app.log(TAG, "increaseCounter: " + type
                + " already on board — counter frozen");
            return;
        }

        int[] counter = globalCounter.get(type);
        if (counter == null || counter.length < 2) {
            Gdx.app.log(TAG, "increaseCounter: no counter entry for type=" + type);
            return;
        }

        int newValue = counter[0] + amount;
        if (newValue >= counter[1]) {
            counter[0] = 0;
            globalCounter.replace(type, counter);
            eventManager.spawnObject(type, 1, 0, 0);
            Gdx.app.log(TAG, "Counter threshold reached for " + type + " — spawning");
        } else {
            counter[0] = newValue;
            globalCounter.replace(type, counter);
            Gdx.app.log(TAG, type + " counter: " + newValue + "/" + counter[1]);
        }
    }
}
