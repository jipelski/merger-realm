package com.jipelski.mergerrealm.util;

import com.badlogic.gdx.Gdx;

import com.jipelski.mergerrealm.model.Item;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages Enchanted Equipment sets — raid-exclusive gear with set bonuses.
 *
 * 4 sets: Dragonscale, Shadowsteel, Holy Radiance, Infernal
 * Each set has 2 pieces: sword + shield
 * When both pieces are equipped on units in the SAME raid party,
 * the 2-piece set bonus activates.
 *
 * Enchanted items are stored as regular Items in the inventory
 * with a special "enchanted_setname_type" item type
 * (e.g. "enchanted_dragonscale_sword").
 *
 * This manager handles:
 *   - Creating enchanted items from set definitions
 *   - Detecting active set bonuses in a raid party
 *   - Providing bonus values for the combat engine
 */
public class EnchantedSetManager {

    private static final String TAG = "EnchantedSetManager";

    public static final String ENCHANTED_PREFIX = "enchanted_";

    // ── Set definitions ──

    public static final String[] SET_NAMES = {
        "dragonscale", "shadowsteel", "holy_radiance", "infernal"
    };

    public static final String[] SET_DISPLAY_NAMES = {
        "Dragonscale", "Shadowsteel", "Holy Radiance", "Infernal"
    };

    public static final String[] PIECE_TYPES = {"sword", "shield"};

    // ── Set bonus data (mirrors enchanted_sets.json) ──

    /**
     * Holds the computed set bonuses for an active raid party.
     * Populated by checkSetBonuses() at raid start.
     */
    public static class ActiveSetBonuses {
        // Which sets are active (both pieces in party)
        public boolean dragonscale = false;
        public boolean shadowsteel = false;
        public boolean holyRadiance = false;
        public boolean infernal = false;

        // Which party slots hold each set's pieces (for holder-specific bonuses)
        public int dragonscaleHolder = -1;
        public int shadowsteelHolder = -1;
        public int holyRadianceHolder = -1;
        public int infernalHolder = -1;

        // Aggregate bonuses
        public float partyDamageBonus = 0f;      // multiplicative bonus to all party damage
        public float partyHpBonus = 0f;           // multiplicative bonus to all party HP
        public float partyDamageReduction = 0f;   // % reduction to incoming damage
        public float partyHealPercent = 0f;       // % of max HP healed periodically
        public float healIntervalSeconds = 0f;    // heal tick interval
        public float reflectPercent = 0f;         // % of damage reflected back

        // Per-slot holder bonuses (indexed by party slot)
        public float[] holderDamageBonus = new float[4];

        public boolean hasAnyBonus() {
            return dragonscale || shadowsteel || holyRadiance || infernal;
        }
    }

    // ── Data (loaded from JSON) ──
    private Map<String, Object> setData;

    // ── Active bonuses (computed at raid start) ──
    private ActiveSetBonuses activeBonuses = new ActiveSetBonuses();

    private final EventManager eventManager;

    public EnchantedSetManager(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    // ══════════════════════════════════════════════════════════════
    // DATA LOADING
    // ══════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    public void loadData(String json) {
        if (json == null) {
            setData = new HashMap<>();
            return;
        }
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            Map<String, Object> root = gson.fromJson(json, Map.class);
            setData = (Map<String, Object>) root.get("sets");
            if (setData == null) setData = new HashMap<>();
            Gdx.app.log(TAG, "Loaded enchanted sets: " + setData.keySet());
        } catch (Exception e) {
            Gdx.app.error(TAG, "Failed to load enchanted set data", e);
            setData = new HashMap<>();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ITEM CREATION
    // ══════════════════════════════════════════════════════════════

    /**
     * Creates an enchanted item from a set.
     *
     * @param setName  "dragonscale", "shadowsteel", "holy_radiance", "infernal"
     * @param pieceType "sword" or "shield"
     * @return a new Item, or null if set/piece not found
     */
    @SuppressWarnings("unchecked")
    public Item createEnchantedItem(String setName, String pieceType) {
        Map<String, Object> set = getSetData(setName);
        if (set == null) {
            Gdx.app.log(TAG, "createEnchantedItem: unknown set " + setName);
            return null;
        }

        Map<String, Object> pieces = (Map<String, Object>) set.get("pieces");
        if (pieces == null) return null;

        Map<String, Object> piece = (Map<String, Object>) pieces.get(pieceType);
        if (piece == null) {
            Gdx.app.log(TAG, "createEnchantedItem: no " + pieceType + " in " + setName);
            return null;
        }

        String itemType = ENCHANTED_PREFIX + setName + "_" + pieceType;
        String id = itemType + "_" + System.currentTimeMillis()
            + "_" + (int)(Math.random() * 10000);

        String name = (String) piece.get("name");
        String desc = (String) piece.get("description");
        int bonusDmg = toInt(piece, "bonusDamage");
        int bonusHp = toInt(piece, "bonusHp");
        String sprite = (String) piece.get("spritePath");

        Item item = new Item(id, itemType, 1, name, desc,
            bonusDmg, bonusHp, false, sprite);

        Gdx.app.log(TAG, "Created enchanted item: " + name
            + " (" + setName + " " + pieceType + ")");
        return item;
    }

    /**
     * Creates a random enchanted piece from the given set.
     * 50/50 chance of sword or shield.
     */
    public Item createRandomPiece(String setName) {
        if ("any".equals(setName)) {
            setName = SET_NAMES[(int)(Math.random() * SET_NAMES.length)];
        }
        String piece = Math.random() < 0.5 ? "sword" : "shield";
        return createEnchantedItem(setName, piece);
    }

    // ══════════════════════════════════════════════════════════════
    // SET BONUS DETECTION
    // ══════════════════════════════════════════════════════════════

    /**
     * Checks which set bonuses are active for the given raid party.
     * Call at raid start and store the result.
     *
     * A set is active when both its sword and shield pieces are
     * equipped on ANY units in the raid party (can be different units).
     *
     * @param partyUnitIds array of 4 unit IDs (nulls for empty slots)
     * @return computed bonuses
     */
    public ActiveSetBonuses checkSetBonuses(String[] partyUnitIds) {
        ActiveSetBonuses bonuses = new ActiveSetBonuses();
        Inventory inv = eventManager.getInventory();
        if (inv == null) return bonuses;

        // Track which set pieces are present and on which slot
        // setName → { "sword": slotIndex, "shield": slotIndex }
        Map<String, Map<String, Integer>> setPieces = new HashMap<>();

        for (int slot = 0; slot < 4; slot++) {
            if (partyUnitIds[slot] == null) continue;

            Item equipped = inv.getEquippedItem(partyUnitIds[slot]);
            if (equipped == null) continue;

            String itemType = equipped.getType();
            if (itemType == null || !itemType.startsWith(ENCHANTED_PREFIX)) continue;

            // Parse: "enchanted_dragonscale_sword" → set="dragonscale", piece="sword"
            String remainder = itemType.substring(ENCHANTED_PREFIX.length());
            int lastUnderscore = remainder.lastIndexOf('_');
            if (lastUnderscore < 0) continue;

            String setName = remainder.substring(0, lastUnderscore);
            String pieceType = remainder.substring(lastUnderscore + 1);

            Map<String, Integer> pieces = setPieces.computeIfAbsent(
                setName, k -> new HashMap<>());
            pieces.put(pieceType, slot);
        }

        // Check for complete sets (both sword and shield present)
        for (Map.Entry<String, Map<String, Integer>> entry : setPieces.entrySet()) {
            String setName = entry.getKey();
            Map<String, Integer> pieces = entry.getValue();

            if (pieces.containsKey("sword") && pieces.containsKey("shield")) {
                // Set is active!
                int swordSlot = pieces.get("sword");
                int shieldSlot = pieces.get("shield");
                // "Holder" = the unit with the sword (primary attacker)
                int holder = swordSlot;

                applySetBonus(bonuses, setName, holder);
                Gdx.app.log(TAG, "Set bonus active: " + setName
                    + " (sword slot " + swordSlot + ", shield slot " + shieldSlot + ")");
            }
        }

        activeBonuses = bonuses;
        return bonuses;
    }

    @SuppressWarnings("unchecked")
    private void applySetBonus(ActiveSetBonuses bonuses, String setName, int holderSlot) {
        Map<String, Object> set = getSetData(setName);
        if (set == null) return;

        Map<String, Object> bonus = (Map<String, Object>) set.get("setBonus");
        if (bonus == null) return;

        switch (setName) {
            case "dragonscale":
                bonuses.dragonscale = true;
                bonuses.dragonscaleHolder = holderSlot;
                bonuses.partyDamageBonus += toFloat(bonus, "partyDamageBonus");
                bonuses.partyHpBonus += toFloat(bonus, "partyHpBonus");
                break;

            case "shadowsteel":
                bonuses.shadowsteel = true;
                bonuses.shadowsteelHolder = holderSlot;
                bonuses.holderDamageBonus[holderSlot] += toFloat(bonus, "holderDamageBonus");
                bonuses.partyDamageReduction += toFloat(bonus, "partyDamageReduction");
                break;

            case "holy_radiance":
                bonuses.holyRadiance = true;
                bonuses.holyRadianceHolder = holderSlot;
                bonuses.partyHealPercent += toFloat(bonus, "partyHealPercent");
                bonuses.healIntervalSeconds = toFloat(bonus, "healIntervalSeconds");
                break;

            case "infernal":
                bonuses.infernal = true;
                bonuses.infernalHolder = holderSlot;
                bonuses.holderDamageBonus[holderSlot] += toFloat(bonus, "holderDamageBonus");
                bonuses.reflectPercent += toFloat(bonus, "reflectPercent");
                break;
        }
    }

    public ActiveSetBonuses getActiveBonuses() { return activeBonuses; }
    public void clearActiveBonuses() { activeBonuses = new ActiveSetBonuses(); }

    // ══════════════════════════════════════════════════════════════
    // ITEM TYPE CHECKS
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns true if the item type is an enchanted set piece.
     */
    public static boolean isEnchantedItem(String itemType) {
        return itemType != null && itemType.startsWith(ENCHANTED_PREFIX);
    }

    /**
     * Returns the set name for an enchanted item type, or null.
     * "enchanted_dragonscale_sword" → "dragonscale"
     */
    public static String getSetName(String itemType) {
        if (!isEnchantedItem(itemType)) return null;
        String remainder = itemType.substring(ENCHANTED_PREFIX.length());
        int lastUnderscore = remainder.lastIndexOf('_');
        if (lastUnderscore < 0) return null;
        return remainder.substring(0, lastUnderscore);
    }

    /**
     * Returns the piece type ("sword"/"shield") for an enchanted item type,
     * or null. "enchanted_dragonscale_sword" -> "sword". Mirrors
     * getSetName() (same parse, other half of the split) — added for
     * EventManager.refineItem(), which needs both halves to look up the
     * canonical piece via createEnchantedItem(setName, pieceType).
     */
    public static String getPieceType(String itemType) {
        if (!isEnchantedItem(itemType)) return null;
        String remainder = itemType.substring(ENCHANTED_PREFIX.length());
        int lastUnderscore = remainder.lastIndexOf('_');
        if (lastUnderscore < 0) return null;
        return remainder.substring(lastUnderscore + 1);
    }

    /**
     * Returns the display name for a set.
     */
    public static String getSetDisplayName(String setName) {
        for (int i = 0; i < SET_NAMES.length; i++) {
            if (SET_NAMES[i].equals(setName)) return SET_DISPLAY_NAMES[i];
        }
        return setName;
    }

    /**
     * Returns the set bonus description text.
     */
    @SuppressWarnings("unchecked")
    public String getSetBonusDescription(String setName) {
        Map<String, Object> set = getSetData(setName);
        if (set == null) return null;
        Map<String, Object> bonus = (Map<String, Object>) set.get("setBonus");
        if (bonus == null) return null;
        return (String) bonus.get("description");
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private Map<String, Object> getSetData(String setName) {
        if (setData == null) return null;
        Object data = setData.get(setName);
        if (data instanceof Map) return (Map<String, Object>) data;
        return null;
    }

    private int toInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Double) return ((Double) val).intValue();
        if (val instanceof Number) return ((Number) val).intValue();
        return 0;
    }

    private float toFloat(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Double) return ((Double) val).floatValue();
        if (val instanceof Number) return ((Number) val).floatValue();
        return 0f;
    }
}
