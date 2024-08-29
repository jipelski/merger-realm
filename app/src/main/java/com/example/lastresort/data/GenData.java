package com.example.lastresort.data;

public class GenData {
    // STATS
    protected String            sprite_path;
    protected String            description;
    protected int               maxLVL;

    //NULL CONSTRUCTOR
    public GenData()
    {
    }

    //CONSTRUCTOR
    public GenData(String sprite_path, String description, int maxLVL)
    {
        this.sprite_path = sprite_path;
        this.description = description;
        this.maxLVL      = maxLVL;
    }

    //GETTERS
    public String getSprite_path() {
        return sprite_path;
    }

    public String getDescription() {
        return description;
    }
    public int getMaxLVL() {
        return maxLVL;
    }

    //SETTERS
    public void setSprite_path(String sprite_path) {
        this.sprite_path = sprite_path;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setMaxLVL(int maxLVL) {
        this.maxLVL = maxLVL;
    }
}
