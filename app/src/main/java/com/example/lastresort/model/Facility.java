package com.example.lastresort.model;

import android.media.Image;

import com.example.lastresort.data.FacilityData;
import com.example.lastresort.data.UnitData;
import com.example.lastresort.util.GameDataLoader;
import com.example.lastresort.util.UUIDGenerator;

import java.util.ArrayList;
import java.util.List;

public class Facility extends GameObject{
    protected int       buildCost1;
    protected int       buildCost2;
    protected int       buildCost3;
    protected int       buildCost4;
    protected int       tapCost1;
    protected int       tapCost2;
    protected int       tapCost3;

    private List<FacilitySpawnConfiguration> spawnRates;

    // NULL CONSTRUCTOR
    public Facility()
    {
        this.buildCost1      = 0;
        this.buildCost2      = 0;
        this.buildCost3      = 0;
        this.tapCost1        = 0;
        this.tapCost2        = 0;
        this.tapCost3        = 0;
    }
    // CONSTRUCTOR
    public Facility(String type, String id, int lvl, int maxLVL, int xPos, int yPos, String sprite, String description, int buildCost1, int buildCost2, int buildCost3, int buildCost4, int tapCost1, int tapCost2, int tapCost3)
    {
        super(type, id, lvl, maxLVL, xPos, yPos, sprite, description);
        this.buildCost1      = buildCost1;
        this.buildCost2      = buildCost2;
        this.buildCost3      = buildCost3;
        this.buildCost4      = buildCost4;
        this.tapCost1        = tapCost1;
        this.tapCost2        = tapCost2;
        this.tapCost3        = tapCost3;
    }

    // DATA STRUCTURE CONSTRUCTOR
    public Facility(FacilityData facilityData, String type, String id, int lvl, int xPos, int yPos)
    {
        super(type, id, lvl, facilityData.getMaxLVL(), xPos, yPos, facilityData.getSprite_path(), facilityData.getDescription());
        this.buildCost1      = facilityData.getBuildCost1();
        this.buildCost2      = facilityData.getBuildCost2();
        this.buildCost3      = facilityData.getBuildCost3();
        this.buildCost4      = facilityData.getBuildCost4();
        this.tapCost1        = facilityData.getTapCost1();
        this.tapCost2        = facilityData.getTapCost2();
        this.tapCost3        = facilityData.getTapCost3();
    }

    //  GETTERS
    public int getBuildCost1()
    {
        return buildCost1;
    }
    public int getBuildCost2()
    {
        return buildCost2;
    }
    public int getBuildCost3()
    {
        return buildCost3;
    }
    public int getBuildCost4()
    {
        return buildCost4;
    }
    public int getTapCost1() {
        return tapCost1;
    }
    public int getTapCost2() {
        return tapCost2;
    }
    public int getTapCost3() {
        return tapCost3;
    }
    public List<FacilitySpawnConfiguration> getSpawnRates() {
        return spawnRates;
    }

    // SETTERS
    public void setBuildCost1(int buildCost1){
        this.buildCost1 = buildCost1;
    }
    public void setBuildCost2(int buildCost2){
        this.buildCost2 = buildCost2;
    }
    public void setBuildCost3(int buildCost3){
        this.buildCost3 = buildCost3;
    }
    public void setBuildCost4(int buildCost4){
        this.buildCost4 = buildCost4;
    }
    public void setTapCost1(int tapCost1){
        this.tapCost1 = tapCost1;
    }
    public void setTapCost2(int tapCost2){
        this.tapCost2 = tapCost2;
    }
    public void setTapCost3(int tapCost3){
        this.tapCost3 = tapCost3;
    }
    public void setSpawnRates(List<FacilitySpawnConfiguration> spawnRates) {
        this.spawnRates = spawnRates;
    }

    // METHODS
    public String[] spawn(List<FacilitySpawnConfiguration> spawnRates)
    {
        double totalProbability = spawnRates.stream()
                                            .mapToDouble(FacilitySpawnConfiguration::getSpawnProbability)
                                            .sum();

        if (Math.abs(totalProbability - 1.0) > 0.001)
        {
            throw new IllegalArgumentException("Spawn probabilities do not add up to 1.0");
        }

        double randomValue = Math.random();
        double cumulativeProbability = 0.0;

        for (FacilitySpawnConfiguration config : spawnRates) {
            cumulativeProbability += config.spawnProbability;
            if (randomValue <= cumulativeProbability) {

                String unitType = config.getUnitType();
                int unitLVL  = config.getUnitLVL();

                return new String[]{unitType, String.valueOf(unitLVL)};
            }
        }

        //Handling exceptions
        return null;
    }
}
