package com.jipelski.mergerrealm.ui;

/**
 * Centralized layout configuration. All screen zones are defined here
 * as percentages of the world height, so nothing overlaps regardless
 * of grid size or device aspect ratio.
 *
 * Screen layout (top to bottom):
 *
 * +─────────────────────────────────+  ← WORLD_HEIGHT (800)
 * │          TOP BAR (22%)          │  Resources, level, info, nemesis
 * │  176px                          │
 * +─────────────────────────────────+  ← 624
 * │          WALL (18%)             │  Exploration, raids (future)
 * │  144px                          │
 * +─────────────────────────────────+  ← 480
 * │                                 │
 * │        GRID AREA (52%)          │  Battlefield grid (scales to fit)
 * │  416px                          │
 * │                                 │
 * +─────────────────────────────────+  ← 64
 * │       BOTTOM BAR (8%)           │  Build, shop, spells, quests
 * │  64px                           │
 * +─────────────────────────────────+  ← 0
 */
public class LayoutConfig {

    public static final float WORLD_WIDTH = 480f;
    public static final float WORLD_HEIGHT = 800f; // minimum height

    // ── The actual height at runtime (set by MergerRealmGame after viewport update) ──
    private static float actualHeight = WORLD_HEIGHT;

    public static void setActualHeight(float height) {
        actualHeight = height;
    }

    public static float getActualHeight() {
        return actualHeight;
    }

    // ── Zone percentages (applied to actual height) ──
    public static final float TOP_BAR_PCT = 0.22f;
    public static final float WALL_PCT = 0.18f;
    public static final float GRID_PCT = 0.52f;
    public static final float BOTTOM_BAR_PCT = 0.08f;

    // ── Dynamic zone heights ──
    public static float getTopBarHeight()    { return actualHeight * TOP_BAR_PCT; }
    public static float getWallHeight()      { return actualHeight * WALL_PCT; }
    public static float getGridHeight()      { return actualHeight * GRID_PCT; }
    public static float getBottomBarHeight() { return actualHeight * BOTTOM_BAR_PCT; }

    // ── Dynamic zone Y positions ──
    public static float getBottomBarY() { return 0f; }
    public static float getGridY()      { return getBottomBarHeight(); }
    public static float getWallY()      { return getGridY() + getGridHeight(); }
    public static float getTopBarY()    { return getWallY() + getWallHeight(); }

    // ── Grid internal padding ──
    public static final float GRID_PADDING = 8f;
    public static final float CELL_GAP = 4f;

    // ── Top bar sub-sections (anchored from actual top) ──
    public static float getResourcesY()     { return actualHeight - 18f; }
    public static float getResourcesRow2Y() { return actualHeight - 38f; }

    // ── Level box ──
    public static final float LEVEL_BOX_X = 4f;
    public static final float LEVEL_BOX_SIZE = 66f;
    public static float getLevelBoxY() { return getTopBarY() + 4f; }

    // ── Nemesis box ──
    public static final float NEMESIS_BOX_SIZE = LEVEL_BOX_SIZE;
    public static float getNemesisBoxX() { return WORLD_WIDTH - NEMESIS_BOX_SIZE - 4f; }
    public static float getNemesisBoxY() { return getLevelBoxY(); }

    // ── Info text area ──
    public static float getInfoTextX()     { return LEVEL_BOX_X + LEVEL_BOX_SIZE + 8f; }
    public static float getInfoTextWidth() { return getNemesisBoxX() - getInfoTextX() - 8f; }

    // ── Bottom bar buttons ──
    public static final float BTN_HEIGHT = 40f;
    public static final float BTN_GAP = 8f;
    public static final int BTN_COUNT = 4;
    public static final float BTN_TOTAL_GAP = BTN_GAP * (BTN_COUNT + 1);
    public static final float BTN_WIDTH = (WORLD_WIDTH - BTN_TOTAL_GAP) / BTN_COUNT;

    public static float getBtnY() {
        return getBottomBarY() + (getBottomBarHeight() - BTN_HEIGHT) / 2f;
    }

    public static float getButtonX(int index) {
        return BTN_GAP + index * (BTN_WIDTH + BTN_GAP);
    }

    // ── Grid cell sizing ──
    public static float calculateCellSize(int cols, int rows) {
        float availableWidth = WORLD_WIDTH - (GRID_PADDING * 2);
        float availableHeight = getGridHeight() - (GRID_PADDING * 2);
        float maxCellWidth = (availableWidth - (CELL_GAP * (cols - 1))) / cols;
        float maxCellHeight = (availableHeight - (CELL_GAP * (rows - 1))) / rows;
        return Math.min(maxCellWidth, maxCellHeight);
    }

    public static float calculateGridStartX(int cols, float cellSize) {
        float totalGridWidth = (cellSize * cols) + (CELL_GAP * (cols - 1));
        return (WORLD_WIDTH - totalGridWidth) / 2f;
    }

    public static float calculateGridStartY(int rows, float cellSize) {
        float totalGridHeight = (cellSize * rows) + (CELL_GAP * (rows - 1));
        return getGridY() + (getGridHeight() - totalGridHeight) / 2f;
    }
}
