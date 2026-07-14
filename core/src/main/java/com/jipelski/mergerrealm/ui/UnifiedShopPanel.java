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
import com.jipelski.mergerrealm.util.DailyLoginManager;
import com.jipelski.mergerrealm.util.EventManager;
import com.jipelski.mergerrealm.util.GoldManager;
import com.jipelski.mergerrealm.util.RaidManager;
import com.jipelski.mergerrealm.util.SpriteManager;
import com.jipelski.mergerrealm.util.TextUtil;
import com.jipelski.mergerrealm.util.TrophyShop;
import com.jipelski.mergerrealm.util.TrophyShop.ShopEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified Shop — merges the old TrophyShopPanel + GoldShopPanel into one
 * panel with currencies sectioned by tab (never a blended single list).
 * Lifts InventoryMenu's tab-strip pattern (activeTab cursor + shared
 * scrollY reset on switch).
 *
 * Tabs:
 *   Equipment / Consumables / Rare — War Trophies, filtered from
 *     TrophyShop.getCatalog() by ShopEntry.category. Equipment and
 *     Consumables refresh Daily, Rare refreshes Weekly.
 *   Gold — the five Gold sinks (unchanged from the old GoldShopPanel):
 *     three actionable buy rows (fill resources, revive party, extra
 *     explore slot) plus two informational rows (instant spawn, speed
 *     exploration) that point to their in-context on-grid buttons.
 *   Daily — the 7-day login streak calendar. Delegates its drawing/hit-test
 *     to a DailyLoginPopup reference (set via setDailyLoginPopup) so the
 *     calendar strip and its claim state are a single source of truth
 *     shared with the auto-opens-on-launch popup, not a second copy.
 *   Outfit — Prince Outfits. Delegates its drawing/hit-test to an
 *     OutfitPanel reference (set via setOutfitPanel), same shared-rows
 *     pattern as Daily above — the standalone OutfitPanel (opened by
 *     tapping the already-selected Prince a second time) and this tab are
 *     two independently-openable entry points onto the same OutfitManager
 *     state, never both visible at once through normal input.
 *
 * Opened either on the Gold tab (top-bar gold chip) or the Equipment tab
 * (RaidPanel's [Shop] button) via open(int tab).
 */
public class UnifiedShopPanel {

    private static final String TAG = "UnifiedShopPanel";

    private boolean visible = false;

    // ── Tabs ──
    public static final int TAB_EQUIPMENT = 0;
    public static final int TAB_CONSUMABLES = 1;
    public static final int TAB_RARE = 2;
    public static final int TAB_GOLD = 3;
    public static final int TAB_DAILY = 4;
    public static final int TAB_OUTFITS = 5;
    private static final int NUM_TABS = 6;
    // "Outfit" kept short like Gold/Rare/Daily — 6 tabs now share the strip.
    private static final String[] TAB_LABELS = {"Equipment", "Consume", "Rare", "Gold", "Daily", "Outfit"};
    // Indexed by tab for tabs 0-2 — maps a trophy tab to its ShopEntry.category.
    private static final String[] TAB_CATEGORY = {"equipment", "consumable", "rare"};
    private int activeTab = TAB_EQUIPMENT;

    // ── Layout ──
    private static final float MARGIN = 16f;
    private static final float HEADER_HEIGHT = 56f;
    private static final float TAB_HEIGHT = 32f;
    private static final float ROW_HEIGHT_TROPHY = 38f;
    private static final float ROW_GAP_TROPHY = 2f;
    private static final float ROW_HEIGHT_GOLD = 60f;
    private static final float ROW_GAP_GOLD = 8f;

    // ── Scroll (trophy tabs only — the Gold tab's 5 rows always fit) ──
    private float scrollY = 0f;
    private float maxScrollY = 0f;

    // ── References ──
    private final EventManager eventManager;
    private final Viewport viewport;
    private final UITextureManager uiTex;
    private final SpriteManager spriteManager;
    private final GlyphLayout glyphLayout;

    // ── Touch ──
    private final Vector2 touchPos = new Vector2();
    private boolean touchDown = false;
    private float touchStartY = 0f;
    private boolean scrolling = false;

    // Filtered catalog for the active trophy tab (rebuilt on open/tab-switch)
    private final List<ShopEntry> filtered = new ArrayList<>();

    // ── Gold tab row model (unchanged from the old GoldShopPanel) ──
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

    // Daily tab's calendar strip is drawn/hit-tested by this shared reference
    // — see the class javadoc.
    private DailyLoginPopup dailyLoginPopup;

    // Outfit tab's rows are drawn/hit-tested by this shared reference — same
    // pattern as dailyLoginPopup above, see OutfitPanel's own class javadoc.
    private OutfitPanel outfitPanel;

    // Opened for the Fill Resources / Revive Party rows' [Ad N/5] affordance
    // — see AdRewardPopup's class javadoc for the full picture across all
    // 4 ad-eligible actions.
    private AdRewardPopup adRewardPopup;

    public UnifiedShopPanel(EventManager eventManager, Viewport viewport, UITextureManager uiTex,
                            SpriteManager spriteManager) {
        this.eventManager = eventManager;
        this.viewport = viewport;
        this.uiTex = uiTex;
        this.spriteManager = spriteManager;
        this.glyphLayout = new GlyphLayout();
    }

    public void setDailyLoginPopup(DailyLoginPopup popup) { this.dailyLoginPopup = popup; }
    public void setOutfitPanel(OutfitPanel panel) { this.outfitPanel = panel; }
    public void setAdRewardPopup(AdRewardPopup popup) { this.adRewardPopup = popup; }

    /** Fill Resources / Revive Party rows also offer a free ad-watch path; the other 3 sinks don't. */
    private AdManager.AdAction adActionFor(Sink s) {
        if (s.cost == GoldManager.COST_FILL_RESOURCES) return AdManager.AdAction.FILL_RESOURCES;
        if (s.cost == GoldManager.COST_RAID_REVIVE_ALL) return AdManager.AdAction.REVIVE_PARTY;
        return null;
    }

    // ══════════════════════════════════════════════════════════════
    // STATE
    // ══════════════════════════════════════════════════════════════

    public boolean isVisible() { return visible; }

    /** Opens on the Equipment tab. */
    public void open() { open(TAB_EQUIPMENT); }

    public void open(int tab) {
        visible = true;
        activeTab = (tab >= 0 && tab < NUM_TABS) ? tab : TAB_EQUIPMENT;
        // Refresh trophy stock whenever the shop opens, regardless of which
        // tab — keeps timers correct even before the player switches tabs.
        eventManager.getTrophyShop().checkRefresh();
        refreshFilteredForTab();
        recalculateScroll();
    }

    public void close() {
        visible = false;
    }

    private void refreshFilteredForTab() {
        filtered.clear();
        if (activeTab >= 0 && activeTab < TAB_CATEGORY.length) {
            String category = TAB_CATEGORY[activeTab];
            for (ShopEntry entry : TrophyShop.getCatalog()) {
                if (entry.category.equals(category)) filtered.add(entry);
            }
        }
    }

    private void recalculateScroll() {
        if (activeTab == TAB_GOLD || activeTab == TAB_DAILY || activeTab == TAB_OUTFITS) {
            maxScrollY = 0f;
            scrollY = 0f;
            return;
        }
        float totalHeight = filtered.size() * (ROW_HEIGHT_TROPHY + ROW_GAP_TROPHY);
        maxScrollY = Math.max(0, totalHeight - getContentHeight());
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
    private float getTabY() { return getMenuTop() - HEADER_HEIGHT - TAB_HEIGHT; }
    private float getContentTop() { return getTabY(); }
    private float getContentHeight() { return getContentTop() - getMenuBottom(); }

    // Daily tab's content rect, passed to DailyLoginPopup's shared calendar
    // renderer — same "8px inset" convention DailyLoginPopup uses for its
    // own standalone content area.
    private float getDailyX() { return getMenuX() + 8f; }
    private float getDailyY() { return getMenuBottom() + 8f; }
    private float getDailyWidth() { return getMenuWidth() - 16f; }
    private float getDailyHeight() { return getContentHeight() - 16f; }

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

        // ── Tab strip ──
        float tabWidth = getMenuWidth() / NUM_TABS;
        for (int i = 0; i < NUM_TABS; i++) {
            float tabX = getMenuX() + i * tabWidth;
            if (i == activeTab) sr.setColor(0.25f, 0.25f, 0.38f, 1f);
            else                sr.setColor(0.15f, 0.15f, 0.22f, 1f);
            sr.rect(tabX, getTabY(), tabWidth, TAB_HEIGHT);
        }
        // Active tab accent
        sr.setColor(0.4f, 0.75f, 0.4f, 1f);
        sr.rect(getMenuX() + activeTab * tabWidth, getTabY(), tabWidth, 2f);

        // ── Gold tab row backgrounds (trophy tabs draw no row backgrounds,
        // matching the old TrophyShopPanel's text-only rows) ──
        if (activeTab == TAB_GOLD) {
            GoldManager gm = eventManager.getGoldManager();
            float y = getContentTop() - ROW_HEIGHT_GOLD;
            for (Sink s : sinks) {
                boolean affordable = s.actionable && gm.canAfford(s.cost);
                if (affordable) sr.setColor(0.18f, 0.18f, 0.24f, 1f);
                else            sr.setColor(0.14f, 0.14f, 0.18f, 1f);
                sr.rect(getMenuX() + 8f, y, getMenuWidth() - 16f, ROW_HEIGHT_GOLD);
                y -= (ROW_HEIGHT_GOLD + ROW_GAP_GOLD);
            }
        }

        // ── Daily tab calendar strip — delegated to the shared renderer ──
        if (activeTab == TAB_DAILY && dailyLoginPopup != null) {
            dailyLoginPopup.drawCalendarBg(sr, getDailyX(), getDailyY(), getDailyWidth(), getDailyHeight());
        }

        // ── Outfit tab rows — delegated to the shared renderer ──
        if (activeTab == TAB_OUTFITS && outfitPanel != null) {
            outfitPanel.drawOutfitRowsBg(sr, getMenuX(), getMenuBottom(), getMenuWidth(), getContentHeight());
        }

        sr.end();
        Gdx.gl.glDisable(Gdx.gl.GL_BLEND);
    }

    public void drawContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        if (!visible) return;

        // ── Header ──
        font.setColor(Color.WHITE);
        font.draw(batch, "Shop", getMenuX() + 12f, getMenuTop() - 10f);
        font.draw(batch, "X", getMenuX() + getMenuWidth() - 28f, getMenuTop() - 10f);

        if (activeTab == TAB_GOLD) {
            GoldManager gm = eventManager.getGoldManager();
            fontSmall.setColor(1f, 0.85f, 0.25f, 1f);
            IconText.iconThenText(batch, fontSmall, glyphLayout, spriteManager, "gold",
                " " + gm.getGold(), getMenuX() + 12f, getMenuTop() - 32f, 16f);
        } else if (activeTab == TAB_DAILY) {
            DailyLoginManager dlm = eventManager.getDailyLoginManager();
            fontSmall.setColor(0.85f, 0.8f, 0.5f, 1f);
            fontSmall.draw(batch, "Streak: Day " + dlm.getDayToClaim() + "/" + DailyLoginManager.STREAK_LENGTH,
                getMenuX() + 12f, getMenuTop() - 32f);
        } else if (activeTab == TAB_OUTFITS) {
            GoldManager gm = eventManager.getGoldManager();
            fontSmall.setColor(1f, 0.85f, 0.25f, 1f);
            IconText.iconThenText(batch, fontSmall, glyphLayout, spriteManager, "gold",
                " " + gm.getGold(), getMenuX() + 12f, getMenuTop() - 32f, 16f);
        } else {
            TrophyShop shop = eventManager.getTrophyShop();
            RaidManager rm = eventManager.getRaidManager();
            fontSmall.setColor(0.85f, 0.8f, 0.5f, 1f);
            IconText.iconThenText(batch, fontSmall, glyphLayout, spriteManager, "trophy",
                " " + rm.getWarTrophies(), getMenuX() + 12f, getMenuTop() - 32f, 16f);

            fontSmall.setColor(0.5f, 0.5f, 0.6f, 1f);
            String timerLabel = (activeTab == TAB_RARE)
                ? "Weekly: " + TextUtil.formatDurationMsShort(shop.getTimeUntilWeeklyRefresh())
                : "Daily: " + TextUtil.formatDurationMsShort(shop.getTimeUntilDailyRefresh());
            fontSmall.draw(batch, timerLabel, getMenuX() + 12f, getMenuTop() - 46f);
        }

        // ── Tab labels ──
        float tabWidth = getMenuWidth() / NUM_TABS;
        for (int i = 0; i < NUM_TABS; i++) {
            float tabCenterX = getMenuX() + i * tabWidth + tabWidth / 2f;
            if (i == activeTab) fontSmall.setColor(1f, 1f, 1f, 1f);
            else                fontSmall.setColor(0.5f, 0.5f, 0.6f, 1f);
            glyphLayout.setText(fontSmall, TAB_LABELS[i]);
            fontSmall.draw(batch, TAB_LABELS[i],
                tabCenterX - glyphLayout.width / 2f,
                getTabY() + TAB_HEIGHT - 10f);
        }

        // ── Body ──
        if (activeTab == TAB_GOLD) {
            drawGoldRows(batch, fontSmall);
        } else if (activeTab == TAB_DAILY) {
            if (dailyLoginPopup != null) {
                dailyLoginPopup.drawCalendarContent(batch, font, fontSmall,
                    getDailyX(), getDailyY(), getDailyWidth(), getDailyHeight());
            }
        } else if (activeTab == TAB_OUTFITS) {
            if (outfitPanel != null) {
                outfitPanel.drawOutfitRowsContent(batch, fontSmall,
                    getMenuX(), getMenuBottom(), getMenuWidth(), getContentHeight());
            }
        } else {
            drawTrophyRows(batch, fontSmall);
        }

        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    private void drawTrophyRows(SpriteBatch batch, BitmapFont fontSmall) {
        TrophyShop shop = eventManager.getTrophyShop();
        RaidManager rm = eventManager.getRaidManager();
        int trophies = rm.getWarTrophies();

        float y = getContentTop() + scrollY;
        for (ShopEntry entry : filtered) {
            y -= (ROW_HEIGHT_TROPHY + ROW_GAP_TROPHY);

            // Skip if off screen
            if (y + ROW_HEIGHT_TROPHY < getMenuBottom() || y > getContentTop() + ROW_HEIGHT_TROPHY) continue;

            int stock = shop.getStock(entry);
            boolean canAfford = trophies >= entry.trophyCost;
            boolean inStock = stock > 0;
            boolean canBuy = canAfford && inStock;

            // Item name
            if (canBuy) fontSmall.setColor(0.8f, 0.8f, 0.9f, 1f);
            else        fontSmall.setColor(0.45f, 0.45f, 0.5f, 1f);
            fontSmall.draw(batch, entry.displayName, getMenuX() + 12f, y + 20f);

            // Level
            fontSmall.setColor(0.5f, 0.5f, 0.6f, 1f);
            fontSmall.draw(batch, "Lv" + entry.itemLevel, getMenuX() + 12f, y + 4f);

            // Cost
            if (canAfford) fontSmall.setColor(0.85f, 0.8f, 0.5f, 1f);
            else           fontSmall.setColor(0.6f, 0.3f, 0.3f, 1f);
            IconText.textThenIcon(batch, fontSmall, glyphLayout, spriteManager,
                String.valueOf(entry.trophyCost), "trophy",
                getMenuX() + getMenuWidth() - 150f, y + 20f, 14f);

            // Stock
            if (inStock) fontSmall.setColor(0.5f, 0.5f, 0.6f, 1f);
            else         fontSmall.setColor(0.6f, 0.3f, 0.3f, 1f);
            fontSmall.draw(batch, inStock ? "x" + stock : "SOLD OUT",
                getMenuX() + getMenuWidth() - 100f, y + 20f);

            // [Buy] button
            if (canBuy) {
                fontSmall.setColor(0.3f, 0.9f, 0.3f, 1f);
                fontSmall.draw(batch, "[Buy]", getMenuX() + getMenuWidth() - 48f, y + 20f);
            }
        }
    }

    private void drawGoldRows(SpriteBatch batch, BitmapFont fontSmall) {
        GoldManager gm = eventManager.getGoldManager();

        float y = getContentTop() - ROW_HEIGHT_GOLD;
        for (Sink s : sinks) {
            boolean affordable = s.actionable && gm.canAfford(s.cost);

            if (s.actionable) fontSmall.setColor(0.85f, 0.85f, 0.95f, 1f);
            else              fontSmall.setColor(0.55f, 0.55f, 0.6f, 1f);
            fontSmall.draw(batch, s.name, getMenuX() + 16f, y + ROW_HEIGHT_GOLD - 12f);

            fontSmall.setColor(0.55f, 0.55f, 0.62f, 1f);
            fontSmall.draw(batch, s.desc, getMenuX() + 16f, y + ROW_HEIGHT_GOLD - 30f);

            if (affordable) fontSmall.setColor(1f, 0.85f, 0.25f, 1f);
            else             fontSmall.setColor(0.6f, 0.45f, 0.2f, 1f);
            IconText.textThenIcon(batch, fontSmall, glyphLayout, spriteManager,
                String.valueOf(s.cost), "gold",
                getMenuX() + getMenuWidth() - 130f, y + ROW_HEIGHT_GOLD - 20f, 14f);

            if (s.actionable) {
                if (affordable) fontSmall.setColor(0.3f, 0.9f, 0.3f, 1f);
                else             fontSmall.setColor(0.4f, 0.4f, 0.45f, 1f);
                fontSmall.draw(batch, "[Buy]", getMenuX() + getMenuWidth() - 58f, y + ROW_HEIGHT_GOLD - 20f);
            } else {
                fontSmall.setColor(0.5f, 0.5f, 0.7f, 1f);
                fontSmall.draw(batch, "in-game", getMenuX() + getMenuWidth() - 62f, y + ROW_HEIGHT_GOLD - 20f);
            }

            // Free ad-watch alternative — bottom-right of the row, well below
            // the cost/[Buy] line (see the Gold-tab tap handler for the
            // matching non-overlapping hit-band split).
            AdManager.AdAction adAction = adActionFor(s);
            if (adAction != null) {
                int remaining = eventManager.getAdManager().getRemaining(adAction);
                if (remaining > 0) fontSmall.setColor(0.3f, 0.75f, 0.95f, 1f);
                else                fontSmall.setColor(0.4f, 0.4f, 0.45f, 1f);
                fontSmall.draw(batch, "[Ad " + remaining + "/" + AdManager.MAX_PER_DAY + "]",
                    getMenuX() + getMenuWidth() - 90f, y + 14f);
            }

            y -= (ROW_HEIGHT_GOLD + ROW_GAP_GOLD);
        }
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

        // The Gold tab's 5 rows, the Daily tab's calendar strip, and the
        // Outfit tab's 5 rows always fit — nothing to scroll — but the drag
        // must still be swallowed so it doesn't fall through to the grid
        // underneath.
        if (activeTab == TAB_GOLD || activeTab == TAB_DAILY || activeTab == TAB_OUTFITS) return true;

        touchPos.set(screenX, screenY);
        viewport.unproject(touchPos);

        float dy = touchPos.y - touchStartY;
        if (Math.abs(dy) > 6f) scrolling = true;

        if (scrolling) {
            scrollY += dy * 0.5f;
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

        // ── Tab switch ──
        if (touchPos.y >= getTabY() && touchPos.y <= getTabY() + TAB_HEIGHT) {
            float tabWidth = getMenuWidth() / NUM_TABS;
            int tab = (int) ((touchPos.x - getMenuX()) / tabWidth);
            if (tab >= 0 && tab < NUM_TABS) {
                activeTab = tab;
                scrollY = 0f;
                refreshFilteredForTab();
                recalculateScroll();
            }
            return true;
        }

        // ── Row taps ──
        if (activeTab == TAB_GOLD) {
            // Two non-overlapping hit-bands per row: [Buy] sits in the upper
            // portion (same y as the cost text), [Ad N/5] in the lower
            // portion (see drawGoldRows) — split by localY so a tap can never
            // land in both at once, even though their x-ranges are close.
            float buyX = getMenuX() + getMenuWidth() - 62f;
            float adX = getMenuX() + getMenuWidth() - 100f;
            float rowSplitY = 25f;
            float y = getContentTop() - ROW_HEIGHT_GOLD;
            for (Sink s : sinks) {
                if (touchPos.y >= y && touchPos.y <= y + ROW_HEIGHT_GOLD) {
                    float localY = touchPos.y - y;
                    AdManager.AdAction adAction = adActionFor(s);
                    if (adAction != null && adRewardPopup != null
                        && touchPos.x >= adX && localY < rowSplitY) {
                        adRewardPopup.open(adAction, false, -1, null);
                        return true;
                    }
                    if (s.actionable && touchPos.x >= buyX && localY >= rowSplitY) {
                        executeSink(s);
                        return true;
                    }
                    return true;
                }
                y -= (ROW_HEIGHT_GOLD + ROW_GAP_GOLD);
            }
        } else if (activeTab == TAB_DAILY) {
            if (dailyLoginPopup != null) {
                dailyLoginPopup.handleCalendarTap(touchPos.x, touchPos.y,
                    getDailyX(), getDailyY(), getDailyWidth(), getDailyHeight());
            }
        } else if (activeTab == TAB_OUTFITS) {
            if (outfitPanel != null) {
                outfitPanel.handleOutfitRowTap(touchPos.x, touchPos.y,
                    getMenuX(), getMenuBottom(), getMenuWidth(), getContentHeight());
            }
        } else {
            float buyX = getMenuX() + getMenuWidth() - 58f;
            if (touchPos.x >= buyX) {
                int entryIndex = getEntryIndexAtY(touchPos.y);
                if (entryIndex >= 0) {
                    TrophyShop shop = eventManager.getTrophyShop();
                    ShopEntry entry = filtered.get(entryIndex);
                    if (shop.purchase(entry)) {
                        Gdx.app.log(TAG, "Purchased: " + entry.displayName);
                    }
                }
            }
        }

        return true;
    }

    /**
     * Maps a Y coordinate to an index in the active tab's filtered list,
     * accounting for scroll offset. No section headers — each tab is a
     * single category.
     */
    private int getEntryIndexAtY(float worldY) {
        float y = getContentTop() + scrollY;
        for (int i = 0; i < filtered.size(); i++) {
            y -= (ROW_HEIGHT_TROPHY + ROW_GAP_TROPHY);
            if (worldY <= y + ROW_HEIGHT_TROPHY && worldY >= y) {
                return i;
            }
        }
        return -1;
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
