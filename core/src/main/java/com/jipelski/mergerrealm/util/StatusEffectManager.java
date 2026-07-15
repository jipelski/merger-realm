package com.jipelski.mergerrealm.util;

import com.badlogic.gdx.Gdx;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.jipelski.mergerrealm.database.JsonManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and looks up the raid status-effect catalog (Raid V3). Modeled 1:1
 * on OutfitManager — raw JSON string -> Gson into a generic List/Map ->
 * manual field extraction (this project's established convention for
 * open-ended named content, see OutfitManager's own doc comment), not
 * direct Gson POJO binding.
 *
 * This manager only owns the CATALOG (id -> definition). Live effect
 * instances (ActiveStatusEffect) live on individual Combatants and are
 * resolved back to a StatusEffectData here at use time — see
 * StatusEffectEngine, which does the actual apply/tick/read-multiplier
 * work and is the only other class that should call getEffect().
 */
public class StatusEffectManager {

    private static final String TAG = "StatusEffectManager";

    /** kind values: dot, hot, modifier, execute, immunity, cleanse, shield, stun, revive */
    public static final String KIND_DOT = "dot";
    public static final String KIND_HOT = "hot";
    public static final String KIND_MODIFIER = "modifier";
    public static final String KIND_EXECUTE = "execute";
    public static final String KIND_IMMUNITY = "immunity";
    public static final String KIND_CLEANSE = "cleanse";
    public static final String KIND_SHIELD = "shield";
    public static final String KIND_STUN = "stun";
    public static final String KIND_REVIVE = "revive";

    /** magnitude_type values */
    public static final String MAG_PERCENT = "percent";
    public static final String MAG_FLAT = "flat";
    public static final String MAG_MULTIPLIER = "multiplier";
    public static final String MAG_THRESHOLD = "threshold";
    public static final String MAG_PERCENT_MAX_HP = "percent_max_hp";

    /** target values (kind == modifier only) */
    public static final String TARGET_ATTACK_SPEED = "attack_speed";
    public static final String TARGET_DAMAGE_TAKEN = "damage_taken";
    public static final String TARGET_DAMAGE_DEALT = "damage_dealt";
    public static final String TARGET_REFLECT = "reflect";

    public static class StatusEffectData {
        public String id = "";
        public String name = "";
        public String icon = "";
        public float[] color = {1f, 1f, 1f};
        public String kind = "";
        public String target = "";               // modifier only
        public float duration = 0f;               // <=0: instant (cleanse/shield/execute) or
                                                    // permanent-until-removed (modifier/hot/immunity/revive)
        public float tickInterval = 1f;            // dot/hot cadence
        public float magnitude = 0f;
        public String magnitudeType = MAG_PERCENT;
        public boolean beneficial = true;
        public int maxStacks = 1;
        public boolean refreshOnReapply = true;

        public boolean isInstant() {
            return KIND_CLEANSE.equals(kind) || KIND_SHIELD.equals(kind)
                || KIND_EXECUTE.equals(kind);
        }

        public boolean isPermanent() {
            return duration <= 0f && !isInstant();
        }
    }

    private final Gson gson = new Gson();
    private final Map<String, StatusEffectData> catalog = new HashMap<>();

    public StatusEffectManager(JsonManager jsonManager) {
        loadDefinitions(jsonManager);
    }

    public StatusEffectData getEffect(String id) {
        return id != null ? catalog.get(id) : null;
    }

    public List<StatusEffectData> getAllEffects() {
        return new ArrayList<>(catalog.values());
    }

    @SuppressWarnings("unchecked")
    private void loadDefinitions(JsonManager jsonManager) {
        String json = jsonManager.readRawJson("status_effects");
        if (json == null) {
            Gdx.app.error(TAG, "status_effects.json missing — no status effects available");
            return;
        }
        try {
            List<Object> raw = gson.fromJson(json, new TypeToken<List<Object>>() {}.getType());
            if (raw == null) return;
            for (Object o : raw) {
                if (o instanceof Map) {
                    StatusEffectData def = parseEffect((Map<String, Object>) o);
                    if (!def.id.isEmpty()) catalog.put(def.id, def);
                }
            }
            Gdx.app.log(TAG, "Loaded " + catalog.size() + " status effects");
        } catch (Exception e) {
            Gdx.app.error(TAG, "Failed to parse status_effects.json", e);
        }
    }

    @SuppressWarnings("unchecked")
    private StatusEffectData parseEffect(Map<String, Object> m) {
        StatusEffectData def = new StatusEffectData();
        def.id = str(m, "id", "");
        def.name = str(m, "name", def.id);
        def.icon = str(m, "icon", "");
        def.kind = str(m, "kind", "");
        def.target = str(m, "target", "");
        def.duration = toFloat(m, "duration", 0f);
        def.tickInterval = toFloat(m, "tick_interval", 1f);
        def.magnitude = toFloat(m, "magnitude", 0f);
        def.magnitudeType = str(m, "magnitude_type", MAG_PERCENT);
        def.beneficial = m.containsKey("beneficial")
            ? Boolean.TRUE.equals(m.get("beneficial")) : true;
        def.maxStacks = toInt(m, "max_stacks", 1);
        def.refreshOnReapply = !m.containsKey("refresh_on_reapply")
            || Boolean.TRUE.equals(m.get("refresh_on_reapply"));

        Object colorObj = m.get("color");
        if (colorObj instanceof List) {
            List<Object> c = (List<Object>) colorObj;
            float[] rgb = new float[3];
            for (int i = 0; i < 3 && i < c.size(); i++) {
                rgb[i] = c.get(i) instanceof Number ? ((Number) c.get(i)).floatValue() : 1f;
            }
            def.color = rgb;
        }
        return def;
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
