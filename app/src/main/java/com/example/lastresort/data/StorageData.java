package com.example.lastresort.data;

public class StorageData extends GenData{

    //STATS
    protected String storage_type;
    protected int    storage_size;
    protected int    build_cost1;
    protected int    build_cost2;
    protected int    build_cost3;

    //NULL CONSTRUCTOR
    public StorageData(){}

    //CONSTRUCTOR
    public StorageData(String sprite_path, String description, int maxLVL, String storage_type, int storage_size, int build_cost1, int build_cost2, int build_cost3)
    {
        super(sprite_path, description, maxLVL);
        this.storage_type = storage_type;
        this.storage_size = storage_size;
        this.build_cost1  = build_cost1;
        this.build_cost2  = build_cost2;
        this.build_cost3  = build_cost3;
    }

    //GETTERS

    public String getStorage_type() {
        return storage_type;
    }

    public int getStorage_size() {
        return storage_size;
    }

    public int getBuild_cost1() {
        return build_cost1;
    }

    public int getBuild_cost2() {
        return build_cost2;
    }

    public int getBuild_cost3() {
        return build_cost3;
    }
    //SETTERS

    public void setStorage_type(String storage_type) {
        this.storage_type = storage_type;
    }

    public void setStorage_size(int storage_size) {
        this.storage_size = storage_size;
    }

    public void setBuild_cost1(int build_cost1) {
        this.build_cost1 = build_cost1;
    }

    public void setBuild_cost2(int build_cost2) {
        this.build_cost2 = build_cost2;
    }

    public void setBuild_cost3(int build_cost3) {
        this.build_cost3 = build_cost3;
    }
}
