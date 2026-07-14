package com.jipelski.mergerrealm.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;

import com.jipelski.mergerrealm.model.GameObject;
import com.jipelski.mergerrealm.model.Item;
import com.jipelski.mergerrealm.model.RaidState;
import com.jipelski.mergerrealm.model.RaidState.RaidEnemy;
import com.jipelski.mergerrealm.model.Unit;
import com.jipelski.mergerrealm.util.EventManager;
import com.jipelski.mergerrealm.util.GridObjectManager;
import com.jipelski.mergerrealm.util.Inventory;
import com.jipelski.mergerrealm.util.RaidManager;
import com.jipelski.mergerrealm.util.SpriteManager;
import com.jipelski.mergerrealm.util.TextUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Raid UI panel.
 *
 * States:
 *   CLOSED         — not visible
 *   SELECT_CHAPTER — choose a chapter
 *   SELECT_NODE    — choose a node within the chapter
 *   FORM_PARTY     — pick 4 units from grid (grid visible with tints)
 *   ARRANGE_PARTY  — swap the selected units between front/back slots
 *                    (slot 0/1 = front, 2/3 = back — see
 *                    RaidState.DAMAGE_DISTRIBUTION for why this matters)
 *                    before committing to the raid
 *   COMBAT         — live auto-combat view
 *   RESULTS        — raid complete/failed summary
 */
public class RaidPanel {

    private static final String TAG = "RaidPanel";

    public enum State { CLOSED, SELECT_CHAPTER, SELECT_NODE, FORM_PARTY, ARRANGE_PARTY, COMBAT, RESULTS }
    private State state = State.CLOSED;

    // ── Layout ──
    private static final float MARGIN = 16f;
    private static final float HEADER_HEIGHT = 36f;
    private static final float ROW_HEIGHT = 56f;
    private static final float PARTY_SLOT_SIZE = 72f;
    private static final float PARTY_GAP = 10f;
    private static final float HP_BAR_HEIGHT = 6f;
    private static final float COMBAT_LOG_LINE = 16f;
    private static final float SELECT_HEADER_HEIGHT = 90f;

    // ── Grid tint palette (shared instances — drawTintsAtY only reads the
    // returned color via batch.setColor, never mutates it, so a `new Color`
    // per cell per frame is pure avoidable garbage) ──
    private static final Color TINT_IN_PARTY   = new Color(0.3f, 0.5f, 0.9f, 0.4f);
    private static final Color TINT_NON_UNIT   = new Color(0.15f, 0.15f, 0.15f, 0.6f);
    private static final Color TINT_INELIGIBLE = new Color(0.3f, 0.3f, 0.3f, 0.5f);
    private static final Color TINT_ELIGIBLE   = new Color(0.2f, 0.85f, 0.2f, 0.35f);

    // ── Selection state ──
    private String selectedChapterId = null;
    private String selectedNodeId = null;
    private String[] partyUnitIds = new String[4];
    private int partySlotFilling = 0; // count of filled slots (not a direct index — see onUnitSelected)

    // ── Arrange-party state ──
    private int arrangeSelectedSlot = -1; // -1 = no slot currently selected for swap

    // ── Combat scroll ──
    private float logScrollY = 0f;

    // ── References ──
    private final EventManager eventManager;
    private final SpriteManager spriteManager;
    private final Viewport viewport;
    private final UITextureManager uiTex;
    private final GlyphLayout glyphLayout;

    private final RaidArenaRenderer arena;

    private UnifiedShopPanel unifiedShopPanel;
    public void setUnifiedShopPanel(UnifiedShopPanel panel) {
        this.unifiedShopPanel = panel;
    }

    // ── Touch ──
    private final Vector2 touchPos = new Vector2();

    public RaidPanel(EventManager eventManager, SpriteManager spriteManager,
                     Viewport viewport, UITextureManager uiTex) {
        this.eventManager = eventManager;
        this.spriteManager = spriteManager;
        this.viewport = viewport;
        this.uiTex = uiTex;
        this.glyphLayout = new GlyphLayout();
        this.arena = new RaidArenaRenderer(eventManager, spriteManager, viewport);
    }

    // ══════════════════════════════════════════════════════════════
    // STATE
    // ══════════════════════════════════════════════════════════════

    public State getState() { return state; }
    public boolean isVisible() { return state != State.CLOSED; }
    public boolean isFormingParty() { return state == State.FORM_PARTY; }
    public boolean isCombatActive() { return state == State.COMBAT; }

    public void open() {
        // A raid can already be in progress here if it was restored from disk
        // after the process was killed mid-raid (RaidManager.resumeFromSave) —
        // jump straight into combat/results instead of resetting to
        // chapter-select, which would strand the resumed RaidState with no
        // way back into its own view. drawCombat()'s own isCompleted()/
        // isFailed() check auto-transitions to RESULTS if it already resolved.
        if (eventManager.getRaidManager().getActiveRaid() != null) {
            arena.reset();
            state = State.COMBAT;
            return;
        }
        state = State.SELECT_CHAPTER;
        selectedChapterId = null;
        selectedNodeId = null;
        resetParty();
    }

    public void close() {
        state = State.CLOSED;
        selectedChapterId = null;
        selectedNodeId = null;
        resetParty();
    }

    private void resetParty() {
        for (int i = 0; i < 4; i++) partyUnitIds[i] = null;
        partySlotFilling = 0;
    }

    /**
     * Called when a unit is tapped on the grid during FORM_PARTY.
     */
    public boolean onUnitSelected(String unitId) {
        if (state != State.FORM_PARTY) return false;
        if (partySlotFilling >= 4) return false;

        // Check not already in party
        for (String id : partyUnitIds) {
            if (unitId.equals(id)) return false;
        }

        // Scan for the first empty slot rather than writing directly at
        // partySlotFilling's value — ARRANGE_PARTY can swap a unit into a
        // slot index other than where fill-order would have placed it
        // (e.g. an early unit swapped into slot 3, leaving slot 0/1 empty),
        // so partySlotFilling can no longer be trusted as a direct array
        // index once reordering exists. It still counts "how many filled"
        // for the FORM_PARTY UI's `partySlotFilling > 0` gate — that stays
        // correct since swaps never change the count, only positions.
        int slot = -1;
        for (int i = 0; i < 4; i++) {
            if (partyUnitIds[i] == null) { slot = i; break; }
        }
        if (slot == -1) return false; // shouldn't happen given the guard above, but stay safe

        partyUnitIds[slot] = unitId;
        partySlotFilling++;

        Gdx.app.log(TAG, "Added unit to party slot " + slot + ": " + unitId);
        return true;
    }

    /**
     * Returns true if the given unit is a valid, alive combat unit not
     * already in the party — the single source of truth for FORM_PARTY
     * selection. {@link #getUnitTintColor} derives its color FROM this,
     * never the reverse.
     */
    public boolean canSelectUnit(String objectId) {
        if (state != State.FORM_PARTY) return false;

        // Already in party — not re-selectable (rendered as its own blue tint)
        for (String id : partyUnitIds) {
            if (objectId.equals(id)) return false;
        }

        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();
        GameObject obj = gom.getObject(objectId);
        if (obj == null || !(obj instanceof Unit)) return false;

        Unit unit = (Unit) obj;
        return unit.getMax_hp() > 0 && unit.isAlive() && unit.getDamage() > 0;
    }

    /**
     * Returns tint color for grid cells during party formation.
     * Green = eligible, Grey = ineligible, Blue = already selected
     */
    public Color getUnitTintColor(String objectId) {
        if (state != State.FORM_PARTY) return null;

        // Already in party
        for (String id : partyUnitIds) {
            if (objectId.equals(id)) {
                return TINT_IN_PARTY; // blue
            }
        }

        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();
        GameObject obj = gom.getObject(objectId);
        if (obj == null || !(obj instanceof Unit)) {
            return TINT_NON_UNIT; // dark — non-unit
        }

        if (!canSelectUnit(objectId)) {
            return TINT_INELIGIBLE; // grey — ineligible
        }

        return TINT_ELIGIBLE; // green — eligible
    }

    // ══════════════════════════════════════════════════════════════
    // LAYOUT
    // ══════════════════════════════════════════════════════════════

    private float getWorldWidth() { return LayoutConfig.WORLD_WIDTH; }
    private float getWorldHeight() { return viewport.getWorldHeight(); }
    private float getMenuX() { return MARGIN; }
    private float getMenuWidth() { return getWorldWidth() - MARGIN * 2; }
    private float getMenuTop() { return getWorldHeight() - MARGIN; }
    private float getMenuBottom() { return MARGIN; }
    private float getContentTop() { return getMenuTop() - HEADER_HEIGHT; }

    // ── Arrange-party slot geometry — shared by the background rects,
    // content text, and touch hit-test below, so all three can never drift
    // out of sync with each other (unlike a mismatch in most other panels'
    // row geometry, a mismatch here would mean taps hit the wrong slot). ──
    private float getArrangeGridStartX() {
        float cardsWidth = PARTY_SLOT_SIZE * 2 + PARTY_GAP;
        return getMenuX() + (getMenuWidth() - cardsWidth) / 2f;
    }
    private float getArrangeFrontRowY() {
        return getContentTop() - 70f - PARTY_SLOT_SIZE;
    }
    private float getArrangeBackRowY() {
        return getArrangeFrontRowY() - 40f - PARTY_SLOT_SIZE;
    }
    private float getArrangeSlotX(int slot) {
        return getArrangeGridStartX() + (slot % 2) * (PARTY_SLOT_SIZE + PARTY_GAP);
    }
    private float getArrangeSlotY(int slot) {
        return slot < 2 ? getArrangeFrontRowY() : getArrangeBackRowY();
    }
    private float getArrangeConfirmButtonY() {
        return getArrangeBackRowY() - 40f;
    }

    // ══════════════════════════════════════════════════════════════
    // DRAWING
    // ══════════════════════════════════════════════════════════════

    // TODO: add gold respawn button
    public void drawBackground(ShapeRenderer sr) {
        if (state == State.CLOSED) return;
        if (state == State.FORM_PARTY) { drawFormPartyBackground(sr); return; }

        Gdx.gl.glEnable(Gdx.gl.GL_BLEND);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        sr.setColor(0f, 0f, 0f, 0.75f);
        sr.rect(0, 0, getWorldWidth(), getWorldHeight());

        sr.setColor(0.12f, 0.12f, 0.18f, 1f);
        sr.rect(getMenuX(), getMenuBottom(), getMenuWidth(), getMenuTop() - getMenuBottom());

        sr.setColor(0.18f, 0.18f, 0.26f, 1f);
        sr.rect(getMenuX(), getMenuTop() - HEADER_HEIGHT, getMenuWidth(), HEADER_HEIGHT);

        if (state == State.ARRANGE_PARTY) {
            for (int slot = 0; slot < 4; slot++) {
                float x = getArrangeSlotX(slot);
                float y = getArrangeSlotY(slot);
                if (slot == arrangeSelectedSlot) {
                    sr.setColor(0.9f, 0.7f, 0.2f, 1f); // selected — gold highlight
                } else if (partyUnitIds[slot] != null) {
                    sr.setColor(0.2f, 0.24f, 0.32f, 1f); // filled
                } else {
                    sr.setColor(0.15f, 0.15f, 0.18f, 1f); // empty
                }
                sr.rect(x, y, PARTY_SLOT_SIZE, PARTY_SLOT_SIZE);
            }
        }

        sr.end();
        Gdx.gl.glDisable(Gdx.gl.GL_BLEND);
    }

    public void drawContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        if (state == State.CLOSED) return;

        switch (state) {
            case SELECT_CHAPTER: drawChapterSelect(batch, font, fontSmall); break;
            case SELECT_NODE:    drawNodeSelect(batch, font, fontSmall); break;
            case FORM_PARTY:     drawFormPartyContent(batch, font, fontSmall); break;
            case ARRANGE_PARTY:  drawArrangePartyContent(batch, font, fontSmall); break;
            case COMBAT:         drawCombat(batch, font, fontSmall); break;
            case RESULTS:        drawResults(batch, font, fontSmall); break;
        }

        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    // ── SELECT CHAPTER ──

    private void drawChapterSelect(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        font.setColor(Color.WHITE);
        font.draw(batch, "Raids", getMenuX() + 12f, getMenuTop() - 10f);
        font.draw(batch, "X", getMenuX() + getMenuWidth() - 28f, getMenuTop() - 10f);

        // Trophy count
        RaidManager rm = eventManager.getRaidManager();
        fontSmall.setColor(0.85f, 0.8f, 0.5f, 1f);
        fontSmall.draw(batch, "Trophies: " + rm.getWarTrophies()
                + "  |  Boss Tokens: " + rm.getBossTokens(),
            getMenuX() + 12f, getContentTop() - 8f);

        // Shop button
        fontSmall.setColor(0.85f, 0.8f, 0.5f, 1f);
        fontSmall.draw(batch, "[Shop]",
            getMenuX() + getMenuWidth() - 110f, getContentTop() - 8f);

        float rowY = getContentTop() - 36f;
        List<Map<String, Object>> chapters = rm.getChapters();

        for (int i = 0; i < chapters.size(); i++) {
            Map<String, Object> ch = chapters.get(i);
            String name = (String) ch.get("name");
            int unlock = ((Number) ch.get("unlockLevel")).intValue();
            String chId = (String) ch.get("id");
            boolean unlocked = rm.isChapterUnlocked(chId);

            float y = rowY - i * (ROW_HEIGHT + 4f);

            if (unlocked) {
                font.setColor(Color.WHITE);
                font.draw(batch, name, getMenuX() + 16f, y);
                fontSmall.setColor(0.6f, 0.6f, 0.7f, 1f);
                fontSmall.draw(batch, "Unlock: Lv" + unlock, getMenuX() + 16f, y - 22f);
            } else {
                font.setColor(0.4f, 0.4f, 0.5f, 1f);
                font.draw(batch, name + " — LOCKED", getMenuX() + 16f, y);
                fontSmall.setColor(0.35f, 0.35f, 0.45f, 1f);
                fontSmall.draw(batch, "Prince Lv" + unlock + " required",
                    getMenuX() + 16f, y - 22f);
            }
        }
    }

    // ── SELECT NODE ──

    @SuppressWarnings("unchecked")
    private void drawNodeSelect(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        font.setColor(Color.WHITE);
        Map<String, Object> chapter = eventManager.getRaidManager().getChapter(selectedChapterId);
        String chapterName = chapter != null ? (String) chapter.get("name") : selectedChapterId;
        font.draw(batch, chapterName, getMenuX() + 12f, getMenuTop() - 10f);

        fontSmall.setColor(0.6f, 0.8f, 0.6f, 1f);
        fontSmall.draw(batch, "< Back", getMenuX() + getMenuWidth() - 60f, getMenuTop() - 12f);

        RaidManager rm = eventManager.getRaidManager();
        List<Map<String, Object>> nodes = rm.getNodes(selectedChapterId);
        float rowY = getContentTop() - 16f;

        for (int i = 0; i < nodes.size(); i++) {
            Map<String, Object> node = nodes.get(i);
            String nodeId = (String) node.get("id");
            String name = (String) node.get("name");
            String type = (String) node.get("type");
            boolean unlocked = rm.isNodeUnlocked(selectedChapterId, nodeId);
            int stars = rm.getBestStarRating(selectedChapterId, nodeId);

            float y = rowY - i * (ROW_HEIGHT + 4f);

            if (unlocked) {
                font.setColor(Color.WHITE);
                String label = name;
                if ("boss".equals(type)) label = "★ " + name + " ★";
                else if ("side".equals(type)) label = "⊕ " + name;
                else if ("challenge".equals(type)) label = "⚔ " + name;
                font.draw(batch, label, getMenuX() + 16f, y);

                // Star rating
                fontSmall.setColor(0.85f, 0.8f, 0.5f, 1f);
                StringBuilder starStr = new StringBuilder();
                for (int s = 0; s < 3; s++) starStr.append(s < stars ? "★" : "☆");
                fontSmall.draw(batch, starStr.toString(),
                    getMenuX() + getMenuWidth() - 50f, y);

                // Rooms count, or Boss Token entry cost for challenge nodes
                // (shown instead so a tap on Start doesn't silently fail
                // from RaidManager.startRaid's token guard with no warning).
                Object tokenCostObj = node.get("tokenCost");
                int tokenCost = tokenCostObj instanceof Number ? ((Number) tokenCostObj).intValue() : 0;
                fontSmall.setColor(0.5f, 0.5f, 0.6f, 1f);
                if (tokenCost > 0) {
                    fontSmall.draw(batch, "Cost: " + tokenCost + " Boss Tokens",
                        getMenuX() + 16f, y - 20f);
                } else {
                    List<Map<String, Object>> rooms = (List<Map<String, Object>>) node.get("rooms");
                    fontSmall.draw(batch, (rooms != null ? rooms.size() : 0) + " rooms",
                        getMenuX() + 16f, y - 20f);
                }
            } else {
                font.setColor(0.4f, 0.4f, 0.5f, 1f);
                font.draw(batch, name + " — LOCKED", getMenuX() + 16f, y);
            }
        }
    }

    // ── FORM PARTY ──

    private void drawFormPartyBackground(ShapeRenderer sr) {
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0.12f, 0.12f, 0.18f, 0.95f);
        sr.rect(0, getWorldHeight() - SELECT_HEADER_HEIGHT,
            getWorldWidth(), SELECT_HEADER_HEIGHT);
        sr.setColor(0.9f, 0.5f, 0.2f, 1f);
        sr.rect(0, getWorldHeight() - SELECT_HEADER_HEIGHT, getWorldWidth(), 2f);
        sr.end();
    }

    private void drawFormPartyContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        float top = getWorldHeight();

        fontSmall.setColor(0.6f, 0.8f, 0.6f, 1f);
        fontSmall.draw(batch, "< Back", 12f, top - 14f);

        font.setColor(0.9f, 0.5f, 0.2f, 1f);
        font.draw(batch, "Form Party", 80f, top - 14f);

        // Party slots
        float slotStartX = 12f;
        fontSmall.setColor(0.7f, 0.7f, 0.8f, 1f);
        StringBuilder partyText = new StringBuilder("Party: ");
        for (int i = 0; i < 4; i++) {
            if (partyUnitIds[i] != null) {
                GameObject obj = eventManager.getGRID_OBJECT_MANAGER().getObject(partyUnitIds[i]);
                if (obj != null) {
                    String pos = (i < 2) ? "[Front] " : "[Back] ";
                    partyText.append(pos).append(TextUtil.capitalize(obj.getType())).append("  ");
                }
            } else {
                String pos = (i < 2) ? "[Front] " : "[Back] ";
                partyText.append(pos).append("Empty  ");
            }
        }
        fontSmall.draw(batch, partyText.toString(), 12f, top - 34f);

        // Instructions + Start button
        fontSmall.setColor(0.5f, 0.5f, 0.6f, 1f);
        fontSmall.draw(batch, "Tap units to add (slots 0-1 = front, 2-3 = back)",
            12f, top - 54f);

        if (partySlotFilling > 0) {
            fontSmall.setColor(0.3f, 0.9f, 0.3f, 1f);
            fontSmall.draw(batch, "[ARRANGE PARTY]",
                getWorldWidth() - 130f, top - 74f);
        }

        // Legend
        fontSmall.setColor(0.2f, 0.85f, 0.2f, 1f);
        fontSmall.draw(batch, "●", 12f, top - 78f);
        fontSmall.setColor(0.6f, 0.6f, 0.7f, 1f);
        fontSmall.draw(batch, "Eligible  ", 24f, top - 78f);

        fontSmall.setColor(0.3f, 0.5f, 0.9f, 1f);
        fontSmall.draw(batch, "●", 100f, top - 78f);
        fontSmall.setColor(0.6f, 0.6f, 0.7f, 1f);
        fontSmall.draw(batch, "In party", 112f, top - 78f);

        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    // ── ARRANGE PARTY ──

    private void drawArrangePartyContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        font.setColor(0.9f, 0.5f, 0.2f, 1f);
        font.draw(batch, "Arrange Party", getMenuX() + 12f, getMenuTop() - 10f);
        font.setColor(Color.WHITE);
        font.draw(batch, "X", getMenuX() + getMenuWidth() - 28f, getMenuTop() - 10f);

        fontSmall.setColor(0.6f, 0.8f, 0.6f, 1f);
        fontSmall.draw(batch, "< Back", getMenuX() + 12f, getMenuTop() - 28f);

        fontSmall.setColor(0.7f, 0.7f, 0.8f, 1f);
        fontSmall.draw(batch,
            "Front absorbs 50%/30% of incoming damage, Back 10%/10%. Tap two slots to swap.",
            getMenuX() + 12f, getContentTop() - 12f, getMenuWidth() - 24f,
            com.badlogic.gdx.utils.Align.left, true);

        fontSmall.setColor(0.85f, 0.6f, 0.3f, 1f);
        fontSmall.draw(batch, "FRONT", getArrangeGridStartX(), getArrangeFrontRowY() + PARTY_SLOT_SIZE + 16f);
        fontSmall.setColor(0.5f, 0.6f, 0.85f, 1f);
        fontSmall.draw(batch, "BACK", getArrangeGridStartX(), getArrangeBackRowY() + PARTY_SLOT_SIZE + 16f);

        for (int slot = 0; slot < 4; slot++) {
            drawArrangeSlotLabel(batch, fontSmall, slot);
        }

        String confirmLabel = "[ Confirm & Start Raid ]";
        fontSmall.setColor(0.3f, 0.9f, 0.3f, 1f);
        glyphLayout.setText(fontSmall, confirmLabel);
        fontSmall.draw(batch, confirmLabel,
            getMenuX() + getMenuWidth() / 2f - glyphLayout.width / 2f,
            getArrangeConfirmButtonY());

        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    private void drawArrangeSlotLabel(SpriteBatch batch, BitmapFont fontSmall, int slot) {
        float x = getArrangeSlotX(slot);
        float y = getArrangeSlotY(slot);
        String unitId = partyUnitIds[slot];

        if (unitId == null) {
            fontSmall.setColor(0.4f, 0.4f, 0.45f, 1f);
            String label = "Empty";
            glyphLayout.setText(fontSmall, label);
            fontSmall.draw(batch, label,
                x + PARTY_SLOT_SIZE / 2f - glyphLayout.width / 2f, y + PARTY_SLOT_SIZE / 2f);
            return;
        }

        GameObject obj = eventManager.getGRID_OBJECT_MANAGER().getObject(unitId);
        if (obj == null) return;

        fontSmall.setColor(slot == arrangeSelectedSlot ? Color.BLACK : Color.WHITE);
        String name = TextUtil.capitalize(obj.getType());
        glyphLayout.setText(fontSmall, name);
        fontSmall.draw(batch, name,
            x + PARTY_SLOT_SIZE / 2f - glyphLayout.width / 2f, y + PARTY_SLOT_SIZE / 2f + 10f);

        String lvl = "Lv." + obj.getLvl();
        glyphLayout.setText(fontSmall, lvl);
        fontSmall.draw(batch, lvl,
            x + PARTY_SLOT_SIZE / 2f - glyphLayout.width / 2f, y + PARTY_SLOT_SIZE / 2f - 8f);
    }

    // ── COMBAT ──

    private void drawCombat(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        RaidManager rm = eventManager.getRaidManager();
        RaidState raid = rm.getActiveRaid();
        if (raid == null) return;

        // Auto-transition to results
        if (raid.isCompleted() || raid.isFailed()) {
            state = State.RESULTS;
            drawResults(batch, font, fontSmall);
            return;
        }

        // Header
        font.setColor(Color.WHITE);
        font.draw(batch, "Room " + (raid.getCurrentRoomIndex() + 1),
            getMenuX() + 12f, getMenuTop() - 10f);

        // Last combat-log line (small, unobtrusive)
        java.util.List<String> log = rm.getCombatLog();
        if (!log.isEmpty()) {
            fontSmall.setColor(0.55f, 0.55f, 0.65f, 1f);
            fontSmall.draw(batch, log.get(log.size() - 1),
                getMenuX() + 12f, getMenuBottom() + 118f,
                getMenuWidth() - 24f, com.badlogic.gdx.utils.Align.left, true);
        }

        // Sprites, HP bars labels, damage numbers, fury label, banner
        arena.drawBatch(batch, font, fontSmall);

        // Side buttons (right side of control bar)
        Inventory inv = eventManager.getInventory();
        float btnX = getMenuX() + getMenuWidth() - 96f;
        float btnY = getMenuBottom() + 16f;

        // Counts only — countItemsByType avoids allocating the ArrayList
        // getItemsByType would build just to read .size()/.isEmpty() here.
        int potionCount = inv.countItemsByType("potion");
        if (potionCount == 0) fontSmall.setColor(0.4f, 0.4f, 0.45f, 1f);
        else                  fontSmall.setColor(0.3f, 0.9f, 0.3f, 1f);
        fontSmall.draw(batch, "[Potion x" + potionCount + "]", btnX, btnY + 72f);

        int featherCount = inv.countItemsByType("phoenix_feather");
        if (featherCount == 0 || raid.getDeathCount() == 0) fontSmall.setColor(0.4f, 0.4f, 0.45f, 1f);
        else                                                 fontSmall.setColor(0.9f, 0.6f, 0.2f, 1f);
        fontSmall.draw(batch, "[Revive x" + featherCount + "]", btnX, btnY + 48f);

        fontSmall.setColor(0.7f, 0.3f, 0.3f, 1f);
        fontSmall.draw(batch, "[Abandon]", btnX, btnY + 24f);

        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    // ── RESULTS ──

    private void drawResults(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        RaidManager rm = eventManager.getRaidManager();
        RaidState raid = rm.getActiveRaid();
        if (raid == null) return;

        // Header
        font.setColor(raid.isCompleted()
            ? new Color(0.3f, 0.9f, 0.3f, 1f)
            : new Color(0.9f, 0.3f, 0.3f, 1f));
        font.draw(batch, raid.isCompleted() ? "VICTORY!" : "DEFEAT",
            getMenuX() + 12f, getMenuTop() - 10f);

        float y = getContentTop() - 16f;

        // Stars
        if (raid.isCompleted()) {
            int stars = raid.getStarRating();
            fontSmall.setColor(0.85f, 0.8f, 0.5f, 1f);
            StringBuilder starStr = new StringBuilder("Rating: ");
            for (int s = 0; s < 3; s++) starStr.append(s < stars ? "★" : "☆");
            fontSmall.draw(batch, starStr.toString(), getMenuX() + 12f, y);
            y -= 24f;
        }

        // Deaths
        fontSmall.setColor(0.7f, 0.7f, 0.8f, 1f);
        fontSmall.draw(batch, "Deaths: " + raid.getDeathCount(), getMenuX() + 12f, y);
        y -= 24f;

        // Trophies
        fontSmall.setColor(0.85f, 0.8f, 0.5f, 1f);
        fontSmall.draw(batch, "War Trophies: +" + raid.getTrophiesEarned(),
            getMenuX() + 12f, y);
        y -= 24f;

        // Boss tokens
        if (raid.getBossTokens() > 0) {
            fontSmall.setColor(0.9f, 0.6f, 0.2f, 1f);
            fontSmall.draw(batch, "Boss Tokens: +" + raid.getBossTokens(),
                getMenuX() + 12f, y);
            y -= 24f;
        }

        // Enchanted drop
        if (raid.getEnchantedDrop() != null) {
            fontSmall.setColor(0.6f, 0.3f, 0.9f, 1f);
            fontSmall.draw(batch, "Enchanted Drop: " + TextUtil.capitalize(raid.getEnchantedDrop())
                + " Set!", getMenuX() + 12f, y);
            y -= 24f;
        }

        // Continue button
        y -= 20f;
        fontSmall.setColor(0.3f, 0.9f, 0.3f, 1f);
        fontSmall.draw(batch, "[Continue]",
            getMenuX() + getMenuWidth() / 2f - 30f, y);
    }

    // ══════════════════════════════════════════════════════════════
    // TOUCH HANDLING
    // ══════════════════════════════════════════════════════════════

    public boolean handleTouchDown(int screenX, int screenY) {
        if (state == State.CLOSED) return false;
        if (state == State.FORM_PARTY) {
            touchPos.set(screenX, screenY);
            viewport.unproject(touchPos);
            // Back button
            if (touchPos.y > getWorldHeight() - SELECT_HEADER_HEIGHT && touchPos.x < 80f) {
                state = State.SELECT_NODE;
                resetParty();
                return true;
            }
            // Arrange-party button — transitions to ARRANGE_PARTY instead of
            // starting the raid directly; the actual startRaidWithParty()
            // call now lives on ARRANGE_PARTY's own confirm button, after
            // the player has had a chance to reorder front/back slots.
            if (partySlotFilling > 0
                && touchPos.x > getWorldWidth() - 140f
                && touchPos.y > getWorldHeight() - SELECT_HEADER_HEIGHT) {
                state = State.ARRANGE_PARTY;
                arrangeSelectedSlot = -1;
                return true;
            }
            if (touchPos.y > getWorldHeight() - SELECT_HEADER_HEIGHT) return true;
            return false; // let grid handle unit selection
        }
        return true; // consume all touches for other states
    }

    public boolean handleTouchUp(int screenX, int screenY) {
        if (state == State.CLOSED || state == State.FORM_PARTY) return false;

        touchPos.set(screenX, screenY);
        viewport.unproject(touchPos);

        switch (state) {
            case SELECT_CHAPTER: return handleChapterTouch();
            case SELECT_NODE:    return handleNodeTouch();
            case ARRANGE_PARTY:  return handleArrangeTouch();
            case COMBAT:         return handleCombatTouch();
            case RESULTS:        return handleResultsTouch();
        }
        return true;
    }

    public boolean handleTouchDragged(int screenX, int screenY) {
        return state != State.CLOSED && state != State.FORM_PARTY;
    }

    @SuppressWarnings("unchecked")
    private boolean handleChapterTouch() {
        // Close button
        if (touchPos.x > getMenuX() + getMenuWidth() - 40f
            && touchPos.y > getMenuTop() - HEADER_HEIGHT) {
            close();
            return true;
        }

        // Outside menu
        if (touchPos.x < getMenuX() || touchPos.x > getMenuX() + getMenuWidth()
            || touchPos.y < getMenuBottom()) {
            close();
            return true;
        }

        if (touchPos.y >= getContentTop() - 20f && touchPos.y <= getContentTop()
            && touchPos.x >= getMenuX() + getMenuWidth() - 120f) {
            // Open the unified shop on the Equipment tab
            if (unifiedShopPanel != null) {
                unifiedShopPanel.open(UnifiedShopPanel.TAB_EQUIPMENT);
            }
            return true;
        }

        // Chapter rows
        RaidManager rm = eventManager.getRaidManager();
        List<Map<String, Object>> chapters = rm.getChapters();
        float rowY = getContentTop() - 36f;

        for (int i = 0; i < chapters.size(); i++) {
            float y = rowY - i * (ROW_HEIGHT + 4f);
            if (touchPos.y <= y && touchPos.y >= y - ROW_HEIGHT) {
                String chId = (String) chapters.get(i).get("id");
                if (rm.isChapterUnlocked(chId)) {
                    selectedChapterId = chId;
                    state = State.SELECT_NODE;
                }
                return true;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean handleNodeTouch() {
        // Back button
        if (touchPos.y > getMenuTop() - HEADER_HEIGHT) {
            state = State.SELECT_CHAPTER;
            selectedChapterId = null;
            return true;
        }

        RaidManager rm = eventManager.getRaidManager();
        List<Map<String, Object>> nodes = rm.getNodes(selectedChapterId);
        float rowY = getContentTop() - 16f;

        for (int i = 0; i < nodes.size(); i++) {
            float y = rowY - i * (ROW_HEIGHT + 4f);
            if (touchPos.y <= y && touchPos.y >= y - ROW_HEIGHT) {
                String nodeId = (String) nodes.get(i).get("id");
                if (rm.isNodeUnlocked(selectedChapterId, nodeId)) {
                    selectedNodeId = nodeId;
                    state = State.FORM_PARTY;
                    resetParty();
                }
                return true;
            }
        }
        return true;
    }

    private boolean handleCombatTouch() {
        RaidManager rm = eventManager.getRaidManager();
        RaidState raid = rm.getActiveRaid();
        if (raid == null) return true;

        // ── Fury button ──
        if (arena.isFuryButtonHit(touchPos.x, touchPos.y)) {
            rm.tapFury();
            return true;
        }

        // ── Side buttons ──
        float btnX = getMenuX() + getMenuWidth() - 100f;
        float btnY = getMenuBottom() + 16f;

        if (touchPos.x >= btnX) {
            // Potion
            if (touchPos.y >= btnY + 56f && touchPos.y <= btnY + 84f) {
                java.util.List<Item> potions =
                    eventManager.getInventory().getItemsByType("potion");
                if (!potions.isEmpty()) {
                    for (int i = 0; i < 4; i++) {
                        if (raid.isSlotAlive(i)
                            && raid.getPartyCurrentHp()[i] < raid.getPartyMaxHp()[i]) {
                            rm.usePotion(i, potions.get(0).getId());
                            break;
                        }
                    }
                }
                return true;
            }
            // Revive
            if (touchPos.y >= btnY + 32f && touchPos.y < btnY + 56f) {
                java.util.List<Item> feathers =
                    eventManager.getInventory().getItemsByType("phoenix_feather");
                if (!feathers.isEmpty()) {
                    for (int i = 0; i < 4; i++) {
                        if (raid.isSlotOccupied(i) && raid.getPartyDead()[i]) {
                            rm.usePhoenixFeather(i, feathers.get(0).getId());
                            break;
                        }
                    }
                }
                return true;
            }
            // Revive party
            if (arena.isReviveButtonHit(touchPos.x, touchPos.y)) {
                if (raid.getDeathCount() > 0) {
                    eventManager.getGoldManager().reviveRaidParty();
                }
                return true;
            }
            // Abandon
            if (touchPos.y >= btnY && touchPos.y < btnY + 32f) {
                rm.abandonRaid();
                state = State.RESULTS;
                return true;
            }
        }

        return true;
    }

    private boolean handleResultsTouch() {
        // Continue button — end raid and close
        RaidManager rm = eventManager.getRaidManager();
        rm.endRaid();
        close();
        return true;
    }

    private boolean handleArrangeTouch() {
        // Close button (X) — fully closes the panel, same as every other state.
        if (touchPos.x > getMenuX() + getMenuWidth() - 40f
            && touchPos.y > getMenuTop() - HEADER_HEIGHT) {
            close();
            return true;
        }

        // Back — returns to FORM_PARTY WITHOUT resetting the party, so the
        // player can add more units or just double-check the roster without
        // losing their arrangement. Safe because onUnitSelected now scans
        // for the first empty slot instead of trusting partySlotFilling as
        // a direct index (see its comment).
        if (touchPos.x < getMenuX() + 70f
            && touchPos.y >= getMenuTop() - 34f && touchPos.y <= getMenuTop() - 14f) {
            state = State.FORM_PARTY;
            arrangeSelectedSlot = -1;
            return true;
        }

        // Slot taps — tap one slot to select it, tap a second (different)
        // slot to swap their contents, tap the same slot again to deselect.
        for (int slot = 0; slot < 4; slot++) {
            float x = getArrangeSlotX(slot);
            float y = getArrangeSlotY(slot);
            if (touchPos.x >= x && touchPos.x <= x + PARTY_SLOT_SIZE
                && touchPos.y >= y && touchPos.y <= y + PARTY_SLOT_SIZE) {
                if (arrangeSelectedSlot == -1) {
                    arrangeSelectedSlot = slot;
                } else if (arrangeSelectedSlot == slot) {
                    arrangeSelectedSlot = -1;
                } else {
                    String tmp = partyUnitIds[arrangeSelectedSlot];
                    partyUnitIds[arrangeSelectedSlot] = partyUnitIds[slot];
                    partyUnitIds[slot] = tmp;
                    arrangeSelectedSlot = -1;
                }
                return true;
            }
        }

        // Confirm button
        float btnY = getArrangeConfirmButtonY();
        if (touchPos.y >= btnY - 6f && touchPos.y <= btnY + 20f
            && touchPos.x >= getMenuX() && touchPos.x <= getMenuX() + getMenuWidth()) {
            startRaidWithParty();
            return true;
        }

        return true;
    }

    private void startRaidWithParty() {
        RaidManager rm = eventManager.getRaidManager();
        boolean started = rm.startRaid(selectedChapterId, selectedNodeId, partyUnitIds);
        if (started) {
            arena.reset();
            state = State.COMBAT;
        }
    }

    public void update(float delta) {
        if (state == State.COMBAT) arena.update(delta);
    }

    /**
     * Draws the whole panel with correct shape/batch sequencing:
     * shapes fully outside the batch, sprites/text inside one batch pass.
     */
    public void draw(ShapeRenderer sr, SpriteBatch batch,
                     BitmapFont font, BitmapFont fontSmall) {
        drawBackground(sr);                       // dim + panel (own begin/end)
        if (state == State.COMBAT) {
            arena.drawShapes(sr);                 // HP bars + fury bar (own begin/end)
        }
        batch.begin();
        drawContent(batch, font, fontSmall);      // headers, non-combat states
        batch.end();
    }

}
