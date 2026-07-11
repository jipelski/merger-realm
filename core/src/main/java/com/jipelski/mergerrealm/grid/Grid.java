package com.jipelski.mergerrealm.grid;

import com.badlogic.gdx.Gdx;

import com.jipelski.mergerrealm.database.JsonManager;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

public class Grid {

    private static final int DEFAULT_WIDTH  = 5;
    private static final int DEFAULT_HEIGHT = 5;

    private int width;
    private int height;
    private Cell[][] cells;
    private JsonManager jsonManager;
    private int[] XoY;
    private ArrayList<String> arr;

    // NULL CONSTRUCTOR
    public Grid() {}

    // MAIN CONSTRUCTOR — loads from saved files, falls back to default if missing
    public Grid(JsonManager jsnm) {
        jsonManager = jsnm;

        XoY = jsonManager.loadArray("grid_size");
        if (XoY == null || XoY.length < 2) {
            Gdx.app.log("GRID", "grid_size missing or corrupt — using default " + DEFAULT_WIDTH + "x" + DEFAULT_HEIGHT);
            XoY = new int[]{DEFAULT_WIDTH, DEFAULT_HEIGHT};
        } else {
            Gdx.app.log("GRID", "Loaded grid size: x=" + XoY[0] + " y=" + XoY[1]);
        }

        this.width  = XoY[0];
        this.height = XoY[1];
        this.cells  = new Cell[width][height];

        arr = jsonManager.loadArrayList("grid_array");
        boolean arrValid = arr != null && arr.size() == width * height;

        if (!arrValid) {
            Gdx.app.log("GRID", "grid_array missing, corrupt, or wrong size — initialising empty grid");
            initEmptyCells();
        } else {
            Gdx.app.log("GRID", "Loaded grid_array with " + arr.size() + " entries");
            int index = 0;
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    cells[x][y] = new Cell(arr.get(index));
                    cells[x][y].setX(x);
                    cells[x][y].setY(y);
                    Gdx.app.log("GRID", "Cell[" + x + "][" + y + "] = " + cells[x][y].getOccupant());
                    index++;
                }
            }
        }
    }

    // Fills every cell with an empty default_tile Cell
    private void initEmptyCells() {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                cells[x][y] = new Cell();
                cells[x][y].setX(x);
                cells[x][y].setY(y);
            }
        }
    }

    // GETTERS

    public int[] getXoY() {
        return XoY;
    }

    // Returns a snapshot — callers cannot mutate internal state
    public ArrayList<String> getArr() {
        ArrayList<String> current = new ArrayList<>(width * height);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                current.add(cells[x][y].getOccupant());
            }
        }
        return current;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Cell getCell(int x, int y) {
        return cells[x][y];
    }

    public Cell[][] getCells() {
        return cells;
    }

    // BFS — finds closest empty cell to (x, y), returns null if grid is full
    public int[] getClosestEmptyCell(int x, int y) {
        Queue<Cell> queue = new LinkedList<>();
        boolean[][] visited = new boolean[width][height];

        queue.add(cells[x][y]);
        visited[x][y] = true;

        while (!queue.isEmpty()) {
            Cell current = queue.remove();
            if (current.isEmpty())
                return new int[]{current.getX(), current.getY()};

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;

                    int nx = current.getX() + dx;
                    int ny = current.getY() + dy;

                    if (isValidCell(nx, ny) && !visited[nx][ny]) {
                        queue.add(cells[nx][ny]);
                        visited[nx][ny] = true;
                    }
                }
            }
        }

        Gdx.app.log("GRID", "getClosestEmptyCell: no empty cell found - grid may be full");
        return null;
    }

    // SETTERS

    public void setWidth(int width)   { this.width  = width;  }
    public void setHeight(int height) { this.height = height; }
    public void setCells(Cell[][] cells) { this.cells = cells; }

    public void setCell(Cell cell, int x, int y) {
        cells[x][y] = cell;
    }

    public void setOnCell(String id, int x, int y) {
        cells[x][y].setOccupant(id);
    }

    // HELPERS

    private boolean isValidCell(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public boolean hasEmptyCell() {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (cells[x][y].isEmpty()) return true;
            }
        }
        return false;
    }

    /**
     * Expands the grid to a new width and height.
     * Preserves all existing cell contents.
     */
    public void expandGrid(int newWidth, int newHeight) {
        if (newWidth < width || newHeight < height) {
            Gdx.app.log("GRID", "Cannot shrink grid — ignoring");
            return;
        }
        if (newWidth == width && newHeight == height) return;

        Gdx.app.log("GRID", "Expanding grid from " + width + "x" + height
            + " to " + newWidth + "x" + newHeight);

        Cell[][] newCells = new Cell[newWidth][newHeight];

        // Copy existing cells
        for (int x = 0; x < newWidth; x++) {
            for (int y = 0; y < newHeight; y++) {
                if (x < width && y < height) {
                    newCells[x][y] = cells[x][y];
                } else {
                    newCells[x][y] = new Cell();
                    newCells[x][y].setX(x);
                    newCells[x][y].setY(y);
                }
            }
        }

        cells = newCells;
        width = newWidth;
        height = newHeight;
        XoY = new int[]{width, height};
    }

    /**
     * Resets the grid to a fresh, fully-empty layout at the given size —
     * unlike expandGrid(), this can SHRINK the grid (e.g. a Prestige reset
     * going from a maxed-out board back to a lower start-level size).
     * Discards all existing cell contents; callers are responsible for
     * having already cleared any object-manager state that referenced them.
     */
    public void resetToSize(int newWidth, int newHeight) {
        Gdx.app.log("GRID", "Resetting grid to " + newWidth + "x" + newHeight);
        cells = new Cell[newWidth][newHeight];
        width = newWidth;
        height = newHeight;
        XoY = new int[]{width, height};
        initEmptyCells();
    }
}
