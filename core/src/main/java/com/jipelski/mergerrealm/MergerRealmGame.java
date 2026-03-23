package com.jipelski.mergerrealm;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

import com.jipelski.mergerrealm.database.JsonManager;
import com.jipelski.mergerrealm.grid.Cell;
import com.jipelski.mergerrealm.grid.Grid;
import com.jipelski.mergerrealm.input.GridInputHandler;
import com.jipelski.mergerrealm.model.GameObject;
import com.jipelski.mergerrealm.util.EventManager;
import com.jipelski.mergerrealm.util.GridObjectManager;
import com.jipelski.mergerrealm.util.ResourceManager;
import com.jipelski.mergerrealm.util.SpriteManager;

public class MergerRealmGame extends ApplicationAdapter {

    private static final String TAG = "MergerRealmGame";

    // ── Rendering ──
    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private BitmapFont font;
    private OrthographicCamera camera;
    private FitViewport viewport;

    // ── Game world constants ──
    private static final float WORLD_WIDTH = 480f;
    private static final float WORLD_HEIGHT = 800f;

    // Grid rendering config
    private static final float GRID_PADDING = 16f;
    private static final float CELL_GAP = 4f;
    private float cellSize;
    private float gridStartX;
    private float gridStartY;

    // ── HUD area ──
    private static final float HUD_HEIGHT = 120f;       // resources at top
    private static final float INFO_BAR_HEIGHT = 50f;    // object info below resources

    // ── Game systems ──
    private JsonManager jsonManager;
    private EventManager eventManager;
    private SpriteManager spriteManager;
    private GridInputHandler inputHandler;

    // ── Game logic tick (1 second) ──
    private float tickTimer = 0f;
    private static final float TICK_INTERVAL = 1.0f;

    // ── Save system (dirty flag + interval) ──
    private boolean saveDirty = false;
    private float saveTimer = 0f;
    private static final float SAVE_INTERVAL = 5f;

    // ── Offline tracking ──
    private static final String TIMESTAMP_KEY = "last_active_timestamp";
    private static final long MAX_OFFLINE_SECONDS = 8 * 60 * 60; // cap at 8 hours

    // ── Animation state (runs every frame) ──
    private float animationTime = 0f;

    @Override
    public void create() {
        Gdx.app.log(TAG, "=== MergerRealm starting ===");

        // Set up rendering
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font = new BitmapFont();
        font.setColor(Color.WHITE);

        // Set up camera and viewport
        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);
        viewport.apply(true);

        // Load sprites
        spriteManager = new SpriteManager();
        spriteManager.loadFallback();

        // Initialize game systems
        jsonManager = new JsonManager();
        eventManager = new EventManager(jsonManager);

        // Calculate grid layout
        calculateGridLayout();

        // Set up input handling
        inputHandler = new GridInputHandler(eventManager, viewport);
        Grid grid = eventManager.getGridInstance();
        inputHandler.setGridLayout(gridStartX, gridStartY, cellSize, CELL_GAP,
            grid.getWidth(), grid.getHeight());
        Gdx.input.setInputProcessor(inputHandler);

        // Process any offline time
        processOfflineProgress();

        Gdx.app.log(TAG, "=== Init complete ===");
    }

    // ══════════════════════════════════════════════════════════════
    // OFFLINE RESOURCE GENERATION
    // ══════════════════════════════════════════════════════════════

    /**
     * Calculates how long the player was away and awards resources
     * for that time, capped at MAX_OFFLINE_SECONDS.
     */
    private void processOfflineProgress() {
        int[] savedTimestamp = jsonManager.loadArray(TIMESTAMP_KEY);
        if (savedTimestamp == null || savedTimestamp.length < 2) {
            Gdx.app.log(TAG, "No timestamp found — first launch");
            return;
        }

        // Reconstruct the saved time (stored as two ints: high and low bits)
        long savedTime = ((long) savedTimestamp[0] << 32) | (savedTimestamp[1] & 0xFFFFFFFFL);
        long now = System.currentTimeMillis();
        long elapsedMs = now - savedTime;

        if (elapsedMs <= 0) {
            Gdx.app.log(TAG, "Timestamp in the future — skipping offline calc");
            return;
        }

        long elapsedSeconds = elapsedMs / 1000;
        long cappedSeconds = Math.min(elapsedSeconds, MAX_OFFLINE_SECONDS);

        if (cappedSeconds < 2) {
            // Less than 2 seconds — not worth calculating
            return;
        }

        Gdx.app.log(TAG, "Offline for " + elapsedSeconds + "s (capped to " + cappedSeconds + "s)");

        // Award resources: simulate that many ticks of resource generation
        ResourceManager rm = eventManager.getResourceManager();
        for (long i = 0; i < cappedSeconds; i++) {
            rm.updateResources();
        }

        saveDirty = true;

        // TODO: show "Welcome back!" popup with offline earnings summary
        Gdx.app.log(TAG, "Offline resources awarded for " + cappedSeconds + " ticks");
    }

    /**
     * Saves the current timestamp so we can calculate offline time on next launch.
     */
    private void saveTimestamp() {
        long now = System.currentTimeMillis();
        int high = (int) (now >>> 32);
        int low = (int) now;
        jsonManager.saveArray(TIMESTAMP_KEY, new int[]{high, low});
    }

    // ══════════════════════════════════════════════════════════════
    // GRID LAYOUT
    // ══════════════════════════════════════════════════════════════

    private void calculateGridLayout() {
        Grid grid = eventManager.getGridInstance();
        int cols = grid.getWidth();
        int rows = grid.getHeight();

        float availableWidth = WORLD_WIDTH - (GRID_PADDING * 2);
        float availableHeight = WORLD_HEIGHT - HUD_HEIGHT - INFO_BAR_HEIGHT - (GRID_PADDING * 2);

        float maxCellWidth = (availableWidth - (CELL_GAP * (cols - 1))) / cols;
        float maxCellHeight = (availableHeight - (CELL_GAP * (rows - 1))) / rows;
        cellSize = Math.min(maxCellWidth, maxCellHeight);

        float totalGridWidth = (cellSize * cols) + (CELL_GAP * (cols - 1));
        gridStartX = (WORLD_WIDTH - totalGridWidth) / 2f;

        float totalGridHeight = (cellSize * rows) + (CELL_GAP * (rows - 1));
        float gridStartY = WORLD_HEIGHT - HUD_HEIGHT - INFO_BAR_HEIGHT - GRID_PADDING - totalGridHeight;

        Gdx.app.log(TAG, "Grid layout: cellSize=" + cellSize
            + " startX=" + gridStartX + " startY=" + gridStartY
            + " cols=" + cols + " rows=" + rows);
    }

    // ══════════════════════════════════════════════════════════════
    // MAIN LOOP
    // ══════════════════════════════════════════════════════════════

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        // ── 0. Input update (hold-to-spawn timer) ──
        inputHandler.update(delta);

        // ── 1. Animation time (every frame, ~60fps) ──
        animationTime += delta;

        // ── 2. Game logic tick (every 1 second) ──
        tickTimer += delta;
        if (tickTimer >= TICK_INTERVAL) {
            tickTimer -= TICK_INTERVAL;
            gameTick();
        }

        // ── 3. Deferred save (every 5 seconds, only if dirty) ──
        if (saveDirty) {
            saveTimer += delta;
            if (saveTimer >= SAVE_INTERVAL) {
                saveGame();
                saveDirty = false;
                saveTimer = 0f;
            }
        }

        // ── 4. Draw everything ──
        ScreenUtils.clear(0.12f, 0.12f, 0.18f, 1f);

        camera.update();
        batch.setProjectionMatrix(camera.combined);
        shapeRenderer.setProjectionMatrix(camera.combined);

        drawGridBackground();
        drawSelectionOutline();

        batch.begin();
        drawGrid();
        drawDraggedObject();
        drawHUD();
        drawInfoBar();
        batch.end();
    }

    private void gameTick() {
        ResourceManager rm = eventManager.getResourceManager();
        rm.updateResources();
        saveDirty = true;
    }

    /**
     * Call this from anywhere that changes game state
     * (EventManager actions, purchases, etc.)
     */
    public void markDirty() {
        saveDirty = true;
    }

    // ══════════════════════════════════════════════════════════════
    // RENDERING
    // ══════════════════════════════════════════════════════════════

    private void drawGridBackground() {
        Grid grid = eventManager.getGridInstance();
        int cols = grid.getWidth();
        int rows = grid.getHeight();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                float drawX = gridStartX + x * (cellSize + CELL_GAP);
                float drawY = gridStartY + (rows - 1 - y) * (cellSize + CELL_GAP);

                Cell cell = grid.getCell(x, y);

                if (inputHandler.isDragging()
                    && x == inputHandler.getOriginCellX()
                    && y == inputHandler.getOriginCellY()) {
                    shapeRenderer.setColor(0.35f, 0.35f, 0.15f, 1f);
                } else if (cell.isEmpty()) {
                    shapeRenderer.setColor(0.2f, 0.2f, 0.28f, 1f);
                } else {
                    shapeRenderer.setColor(0.25f, 0.25f, 0.35f, 1f);
                }

                shapeRenderer.rect(drawX, drawY, cellSize, cellSize);
            }
        }

        shapeRenderer.end();
    }

    private void drawGrid() {
        Grid grid = eventManager.getGridInstance();
        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();
        int cols = grid.getWidth();
        int rows = grid.getHeight();

        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                Cell cell = grid.getCell(x, y);
                if (cell.isEmpty()) continue;

                if (inputHandler.isDragging()
                    && x == inputHandler.getOriginCellX()
                    && y == inputHandler.getOriginCellY()) {
                    continue;
                }

                float drawX = gridStartX + x * (cellSize + CELL_GAP);
                float drawY = gridStartY + (rows - 1 - y) * (cellSize + CELL_GAP);

                String objectId = cell.getOccupant();
                GameObject obj = gom.getObject(objectId);

                Texture tex;
                if (obj != null) {
                    tex = spriteManager.getTextureForObject(obj.getType(), obj.getLvl());
                } else {
                    tex = spriteManager.getDefaultTile();
                }

                float margin = cellSize * 0.05f;
                batch.draw(tex, drawX + margin, drawY + margin,
                    cellSize - margin * 2, cellSize - margin * 2);
            }
        }
    }

    private void drawDraggedObject() {
        if (!inputHandler.isDragging()) return;

        String dragId = inputHandler.getDraggedObjectId();
        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();
        GameObject obj = gom.getObject(dragId);
        if (obj == null) return;

        Texture tex = spriteManager.getTextureForObject(obj.getType(), obj.getLvl());
        float dragSize = cellSize * 1.15f;
        float halfSize = dragSize / 2f;

        batch.setColor(1f, 1f, 1f, 0.85f);
        batch.draw(tex,
            inputHandler.getDragX() - halfSize,
            inputHandler.getDragY() - halfSize,
            dragSize, dragSize);
        batch.setColor(1f, 1f, 1f, 1f);
    }

    private void drawHUD() {
        ResourceManager rm = eventManager.getResourceManager();

        float hudY = WORLD_HEIGHT - 20f;
        float col1 = 20f;
        float col2 = 170f;
        float col3 = 320f;

        font.draw(batch, "Timber: " + rm.getAmount("timber"), col1, hudY);
        font.draw(batch, "Stone: " + rm.getAmount("quarrystone"), col2, hudY);
        font.draw(batch, "Iron: " + rm.getAmount("iron"), col3, hudY);

        float hudY2 = hudY - 25f;
        font.draw(batch, "Wood: " + rm.getAmount("wood"), col1, hudY2);
        font.draw(batch, "Wheat: " + rm.getAmount("wheat"), col2, hudY2);

        float hudY3 = hudY2 - 25f;
        font.draw(batch, "Stone T: " + rm.getAmount("stone"), col1, hudY3);
        font.draw(batch, "Fire: " + rm.getAmount("fire"), col2, hudY3);
    }

    /**
     * Draws a thick green outline around the selected facility cell.
     */
    private void drawSelectionOutline() {
        if (!inputHandler.hasSelection()) return;

        int selX = inputHandler.getSelectedCellX();
        int selY = inputHandler.getSelectedCellY();

        Grid grid = eventManager.getGridInstance();
        int rows = grid.getHeight();

        float drawX = gridStartX + selX * (cellSize + CELL_GAP);
        float drawY = gridStartY + (rows - 1 - selY) * (cellSize + CELL_GAP);
        float t = 3f; // outline thickness

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.2f, 0.85f, 0.2f, 1f); // bright green

        // Top edge
        shapeRenderer.rect(drawX - t, drawY + cellSize, cellSize + t * 2, t);
        // Bottom edge
        shapeRenderer.rect(drawX - t, drawY - t, cellSize + t * 2, t);
        // Left edge
        shapeRenderer.rect(drawX - t, drawY - t, t, cellSize + t * 2);
        // Right edge
        shapeRenderer.rect(drawX + cellSize, drawY - t, t, cellSize + t * 2);

        shapeRenderer.end();
    }

    /**
     * Draws object name and description in the info bar between
     * the resource HUD and the grid.
     */
    private void drawInfoBar() {
        String name = inputHandler.getSelectedDisplayName();
        String desc = inputHandler.getSelectedDescription();

        float barY = WORLD_HEIGHT - HUD_HEIGHT;
        float textX = 20f;

        if (name != null) {
            // Object name in white
            font.setColor(Color.WHITE);
            font.draw(batch, name, textX, barY - 10f);

            // Description in a softer color below
            if (desc != null) {
                font.setColor(0.7f, 0.7f, 0.8f, 1f);
                font.draw(batch, desc, textX, barY - 30f);
                font.setColor(Color.WHITE); // reset
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ══════════════════════════════════════════════════════════════

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void pause() {
        Gdx.app.log(TAG, "Paused — saving game...");
        saveGame();
        saveTimestamp();
        saveDirty = false;
        saveTimer = 0f;
    }

    @Override
    public void resume() {
        Gdx.app.log(TAG, "Resumed — checking offline progress...");
        processOfflineProgress();
    }

    @Override
    public void dispose() {
        Gdx.app.log(TAG, "Disposing...");
        saveGame();
        saveTimestamp();
        batch.dispose();
        shapeRenderer.dispose();
        font.dispose();
        spriteManager.dispose();
    }

    private void saveGame() {
        try {
            Grid grid = eventManager.getGridInstance();
            jsonManager.saveArrayList("grid_array", grid.getArr());
            jsonManager.saveArray("grid_size", grid.getXoY());
            jsonManager.saveGridObjects("object_map",
                eventManager.getGRID_OBJECT_MANAGER().getObjectMap());
            jsonManager.saveResources("consumable_map",
                eventManager.getResourceManager().getConsumableMap());
            Gdx.app.log(TAG, "Game saved successfully");
        } catch (Exception e) {
            Gdx.app.error(TAG, "Failed to save game", e);
        }
    }
}
