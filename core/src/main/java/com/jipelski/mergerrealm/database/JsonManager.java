package com.jipelski.mergerrealm.database;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import com.jipelski.mergerrealm.data.GenData;
import com.jipelski.mergerrealm.model.Chest;
import com.jipelski.mergerrealm.model.ExplorationSlot;
import com.jipelski.mergerrealm.model.Facility;
import com.jipelski.mergerrealm.model.FacilitySpawnConfiguration;
import com.jipelski.mergerrealm.model.GameObject;
import com.jipelski.mergerrealm.model.Monster;
import com.jipelski.mergerrealm.model.Prince;
import com.jipelski.mergerrealm.model.RaidState;
import com.jipelski.mergerrealm.model.ResourcePouch;
import com.jipelski.mergerrealm.model.Storage;
import com.jipelski.mergerrealm.model.Token;
import com.jipelski.mergerrealm.model.Unit;
import com.jipelski.mergerrealm.model.Item;
import com.jipelski.mergerrealm.util.RuntimeTypeAdapterFactory;
import com.jipelski.mergerrealm.util.RuneSystem;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;
import java.util.List;

public class JsonManager {

    private static final String TAG = "JsonManager";
    private static final String DATA_DIR = "data/";
    private static final String SAVE_DIR = "saves/";
    private static final String HASH_DIR = "saves/hashes/";

    // Salt makes it harder for someone to recalculate the hash
    // after modifying a save file. Change this to anything unique to your game.
    private static final String SALT = "MergerRealm_v1_s3cr3t";

    private final Gson gson = new GsonBuilder()
        .registerTypeAdapterFactory(
            RuntimeTypeAdapterFactory.of(GameObject.class, "objectClass")
                .registerSubtype(Unit.class,     "Unit")
                .registerSubtype(Facility.class, "Facility")
                .registerSubtype(Storage.class,  "Storage")
                .registerSubtype(Monster.class,  "Monster")
                .registerSubtype(Chest.class,    "Chest")
                .registerSubtype(Token.class,    "Token")
                .registerSubtype(ResourcePouch.class, "ResourcePouch")
                .registerSubtype(Prince.class,   "Prince")
        )
        .create();

    public JsonManager() {
    }

    // ── FILE HELPERS ──

    /**
     * Reads a JSON file. Checks local saves first (writable),
     * falls back to bundled assets (read-only defaults).
     *
     * If a save file exists but its integrity hash doesn't match,
     * the save is considered tampered and we fall back to defaults.
     */
    private String readJson(String filename) {
        FileHandle saveFile = Gdx.files.local(SAVE_DIR + filename + ".json");
        if (saveFile.exists()) {
            String content = saveFile.readString();

            if (verifyIntegrity(filename, content)) {
                return content;
            } else {
                Gdx.app.error(TAG, "Save integrity check FAILED for " + filename
                    + " — falling back to defaults (possible tampering)");
                // Delete the corrupt/tampered save
                saveFile.delete();
                FileHandle hashFile = Gdx.files.local(HASH_DIR + filename + ".hash");
                if (hashFile.exists()) hashFile.delete();
            }
        }

        FileHandle assetFile = Gdx.files.internal(DATA_DIR + filename + ".json");
        if (assetFile.exists()) {
            return assetFile.readString();
        }
        Gdx.app.error(TAG, "File not found: " + filename);
        return null;
    }

    /**
     * Writes JSON to local writable storage with an integrity hash.
     */
    private void writeJson(String filename, Object data) {
        try {
            String json = gson.toJson(data);

            FileHandle saveFile = Gdx.files.local(SAVE_DIR + filename + ".json");
            saveFile.writeString(json, false);

            // Write integrity hash alongside the save
            String hash = computeHash(filename, json);
            FileHandle hashFile = Gdx.files.local(HASH_DIR + filename + ".hash");
            hashFile.writeString(hash, false);

            Gdx.app.log(TAG, "Saved: " + filename);
        } catch (Exception e) {
            Gdx.app.error(TAG, "Failed to save: " + filename, e);
        }
    }

    // ── INTEGRITY HELPERS ──

    /**
     * Computes a SHA-256 hash of the content salted with a secret and the filename.
     * This prevents simple copy-paste attacks between save files.
     */
    private String computeHash(String filename, String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String salted = SALT + ":" + filename + ":" + content;
            byte[] hashBytes = digest.digest(salted.getBytes("UTF-8"));

            // Convert to hex string
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            Gdx.app.error(TAG, "Hash computation failed for " + filename, e);
            return "";
        }
    }

    /**
     * Verifies that a save file hasn't been tampered with.
     * Returns true if the hash matches, or if no hash exists yet
     * (first save from before the integrity system was added).
     */
    private boolean verifyIntegrity(String filename, String content) {
        FileHandle hashFile = Gdx.files.local(HASH_DIR + filename + ".hash");
        if (!hashFile.exists()) {
            // No hash file — this save predates the integrity system.
            // Accept it this time, it will get a hash on next save.
            Gdx.app.log(TAG, "No hash for " + filename + " — accepting (legacy save)");
            return true;
        }

        String storedHash = hashFile.readString().trim();
        String computedHash = computeHash(filename, content);
        return storedHash.equals(computedHash);
    }

    /**
     * Returns true if any save files exist (i.e. this isn't a fresh install).
     */
    public boolean hasSaveData() {
        FileHandle saveDir = Gdx.files.local(SAVE_DIR);
        if (!saveDir.exists()) return false;
        FileHandle[] files = saveDir.list(".json");
        return files != null && files.length > 0;
    }

    // ── LOADER METHODS ──

    public Map<String, GameObject> loadGridData(String filename) {
        String json = readJson(filename);
        if (json == null) return null;
        return gson.fromJson(json, new TypeToken<Map<String, GameObject>>() {}.getType());
    }

    public Map<String, int[]> loadConsumableMap(String filename) {
        String json = readJson(filename);
        if (json == null) return null;
        return gson.fromJson(json, new TypeToken<Map<String, int[]>>() {}.getType());
    }

    public int[] loadArray(String filename) {
        String json = readJson(filename);
        if (json == null) return null;
        return gson.fromJson(json, new TypeToken<int[]>() {}.getType());
    }

    public ArrayList<String> loadArrayList(String filename) {
        String json = readJson(filename);
        if (json == null) return null;
        return gson.fromJson(json, new TypeToken<ArrayList<String>>() {}.getType());
    }

    public LinkedList<GenData> loadRewardQueue(String filename) {
        String json = readJson(filename);
        if (json == null) return null;
        return gson.fromJson(json, new TypeToken<LinkedList<GenData>>() {}.getType());
    }

    public Map<String, ArrayList<FacilitySpawnConfiguration>> loadArrMap(String filename) {
        String json = readJson(filename);
        if (json == null) return null;
        return gson.fromJson(json,
            new TypeToken<Map<String, ArrayList<FacilitySpawnConfiguration>>>() {}.getType());
    }

    public Map<String, Object> loadJsonData(String filename) {
        String json = readJson(filename);
        if (json == null) return null;
        return gson.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
    }

    public Map<String, Boolean> loadStatus(String filename) {
        String json = readJson(filename);
        if (json == null) return null;
        return gson.fromJson(json, new TypeToken<Map<String, Boolean>>() {}.getType());
    }

    public Map<String, int[]> loadGlobalCounterMap(String filename) {
        String json = readJson(filename);
        if (json == null) return null;
        return gson.fromJson(json, new TypeToken<Map<String, int[]>>() {}.getType());
    }

    public java.util.Set<String> loadLockedObjects(String filename) {
        String json = readJson(filename);
        if (json == null) return null;
        java.util.List<String> list = gson.fromJson(json,
            new TypeToken<java.util.List<String>>() {}.getType());
        return list != null ? new java.util.HashSet<>(list) : null;
    }

    /**
     * Loads the inventory item list.
     */
    public List<Item> loadInventoryItems(String filename) {
        String json = readJson(filename);
        if (json == null) return null;
        return gson.fromJson(json, new TypeToken<List<Item>>() {}.getType());
    }

    /**
     * Loads the equipped item map (unitId → itemId).
     */
    public Map<String, String> loadEquippedMap(String filename) {
        String json = readJson(filename);
        if (json == null) return null;
        return gson.fromJson(json, new TypeToken<Map<String, String>>() {}.getType());
    }

    public Map<String, Map<String, Integer>> loadRuneApplications(String filename) {
        String json = readJson(filename);
        if (json == null) return null;
        return gson.fromJson(json,
            new TypeToken<Map<String, Map<String, Integer>>>() {}.getType());
    }

    /**
     * Saves rune fragment counts: { "might": 3, "vitality": 1, ... }
     */
    public void saveRuneFragments(String filename, Map<String, Integer> fragments) {
        writeJson(filename, fragments);
    }

    public Map<String, Integer> loadRuneFragments(String filename) {
        String json = readJson(filename);
        if (json == null) return null;
        return gson.fromJson(json, new TypeToken<Map<String, Integer>>() {}.getType());
    }

    /**
     * Saves crafted rune counts: { "might": 1, "vitality": 0, ... }
     */
    public void saveRuneCrafted(String filename, Map<String, Integer> crafted) {
        writeJson(filename, crafted);
    }

    public Map<String, Integer> loadRuneCrafted(String filename) {
        String json = readJson(filename);
        if (json == null) return null;
        return gson.fromJson(json, new TypeToken<Map<String, Integer>>() {}.getType());
    }

    /**
     * Saves applied runes: { "unitId": { "might": 3, "vitality": 2 }, ... }
     */
    public void saveRuneApplications(String filename,
                                     Map<String, Map<String, Integer>> applied) {
        writeJson(filename, applied);
    }

    /**
     * Saves active exploration slots.
     */
    public void saveExplorationSlots(String filename, List<ExplorationSlot> slots) {
        writeJson(filename, slots);
    }

    /**
     * Saves the active raid, if any (null while no raid is in progress —
     * written unconditionally every autosave/pause/dispose, same as the
     * other "always write, even if empty" state, so relaunching never has
     * to distinguish "no file" from "no active raid"). Lets a raid resume
     * exactly where it left off if the process is killed mid-raid instead
     * of losing the party permanently.
     */
    public void saveRaidState(String filename, RaidState raid) {
        writeJson(filename, raid);
    }

    public RaidState loadRaidState(String filename) {
        String json = readJson(filename);
        if (json == null || "null".equals(json.trim())) return null;
        return gson.fromJson(json, RaidState.class);
    }

    /**
     * Loads active exploration slots.
     */
    public List<ExplorationSlot> loadExplorationSlots(String filename) {
        String json = readJson(filename);
        if (json == null) return null;
        return gson.fromJson(json, new TypeToken<List<ExplorationSlot>>() {}.getType());
    }

    /**
     * Reads a raw JSON string from a file (for zone/enemy data).
     * Falls back to bundled assets.
     */
    public String readRawJson(String filename) {
        return readJson(filename);
    }

    // ── SAVER METHODS ──

    public void saveResources(String filename, Map<String, int[]> resMap) {
        writeJson(filename, resMap);
    }

    public void saveArrayList(String filename, ArrayList<String> arr) {
        writeJson(filename, arr);
    }

    public void saveArray(String filename, int[] arr) {
        writeJson(filename, arr);
    }

    public void saveArray(String filename, LinkedList<?> queue) {
        writeJson(filename, queue);
    }

    public void saveGridObjects(String filename, Map<String, GameObject> objMap) {
        writeJson(filename, objMap);
    }

    public void saveCounterMap(String filename, Map<String, int[]> objMap) {
        writeJson(filename, objMap);
    }

    public void saveStatus(String filename, Map<String, Boolean> statusMap) {
        writeJson(filename, statusMap);
    }

    public void saveLockedObjects(String filename, java.util.Set<String> locks) {
        writeJson(filename, new java.util.ArrayList<>(locks));
    }

    /**
     * Saves the inventory item list.
     */
    public void saveInventoryItems(String filename, List<Item> items) {
        writeJson(filename, items);
    }

    /**
     * Saves the equipped item map (unitId → itemId).
     */
    public void saveEquippedMap(String filename, Map<String, String> equipped) {
        writeJson(filename, equipped);
    }

    /**
     * Saves shop stock and refresh timestamps.
     */
    public void saveShopState(String filename, Map<String, Object> state) {
        writeJson(filename, state);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> loadShopState(String filename) {
        String json = readJson(filename);
        if (json == null) return null;
        return gson.fromJson(json, new TypeToken<Map<String, Object>>() {}.getType());
    }

    // Reuses existing methods:
    //   saveArray("gold_state", ...) for balance + extra slots
    //   saveArray("daily_login_state", ...) for the login-streak timestamp + day
    //   saveLockedObjects("gold_claimed", ...) for claimed reward keys (Set<String>)

    // If saveLockedObjects/loadLockedObjects already save/load Set<String>,
    // reuse them. Otherwise, add:

    public void saveStringSet(String filename, java.util.Set<String> set) {
        writeJson(filename, set);
    }

    public java.util.Set<String> loadStringSet(String filename) {
        String json = readJson(filename);
        if (json == null) return null;
        return gson.fromJson(json, new TypeToken<java.util.HashSet<String>>() {}.getType());
    }
}
