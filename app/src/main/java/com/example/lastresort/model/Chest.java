package com.example.lastresort.model;

import android.media.Image;

import com.example.lastresort.data.ChestData;

import java.util.List;

public class Chest extends GameObject{
    protected String token_type;
    protected int    tap_count;

    //protected ChestConfiguration config;

    // NULL CONSTRUCTOR
    public Chest()
    {
        this.token_type = null;
        this.tap_count  = 0;
    }

    // CONSTRUCTOR
    public Chest(String type, String id, int lvl, int maxLvl, int xPos, int yPos, String sprite, String description, String token_type, int tap_count)
    {
        super(type, id, lvl, maxLvl, xPos, yPos, sprite, description);
        this.token_type = token_type;
        this.tap_count  = tap_count;
    }

    //DATASTRUCTURE CONSTRUCTOR
    public Chest(ChestData chestData, String type, String id, int lvl, int xPos, int yPos)
    {
        super(type, id, lvl, chestData.getMaxLVL(), xPos, yPos, chestData.getSprite_path(), chestData.getDescription());
        this.token_type = chestData.getToken_type();
        this.tap_count  = chestData.getTap_count();
    }

    // GETTERS
    public String getToken_type() {
        return token_type;
    }
    public int getTap_count() {
        return tap_count;
    }

    // SETTERS
    public void setToken_type(String token_type) {
        this.token_type = token_type;
    }
    public void setTap_count(int tap_count) {
        this.tap_count = tap_count;
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

    public boolean decreaseTap_Count() {
        this.tap_count -=1;
        return tap_count <= 0;
    }
}
