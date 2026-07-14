package com.jipelski.mergerrealm.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.Viewport;

import com.jipelski.mergerrealm.model.Item;
import com.jipelski.mergerrealm.util.EventManager;

/**
 * "Salvage" — the equipment sink. Triggered by the [Salvage] header button
 * on InventoryMenu's Equipment tab (see InventoryMenu.setSalvagePanel()).
 * Converts unwanted low-level swords/shields/amulets into Gold in bulk,
 * rather than a per-item multi-select (no such UI pattern exists anywhere
 * in this project, and tapping through "hundreds" of items individually
 * would be miserable regardless).
 *
 * Interaction: a 5-segment level picker sets a threshold (tap Lv3 -> "every
 * unequipped sword/shield/amulet at Lv1-3"); a live preview recomputes on
 * every threshold change; one confirm button executes
 * EventManager.salvageEquipment(threshold) and the preview refreshes in
 * place to show the new, smaller remaining count — the live preview IS the
 * confirmation step, so there's no separate nested confirm dialog.
 *
 * Only ever reads/removes from Inventory.getUnequippedItems() (via
 * EventManager.salvageEquipment) — currently-equipped gear is never a
 * candidate, sidestepping the max_hp-reconciliation bug class
 * EventManager.equipItem()/unequipItem() exist specifically to handle.
 */
public class SalvagePanel {

    private static final String TAG = "SalvagePanel";

    private static final int MAX_LEVEL = 5;
    private static final float MARGIN = 50f;
    private static final float HEADER_HEIGHT = 40f;
    private static final float SEGMENT_HEIGHT = 36f;
    private static final float BUTTON_HEIGHT = 36f;
    private static final float BUTTON_MARGIN = 12f;
    private static final float ROW_GAP = 12f;

    private final EventManager eventManager;
    private final Viewport viewport;
    private final GlyphLayout glyphLayout;
    private final Vector2 touchPos = new Vector2();

    private boolean visible = false;
    private int threshold = 1;
    private int previewCount = 0;
    private int previewGold = 0;
    private EventManager.SalvageResult lastResult;

    public SalvagePanel(EventManager eventManager, Viewport viewport, UITextureManager uiTex) {
        this.eventManager = eventManager;
        this.viewport = viewport;
        this.glyphLayout = new GlyphLayout();
        // uiTex accepted for constructor-signature symmetry with sibling
        // popups (HiddenTemplePopup/DailyLoginPopup/OutfitPanel) — plain
        // ShapeRenderer rects + text here too, no textured chrome.
    }

    public boolean isVisible() { return visible; }

    public void open() {
        threshold = 1;
        lastResult = null;
        recomputePreview();
        visible = true;
    }

    public void close() { visible = false; }

    // ── Layout helpers ──
    private float getWorldWidth() { return LayoutConfig.WORLD_WIDTH; }
    private float getWorldHeight() { return viewport.getWorldHeight(); }
    private float getMenuX() { return MARGIN; }
    private float getMenuWidth() { return getWorldWidth() - MARGIN * 2; }
    private float getMenuTop() { return getWorldHeight() / 2f + 150f; }
    private float getMenuBottom() { return getWorldHeight() / 2f - 150f; }
    private float getContentTop() { return getMenuTop() - HEADER_HEIGHT; }
    private float getSegmentRowY() { return getContentTop() - ROW_GAP - SEGMENT_HEIGHT; }
    private float getButtonY() { return getMenuBottom() + BUTTON_MARGIN; }
    private float getPreviewY() { return getSegmentRowY() - 28f; }

    // ── Preview computation — a pure read over Inventory.getUnequippedItems(),
    // recomputed only on threshold change (open()/segment tap), not per frame. ──

    private void recomputePreview() {
        previewCount = 0;
        previewGold = 0;
        for (Item item : eventManager.getInventory().getUnequippedItems()) {
            if (!isSalvageableType(item.getType()) || item.getLevel() > threshold) continue;
            previewCount++;
            previewGold += eventManager.getGoldManager().getSalvageValue(item.getLevel());
        }
    }

    private boolean isSalvageableType(String type) {
        return "sword".equals(type) || "shield".equals(type) || "amulet".equals(type);
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
        sr.setColor(0.2f, 0.16f, 0.1f, 1f); // gold-tinted header — same family as OutfitPanel/GoldShop
        sr.rect(getMenuX(), getContentTop(), getMenuWidth(), HEADER_HEIGHT);

        // Level segments — reuses the row-of-selectable-segments idiom every
        // tab strip in this project already uses.
        float segW = getMenuWidth() / MAX_LEVEL;
        for (int lvl = 1; lvl <= MAX_LEVEL; lvl++) {
            float segX = getMenuX() + (lvl - 1) * segW;
            if (lvl <= threshold) {
                sr.setColor(0.25f, 0.35f, 0.22f, 1f);
            } else {
                sr.setColor(0.16f, 0.16f, 0.2f, 1f);
            }
            sr.rect(segX + 1f, getSegmentRowY(), segW - 2f, SEGMENT_HEIGHT);
        }

        // Confirm button
        if (previewCount > 0) {
            sr.setColor(0.22f, 0.45f, 0.22f, 1f);
        } else {
            sr.setColor(0.18f, 0.18f, 0.22f, 1f);
        }
        sr.rect(getMenuX(), getButtonY(), getMenuWidth(), BUTTON_HEIGHT);

        sr.end();
        Gdx.gl.glDisable(Gdx.gl.GL_BLEND);
    }

    public void drawContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        if (!visible) return;

        font.setColor(1f, 0.85f, 0.4f, 1f);
        font.draw(batch, "Salvage Equipment", getMenuX() + 12f, getMenuTop() - 12f);
        font.setColor(Color.WHITE);
        font.draw(batch, "X", getMenuX() + getMenuWidth() - 24f, getMenuTop() - 12f);

        // Segment labels
        float segW = getMenuWidth() / MAX_LEVEL;
        for (int lvl = 1; lvl <= MAX_LEVEL; lvl++) {
            float segX = getMenuX() + (lvl - 1) * segW;
            if (lvl <= threshold) {
                fontSmall.setColor(Color.WHITE);
            } else {
                fontSmall.setColor(0.5f, 0.5f, 0.58f, 1f);
            }
            String label = "Lv" + lvl;
            glyphLayout.setText(fontSmall, label);
            fontSmall.draw(batch, label,
                segX + segW / 2f - glyphLayout.width / 2f,
                getSegmentRowY() + SEGMENT_HEIGHT / 2f + glyphLayout.height / 2f);
        }

        // Explanation + live preview
        fontSmall.setColor(0.75f, 0.75f, 0.85f, 1f);
        String explain = "Salvages unequipped swords, shields & amulets Lv1-"
            + threshold + " for Gold.";
        fontSmall.draw(batch, explain, getMenuX() + 12f, getPreviewY(),
            getMenuWidth() - 24f, Align.left, true);

        fontSmall.setColor(1f, 0.85f, 0.25f, 1f);
        String preview = previewCount + " item" + (previewCount == 1 ? "" : "s")
            + "  ->  " + previewGold + " Gold";
        glyphLayout.setText(fontSmall, preview);
        fontSmall.draw(batch, preview,
            getMenuX() + getMenuWidth() / 2f - glyphLayout.width / 2f, getPreviewY() - 24f);

        if (lastResult != null) {
            fontSmall.setColor(0.6f, 0.9f, 0.6f, 1f);
            String resultMsg = "Salvaged " + lastResult.count + " items for "
                + lastResult.totalGold + " Gold!";
            glyphLayout.setText(fontSmall, resultMsg);
            fontSmall.draw(batch, resultMsg,
                getMenuX() + getMenuWidth() / 2f - glyphLayout.width / 2f, getPreviewY() - 44f);
        }

        // Confirm button label
        if (previewCount > 0) {
            font.setColor(Color.WHITE);
        } else {
            font.setColor(0.5f, 0.5f, 0.55f, 1f);
        }
        String confirmLabel = "[ Salvage " + previewCount + " for " + previewGold + " Gold ]";
        glyphLayout.setText(font, confirmLabel);
        font.draw(batch, confirmLabel,
            getMenuX() + getMenuWidth() / 2f - glyphLayout.width / 2f,
            getButtonY() + BUTTON_HEIGHT / 2f + glyphLayout.height / 2f);

        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    // ══════════════════════════════════════════════════════════════
    // TOUCH
    // ══════════════════════════════════════════════════════════════

    public boolean handleTouchDown(int screenX, int screenY) {
        return visible; // modal — consume everything
    }

    public boolean handleTouchDragged(int screenX, int screenY) {
        return visible;
    }

    public boolean handleTouchUp(int screenX, int screenY) {
        if (!visible) return false;
        touchPos.set(screenX, screenY);
        viewport.unproject(touchPos);

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

        // Level segment tap
        if (touchPos.y >= getSegmentRowY() && touchPos.y <= getSegmentRowY() + SEGMENT_HEIGHT) {
            float segW = getMenuWidth() / MAX_LEVEL;
            int lvl = (int) ((touchPos.x - getMenuX()) / segW) + 1;
            if (lvl >= 1 && lvl <= MAX_LEVEL) {
                threshold = lvl;
                lastResult = null;
                recomputePreview();
            }
            return true;
        }

        // Confirm button
        if (touchPos.y >= getButtonY() && touchPos.y <= getButtonY() + BUTTON_HEIGHT) {
            if (previewCount > 0) {
                lastResult = eventManager.salvageEquipment(threshold);
                recomputePreview();
            }
            return true;
        }

        return true;
    }
}
