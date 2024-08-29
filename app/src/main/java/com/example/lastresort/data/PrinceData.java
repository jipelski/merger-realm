package com.example.lastresort.data;

public class PrinceData extends GenData{
    private int resource_1;
    private int resource_2;
    private int resource_3;

    //NULL CONSTRUCTOR
    public PrinceData()
    {}

    //CONSTRUCTOR
    public PrinceData(String sprite_path, String description, int maxLVL, int resource_1, int resource_2, int resource_3)
    {
        super(sprite_path, description, maxLVL);
        this.resource_1 = resource_1;
        this.resource_2 = resource_2;
        this.resource_3 = resource_3;
    }

    //GETTERS

    public int getResource_1() {
        return resource_1;
    }

    public int getResource_2() {
        return resource_2;
    }

    public int getResource_3() {
        return resource_3;
    }

    //SETTERS

    public void setResource_1(int resource_1) {
        this.resource_1 = resource_1;
    }

    public void setResource_2(int resource_2) {
        this.resource_2 = resource_2;
    }

    public void setResource_3(int resource_3) {
        this.resource_3 = resource_3;
    }
}
