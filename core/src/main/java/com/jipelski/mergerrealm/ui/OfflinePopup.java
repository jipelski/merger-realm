package com.jipelski.mergerrealm.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.Viewport;

import com.jipelski.mergerrealm.util.SpriteManager;

import java.util.Arrays;
import java.util.List;

/**
 * A modal popup that shows offline progress and stays visible
 * until the player presses the OK button.
 *
 * Layout:
 * +──────────────────────────+
 * │      Welcome Back!       │
 * │    You were away for     │
 * │     2h 34m               │
 * │                          │
 * │  +120 food             │
 * │  +85 wood               │
 * │  +40 iron                │
 * │                          │
 * │        [ OK ]            │
 * +──────────────────────────+
 */
public class OfflinePopup {

    private boolean visible = false;

    // Content
    private String timeAwayText;
    private String foodText;
    private String woodText;
    private String ironText;

    // Layout
    private static final float POPUP_WIDTH = 300f;
    private static final float POPUP_HEIGHT = 220f;
    private static final float BTN_WIDTH = 100f;
    private static final float BTN_HEIGHT = 36f;
    private static final float PADDING = 16f;

    private float popupX;
    private float popupY;
    private float btnX;
    private float btnY;

    private final UITextureManager uiTex;
    private final SpriteManager spriteManager;
    private final GlyphLayout glyphLayout;

    public OfflinePopup(UITextureManager uiTex, SpriteManager spriteManager) {
        this.uiTex = uiTex;
        this.spriteManager = spriteManager;
        this.glyphLayout = new GlyphLayout();
    }

    /**
     * Shows the popup with offline progress details.
     */
    public void show(long secondsAway, int foodGained, int woodGained, int ironGained) {
        // Format time
        long hours = secondsAway / 3600;
        long minutes = (secondsAway % 3600) / 60;
        StringBuilder timeSb = new StringBuilder();
        if (hours > 0) timeSb.append(hours).append("h ");
        timeSb.append(minutes).append("m");
        timeAwayText = timeSb.toString();

        // Word dropped in favor of an icon (see drawContent) — same "icon
        // replaces the word" convention as the HUD's CachedLabel change.
        foodText = foodGained > 0 ? "+" + foodGained : null;
        woodText = woodGained > 0 ? "+" + woodGained : null;
        ironText = ironGained > 0 ? "+" + ironGained : null;

        // Center popup on screen
        float actualHeight = LayoutConfig.getActualHeight();
        popupX = (LayoutConfig.WORLD_WIDTH - POPUP_WIDTH) / 2f;
        popupY = (actualHeight + POPUP_HEIGHT) / 2f;// TODO: make sure this is correctly rendered / 2f; //(actualHeight - POPUP_HEIGHT) / 2f;

        // Center button at bottom of popup
        btnX = popupX + (POPUP_WIDTH - BTN_WIDTH) / 2f;
        btnY = popupY + PADDING;

        visible = true;
    }

    public boolean isVisible() {
        return visible;
    }

    /**
     * Handles touch. Returns true if consumed (popup is visible).
     */
    public boolean handleTouch(float worldX, float worldY) {
        if (!visible) return false;

        // Check OK button
        if (worldX >= btnX && worldX <= btnX + BTN_WIDTH
            && worldY >= btnY && worldY <= btnY + BTN_HEIGHT) {
            visible = false;
            return true;
        }

        // Consume all touches while popup is visible (modal)
        visible = false;
        return true;
    }

    /**
     * Draws the popup. Call this LAST in the render cycle so it's on top.
     * Handles both shape and sprite rendering internally.
     */
    public void draw(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        if (!visible) return;

        float actualHeight = LayoutConfig.getActualHeight();

        // ── Dim overlay ──
        batch.draw(uiTex.popupOverlay, 0, 0, LayoutConfig.WORLD_WIDTH, actualHeight);

        // ── Popup panel ──
        uiTex.drawPanel(batch, uiTex.popupPanel, popupX, popupY, POPUP_WIDTH, POPUP_HEIGHT);

        float centerX = popupX + POPUP_WIDTH / 2f;
        float textX = popupX + PADDING;
        float textWidth = POPUP_WIDTH - PADDING * 2;

        // ── Title ──
        font.setColor(1f, 0.9f, 0.4f, 1f); // gold
        glyphLayout.setText(font, "Welcome Back!");
        font.draw(batch, "Welcome Back!",
            centerX - glyphLayout.width / 2f,
            popupY + POPUP_HEIGHT - PADDING - 4f);

        // ── Time away ──
        fontSmall.setColor(0.7f, 0.7f, 0.8f, 1f);
        String awayMsg = "You were away for " + timeAwayText;
        glyphLayout.setText(fontSmall, awayMsg);
        fontSmall.draw(batch, awayMsg,
            centerX - glyphLayout.width / 2f,
            popupY + POPUP_HEIGHT - PADDING - 28f);

        // ── Resource gains ──
        float resourceY = popupY + POPUP_HEIGHT - PADDING - 58f;
        float lineHeight = 20f;

        boolean anyGained = false;

        if (foodText != null) {
            fontSmall.setColor(0.6f, 0.9f, 0.4f, 1f); // green
            List<IconText.Seg> segs = Arrays.asList(IconText.Seg.icon("food"), IconText.Seg.text(foodText));
            float w = IconText.measure(fontSmall, glyphLayout, segs, 16f);
            IconText.draw(batch, fontSmall, glyphLayout, spriteManager, segs,
                centerX - w / 2f, resourceY, 16f);
            resourceY -= lineHeight;
            anyGained = true;
        }
        if (woodText != null) {
            fontSmall.setColor(0.6f, 0.7f, 0.9f, 1f); // blue-ish
            List<IconText.Seg> segs = Arrays.asList(IconText.Seg.icon("wood"), IconText.Seg.text(woodText));
            float w = IconText.measure(fontSmall, glyphLayout, segs, 16f);
            IconText.draw(batch, fontSmall, glyphLayout, spriteManager, segs,
                centerX - w / 2f, resourceY, 16f);
            resourceY -= lineHeight;
            anyGained = true;
        }
        if (ironText != null) {
            fontSmall.setColor(0.9f, 0.6f, 0.4f, 1f); // orange
            List<IconText.Seg> segs = Arrays.asList(IconText.Seg.icon("iron"), IconText.Seg.text(ironText));
            float w = IconText.measure(fontSmall, glyphLayout, segs, 16f);
            IconText.draw(batch, fontSmall, glyphLayout, spriteManager, segs,
                centerX - w / 2f, resourceY, 16f);
            anyGained = true;
        }

        if (!anyGained) {
            fontSmall.setColor(0.5f, 0.5f, 0.6f, 1f);
            String noRes = "No resources generated";
            glyphLayout.setText(fontSmall, noRes);
            fontSmall.draw(batch, noRes,
                centerX - glyphLayout.width / 2f, resourceY);
        }

        // ── OK Button ──
        uiTex.drawPanel(batch, uiTex.btnNormal, btnX, btnY, BTN_WIDTH, BTN_HEIGHT);

        font.setColor(Color.WHITE);
        glyphLayout.setText(font, "OK");
        font.draw(batch, "OK",
            btnX + (BTN_WIDTH - glyphLayout.width) / 2f,
            btnY + BTN_HEIGHT / 2f + glyphLayout.height / 2f);

        // Reset colors
        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }
}
