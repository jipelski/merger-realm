package com.jipelski.mergerrealm.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;

import com.jipelski.mergerrealm.grid.Cell;
import com.jipelski.mergerrealm.grid.Grid;
import com.jipelski.mergerrealm.model.GameObject;
import com.jipelski.mergerrealm.util.EventManager;
import com.jipelski.mergerrealm.util.GridObjectManager;

/**
 * Handles all grid touch input: tap, drag-and-drop, merge, swap.
 *
 * Touch logic:
 *   - Touch down on an occupied cell → starts a potential drag
 *   - If finger moves beyond DRAG_THRESHOLD → enters drag mode
 *   - Touch up without dragging → tap (facility spawn, chest open, token collect)
 *   - Touch up while dragging on a different occupied cell → merge or swap
 *   - Touch up while dragging on an empty cell → move object there
 *   - Touch up while dragging outside grid → cancel, object returns
 */
public class GridInputHandler extends InputAdapter {

    private static final String TAG = "GridInputHandler";
    private static final float DRAG_THRESHOLD = 8f; // world units before drag activates

    // References
    private final EventManager eventManager;
    private final Viewport viewport;

    // Grid layout info (set by MergerRealmGame after layout calculation)
    private float gridStartX;
    private float gridStartY;
    private float cellSize;
    private float cellGap;
    private int gridCols;
    private int gridRows;

    // Drag state
    private boolean touching = false;
    private boolean dragging = false;
    private int originCellX = -1;
    private int originCellY = -1;
    private String draggedObjectId = null;
    private final Vector2 touchStart = new Vector2();
    private final Vector2 dragPos = new Vector2(); // current finger position in world coords

    // Temp vector to avoid allocations
    private final Vector2 worldPos = new Vector2();

    public GridInputHandler(EventManager eventManager, Viewport viewport) {
        this.eventManager = eventManager;
        this.viewport = viewport;
    }

    /**
     * Must be called after grid layout is calculated so we know
     * where cells are on screen.
     */
    public void setGridLayout(float gridStartX, float gridStartY,
                              float cellSize, float cellGap,
                              int gridCols, int gridRows) {
        this.gridStartX = gridStartX;
        this.gridStartY = gridStartY;
        this.cellSize = cellSize;
        this.cellGap = cellGap;
        this.gridCols = gridCols;
        this.gridRows = gridRows;
    }

    // ── InputAdapter overrides ──

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (pointer != 0) return false; // only handle first finger

        toWorldCoords(screenX, screenY);

        int[] cell = worldToCell(worldPos.x, worldPos.y);
        if (cell == null) return false; // touched outside grid

        Grid grid = eventManager.getGridInstance();
        Cell gridCell = grid.getCell(cell[0], cell[1]);

        if (gridCell.isEmpty()) return false; // nothing to interact with

        // Start tracking this touch
        touching = true;
        dragging = false;
        originCellX = cell[0];
        originCellY = cell[1];
        draggedObjectId = gridCell.getOccupant();
        touchStart.set(worldPos);
        dragPos.set(worldPos);

        Gdx.app.log(TAG, "Touch down on cell [" + cell[0] + "," + cell[1]
            + "] id=" + draggedObjectId);
        return true;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (!touching || pointer != 0) return false;

        toWorldCoords(screenX, screenY);
        dragPos.set(worldPos);

        // Check if we've moved enough to start dragging
        if (!dragging && touchStart.dst(worldPos) > DRAG_THRESHOLD) {
            dragging = true;
            Gdx.app.log(TAG, "Drag started from cell [" + originCellX + "," + originCellY + "]");
        }

        return true;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (!touching || pointer != 0) return false;

        toWorldCoords(screenX, screenY);

        if (!dragging) {
            // ── TAP ──
            Gdx.app.log(TAG, "Tap on cell [" + originCellX + "," + originCellY
                + "] id=" + draggedObjectId);
            eventManager.tap(draggedObjectId);
        } else {
            // ── DROP ──
            int[] targetCell = worldToCell(worldPos.x, worldPos.y);

            if (targetCell == null) {
                // Dropped outside grid — cancel
                Gdx.app.log(TAG, "Drop outside grid — cancelled");
            } else if (targetCell[0] == originCellX && targetCell[1] == originCellY) {
                // Dropped back on same cell — treat as tap
                Gdx.app.log(TAG, "Dropped on same cell — not treating as tap");
                //eventManager.tap(draggedObjectId);
            } else {
                Grid grid = eventManager.getGridInstance();
                Cell target = grid.getCell(targetCell[0], targetCell[1]);

                if (target.isEmpty()) {
                    // ── MOVE to empty cell ──
                    moveToEmptyCell(targetCell[0], targetCell[1]);
                } else {
                    // ── MERGE or SWAP ──
                    String targetId = target.getOccupant();
                    Gdx.app.log(TAG, "Drop on occupied cell [" + targetCell[0] + ","
                        + targetCell[1] + "] targetId=" + targetId);
                    eventManager.swapOrMerge(draggedObjectId, targetId);
                }
            }
        }

        // Reset state
        resetDragState();
        return true;
    }

    // ── Public getters for rendering the drag visual ──

    public boolean isDragging() {
        return dragging;
    }

    public String getDraggedObjectId() {
        return draggedObjectId;
    }

    public float getDragX() {
        return dragPos.x;
    }

    public float getDragY() {
        return dragPos.y;
    }

    public int getOriginCellX() {
        return originCellX;
    }

    public int getOriginCellY() {
        return originCellY;
    }

    // ── Private helpers ──

    /**
     * Moves the dragged object to an empty cell.
     */
    private void moveToEmptyCell(int targetX, int targetY) {
        Grid grid = eventManager.getGridInstance();
        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();

        Gdx.app.log(TAG, "Moving " + draggedObjectId + " to empty cell ["
            + targetX + "," + targetY + "]");

        // Clear origin cell
        grid.getCell(originCellX, originCellY).setOccupant("default_tile");

        // Set target cell
        grid.setOnCell(draggedObjectId, targetX, targetY);

        // Update object position in the manager
        gom.update(draggedObjectId, targetX, targetY);
    }

    /**
     * Converts screen coordinates to world coordinates using the viewport.
     */
    private void toWorldCoords(int screenX, int screenY) {
        worldPos.set(screenX, screenY);
        viewport.unproject(worldPos);
    }

    /**
     * Converts world coordinates to grid cell indices.
     * Returns null if the position is outside the grid.
     */
    private int[] worldToCell(float wx, float wy) {
        // Check if within grid bounds
        float totalGridWidth = (cellSize * gridCols) + (cellGap * (gridCols - 1));
        float totalGridHeight = (cellSize * gridRows) + (cellGap * (gridRows - 1));

        if (wx < gridStartX || wx > gridStartX + totalGridWidth) return null;
        if (wy < gridStartY || wy > gridStartY + totalGridHeight) return null;

        // Calculate which cell
        float relX = wx - gridStartX;
        float relY = wy - gridStartY;

        int col = (int) (relX / (cellSize + cellGap));
        int row = (int) (relY / (cellSize + cellGap));

        // Check we're actually on a cell, not in a gap
        float cellLocalX = relX - col * (cellSize + cellGap);
        float cellLocalY = relY - row * (cellSize + cellGap);
        if (cellLocalX > cellSize || cellLocalY > cellSize) return null;

        // Flip Y: screen row 0 is at the top, but we draw row 0 at the top
        // using (rows - 1 - y) in rendering, so reverse it here
        int gridY = (gridRows - 1) - row;

        // Bounds check
        if (col < 0 || col >= gridCols || gridY < 0 || gridY >= gridRows) return null;

        return new int[]{col, gridY};
    }

    private void resetDragState() {
        touching = false;
        dragging = false;
        originCellX = -1;
        originCellY = -1;
        draggedObjectId = null;
    }
}
