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
import com.jipelski.mergerrealm.util.RaidManager;
import com.jipelski.mergerrealm.util.SoundManager;
import com.jipelski.mergerrealm.util.SpriteManager;

/**
 * Global settings panel — mirrors OutfitPanel's construction contract and
 * modal shape (own drawBackground/drawContent split, consume-all touchDown,
 * close on X or tap-outside). Unlike OutfitPanel's data-driven row list, the
 * rows here are a fixed, heterogeneous sequence (section headers + controls),
 * so row Y positions are computed by rowTopY(int) — a single function called
 * identically by both drawContent and handleTouchUp, so the two can never
 * drift apart (same "shared row state" discipline OutfitPanel's own comment
 * calls out, adapted for a fixed layout instead of a loop over data).
 *
 * Every control here mutates an existing manager that already persists on
 * the normal autosave cadence (SoundManager -> "audio_settings",
 * RaidManager.endlessWarningSuppressed -> the "raid_currency" 3rd int) —
 * this panel adds NO new save state of its own.
 */
public class SettingsPanel {

    private boolean visible = false;

    // ── Layout ──
    private static final float MARGIN = 24f;
    private static final float HEADER_HEIGHT = 52f;
    private static final float SECTION_HEADER_H = 26f;
    private static final float ROW_H = 40f;
    private static final float ROW_GAP = 4f;

    // Fixed row sequence — index must stay in sync between rowTopY()'s
    // heights array and the draw/touch switches below.
    private static final int ROW_AUDIO_HEADER = 0;
    private static final int ROW_SOUND = 1;
    private static final int ROW_VOLUME = 2;
    private static final int ROW_GAMEPLAY_HEADER = 3;
    private static final int ROW_ENDLESS_WARNING = 4;
    private static final float[] ROW_HEIGHTS = {
        SECTION_HEADER_H, ROW_H, ROW_H, SECTION_HEADER_H, ROW_H
    };

    // ── References ──
    private final EventManager eventManager;
    private final Viewport viewport;
    private final UITextureManager uiTex;
    private final SpriteManager spriteManager;
    private final SoundManager soundManager;
    private final GlyphLayout glyphLayout;

    // ── Touch ──
    private final Vector2 touchPos = new Vector2();

    public SettingsPanel(EventManager eventManager, Viewport viewport, UITextureManager uiTex,
                          SpriteManager spriteManager, SoundManager soundManager) {
        this.eventManager = eventManager;
        this.viewport = viewport;
        this.uiTex = uiTex;
        this.spriteManager = spriteManager;
        this.soundManager = soundManager;
        this.glyphLayout = new GlyphLayout();
    }

    public boolean isVisible() { return visible; }
    public void open() { visible = true; }
    public void close() { visible = false; }

    // ── Layout helpers (same shape as OutfitPanel's) ──
    private float getWorldWidth() { return LayoutConfig.WORLD_WIDTH; }
    private float getWorldHeight() { return viewport.getWorldHeight(); }
    private float getMenuX() { return MARGIN; }
    private float getMenuWidth() { return getWorldWidth() - MARGIN * 2; }
    private float getMenuTop() { return getWorldHeight() - MARGIN; }
    private float getMenuBottom() { return MARGIN; }
    private float getContentTop() { return getMenuTop() - HEADER_HEIGHT; }

    /** Top Y of the given fixed row index — the single source both draw and touch use. */
    private float rowTopY(int rowIndex) {
        float y = getContentTop() - 8f;
        for (int i = 0; i < rowIndex; i++) {
            y -= (ROW_HEIGHTS[i] + ROW_GAP);
        }
        return y;
    }

    private float volMinusX() { return getMenuX() + getMenuWidth() - 150f; }
    private float volPlusX()  { return getMenuX() + getMenuWidth() - 34f; }

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
        sr.setColor(0.16f, 0.16f, 0.2f, 1f);
        sr.rect(getMenuX(), getMenuTop() - HEADER_HEIGHT, getMenuWidth(), HEADER_HEIGHT);
        sr.end();
        Gdx.gl.glDisable(Gdx.gl.GL_BLEND);
    }

    public void drawContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        if (!visible) return;

        font.setColor(Color.WHITE);
        font.draw(batch, "Settings", getMenuX() + 12f, getMenuTop() - 12f);
        font.draw(batch, "X", getMenuX() + getMenuWidth() - 28f, getMenuTop() - 12f);

        // ── Audio section ──
        fontSmall.setColor(0.6f, 0.7f, 0.9f, 1f);
        fontSmall.draw(batch, "Audio", getMenuX() + 12f, rowTopY(ROW_AUDIO_HEADER) - 4f);

        boolean muted = soundManager.isMuted();
        drawToggleRow(batch, fontSmall, ROW_SOUND, "Sound", !muted);

        drawVolumeRow(batch, fontSmall, ROW_VOLUME);

        // ── Gameplay section ──
        fontSmall.setColor(0.6f, 0.7f, 0.9f, 1f);
        fontSmall.draw(batch, "Gameplay", getMenuX() + 12f, rowTopY(ROW_GAMEPLAY_HEADER) - 4f);

        RaidManager rm = eventManager.getRaidManager();
        boolean endlessWarningOn = !rm.isEndlessWarningSuppressed();
        drawToggleRow(batch, fontSmall, ROW_ENDLESS_WARNING, "Endless Warning", endlessWarningOn);

        // ── About ──
        fontSmall.setColor(0.5f, 0.5f, 0.58f, 1f);
        fontSmall.draw(batch, "Merger Realm v1.0", getMenuX() + 12f, getMenuBottom() + 40f);
        fontSmall.draw(batch, "Solo dev project - thanks for playing!",
            getMenuX() + 12f, getMenuBottom() + 22f);

        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    private void drawToggleRow(SpriteBatch batch, BitmapFont fontSmall, int rowIndex,
                                String label, boolean on) {
        float y = rowTopY(rowIndex) - ROW_H / 2f + 6f;

        fontSmall.setColor(0.85f, 0.85f, 0.9f, 1f);
        fontSmall.draw(batch, label, getMenuX() + 16f, y);

        String toggleText = on ? "[ On ]" : "[ Off ]";
        fontSmall.setColor(on ? new Color(0.3f, 0.9f, 0.3f, 1f) : new Color(0.9f, 0.4f, 0.4f, 1f));
        glyphLayout.setText(fontSmall, toggleText);
        fontSmall.draw(batch, toggleText,
            getMenuX() + getMenuWidth() - 16f - glyphLayout.width, y);
    }

    private void drawVolumeRow(SpriteBatch batch, BitmapFont fontSmall, int rowIndex) {
        float y = rowTopY(rowIndex) - ROW_H / 2f + 6f;
        boolean muted = soundManager.isMuted();

        fontSmall.setColor(muted ? 0.45f : 0.85f, muted ? 0.45f : 0.85f, muted ? 0.5f : 0.9f, 1f);
        fontSmall.draw(batch, "Volume", getMenuX() + 16f, y);

        int pct = Math.round(soundManager.getMasterVolume() * 100f);
        fontSmall.setColor(muted ? 0.4f : 0.7f, muted ? 0.4f : 0.7f, muted ? 0.45f : 0.8f, 1f);
        fontSmall.draw(batch, "[-]", volMinusX(), y);
        String pctStr = pct + "%";
        glyphLayout.setText(fontSmall, pctStr);
        fontSmall.draw(batch, pctStr,
            (volMinusX() + volPlusX()) / 2f - glyphLayout.width / 2f, y);
        fontSmall.draw(batch, "[+]", volPlusX(), y);
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

        // Sound toggle — whole row is the tap target
        if (inRowBand(ROW_SOUND)) {
            soundManager.setMuted(!soundManager.isMuted());
            return true;
        }

        // Volume stepper — narrow [-]/[+] bands, same row band for y
        if (inRowBand(ROW_VOLUME)) {
            if (touchPos.x >= volMinusX() - 12f && touchPos.x <= volMinusX() + 26f) {
                soundManager.setMasterVolume(soundManager.getMasterVolume() - 0.25f);
                return true;
            }
            if (touchPos.x >= volPlusX() - 12f && touchPos.x <= volPlusX() + 26f) {
                soundManager.setMasterVolume(soundManager.getMasterVolume() + 0.25f);
                return true;
            }
            return true; // consume the rest of the row (label area) as a no-op
        }

        // Endless permadeath warning toggle — whole row is the tap target
        if (inRowBand(ROW_ENDLESS_WARNING)) {
            RaidManager rm = eventManager.getRaidManager();
            rm.setEndlessWarningSuppressed(!rm.isEndlessWarningSuppressed());
            return true;
        }

        return true;
    }

    private boolean inRowBand(int rowIndex) {
        float top = rowTopY(rowIndex);
        float bottom = top - ROW_HEIGHTS[rowIndex];
        return touchPos.y <= top && touchPos.y >= bottom;
    }
}
