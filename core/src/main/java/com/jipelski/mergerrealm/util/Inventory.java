package com.jipelski.mergerrealm.util;

import com.badlogic.gdx.Gdx;

import com.jipelski.mergerrealm.model.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the player's item inventory and unit equipment.
 *
 * Items are stored in a flat list (the player's bag).
 * Each unit can have one item equipped, tracked by unit ID.
 *
 * Saved/loaded via JsonManager as two structures:
 *   - "inventory_items" → List<Item> (all owned items)
 *   - "inventory_equipped" → Map<String, String> (unitId → itemId)
 */
public class Inventory {

    private static final String TAG = "Inventory";

    // All items the player owns (including equipped ones)
    private List<Item> items;

    // Maps unitId → itemId for currently equipped items
    private Map<String, String> equipped;

    public Inventory() {
        this.items = new ArrayList<>();
        this.equipped = new HashMap<>();
    }

    // ── Item management ──

    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }

    public Map<String, String> getEquipped() { return equipped; }
    public void setEquipped(Map<String, String> equipped) { this.equipped = equipped; }

    /**
     * Adds an item to the inventory.
     */
    public void addItem(Item item) {
        items.add(item);
        Gdx.app.log(TAG, "Added item: " + item.getName() + " (" + item.getId() + ")");
    }

    /**
     * Removes an item from the inventory and unequips it if equipped.
     * Returns true if the item was found and removed.
     */
    public boolean removeItem(String itemId) {
        // Unequip if currently equipped
        equipped.values().remove(itemId);

        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId().equals(itemId)) {
                Item removed = items.remove(i);
                Gdx.app.log(TAG, "Removed item: " + removed.getName());
                return true;
            }
        }
        Gdx.app.log(TAG, "removeItem: item not found id=" + itemId);
        return false;
    }

    /**
     * Returns an item by its ID, or null if not found.
     */
    public Item getItem(String itemId) {
        for (Item item : items) {
            if (item.getId().equals(itemId)) return item;
        }
        return null;
    }

    /**
     * Returns how many items are in the inventory.
     */
    public int getItemCount() {
        return items.size();
    }

    /**
     * Returns all items of a specific type (e.g. all "potion" items).
     */
    public List<Item> getItemsByType(String type) {
        List<Item> result = new ArrayList<>();
        for (Item item : items) {
            if (item.getType().equals(type)) result.add(item);
        }
        return result;
    }

    /**
     * Non-allocating count of items of a specific type — prefer this over
     * {@code getItemsByType(type).size()} in any per-frame draw path (e.g.
     * RaidPanel's combat button labels), since that allocates a fresh
     * ArrayList every call just to read its size.
     */
    public int countItemsByType(String type) {
        int count = 0;
        for (Item item : items) {
            if (item.getType().equals(type)) count++;
        }
        return count;
    }

    /**
     * Returns all unequipped items.
     */
    public List<Item> getUnequippedItems() {
        List<Item> result = new ArrayList<>();
        for (Item item : items) {
            if (!equipped.containsValue(item.getId())) {
                result.add(item);
            }
        }
        return result;
    }

    // ── Equipment management ──

    /**
     * Equips an item on a unit. If the unit already has an item,
     * the old item is unequipped (stays in inventory).
     * Returns the previously equipped item ID, or null.
     */
    public String equip(String unitId, String itemId) {
        // Verify item exists
        Item item = getItem(itemId);
        if (item == null) {
            Gdx.app.log(TAG, "equip: item not found id=" + itemId);
            return null;
        }

        if (item.isConsumable()) {
            Gdx.app.log(TAG, "equip: cannot equip consumable item " + item.getName());
            return null;
        }

        // Unequip from any other unit that has this item
        equipped.values().remove(itemId);

        // Get previous item on this unit
        String previousItemId = equipped.get(unitId);

        // Equip new item
        equipped.put(unitId, itemId);
        Gdx.app.log(TAG, "Equipped " + item.getName() + " on unit " + unitId);

        return previousItemId;
    }

    /**
     * Unequips the item from a unit. The item stays in inventory.
     * Returns the unequipped item ID, or null if nothing was equipped.
     */
    public String unequip(String unitId) {
        String itemId = equipped.remove(unitId);
        if (itemId != null) {
            Gdx.app.log(TAG, "Unequipped item " + itemId + " from unit " + unitId);
        }
        return itemId;
    }

    /**
     * Returns the item equipped on a unit, or null.
     */
    public Item getEquippedItem(String unitId) {
        String itemId = equipped.get(unitId);
        if (itemId == null) return null;
        return getItem(itemId);
    }

    /**
     * Returns true if the unit has an item equipped.
     */
    public boolean hasEquippedItem(String unitId) {
        return equipped.containsKey(unitId);
    }

    /**
     * Returns the bonus damage for a unit (from equipped item).
     */
    public int getEquipBonusDamage(String unitId) {
        Item item = getEquippedItem(unitId);
        return (item != null) ? item.getBonusDamage() : 0;
    }

    /**
     * Returns the bonus HP for a unit (from equipped item).
     */
    public int getEquipBonusHp(String unitId) {
        Item item = getEquippedItem(unitId);
        return (item != null) ? item.getBonusHp() : 0;
    }

    /**
     * Uses a consumable item (potion, phoenix feather).
     * Removes it from inventory. Returns the item if found, null otherwise.
     */
    public Item useConsumable(String itemId) {
        Item item = getItem(itemId);
        if (item == null) {
            Gdx.app.log(TAG, "useConsumable: item not found id=" + itemId);
            return null;
        }
        if (!item.isConsumable()) {
            Gdx.app.log(TAG, "useConsumable: " + item.getName() + " is not consumable");
            return null;
        }

        removeItem(itemId);
        Gdx.app.log(TAG, "Used consumable: " + item.getName());
        return item;
    }

    /**
     * Cleans up equipment references for a unit that was removed from the game.
     * Call this when a unit is dismissed or dies.
     */
    public void onUnitRemoved(String unitId) {
        String itemId = equipped.remove(unitId);
        if (itemId != null) {
            Gdx.app.log(TAG, "Unit " + unitId + " removed — item "
                + itemId + " returned to inventory");
        }
    }
}
