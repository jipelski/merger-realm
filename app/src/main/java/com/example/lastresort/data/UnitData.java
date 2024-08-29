package com.example.lastresort.data;

public class UnitData extends GenData{
    //STATS
    protected String resource;
    protected int    gen_rate;
    protected int    damage;
    protected int    xp_rate;
    protected String nemesis;
    protected int    nemesis_rate;

    //NULL CONSTRUCTOR
    public UnitData(){}

    //CONSTRUCTOR
    public UnitData(String sprite_path, String description, int maxLVL, String resource, int gen_rate, int damage, int xp_rate, String nemesis, int nemesis_rate)
    {
        super(sprite_path, description, maxLVL);
        this.resource = resource;
        this.gen_rate = gen_rate;
        this.damage = damage;
        this.xp_rate = xp_rate;
        this.nemesis = nemesis;
        this.nemesis_rate = nemesis_rate;
    }

    //GETTERS

    public String getResource() {
        return resource;
    }

    public int getGen_rate() {
        return gen_rate;
    }

    public int getDamage() {
        return damage;
    }

    public int getXP_Rate() {
        return xp_rate;
    }

    public String getNemesis() {
        return nemesis;
    }

    public int getNemesis_Rate() {
        return nemesis_rate;
    }

    //SETTERS

    public void setResource(String resource) {
        this.resource = resource;
    }

    public void setGen_rate(int gen_rate) {
        this.gen_rate = gen_rate;
    }

    public void setDamage(int damage) {
        this.damage = damage;
    }

    public void setXP_Rate(int food_rate) {
        this.xp_rate = food_rate;
    }

    public void setNemesis(String nemesis) {
        this.nemesis = nemesis;
    }

    public void setNemesis_rate(int nemesis_rate) {
        this.nemesis_rate = nemesis_rate;
    }
}
