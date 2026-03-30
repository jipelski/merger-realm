package com.jipelski.mergerrealm.model;

import com.jipelski.mergerrealm.data.MonsterData;

public class Monster extends GameObject {

    protected int    hp;
    protected int    damage;
    protected String reward;

    // NULL CONSTRUCTOR
    public Monster() {
        this.hp     = 0;
        this.damage = 0;
        this.reward = null;
    }

    // FULL CONSTRUCTOR
    public Monster(String type, String id, int lvl, int maxLVL,
                   int xPos, int yPos, String sprite, String description,
                   int hp, int damage, String reward) {
        super(type, id, lvl, maxLVL, xPos, yPos, sprite, description);
        this.hp     = hp;
        this.damage = damage;
        this.reward = reward;
    }

    // DATA STRUCTURE CONSTRUCTOR
    public Monster(MonsterData monsterData, String type, String id, int lvl, int xPos, int yPos) {
        super(type, id, lvl, monsterData.getMaxLVL(), xPos, yPos,
                monsterData.getSprite_path(), monsterData.getDescription());
        this.hp     = monsterData.getHp();
        this.damage = monsterData.getDamage();
        this.reward = monsterData.getReward();
    }

    // GETTERS
    public int    getHp()     { return hp;     }
    public int    getDamage() { return damage; }
    public String getReward() { return reward; }

    // SETTERS
    public void setHp(int hp)          { this.hp     = hp;     }
    public void setDamage(int damage)    { this.damage = damage; }
    public void setReward(String reward) { this.reward = reward; }

    // METHODS

    /**
     * Reduces HP by the given damage amount.
     * Returns true if the monster is defeated (hp drops to 0 or below).
     */
    public boolean reduceHp(int damage) {
        this.hp -= damage;
        return this.hp <= 0;
    }
}
