package com.jipelski.mergerrealm.data;



public class MonsterData extends GenData {

    protected int    hp;
    protected String reward;

    // NULL CONSTRUCTOR
    public MonsterData() {
        super();
        this.hp     = 0;
        this.reward = null;
    }

    // CONSTRUCTOR
    public MonsterData(String sprite_path, String description, int maxLVL,
                       int hp, String reward) {
        super(sprite_path, description, maxLVL);
        this.hp     = hp;
        this.reward = reward;
    }

    // GETTERS
    public int    getHp()     { return hp;     }
    public String getReward() { return reward; }

    // SETTERS
    public void setHp(int hp)          { this.hp     = hp;     }
    public void setReward(String reward) { this.reward = reward; }


    @Override
    public String toString() {
        return "MonsterData{hp=" + hp
                + ", reward='" + reward
                + "', maxLVL=" + maxLVL + "}";
    }
}
