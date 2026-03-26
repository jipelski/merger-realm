package com.jipelski.mergerrealm;

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
import com.jipelski.mergerrealm.model.Monster;
import com.jipelski.mergerrealm.model.Unit;
import com.jipelski.mergerrealm.util.BattleFieldManager;
import com.jipelski.mergerrealm.util.EventManager;
import com.jipelski.mergerrealm.util.GameDataLoader;
import com.jipelski.mergerrealm.util.GameEventListener;
import com.jipelski.mergerrealm.util.GridObjectManager;
import com.jipelski.mergerrealm.util.ResourceManager;
import com.jipelski.mergerrealm.util.SpriteManager;

import com.jipelski.mergerrealm.ui.BuildMenu;
import com.jipelski.mergerrealm.ui.LayoutConfig;

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

    // ── Offline tracking ──
    private static final String TIMESTAMP_KEY = "last_active_timestamp";
    private static final long MAX_OFFLINE_SECONDS = 8 * 60 * 60;

    private String offlineMessage = null;
    private float offlineMessageTimer = 0f;
    private static final float OFFLINE_MESSAGE_DURATION = 5f;

    // ── Animation ──
    private float animationTime = 0f;

    // ── Menus ──
    private BuildMenu buildMenu;
    private boolean buildMenuOpen = false;

    private float gridStartYShifted;

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

        spriteManager = new SpriteManager();
        spriteManager.loadFallback();

        jsonManager = new JsonManager();
        eventManager = new EventManager(jsonManager);

        eventManager.getBattleFieldManager().setGameEventListener(this);

        calculateGridLayout();

        inputHandler = new GridInputHandler(eventManager, viewport);
        Grid grid = eventManager.getGridInstance();
        inputHandler.setGridLayout(gridStartX, gridStartY, cellSize, LayoutConfig.CELL_GAP,
            grid.getWidth(), grid.getHeight());
        Gdx.input.setInputProcessor(inputHandler);


        buildMenu = new BuildMenu(eventManager, spriteManager, viewport, LayoutConfig.WORLD_WIDTH , LayoutConfig.WORLD_HEIGHT );
        inputHandler.setBuildMenu(buildMenu);

        gridStartYShifted = BuildMenu.MENU_HEIGHT + LayoutConfig.GRID_PADDING ;

        inputHandler.setBuildMenu(buildMenu);
        inputHandler.setBuildButtonBounds(
            LayoutConfig.getButtonX(0), LayoutConfig.getBtnY(),
            LayoutConfig.BTN_WIDTH, LayoutConfig.BTN_HEIGHT);

        LayoutConfig.setActualHeight(viewport.getWorldHeight());
        calculateGridLayout();
        processOfflineProgress();

        Gdx.app.log(TAG, "=== Init complete ===");
    }

    // ══════════════════════════════════════════════════════════════
    // OFFLINE
    // ══════════════════════════════════════════════════════════════

    private void processOfflineProgress() {
        int[] savedTimestamp = jsonManager.loadArray(TIMESTAMP_KEY);
        if (savedTimestamp == null || savedTimestamp.length < 2) return;

        long savedTime = ((long) savedTimestamp[0] << 32) | (savedTimestamp[1] & 0xFFFFFFFFL);
        long now = System.currentTimeMillis();
        long elapsedMs = now - savedTime;
        if (elapsedMs <= 0) return;

        long cappedSeconds = elapsedMs / 1000;
        if (cappedSeconds < 15) return;

        long ticks = cappedSeconds / 15;

        // Snapshot before
        ResourceManager rm = eventManager.getResourceManager();
        int timberBefore = rm.getAmount("timber");
        int stoneBefore = rm.getAmount("quarrystone");
        int ironBefore = rm.getAmount("iron");

        for (long i = 0; i < ticks; i++) {
            rm.updateResources();
        }

        // Calculate earnings
        int timberGained = rm.getAmount("timber") - timberBefore;
        int stoneGained = rm.getAmount("quarrystone") - stoneBefore;
        int ironGained = rm.getAmount("iron") - ironBefore;

        // Build message
        long hours = cappedSeconds / 3600;
        long minutes = (cappedSeconds % 3600) / 60;
        StringBuilder sb = new StringBuilder("Welcome back! (");
        if (hours > 0) sb.append(hours).append("h ");
        sb.append(minutes).append("m) ");

        boolean anyGained = false;
        if (timberGained > 0) {
            sb.append("+").append(timberGained).append(" timber ");
            anyGained = true;
        }
        if (stoneGained > 0) {
            sb.append("+").append(stoneGained).append(" stone ");
            anyGained = true;
        }
        if (ironGained > 0) {
            sb.append("+").append(ironGained).append(" iron");
            anyGained = true;
        }
        if (!anyGained) {
            sb.append("No resources generated");
        }

        offlineMessage = sb.toString();
        offlineMessageTimer = OFFLINE_MESSAGE_DURATION;

        saveDirty = true;
        Gdx.app.log(TAG, offlineMessage);
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

        inputHandler.update(delta);
        animationTime += delta;

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

        buildMenuOpen = buildMenu.isVisible();

        ScreenUtils.clear(0.12f, 0.12f, 0.18f, 1f);
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        shapeRenderer.setProjectionMatrix(camera.combined);

        if (buildMenuOpen) {
            // Build menu mode — grid shifts up, menu at bottom
            drawGridBackgroundShifted();
            drawSelectionOutlineShifted();
            buildMenu.drawBackground(shapeRenderer);

            batch.begin();
            drawGridShifted();
            drawDraggedObject();
            buildMenu.drawContent(batch, font, fontSmall);
            batch.end();
        } else {
            // Normal mode — all zones visible
            // Shape pass
            drawInfoBarBackground();
            drawWallBackground();
            drawGridBackground();
            drawSelectionOutline();
            drawBottomBarBackground();

            // Text/sprite pass
            batch.begin();
            drawHUD();
            drawInfoBarContent();
            drawWallContent();
            drawGrid();
            drawDraggedObject();
            drawBottomBarContent();
            drawOfflineMessage(delta);
            batch.end();
        }
    }

    private void gameTick() {
        resourceTimer += TICK_INTERVAL;
        if (resourceTimer >= RESOURCE_INTERVAL) {
            resourceTimer -= RESOURCE_INTERVAL;
            ResourceManager rm = eventManager.getResourceManager();
            rm.updateResources();
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
        font.draw(batch, "Timber: " + rm.getAmount("timber"), 20f, LayoutConfig.getResourcesY());
        font.draw(batch, "Stone: " + rm.getAmount("quarrystone"), 170f, LayoutConfig.getResourcesY());
        font.draw(batch, "Iron: " + rm.getAmount("iron"), 320f, LayoutConfig.getResourcesY());

        fontSmall.setColor(0.7f, 0.8f, 0.7f, 1f);
        fontSmall.draw(batch, "Wood: " + rm.getAmount("wood"), 20f, LayoutConfig.getResourcesRow2Y());
        fontSmall.draw(batch, "Wheat: " + rm.getAmount("wheat"), 130f, LayoutConfig.getResourcesRow2Y());
        fontSmall.draw(batch, "Stone: " + rm.getAmount("stone"), 240f, LayoutConfig.getResourcesRow2Y());
        fontSmall.draw(batch, "Fire: " + rm.getAmount("fire"), 350f, LayoutConfig.getResourcesRow2Y());
        fontSmall.setColor(Color.WHITE);
    }

    // ══════════════════════════════════════════════════════════════
    // INFO BAR (between HUD and grid)
    // ══════════════════════════════════════════════════════════════
    private void drawInfoBarBackground() {
        float boxY = LayoutConfig.getLevelBoxY();
        float boxSize = LayoutConfig.LEVEL_BOX_SIZE;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Left box (Level/XP)
        shapeRenderer.setColor(0.18f, 0.18f, 0.26f, 1f);
        shapeRenderer.rect(LayoutConfig.LEVEL_BOX_X, boxY, boxSize, boxSize);

        // Center area (description) — spans between the two boxes
        shapeRenderer.setColor(0.16f, 0.16f, 0.22f, 1f);
        shapeRenderer.rect(LayoutConfig.getInfoTextX(), boxY,
            LayoutConfig.getInfoTextWidth(), boxSize);

        // Right box (nemesis counter)
        shapeRenderer.setColor(0.18f, 0.18f, 0.26f, 1f);
        shapeRenderer.rect(LayoutConfig.getNemesisBoxX(), boxY, boxSize, boxSize);

        // XP progress bar in left box
        BattleFieldManager bfm = eventManager.getBattleFieldManager();
        float xpRatio = (float) bfm.getCurrent_xp() / Math.max(1, bfm.getXp_required());
        float barWidth = boxSize - 8f;
        float barHeight = 8f;
        float barX = LayoutConfig.LEVEL_BOX_X + 4f;
        float barY = boxY + 4f;

        shapeRenderer.setColor(0.1f, 0.1f, 0.15f, 1f);
        shapeRenderer.rect(barX, barY, barWidth, barHeight);
        shapeRenderer.setColor(0.3f, 0.7f, 0.3f, 1f);
        shapeRenderer.rect(barX, barY, barWidth * xpRatio, barHeight);

        // Monster counter bar in right box
        drawMonsterCounterBar(boxY);

        shapeRenderer.end();
    }

    /**
     * Draws monster counter progress bar in the right box.
     * Only visible when a unit is selected, showing its nemesis counter.
     */
    private void drawMonsterCounterBar(float boxY) {
        if (displayedNemesis == null) return;

        BattleFieldManager bfm = eventManager.getBattleFieldManager();
        Map<String, int[]> counters = bfm.getGlobalCounter();
        int[] counter = counters.get(displayedNemesis);
        if (counter == null || counter.length < 2) return;

        float ratio = (float) counter[0] / Math.max(1, counter[1]);
        float barWidth = LayoutConfig.NEMESIS_BOX_SIZE - 8f;
        float barHeight = 8f;
        float barX = LayoutConfig.getNemesisBoxX() + 4f;
        float barY = boxY + 4f;

        shapeRenderer.setColor(0.1f, 0.1f, 0.15f, 1f);
        shapeRenderer.rect(barX, barY, barWidth, barHeight);
        shapeRenderer.setColor(0.8f, 0.25f, 0.25f, 1f);
        shapeRenderer.rect(barX, barY, barWidth * ratio, barHeight);
    }

    /**
     * Draws all text content of the info bar (called inside batch.begin/end).
     */
    private void drawInfoBarContent() {
        float boxY = LayoutConfig.getLevelBoxY();
        float boxSize = LayoutConfig.LEVEL_BOX_SIZE;
        float boxTop = boxY + boxSize;

        // ── Left box: Level and XP ──
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

        // ── Center: selected object info ──
        if (inputHandler.hasSelection()) {
            drawSelectedObjectInfo(boxTop);
        }

        // ── Right box: monster counter ──
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
    private void drawBottomBarBackground() {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // Bar background
        shapeRenderer.setColor(0.12f, 0.12f, 0.18f, 1f);
        shapeRenderer.rect(0, LayoutConfig.getBottomBarY(),
            LayoutConfig.WORLD_WIDTH , LayoutConfig.getBottomBarHeight());

        // Top border
        shapeRenderer.setColor(0.25f, 0.25f, 0.35f, 1f);
        shapeRenderer.rect(0, LayoutConfig.getBottomBarHeight() - 2f, LayoutConfig.WORLD_WIDTH , 2f);

        // Buttons
        String[] labels = {"Build", "Shop", "Spells", "Quests"};
        for (int i = 0; i < labels.length; i++) {
            float btnX = LayoutConfig.getButtonX(i);
            if (i == 0) {
                // Build button — green
                shapeRenderer.setColor(0.2f, 0.5f, 0.2f, 1f);
            } else {
                // Future buttons — greyed out
                shapeRenderer.setColor(0.25f, 0.25f, 0.3f, 1f);
            }
            shapeRenderer.rect(btnX, LayoutConfig.getBtnY(),
                LayoutConfig.BTN_WIDTH, LayoutConfig.BTN_HEIGHT);
        }

        shapeRenderer.end();
    }

    private void drawBottomBarContent() {
        String[] labels = {"Build", "Shop", "Spells", "Quests"};
        for (int i = 0; i < labels.length; i++) {
            float btnX = LayoutConfig.getButtonX(i);
            float centerX = btnX + LayoutConfig.BTN_WIDTH / 2f;

            if (i == 0) {
                fontSmall.setColor(Color.WHITE);
            } else {
                fontSmall.setColor(0.5f, 0.5f, 0.5f, 1f);
            }

            glyphLayout.setText(fontSmall, labels[i]);
            fontSmall.draw(batch, labels[i],
                centerX - glyphLayout.width / 2f,
                LayoutConfig.getBtnY() + LayoutConfig.BTN_HEIGHT - 12f);
        }
        fontSmall.setColor(Color.WHITE);
    }


    /**
     * Returns type-specific stats string for the info bar.
     */
    private String getStatsString(GameObject obj, GenData data) {
        if (data == null) return null;

        switch (obj.getType()) {
            // Units
            case "archer": case "farmer": case "spearman":
            case "griffin": case "eldergriffin": case "monk": case "swordsman": {
                UnitData ud = (UnitData) data;
                StringBuilder sb = new StringBuilder();
                if (ud.getDamage() > 0)   sb.append("DMG: ").append(ud.getDamage());
                if (ud.getGen_rate() > 0) {
                    if (sb.length() > 0) sb.append(" | ");
                    sb.append("+").append(ud.getGen_rate()).append(" ")
                        .append(ud.getResource()).append("/s");
                }
                return sb.toString();
            }

            // Facilities
            case "archeryrange": case "farmhouse": case "barracks":
            case "griffinnest": case "monastery": {
                FacilityData fd = (FacilityData) data;
                StringBuilder sb = new StringBuilder("Cost: ");
                boolean first = true;
                if (fd.getTapCost1() > 0) {
                    sb.append(fd.getTapCost1()).append(" timber");
                    first = false;
                }
                if (fd.getTapCost2() > 0) {
                    if (!first) sb.append(", ");
                    sb.append(fd.getTapCost2()).append(" stone");
                    first = false;
                }
                if (fd.getTapCost3() > 0) {
                    if (!first) sb.append(", ");
                    sb.append(fd.getTapCost3()).append(" iron");
                }
                return sb.toString();
            }

            // Storage
            case "sawmill": case "quarry": case "ironmine": {
                StorageData sd = (StorageData) data;
                return "+" + sd.getStorage_size() + " " + sd.getStorage_type() + " capacity";
            }

            // Monsters
            case "imp": case "scarecrow": case "gargoyle": case "efreet": {
                MonsterData md = (MonsterData) data;
                Monster m = (Monster) obj;
                return "HP: " + m.getHp() + "/" + md.getHp()
                    + " | Reward: " + capitalize(md.getReward());
            }

            // Chests
            case "woodchest": case "wheatchest": case "stonechest": case "firechest": {
                Chest chest = (Chest) obj;
                ChestData cd = (ChestData) data;
                return "Taps left: " + chest.getTap_count()
                    + " | Drops: " + capitalize(cd.getToken_type());
            }

            // Tokens
            case "wood_token": case "wheat_token": case "stone_token": case "fire_token": {
                TokenData td = (TokenData) data;
                return "Value: +" + td.getValue() + " " + td.getTokenType();
            }

            // Prince
            case "prince": {
                PrinceData pd = (PrinceData) data;
                return "Gen: +" + pd.getResource_1() + " timber, +"
                    + pd.getResource_2() + " stone, +"
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
            case "archer": case "farmer": case "spearman":
            case "griffin": case "eldergriffin": case "monk": case "swordsman": {
                UnitData ud = (UnitData) data;
                return "Dismiss XP: " + ud.getXP_Rate()
                    + " | Provokes: " + capitalize(ud.getNemesis());
            }

            // Facilities: show build cost for upgrade reference
            case "archeryrange": case "farmhouse": case "barracks":
            case "griffinnest": case "monastery": {
                FacilityData fd = (FacilityData) data;
                if (fd.getBuildCost1() + fd.getBuildCost2()
                    + fd.getBuildCost3() + fd.getBuildCost4() == 0) {
                    return null;
                }
                StringBuilder sb = new StringBuilder("Built with: ");
                boolean first = true;
                if (fd.getBuildCost1() > 0) {
                    sb.append(fd.getBuildCost1()).append(" timber");
                    first = false;
                }
                if (fd.getBuildCost2() > 0) {
                    if (!first) sb.append(", ");
                    sb.append(fd.getBuildCost2()).append(" stone");
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

    // ═══════════════════════════
    // New method for the wall area:
    // ═══════════════════════════

    private void drawWallBackground() {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.14f, 0.14f, 0.2f, 1f);
        shapeRenderer.rect(0, LayoutConfig.getWallY(), LayoutConfig.WORLD_WIDTH , LayoutConfig.getWallHeight());

        // Border lines
        shapeRenderer.setColor(0.25f, 0.25f, 0.35f, 1f);
        shapeRenderer.rect(0, LayoutConfig.getWallY(), LayoutConfig.WORLD_WIDTH , 2f);
        shapeRenderer.rect(0, LayoutConfig.getWallY() + LayoutConfig.getWallHeight() - 2f,
            LayoutConfig.WORLD_WIDTH , 2f);
        shapeRenderer.end();
    }

    private void drawWallContent() {
        // Placeholder text for future features
        fontSmall.setColor(0.4f, 0.4f, 0.5f, 1f);
        float centerY = LayoutConfig.getWallY() + LayoutConfig.getWallHeight() / 2f + 5f;
        drawCenteredText(fontSmall, "— The Wall —", LayoutConfig.WORLD_WIDTH  / 2f, centerY);
        drawCenteredText(fontSmall, "Exploration & Raids coming soon",
            LayoutConfig.WORLD_WIDTH  / 2f, centerY - 18f);
        fontSmall.setColor(Color.WHITE);
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
            }
        }
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

    private void drawOfflineMessage(float delta) {
        if (offlineMessage == null) return;

        offlineMessageTimer -= delta;
        if (offlineMessageTimer <= 0) {
            offlineMessage = null;
            return;
        }

        // Fade out in the last second
        float alpha = Math.min(1f, offlineMessageTimer);

        fontSmall.setColor(0.9f, 0.85f, 0.4f, alpha);
        glyphLayout.setText(fontSmall, offlineMessage);
        float msgX = (LayoutConfig.WORLD_WIDTH  - glyphLayout.width) / 2f;
        float msgY = gridStartY - 10f;
        fontSmall.draw(batch, offlineMessage, msgX, msgY);
        fontSmall.setColor(Color.WHITE);
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
        calculateGridLayout();

        Grid grid = eventManager.getGridInstance();
        inputHandler.setGridLayout(gridStartX, gridStartY, cellSize, LayoutConfig.CELL_GAP,
            grid.getWidth(), grid.getHeight());
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

            Gdx.app.log(TAG, "Game saved successfully");
        } catch (Exception e) {
            Gdx.app.error(TAG, "Failed to save game", e);
        }
    }
}
