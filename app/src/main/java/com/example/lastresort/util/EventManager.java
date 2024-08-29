package com.example.lastresort.util;

import android.util.Log;

import com.example.lastresort.data.ChestData;
import com.example.lastresort.data.FacilityData;
import com.example.lastresort.data.GenData;
import com.example.lastresort.data.MonsterData;
import com.example.lastresort.data.PrinceData;
import com.example.lastresort.data.StorageData;
import com.example.lastresort.data.TokenData;
import com.example.lastresort.data.UnitData;
import com.example.lastresort.database.JsonManager;
import com.example.lastresort.grid.Cell;
import com.example.lastresort.grid.Grid;
import com.example.lastresort.model.Chest;
import com.example.lastresort.model.Facility;
import com.example.lastresort.model.GameObject;
import com.example.lastresort.model.Monster;
import com.example.lastresort.model.Prince;
import com.example.lastresort.model.Storage;
import com.example.lastresort.model.Token;
import com.example.lastresort.model.Unit;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

public class EventManager {
    private Grid gridInstance;
    private GameDataLoader GDLInstance;
    private final UUIDGenerator idGenerator  = UUIDGenerator.getInstance();
    private JsonManager jsonInstance;
    private ResourceManager resourceManager;
    private GridObjectManager GRID_OBJECT_MANAGER;
    private BattleFieldManager BATTLE_FIELD_MANAGER;

    private GameObject selectedObject = null;

   public EventManager(JsonManager jsonManager)
    {
        this.jsonInstance = jsonManager;
        this.gridInstance = new Grid(jsonInstance);
        this.GDLInstance  = new GameDataLoader(jsonInstance);
        this.resourceManager = new ResourceManager(jsonManager);
        this.GRID_OBJECT_MANAGER = new GridObjectManager(jsonManager);
        this.BATTLE_FIELD_MANAGER = new BattleFieldManager(this, jsonManager, gridInstance);
        spawnObject("prince", 1, 0, 0);
        spawnObject("archer", 7, 0, 1);
        spawnObject("archer", 7, 1, 0);
        spawnObject("archeryrange", 1, 1, 1);
        spawnObject("archeryrange", 1, 3, 3);
        spawnObject("farmhouse", 3, 2, 2);
        spawnObject("monastery", 4, 0, 3);
        spawnObject("archer", 2,3,3);
        spawnObject("griffinnest",3,0,3);
    }

    //GETTERS

    public Grid getGridInstance() {
        return gridInstance;
    }

    public GridObjectManager getGRID_OBJECT_MANAGER() {
        return GRID_OBJECT_MANAGER;
    }

    //METHODS
    public void spawnObject(String type, int level, int x, int y)
    {
        int[] XoY = gridInstance.getClosestEmptyCell(x, y);
        if (!(XoY == null))
        {
            String id = idGenerator.generateFormattedID(type, level);
            Log.d("UUID", "ID OF THE OBJECT TO BE SPAWNED" + id);
            Log.d("Spawning Object", "Type of object " + type + " and level " + level);
            switch (type)
            {
                case "archer":
                case "farmer":
                case "spearman":
                case "griffin":
                case "eldergriffin":
                case "monk":
                case "swordsman":
                {
                    //TODO FIX THIS METHOD FETCHING DATA OF TYPE TYPE AND LVL 0
                    UnitData unitData = (UnitData) GDLInstance.getGameData(type, level);

                    if (unitData == null) {
                        Log.e("Error Tag", "unitData is null for type: " + type + " and level: " + level);
                    } else {
                        Log.i("Info Tag", "unitData retrieved successfully for type: " + type + " and level: " + level);
                    }

                    Unit unit = new Unit(unitData, type, id, level, XoY[0], XoY[1]);
                    resourceManager.modifyResourceRate(unit.getResource(), unit.getGen_rate(), true);
                    Log.d("Resources for Unit", "Resource type " + unit.getResource() + " amount " + unit.getGen_rate());
                    gridInstance.setOnCell(id, XoY[0], XoY[1]);
                    GRID_OBJECT_MANAGER.addObject(id, unit);
                    BATTLE_FIELD_MANAGER.increaseCounter(unitData.getNemesis(), unitData.getNemesis_Rate());
                    break;
                }
                case "sawmill": case"quarry": case "ironmine":
                {
                    StorageData storageData = (StorageData) GDLInstance.getGameData(type, level);
                    Storage     storage = new Storage(storageData, type, id, level, XoY[0], XoY[1]);
                    resourceManager.modifyResourcePoolSize(type, storageData.getStorage_size(), true);
                    gridInstance.setOnCell(id, XoY[0], XoY[1]);
                    GRID_OBJECT_MANAGER.addObject(id, storage);
                    break;
                }
                case "archeryrange": case "farmhouse": case "barracks": case"griffinnest": case"monastery":
                {
                    FacilityData facilityData = (FacilityData) GDLInstance.getGameData(type, level);
                    Facility facility         = new Facility(facilityData, type, id, level, XoY[0], XoY[1]);
                    gridInstance.setOnCell(id, XoY[0], XoY[1]);
                    GRID_OBJECT_MANAGER.addObject(id, facility);
                    break;
                }
                case "imp": case "scarecrow": case "stonegolem": case "efreet":
                {
                    MonsterData monsterData = (MonsterData) GDLInstance.getGameData(type, level);
                    Monster monster = new Monster(monsterData, type, id, level,XoY[0], XoY[1]);
                    gridInstance.setOnCell(id,XoY[0], XoY[1]);
                    GRID_OBJECT_MANAGER.addObject(id, monster);
                    break;
                }
                case "woodchest": case "wheatchest": case "stonechest": case "firechest":
                {
                    ChestData chestData = (ChestData) GDLInstance.getGameData(type, level);
                    Chest chest = new Chest(chestData, type, id, level, XoY[0], XoY[1]);
                    gridInstance.setOnCell(id,XoY[0], XoY[1]);
                    GRID_OBJECT_MANAGER.addObject(id, chest);
                    break;
                }
                case "wood_token": case "wheat_token": case "stone_token": case "fire_token":
                {
                    TokenData tokenData = (TokenData) GDLInstance.getGameData(type, level);
                    Token token = new Token(tokenData, type, id, level,XoY[0], XoY[1]);
                    gridInstance.setOnCell(id,XoY[0], XoY[1]);
                    GRID_OBJECT_MANAGER.addObject(id, token);
                    break;
                }
                case "prince":
                    PrinceData princeData = (PrinceData) GDLInstance.getGameData(type, level);
                    Prince prince = new Prince(princeData, type, id, level,XoY[0], XoY[1]);
                    gridInstance.setOnCell(id, XoY[0], XoY[1]);
                    GRID_OBJECT_MANAGER.addObject(id,prince);
                    Log.d("SPAWNSUCCES", "PRINCE SPAWNED AT" + XoY[0] + XoY[1]);
                    break;
            }
            for (String k: resourceManager.getConsumableMap().keySet()
                 ) {
                Log.d("Resource Manager Log", "Resource " + k + " amount: " +resourceManager.getAmount(k)+
                        " rate: " +resourceManager.getRate(k)+
                        " poolsize: " +resourceManager.getPoolSize(k)
                );
            }
        }
        else
        {

            //TODO PUT THEM IN THE QUEUE
        }
    }

    public void removeObject(String id)
    {
        GameObject object = GRID_OBJECT_MANAGER.getObject(id);
        int x = object.getxPos();
        int y = object.getyPos();
        gridInstance.setOnCell("default_tile", x, y);
        GRID_OBJECT_MANAGER.removeObject(id);
        switch (object.getType())
        {
            case "archer": case "farmer": case "spearman": case "griffin": case "eldergriffin": case "monk": case "swordsman":
            {
                UnitData unitData = (UnitData) GDLInstance.getGameData(object.getType(), object.getLvl());
                resourceManager.modifyResourceRate(unitData.getResource(), unitData.getGen_rate(), false);
                BATTLE_FIELD_MANAGER.increaseXP(unitData.getXP_Rate());
                break;
            }
            case "sawmill": case "quarry": case "ironmine":
            {
                StorageData storageData = (StorageData) GDLInstance.getGameData(object.getType(), object.getLvl());
                resourceManager.modifyResourcePoolSize(object.getType(), storageData.getStorage_size(), false);
                break;
            }
            case "archeryrange": case "farmhouse": case "barracks": case "griffinnest": case "monastery":
            {
                FacilityData facilityData = (FacilityData) GDLInstance.getGameData(object.getType(), object.getLvl());
                //TODO in next update implement a counter for rewards when removing facilities
                break;
            }
            case "imp": case "scarecrow": case "stonegolem": case "efreet":
            {
                Monster monster = (Monster) GRID_OBJECT_MANAGER.getObject(id);
                if (monster.getHp() <= 0)
                {
                    MonsterData monsterData = (MonsterData) GDLInstance.getGameData(object.getType(), object.getLvl());
                    spawnObject(monsterData.getReward(), 1, x, y);
                }
                break;
            }
            case "woodchest": case "wheatchest": case "stonechest": case "firechest":
            {
                //TODO implement a reward for removing chests
                // ChestData chestData = (ChestData) GDLInstance.getGameData(object.getType(), object.getLvl());
                break;
            }
            case "wood_token": case "wheat_token": case "stone_token": case "fire_token":
            {
                TokenData tokenData = (TokenData) GDLInstance.getGameData(object.getType(), object.getLvl());
                resourceManager.increaseToken(tokenData.getTokenType(), tokenData.getValue());
                break;
            }
            case "prince":
            {
                PrinceData princeData = (PrinceData) GDLInstance.getGameData(object.getType(), object.getLvl());
                Prince prince = new Prince(princeData, object.getType(), id, object.getLvl(), x, y);
                gridInstance.setOnCell(id, x, y);
                GRID_OBJECT_MANAGER.addObject(id, prince);
                break;
            }
        }
        //TODO String something = object.onDelete();
        //TODO create an onDelete method in GameObject class to handle the removal (sending to battlefiled) of the objects
        //TODO ASAP call update() or draw() method;
    }

    public void tap(String id)
    {
        for (Cell[] cell: gridInstance.getCells()) {
            for (Cell c: cell
                 ) {
                Log.d("EVENT MANAGER GRID TAP LOG:" , "THE FOLLOWING ITEM is at x: " + c.getX() + " y " + c.getY() + ":" + c.getOccupant());
            }
        }
        String actualId = id.split("_")[0];
        Log.d("Event manager", "The following id has been tapped:"  + id);
        GameObject object = GRID_OBJECT_MANAGER.getObject(id);
        switch (object.getType())
        {
            case "archer": case "farmer": case "spearman": case "griffin": case "eldergriffin": case "monk": case "swordsman":
            {
                //DOES NOTHING
                break;
            }
            case "sawmill": case "quarry": case "ironmine":
            {
                //DOES NOTHING at the moment
                break;
            }
            case "archeryrange": case "farmhouse": case "barracks": case "griffinnest": case "monastery":
            {
                //TODO rething selected object logic
                Facility facility = (Facility) GRID_OBJECT_MANAGER.getObject(id);
                if (resourceManager.reduceResource(facility.getTapCost1(), facility.getTapCost2(), facility.getTapCost3())) {
                    String[] unitString = facility.spawn(GDLInstance.getSpawnConfiguration(facility.getType() + "_" + facility.getLvl()));
                    spawnObject(unitString[0], Integer.parseInt(unitString[1]), facility.getxPos(), facility.getyPos());
                }

                break;
            }
            case "imp": case "scarecrow": case "stonegolem": case "efreet":
            {
                //DOES NOTHING
                break;
            }
            case "woodchest": case "wheatchest": case "stonechest": case "firechest":
            {
                Chest chest = (Chest) GRID_OBJECT_MANAGER.getObject(id);
                if (chest.getTap_count() > 0)
                {
                    String[] tokenString = chest.spawn(GDLInstance.getSpawnConfiguration(chest.getId() + "_1"));
                    if(chest.decreaseTap_Count())
                    {
                        removeObject(chest.getId());
                    }
                    spawnObject(tokenString[0], Integer.parseInt(tokenString[1]), chest.getxPos(), chest.getyPos());
                }
                break;
            }
            case "wood_token": case "wheat_token": case "stone_token": case "fire_token":
            {
                TokenData tokenData = (TokenData) GDLInstance.getGameData(object.getType(), object.getLvl());
                resourceManager.increaseToken(tokenData.getTokenType(), tokenData.getValue());
                break;
            }
        }
    }

    public void swapOrMerge(String origin, String target)
    {
        GameObject originGO = GRID_OBJECT_MANAGER.getObject(origin);
        GameObject targetGO = GRID_OBJECT_MANAGER.getObject(target);

        if((originGO.getLvl() < originGO.getMaxLVL()) && Objects.equals(originGO.getType(), targetGO.getType()) && originGO.getLvl() == targetGO.getLvl())
        {
            int x = targetGO.getxPos();
            int y = targetGO.getyPos();
            removeObject(origin);
            removeObject(target);
            spawnObject(targetGO.getType(), targetGO.getLvl() + 1, x, y);
        }
        else if ("\"archer\", \"farmer\", \"spearman\", \"griffin\", \"eldergriffin\", \"monk\", \"swordsman\"".contains(originGO.getType())) {
            Unit originUnit = (Unit) GRID_OBJECT_MANAGER.getObject(origin);
            if(originUnit.getNemesis().equals(targetGO.getType()))
            {
                Monster monster = (Monster) GRID_OBJECT_MANAGER.getObject(target);
                if(monster.reduceHp(originUnit.getDamage()))
                    removeObject(monster.getId());
                removeObject(origin);
            }
            else
            {
                Cell ocell = gridInstance.getCell(originGO.getxPos(), originGO.getyPos());
                ocell.setX(targetGO.getxPos());
                ocell.setY(targetGO.getyPos());
                ocell.setOccupant(target);
                GRID_OBJECT_MANAGER.update(ocell.getOccupant(), ocell.getX(), ocell.getY());

                Cell tcell = gridInstance.getCell(targetGO.getxPos(), targetGO.getyPos());
                tcell.setX(originGO.getxPos());
                tcell.setY(originGO.getyPos());
                tcell.setOccupant(origin);
                GRID_OBJECT_MANAGER.update(tcell.getOccupant(), tcell.getX(), tcell.getY());
            }
        }
        else if(targetGO.getType().equals("prince"))
        {
            removeObject(origin);
        }
        else
        {
            Cell ocell = gridInstance.getCell(originGO.getxPos(), originGO.getyPos());
            ocell.setX(targetGO.getxPos());
            ocell.setY(targetGO.getyPos());
            ocell.setOccupant(target);

            Cell tcell = gridInstance.getCell(targetGO.getxPos(), targetGO.getyPos());
            tcell.setX(originGO.getxPos());
            tcell.setY(originGO.getyPos());
            tcell.setOccupant(origin);
        }
    }

    public ResourceManager getResourceManager() {
        return resourceManager;
    }
}
