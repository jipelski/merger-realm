package com.example.lastresort.model;

import com.example.lastresort.data.UnitData;

public class Unit extends GameObject{
    protected String resource;
    protected int    gen_rate;
    protected int    damage;
    protected int xp_rate;
    protected String nemesis;
    protected int    nemesis_rate;

    //protected UnitConfiguration config;

    // NULL CONSTRUCTOR
    public Unit()
    {
        this.resource  = null;
        this.gen_rate  = 0;
        this.damage    = 0;
        this.xp_rate = 0;
    }

    // BASIC CONSTRUCTOR
    public Unit(String type, String id, int lvl, int maxLVL, int xPos, int yPos, String sprite, String description, String resource, int gen_rate, int damage, int xp_rate, String nemesis, int nemesis_rate)
    {
        super(type, id, lvl, maxLVL, xPos, yPos, sprite, description);
        this.resource     = resource;
        this.gen_rate     = gen_rate;
        this.damage       = damage;
        this.xp_rate = xp_rate;
        this.nemesis      = nemesis;
        this.nemesis_rate = nemesis_rate;
    }
    // DATA STRUCTURE CONSTRUCTOR
    public Unit(UnitData unitData, String type, String id, int lvl, int xPos, int yPos)
    {
        super(type, id, lvl, unitData.getMaxLVL(),xPos, yPos, unitData.getSprite_path(), unitData.getDescription());
        this.resource     = unitData.getResource();
        this.gen_rate     = unitData.getGen_rate();
        this.damage       = unitData.getDamage();
        this.xp_rate = unitData.getXP_Rate();
        this.nemesis      = unitData.getNemesis();
        this.nemesis_rate = unitData.getNemesis_Rate();
    }

    // GETTERS
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
    public int getNemesis_rate() {
        return nemesis_rate;
    }

    // SETTERS
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
