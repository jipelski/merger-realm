package com.jipelski.mergerrealm.model;

import com.badlogic.gdx.Gdx;

import com.jipelski.mergerrealm.data.ChestData;

import java.util.List;

public class Chest extends GameObject {

    private static final String TAG = "Chest";

    protected String token_type;
    protected int    tap_count;

    // NULL CONSTRUCTOR
    public Chest() {
        this.token_type = null;
        this.tap_count  = 0;
    }

    // FULL CONSTRUCTOR
    public Chest(String type, String id, int lvl, int maxLvl, int xPos, int yPos,
                 String sprite, String description, String token_type, int tap_count) {
        super(type, id, lvl, maxLvl, xPos, yPos, sprite, description);
        this.token_type = token_type;
        this.tap_count  = tap_count;
    }

    // DATA STRUCTURE CONSTRUCTOR
    public Chest(ChestData chestData, String type, String id, int lvl, int xPos, int yPos) {
        super(type, id, lvl, chestData.getMaxLVL(), xPos, yPos,
                chestData.getSprite_path(), chestData.getDescription());
        this.token_type = chestData.getToken_type();
        this.tap_count  = chestData.getTap_count();
    }

    // GETTERS
    public String getToken_type()  { return token_type; }
    public int    getTap_count()   { return tap_count;  }

    // SETTERS
    public void setToken_type(String token_type) { this.token_type = token_type; }
    public void setTap_count(int tap_count)      { this.tap_count  = tap_count;  }

    // METHODS

    /**
     * Selects a random token to spawn based on weighted probabilities.
     * Returns a String[] of { unitType, level } or null if spawnRates is empty.
     * Throws IllegalArgumentException if probabilities do not sum to ~1.0.
     */
    public String[] spawn(List<FacilitySpawnConfiguration> spawnRates) {
        if (spawnRates == null || spawnRates.isEmpty()) {
            Gdx.app.log(TAG, "spawn: spawnRates is null or empty");
            return null;
        }

        double totalProbability = spawnRates.stream()
                .mapToDouble(FacilitySpawnConfiguration::getSpawnProbability)
                .sum();

        if (Math.abs(totalProbability - 1.0) > 0.001) {
            throw new IllegalArgumentException(
                    "Spawn probabilities sum to " + totalProbability + ", expected 1.0");
        }

        double randomValue         = Math.random();
        double cumulativeProbability = 0.0;

        for (FacilitySpawnConfiguration config : spawnRates) {
            cumulativeProbability += config.getSpawnProbability();
            // Use < not <= so that randomValue == 1.0 still selects the last item
            // via the fallback below rather than never matching
            if (randomValue < cumulativeProbability) {
                return new String[]{config.getUnitType(), String.valueOf(config.getUnitLVL())};
            }
        }

        // Fallback for floating point edge case where cumulative sum lands just
        // below 1.0 — return the last entry rather than null
        FacilitySpawnConfiguration last = spawnRates.get(spawnRates.size() - 1);
        Gdx.app.log(TAG, "spawn: floating point fallback triggered — returning last config");
        return new String[]{last.getUnitType(), String.valueOf(last.getUnitLVL())};
    }

    /**
     * Decrements tap count by one.
     * Returns true when the chest is exhausted (tap_count reaches 0).
     */
    public boolean decreaseTap_Count() {
        this.tap_count -= 1;
        return tap_count <= 0;
    }
}
