package com.jipelski.mergerrealm.ui;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import com.jipelski.mergerrealm.util.SpriteManager;

import java.util.Arrays;
import java.util.List;

/**
 * Shared "line of mixed text + inline sprite icon" drawing helper — lifted
 * out of InfoPanel (which introduced this Seg/drawSegLine idea first, to
 * de-crowd its wiki-style stat tables with unit/resource NAME tokens
 * rendered as icons instead of words) since the 2026-07-14 placeholder-icon
 * pass for items/resources/Gold/War Trophies/Boss Tokens needs the exact
 * same capability in ~10 other panels. Single source of truth now —
 * InfoPanel itself uses this instead of keeping its own private copy.
 */
public final class IconText {

    private static final float DEFAULT_ICON_GAP = 2f;

    private IconText() {}

    /** One rendered token within a line: either a text run or a sprite icon (never both). */
    public static class Seg {
        final String text;
        final String iconKey;
        private Seg(String text, String iconKey) { this.text = text; this.iconKey = iconKey; }
        public static Seg text(String t) { return new Seg(t, null); }
        public static Seg icon(String key) { return new Seg(null, key); }
        public boolean isIcon() { return iconKey != null; }
    }

    /**
     * Draws segs left-to-right starting at x: icon segments as fixed-size
     * (iconSize x iconSize) sprites, text segments measured via glyphLayout
     * so the next segment's cursor lands right after it. No wrapping — same
     * single-line assumption InfoPanel's original drawSegLine made. Returns
     * the total width drawn, for callers that need to right-align (e.g. a
     * HUD chip sized to fit its content).
     */
    public static float draw(SpriteBatch batch, BitmapFont font, GlyphLayout glyphLayout,
                              SpriteManager spriteManager, List<Seg> segs,
                              float x, float y, float iconSize) {
        float cursor = x;
        float iconY = y - iconSize + 2f;
        for (Seg s : segs) {
            if (s.isIcon()) {
                batch.draw(spriteManager.getTextureByKey(s.iconKey), cursor, iconY, iconSize, iconSize);
                cursor += iconSize + DEFAULT_ICON_GAP;
            } else {
                glyphLayout.setText(font, s.text);
                font.draw(batch, s.text, cursor, y);
                cursor += glyphLayout.width;
            }
        }
        return cursor - x;
    }

    /** Convenience for the common 2-segment "icon then text" case, e.g. [icon] "142". */
    public static float iconThenText(SpriteBatch batch, BitmapFont font, GlyphLayout glyphLayout,
                                      SpriteManager spriteManager, String iconKey, String text,
                                      float x, float y, float iconSize) {
        return draw(batch, font, glyphLayout, spriteManager,
            Arrays.asList(Seg.icon(iconKey), Seg.text(text)), x, y, iconSize);
    }

    /** Convenience for the common 2-segment "text then icon" case, e.g. "142" [icon]. */
    public static float textThenIcon(SpriteBatch batch, BitmapFont font, GlyphLayout glyphLayout,
                                      SpriteManager spriteManager, String text, String iconKey,
                                      float x, float y, float iconSize) {
        return draw(batch, font, glyphLayout, spriteManager,
            Arrays.asList(Seg.text(text), Seg.icon(iconKey)), x, y, iconSize);
    }

    /**
     * Width a draw()/iconThenText()/textThenIcon() call would occupy,
     * without actually drawing — needed by callers that must size a
     * background box (e.g. the HUD gold chip's NinePatch panel) before the
     * pass that draws the segs themselves.
     */
    public static float measure(BitmapFont font, GlyphLayout glyphLayout, List<Seg> segs, float iconSize) {
        float width = 0f;
        for (Seg s : segs) {
            if (s.isIcon()) {
                width += iconSize + DEFAULT_ICON_GAP;
            } else {
                glyphLayout.setText(font, s.text);
                width += glyphLayout.width;
            }
        }
        return width;
    }
}
