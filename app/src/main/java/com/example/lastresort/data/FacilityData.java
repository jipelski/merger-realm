package com.example.lastresort.data;

public class FacilityData extends GenData{

    //STATS
    protected String    resourceType;
    protected int       buildCost1;
    protected int       buildCost2;
    protected int       buildCost3;
    protected int       buildCost4;
    protected int       tapCost1;
    protected int       tapCost2;
    protected int       tapCost3;

    //NULL CONSTRUCTOR
    public FacilityData()
    {

    }

    //CONSTRUCTOR
    public FacilityData(String sprite_path, String description, int maxLVL,  int buildCost1, int buildCost2, int buildCost3, int buildCost4, int tapCost1, int tapCost2, int tapCost3)
    {
        super(sprite_path, description, maxLVL);
        this.buildCost1      = buildCost1;
        this.buildCost2      = buildCost2;
        this.buildCost3      = buildCost3;
        this.buildCost4      = buildCost4;
        this.tapCost1        = tapCost1;
        this.tapCost2        = tapCost2;
        this.tapCost3        = tapCost3;
    }

    //GETTERS
    public String getResourceType() {
        return resourceType;
    }

    public int getBuildCost1() {
        return buildCost1;
    }
    public int getBuildCost2() {
        return buildCost2;
    }
    public int getBuildCost3() {
        return buildCost3;
    }
    public int getBuildCost4() {
        return buildCost4;
    }

    public int getTapCost1() {
        return tapCost1;
    }

    public int getTapCost2() {
        return tapCost2;
    }

    public int getTapCost3() {
        return tapCost3;
    }

    //SETTERS

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public void setBuildCost1(int buildCost1) {
        this.buildCost1 = buildCost1;
    }
    public void setBuildCost2(int buildCost2) {
        this.buildCost2 = buildCost2;
    }
    public void setBuildCost3(int buildCost3) {
        this.buildCost3 = buildCost3;
    }
    public void setBuildCost4(int buildCost4) {
        this.buildCost4 = buildCost4;
    }

    public void setTapCost1(int tapCost1) {
        this.tapCost1 = tapCost1;
    }
    public void setTapCost2(int tapCost2) {
        this.tapCost2 = tapCost2;
    }
    public void setTapCost3(int tapCost3) {
        this.tapCost3 = tapCost3;
    }
}
