package com.example.lastresort.model;

import com.example.lastresort.data.PrinceData;

public class Prince extends GameObject{
    private int resource_1;
    private int resource_2;
    private int resource_3;

    //NULL CONSTRUCTOR
    public Prince()
    {

    }

    public Prince(String type, String id, int lvl, int maxLVL, int xPos, int yPos, String sprite, String description, int resource_1, int resource_2, int resource_3)
    {
        super(type, id, lvl, maxLVL, xPos, yPos, sprite, description);
        this.resource_1 = resource_1;
        this.resource_2 = resource_2;
        this.resource_3 = resource_3;
    }

    //DATA STRUCTURE CONSTRUCTOR

    public Prince(PrinceData princeData, String type, String id, int lvl, int xPos, int yPos)
    {
        super(type, id, lvl, princeData.getMaxLVL(), xPos, yPos, princeData.getSprite_path(), princeData.getDescription());
        this.resource_1 = princeData.getResource_1();
        this.resource_2 = princeData.getResource_2();
        this.resource_3 = princeData.getResource_3();
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
