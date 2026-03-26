package com.jipelski.mergerrealm.util;

import com.badlogic.gdx.Gdx;

import com.jipelski.mergerrealm.database.JsonManager;
import com.jipelski.mergerrealm.model.GameObject;

import java.util.HashMap;
import java.util.Map;

public class GridObjectManager {

    private static final String TAG = "GridObjectManager";

    private final java.util.Set<String> lockedObjects = new java.util.HashSet<>();


    private final Map<String, GameObject> objectMap; // Key: ID | Value: GameObject

    public GridObjectManager(JsonManager jsonManager) {
        Map<String, GameObject> loaded = null;
        try {
            loaded = jsonManager.loadGridData("object_map");
        } catch (Exception e) {
            Gdx.app.log(TAG, "object_map failed to load — starting fresh", e);
        }

        if (loaded == null) {
            Gdx.app.log(TAG, "object_map missing or unreadable — starting with empty object map");
            objectMap = new HashMap<>();
        } else {
            objectMap = loaded;
            for (GameObject g : objectMap.values()) {
                Gdx.app.log(TAG, "Loaded object: id=" + g.getId() + " type=" + g.getType());
            }
        }
    }

    // GETTERS

    public Map<String, GameObject> getObjectMap() {
        return objectMap;
    }

    public GameObject getObject(String id) {
        return objectMap.get(id);
    }

    // METHODS

    public boolean isLocked(String id) {
        return lockedObjects.contains(id);
    }

    public void toggleLock(String id) {
        if (lockedObjects.contains(id)) {
            lockedObjects.remove(id);
            Gdx.app.log(TAG, "Unlocked: " + id);
        } else {
            lockedObjects.add(id);
            Gdx.app.log(TAG, "Locked: " + id);
        }
    }

    public void removeLock(String id) {
        lockedObjects.remove(id);
    }

    public java.util.Set<String> getLockedObjects() {
        return lockedObjects;
    }

    public void setLockedObjects(java.util.Set<String> locks) {
        lockedObjects.clear();
        if (locks != null) lockedObjects.addAll(locks);
    }

    public void addObject(String id, GameObject gameObject) {
        if (id == null || gameObject == null) {
            Gdx.app.log(TAG, "addObject: null id or object — skipping");
            return;
        }
        objectMap.putIfAbsent(id, gameObject);
        Gdx.app.log(TAG, "Added: id=" + id + " type=" + gameObject.getType());
    }

    public void removeObject(String id) {
        GameObject g = objectMap.get(id);
        if (g == null) {
            Gdx.app.log(TAG, "removeObject: no object found for id=" + id);
            return;
        }
        Gdx.app.log(TAG, "Removed: id=" + id + " type=" + g.getType());
        objectMap.remove(id);
        lockedObjects.remove(id);
    }

    /**
     * Returns true if any object of the given type exists on the grid.
     */
    public boolean hasObjectOfType(String type) {
        for (GameObject obj : objectMap.values()) {
            if (type.equals(obj.getType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Updates the stored position of an object after a swap or move.
     * Previously set yPos twice and never set xPos, silently corrupting
     * the x-coordinate of every object involved in a swap.
     */
    public void update(String id, int x, int y) {
        GameObject g = objectMap.get(id);
        if (g == null) {
            Gdx.app.log(TAG, "update: no object found for id=" + id);
            return;
        }
        g.setxPos(x);
        g.setyPos(y);
        objectMap.replace(id, g);
        Gdx.app.log(TAG, "Updated position: id=" + id + " x=" + x + " y=" + y);
    }
}
