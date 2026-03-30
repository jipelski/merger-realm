package com.jipelski.mergerrealm.model;

import com.badlogic.gdx.Gdx;

import com.jipelski.mergerrealm.data.FacilityData;

import java.util.List;

public class Facility extends GameObject {

    private static final String TAG = "Facility";

    protected int buildCost1;
    protected int buildCost2;
    protected int buildCost3;
    protected int buildCost4;
    protected int tapCost1;
    protected int tapCost2;
    protected int tapCost3;
    protected int time_cost;

    // Periodic spawner state
    private transient float spawnTimer = 0f;
    private java.util.List<String[]> heldUnits = new java.util.ArrayList<>();

    private List<FacilitySpawnConfiguration> spawnRates;

    // NULL CONSTRUCTOR
    public Facility() {
        this.buildCost1 = 0;
        this.buildCost2 = 0;
        this.buildCost3 = 0;
        this.buildCost4 = 0;
        this.tapCost1   = 0;
        this.tapCost2   = 0;
        this.tapCost3   = 0;
        this.time_cost  = 0;
    }

    // FULL CONSTRUCTOR
    public Facility(String type, String id, int lvl, int maxLVL, int xPos, int yPos,
                    String sprite, String description,
                    int buildCost1, int buildCost2, int buildCost3, int buildCost4,
                    int tapCost1, int tapCost2, int tapCost3, int time_cost) {
        super(type, id, lvl, maxLVL, xPos, yPos, sprite, description);
        this.buildCost1 = buildCost1;
        this.buildCost2 = buildCost2;
        this.buildCost3 = buildCost3;
        this.buildCost4 = buildCost4;
        this.tapCost1   = tapCost1;
        this.tapCost2   = tapCost2;
        this.tapCost3   = tapCost3;
        this.time_cost  = time_cost;
    }

    // DATA STRUCTURE CONSTRUCTOR
    public Facility(FacilityData facilityData, String type, String id, int lvl, int xPos, int yPos) {
        super(type, id, lvl, facilityData.getMaxLVL(), xPos, yPos,
                facilityData.getSprite_path(), facilityData.getDescription());
        this.buildCost1 = facilityData.getBuildCost1();
        this.buildCost2 = facilityData.getBuildCost2();
        this.buildCost3 = facilityData.getBuildCost3();
        this.buildCost4 = facilityData.getBuildCost4();
        this.tapCost1   = facilityData.getTapCost1();
        this.tapCost2   = facilityData.getTapCost2();
        this.tapCost3   = facilityData.getTapCost3();
        this.time_cost  = facilityData.getTimeCost();
    }

    // GETTERS
    public int getBuildCost1() { return buildCost1; }
    public int getBuildCost2() { return buildCost2; }
    public int getBuildCost3() { return buildCost3; }
    public int getBuildCost4() { return buildCost4; }
    public int getTapCost1()   { return tapCost1;   }
    public int getTapCost2()   { return tapCost2;   }
    public int getTapCost3()   { return tapCost3;   }
    public int getTimeCost()  { return time_cost;  }
    public List<FacilitySpawnConfiguration> getSpawnRates() { return spawnRates; }

    // SETTERS
    public void setBuildCost1(int buildCost1) { this.buildCost1 = buildCost1; }
    public void setBuildCost2(int buildCost2) { this.buildCost2 = buildCost2; }
    public void setBuildCost3(int buildCost3) { this.buildCost3 = buildCost3; }
    public void setBuildCost4(int buildCost4) { this.buildCost4 = buildCost4; }
    public void setTapCost1(int tapCost1)     { this.tapCost1   = tapCost1;   }
    public void setTapCost2(int tapCost2)     { this.tapCost2   = tapCost2;   }
    public void setTapCost3(int tapCost3)     { this.tapCost3   = tapCost3;   }
    public void setTimeCost(int time_cost)    { this.time_cost  = time_cost;  }
    public void setSpawnRates(List<FacilitySpawnConfiguration> spawnRates) {
        this.spawnRates = spawnRates;
    }

    // Timer
    public float getSpawnTimer() { return spawnTimer; }
    public void setSpawnTimer(float t) { this.spawnTimer = t; }
    public void addSpawnTime(float delta) { this.spawnTimer += delta; }
    public void resetSpawnTimer() { this.spawnTimer = 0f; }

    // METHODS

    /**
     * Selects a random unit to spawn based on weighted probabilities.
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

        double randomValue          = Math.random();
        double cumulativeProbability = 0.0;

        for (FacilitySpawnConfiguration config : spawnRates) {
            cumulativeProbability += config.getSpawnProbability();
            // Use < not <= so randomValue == 1.0 is caught by the fallback
            if (randomValue < cumulativeProbability) {
                return new String[]{config.getUnitType(), String.valueOf(config.getUnitLVL())};
            }
        }

        // Fallback for floating point edge case — return last entry rather than null
        FacilitySpawnConfiguration last = spawnRates.get(spawnRates.size() - 1);
        Gdx.app.log(TAG, "spawn: floating point fallback triggered — returning last config");
        return new String[]{last.getUnitType(), String.valueOf(last.getUnitLVL())};
    }

    // Held units
    public java.util.List<String[]> getHeldUnits() { return heldUnits; }
    public int getHeldCount() { return heldUnits.size(); }

    public void addHeldUnit(String type, int level) {
        heldUnits.add(new String[]{type, String.valueOf(level)});
    }

    public String[] removeHeldUnit() {
        if (heldUnits.isEmpty()) return null;
        return heldUnits.remove(0);
    }

    public int getHoldCapacity() {
        // Level 1: 0, Level 2: 3, Level 3: 5, Level 4: 7, Level 5: 9
        if (getLvl() <= 1) return 0;
        return getLvl() * 2 - 1;
    }
}
