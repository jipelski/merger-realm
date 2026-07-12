package com.jipelski.mergerrealm.model;

/**
 * A piece of loot found during exploration.
 * Can be a resource, token, item, or grid object.
 *
 * lootCategory:
 *   "resource"    — food/wood/iron (amount used)
 *   "token"       — nail/slate/ingot/relic (amount used)
 *   "item"        — sword/shield/amulet/potion/phoenix_feather (level used, amount = 1)
 *   "grid_object" — food_pouch/wood_pouch/iron_pouch (level used, amount = 1) —
 *                   placed on the grid via EventManager.spawnObject(), falling
 *                   back to the Wall Gate reward queue if the grid is full
 */
public class ExplorationLoot {

    private String lootCategory;   // "resource", "token", "item"
    private String type;           // "food", "nail", "sword", etc.
    private int amount;            // quantity for resources/tokens, 1 for items
    private int level;             // item level (1-5), 0 for resources/tokens
    private String itemId;         // unique ID for items, null for resources/tokens

    public ExplorationLoot() {}

    public ExplorationLoot(String lootCategory, String type, int amount, int level, String itemId) {
        this.lootCategory = lootCategory;
        this.type = type;
        this.amount = amount;
        this.level = level;
        this.itemId = itemId;
    }

    // Convenience constructors

    public static ExplorationLoot resource(String type, int amount) {
        return new ExplorationLoot("resource", type, amount, 0, null);
    }

    public static ExplorationLoot token(String type, int amount) {
        return new ExplorationLoot("token", type, amount, 0, null);
    }

    public static ExplorationLoot item(String type, int level, String itemId) {
        return new ExplorationLoot("item", type, 1, level, itemId);
    }

    public static ExplorationLoot gridObject(String type, int level) {
        return new ExplorationLoot("grid_object", type, 1, level, null);
    }

    // Getters

    public String getLootCategory() { return lootCategory; }
    public String getType() { return type; }
    public int getAmount() { return amount; }
    public int getLevel() { return level; }
    public String getItemId() { return itemId; }

    // Setters

    public void setLootCategory(String lootCategory) { this.lootCategory = lootCategory; }
    public void setType(String type) { this.type = type; }
    public void setAmount(int amount) { this.amount = amount; }
    public void setLevel(int level) { this.level = level; }
    public void setItemId(String itemId) { this.itemId = itemId; }

    @Override
    public String toString() {
        if ("item".equals(lootCategory)) {
            return type + " (Lv." + level + ")";
        }
        return amount + " " + type;
    }
}
