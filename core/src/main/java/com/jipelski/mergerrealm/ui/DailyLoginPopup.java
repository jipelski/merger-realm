package com.jipelski.mergerrealm.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;

import com.jipelski.mergerrealm.util.DailyLoginManager;
import com.jipelski.mergerrealm.util.EventManager;

/**
 * 7-day login streak popup — auto-opens on launch (from MergerRealmGame,
 * right after processOfflineProgress()) whenever
 * DailyLoginManager.isClaimAvailable() is true, and can be re-opened from
 * the Unified Shop's Daily tab to check streak progress.
 *
 * The 7-day calendar strip is drawn/hit-tested by rect-parameterized helpers
 * (drawCalendarBg/drawCalendarContent/handleCalendarTap) so UnifiedShopPanel
 * can render the exact same strip inside its own content area instead of
 * duplicating the layout — see UnifiedShopPanel's TAB_DAILY, which holds a
 * reference to this instance and positions the strip inside its own panel.
 * This instance also owns the single lastReward result shown after a claim,
 * so the popup and the shop tab never show divergent state.
 */
public class DailyLoginPopup {

    private static final String TAG = "DailyLoginPopup";

    private boolean visible = false;

    // ── Layout (standalone popup form) ──
    private static final float MARGIN = 24f;
    private static final float HEADER_HEIGHT = 52f;

    // ── Calendar strip layout (shared by popup + shop tab) ──
    private static final float CELL_GAP = 4f;
    private static final float CLAIM_BTN_HEIGHT = 32f;

    private final EventManager eventManager;
    private final Viewport viewport;
    private final UITextureManager uiTex;
    private final GlyphLayout glyphLayout;

    private final Vector2 touchPos = new Vector2();

    private DailyLoginManager.DailyReward lastReward;

    public DailyLoginPopup(EventManager eventManager, Viewport viewport, UITextureManager uiTex) {
        this.eventManager = eventManager;
        this.viewport = viewport;
        this.uiTex = uiTex;
        this.glyphLayout = new GlyphLayout();
    }

    public boolean isVisible() { return visible; }
    public void open() { visible = true; }
    public void close() { visible = false; }

    // ── Layout helpers (standalone popup form) ──
    private float getWorldWidth() { return LayoutConfig.WORLD_WIDTH; }
    private float getWorldHeight() { return viewport.getWorldHeight(); }
    private float getMenuX() { return MARGIN; }
    private float getMenuWidth() { return getWorldWidth() - MARGIN * 2; }
    private float getMenuTop() { return getWorldHeight() - MARGIN; }
    private float getMenuBottom() { return MARGIN; }
    private float getContentTop() { return getMenuTop() - HEADER_HEIGHT; }

    // ══════════════════════════════════════════════════════════════
    // DRAWING — standalone popup form
    // ══════════════════════════════════════════════════════════════

    public void drawBackground(ShapeRenderer sr) {
        if (!visible) return;
        Gdx.gl.glEnable(Gdx.gl.GL_BLEND);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0f, 0f, 0f, 0.75f);
        sr.rect(0, 0, getWorldWidth(), getWorldHeight());
        sr.setColor(0.12f, 0.12f, 0.18f, 1f);
        sr.rect(getMenuX(), getMenuBottom(), getMenuWidth(), getMenuTop() - getMenuBottom());
        sr.setColor(0.2f, 0.16f, 0.1f, 1f); // gold-tinted header — same family as the Gold tab
        sr.rect(getMenuX(), getMenuTop() - HEADER_HEIGHT, getMenuWidth(), HEADER_HEIGHT);

        // drawCalendarBg issues raw setColor/rect calls assuming an already-
        // open begin/end block (see its javadoc) — stays inside this one.
        drawCalendarBg(sr, getMenuX() + 8f, getMenuBottom() + 8f,
            getMenuWidth() - 16f, getContentTop() - getMenuBottom() - 16f);

        sr.end();
        Gdx.gl.glDisable(Gdx.gl.GL_BLEND);
    }

    public void drawContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        if (!visible) return;

        font.setColor(1f, 0.85f, 0.25f, 1f);
        font.draw(batch, "Daily Login", getMenuX() + 12f, getMenuTop() - 12f);
        font.setColor(Color.WHITE);
        font.draw(batch, "X", getMenuX() + getMenuWidth() - 28f, getMenuTop() - 12f);

        DailyLoginManager dlm = eventManager.getDailyLoginManager();
        fontSmall.setColor(0.85f, 0.8f, 0.5f, 1f);
        fontSmall.draw(batch, "Streak: Day " + dlm.getDayToClaim() + "/" + DailyLoginManager.STREAK_LENGTH,
            getMenuX() + 12f, getMenuTop() - 34f);

        drawCalendarContent(batch, font, fontSmall, getMenuX() + 8f, getMenuBottom() + 8f,
            getMenuWidth() - 16f, getContentTop() - getMenuBottom() - 16f);

        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    // ══════════════════════════════════════════════════════════════
    // SHARED CALENDAR STRIP — reused by UnifiedShopPanel's Daily tab
    // ══════════════════════════════════════════════════════════════

    private float getCellWidth(float w) {
        return (w - CELL_GAP * (DailyLoginManager.STREAK_LENGTH - 1)) / DailyLoginManager.STREAK_LENGTH;
    }

    /**
     * Draws the 7 day cells + claim button backgrounds inside the given rect.
     * Issues raw setColor/rect calls only — the caller must already have an
     * open {@code sr.begin(ShapeType.Filled)}/{@code end()} block (both
     * drawBackground() above and UnifiedShopPanel's Daily tab satisfy this).
     */
    public void drawCalendarBg(ShapeRenderer sr, float x, float y, float w, float h) {
        DailyLoginManager dlm = eventManager.getDailyLoginManager();
        int dayToClaim = dlm.getDayToClaim();
        boolean todayClaimed = dlm.isTodayClaimed();

        float cellWidth = getCellWidth(w);
        float cellHeight = h - CLAIM_BTN_HEIGHT - CELL_GAP;
        float cellY = y + CLAIM_BTN_HEIGHT + CELL_GAP;

        for (int day = 1; day <= DailyLoginManager.STREAK_LENGTH; day++) {
            float cellX = x + (day - 1) * (cellWidth + CELL_GAP);

            boolean claimed = day < dayToClaim || (day == dayToClaim && todayClaimed);
            boolean isCurrent = day == dayToClaim && !todayClaimed;

            if (isCurrent)      sr.setColor(0.28f, 0.34f, 0.16f, 1f); // claimable — highlighted
            else if (claimed)   sr.setColor(0.16f, 0.22f, 0.16f, 1f); // already claimed
            else                sr.setColor(0.15f, 0.15f, 0.2f, 1f); // future
            sr.rect(cellX, cellY, cellWidth, cellHeight);
        }

        // Claim button
        boolean available = dlm.isClaimAvailable();
        if (available) sr.setColor(0.25f, 0.55f, 0.25f, 1f);
        else            sr.setColor(0.18f, 0.18f, 0.22f, 1f);
        sr.rect(x, y, w, CLAIM_BTN_HEIGHT);
    }

    /** Draws day labels/hints/checkmarks + the claim button text inside the given rect. */
    public void drawCalendarContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall,
                                     float x, float y, float w, float h) {
        DailyLoginManager dlm = eventManager.getDailyLoginManager();
        int dayToClaim = dlm.getDayToClaim();
        boolean todayClaimed = dlm.isTodayClaimed();
        boolean available = dlm.isClaimAvailable();

        float cellWidth = getCellWidth(w);
        float cellHeight = h - CLAIM_BTN_HEIGHT - CELL_GAP;
        float cellY = y + CLAIM_BTN_HEIGHT + CELL_GAP;

        for (int day = 1; day <= DailyLoginManager.STREAK_LENGTH; day++) {
            float cellX = x + (day - 1) * (cellWidth + CELL_GAP);
            boolean claimed = day < dayToClaim || (day == dayToClaim && todayClaimed);
            boolean isCurrent = day == dayToClaim && !todayClaimed;
            boolean isDay7 = day == DailyLoginManager.STREAK_LENGTH;

            if (isCurrent)      fontSmall.setColor(0.7f, 1f, 0.5f, 1f);
            else if (claimed)   fontSmall.setColor(0.5f, 0.75f, 0.5f, 1f);
            else                fontSmall.setColor(0.5f, 0.5f, 0.6f, 1f);

            String dayLabel = claimed ? "Day " + day + " ✓" : "Day " + day;
            glyphLayout.setText(fontSmall, dayLabel);
            fontSmall.draw(batch, dayLabel, cellX + cellWidth / 2f - glyphLayout.width / 2f,
                cellY + cellHeight - 10f);

            String hint = isDay7 ? "Unit!" : "Reward";
            if (isDay7) fontSmall.setColor(1f, 0.85f, 0.25f, 1f);
            glyphLayout.setText(fontSmall, hint);
            fontSmall.draw(batch, hint, cellX + cellWidth / 2f - glyphLayout.width / 2f,
                cellY + cellHeight / 2f);
        }

        // Claim button label
        String btnLabel = available ? "[ Claim Day " + dayToClaim + " ]" : "Come back tomorrow";
        font.setColor(available ? Color.WHITE : new Color(0.5f, 0.5f, 0.55f, 1f));
        glyphLayout.setText(font, btnLabel);
        font.draw(batch, btnLabel, x + w / 2f - glyphLayout.width / 2f, y + CLAIM_BTN_HEIGHT / 2f + glyphLayout.height / 2f);

        // Last reward result, drawn above the strip if there's room and a claim happened this session
        if (lastReward != null) {
            fontSmall.setColor(0.85f, 0.85f, 0.95f, 1f);
            String rewardMsg = rewardSummary(lastReward);
            glyphLayout.setText(fontSmall, rewardMsg);
            fontSmall.draw(batch, rewardMsg, x + w / 2f - glyphLayout.width / 2f, cellY + cellHeight + 16f);
        }
    }

    private String rewardSummary(DailyLoginManager.DailyReward reward) {
        StringBuilder sb = new StringBuilder("+").append(reward.gold).append(" Gold");
        if (reward.rewardType != null) {
            sb.append(", ").append(reward.rewardType).append(" Lv").append(reward.rewardLevel);
            if (reward.queued) sb.append(" (Wall Gate)");
        }
        return sb.toString();
    }

    /** Handles a tap inside the given rect — claims today's reward if the claim button was hit. */
    public void handleCalendarTap(float worldX, float worldY, float x, float y, float w, float h) {
        if (worldY < y || worldY > y + CLAIM_BTN_HEIGHT || worldX < x || worldX > x + w) return;

        DailyLoginManager dlm = eventManager.getDailyLoginManager();
        if (!dlm.isClaimAvailable()) return;

        DailyLoginManager.DailyReward reward = dlm.claim();
        if (reward != null) {
            lastReward = reward;
            Gdx.app.log(TAG, "Claimed day " + reward.day + ": +" + reward.gold + " Gold"
                + (reward.rewardType != null ? ", " + reward.rewardType + " lv" + reward.rewardLevel : ""));
        }
    }

    // ══════════════════════════════════════════════════════════════
    // TOUCH — standalone popup form
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

        if (touchPos.x > getMenuX() + getMenuWidth() - 40f
            && touchPos.y > getMenuTop() - HEADER_HEIGHT) {
            close();
            return true;
        }
        if (touchPos.x < getMenuX() || touchPos.x > getMenuX() + getMenuWidth()
            || touchPos.y < getMenuBottom() || touchPos.y > getMenuTop()) {
            close();
            return true;
        }

        handleCalendarTap(touchPos.x, touchPos.y, getMenuX() + 8f, getMenuBottom() + 8f,
            getMenuWidth() - 16f, getContentTop() - getMenuBottom() - 16f);
        return true;
    }
}
