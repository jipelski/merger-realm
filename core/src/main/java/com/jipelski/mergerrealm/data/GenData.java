package com.jipelski.mergerrealm.data;



public class GenData {

    protected String sprite_path;
    protected String description;
    protected int    maxLVL;

    // NULL CONSTRUCTOR
    public GenData() {
        this.sprite_path = null;
        this.description = null;
        this.maxLVL      = -1;
    }

    // CONSTRUCTOR
    public GenData(String sprite_path, String description, int maxLVL) {
        this.sprite_path = sprite_path;
        this.description = description;
        this.maxLVL      = maxLVL;
    }

    // GETTERS
    public String getSprite_path() { return sprite_path; }
    public String getDescription() { return description; }
    public int    getMaxLVL()      { return maxLVL;      }

    // SETTERS
    public void setSprite_path(String sprite_path) { this.sprite_path = sprite_path; }
    public void setDescription(String description) { this.description = description; }
    public void setMaxLVL(int maxLVL)              { this.maxLVL      = maxLVL;      }


    @Override
    public String toString() {
        return "GenData{sprite_path='" + sprite_path
                + "', maxLVL=" + maxLVL + "}";
    }
}
