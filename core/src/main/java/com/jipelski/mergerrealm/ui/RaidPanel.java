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
 *   COMBAT         — live auto-combat view
 *   RESULTS        — raid complete/failed summary
 */
public class RaidPanel {

    private static final String TAG = "RaidPanel";

    public enum State { CLOSED, SELECT_CHAPTER, SELECT_NODE, FORM_PARTY, COMBAT, RESULTS }
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

    // ── Selection state ──
    private String selectedChapterId = null;
    private String selectedNodeId = null;
    private String[] partyUnitIds = new String[4];
    private int partySlotFilling = 0; // which slot we're filling next (0-3)

    // ── Combat scroll ──
    private float logScrollY = 0f;

    // ── References ──
    private final EventManager eventManager;
    private final SpriteManager spriteManager;
    private final Viewport viewport;
    private final UITextureManager uiTex;
    private final GlyphLayout glyphLayout;

    private final RaidArenaRenderer arena;

    private TrophyShopPanel trophyShopPanel;
    public void setTrophyShopPanel(TrophyShopPanel panel) {
        this.trophyShopPanel = panel;
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

        partyUnitIds[partySlotFilling] = unitId;
        partySlotFilling++;

        Gdx.app.log(TAG, "Added unit to party slot " + (partySlotFilling - 1)
            + ": " + unitId);
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
                return new Color(0.3f, 0.5f, 0.9f, 0.4f); // blue
            }
        }

        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();
        GameObject obj = gom.getObject(objectId);
        if (obj == null || !(obj instanceof Unit)) {
            return new Color(0.15f, 0.15f, 0.15f, 0.6f); // dark — non-unit
        }

        if (!canSelectUnit(objectId)) {
            return new Color(0.3f, 0.3f, 0.3f, 0.5f); // grey — ineligible
        }

        return new Color(0.2f, 0.85f, 0.2f, 0.35f); // green — eligible
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

        sr.end();
        Gdx.gl.glDisable(Gdx.gl.GL_BLEND);
    }

    public void drawContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        if (state == State.CLOSED) return;

        switch (state) {
            case SELECT_CHAPTER: drawChapterSelect(batch, font, fontSmall); break;
            case SELECT_NODE:    drawNodeSelect(batch, font, fontSmall); break;
            case FORM_PARTY:     drawFormPartyContent(batch, font, fontSmall); break;
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

        // Trophy button
        fontSmall.setColor(0.85f, 0.8f, 0.5f, 1f);
        fontSmall.draw(batch, "[Trophy Shop]",
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
                font.draw(batch, label, getMenuX() + 16f, y);

                // Star rating
                fontSmall.setColor(0.85f, 0.8f, 0.5f, 1f);
                StringBuilder starStr = new StringBuilder();
                for (int s = 0; s < 3; s++) starStr.append(s < stars ? "★" : "☆");
                fontSmall.draw(batch, starStr.toString(),
                    getMenuX() + getMenuWidth() - 50f, y);

                // Rooms count
                List<Map<String, Object>> rooms = (List<Map<String, Object>>) node.get("rooms");
                fontSmall.setColor(0.5f, 0.5f, 0.6f, 1f);
                fontSmall.draw(batch, (rooms != null ? rooms.size() : 0) + " rooms",
                    getMenuX() + 16f, y - 20f);
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
                    partyText.append(pos).append(capitalize(obj.getType())).append("  ");
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
            fontSmall.draw(batch, "[START RAID]",
                getWorldWidth() - 100f, top - 74f);
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

        java.util.List<Item> potions = inv.getItemsByType("potion");
        fontSmall.setColor(potions.isEmpty()
            ? new Color(0.4f, 0.4f, 0.45f, 1f) : new Color(0.3f, 0.9f, 0.3f, 1f));
        fontSmall.draw(batch, "[Potion x" + potions.size() + "]", btnX, btnY + 72f);

        java.util.List<Item> feathers = inv.getItemsByType("phoenix_feather");
        fontSmall.setColor(feathers.isEmpty() || raid.getDeathCount() == 0
            ? new Color(0.4f, 0.4f, 0.45f, 1f) : new Color(0.9f, 0.6f, 0.2f, 1f));
        fontSmall.draw(batch, "[Revive x" + feathers.size() + "]", btnX, btnY + 48f);

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
            fontSmall.draw(batch, "Enchanted Drop: " + capitalize(raid.getEnchantedDrop())
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
            // Start raid button
            if (partySlotFilling > 0
                && touchPos.x > getWorldWidth() - 110f
                && touchPos.y > getWorldHeight() - SELECT_HEADER_HEIGHT) {
                startRaidWithParty();
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
            // Open trophy shop — need a reference to the panel
            if (trophyShopPanel != null) {
                trophyShopPanel.open();
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

    // ── Helper ──

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
