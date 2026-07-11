package com.jipelski.mergerrealm.util;

import com.badlogic.gdx.Gdx;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.jipelski.mergerrealm.database.JsonManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages Prince Outfits: equippable items on the Prince (one equipped at a
 * time, any number owned) that grant a passive stat bonus across three
 * systems — economy (resource gen), raid combat (damage/HP), exploration
 * (speed/find odds). Sold for Gold as a long-term aspirational sink.
 *
 * Unlike PrestigeManager's fixed 5 named upgrade tiers (hardcoded constants,
 * mirroring RuneSystem's BOOST_STANDARD style), outfits are open-ended named
 * content, loaded from assets/data/outfits.json — new ones should be
 * addable without touching code. Parsed the same way TutorialManager loads
 * tutorial_steps.json: raw JSON string -> Gson into a generic Map/List ->
 * manual field extraction (this project's established convention for
 * content data, see GameDataLoader's toInt/str helpers), not direct Gson
 * POJO binding.
 *
 * Multipliers are read at point-of-use by the systems they affect — see
 * ResourceManager/RaidManager/ExplorationManager call sites — this class
 * never threads itself through those internals.
 */
public class OutfitManager {

    private static final String TAG = "OutfitManager";

    public static class OutfitData {
        public String id = "";
        public String name = "";
        public String description = "";
        public int goldCost = 0;
        public float resourceGenBonus = 0f;
        public float raidDamageBonus = 0f;
        public float raidHpBonus = 0f;
        public float explorationSpeedBonus = 0f;
        public float explorationFindBonus = 0f;
    }

    private final Gson gson = new Gson();
    private final List<OutfitData> outfits = new ArrayList<>();

    private final Set<String> ownedOutfitIds = new HashSet<>();
    private String equippedOutfitId = null;

    private final EventManager eventManager;

    public OutfitManager(EventManager eventManager, JsonManager jsonManager) {
        this.eventManager = eventManager;
        loadDefinitions(jsonManager);
    }

    // ══════════════════════════════════════════════════════════════
    // CATALOG
    // ══════════════════════════════════════════════════════════════

    public List<OutfitData> getAllOutfits() { return outfits; }

    public OutfitData getOutfit(String id) {
        for (OutfitData o : outfits) {
            if (o.id.equals(id)) return o;
        }
        return null;
    }

    // ══════════════════════════════════════════════════════════════
    // OWNERSHIP + EQUIP
    // ══════════════════════════════════════════════════════════════

    public boolean isOwned(String id) { return ownedOutfitIds.contains(id); }
    public String getEquippedOutfitId() { return equippedOutfitId; }
    public boolean isEquipped(String id) { return id != null && id.equals(equippedOutfitId); }

    public boolean canAfford(String id) {
        OutfitData outfit = getOutfit(id);
        return outfit != null && eventManager.getGoldManager().canAfford(outfit.goldCost);
    }

    /** Purchases an outfit (adds to the owned set). Does NOT auto-equip. */
    public boolean purchase(String id) {
        OutfitData outfit = getOutfit(id);
        if (outfit == null) {
            Gdx.app.log(TAG, "purchase: unknown outfit id=" + id);
            return false;
        }
        if (isOwned(id)) {
            Gdx.app.log(TAG, "purchase: already owned id=" + id);
            return false;
        }
        if (!eventManager.getGoldManager().spendGold(outfit.goldCost, "outfit_" + id)) {
            return false;
        }
        ownedOutfitIds.add(id);
        Gdx.app.log(TAG, "Purchased outfit: " + outfit.name);
        return true;
    }

    /** Equips an already-owned outfit, replacing any currently-equipped one. */
    public boolean equip(String id) {
        if (!isOwned(id)) {
            Gdx.app.log(TAG, "equip: outfit not owned id=" + id);
            return false;
        }
        equippedOutfitId = id;
        Gdx.app.log(TAG, "Equipped outfit: " + id);
        return true;
    }

    public void unequip() {
        equippedOutfitId = null;
        Gdx.app.log(TAG, "Outfit unequipped");
    }

    // ══════════════════════════════════════════════════════════════
    // POINT-OF-USE MULTIPLIERS (mirrors RuneSystem's getBoosted*() pattern)
    // ══════════════════════════════════════════════════════════════

    private OutfitData getEquipped() {
        return equippedOutfitId != null ? getOutfit(equippedOutfitId) : null;
    }

    public float getResourceGenMultiplier() {
        OutfitData o = getEquipped();
        return o != null ? 1f + o.resourceGenBonus : 1f;
    }

    public float getRaidDamageMultiplier() {
        OutfitData o = getEquipped();
        return o != null ? 1f + o.raidDamageBonus : 1f;
    }

    public float getRaidHpMultiplier() {
        OutfitData o = getEquipped();
        return o != null ? 1f + o.raidHpBonus : 1f;
    }

    public float getExplorationSpeedMultiplier() {
        OutfitData o = getEquipped();
        return o != null ? 1f + o.explorationSpeedBonus : 1f;
    }

    public float getExplorationFindMultiplier() {
        OutfitData o = getEquipped();
        return o != null ? 1f + o.explorationFindBonus : 1f;
    }

    // ══════════════════════════════════════════════════════════════
    // SAVE / LOAD
    // ══════════════════════════════════════════════════════════════

    public Set<String> getOwnedOutfitIds() { return ownedOutfitIds; }

    public void setOwnedOutfitIds(Set<String> ids) {
        ownedOutfitIds.clear();
        if (ids != null) ownedOutfitIds.addAll(ids);
    }

    /** 0-or-1-element set — reuses the saveStringSet/loadStringSet pair, same as gold_claimed. */
    public Set<String> getEquippedOutfitIdAsSet() {
        Set<String> result = new HashSet<>();
        if (equippedOutfitId != null) result.add(equippedOutfitId);
        return result;
    }

    public void setEquippedOutfitIdFromSet(Set<String> ids) {
        equippedOutfitId = (ids != null && !ids.isEmpty()) ? ids.iterator().next() : null;
    }

    // ══════════════════════════════════════════════════════════════
    // DATA LOADING
    // ══════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private void loadDefinitions(JsonManager jsonManager) {
        String json = jsonManager.readRawJson("outfits");
        if (json == null) {
            Gdx.app.error(TAG, "outfits.json missing — no outfits available");
            return;
        }
        try {
            List<Object> raw = gson.fromJson(json, new TypeToken<List<Object>>() {}.getType());
            if (raw == null) return;
            for (Object o : raw) {
                if (o instanceof Map) outfits.add(parseOutfit((Map<String, Object>) o));
            }
            Gdx.app.log(TAG, "Loaded " + outfits.size() + " outfits");
        } catch (Exception e) {
            Gdx.app.error(TAG, "Failed to parse outfits.json", e);
        }
    }

    private OutfitData parseOutfit(Map<String, Object> m) {
        OutfitData outfit = new OutfitData();
        outfit.id = str(m, "id", "");
        outfit.name = str(m, "name", outfit.id);
        outfit.description = str(m, "description", "");
        outfit.goldCost = toInt(m, "gold_cost", 0);
        outfit.resourceGenBonus = toFloat(m, "resource_gen_bonus", 0f);
        outfit.raidDamageBonus = toFloat(m, "raid_damage_bonus", 0f);
        outfit.raidHpBonus = toFloat(m, "raid_hp_bonus", 0f);
        outfit.explorationSpeedBonus = toFloat(m, "exploration_speed_bonus", 0f);
        outfit.explorationFindBonus = toFloat(m, "exploration_find_bonus", 0f);
        return outfit;
    }

    private String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v != null ? v.toString() : def;
    }

    private int toInt(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        return def;
    }

    private float toFloat(Map<String, Object> m, String key, float def) {
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).floatValue();
        return def;
    }
}
