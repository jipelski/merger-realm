package com.example.lastresort.util;

import com.example.lastresort.data.GenData;
import com.example.lastresort.database.JsonManager;
import com.example.lastresort.grid.Grid;
import com.example.lastresort.R;

import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;

public class BattleFieldManager {
    private int level;
    private int current_xp;
    private int xp_required;
    private int width;
    private int height;
    private LinkedList<GenData>     rewardQueue;  // ArrayList
    private Map<String, Boolean>    lockedStatus;  // Key: Object || Value: BooleanLocked
    private Map<String, int[]>      globalCounter; // Key: Type || [IntCurrValue, IntMaxValue]
    private EventManager            eventManager;
    private Grid                    grid;

    public BattleFieldManager(EventManager eventManager, JsonManager jsonManager, Grid grid)
    {
        this.eventManager = eventManager;
        this.grid         = grid;
        initialiseQueue(jsonManager);
        initialiseGrid(jsonManager);
        initialiseProgression(jsonManager);
        initialiseLockedStatus(jsonManager);
        initialiseGlobalCounter(jsonManager);
    }

    private void initialiseGlobalCounter(JsonManager jsonManager) {
        this.globalCounter = jsonManager.loadGlobalCounterMap("counter_map");
    }

    private void initialiseLockedStatus(JsonManager jsonManager) {
        this.lockedStatus = jsonManager.loadStatus("status");
    }

    private void initialiseProgression(JsonManager jsonManager) {
        int[] progressionArr = jsonManager.loadArray("progression"); // [intBTLFLDlevel, intCurrXp, intXpReq]
        this.level       = progressionArr[0];
        this.current_xp  = progressionArr[1];
        this.xp_required = progressionArr[2];
    }

    private void initialiseGrid(JsonManager jsonManager) {
        int[] XoY   = jsonManager.loadArray("grid_size");
        this.width  = XoY[0];
        this.height = XoY[1];
    }

    private void initialiseQueue(JsonManager jsonManager) {
        this.rewardQueue = jsonManager.loadRewardQueue("reward_queue"); //[GenData1, GenData2...]
    }

    //GETTERS

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }

    public int getCurrent_xp() {
        return current_xp;
    }

    public int getLevel() {
        return level;
    }

    public int getXp_required() {
        return xp_required;
    }

    public LinkedList<GenData> getRewardQueue() {
        return rewardQueue;
    }

    public Map<String, Boolean> getLockedStatus() {
        return lockedStatus;
    }

    //SETTERS

    public void setHeight(int height) {
        this.height = height;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setCurrent_xp(int current_xp) {
        this.current_xp = current_xp;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void setXp_required(int xp_required) {
        this.xp_required = xp_required;
    }

    public void setRewardQueue(LinkedList<GenData> rewardQueue) {
        this.rewardQueue = rewardQueue;
    }

    public void setLockedStatus(Map<String, Boolean> lockedStatus) {
        this.lockedStatus = lockedStatus;
    }

    //METHODS
    public void addToQueue(GenData obj)
    {
        rewardQueue.addLast(obj);
    }

    public GenData getReward()
    {
        return rewardQueue.removeFirst();
    }

    public void increaseXP(int xp)
    {
        if(xp+current_xp >= xp_required)
        {
            current_xp  =  0;
            level       += 1;
        }
        else
            current_xp += xp;
    }

    public boolean checkStatus(String type)
    {
        return Boolean.TRUE.equals(lockedStatus.get(type));
    }

    public void unlock(String type)
    {
        lockedStatus.replace(type, false);
    }

    public void increaseCounter(String type, int amount)
    {
        if(globalCounter.get(type)[0] + amount >= globalCounter.get(type)[1])
        {
            int[] value = new int[]{0, globalCounter.get(type)[1]};
            globalCounter.replace(type, value);
            eventManager.spawnObject(type, 1,0, 0);
        }
        else
        {
            int[] value = new int[]{globalCounter.get(type)[0] += amount, globalCounter.get(type)[1]};
            globalCounter.replace(type, value);
        }
    }
}