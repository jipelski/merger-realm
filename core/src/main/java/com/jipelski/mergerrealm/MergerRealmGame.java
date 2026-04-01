package com.jipelski.mergerrealm;

import static com.jipelski.mergerrealm.util.EventManager.PERIODIC_FACILITIES;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

import com.jipelski.mergerrealm.database.JsonManager;
import com.jipelski.mergerrealm.data.FacilityData;
import com.jipelski.mergerrealm.data.GenData;
import com.jipelski.mergerrealm.data.MonsterData;
import com.jipelski.mergerrealm.data.StorageData;
import com.jipelski.mergerrealm.data.TokenData;
import com.jipelski.mergerrealm.data.UnitData;
import com.jipelski.mergerrealm.data.ChestData;
import com.jipelski.mergerrealm.data.PrinceData;
import com.jipelski.mergerrealm.grid.Cell;
import com.jipelski.mergerrealm.grid.Grid;
import com.jipelski.mergerrealm.input.GridInputHandler;
import com.jipelski.mergerrealm.model.Chest;
import com.jipelski.mergerrealm.model.GameObject;
import com.jipelski.mergerrealm.model.Item;
import com.jipelski.mergerrealm.model.Monster;
import com.jipelski.mergerrealm.model.Facility;
import com.jipelski.mergerrealm.model.Unit;
import com.jipelski.mergerrealm.ui.ExplorePanel;
import com.jipelski.mergerrealm.ui.WallGate;
import com.jipelski.mergerrealm.util.BattleFieldManager;
import com.jipelski.mergerrealm.util.EventManager;
import com.jipelski.mergerrealm.util.GameDataLoader;
import com.jipelski.mergerrealm.util.GameEventListener;
import com.jipelski.mergerrealm.util.GridObjectManager;
import com.jipelski.mergerrealm.util.ResourceManager;
import com.jipelski.mergerrealm.util.SpriteManager;

import com.jipelski.mergerrealm.ui.BuildMenu;
import com.jipelski.mergerrealm.ui.InventoryMenu;
import com.jipelski.mergerrealm.ui.LayoutConfig;
import com.jipelski.mergerrealm.ui.UITextureManager;
import com.jipelski.mergerrealm.ui.OfflinePopup;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MergerRealmGame extends ApplicationAdapter implements GameEventListener {

    private static final String TAG = "MergerRealmGame";

    // ── Rendering ──
    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private BitmapFont font;
    private BitmapFont fontSmall;
    private GlyphLayout glyphLayout;
    private OrthographicCamera camera;
    private ExtendViewport viewport;

    // ── Persistent nemesis display ──
    private String displayedNemesis = null;

    // ── World constants ──
    private float cellSize;
    private float gridStartX;
    private float gridStartY;

    // ── Game systems ──
    private JsonManager jsonManager;
    private EventManager eventManager;
    private SpriteManager spriteManager;
    private GridInputHandler inputHandler;

    // ── Game tick ──
    private float tickTimer = 0f;
    private static final float TICK_INTERVAL = 1.0f;

    private float resourceTimer = 0f;
    private static final float RESOURCE_INTERVAL = 15f;

    // ── Save system ──
    private boolean saveDirty = false;
    private float saveTimer = 0f;
    private static final float SAVE_INTERVAL = 5f;

    private static final float LOCK_BTN_SIZE = 30f;

    // ── Offline tracking ──
    private static final String TIMESTAMP_KEY = "last_active_timestamp";
    private static final long MAX_OFFLINE_SECONDS = 8 * 60 * 60;

    // ── Animation ──
    private float animationTime = 0f;

    // ── Menus ──
    private BuildMenu buildMenu;
    private boolean buildMenuOpen = false;

    private InventoryMenu inventoryMenu;

    private ExplorePanel explorePanel;

    private float gridStartYShifted;

    private UITextureManager uiTex;
    private OfflinePopup offlinePopup;

    private WallGate wallGate;
    private Texture whiteTex;

    @Override
    public void create() {
        Gdx.app.log(TAG, "=== MergerRealm starting ===");

        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        glyphLayout = new GlyphLayout();

        font = new BitmapFont();
        font.setColor(Color.WHITE);

        fontSmall = new BitmapFont();
        fontSmall.getData().setScale(0.8f);
        fontSmall.setColor(Color.WHITE);

        camera = new OrthographicCamera();
        viewport = new ExtendViewport(LayoutConfig.WORLD_WIDTH , LayoutConfig.WORLD_HEIGHT , camera);
        viewport.apply(true);

        uiTex = new UITextureManager();
        uiTex.load();

        spriteManager = new SpriteManager();
        spriteManager.loadFallback();

        com.badlogic.gdx.graphics.Pixmap px = new com.badlogic.gdx.graphics.Pixmap(1, 1, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        px.setColor(Color.WHITE);
        px.fill();
        whiteTex = new Texture(px);
        px.dispose();

        jsonManager = new JsonManager();
        eventManager = new EventManager(jsonManager);

        eventManager.getBattleFieldManager().setGameEventListener(this);

        wallGate = new WallGate(eventManager, spriteManager, uiTex);
        wallGate.updateLayout();

        calculateGridLayout();

        inputHandler = new GridInputHandler(eventManager, viewport);
        Grid grid = eventManager.getGridInstance();
        inputHandler.setGridLayout(gridStartX, gridStartY, cellSize, LayoutConfig.CELL_GAP,
            grid.getWidth(), grid.getHeight());
        Gdx.input.setInputProcessor(inputHandler);


        buildMenu = new BuildMenu(eventManager, spriteManager, viewport, LayoutConfig.WORLD_WIDTH , LayoutConfig.WORLD_HEIGHT );
        inputHandler.setBuildMenu(buildMenu);

        inventoryMenu = new InventoryMenu(eventManager, spriteManager, viewport, uiTex);
        inputHandler.setInventoryMenu(inventoryMenu);
        inputHandler.setInventoryButtonBounds(
            LayoutConfig.getButtonX(1), LayoutConfig.getBtnY(),
            LayoutConfig.BTN_WIDTH, LayoutConfig.BTN_HEIGHT);

        explorePanel = new ExplorePanel(eventManager, spriteManager, viewport, uiTex);
        inputHandler.setExplorePanel(explorePanel);

        wallGate.setExplorePanel(explorePanel);

        offlinePopup = new OfflinePopup(uiTex);

        gridStartYShifted = BuildMenu.MENU_HEIGHT + LayoutConfig.GRID_PADDING ;

        inputHandler.setBuildMenu(buildMenu);
        inputHandler.setBuildButtonBounds(
            LayoutConfig.getButtonX(0), LayoutConfig.getBtnY(),
            LayoutConfig.BTN_WIDTH, LayoutConfig.BTN_HEIGHT);

        inputHandler.setOfflinePopup(offlinePopup);

        float lockBtnX = LayoutConfig.getInfoTextX() + LayoutConfig.getInfoTextWidth() - LOCK_BTN_SIZE - 2f;
        float lockBtnY = LayoutConfig.getLevelBoxY() + (LayoutConfig.LEVEL_BOX_SIZE - LOCK_BTN_SIZE) / 2f;
        inputHandler.setLockButtonBounds(lockBtnX, lockBtnY, LOCK_BTN_SIZE, LOCK_BTN_SIZE);

        /*float lockBtnX = LayoutConfig.getNemesisBoxX() + (LayoutConfig.NEMESIS_BOX_SIZE - LOCK_BTN_SIZE) / 2f;
        float lockBtnY = LayoutConfig.getLevelBoxY() - LOCK_BTN_SIZE - 4f;
        inputHandler.setLockButtonBounds(lockBtnX, lockBtnY, LOCK_BTN_SIZE, LOCK_BTN_SIZE);*/

        inputHandler.setWallGate(wallGate);

        LayoutConfig.setActualHeight(viewport.getWorldHeight());
        // calculateGridLayout();
        processOfflineProgress();

        Gdx.app.log(TAG, "=== Init complete ===");
    }

    // ══════════════════════════════════════════════════════════════
    // OFFLINE
    // ══════════════════════════════════════════════════════════════

    private void processOfflineProgress() {

        Gdx.app.log(TAG, "offline progress");
        int[] savedTimestamp = jsonManager.loadArray(TIMESTAMP_KEY);
        if (savedTimestamp == null || savedTimestamp.length < 2) return;

        long savedTime = ((long) savedTimestamp[0] << 32) | (savedTimestamp[1] & 0xFFFFFFFFL);
        long now = System.currentTimeMillis();
        long elapsedMs = now - savedTime;
        if (elapsedMs <= 0) return;

        long cappedSeconds = elapsedMs / 1000;
        if (cappedSeconds < 15) return;

        long ticks = cappedSeconds / 15;

        ResourceManager rm = eventManager.getResourceManager();
        int foodBefore = rm.getAmount("food");
        int woodBefore = rm.getAmount("wood");
        int ironBefore = rm.getAmount("iron");

        for (long i = 0; i < ticks; i++) {
            rm.updateResources();
        }

        int foodGained = rm.getAmount("food") - foodBefore;
        int woodGained = rm.getAmount("wood") - woodBefore;
        int ironGained = rm.getAmount("iron") - ironBefore;

        if (eventManager.getExplorationManager() != null) {
            eventManager.getExplorationManager().update();
            Gdx.app.log(TAG, "Processed offline exploration events");
        }

        int periodicSpawned = 0;
        int periodicHeld = 0;
        List<Facility> periodicList = new ArrayList<>();
        for (GameObject obj : eventManager.getGRID_OBJECT_MANAGER().getObjectMap().values()) {
            if (PERIODIC_FACILITIES.contains(obj.getType())) {
                periodicList.add((Facility) obj);
            }
        }

        for (Facility facility : periodicList) {
            FacilityData data = (FacilityData) eventManager.getGameDataLoader()
                .getGameData(facility.getType(), facility.getLvl());
            if (data == null || data.getTimeCost() <= 0) continue;

            // How many spawns would have happened offline?
            int offlineSpawns = (int)(cappedSeconds / data.getTimeCost());
            if (offlineSpawns <= 0) continue;

            GameDataLoader gdl = eventManager.getGameDataLoader();

            for (int i = 0; i < offlineSpawns; i++) {
                String[] unitString = facility.spawn(
                    gdl.getSpawnConfiguration(
                        facility.getType() + "_" + facility.getLvl()));
                if (unitString == null) continue;

                String unitType = unitString[0];
                int unitLevel = Integer.parseInt(unitString[1]);

                // Try adjacent cell first
                int[] adjacent = eventManager.getAdjacentEmptyCell(
                    facility.getxPos(), facility.getyPos());
                if (adjacent != null) {
                    eventManager.spawnObject(unitType, unitLevel,
                        adjacent[0], adjacent[1]);
                    periodicSpawned++;
                } else if (facility.getHeldCount() < facility.getHoldCapacity()) {
                    // Store internally
                    facility.addHeldUnit(unitType, unitLevel);
                    periodicHeld++;
                } else {
                    // Both full — stop spawning for this facility
                    break;
                }
            }
        }

        if (periodicSpawned + periodicHeld > 0) {
            Gdx.app.log(TAG, "Offline periodic: " + periodicSpawned
                + " spawned, " + periodicHeld + " held");
        }

        // Show popup instead of timed message
        offlinePopup.show(cappedSeconds, foodGained, woodGained, ironGained);

        saveDirty = true;
        Gdx.app.log(TAG, "Offline: " + cappedSeconds + "s, food+"
            + foodGained + " wood+" + woodGained + " iron+" + ironGained);
    }

    private void saveTimestamp() {
        long now = System.currentTimeMillis();
        jsonManager.saveArray(TIMESTAMP_KEY, new int[]{(int) (now >>> 32), (int) now});
    }

    // ══════════════════════════════════════════════════════════════
    // GRID LAYOUT
    // ══════════════════════════════════════════════════════════════

    private void calculateGridLayout() {
        Grid grid = eventManager.getGridInstance();
        int cols = grid.getWidth();
        int rows = grid.getHeight();

        cellSize = LayoutConfig.calculateCellSize(cols, rows);
        gridStartX = LayoutConfig.calculateGridStartX(cols, cellSize);
        gridStartY = LayoutConfig.calculateGridStartY(rows, cellSize);

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

        // Don't process input/ticks while popup is visible
        if (!offlinePopup.isVisible() && !inventoryMenu.isBrowsing() && !explorePanel.isVisible()) {
            inputHandler.update(delta);
            eventManager.updatePeriodicFacilities(delta);
            inventoryMenu.update(delta);

            tickTimer += delta;
            if (tickTimer >= TICK_INTERVAL) {
                tickTimer -= TICK_INTERVAL;
                gameTick();
            }

            if (saveDirty) {
                saveTimer += delta;
                if (saveTimer >= SAVE_INTERVAL) {
                    saveGame();
                    saveDirty = false;
                    saveTimer = 0f;
                }
            }
        }

        animationTime += delta;
        buildMenuOpen = buildMenu.isVisible();

        ScreenUtils.clear(0.12f, 0.12f, 0.18f, 1f);
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        shapeRenderer.setProjectionMatrix(camera.combined);

        if (buildMenuOpen) {
            drawGridBackgroundShifted();
            drawSelectionOutlineShifted();
            drawHighlightsShifted();
            drawLockIndicatorsShifted();
            buildMenu.drawBackground(shapeRenderer);

            batch.begin();
            drawGridShifted();
            drawDraggedObject();
            buildMenu.drawContent(batch, font, fontSmall);
            batch.end();
        } else if (inventoryMenu.isSelectingUnit()) {
            drawGridBackground();
            drawSelectionOutline();
            drawHighlights();
            drawLockIndicators();
            drawInventoryTints();

            batch.begin();

            drawGrid();
            drawDraggedObject();

            batch.end();
        } else {
            // Shape pass (only for things not yet converted to textures)
            drawGridBackground();
            drawSelectionOutline();
            drawHighlights();
            drawLockIndicators();
            wallGate.drawBackground(shapeRenderer);

            // Sprite/text pass
            batch.begin();

            // Top bar panels
            uiTex.drawPanel(batch, uiTex.panelMedium,
                LayoutConfig.LEVEL_BOX_X, LayoutConfig.getLevelBoxY(),
                LayoutConfig.LEVEL_BOX_SIZE, LayoutConfig.LEVEL_BOX_SIZE);

            uiTex.drawPanel(batch, uiTex.panelDark,
                LayoutConfig.getInfoTextX(), LayoutConfig.getLevelBoxY(),
                LayoutConfig.getInfoTextWidth(), LayoutConfig.LEVEL_BOX_SIZE);

            uiTex.drawPanel(batch, uiTex.panelMedium,
                LayoutConfig.getNemesisBoxX(), LayoutConfig.getNemesisBoxY(),
                LayoutConfig.NEMESIS_BOX_SIZE, LayoutConfig.NEMESIS_BOX_SIZE);


            // XP progress bar
            BattleFieldManager bfm = eventManager.getBattleFieldManager();
            float xpRatio = (float) bfm.getCurrent_xp() / Math.max(1, bfm.getXp_required());
            float barWidth = LayoutConfig.LEVEL_BOX_SIZE - 8f;
            float barX = LayoutConfig.LEVEL_BOX_X + 4f;
            float barY = LayoutConfig.getLevelBoxY() + 4f;
            uiTex.drawProgressBar(batch, uiTex.barXpBg, uiTex.barXpFill,
                barX, barY, barWidth, 8f, xpRatio);

            // Nemesis progress bar
            drawMonsterCounterBarTextured();

            drawHUD();
            drawInfoBarContentTextured();
            drawLockButton();

            uiTex.drawPanel(batch, uiTex.panelDark,
                0, LayoutConfig.getWallY(),
                LayoutConfig.WORLD_WIDTH, LayoutConfig.getWallHeight());
            wallGate.drawContent(batch, font, fontSmall);

            drawGrid();
            drawDraggedObject();
            drawBottomBarTextured();

            batch.end();
        }

        if (inventoryMenu.isVisible()) {
            inventoryMenu.drawBackground(shapeRenderer);
            batch.begin();
            inventoryMenu.drawContent(batch, font, fontSmall);
            batch.end();
        }

        // Exploration tints (during unit selection)
        if (explorePanel.isSelectingUnit()) {
            drawExploreTints();
        }

        // Exploration panel overlay
        if (explorePanel.isVisible()) {
            explorePanel.drawBackground(shapeRenderer);
            batch.begin();
            explorePanel.drawContent(batch, font, fontSmall);
            batch.end();
        }

        // IMPORTANT: Call explorationManager.update() ALWAYS (even when panel is open)
        // so events process in real time:
        if (!offlinePopup.isVisible()) {
            eventManager.getExplorationManager().update();
        }

        // Popup always draws on top of everything
        if (offlinePopup.isVisible()) {
            batch.begin();
            offlinePopup.draw(batch, font, fontSmall);
            batch.end();
        }
    }

    private void gameTick() {
        resourceTimer += TICK_INTERVAL;
        if (resourceTimer >= RESOURCE_INTERVAL) {
            resourceTimer -= RESOURCE_INTERVAL;
            ResourceManager rm = eventManager.getResourceManager();
            rm.updateResources();
            eventManager.healWoundedUnits();
        }
        saveDirty = true;
    }

    public void markDirty() {
        saveDirty = true;
    }

    // ══════════════════════════════════════════════════════════════
    // GRID RENDERING
    // ══════════════════════════════════════════════════════════════

    private void drawGridBackground() {
        Grid grid = eventManager.getGridInstance();
        int cols = grid.getWidth();
        int rows = grid.getHeight();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                float drawX = gridStartX + x * (cellSize + LayoutConfig.CELL_GAP);
                float drawY = gridStartY + (rows - 1 - y) * (cellSize + LayoutConfig.CELL_GAP);
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

    private void drawSelectionOutline() {
        if (!inputHandler.hasSelection()) return;

        int selX = inputHandler.getSelectedCellX();
        int selY = inputHandler.getSelectedCellY();
        Grid grid = eventManager.getGridInstance();
        int rows = grid.getHeight();

        float drawX = gridStartX + selX * (cellSize + LayoutConfig.CELL_GAP);
        float drawY = gridStartY + (rows - 1 - selY) * (cellSize + LayoutConfig.CELL_GAP);
        float t = 3f;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.2f, 0.85f, 0.2f, 1f);
        shapeRenderer.rect(drawX - t, drawY + cellSize, cellSize + t * 2, t);
        shapeRenderer.rect(drawX - t, drawY - t, cellSize + t * 2, t);
        shapeRenderer.rect(drawX - t, drawY - t, t, cellSize + t * 2);
        shapeRenderer.rect(drawX + cellSize, drawY - t, t, cellSize + t * 2);
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

                float drawX = gridStartX + x * (cellSize + LayoutConfig.CELL_GAP);
                float drawY = gridStartY + (rows - 1 - y) * (cellSize + LayoutConfig.CELL_GAP);

                String objectId = cell.getOccupant();
                GameObject obj = gom.getObject(objectId);
                Texture tex = (obj != null)
                    ? spriteManager.getTextureForObject(obj.getType(), obj.getLvl())
                    : spriteManager.getDefaultTile();

                float margin = cellSize * 0.05f;
                batch.draw(tex, drawX + margin, drawY + margin,
                    cellSize - margin * 2, cellSize - margin * 2);
                if (obj instanceof Unit) {
                    Unit unit = (Unit) obj;
                    if (unit.isWounded()) {
                        float barWidth = cellSize - margin * 4;
                        float barHeight = 3f;
                        float barX = drawX + margin * 2;
                        float barY = drawY + margin;
                        float hpRatio = (float) unit.getHp() / unit.getMax_hp();

                        // Background (dark red)
                        batch.setColor(0.4f, 0.1f, 0.1f, 0.8f);
                        // Use a 1x1 white pixel texture or uiTex for the bar
                        batch.draw(whiteTex, barX, barY, barWidth, barHeight);

                        // Fill (green to red based on HP)
                        float r = 1f - hpRatio;
                        float g = hpRatio;
                        batch.setColor(r, g, 0.1f, 0.9f);
                        batch.draw(whiteTex, barX, barY, barWidth * hpRatio, barHeight);

                        batch.setColor(1f, 1f, 1f, 1f); // reset
                    }
                }
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
        float half = dragSize / 2f;

        batch.setColor(1f, 1f, 1f, 0.85f);
        batch.draw(tex, inputHandler.getDragX() - half,
            inputHandler.getDragY() - half, dragSize, dragSize);
        batch.setColor(1f, 1f, 1f, 1f);
    }

    // ══════════════════════════════════════════════════════════════
    // HUD (top resource bar)
    // ══════════════════════════════════════════════════════════════
    private void drawHUD() {
        ResourceManager rm = eventManager.getResourceManager();

        font.setColor(Color.WHITE);
        font.draw(batch, "Food: " + rm.getAmount("food"), 20f, LayoutConfig.getResourcesY());
        font.draw(batch, "Wood: " + rm.getAmount("wood"), 170f, LayoutConfig.getResourcesY());
        font.draw(batch, "Iron: " + rm.getAmount("iron"), 320f, LayoutConfig.getResourcesY());

        fontSmall.setColor(0.7f, 0.8f, 0.7f, 1f);
        fontSmall.draw(batch, "Nail: " + rm.getAmount("nail"), 20f, LayoutConfig.getResourcesRow2Y());
        fontSmall.draw(batch, "Slate: " + rm.getAmount("slate"), 130f, LayoutConfig.getResourcesRow2Y());
        fontSmall.draw(batch, "Ingot: " + rm.getAmount("ingot"), 240f, LayoutConfig.getResourcesRow2Y());
        fontSmall.draw(batch, "Relic: " + rm.getAmount("relic"), 350f, LayoutConfig.getResourcesRow2Y());
        fontSmall.setColor(Color.WHITE);
    }

    /**
     * Draws monster counter progress bar in the right box.
     * Only visible when a unit is selected, showing its nemesis counter.
     */
    private void drawMonsterCounterBarTextured() {
        if (displayedNemesis == null) return;

        BattleFieldManager bfm = eventManager.getBattleFieldManager();
        Map<String, int[]> counters = bfm.getGlobalCounter();
        int[] counter = counters.get(displayedNemesis);
        if (counter == null || counter.length < 2) return;

        float ratio = (float) counter[0] / Math.max(1, counter[1]);
        float barWidth = LayoutConfig.NEMESIS_BOX_SIZE - 8f;
        float barX = LayoutConfig.getNemesisBoxX() + 4f;
        float barY = LayoutConfig.getNemesisBoxY() + 4f;

        uiTex.drawProgressBar(batch, uiTex.barNemesisBg, uiTex.barNemesisFill,
            barX, barY, barWidth, 8f, ratio);
    }

    /**
     * Draws all text content of the info bar (called inside batch.begin/end).
     */
    private void drawInfoBarContentTextured() {
        float boxY = LayoutConfig.getLevelBoxY();
        float boxSize = LayoutConfig.LEVEL_BOX_SIZE;
        float boxTop = boxY + boxSize;

        BattleFieldManager bfm = eventManager.getBattleFieldManager();

        fontSmall.setColor(0.6f, 0.6f, 0.7f, 1f);
        drawCenteredText(fontSmall, "LEVEL",
            LayoutConfig.LEVEL_BOX_X + boxSize / 2f, boxTop - 14f);

        font.setColor(Color.WHITE);
        drawCenteredText(font, String.valueOf(bfm.getLevel()),
            LayoutConfig.LEVEL_BOX_X + boxSize / 2f, boxTop - 32f);

        fontSmall.setColor(0.5f, 0.5f, 0.6f, 1f);
        drawCenteredText(fontSmall, bfm.getCurrent_xp() + "/" + bfm.getXp_required(),
            LayoutConfig.LEVEL_BOX_X + boxSize / 2f, boxTop - 48f);

        if (inputHandler.hasSelection()) {
            drawSelectedObjectInfo(boxTop);
        }

        // Update nemesis display
        String selectedType = getSelectedType();
        if (selectedType != null) {
            String currentNemesis = getNemesisForType(selectedType);
            if (currentNemesis != null) {
                displayedNemesis = currentNemesis;
            }
        }

        drawMonsterCounterText(boxTop);

        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    /**
     * Draws name, description, and type-specific stats for the selected object.
     */
    private void drawSelectedObjectInfo(float barTop) {
        String selId = inputHandler.getSelectedObjectId();
        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();
        GameObject obj = gom.getObject(selId);
        if (obj == null) return;

        GameDataLoader gdl = eventManager.getGameDataLoader();
        GenData data = gdl.getGameData(obj.getType(), obj.getLvl());

        float textX = LayoutConfig.getInfoTextX() + 4f;
        float maxWidth = LayoutConfig.getInfoTextWidth() - 8f;

        // Line 1: Name
        String name = capitalize(obj.getType()) + " (Lv." + obj.getLvl() + ")";
        font.setColor(Color.WHITE);
        font.draw(batch, name, textX, barTop - 10f);

        // Line 2: Description
        fontSmall.setColor(0.7f, 0.7f, 0.8f, 1f);
        String desc = obj.getDescription();
        if (desc != null) {
            fontSmall.draw(batch, desc, textX, barTop - 28f, maxWidth,
                com.badlogic.gdx.utils.Align.left, false);
        }

        // Line 3: Type-specific stats
        String stats = getStatsString(obj, data);
        if (stats != null) {
            fontSmall.setColor(0.85f, 0.8f, 0.5f, 1f); // gold color for stats
            fontSmall.draw(batch, stats, textX, barTop - 46f, maxWidth,
                com.badlogic.gdx.utils.Align.left, false);
        }

        // Line 4: Extra info (if needed)
        String extra = getExtraString(obj, data);
        if (extra != null) {
            fontSmall.setColor(0.6f, 0.8f, 0.6f, 1f); // green for extra
            fontSmall.draw(batch, extra, textX, barTop - 62f, maxWidth,
                com.badlogic.gdx.utils.Align.left, false);
        }
    }

    // ── Draw Bottom Area  ──
    private void drawBottomBarTextured() {
        float barY = LayoutConfig.getBottomBarY();
        float barH = LayoutConfig.getBottomBarHeight();

        // Bar background
        uiTex.drawPanel(batch, uiTex.panelDark, 0, barY, LayoutConfig.WORLD_WIDTH, barH);

        // Buttons
        String[] labels = {"Build", "Items", "Spells", "Quests"};
        for (int i = 0; i < labels.length; i++) {
            float btnX = LayoutConfig.getButtonX(i);
            float btnBY = LayoutConfig.getBtnY();

            // Use appropriate button style
            if (i <= 1) {
                uiTex.drawPanel(batch, uiTex.btnNormal,
                    btnX, btnBY, LayoutConfig.BTN_WIDTH, LayoutConfig.BTN_HEIGHT);
                fontSmall.setColor(Color.WHITE);
            } else {
                uiTex.drawPanel(batch, uiTex.btnDisabled,
                    btnX, btnBY, LayoutConfig.BTN_WIDTH, LayoutConfig.BTN_HEIGHT);
                fontSmall.setColor(0.5f, 0.5f, 0.5f, 1f);
            }

            float centerX = btnX + LayoutConfig.BTN_WIDTH / 2f;
            glyphLayout.setText(fontSmall, labels[i]);
            fontSmall.draw(batch, labels[i],
                centerX - glyphLayout.width / 2f,
                btnBY + LayoutConfig.BTN_HEIGHT - 12f);
        }
        fontSmall.setColor(Color.WHITE);
    }


    /**
     * Draws merge (blue) and combat (red) outlines on eligible objects
     * based on the currently selected object.
     * Only highlights unlocked objects when the selected object is also unlocked.
     */
    private void drawHighlights() {
        drawHighlightsAtY(gridStartY);
    }

    private void drawHighlightsShifted() {
        drawHighlightsAtY(gridStartYShifted);
    }

    private void drawHighlightsAtY(float baseY) {
        if (!inputHandler.hasSelection()) return;

        String selId = inputHandler.getSelectedObjectId();
        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();
        GameObject selObj = gom.getObject(selId);
        if (selObj == null) return;

        // Don't highlight if selected object is locked
        if (gom.isLocked(selId)) return;

        Grid grid = eventManager.getGridInstance();
        int cols = grid.getWidth();
        int rows = grid.getHeight();

        boolean selIsUnit = isUnitType(selObj.getType());

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                Cell cell = grid.getCell(x, y);
                if (cell.isEmpty()) continue;

                String cellId = cell.getOccupant();
                if (cellId.equals(selId)) continue; // skip self

                // Don't highlight locked objects
                if (gom.isLocked(cellId)) continue;

                GameObject cellObj = gom.getObject(cellId);
                if (cellObj == null) continue;

                float drawX = gridStartX + x * (cellSize + LayoutConfig.CELL_GAP);
                float drawY = baseY + (rows - 1 - y) * (cellSize + LayoutConfig.CELL_GAP);
                float t = 2.5f;

                // Check for merge compatibility
                boolean canMerge = selObj.getType().equals(cellObj.getType())
                    && selObj.getLvl() == cellObj.getLvl()
                    && selObj.getLvl() < selObj.getMaxLVL();

                if (canMerge) {
                    // Dark blue outline for mergeable
                    shapeRenderer.setColor(0.2f, 0.3f, 0.8f, 1f);
                    drawOutlineRect(drawX, drawY, cellSize, t);
                    continue;
                }

                // Check for combat — unit selected, target is a monster
                if (selIsUnit && isMonsterType(cellObj.getType())) {
                    // Bright red outline for fightable
                    shapeRenderer.setColor(0.9f, 0.15f, 0.15f, 1f);
                    drawOutlineRect(drawX, drawY, cellSize, t);
                }
            }
        }

        shapeRenderer.end();
    }

    /**
     * Draws lock indicators (small lock icon/marker) on all locked objects.
     */
    private void drawLockIndicators() {
        drawLockIndicatorsAtY(gridStartY);
    }

    private void drawLockIndicatorsShifted() {
        drawLockIndicatorsAtY(gridStartYShifted);
    }

    private void drawLockIndicatorsAtY(float baseY) {
        Grid grid = eventManager.getGridInstance();
        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();
        int cols = grid.getWidth();
        int rows = grid.getHeight();

        boolean anyLocked = false;
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                Cell cell = grid.getCell(x, y);
                if (cell.isEmpty()) continue;
                if (!gom.isLocked(cell.getOccupant())) continue;

                if (!anyLocked) {
                    shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
                    anyLocked = true;
                }

                float drawX = gridStartX + x * (cellSize + LayoutConfig.CELL_GAP);
                float drawY = baseY + (rows - 1 - y) * (cellSize + LayoutConfig.CELL_GAP);

                // Small lock indicator in top-right corner
                float indicatorSize = cellSize * 0.2f;
                float ix = drawX + cellSize - indicatorSize - 2f;
                float iy = drawY + cellSize - indicatorSize - 2f;

                // Lock background
                shapeRenderer.setColor(0.8f, 0.6f, 0.1f, 0.9f);
                shapeRenderer.rect(ix, iy, indicatorSize, indicatorSize);

                // Lock inner (keyhole look)
                shapeRenderer.setColor(0.3f, 0.2f, 0.05f, 1f);
                float inner = indicatorSize * 0.4f;
                shapeRenderer.rect(ix + (indicatorSize - inner) / 2f,
                    iy + (indicatorSize - inner) / 2f, inner, inner);
            }
        }
        if (anyLocked) {
            shapeRenderer.end();
        }
    }

    /**
     * Draws the lock/unlock button below the info bar when an object is selected.
     */
    /*private void drawLockButton() {
        if (!inputHandler.hasSelection()) return;

        String selId = inputHandler.getSelectedObjectId();
        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();
        boolean isLocked = gom.isLocked(selId);

        float btnX = LayoutConfig.getNemesisBoxX()
            + (LayoutConfig.NEMESIS_BOX_SIZE - LOCK_BTN_SIZE) / 2f;
        float btnY = LayoutConfig.getLevelBoxY() - LOCK_BTN_SIZE - 4f;

        // Button background
        if (isLocked) {
            uiTex.drawPanel(batch, uiTex.btnActive, btnX, btnY, LOCK_BTN_SIZE, LOCK_BTN_SIZE);
        } else {
            uiTex.drawPanel(batch, uiTex.btnNormal, btnX, btnY, LOCK_BTN_SIZE, LOCK_BTN_SIZE);
        }

        // Lock text/icon
        font.setColor(Color.WHITE);
        String lockText = isLocked ? "U" : "L";
        glyphLayout.setText(font, lockText);
        font.draw(batch, lockText,
            btnX + (LOCK_BTN_SIZE - glyphLayout.width) / 2f,
            btnY + LOCK_BTN_SIZE / 2f + glyphLayout.height / 2f);
    }*/
    private void drawLockButton() {
        if (!inputHandler.hasSelection()) return;

        String selId = inputHandler.getSelectedObjectId();
        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();
        boolean isLocked = gom.isLocked(selId);

        float btnX = LayoutConfig.getInfoTextX() + LayoutConfig.getInfoTextWidth() - LOCK_BTN_SIZE - 2f;
        float btnY = LayoutConfig.getLevelBoxY() + (LayoutConfig.LEVEL_BOX_SIZE - LOCK_BTN_SIZE) / 2f;

        if (isLocked) {
            uiTex.drawPanel(batch, uiTex.btnActive, btnX, btnY, LOCK_BTN_SIZE, LOCK_BTN_SIZE);
        } else {
            uiTex.drawPanel(batch, uiTex.btnNormal, btnX, btnY, LOCK_BTN_SIZE, LOCK_BTN_SIZE);
        }

        font.setColor(Color.PINK);
        String lockText = isLocked ? "U" : "L";
        glyphLayout.setText(font, lockText);
        font.draw(batch, lockText,
            btnX + (LOCK_BTN_SIZE - glyphLayout.width) / 2f,
            btnY + LOCK_BTN_SIZE / 2f + glyphLayout.height / 2f);
    }

    private void drawOutlineRect(float x, float y, float size, float thickness) {
        // Top
        shapeRenderer.rect(x - thickness, y + size, size + thickness * 2, thickness);
        // Bottom
        shapeRenderer.rect(x - thickness, y - thickness, size + thickness * 2, thickness);
        // Left
        shapeRenderer.rect(x - thickness, y - thickness, thickness, size + thickness * 2);
        // Right
        shapeRenderer.rect(x + size, y - thickness, thickness, size + thickness * 2);
    }

    private boolean isUnitType(String type) {
        switch (type) {
            case "villager": case "woodsman": case "cook": case "prospector":
            case "mercenary": case "carpenter": case "knight": case "hunter":
            case "archer": case "blacksmith": case "bulwark": case "monk":
            case "paladin": case "griffin": case "wyvern": case "dragon":
            case "phoenix":
                return true;
            default:
                return false;
        }
    }

    private boolean isMonsterType(String type) {
        switch (type) {
            case "gremlin": case "troll": case "orc":
            case "wraith": case "demon":
                return true;
            default:
                return false;
        }
    }


    /**
     * Returns type-specific stats string for the info bar.
     */
    private String getStatsString(GameObject obj, GenData data) {
        if (data == null) return null;

        switch (obj.getType()) {
            // Units
            case "villager": case "woodsman": case "cook": case "prospector":
            case "mercenary": case "carpenter": case "knight": case "hunter":
            case "archer": case "blacksmith": case "bulwark": case "monk":
            case "paladin": case "griffin": case "wyvern": case "dragon":
            case "phoenix": {
                UnitData ud = (UnitData) data;
                Unit u = (Unit) obj;
                StringBuilder sb = new StringBuilder();
                if (u.getMax_hp() > 0) {
                    sb.append("HP: ").append(u.getHp()).append("/").append(u.getMax_hp());
                }
                if (ud.getDamage() > 0) {
                    if (sb.length() > 0) sb.append(" | ");
                    int bonus = eventManager.getInventory().getEquipBonusDamage(
                        inputHandler.getSelectedObjectId());
                    sb.append("DMG: ").append(ud.getDamage());
                    if (bonus > 0) sb.append("+").append(bonus);
                }
                if (ud.getGen_rate() > 0) {
                    if (sb.length() > 0) sb.append(" | ");
                    sb.append("+").append(ud.getGen_rate()).append(" ")
                        .append(ud.getResource()).append("/tick");
                }
                return sb.toString();
            }

            // Facilities
            case "homestead": case "lodge": case "tavernboard":
            case "barracks": case "archeryrange": case "forge":
            case "monastery": case "griffinnest": case "dragonslair": {
                FacilityData fd = (FacilityData) data;

                // Periodic facilities show timer + held count
                if (fd.getTimeCost() > 0) {
                    Facility f = (Facility) obj;
                    StringBuilder sb = new StringBuilder();
                    sb.append("Spawns every ").append(fd.getTimeCost()).append("s");
                    if (f.getHoldCapacity() > 0) {
                        sb.append(" | Held: ").append(f.getHeldCount())
                            .append("/").append(f.getHoldCapacity());
                    }
                    return sb.toString();
                }

                StringBuilder sb = new StringBuilder("Cost: ");
                boolean first = true;
                if (fd.getTapCost1() > 0) {
                    sb.append(fd.getTapCost1()).append(" food");
                    first = false;
                }
                if (fd.getTapCost2() > 0) {
                    if (!first) sb.append(", ");
                    sb.append(fd.getTapCost2()).append(" wood");
                    first = false;
                }
                if (fd.getTapCost3() > 0) {
                    if (!first) sb.append(", ");
                    sb.append(fd.getTapCost3()).append(" iron");
                }
                return sb.toString();
            }

            // Storage
            case "silo": case "timberyard": case "ironvault": {
                StorageData sd = (StorageData) data;
                return "+" + sd.getStorage_size() + " " + sd.getStorage_type() + " capacity";
            }

            // Monsters
            case "gremlin": case "troll": case "orc":
            case "wraith": case "demon": {
                MonsterData md = (MonsterData) data;
                Monster m = (Monster) obj;
                return "HP: " + m.getHp() + "/" + md.getHp()
                    + " | Reward: " + capitalize(md.getReward());
            }

            // Chests
            case "nail_chest": case "slate_chest":
            case "ingot_chest": case "relic_chest": {
                Chest chest = (Chest) obj;
                ChestData cd = (ChestData) data;
                return "Taps left: " + chest.getTap_count()
                    + " | Drops: " + capitalize(cd.getToken_type());
            }

            // Tokens
            case "nail_token": case "slate_token":
            case "ingot_token": case "relic_token": {
                TokenData td = (TokenData) data;
                return "Value: +" + td.getValue() + " " + td.getTokenType();
            }

            // Prince
            case "prince": {
                PrinceData pd = (PrinceData) data;
                return "Gen: +" + pd.getResource_1() + " food, +"
                    + pd.getResource_2() + " wood, +"
                    + pd.getResource_3() + " iron";
            }

            default:
                return null;
        }
    }

    /**
     * Returns extra info line — dismiss XP for units, build costs for facilities.
     */
    private String getExtraString(GameObject obj, GenData data) {
        if (data == null) return null;

        switch (obj.getType()) {
            // Units: show dismiss XP
            case "villager": case "woodsman": case "cook": case "prospector":
            case "mercenary": case "carpenter": case "knight": case "hunter":
            case "archer": case "blacksmith": case "bulwark": case "monk":
            case "paladin": case "griffin": case "wyvern": case "dragon":
            case "phoenix": {
                UnitData ud = (UnitData) data;
                StringBuilder sb = new StringBuilder();
                sb.append("XP: ").append(ud.getXP_Rate());
                sb.append(" | ").append(capitalize(ud.getNemesis()));

                Item equipped = eventManager.getInventory().getEquippedItem(
                    inputHandler.getSelectedObjectId());
                if (equipped != null) {
                    sb.append(" | ").append(equipped.getName());
                } else if (ud.getHp() > 0) {
                    sb.append(" | [No item]");
                }
                return sb.toString();
            }

            // Facilities: show build cost for upgrade reference
            case "homestead": case "lodge": case "tavernboard":
            case "barracks": case "archeryrange": case "forge":
            case "monastery": case "griffinnest": case "dragonslair": {
                FacilityData fd = (FacilityData) data;
                if (fd.getBuildCost1() + fd.getBuildCost2()
                    + fd.getBuildCost3() + fd.getBuildCost4() == 0) {
                    return null;
                }
                StringBuilder sb = new StringBuilder("Built with: ");
                boolean first = true;
                if (fd.getBuildCost1() > 0) {
                    sb.append(fd.getBuildCost1()).append(" food");
                    first = false;
                }
                if (fd.getBuildCost2() > 0) {
                    if (!first) sb.append(", ");
                    sb.append(fd.getBuildCost2()).append(" wood");
                    first = false;
                }
                if (fd.getBuildCost3() > 0) {
                    if (!first) sb.append(", ");
                    sb.append(fd.getBuildCost3()).append(" iron");
                }
                return sb.toString();
            }

            default:
                return null;
        }
    }

    /**
     * Draws monster name and counter text in the right box.
     */
    private void drawMonsterCounterText(float barTop) {
        if (displayedNemesis == null) return;

        BattleFieldManager bfm = eventManager.getBattleFieldManager();
        Map<String, int[]> counters = bfm.getGlobalCounter();
        int[] counter = counters.get(displayedNemesis);
        if (counter == null || counter.length < 2) return;

        float boxCenterX = LayoutConfig.getNemesisBoxX() + LayoutConfig.NEMESIS_BOX_SIZE / 2f;

        fontSmall.setColor(0.8f, 0.4f, 0.4f, 1f);
        drawCenteredText(fontSmall, capitalize(displayedNemesis), boxCenterX, barTop - 14f);

        fontSmall.setColor(0.7f, 0.7f, 0.7f, 1f);
        drawCenteredText(fontSmall, counter[0] + "/" + counter[1], boxCenterX, barTop - 32f);

        if (eventManager.getGRID_OBJECT_MANAGER().hasObjectOfType(displayedNemesis)) {
            fontSmall.setColor(0.8f, 0.5f, 0.2f, 1f);
            drawCenteredText(fontSmall, "ON BOARD", boxCenterX, barTop - 48f);
        } else {
            fontSmall.setColor(0.5f, 0.5f, 0.5f, 1f);
            drawCenteredText(fontSmall, "Dormant", boxCenterX, barTop - 48f);
        }
    }

    private float getCurrentGridStartY() {
        return buildMenuOpen ? gridStartYShifted : gridStartY;
    }

    private void drawGridBackgroundShifted() {
        Grid grid = eventManager.getGridInstance();
        int cols = grid.getWidth();
        int rows = grid.getHeight();
        float currentY = gridStartYShifted;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                float drawX = gridStartX + x * (cellSize + LayoutConfig.CELL_GAP);
                float drawY = currentY + (rows - 1 - y) * (cellSize + LayoutConfig.CELL_GAP);
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

    private void drawSelectionOutlineShifted() {
        if (!inputHandler.hasSelection()) return;

        int selX = inputHandler.getSelectedCellX();
        int selY = inputHandler.getSelectedCellY();
        Grid grid = eventManager.getGridInstance();
        int rows = grid.getHeight();
        float currentY = gridStartYShifted;

        float drawX = gridStartX + selX * (cellSize + LayoutConfig.CELL_GAP);
        float drawY = currentY + (rows - 1 - selY) * (cellSize + LayoutConfig.CELL_GAP);
        float t = 3f;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.2f, 0.85f, 0.2f, 1f);
        shapeRenderer.rect(drawX - t, drawY + cellSize, cellSize + t * 2, t);
        shapeRenderer.rect(drawX - t, drawY - t, cellSize + t * 2, t);
        shapeRenderer.rect(drawX - t, drawY - t, t, cellSize + t * 2);
        shapeRenderer.rect(drawX + cellSize, drawY - t, t, cellSize + t * 2);
        shapeRenderer.end();
    }

    private void drawGridShifted() {
        Grid grid = eventManager.getGridInstance();
        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();
        int cols = grid.getWidth();
        int rows = grid.getHeight();
        float currentY = gridStartYShifted;

        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                Cell cell = grid.getCell(x, y);
                if (cell.isEmpty()) continue;

                if (inputHandler.isDragging()
                    && x == inputHandler.getOriginCellX()
                    && y == inputHandler.getOriginCellY()) {
                    continue;
                }

                float drawX = gridStartX + x * (cellSize + LayoutConfig.CELL_GAP);
                float drawY = currentY + (rows - 1 - y) * (cellSize + LayoutConfig.CELL_GAP);

                String objectId = cell.getOccupant();
                GameObject obj = gom.getObject(objectId);
                Texture tex = (obj != null)
                    ? spriteManager.getTextureForObject(obj.getType(), obj.getLvl())
                    : spriteManager.getDefaultTile();

                float margin = cellSize * 0.05f;
                batch.draw(tex, drawX + margin, drawY + margin,
                    cellSize - margin * 2, cellSize - margin * 2);
                if (obj instanceof Unit) {
                    Unit unit = (Unit) obj;
                    if (unit.isWounded()) {
                        float barWidth = cellSize - margin * 4;
                        float barHeight = 3f;
                        float barX = drawX + margin * 2;
                        float barY = drawY + margin;
                        float hpRatio = (float) unit.getHp() / unit.getMax_hp();

                        // Background (dark red)
                        batch.setColor(0.4f, 0.1f, 0.1f, 0.8f);
                        // Use a 1x1 white pixel texture or uiTex for the bar
                        batch.draw(whiteTex, barX, barY, barWidth, barHeight);

                        // Fill (green to red based on HP)
                        float r = 1f - hpRatio;
                        float g = hpRatio;
                        batch.setColor(r, g, 0.1f, 0.9f);
                        batch.draw(whiteTex, barX, barY, barWidth * hpRatio, barHeight);

                        batch.setColor(1f, 1f, 1f, 1f); // reset
                    }
                }
            }
        }
    }

    private void drawInventoryTints() {
        Grid grid = eventManager.getGridInstance();
        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();
        int cols = grid.getWidth();
        int rows = grid.getHeight();

        batch.begin();
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                Cell cell = grid.getCell(x, y);
                if (cell.isEmpty()) continue;

                String objectId = cell.getOccupant();
                Color tint = inventoryMenu.getUnitTintColor(objectId);
                if (tint == null) continue;

                float drawX = gridStartX + x * (cellSize + LayoutConfig.CELL_GAP);
                float drawY = gridStartY + (rows - 1 - y) * (cellSize + LayoutConfig.CELL_GAP);

                batch.setColor(tint);
                batch.draw(whiteTex, drawX, drawY, cellSize, cellSize);
            }
        }
        batch.setColor(1f, 1f, 1f, 1f);
        batch.end();
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns the nemesis type for a given unit type, or null for non-units.
     */
    private String getNemesisForType(String type) {
        if (type == null) return null;
        GameDataLoader gdl = eventManager.getGameDataLoader();
        GenData data = gdl.getGameData(type, 1);
        if (data instanceof UnitData) {
            return ((UnitData) data).getNemesis();
        }
        return null;
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private void drawCenteredText(BitmapFont f, String text, float centerX, float y) {
        glyphLayout.setText(f, text);
        f.draw(batch, text, centerX - glyphLayout.width / 2f, y);
    }

    private void drawExploreTints() {
        Grid grid = eventManager.getGridInstance();
        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();
        int cols = grid.getWidth();
        int rows = grid.getHeight();

        batch.begin();
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                Cell cell = grid.getCell(x, y);
                if (cell.isEmpty()) continue;

                String objectId = cell.getOccupant();
                Color tint = explorePanel.getUnitTintColor(objectId);
                if (tint == null) continue;

                float drawX = gridStartX + x * (cellSize + LayoutConfig.CELL_GAP);
                float drawY = gridStartY + (rows - 1 - y) * (cellSize + LayoutConfig.CELL_GAP);

                batch.setColor(tint);
                batch.draw(whiteTex, drawX, drawY, cellSize, cellSize);
            }
        }
        batch.setColor(1f, 1f, 1f, 1f);
        batch.end();
    }

    private String getSelectedType() {
        if (!inputHandler.hasSelection()) return null;
        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();
        GameObject obj = gom.getObject(inputHandler.getSelectedObjectId());
        return obj != null ? obj.getType() : null;
    }

    @Override
    public void onGridExpanded() {
        Gdx.app.log(TAG, "Grid expanded — recalculating layout");
        calculateGridLayout();
        wallGate.updateLayout();

        // Update input handler with new layout
        Grid grid = eventManager.getGridInstance();
        inputHandler.setGridLayout(gridStartX, gridStartY, cellSize, LayoutConfig.CELL_GAP,
            grid.getWidth(), grid.getHeight());
    }

    // ══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ══════════════════════════════════════════════════════════════

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        LayoutConfig.setActualHeight(viewport.getWorldHeight());
        wallGate.updateLayout();
        calculateGridLayout();

        Grid grid = eventManager.getGridInstance();
        inputHandler.setGridLayout(gridStartX, gridStartY, cellSize, LayoutConfig.CELL_GAP,
            grid.getWidth(), grid.getHeight());

        // Recalculate lock button bounds with correct actualHeight
        float lockBtnX = LayoutConfig.getInfoTextX() + LayoutConfig.getInfoTextWidth() - LOCK_BTN_SIZE - 2f;
        float lockBtnY = LayoutConfig.getLevelBoxY() + (LayoutConfig.LEVEL_BOX_SIZE - LOCK_BTN_SIZE) / 2f;
        inputHandler.setLockButtonBounds(lockBtnX, lockBtnY, LOCK_BTN_SIZE, LOCK_BTN_SIZE);

        // Also recalculate bottom bar button bounds
        inputHandler.setBuildButtonBounds(
            LayoutConfig.getButtonX(0), LayoutConfig.getBtnY(),
            LayoutConfig.BTN_WIDTH, LayoutConfig.BTN_HEIGHT);
    }

    @Override
    public void pause() {
        Gdx.app.log(TAG, "Paused — saving...");
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
        fontSmall.dispose();
        spriteManager.dispose();
        uiTex.dispose();
        whiteTex.dispose();
    }

    private void saveGame() {
        try {
            Grid grid = eventManager.getGridInstance();
            BattleFieldManager bfm = eventManager.getBattleFieldManager();

            // Grid state
            jsonManager.saveArrayList("grid_array", grid.getArr());
            jsonManager.saveArray("grid_size", grid.getXoY());

            // Objects on grid
            jsonManager.saveGridObjects("object_map",
                eventManager.getGRID_OBJECT_MANAGER().getObjectMap());

            // Resources
            jsonManager.saveResources("consumable_map",
                eventManager.getResourceManager().getConsumableMap());

            // Progression (level, current XP, XP required)
            jsonManager.saveArray("progression", new int[]{
                bfm.getLevel(),
                bfm.getCurrent_xp(),
                bfm.getXp_required()
            });

            // Monster spawn counters
            jsonManager.saveCounterMap("counter_map", bfm.getGlobalCounter());

            // Locked status
            jsonManager.saveStatus("status", bfm.getLockedStatus());

            // Reward queue
            jsonManager.saveArray("reward_queue", bfm.getRewardQueue());

            // Locked objects
            jsonManager.saveLockedObjects("locked_objects",
                eventManager.getGRID_OBJECT_MANAGER().getLockedObjects());

            jsonManager.saveInventoryItems("inventory_items",
                eventManager.getInventory().getItems());
            jsonManager.saveEquippedMap("inventory_equipped",
                eventManager.getInventory().getEquipped());

            jsonManager.saveExplorationSlots("exploration_slots",
                eventManager.getExplorationManager().getActiveSlots());

            Gdx.app.log(TAG, "Game saved successfully");
        } catch (Exception e) {
            Gdx.app.error(TAG, "Failed to save game", e);
        }
    }
}
