package com.jipelski.mergerrealm.data;



public class ResourcePouchData extends GenData {

    protected int    fill_percent;
    protected String resource_type;

    // NULL CONSTRUCTOR
    public ResourcePouchData() {
        super();
        this.fill_percent  = 0;
        this.resource_type = null;
    }

    // CONSTRUCTOR
    public ResourcePouchData(String sprite_path, String description, int maxLVL,
                             int fill_percent, String resource_type) {
        super(sprite_path, description, maxLVL);
        this.fill_percent  = fill_percent;
        this.resource_type = resource_type;
    }

    // GETTERS
    public int    getFill_percent()  { return fill_percent;  }
    public String getResource_type() { return resource_type; }

    // SETTERS
    public void setFill_percent(int fill_percent)   { this.fill_percent  = fill_percent;  }
    public void setResource_type(String resource_type) { this.resource_type = resource_type; }


    @Override
    public String toString() {
        return "ResourcePouchData{resource_type='" + resource_type
                + "', fill_percent=" + fill_percent
                + ", maxLVL=" + maxLVL + "}";
    }
}
