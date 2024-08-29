package com.example.lastresort.util;

import android.util.Log;

import com.example.lastresort.R;
import com.example.lastresort.database.JsonManager;
import com.example.lastresort.model.GameObject;

import java.util.HashMap;
import java.util.Map;

public class GridObjectManager {
    private final Map<String, GameObject> objectMap; //Key: ID | Value: GameObject

    //CONSTRUCTOR
    public GridObjectManager(JsonManager jsonManager)
    {
        this.objectMap = jsonManager.loadGridData("object_map");
        for (GameObject g: objectMap.values()) {
            Log.d("GRID_OBJECT_MANAGER", "The following game object exists in the grid map " + g.getType());
        }
    }

    //GETTERS

    public Map<String, GameObject> getObjectMap() {
        return objectMap;
    }

    public GameObject getObject(String id)
    {
        return objectMap.get(id);
    }

    //ADD A VALUE TO THE MAP

    public void addObject(String id, GameObject gameObject)
    {
        objectMap.putIfAbsent(id, gameObject);
        Log.d("GRID_OBJECT_MANAGER", " The following object added: " + objectMap.get(id).getId());
    }

    //REMOVE A VALUE FROM THE MAP

    public void removeObject(String id)
    {
        Log.d("GRID_OBJECT_MANAGER", " The following object removed: " + objectMap.get(id).getId());
        objectMap.remove(id);

    }

    public void update(String occupant, int x, int y) {
        GameObject g = objectMap.get(occupant);
        g.setyPos(x);
        g.setyPos(y);
        objectMap.replace(occupant, g);
    }
}
