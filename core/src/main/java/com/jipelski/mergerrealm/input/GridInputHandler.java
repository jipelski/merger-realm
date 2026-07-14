package com.jipelski.mergerrealm.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;

import com.jipelski.mergerrealm.grid.Cell;
import com.jipelski.mergerrealm.grid.Grid;
import com.jipelski.mergerrealm.model.GameObject;
import com.jipelski.mergerrealm.ui.BuildMenu;
import com.jipelski.mergerrealm.ui.ExplorePanel;
import com.jipelski.mergerrealm.ui.RaidPanel;
import com.jipelski.mergerrealm.ui.UnifiedShopPanel;
import com.jipelski.mergerrealm.ui.WallGate;
import com.jipelski.mergerrealm.util.EventManager;
import com.jipelski.mergerrealm.util.GameTypes;
import com.jipelski.mergerrealm.util.GridObjectManager;
import com.jipelski.mergerrealm.util.SoundManager;

import com.jipelski.mergerrealm.ui.OfflinePopup;
import com.jipelski.mergerrealm.ui.DailyLoginPopup;
import com.jipelski.mergerrealm.ui.HiddenTemplePopup;
import com.jipelski.mergerrealm.ui.SalvagePanel;
import com.jipelski.mergerrealm.ui.RefinePanel;
import com.jipelski.mergerrealm.ui.InventoryMenu;
import com.jipelski.mergerrealm.ui.TutorialOverlay;
import com.jipelski.mergerrealm.ui.PrestigePanel;
import com.jipelski.mergerrealm.ui.OutfitPanel;
import com.jipelski.mergerrealm.ui.InfoPanel;
import com.jipelski.mergerrealm.ui.AdRewardPopup;
import com.jipelski.mergerrealm.util.AdManager;

import java.util.Objects;

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
    private float infoBtnX, infoBtnY, infoBtnW, infoBtnH;

    private OfflinePopup offlinePopup;
    private DailyLoginPopup dailyLoginPopup;
    private HiddenTemplePopup hiddenTemplePopup;
    private SalvagePanel salvagePanel;
    private RefinePanel refinePanel;

    private InventoryMenu inventoryMenu;
    private float invBtnX, invBtnY, invBtnW, invBtnH;


    private ExplorePanel explorePanel;


    private WallGate wallGate;

    private UnifiedShopPanel unifiedShopPanel;
    private float goldChipX, goldChipY, goldChipW, goldChipH;

    private SoundManager soundManager;
    public void setSoundManager(SoundManager soundManager) { this.soundManager = soundManager; }

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

    public void setDailyLoginPopup(DailyLoginPopup popup) {
        this.dailyLoginPopup = popup;
    }

    public void setSalvagePanel(SalvagePanel panel) {
        this.salvagePanel = panel;
    }

    public void setRefinePanel(RefinePanel panel) {
        this.refinePanel = panel;
    }

    public void setHiddenTemplePopup(HiddenTemplePopup popup) {
        this.hiddenTemplePopup = popup;
    }

    public void setOfflinePopup(OfflinePopup popup) {
        this.offlinePopup = popup;
    }

    public void setInventoryMenu(InventoryMenu menu) {
        this.inventoryMenu = menu;
    }

    public void setInventoryButtonBounds(float x, float y, float w, float h) {
        this.invBtnX = x;
        this.invBtnY = y;
        this.invBtnW = w;
        this.invBtnH = h;
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

    public void setInfoButtonBounds(float x, float y, float w, float h) {
        this.infoBtnX = x;
        this.infoBtnY = y;
        this.infoBtnW = w;
        this.infoBtnH = h;
    }

    private boolean isInfoButtonTap(float wx, float wy) {
        return wx >= infoBtnX && wx <= infoBtnX + infoBtnW
            && wy >= infoBtnY && wy <= infoBtnY + infoBtnH;
    }

    public void setWallGate(WallGate wallGate) {
        this.wallGate = wallGate;
    }

    public void setExplorePanel(ExplorePanel panel) {
        this.explorePanel = panel;
    }

    private RaidPanel raidPanel;
    public void setRaidPanel(RaidPanel panel) { this.raidPanel = panel; }

    public void setUnifiedShopPanel(UnifiedShopPanel panel) { this.unifiedShopPanel = panel; }

    private PrestigePanel prestigePanel;
    private float prestigeBoxX, prestigeBoxY, prestigeBoxW, prestigeBoxH;
    public void setPrestigePanel(PrestigePanel panel) { this.prestigePanel = panel; }

    private OutfitPanel outfitPanel;
    public void setOutfitPanel(OutfitPanel panel) { this.outfitPanel = panel; }

    private InfoPanel infoPanel;
    public void setInfoPanel(InfoPanel panel) { this.infoPanel = panel; }

    private AdRewardPopup adRewardPopup;
    public void setAdRewardPopup(AdRewardPopup popup) { this.adRewardPopup = popup; }

    public void setPrestigeBoxBounds(float x, float y, float w, float h) {
        this.prestigeBoxX = x; this.prestigeBoxY = y; this.prestigeBoxW = w; this.prestigeBoxH = h;
    }

    private boolean isPrestigeBoxTap(float wx, float wy) {
        return wx >= prestigeBoxX && wx <= prestigeBoxX + prestigeBoxW
            && wy >= prestigeBoxY && wy <= prestigeBoxY + prestigeBoxH;
    }

    private TutorialOverlay tutorialOverlay;
    public void setTutorialOverlay(TutorialOverlay overlay) { this.tutorialOverlay = overlay; }

    public void setGoldChipBounds(float x, float y, float w, float h) {
        this.goldChipX = x; this.goldChipY = y; this.goldChipW = w; this.goldChipH = h;
    }

    private boolean isGoldChipTap(float wx, float wy) {
        return wx >= goldChipX && wx <= goldChipX + goldChipW
            && wy >= goldChipY && wy <= goldChipY + goldChipH;
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

        // Tutorial gate — checked first so a TEXT/tip card can block every
        // panel below it, exactly like OfflinePopup. Deliberately deferred
        // while the offline popup itself is visible (that popup is drawn
        // AFTER the tutorial overlay in render(), so it must also win input
        // priority — see MergerRealmGame's matching render-order comment).
        // For an INTERACTIVE step this only consumes taps on the overlay's
        // own strip/skip controls — grid taps still fall through so the
        // taught drag/tap action can actually happen.
        if (tutorialOverlay != null && (offlinePopup == null || !offlinePopup.isVisible())
            && tutorialOverlay.handleTouchDown(screenX, screenY)) {
            return true;
        }

        if (adRewardPopup != null && adRewardPopup.handleTouchDown(screenX, screenY)) {
            return true;
        }

        if (unifiedShopPanel != null && unifiedShopPanel.handleTouchDown(screenX, screenY)) {
            return true;
        }

        if (prestigePanel != null && prestigePanel.handleTouchDown(screenX, screenY)) {
            return true;
        }

        if (outfitPanel != null && outfitPanel.handleTouchDown(screenX, screenY)) {
            return true;
        }

        if (infoPanel != null && infoPanel.handleTouchDown(screenX, screenY)) {
            return true;
        }

        // Raid panel touches
        if (raidPanel != null && raidPanel.handleTouchDown(screenX, screenY)) {
            return true;
        }

        // Raid party formation — unit selection from grid
        if (raidPanel != null && raidPanel.isFormingParty()) {
            toWorldCoords(screenX, screenY);
            int[] cell = worldToCell(worldPos.x, worldPos.y);
            if (cell != null) {
                Grid grid = eventManager.getGridInstance();
                Cell gridCell = grid.getCell(cell[0], cell[1]);
                if (!gridCell.isEmpty()) {
                    String objectId = gridCell.getOccupant();
                    if (raidPanel.canSelectUnit(objectId)) {
                        raidPanel.onUnitSelected(objectId);
                    }
                }
            }
            return true;
        }

        // Explore panel touches
        if (explorePanel != null && explorePanel.handleTouchDown(screenX, screenY)) {
            return true;
        }

        // Explore unit selection (grid visible)
        if (explorePanel != null && explorePanel.isSelectingUnit()) {
            toWorldCoords(screenX, screenY);
            int[] cell = worldToCell(worldPos.x, worldPos.y);
            if (cell != null) {
                Grid grid = eventManager.getGridInstance();
                Cell gridCell = grid.getCell(cell[0], cell[1]);
                if (!gridCell.isEmpty()) {
                    String objectId = gridCell.getOccupant();
                    if (explorePanel.canSendUnit(objectId)) {
                        explorePanel.onUnitSelected(objectId);
                    }
                }
            }
            return true; // consume all touches during unit selection
        }

        // Checked ahead of inventoryMenu below so the popup wins the touch
        // while the menu stays open (browsing) underneath it — same tier as
        // the other panel-openers above (unifiedShop/prestige/outfit/raid/
        // explore), all of which also precede the inventory check.
        if (hiddenTemplePopup != null && hiddenTemplePopup.handleTouchDown(screenX, screenY)) {
            return true;
        }

        if (salvagePanel != null && salvagePanel.handleTouchDown(screenX, screenY)) {
            return true;
        }
        if (refinePanel != null && refinePanel.handleTouchDown(screenX, screenY)) {
            return true;
        }

        // ── Inventory menu touches (browsing mode) ──
        if (inventoryMenu != null && inventoryMenu.handleTouchDown(screenX, screenY)) {
            return true;
        }

        toWorldCoords(screenX, screenY);

        // Bottom bar buttons sit at y ~[0, bottomBarHeight], which BuildMenu
        // (0..MENU_HEIGHT=160) fully covers when open — must not fire underneath it.
        // Also not drawn during Inventory's own SELECTING_UNIT mode (see
        // MergerRealmGame's render branch), so it must not be hit-testable then either.
        if (inventoryMenu != null
            && (buildMenu == null || !buildMenu.isVisible())
            && !inventoryMenu.isSelectingUnit()
            && worldPos.x >= invBtnX && worldPos.x <= invBtnX + invBtnW
            && worldPos.y >= invBtnY && worldPos.y <= invBtnY + invBtnH) {
            inventoryMenu.toggle();
            if (soundManager != null) soundManager.play(SoundManager.SfxId.CLICK);
            return true;
        }

        // The actual open() happens on touchUp, not here — the chip sits in the
        // same top-right corner as the shop panel's own close button, so opening
        // immediately would let this same gesture's touchUp be misread as a tap
        // on the now-visible panel's close button, closing it right back.
        if (unifiedShopPanel != null && isGoldChipTap(worldPos.x, worldPos.y)) {
            return true;
        }

        // ── Unit selection for inventory item ──
        if (inventoryMenu != null && inventoryMenu.isSelectingUnit()) {
            int[] cell = worldToCell(worldPos.x, worldPos.y);
            if (cell != null) {
                Grid grid = eventManager.getGridInstance();
                Cell gridCell = grid.getCell(cell[0], cell[1]);
                if (!gridCell.isEmpty()) {
                    String objectId = gridCell.getOccupant();
                    if (inventoryMenu.canApplyToUnit(objectId)) {
                        inventoryMenu.onUnitSelected(objectId);
                    }
                }
            }
            return true; // consume all touches during unit selection
        }

        if (offlinePopup != null && offlinePopup.isVisible()) {
            offlinePopup.handleTouch(worldPos.x, worldPos.y);
            return true;
        }

        // Checked AFTER offlinePopup (not alongside the unifiedShopPanel/
        // prestigePanel/outfitPanel tier above) — this is the one panel that
        // can genuinely be visible at the same time as the offline popup
        // (both may auto-open back-to-back on launch/resume), and the
        // offline popup must win the tap when that happens.
        if (dailyLoginPopup != null && dailyLoginPopup.handleTouchDown(screenX, screenY)) {
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
            clearSelection();
            if (soundManager != null) soundManager.play(SoundManager.SfxId.CLICK);
            return true;
        }

        // The actual action fires on touchUp, not here — WallGate's Raid button
        // opens RaidPanel, whose chapter-select rows sit in the same Y-band as
        // the button itself. Opening on touchDown would let this same gesture's
        // touchUp be misread as a tap on whichever chapter row aligns with the
        // button's Y (RaidPanel's row hit-test is Y-only, by design — rows are
        // meant to span the full menu width), skipping chapter-select entirely.
        if (wallGate != null && wallGate.isInWallArea(worldPos.y)) {
            return true;
        }

        if (selectedObjectId != null && isLockButtonTap(worldPos.x, worldPos.y)) {
            eventManager.getGRID_OBJECT_MANAGER().toggleLock(selectedObjectId);
            //Gdx.app.log(TAG, "Lock toggled for: " + selectedObjectId);
            return true;
        }

        // The actual open() fires on touchUp, not here — this button opens
        // InfoPanel, a modal with its own close-zone, so it must follow the
        // gold-chip/prestige-box convention (not the lock button's — the
        // lock button doesn't open a panel, so the touchUp-misread-as-a-
        // tap-on-the-new-panel bug class doesn't apply to it).
        if (selectedObjectId != null && isInfoButtonTap(worldPos.x, worldPos.y)) {
            return true;
        }

        // Normal grid input from here
        int[] cell = worldToCell(worldPos.x, worldPos.y);
        if (cell == null) {
            // TODO: remove selection
            clearSelection();
            return false;
        }

        Grid grid = eventManager.getGridInstance();
        Cell gridCell = grid.getCell(cell[0], cell[1]);

        if (gridCell.isEmpty()) {
            // TODO: remove selection
            clearSelection();
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
        if (adRewardPopup != null && adRewardPopup.handleTouchDragged(screenX, screenY)) {
            return true;
        }

        if (unifiedShopPanel != null && unifiedShopPanel.handleTouchDragged(screenX, screenY)) {
            return true;
        }

        if (prestigePanel != null && prestigePanel.handleTouchDragged(screenX, screenY)) {
            return true;
        }

        if (outfitPanel != null && outfitPanel.handleTouchDragged(screenX, screenY)) {
            return true;
        }

        if (infoPanel != null && infoPanel.handleTouchDragged(screenX, screenY)) {
            return true;
        }

        if (dailyLoginPopup != null && dailyLoginPopup.handleTouchDragged(screenX, screenY)) {
            return true;
        }

        if (raidPanel != null && raidPanel.handleTouchDragged(screenX, screenY)) {
            return true;
        }

        if (explorePanel != null && explorePanel.handleTouchDragged(screenX, screenY)) {
            return true;
        }

        if (hiddenTemplePopup != null && hiddenTemplePopup.handleTouchDragged(screenX, screenY)) {
            return true;
        }

        if (salvagePanel != null && salvagePanel.handleTouchDragged(screenX, screenY)) {
            return true;
        }
        if (refinePanel != null && refinePanel.handleTouchDragged(screenX, screenY)) {
            return true;
        }

        if (inventoryMenu != null && inventoryMenu.handleTouchDragged(screenX, screenY)) {
            return true;
        }
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
        // Mirrors the touchDown gate above — see its comment for why this
        // runs first and defers to a visible offline popup.
        if (tutorialOverlay != null && (offlinePopup == null || !offlinePopup.isVisible())
            && tutorialOverlay.handleTouchUp(screenX, screenY)) {
            return true;
        }

        if (adRewardPopup != null && adRewardPopup.handleTouchUp(screenX, screenY)) {
            return true;
        }

        if (unifiedShopPanel != null && unifiedShopPanel.handleTouchUp(screenX, screenY)) {
            return true;
        }

        if (prestigePanel != null && prestigePanel.handleTouchUp(screenX, screenY)) {
            return true;
        }

        if (outfitPanel != null && outfitPanel.handleTouchUp(screenX, screenY)) {
            return true;
        }

        if (infoPanel != null && infoPanel.handleTouchUp(screenX, screenY)) {
            return true;
        }

        if (dailyLoginPopup != null && dailyLoginPopup.handleTouchUp(screenX, screenY)) {
            return true;
        }

        if (raidPanel != null && raidPanel.handleTouchUp(screenX, screenY)) {
            return true;
        }

        if (explorePanel != null && explorePanel.handleTouchUp(screenX, screenY)) {
            return true;
        }
        if (hiddenTemplePopup != null && hiddenTemplePopup.handleTouchUp(screenX, screenY)) {
            return true;
        }

        if (salvagePanel != null && salvagePanel.handleTouchUp(screenX, screenY)) {
            return true;
        }
        if (refinePanel != null && refinePanel.handleTouchUp(screenX, screenY)) {
            return true;
        }
        if (inventoryMenu != null && inventoryMenu.handleTouchUp(screenX, screenY)) {
            return true;
        }
        if (pointer != 0) return false;

        toWorldCoords(screenX, screenY);

        // A drag that reaches touchUp must always finalize as a drop and fully
        // reset drag state HERE, before the location-based button branches below
        // (gold chip, prestige box, wall gate, build menu). Those early-return, and
        // if the finger lifts over one of their zones mid-drag they'd skip the reset
        // at the bottom of this method — leaving `dragging`/`draggedObjectId` set so
        // the sprite stays frozen at the drop point until the next grid tap. A
        // genuine drag-release is never a button tap, so bypassing them is correct.
        if (dragging) {
            stopHolding();
            handleDrop();
            resetDragState();
            return true;
        }

        // Open the unified shop here (not on touchDown — see touchDown for
        // why). unifiedShopPanel.handleTouchUp() above already returned for
        // the visible case, so reaching here means it's still closed.
        // Guard against the three "pick a unit from the grid" header modes —
        // their compact headers cover the top of the screen (where the chip
        // sits) and deliberately return false here so grid taps fall through;
        // without this guard that fallthrough pops the shop open over them.
        boolean pickingUnitFromGrid =
            (inventoryMenu != null && inventoryMenu.isSelectingUnit())
            || (explorePanel != null && explorePanel.isSelectingUnit())
            || (raidPanel != null && raidPanel.isFormingParty());
        if (unifiedShopPanel != null && !pickingUnitFromGrid
            && isGoldChipTap(worldPos.x, worldPos.y)) {
            unifiedShopPanel.open(UnifiedShopPanel.TAB_GOLD);
            clearSelection();
            if (soundManager != null) soundManager.play(SoundManager.SfxId.CLICK);
            return true;
        }

        // Open Prince Prestige here (not on touchDown — same reasoning as the
        // gold chip above: the level box sits in the top-left corner, clear
        // of any panel's own close button, but the touchUp-not-touchDown
        // convention is kept consistent with every other panel-opening
        // button in this file).
        if (prestigePanel != null && !pickingUnitFromGrid
            && isPrestigeBoxTap(worldPos.x, worldPos.y)) {
            prestigePanel.open();
            clearSelection();
            if (soundManager != null) soundManager.play(SoundManager.SfxId.CLICK);
            return true;
        }

        // Open InfoPanel here (not on touchDown — see touchDown for why).
        // Deliberately does NOT call clearSelection(), unlike the gold-chip/
        // prestige-box openers above — the whole point is showing info
        // about the object the player still has selected, so closing this
        // panel must leave the description box (and grid highlight) intact.
        if (infoPanel != null && selectedObjectId != null && !pickingUnitFromGrid
            && isInfoButtonTap(worldPos.x, worldPos.y)) {
            GameObject selObj = eventManager.getGRID_OBJECT_MANAGER().getObject(selectedObjectId);
            if (selObj != null) {
                infoPanel.open(selObj.getType(), selObj.getLvl());
                if (soundManager != null) soundManager.play(SoundManager.SfxId.CLICK);
            }
            return true;
        }

        // Open the wall-gate action here (not on touchDown — see touchDown for
        // why). raidPanel/explorePanel.handleTouchUp() above already returned
        // for the visible case, so reaching here means nothing is open yet.
        if (wallGate != null && wallGate.isInWallArea(worldPos.y)) {
            wallGate.handleTouch(worldPos.x, worldPos.y);
            clearSelection();
            return true;
        }

        // Forward to build menu
        if (buildMenu != null && buildMenu.isVisible()) {
            if (buildMenu.touchUp(worldPos.x, worldPos.y)) {
                return true;
            }
        }

        if (!touching) return false;

        stopHolding();
        handleTap();
        resetDragState();
        return true;
    }

    // ── Tap handling ──

    private void handleTap() {
        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();
        GameObject obj = gom.getObject(draggedObjectId);
        if (obj == null) return;

        if (isFacility(obj.getType())) {
            handleFacilityTap();
        } else if (isChest(obj.getType())) {
            handleChestTap();
        } else if ("prince".equals(obj.getType())) {
            handlePrinceTap();
        }
        else {
            // Non-facility: normal tap (token collect, chest open, etc.)
            eventManager.tap(draggedObjectId);
        }
    }

    /**
     * Second tap on the already-selected Prince opens the Outfit panel —
     * mirrors handleFacilityTap()'s "select, then tap again to act"
     * convention. Tapping the Prince today otherwise does nothing beyond
     * selecting him (EventManager.tap() has no "prince" case).
     */
    private void handlePrinceTap() {
        if (wasAlreadySelected && outfitPanel != null) {
            outfitPanel.open();
        }
    }

    private void handleFacilityTap() {
        GameObject facilityObj = eventManager.getGRID_OBJECT_MANAGER().getObject(draggedObjectId);
        if (facilityObj != null) facilityObj.triggerPulse();

        if (wasAlreadySelected) {
            // Second tap on same facility — spawn a unit
            if (facilityObj != null && eventManager.isPeriodicFacility(facilityObj.getType())) {
                // Periodic facility — release a held unit, or if there's
                // nothing held, offer the AdRewardPopup (Watch Ad or pay
                // Gold — see AdManager.AdAction.INSTANT_SPAWN) instead of
                // silently spending Gold.
                if (!eventManager.releaseHeldUnit(draggedObjectId)) {
                    Gdx.app.log(TAG, "No held units to release (or no space)");
                    if (adRewardPopup != null) {
                        adRewardPopup.open(AdManager.AdAction.INSTANT_SPAWN, true, -1, draggedObjectId);
                    }
                }
            } else {
                // Normal facility — spawn a unit (costs resources) // TODO: implement gold when resources are not enough for spawning an unit
                eventManager.spawnFromFacility(draggedObjectId);
            }
            Gdx.app.log(TAG, "Spawning from selected facility: " + draggedObjectId);
        } else {
            // First tap — just selected (already done in touchDown)
            Gdx.app.log(TAG, "Selected facility at [" + selectedCellX + "," + selectedCellY + "]");
        }
    }

    private void handleChestTap() {
        GameObject chestObj = eventManager.getGRID_OBJECT_MANAGER().getObject(draggedObjectId);
        if (chestObj != null) chestObj.triggerPulse();

        if (wasAlreadySelected) {
            eventManager.tap(draggedObjectId);
        } else {
            Gdx.app.log(TAG, "Selected chest at [" + selectedCellX + "," + selectedCellY + "]");
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
            if (eventManager.getGRID_OBJECT_MANAGER().isLocked(targetId) && !Objects.equals(eventManager.getGRID_OBJECT_MANAGER().getObject(targetId).getType(), "prince")) {
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

    private void resetDragState() {
        touching = false;
        dragging = false;
        originCellX = -1;
        originCellY = -1;
        draggedObjectId = null;
    }

    /*private boolean isFacility(String type) {
        switch (type) {
            case "homestead": case "lodge": case "tavernboard":
            case "barracks": case "archeryrange": case "forge":
            case "monastery": case "griffinnest": case "dragonslair":
                return true;
            default:
                return false;
        }
    }

    private boolean isChest(String type) {
        switch (type) {
            case "nail_chest": case "slate_chest": case "ingot_chest": case "relic_chest":
                return true;
            default:
                return false;
        }
    }*/

    private boolean isFacility(String type) { return GameTypes.isFacility(type); }
    private boolean isChest(String type)    { return GameTypes.isChest(type); }

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
