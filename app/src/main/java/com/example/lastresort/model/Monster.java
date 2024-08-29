package com.example.lastresort.model;

import android.media.Image;

import com.example.lastresort.data.ChestData;
import com.example.lastresort.data.MonsterData;
import com.example.lastresort.util.GameDataLoader;

public class Monster extends GameObject{
    protected int    hp;
    protected String reward;

    //protected MonsterConfiguration config;

    // NULL CONSTRUCTOR
    public Monster()
    {
        this.hp = 0;
        this.reward = null;
    }

    // CONSTRUCTOR
    public Monster(String type, String id, int lvl, int maxLVL, int xPos, int yPos, String sprite, String description, int hp, String reward)
    {
        super(type, id, lvl, maxLVL, xPos, yPos, sprite, description);
        this.hp     = hp;
        this.reward = reward;
    }

    //DATASTRUCTURE CONSTRUCTOR
    public Monster(MonsterData monsterData, String type, String id, int lvl, int xPos, int yPos)
    {
        super(type, id, lvl, monsterData.getMaxLVL(), xPos, yPos, monsterData.getSprite_path(), monsterData.getDescription());
        this.hp = monsterData.getHp();
        this.reward = monsterData.getReward();
    }

    // GETTERS
    public int getHp() {
        return hp;
    }
    public String getReward() {
        return reward;
    }

    // SETTERS
    public void setHp(int hp) {
        this.hp = hp;
    }
    public void setReward(String reward) {
        this.reward = reward;
    }

    // METHODS
    public boolean reduceHp(int damage) {
        this.hp -= damage;
        return this.hp <= 0;
    }

    public ChestData spawnChest(String reward)
    {
        //CALL GAME MANAGER AND PUT IN QUEUE THE REWARDED TYPE OF CHEST
        return null;
    }
}
