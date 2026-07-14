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
import com.jipelski.mergerrealm.util.EventManager;
import com.jipelski.mergerrealm.util.HiddenTempleManager;
import com.jipelski.mergerrealm.util.SpriteManager;

/**
 * Small modal triggered by tapping an ancient_map in InventoryMenu's
 * Consumables tab (see InventoryMenu's item-tap handling). Mirrors
 * DailyLoginPopup's render/touch-only split (this class owns no reward-
 * rolling logic — that's HiddenTempleManager's job) but is simpler: no
 * calendar strip, just a two-state card.
 *
 * CONFIRM state: the map is NOT yet consumed — mirrors the implicit confirm
 * potion/amulet_of_ascension already get from requiring a second tap on a
 * target unit; ancient_map has no unit target, so this popup is that same
 * "second tap before commitment" in card form. Tapping Open Temple consumes
 * the item (Inventory.useConsumable) and rolls the reward
 * (HiddenTempleManager.openTemple()), then this popup flips to RESULT.
 * Tapping Cancel (or the X, or outside) leaves the item untouched.
 *
 * RESULT state: shows what was found; OK (or X, or outside) just closes —
 * the reward was already delivered to the grid/Wall Gate by openTemple().
 */
public class HiddenTemplePopup {

    private static final String TAG = "HiddenTemplePopup";

    private static final float MARGIN = 60f;
    private static final float HEADER_HEIGHT = 40f;
    private static final float BUTTON_HEIGHT = 36f;
    private static final float BUTTON_MARGIN = 12f;

    private final EventManager eventManager;
    private final Viewport viewport;
    private final SpriteManager spriteManager;
    private final GlyphLayout glyphLayout;

    private final Vector2 touchPos = new Vector2();

    private boolean visible = false;
    private String pendingItemId; // the ancient_map awaiting consumption, CONFIRM state
    private HiddenTempleManager.TempleReward result; // non-null once opened — RESULT state

    public HiddenTemplePopup(EventManager eventManager, Viewport viewport, UITextureManager uiTex,
                             SpriteManager spriteManager) {
        this.eventManager = eventManager;
        this.viewport = viewport;
        this.spriteManager = spriteManager;
        this.glyphLayout = new GlyphLayout();
        // uiTex is accepted for constructor-signature symmetry with the
        // other popups (DailyLoginPopup, OfflinePopup) even though this
        // simple card doesn't currently draw any textured chrome.
    }

    public boolean isVisible() { return visible; }

    /** Opens in CONFIRM state for the given (not-yet-consumed) ancient_map item id. */
    public void openConfirm(String itemId) {
        this.pendingItemId = itemId;
        this.result = null;
        this.visible = true;
    }

    public void close() {
        visible = false;
        pendingItemId = null;
        result = null;
    }

    // ── Layout helpers ──
    private float getWorldWidth() { return LayoutConfig.WORLD_WIDTH; }
    private float getWorldHeight() { return viewport.getWorldHeight(); }
    private float getMenuX() { return MARGIN; }
    private float getMenuWidth() { return getWorldWidth() - MARGIN * 2; }
    private float getMenuTop() { return getWorldHeight() / 2f + 110f; }
    private float getMenuBottom() { return getWorldHeight() / 2f - 110f; }
    private float getButtonY() { return getMenuBottom() + BUTTON_MARGIN; }

    // ══════════════════════════════════════════════════════════════
    // DRAWING
    // ══════════════════════════════════════════════════════════════

    public void drawBackground(ShapeRenderer sr) {
        if (!visible) return;
        Gdx.gl.glEnable(Gdx.gl.GL_BLEND);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0f, 0f, 0f, 0.75f);
        sr.rect(0, 0, getWorldWidth(), getWorldHeight());
        sr.setColor(0.14f, 0.12f, 0.08f, 1f); // temple/ancient-stone tint
        sr.rect(getMenuX(), getMenuBottom(), getMenuWidth(), getMenuTop() - getMenuBottom());
        sr.setColor(0.24f, 0.2f, 0.1f, 1f);
        sr.rect(getMenuX(), getMenuTop() - HEADER_HEIGHT, getMenuWidth(), HEADER_HEIGHT);

        // Button(s)
        if (result == null) {
            float halfW = (getMenuWidth() - BUTTON_MARGIN) / 2f;
            sr.setColor(0.22f, 0.45f, 0.22f, 1f); // Open Temple
            sr.rect(getMenuX(), getButtonY(), halfW, BUTTON_HEIGHT);
            sr.setColor(0.35f, 0.18f, 0.18f, 1f); // Cancel
            sr.rect(getMenuX() + halfW + BUTTON_MARGIN, getButtonY(), halfW, BUTTON_HEIGHT);
        } else {
            sr.setColor(0.22f, 0.45f, 0.22f, 1f); // OK
            sr.rect(getMenuX(), getButtonY(), getMenuWidth(), BUTTON_HEIGHT);
        }

        sr.end();
        Gdx.gl.glDisable(Gdx.gl.GL_BLEND);
    }

    public void drawContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        if (!visible) return;

        font.setColor(1f, 0.85f, 0.4f, 1f);
        font.draw(batch, "Hidden Temple", getMenuX() + 12f, getMenuTop() - 12f);
        font.setColor(Color.WHITE);
        font.draw(batch, "X", getMenuX() + getMenuWidth() - 24f, getMenuTop() - 12f);

        float textY = getMenuTop() - HEADER_HEIGHT - 24f;

        if (result == null) {
            fontSmall.setColor(0.85f, 0.8f, 0.7f, 1f);
            drawWrapped(batch, fontSmall,
                "The ancient map crumbles to dust as you step through the ruins."
                    + " Consume it to reveal what the Temple holds?",
                getMenuX() + 12f, textY, getMenuWidth() - 24f);

            font.setColor(Color.WHITE);
            String openLabel = "[ Open Temple ]";
            float halfW = (getMenuWidth() - BUTTON_MARGIN) / 2f;
            glyphLayout.setText(font, openLabel);
            font.draw(batch, openLabel,
                getMenuX() + halfW / 2f - glyphLayout.width / 2f,
                getButtonY() + BUTTON_HEIGHT / 2f + glyphLayout.height / 2f);

            String cancelLabel = "[ Cancel ]";
            glyphLayout.setText(font, cancelLabel);
            font.draw(batch, cancelLabel,
                getMenuX() + halfW + BUTTON_MARGIN + halfW / 2f - glyphLayout.width / 2f,
                getButtonY() + BUTTON_HEIGHT / 2f + glyphLayout.height / 2f);
        } else {
            fontSmall.setColor(0.9f, 0.9f, 0.85f, 1f);
            // Gold icon prefixes the (now word-dropped, see rewardSummary)
            // wrapped text block — only the icon+first-line pairing is
            // exact; drawWrapped's own multi-line word-wrap is untouched
            // rather than taught to treat an icon as an atomic wrap unit,
            // not worth the complexity for this one reward-summary card.
            float iconSize = 16f;
            batch.draw(spriteManager.getTextureByKey("gold"),
                getMenuX() + 12f, textY - iconSize + 4f, iconSize, iconSize);
            drawWrapped(batch, fontSmall, rewardSummary(result),
                getMenuX() + 12f + iconSize + 4f, textY, getMenuWidth() - 24f - iconSize - 4f);

            font.setColor(Color.WHITE);
            String okLabel = "[ OK ]";
            glyphLayout.setText(font, okLabel);
            font.draw(batch, okLabel,
                getMenuX() + getMenuWidth() / 2f - glyphLayout.width / 2f,
                getButtonY() + BUTTON_HEIGHT / 2f + glyphLayout.height / 2f);
        }

        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    /** Minimal manual word-wrap — this card only ever holds a couple short sentences. */
    private void drawWrapped(SpriteBatch batch, BitmapFont font, String text, float x, float y, float maxWidth) {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        float lineY = y;
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            glyphLayout.setText(font, candidate);
            if (glyphLayout.width > maxWidth && line.length() > 0) {
                font.draw(batch, line.toString(), x, lineY);
                lineY -= glyphLayout.height + 6f;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (line.length() > 0) {
            font.draw(batch, line.toString(), x, lineY);
        }
    }

    /** Word dropped after "+N" — the caller draws a gold icon just before this text instead. */
    private String rewardSummary(HiddenTempleManager.TempleReward reward) {
        StringBuilder sb = new StringBuilder("+").append(reward.gold);
        if (reward.rewardType != null) {
            sb.append(", ").append(reward.rewardType).append(" Lv").append(reward.rewardLevel);
            if (reward.queued) sb.append(" (Wall Gate)");
        }
        if (reward.enchantedItemName != null) {
            sb.append(". Bonus find: ").append(reward.enchantedItemName).append("!");
        }
        return sb.toString();
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
        if (touchPos.x > getMenuX() + getMenuWidth() - 36f
            && touchPos.y > getMenuTop() - HEADER_HEIGHT) {
            close();
            return true;
        }
        // Outside the card
        if (touchPos.x < getMenuX() || touchPos.x > getMenuX() + getMenuWidth()
            || touchPos.y < getMenuBottom() || touchPos.y > getMenuTop()) {
            close();
            return true;
        }

        boolean inButtonRow = touchPos.y >= getButtonY() && touchPos.y <= getButtonY() + BUTTON_HEIGHT;
        if (!inButtonRow) return true;

        if (result == null) {
            float halfW = (getMenuWidth() - BUTTON_MARGIN) / 2f;
            boolean openHit = touchPos.x >= getMenuX() && touchPos.x <= getMenuX() + halfW;
            if (openHit) {
                openTemple();
            } else {
                close(); // Cancel
            }
        } else {
            close(); // OK
        }
        return true;
    }

    private void openTemple() {
        if (pendingItemId == null) { close(); return; }

        // Defends against a stale double-tap — if the item is already gone
        // (e.g. this popup was somehow reopened on an id already consumed),
        // useConsumable returns null and nothing further happens.
        Item consumed = eventManager.getInventory().useConsumable(pendingItemId);
        if (consumed == null) {
            Gdx.app.log(TAG, "openTemple: item already gone, id=" + pendingItemId);
            close();
            return;
        }

        result = eventManager.getHiddenTempleManager().openTemple();
        pendingItemId = null;
    }
}
