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
     * Only applies to food, wood, and iron.
     *
     * @param genMultiplier Prince Prestige's "faster gen" multiplier (1.0 =
     *                      no bonus). Applied only here, at accumulation
     *                      time — NOT baked into the stored gen rate itself,
     *                      since that rate is a per-unit accumulator (see
     *                      RuneSystem's Fortune boost) that must stay stable
     *                      across spawn/despawn regardless of a global,
     *                      purchasable multiplier changing mid-run.
     */
    public void updateResources(float genMultiplier) {
        for (String t : consumableMap.keySet()) {
            if (t.equals("food") || t.equals("wood") || t.equals("iron")) {
                int[] value = consumableMap.get(t);
                if (value == null) continue;

                // Clamp runs unconditionally (even at 0 gain) so a resource
                // left over-cap by a storage teardown gets trimmed within one
                // tick instead of only ever being lowered here on active gen.
                int gain = value[1] > 0 ? Math.round(value[1] * genMultiplier) : 0;
                int cap = value[2] > 0 ? value[2] : 1000; // no storage built yet — base cap
                value[0] = Math.min(value[0] + gain, cap);
            }
        }
    }

    /**
     * Fills a resource to its maximum capacity.
     * Called by GoldManager.fillResources().
     */
    public void fillToMax(String resource) {
        if (consumableMap.containsKey(resource)) {
            int[] value = consumableMap.get(resource);
            if (value[2] > 0) {
                // Has a pool cap — clamp to it
                value[0] = value[2];
            } else {
                // No storage built yet — still generate but with a base cap
                value[0] = 1000;
            }
            Gdx.app.log("ResourceManager", "Filled " + resource + " to max: " + value[2]);
        }
    }

    /**
     * Adds a percentage of the resource's pool size (or the base 1000 cap when
     * no storage is built yet) to its current amount, clamped to that cap.
     * Called when a resource pouch is dismissed to the Prince.
     */
    public void fillByPercent(String resource, int percent) {
        int[] value = consumableMap.get(resource);
        if (value == null) { Gdx.app.log(TAG, "fillByPercent: unknown resource=" + resource); return; }
        int cap = value[2] > 0 ? value[2] : 1000;
        int amountToAdd = cap * percent / 100;
        value[0] = Math.min(value[0] + amountToAdd, cap);
        Gdx.app.log(TAG, "Filled " + resource + " by " + percent + "% (+" + amountToAdd + ")");
    }

    /**
     * Deducts food, wood, and iron if all three are available.
     * Returns true on success, false if any resource is insufficient.
     */
    public boolean reduceResource(int amount1, int amount2, int amount3) {
        int[] food      = consumableMap.get("food");
        int[] wood = consumableMap.get("wood");
        int[] iron        = consumableMap.get("iron");

        if (food == null || wood == null || iron == null) {
            Gdx.app.log(TAG, "reduceResource: one or more resource entries missing");
            return false;
        }

        if (food[0] >= amount1 && wood[0] >= amount2 && iron[0] >= amount3) {
            food[0]      -= amount1;
            wood[0] -= amount2;
            iron[0]        -= amount3;
            return true;
        }
        return false;
    }

    /**
     * Modifies the resource pool size (cap only — does not touch the stored
     * amount). A storage merge tears down both source buildings before
     * spawning the merged one (remove, remove, add), so clamping the stored
     * amount here would discard resources against an intermediate, not-yet-
     * final cap. Genuine over-cap teardown (e.g. dismissing a lone storage
     * building) is instead trimmed by the next updateResources() tick, which
     * clamps unconditionally.
     */
    public void modifyResourcePoolSize(String resource, int amount, boolean increase) {
        int[] value = consumableMap.get(resource);
        if (value == null) {
            Gdx.app.log(TAG, "modifyResourcePoolSize: unknown resource=" + resource);
            return;
        }
        value[2] = Math.max(0, increase ? value[2] + amount : value[2] - amount);
    }

    public void modifyResourceRate(String resource, int amount, boolean increase) {
        if ("nothing".equals(resource)) return;
        int[] value = consumableMap.get(resource);
        if (value == null) { Gdx.app.log(TAG, "modifyResourceRate: unknown resource=" + resource); return; }
        value[1] = increase ? value[1] + amount : value[1] - amount;
    }

    public void increaseTokens(int amount1, int amount2, int amount3, int amount4) {
        addToToken("nail",  amount1);
        addToToken("slate", amount2);
        addToToken("ingot", amount3);
        addToToken("relic",  amount4);
    }

    public void increaseToken(String tokenType, int amount) {
        addToToken(tokenType, amount);
    }

    /**
     * Deducts nail, slate, ingot, and relic tokens if all four are available.
     * Returns true on success, false if any token is insufficient.
     * Previously wrote value3 into the "relic" slot instead of value4 — fixed.
     */
    public boolean decreaseTokens(int amount1, int amount2, int amount3, int amount4) {
        int[] nail  = consumableMap.get("nail");
        int[] slate = consumableMap.get("slate");
        int[] ingot = consumableMap.get("ingot");
        int[] relic  = consumableMap.get("relic");

        if (nail == null || slate == null || ingot == null || relic == null) {
            Gdx.app.log(TAG, "decreaseTokens: one or more token entries missing");
            return false;
        }

        if (nail[0] >= amount1 && slate[0] >= amount2
                && ingot[0] >= amount3 && relic[0] >= amount4) {
            nail[0]  -= amount1;
            slate[0] -= amount2;
            ingot[0] -= amount3;
            relic[0]  -= amount4; // was: value3 (ingot's array) — now correctly value4
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
