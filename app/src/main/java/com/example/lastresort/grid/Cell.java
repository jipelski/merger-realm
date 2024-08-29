package com.example.lastresort.grid;

public class Cell {
    private String occupant;
    private int x;
    private int y;
    public  Cell()
    {
        occupant = null;
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
        return occupant.equals("default_tile");
    }
}
