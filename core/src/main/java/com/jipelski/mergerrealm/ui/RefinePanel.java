package com.jipelski.mergerrealm.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;

import com.jipelski.mergerrealm.model.Item;
import com.jipelski.mergerrealm.util.EnchantedSetManager;
import com.jipelski.mergerrealm.util.EventManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Refine" — the duplicate-equipment sink alongside Salvage. Triggered by
 * the [Refine] header button on InventoryMenu's Equipment tab (see
 * InventoryMenu.setRefinePanel()). Where Salvage converts unwanted low-tier
 * duplicates into Gold, Refine gives ANY duplicate (any tier) a use: two
 * unequipped copies of the same (type, level, refineLevel) combine into one
 * copy at refineLevel+1, up to +9 (EventManager.MAX_REFINE_LEVEL), each
 * level baking a flat stat bonus into the resulting item
 * (EventManager.refineItem()). The two systems don't compete — a player
 * chooses per-duplicate whether it's fodder or salvage.
 *
 * Sourced exclusively from Inventory.getUnequippedItems() — same
 * equipped-item exclusion as SalvagePanel, for the same reason
 * (EventManager.refineItem()'s javadoc). To refine a currently-equipped
 * item, unequip it first.
 *
 * Shape: a scrollable list of "groups" (one row per distinct type/level/
 * refineLevel combination present, each showing its live copy count), one
 * tap per row performs exactly one refine step — lifts the scroll +
 * tap-vs-drag disambiguation pattern from UnifiedShopPanel's trophy tabs
 * (touchStartY/scrolling), since this list can run longer than Salvage's
 * fixed single-screen card.
 */
public class RefinePanel {

    private static final String TAG = "RefinePanel";

    private static final float MARGIN = 16f;
    private static final float HEADER_HEIGHT = 40f;
    private static final float ROW_HEIGHT = 44f;
    private static final float ROW_GAP = 4f;

    private final EventManager eventManager;
    private final Viewport viewport;
    private final GlyphLayout glyphLayout;
    private final Vector2 touchPos = new Vector2();

    private boolean visible = false;

    // ── Scroll ──
    private float scrollY = 0f;
    private float maxScrollY = 0f;
    private boolean touchDown = false;
    private float touchStartY = 0f;
    private boolean scrolling = false;

    private String lastResultText;

    private static class RefineGroup {
        String type; int level; int refineLevel;
        String displayName; String spritePath;
        int count;
    }

    private final List<RefineGroup> groups = new ArrayList<>();

    public RefinePanel(EventManager eventManager, Viewport viewport, UITextureManager uiTex) {
        this.eventManager = eventManager;
        this.viewport = viewport;
        this.glyphLayout = new GlyphLayout();
        // uiTex accepted for constructor-signature symmetry with sibling
        // popups (SalvagePanel/HiddenTemplePopup/OutfitPanel).
    }

    public boolean isVisible() { return visible; }

    public void open() {
        lastResultText = null;
        recomputeGroups();
        visible = true;
    }

    public void close() { visible = false; }

    // ══════════════════════════════════════════════════════════════
    // GROUP COMPUTATION
    // ══════════════════════════════════════════════════════════════

    private boolean isRefinableType(String type) {
        return "sword".equals(type) || "shield".equals(type) || "amulet".equals(type)
            || EnchantedSetManager.isEnchantedItem(type);
    }

    /** Rebuilds the grouped list from Inventory.getUnequippedItems() — called on open() and after every refine. */
    private void recomputeGroups() {
        Map<String, RefineGroup> byKey = new LinkedHashMap<>();
        for (Item item : eventManager.getInventory().getUnequippedItems()) {
            if (!isRefinableType(item.getType())) continue;

            String key = item.getType() + "|" + item.getLevel() + "|" + item.getRefineLevel();
            RefineGroup g = byKey.get(key);
            if (g == null) {
                g = new RefineGroup();
                g.type = item.getType();
                g.level = item.getLevel();
                g.refineLevel = item.getRefineLevel();
                g.displayName = item.getDisplayName();
                g.spritePath = item.getSpritePath();
                byKey.put(key, g);
            }
            g.count++;
        }

        groups.clear();
        groups.addAll(byKey.values());
        groups.sort(Comparator.<RefineGroup, String>comparing(g -> g.type)
            .thenComparingInt(g -> g.level)
            .thenComparingInt(g -> g.refineLevel));

        recalculateScroll();
    }

    private void recalculateScroll() {
        float totalHeight = groups.size() * (ROW_HEIGHT + ROW_GAP);
        maxScrollY = Math.max(0, totalHeight - getContentHeight());
        scrollY = Math.min(scrollY, maxScrollY);
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
    private float getContentHeight() { return getContentTop() - getMenuBottom(); }

    // ══════════════════════════════════════════════════════════════
    // DRAWING
    // ══════════════════════════════════════════════════════════════

    public void drawBackground(ShapeRenderer sr) {
        if (!visible) return;
        Gdx.gl.glEnable(Gdx.gl.GL_BLEND);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        sr.setColor(0f, 0f, 0f, 0.75f);
        sr.rect(0, 0, getWorldWidth(), getWorldHeight());

        sr.setColor(0.12f, 0.12f, 0.18f, 1f);
        sr.rect(getMenuX(), getMenuBottom(), getMenuWidth(), getMenuTop() - getMenuBottom());

        sr.setColor(0.16f, 0.14f, 0.2f, 1f); // violet-tinted header — distinct from Salvage's gold tint
        sr.rect(getMenuX(), getContentTop(), getMenuWidth(), HEADER_HEIGHT);

        float y = getContentTop() + scrollY;
        for (RefineGroup g : groups) {
            y -= (ROW_HEIGHT + ROW_GAP);
            if (y + ROW_HEIGHT < getMenuBottom() || y > getContentTop()) continue;

            boolean canRefine = g.count >= 2 && g.refineLevel < EventManager.MAX_REFINE_LEVEL;
            if (canRefine) sr.setColor(0.2f, 0.22f, 0.32f, 1f);
            else           sr.setColor(0.16f, 0.16f, 0.2f, 1f);
            sr.rect(getMenuX() + 8f, y, getMenuWidth() - 16f, ROW_HEIGHT);
        }

        sr.end();
        Gdx.gl.glDisable(Gdx.gl.GL_BLEND);
    }

    public void drawContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        if (!visible) return;

        font.setColor(0.7f, 0.5f, 0.9f, 1f);
        font.draw(batch, "Refine Equipment", getMenuX() + 12f, getMenuTop() - 12f);
        font.setColor(Color.WHITE);
        font.draw(batch, "X", getMenuX() + getMenuWidth() - 24f, getMenuTop() - 12f);

        if (groups.isEmpty()) {
            fontSmall.setColor(0.4f, 0.4f, 0.5f, 1f);
            String empty = "No unequipped duplicates yet — need 2 copies of the same item to refine.";
            fontSmall.draw(batch, empty, getMenuX() + 12f, getContentTop() - 24f,
                getMenuWidth() - 24f, com.badlogic.gdx.utils.Align.left, true);
            font.setColor(Color.WHITE);
            fontSmall.setColor(Color.WHITE);
            return;
        }

        float y = getContentTop() + scrollY;
        for (RefineGroup g : groups) {
            y -= (ROW_HEIGHT + ROW_GAP);
            if (y + ROW_HEIGHT < getMenuBottom() || y > getContentTop()) continue;

            boolean maxed = g.refineLevel >= EventManager.MAX_REFINE_LEVEL;
            boolean canRefine = g.count >= 2 && !maxed;

            fontSmall.setColor(0.85f, 0.85f, 0.9f, 1f);
            fontSmall.draw(batch, g.displayName + "  (Lv." + g.level + ")  x" + g.count,
                getMenuX() + 16f, y + ROW_HEIGHT - 12f);

            String rightLabel;
            if (maxed) {
                fontSmall.setColor(0.9f, 0.75f, 0.2f, 1f);
                rightLabel = "MAX";
            } else if (canRefine) {
                fontSmall.setColor(0.3f, 0.9f, 0.3f, 1f);
                rightLabel = "[ Refine 2→1 ]";
            } else {
                fontSmall.setColor(0.5f, 0.5f, 0.58f, 1f);
                rightLabel = "need " + (2 - g.count) + " more";
            }
            glyphLayout.setText(fontSmall, rightLabel);
            fontSmall.draw(batch, rightLabel,
                getMenuX() + getMenuWidth() - 24f - glyphLayout.width, y + ROW_HEIGHT - 12f);

            if (canRefine) {
                fontSmall.setColor(0.6f, 0.6f, 0.68f, 1f);
                float mult = 1f + 0.15f * (g.refineLevel + 1);
                String preview = "-> +" + (g.refineLevel + 1) + " (×" + String.format("%.2f", mult) + " stats)";
                fontSmall.draw(batch, preview, getMenuX() + 16f, y + 14f);
            }
        }

        if (lastResultText != null) {
            fontSmall.setColor(0.6f, 0.9f, 0.6f, 1f);
            fontSmall.draw(batch, lastResultText, getMenuX() + 12f, getMenuBottom() - 4f);
        }

        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    // ══════════════════════════════════════════════════════════════
    // TOUCH
    // ══════════════════════════════════════════════════════════════

    public boolean handleTouchDown(int screenX, int screenY) {
        if (!visible) return false;
        touchPos.set(screenX, screenY);
        viewport.unproject(touchPos);
        touchDown = true;
        touchStartY = touchPos.y;
        scrolling = false;
        return true;
    }

    public boolean handleTouchDragged(int screenX, int screenY) {
        if (!visible || !touchDown) return false;
        touchPos.set(screenX, screenY);
        viewport.unproject(touchPos);

        float dy = touchPos.y - touchStartY;
        if (Math.abs(dy) > 6f) scrolling = true;

        if (scrolling) {
            scrollY += dy * 0.5f;
            scrollY = Math.max(0, Math.min(scrollY, maxScrollY));
            touchStartY = touchPos.y;
        }
        return true;
    }

    public boolean handleTouchUp(int screenX, int screenY) {
        if (!visible) return false;
        touchPos.set(screenX, screenY);
        viewport.unproject(touchPos);
        touchDown = false;

        if (scrolling) { scrolling = false; return true; }

        // X button
        if (touchPos.x > getMenuX() + getMenuWidth() - 36f && touchPos.y > getContentTop()) {
            close();
            return true;
        }
        // Outside the card
        if (touchPos.x < getMenuX() || touchPos.x > getMenuX() + getMenuWidth()
            || touchPos.y < getMenuBottom() || touchPos.y > getMenuTop()) {
            close();
            return true;
        }

        // Row taps
        float y = getContentTop() + scrollY;
        for (RefineGroup g : groups) {
            y -= (ROW_HEIGHT + ROW_GAP);
            if (touchPos.y >= y && touchPos.y <= y + ROW_HEIGHT) {
                if (g.count >= 2 && g.refineLevel < EventManager.MAX_REFINE_LEVEL) {
                    EventManager.RefineResult result =
                        eventManager.refineItem(g.type, g.level, g.refineLevel);
                    if (result.success) {
                        lastResultText = "Refined into " + result.newItem.getDisplayName()
                            + "! (+" + result.xpGained + " XP)";
                        recomputeGroups();
                    }
                }
                return true;
            }
        }

        return true;
    }
}
