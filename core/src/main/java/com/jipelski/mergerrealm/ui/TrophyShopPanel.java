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
import com.jipelski.mergerrealm.util.TrophyShop;
import com.jipelski.mergerrealm.util.TrophyShop.ShopEntry;

import java.util.List;

/**
 * Trophy Shop UI — scrollable list of purchasable items.
 *
 * Sections:
 *   EQUIPMENT   — swords, shields, amulets (lv1-5), daily stock
 *   CONSUMABLES — potions (lv1-3), daily stock
 *   RARE        — phoenix feather, rune fragments, amulet of ascension, ancient map, weekly stock
 *
 * Header shows War Trophy balance and refresh timers.
 * Each row shows item name, cost, stock remaining, and [Buy] button.
 * Out-of-stock or unaffordable items are greyed out.
 */
public class TrophyShopPanel {

    private static final String TAG = "TrophyShopPanel";

    private boolean visible = false;

    // ── Layout ──
    private static final float MARGIN = 16f;
    private static final float HEADER_HEIGHT = 56f;
    private static final float SECTION_HEADER_HEIGHT = 24f;
    private static final float ROW_HEIGHT = 38f;
    private static final float ROW_GAP = 2f;
    private static final float BUY_BTN_WIDTH = 50f;

    // ── Scroll ──
    private float scrollY = 0f;
    private float maxScrollY = 0f;

    // ── References ──
    private final EventManager eventManager;
    private final Viewport viewport;
    private final UITextureManager uiTex;
    private final GlyphLayout glyphLayout;

    // ── Touch ──
    private final Vector2 touchPos = new Vector2();
    private boolean touchDown = false;
    private float touchStartY = 0f;
    private boolean scrolling = false;

    public TrophyShopPanel(EventManager eventManager, Viewport viewport,
                           UITextureManager uiTex) {
        this.eventManager = eventManager;
        this.viewport = viewport;
        this.uiTex = uiTex;
        this.glyphLayout = new GlyphLayout();
    }

    // ══════════════════════════════════════════════════════════════
    // STATE
    // ══════════════════════════════════════════════════════════════

    public boolean isVisible() { return visible; }

    public void open() {
        visible = true;
        scrollY = 0f;
        // Refresh stock when opening
        eventManager.getTrophyShop().checkRefresh();
        recalculateScroll();
    }

    public void close() {
        visible = false;
    }

    public void toggle() {
        if (visible) close();
        else open();
    }

    private void recalculateScroll() {
        // 3 section headers + all catalog entries
        int totalRows = TrophyShop.getCatalog().size() + 3; // 3 section headers
        float totalHeight = totalRows * (ROW_HEIGHT + ROW_GAP);
        float available = getContentHeight();
        maxScrollY = Math.max(0, totalHeight - available);
        scrollY = Math.min(scrollY, maxScrollY);
    }

    // ══════════════════════════════════════════════════════════════
    // LAYOUT
    // ══════════════════════════════════════════════════════════════

    private float getWorldWidth() { return LayoutConfig.WORLD_WIDTH; }
    private float getWorldHeight() { return viewport.getWorldHeight(); }
    private float getMenuX() { return MARGIN; }
    private float getMenuWidth() { return getWorldWidth() - MARGIN * 2; }
    private float getMenuTop() { return getWorldHeight() - MARGIN; }
    private float getMenuBottom() { return MARGIN; }
    private float getContentTop() { return getMenuTop() - HEADER_HEIGHT; }
    private float getContentHeight() { return getContentTop() - getMenuBottom(); }

    // ══════════════════════════════════════════════════════════════
    // DRAWING
    // ══════════════════════════════════════════════════════════════

    public void drawBackground(ShapeRenderer sr) {
        if (!visible) return;

        Gdx.gl.glEnable(Gdx.gl.GL_BLEND);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        // Dim
        sr.setColor(0f, 0f, 0f, 0.75f);
        sr.rect(0, 0, getWorldWidth(), getWorldHeight());

        // Panel
        sr.setColor(0.12f, 0.12f, 0.18f, 1f);
        sr.rect(getMenuX(), getMenuBottom(), getMenuWidth(), getMenuTop() - getMenuBottom());

        // Header
        sr.setColor(0.18f, 0.18f, 0.26f, 1f);
        sr.rect(getMenuX(), getMenuTop() - HEADER_HEIGHT, getMenuWidth(), HEADER_HEIGHT);

        sr.end();
        Gdx.gl.glDisable(Gdx.gl.GL_BLEND);
    }

    public void drawContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        if (!visible) return;

        RaidManager rm = eventManager.getRaidManager();
        TrophyShop shop = eventManager.getTrophyShop();

        // ── Header ──
        font.setColor(Color.WHITE);
        font.draw(batch, "Trophy Shop", getMenuX() + 12f, getMenuTop() - 10f);
        font.draw(batch, "X", getMenuX() + getMenuWidth() - 28f, getMenuTop() - 10f);

        // Trophy balance
        fontSmall.setColor(0.85f, 0.8f, 0.5f, 1f);
        fontSmall.draw(batch, "War Trophies: " + rm.getWarTrophies(),
            getMenuX() + 12f, getMenuTop() - 32f);

        // Refresh timers
        fontSmall.setColor(0.5f, 0.5f, 0.6f, 1f);
        fontSmall.draw(batch, "Daily: " + formatMs(shop.getTimeUntilDailyRefresh())
                + "  |  Weekly: " + formatMs(shop.getTimeUntilWeeklyRefresh()),
            getMenuX() + 12f, getMenuTop() - 46f);

        // ── Item list ──
        float y = getContentTop() + scrollY;
        int trophies = rm.getWarTrophies();
        List<ShopEntry> catalog = TrophyShop.getCatalog();

        String lastCategory = null;

        for (int i = 0; i < catalog.size(); i++) {
            ShopEntry entry = catalog.get(i);

            // Section header
            if (!entry.category.equals(lastCategory)) {
                lastCategory = entry.category;
                y -= SECTION_HEADER_HEIGHT;

                if (y > getMenuBottom() && y < getContentTop() + SECTION_HEADER_HEIGHT) {
                    fontSmall.setColor(0.5f, 0.7f, 0.5f, 1f);
                    String sectionName;
                    switch (entry.category) {
                        case "equipment":  sectionName = "── EQUIPMENT (Daily) ──"; break;
                        case "consumable": sectionName = "── CONSUMABLES (Daily) ──"; break;
                        case "rare":       sectionName = "── RARE (Weekly) ──"; break;
                        default:           sectionName = "── OTHER ──"; break;
                    }
                    fontSmall.draw(batch, sectionName, getMenuX() + 12f, y);
                }
            }

            y -= (ROW_HEIGHT + ROW_GAP);

            // Skip if off screen
            if (y + ROW_HEIGHT < getMenuBottom() || y > getContentTop() + ROW_HEIGHT) continue;

            int stock = shop.getStock(entry);
            boolean canAfford = trophies >= entry.trophyCost;
            boolean inStock = stock > 0;
            boolean canBuy = canAfford && inStock;

            // Item name
            fontSmall.setColor(canBuy
                ? new Color(0.8f, 0.8f, 0.9f, 1f)
                : new Color(0.45f, 0.45f, 0.5f, 1f));
            fontSmall.draw(batch, entry.displayName, getMenuX() + 12f, y + 20f);

            // Level
            fontSmall.setColor(0.5f, 0.5f, 0.6f, 1f);
            fontSmall.draw(batch, "Lv" + entry.itemLevel, getMenuX() + 12f, y + 4f);

            // Cost
            fontSmall.setColor(canAfford
                ? new Color(0.85f, 0.8f, 0.5f, 1f)
                : new Color(0.6f, 0.3f, 0.3f, 1f));
            String costText = entry.trophyCost + " T";
            fontSmall.draw(batch, costText, getMenuX() + getMenuWidth() - 150f, y + 20f);

            // Stock
            fontSmall.setColor(inStock
                ? new Color(0.5f, 0.5f, 0.6f, 1f)
                : new Color(0.6f, 0.3f, 0.3f, 1f));
            fontSmall.draw(batch, inStock ? "x" + stock : "SOLD OUT",
                getMenuX() + getMenuWidth() - 100f, y + 20f);

            // [Buy] button
            if (canBuy) {
                fontSmall.setColor(0.3f, 0.9f, 0.3f, 1f);
                fontSmall.draw(batch, "[Buy]",
                    getMenuX() + getMenuWidth() - 48f, y + 20f);
            }
        }

        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    // ══════════════════════════════════════════════════════════════
    // TOUCH HANDLING
    // ══════════════════════════════════════════════════════════════

    public boolean handleTouchDown(int screenX, int screenY) {
        if (!visible) return false;

        touchPos.set(screenX, screenY);
        viewport.unproject(touchPos);

        touchDown = true;
        touchStartY = touchPos.y;
        scrolling = false;
        return true;
    }

    public boolean handleTouchDragged(int screenX, int screenY) {
        if (!visible || !touchDown) return false;

        touchPos.set(screenX, screenY);
        viewport.unproject(touchPos);

        float dy = touchPos.y - touchStartY;
        if (Math.abs(dy) > 6f) scrolling = true;

        if (scrolling) {
            scrollY -= dy * 0.5f;
            scrollY = Math.max(0, Math.min(scrollY, maxScrollY));
            touchStartY = touchPos.y;
        }

        return true;
    }

    public boolean handleTouchUp(int screenX, int screenY) {
        if (!visible) return false;

        touchPos.set(screenX, screenY);
        viewport.unproject(touchPos);
        touchDown = false;

        if (scrolling) { scrolling = false; return true; }

        // ── Close button ──
        if (touchPos.x > getMenuX() + getMenuWidth() - 40f
            && touchPos.y > getMenuTop() - HEADER_HEIGHT) {
            close();
            return true;
        }

        // ── Outside menu ──
        if (touchPos.x < getMenuX() || touchPos.x > getMenuX() + getMenuWidth()
            || touchPos.y < getMenuBottom() || touchPos.y > getMenuTop()) {
            close();
            return true;
        }

        // ── Buy button tap ──
        float buyX = getMenuX() + getMenuWidth() - 58f;
        if (touchPos.x >= buyX) {
            int entryIndex = getEntryIndexAtY(touchPos.y);
            if (entryIndex >= 0) {
                TrophyShop shop = eventManager.getTrophyShop();
                ShopEntry entry = TrophyShop.getCatalog().get(entryIndex);
                if (shop.purchase(entry)) {
                    Gdx.app.log(TAG, "Purchased: " + entry.displayName);
                }
            }
        }

        return true;
    }

    /**
     * Maps a Y coordinate to a catalog entry index, accounting for
     * section headers and scroll offset.
     */
    private int getEntryIndexAtY(float worldY) {
        float y = getContentTop() + scrollY;
        String lastCategory = null;
        List<ShopEntry> catalog = TrophyShop.getCatalog();

        for (int i = 0; i < catalog.size(); i++) {
            ShopEntry entry = catalog.get(i);

            if (!entry.category.equals(lastCategory)) {
                lastCategory = entry.category;
                y -= SECTION_HEADER_HEIGHT;
            }

            y -= (ROW_HEIGHT + ROW_GAP);

            if (worldY <= y + ROW_HEIGHT && worldY >= y) {
                return i;
            }
        }
        return -1;
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    private String formatMs(long ms) {
        if (ms <= 0) return "now";
        long totalSec = ms / 1000;
        long hours = totalSec / 3600;
        long min = (totalSec % 3600) / 60;
        if (hours > 0) return hours + "h " + min + "m";
        return min + "m";
    }
}
