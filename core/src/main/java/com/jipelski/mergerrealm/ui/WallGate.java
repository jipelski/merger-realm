package com.jipelski.mergerrealm.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

import com.jipelski.mergerrealm.data.GenData;
import com.jipelski.mergerrealm.util.BattleFieldManager;
import com.jipelski.mergerrealm.util.EventManager;
import com.jipelski.mergerrealm.util.SpriteManager;

import java.util.LinkedList;

/**
 * The Wall Gate system.
 *
 * Layout within the wall zone:
 * +──────────────────────────────────────────+
 * │  [Explore]        ⛩ GATE        [Raid]  │
 * │                  2 waiting               │
 * │              ← tap to claim →            │
 * +──────────────────────────────────────────+
 *
 * - Gate is CLOSED when reward queue is empty
 * - Gate is OPEN when rewards are waiting
 * - Tapping the gate claims the next reward in FIFO order
 * - Explore/Raid buttons are placeholders for future features
 */
public class WallGate {

    private static final String TAG = "WallGate";

    // Layout
    private static final float GATE_WIDTH = 100f;
    private static final float GATE_HEIGHT = 80f;
    private static final float SIDE_BTN_WIDTH = 70f;
    private static final float SIDE_BTN_HEIGHT = 50f;
    private static final float SIDE_BTN_MARGIN = 16f;
    private static final float ICON_SIZE = 28f;
    private static final float ICON_GAP = 4f;
    private static final int MAX_VISIBLE_ICONS = 5;

    // Computed positions (set in updateLayout)
    private float gateX, gateY;
    private float exploreBtnX, exploreBtnY;
    private float raidBtnX, raidBtnY;
    private float iconStripX, iconStripY;

    // References
    private final EventManager eventManager;
    private final SpriteManager spriteManager;
    private final UITextureManager uiTex;
    private final GlyphLayout glyphLayout;


    private ExplorePanel explorePanel;

    // Animation
    private float gateAnimTime = 0f;
    private boolean wasOpen = false;

    public WallGate(EventManager eventManager, SpriteManager spriteManager,
                    UITextureManager uiTex) {
        this.eventManager = eventManager;
        this.spriteManager = spriteManager;
        this.uiTex = uiTex;
        this.glyphLayout = new GlyphLayout();
    }

    /**
     * Call after layout is calculated or on resize to reposition elements.
     */
    public void updateLayout() {
        float wallY = LayoutConfig.getWallY();
        float wallH = LayoutConfig.getWallHeight();
        float worldW = LayoutConfig.WORLD_WIDTH;
        float centerX = worldW / 2f;
        float centerY = wallY + wallH / 2f;

        // Gate centered
        gateX = centerX - GATE_WIDTH / 2f;
        gateY = centerY - GATE_HEIGHT / 2f + 8f;

        // Explore button — left side
        exploreBtnX = SIDE_BTN_MARGIN;
        exploreBtnY = centerY - SIDE_BTN_HEIGHT / 2f;

        // Raid button — right side
        raidBtnX = worldW - SIDE_BTN_MARGIN - SIDE_BTN_WIDTH;
        raidBtnY = centerY - SIDE_BTN_HEIGHT / 2f;

        // Icon strip — below the gate, centered
        float stripWidth = MAX_VISIBLE_ICONS * (ICON_SIZE + ICON_GAP) - ICON_GAP;
        iconStripX = centerX - stripWidth / 2f;
        iconStripY = gateY - ICON_SIZE - 8f;
    }

    /**
     * Handles touch input. Returns true if consumed.
     */
    public boolean handleTouch(float worldX, float worldY) {
        // Check gate tap
        if (worldX >= gateX && worldX <= gateX + GATE_WIDTH
            && worldY >= gateY && worldY <= gateY + GATE_HEIGHT) {
            return claimNextReward();
        }

        // Check explore button
        if (worldX >= exploreBtnX && worldX <= exploreBtnX + SIDE_BTN_WIDTH
            && worldY >= exploreBtnY && worldY <= exploreBtnY + SIDE_BTN_HEIGHT) {
            if (explorePanel != null) {
                explorePanel.open();
                Gdx.app.log(TAG, "Explore panel opened");
            }
            return true;
        }


        // Check raid button (future — just consume tap)
        if (worldX >= raidBtnX && worldX <= raidBtnX + SIDE_BTN_WIDTH
            && worldY >= raidBtnY && worldY <= raidBtnY + SIDE_BTN_HEIGHT) {
            Gdx.app.log(TAG, "Raid — coming soon");
            return true;
        }

        return false;
    }

    /**
     * Claims the next reward from the queue, spawning it on the grid.
     * Returns true if a reward was claimed or attempted.
     */
    private boolean claimNextReward() {
        BattleFieldManager bfm = eventManager.getBattleFieldManager();
        LinkedList<GenData> queue = bfm.getRewardQueue();

        if (queue == null || queue.isEmpty()) {
            Gdx.app.log(TAG, "Gate tapped — no rewards waiting");
            return true;
        }

        if (!eventManager.getGridInstance().hasEmptyCell()) {
            Gdx.app.log(TAG, "Gate tapped — grid is full, cannot claim");
            return true;
        }

        GenData reward = queue.peekFirst();
        if (reward == null) return true;

        // Spawn the reward on the grid
        String type = reward.getSprite_path(); // we store type in sprite_path for queue items
        int level = reward.getMaxLVL();        // we store level in maxLVL for queue items

        // Try to spawn
        int[] emptyCell = eventManager.getGridInstance().getClosestEmptyCell(0, 0);
        if (emptyCell != null) {
            eventManager.spawnObject(type, level, emptyCell[0], emptyCell[1]);
            queue.removeFirst(); // only remove after successful spawn
            Gdx.app.log(TAG, "Claimed reward: " + type + " lvl " + level
                + " (" + queue.size() + " remaining)");
        }

        return true;
    }

    /**
     * Draws the gate background shapes. Call OUTSIDE batch.begin/end.
     */
    public void drawBackground(ShapeRenderer sr) {
        BattleFieldManager bfm = eventManager.getBattleFieldManager();
        LinkedList<GenData> queue = bfm.getRewardQueue();
        boolean isOpen = queue != null && !queue.isEmpty();

        sr.begin(ShapeRenderer.ShapeType.Filled);

        // ── Gate ──
        if (isOpen) {
            // Open gate — warm color
            sr.setColor(0.35f, 0.28f, 0.15f, 1f);
            sr.rect(gateX, gateY, GATE_WIDTH, GATE_HEIGHT);

            // Gate opening (darker inner area)
            float openMargin = 8f;
            sr.setColor(0.12f, 0.1f, 0.08f, 1f);
            sr.rect(gateX + openMargin, gateY + openMargin,
                GATE_WIDTH - openMargin * 2, GATE_HEIGHT - openMargin * 2);

            // Gate arch top
            sr.setColor(0.4f, 0.32f, 0.18f, 1f);
            sr.rect(gateX, gateY + GATE_HEIGHT - 6f, GATE_WIDTH, 6f);

        } else {
            // Closed gate — stone color
            sr.setColor(0.28f, 0.26f, 0.24f, 1f);
            sr.rect(gateX, gateY, GATE_WIDTH, GATE_HEIGHT);

            // Gate planks (horizontal lines)
            sr.setColor(0.35f, 0.3f, 0.2f, 1f);
            for (float ly = gateY + 10f; ly < gateY + GATE_HEIGHT - 5f; ly += 16f) {
                sr.rect(gateX + 4f, ly, GATE_WIDTH - 8f, 3f);
            }

            // Gate studs (dots at intersections)
            sr.setColor(0.5f, 0.45f, 0.3f, 1f);
            float studSize = 5f;
            sr.rect(gateX + 12f, gateY + 12f, studSize, studSize);
            sr.rect(gateX + GATE_WIDTH - 17f, gateY + 12f, studSize, studSize);
            sr.rect(gateX + 12f, gateY + GATE_HEIGHT - 17f, studSize, studSize);
            sr.rect(gateX + GATE_WIDTH - 17f, gateY + GATE_HEIGHT - 17f, studSize, studSize);
        }

        // Gate border
        sr.setColor(0.45f, 0.38f, 0.25f, 1f);
        // Top
        sr.rect(gateX, gateY + GATE_HEIGHT, GATE_WIDTH, 3f);
        // Bottom
        sr.rect(gateX, gateY - 3f, GATE_WIDTH, 3f);
        // Left pillar
        sr.rect(gateX - 6f, gateY - 3f, 6f, GATE_HEIGHT + 6f);
        // Right pillar
        sr.rect(gateX + GATE_WIDTH, gateY - 3f, 6f, GATE_HEIGHT + 6f);

        // ── Explore button ──
        sr.setColor(0.25f, 0.25f, 0.3f, 1f);
        sr.rect(exploreBtnX, exploreBtnY, SIDE_BTN_WIDTH, SIDE_BTN_HEIGHT);

        // ── Raid button ──
        sr.setColor(0.25f, 0.25f, 0.3f, 1f);
        sr.rect(raidBtnX, raidBtnY, SIDE_BTN_WIDTH, SIDE_BTN_HEIGHT);

        sr.end();
    }

    /**
     * Draws gate text and reward icons. Call INSIDE batch.begin/end.
     */
    public void drawContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        BattleFieldManager bfm = eventManager.getBattleFieldManager();
        LinkedList<GenData> queue = bfm.getRewardQueue();
        boolean isOpen = queue != null && !queue.isEmpty();
        int queueSize = (queue != null) ? queue.size() : 0;

        float gateCenterX = gateX + GATE_WIDTH / 2f;

        if (isOpen) {
            // Draw the next reward's sprite inside the gate opening
            GenData nextReward = queue.peekFirst();
            if (nextReward != null) {
                String type = nextReward.getSprite_path();
                int level = nextReward.getMaxLVL();
                Texture sprite = spriteManager.getTextureForObject(type, level);

                // Draw sprite centered in the gate opening
                float spriteSize = GATE_HEIGHT - 24f;
                float spriteX = gateX + (GATE_WIDTH - spriteSize) / 2f;
                float spriteY = gateY + 12f;
                batch.draw(sprite, spriteX, spriteY, spriteSize, spriteSize);
            }

            // Queue count in top-right corner of gate
            if (queueSize > 1) {
                fontSmall.setColor(1f, 0.9f, 0.4f, 1f);
                String countText = "+" + (queueSize - 1);
                glyphLayout.setText(fontSmall, countText);
                fontSmall.draw(batch, countText,
                    gateX + GATE_WIDTH - glyphLayout.width - 6f,
                    gateY + GATE_HEIGHT - 6f);
            }

            // "Tap" hint below gate
            fontSmall.setColor(0.8f, 0.7f, 0.4f, 1f);
            glyphLayout.setText(fontSmall, "Tap to claim");
            fontSmall.draw(batch, "Tap to claim",
                gateCenterX - glyphLayout.width / 2f,
                gateY - 6f);
        } else {
            // Closed gate text
            fontSmall.setColor(0.5f, 0.45f, 0.35f, 1f);
            glyphLayout.setText(fontSmall, "Gate Closed");
            fontSmall.draw(batch, "Gate Closed",
                gateCenterX - glyphLayout.width / 2f,
                gateY + GATE_HEIGHT / 2f + 4f);
        }

        // ── Explore button text ──
        fontSmall.setColor(0.8f, 0.8f, 0.9f, 1f);
        glyphLayout.setText(fontSmall, "Explore");
        fontSmall.draw(batch, "Explore",
            exploreBtnX + (SIDE_BTN_WIDTH - glyphLayout.width) / 2f,
            exploreBtnY + SIDE_BTN_HEIGHT / 2f + glyphLayout.height / 2f);

        // ── Raid button text ──
        glyphLayout.setText(fontSmall, "Raid");
        fontSmall.draw(batch, "Raid",
            raidBtnX + (SIDE_BTN_WIDTH - glyphLayout.width) / 2f,
            raidBtnY + SIDE_BTN_HEIGHT / 2f + glyphLayout.height / 2f);

        // Reset
        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    /**
     * Returns true if the touch Y is within the wall zone.
     */
    public boolean isInWallArea(float worldY) {
        float wallY = LayoutConfig.getWallY();
        float wallH = LayoutConfig.getWallHeight();
        return worldY >= wallY && worldY <= wallY + wallH;
    }

    public void setExplorePanel(ExplorePanel panel) {
        this.explorePanel = panel;
    }
}
