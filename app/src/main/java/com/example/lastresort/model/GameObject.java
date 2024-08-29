package com.example.lastresort.model;

import android.media.Image;

public class GameObject{

    // STATS
    protected String            type;
    protected String            id;
    protected int               lvl;
    protected int               maxLVL;
    protected int               xPos;
    protected int               yPos;
    //protected String            r_type;
    //protected int               r_cost;
    //protected int               r_gen;
    protected String            sprite;
    protected GameObjectState   state;
    protected String            description;

    // NULL CONSTRUCTOR
    public GameObject()
    {
        this.type   = null;
        this.id     = null;
        this.lvl    = -1;
        this.maxLVL = -1;
        this.xPos   = -1;
        this.yPos   = -1;
        //this.r_type = null;
        //this.r_cost = 0;
        //this.r_gen  = 0;
        this.sprite = null;
        this.state  = null;
        this.description = null;
    }
    // CONSTRUCTOR
    public GameObject(String type, String id, int lvl, int maxLVL, int xPos, int yPos, String sprite, String description)
    {
        this.type        = type;
        this.id          = id;
        this.lvl         = lvl;
        this.maxLVL      = maxLVL;
        this.xPos        = xPos;
        this.yPos        = yPos;
        //this.r_type = r_type;
        //this.r_cost = r_cost;
        //this.r_gen  = r_gen;
        this.sprite      = sprite;
        this.state       = GameObjectState.IDLE;
        this.description = description;
    }

    // GETTERS
    public String getType   () {
        return type;
    }
    public String getId     ()   {
        return id;
    }
    public int getLvl       () {
        return lvl;
    }

    public int getMaxLVL() {
        return maxLVL;
    }

    public int getxPos      ()
    {
        return xPos;
    }
    public int getyPos      ()
    {
        return yPos;
    }

    /*
    public String getR_type ()
    {
        return r_type;
    }
    public int getR_cost    () {
        return r_cost;
    }
    public int getR_gen     () {
        return r_gen;
    }
    */

    public String getSprite  (){
        return sprite;
    }
    public GameObjectState getState()
    {
        return state;
    }
    public String getDescription() {
        return description;
    }

    // SETTERS
    public void setType     (String type) {
        this.type = type;
    }
    public void setId       (String id) {
        this.id = id;
    }
    public void setLvl      (int lvl) {
        this.lvl = lvl;
    }
    public void setMaxLVL(int maxLVL) {
        this.maxLVL = maxLVL;
    }
    public void setxPos     (int xPos) {
        this.xPos = xPos;
    }
    public void setyPos     (int yPos) {
        this.yPos = yPos;
    }
    /*
    public void setR_type   (String r_type) {
        this.r_type = r_type;
    }
    public void setR_cost   (int r_cost) {
        this.r_cost = r_cost;
    }
    public void setR_gen    (int r_gen) {
        this.r_gen = r_gen;
    }
    */

    public void setSprite   (String sprite){
        this.sprite = sprite;
    }
    public void setState    (GameObjectState state) {
        this.state = state;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    public void onTap(){
        if (this.state == GameObjectState.IDLE)
        {
            this.state = GameObjectState.SELECTED;
        }
    }
}
