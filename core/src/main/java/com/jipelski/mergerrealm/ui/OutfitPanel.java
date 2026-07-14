package com.jipelski.mergerrealm.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;

import com.jipelski.mergerrealm.util.EventManager;
import com.jipelski.mergerrealm.util.GoldManager;
import com.jipelski.mergerrealm.util.OutfitManager;
import com.jipelski.mergerrealm.util.SpriteManager;

import java.util.Arrays;
import java.util.List;

/**
 * Prince Outfits panel — opened by tapping the already-selected Prince a
 * second time (mirrors the facility "select, then tap again to act"
 * convention; see GridInputHandler.handlePrinceTap()).
 *
 * One row per outfit: [Buy] if not owned, [Equip] if owned but not the
 * active one, "Equipped" + [Unequip] if it's the currently active one.
 * Buying and equipping are deliberately separate actions — owning several
 * outfits and freely swapping which one is active, same mental model as
 * Inventory's equip/unequip.
 */
public class OutfitPanel {

    private static final String TAG = "OutfitPanel";

    private boolean visible = false;

    // ── Layout ──
    private static final float MARGIN = 24f;
    private static final float HEADER_HEIGHT = 52f;
    private static final float ROW_HEIGHT = 68f;
    private static final float ROW_GAP = 8f;

    // ── References ──
    private final EventManager eventManager;
    private final Viewport viewport;
    private final UITextureManager uiTex;
    private final SpriteManager spriteManager;
    private final GlyphLayout glyphLayout;

    // ── Touch ──
    private final Vector2 touchPos = new Vector2();

    public OutfitPanel(EventManager eventManager, Viewport viewport, UITextureManager uiTex,
                       SpriteManager spriteManager) {
        this.eventManager = eventManager;
        this.viewport = viewport;
        this.uiTex = uiTex;
        this.spriteManager = spriteManager;
        this.glyphLayout = new GlyphLayout();
    }

    public boolean isVisible() { return visible; }
    public void open() { visible = true; }
    public void close() { visible = false; }

    // ── Layout helpers ──
    private float getWorldWidth() { return LayoutConfig.WORLD_WIDTH; }
    private float getWorldHeight() { return viewport.getWorldHeight(); }
    private float getMenuX() { return MARGIN; }
    private float getMenuWidth() { return getWorldWidth() - MARGIN * 2; }
    private float getMenuTop() { return getWorldHeight() - MARGIN; }
    private float getMenuBottom() { return MARGIN; }
    private float getContentTop() { return getMenuTop() - HEADER_HEIGHT; }

    // ── Row state, shared by draw + touch so they can never drift apart ──
    private List<IconText.Seg> rowActionSegs(OutfitManager om, OutfitManager.OutfitData outfit) {
        if (om.isEquipped(outfit.id)) return Arrays.asList(IconText.Seg.text("[Unequip]"));
        if (om.isOwned(outfit.id)) return Arrays.asList(IconText.Seg.text("[Equip]"));
        return Arrays.asList(IconText.Seg.text("[" + outfit.goldCost + " "),
            IconText.Seg.icon("gold"), IconText.Seg.text("]"));
    }

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
        sr.setColor(0.2f, 0.16f, 0.1f, 1f); // gold-tinted header — same family as GoldShopPanel
        sr.rect(getMenuX(), getMenuTop() - HEADER_HEIGHT, getMenuWidth(), HEADER_HEIGHT);

        OutfitManager om = eventManager.getOutfitManager();
        GoldManager gm = eventManager.getGoldManager();
        List<OutfitManager.OutfitData> outfits = om.getAllOutfits();

        float y = getContentTop() - ROW_HEIGHT;
        for (OutfitManager.OutfitData outfit : outfits) {
            boolean equipped = om.isEquipped(outfit.id);
            boolean owned = om.isOwned(outfit.id);
            boolean affordable = owned || gm.canAfford(outfit.goldCost);

            if (equipped) sr.setColor(0.16f, 0.22f, 0.16f, 1f);
            else if (affordable) sr.setColor(0.18f, 0.18f, 0.24f, 1f);
            else sr.setColor(0.14f, 0.14f, 0.18f, 1f);
            sr.rect(getMenuX() + 8f, y, getMenuWidth() - 16f, ROW_HEIGHT);
            y -= (ROW_HEIGHT + ROW_GAP);
        }
        sr.end();
        Gdx.gl.glDisable(Gdx.gl.GL_BLEND);
    }

    public void drawContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        if (!visible) return;
        OutfitManager om = eventManager.getOutfitManager();
        GoldManager gm = eventManager.getGoldManager();
        List<OutfitManager.OutfitData> outfits = om.getAllOutfits();

        font.setColor(1f, 0.85f, 0.25f, 1f);
        font.draw(batch, "Prince Outfits", getMenuX() + 12f, getMenuTop() - 12f);
        font.setColor(Color.WHITE);
        font.draw(batch, "X", getMenuX() + getMenuWidth() - 28f, getMenuTop() - 12f);

        fontSmall.setColor(1f, 0.85f, 0.25f, 1f);
        IconText.textThenIcon(batch, fontSmall, glyphLayout, spriteManager,
            "Balance: " + gm.getGold(), "gold", getMenuX() + 12f, getMenuTop() - 34f, 16f);

        float y = getContentTop() - ROW_HEIGHT;
        for (OutfitManager.OutfitData outfit : outfits) {
            boolean equipped = om.isEquipped(outfit.id);
            boolean owned = om.isOwned(outfit.id);
            boolean affordable = owned || gm.canAfford(outfit.goldCost);

            fontSmall.setColor(0.85f, 0.85f, 0.95f, 1f);
            String nameLine = outfit.name + (equipped ? "  [Equipped]" : "");
            fontSmall.draw(batch, nameLine, getMenuX() + 16f, y + ROW_HEIGHT - 12f);

            fontSmall.setColor(0.55f, 0.55f, 0.62f, 1f);
            fontSmall.draw(batch, outfit.description, getMenuX() + 16f, y + ROW_HEIGHT - 30f,
                getMenuWidth() - 150f, com.badlogic.gdx.utils.Align.left, true);

            List<IconText.Seg> actionSegs = rowActionSegs(om, outfit);
            if (equipped) fontSmall.setColor(0.9f, 0.4f, 0.4f, 1f);
            else if (owned) fontSmall.setColor(0.3f, 0.9f, 0.3f, 1f);
            else if (affordable) fontSmall.setColor(1f, 0.85f, 0.25f, 1f);
            else fontSmall.setColor(0.5f, 0.5f, 0.55f, 1f);

            float actionW = IconText.measure(fontSmall, glyphLayout, actionSegs, 14f);
            IconText.draw(batch, fontSmall, glyphLayout, spriteManager, actionSegs,
                getMenuX() + getMenuWidth() - 16f - actionW, y + ROW_HEIGHT - 20f, 14f);

            y -= (ROW_HEIGHT + ROW_GAP);
        }

        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    // ══════════════════════════════════════════════════════════════
    // TOUCH
    // ══════════════════════════════════════════════════════════════

    public boolean handleTouchDown(int screenX, int screenY) {
        if (!visible) return false;
        return true; // modal — consume everything
    }

    public boolean handleTouchDragged(int screenX, int screenY) {
        return visible;
    }

    public boolean handleTouchUp(int screenX, int screenY) {
        if (!visible) return false;
        touchPos.set(screenX, screenY);
        viewport.unproject(touchPos);

        // Close button
        if (touchPos.x > getMenuX() + getMenuWidth() - 40f
            && touchPos.y > getMenuTop() - HEADER_HEIGHT) {
            close();
            return true;
        }
        // Tap outside panel closes
        if (touchPos.x < getMenuX() || touchPos.x > getMenuX() + getMenuWidth()
            || touchPos.y < getMenuBottom() || touchPos.y > getMenuTop()) {
            close();
            return true;
        }

        OutfitManager om = eventManager.getOutfitManager();
        List<OutfitManager.OutfitData> outfits = om.getAllOutfits();

        // Action taps — right-aligned region of each row, mirrors GoldShopPanel's [Buy] hit zone
        float actionX = getMenuX() + getMenuWidth() - 90f;
        float y = getContentTop() - ROW_HEIGHT;
        for (OutfitManager.OutfitData outfit : outfits) {
            if (touchPos.y >= y && touchPos.y <= y + ROW_HEIGHT) {
                if (touchPos.x >= actionX) {
                    handleRowAction(om, outfit);
                }
                return true;
            }
            y -= (ROW_HEIGHT + ROW_GAP);
        }

        return true;
    }

    private void handleRowAction(OutfitManager om, OutfitManager.OutfitData outfit) {
        if (om.isEquipped(outfit.id)) {
            om.unequip();
        } else if (om.isOwned(outfit.id)) {
            om.equip(outfit.id);
        } else {
            om.purchase(outfit.id);
        }
    }
}
