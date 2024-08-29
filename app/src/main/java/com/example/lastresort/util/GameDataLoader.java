package com.example.lastresort.util;

import android.util.Log;

import com.example.lastresort.R;
import com.example.lastresort.data.ChestData;
import com.example.lastresort.data.FacilityData;
import com.example.lastresort.data.GenData;
import com.example.lastresort.data.MonsterData;
import com.example.lastresort.data.PrinceData;
import com.example.lastresort.data.StorageData;
import com.example.lastresort.data.TokenData;
import com.example.lastresort.data.UnitData;
import com.example.lastresort.database.JsonManager;
import com.example.lastresort.model.FacilitySpawnConfiguration;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
//TODO: USE THIS AS THE MAPPING FOR GAMEGRIDADDAPTER for images
public class GameDataLoader {
    private Map<String, Map<Integer, GenData>> objectDataMap;
    private Map<String, ArrayList<FacilitySpawnConfiguration>> configurationMap;

    private JsonManager jsonManager;

    // NULL CONSTRUCTOR
    public GameDataLoader(JsonManager jsonManager)
    {
        this.jsonManager = jsonManager;
        initialiseData();
    }

    // METHODS

    //INSTANTIATOR


    private void initialiseData()
    {
        objectDataMap = new HashMap<>(); //<TYPE, <LEVEL, RESTOFDATA>>
        configurationMap = new HashMap<>(); //<TYPE_LEVEL, ARRAYLIST<FACILITY_SPAWN_CONFIGURATION>>
        loadUnit("unit");
        loadFacility("facility");
        loadStorage("storage");
        loadMonster("monster");
        loadChest("chest");
        loadToken("token");
        loadPrince("prince");
        loadSpawnConfiguration("facility_config");
        loadSpawnConfiguration("chest_config");

        for (Map.Entry<String, Map<Integer, GenData>> entry : objectDataMap.entrySet()) {
            String type = entry.getKey(); // This is your unit type.
            Map<Integer, GenData> levelsMap = entry.getValue();

            if (levelsMap != null && !levelsMap.isEmpty()) {
                for (Map.Entry<Integer, GenData> levelEntry : levelsMap.entrySet()) {
                    Integer level = levelEntry.getKey();
                    GenData genData = levelEntry.getValue();

                    if (genData != null) {
                        Log.d("GameDataLoader", "Type: " + type + ", Level: " + level + ", Data: " + genData.getMaxLVL());
                    } else {
                        Log.d("GameDataLoader", "Type: " + type + ", Level: " + level + " has null GenData");
                    }
                }
            } else {
                Log.d("GameDataLoader", "Type: " + type + " has no levels or is null");
            }
        }
    }

    public Map<String, Map<Integer, GenData>> getMap()
    {
        return objectDataMap;
    }

    private void loadUnit(String path){
        Map<String, Object> jsonData = jsonManager.loadJsonData(path);

        assert jsonData != null;
        for(Map.Entry<String, Object> unitTypeData : jsonData.entrySet())
        {
            String unitType = unitTypeData.getKey();
            List<Map<String, Object>> levels = (List<Map<String, Object>>) unitTypeData.getValue();

            Map<Integer, GenData> levelMap = objectDataMap.computeIfAbsent(unitType, k -> new HashMap<>());

            for (Map<String, Object> levelInfo : levels) {
                int level = ((Double)levelInfo.get("level")).intValue();

                UnitData unitData = new UnitData(
                        (String)  levelInfo.get("sprite_path"),
                        (String)  levelInfo.get("description"),
                        ((Double) levelInfo.get("maxLVL")).intValue(),
                        (String)  levelInfo.get("resource"),
                        ((Double) levelInfo.get("gen_rate")).intValue(),
                        ((Double) levelInfo.get("damage")).intValue(),
                        ((Double) levelInfo.get("xp")).intValue(),
                        (String)  levelInfo.get("nemesis"),
                        ((Double) levelInfo.get("nemesis_rate")).intValue());

                        levelMap.put(level, unitData);
            }
        }
    }
    private void loadFacility(String  path){
        Map<String, Object> jsonData = jsonManager.loadJsonData(path);

        assert jsonData != null;
        for(Map.Entry<String, Object> facilityTypeData : jsonData.entrySet())
        {
            String facilityType = facilityTypeData.getKey();
            List<Map<String, Object>> levels = (List<Map<String, Object>>) facilityTypeData.getValue();

            Map<Integer, GenData> levelMap = objectDataMap.computeIfAbsent(facilityType, k -> new HashMap<>());

            for (Map<String, Object> levelInfo : levels) {
                int level = ((Double) levelInfo.get("level")).intValue();

                FacilityData facilityData = new FacilityData(
                        (String)  levelInfo.get("sprite_path"),
                        (String)  levelInfo.get("description"), ((Double) levelInfo.get("maxLVL")).intValue(),
                        ((Double) levelInfo.get("build_cost1")).intValue(),
                        ((Double) levelInfo.get("build_cost2")).intValue(),
                        ((Double) levelInfo.get("build_cost3")).intValue(),
                        ((Double) levelInfo.get("build_cost4")).intValue(),
                        ((Double) levelInfo.get("tap_cost1")).intValue(),
                        ((Double) levelInfo.get("tap_cost2")).intValue(),
                        ((Double) levelInfo.get("tap_cost3")).intValue());

                levelMap.put(level, facilityData);
            }
        }
    }
    private void loadStorage(String  path){
        Map<String, Object> jsonData = jsonManager.loadJsonData(path);

        assert jsonData != null;
        for(Map.Entry<String, Object> storageTypeData : jsonData.entrySet())
        {
            String storageType = storageTypeData.getKey();
            List<Map<String, Object>> levels = (List<Map<String, Object>>) storageTypeData.getValue();

            Map<Integer, GenData> levelMap = objectDataMap.computeIfAbsent(storageType, k -> new HashMap<>());

            for (Map<String, Object> levelInfo : levels) {
                int level = ((Double) levelInfo.get("level")).intValue();

                StorageData storageData = new StorageData(
                        (String)  levelInfo.get("sprite_path"),
                        (String)  levelInfo.get("description"),
                        ((Double) levelInfo.get("maxLVL")).intValue(),
                        (String) levelInfo.get("storage_type"),
                        ((Double) levelInfo.get("storage_size")).intValue(),
                        ((Double) levelInfo.get("build_cost1")).intValue(),
                        ((Double) levelInfo.get("build_cost2")).intValue(),
                        ((Double) levelInfo.get("build_cost3")).intValue());

                levelMap.put(level, storageData);
            }
        }
    }
    private void loadMonster(String  path){
        Map<String, Object> jsonData = jsonManager.loadJsonData(path);

        assert jsonData != null;
        for(Map.Entry<String, Object> monsterTypeData : jsonData.entrySet())
        {
            String monsterType = monsterTypeData.getKey();
            List<Map<String, Object>> levels = (List<Map<String, Object>>) monsterTypeData.getValue();

            Map<Integer, GenData> levelMap = objectDataMap.computeIfAbsent(monsterType, k -> new HashMap<>());

            for (Map<String, Object> levelInfo : levels) {
                int level = ((Double) levelInfo.get("level")).intValue();

                MonsterData monsterData = new MonsterData(
                        (String)  levelInfo.get("sprite_path"),
                        (String)  levelInfo.get("description"),
                        ((Double) levelInfo.get("maxLVL")).intValue(),
                        ((Double) levelInfo.get("hp")).intValue(),
                        (String) levelInfo.get("reward"));

                levelMap.put(level, monsterData);
            }
        }
    }
    private void loadChest(String  path){
        Map<String, Object> jsonData = jsonManager.loadJsonData(path);

        assert jsonData != null;
        for(Map.Entry<String, Object> chestTypeData : jsonData.entrySet())
        {
            String chestType = chestTypeData.getKey();
            List<Map<String, Object>> levels = (List<Map<String, Object>>) chestTypeData.getValue();

            Map<Integer, GenData> levelMap = objectDataMap.computeIfAbsent(chestType, k -> new HashMap<>());

            for (Map<String, Object> levelInfo : levels) {
                int level = ((Double) levelInfo.get("level")).intValue();

                ChestData chestData = new ChestData(
                        (String)  levelInfo.get("sprite_path"),
                        (String)  levelInfo.get("description"),
                        ((Double) levelInfo.get("maxLVL")).intValue(),
                        (String) levelInfo.get("token_type"),
                        ((Double) levelInfo.get("tap_count")).intValue());

                levelMap.put(level, chestData);
            }
        }
    }

    private void loadToken(String  path){
        Map<String, Object> jsonData = jsonManager.loadJsonData(path);

        assert jsonData != null;
        for(Map.Entry<String, Object> tokenTypeData : jsonData.entrySet())
        {
            String tokenType = tokenTypeData.getKey();
            List<Map<String, Object>> levels = (List<Map<String, Object>>) tokenTypeData.getValue();

            Map<Integer, GenData> levelMap = objectDataMap.computeIfAbsent(tokenType, k -> new HashMap<>());

            for (Map<String, Object> levelInfo : levels) {
                int level = ((Double) levelInfo.get("level")).intValue();

                TokenData tokenData = new TokenData(
                        (String)  levelInfo.get("sprite_path"),
                        (String)  levelInfo.get("description"),
                        ((Double) levelInfo.get("maxLVL")).intValue(),
                        ((Double) levelInfo.get("token_value")).intValue(),
                        (String) levelInfo.get("token_type"));

                levelMap.put(level, tokenData);
            }
        }
    }

    private void loadPrince(String path){
        Map<String, Object> jsonData = jsonManager.loadJsonData(path);

        assert jsonData != null;
        for(Map.Entry<String, Object> princeTypeData : jsonData.entrySet())
        {
            String princeType = princeTypeData.getKey();
            List<Map<String, Object>> levels = (List<Map<String, Object>>) princeTypeData.getValue();

            Map<Integer, GenData> levelMap = objectDataMap.computeIfAbsent(princeType, k -> new HashMap<>());

            for (Map<String, Object> levelInfo : levels) {
                int level = ((Double) levelInfo.get("level")).intValue();

                PrinceData princeData = new PrinceData(
                        (String)  levelInfo.get("sprite_path"),
                        (String)  levelInfo.get("description"),
                        ((Double) levelInfo.get("maxLVL")).intValue(),
                        ((Double) levelInfo.get("resource_1")).intValue(),
                        ((Double) levelInfo.get("resource_2")).intValue(),
                        ((Double) levelInfo.get("resource_3")).intValue());


                levelMap.put(level, princeData);
            }
        }
    }

    private void loadSpawnConfiguration(String path){
        Map<String,  ArrayList<FacilitySpawnConfiguration>> jsonData = jsonManager.loadArrMap(path);

        assert jsonData != null;
        for(Map.Entry<String,  ArrayList<FacilitySpawnConfiguration>> objectEntry : jsonData.entrySet())
        {
            String key = objectEntry.getKey();
            ArrayList<FacilitySpawnConfiguration> arr = objectEntry.getValue();
            configurationMap.put(key, arr);
        }
    }

    //GETTERS
    public GenData getGameData(String type, Integer level) {
        Map<Integer, GenData> levelMap = objectDataMap.get(type);
        for (Map.Entry<Integer, GenData> k: levelMap.entrySet()
             ) {
            String st = String.valueOf(k.getValue().getMaxLVL());
            Log.d("Game Data Loadder", st);
        }
        if (levelMap != null) {
            return levelMap.get(level);
        } else {
            return null; // Or throw an exception if no data is found
        }
    }
    
    public ArrayList<FacilitySpawnConfiguration> getSpawnConfiguration(String key)
    {
        ArrayList<FacilitySpawnConfiguration> arr = configurationMap.get(key);
        if(arr!=null)
            return arr;
        else
        {
            Log.d("GameDtaLoader", "Somethingsnotright");
            return null;
        }

    }


}
