package com.jipelski.mergerrealm.util;

import com.badlogic.gdx.Gdx;

import com.jipelski.mergerrealm.data.ChestData;
import com.jipelski.mergerrealm.data.FacilityData;
import com.jipelski.mergerrealm.data.GenData;
import com.jipelski.mergerrealm.data.MonsterData;
import com.jipelski.mergerrealm.data.PrinceData;
import com.jipelski.mergerrealm.data.ResourcePouchData;
import com.jipelski.mergerrealm.data.StorageData;
import com.jipelski.mergerrealm.data.TokenData;
import com.jipelski.mergerrealm.data.UnitData;
import com.jipelski.mergerrealm.database.JsonManager;
import com.jipelski.mergerrealm.model.FacilitySpawnConfiguration;
import com.jipelski.mergerrealm.model.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameDataLoader {

    private static final String TAG = "GameDataLoader";

    // <TYPE, <LEVEL, DATA>>
    private final Map<String, Map<Integer, GenData>>                    objectDataMap;
    // <"type_level", LIST<SPAWN_CONFIG>>
    private final Map<String, ArrayList<FacilitySpawnConfiguration>>    configurationMap;

    private final JsonManager jsonManager;

    private Map<String, List<Map<String, Object>>> itemDefinitions;

    public GameDataLoader(JsonManager jsonManager) {
        this.jsonManager      = jsonManager;
        this.objectDataMap    = new HashMap<>();
        this.configurationMap = new HashMap<>();
        initialiseData();
    }

    private void initialiseData() {
        loadUnit("unit");
        loadUnit("legendary_units");
        loadFacility("facility");
        loadStorage("storage");
        loadMonster("monster");
        loadChest("chest");
        loadToken("token");
        loadResourcePouch("resource_pouch");
        loadPrince("prince");
        loadSpawnConfiguration("facility_config");
        loadSpawnConfiguration("chest_config");
        logLoadedData();
        loadItemDefinitions("items");
    }

    // LOAD METHODS

    private void loadUnit(String path) {
        Map<String, Object> jsonData = jsonManager.loadJsonData(path);
        if (jsonData == null) { Gdx.app.error(TAG, "loadUnit: failed to load " + path); return; }

        for (Map.Entry<String, Object> entry : jsonData.entrySet()) {
            String unitType = entry.getKey();
            List<Map<String, Object>> levels = castLevelList(entry.getValue(), unitType);
            if (levels == null) continue;

            Map<Integer, GenData> levelMap = objectDataMap.computeIfAbsent(unitType, k -> new HashMap<>());
            for (Map<String, Object> info : levels) {
                try {
                    int level = toInt(info, "level");
                    // Optional — only legendary_units.json entries have these
                    // (dual-resource legendaries like elder_villager). Default to
                    // "none"/0 rather than toInt()'s throw-on-missing, since plain
                    // unit.json entries don't have the keys at all.
                    String secondaryResource = info.containsKey("secondary_resource")
                        ? str(info, "secondary_resource") : "none";
                    int secondaryGenRate = info.containsKey("secondary_gen_rate")
                        ? toInt(info, "secondary_gen_rate") : 0;
                    levelMap.put(level, new UnitData(
                            str(info, "sprite_path"),
                            str(info, "description"),
                            toInt(info, "maxLVL"),
                            str(info, "resource"),
                            toInt(info, "gen_rate"),
                            toInt(info, "hp"),
                            toInt(info, "damage"),
                            toInt(info, "xp"),
                            str(info, "nemesis"),
                            toInt(info, "nemesis_rate"),
                            secondaryResource,
                            secondaryGenRate));
                } catch (Exception e) {
                    Gdx.app.error(TAG, "loadUnit: parse error for " + unitType, e);
                }
            }
        }
    }

    private void loadFacility(String path) {
        Map<String, Object> jsonData = jsonManager.loadJsonData(path);
        if (jsonData == null) { Gdx.app.error(TAG, "loadFacility: failed to load " + path); return; }

        for (Map.Entry<String, Object> entry : jsonData.entrySet()) {
            String type = entry.getKey();
            List<Map<String, Object>> levels = castLevelList(entry.getValue(), type);
            if (levels == null) continue;

            Map<Integer, GenData> levelMap = objectDataMap.computeIfAbsent(type, k -> new HashMap<>());
            for (Map<String, Object> info : levels) {
                try {
                    int level = toInt(info, "level");
                    levelMap.put(level, new FacilityData(
                            str(info, "sprite_path"),
                            str(info, "description"),
                            toInt(info, "maxLVL"),
                            toInt(info, "build_cost1"),
                            toInt(info, "build_cost2"),
                            toInt(info, "build_cost3"),
                            toInt(info, "build_cost4"),
                            toInt(info, "tap_cost1"),
                            toInt(info, "tap_cost2"),
                            toInt(info, "tap_cost3"),
                            toInt(info, "time_cost")));
                } catch (Exception e) {
                    Gdx.app.error(TAG, "loadFacility: parse error for " + type, e);
                }
            }
        }
    }

    private void loadStorage(String path) {
        Map<String, Object> jsonData = jsonManager.loadJsonData(path);
        if (jsonData == null) { Gdx.app.error(TAG, "loadStorage: failed to load " + path); return; }

        for (Map.Entry<String, Object> entry : jsonData.entrySet()) {
            String type = entry.getKey();
            List<Map<String, Object>> levels = castLevelList(entry.getValue(), type);
            if (levels == null) continue;

            Map<Integer, GenData> levelMap = objectDataMap.computeIfAbsent(type, k -> new HashMap<>());
            for (Map<String, Object> info : levels) {
                try {
                    int level = toInt(info, "level");
                    levelMap.put(level, new StorageData(
                            str(info, "sprite_path"),
                            str(info, "description"),
                            toInt(info, "maxLVL"),
                            str(info, "storage_type"),
                            toInt(info, "storage_size"),
                            toInt(info, "build_cost1"),
                            toInt(info, "build_cost2"),
                            toInt(info, "build_cost3")));
                } catch (Exception e) {
                    Gdx.app.error(TAG, "loadStorage: parse error for " + type, e);
                }
            }
        }
    }

    private void loadMonster(String path) {
        Map<String, Object> jsonData = jsonManager.loadJsonData(path);
        if (jsonData == null) { Gdx.app.error(TAG, "loadMonster: failed to load " + path); return; }

        for (Map.Entry<String, Object> entry : jsonData.entrySet()) {
            String type = entry.getKey();
            List<Map<String, Object>> levels = castLevelList(entry.getValue(), type);
            if (levels == null) continue;

            Map<Integer, GenData> levelMap = objectDataMap.computeIfAbsent(type, k -> new HashMap<>());
            for (Map<String, Object> info : levels) {
                try {
                    int level = toInt(info, "level");
                    levelMap.put(level, new MonsterData(
                            str(info, "sprite_path"),
                            str(info, "description"),
                            toInt(info, "maxLVL"),
                            toInt(info, "hp"),
                            toInt(info, "damage"),
                            str(info, "reward")));
                } catch (Exception e) {
                    Gdx.app.error(TAG, "loadMonster: parse error for " + type, e);
                }
            }
        }
    }

    private void loadChest(String path) {
        Map<String, Object> jsonData = jsonManager.loadJsonData(path);
        if (jsonData == null) { Gdx.app.error(TAG, "loadChest: failed to load " + path); return; }

        for (Map.Entry<String, Object> entry : jsonData.entrySet()) {
            String type = entry.getKey();
            List<Map<String, Object>> levels = castLevelList(entry.getValue(), type);
            if (levels == null) continue;

            Map<Integer, GenData> levelMap = objectDataMap.computeIfAbsent(type, k -> new HashMap<>());
            for (Map<String, Object> info : levels) {
                try {
                    int level = toInt(info, "level");
                    levelMap.put(level, new ChestData(
                            str(info, "sprite_path"),
                            str(info, "description"),
                            toInt(info, "maxLVL"),
                            str(info, "token_type"),
                            toInt(info, "tap_count")));
                } catch (Exception e) {
                    Gdx.app.error(TAG, "loadChest: parse error for " + type, e);
                }
            }
        }
    }

    private void loadToken(String path) {
        Map<String, Object> jsonData = jsonManager.loadJsonData(path);
        if (jsonData == null) { Gdx.app.error(TAG, "loadToken: failed to load " + path); return; }

        for (Map.Entry<String, Object> entry : jsonData.entrySet()) {
            String type = entry.getKey();
            List<Map<String, Object>> levels = castLevelList(entry.getValue(), type);
            if (levels == null) continue;

            Map<Integer, GenData> levelMap = objectDataMap.computeIfAbsent(type, k -> new HashMap<>());
            for (Map<String, Object> info : levels) {
                try {
                    int level = toInt(info, "level");
                    levelMap.put(level, new TokenData(
                            str(info, "sprite_path"),
                            str(info, "description"),
                            toInt(info, "maxLVL"),
                            toInt(info, "token_value"),
                            str(info, "token_type")));
                } catch (Exception e) {
                    Gdx.app.error(TAG, "loadToken: parse error for " + type, e);
                }
            }
        }
    }

    private void loadResourcePouch(String path) {
        Map<String, Object> jsonData = jsonManager.loadJsonData(path);
        if (jsonData == null) { Gdx.app.error(TAG, "loadResourcePouch: failed to load " + path); return; }

        for (Map.Entry<String, Object> entry : jsonData.entrySet()) {
            String type = entry.getKey();
            List<Map<String, Object>> levels = castLevelList(entry.getValue(), type);
            if (levels == null) continue;

            Map<Integer, GenData> levelMap = objectDataMap.computeIfAbsent(type, k -> new HashMap<>());
            for (Map<String, Object> info : levels) {
                try {
                    int level = toInt(info, "level");
                    levelMap.put(level, new ResourcePouchData(
                            str(info, "sprite_path"),
                            str(info, "description"),
                            toInt(info, "maxLVL"),
                            toInt(info, "fill_percent"),
                            str(info, "resource_type")));
                } catch (Exception e) {
                    Gdx.app.error(TAG, "loadResourcePouch: parse error for " + type, e);
                }
            }
        }
    }

    private void loadPrince(String path) {
        Map<String, Object> jsonData = jsonManager.loadJsonData(path);
        if (jsonData == null) { Gdx.app.error(TAG, "loadPrince: failed to load " + path); return; }

        for (Map.Entry<String, Object> entry : jsonData.entrySet()) {
            String type = entry.getKey();
            List<Map<String, Object>> levels = castLevelList(entry.getValue(), type);
            if (levels == null) continue;

            Map<Integer, GenData> levelMap = objectDataMap.computeIfAbsent(type, k -> new HashMap<>());
            for (Map<String, Object> info : levels) {
                try {
                    int level = toInt(info, "level");
                    levelMap.put(level, new PrinceData(
                            str(info, "sprite_path"),
                            str(info, "description"),
                            toInt(info, "maxLVL"),
                            toInt(info, "resource_1"),
                            toInt(info, "resource_2"),
                            toInt(info, "resource_3")));
                } catch (Exception e) {
                    Gdx.app.error(TAG, "loadPrince: parse error for " + type, e);
                }
            }
        }
    }

    private void loadSpawnConfiguration(String path) {
        Map<String, ArrayList<FacilitySpawnConfiguration>> jsonData = jsonManager.loadArrMap(path);
        if (jsonData == null) { Gdx.app.error(TAG, "loadSpawnConfiguration: failed to load " + path); return; }
        configurationMap.putAll(jsonData);
    }

    private void loadItemDefinitions(String path) {
        Map<String, Object> jsonData = jsonManager.loadJsonData(path);
        if (jsonData == null) {
            Gdx.app.error(TAG, "loadItemDefinitions: failed to load " + path);
            itemDefinitions = new java.util.HashMap<>();
            return;
        }
        itemDefinitions = new java.util.HashMap<>();
        for (Map.Entry<String, Object> entry : jsonData.entrySet()) {
            List<Map<String, Object>> levels = castLevelList(entry.getValue(), entry.getKey());
            if (levels != null) {
                itemDefinitions.put(entry.getKey(), levels);
            }
        }
        Gdx.app.log(TAG, "Loaded item definitions: " + itemDefinitions.keySet());
    }

    // GETTERS

    /**
     * Returns the data for the given type and level, or null if not found.
     * Null check is performed BEFORE dereferencing levelMap — previously
     * the loop ran before the null check, causing a NPE on unknown types.
     */
    public GenData getGameData(String type, Integer level) {
        Map<Integer, GenData> levelMap = objectDataMap.get(type);
        if (levelMap == null) {
            Gdx.app.log(TAG, "getGameData: no data for type=" + type);
            return null;
        }
        GenData data = levelMap.get(level);
        if (data == null) {
            Gdx.app.log(TAG, "getGameData: no data for type=" + type + " level=" + level);
        }
        return data;
    }

    public ArrayList<FacilitySpawnConfiguration> getSpawnConfiguration(String key) {
        ArrayList<FacilitySpawnConfiguration> arr = configurationMap.get(key);
        if (arr == null) {
            Gdx.app.log(TAG, "getSpawnConfiguration: no config for key=" + key);
        }
        return arr;
    }

    public Map<String, Map<Integer, GenData>> getMap() {
        return objectDataMap;
    }

    // HELPERS

    /**
     * Creates a new Item instance from the items.json definition.
     * Generates a unique ID for the item.
     *
     * @param type  "sword", "shield", "amulet", "potion", "phoenix_feather"
     * @param level 1-5
     * @return a new Item with a unique ID, or null if the definition wasn't found
     */
    public Item createItem(String type, int level) {
        List<Map<String, Object>> levels = itemDefinitions.get(type);
        if (levels == null) {
            Gdx.app.log(TAG, "createItem: no definition for type=" + type);
            return null;
        }

        // Find the matching level entry
        for (Map<String, Object> info : levels) {
            try {
                int entryLevel = toInt(info, "level");
                if (entryLevel == level) {
                    String id = "item_" + type + "_" + level + "_"
                        + System.currentTimeMillis() + "_"
                        + (int)(Math.random() * 10000);
                    return new Item(
                        id,
                        type,
                        level,
                        str(info, "name"),
                        str(info, "description"),
                        toInt(info, "bonusDamage"),
                        toInt(info, "bonusHp"),
                        Boolean.TRUE.equals(info.get("consumable")),
                        str(info, "spritePath")
                    );
                }
            } catch (Exception e) {
                // Matches the per-entry try/catch used by loadUnit/loadFacility/etc —
                // this was previously the one toInt() call site left unguarded,
                // so a malformed "level" in items.json would throw uncaught here.
                Gdx.app.error(TAG, "createItem: parse error for " + type + " entry", e);
            }
        }
        Gdx.app.log(TAG, "createItem: no level " + level + " for type=" + type);
        return null;
    }

    /** Logs a summary of all loaded types and levels after initialisation. */
    private void logLoadedData() {
        for (Map.Entry<String, Map<Integer, GenData>> entry : objectDataMap.entrySet()) {
            Map<Integer, GenData> levels = entry.getValue();
            Gdx.app.log(TAG, "Loaded type=" + entry.getKey()
                    + " levels=" + (levels != null ? levels.keySet() : "null"));
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castLevelList(Object value, String type) {
        if (!(value instanceof List)) {
            Gdx.app.error(TAG, "castLevelList: unexpected structure for type=" + type);
            return null;
        }
        return (List<Map<String, Object>>) value;
    }

    private int toInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Double) return ((Double) val).intValue();
        if (val instanceof Integer) return (Integer) val;
        throw new IllegalArgumentException("Expected numeric value for key=" + key + " got=" + val);
    }

    private String str(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
