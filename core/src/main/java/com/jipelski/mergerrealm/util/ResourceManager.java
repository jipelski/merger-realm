package com.jipelski.mergerrealm.util;

import com.badlogic.gdx.Gdx;

import com.jipelski.mergerrealm.database.JsonManager;

import java.util.HashMap;
import java.util.Map;

public class ResourceManager {

    private static final String TAG = "ResourceManager";

    // Key: resource name | Value: int[3] { amount, genRate, poolSize }
    private Map<String, int[]> consumableMap;

    public ResourceManager(JsonManager jsonManager) {
        consumableMap = jsonManager.loadConsumableMap("consumable_map");
        if (consumableMap == null) {
            Gdx.app.log(TAG, "consumable_map missing or corrupt — starting with empty resource map");
            consumableMap = new HashMap<>();
        }
    }

    // GETTERS

    public Map<String, int[]> getConsumableMap() {
        return consumableMap;
    }

    public int getAmount(String resource) {
        int[] entry = consumableMap.get(resource);
        if (entry == null) { Gdx.app.log(TAG, "getAmount: unknown resource=" + resource); return 0; }
        return entry[0];
    }

    public int getRate(String resource) {
        int[] entry = consumableMap.get(resource);
        if (entry == null) { Gdx.app.log(TAG, "getRate: unknown resource=" + resource); return 0; }
        return entry[1];
    }

    public int getPoolSize(String resource) {
        int[] entry = consumableMap.get(resource);
        if (entry == null) { Gdx.app.log(TAG, "getPoolSize: unknown resource=" + resource); return 0; }
        return entry[2];
    }

    // SETTERS
    // These are accumulators (+=), not absolute assignments.
    // Renamed from setAmount/setRate/setPoolSize to addAmount/addRate/addPoolSize
    // to match what they actually do.

    public void setConsumableMap(Map<String, int[]> consumableMap) {
        this.consumableMap = consumableMap;
    }

    public void addAmount(String resource, int amount) {
        int[] entry = consumableMap.get(resource);
        if (entry == null) { Gdx.app.log(TAG, "addAmount: unknown resource=" + resource); return; }
        entry[0] += amount;
    }

    public void addRate(String resource, int amount) {
        int[] entry = consumableMap.get(resource);
        if (entry == null) { Gdx.app.log(TAG, "addRate: unknown resource=" + resource); return; }
        entry[1] += amount;
    }

    public void addPoolSize(String resource, int amount) {
        int[] entry = consumableMap.get(resource);
        if (entry == null) { Gdx.app.log(TAG, "addPoolSize: unknown resource=" + resource); return; }
        entry[2] += amount;
    }

    // METHODS

    /**
     * Ticks resource generation. Adds gen rate to amount, capped at pool size.
     * Only applies to timber, quarrystone, and iron.
     */
    public void updateResources() {
        for (String t : consumableMap.keySet()) {
            if (t.equals("timber") || t.equals("quarrystone") || t.equals("iron")) {
                int[] value = consumableMap.get(t);
                if (value == null) continue;
                value[0] = Math.min(value[0] + value[1], value[2]);
            }
        }
    }

    /**
     * Deducts timber, quarrystone, and iron if all three are available.
     * Returns true on success, false if any resource is insufficient.
     */
    public boolean reduceResource(int amount1, int amount2, int amount3) {
        int[] timber      = consumableMap.get("timber");
        int[] quarrystone = consumableMap.get("quarrystone");
        int[] iron        = consumableMap.get("iron");

        if (timber == null || quarrystone == null || iron == null) {
            Gdx.app.log(TAG, "reduceResource: one or more resource entries missing");
            return false;
        }

        if (timber[0] >= amount1 && quarrystone[0] >= amount2 && iron[0] >= amount3) {
            timber[0]      -= amount1;
            quarrystone[0] -= amount2;
            iron[0]        -= amount3;
            return true;
        }
        return false;
    }

    /**
     * Modifies the resource pool size. If the pool shrinks,
     * clamps the current amount to the new cap.
     */
    public void modifyResourcePoolSize(String resource, int amount, boolean increase) {
        int[] value = consumableMap.get(resource);
        if (value == null) {
            Gdx.app.log(TAG, "modifyResourcePoolSize: unknown resource=" + resource);
            return;
        }
        value[2] = increase ? value[2] + amount : value[2] - amount;
        // Clamp current amount if it now exceeds the new cap
        if (value[2] > 0 && value[0] > value[2]) {
            value[0] = value[2];
            Gdx.app.log(TAG, resource + " clamped to new cap: " + value[2]);
        }
    }

    public void modifyResourceRate(String resource, int amount, boolean increase) {
        if ("nothing".equals(resource)) return;
        int[] value = consumableMap.get(resource);
        if (value == null) { Gdx.app.log(TAG, "modifyResourceRate: unknown resource=" + resource); return; }
        value[1] = increase ? value[1] + amount : value[1] - amount;
    }

    public void increaseTokens(int amount1, int amount2, int amount3, int amount4) {
        addToToken("wood",  amount1);
        addToToken("wheat", amount2);
        addToToken("stone", amount3);
        addToToken("fire",  amount4);
    }

    public void increaseToken(String tokenType, int amount) {
        addToToken(tokenType, amount);
    }

    /**
     * Deducts wood, wheat, stone, and fire tokens if all four are available.
     * Returns true on success, false if any token is insufficient.
     * Previously wrote value3 into the "fire" slot instead of value4 — fixed.
     */
    public boolean decreaseTokens(int amount1, int amount2, int amount3, int amount4) {
        int[] wood  = consumableMap.get("wood");
        int[] wheat = consumableMap.get("wheat");
        int[] stone = consumableMap.get("stone");
        int[] fire  = consumableMap.get("fire");

        if (wood == null || wheat == null || stone == null || fire == null) {
            Gdx.app.log(TAG, "decreaseTokens: one or more token entries missing");
            return false;
        }

        if (wood[0] >= amount1 && wheat[0] >= amount2
                && stone[0] >= amount3 && fire[0] >= amount4) {
            wood[0]  -= amount1;
            wheat[0] -= amount2;
            stone[0] -= amount3;
            fire[0]  -= amount4; // was: value3 (stone's array) — now correctly value4
            return true;
        }
        return false;
    }

    // HELPERS

    private void addToToken(String tokenType, int amount) {
        int[] value = consumableMap.get(tokenType);
        if (value == null) { Gdx.app.log(TAG, "addToToken: unknown token=" + tokenType); return; }
        value[0] += amount;
    }
}
