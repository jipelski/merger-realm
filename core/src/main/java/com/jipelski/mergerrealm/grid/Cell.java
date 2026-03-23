package com.jipelski.mergerrealm.grid;

public class Cell {
    private String occupant;
    private int x;
    private int y;
    public  Cell()
    {
        occupant = "default_tile";
        x = -1;
        y = -1;
    }
    public Cell(String occupant) {
        this.occupant = occupant;
    }

    //GETTERS

    public String getOccupant() {
        return occupant;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    //SETTERS

    public void setOccupant(String occupant) {
        this.occupant = occupant;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    //METHODS
    public boolean isEmpty()
    {
        return "default_tile".equals(occupant);
    }
}
