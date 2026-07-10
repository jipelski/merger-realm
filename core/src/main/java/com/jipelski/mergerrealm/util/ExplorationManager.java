package com.jipelski.mergerrealm.util;

import com.badlogic.gdx.Gdx;

import com.jipelski.mergerrealm.data.UnitData;
import com.jipelski.mergerrealm.model.ExplorationEvent;
import com.jipelski.mergerrealm.model.ExplorationLoot;
import com.jipelski.mergerrealm.model.ExplorationSlot;
import com.jipelski.mergerrealm.model.Item;
import com.jipelski.mergerrealm.model.Unit;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages all exploration slots.
 *
 * Handles:
 *   - Sending units on exploration (removing from grid)
 *   - Processing events over time (combat, loot, healing)
 *   - Recalling units (half-time return trip)
 *   - Collecting loot on return (resources, tokens, items to inventory)
 *   - Offline catch-up (process all missed events on resume)
 *
 * Exploration slot count unlocked by Prince level:
 *   Lv1=1, Lv5=2, Lv10=3, Lv15=4, Lv20=5, Lv25=6, Lv30=7
 */
public class ExplorationManager {

    private static final String TAG = "ExplorationManager";

    // Active exploration slots
    private List<ExplorationSlot> activeSlots;

    // Zone data (loaded from exploration_zones.json)
    private Map<String, Object> zoneData;

    // Enemy data (loaded from exploration_enemies.json)
    private Map<String, Object> enemyData;

    // References
    private final EventManager eventManager;
    private final Gson gson;

    // Slot unlock levels
    private static final int[] SLOT_UNLOCK_LEVELS = {1, 5, 10, 15, 20, 25, 30};

    /**
     * Rare drop rates per zone. Each entry is checked independently
     * after EVERY exploration event (not just item finds).
     *
     * Format: RARE_DROPS[zoneIndex][dropIndex] = probability (0.0 to 1.0)
     *
     * Zone indices:  0=forest, 1=mountain, 2=mines, 3=ruins, 4=wastes
     * Drop indices:  0=phoenix_feather, 1=amulet_of_ascension,
     *                2=rune_fragment, 3=ancient_map
     */
    private static final double[][] RARE_DROP_RATES = {
        //              feather  ascension  fragment  map
        /* forest   */ { 0.000,   0.000,     0.005,   0.000 },
        /* mountain */ { 0.000,   0.000,     0.010,   0.000 },
        /* mines    */ { 0.000,   0.000,     0.020,   0.005 },
        /* ruins    */ { 0.005,   0.000,     0.030,   0.010 },
        /* wastes   */ { 0.020,   0.005,     0.050,   0.020 },
    };

    private static final String[] ZONE_INDEX_MAP = {
        "forest", "mountain", "mines", "ruins", "wastes"
    };

    private static final String[] RUNE_FRAGMENT_TYPES = {
        "rune_fragment_might", "rune_fragment_vitality",
        "rune_fragment_fortune", "rune_fragment_swiftness"
    };

    // Flavor text for rare drops
    private static final String[][] RARE_DROP_TEXTS = {
        // Phoenix Feather
        {
            "A glowing feather drifts down from the sky!",
            "Embers swirl into the shape of a feather!",
            "A Phoenix Feather materializes in a flash of flame!"
        },
        // Amulet of Ascension
        {
            "The air crackles with ancient power — an Amulet of Ascension!",
            "A golden amulet pulses with transcendent energy!",
            "Hidden in the ruins of a forgotten altar — the Amulet of Ascension!"
        },
        // Rune Fragment
        {
            "A rune fragment glimmers among the debris!",
            "Ancient energy crystallizes into a fragment!",
            "A shard of runic power catches the light!"
        },
        // Ancient Map
        {
            "A weathered scroll reveals a hidden path — an Ancient Map!",
            "Carved into the wall — directions to a forgotten temple!",
            "An Ancient Map crumbles free from a sealed chest!"
        }
    };

    public ExplorationManager(EventManager eventManager) {
        this.eventManager = eventManager;
        this.gson = new Gson();
        this.activeSlots = new ArrayList<>();
    }

    /**
     * Load zone and enemy data from JSON strings.
     * Call this during initialization after files are read.
     */
    public void loadData(String zoneJson, String enemyJson) {
        if (zoneJson != null) {
            zoneData = gson.fromJson(zoneJson,
                new TypeToken<Map<String, Object>>() {}.getType());
        }
        if (enemyJson != null) {
            enemyData = gson.fromJson(enemyJson,
                new TypeToken<Map<String, Object>>() {}.getType());
        }
    }

    // ── Slot management ──

    public List<ExplorationSlot> getActiveSlots() { return activeSlots; }
    public void setActiveSlots(List<ExplorationSlot> slots) { this.activeSlots = slots; }

    /**
     * Returns the number of exploration slots available at the given prince level.
     */
    public static int getSlotsForLevel(int princeLvl) {
        int count = 0;
        for (int lvl : SLOT_UNLOCK_LEVELS) {
            if (princeLvl >= lvl) count++;
        }
        return count;
    }

    /**
     * Returns how many slots are currently in use.
     */
    public int getUsedSlotCount() {
        return activeSlots.size();
    }

    /**
     * Returns true if the player has an available exploration slot.
     */
    public boolean hasAvailableSlot() {
        int princeLvl = eventManager.getBattleFieldManager().getLevel();
        int baseSlots = getSlotsForLevel(princeLvl);
        int extraSlots = eventManager.getGoldManager().getExtraExploreSlots();
        int maxSlots = baseSlots + extraSlots;
        return getUsedSlotCount() < maxSlots;
    }

    // ── Sending a unit ──

    /**
     * Sends a unit on exploration. Removes it from the grid and creates a slot.
     *
     * @param unitId  The unit's ID on the grid
     * @param zone    The zone to explore ("forest", "mountain", etc.)
     * @return true if the unit was sent successfully
     */
    public boolean sendUnit(String unitId, String zone) {
        if (!hasAvailableSlot()) {
            Gdx.app.log(TAG, "No available exploration slots");
            return false;
        }

        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();
        com.jipelski.mergerrealm.model.GameObject obj = gom.getObject(unitId);
        if (obj == null || !(obj instanceof Unit)) {
            Gdx.app.log(TAG, "sendUnit: invalid unit id=" + unitId);
            return false;
        }

        Unit unit = (Unit) obj;

        // Must be able to fight: has HP and effective damage > 0 (base +
        // equipment). Lets low-level combat units (e.g. mercenary/griffin L2)
        // and equipped resource units (e.g. cook + sword) explore; still
        // excludes pre-units and unarmed 0-damage resource units.
        if (unit.getMax_hp() <= 0 || eventManager.getEffectiveDamage(unitId) <= 0) {
            Gdx.app.log(TAG, "sendUnit: unit can't fight (pre-unit, no HP, or 0 damage)");
            return false;
        }

        // Must be alive
        if (unit.getHp() <= 0) {
            Gdx.app.log(TAG, "sendUnit: unit is dead, cannot explore");
            return false;
        }

        // Create exploration slot with unit snapshot
        ExplorationSlot slot = new ExplorationSlot();
        slot.setUnitId(unitId);
        slot.setUnitType(unit.getType());
        slot.setUnitLevel(unit.getLvl());
        slot.setUnitMaxLvl(unit.getMaxLVL());
        slot.setUnitMaxHp(unit.getMax_hp());
        slot.setUnitCurrentHp(unit.getHp());
        slot.setUnitDamage(unit.getDamage());
        slot.setUnitGenRate(unit.getGen_rate());
        slot.setUnitResource(unit.getResource());
        slot.setZone(zone);
        slot.setStartTimeMs(System.currentTimeMillis());
        slot.setLastEventTimeMs(0);

        // Check for equipped item
        Inventory inv = eventManager.getInventory();
        if (inv != null) {
            Item equipped = inv.getEquippedItem(unitId);
            if (equipped != null) {
                slot.setEquippedItemId(equipped.getId());
                // Apply item bonuses to snapshot
                slot.setUnitDamage(slot.getUnitDamage() + equipped.getBonusDamage());
                slot.setUnitMaxHp(slot.getUnitMaxHp() + equipped.getBonusHp());
                slot.setUnitCurrentHp(Math.min(slot.getUnitCurrentHp(),
                    slot.getUnitMaxHp()));
            }
        }

        // Add departure log entry
        slot.addEvent(0, capitalize(unit.getType()) + " ventures into the "
            + getZoneName(zone) + "...", "nothing");

        // Remove unit from grid (resource gen rate is also removed)
        eventManager.removeObject(unitId);

        activeSlots.add(slot);
        Gdx.app.log(TAG, "Sent " + unit.getType() + " lv" + unit.getLvl()
            + " to " + zone + " (slot " + activeSlots.size() + ")");

        return true;
    }

    // ── Recalling a unit ──

    /**
     * Recalls a unit from exploration. It will take half the elapsed time to return.
     */
    public boolean recallUnit(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= activeSlots.size()) return false;

        ExplorationSlot slot = activeSlots.get(slotIndex);
        if (slot.isReturning()) {
            Gdx.app.log(TAG, "Unit already returning");
            return false;
        }
        if (slot.isDead()) {
            Gdx.app.log(TAG, "Unit is dead — collecting loot instead");
            return false;
        }

        slot.setReturning(true);
        slot.setRecallTimeMs(System.currentTimeMillis());

        long exploreSeconds = (slot.getRecallTimeMs() - slot.getStartTimeMs()) / 1000;
        long returnSeconds = exploreSeconds / 2;
        slot.addEvent(slot.getElapsedMs(),
            capitalize(slot.getUnitType()) + " is heading home... (ETA: "
                + formatSeconds(returnSeconds) + ")", "nothing");

        Gdx.app.log(TAG, "Recalled " + slot.getUnitType()
            + " — return in " + returnSeconds + "s");
        return true;
    }

    // ── Collecting results ──

    /**
     * Collects loot from a returned or dead unit.
     * If alive, respawns the unit on the grid.
     * If dead and max level, respawns at 1 HP.
     * If dead and not max level, unit is lost.
     * Removes the slot.
     *
     * @return true if collection was successful
     */
    public boolean collectResults(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= activeSlots.size()) return false;

        ExplorationSlot slot = activeSlots.get(slotIndex);

        // Must have arrived or be dead
        if (!slot.isDead() && !slot.hasArrived()) {
            Gdx.app.log(TAG, "Unit hasn't returned yet");
            return false;
        }

        // Deliver loot
        ResourceManager rm = eventManager.getResourceManager();
        Inventory inv = eventManager.getInventory();

        for (ExplorationLoot l : slot.getLoot()) {
            switch (l.getLootCategory()) {
                case "resource":
                    rm.addAmount(l.getType(), l.getAmount());
                    break;
                case "token":
                    rm.addAmount(l.getType(), l.getAmount());
                    break;
                case "item":
                    if (inv != null) {
                        // Check if this is a rune fragment
                        String runeType = RuneSystem.getRuneTypeFromFragmentType(l.getType());
                        if (runeType != null) {
                            // Add to RuneSystem fragment count (not inventory)
                            eventManager.getRuneSystem().addFragment(runeType);
                            Gdx.app.log(TAG, "Rune fragment delivered: " + runeType);
                            break; // don't create an Item for fragments
                        }

                        // Create the actual Item object from the data definition
                        GameDataLoader gdl = eventManager.getGameDataLoader();
                        Item createdItem = gdl.createItem(l.getType(), l.getLevel());
                        if (createdItem != null) {
                            // Override the ID with the one generated during exploration
                            if (l.getItemId() != null) {
                                createdItem.setId(l.getItemId());
                            }
                            inv.addItem(createdItem);
                            Gdx.app.log(TAG, "Item delivered to inventory: " + createdItem.getName());
                        } else {
                            Gdx.app.log(TAG, "Could not create item: " + l.getType() + " lv" + l.getLevel());
                        }
                    }
                    break;
            }
        }

        // Respawn unit if alive (or max level)
        boolean unitSurvived = !slot.isDead();
        boolean maxLevelSave = slot.isDead() && slot.isMaxLevel();

        if (unitSurvived || maxLevelSave) {
            int respawnHp = unitSurvived ? slot.getUnitCurrentHp() : 1;

            // Respawn REUSING the original unit id (not spawnObject(), which
            // always mints a fresh one) — runes (RuneSystem.appliedRunes) and
            // the equipped-item link (Inventory.equipped) are both keyed by
            // unit id, so a fresh id would silently orphan both.
            boolean respawned = eventManager.respawnUnitWithId(
                slot.getUnitId(), slot.getUnitType(), slot.getUnitLevel(), respawnHp);

            if (respawned) {
                // sendUnit()'s removeObject() call unequipped the item (it stays
                // in inventory, just unlinked) — re-link it now that the same id
                // is back on the grid.
                if (slot.getEquippedItemId() != null && inv != null) {
                    inv.equip(slot.getUnitId(), slot.getEquippedItemId());
                }

                Gdx.app.log(TAG, slot.getUnitType() + " returned to grid with "
                    + respawnHp + " HP"
                    + (maxLevelSave ? " (max level saved from death)" : ""));
            } else {
                // Grid full (or data missing) — queue the unit as a reward
                com.jipelski.mergerrealm.data.GenData reward =
                    new com.jipelski.mergerrealm.data.GenData(
                        slot.getUnitType(), "Returning explorer", slot.getUnitLevel());
                eventManager.getBattleFieldManager().addToQueue(reward);
                Gdx.app.log(TAG, "Grid full — queued returning " + slot.getUnitType());
            }
        } else {
            Gdx.app.log(TAG, slot.getUnitType() + " lv" + slot.getUnitLevel()
                + " was lost during exploration");
        }

        // Remove the slot
        activeSlots.remove(slotIndex);

        Gdx.app.log(TAG, "Exploration collected — " + slot.getLoot().size()
            + " loot items delivered");
        return true;
    }

    // ── Event processing ──

    /**
     * Called every frame (or every second). Processes pending events for all active slots.
     * Handles offline catch-up automatically — processes all events between
     * lastEventTimeMs and now.
     */
    public void update() {
        long now = System.currentTimeMillis();

        for (int i = activeSlots.size() - 1; i >= 0; i--) {
            ExplorationSlot slot = activeSlots.get(i);

            // Skip dead or returning units (no new events)
            if (slot.isDead() || slot.isReturning()) continue;

            int eventInterval = getZoneEventInterval(slot.getZone());
            if (eventInterval <= 0) continue;

            // Swiftness runes shorten the event interval (faster exploration cadence)
            float speedMultiplier = eventManager.getRuneSystem()
                .getExplorationSpeedMultiplier(slot.getUnitId());
            long eventIntervalMs = (long) (eventInterval * 1000L / speedMultiplier);
            long elapsed = now - slot.getStartTimeMs();
            long nextEventAt = slot.getLastEventTimeMs() + eventIntervalMs;

            // Process all missed events (handles offline catch-up)
            while (nextEventAt <= elapsed && !slot.isDead()) {
                processEvent(slot, nextEventAt);
                slot.setLastEventTimeMs(nextEventAt);
                nextEventAt += eventIntervalMs;
            }
        }
    }

    /**
     * Processes a single exploration event for a slot.
     */
    private void processEvent(ExplorationSlot slot, long eventTimeMs) {
        String eventType = rollEventType(slot.getZone());

        switch (eventType) {
            case "enemy":
                processEnemyEncounter(slot, eventTimeMs);
                break;
            case "resource":
                processResourceFind(slot, eventTimeMs);
                break;
            case "token":
                processTokenFind(slot, eventTimeMs);
                break;
            case "item":
                processItemFind(slot, eventTimeMs);
                break;
            case "heal":
                processHeal(slot, eventTimeMs);
                break;
            case "trap":
                processTrap(slot, eventTimeMs);
                break;
            case "curse":
                processCurse(slot, eventTimeMs);
                break;
            case "nothing":
            default:
                processNothing(slot, eventTimeMs);
                break;
        }

        // ── Rare drop check — runs after EVERY event ──
        if (!slot.isDead()) {
            checkRareDrops(slot, eventTimeMs);
        }

        // Decrement curse counter after each event
        if (slot.getCursedEncounters() > 0) {
            slot.setCursedEncounters(slot.getCursedEncounters() - 1);
        }
    }

    private void processEnemyEncounter(ExplorationSlot slot, long timeMs) {
        String enemyKey = rollEnemy(slot.getZone());
        if (enemyKey == null) {
            processNothing(slot, timeMs);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> enemy = (Map<String, Object>) enemyData.get(enemyKey);
        if (enemy == null) {
            processNothing(slot, timeMs);
            return;
        }

        String enemyName = (String) enemy.get("name");
        int enemyDamage = ((Number) enemy.get("damage")).intValue();
        int enemyHp = ((Number) enemy.get("hp")).intValue();
        int enemyXp = ((Number) enemy.get("xp")).intValue();

        @SuppressWarnings("unchecked")
        List<String> texts = (List<String>) enemy.get("texts");
        String encounterText = texts != null && !texts.isEmpty()
            ? texts.get((int)(Math.random() * texts.size()))
            : "Encountered a " + enemyName + "!";

        // Unit attacks enemy
        int unitDmg = slot.getExplorationDamage();
        boolean enemyKilled = unitDmg >= enemyHp;

        // Enemy attacks unit
        int damageTaken = enemyKilled ? 0 : enemyDamage;
        // If unit one-shots the enemy, no damage taken
        if (!enemyKilled) {
            damageTaken = enemyDamage;
        }

        boolean unitDied = slot.takeDamage(damageTaken);

        StringBuilder logText = new StringBuilder(encounterText);
        if (enemyKilled) {
            logText.append(" Defeated! (+").append(enemyXp).append(" XP)");
        } else {
            logText.append(" Took ").append(damageTaken).append(" damage!");
        }
        logText.append(" (HP: ").append(slot.getUnitCurrentHp())
            .append("/").append(slot.getUnitMaxHp()).append(")");

        slot.addEvent(timeMs, logText.toString(), "combat");

        // Award XP for kills
        if (enemyKilled) {
            eventManager.getBattleFieldManager().increaseXP(enemyXp);
        }

        if (unitDied) {
            if (slot.isMaxLevel()) {
                slot.addEvent(timeMs, capitalize(slot.getUnitType())
                    + " collapses but refuses to die! (Max level protection)", "death");
            } else {
                slot.addEvent(timeMs, capitalize(slot.getUnitType())
                    + " has fallen...", "death");
            }
        } else if (slot.getUnitCurrentHp() <= slot.getUnitMaxHp() * 0.2f) {
            slot.addEvent(timeMs, "⚠ HP critical — consider recalling!", "nothing");
        }
    }

    private void processResourceFind(ExplorationSlot slot, long timeMs) {
        // Get zone resource table
        String resource = rollResource(slot.getZone());
        int[] range = getResourceRange(slot.getZone(), resource);
        int amount = range[0] + (int)(Math.random() * (range[1] - range[0] + 1));

        slot.addLoot(ExplorationLoot.resource(resource, amount));
        slot.addEvent(timeMs, "Found " + amount + " " + resource + "!", "loot");
    }

    private void processTokenFind(ExplorationSlot slot, long timeMs) {
        String token = rollToken(slot.getZone());
        int[] range = getTokenRange(slot.getZone(), token);
        int amount = range[0] + (int)(Math.random() * (range[1] - range[0] + 1));

        slot.addLoot(ExplorationLoot.token(token, amount));
        slot.addEvent(timeMs, "Discovered " + amount + " "
            + capitalize(token) + "!", "loot");
    }

    private void processItemFind(ExplorationSlot slot, long timeMs) {
        int tier = getZoneItemTier(slot.getZone());
        // Random item type
        String[] itemTypes = {"sword", "shield", "amulet"};
        String itemType = itemTypes[(int)(Math.random() * itemTypes.length)];

        // Level based on tier with some randomness
        int level = Math.max(1, tier + (int)(Math.random() * 2) - 1);
        level = Math.min(level, 5);

        String itemId = "item_" + System.currentTimeMillis()
            + "_" + (int)(Math.random() * 10000);

        slot.addLoot(ExplorationLoot.item(itemType, level, itemId));
        slot.addEvent(timeMs, "Found a " + capitalize(itemType)
            + " (Lv." + level + ")!", "loot");
    }

    private void processHeal(ExplorationSlot slot, long timeMs) {
        int healPercent = getZoneHealPercent(slot.getZone());
        if (healPercent <= 0) {
            processNothing(slot, timeMs);
            return;
        }
        int before = slot.getUnitCurrentHp();
        slot.healPercent(healPercent);
        int healed = slot.getUnitCurrentHp() - before;

        String text = getRandomFlavorText(slot.getZone(), "heal");
        slot.addEvent(timeMs, text + " Restored " + healed + " HP (HP: "
            + slot.getUnitCurrentHp() + "/" + slot.getUnitMaxHp() + ")", "heal");
    }

    private void processTrap(ExplorationSlot slot, long timeMs) {
        int trapDmg = getZoneTrapDamage(slot.getZone());
        if (trapDmg <= 0) {
            processNothing(slot, timeMs);
            return;
        }

        boolean died = slot.takeDamage(trapDmg);
        String text = getRandomFlavorText(slot.getZone(), "trap");
        slot.addEvent(timeMs, text + " Took " + trapDmg + " damage! (HP: "
            + slot.getUnitCurrentHp() + "/" + slot.getUnitMaxHp() + ")", "trap");

        if (died) {
            if (slot.isMaxLevel()) {
                slot.addEvent(timeMs, capitalize(slot.getUnitType())
                    + " barely survives! (Max level protection)", "death");
            } else {
                slot.addEvent(timeMs, capitalize(slot.getUnitType())
                    + " has fallen to a trap...", "death");
            }
        }
    }

    private void processCurse(ExplorationSlot slot, long timeMs) {
        int duration = getZoneCurseDuration(slot.getZone());
        slot.setCursedEncounters(duration);

        String text = getRandomFlavorText(slot.getZone(), "curse");
        slot.addEvent(timeMs, text + " (-20% damage for "
            + duration + " encounters)", "curse");
    }

    private void processNothing(ExplorationSlot slot, long timeMs) {
        String text = getRandomFlavorText(slot.getZone(), "nothing");
        if (text.isEmpty()) text = "Continues exploring...";
        slot.addEvent(timeMs, text, "nothing");
    }

    /**
     * Rolls for rare drops after every exploration event.
     * Each rare drop type is checked independently — a single event
     * can theoretically yield multiple rare drops (astronomically unlikely).
     *
     * Rare drops are added to the slot's loot and logged as special events.
     */
    private void checkRareDrops(ExplorationSlot slot, long eventTimeMs) {
        String zone = slot.getZone();
        int zoneIndex = getZoneIndex(zone);
        if (zoneIndex < 0) return;

        double[] rates = RARE_DROP_RATES[zoneIndex];

        // ── Phoenix Feather ──
        if (rates[0] > 0 && Math.random() < rates[0]) {
            String id = "phoenix_feather_" + System.currentTimeMillis()
                + "_" + (int)(Math.random() * 10000);
            slot.addLoot(ExplorationLoot.item("phoenix_feather", 1, id));
            slot.addEvent(eventTimeMs, getRandomRareText(0), "loot");
        }

        // ── Amulet of Ascension ──
        if (rates[1] > 0 && Math.random() < rates[1]) {
            String id = "amulet_of_ascension_" + System.currentTimeMillis()
                + "_" + (int)(Math.random() * 10000);
            slot.addLoot(ExplorationLoot.item("amulet_of_ascension", 1, id));
            slot.addEvent(eventTimeMs, getRandomRareText(1), "loot");
        }

        // ── Rune Fragment (random type) ──
        if (rates[2] > 0 && Math.random() < rates[2]) {
            String fragmentType = RUNE_FRAGMENT_TYPES[
                (int)(Math.random() * RUNE_FRAGMENT_TYPES.length)];
            String id = fragmentType + "_" + System.currentTimeMillis()
                + "_" + (int)(Math.random() * 10000);
            slot.addLoot(ExplorationLoot.item(fragmentType, 1, id));

            // Include which type in the log
            String typeName = fragmentType.replace("rune_fragment_", "");
            slot.addEvent(eventTimeMs,
                getRandomRareText(2) + " (" + capitalize(typeName) + ")", "loot");
        }

        // ── Ancient Map ──
        if (rates[3] > 0 && Math.random() < rates[3]) {
            String id = "ancient_map_" + System.currentTimeMillis()
                + "_" + (int)(Math.random() * 10000);
            slot.addLoot(ExplorationLoot.item("ancient_map", 1, id));
            slot.addEvent(eventTimeMs, getRandomRareText(3), "loot");
        }

        // ── Gold nugget rare find ──
        // Only in ruins (1%) and wastes (2%)
        double goldDropRate = 0;
        if ("ruins".equals(zone)) goldDropRate = 0.01;
        else if ("wastes".equals(zone)) goldDropRate = 0.02;

        if (goldDropRate > 0 && Math.random() < goldDropRate) {
            eventManager.getGoldManager().onExplorationGoldFind();
            slot.addEvent(eventTimeMs, "A gold nugget glints in the rubble!", "loot");
        }
    }

    private int getZoneIndex(String zone) {
        for (int i = 0; i < ZONE_INDEX_MAP.length; i++) {
            if (ZONE_INDEX_MAP[i].equals(zone)) return i;
        }
        return -1;
    }

    private String getRandomRareText(int dropIndex) {
        String[] texts = RARE_DROP_TEXTS[dropIndex];
        return texts[(int)(Math.random() * texts.length)];
    }


    // ── Zone data helpers ──

    @SuppressWarnings("unchecked")
    private int getZoneEventInterval(String zone) {
        Map<String, Object> z = (Map<String, Object>) zoneData.get(zone);
        return z != null ? ((Number) z.get("eventInterval")).intValue() : 90;
    }

    @SuppressWarnings("unchecked")
    private String getZoneName(String zone) {
        Map<String, Object> z = (Map<String, Object>) zoneData.get(zone);
        return z != null ? (String) z.get("name") : zone;
    }

    @SuppressWarnings("unchecked")
    private int getZoneItemTier(String zone) {
        Map<String, Object> z = (Map<String, Object>) zoneData.get(zone);
        return z != null ? ((Number) z.get("itemTier")).intValue() : 1;
    }

    @SuppressWarnings("unchecked")
    private int getZoneHealPercent(String zone) {
        Map<String, Object> z = (Map<String, Object>) zoneData.get(zone);
        return z != null && z.containsKey("healPercent")
            ? ((Number) z.get("healPercent")).intValue() : 0;
    }

    @SuppressWarnings("unchecked")
    private int getZoneTrapDamage(String zone) {
        Map<String, Object> z = (Map<String, Object>) zoneData.get(zone);
        return z != null && z.containsKey("trapDamage")
            ? ((Number) z.get("trapDamage")).intValue() : 0;
    }

    @SuppressWarnings("unchecked")
    private int getZoneCurseDuration(String zone) {
        Map<String, Object> z = (Map<String, Object>) zoneData.get(zone);
        return z != null && z.containsKey("curseDuration")
            ? ((Number) z.get("curseDuration")).intValue() : 3;
    }

    /**
     * Rolls a random event type using the zone's event weight table.
     */
    @SuppressWarnings("unchecked")
    private String rollEventType(String zone) {
        Map<String, Object> z = (Map<String, Object>) zoneData.get(zone);
        if (z == null) return "nothing";

        List<Map<String, Object>> events = (List<Map<String, Object>>) z.get("events");
        if (events == null) return "nothing";

        int totalWeight = 0;
        for (Map<String, Object> e : events) {
            totalWeight += ((Number) e.get("weight")).intValue();
        }

        int roll = (int)(Math.random() * totalWeight);
        int cumulative = 0;
        for (Map<String, Object> e : events) {
            cumulative += ((Number) e.get("weight")).intValue();
            if (roll < cumulative) return (String) e.get("type");
        }
        return "nothing";
    }

    @SuppressWarnings("unchecked")
    private String rollEnemy(String zone) {
        Map<String, Object> z = (Map<String, Object>) zoneData.get(zone);
        if (z == null) return null;
        List<String> enemies = (List<String>) z.get("enemies");
        if (enemies == null || enemies.isEmpty()) return null;
        return enemies.get((int)(Math.random() * enemies.size()));
    }

    @SuppressWarnings("unchecked")
    private String rollResource(String zone) {
        Map<String, Object> z = (Map<String, Object>) zoneData.get(zone);
        if (z == null) return "food";
        List<Map<String, Object>> resources = (List<Map<String, Object>>) z.get("resources");
        return rollWeightedString(resources, "resource");
    }

    @SuppressWarnings("unchecked")
    private int[] getResourceRange(String zone, String resource) {
        Map<String, Object> z = (Map<String, Object>) zoneData.get(zone);
        if (z == null) return new int[]{1, 10};
        List<Map<String, Object>> resources = (List<Map<String, Object>>) z.get("resources");
        for (Map<String, Object> r : resources) {
            if (resource.equals(r.get("resource"))) {
                return new int[]{
                    ((Number) r.get("min")).intValue(),
                    ((Number) r.get("max")).intValue()
                };
            }
        }
        return new int[]{1, 10};
    }

    @SuppressWarnings("unchecked")
    private String rollToken(String zone) {
        Map<String, Object> z = (Map<String, Object>) zoneData.get(zone);
        if (z == null) return "nail";
        List<Map<String, Object>> tokens = (List<Map<String, Object>>) z.get("tokens");
        return rollWeightedString(tokens, "token");
    }

    @SuppressWarnings("unchecked")
    private int[] getTokenRange(String zone, String token) {
        Map<String, Object> z = (Map<String, Object>) zoneData.get(zone);
        if (z == null) return new int[]{1, 1};
        List<Map<String, Object>> tokens = (List<Map<String, Object>>) z.get("tokens");
        for (Map<String, Object> t : tokens) {
            if (token.equals(t.get("token"))) {
                return new int[]{
                    ((Number) t.get("min")).intValue(),
                    ((Number) t.get("max")).intValue()
                };
            }
        }
        return new int[]{1, 1};
    }

    @SuppressWarnings("unchecked")
    private String rollWeightedString(List<Map<String, Object>> entries, String key) {
        if (entries == null || entries.isEmpty()) return "food";
        int totalWeight = 0;
        for (Map<String, Object> e : entries) {
            totalWeight += ((Number) e.get("weight")).intValue();
        }
        int roll = (int)(Math.random() * totalWeight);
        int cumulative = 0;
        for (Map<String, Object> e : entries) {
            cumulative += ((Number) e.get("weight")).intValue();
            if (roll < cumulative) return (String) e.get(key);
        }
        return (String) entries.get(0).get(key);
    }

    @SuppressWarnings("unchecked")
    private String getRandomFlavorText(String zone, String eventType) {
        Map<String, Object> z = (Map<String, Object>) zoneData.get(zone);
        if (z == null) return "";
        Map<String, Object> flavors = (Map<String, Object>) z.get("flavorTexts");
        if (flavors == null) return "";
        List<String> texts = (List<String>) flavors.get(eventType);
        if (texts == null || texts.isEmpty()) return "";
        return texts.get((int)(Math.random() * texts.size()));
    }

    // ── Helpers ──

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private String formatSeconds(long seconds) {
        long min = seconds / 60;
        long sec = seconds % 60;
        return String.format("%dm %ds", min, sec);
    }

    /**
     * Returns the slot for a given unit ID, or null if not exploring.
     */
    public ExplorationSlot getSlotForUnit(String unitId) {
        for (ExplorationSlot slot : activeSlots) {
            if (slot.getUnitId().equals(unitId)) return slot;
        }
        return null;
    }

    /**
     * Returns true if the given unit is currently exploring.
     */
    public boolean isUnitExploring(String unitId) {
        return getSlotForUnit(unitId) != null;
    }

    /**
     * Use a Phoenix Feather to revive a dead exploring unit.
     * Consumes the feather from inventory.
     */
    public boolean usePhoenixFeather(int slotIndex, String featherItemId) {
        if (slotIndex < 0 || slotIndex >= activeSlots.size()) return false;

        ExplorationSlot slot = activeSlots.get(slotIndex);
        if (!slot.isDead()) return false;

        Inventory inv = eventManager.getInventory();
        if (inv == null) return false;

        Item feather = inv.useConsumable(featherItemId);
        if (feather == null) return false;

        slot.usePhoenixFeather();
        slot.addEvent(slot.getElapsedMs(),
            "A Phoenix Feather burns bright — " + capitalize(slot.getUnitType())
                + " rises from the ashes! (HP: " + slot.getUnitCurrentHp()
                + "/" + slot.getUnitMaxHp() + ")", "heal");

        Gdx.app.log(TAG, "Phoenix Feather used — " + slot.getUnitType()
            + " revived at " + slot.getUnitCurrentHp() + " HP");
        return true;
    }
}
