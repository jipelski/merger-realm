package com.jipelski.mergerrealm.model;



import com.jipelski.mergerrealm.data.ResourcePouchData;

public class ResourcePouch extends GameObject {

    protected int    fill_percent;
    protected String resource_type;

    // NULL CONSTRUCTOR
    public ResourcePouch() {
        super();
        this.fill_percent  = 0;
        this.resource_type = null;
    }

    // FULL CONSTRUCTOR
    public ResourcePouch(String type, String id, int lvl, int maxLVL,
                         int xPos, int yPos, String sprite, String description,
                         int fill_percent, String resource_type) {
        super(type, id, lvl, maxLVL, xPos, yPos, sprite, description);
        this.fill_percent  = fill_percent;
        this.resource_type = resource_type;
    }

    // DATA STRUCTURE CONSTRUCTOR
    public ResourcePouch(ResourcePouchData data, String type, String id, int lvl, int xPos, int yPos) {
        super(type, id, lvl, data.getMaxLVL(), xPos, yPos,
                data.getSprite_path(), data.getDescription());
        this.fill_percent  = data.getFill_percent();
        this.resource_type = data.getResource_type();
    }

    // GETTERS
    public int    getFill_percent()  { return fill_percent;  }
    public String getResource_type() { return resource_type; }

    // SETTERS
    public void setFill_percent(int fill_percent)      { this.fill_percent  = fill_percent;  }
    public void setResource_type(String resource_type) { this.resource_type = resource_type; }


    @Override
    public String toString() {
        return "ResourcePouch{id='" + id + "', resource_type='" + resource_type
                + "', fill_percent=" + fill_percent
                + ", lvl=" + lvl
                + ", pos=[" + xPos + "," + yPos + "]}";
    }
}
