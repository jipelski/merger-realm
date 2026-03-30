package com.jipelski.mergerrealm.data;



public class FacilityData extends GenData {

    protected int buildCost1;
    protected int buildCost2;
    protected int buildCost3;
    protected int buildCost4;
    protected int tapCost1;
    protected int tapCost2;
    protected int tapCost3;
    protected int time_cost;

    // NULL CONSTRUCTOR
    public FacilityData() {
        super();
        this.buildCost1 = 0;
        this.buildCost2 = 0;
        this.buildCost3 = 0;
        this.buildCost4 = 0;
        this.tapCost1   = 0;
        this.tapCost2   = 0;
        this.tapCost3   = 0;
        this.time_cost  = 0;
    }

    // CONSTRUCTOR
    public FacilityData(String sprite_path, String description, int maxLVL,
                        int buildCost1, int buildCost2, int buildCost3, int buildCost4,
                        int tapCost1, int tapCost2, int tapCost3, int time_cost) {
        super(sprite_path, description, maxLVL);
        this.buildCost1 = buildCost1;
        this.buildCost2 = buildCost2;
        this.buildCost3 = buildCost3;
        this.buildCost4 = buildCost4;
        this.tapCost1   = tapCost1;
        this.tapCost2   = tapCost2;
        this.tapCost3   = tapCost3;
        this.time_cost  = time_cost;
    }

    // GETTERS
    public int getBuildCost1() { return buildCost1; }
    public int getBuildCost2() { return buildCost2; }
    public int getBuildCost3() { return buildCost3; }
    public int getBuildCost4() { return buildCost4; }
    public int getTapCost1()   { return tapCost1;   }
    public int getTapCost2()   { return tapCost2;   }
    public int getTapCost3()   { return tapCost3;   }
    public int getTimeCost() { return time_cost; }

    // SETTERS
    public void setBuildCost1(int buildCost1) { this.buildCost1 = buildCost1; }
    public void setBuildCost2(int buildCost2) { this.buildCost2 = buildCost2; }
    public void setBuildCost3(int buildCost3) { this.buildCost3 = buildCost3; }
    public void setBuildCost4(int buildCost4) { this.buildCost4 = buildCost4; }
    public void setTapCost1(int tapCost1)     { this.tapCost1   = tapCost1;   }
    public void setTapCost2(int tapCost2)     { this.tapCost2   = tapCost2;   }
    public void setTapCost3(int tapCost3)     { this.tapCost3   = tapCost3;   }
    public void setTimeCost(int time_cost)    { this.time_cost  = time_cost;  }


    @Override
    public String toString() {
        return "FacilityData{maxLVL=" + maxLVL
                + ", buildCosts=[" + buildCost1 + "," + buildCost2
                + "," + buildCost3 + "," + buildCost4 + "]"
                + ", tapCosts=[" + tapCost1 + "," + tapCost2 + "," + tapCost3
                + ", timeCost=[" + time_cost + "]}";
    }
}
