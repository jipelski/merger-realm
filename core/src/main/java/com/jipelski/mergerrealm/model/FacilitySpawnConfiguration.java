package com.jipelski.mergerrealm.model;



public class FacilitySpawnConfiguration {

    // Fields must remain accessible to Gson for deserialization —
    // private fields work fine as Gson uses reflection
    private String unitType;
    private int    level;
    private double spawnProbability;

    // No-args constructor required by Gson
    public FacilitySpawnConfiguration() {}

    // Constructor for creating configurations in code or tests
    public FacilitySpawnConfiguration(String unitType, int level, double spawnProbability) {
        this.unitType         = unitType;
        this.level            = level;
        this.spawnProbability = spawnProbability;
    }

    // GETTERS
    public String getUnitType()        { return unitType;         }
    public int    getUnitLVL()         { return level;            }
    public double getSpawnProbability(){ return spawnProbability; }

    // SETTERS
    public void setUnitType(String unitType)             { this.unitType         = unitType;         }
    public void setLevel(int level)                      { this.level            = level;            }
    public void setSpawnProbability(double spawnProbability) { this.spawnProbability = spawnProbability; }


    @Override
    public String toString() {
        return "FacilitySpawnConfiguration{unitType='" + unitType
                + "', level=" + level
                + ", spawnProbability=" + spawnProbability + "}";
    }
}
