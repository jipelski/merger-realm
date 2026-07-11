package com.jipelski.mergerrealm.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.Viewport;

import com.jipelski.mergerrealm.util.TutorialManager;

/**
 * Visual layer for the first-time-player tutorial. Follows the same
 * batch-only draw contract as OfflinePopup: no ShapeRenderer here (the
 * pulsing target ring is a separate ShapeRenderer pass owned by
 * MergerRealmGame, which already has the grid-layout math needed to resolve
 * a semantic target like "cell:homestead" to a screen rect).
 *
 * Two presentations, chosen by TutorialManager.getCurrentStep().type:
 *  - TEXT steps (and contextual tips): a centered card over a full dim,
 *    fully modal — mirrors OfflinePopup exactly (Back/Next/Skip, or a
 *    single OK for tips).
 *  - INTERACTIVE steps: NO full dim (the player needs to see and touch the
 *    real grid/facility/Prince), just a compact bottom-docked instruction
 *    strip + a Skip chip. The strip only consumes touches inside its own
 *    rect — everything else falls through to the normal input chain so the
 *    taught action (drag-merge, tap-to-spawn, drag-to-dismiss) can actually
 *    happen; TutorialManager advances the step via the resulting game event,
 *    not via this class.
 */
public class TutorialOverlay {

    private final TutorialManager tutorialManager;
    private final Viewport viewport;
    private final UITextureManager uiTex;
    private final GlyphLayout glyphLayout;
    private final Vector2 touchPos = new Vector2();

    // ── Layout constants ──
    private static final float CARD_WIDTH = 320f;
    private static final float CARD_HEIGHT = 200f;
    private static final float PADDING = 16f;
    private static final float BTN_WIDTH = 84f;
    private static final float BTN_HEIGHT = 32f;
    private static final float SKIP_WIDTH = 56f;
    private static final float SKIP_HEIGHT = 22f;
    private static final float STRIP_HEIGHT = 64f;

    // ── Computed layout (updateLayout()) ──
    private float cardX, cardY;
    private float backX, backY;
    private float nextX, nextY;
    private float tipOkX, tipOkY;
    private float cardSkipX, cardSkipY;
    private float stripX, stripY, stripW, stripH;
    private float stripSkipX, stripSkipY;

    public TutorialOverlay(TutorialManager tutorialManager, Viewport viewport, UITextureManager uiTex) {
        this.tutorialManager = tutorialManager;
        this.viewport = viewport;
        this.uiTex = uiTex;
        this.glyphLayout = new GlyphLayout();
    }

    /** Call after LayoutConfig.setActualHeight(...) — in create() and on resize(). */
    public void updateLayout() {
        float actualHeight = LayoutConfig.getActualHeight();

        cardX = (LayoutConfig.WORLD_WIDTH - CARD_WIDTH) / 2f;
        cardY = (actualHeight - CARD_HEIGHT) / 2f;

        backX = cardX + PADDING;
        backY = cardY + PADDING;
        nextX = cardX + CARD_WIDTH - PADDING - BTN_WIDTH;
        nextY = cardY + PADDING;
        tipOkX = cardX + (CARD_WIDTH - BTN_WIDTH) / 2f;
        tipOkY = cardY + PADDING;

        cardSkipX = cardX + CARD_WIDTH - SKIP_WIDTH - 6f;
        cardSkipY = cardY + CARD_HEIGHT - SKIP_HEIGHT - 6f;

        stripX = 12f;
        stripY = LayoutConfig.getBottomBarHeight() + 10f;
        stripW = LayoutConfig.WORLD_WIDTH - 24f;
        stripH = STRIP_HEIGHT;
        stripSkipX = stripX + stripW - SKIP_WIDTH - 8f;
        stripSkipY = stripY + (stripH - SKIP_HEIGHT) / 2f;
    }

    public boolean isVisible() {
        return tutorialManager.getCurrentStep() != null || tutorialManager.getActiveTip() != null;
    }

    // ── Input ──

    /** Only consumes if the touch lands on one of this overlay's own controls — see class javadoc. */
    public boolean handleTouchDown(int screenX, int screenY) {
        if (!isVisible()) return false;
        touchPos.set(screenX, screenY);
        viewport.unproject(touchPos);
        return hitTest(touchPos.x, touchPos.y) != null;
    }

    public boolean handleTouchUp(int screenX, int screenY) {
        if (!isVisible()) return false;
        touchPos.set(screenX, screenY);
        viewport.unproject(touchPos);
        String hit = hitTest(touchPos.x, touchPos.y);

        if (hit == null) {
            // Not on one of our own controls. TEXT steps and tips are fully
            // modal (mirrors OfflinePopup) — swallow the stray tap. An
            // INTERACTIVE step is not modal, so let it fall through to the
            // grid/facility/Prince underneath.
            return isModalStep();
        }

        switch (hit) {
            case "skip":  tutorialManager.skip(); break;
            case "next":  tutorialManager.next(); break;
            case "back":  tutorialManager.back(); break;
            case "tipOk": tutorialManager.dismissActiveTip(); break;
            case "strip": break; // absorbed the tap, no action
            default: break;
        }
        return true;
    }

    private boolean isModalStep() {
        if (tutorialManager.getActiveTip() != null) return true;
        TutorialManager.TutorialStep step = tutorialManager.getCurrentStep();
        return step != null && step.type == TutorialManager.StepType.TEXT;
    }

    private String hitTest(float wx, float wy) {
        TutorialManager.TutorialStep tip = tutorialManager.getActiveTip();
        if (tip != null) {
            return hit(tipOkX, tipOkY, BTN_WIDTH, BTN_HEIGHT, wx, wy) ? "tipOk" : null;
        }

        TutorialManager.TutorialStep step = tutorialManager.getCurrentStep();
        if (step == null) return null;

        if (step.type == TutorialManager.StepType.TEXT) {
            if (hit(cardSkipX, cardSkipY, SKIP_WIDTH, SKIP_HEIGHT, wx, wy)) return "skip";
            if (hit(nextX, nextY, BTN_WIDTH, BTN_HEIGHT, wx, wy)) return "next";
            if (!tutorialManager.isFirstStep()
                && hit(backX, backY, BTN_WIDTH, BTN_HEIGHT, wx, wy)) return "back";
            return null;
        }

        // INTERACTIVE
        if (hit(stripSkipX, stripSkipY, SKIP_WIDTH, SKIP_HEIGHT, wx, wy)) return "skip";
        if (hit(stripX, stripY, stripW, stripH, wx, wy)) return "strip";
        return null;
    }

    private boolean hit(float x, float y, float w, float h, float px, float py) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }

    // ── Drawing (batch-only, called inside the caller's batch.begin/end) ──

    public void draw(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        TutorialManager.TutorialStep tip = tutorialManager.getActiveTip();
        if (tip != null) {
            drawCard(batch, font, fontSmall, tip.title, tip.body, true);
            return;
        }

        TutorialManager.TutorialStep step = tutorialManager.getCurrentStep();
        if (step == null) return;

        if (step.type == TutorialManager.StepType.TEXT) {
            drawCard(batch, font, fontSmall, step.title, step.body, false);
        } else {
            drawInteractiveStrip(batch, font, fontSmall, step);
        }
    }

    private void drawCard(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall,
                          String title, String body, boolean isTip) {
        // Dim background — same technique as OfflinePopup.
        batch.draw(uiTex.popupOverlay, 0, 0, LayoutConfig.WORLD_WIDTH, LayoutConfig.getActualHeight());
        uiTex.drawPanel(batch, uiTex.popupPanel, cardX, cardY, CARD_WIDTH, CARD_HEIGHT);

        float centerX = cardX + CARD_WIDTH / 2f;
        float textX = cardX + PADDING;
        float textWidth = CARD_WIDTH - PADDING * 2;

        font.setColor(1f, 0.9f, 0.4f, 1f);
        glyphLayout.setText(font, title);
        font.draw(batch, title, centerX - glyphLayout.width / 2f,
            cardY + CARD_HEIGHT - PADDING - 4f);

        fontSmall.setColor(0.85f, 0.85f, 0.9f, 1f);
        fontSmall.draw(batch, body, textX, cardY + CARD_HEIGHT - PADDING - 32f,
            textWidth, Align.left, true);

        if (isTip) {
            drawButton(batch, font, uiTex.btnNormal, tipOkX, tipOkY, BTN_WIDTH, BTN_HEIGHT, "OK");
        } else {
            drawButton(batch, font, uiTex.btnDisabled, cardSkipX, cardSkipY,
                SKIP_WIDTH, SKIP_HEIGHT, "Skip");
            if (!tutorialManager.isFirstStep()) {
                drawButton(batch, font, uiTex.btnNormal, backX, backY, BTN_WIDTH, BTN_HEIGHT, "Back");
            }
            String nextLabel = tutorialManager.isLastStep() ? "Done" : "Next";
            drawButton(batch, font, uiTex.btnActive, nextX, nextY, BTN_WIDTH, BTN_HEIGHT, nextLabel);
        }

        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    private void drawInteractiveStrip(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall,
                                      TutorialManager.TutorialStep step) {
        uiTex.drawPanel(batch, uiTex.panelDark, stripX, stripY, stripW, stripH);

        float textX = stripX + 12f;
        float textWidth = stripW - 24f - SKIP_WIDTH - 8f;

        font.setColor(1f, 0.9f, 0.4f, 1f);
        font.draw(batch, step.title, textX, stripY + stripH - 8f, textWidth, Align.left, true);

        fontSmall.setColor(0.85f, 0.85f, 0.9f, 1f);
        fontSmall.draw(batch, step.body, textX, stripY + stripH - 28f, textWidth, Align.left, true);

        drawButton(batch, font, uiTex.btnDisabled, stripSkipX, stripSkipY,
            SKIP_WIDTH, SKIP_HEIGHT, "Skip");

        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    // ── Small button helper — always measures and draws with the same font ──

    private void drawButton(SpriteBatch batch, BitmapFont font, NinePatch patch,
                            float x, float y, float w, float h, String label) {
        uiTex.drawPanel(batch, patch, x, y, w, h);
        font.setColor(Color.WHITE);
        glyphLayout.setText(font, label);
        font.draw(batch, label,
            x + (w - glyphLayout.width) / 2f,
            y + h / 2f + glyphLayout.height / 2f);
    }
}
