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
import com.badlogic.gdx.utils.TimeUtils;
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
import com.jipelski.mergerrealm.data.ResourcePouchData;
import com.jipelski.mergerrealm.grid.Cell;
import com.jipelski.mergerrealm.grid.Grid;
import com.jipelski.mergerrealm.input.GridInputHandler;
import com.jipelski.mergerrealm.model.Chest;
import com.jipelski.mergerrealm.model.GameObject;
import com.jipelski.mergerrealm.model.Item;
import com.jipelski.mergerrealm.model.Monster;
import com.jipelski.mergerrealm.model.Facility;
import com.jipelski.mergerrealm.model.RaidState;
import com.jipelski.mergerrealm.model.Unit;
import com.jipelski.mergerrealm.ui.ExplorePanel;
import com.jipelski.mergerrealm.ui.RaidPanel;
import com.jipelski.mergerrealm.ui.UnifiedShopPanel;
import com.jipelski.mergerrealm.ui.DailyLoginPopup;
import com.jipelski.mergerrealm.ui.HiddenTemplePopup;
import com.jipelski.mergerrealm.ui.SalvagePanel;
import com.jipelski.mergerrealm.ui.RefinePanel;
import com.jipelski.mergerrealm.ui.WallGate;
import com.jipelski.mergerrealm.util.BattleFieldManager;
import com.jipelski.mergerrealm.util.EventManager;
import com.jipelski.mergerrealm.util.GameDataLoader;
import com.jipelski.mergerrealm.util.GameEventListener;
import com.jipelski.mergerrealm.util.GameTypes;
import com.jipelski.mergerrealm.util.GoldManager;
import com.jipelski.mergerrealm.util.DailyLoginManager;
import com.jipelski.mergerrealm.util.GridObjectManager;
import com.jipelski.mergerrealm.util.PrestigeManager;
import com.jipelski.mergerrealm.util.OutfitManager;
import com.jipelski.mergerrealm.util.RaidManager;
import com.jipelski.mergerrealm.util.ResourceManager;
import com.jipelski.mergerrealm.util.RuneSystem;
import com.jipelski.mergerrealm.util.ServerTimeManager;
import com.jipelski.mergerrealm.util.SoundManager;
import com.jipelski.mergerrealm.util.SpriteManager;
import com.jipelski.mergerrealm.util.TextUtil;

import com.jipelski.mergerrealm.ui.BuildMenu;
import com.jipelski.mergerrealm.ui.InventoryMenu;
import com.jipelski.mergerrealm.ui.LayoutConfig;
import com.jipelski.mergerrealm.ui.UITextureManager;
import com.jipelski.mergerrealm.ui.OfflinePopup;
import com.jipelski.mergerrealm.ui.TutorialOverlay;
import com.jipelski.mergerrealm.ui.PrestigePanel;
import com.jipelski.mergerrealm.ui.OutfitPanel;
import com.jipelski.mergerrealm.ui.SettingsPanel;
import com.jipelski.mergerrealm.ui.InfoPanel;
import com.jipelski.mergerrealm.ui.AdRewardPopup;
import com.jipelski.mergerrealm.ui.IconText;
import com.jipelski.mergerrealm.util.TutorialManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
    private SoundManager soundManager;
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
    private static final float INFO_BTN_SIZE = 16f;
    private static final float SETTINGS_BTN_SIZE = 28f;

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

    private float goldChipX, goldChipY, goldChipW, goldChipH;
    private UnifiedShopPanel unifiedShopPanel;
    private DailyLoginPopup dailyLoginPopup;
    private HiddenTemplePopup hiddenTemplePopup;
    private SalvagePanel salvagePanel;
    private RefinePanel refinePanel;
    private PrestigePanel prestigePanel;
    private OutfitPanel outfitPanel;
    private SettingsPanel settingsPanel;
    private InfoPanel infoPanel;
    private AdRewardPopup adRewardPopup;
    private RaidPanel raidPanel;

    /**
     * Holds a "prefix + value" display string, rebuilt only when value
     * actually changes. drawHUD() runs every frame but resources tick every
     * 15s, so this turns ~8 throwaway string concats/frame into effectively
     * zero on the overwhelming majority of frames.
     */
    private static final class CachedLabel {
        private int lastValue = Integer.MIN_VALUE;
        private String text = "";
        String get(String prefix, int value) {
            if (value != lastValue) {
                lastValue = value;
                text = prefix + value;
            }
            return text;
        }
    }
    private final CachedLabel foodLabel = new CachedLabel();
    private final CachedLabel woodLabel = new CachedLabel();
    private final CachedLabel ironLabel = new CachedLabel();
    private final CachedLabel nailLabel = new CachedLabel();
    private final CachedLabel slateLabel = new CachedLabel();
    private final CachedLabel ingotLabel = new CachedLabel();
    private final CachedLabel relicLabel = new CachedLabel();
    private final CachedLabel goldLabel = new CachedLabel();

    // ── Selected-object stat-line cache (drawSelectedObjectInfo) ──
    // getStatsString/getExtraString build a StringBuilder per call, and the
    // fields they read (unit HP, facility held-count, monster HP, chest
    // tap-count...) vary per object type — enumerating every one for a
    // change-detection signature would be exactly the "meaningful complexity
    // that risks a stale-display bug" this cleanup pass is told to avoid.
    // Instead: rebuild immediately on selection change, otherwise at most
    // 4x/second — caps allocation to ~4/s instead of ~60/s while something is
    // selected, with a worst-case 250ms display staleness that's
    // imperceptible (this is a single object, not per-cell/per-row).
    private static final float STATS_REFRESH_INTERVAL = 0.25f;
    private String cachedStatsSelId = null;
    private String cachedStatsLine = null;
    private String cachedExtraLine = null;
    private float statsRefreshTimer = 0f;

    // ── Tutorial ──
    private TutorialManager tutorialManager;
    private TutorialOverlay tutorialOverlay;
    // Rising-edge trackers for one-shot "first time this panel opened" tips —
    // same pattern already used for buildMenuOpen just above.
    private boolean wasExploreVisible = false;
    private boolean wasRaidVisible = false;
    private boolean wasInventoryVisible = false;

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

        soundManager = new SoundManager(jsonManager);
        soundManager.load();

        wallGate = new WallGate(eventManager, spriteManager, uiTex);
        wallGate.setSoundManager(soundManager);
        wallGate.updateLayout();

        calculateGridLayout();

        inputHandler = new GridInputHandler(eventManager, viewport);
        inputHandler.setSoundManager(soundManager);
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

        raidPanel = new RaidPanel(eventManager, spriteManager, viewport, uiTex);
        inputHandler.setRaidPanel(raidPanel);

        wallGate.setRaidPanel(raidPanel);

        // If a raid was restored from disk (process killed mid-raid — see
        // EventManager's constructor / RaidManager.resumeFromSave), reopen the
        // panel straight into it. Raid combat only ticks while the panel is in
        // COMBAT state (see render()'s raidPanel.isCombatActive() gate), so
        // without this the resumed raid would just sit frozen until the player
        // happened to tap the Raid button themselves.
        if (eventManager.getRaidManager().getActiveRaid() != null) {
            raidPanel.open();
        }

        unifiedShopPanel = new UnifiedShopPanel(eventManager, viewport, uiTex, spriteManager);
        inputHandler.setUnifiedShopPanel(unifiedShopPanel);
        raidPanel.setUnifiedShopPanel(unifiedShopPanel);

        dailyLoginPopup = new DailyLoginPopup(eventManager, viewport, uiTex, spriteManager);
        unifiedShopPanel.setDailyLoginPopup(dailyLoginPopup);
        inputHandler.setDailyLoginPopup(dailyLoginPopup);

        // Triggered by tapping an ancient_map in InventoryMenu (see its
        // item-tap handling) rather than an auto-open check like
        // dailyLoginPopup above — see HiddenTemplePopup's class javadoc.
        hiddenTemplePopup = new HiddenTemplePopup(eventManager, viewport, uiTex, spriteManager);
        inventoryMenu.setHiddenTemplePopup(hiddenTemplePopup);
        inputHandler.setHiddenTemplePopup(hiddenTemplePopup);

        // Equipment sink — opened from InventoryMenu's [Salvage] header
        // button on the Equipment tab, same wiring shape as hiddenTemplePopup
        // above (opened from inside a panel, but drawn/touch-routed at the
        // top level since panels don't nest batch/shape calls).
        salvagePanel = new SalvagePanel(eventManager, viewport, uiTex, spriteManager);
        inventoryMenu.setSalvagePanel(salvagePanel);
        inputHandler.setSalvagePanel(salvagePanel);

        // Duplicate-equipment sink alongside Salvage — opened from
        // InventoryMenu's [Refine] header button, same wiring shape as
        // salvagePanel above.
        refinePanel = new RefinePanel(eventManager, viewport, uiTex);
        inventoryMenu.setRefinePanel(refinePanel);
        inputHandler.setRefinePanel(refinePanel);

        prestigePanel = new PrestigePanel(eventManager, viewport, uiTex);
        inputHandler.setPrestigePanel(prestigePanel);

        outfitPanel = new OutfitPanel(eventManager, viewport, uiTex, spriteManager);
        inputHandler.setOutfitPanel(outfitPanel);
        unifiedShopPanel.setOutfitPanel(outfitPanel);

        settingsPanel = new SettingsPanel(eventManager, viewport, uiTex, spriteManager, soundManager);
        inputHandler.setSettingsPanel(settingsPanel);

        infoPanel = new InfoPanel(eventManager, spriteManager, viewport, uiTex);
        inputHandler.setInfoPanel(infoPanel);

        // Shared "Watch Ad" confirm popup for the 4 actions that also have a
        // Gold-cost path (see GoldManager/AdManager). Opened from the shop's
        // Gold tab (Fill Resources/Revive Party), ExplorePanel's per-slot
        // Rush button (Speed Exploration), and a periodic facility's 2nd tap
        // (Instant Spawn — the only one of the 4 with no prior button).
        adRewardPopup = new AdRewardPopup(eventManager, viewport, uiTex, spriteManager);
        unifiedShopPanel.setAdRewardPopup(adRewardPopup);
        explorePanel.setAdRewardPopup(adRewardPopup);
        inputHandler.setAdRewardPopup(adRewardPopup);

        offlinePopup = new OfflinePopup(uiTex, spriteManager);

        gridStartYShifted = BuildMenu.MENU_HEIGHT + LayoutConfig.GRID_PADDING ;

        inputHandler.setBuildMenu(buildMenu);
        inputHandler.setBuildButtonBounds(
            LayoutConfig.getButtonX(0), LayoutConfig.getBtnY(),
            LayoutConfig.BTN_WIDTH, LayoutConfig.BTN_HEIGHT);

        inputHandler.setOfflinePopup(offlinePopup);

        float lockBtnX = LayoutConfig.getInfoTextX() + LayoutConfig.getInfoTextWidth() - LOCK_BTN_SIZE - 2f;
        float lockBtnY = LayoutConfig.getLevelBoxY() + (LayoutConfig.LEVEL_BOX_SIZE - LOCK_BTN_SIZE) / 2f;
        inputHandler.setLockButtonBounds(lockBtnX, lockBtnY, LOCK_BTN_SIZE, LOCK_BTN_SIZE);

        // Sits in the sliver directly above the lock button, right-aligned
        // to the same margin — see InfoPanel's opening button placement note.
        float infoBtnX = LayoutConfig.getInfoTextX() + LayoutConfig.getInfoTextWidth() - INFO_BTN_SIZE - 2f;
        float infoBtnY = LayoutConfig.getLevelBoxY() + LayoutConfig.LEVEL_BOX_SIZE - INFO_BTN_SIZE - 1f;
        inputHandler.setInfoButtonBounds(infoBtnX, infoBtnY, INFO_BTN_SIZE, INFO_BTN_SIZE);

        inputHandler.setPrestigeBoxBounds(LayoutConfig.LEVEL_BOX_X, LayoutConfig.getLevelBoxY(),
            LayoutConfig.LEVEL_BOX_SIZE, LayoutConfig.LEVEL_BOX_SIZE);

        // Gear icon — sits in the blank strip above the nemesis box (between
        // the resource rows and the level/nemesis boxes), always visible
        // regardless of selection, unlike the info/lock buttons.
        float settingsBtnX = LayoutConfig.getNemesisBoxX()
            + (LayoutConfig.NEMESIS_BOX_SIZE - SETTINGS_BTN_SIZE) / 2f;
        float settingsBtnY = LayoutConfig.getLevelBoxY() + LayoutConfig.LEVEL_BOX_SIZE + 6f;
        inputHandler.setSettingsButtonBounds(settingsBtnX, settingsBtnY, SETTINGS_BTN_SIZE, SETTINGS_BTN_SIZE);

        /*float lockBtnX = LayoutConfig.getNemesisBoxX() + (LayoutConfig.NEMESIS_BOX_SIZE - LOCK_BTN_SIZE) / 2f;
        float lockBtnY = LayoutConfig.getLevelBoxY() - LOCK_BTN_SIZE - 4f;
        inputHandler.setLockButtonBounds(lockBtnX, lockBtnY, LOCK_BTN_SIZE, LOCK_BTN_SIZE);*/

        inputHandler.setWallGate(wallGate);


        LayoutConfig.setActualHeight(viewport.getWorldHeight());
        // calculateGridLayout();

        // Tutorial — constructed after every panel/system above exists (its
        // target-rect resolution reads the grid, WallGate, and LayoutConfig)
        // and after setActualHeight so TutorialOverlay.updateLayout() centers
        // correctly on the first frame. start() only actually activates it if
        // tutorial_state says it isn't completed/skipped yet — see
        // TutorialManager's persistence javadoc.
        tutorialManager = new TutorialManager(jsonManager);
        tutorialOverlay = new TutorialOverlay(tutorialManager, viewport, uiTex);
        tutorialOverlay.updateLayout();
        tutorialManager.start();
        inputHandler.setTutorialOverlay(tutorialOverlay);

        // Must run BEFORE processOfflineProgress() — checkpoint() refreshes
        // the trusted-clock anchor (and rejects an implausible wall-clock
        // jump) that processOfflineProgress() then reads via
        // getTrustedTimeMillis(). sync() is fire-and-forget (async, no-ops
        // today with no backend configured — see ServerTimeManager).
        eventManager.getServerTimeManager().checkpoint();
        eventManager.getServerTimeManager().sync();

        // Suppressed so offline catch-up (facility spawns, level-ups) replayed
        // from a long-offline gap doesn't play a burst of SFX on cold launch.
        soundManager.setSuppressed(true);
        processOfflineProgress();
        soundManager.setSuppressed(false);
        checkDailyLoginPopup();
        eventManager.getAdManager().checkDailyReset();

        Gdx.app.log(TAG, "=== Init complete ===");
    }

    /**
     * Auto-opens the Daily Login popup whenever a streak reward is
     * claimable. Called alongside processOfflineProgress() in both create()
     * and resume() (same "check on cold launch AND foreground resume"
     * convention offline progress already uses) so a player who backgrounds
     * the app past the 24h window is reminded as soon as they return.
     */
    private void checkDailyLoginPopup() {
        if (eventManager.getDailyLoginManager().isClaimAvailable()) {
            dailyLoginPopup.open();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // OFFLINE
    // ══════════════════════════════════════════════════════════════

    private void processOfflineProgress() {

        Gdx.app.log(TAG, "offline progress");
        int[] savedTimestamp = jsonManager.loadArray(TIMESTAMP_KEY);
        if (savedTimestamp == null || savedTimestamp.length < 2) return;

        long savedTime = ((long) savedTimestamp[0] << 32) | (savedTimestamp[1] & 0xFFFFFFFFL);
        long now = eventManager.getServerTimeManager().getTrustedTimeMillis();
        long elapsedMs = now - savedTime;
        if (elapsedMs <= 0) return;

        long cappedSeconds = Math.min(elapsedMs / 1000, MAX_OFFLINE_SECONDS);
        if (cappedSeconds < 15) return;

        long ticks = cappedSeconds / 15;

        ResourceManager rm = eventManager.getResourceManager();
        int foodBefore = rm.getAmount("food");
        int woodBefore = rm.getAmount("wood");
        int ironBefore = rm.getAmount("iron");

        float genMultiplier = eventManager.getPrestigeManager().getResourceGenMultiplier()
            * eventManager.getOutfitManager().getResourceGenMultiplier();
        for (long i = 0; i < ticks; i++) {
            rm.updateResources(genMultiplier);
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

        // Re-stamp "now" immediately after applying gains. Without this, create()
        // and resume() — which both call this method, and both fire on a cold
        // Android launch with no pause() in between — would read the same stale
        // timestamp and double-apply the same offline batch (resources, facility
        // spawns). Re-stamping makes the second call compute ~0 elapsed and no-op
        // via the cappedSeconds < 15 guard above.
        saveTimestamp();

        saveDirty = true;
        Gdx.app.log(TAG, "Offline: " + cappedSeconds + "s, food+"
            + foodGained + " wood+" + woodGained + " iron+" + ironGained);
    }

    private void saveTimestamp() {
        long now = eventManager.getServerTimeManager().getTrustedTimeMillis();
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
        if (!offlinePopup.isVisible() && !inventoryMenu.isBrowsing() && !explorePanel.isVisible()
            && !raidPanel.isVisible() && !unifiedShopPanel.isVisible()
            && !prestigePanel.isVisible() && !outfitPanel.isVisible()
            && !infoPanel.isVisible() && !salvagePanel.isVisible()
            && !refinePanel.isVisible() && !dailyLoginPopup.isVisible()
            && !settingsPanel.isVisible()) {
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

        // ALWAYS update raid combat (real-time):
        if (raidPanel.isCombatActive()) {
            eventManager.getRaidManager().update(delta);
            raidPanel.update(delta);
            // Check if raid ended
            RaidState raid = eventManager.getRaidManager().getActiveRaid();
            if (raid != null && (raid.isCompleted() || raid.isFailed())) {
                // RaidPanel will detect this and switch to RESULTS state
                if (raidPanel.isCombatActive()) {
                    // Force state transition
                    // This is handled in RaidPanel.drawCombat by checking raid state
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

            batch.begin();

            drawGrid();
            drawDraggedObject();

            batch.end();

            drawInventoryTints();
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
            drawInfoButton();
            drawSettingsButton();

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
        // One-shot contextual tip the first time this panel is opened —
        // rising-edge check, same pattern as buildMenuOpen above.
        if (inventoryMenu.isVisible() && !wasInventoryVisible) {
            tutorialManager.onPanelFirstOpened("inventory");
        }
        wasInventoryVisible = inventoryMenu.isVisible();

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
        if (explorePanel.isVisible() && !wasExploreVisible) {
            tutorialManager.onPanelFirstOpened("explore");
        }
        wasExploreVisible = explorePanel.isVisible();

        if (raidPanel.isFormingParty()) {
            drawRaidPartyTints();
        }
        if (raidPanel.isVisible()) {
            raidPanel.draw(shapeRenderer, batch, font, fontSmall);
        }
        if (raidPanel.isVisible() && !wasRaidVisible) {
            tutorialManager.onPanelFirstOpened("raid");
        }
        wasRaidVisible = raidPanel.isVisible();

        if (unifiedShopPanel.isVisible()) {
            unifiedShopPanel.drawBackground(shapeRenderer);
            batch.begin();
            unifiedShopPanel.drawContent(batch, font, fontSmall);
            batch.end();
        }

        if (prestigePanel.isVisible()) {
            prestigePanel.drawBackground(shapeRenderer);
            batch.begin();
            prestigePanel.drawContent(batch, font, fontSmall);
            batch.end();
        }

        if (outfitPanel.isVisible()) {
            outfitPanel.drawBackground(shapeRenderer);
            batch.begin();
            outfitPanel.drawContent(batch, font, fontSmall);
            batch.end();
        }

        if (settingsPanel.isVisible()) {
            settingsPanel.drawBackground(shapeRenderer);
            batch.begin();
            settingsPanel.drawContent(batch, font, fontSmall);
            batch.end();
        }

        if (infoPanel.isVisible()) {
            infoPanel.drawBackground(shapeRenderer);
            batch.begin();
            infoPanel.drawContent(batch, font, fontSmall);
            batch.end();
        }

        if (dailyLoginPopup.isVisible()) {
            dailyLoginPopup.drawBackground(shapeRenderer);
            batch.begin();
            dailyLoginPopup.drawContent(batch, font, fontSmall);
            batch.end();
        }

        if (hiddenTemplePopup.isVisible()) {
            hiddenTemplePopup.drawBackground(shapeRenderer);
            batch.begin();
            hiddenTemplePopup.drawContent(batch, font, fontSmall);
            batch.end();
        }

        if (salvagePanel.isVisible()) {
            salvagePanel.drawBackground(shapeRenderer);
            batch.begin();
            salvagePanel.drawContent(batch, font, fontSmall);
            batch.end();
        }

        if (refinePanel.isVisible()) {
            refinePanel.drawBackground(shapeRenderer);
            batch.begin();
            refinePanel.drawContent(batch, font, fontSmall);
            batch.end();
        }

        // Drawn last among the panels (top-most) — it can be opened from
        // inside the shop's Gold tab, so it must paint over that panel too.
        if (adRewardPopup.isVisible()) {
            adRewardPopup.drawBackground(shapeRenderer);
            batch.begin();
            adRewardPopup.drawContent(batch, font, fontSmall);
            batch.end();
        }

        // Tutorial overlay + target ring — drawn after every other panel but
        // BEFORE the offline popup (unlike those panels, deliberately not
        // "on top of everything": if a fresh install also has a stale
        // last_active_timestamp from an earlier save format, both could be
        // visible on the same frame, and the offline popup must win so its
        // OK button isn't visually buried under the tutorial card — see
        // GridInputHandler's matching input-gate ordering).
        if (!offlinePopup.isVisible()) {
            drawTutorialHighlightRing();
            if (tutorialOverlay.isVisible()) {
                batch.begin();
                tutorialOverlay.draw(batch, font, fontSmall);
                batch.end();
            }
        }

        // IMPORTANT: Call explorationManager.update() ALWAYS (even when panel is open)
        // so events process in real time:
        if (!offlinePopup.isVisible() && eventManager.getExplorationManager() != null) {
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
            rm.updateResources(eventManager.getPrestigeManager().getResourceGenMultiplier()
                * eventManager.getOutfitManager().getResourceGenMultiplier());
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
        drawGridBackgroundAtY(gridStartY);
    }

    private void drawGridBackgroundShifted() {
        drawGridBackgroundAtY(gridStartYShifted);
    }

    private void drawGridBackgroundAtY(float baseY) {
        Grid grid = eventManager.getGridInstance();
        int cols = grid.getWidth();
        int rows = grid.getHeight();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                float drawX = gridStartX + x * (cellSize + LayoutConfig.CELL_GAP);
                float drawY = baseY + (rows - 1 - y) * (cellSize + LayoutConfig.CELL_GAP);
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
        drawSelectionOutlineAtY(gridStartY);
    }

    private void drawSelectionOutlineShifted() {
        drawSelectionOutlineAtY(gridStartYShifted);
    }

    private void drawSelectionOutlineAtY(float baseY) {
        if (!inputHandler.hasSelection()) return;

        int selX = inputHandler.getSelectedCellX();
        int selY = inputHandler.getSelectedCellY();
        Grid grid = eventManager.getGridInstance();
        int rows = grid.getHeight();

        float drawX = gridStartX + selX * (cellSize + LayoutConfig.CELL_GAP);
        float drawY = baseY + (rows - 1 - selY) * (cellSize + LayoutConfig.CELL_GAP);
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
        drawGridAtY(gridStartY);
    }

    private void drawGridShifted() {
        drawGridAtY(gridStartYShifted);
    }

    private void drawGridAtY(float baseY) {
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
                float drawY = baseY + (rows - 1 - y) * (cellSize + LayoutConfig.CELL_GAP);

                String objectId = cell.getOccupant();
                GameObject obj = gom.getObject(objectId);

                // Swap slide: while active, draw at an interpolated position
                // between the object's old cell and its current (destination)
                // cell instead of snapping straight there. The HP bar below
                // reuses drawX/drawY, so it travels with the sprite for free.
                if (obj != null) {
                    float slideP = slideProgress(obj);
                    if (slideP < 1f) {
                        float fromX = gridStartX + obj.getSlideFromX() * (cellSize + LayoutConfig.CELL_GAP);
                        float fromY = baseY + (rows - 1 - obj.getSlideFromY()) * (cellSize + LayoutConfig.CELL_GAP);
                        drawX = fromX + (drawX - fromX) * slideP;
                        drawY = fromY + (drawY - fromY) * slideP;
                    }
                }

                Texture tex = (obj != null)
                    ? spriteManager.getTextureForObject(obj.getType(), obj.getLvl())
                    : spriteManager.getDefaultTile();

                float margin = cellSize * 0.05f;
                drawObjectSprite(tex, obj, drawX + margin, drawY + margin, cellSize - margin * 2);
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

    // ── Idle animation (units, monsters, Prince — see GameTypes) ──
    // Everything else (facilities, storage, chests, tokens, pouches) draws
    // via the plain unscaled path below, unless it's mid a one-shot reaction
    // pulse (tap, periodic spawn) — see tryDrawPulse below.
    private static final float IDLE_BOB_UNIT = 0.035f;     // fraction of cell size
    private static final float IDLE_SPEED_UNIT = 2.0f;     // rad/s
    private static final float IDLE_BOB_MONSTER = 0.05f;   // more restless than units
    private static final float IDLE_SPEED_MONSTER = 3.2f;
    private static final float IDLE_BOB_PRINCE = 0.03f;    // calmer, regal
    private static final float IDLE_SPEED_PRINCE = 1.5f;
    private static final float IDLE_SQUASH = 0.06f;        // max scale deviation

    // ── Reaction pulse (GameObject.triggerPulse() — facility/chest taps,
    // periodic facility production) ──
    private static final long PULSE_DURATION_MS = 260L;
    private static final float PULSE_AMOUNT = 0.20f;       // peak scale-up

    // ── Swap slide (GameObject.triggerSlide() — the displaced side of a
    // board swap gliding from its old cell into the dragged object's old
    // cell instead of teleporting) ──
    private static final long SLIDE_DURATION_MS = 180L;    // snappy; tunable feel knob

    /**
     * Eased 0..1 slide progress for obj's active swap-slide, or 1f (done /
     * no active slide) so the common no-slide path skips the position
     * offset entirely.
     */
    private float slideProgress(GameObject obj) {
        long start = obj.getSlideStartMs();
        if (start == 0L) return 1f;
        long elapsed = TimeUtils.millis() - start;
        if (elapsed < 0L || elapsed >= SLIDE_DURATION_MS) return 1f;
        float t = elapsed / (float) SLIDE_DURATION_MS;
        float inv = 1f - t;
        return 1f - inv * inv * inv; // cubic ease-out (fast start, gentle settle)
    }

    /**
     * Draws a grid-cell sprite, applying a subtle idle bob + squash-stretch
     * for units/monsters/Prince, or a brief center-anchored "pop" for
     * anything mid a reaction pulse (facility/chest tap, periodic spawn —
     * see GameObject.triggerPulse()). Each idle-bobbing object gets a phase
     * offset derived from its id so they don't all bounce in lockstep.
     * Anything else with no active pulse (and null obj, e.g. an orphaned
     * cell) falls through to a plain draw — zero behavior change and zero
     * extra cost for the common case.
     */
    private void drawObjectSprite(Texture tex, GameObject obj, float x, float y, float size) {
        float bobFrac;
        float speed;
        if (obj == null) {
            batch.draw(tex, x, y, size, size);
            return;
        }
        String type = obj.getType();
        if (GameTypes.isUnit(type)) {
            bobFrac = IDLE_BOB_UNIT;
            speed = IDLE_SPEED_UNIT;
        } else if (GameTypes.isMonster(type)) {
            bobFrac = IDLE_BOB_MONSTER;
            speed = IDLE_SPEED_MONSTER;
        } else if ("prince".equals(type)) {
            bobFrac = IDLE_BOB_PRINCE;
            speed = IDLE_SPEED_PRINCE;
        } else {
            if (!tryDrawPulse(tex, obj, x, y, size)) {
                batch.draw(tex, x, y, size, size);
            }
            return;
        }

        float phase = ((obj.getId().hashCode() & 0x7FFFFFFF) % 1000) / 1000f * (float) (Math.PI * 2);
        float t = animationTime * speed + phase;
        float bob = 0.5f + 0.5f * (float) Math.sin(t);
        float bobOffset = bob * size * bobFrac;
        // Squash at the top/bottom of the hop (momentarily still), stretch
        // at the midpoint (moving fastest) — standard squash-and-stretch,
        // double the bob's frequency since it happens twice per hop cycle.
        float squash = IDLE_SQUASH * (float) Math.cos(2f * t);
        float scaleY = 1f + squash;
        float scaleX = 1f - squash * 0.5f;

        batch.draw(tex, x, y + bobOffset, size / 2f, 0f,
            size, size, scaleX, scaleY, 0f,
            0, 0, tex.getWidth(), tex.getHeight(), false, false);
    }

    /**
     * Draws a brief center-anchored scale-up "pop" if obj is within
     * PULSE_DURATION_MS of its last triggerPulse() call. Returns false
     * (drew nothing) when there's no active pulse, so the caller falls
     * back to a plain draw.
     */
    private boolean tryDrawPulse(Texture tex, GameObject obj, float x, float y, float size) {
        long pulseStart = obj.getPulseStartMs();
        if (pulseStart == 0L) return false;
        long elapsed = TimeUtils.millis() - pulseStart;
        if (elapsed < 0L || elapsed >= PULSE_DURATION_MS) return false;

        float progress = elapsed / (float) PULSE_DURATION_MS;
        float envelope = (float) Math.sin(progress * Math.PI); // 0 -> 1 -> 0
        float scale = 1f + PULSE_AMOUNT * envelope;

        batch.draw(tex, x, y, size / 2f, size / 2f, size, size, scale, scale, 0f,
            0, 0, tex.getWidth(), tex.getHeight(), false, false);
        return true;
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

        // Icon replaces the "Food:"/"Wood:"/etc. word — CachedLabel still
        // only rebuilds its string when the underlying int changes (pass ""
        // as the prefix instead of "Food: " — same class, same caching,
        // just a bare number now that the word isn't needed).
        font.setColor(Color.WHITE);
        IconText.iconThenText(batch, font, glyphLayout, spriteManager, "food",
            foodLabel.get("", rm.getAmount("food")), 20f, LayoutConfig.getResourcesY(), 16f);
        IconText.iconThenText(batch, font, glyphLayout, spriteManager, "wood",
            woodLabel.get("", rm.getAmount("wood")), 170f, LayoutConfig.getResourcesY(), 16f);
        IconText.iconThenText(batch, font, glyphLayout, spriteManager, "iron",
            ironLabel.get("", rm.getAmount("iron")), 320f, LayoutConfig.getResourcesY(), 16f);

        // Nail/Slate/Ingot/Relic already have real art (Token grid-object
        // sprites) — reuse it directly, same "<name>_token_1" key InfoPanel
        // already established for these four.
        fontSmall.setColor(0.7f, 0.8f, 0.7f, 1f);
        IconText.iconThenText(batch, fontSmall, glyphLayout, spriteManager, "nail_token_1",
            nailLabel.get("", rm.getAmount("nail")), 20f, LayoutConfig.getResourcesRow2Y(), 14f);
        IconText.iconThenText(batch, fontSmall, glyphLayout, spriteManager, "slate_token_1",
            slateLabel.get("", rm.getAmount("slate")), 130f, LayoutConfig.getResourcesRow2Y(), 14f);
        IconText.iconThenText(batch, fontSmall, glyphLayout, spriteManager, "ingot_token_1",
            ingotLabel.get("", rm.getAmount("ingot")), 240f, LayoutConfig.getResourcesRow2Y(), 14f);
        IconText.iconThenText(batch, fontSmall, glyphLayout, spriteManager, "relic_token_1",
            relicLabel.get("", rm.getAmount("relic")), 350f, LayoutConfig.getResourcesRow2Y(), 14f);

        // Gold chip — right-aligned on the top resource row, tappable to open shop
        int goldAmt = eventManager.getGoldManager().getGold();
        String goldStr = goldLabel.get("", goldAmt);
        fontSmall.setColor(1f, 0.85f, 0.25f, 1f); // gold color
        List<IconText.Seg> goldSegs = Arrays.asList(IconText.Seg.icon("gold"), IconText.Seg.text(goldStr));
        float goldContentW = IconText.measure(fontSmall, glyphLayout, goldSegs, 16f);
        goldChipW = goldContentW + 20f;
        goldChipH = 20f;
        goldChipX = LayoutConfig.WORLD_WIDTH - goldChipW - 8f;
        goldChipY = LayoutConfig.getResourcesY() - 15f;
        uiTex.drawPanel(batch, uiTex.panelMedium, goldChipX, goldChipY, goldChipW, goldChipH);
        fontSmall.setColor(1f, 0.85f, 0.25f, 1f);
        IconText.draw(batch, fontSmall, glyphLayout, spriteManager, goldSegs,
            goldChipX + 8f, goldChipY + goldChipH - 5f, 16f);
        inputHandler.setGoldChipBounds(goldChipX, goldChipY, goldChipW, goldChipH);
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

        // Gold "LEVEL" label = tap here, Prince Prestige is ready.
        if (eventManager.getPrestigeManager().canPrestige()) {
            fontSmall.setColor(1f, 0.85f, 0.3f, 1f);
        } else {
            fontSmall.setColor(0.6f, 0.6f, 0.7f, 1f);
        }
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
        String name = TextUtil.capitalize(obj.getType()) + " (Lv." + obj.getLvl() + ")";
        font.setColor(Color.WHITE);
        font.draw(batch, name, textX, barTop - 10f);

        // Line 2: Description
        fontSmall.setColor(0.7f, 0.7f, 0.8f, 1f);
        String desc = obj.getDescription();
        if (desc != null) {
            fontSmall.draw(batch, desc, textX, barTop - 28f, maxWidth,
                com.badlogic.gdx.utils.Align.left, false);
        }

        // Line 3 & 4: type-specific stats + extra info — rebuilt immediately
        // on selection change, otherwise throttled (see field comment above).
        boolean selectionChanged = !selId.equals(cachedStatsSelId);
        statsRefreshTimer += Gdx.graphics.getDeltaTime();
        if (selectionChanged || cachedStatsLine == null
            || statsRefreshTimer >= STATS_REFRESH_INTERVAL) {
            cachedStatsSelId = selId;
            cachedStatsLine = getStatsString(obj, data);
            cachedExtraLine = getExtraString(obj, data);
            statsRefreshTimer = 0f;
        }
        String stats = cachedStatsLine;
        if (stats != null) {
            fontSmall.setColor(0.85f, 0.8f, 0.5f, 1f); // gold color for stats
            fontSmall.draw(batch, stats, textX, barTop - 46f, maxWidth,
                com.badlogic.gdx.utils.Align.left, false);
        }

        // Line 4: Extra info (if needed)
        String extra = cachedExtraLine;
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

    /**
     * Opens InfoPanel (all-levels reference table) for the selected object's
     * type. Sits in the sliver directly above the lock button — see
     * INFO_BTN_SIZE's bounds computation in create()/resize().
     */
    private void drawInfoButton() {
        if (!inputHandler.hasSelection()) return;

        float btnX = LayoutConfig.getInfoTextX() + LayoutConfig.getInfoTextWidth() - INFO_BTN_SIZE - 2f;
        float btnY = LayoutConfig.getLevelBoxY() + LayoutConfig.LEVEL_BOX_SIZE - INFO_BTN_SIZE - 1f;

        uiTex.drawPanel(batch, uiTex.btnNormal, btnX, btnY, INFO_BTN_SIZE, INFO_BTN_SIZE);

        font.setColor(Color.CYAN);
        String infoText = "i";
        glyphLayout.setText(font, infoText);
        font.draw(batch, infoText,
            btnX + (INFO_BTN_SIZE - glyphLayout.width) / 2f,
            btnY + INFO_BTN_SIZE / 2f + glyphLayout.height / 2f);
        font.setColor(Color.WHITE);
    }

    /**
     * Opens SettingsPanel. Always visible (unlike the lock/info buttons,
     * which only show for a selected object) — sits in the blank strip
     * above the nemesis box; see SETTINGS_BTN_SIZE's bounds computation in
     * create()/resize(). Uses the "⚙" glyph already relied on elsewhere
     * in the UI font (RaidPanel's "⚠"/"★" glyphs); if it doesn't
     * render on a real device, swap for a plain ASCII label like "S".
     */
    private void drawSettingsButton() {
        float btnX = LayoutConfig.getNemesisBoxX()
            + (LayoutConfig.NEMESIS_BOX_SIZE - SETTINGS_BTN_SIZE) / 2f;
        float btnY = LayoutConfig.getLevelBoxY() + LayoutConfig.LEVEL_BOX_SIZE + 6f;

        uiTex.drawPanel(batch, uiTex.btnNormal, btnX, btnY, SETTINGS_BTN_SIZE, SETTINGS_BTN_SIZE);

        font.setColor(Color.LIGHT_GRAY);
        String gearText = "⚙";
        glyphLayout.setText(font, gearText);
        font.draw(batch, gearText,
            btnX + (SETTINGS_BTN_SIZE - glyphLayout.width) / 2f,
            btnY + SETTINGS_BTN_SIZE / 2f + glyphLayout.height / 2f);
        font.setColor(Color.WHITE);
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

    // ══════════════════════════════════════════════════════════════
    // TUTORIAL HIGHLIGHT RING
    // ══════════════════════════════════════════════════════════════

    /**
     * Same 4-rect outline technique as drawOutlineRect above, but for a
     * non-square width/height rect — needed for HUD/gate/button targets
     * (drawOutlineRect assumes a square, which only grid cells are).
     */
    private void drawOutlineRectWH(float x, float y, float w, float h, float thickness) {
        shapeRenderer.rect(x - thickness, y + h, w + thickness * 2, thickness);
        shapeRenderer.rect(x - thickness, y - thickness, w + thickness * 2, thickness);
        shapeRenderer.rect(x - thickness, y - thickness, thickness, h + thickness * 2);
        shapeRenderer.rect(x + w, y - thickness, thickness, h + thickness * 2);
    }

    private float[] cellRect(int x, int y, int rows) {
        float drawX = gridStartX + x * (cellSize + LayoutConfig.CELL_GAP);
        float drawY = gridStartY + (rows - 1 - y) * (cellSize + LayoutConfig.CELL_GAP);
        return new float[]{ drawX, drawY, cellSize, cellSize };
    }

    /**
     * Resolves a tutorial step's semantic target string into one or more
     * screen rects {x,y,w,h} in world coordinates. Target syntax (see
     * TutorialManager.TutorialStep.target javadoc):
     *   "cell:<type>"    — the cell of the first object of that type
     *   "cells:mergeable"— the first pair of same-type+same-level units found
     *   "hud:resources"  — the top-bar resource readout
     *   "hud:xpbar"      — the level box / XP bar
     *   "wallgate"        — the Wall Gate
     *   "buildbutton"     — the bottom-bar Build button
     * Returns an empty list if the target can't be resolved right now (e.g.
     * no matching object on the board yet) — the caller just skips the ring.
     */
    private List<float[]> resolveTutorialTargetRects(String target) {
        List<float[]> rects = new ArrayList<>();
        if (target == null || target.isEmpty()) return rects;

        Grid grid = eventManager.getGridInstance();
        int rows = grid.getHeight();

        if (target.startsWith("cell:")) {
            String type = target.substring("cell:".length());
            for (GameObject go : eventManager.getGRID_OBJECT_MANAGER().getObjectMap().values()) {
                if (type.equals(go.getType())) {
                    rects.add(cellRect(go.getxPos(), go.getyPos(), rows));
                    break;
                }
            }
        } else if ("cells:mergeable".equals(target)) {
            List<GameObject> units = new ArrayList<>();
            for (GameObject go : eventManager.getGRID_OBJECT_MANAGER().getObjectMap().values()) {
                if (GameTypes.isUnit(go.getType())) units.add(go);
            }
            outer:
            for (int i = 0; i < units.size(); i++) {
                for (int j = i + 1; j < units.size(); j++) {
                    GameObject a = units.get(i);
                    GameObject b = units.get(j);
                    if (a.getType().equals(b.getType()) && a.getLvl() == b.getLvl()) {
                        rects.add(cellRect(a.getxPos(), a.getyPos(), rows));
                        rects.add(cellRect(b.getxPos(), b.getyPos(), rows));
                        break outer;
                    }
                }
            }
        } else if ("hud:resources".equals(target)) {
            rects.add(new float[]{ 4f, LayoutConfig.getResourcesRow2Y() - 6f,
                LayoutConfig.WORLD_WIDTH - 8f, 34f });
        } else if ("hud:xpbar".equals(target)) {
            rects.add(new float[]{ LayoutConfig.LEVEL_BOX_X, LayoutConfig.getLevelBoxY(),
                LayoutConfig.LEVEL_BOX_SIZE, LayoutConfig.LEVEL_BOX_SIZE });
        } else if ("wallgate".equals(target)) {
            rects.add(new float[]{ wallGate.getGateX(), wallGate.getGateY(),
                wallGate.getGateWidth(), wallGate.getGateHeight() });
        } else if ("buildbutton".equals(target)) {
            rects.add(new float[]{ LayoutConfig.getButtonX(0), LayoutConfig.getBtnY(),
                LayoutConfig.BTN_WIDTH, LayoutConfig.BTN_HEIGHT });
        }
        return rects;
    }

    /**
     * Draws a pulsing ring around the current tutorial step's target, if
     * any. TEXT steps also carry an (optional) target so the card's subject
     * is still highlighted on the board even though the step advances via
     * Next rather than the highlighted action — INTERACTIVE steps are the
     * only ones that gate on the player actually touching the target.
     */
    private void drawTutorialHighlightRing() {
        TutorialManager.TutorialStep step = tutorialManager.getCurrentStep();
        if (step == null || step.target == null || step.target.isEmpty()) return;

        List<float[]> rects = resolveTutorialTargetRects(step.target);
        if (rects.isEmpty()) return;

        float pulse = 0.6f + 0.4f * (float) Math.sin(animationTime * 4.0);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(1f * pulse, 0.85f * pulse, 0.2f * pulse, 1f);
        for (float[] r : rects) {
            drawOutlineRectWH(r[0], r[1], r[2], r[3], 3f);
        }
        shapeRenderer.end();
    }

    /*private boolean isUnitType(String type) {
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
    }*/

    private boolean isUnitType(String type)    { return GameTypes.isUnit(type); }

    /*private boolean isMonsterType(String type) {
        switch (type) {
            case "gremlin": case "troll": case "orc":
            case "wraith": case "demon":
                return true;
            default:
                return false;
        }
    }*/

    private boolean isMonsterType(String type) { return GameTypes.isMonster(type); }


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
            case "phoenix": case "elder_villager": case "lumberlord": case "grand_chef":
            case "ore_master": case "war_veteran": case "master_builder":
            case "royal_knight": case "beastmaster": case "shadowbow":
            case "forgemaster": case "ironclad": case "high_priest":
            case "archangel": case "storm_griffin": case "venom_drake":
            case "elder_dragon": case "eternal_phoenix":{
                UnitData ud = (UnitData) data;
                Unit u = (Unit) obj;
                RuneSystem rs = eventManager.getRuneSystem();
                StringBuilder sb = new StringBuilder();
                if (u.getMax_hp() > 0) {
                    sb.append("HP: ").append(u.getHp()).append("/").append(u.getMax_hp());
                }
                if (ud.getDamage() > 0) {
                    if (sb.length() > 0) sb.append(" | ");
                    int bonus = eventManager.getInventory().getEquipBonusDamage(
                        inputHandler.getSelectedObjectId());
                    int baseDmg = ud.getDamage() + bonus;
                    int boosted = rs.getBoostedDamage(inputHandler.getSelectedObjectId(), baseDmg);
                    sb.append("DMG: ").append(boosted);
                    if (boosted > ud.getDamage()) {
                        sb.append(" (base ").append(ud.getDamage()).append(")");
                    }
                }
                if (ud.getGen_rate() > 0) {
                    if (sb.length() > 0) sb.append(" | ");
                    sb.append("+").append(ud.getGen_rate()).append(" ")
                        .append(ud.getResource()).append("/tick");
                }
                if (ud.getSecondaryGenRate() > 0) {
                    if (sb.length() > 0) sb.append(" | ");
                    sb.append("+").append(ud.getSecondaryGenRate()).append(" ")
                        .append(ud.getSecondaryResource()).append("/tick");
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
                    + " | Reward: " + TextUtil.capitalize(md.getReward());
            }

            // Chests
            case "nail_chest": case "slate_chest":
            case "ingot_chest": case "relic_chest": {
                Chest chest = (Chest) obj;
                ChestData cd = (ChestData) data;
                return "Taps left: " + chest.getTap_count()
                    + " | Drops: " + TextUtil.capitalize(cd.getToken_type());
            }

            // Tokens
            case "nail_token": case "slate_token":
            case "ingot_token": case "relic_token": {
                TokenData td = (TokenData) data;
                return "Value: +" + td.getValue() + " " + td.getTokenType();
            }

            // Resource pouches
            case "food_pouch": case "wood_pouch": case "iron_pouch": {
                ResourcePouchData pd = (ResourcePouchData) data;
                return "Fills: +" + pd.getFill_percent() + "% " + pd.getResource_type();
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
            case "phoenix": case "elder_villager": case "lumberlord": case "grand_chef":
            case "ore_master": case "war_veteran": case "master_builder":
            case "royal_knight": case "beastmaster": case "shadowbow":
            case "forgemaster": case "ironclad": case "high_priest":
            case "archangel": case "storm_griffin": case "venom_drake":
            case "elder_dragon": case "eternal_phoenix":{
                UnitData ud = (UnitData) data;
                StringBuilder sb = new StringBuilder();
                sb.append("XP: ").append(ud.getXP_Rate());
                sb.append(" | ").append(TextUtil.capitalize(ud.getNemesis()));

                Item equipped = eventManager.getInventory().getEquippedItem(
                    inputHandler.getSelectedObjectId());
                if (equipped != null) {
                    sb.append(" | ").append(equipped.getName());
                } else if (ud.getHp() > 0) {
                    sb.append(" | [No item]");
                }

                RuneSystem rs = eventManager.getRuneSystem();
                String runeSummary = rs.getRuneSummary(inputHandler.getSelectedObjectId());
                if (runeSummary != null) {
                    sb.append(" | Runes: ").append(runeSummary);
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
        drawCenteredText(fontSmall, TextUtil.capitalize(displayedNemesis), boxCenterX, barTop - 14f);

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

    private void drawInventoryTints() {
        drawTintsAtY(gridStartY, inventoryMenu::getUnitTintColor);
    }

    /**
     * Shared by the three "select a unit from the grid" tint overlays
     * (Inventory equip-target, Explore send-to-zone, Raid form-party) — the
     * only thing that varies between them is which panel's eligibility/tint
     * lookup to call.
     */
    private void drawTintsAtY(float baseY, Function<String, Color> tintProvider) {
        Grid grid = eventManager.getGridInstance();
        int cols = grid.getWidth();
        int rows = grid.getHeight();

        batch.begin();
        for (int x = 0; x < cols; x++) {
            for (int y = 0; y < rows; y++) {
                Cell cell = grid.getCell(x, y);
                if (cell.isEmpty()) continue;

                String objectId = cell.getOccupant();
                Color tint = tintProvider.apply(objectId);
                if (tint == null) continue;

                float drawX = gridStartX + x * (cellSize + LayoutConfig.CELL_GAP);
                float drawY = baseY + (rows - 1 - y) * (cellSize + LayoutConfig.CELL_GAP);

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

    private void drawCenteredText(BitmapFont f, String text, float centerX, float y) {
        glyphLayout.setText(f, text);
        f.draw(batch, text, centerX - glyphLayout.width / 2f, y);
    }

    private void drawExploreTints() {
        drawTintsAtY(gridStartY, explorePanel::getUnitTintColor);
    }

    private void drawRaidPartyTints() {
        drawTintsAtY(gridStartY, raidPanel::getUnitTintColor);
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

    @Override
    public void onPrestigeReset() {
        Gdx.app.log(TAG, "Prestige reset — recalculating layout");
        // Same layout refresh as onGridExpanded() — the reset can both shrink
        // and grow the grid relative to where it was.
        calculateGridLayout();
        wallGate.updateLayout();
        Grid grid = eventManager.getGridInstance();
        inputHandler.setGridLayout(gridStartX, gridStartY, cellSize, LayoutConfig.CELL_GAP,
            grid.getWidth(), grid.getHeight());

        inputHandler.clearSelection();
        if (buildMenu.isVisible()) buildMenu.hide();

        saveGame();
    }

    // ── Tutorial hooks — forwarded to TutorialManager. tutorialManager is
    // constructed partway through create(), but BATTLE_FIELD_MANAGER's
    // listener (= this class) is wired up earlier, so these null-check the
    // same way the rest of this class treats not-yet-constructed fields. ──

    @Override
    public void onUnitSpawnedFromFacility(String facilityType) {
        if (tutorialManager != null) tutorialManager.onUnitSpawnedFromFacility(facilityType);
        if (soundManager != null) soundManager.play(SoundManager.SfxId.SPAWN);
    }

    @Override
    public void onUnitMerged(String type, int newLevel) {
        if (tutorialManager != null) tutorialManager.onUnitMerged(type, newLevel);
        if (soundManager != null) soundManager.play(SoundManager.SfxId.MERGE);
    }

    @Override
    public void onUnitDismissedToPrince(String type) {
        if (tutorialManager != null) tutorialManager.onUnitDismissedToPrince(type);
        if (soundManager != null) soundManager.play(SoundManager.SfxId.DISMISS);
    }

    @Override
    public void onMonsterSpawned(String type) {
        if (tutorialManager != null) tutorialManager.onMonsterSpawned(type);
        if (soundManager != null) soundManager.play(SoundManager.SfxId.MONSTER);
    }

    @Override
    public void onLevelUp(int newLevel) {
        if (tutorialManager != null) tutorialManager.onLevelUp(newLevel);
        if (soundManager != null) soundManager.play(SoundManager.SfxId.LEVEL_UP);
    }

    // ══════════════════════════════════════════════════════════════
    // LIFECYCLE
    // ══════════════════════════════════════════════════════════════

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        LayoutConfig.setActualHeight(viewport.getWorldHeight());
        wallGate.updateLayout();
        if (tutorialOverlay != null) tutorialOverlay.updateLayout();
        calculateGridLayout();

        Grid grid = eventManager.getGridInstance();
        inputHandler.setGridLayout(gridStartX, gridStartY, cellSize, LayoutConfig.CELL_GAP,
            grid.getWidth(), grid.getHeight());

        // Recalculate lock button bounds with correct actualHeight
        float lockBtnX = LayoutConfig.getInfoTextX() + LayoutConfig.getInfoTextWidth() - LOCK_BTN_SIZE - 2f;
        float lockBtnY = LayoutConfig.getLevelBoxY() + (LayoutConfig.LEVEL_BOX_SIZE - LOCK_BTN_SIZE) / 2f;
        inputHandler.setLockButtonBounds(lockBtnX, lockBtnY, LOCK_BTN_SIZE, LOCK_BTN_SIZE);

        float infoBtnX = LayoutConfig.getInfoTextX() + LayoutConfig.getInfoTextWidth() - INFO_BTN_SIZE - 2f;
        float infoBtnY = LayoutConfig.getLevelBoxY() + LayoutConfig.LEVEL_BOX_SIZE - INFO_BTN_SIZE - 1f;
        inputHandler.setInfoButtonBounds(infoBtnX, infoBtnY, INFO_BTN_SIZE, INFO_BTN_SIZE);

        inputHandler.setPrestigeBoxBounds(LayoutConfig.LEVEL_BOX_X, LayoutConfig.getLevelBoxY(),
            LayoutConfig.LEVEL_BOX_SIZE, LayoutConfig.LEVEL_BOX_SIZE);

        float settingsBtnX = LayoutConfig.getNemesisBoxX()
            + (LayoutConfig.NEMESIS_BOX_SIZE - SETTINGS_BTN_SIZE) / 2f;
        float settingsBtnY = LayoutConfig.getLevelBoxY() + LayoutConfig.LEVEL_BOX_SIZE + 6f;
        inputHandler.setSettingsButtonBounds(settingsBtnX, settingsBtnY, SETTINGS_BTN_SIZE, SETTINGS_BTN_SIZE);

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
        // See create()'s matching checkpoint()/sync()-before-offline-progress
        // ordering note — same reasoning applies on foreground resume.
        eventManager.getServerTimeManager().checkpoint();
        eventManager.getServerTimeManager().sync();

        // See create()'s matching suppress/unsuppress — same reasoning.
        soundManager.setSuppressed(true);
        processOfflineProgress();
        soundManager.setSuppressed(false);
        checkDailyLoginPopup();
        eventManager.getAdManager().checkDailyReset();
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
        soundManager.dispose();
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

            RuneSystem rs = eventManager.getRuneSystem();
            jsonManager.saveRuneFragments("rune_fragments", rs.getFragmentCounts());
            jsonManager.saveRuneCrafted("rune_crafted", rs.getCraftedRunes());
            jsonManager.saveRuneApplications("rune_applications", rs.getAppliedRunes());

            RaidManager raidMgr = eventManager.getRaidManager();
            jsonManager.saveRuneFragments("raid_completion", raidMgr.getCompletionMap());
            jsonManager.saveArray("raid_currency", new int[]{
                raidMgr.getWarTrophies(), raidMgr.getBossTokens(),
                raidMgr.isEndlessWarningSuppressed() ? 1 : 0
            });
            // Always written, even when null (no active raid) — see
            // JsonManager.saveRaidState. Lets a raid resume after the process
            // is killed mid-raid instead of losing the party permanently.
            jsonManager.saveRaidState("raid_active_state", raidMgr.getActiveRaid());

            // Endless Gauntlet's permanently-lost parties — see DeadPartyManager.
            jsonManager.saveDeadPartyPool("dead_party_pool",
                eventManager.getDeadPartyManager().getPool());

            Map<String, Object> shopSave = new java.util.HashMap<>();
            shopSave.put("stock", eventManager.getTrophyShop().getCurrentStock());
            shopSave.put("lastDailyRefresh", eventManager.getTrophyShop().getLastDailyRefresh());
            shopSave.put("lastWeeklyRefresh", eventManager.getTrophyShop().getLastWeeklyRefresh());
            jsonManager.saveShopState("trophy_shop", shopSave);

            // Gold
            GoldManager gm = eventManager.getGoldManager();
            jsonManager.saveArray("gold_state", new int[]{
                gm.getGold(), gm.getExtraExploreSlots()
            });
            jsonManager.saveStringSet("gold_claimed", gm.getClaimedRewards());

            // Daily Login streak — see DailyLoginManager's persistence section.
            DailyLoginManager dlm = eventManager.getDailyLoginManager();
            long dailyClaimMs2 = dlm.getLastClaimMs();
            jsonManager.saveArray("daily_login_state", new int[]{
                (int)(dailyClaimMs2 >>> 32), (int)(dailyClaimMs2),
                dlm.getSavedLastClaimDay()
            });

            // Watch-ad daily budget — see AdManager's persistence section.
            jsonManager.saveArray("ad_watch_state", eventManager.getAdManager().getSaveState());

            // Player identity + trusted-clock anchor — see ServerTimeManager's
            // persistence section.
            ServerTimeManager stm = eventManager.getServerTimeManager();
            jsonManager.saveStringSet("player_identity", stm.getPlayerIdAsSet());
            jsonManager.saveArray("server_time_state", stm.getSaveState());

            // Tutorial progress — see TutorialManager's persistence javadoc.
            jsonManager.saveArray("tutorial_state", tutorialManager.getSaveState());
            jsonManager.saveStringSet("tutorial_tips_seen", tutorialManager.getTipsSeen());

            // Audio mute/volume — see SoundManager's persistence javadoc.
            // No mute UI exists yet; this just persists the state for one.
            jsonManager.saveArray("audio_settings", soundManager.getSaveState());

            // Prince Prestige — see PrestigeManager's persistence section.
            PrestigeManager pm = eventManager.getPrestigeManager();
            jsonManager.saveArray("prestige_currency", pm.getSaveState());
            jsonManager.saveRuneCrafted("prestige_upgrades", pm.getUpgradeTiers());
            jsonManager.saveStringSet("prestige_protected_items", pm.getProtectedItemIds());

            // Prince Outfits — see OutfitManager's persistence section.
            OutfitManager om = eventManager.getOutfitManager();
            jsonManager.saveStringSet("owned_outfits", om.getOwnedOutfitIds());
            jsonManager.saveStringSet("equipped_outfit", om.getEquippedOutfitIdAsSet());

            Gdx.app.log(TAG, "Game saved successfully");
        } catch (Exception e) {
            Gdx.app.error(TAG, "Failed to save game", e);
        }
    }
}
