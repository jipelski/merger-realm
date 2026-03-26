package com.jipelski.mergerrealm.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;

import com.jipelski.mergerrealm.grid.Cell;
import com.jipelski.mergerrealm.grid.Grid;
import com.jipelski.mergerrealm.model.GameObject;
import com.jipelski.mergerrealm.ui.BuildMenu;
import com.jipelski.mergerrealm.ui.WallGate;
import com.jipelski.mergerrealm.util.EventManager;
import com.jipelski.mergerrealm.util.GridObjectManager;

import com.jipelski.mergerrealm.ui.OfflinePopup;

public class GridInputHandler extends InputAdapter {

    private static final String TAG = "GridInputHandler";
    private static final float DRAG_THRESHOLD = 8f;
    private static final float HOLD_SPAWN_INTERVAL = 0.25f;
    private static final float HOLD_DELAY = 0.35f;

    private final EventManager eventManager;
    private final Viewport viewport;

    // Grid layout
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
    private final Vector2 dragPos = new Vector2();

    // Selection state — persists across touches until something else is selected
    private int selectedCellX = -1;
    private int selectedCellY = -1;
    private String selectedObjectId = null;

    // Tracks if the facility was already selected before this touch
    // (so we know to spawn on tap-up rather than just selecting)
    private boolean wasAlreadySelected = false;

    // Hold-to-spawn state (facilities only)
    private boolean holding = false;
    private float holdTimer = 0f;
    private float holdDelay = 0f;
    private String holdFacilityId = null;

    private final Vector2 worldPos = new Vector2();

    private BuildMenu buildMenu;

    private float buildBtnX, buildBtnY, buildBtnW, buildBtnH;

    private float lockBtnX, lockBtnY, lockBtnW, lockBtnH;

    private OfflinePopup offlinePopup;

    private WallGate wallGate;

    public GridInputHandler(EventManager eventManager, Viewport viewport) {
        this.eventManager = eventManager;
        this.viewport = viewport;
    }

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

    public void setBuildMenu(BuildMenu buildMenu) {
        this.buildMenu = buildMenu;
    }

    public void setOfflinePopup(OfflinePopup popup) {
        this.offlinePopup = popup;
    }

    public void setLockButtonBounds(float x, float y, float w, float h) {
        this.lockBtnX = x;
        this.lockBtnY = y;
        this.lockBtnW = w;
        this.lockBtnH = h;
    }

    private boolean isLockButtonTap(float wx, float wy) {
        return wx >= lockBtnX && wx <= lockBtnX + lockBtnW
            && wy >= lockBtnY && wy <= lockBtnY + lockBtnH;
    }

    public void setWallGate(WallGate wallGate) {
        this.wallGate = wallGate;
    }

    /**
     * Called every frame from render(). Handles hold-to-spawn timing.
     */
    public void update(float delta) {
        if (!holding || holdFacilityId == null) return;

        holdDelay += delta;
        if (holdDelay < HOLD_DELAY) return;

        holdTimer += delta;
        if (holdTimer >= HOLD_SPAWN_INTERVAL) {
            holdTimer -= HOLD_SPAWN_INTERVAL;
            eventManager.spawnFromFacility(holdFacilityId);
        }
    }

    // ── InputAdapter overrides ──

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (pointer != 0) return false;

        toWorldCoords(screenX, screenY);

        if (offlinePopup != null && offlinePopup.isVisible()) {
            offlinePopup.handleTouch(worldPos.x, worldPos.y);
            return true;
        }

        // Build menu intercepts touches in its area
        if (buildMenu != null && buildMenu.isVisible()) {
            if (buildMenu.touchDown(worldPos.x, worldPos.y)) {
                return true;
            }
            // Touch was above the menu — close it
            buildMenu.hide();
            return true;
        }

        // Check build button tap
        if (buildMenu != null && !buildMenu.isVisible() && isBuildButtonTap(worldPos.x, worldPos.y)) {
            buildMenu.toggle();
            return true;
        }

        if (wallGate != null && wallGate.isInWallArea(worldPos.y)) {
            wallGate.handleTouch(worldPos.x, worldPos.y);
            return true;
        }

        if (selectedObjectId != null && isLockButtonTap(worldPos.x, worldPos.y)) {
            eventManager.getGRID_OBJECT_MANAGER().toggleLock(selectedObjectId);
            return true;
        }

        // Normal grid input from here
        int[] cell = worldToCell(worldPos.x, worldPos.y);
        if (cell == null) {
            return false;
        }

        Grid grid = eventManager.getGridInstance();
        Cell gridCell = grid.getCell(cell[0], cell[1]);

        if (gridCell.isEmpty()) {
            return false;
        }

        String objectId = gridCell.getOccupant();
        GameObject obj = eventManager.getGRID_OBJECT_MANAGER().getObject(objectId);

        // Select immediately on touch
        wasAlreadySelected = objectId.equals(selectedObjectId);
        selectedCellX = cell[0];
        selectedCellY = cell[1];
        selectedObjectId = objectId;

        // Start tracking touch
        touching = true;
        dragging = false;
        originCellX = cell[0];
        originCellY = cell[1];
        draggedObjectId = objectId;
        touchStart.set(worldPos);
        dragPos.set(worldPos);

        // Check if object is locked — if so, allow tap but prevent drag
        boolean isLocked = eventManager.getGRID_OBJECT_MANAGER().isLocked(objectId);


        // Start hold timer if touching an UNLOCKED facility
        if (obj != null && isFacility(obj.getType()) && !isLocked) {
            holding = true;
            holdTimer = 0f;
            holdDelay = 0f;
            holdFacilityId = objectId;
        }
        // Allow hold-to-spawn even if locked (tapping still works)
        if (obj != null && isFacility(obj.getType()) && isLocked) {
            holding = true;
            holdTimer = 0f;
            holdDelay = 0f;
            holdFacilityId = objectId;
        }

        return true;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (pointer != 0) return false;

        toWorldCoords(screenX, screenY);

        // Forward to build menu if it's handling a scroll
        if (buildMenu != null && buildMenu.isVisible()) {
            if (buildMenu.touchDragged(worldPos.x, worldPos.y)) {
                return true;
            }
        }

        if (!touching) return false;

        dragPos.set(worldPos);

        if (!dragging && touchStart.dst(worldPos) > DRAG_THRESHOLD) {
            // Don't allow dragging locked objects
            if (eventManager.getGRID_OBJECT_MANAGER().isLocked(draggedObjectId)) {
                return true; // consume event but don't start drag
            }
            dragging = true;
            stopHolding();
            Gdx.app.log(TAG, "Drag started from [" + originCellX + "," + originCellY + "]");
        }

        return true;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (pointer != 0) return false;

        toWorldCoords(screenX, screenY);

        // Forward to build menu
        if (buildMenu != null && buildMenu.isVisible()) {
            if (buildMenu.touchUp(worldPos.x, worldPos.y)) {
                return true;
            }
        }

        if (!touching) return false;

        stopHolding();

        if (!dragging) {
            handleTap();
        } else {
            handleDrop();
        }

        touching = false;
        dragging = false;
        originCellX = -1;
        originCellY = -1;
        draggedObjectId = null;
        return true;
    }

    // ── Tap handling ──

    private void handleTap() {
        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();
        GameObject obj = gom.getObject(draggedObjectId);
        if (obj == null) return;

        if (isFacility(obj.getType())) {
            handleFacilityTap();
        } else {
            // Non-facility: normal tap (token collect, chest open, etc.)
            eventManager.tap(draggedObjectId);
        }
    }

    private void handleFacilityTap() {
        if (wasAlreadySelected) {
            // Second tap on same facility — spawn a unit
            eventManager.spawnFromFacility(draggedObjectId);
            Gdx.app.log(TAG, "Spawning from selected facility: " + draggedObjectId);
        } else {
            // First tap — just selected (already done in touchDown)
            Gdx.app.log(TAG, "Selected facility at [" + selectedCellX + "," + selectedCellY + "]");
        }
    }

    // ── Drop handling ──

    private void handleDrop() {
        int[] targetCell = worldToCell(worldPos.x, worldPos.y);

        if (targetCell == null) {
            Gdx.app.log(TAG, "Drop outside grid — cancelled");
            return;
        }

        if (targetCell[0] == originCellX && targetCell[1] == originCellY) {
            return;
        }

        Grid grid = eventManager.getGridInstance();
        Cell target = grid.getCell(targetCell[0], targetCell[1]);

        if (target.isEmpty()) {
            moveToEmptyCell(targetCell[0], targetCell[1]);
            // Update selection to new position
            selectedCellX = targetCell[0];
            selectedCellY = targetCell[1];
        } else {
            String targetId = target.getOccupant();

            // Block merge/swap if target is locked
            if (eventManager.getGRID_OBJECT_MANAGER().isLocked(targetId)) {
                Gdx.app.log(TAG, "Target is locked — cannot merge or swap");
                return; // object snaps back to origin
            }

            eventManager.swapOrMerge(draggedObjectId, targetId);
            // After merge/swap, update selection to where the object ended up
            GameObject obj = eventManager.getGRID_OBJECT_MANAGER().getObject(draggedObjectId);

            if (obj != null) {
                selectedCellX = obj.getxPos();
                selectedCellY = obj.getyPos();
            } else {
                // Object was consumed (merge or combat) — clear selection
                clearSelection();
            }
        }
    }

    // ── Public getters ──

    public boolean isDragging() { return dragging; }
    public String getDraggedObjectId() { return draggedObjectId; }
    public float getDragX() { return dragPos.x; }
    public float getDragY() { return dragPos.y; }
    public int getOriginCellX() { return originCellX; }
    public int getOriginCellY() { return originCellY; }

    public boolean hasSelection() { return selectedObjectId != null; }
    public int getSelectedCellX() { return selectedCellX; }
    public int getSelectedCellY() { return selectedCellY; }
    public String getSelectedObjectId() { return selectedObjectId; }

    /**
     * Returns the description of the currently selected object, or null.
     */
    public String getSelectedDescription() {
        if (selectedObjectId == null) return null;
        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();
        GameObject obj = gom.getObject(selectedObjectId);
        if (obj == null) return null;
        return obj.getDescription();
    }

    /**
     * Returns a display name for the selected object (type + level).
     */
    public String getSelectedDisplayName() {
        if (selectedObjectId == null) return null;
        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();
        GameObject obj = gom.getObject(selectedObjectId);
        if (obj == null) return null;

        String type = obj.getType();
        // Capitalize first letter
        String name = type.substring(0, 1).toUpperCase() + type.substring(1);
        return name + " (Lv." + obj.getLvl() + ")";
    }

    // ── Private helpers ──

    public void clearSelection() {
        selectedCellX = -1;
        selectedCellY = -1;
        selectedObjectId = null;
    }

    private void stopHolding() {
        holding = false;
        holdTimer = 0f;
        holdDelay = 0f;
        holdFacilityId = null;
    }

    private boolean isFacility(String type) {
        switch (type) {
            case "archeryrange": case "farmhouse": case "barracks":
            case "griffinnest": case "monastery":
                return true;
            default:
                return false;
        }
    }

    private void moveToEmptyCell(int targetX, int targetY) {
        Grid grid = eventManager.getGridInstance();
        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();

        grid.getCell(originCellX, originCellY).setOccupant("default_tile");
        grid.setOnCell(draggedObjectId, targetX, targetY);
        gom.update(draggedObjectId, targetX, targetY);
    }

    private void toWorldCoords(int screenX, int screenY) {
        worldPos.set(screenX, screenY);
        viewport.unproject(worldPos);
    }

    private int[] worldToCell(float wx, float wy) {
        float totalGridWidth = (cellSize * gridCols) + (cellGap * (gridCols - 1));
        float totalGridHeight = (cellSize * gridRows) + (cellGap * (gridRows - 1));

        if (wx < gridStartX || wx > gridStartX + totalGridWidth) return null;
        if (wy < gridStartY || wy > gridStartY + totalGridHeight) return null;

        float relX = wx - gridStartX;
        float relY = wy - gridStartY;

        int col = (int) (relX / (cellSize + cellGap));
        int row = (int) (relY / (cellSize + cellGap));

        float cellLocalX = relX - col * (cellSize + cellGap);
        float cellLocalY = relY - row * (cellSize + cellGap);
        if (cellLocalX > cellSize || cellLocalY > cellSize) return null;

        int gridY = (gridRows - 1) - row;

        if (col < 0 || col >= gridCols || gridY < 0 || gridY >= gridRows) return null;

        return new int[]{col, gridY};
    }

    public void setBuildButtonBounds(float x, float y, float w, float h) {
        this.buildBtnX = x;
        this.buildBtnY = y;
        this.buildBtnW = w;
        this.buildBtnH = h;
    }

    private boolean isBuildButtonTap(float wx, float wy) {
        return wx >= buildBtnX && wx <= buildBtnX + buildBtnW
            && wy >= buildBtnY && wy <= buildBtnY + buildBtnH;
    }
}
