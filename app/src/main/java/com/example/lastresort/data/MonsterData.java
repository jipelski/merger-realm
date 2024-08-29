package com.example.lastresort.data;

public class MonsterData extends GenData{
    //STATS
    protected int    hp;
    protected String reward;

    //NULL CONSTRUCTOR
    public MonsterData(){}

    //CONSTRUCTOR
    public MonsterData(String sprite_path, String description, int maxLVL, int hp, String reward)
    {
        super(sprite_path, description, maxLVL);
        this.hp = hp;
        this.reward = reward;
    }

    //GETTERS
    public int getHp() {
        return hp;
    }

    public String getReward() {
        return reward;
    }

    //SETTERS
    public void setHp(int hp) {
        this.hp = hp;
    }

    public void setReward(String reward) {
        this.reward = reward;
    }
}
