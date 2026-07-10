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
import com.jipelski.mergerrealm.util.RaidManager;

/**
 * Central Gold Shop — opened by tapping the top-bar Gold chip.
 *
 * Lists all five Gold sinks. Three are global actions the panel can
 * execute directly (fill resources, revive party, extra explore slot).
 * The other two (instant spawn, speed exploration) need an on-grid /
 * in-panel target, so they appear as informational rows pointing the
 * player to where the context button lives.
 */
public class GoldShopPanel {

    private static final String TAG = "GoldShopPanel";

    private boolean visible = false;

    // ── Layout ──
    private static final float MARGIN = 24f;
    private static final float HEADER_HEIGHT = 52f;
    private static final float ROW_HEIGHT = 60f;
    private static final float ROW_GAP = 8f;

    // ── References ──
    private final EventManager eventManager;
    private final Viewport viewport;
    private final UITextureManager uiTex;
    private final GlyphLayout glyphLayout;

    // ── Touch ──
    private final Vector2 touchPos = new Vector2();

    // Row model
    private static class Sink {
        final String name; final int cost; final String desc; final boolean actionable;
        Sink(String name, int cost, String desc, boolean actionable) {
            this.name = name; this.cost = cost; this.desc = desc; this.actionable = actionable;
        }
    }

    private final Sink[] sinks = {
        new Sink("Fill Resources",  GoldManager.COST_FILL_RESOURCES,
            "Food, Wood & Iron to max", true),
        new Sink("Revive Party",    GoldManager.COST_RAID_REVIVE_ALL,
            "All fallen raiders at 50% HP", true),
        new Sink("Extra Explore Slot", GoldManager.COST_EXTRA_EXPLORE_SLOT,
            "Permanent +1 exploration slot", true),
        new Sink("Instant Spawn",   GoldManager.COST_INSTANT_SPAWN,
            "Tap a periodic facility to use", false),
        new Sink("Speed Exploration", GoldManager.COST_SPEED_EXPLORATION,
            "Use from an active exploration", false),
    };

    public GoldShopPanel(EventManager eventManager, Viewport viewport, UITextureManager uiTex) {
        this.eventManager = eventManager;
        this.viewport = viewport;
        this.uiTex = uiTex;
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

    // ── Drawing ──
    public void drawBackground(ShapeRenderer sr) {
        if (!visible) return;
        Gdx.gl.glEnable(Gdx.gl.GL_BLEND);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0f, 0f, 0f, 0.75f);
        sr.rect(0, 0, getWorldWidth(), getWorldHeight());
        sr.setColor(0.12f, 0.12f, 0.18f, 1f);
        sr.rect(getMenuX(), getMenuBottom(), getMenuWidth(), getMenuTop() - getMenuBottom());
        sr.setColor(0.2f, 0.18f, 0.1f, 1f); // gold-tinted header
        sr.rect(getMenuX(), getMenuTop() - HEADER_HEIGHT, getMenuWidth(), HEADER_HEIGHT);

        // Row backgrounds
        GoldManager gm = eventManager.getGoldManager();
        float y = getContentTop() - ROW_HEIGHT;
        for (Sink s : sinks) {
            boolean affordable = s.actionable && gm.canAfford(s.cost);
            sr.setColor(affordable ? new Color(0.18f, 0.18f, 0.24f, 1f)
                : new Color(0.14f, 0.14f, 0.18f, 1f));
            sr.rect(getMenuX() + 8f, y, getMenuWidth() - 16f, ROW_HEIGHT);
            y -= (ROW_HEIGHT + ROW_GAP);
        }
        sr.end();
        Gdx.gl.glDisable(Gdx.gl.GL_BLEND);
    }

    public void drawContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        if (!visible) return;
        GoldManager gm = eventManager.getGoldManager();

        font.setColor(1f, 0.85f, 0.25f, 1f);
        font.draw(batch, "Gold Shop", getMenuX() + 12f, getMenuTop() - 12f);
        font.setColor(Color.WHITE);
        font.draw(batch, "X", getMenuX() + getMenuWidth() - 28f, getMenuTop() - 12f);

        fontSmall.setColor(1f, 0.85f, 0.25f, 1f);
        fontSmall.draw(batch, "Balance: " + gm.getGold() + " Gold",
            getMenuX() + 12f, getMenuTop() - 34f);

        float y = getContentTop() - ROW_HEIGHT;
        for (Sink s : sinks) {
            boolean affordable = s.actionable && gm.canAfford(s.cost);

            fontSmall.setColor(s.actionable ? new Color(0.85f, 0.85f, 0.95f, 1f)
                : new Color(0.55f, 0.55f, 0.6f, 1f));
            fontSmall.draw(batch, s.name, getMenuX() + 16f, y + ROW_HEIGHT - 12f);

            fontSmall.setColor(0.55f, 0.55f, 0.62f, 1f);
            fontSmall.draw(batch, s.desc, getMenuX() + 16f, y + ROW_HEIGHT - 30f);

            fontSmall.setColor(affordable ? new Color(1f, 0.85f, 0.25f, 1f)
                : new Color(0.6f, 0.45f, 0.2f, 1f));
            fontSmall.draw(batch, s.cost + " G",
                getMenuX() + getMenuWidth() - 130f, y + ROW_HEIGHT - 20f);

            if (s.actionable) {
                fontSmall.setColor(affordable ? new Color(0.3f, 0.9f, 0.3f, 1f)
                    : new Color(0.4f, 0.4f, 0.45f, 1f));
                fontSmall.draw(batch, "[Buy]", getMenuX() + getMenuWidth() - 58f, y + ROW_HEIGHT - 20f);
            } else {
                fontSmall.setColor(0.5f, 0.5f, 0.7f, 1f);
                fontSmall.draw(batch, "in-game", getMenuX() + getMenuWidth() - 62f, y + ROW_HEIGHT - 20f);
            }
            y -= (ROW_HEIGHT + ROW_GAP);
        }
        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    // ── Touch ──
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

        // Buy taps — only actionable rows, only the [Buy] region
        float buyX = getMenuX() + getMenuWidth() - 62f;
        if (touchPos.x >= buyX) {
            float y = getContentTop() - ROW_HEIGHT;
            for (Sink s : sinks) {
                if (touchPos.y >= y && touchPos.y <= y + ROW_HEIGHT) {
                    if (s.actionable) executeSink(s);
                    return true;
                }
                y -= (ROW_HEIGHT + ROW_GAP);
            }
        }
        return true;
    }

    private void executeSink(Sink s) {
        GoldManager gm = eventManager.getGoldManager();
        if (s.cost == GoldManager.COST_FILL_RESOURCES) {
            gm.fillResources();
        } else if (s.cost == GoldManager.COST_RAID_REVIVE_ALL) {
            RaidManager rm = eventManager.getRaidManager();
            if (rm != null && rm.isRaidActive()) gm.reviveRaidParty();
            else Gdx.app.log(TAG, "Revive: no active raid");
        } else if (s.cost == GoldManager.COST_EXTRA_EXPLORE_SLOT) {
            gm.purchaseExtraExploreSlot();
        }
    }
}
