package com.jipelski.mergerrealm.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;

import com.jipelski.mergerrealm.util.AdManager;
import com.jipelski.mergerrealm.util.EventManager;
import com.jipelski.mergerrealm.util.GoldManager;

/**
 * Shared "Watch Ad" confirm popup for the 4 gameplay actions that also have
 * an existing Gold-cost path (see GoldManager/AdManager). Mirrors
 * HiddenTemplePopup's CONFIRM→RESULT two-state card shape exactly, but with
 * up to 3 buttons instead of 2: [Watch Ad N/5], an optional [Pay X Gold]
 * (only when opened with showGold=true), and [Cancel].
 *
 * There is no real ad SDK yet — tapping Watch Ad grants the reward
 * immediately (see attemptWatchAd()'s TODO, and AdManager.watchAd() for the
 * matching manager-side hook).
 *
 * One instance is shared by every entry point (UnifiedShopPanel's Gold tab
 * for Fill Resources/Revive Party, ExplorePanel's per-slot Rush button for
 * Speed Exploration, and a periodic facility's 2nd tap for Instant Spawn —
 * the only one of the 4 that also passes showGold=true, since it had no
 * prior button of its own). Each entry point calls open(...) with the
 * action-specific context (slotIndex / facilityId) needed to perform it.
 */
public class AdRewardPopup {

    private static final String TAG = "AdRewardPopup";

    private static final float MARGIN = 60f;
    private static final float HEADER_HEIGHT = 40f;
    private static final float BUTTON_HEIGHT = 36f;
    private static final float BUTTON_MARGIN = 12f;

    private final EventManager eventManager;
    private final Viewport viewport;
    private final GlyphLayout glyphLayout;

    private final Vector2 touchPos = new Vector2();

    private boolean visible = false;
    private AdManager.AdAction pendingAction; // CONFIRM subject
    private int pendingSlotIndex = -1;        // SPEED_EXPLORATION only
    private String pendingFacilityId;         // INSTANT_SPAWN only
    private boolean showGold;                 // whether to also offer the Gold path
    private String resultText;                // non-null once resolved — RESULT state

    public AdRewardPopup(EventManager eventManager, Viewport viewport, UITextureManager uiTex) {
        this.eventManager = eventManager;
        this.viewport = viewport;
        this.glyphLayout = new GlyphLayout();
        // uiTex accepted for constructor-signature symmetry with the other
        // popups (HiddenTemplePopup/DailyLoginPopup/OfflinePopup).
    }

    public boolean isVisible() { return visible; }

    /**
     * Opens in CONFIRM state. slotIndex/facilityId are only read by the
     * action they apply to (SPEED_EXPLORATION / INSTANT_SPAWN respectively)
     * — pass -1 / null otherwise. showGold also renders a [Pay X Gold]
     * button alongside [Watch Ad].
     */
    public void open(AdManager.AdAction action, boolean showGold, int slotIndex, String facilityId) {
        this.pendingAction = action;
        this.showGold = showGold;
        this.pendingSlotIndex = slotIndex;
        this.pendingFacilityId = facilityId;
        this.resultText = null;
        this.visible = true;
    }

    public void close() {
        visible = false;
        pendingAction = null;
        pendingFacilityId = null;
        pendingSlotIndex = -1;
        resultText = null;
    }

    // ── Layout helpers ──
    private float getWorldWidth() { return LayoutConfig.WORLD_WIDTH; }
    private float getWorldHeight() { return viewport.getWorldHeight(); }
    private float getMenuX() { return MARGIN; }
    private float getMenuWidth() { return getWorldWidth() - MARGIN * 2; }
    private float getMenuTop() { return getWorldHeight() / 2f + 110f; }
    private float getMenuBottom() { return getWorldHeight() / 2f - 110f; }
    private float getButtonY() { return getMenuBottom() + BUTTON_MARGIN; }

    /** Number of CONFIRM-state buttons: Watch Ad + (Pay Gold?) + Cancel. */
    private int buttonCount() { return showGold ? 3 : 2; }
    private float getButtonWidth() {
        int n = buttonCount();
        return (getMenuWidth() - BUTTON_MARGIN * (n - 1)) / n;
    }
    private float getButtonX(int index) {
        return getMenuX() + index * (getButtonWidth() + BUTTON_MARGIN);
    }
    private int payGoldIndex() { return showGold ? 1 : -1; }
    private int cancelIndex() { return showGold ? 2 : 1; }

    // ══════════════════════════════════════════════════════════════
    // ACTION TEXT
    // ══════════════════════════════════════════════════════════════

    private String descriptionFor(AdManager.AdAction action) {
        switch (action) {
            case FILL_RESOURCES:     return "Fill Food, Wood & Iron to max?";
            case REVIVE_PARTY:       return "Revive all fallen party members at 50% HP?";
            case INSTANT_SPAWN:      return "Instantly trigger this facility's spawn, skipping its timer?";
            case SPEED_EXPLORATION:  return "Halve this unit's remaining time on the trip home?";
            default:                 return "";
        }
    }

    private String successTextFor(AdManager.AdAction action) {
        switch (action) {
            case FILL_RESOURCES:     return "Resources filled to max!";
            case REVIVE_PARTY:       return "Party revived!";
            case INSTANT_SPAWN:      return "Spawn triggered!";
            case SPEED_EXPLORATION:  return "Return time halved!";
            default:                 return "Done!";
        }
    }

    private int costFor(AdManager.AdAction action) {
        switch (action) {
            case FILL_RESOURCES:     return GoldManager.COST_FILL_RESOURCES;
            case REVIVE_PARTY:       return GoldManager.COST_RAID_REVIVE_ALL;
            case INSTANT_SPAWN:      return GoldManager.COST_INSTANT_SPAWN;
            case SPEED_EXPLORATION:  return GoldManager.COST_SPEED_EXPLORATION;
            default:                 return 0;
        }
    }

    /** Pays Gold for pendingAction via GoldManager's normal charged path. */
    private boolean payGold(GoldManager gm) {
        switch (pendingAction) {
            case FILL_RESOURCES:     return gm.fillResources();
            case REVIVE_PARTY:       return gm.reviveRaidParty();
            case INSTANT_SPAWN:      return gm.instantPeriodicSpawn(pendingFacilityId);
            case SPEED_EXPLORATION:  return gm.speedUpExploration(pendingSlotIndex);
            default:                 return false;
        }
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
        sr.setColor(0.08f, 0.12f, 0.18f, 1f); // screen/media tint, distinct from other popups
        sr.rect(getMenuX(), getMenuBottom(), getMenuWidth(), getMenuTop() - getMenuBottom());
        sr.setColor(0.12f, 0.2f, 0.3f, 1f);
        sr.rect(getMenuX(), getMenuTop() - HEADER_HEIGHT, getMenuWidth(), HEADER_HEIGHT);

        if (resultText == null) {
            boolean canWatch = pendingAction != null && eventManager.getAdManager().canWatch(pendingAction);
            if (canWatch) sr.setColor(0.18f, 0.4f, 0.55f, 1f);
            else          sr.setColor(0.2f, 0.22f, 0.25f, 1f);
            sr.rect(getButtonX(0), getButtonY(), getButtonWidth(), BUTTON_HEIGHT);

            if (showGold) {
                boolean canAfford = pendingAction != null
                    && eventManager.getGoldManager().canAfford(costFor(pendingAction));
                if (canAfford) sr.setColor(0.45f, 0.38f, 0.1f, 1f);
                else            sr.setColor(0.22f, 0.2f, 0.15f, 1f);
                sr.rect(getButtonX(payGoldIndex()), getButtonY(), getButtonWidth(), BUTTON_HEIGHT);
            }

            sr.setColor(0.35f, 0.18f, 0.18f, 1f); // Cancel
            sr.rect(getButtonX(cancelIndex()), getButtonY(), getButtonWidth(), BUTTON_HEIGHT);
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
        font.draw(batch, "Watch Ad", getMenuX() + 12f, getMenuTop() - 12f);
        font.setColor(Color.WHITE);
        font.draw(batch, "X", getMenuX() + getMenuWidth() - 24f, getMenuTop() - 12f);

        float textY = getMenuTop() - HEADER_HEIGHT - 24f;

        if (resultText == null && pendingAction != null) {
            fontSmall.setColor(0.85f, 0.85f, 0.9f, 1f);
            drawWrapped(batch, fontSmall, descriptionFor(pendingAction),
                getMenuX() + 12f, textY, getMenuWidth() - 24f);

            int remaining = eventManager.getAdManager().getRemaining(pendingAction);
            drawCenteredLabel(batch, font, "[ Watch Ad  " + remaining + "/" + AdManager.MAX_PER_DAY + " ]",
                getButtonX(0), getButtonWidth());

            if (showGold) {
                drawCenteredLabel(batch, font, "[ Pay " + costFor(pendingAction) + " Gold ]",
                    getButtonX(payGoldIndex()), getButtonWidth());
            }

            drawCenteredLabel(batch, font, "[ Cancel ]", getButtonX(cancelIndex()), getButtonWidth());
        } else if (resultText != null) {
            fontSmall.setColor(0.9f, 0.9f, 0.85f, 1f);
            drawWrapped(batch, fontSmall, resultText, getMenuX() + 12f, textY, getMenuWidth() - 24f);

            drawCenteredLabel(batch, font, "[ OK ]", getMenuX(), getMenuWidth());
        }

        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    private void drawCenteredLabel(SpriteBatch batch, BitmapFont font, String label, float boxX, float boxW) {
        font.setColor(Color.WHITE);
        glyphLayout.setText(font, label);
        font.draw(batch, label,
            boxX + boxW / 2f - glyphLayout.width / 2f,
            getButtonY() + BUTTON_HEIGHT / 2f + glyphLayout.height / 2f);
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

        if (resultText == null) {
            int index = buttonIndexAt(touchPos.x);
            if (index == 0) {
                attemptWatchAd();
            } else if (index == payGoldIndex()) {
                attemptPayGold();
            } else if (index == cancelIndex()) {
                close();
            }
        } else {
            close(); // OK
        }
        return true;
    }

    /** Which CONFIRM-row button (0..buttonCount()-1) contains x, or -1. */
    private int buttonIndexAt(float x) {
        int n = buttonCount();
        for (int i = 0; i < n; i++) {
            float bx = getButtonX(i);
            if (x >= bx && x <= bx + getButtonWidth()) return i;
        }
        return -1;
    }

    private void attemptWatchAd() {
        if (pendingAction == null) { close(); return; }

        // TODO: a real rewarded-ad SDK call belongs here — play the ad and
        // only proceed to AdManager.watchAd() on its "reward earned"
        // callback. This placeholder grants immediately.
        boolean ok = eventManager.getAdManager().watchAd(pendingAction, pendingSlotIndex, pendingFacilityId);
        resultText = ok ? successTextFor(pendingAction) : "No ad available right now — try again tomorrow!";
        Gdx.app.log(TAG, "Watch Ad (" + pendingAction + "): " + (ok ? "granted" : "unavailable"));
    }

    private void attemptPayGold() {
        if (pendingAction == null) { close(); return; }

        boolean ok = payGold(eventManager.getGoldManager());
        resultText = ok ? successTextFor(pendingAction) : "Not enough Gold.";
        Gdx.app.log(TAG, "Pay Gold (" + pendingAction + "): " + (ok ? "granted" : "failed"));
    }
}
