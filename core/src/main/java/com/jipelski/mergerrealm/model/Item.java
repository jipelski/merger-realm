package com.jipelski.mergerrealm.model;

/**
 * Represents an item that can be equipped by a unit or consumed during raids.
 *
 * Item types:
 *   sword   — bonus damage
 *   shield  — bonus HP
 *   amulet  — bonus damage + HP (smaller amounts)
 *   potion  — heals unit to full HP (consumable, destroyed on use)
 *   phoenix_feather — revives a dead unit during a raid (consumable)
 *
 * Items exist in the player's inventory (not on the grid).
 * Each unit has one item slot. Equipping replaces the current item.
 */
public class Item {

    private String id;
    private String type;          // "sword", "shield", "amulet", "potion", "phoenix_feather"
    private int level;            // 1-5
    private String name;          // display name e.g. "Iron Sword"
    private String description;
    private int bonusDamage;      // flat +damage when equipped
    private int bonusHp;          // flat +HP when equipped
    private boolean consumable;   // true for potions and feathers (destroyed on use)
    private String spritePath;    // sprite for inventory display

    public Item() {}

    public Item(String id, String type, int level, String name, String description,
                int bonusDamage, int bonusHp, boolean consumable, String spritePath) {
        this.id = id;
        this.type = type;
        this.level = level;
        this.name = name;
        this.description = description;
        this.bonusDamage = bonusDamage;
        this.bonusHp = bonusHp;
        this.consumable = consumable;
        this.spritePath = spritePath;
    }

    // Getters

    public String getId() { return id; }
    public String getType() { return type; }
    public int getLevel() { return level; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getBonusDamage() { return bonusDamage; }
    public int getBonusHp() { return bonusHp; }
    public boolean isConsumable() { return consumable; }
    public String getSpritePath() { return spritePath; }

    // Setters

    public void setId(String id) { this.id = id; }
    public void setType(String type) { this.type = type; }
    public void setLevel(int level) { this.level = level; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setBonusDamage(int bonusDamage) { this.bonusDamage = bonusDamage; }
    public void setBonusHp(int bonusHp) { this.bonusHp = bonusHp; }
    public void setConsumable(boolean consumable) { this.consumable = consumable; }
    public void setSpritePath(String spritePath) { this.spritePath = spritePath; }

    @Override
    public String toString() {
        return name + " (Lv." + level + ") +" + bonusDamage + "DMG +" + bonusHp + "HP"
            + (consumable ? " [consumable]" : "");
    }
}
