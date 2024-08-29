package com.example.lastresort.grid;

import android.util.Log;

import com.example.lastresort.database.JsonManager;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import com.example.lastresort.R;

public class Grid {
    //STATS
    private int width;
    private int height;
    private Cell[][] cells;
    private JsonManager jsonManager;
    private int[] XoY;
    private ArrayList<String> arr;

    //NULL CONSTRUCTOR
    public Grid(){}

    //CONSTRUCTOR
    private Grid(int width, int height)
    {
        this.width  = width;
        this.height = height;
        this.cells  = new Cell[width][height];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                cells[x][y] = new Cell();
            }
        }
    }

    public Grid(JsonManager jsnm)
    {
        jsonManager = jsnm;
        this.XoY = jsonManager.loadArray("grid_size");
        Log.d("GRID", "x:" + XoY[0] + "y" + XoY[1]);
        this.arr = jsonManager.loadArrayList("grid_array");
        Log.d("ARR", "1:" + arr.get(0) + "2" + arr.get(1));

        this.width  = XoY[0];
        this.height = XoY[1];
        this.cells  = new Cell[width][height];

        int index = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                this.cells[x][y] = new Cell(arr.get(index));
                this.cells[x][y].setX(x);
                this.cells[x][y].setY(y);
                index++;
                Log.d("GRID", "Entry at index " + index + " is " + cells[x][y].getOccupant() + " and x " + this.cells[x][y].getX() + " and y " + this.cells[x][y].getY());
            }

        }
    }

    //CONSTRUCTOR USING SAVED INFORMATION
    private Grid(int width, int height, String[] cellArray)
    {
        this.width  = width;
        this.height = height;
        this.cells  = new Cell[width][height];

        int index = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                this.cells[x][y] = new Cell(cellArray[index]);
                index++;
            }

        }
    }

    //GETTERS


    public int[] getXoY() {
        return XoY;
    }

    public ArrayList<String> getArr() {
        return arr;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Cell getCell(int x, int y) {
        return this.cells[x][y];
    }

    public Cell[][] getCells() {
        return this.cells;
    }

    //BFS algorithm to find the closest empty cell or returns null if no such cell exists
    public int[] getClosestEmptyCell(int x, int y)
    {
        Queue<Cell> queue = new LinkedList<>();
        boolean[][] visited = new boolean[this.width][this.height];

        queue.add(cells[x][y]);
        Log.d("GridGetClosestEmptyCell", " The following cells should be at x: " + cells[x][y].getX() + " y: " + cells[x][y].getY() + " occupant " + cells[x][y].getOccupant());
        visited[x][y] = true;

        while (!queue.isEmpty())
        {
            Cell currentCell = queue.remove();
            if (currentCell.isEmpty())
                return new int[]{currentCell.getX(), currentCell.getY()};

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }

                    int newX = currentCell.getX() + dx;
                    int newY = currentCell.getY() + dy;

                    if (isValidCell(newX, newY) && !visited[newX][newY]) {
                        queue.add(cells[newX][newY]);
                        visited[newX][newY] = true;
                    }
                }
            }
        }
        return null;
    }

    //SETTERS


    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setCells(Cell[][] cells) {
        this.cells = cells;
    }

    public void setCell(Cell cell, int horizontal, int vertical)
    {
        this.cells[horizontal][vertical] = cell;
    }

    public void setOnCell(String id, int x, int y)
    {
        cells[x][y].setOccupant(id);
    }

    //METHODS

    //Helper method to check if a cell is within the bounds of the grid.
    private boolean isValidCell(int x, int y)
    {
        return x >= 0 && x < width && y >= 0 && y < height;
    }
}
