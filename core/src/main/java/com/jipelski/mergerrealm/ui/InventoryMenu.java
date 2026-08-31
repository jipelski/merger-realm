package com.jipelski.mergerrealm.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;

import com.jipelski.mergerrealm.model.GameObject;
import com.jipelski.mergerrealm.model.Item;
import com.jipelski.mergerrealm.model.Unit;
import com.jipelski.mergerrealm.util.EnchantedSetManager;
import com.jipelski.mergerrealm.util.EventManager;
import com.jipelski.mergerrealm.util.GridObjectManager;
import com.jipelski.mergerrealm.util.Inventory;
import com.jipelski.mergerrealm.util.LegendaryEvolution;
import com.jipelski.mergerrealm.util.RuneSystem;
import com.jipelski.mergerrealm.util.SpriteManager;
import com.jipelski.mergerrealm.util.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Inventory menu overlay.
 *
 * Two modes:
 *   BROWSING       — full-screen overlay showing item grid + tabs + equipped list
 *   SELECTING_UNIT — compact header at top, grid visible below with tint overlays
 *
 * Tabs:
 *   0 = Equipment (swords, shields, amulets)
 *   1 = Consumables (potions, phoenix feathers)
 *   2 = Fragments (rune fragments — placeholder for now)
 *
 * Flow:
 *   Open menu → browse items → tap item → menu shrinks, grid shows with tints
 *   → tap eligible unit on grid → item equipped/used → menu reopens
 */
public class InventoryMenu {

    private static final String TAG = "InventoryMenu";

    // ── States ──
    public enum State { CLOSED, BROWSING, SELECTING_UNIT }
    private State state = State.CLOSED;

    // ── Tabs ──
    private static final int TAB_EQUIPMENT = 0;
    private static final int TAB_CONSUMABLES = 1;
    private static final int TAB_FRAGMENTS = 2;
    private int activeTab = TAB_EQUIPMENT;

    // ── Layout constants ──
    private static final float MARGIN = 16f;
    private static final float HEADER_HEIGHT = 36f;
    private static final float TAB_HEIGHT = 32f;
    private static final float ITEM_SIZE = 72f;
    private static final float ITEM_GAP = 8f;
    private static final int   ITEMS_PER_ROW = 5;
    private static final float EQUIPPED_ROW_HEIGHT = 28f;
    private static final float SELECT_HEADER_HEIGHT = 80f;

    // ── Grid tint / rune palette (shared instances — drawTintsAtY only reads
    // the returned color via batch.setColor, never mutates it, so a `new
    // Color` per cell per frame is pure avoidable garbage) ──
    private static final Color TINT_NON_UNIT      = new Color(0.15f, 0.15f, 0.15f, 0.6f);
    private static final Color TINT_INELIGIBLE    = new Color(0.3f, 0.3f, 0.3f, 0.5f);
    private static final Color TINT_ASCENSION     = new Color(1f, 0.85f, 0.2f, 0.4f);
    private static final Color TINT_GREEN_ELIGIBLE = new Color(0.2f, 0.85f, 0.2f, 0.35f);
    private static final Color TINT_BLUE_HAS_ITEM  = new Color(0.3f, 0.5f, 0.9f, 0.35f);

    private static final Color RUNE_COLOR_MIGHT     = new Color(0.9f, 0.3f, 0.3f, 1f);
    private static final Color RUNE_COLOR_VITALITY  = new Color(0.3f, 0.9f, 0.3f, 1f);
    private static final Color RUNE_COLOR_FORTUNE   = new Color(0.9f, 0.85f, 0.2f, 1f);
    private static final Color RUNE_COLOR_SWIFTNESS = new Color(0.3f, 0.7f, 0.9f, 1f);

    // Same RGB as the RUNE_COLOR_* constants above at the tint alpha (0.4) —
    // precomputed rather than blended per-frame in getUnitTintColor.
    private static final Color TINT_RUNE_MIGHT     = new Color(0.9f, 0.3f, 0.3f, 0.4f);
    private static final Color TINT_RUNE_VITALITY  = new Color(0.3f, 0.9f, 0.3f, 0.4f);
    private static final Color TINT_RUNE_FORTUNE   = new Color(0.9f, 0.85f, 0.2f, 0.4f);
    private static final Color TINT_RUNE_SWIFTNESS = new Color(0.3f, 0.7f, 0.9f, 0.4f);
    private static final Color TINT_RUNE_DEFAULT   = new Color(1f, 1f, 1f, 0.4f);

    // ── Hold-to-preview ──
    private static final float HOLD_PREVIEW_DELAY = 0.4f;
    private float holdTimer = 0f;
    private boolean holding = false;
    private int holdIndex = -1;
    private boolean showingPreview = false;

    // ── Scroll ──
    private float scrollY = 0f;
    private float maxScrollY = 0f;

    // ── Selected item (for SELECTING_UNIT mode) ──
    private Item selectedItem = null;

    // ── Stale-cache detection — filteredItems is a cached filter over
    // Inventory.getItems(), but RefinePanel/SalvagePanel/HiddenTemplePopup
    // mutate Inventory directly while this menu stays open (BROWSING)
    // underneath them, with no callback back into it. Checked once per frame
    // in drawBackground() — see checkInventoryStale(). ──
    private int lastSeenInventoryVersion = -1;

    // ── References ──
    private final EventManager eventManager;
    private final SpriteManager spriteManager;
    private final Viewport viewport;
    private final UITextureManager uiTex;
    private final GlyphLayout glyphLayout;

    private HiddenTemplePopup hiddenTemplePopup;
    public void setHiddenTemplePopup(HiddenTemplePopup popup) { this.hiddenTemplePopup = popup; }

    private SalvagePanel salvagePanel;
    public void setSalvagePanel(SalvagePanel panel) { this.salvagePanel = panel; }

    private RefinePanel refinePanel;
    public void setRefinePanel(RefinePanel panel) { this.refinePanel = panel; }

    // ── Touch state ──
    private final Vector2 touchPos = new Vector2();
    private boolean touchDown = false;
    private float touchStartY = 0f;
    private boolean scrolling = false;

    // ── Cached filtered items ──
    private final List<Item> filteredItems = new ArrayList<>();

    private String selectedRuneType = null;

    public InventoryMenu(EventManager eventManager, SpriteManager spriteManager,
                         Viewport viewport, UITextureManager uiTex) {
        this.eventManager = eventManager;
        this.spriteManager = spriteManager;
        this.viewport = viewport;
        this.uiTex = uiTex;
        this.glyphLayout = new GlyphLayout();
    }

    // ══════════════════════════════════════════════════════════════
    // STATE MANAGEMENT
    // ══════════════════════════════════════════════════════════════

    public State getState() { return state; }
    public boolean isVisible() { return state != State.CLOSED; }
    public boolean isBrowsing() { return state == State.BROWSING; }
    public boolean isSelectingUnit() { return state == State.SELECTING_UNIT; }
    public Item getSelectedItem() { return selectedItem; }

    public void open() {
        state = State.BROWSING;
        activeTab = TAB_EQUIPMENT;
        selectedItem = null;
        selectedRuneType = null;
        scrollY = 0f;
        showingPreview = false;
        refreshFilteredItems();
        lastSeenInventoryVersion = eventManager.getInventory().getVersion();
    }

    public void close() {
        state = State.CLOSED;
        selectedItem = null;
        selectedRuneType = null;
        showingPreview = false;
        holding = false;
    }

    public void toggle() {
        if (state == State.CLOSED) open();
        else close();
    }

    /**
     * Called when a unit is tapped on the grid during SELECTING_UNIT mode.
     * Applies the selected item to the unit.
     * Returns true if the item was successfully applied.
     */
    public boolean onUnitSelected(String unitId) {
        // A rune selection has no Item (selectedItem stays null the whole time —
        // see handleFragmentTabTouch), so it must be accepted here too, not just
        // an item selection.
        if (state != State.SELECTING_UNIT
            || (selectedItem == null && selectedRuneType == null)) return false;

        boolean success;
        // Rune application
        if (selectedRuneType != null) {
            RuneSystem rs = eventManager.getRuneSystem();

            // Fortune boosts resource-gen rate, which ResourceManager maintains as
            // a running accumulator (ticked every 15s) rather than recomputing it
            // live — so applying the rune must nudge that accumulator by exactly
            // the delta the new application count adds, or the boost has no effect
            // until the unit is re-spawned.
            Unit fortuneUnit = null;
            int genRateBefore = 0;
            if (RuneSystem.FORTUNE.equals(selectedRuneType)) {
                GameObject obj = eventManager.getGRID_OBJECT_MANAGER().getObject(unitId);
                if (obj instanceof Unit) {
                    fortuneUnit = (Unit) obj;
                    genRateBefore = rs.getBoostedGenRate(unitId, fortuneUnit.getGen_rate());
                }
            }

            boolean applied = rs.applyRune(unitId, selectedRuneType);
            if (applied) {
                if (fortuneUnit != null) {
                    int genRateAfter = rs.getBoostedGenRate(unitId, fortuneUnit.getGen_rate());
                    eventManager.getResourceManager()
                        .addRate(fortuneUnit.getResource(), genRateAfter - genRateBefore);
                }
                Gdx.app.log(TAG, "Rune of " + selectedRuneType + " applied to " + unitId);
                selectedRuneType = null;
                state = State.BROWSING;
                refreshFilteredItems();
                return true;
            }
            return false;
        }
        if (selectedItem.isConsumable() && "potion".equals(selectedItem.getType())) {
            success = eventManager.usePotionOnUnit(unitId, selectedItem.getId());
        } else if (selectedItem.isConsumable()
            && "amulet_of_ascension".equals(selectedItem.getType())) {
            // Amulet of Ascension — evolve to legendary
            success = eventManager.evolveUnit(unitId, selectedItem.getId());

        } else if (!selectedItem.isConsumable()) {
            eventManager.equipItem(unitId, selectedItem.getId());
            success = true;
        } else {
            // Phoenix feather can't be used on grid units directly
            success = false;
        }

        if (success) {
            selectedItem = null;
            state = State.BROWSING;
            refreshFilteredItems();
            Gdx.app.log(TAG, "Item applied to unit " + unitId + " — returning to browse");
        }
        return success;
    }

    /**
     * Called every frame for hold-to-preview timing.
     */
    public void update(float delta) {
        if (holding && !showingPreview) {
            holdTimer += delta;
            if (holdTimer >= HOLD_PREVIEW_DELAY) {
                showingPreview = true;
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // FILTERING
    // ══════════════════════════════════════════════════════════════

    private void refreshFilteredItems() {
        filteredItems.clear();
        Inventory inv = eventManager.getInventory();
        if (inv == null) return;

        for (Item item : inv.getItems()) {
            if (matchesTab(item)) {
                filteredItems.add(item);
            }
        }
        recalculateScroll();
    }

    private boolean matchesTab(Item item) {
        switch (activeTab) {
            case TAB_EQUIPMENT:
                return "sword".equals(item.getType())
                    || "shield".equals(item.getType())
                    || "amulet".equals(item.getType())
                    || EnchantedSetManager.isEnchantedItem(item.getType());
            case TAB_CONSUMABLES:
                return "potion".equals(item.getType())
                    || "phoenix_feather".equals(item.getType())
                    || "amulet_of_ascension".equals(item.getType())
                    || "ancient_map".equals(item.getType());
            case TAB_FRAGMENTS:
                return item.getType() != null && item.getType().startsWith("rune_fragment_");
            default:
                return false;
        }
    }

    private void recalculateScroll() {
        int rowCount = (int) Math.ceil((float) filteredItems.size() / ITEMS_PER_ROW);
        float itemsHeight = rowCount * (ITEM_SIZE + ITEM_GAP);
        float availableHeight = getItemAreaHeight();
        maxScrollY = Math.max(0, itemsHeight - availableHeight);
        scrollY = Math.min(scrollY, maxScrollY);
    }

    // ══════════════════════════════════════════════════════════════
    // LAYOUT HELPERS
    // ══════════════════════════════════════════════════════════════

    private float getWorldWidth() { return LayoutConfig.WORLD_WIDTH; }
    private float getWorldHeight() { return viewport.getWorldHeight(); }

    private float getMenuX() { return MARGIN; }
    private float getMenuWidth() { return getWorldWidth() - MARGIN * 2; }
    private float getMenuTop() { return getWorldHeight() - MARGIN; }
    private float getMenuBottom() { return MARGIN; }
    private float getMenuHeight() { return getMenuTop() - getMenuBottom(); }

    private float getTabY() { return getMenuTop() - HEADER_HEIGHT - TAB_HEIGHT; }
    private float getItemAreaTop() { return getTabY(); }
    private float getEquippedSectionHeight() {
        Inventory inv = eventManager.getInventory();
        int equippedCount = (inv != null) ? inv.getEquipped().size() : 0;
        return Math.max(60f, 28f + equippedCount * EQUIPPED_ROW_HEIGHT);
    }
    private float getItemAreaBottom() { return getMenuBottom() + getEquippedSectionHeight(); }
    private float getItemAreaHeight() { return getItemAreaTop() - getItemAreaBottom(); }

    private float getItemGridStartX() {
        float totalWidth = ITEMS_PER_ROW * ITEM_SIZE + (ITEMS_PER_ROW - 1) * ITEM_GAP;
        return getMenuX() + (getMenuWidth() - totalWidth) / 2f;
    }

    // ══════════════════════════════════════════════════════════════
    // GRID TINT LOGIC (used by MergerRealmGame in SELECTING_UNIT)
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns true if the given unit can receive the currently selected item.
     * This is the single source of truth for eligibility — game state only,
     * never derived from a tint color. {@link #getUnitTintColor} derives its
     * color FROM this predicate, not the other way around.
     */
    public boolean isEligibleTarget(String objectId) {
        // A rune selection has no Item (selectedItem stays null — see
        // handleFragmentTabTouch), so it must be accepted here too.
        if (state != State.SELECTING_UNIT
            || (selectedItem == null && selectedRuneType == null)) return false;

        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();
        GameObject obj = gom.getObject(objectId);
        if (obj == null) return false;

        // Non-units can't receive items
        if (!(obj instanceof Unit)) return false;

        Unit unit = (Unit) obj;

        // Pre-units (hp=0) or dead units can't equip
        if (unit.getMax_hp() <= 0 || !unit.isAlive()) return false;

        // Amulet of Ascension: only max-level, evolvable, non-legendary units
        if (selectedItem != null && selectedItem.isConsumable()
            && "amulet_of_ascension".equals(selectedItem.getType())) {
            if (unit.getLvl() < unit.getMaxLVL()) return false; // not max level
            if (LegendaryEvolution.isLegendary(obj.getType())) return false; // already evolved
            return LegendaryEvolution.canEvolve(obj.getType()); // must have a path
        }

        // Rune application: rune system's own cap check
        if (selectedRuneType != null) {
            RuneSystem rs = eventManager.getRuneSystem();
            return rs.canApplyRune(objectId, selectedRuneType);
        }

        // Potions: only wounded units
        if (selectedItem.isConsumable() && "potion".equals(selectedItem.getType())) {
            return unit.isWounded();
        }

        // Equipment: always eligible (no item vs replacing is visual-only)
        return true;
    }

    /**
     * Returns the tint color for a grid cell's object during unit selection.
     *   GREEN  — eligible, no item equipped
     *   BLUE   — eligible, already has item (will replace)
     *   GOLD   — eligible, can evolve (Amulet of Ascension)
     *   GREY   — cannot equip (non-unit, pre-unit, dead, etc.)
     *   null   — state is not SELECTING_UNIT
     */
    public Color getUnitTintColor(String objectId) {
        // A rune selection has no Item (selectedItem stays null — see
        // handleFragmentTabTouch), so it must be accepted here too.
        if (state != State.SELECTING_UNIT
            || (selectedItem == null && selectedRuneType == null)) return null;

        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();
        GameObject obj = gom.getObject(objectId);
        if (obj == null) return null;

        // Non-units get dark tint
        if (!(obj instanceof Unit)) {
            return TINT_NON_UNIT;
        }

        // Ineligible — grey, regardless of item type or reason
        if (!isEligibleTarget(objectId)) {
            return TINT_INELIGIBLE;
        }

        // Eligible — color matches the action
        if (selectedItem != null && selectedItem.isConsumable()
            && "amulet_of_ascension".equals(selectedItem.getType())) {
            return TINT_ASCENSION; // gold tint — can evolve!
        }

        if (selectedRuneType != null) {
            return getRuneTintColor(selectedRuneType);
        }

        // Potions: only wounded units (already confirmed eligible above)
        if (selectedItem.isConsumable() && "potion".equals(selectedItem.getType())) {
            return TINT_GREEN_ELIGIBLE; // green
        }

        // Equipment: green if no item, blue if replacing
        Inventory inv = eventManager.getInventory();
        if (inv.hasEquippedItem(objectId)) {
            return TINT_BLUE_HAS_ITEM; // blue — has item
        }
        return TINT_GREEN_ELIGIBLE; // green — available
    }

    /**
     * Returns true if the given unit can receive the currently selected item.
     */
    public boolean canApplyToUnit(String objectId) {
        return isEligibleTarget(objectId);
    }

    // ══════════════════════════════════════════════════════════════
    // DRAWING — BROWSING MODE
    // ══════════════════════════════════════════════════════════════

    /**
     * Draws the menu background shapes. Call OUTSIDE batch.begin/end.
     */
    public void drawBackground(ShapeRenderer sr) {
        if (state == State.BROWSING) {
            checkInventoryStale();
            drawBrowsingBackground(sr);
        } else if (state == State.SELECTING_UNIT) {
            drawSelectingBackground(sr);
        }
    }

    /**
     * Detects "Inventory changed since I last filtered it" via a cheap int
     * compare, and only re-filters when it actually has — see the
     * lastSeenInventoryVersion field javadoc for why this exists.
     * Called once per frame (from drawBackground, guaranteed to run before
     * both the ShapeRenderer and SpriteBatch passes read filteredItems) while
     * BROWSING, so a panel like Refine/Salvage/Hidden Temple that mutates
     * Inventory while this menu sits open underneath it self-heals the very
     * next frame — no callback wiring needed per panel.
     */
    private void checkInventoryStale() {
        int v = eventManager.getInventory().getVersion();
        if (v != lastSeenInventoryVersion) {
            lastSeenInventoryVersion = v;
            refreshFilteredItems();
        }
    }

    /**
     * Draws the menu content (text + sprites). Call INSIDE batch.begin/end.
     */
    public void drawContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        if (state == State.BROWSING) {
            drawBrowsingContent(batch, font, fontSmall);
        } else if (state == State.SELECTING_UNIT) {
            drawSelectingContent(batch, font, fontSmall);
        }
    }

    private void drawBrowsingBackground(ShapeRenderer sr) {
        Gdx.gl.glEnable(Gdx.gl.GL_BLEND);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        // Dim whole screen
        sr.setColor(0f, 0f, 0f, 0.75f);
        sr.rect(0, 0, getWorldWidth(), getWorldHeight());

        // Menu panel
        sr.setColor(0.12f, 0.12f, 0.18f, 1f);
        sr.rect(getMenuX(), getMenuBottom(), getMenuWidth(), getMenuHeight());

        // Header
        sr.setColor(0.18f, 0.18f, 0.26f, 1f);
        sr.rect(getMenuX(), getMenuTop() - HEADER_HEIGHT, getMenuWidth(), HEADER_HEIGHT);

        // Tabs
        float tabWidth = getMenuWidth() / 3f;
        for (int i = 0; i < 3; i++) {
            float tabX = getMenuX() + i * tabWidth;
            if (i == activeTab) sr.setColor(0.25f, 0.25f, 0.38f, 1f);
            else                sr.setColor(0.15f, 0.15f, 0.22f, 1f);
            sr.rect(tabX, getTabY(), tabWidth, TAB_HEIGHT);
        }
        // Active tab accent
        sr.setColor(0.4f, 0.75f, 0.4f, 1f);
        sr.rect(getMenuX() + activeTab * tabWidth, getTabY(), tabWidth, 2f);

        // Item slot backgrounds
        drawItemSlotBackgrounds(sr);

        // Equipped section
        sr.setColor(0.15f, 0.15f, 0.22f, 1f);
        sr.rect(getMenuX(), getMenuBottom(), getMenuWidth(), getEquippedSectionHeight());
        sr.setColor(0.3f, 0.3f, 0.4f, 1f);
        sr.rect(getMenuX(), getMenuBottom() + getEquippedSectionHeight(),
            getMenuWidth(), 1f);

        sr.end();
        Gdx.gl.glDisable(Gdx.gl.GL_BLEND);
    }

    private void drawItemSlotBackgrounds(ShapeRenderer sr) {
        float startX = getItemGridStartX();
        float startY = getItemAreaTop() - ITEM_SIZE + scrollY;
        Inventory inv = eventManager.getInventory();

        for (int i = 0; i < filteredItems.size(); i++) {
            int col = i % ITEMS_PER_ROW;
            int row = i / ITEMS_PER_ROW;
            float ix = startX + col * (ITEM_SIZE + ITEM_GAP);
            float iy = startY - row * (ITEM_SIZE + ITEM_GAP);

            if (iy + ITEM_SIZE < getItemAreaBottom() || iy > getItemAreaTop()) continue;

            boolean equipped = inv.getEquipped().containsValue(filteredItems.get(i).getId());
            if (equipped) sr.setColor(0.2f, 0.3f, 0.45f, 1f);
            else          sr.setColor(0.2f, 0.2f, 0.3f, 1f);
            sr.rect(ix, iy, ITEM_SIZE, ITEM_SIZE);
        }
    }

    private void drawBrowsingContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        // Header
        font.setColor(Color.WHITE);
        font.draw(batch, "Inventory", getMenuX() + 12f, getMenuTop() - 10f);
        font.draw(batch, "X", getMenuX() + getMenuWidth() - 28f, getMenuTop() - 10f);

        // Equipment sink entry point — only shown/tappable on the Equipment
        // tab. Sits in the header's otherwise-empty middle ground, clear of
        // the close button's own x > getMenuWidth()-40f hit-zone (see
        // handleTouchUp) and deliberately NOT at the tab-row's "N items"
        // position, which sits inside the full-width tab-switch hit-band.
        if (activeTab == TAB_EQUIPMENT) {
            fontSmall.setColor(0.85f, 0.8f, 0.5f, 1f);
            String salvageLabel = "[Salvage]";
            glyphLayout.setText(fontSmall, salvageLabel);
            fontSmall.draw(batch, salvageLabel,
                getMenuX() + getMenuWidth() - 44f - glyphLayout.width,
                getMenuTop() - 10f);

            // Refine button — sits left of Salvage with a fixed gap; see
            // handleTouchUp's dedicated [-240f,-144f] hit-zone (Salvage's own
            // zone is [-140f,-44f], so the two never overlap).
            fontSmall.setColor(0.7f, 0.5f, 0.9f, 1f);
            String refineLabel = "[Refine]";
            glyphLayout.setText(fontSmall, refineLabel);
            fontSmall.draw(batch, refineLabel,
                getMenuX() + getMenuWidth() - 148f - glyphLayout.width,
                getMenuTop() - 10f);

            fontSmall.setColor(Color.WHITE);
        }

        // Tab labels
        String[] tabLabels = {"Equipment", "Consume", "Fragments"};
        float tabWidth = getMenuWidth() / 3f;
        for (int i = 0; i < 3; i++) {
            float tabCenterX = getMenuX() + i * tabWidth + tabWidth / 2f;
            if (i == activeTab) fontSmall.setColor(1f, 1f, 1f, 1f);
            else                fontSmall.setColor(0.5f, 0.5f, 0.6f, 1f);
            glyphLayout.setText(fontSmall, tabLabels[i]);
            fontSmall.draw(batch, tabLabels[i],
                tabCenterX - glyphLayout.width / 2f,
                getTabY() + TAB_HEIGHT - 10f);
        }

        // Item count
        fontSmall.setColor(0.5f, 0.5f, 0.6f, 1f);
        glyphLayout.setText(fontSmall, filteredItems.size() + " items");
        fontSmall.draw(batch, filteredItems.size() + " items",
            getMenuX() + getMenuWidth() - glyphLayout.width - 12f,
            getTabY() + TAB_HEIGHT - 10f);

        // Item grid
        if (activeTab == TAB_FRAGMENTS) {
            drawFragmentsTab(batch, font, fontSmall);
        } else {
            drawItemGrid(batch, fontSmall);
        }

        // Equipped section
        drawEquippedSection(batch, fontSmall);

        // Preview card
        if (showingPreview && holdIndex >= 0 && holdIndex < filteredItems.size()) {
            drawPreviewCard(batch, font, fontSmall, filteredItems.get(holdIndex));
        }

        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    private void drawItemGrid(SpriteBatch batch, BitmapFont fontSmall) {
        if (filteredItems.isEmpty()) {
            fontSmall.setColor(0.4f, 0.4f, 0.5f, 1f);
            String empty = activeTab == TAB_FRAGMENTS
                ? "No fragments found yet" : "No items in this category";
            glyphLayout.setText(fontSmall, empty);
            fontSmall.draw(batch, empty,
                getMenuX() + getMenuWidth() / 2f - glyphLayout.width / 2f,
                getItemAreaTop() - 40f);
            return;
        }

        float startX = getItemGridStartX();
        float startY = getItemAreaTop() - ITEM_SIZE + scrollY;
        Inventory inv = eventManager.getInventory();

        for (int i = 0; i < filteredItems.size(); i++) {
            int col = i % ITEMS_PER_ROW;
            int row = i / ITEMS_PER_ROW;
            float ix = startX + col * (ITEM_SIZE + ITEM_GAP);
            float iy = startY - row * (ITEM_SIZE + ITEM_GAP);

            if (iy + ITEM_SIZE < getItemAreaBottom() || iy > getItemAreaTop()) continue;

            Item item = filteredItems.get(i);

            // Item sprite — spritePath is already a full "type_level" key (e.g.
            // "sword_3"); getTexture(type, level) would append a redundant "_1"
            // and always miss, falling back to the placeholder.
            Texture tex = spriteManager.getTextureByKey(item.getSpritePath());
            float sprMargin = 6f;
            batch.draw(tex, ix + sprMargin, iy + sprMargin + 12f,
                ITEM_SIZE - sprMargin * 2, ITEM_SIZE - sprMargin * 2 - 12f);

            // Level at bottom
            fontSmall.setColor(0.7f, 0.7f, 0.8f, 1f);
            String lvl = "Lv." + item.getLevel();
            glyphLayout.setText(fontSmall, lvl);
            fontSmall.draw(batch, lvl,
                ix + (ITEM_SIZE - glyphLayout.width) / 2f, iy + 14f);

            // "E" badge if equipped
            if (inv.getEquipped().containsValue(item.getId())) {
                fontSmall.setColor(0.4f, 0.7f, 1f, 1f);
                fontSmall.draw(batch, "E", ix + ITEM_SIZE - 14f, iy + ITEM_SIZE - 4f);
            }

            // "+N" badge if refined — opposite corner from "E" so both can
            // show at once on an equipped, refined item.
            if (item.getRefineLevel() > 0) {
                fontSmall.setColor(0.7f, 0.5f, 0.9f, 1f);
                fontSmall.draw(batch, "+" + item.getRefineLevel(), ix + 2f, iy + ITEM_SIZE - 4f);
            }
        }
    }

    private void drawEquippedSection(SpriteBatch batch, BitmapFont fontSmall) {
        float sectionTop = getMenuBottom() + getEquippedSectionHeight();
        Inventory inv = eventManager.getInventory();
        if (inv == null) return;

        fontSmall.setColor(0.6f, 0.6f, 0.7f, 1f);
        fontSmall.draw(batch, "EQUIPPED:", getMenuX() + 12f, sectionTop - 8f);

        Map<String, String> equipped = inv.getEquipped();
        if (equipped.isEmpty()) {
            fontSmall.setColor(0.4f, 0.4f, 0.5f, 1f);
            fontSmall.draw(batch, "No items equipped", getMenuX() + 12f, sectionTop - 28f);
            return;
        }

        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();
        float rowY = sectionTop - 28f;

        for (Map.Entry<String, String> entry : equipped.entrySet()) {
            GameObject unitObj = gom.getObject(entry.getKey());
            Item item = inv.getItem(entry.getValue());
            if (unitObj == null || item == null) continue;

            // Unit name
            fontSmall.setColor(0.8f, 0.8f, 0.9f, 1f);
            fontSmall.draw(batch,
                TextUtil.capitalize(unitObj.getType()) + " Lv" + unitObj.getLvl(),
                getMenuX() + 12f, rowY);

            // Item info
            fontSmall.setColor(0.85f, 0.8f, 0.5f, 1f);
            StringBuilder info = new StringBuilder(" — ").append(item.getDisplayName());
            if (item.getBonusDamage() > 0) info.append(" (+").append(item.getBonusDamage()).append("DMG)");
            if (item.getBonusHp() > 0) info.append(" (+").append(item.getBonusHp()).append("HP)");
            fontSmall.draw(batch, info.toString(), getMenuX() + 105f, rowY);

            // [X] unequip
            fontSmall.setColor(0.8f, 0.3f, 0.3f, 1f);
            fontSmall.draw(batch, "[X]", getMenuX() + getMenuWidth() - 36f, rowY);

            rowY -= EQUIPPED_ROW_HEIGHT;
        }
    }

    private void drawPreviewCard(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall, Item item) {
        float cardW = 280f;
        float cardH = EnchantedSetManager.isEnchantedItem(item.getType()) ? 150f : 110f;
        float cardX = (getWorldWidth() - cardW) / 2f;
        float cardY = (getWorldHeight() - cardH) / 2f;

        uiTex.drawPanel(batch, uiTex.panelDark, cardX, cardY, cardW, cardH);

        // Name
        font.setColor(1f, 0.9f, 0.4f, 1f);
        font.draw(batch, item.getDisplayName(), cardX + 12f, cardY + cardH - 12f);

        // Description
        fontSmall.setColor(0.7f, 0.7f, 0.8f, 1f);
        fontSmall.draw(batch, item.getDescription(), cardX + 12f, cardY + cardH - 34f,
            cardW - 24f, com.badlogic.gdx.utils.Align.left, false);

        // Stats
        fontSmall.setColor(0.85f, 0.8f, 0.5f, 1f);
        StringBuilder stats = new StringBuilder();
        if (item.getBonusDamage() > 0) stats.append("+").append(item.getBonusDamage()).append(" DMG  ");
        if (item.getBonusHp() > 0) stats.append("+").append(item.getBonusHp()).append(" HP  ");
        if (item.isConsumable()) stats.append("[Consumable]");
        fontSmall.draw(batch, stats.toString(), cardX + 12f, cardY + cardH - 56f);

        if (EnchantedSetManager.isEnchantedItem(item.getType())) {
            String setName = EnchantedSetManager.getSetName(item.getType());
            if (setName != null) {
                fontSmall.setColor(0.6f, 0.3f, 0.9f, 1f); // purple for set info
                String setDisplay = EnchantedSetManager.getSetDisplayName(setName);
                fontSmall.draw(batch, "Set: " + setDisplay, cardX + 12f, cardY + cardH - 74f);

                EnchantedSetManager esm = eventManager.getEnchantedSetManager();
                String bonusDesc = esm.getSetBonusDescription(setName);
                if (bonusDesc != null) {
                    fontSmall.setColor(0.5f, 0.3f, 0.8f, 1f);
                    fontSmall.draw(batch, "2pc: " + bonusDesc, cardX + 12f, cardY + cardH - 90f,
                        cardW - 24f, com.badlogic.gdx.utils.Align.left, true);
                }
            }
        }

        // Type
        fontSmall.setColor(0.5f, 0.5f, 0.6f, 1f);
        fontSmall.draw(batch, TextUtil.capitalize(item.getType()) + " Lv." + item.getLevel(),
            cardX + 12f, cardY + 18f);

        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    // ══════════════════════════════════════════════════════════════
    // DRAWING — SELECTING UNIT MODE
    // ══════════════════════════════════════════════════════════════

    private void drawSelectingBackground(ShapeRenderer sr) {
        sr.begin(ShapeRenderer.ShapeType.Filled);

        // Compact header
        sr.setColor(0.12f, 0.12f, 0.18f, 0.95f);
        sr.rect(0, getWorldHeight() - SELECT_HEADER_HEIGHT,
            getWorldWidth(), SELECT_HEADER_HEIGHT);

        // Accent line
        sr.setColor(0.4f, 0.75f, 0.4f, 1f);
        sr.rect(0, getWorldHeight() - SELECT_HEADER_HEIGHT, getWorldWidth(), 2f);

        sr.end();
    }

    private void drawSelectingContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        if (selectedItem == null && selectedRuneType == null) return;

        float top = getWorldHeight();

        // Back button
        fontSmall.setColor(0.6f, 0.8f, 0.6f, 1f);
        fontSmall.draw(batch, "< Back", 12f, top - 14f);

        if (selectedRuneType != null) {
            // Rune selection has no backing Item — its own header
            Color runeColor = getRuneColor(selectedRuneType);
            font.setColor(runeColor.r, runeColor.g, runeColor.b, 1f);
            font.draw(batch, "Apply: Rune of " + TextUtil.capitalize(selectedRuneType), 80f, top - 14f);

            fontSmall.setColor(0.5f, 0.5f, 0.6f, 1f);
            String hint = "Tap a unit";
            glyphLayout.setText(fontSmall, hint);
            fontSmall.draw(batch, hint, getWorldWidth() - glyphLayout.width - 12f, top - 54f);
        } else {
            // Item name
            font.setColor(1f, 0.9f, 0.4f, 1f);
            String title = (selectedItem.isConsumable() ? "Use: " : "Equip: ")
                + selectedItem.getDisplayName();
            font.draw(batch, title, 80f, top - 14f);

            // Stats
            fontSmall.setColor(0.85f, 0.8f, 0.5f, 1f);
            StringBuilder stats = new StringBuilder();
            if (selectedItem.getBonusDamage() > 0) stats.append("+").append(selectedItem.getBonusDamage()).append(" DMG  ");
            if (selectedItem.getBonusHp() > 0) stats.append("+").append(selectedItem.getBonusHp()).append(" HP  ");
            fontSmall.draw(batch, stats.toString(), 80f, top - 34f);

            // Hint
            fontSmall.setColor(0.5f, 0.5f, 0.6f, 1f);
            String hint = selectedItem.isConsumable() ? "Tap a wounded unit" : "Tap a unit to equip";
            glyphLayout.setText(fontSmall, hint);
            fontSmall.draw(batch, hint, getWorldWidth() - glyphLayout.width - 12f, top - 54f);
        }

        // Legend
        fontSmall.setColor(0.2f, 0.85f, 0.2f, 1f);
        fontSmall.draw(batch, "●", 12f, top - 68f);
        fontSmall.setColor(0.6f, 0.6f, 0.7f, 1f);
        fontSmall.draw(batch, "Available  ", 24f, top - 68f);

        fontSmall.setColor(0.3f, 0.5f, 0.9f, 1f);
        fontSmall.draw(batch, "●", 110f, top - 68f);
        fontSmall.setColor(0.6f, 0.6f, 0.7f, 1f);
        fontSmall.draw(batch, "Has item  ", 122f, top - 68f);

        fontSmall.setColor(0.4f, 0.4f, 0.4f, 1f);
        fontSmall.draw(batch, "●", 205f, top - 68f);
        fontSmall.setColor(0.6f, 0.6f, 0.7f, 1f);
        fontSmall.draw(batch, "Ineligible", 217f, top - 68f);

        fontSmall.setColor(1f, 0.85f, 0.2f, 1f);
        fontSmall.draw(batch, "●", 12f, top - 68f);  // adjust positioning
        fontSmall.setColor(0.6f, 0.6f, 0.7f, 1f);
        fontSmall.draw(batch, "Can evolve", 24f, top - 68f);

        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    private void drawFragmentsTab(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        RuneSystem rs = eventManager.getRuneSystem();
        float x = getMenuX() + 16f;
        float y = getItemAreaTop() - 20f;
        float barWidth = 120f;
        float barHeight = 10f;
        float rowHeight = 56f;

        // ── Fragment counts ──
        fontSmall.setColor(0.6f, 0.6f, 0.7f, 1f);
        fontSmall.draw(batch, "FRAGMENTS:", x, y);
        y -= 24f;

        String[] displayNames = {"Might", "Vitality", "Fortune", "Swiftness"};

        for (int i = 0; i < RuneSystem.RUNE_TYPES.length; i++) {
            String type = RuneSystem.RUNE_TYPES[i];
            int count = rs.getFragmentCount(type);
            boolean canCraft = rs.canCraft(type);

            // Name
            fontSmall.setColor(0.8f, 0.8f, 0.9f, 1f);
            fontSmall.draw(batch, displayNames[i] + ":", x, y);

            // Count
            if (canCraft) fontSmall.setColor(0.3f, 0.9f, 0.3f, 1f);
            else          fontSmall.setColor(0.7f, 0.7f, 0.8f, 1f);
            fontSmall.draw(batch, count + "/" + RuneSystem.FRAGMENTS_PER_RUNE,
                x + 90f, y);

            // Progress blocks (■■■□□)
            float blockX = x + 130f;
            float blockSize = 12f;
            float blockGap = 3f;
            for (int b = 0; b < RuneSystem.FRAGMENTS_PER_RUNE; b++) {
                if (b < count) {
                    fontSmall.setColor(getRuneColor(type));
                } else {
                    fontSmall.setColor(0.3f, 0.3f, 0.35f, 1f);
                }
                fontSmall.draw(batch, "■", blockX + b * (blockSize + blockGap), y);
            }

            // [Craft] button
            if (canCraft) {
                fontSmall.setColor(0.3f, 0.9f, 0.3f, 1f);
                fontSmall.draw(batch, "[Craft]",
                    getMenuX() + getMenuWidth() - 60f, y);
            }

            y -= rowHeight;
        }

        // ── Crafted runes ──
        y -= 10f;
        fontSmall.setColor(0.6f, 0.6f, 0.7f, 1f);
        fontSmall.draw(batch, "CRAFTED RUNES:", x, y);
        y -= 24f;

        boolean hasCrafted = false;
        for (int i = 0; i < RuneSystem.RUNE_TYPES.length; i++) {
            String type = RuneSystem.RUNE_TYPES[i];
            int crafted = rs.getCraftedCount(type);
            if (crafted <= 0) continue;

            hasCrafted = true;

            fontSmall.setColor(getRuneColor(type));
            String boostText;
            if (RuneSystem.SWIFTNESS.equals(type)) {
                boostText = "+" + (int)(RuneSystem.BOOST_SWIFTNESS * 100) + "% explore speed";
            } else {
                String stat = RuneSystem.MIGHT.equals(type) ? "damage"
                    : RuneSystem.VITALITY.equals(type) ? "HP" : "resource gen";
                boostText = "+" + (int)(RuneSystem.BOOST_STANDARD * 100) + "% " + stat;
            }

            fontSmall.draw(batch, "Rune of " + displayNames[i] + " x" + crafted
                + "  (" + boostText + ")", x, y);

            // [Apply] button
            fontSmall.setColor(0.85f, 0.8f, 0.5f, 1f);
            fontSmall.draw(batch, "[Apply]",
                getMenuX() + getMenuWidth() - 60f, y);

            y -= 28f;
        }

        if (!hasCrafted) {
            fontSmall.setColor(0.4f, 0.4f, 0.5f, 1f);
            fontSmall.draw(batch, "No runes crafted yet", x, y);
        }

        fontSmall.setColor(Color.WHITE);
    }

    private Color getRuneColor(String runeType) {
        switch (runeType) {
            case "might":     return RUNE_COLOR_MIGHT;     // red
            case "vitality":  return RUNE_COLOR_VITALITY;  // green
            case "fortune":   return RUNE_COLOR_FORTUNE;   // gold
            case "swiftness": return RUNE_COLOR_SWIFTNESS; // blue
            default:          return Color.WHITE;
        }
    }

    /**
     * Same palette as {@link #getRuneColor} at the grid-tint alpha (0.4),
     * precomputed rather than blended per-frame — used only by
     * {@link #getUnitTintColor}.
     */
    private Color getRuneTintColor(String runeType) {
        switch (runeType) {
            case "might":     return TINT_RUNE_MIGHT;
            case "vitality":  return TINT_RUNE_VITALITY;
            case "fortune":   return TINT_RUNE_FORTUNE;
            case "swiftness": return TINT_RUNE_SWIFTNESS;
            default:          return TINT_RUNE_DEFAULT;
        }
    }

    // ══════════════════════════════════════════════════════════════
    // TOUCH HANDLING
    // ══════════════════════════════════════════════════════════════

    /**
     * Handles touch down. Returns true if consumed.
     */
    public boolean handleTouchDown(int screenX, int screenY) {
        if (state == State.CLOSED) return false;

        touchPos.set(screenX, screenY);
        viewport.unproject(touchPos);

        if (state == State.SELECTING_UNIT) {
            // Back button
            if (touchPos.y > getWorldHeight() - SELECT_HEADER_HEIGHT) {
                if (touchPos.x < 80f) {
                    state = State.BROWSING;
                    selectedItem = null;
                    selectedRuneType = null;
                    refreshFilteredItems();
                    return true;
                }
                return true; // consume header touch
            }
            // Grid touches pass through — GridInputHandler handles them
            return false;
        }

        // BROWSING — consume all touches
        touchDown = true;
        touchStartY = touchPos.y;
        scrolling = false;

        int itemIdx = getItemIndexAt(touchPos.x, touchPos.y);
        if (itemIdx >= 0) {
            holding = true;
            holdTimer = 0f;
            holdIndex = itemIdx;
            showingPreview = false;
        }

        return true;
    }

    /**
     * Handles touch dragged. Returns true if consumed.
     */
    public boolean handleTouchDragged(int screenX, int screenY) {
        if (state != State.BROWSING || !touchDown) return false;

        touchPos.set(screenX, screenY);
        viewport.unproject(touchPos);

        float dy = touchPos.y - touchStartY;
        if (Math.abs(dy) > 8f) {
            scrolling = true;
            holding = false;
            showingPreview = false;
        }

        if (scrolling) {
            scrollY += dy * 0.5f;
            scrollY = Math.max(0, Math.min(scrollY, maxScrollY));
            touchStartY = touchPos.y;
        }

        return true;
    }

    /**
     * Handles touch up. Returns true if consumed.
     */
    public boolean handleTouchUp(int screenX, int screenY) {
        if (state == State.CLOSED) return false;
        if (state == State.SELECTING_UNIT) return false;

        touchPos.set(screenX, screenY);
        viewport.unproject(touchPos);

        holding = false;
        boolean wasPreviewing = showingPreview;
        showingPreview = false;
        touchDown = false;

        // Dismiss preview without action
        if (wasPreviewing) return true;
        if (scrolling) { scrolling = false; return true; }

        // ── Salvage button (Equipment tab only) ── checked ahead of the
        // close button below — its x-range [-140f, -44f] is clear of the
        // close button's own x > -40f zone, so ordering doesn't strictly
        // matter, but this keeps the two related header-button checks
        // grouped together.
        if (activeTab == TAB_EQUIPMENT && salvagePanel != null
            && touchPos.x >= getMenuX() + getMenuWidth() - 140f
            && touchPos.x <= getMenuX() + getMenuWidth() - 44f
            && touchPos.y > getMenuTop() - HEADER_HEIGHT) {
            salvagePanel.open();
            return true;
        }

        // ── Refine button (Equipment tab only) ── same shape as Salvage's
        // check above, own non-overlapping hit-zone to its left.
        if (activeTab == TAB_EQUIPMENT && refinePanel != null
            && touchPos.x >= getMenuX() + getMenuWidth() - 240f
            && touchPos.x <= getMenuX() + getMenuWidth() - 144f
            && touchPos.y > getMenuTop() - HEADER_HEIGHT) {
            refinePanel.open();
            return true;
        }

        // ── Close button ──
        if (touchPos.x > getMenuX() + getMenuWidth() - 40f
            && touchPos.y > getMenuTop() - HEADER_HEIGHT) {
            close();
            return true;
        }

        // ── Outside menu ──
        if (touchPos.x < getMenuX() || touchPos.x > getMenuX() + getMenuWidth()
            || touchPos.y < getMenuBottom() || touchPos.y > getMenuTop()) {
            close();
            return true;
        }

        // ── Tab switch ──
        if (touchPos.y >= getTabY() && touchPos.y <= getTabY() + TAB_HEIGHT) {
            float tabWidth = getMenuWidth() / 3f;
            int tab = (int) ((touchPos.x - getMenuX()) / tabWidth);
            if (tab >= 0 && tab <= 2) {
                activeTab = tab;
                scrollY = 0f;
                refreshFilteredItems();
            }
            return true;
        }

        // ── Fragments tab dispatch ──
        // Fragments/runes live in RuneSystem's counter, never as Inventory
        // items, so filteredItems (and getItemIndexAt below) is always empty
        // on this tab. Route [Craft]/[Apply] taps in the content area to the
        // tab's own hit-testing directly; otherwise the visible [Craft] button
        // is drawn but never actionable.
        if (activeTab == TAB_FRAGMENTS
            && touchPos.y >= getItemAreaBottom() && touchPos.y <= getItemAreaTop()) {
            handleFragmentTabTouch();
            return true;
        }

        // ── Item tap ──
        int idx = getItemIndexAt(touchPos.x, touchPos.y);
        if (idx >= 0 && idx < filteredItems.size()) {
            if (activeTab == TAB_FRAGMENTS) {
                handleFragmentTabTouch();
                return true;
            }
            Item tapped = filteredItems.get(idx);
            // ancient_map needs no unit target — open the Hidden Temple
            // confirm card instead of entering SELECTING_UNIT. Consuming
            // the item happens inside the popup, not here (see
            // HiddenTemplePopup.openConfirm/openTemple).
            if ("ancient_map".equals(tapped.getType())) {
                if (hiddenTemplePopup != null) {
                    hiddenTemplePopup.openConfirm(tapped.getId());
                }
                return true;
            }
            selectedItem = tapped;
            state = State.SELECTING_UNIT;
            Gdx.app.log(TAG, "Selected: " + selectedItem.getDisplayName() + " — pick a unit");
            return true;
        }

        // ── Unequip [X] ──
        if (touchPos.y < getMenuBottom() + getEquippedSectionHeight()
            && touchPos.x >= getMenuX() + getMenuWidth() - 46f) {
            Inventory inv = eventManager.getInventory();
            float rowY = getMenuBottom() + getEquippedSectionHeight() - 28f;
            for (Map.Entry<String, String> entry : inv.getEquipped().entrySet()) {
                if (Math.abs(touchPos.y - rowY) < EQUIPPED_ROW_HEIGHT / 2f) {
                    eventManager.unequipItem(entry.getKey());
                    refreshFilteredItems();
                    return true;
                }
                rowY -= EQUIPPED_ROW_HEIGHT;
            }
        }

        return true;
    }

    private void handleFragmentTabTouch() {
        RuneSystem rs = eventManager.getRuneSystem();
        float x = getMenuX() + 16f;
        float y = getItemAreaTop() - 44f;
        float rowHeight = 56f;
        float btnX = getMenuX() + getMenuWidth() - 70f;

        // Check [Craft] buttons
        for (int i = 0; i < RuneSystem.RUNE_TYPES.length; i++) {
            String type = RuneSystem.RUNE_TYPES[i];
            float rowY = y - i * rowHeight;

            if (touchPos.x >= btnX && touchPos.y >= rowY - 12f && touchPos.y <= rowY + 12f) {
                if (rs.canCraft(type)) {
                    rs.craftRune(type);
                    Gdx.app.log(TAG, "Crafted rune: " + type);
                }
                return;
            }
        }

        // Check [Apply] buttons for crafted runes
        float craftedY = y - RuneSystem.RUNE_TYPES.length * rowHeight - 34f;
        for (int i = 0; i < RuneSystem.RUNE_TYPES.length; i++) {
            String type = RuneSystem.RUNE_TYPES[i];
            int crafted = rs.getCraftedCount(type);
            if (crafted <= 0) continue;

            if (touchPos.x >= btnX && touchPos.y >= craftedY - 12f
                && touchPos.y <= craftedY + 12f) {
                // Switch to unit selection mode for rune application
                selectedRuneType = type;
                state = State.SELECTING_UNIT;
                Gdx.app.log(TAG, "Applying rune: " + type + " — select a unit");
                return;
            }
            craftedY -= 28f;
        }
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    private int getItemIndexAt(float wx, float wy) {
        if (wy < getItemAreaBottom() || wy > getItemAreaTop()) return -1;

        float startX = getItemGridStartX();
        float startY = getItemAreaTop() - ITEM_SIZE + scrollY;

        for (int i = 0; i < filteredItems.size(); i++) {
            int col = i % ITEMS_PER_ROW;
            int row = i / ITEMS_PER_ROW;
            float ix = startX + col * (ITEM_SIZE + ITEM_GAP);
            float iy = startY - row * (ITEM_SIZE + ITEM_GAP);

            if (wx >= ix && wx <= ix + ITEM_SIZE && wy >= iy && wy <= iy + ITEM_SIZE) {
                return i;
            }
        }
        return -1;
    }

}
