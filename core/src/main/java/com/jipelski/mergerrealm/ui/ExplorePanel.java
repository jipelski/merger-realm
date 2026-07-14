package com.jipelski.mergerrealm.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;

import com.jipelski.mergerrealm.model.ExplorationEvent;
import com.jipelski.mergerrealm.model.ExplorationSlot;
import com.jipelski.mergerrealm.model.GameObject;
import com.jipelski.mergerrealm.model.Unit;
import com.jipelski.mergerrealm.util.AdManager;
import com.jipelski.mergerrealm.util.EventManager;
import com.jipelski.mergerrealm.util.ExplorationManager;
import com.jipelski.mergerrealm.util.GoldManager;
import com.jipelski.mergerrealm.util.GridObjectManager;
import com.jipelski.mergerrealm.util.SpriteManager;
import com.jipelski.mergerrealm.util.TextUtil;

import java.util.List;

/**
 * Exploration panel overlay.
 *
 * States:
 *   CLOSED        — not visible
 *   OVERVIEW      — shows active exploration slots, send button
 *   SELECT_ZONE   — pick a zone to explore
 *   SELECT_UNIT   — grid visible below, tap a unit to send
 *   VIEW_LOG      — scrollable text log for one exploration slot
 *
 * Flow:
 *   WallGate "Explore" → OVERVIEW → "Send" → SELECT_ZONE → tap zone
 *   → SELECT_UNIT → tap unit on grid → unit sent → back to OVERVIEW
 *
 *   Tap active slot → VIEW_LOG (scrollable log)
 *   "Recall" → unit starts returning
 *   "Collect" → loot delivered, unit returned
 */
public class ExplorePanel {

    private static final String TAG = "ExplorePanel";

    // ── States ──
    public enum State { CLOSED, OVERVIEW, SELECT_ZONE, SELECT_UNIT, VIEW_LOG }
    private State state = State.CLOSED;

    // ── Layout ──
    private static final float MARGIN = 16f;
    private static final float HEADER_HEIGHT = 36f;
    private static final float SLOT_HEIGHT = 72f;
    private static final float SLOT_GAP = 6f;
    private static final float ZONE_ROW_HEIGHT = 60f;
    private static final float BTN_WIDTH = 70f;
    private static final float BTN_HEIGHT = 28f;
    private static final float SELECT_HEADER_HEIGHT = 80f;
    private static final float LOG_LINE_HEIGHT = 18f;

    // ── Zone definitions (display order) ──
    private static final String[] ZONE_KEYS = {"forest", "mountain", "mines", "ruins", "wastes"};
    private static final String[] ZONE_NAMES = {"Forest Path", "Mountain Trail", "Underground Mines", "Cursed Ruins", "Demon Wastes"};
    private static final int[] ZONE_UNLOCK_LEVELS = {1, 6, 12, 20, 30};

    // ── Grid tint palette (shared instances — drawTintsAtY only reads the
    // returned color via batch.setColor, never mutates it, so a `new Color`
    // per cell per frame is pure avoidable garbage) ──
    private static final Color TINT_NON_UNIT   = new Color(0.15f, 0.15f, 0.15f, 0.6f);
    private static final Color TINT_INELIGIBLE = new Color(0.3f, 0.3f, 0.3f, 0.5f);
    private static final Color TINT_ELIGIBLE   = new Color(0.2f, 0.85f, 0.2f, 0.35f);

    // ── Selection state ──
    private String selectedZone = null;
    private int viewingSlotIndex = -1;

    // ── Log scroll ──
    private float logScrollY = 0f;
    private float logMaxScrollY = 0f;

    // ── References ──
    private final EventManager eventManager;
    private final SpriteManager spriteManager;
    private final Viewport viewport;
    private final UITextureManager uiTex;
    private final GlyphLayout glyphLayout;

    // ── Touch ──
    private final Vector2 touchPos = new Vector2();
    private boolean touchDown = false;
    private float touchStartY = 0f;
    private boolean scrolling = false;

    // Opened for the per-slot [Ad N/5] affordance next to [Rush +10G] — see
    // AdRewardPopup's class javadoc for the full picture across all 4
    // ad-eligible actions.
    private AdRewardPopup adRewardPopup;

    public ExplorePanel(EventManager eventManager, SpriteManager spriteManager,
                        Viewport viewport, UITextureManager uiTex) {
        this.eventManager = eventManager;
        this.spriteManager = spriteManager;
        this.viewport = viewport;
        this.uiTex = uiTex;
        this.glyphLayout = new GlyphLayout();
    }

    public void setAdRewardPopup(AdRewardPopup popup) { this.adRewardPopup = popup; }

    // ══════════════════════════════════════════════════════════════
    // STATE
    // ══════════════════════════════════════════════════════════════

    public State getState() { return state; }
    public boolean isVisible() { return state != State.CLOSED; }
    public boolean isSelectingUnit() { return state == State.SELECT_UNIT; }

    public void open() {
        state = State.OVERVIEW;
        selectedZone = null;
        viewingSlotIndex = -1;
    }

    public void close() {
        state = State.CLOSED;
        selectedZone = null;
        viewingSlotIndex = -1;
    }

    /**
     * Called when a unit is tapped on the grid during SELECT_UNIT.
     * Returns true if the unit was sent exploring.
     */
    public boolean onUnitSelected(String unitId) {
        if (state != State.SELECT_UNIT || selectedZone == null) return false;

        ExplorationManager em = eventManager.getExplorationManager();
        if (em == null) return false;

        boolean sent = em.sendUnit(unitId, selectedZone);
        if (sent) {
            Gdx.app.log(TAG, "Sent unit " + unitId + " to " + selectedZone);
            state = State.OVERVIEW;
            selectedZone = null;
        }
        return sent;
    }

    /**
     * Returns true if the given unit is alive, has HP, and can deal damage
     * (base + equipped weapon) — the single source of truth for SELECT_UNIT
     * eligibility. {@link #getUnitTintColor} derives its color FROM this,
     * never the reverse. Sendable at any level (e.g. lv2 mercenary/griffin),
     * but excludes 0-damage resource units unless equipped with a weapon.
     */
    public boolean canSendUnit(String objectId) {
        if (state != State.SELECT_UNIT) return false;

        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();
        GameObject obj = gom.getObject(objectId);
        if (obj == null || !(obj instanceof Unit)) return false;

        Unit unit = (Unit) obj;
        return unit.getMax_hp() > 0 && unit.isAlive()
            && eventManager.getEffectiveDamage(objectId) > 0;
    }

    /**
     * Returns the tint color for a grid cell during unit selection.
     * Green = eligible (alive, has HP, can deal damage)
     * Grey = ineligible
     */
    public Color getUnitTintColor(String objectId) {
        if (state != State.SELECT_UNIT) return null;

        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();
        GameObject obj = gom.getObject(objectId);
        if (obj == null) return null;

        if (!(obj instanceof Unit)) {
            return TINT_NON_UNIT; // dark — non-unit
        }

        if (!canSendUnit(objectId)) {
            return TINT_INELIGIBLE; // grey — ineligible
        }

        return TINT_ELIGIBLE; // green — eligible
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
    private float getContentTop() { return getMenuTop() - HEADER_HEIGHT; }

    // ══════════════════════════════════════════════════════════════
    // DRAWING
    // ══════════════════════════════════════════════════════════════

    public void drawBackground(ShapeRenderer sr) {
        if (state == State.CLOSED) return;

        if (state == State.SELECT_UNIT) {
            drawSelectUnitBackground(sr);
            return;
        }

        // Full overlay for OVERVIEW, SELECT_ZONE, VIEW_LOG
        Gdx.gl.glEnable(Gdx.gl.GL_BLEND);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        sr.setColor(0f, 0f, 0f, 0.75f);
        sr.rect(0, 0, getWorldWidth(), getWorldHeight());

        sr.setColor(0.12f, 0.12f, 0.18f, 1f);
        sr.rect(getMenuX(), getMenuBottom(), getMenuWidth(), getMenuHeight());

        sr.setColor(0.18f, 0.18f, 0.26f, 1f);
        sr.rect(getMenuX(), getMenuTop() - HEADER_HEIGHT, getMenuWidth(), HEADER_HEIGHT);

        // State-specific backgrounds
        if (state == State.OVERVIEW) {
            drawOverviewSlotBackgrounds(sr);
        } else if (state == State.SELECT_ZONE) {
            drawZoneRowBackgrounds(sr);
        } else if (state == State.VIEW_LOG) {
            // Log area is just the panel background (already drawn)
        }

        sr.end();
        Gdx.gl.glDisable(Gdx.gl.GL_BLEND);
    }

    public void drawContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        if (state == State.CLOSED) return;

        if (state == State.SELECT_UNIT) {
            drawSelectUnitContent(batch, font, fontSmall);
            return;
        }

        // Header
        font.setColor(Color.WHITE);
        String title;
        switch (state) {
            case SELECT_ZONE: title = "Choose a Zone"; break;
            case VIEW_LOG: title = "Exploration Log"; break;
            default: title = "Exploration"; break;
        }
        font.draw(batch, title, getMenuX() + 12f, getMenuTop() - 10f);

        // Close / Back button
        if (state == State.OVERVIEW) {
            font.draw(batch, "X", getMenuX() + getMenuWidth() - 28f, getMenuTop() - 10f);
        } else {
            fontSmall.setColor(0.6f, 0.8f, 0.6f, 1f);
            fontSmall.draw(batch, "< Back", getMenuX() + getMenuWidth() - 60f, getMenuTop() - 12f);
        }

        ExplorationManager em = eventManager.getExplorationManager();
        if (em != null) {
            int princeLvl = eventManager.getBattleFieldManager().getLevel();
            int maxSlots = ExplorationManager.getSlotsForLevel(princeLvl)
                + eventManager.getGoldManager().getExtraExploreSlots();
            fontSmall.setColor(0.5f, 0.5f, 0.6f, 1f);
            String slotText = "Slots: " + em.getUsedSlotCount() + "/" + maxSlots;
            glyphLayout.setText(fontSmall, slotText);
            fontSmall.draw(batch, slotText, getMenuX() + 12f, getMenuTop() - HEADER_HEIGHT - 8f);
        }

        switch (state) {
            case OVERVIEW: drawOverviewContent(batch, font, fontSmall); break;
            case SELECT_ZONE: drawZoneContent(batch, font, fontSmall); break;
            case VIEW_LOG: drawLogContent(batch, font, fontSmall); break;
        }

        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    // ── OVERVIEW ──

    private void drawOverviewSlotBackgrounds(ShapeRenderer sr) {
        ExplorationManager em = eventManager.getExplorationManager();
        if (em == null) return;

        float slotY = getContentTop() - 24f;
        for (int i = 0; i < em.getActiveSlots().size(); i++) {
            float y = slotY - i * (SLOT_HEIGHT + SLOT_GAP);
            ExplorationSlot slot = em.getActiveSlots().get(i);

            if (slot.isDead()) {
                sr.setColor(0.25f, 0.15f, 0.15f, 1f); // red tint
            } else if (slot.hasArrived()) {
                sr.setColor(0.15f, 0.25f, 0.15f, 1f); // green tint
            } else if (slot.isReturning()) {
                sr.setColor(0.2f, 0.2f, 0.28f, 1f); // blue-ish
            } else {
                sr.setColor(0.18f, 0.18f, 0.26f, 1f); // normal
            }
            sr.rect(getMenuX() + 8f, y - SLOT_HEIGHT, getMenuWidth() - 16f, SLOT_HEIGHT);
        }

        // "Send Unit" button
        if (em.hasAvailableSlot()) {
            float btnY = slotY - em.getActiveSlots().size() * (SLOT_HEIGHT + SLOT_GAP) - 40f;
            sr.setColor(0.2f, 0.6f, 0.2f, 1f);
            sr.rect(getMenuX() + getMenuWidth() / 2f - BTN_WIDTH,
                btnY, BTN_WIDTH * 2, BTN_HEIGHT);
        }
    }

    private void drawOverviewContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        ExplorationManager em = eventManager.getExplorationManager();
        if (em == null) return;

        float slotY = getContentTop() - 24f;
        List<ExplorationSlot> slots = em.getActiveSlots();

        if (slots.isEmpty()) {
            fontSmall.setColor(0.4f, 0.4f, 0.5f, 1f);
            fontSmall.draw(batch, "No active explorations",
                getMenuX() + 12f, slotY - 20f);
        }

        for (int i = 0; i < slots.size(); i++) {
            ExplorationSlot slot = slots.get(i);
            float y = slotY - i * (SLOT_HEIGHT + SLOT_GAP);

            // Unit name + zone
            font.setColor(Color.WHITE);
            font.draw(batch, TextUtil.capitalize(slot.getUnitType()) + " Lv" + slot.getUnitLevel(),
                getMenuX() + 16f, y - 8f);

            fontSmall.setColor(0.7f, 0.7f, 0.8f, 1f);
            fontSmall.draw(batch, getZoneDisplayName(slot.getZone()),
                getMenuX() + 16f, y - 28f);

            // Status + HP
            fontSmall.setColor(0.85f, 0.8f, 0.5f, 1f);
            String status;
            if (slot.isDead()) {
                status = "FALLEN | Loot ready";
                fontSmall.setColor(0.9f, 0.3f, 0.3f, 1f);
            } else if (slot.hasArrived()) {
                status = "RETURNED | Tap to collect";
                fontSmall.setColor(0.3f, 0.9f, 0.3f, 1f);
            } else if (slot.isReturning()) {
                long remainMs = slot.getReturnRemainingMs();
                status = "Returning... " + TextUtil.formatDurationMs(remainMs);
            } else {
                long elapsed = slot.getElapsedMs();
                status = "Exploring " + TextUtil.formatDurationMs(elapsed)
                    + " | HP: " + slot.getUnitCurrentHp() + "/" + slot.getUnitMaxHp();
            }
            fontSmall.draw(batch, status, getMenuX() + 16f, y - 46f);

            // Loot count
            fontSmall.setColor(0.6f, 0.6f, 0.7f, 1f);
            fontSmall.draw(batch, slot.getLoot().size() + " loot",
                getMenuX() + getMenuWidth() - 70f, y - 8f);

            // Action buttons on right side
            float btnX = getMenuX() + getMenuWidth() - BTN_WIDTH - 16f;
            float btnY = y - SLOT_HEIGHT + 8f;

            if (slot.isDead() || slot.hasArrived()) {
                // "Collect" button
                fontSmall.setColor(0.3f, 0.9f, 0.3f, 1f);
                fontSmall.draw(batch, "[Collect]", btnX, btnY + 16f);
            } else if (!slot.isReturning()) {
                // "Recall" button
                fontSmall.setColor(0.9f, 0.7f, 0.2f, 1f);
                fontSmall.draw(batch, "[Recall]", btnX, btnY + 16f);
            }

            // "Log" button
            fontSmall.setColor(0.5f, 0.5f, 0.7f, 1f);
            fontSmall.draw(batch, "[Log]", btnX - 50f, btnY + 16f);

            if (!slot.isDead() && !slot.hasArrived() && slot.isReturning()) {
                GoldManager gm = eventManager.getGoldManager();
                boolean can = gm.canAfford(GoldManager.COST_SPEED_EXPLORATION);
                if (can) fontSmall.setColor(1f, 0.85f, 0.25f, 1f);
                else     fontSmall.setColor(0.5f, 0.4f, 0.2f, 1f);
                fontSmall.draw(batch, "[Rush +10G]", btnX - 110f, btnY + 16f);

                int remaining = eventManager.getAdManager().getRemaining(AdManager.AdAction.SPEED_EXPLORATION);
                if (remaining > 0) fontSmall.setColor(0.3f, 0.75f, 0.95f, 1f);
                else                fontSmall.setColor(0.4f, 0.4f, 0.45f, 1f);
                fontSmall.draw(batch, "[Ad " + remaining + "/" + AdManager.MAX_PER_DAY + "]", btnX - 175f, btnY + 16f);
            }
        }

        // "Send Unit" button text
        if (em.hasAvailableSlot()) {
            float btnY = slotY - slots.size() * (SLOT_HEIGHT + SLOT_GAP) - 40f;
            fontSmall.setColor(Color.WHITE);
            glyphLayout.setText(fontSmall, "Send Unit");
            fontSmall.draw(batch, "Send Unit",
                getMenuX() + getMenuWidth() / 2f - glyphLayout.width / 2f,
                btnY + BTN_HEIGHT - 8f);
        }

        if (!em.hasAvailableSlot()) {
            GoldManager gm = eventManager.getGoldManager();
            boolean can = gm.canAfford(GoldManager.COST_EXTRA_EXPLORE_SLOT);
            if (can) fontSmall.setColor(1f, 0.85f, 0.25f, 1f);
            else     fontSmall.setColor(0.5f, 0.4f, 0.2f, 1f);
            glyphLayout.setText(fontSmall, "[+1 Slot: 40G]");
            fontSmall.draw(batch, "[+1 Slot: 40G]",
                getMenuX() + getMenuWidth() / 2f - glyphLayout.width / 2f, getMenuBottom() + 24f);
        }
    }

    // ── SELECT ZONE ──

    private void drawZoneRowBackgrounds(ShapeRenderer sr) {
        int princeLvl = eventManager.getBattleFieldManager().getLevel();
        float rowY = getContentTop() - 30f;

        for (int i = 0; i < ZONE_KEYS.length; i++) {
            float y = rowY - i * (ZONE_ROW_HEIGHT + SLOT_GAP);
            boolean unlocked = princeLvl >= ZONE_UNLOCK_LEVELS[i];

            if (unlocked) {
                sr.setColor(0.18f, 0.18f, 0.26f, 1f);
            } else {
                sr.setColor(0.12f, 0.12f, 0.16f, 1f);
            }
            sr.rect(getMenuX() + 8f, y - ZONE_ROW_HEIGHT, getMenuWidth() - 16f, ZONE_ROW_HEIGHT);
        }
    }

    private void drawZoneContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        int princeLvl = eventManager.getBattleFieldManager().getLevel();
        float rowY = getContentTop() - 30f;

        for (int i = 0; i < ZONE_KEYS.length; i++) {
            float y = rowY - i * (ZONE_ROW_HEIGHT + SLOT_GAP);
            boolean unlocked = princeLvl >= ZONE_UNLOCK_LEVELS[i];

            if (unlocked) {
                font.setColor(Color.WHITE);
                font.draw(batch, ZONE_NAMES[i], getMenuX() + 20f, y - 10f);

                fontSmall.setColor(0.6f, 0.6f, 0.7f, 1f);
                fontSmall.draw(batch, "Unlock: Lv" + ZONE_UNLOCK_LEVELS[i],
                    getMenuX() + 20f, y - 32f);

                // Difficulty indicator
                fontSmall.setColor(0.85f, 0.8f, 0.5f, 1f);
                String diff;
                switch (i) {
                    case 0: diff = "Easy"; break;
                    case 1: diff = "Medium"; break;
                    case 2: diff = "Hard"; break;
                    case 3: diff = "Very Hard"; break;
                    default: diff = "Extreme"; break;
                }
                glyphLayout.setText(fontSmall, diff);
                fontSmall.draw(batch, diff,
                    getMenuX() + getMenuWidth() - glyphLayout.width - 20f, y - 20f);
            } else {
                font.setColor(0.4f, 0.4f, 0.5f, 1f);
                font.draw(batch, ZONE_NAMES[i] + " — LOCKED",
                    getMenuX() + 20f, y - 10f);

                fontSmall.setColor(0.35f, 0.35f, 0.45f, 1f);
                fontSmall.draw(batch, "Requires Prince Lv" + ZONE_UNLOCK_LEVELS[i],
                    getMenuX() + 20f, y - 32f);
            }
        }
    }

    // ── SELECT UNIT (compact header) ──

    private void drawSelectUnitBackground(ShapeRenderer sr) {
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0.12f, 0.12f, 0.18f, 0.95f);
        sr.rect(0, getWorldHeight() - SELECT_HEADER_HEIGHT,
            getWorldWidth(), SELECT_HEADER_HEIGHT);
        sr.setColor(0.4f, 0.6f, 0.9f, 1f);
        sr.rect(0, getWorldHeight() - SELECT_HEADER_HEIGHT, getWorldWidth(), 2f);
        sr.end();
    }

    private void drawSelectUnitContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        float top = getWorldHeight();

        fontSmall.setColor(0.6f, 0.8f, 0.6f, 1f);
        fontSmall.draw(batch, "< Back", 12f, top - 14f);

        font.setColor(0.4f, 0.7f, 1f, 1f);
        font.draw(batch, "Send to: " + getZoneDisplayName(selectedZone),
            80f, top - 14f);

        fontSmall.setColor(0.7f, 0.7f, 0.8f, 1f);
        fontSmall.draw(batch, "Tap a unit with HP & damage to send exploring",
            80f, top - 34f);

        // Legend
        fontSmall.setColor(0.2f, 0.85f, 0.2f, 1f);
        fontSmall.draw(batch, "●", 12f, top - 60f);
        fontSmall.setColor(0.6f, 0.6f, 0.7f, 1f);
        fontSmall.draw(batch, "Eligible", 24f, top - 60f);

        fontSmall.setColor(0.4f, 0.4f, 0.4f, 1f);
        fontSmall.draw(batch, "●", 100f, top - 60f);
        fontSmall.setColor(0.6f, 0.6f, 0.7f, 1f);
        fontSmall.draw(batch, "Ineligible", 112f, top - 60f);

        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    // ── VIEW LOG ──

    private void drawLogContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        ExplorationManager em = eventManager.getExplorationManager();
        if (em == null || viewingSlotIndex < 0
            || viewingSlotIndex >= em.getActiveSlots().size()) return;

        ExplorationSlot slot = em.getActiveSlots().get(viewingSlotIndex);
        List<ExplorationEvent> log = slot.getLog();

        float startY = getContentTop() - 30f + logScrollY;

        // Unit info at top
        font.setColor(Color.WHITE);
        font.draw(batch, TextUtil.capitalize(slot.getUnitType()) + " Lv" + slot.getUnitLevel()
                + " — " + getZoneDisplayName(slot.getZone()),
            getMenuX() + 12f, getContentTop() - 8f);

        fontSmall.setColor(0.85f, 0.8f, 0.5f, 1f);
        fontSmall.draw(batch, "HP: " + slot.getUnitCurrentHp() + "/" + slot.getUnitMaxHp()
                + " | Loot: " + slot.getLoot().size(),
            getMenuX() + 12f, getContentTop() - 26f);

        // Log entries (newest at top)
        float logTop = getContentTop() - 44f;
        float logBottom = getMenuBottom() + 8f;

        for (int i = log.size() - 1; i >= 0; i--) {
            ExplorationEvent event = log.get(i);
            float y = startY - (log.size() - 1 - i) * LOG_LINE_HEIGHT;

            if (y < logBottom || y > logTop + LOG_LINE_HEIGHT) continue;

            // Color by event type
            switch (event.getType()) {
                case "combat": fontSmall.setColor(0.9f, 0.4f, 0.4f, 1f); break;
                case "loot":   fontSmall.setColor(0.4f, 0.9f, 0.4f, 1f); break;
                case "heal":   fontSmall.setColor(0.4f, 0.8f, 0.9f, 1f); break;
                case "death":  fontSmall.setColor(1f, 0.2f, 0.2f, 1f); break;
                case "trap":   fontSmall.setColor(0.9f, 0.6f, 0.2f, 1f); break;
                case "curse":  fontSmall.setColor(0.7f, 0.3f, 0.9f, 1f); break;
                default:       fontSmall.setColor(0.6f, 0.6f, 0.7f, 1f); break;
            }

            String line = event.getFormattedTime() + " " + event.getText();
            fontSmall.draw(batch, line, getMenuX() + 12f, y,
                getMenuWidth() - 24f, com.badlogic.gdx.utils.Align.left, true);
        }

        // Update max scroll
        logMaxScrollY = Math.max(0, log.size() * LOG_LINE_HEIGHT - (logTop - logBottom));
    }

    // ══════════════════════════════════════════════════════════════
    // TOUCH HANDLING
    // ══════════════════════════════════════════════════════════════

    public boolean handleTouchDown(int screenX, int screenY) {
        if (state == State.CLOSED) return false;

        touchPos.set(screenX, screenY);
        viewport.unproject(touchPos);

        if (state == State.SELECT_UNIT) {
            // Back button in header
            if (touchPos.y > getWorldHeight() - SELECT_HEADER_HEIGHT && touchPos.x < 80f) {
                state = State.SELECT_ZONE;
                selectedZone = null;
                return true;
            }
            if (touchPos.y > getWorldHeight() - SELECT_HEADER_HEIGHT) return true;
            return false; // let grid handle it
        }

        touchDown = true;
        touchStartY = touchPos.y;
        scrolling = false;
        return true;
    }

    public boolean handleTouchDragged(int screenX, int screenY) {
        if (!touchDown || state == State.SELECT_UNIT) return false;

        touchPos.set(screenX, screenY);
        viewport.unproject(touchPos);

        if (state == State.VIEW_LOG) {
            float dy = touchPos.y - touchStartY;
            if (Math.abs(dy) > 6f) scrolling = true;
            if (scrolling) {
                logScrollY += dy * 0.5f;
                logScrollY = Math.max(0, Math.min(logScrollY, logMaxScrollY));
                touchStartY = touchPos.y;
            }
        }

        return true;
    }

    public boolean handleTouchUp(int screenX, int screenY) {
        if (state == State.CLOSED || state == State.SELECT_UNIT) return false;

        touchPos.set(screenX, screenY);
        viewport.unproject(touchPos);
        touchDown = false;

        if (scrolling) { scrolling = false; return true; }

        // ── Close / Back ──
        if (touchPos.y > getMenuTop() - HEADER_HEIGHT) {
            if (state == State.OVERVIEW) {
                close();
            } else {
                // Back to previous state
                if (state == State.VIEW_LOG) state = State.OVERVIEW;
                else if (state == State.SELECT_ZONE) state = State.OVERVIEW;
            }
            return true;
        }

        // ── Outside menu ──
        if (touchPos.x < getMenuX() || touchPos.x > getMenuX() + getMenuWidth()
            || touchPos.y < getMenuBottom() || touchPos.y > getMenuTop()) {
            close();
            return true;
        }

        // ── State-specific touch ──
        switch (state) {
            case OVERVIEW: return handleOverviewTouch();
            case SELECT_ZONE: return handleZoneTouch();
            case VIEW_LOG: return true; // consume
        }

        return true;
    }

    private boolean handleOverviewTouch() {
        ExplorationManager em = eventManager.getExplorationManager();
        if (em == null) return true;

        float slotY = getContentTop() - 24f;
        List<ExplorationSlot> slots = em.getActiveSlots();

        // Check slot taps
        for (int i = 0; i < slots.size(); i++) {
            float y = slotY - i * (SLOT_HEIGHT + SLOT_GAP);
            if (touchPos.y <= y && touchPos.y >= y - SLOT_HEIGHT) {
                ExplorationSlot slot = slots.get(i);
                float btnX = getMenuX() + getMenuWidth() - BTN_WIDTH - 16f;

                // Action button area (right side)
                if (touchPos.x >= btnX - 10f) {
                    if (slot.isDead() || slot.hasArrived()) {
                        em.collectResults(i);
                        Gdx.app.log(TAG, "Collected results from slot " + i);
                    } else if (!slot.isReturning()) {
                        em.recallUnit(i);
                        Gdx.app.log(TAG, "Recalled unit from slot " + i);
                    }
                    return true;
                }

                // Log button
                if (touchPos.x >= btnX - 60f && touchPos.x < btnX - 10f) {
                    viewingSlotIndex = i;
                    logScrollY = 0f;
                    state = State.VIEW_LOG;
                    Gdx.app.log(TAG, "Viewing log for slot " + i);
                    return true;
                }

                // speed button region (left of the Log button)
                if (touchPos.x >= btnX - 110f && touchPos.x < btnX - 60f) {
                    if (!slot.isDead() && !slot.hasArrived() && slot.isReturning()) {
                        eventManager.getGoldManager().speedUpExploration(i);
                    }
                    return true;
                }

                // ad button region (left of the Rush button)
                if (touchPos.x >= btnX - 175f && touchPos.x < btnX - 110f) {
                    if (!slot.isDead() && !slot.hasArrived() && slot.isReturning() && adRewardPopup != null) {
                        adRewardPopup.open(AdManager.AdAction.SPEED_EXPLORATION, false, i, null);
                    }
                    return true;
                }

                return true;
            }
        }

        // "Send Unit" button
        if (em.hasAvailableSlot()) {
            float btnY = slotY - slots.size() * (SLOT_HEIGHT + SLOT_GAP) - 40f;
            if (touchPos.y >= btnY && touchPos.y <= btnY + BTN_HEIGHT
                && touchPos.x >= getMenuX() + getMenuWidth() / 2f - BTN_WIDTH
                && touchPos.x <= getMenuX() + getMenuWidth() / 2f + BTN_WIDTH) {
                state = State.SELECT_ZONE;
                Gdx.app.log(TAG, "Opening zone selection");
                return true;
            }
        }

        if (!em.hasAvailableSlot()
            && touchPos.y >= getMenuBottom() + 8f && touchPos.y <= getMenuBottom() + 32f) {
            eventManager.getGoldManager().purchaseExtraExploreSlot();
            return true;
        }
        return true;
    }

    private boolean handleZoneTouch() {
        int princeLvl = eventManager.getBattleFieldManager().getLevel();
        float rowY = getContentTop() - 30f;

        for (int i = 0; i < ZONE_KEYS.length; i++) {
            float y = rowY - i * (ZONE_ROW_HEIGHT + SLOT_GAP);
            if (touchPos.y <= y && touchPos.y >= y - ZONE_ROW_HEIGHT) {
                if (princeLvl >= ZONE_UNLOCK_LEVELS[i]) {
                    selectedZone = ZONE_KEYS[i];
                    state = State.SELECT_UNIT;
                    Gdx.app.log(TAG, "Selected zone: " + selectedZone);
                }
                return true;
            }
        }

        return true;
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    private String getZoneDisplayName(String zoneKey) {
        if (zoneKey == null) return "Unknown";
        for (int i = 0; i < ZONE_KEYS.length; i++) {
            if (ZONE_KEYS[i].equals(zoneKey)) return ZONE_NAMES[i];
        }
        return zoneKey;
    }
}
