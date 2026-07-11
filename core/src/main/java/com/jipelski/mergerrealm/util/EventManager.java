package com.jipelski.mergerrealm.util;

import com.badlogic.gdx.Gdx;

import com.jipelski.mergerrealm.data.ChestData;
import com.jipelski.mergerrealm.data.FacilityData;
import com.jipelski.mergerrealm.data.MonsterData;
import com.jipelski.mergerrealm.data.PrinceData;
import com.jipelski.mergerrealm.data.ResourcePouchData;
import com.jipelski.mergerrealm.data.StorageData;
import com.jipelski.mergerrealm.data.TokenData;
import com.jipelski.mergerrealm.data.UnitData;
import com.jipelski.mergerrealm.database.JsonManager;
import com.jipelski.mergerrealm.grid.Cell;
import com.jipelski.mergerrealm.grid.Grid;
import com.jipelski.mergerrealm.model.Chest;
import com.jipelski.mergerrealm.model.ExplorationSlot;
import com.jipelski.mergerrealm.model.Facility;
import com.jipelski.mergerrealm.model.GameObject;
import com.jipelski.mergerrealm.model.Item;
import com.jipelski.mergerrealm.model.Monster;
import com.jipelski.mergerrealm.model.Prince;
import com.jipelski.mergerrealm.model.RaidState;
import com.jipelski.mergerrealm.model.ResourcePouch;
import com.jipelski.mergerrealm.model.Storage;
import com.jipelski.mergerrealm.model.Token;
import com.jipelski.mergerrealm.model.Unit;
import com.jipelski.mergerrealm.util.PrinceLevelConfig;
import com.jipelski.mergerrealm.util.LegendaryEvolution;
import com.jipelski.mergerrealm.util.RuneSystem;
import com.jipelski.mergerrealm.util.RaidManager;
import com.jipelski.mergerrealm.util.EnchantedSetManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class EventManager {

    private static final String TAG = "EventManager";

    // Used for type checks — avoids the broken string.contains() pattern
    /*private static final Set<String> UNIT_TYPES = new HashSet<>(Arrays.asList(
        // Base units
        "villager", "woodsman", "cook", "prospector", "mercenary",
        "carpenter", "knight", "hunter", "archer", "blacksmith",
        "bulwark", "monk", "paladin", "griffin", "wyvern",
        "dragon", "phoenix",
        // Legendary units
        "elder_villager", "lumberlord", "grand_chef", "ore_master",
        "war_veteran", "master_builder", "royal_knight", "beastmaster",
        "shadowbow", "forgemaster", "ironclad", "high_priest",
        "archangel", "storm_griffin", "venom_drake", "elder_dragon",
        "eternal_phoenix"
    ));

    public static final Set<String> PERIODIC_FACILITIES = new HashSet<>(Arrays.asList(
        "tavernboard", "griffinnest", "dragonslair"
    ));*/

    public static final Set<String> PERIODIC_FACILITIES = GameTypes.PERIODIC_FACILITIES;

    private final Grid               gridInstance;
    private final GameDataLoader     GDLInstance;
    private final UUIDGenerator      idGenerator = UUIDGenerator.getInstance();
    private final JsonManager        jsonInstance;
    private final ResourceManager    resourceManager;
    private final GridObjectManager  GRID_OBJECT_MANAGER;
    private final BattleFieldManager BATTLE_FIELD_MANAGER;
    private ExplorationManager explorationManager;

    private Inventory inventory;
    public Inventory getInventory() { return inventory; }

    private TrophyShop trophyShop;
    public TrophyShop getTrophyShop() { return trophyShop; }

    public GameDataLoader getGameDataLoader() { return GDLInstance; }
    public BattleFieldManager getBattleFieldManager() { return BATTLE_FIELD_MANAGER; }

    public RaidManager getRaidManager() { return raidManager; }
    private RuneSystem runeSystem;

    private RaidManager raidManager;

    private EnchantedSetManager enchantedSetManager;
    public EnchantedSetManager getEnchantedSetManager() { return enchantedSetManager; }

    private GoldManager goldManager;
    public GoldManager getGoldManager() { return goldManager; }

    public EventManager(JsonManager jsonManager) {
        this.jsonInstance        = jsonManager;
        this.gridInstance        = new Grid(jsonInstance);
        this.GDLInstance         = new GameDataLoader(jsonInstance);
        this.resourceManager     = new ResourceManager(jsonManager);
        this.GRID_OBJECT_MANAGER = new GridObjectManager(jsonManager);
        this.runeSystem          = new RuneSystem();
        this.goldManager         = new GoldManager(this);
        this.inventory           = new Inventory();

        List<Item> savedItems = jsonManager.loadInventoryItems("inventory_items");
        if (savedItems != null) {
            inventory.setItems(savedItems);
        }
        Map<String, String> savedEquipped = jsonManager.loadEquippedMap("inventory_equipped");
        if (savedEquipped != null) {
            inventory.setEquipped(savedEquipped);
        }
        Gdx.app.log(TAG, "Inventory loaded: " + inventory.getItemCount() + " items, "
            + inventory.getEquipped().size() + " equipped");

        java.util.Set<String> savedLocks = jsonManager.loadLockedObjects("locked_objects");
        if (savedLocks != null) {
            GRID_OBJECT_MANAGER.setLockedObjects(savedLocks);
        }

        this.BATTLE_FIELD_MANAGER = new BattleFieldManager(this, jsonManager, gridInstance, GRID_OBJECT_MANAGER);

        // Exploration
        this.explorationManager = new ExplorationManager(this);

        // Load zone and enemy definitions
        String zoneJson = jsonManager.readRawJson("exploration_zones");
        String enemyJson = jsonManager.readRawJson("exploration_enemies");
        explorationManager.loadData(zoneJson, enemyJson);

        // Load saved exploration state
        java.util.List<ExplorationSlot> savedSlots =
            jsonManager.loadExplorationSlots("exploration_slots");
        if (savedSlots != null) {
            explorationManager.setActiveSlots(savedSlots);
            Gdx.app.log(TAG, "Loaded " + savedSlots.size() + " exploration slots");
        }

        this.raidManager = new RaidManager(this);
        String raidChaptersJson = jsonManager.readRawJson("raid_chapters");
        String raidEnemiesJson = jsonManager.readRawJson("raid_enemies");
        raidManager.loadData(raidChaptersJson, raidEnemiesJson);

        // Load saved raid state
        Map<String, Integer> savedCompletion = jsonManager.loadRuneFragments("raid_completion");
        if (savedCompletion != null) raidManager.setCompletionMap(savedCompletion);

        int[] savedRaidCurrency = jsonManager.loadArray("raid_currency");
        if (savedRaidCurrency != null && savedRaidCurrency.length >= 2) {
            raidManager.setWarTrophies(savedRaidCurrency[0]);
            raidManager.setBossTokens(savedRaidCurrency[1]);
        }

        // Restore an in-progress raid (e.g. process was killed mid-raid) so
        // the party isn't permanently lost — see RaidManager.resumeFromSave.
        RaidState savedActiveRaid = jsonManager.loadRaidState("raid_active_state");
        raidManager.resumeFromSave(savedActiveRaid);

        Gdx.app.log(TAG, "RaidManager loaded");

        this.trophyShop = new TrophyShop(this);

        // Load saved state
        Map<String, Object> savedShop = jsonManager.loadShopState("trophy_shop");
        if (savedShop != null) {
            try {
                // Stock
                Object stockObj = savedShop.get("stock");
                if (stockObj instanceof Map) {
                    Map<String, Double> rawStock = (Map<String, Double>) stockObj;
                    Map<String, Integer> stock = new java.util.HashMap<>();
                    for (Map.Entry<String, Double> e : rawStock.entrySet()) {
                        stock.put(e.getKey(), e.getValue().intValue());
                    }
                    trophyShop.setCurrentStock(stock);
                }
                // Timestamps
                Object dailyObj = savedShop.get("lastDailyRefresh");
                if (dailyObj instanceof Double) {
                    trophyShop.setLastDailyRefresh(((Double) dailyObj).longValue());
                }
                Object weeklyObj = savedShop.get("lastWeeklyRefresh");
                if (weeklyObj instanceof Double) {
                    trophyShop.setLastWeeklyRefresh(((Double) weeklyObj).longValue());
                }
            } catch (Exception e) {
                Gdx.app.error(TAG, "Error loading shop state", e);
            }
        }
        trophyShop.checkRefresh(); // Apply any pending refreshes

        Gdx.app.log(TAG, "TrophyShop loaded");

        // Load gold saved state
        int[] goldState = jsonManager.loadArray("gold_state");
        if (goldState != null && goldState.length >= 3) {
            goldManager.setGold(goldState[0]);
            goldManager.setExtraExploreSlots(goldState[1]);
            goldManager.setLastDailyLoginMs(
                ((long) goldState[2] << 32) | (goldState.length > 3 ? goldState[3] & 0xFFFFFFFFL : 0));
        }

        java.util.Set<String> claimedRewards = jsonManager.loadStringSet("gold_claimed");
        if (claimedRewards != null) {
            goldManager.setClaimedRewards(claimedRewards);
        }

        // Award daily login Gold
        int dailyGold = goldManager.onDailyLogin();
        if (dailyGold > 0) {
            Gdx.app.log(TAG, "Daily login bonus: +" + dailyGold + " Gold");
        }

        Gdx.app.log(TAG, "GoldManager loaded — balance: " + goldManager.getGold());

        this.enchantedSetManager = new EnchantedSetManager(this);
        String enchantedJson = jsonManager.readRawJson("enchanted_sets");
        enchantedSetManager.loadData(enchantedJson);
        Gdx.app.log(TAG, "EnchantedSetManager loaded");

        // Load saved state
        Map<String, Integer> savedFragments = jsonManager.loadRuneFragments("rune_fragments");
        if (savedFragments != null) runeSystem.setFragmentCounts(savedFragments);

        Map<String, Integer> savedCrafted = jsonManager.loadRuneCrafted("rune_crafted");
        if (savedCrafted != null) runeSystem.setCraftedRunes(savedCrafted);

        Map<String, Map<String, Integer>> savedApplied =
            jsonManager.loadRuneApplications("rune_applications");
        if (savedApplied != null) runeSystem.setAppliedRunes(savedApplied);

        Gdx.app.log(TAG, "RuneSystem loaded");

        // Only spawn the default starting objects on a completely fresh grid.
        // If the saved grid already has objects, spawnInitialObjects() does nothing.
        spawnInitialObjectsIfNeeded();
    }


    public ExplorationManager getExplorationManager() { return explorationManager; }

    /**
     * Spawns the starting set of objects only when the saved grid is fully empty.
     * On every subsequent launch the saved grid is used instead, preventing duplicates.
     */
    /*private void spawnInitialObjectsIfNeeded() {
        boolean gridIsEmpty = true;
        for (Cell[] row : gridInstance.getCells()) {
            for (Cell cell : row) {
                if (!cell.isEmpty()) {
                    gridIsEmpty = false;
                    break;
                }
            }
            if (!gridIsEmpty) break;
        }

        if (gridIsEmpty) {
            Gdx.app.log(TAG, "Fresh grid detected — spawning initial objects");

            // Prince
            spawnObject("prince", 1, 0, 0);

            // One of each facility for testing
            spawnObject("archeryrange", 3, 1, 0);
            spawnObject("farmhouse",    3, 2, 0);
            spawnObject("barracks",     3, 3, 0);
            spawnObject("griffinnest",  3, 0, 1);
            spawnObject("monastery",    3, 1, 1);

            // One of each storage
            spawnObject("sawmill",  3, 2, 1);
            spawnObject("quarry",   3, 3, 1);
            spawnObject("ironmine", 3, 0, 2);

            // A couple units to test merging
            spawnObject("archer", 1, 1, 2);
            spawnObject("archer", 1, 2, 2);

        } else {
            Gdx.app.log(TAG, "Saved grid loaded — skipping initial spawns");
        }
    }
    */

    private void spawnInitialObjectsIfNeeded() {
        boolean gridIsEmpty = true;
        for (Cell[] row : gridInstance.getCells()) {
            for (Cell cell : row) {
                if (!cell.isEmpty()) {
                    gridIsEmpty = false;
                    break;
                }
            }
            if (!gridIsEmpty) break;
        }

        if (gridIsEmpty) {
            Gdx.app.log(TAG, "Fresh grid detected — spawning starting objects");

            // Prince always starts at position 0,0
            spawnObject("prince", 1, 0, 0);

            // Starting archery range (only facility unlocked at level 1)
            spawnObject("homestead", 1, 1, 0);

            // Two starting archers so the player can immediately merge
            spawnObject("villager", 7, 0, 1);
            spawnObject("woodsman", 7, 1, 1);
            ///*
            spawnObject("archer", 6, 2, 1);
            spawnObject("archer", 6, 1, 2);
            spawnObject("archer", 6, 2, 2);

            spawnObject("archer", 6, 2, 0);
            spawnObject("archer", 6, 0, 2);

            spawnObject("archer", 6, 0, 3);
            spawnObject("archer", 6, 1, 3);

            spawnObject("archer", 6, 2, 3);

            Item starterSword = GDLInstance.createItem("sword", 1);
            if (starterSword != null) {
                inventory.addItem(starterSword);
                Gdx.app.log(TAG, "Added starter item: " + starterSword);
            }
            Item starterPotion = GDLInstance.createItem("potion", 1);
            if (starterPotion != null) {
                inventory.addItem(starterPotion);
                Gdx.app.log(TAG, "Added starter item: " + starterPotion);
            }
            //*/

        } else {
            Gdx.app.log(TAG, "Saved grid loaded — skipping initial spawns");
        }
    }
    // GETTERS

    public Grid getGridInstance() {
        return gridInstance;
    }

    public GridObjectManager getGRID_OBJECT_MANAGER() {
        return GRID_OBJECT_MANAGER;
    }

    public ResourceManager getResourceManager() {
        return resourceManager;
    }

    // METHODS

    /**
     * Attempts to spawn a unit from the given facility.
     * Checks for grid space and resources before spawning.
     * Used by both tap-to-spawn and hold-to-spawn.
     *
     * @return true if a unit was spawned, false if blocked (no space, no resources)
     */
    public boolean spawnFromFacility(String facilityId) {
        GameObject object = GRID_OBJECT_MANAGER.getObject(facilityId);
        if (object == null) {
            Gdx.app.log(TAG, "spawnFromFacility: object not found id=" + facilityId);
            return false;
        }

        if (!isFacilityType(object.getType())) {
            Gdx.app.log(TAG, "spawnFromFacility: not a facility type=" + object.getType());
            return false;
        }

        // Check if this facility type is unlocked
        int princeLvl = BATTLE_FIELD_MANAGER.getLevel();
        if (!PrinceLevelConfig.isFacilityUnlocked(object.getType(), princeLvl)) {
            Gdx.app.log(TAG, "spawnFromFacility: " + object.getType()
                + " not yet unlocked (prince lvl " + princeLvl + ")");
            return false;
        }

        if (!gridInstance.hasEmptyCell()) {
            Gdx.app.log(TAG, "spawnFromFacility: grid full");
            return false;
        }

        Facility facility = (Facility) object;

        if (!resourceManager.reduceResource(
            facility.getTapCost1(), facility.getTapCost2(), facility.getTapCost3())) {
            Gdx.app.log(TAG, "spawnFromFacility: not enough resources");
            return false;
        }

        String[] unitString = facility.spawn(
            GDLInstance.getSpawnConfiguration(facility.getType() + "_" + facility.getLvl()));
        if (unitString != null) {
            spawnObject(unitString[0], Integer.parseInt(unitString[1]),
                facility.getxPos(), facility.getyPos());
            Gdx.app.log(TAG, "spawnFromFacility: spawned " + unitString[0]
                + " lvl " + unitString[1] + " from " + facility.getType());
            if (BATTLE_FIELD_MANAGER.getListener() != null) {
                BATTLE_FIELD_MANAGER.getListener().onUnitSpawnedFromFacility(facility.getType());
            }
            return true;
        }
        return false;
    }

    /* private boolean isFacilityType(String type) {
        switch (type) {
            case "homestead": case "lodge": case "tavernboard":
            case "barracks": case "archeryrange": case "forge":
            case "monastery": case "griffinnest": case "dragonslair":
                return true;
            default:
                return false;
        }
    } */
    private boolean isFacilityType(String type) { return GameTypes.isFacility(type); }

    public void spawnObject(String type, int level, int x, int y) {
        int[] XoY = gridInstance.getClosestEmptyCell(x, y);
        if (XoY == null) {
            Gdx.app.log(TAG, "spawnObject: grid full, cannot spawn " + type + " lvl " + level);
            // TODO: add to reward queue when full
            return;
        }

        String id = idGenerator.generateFormattedID(type, level);
        Gdx.app.log(TAG, "Spawning " + type + " lvl=" + level + " id=" + id
                + " at [" + XoY[0] + "," + XoY[1] + "]");

        switch (type) {
            case "villager": case "woodsman": case "cook": case "prospector":
            case "mercenary": case "carpenter": case "knight": case "hunter":
            case "archer": case "blacksmith": case "bulwark": case "monk":
            case "paladin": case "griffin": case "wyvern": case "dragon":
            case "phoenix": case "elder_villager": case "lumberlord": case "grand_chef":
            case "ore_master": case "war_veteran": case "master_builder":
            case "royal_knight": case "beastmaster": case "shadowbow":
            case "forgemaster": case "ironclad": case "high_priest":
            case "archangel": case "storm_griffin": case "venom_drake":
            case "elder_dragon": case "eternal_phoenix": {
                UnitData unitData = (UnitData) GDLInstance.getGameData(type, level);
                if (unitData == null) {
                    Gdx.app.error(TAG, "spawnObject: no UnitData for " + type + " lvl " + level);
                    return;
                }
                Unit unit = new Unit(unitData, type, id, level, XoY[0], XoY[1]);
                gridInstance.setOnCell(id, XoY[0], XoY[1]);
                GRID_OBJECT_MANAGER.addObject(id, unit);
                resourceManager.modifyResourceRate(unit.getResource(), unit.getGen_rate(), true);
                // Dual-resource legendaries (elder_villager, lumberlord, ...) also
                // generate a second resource — "none"/0 for everything else.
                if (unitData.getSecondaryGenRate() > 0) {
                    resourceManager.modifyResourceRate(
                        unitData.getSecondaryResource(), unitData.getSecondaryGenRate(), true);
                }
                //BATTLE_FIELD_MANAGER.increaseCounter(unitData.getNemesis(), unitData.getNemesis_Rate());
                break;
            }
            case "silo": case "timberyard": case "ironvault": {
                StorageData storageData = (StorageData) GDLInstance.getGameData(type, level);
                if (storageData == null) {
                    Gdx.app.error(TAG, "spawnObject: no StorageData for " + type + " lvl " + level);
                    return;
                }
                Storage storage = new Storage(storageData, type, id, level, XoY[0], XoY[1]);
                gridInstance.setOnCell(id, XoY[0], XoY[1]);
                GRID_OBJECT_MANAGER.addObject(id, storage);
                resourceManager.modifyResourcePoolSize(storageData.getStorage_type(), storageData.getStorage_size(), true);
                break;
            }
            case "homestead": case "lodge": case "tavernboard":
            case "barracks": case "archeryrange": case "forge":
            case "monastery": case "griffinnest": case "dragonslair": {
                FacilityData facilityData = (FacilityData) GDLInstance.getGameData(type, level);
                if (facilityData == null) {
                    Gdx.app.error(TAG, "spawnObject: no FacilityData for " + type + " lvl " + level);
                    return;
                }
                Facility facility = new Facility(facilityData, type, id, level, XoY[0], XoY[1]);
                gridInstance.setOnCell(id, XoY[0], XoY[1]);
                GRID_OBJECT_MANAGER.addObject(id, facility);
                break;
            }
            case "gremlin": case "troll": case "orc":
            case "wraith": case "demon": {
                MonsterData monsterData = (MonsterData) GDLInstance.getGameData(type, level);
                if (monsterData == null) {
                    Gdx.app.error(TAG, "spawnObject: no MonsterData for " + type + " lvl " + level);
                    return;
                }
                Monster monster = new Monster(monsterData, type, id, level, XoY[0], XoY[1]);
                gridInstance.setOnCell(id, XoY[0], XoY[1]);
                GRID_OBJECT_MANAGER.addObject(id, monster);
                break;
            }
            case "nail_chest": case "slate_chest":
            case "ingot_chest": case "relic_chest": {
                ChestData chestData = (ChestData) GDLInstance.getGameData(type, level);
                if (chestData == null) {
                    Gdx.app.error(TAG, "spawnObject: no ChestData for " + type + " lvl " + level);
                    return;
                }
                Chest chest = new Chest(chestData, type, id, level, XoY[0], XoY[1]);
                gridInstance.setOnCell(id, XoY[0], XoY[1]);
                GRID_OBJECT_MANAGER.addObject(id, chest);
                break;
            }
            case "nail_token": case "slate_token":
            case "ingot_token": case "relic_token": {
                TokenData tokenData = (TokenData) GDLInstance.getGameData(type, level);
                if (tokenData == null) {
                    Gdx.app.error(TAG, "spawnObject: no TokenData for " + type + " lvl " + level);
                    return;
                }
                Token token = new Token(tokenData, type, id, level, XoY[0], XoY[1]);
                gridInstance.setOnCell(id, XoY[0], XoY[1]);
                GRID_OBJECT_MANAGER.addObject(id, token);
                break;
            }
            case "food_pouch": case "wood_pouch": case "iron_pouch": {
                ResourcePouchData pouchData = (ResourcePouchData) GDLInstance.getGameData(type, level);
                if (pouchData == null) {
                    Gdx.app.error(TAG, "spawnObject: no ResourcePouchData for " + type + " lvl " + level);
                    return;
                }
                ResourcePouch pouch = new ResourcePouch(pouchData, type, id, level, XoY[0], XoY[1]);
                gridInstance.setOnCell(id, XoY[0], XoY[1]);
                GRID_OBJECT_MANAGER.addObject(id, pouch);
                break;
            }
            case "prince": {
                PrinceData princeData = (PrinceData) GDLInstance.getGameData(type, level);
                if (princeData == null) {
                    Gdx.app.error(TAG, "spawnObject: no PrinceData for " + type + " lvl " + level);
                    return;
                }
                Prince prince = new Prince(princeData, type, id, level, XoY[0], XoY[1]);
                gridInstance.setOnCell(id, XoY[0], XoY[1]);
                GRID_OBJECT_MANAGER.addObject(id, prince);
                Gdx.app.log(TAG, "Prince spawned at [" + XoY[0] + "," + XoY[1] + "]");
                break;
            }
            default:
                Gdx.app.log(TAG, "spawnObject: unknown type '" + type + "'");
        }
    }

    private void autoSave() {
        jsonInstance.saveGridObjects("object_map", GRID_OBJECT_MANAGER.getObjectMap());
        jsonInstance.saveArrayList("grid_array", gridInstance.getArr());
        jsonInstance.saveResources("consumable_map", resourceManager.getConsumableMap());
        jsonInstance.saveCounterMap("counter_map", BATTLE_FIELD_MANAGER.getGlobalCounter());
        jsonInstance.saveArray("progression", new int[]{
            BATTLE_FIELD_MANAGER.getLevel(),
            BATTLE_FIELD_MANAGER.getCurrent_xp(),
            BATTLE_FIELD_MANAGER.getXp_required()
        });
        jsonInstance.saveInventoryItems("inventory_items", inventory.getItems());
        jsonInstance.saveEquippedMap("inventory_equipped", inventory.getEquipped());
        jsonInstance.saveExplorationSlots("exploration_slots",
            explorationManager.getActiveSlots());
        jsonInstance.saveRuneFragments("rune_fragments", runeSystem.getFragmentCounts());
        jsonInstance.saveRuneCrafted("rune_crafted", runeSystem.getCraftedRunes());
        jsonInstance.saveRuneApplications("rune_applications", runeSystem.getAppliedRunes());
        jsonInstance.saveRuneFragments("raid_completion", raidManager.getCompletionMap());
        jsonInstance.saveArray("raid_currency", new int[]{
            raidManager.getWarTrophies(), raidManager.getBossTokens()
        });
        Map<String, Object> shopState = new java.util.HashMap<>();
        shopState.put("stock", trophyShop.getCurrentStock());
        shopState.put("lastDailyRefresh", trophyShop.getLastDailyRefresh());
        shopState.put("lastWeeklyRefresh", trophyShop.getLastWeeklyRefresh());
        jsonInstance.saveShopState("trophy_shop", shopState);

        long loginMs = goldManager.getLastDailyLoginMs();
        jsonInstance.saveArray("gold_state", new int[]{
            goldManager.getGold(),
            goldManager.getExtraExploreSlots(),
            (int)(loginMs >>> 32),
            (int)(loginMs)
        });
        jsonInstance.saveStringSet("gold_claimed", goldManager.getClaimedRewards());
    }

    public void removeObject(String id) {
        // Fetch the object BEFORE removing it — the switch needs it after removal
        GameObject object = GRID_OBJECT_MANAGER.getObject(id);
        if (object == null) {
            Gdx.app.log(TAG, "removeObject: no object found for id=" + id);
            return;
        }

        int x = object.getxPos();
        int y = object.getyPos();

        gridInstance.setOnCell("default_tile", x, y);
        GRID_OBJECT_MANAGER.removeObject(id);

        switch (object.getType()) {
            case "villager": case "woodsman": case "cook": case "prospector":
            case "mercenary": case "carpenter": case "knight": case "hunter":
            case "archer": case "blacksmith": case "bulwark": case "monk":
            case "paladin": case "griffin": case "wyvern": case "dragon":
            case "phoenix": case "elder_villager": case "lumberlord": case "grand_chef":
            case "ore_master": case "war_veteran": case "master_builder":
            case "royal_knight": case "beastmaster": case "shadowbow":
            case "forgemaster": case "ironclad": case "high_priest":
            case "archangel": case "storm_griffin": case "venom_drake":
            case "elder_dragon": case "eternal_phoenix":{
                UnitData unitData = (UnitData) GDLInstance.getGameData(
                    object.getType(), object.getLvl());
                if (unitData != null) {
                    // Subtract the rune-boosted rate (Fortune), not the base rate,
                    // so it exactly cancels whatever was actually contributed —
                    // see spawnObject (base, fresh id) and the Fortune-apply bump
                    // in InventoryMenu.onUnitSelected.
                    int effectiveGenRate = runeSystem.getBoostedGenRate(id, unitData.getGen_rate());
                    resourceManager.modifyResourceRate(
                        unitData.getResource(), effectiveGenRate, false);
                    // Dual-resource legendaries also generate a second resource —
                    // subtract raw (not rune-boosted): Fortune's InventoryMenu bump
                    // only ever adjusts the primary rate, so nothing beyond the raw
                    // secondary rate was ever added for it.
                    if (unitData.getSecondaryGenRate() > 0) {
                        resourceManager.modifyResourceRate(
                            unitData.getSecondaryResource(), unitData.getSecondaryGenRate(), false);
                    }
                }
                // Unequip item when unit is removed
                if (inventory != null) {
                    inventory.onUnitRemoved(id);
                }
                break;
            }
            case "silo": case "timberyard": case "ironvault": {
                StorageData storageData = (StorageData) GDLInstance.getGameData(object.getType(), object.getLvl());
                if (storageData != null) {
                    resourceManager.modifyResourcePoolSize(storageData.getStorage_type(), storageData.getStorage_size(), false);
                }
                break;
            }
            case "homestead": case "lodge": case "tavernboard":
            case "barracks": case "archeryrange": case "forge":
            case "monastery": case "griffinnest": case "dragonslair": {
                // TODO: implement counter for facility removal rewards
                break;
            }
            case "gremlin": case "troll": case "orc":
            case "wraith": case "demon": {
                // object is already fetched above — cast directly, no second lookup
                Monster monster = (Monster) object;
                if (monster.getHp() <= 0) {
                    MonsterData monsterData = (MonsterData) GDLInstance.getGameData(object.getType(), object.getLvl());
                    if (monsterData != null) {
                        spawnObject(monsterData.getReward(), 1, x, y);
                    }
                }
                break;
            }
            case "nail_chest": case "slate_chest":
            case "ingot_chest": case "relic_chest": {
                // TODO: implement chest removal reward
                break;
            }
            case "nail_token": case "slate_token":
            case "ingot_token": case "relic_token": {
                //TokenData tokenData = (TokenData) GDLInstance.getGameData(object.getType(), object.getLvl());
                //if (tokenData != null) {
                //resourceManager.increaseToken(tokenData.getTokenType(), tokenData.getValue());
                //}
                break;
            }
            case "food_pouch": case "wood_pouch": case "iron_pouch": {
                // Consumed via dismissToPrince, not removal — nothing to refund here
                break;
            }
            case "prince": {
                // Prince cannot be removed — put it back where it was
                PrinceData princeData = (PrinceData) GDLInstance.getGameData(object.getType(), object.getLvl());
                if (princeData != null) {
                    Prince prince = new Prince(princeData, object.getType(), id, object.getLvl(), x, y);
                    gridInstance.setOnCell(id, x, y);
                    GRID_OBJECT_MANAGER.addObject(id, prince);
                    Gdx.app.log(TAG, "Prince removal blocked — restored at [" + x + "," + y + "]");
                }
                break;
            }
        }
    }

    public void tap(String id) {
        GameObject object = GRID_OBJECT_MANAGER.getObject(id);
        if (object == null) {
            Gdx.app.log(TAG, "tap: no object found for id=" + id);
            return;
        }
        Gdx.app.log(TAG, "tap: " + id + " type=" + object.getType());

        switch (object.getType()) {
            case "villager": case "woodsman": case "cook": case "prospector":
            case "mercenary": case "carpenter": case "knight": case "hunter":
            case "archer": case "blacksmith": case "bulwark": case "monk":
            case "paladin": case "griffin": case "wyvern": case "dragon":
            case "phoenix": case "elder_villager": case "lumberlord": case "grand_chef":
            case "ore_master": case "war_veteran": case "master_builder":
            case "royal_knight": case "beastmaster": case "shadowbow":
            case "forgemaster": case "ironclad": case "high_priest":
            case "archangel": case "storm_griffin": case "venom_drake":
            case "elder_dragon": case "eternal_phoenix":
            case "silo": case "timberyard": case "ironvault":
            case "gremlin": case "troll": case "orc":
            case "wraith": case "demon":
            case "food_pouch": case "wood_pouch": case "iron_pouch": {
                // No tap action for these types TODO: implement some sort of action like a puulling up a stats card
                // Resource pouches are consumed by dragging onto the Prince, not by tapping.
                break;
            }
            case "homestead": case "lodge": case "tavernboard":
            case "barracks": case "archeryrange": case "forge":
            case "monastery": case "griffinnest": case "dragonslair": {
                break; // TODO:
                /*Facility facility = (Facility) object;
                if (!gridInstance.hasEmptyCell()) {
                    Gdx.app.log(TAG, "tap: grid full — cannot spawn unit");
                    break;
                }
                if (resourceManager.reduceResource(
                        facility.getTapCost1(), facility.getTapCost2(), facility.getTapCost3())) {
                    String[] unitString = facility.spawn(
                            GDLInstance.getSpawnConfiguration(facility.getType() + "_" + facility.getLvl()));
                    if (unitString != null) {
                        spawnObject(unitString[0], Integer.parseInt(unitString[1]),
                                facility.getxPos(), facility.getyPos());
                    }
                }
                break;*/
            }
            case "nail_chest": case "slate_chest":
            case "ingot_chest": case "relic_chest": {
                if (!gridInstance.hasEmptyCell()) {
                    Gdx.app.log(TAG, "tap: grid full — cannot open chest");
                    break;
                }
                Chest chest = (Chest) object;
                if (chest.getTap_count() > 0) {
                    String[] tokenString = chest.spawn(
                            GDLInstance.getSpawnConfiguration(chest.getType()));
                    if (chest.decreaseTap_Count()) {
                        removeObject(chest.getId());
                    }
                    if (tokenString != null) {
                        spawnObject(tokenString[0], Integer.parseInt(tokenString[1]),
                                chest.getxPos(), chest.getyPos());
                    }
                }
                break;
            }
            //case "nail_token": case "slate_token":
            //case "ingot_token": case "relic_token": {
                // Award resources then remove the token from the grid
            //    TokenData tokenData = (TokenData) GDLInstance.getGameData(object.getType(), object.getLvl());
            //    if (tokenData != null) {
            //        resourceManager.increaseToken(tokenData.getTokenType(), tokenData.getValue());
            //    }
            //    removeObject(id);
            //    break;
            //}
        }
    }

    public void swapOrMerge(String originId, String targetId) {
        GameObject originGO = GRID_OBJECT_MANAGER.getObject(originId);
        GameObject targetGO = GRID_OBJECT_MANAGER.getObject(targetId);

        if (originGO == null || targetGO == null) {
            Gdx.app.log(TAG, "swapOrMerge: null object — origin=" + originId + " target=" + targetId);
            return;
        }

        // ── Prince being dragged → always just swap (he's being repositioned) ──
        if ("prince".equals(originGO.getType())) {
            swapPositions(originGO, targetGO, originId, targetId);
            return;
        }

        // Legendary units cannot merge
        if (LegendaryEvolution.isLegendary(originGO.getType())
            && LegendaryEvolution.isLegendary(targetGO.getType())) {
            if (Objects.equals(originGO.getType(), targetGO.getType())) {
                Gdx.app.log(TAG, "Legendary units cannot merge");
                swapPositions(originGO, targetGO, originId, targetId);
                return;
            }
        }

        // ── Merge check: same type, same level, below max ──
        boolean canMerge = Objects.equals(originGO.getType(), targetGO.getType())
            && originGO.getLvl() == targetGO.getLvl()
            && originGO.getLvl() < originGO.getMaxLVL();

        if (canMerge) {
            int x = targetGO.getxPos();
            int y = targetGO.getyPos();
            String mergedType = targetGO.getType();
            int mergedLvl = targetGO.getLvl() + 1;

            // Increase nemesis counter if merging units
            if (GameTypes.isUnit(mergedType)) {
                Unit unit = (Unit) originGO;
                //int contribution = 1 << mergedLvl - 1; // 2^level (both units combined)
                int nemesis_rate = unit.getNemesis_rate() + 1;
                int contribution = 1 << nemesis_rate;
                BATTLE_FIELD_MANAGER.increaseCounter(unit.getNemesis(), contribution);
                Gdx.app.log(TAG, "Merge " + mergedType + ": +" + unit.getNemesis_rate()
                    + " to " + unit.getNemesis() + " counter");

                // Both ids are permanently consumed by the merge (a fresh id
                // is minted for the merged unit below) — clear any rune data
                // tied to them so RuneSystem.appliedRunes doesn't accumulate
                // orphaned entries forever.
                runeSystem.onUnitPermanentlyLost(originId);
                runeSystem.onUnitPermanentlyLost(targetId);
            }

            removeObject(originId);
            removeObject(targetId);
            spawnObject(mergedType, mergedLvl, x, y);
            if (BATTLE_FIELD_MANAGER.getListener() != null) {
                BATTLE_FIELD_MANAGER.getListener().onUnitMerged(mergedType, mergedLvl);
            }
            return;
        }

        // ── Unit dropped on monster → HP-based combat ──
        if (GameTypes.isUnit(originGO.getType()) && GameTypes.isMonster(targetGO.getType())) {
            Unit originUnit = (Unit) originGO;

            if (originUnit.getDamage() <= 0 || !originUnit.isAlive()) {
                Gdx.app.log(TAG, originUnit.getType() + " can't fight — swapping instead");
                swapPositions(originGO, targetGO, originId, targetId);
                return;
            }

            Monster monster = (Monster) targetGO;

            // Calculate effective damage with equipment bonus
            int baseDamage = originUnit.getDamage()
                + inventory.getEquipBonusDamage(originId);
            int effectiveDamage = runeSystem.getBoostedDamage(originId, baseDamage);

            // Unit attacks monster
            boolean monsterDied = monster.reduceHp(effectiveDamage);

            // Monster attacks unit back (only if monster survived)
            if (!monsterDied) {
                int monsterDmg = monster.getDamage();
                boolean unitDied = originUnit.takeDamage(monsterDmg);

                Gdx.app.log(TAG, originUnit.getType() + " hit " + monster.getType()
                    + " for " + effectiveDamage + " (monster HP: " + monster.getHp() + ")"
                    + " | " + monster.getType() + " hit back for " + monsterDmg
                    + " (unit HP: " + originUnit.getHp() + "/" + originUnit.getMax_hp() + ")");

                if (unitDied) {
                    BATTLE_FIELD_MANAGER.increaseXP(originUnit.getXP_Rate() / 2);
                    removeObject(originId);
                    runeSystem.onUnitPermanentlyLost(originId);
                    Gdx.app.log(TAG, originUnit.getType() + " died in combat");
                }
            } else {
                BATTLE_FIELD_MANAGER.increaseXP(originUnit.getXP_Rate() / 2);
                removeObject(targetId);
                Gdx.app.log(TAG, monster.getType() + " defeated by " + originUnit.getType()
                    + "! (unit HP: " + originUnit.getHp() + "/" + originUnit.getMax_hp() + ")");
            }

            return;
        }

        // ── Monster dropped on Prince → invaders can't be dismissed, just swap ──
        if ("prince".equals(targetGO.getType()) && isMonster(originGO.getType())) {
            Gdx.app.log(TAG, "Monster " + originGO.getType()
                + " swaps with Prince — invaders cannot be dismissed");
            swapPositions(originGO, targetGO, originId, targetId);
            return;
        }

        // ── Anything else dropped on Prince → dismiss to the Prince ──
        if ("prince".equals(targetGO.getType())) {
            dismissToPrince(originGO, originId);
            return;
        }

        // ── Default → swap positions ──
        swapPositions(originGO, targetGO, originId, targetId);
    }

    private void dismissToPrince(GameObject object, String objectId) {
        String type = object.getType();

        switch (type) {
            // ── Units: dismissed from service, Prince gains leadership XP ──
            case "villager": case "woodsman": case "cook": case "prospector":
            case "mercenary": case "carpenter": case "knight": case "hunter":
            case "archer": case "blacksmith": case "bulwark": case "monk":
            case "paladin": case "griffin": case "wyvern": case "dragon":
            case "phoenix": case "elder_villager": case "lumberlord": case "grand_chef":
            case "ore_master": case "war_veteran": case "master_builder":
            case "royal_knight": case "beastmaster": case "shadowbow":
            case "forgemaster": case "ironclad": case "high_priest":
            case "archangel": case "storm_griffin": case "venom_drake":
            case "elder_dragon": case "eternal_phoenix":{
                UnitData unitData = (UnitData) GDLInstance.getGameData(type, object.getLvl());
                if (unitData != null) {
                    int xpGain = unitData.getXP_Rate();
                    BATTLE_FIELD_MANAGER.increaseXP(xpGain);
                    Gdx.app.log(TAG, "Prince dismissed " + type + " lvl " + object.getLvl()
                        + " — gained " + xpGain + " leadership XP");
                    if (BATTLE_FIELD_MANAGER.getListener() != null) {
                        BATTLE_FIELD_MANAGER.getListener().onUnitDismissedToPrince(type);
                    }
                }
                if (LegendaryEvolution.isLegendary(type)) {
                    goldManager.onDismissLegendary();
                    Gdx.app.log(TAG, "Legendary dismissed — +" + GoldManager.EARN_DISMISS_LEGENDARY + " Gold");
                }
                removeObject(objectId);
                runeSystem.onUnitPermanentlyLost(objectId);
                break;
            }

            // ── Facilities: torn down, land reclaimed, partial resources returned ──
            case "homestead": case "lodge": case "tavernboard":
            case "barracks": case "archeryrange": case "forge":
            case "monastery": case "griffinnest": case "dragonslair": {
                FacilityData facilityData = (FacilityData) GDLInstance.getGameData(type, object.getLvl());
                if (facilityData != null) {
                    // Return half the build cost (rounded down) as a refund
                    int refund1 = facilityData.getBuildCost1() / 2;
                    int refund2 = facilityData.getBuildCost2() / 2;
                    int refund3 = facilityData.getBuildCost3() / 2;
                    resourceManager.addAmount("food", refund1);
                    resourceManager.addAmount("wood", refund2);
                    resourceManager.addAmount("iron", refund3);
                    Gdx.app.log(TAG, "Prince reclaimed " + type + " lvl " + object.getLvl()
                        + " — refunded [" + refund1 + "," + refund2 + "," + refund3 + "]");
                }
                removeObject(objectId);
                break;
            }

            // ── Storage: torn down, pool size reduced ──
            case "silo": case "timberyard": case "ironvault": {
                // removeObject already handles pool size reduction
                removeObject(objectId);
                Gdx.app.log(TAG, "Prince reclaimed storage: " + type);
                break;
            }

            // ── Tokens: collected into the royal treasury ──
            case "nail_token": case "slate_token":
            case "ingot_token": case "relic_token": {
                TokenData tokenData = (TokenData) GDLInstance.getGameData(type, object.getLvl());
                if (tokenData != null) {
                    resourceManager.increaseToken(tokenData.getTokenType(), tokenData.getValue());
                    Gdx.app.log(TAG, "Prince collected " + type + " lvl " + object.getLvl()
                        + " — +" + tokenData.getValue() + " " + tokenData.getTokenType());
                }
                removeObject(objectId);
                break;
            }

            // ── Chests: opened by royal decree ──
            case "nail_chest": case "slate_chest":
            case "ingot_chest": case "relic_chest": {
                removeObject(objectId);
                Gdx.app.log(TAG, "Prince dismissed chest: " + type);
                break;
            }

            // ── Resource pouches: emptied into the royal stores ──
            case "food_pouch": case "wood_pouch": case "iron_pouch": {
                ResourcePouchData pouchData = (ResourcePouchData) GDLInstance.getGameData(type, object.getLvl());
                if (pouchData != null) {
                    resourceManager.fillByPercent(pouchData.getResource_type(), pouchData.getFill_percent());
                    Gdx.app.log(TAG, "Prince emptied " + type + " lvl " + object.getLvl()
                        + " — +" + pouchData.getFill_percent() + "% " + pouchData.getResource_type());
                }
                removeObject(objectId);
                break;
            }

            // ── Monsters: should never reach here (handled in swapOrMerge) ──
            case "gremlin": case "troll": case "orc":
            case "wraith": case "demon": {
                Gdx.app.log(TAG, "dismissToPrince: monster " + type
                    + " should not reach here — this is a bug");
                break;
            }

            default: {
                Gdx.app.log(TAG, "dismissToPrince: unhandled type " + type + " — removing");
                removeObject(objectId);
                break;
            }
        }
    }

    /**
     * Called every frame. Updates timers on all periodic facilities.
     */
    public void updatePeriodicFacilities(float delta) {
        List<Facility> periodicList = new ArrayList<>();
        for (GameObject obj : GRID_OBJECT_MANAGER.getObjectMap().values()) {
            if (PERIODIC_FACILITIES.contains(obj.getType())) {
                periodicList.add((Facility) obj);
            }
        }

        for (Facility facility : periodicList) {
            FacilityData data = (FacilityData) GDLInstance.getGameData(
                facility.getType(), facility.getLvl());
            if (data == null || data.getTimeCost() <= 0)
            {
                continue;
            }
            facility.addSpawnTime(delta);

            if (facility.getSpawnTimer() >= data.getTimeCost()) {
                facility.resetSpawnTimer();
                periodicSpawn(facility);
            }
        }
    }

    /**
     * Spawns a unit from a periodic facility.
     * Tries adjacent cells first, then stores internally if capacity allows.
     */
    private void periodicSpawn(Facility facility) {
        // Roll what unit to spawn
        String[] unitString = facility.spawn(
            GDLInstance.getSpawnConfiguration(
                facility.getType() + "_" + facility.getLvl()));
        if (unitString == null) return;

        facility.triggerPulse();

        String unitType = unitString[0];
        int unitLevel = Integer.parseInt(unitString[1]);

        // Try to spawn on an adjacent empty cell
        int[] adjacent = getAdjacentEmptyCell(facility.getxPos(), facility.getyPos());
        if (adjacent != null) {
            spawnObject(unitType, unitLevel, adjacent[0], adjacent[1]);
            Gdx.app.log(TAG, "Periodic spawn: " + unitType + " at ["
                + adjacent[0] + "," + adjacent[1] + "] from " + facility.getType());
            return;
        }

        // No adjacent space — try to hold internally
        if (facility.getHeldCount() < facility.getHoldCapacity()) {
            facility.addHeldUnit(unitType, unitLevel);
            Gdx.app.log(TAG, "Periodic hold: " + unitType
                + " stored in " + facility.getType()
                + " (" + facility.getHeldCount() + "/" + facility.getHoldCapacity() + ")");
        } else {
            Gdx.app.log(TAG, "Periodic spawn blocked: " + facility.getType()
                + " — no adjacent space and hold full ("
                + facility.getHeldCount() + "/" + facility.getHoldCapacity() + ")");
        }
    }

    /**
     * Releases one held unit from a periodic facility to an adjacent empty cell.
     * Returns true if a unit was released.
     */
    public boolean releaseHeldUnit(String facilityId) {
        GameObject obj = GRID_OBJECT_MANAGER.getObject(facilityId);
        if (obj == null || !PERIODIC_FACILITIES.contains(obj.getType())) return false;

        Facility facility = (Facility) obj;
        if (facility.getHeldCount() <= 0) return false;

        int[] adjacent = getAdjacentEmptyCell(facility.getxPos(), facility.getyPos());
        if (adjacent == null) {
            // No adjacent space — try any empty cell on grid
            if (!gridInstance.hasEmptyCell()) {
                Gdx.app.log(TAG, "releaseHeldUnit: no space anywhere");
                return false;
            }
            adjacent = gridInstance.getClosestEmptyCell(facility.getxPos(), facility.getyPos());
            if (adjacent == null) return false;
        }

        String[] held = facility.removeHeldUnit();
        if (held == null) return false;

        spawnObject(held[0], Integer.parseInt(held[1]), adjacent[0], adjacent[1]);
        Gdx.app.log(TAG, "Released held " + held[0] + " from " + facility.getType()
            + " to [" + adjacent[0] + "," + adjacent[1] + "]"
            + " (" + facility.getHeldCount() + " remaining)");
        return true;
    }

    /**
     * Returns an empty cell adjacent to (x, y), or null if none available.
     * Checks all 8 neighbors in random order to avoid bias.
     */
    public int[] getAdjacentEmptyCell(int cx, int cy) {
        int[][] offsets = {{-1,-1},{-1,0},{-1,1},{0,-1},{0,1},{1,-1},{1,0},{1,1}};

        // Shuffle offsets for variety
        for (int i = offsets.length - 1; i > 0; i--) {
            int j = (int)(Math.random() * (i + 1));
            int[] temp = offsets[i];
            offsets[i] = offsets[j];
            offsets[j] = temp;
        }

        for (int[] off : offsets) {
            int nx = cx + off[0];
            int ny = cy + off[1];
            if (nx < 0 || nx >= gridInstance.getWidth()) continue;
            if (ny < 0 || ny >= gridInstance.getHeight()) continue;
            if (gridInstance.getCell(nx, ny).isEmpty()) {
                return new int[]{nx, ny};
            }
        }
        return null;
    }


    public RuneSystem getRuneSystem() { return runeSystem; }

    /**
     * Returns true if this is a periodic (timer-based) facility.
     */
    public boolean isPeriodicFacility(String type) {
        return PERIODIC_FACILITIES.contains(type);
    }

    /**
     * Returns true if the given type is a monster/invader.
     */
    private boolean isMonster(String type)       { return GameTypes.isMonster(type); }

    /**
     * Heals all wounded units on the grid by a percentage of their max HP.
     * Called every resource tick (15 seconds).
     */
    public void healWoundedUnits() {
        List<Unit> wounded = new ArrayList<>();
        for (GameObject obj : GRID_OBJECT_MANAGER.getObjectMap().values()) {
            if (obj instanceof Unit) {
                Unit unit = (Unit) obj;
                if (unit.isWounded()) {
                    wounded.add(unit);
                }
            }
        }
        for (Unit unit : wounded) {
            int healAmount = Math.max(1, unit.getMax_hp() / 10);
            unit.heal(healAmount);
        }
    }

    /**
     * Equips an item on a unit and adjusts the unit's max_hp.
     * Call this instead of inventory.equip() directly.
     */
    public void equipItem(String unitId, String itemId) {
        // Get current equipment to reverse its bonus
        Item oldItem = inventory.getEquippedItem(unitId);
        GameObject obj = GRID_OBJECT_MANAGER.getObject(unitId);

        if (obj instanceof Unit) {
            Unit unit = (Unit) obj;

            // Remove old item's HP bonus
            if (oldItem != null) {
                unit.setMax_hp(unit.getMax_hp() - oldItem.getBonusHp());
                // Clamp current HP if it exceeds new max
                if (unit.getHp() > unit.getMax_hp()) {
                    unit.setHp(unit.getMax_hp());
                }
            }

            // Equip new item
            inventory.equip(unitId, itemId);

            // Apply new item's HP bonus
            Item newItem = inventory.getEquippedItem(unitId);
            if (newItem != null) {
                unit.setMax_hp(unit.getMax_hp() + newItem.getBonusHp());
                // Don't auto-heal — just raise the ceiling
            }
        } else {
            // Unit not on grid (shouldn't happen but handle gracefully)
            inventory.equip(unitId, itemId);
        }
    }

    /**
     * Unequips the item from a unit and adjusts max_hp.
     */
    public void unequipItem(String unitId) {
        Item oldItem = inventory.getEquippedItem(unitId);
        GameObject obj = GRID_OBJECT_MANAGER.getObject(unitId);

        if (obj instanceof Unit && oldItem != null) {
            Unit unit = (Unit) obj;
            unit.setMax_hp(unit.getMax_hp() - oldItem.getBonusHp());
            if (unit.getHp() > unit.getMax_hp()) {
                unit.setHp(unit.getMax_hp());
            }
        }

        inventory.unequip(unitId);
    }

    /**
     * Uses a potion on a unit to heal it to full HP.
     * The potion is consumed (removed from inventory).
     *
     * @param unitId    the unit to heal
     * @param potionId  the potion item to consume
     * @return true if the potion was used successfully
     */
    public boolean usePotionOnUnit(String unitId, String potionId) {
        GameObject obj = GRID_OBJECT_MANAGER.getObject(unitId);
        if (!(obj instanceof Unit)) {
            Gdx.app.log(TAG, "usePotionOnUnit: not a unit id=" + unitId);
            return false;
        }

        Unit unit = (Unit) obj;
        if (!unit.isWounded()) {
            Gdx.app.log(TAG, "usePotionOnUnit: unit is at full HP");
            return false;
        }

        Item potion = inventory.useConsumable(potionId);
        if (potion == null) {
            return false;
        }

        unit.healToFull();
        Gdx.app.log(TAG, "Used " + potion.getName() + " on " + unit.getType()
            + " — healed to " + unit.getHp() + "/" + unit.getMax_hp());
        return true;
    }

    /**
     * Evolves a max-level unit into its legendary form using an
     * Amulet of Ascension. The amulet is consumed, the base unit
     * is removed, and the legendary unit spawns in its place.
     *
     * @param unitId   the unit to evolve (must be at max level)
     * @param amuletId the Amulet of Ascension item to consume
     * @return true if evolution was successful
     */
    public boolean evolveUnit(String unitId, String amuletId) {
        GameObject obj = GRID_OBJECT_MANAGER.getObject(unitId);
        if (obj == null || !(obj instanceof Unit)) {
            Gdx.app.log(TAG, "evolveUnit: not a unit id=" + unitId);
            return false;
        }

        Unit unit = (Unit) obj;
        String baseType = unit.getType();

        // Must be at max level
        if (unit.getLvl() < unit.getMaxLVL()) {
            Gdx.app.log(TAG, "evolveUnit: " + baseType + " is not max level ("
                + unit.getLvl() + "/" + unit.getMaxLVL() + ")");
            return false;
        }

        // Must have a legendary evolution path
        String legendaryType = LegendaryEvolution.getLegendaryType(baseType);
        if (legendaryType == null) {
            Gdx.app.log(TAG, "evolveUnit: no legendary form for " + baseType);
            return false;
        }

        // Already legendary
        if (LegendaryEvolution.isLegendary(baseType)) {
            Gdx.app.log(TAG, "evolveUnit: " + baseType + " is already legendary");
            return false;
        }

        // Consume the amulet
        Item amulet = inventory.useConsumable(amuletId);
        if (amulet == null) {
            Gdx.app.log(TAG, "evolveUnit: amulet not found or not consumable");
            return false;
        }

        // Remember position and equipped item
        int x = unit.getxPos();
        int y = unit.getyPos();
        String equippedItemId = null;
        if (inventory.hasEquippedItem(unitId)) {
            equippedItemId = inventory.getEquippedItem(unitId).getId();
        }

        // Remove the base unit
        removeObject(unitId);

        // Spawn the legendary unit at the same position
        spawnObject(legendaryType, 1, x, y);

        // Re-equip the item on the new unit if it had one
        if (equippedItemId != null) {
            // Find the newly spawned legendary unit at position (x, y)
            String newOccupant = gridInstance.getCell(x, y).getOccupant();
            if (newOccupant != null && !newOccupant.equals("default_tile")) {
                equipItem(newOccupant, equippedItemId);
            }
        }

        Gdx.app.log(TAG, "LEGENDARY EVOLUTION: " + baseType + " → " + legendaryType
            + " at [" + x + "," + y + "]");

        return true;
    }

    /**
     * Removes a unit from the grid for a raid. Unlike removeObject(),
     * this does NOT unequip items or reduce resource rates permanently.
     * The unit will be returned after the raid.
     */
    public void removeUnitForRaid(String unitId) {
        GameObject obj = GRID_OBJECT_MANAGER.getObject(unitId);
        if (obj == null) return;

        int x = obj.getxPos();
        int y = obj.getyPos();

        // Reduce resource rate temporarily — use the rune-boosted (Fortune) rate
        // so it exactly cancels what returnUnitFromRaid re-adds for this same id.
        if (obj instanceof Unit) {
            Unit unit = (Unit) obj;
            int effectiveGenRate = runeSystem.getBoostedGenRate(unitId, unit.getGen_rate());
            resourceManager.modifyResourceRate(unit.getResource(), effectiveGenRate, false);
        }

        // Remove from grid and object manager but keep equipped item mapping
        gridInstance.setOnCell("default_tile", x, y);
        GRID_OBJECT_MANAGER.removeObject(unitId);

        Gdx.app.log(TAG, "Unit removed for raid: " + unitId);
    }

    /**
     * Respawns a unit on the grid REUSING its original id, at the given HP.
     * Used to bring a unit back after a temporary removal (raid, exploration)
     * without losing anything keyed by unit id — runes (RuneSystem.appliedRunes)
     * and equipped-item links (Inventory.equipped) both key off this exact id,
     * so calling spawnObject() instead (which always mints a fresh id) silently
     * orphans both.
     *
     * @return true if the unit was respawned; false if the type/level data was
     *         missing or the grid was full (caller should handle as "unit lost"
     *         or queue it, same as any other spawn-blocked-by-full-grid case).
     */
    public boolean respawnUnitWithId(String unitId, String type, int level, int currentHp) {
        UnitData unitData = (UnitData) GDLInstance.getGameData(type, level);
        if (unitData == null) {
            Gdx.app.log(TAG, "respawnUnitWithId: no data for " + type + " lv" + level);
            return false;
        }

        int[] pos = gridInstance.getClosestEmptyCell(0, 0);
        if (pos == null) {
            Gdx.app.log(TAG, "respawnUnitWithId: grid full, cannot respawn " + unitId);
            return false;
        }

        Unit unit = new Unit(unitData, type, unitId, level, pos[0], pos[1]);
        unit.setHp(Math.max(1, currentHp)); // at least 1 HP
        gridInstance.setOnCell(unitId, pos[0], pos[1]);
        GRID_OBJECT_MANAGER.addObject(unitId, unit);
        // Same id as removeUnitForRaid/removeObject subtracted — re-add the same
        // rune-boosted rate.
        int effectiveGenRate = runeSystem.getBoostedGenRate(unitId, unit.getGen_rate());
        resourceManager.modifyResourceRate(unit.getResource(), effectiveGenRate, true);

        Gdx.app.log(TAG, "Unit respawned: " + unitId + " (" + type
            + " lv" + level + ") HP=" + currentHp + " at [" + pos[0] + "," + pos[1] + "]");
        return true;
    }

    /**
     * Returns the effective max HP for a unit, including rune bonuses.
     */
    public int getEffectiveMaxHp(String unitId) {
        GameObject obj = GRID_OBJECT_MANAGER.getObject(unitId);
        if (!(obj instanceof Unit)) return 0;
        Unit unit = (Unit) obj;
        int base = unit.getMax_hp(); // already includes equipment bonus
        return runeSystem.getBoostedMaxHp(unitId, base);
    }

    /**
     * Returns the effective damage for a unit, including equipment + rune bonuses.
     */
    public int getEffectiveDamage(String unitId) {
        GameObject obj = GRID_OBJECT_MANAGER.getObject(unitId);
        if (!(obj instanceof Unit)) return 0;
        Unit unit = (Unit) obj;
        int base = unit.getDamage() + inventory.getEquipBonusDamage(unitId);
        return runeSystem.getBoostedDamage(unitId, base);
    }


    /**
     * Swaps the grid cell occupants and updates GridObjectManager positions
     * for both objects. Previously the GridObjectManager update was missing
     * in the non-unit swap branch, leaving stored positions out of sync.
     */
    private void swapPositions(GameObject originGO, GameObject targetGO,
                               String originId, String targetId) {
        int ox = originGO.getxPos(), oy = originGO.getyPos();
        int tx = targetGO.getxPos(), ty = targetGO.getyPos();

        // Update the grid cells
        gridInstance.getCell(ox, oy).setOccupant(targetId);
        gridInstance.getCell(ox, oy).setX(ox);
        gridInstance.getCell(ox, oy).setY(oy);

        gridInstance.getCell(tx, ty).setOccupant(originId);
        gridInstance.getCell(tx, ty).setX(tx);
        gridInstance.getCell(tx, ty).setY(ty);

        // Update the object manager so stored positions stay in sync
        GRID_OBJECT_MANAGER.update(originId, tx, ty);
        GRID_OBJECT_MANAGER.update(targetId, ox, oy);
    }
}
