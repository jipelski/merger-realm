package com.example.lastresort.model;

public class FacilitySpawnConfiguration {
    protected String   unitType;
    protected int      level;
    protected double   spawnProbability;

    // GETTERS

    public double getSpawnProbability() {
        return spawnProbability;
    }

    public int getUnitLVL() {
        return level;
    }

    public String getUnitType() {
        return unitType;
    }
}
