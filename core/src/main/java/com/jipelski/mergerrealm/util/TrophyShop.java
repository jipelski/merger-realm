package com.jipelski.mergerrealm.util;

import com.badlogic.gdx.Gdx;

import com.jipelski.mergerrealm.model.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trophy Shop — spend War Trophies on guaranteed item purchases.
 *
 * Stock is limited and refreshes on timers:
 *   - Daily items: equipment (lv1-5), potions (lv1-3)
 *   - Weekly items: phoenix feather, rune fragments, amulet of ascension, ancient map
 *
 * Stock is tracked per shop entry (type + level).
 * Refresh timestamps are stored and checked against System.currentTimeMillis().
 */
public class TrophyShop {

    private static final String TAG = "TrophyShop";

    public static final long DAILY_MS  = 24L * 60 * 60 * 1000;
    public static final long WEEKLY_MS = 7L * 24 * 60 * 60 * 1000;

    // ── Shop entry definition ──
    public static class ShopEntry {
        public final String itemType;
        public final int itemLevel;
        public final String displayName;
        public final int trophyCost;
        public final int maxStock;
        public final long refreshInterval; // DAILY_MS or WEEKLY_MS
        public final String category;      // "equipment", "consumable", "rare"

        public ShopEntry(String itemType, int itemLevel, String displayName,
                         int trophyCost, int maxStock, long refreshInterval,
                         String category) {
            this.itemType = itemType;
            this.itemLevel = itemLevel;
            this.displayName = displayName;
            this.trophyCost = trophyCost;
            this.maxStock = maxStock;
            this.refreshInterval = refreshInterval;
            this.category = category;
        }

        public String getStockKey() { return itemType + "_" + itemLevel; }
    }

    // ── All shop entries (static catalog) ──
    private static final List<ShopEntry> CATALOG = new ArrayList<>();
    static {
        // Equipment — daily refresh, 3 stock each
        CATALOG.add(new ShopEntry("sword", 1, "Rusty Sword",       10, 3, DAILY_MS, "equipment"));
        CATALOG.add(new ShopEntry("sword", 2, "Iron Sword",        20, 3, DAILY_MS, "equipment"));
        CATALOG.add(new ShopEntry("sword", 3, "Steel Sword",       35, 3, DAILY_MS, "equipment"));
        CATALOG.add(new ShopEntry("sword", 4, "Mithril Blade",     55, 2, DAILY_MS, "equipment"));
        CATALOG.add(new ShopEntry("sword", 5, "Dragonslayer",      80, 1, DAILY_MS, "equipment"));

        CATALOG.add(new ShopEntry("shield", 1, "Wooden Buckler",   10, 3, DAILY_MS, "equipment"));
        CATALOG.add(new ShopEntry("shield", 2, "Iron Shield",      20, 3, DAILY_MS, "equipment"));
        CATALOG.add(new ShopEntry("shield", 3, "Steel Kite Shield",35, 3, DAILY_MS, "equipment"));
        CATALOG.add(new ShopEntry("shield", 4, "Tower Shield",     55, 2, DAILY_MS, "equipment"));
        CATALOG.add(new ShopEntry("shield", 5, "Aegis",            80, 1, DAILY_MS, "equipment"));

        CATALOG.add(new ShopEntry("amulet", 1, "Copper Charm",     10, 3, DAILY_MS, "equipment"));
        CATALOG.add(new ShopEntry("amulet", 2, "Silver Pendant",   20, 3, DAILY_MS, "equipment"));
        CATALOG.add(new ShopEntry("amulet", 3, "Gold Medallion",   35, 3, DAILY_MS, "equipment"));
        CATALOG.add(new ShopEntry("amulet", 4, "Ruby Amulet",      55, 2, DAILY_MS, "equipment"));
        CATALOG.add(new ShopEntry("amulet", 5, "Crown of Ages",    80, 1, DAILY_MS, "equipment"));

        // Consumables — daily refresh
        CATALOG.add(new ShopEntry("potion", 1, "Weak Potion",       5, 3, DAILY_MS, "consumable"));
        CATALOG.add(new ShopEntry("potion", 2, "Strong Potion",    10, 3, DAILY_MS, "consumable"));
        CATALOG.add(new ShopEntry("potion", 3, "Royal Elixir",     15, 2, DAILY_MS, "consumable"));

        // Rare — weekly refresh
        CATALOG.add(new ShopEntry("phoenix_feather", 1, "Phoenix Feather", 25, 2, WEEKLY_MS, "rare"));
        CATALOG.add(new ShopEntry("rune_fragment_might", 1, "Might Fragment", 20, 2, WEEKLY_MS, "rare"));
        CATALOG.add(new ShopEntry("rune_fragment_vitality", 1, "Vitality Fragment", 20, 2, WEEKLY_MS, "rare"));
        CATALOG.add(new ShopEntry("rune_fragment_fortune", 1, "Fortune Fragment", 20, 2, WEEKLY_MS, "rare"));
        CATALOG.add(new ShopEntry("rune_fragment_swiftness", 1, "Swiftness Fragment", 20, 2, WEEKLY_MS, "rare"));
        CATALOG.add(new ShopEntry("amulet_of_ascension", 1, "Amulet of Ascension", 100, 1, WEEKLY_MS, "rare"));
        CATALOG.add(new ShopEntry("ancient_map", 1, "Ancient Map", 60, 1, WEEKLY_MS, "rare"));
    }

    public static List<ShopEntry> getCatalog() { return CATALOG; }

    // ── Stock state ──
    // stockKey → remaining stock count
    private Map<String, Integer> currentStock = new HashMap<>();

    // Refresh timestamps: "daily" → last daily refresh time, "weekly" → last weekly refresh
    private long lastDailyRefresh = 0;
    private long lastWeeklyRefresh = 0;

    // ── Reference ──
    private final EventManager eventManager;

    public TrophyShop(EventManager eventManager) {
        this.eventManager = eventManager;
        resetAllStock();
    }

    // ══════════════════════════════════════════════════════════════
    // STOCK MANAGEMENT
    // ══════════════════════════════════════════════════════════════

    /**
     * Checks if stock needs refreshing and refills expired categories.
     * Call this once per game tick or when opening the shop.
     */
    public void checkRefresh() {
        long now = System.currentTimeMillis();

        if (lastDailyRefresh == 0 || now - lastDailyRefresh >= DAILY_MS) {
            refreshDaily();
            lastDailyRefresh = now;
            Gdx.app.log(TAG, "Daily stock refreshed");
        }

        if (lastWeeklyRefresh == 0 || now - lastWeeklyRefresh >= WEEKLY_MS) {
            refreshWeekly();
            lastWeeklyRefresh = now;
            Gdx.app.log(TAG, "Weekly stock refreshed");
        }
    }

    private void refreshDaily() {
        for (ShopEntry entry : CATALOG) {
            if (entry.refreshInterval == DAILY_MS) {
                currentStock.put(entry.getStockKey(), entry.maxStock);
            }
        }
    }

    private void refreshWeekly() {
        for (ShopEntry entry : CATALOG) {
            if (entry.refreshInterval == WEEKLY_MS) {
                currentStock.put(entry.getStockKey(), entry.maxStock);
            }
        }
    }

    private void resetAllStock() {
        for (ShopEntry entry : CATALOG) {
            currentStock.put(entry.getStockKey(), entry.maxStock);
        }
        lastDailyRefresh = System.currentTimeMillis();
        lastWeeklyRefresh = System.currentTimeMillis();
    }

    /**
     * Returns remaining stock for a shop entry.
     */
    public int getStock(ShopEntry entry) {
        return currentStock.getOrDefault(entry.getStockKey(), 0);
    }

    /**
     * Returns time until next daily refresh in milliseconds.
     */
    public long getTimeUntilDailyRefresh() {
        long elapsed = System.currentTimeMillis() - lastDailyRefresh;
        return Math.max(0, DAILY_MS - elapsed);
    }

    /**
     * Returns time until next weekly refresh in milliseconds.
     */
    public long getTimeUntilWeeklyRefresh() {
        long elapsed = System.currentTimeMillis() - lastWeeklyRefresh;
        return Math.max(0, WEEKLY_MS - elapsed);
    }

    // ══════════════════════════════════════════════════════════════
    // PURCHASING
    // ══════════════════════════════════════════════════════════════

    /**
     * Attempts to purchase an item from the shop.
     * Deducts trophies, reduces stock, and adds item to inventory.
     *
     * @return true if purchase succeeded
     */
    public boolean purchase(ShopEntry entry) {
        RaidManager rm = eventManager.getRaidManager();
        int trophies = rm.getWarTrophies();

        // Check affordability
        if (trophies < entry.trophyCost) {
            Gdx.app.log(TAG, "Not enough trophies: have " + trophies
                + ", need " + entry.trophyCost);
            return false;
        }

        // Check stock
        int stock = getStock(entry);
        if (stock <= 0) {
            Gdx.app.log(TAG, "Out of stock: " + entry.displayName);
            return false;
        }

        // Deduct trophies
        rm.setWarTrophies(trophies - entry.trophyCost);

        // Reduce stock
        currentStock.put(entry.getStockKey(), stock - 1);

        // Deliver item
        deliverItem(entry);

        Gdx.app.log(TAG, "Purchased: " + entry.displayName
            + " for " + entry.trophyCost + " trophies"
            + " (stock: " + (stock - 1) + " remaining)");
        return true;
    }

    private void deliverItem(ShopEntry entry) {
        String type = entry.itemType;

        // Rune fragments go to RuneSystem, not inventory
        String runeType = RuneSystem.getRuneTypeFromFragmentType(type);
        if (runeType != null) {
            eventManager.getRuneSystem().addFragment(runeType);
            return;
        }

        // All other items created via GameDataLoader and added to inventory
        GameDataLoader gdl = eventManager.getGameDataLoader();
        Item item = gdl.createItem(type, entry.itemLevel);
        if (item != null) {
            eventManager.getInventory().addItem(item);
        } else {
            Gdx.app.log(TAG, "Failed to create item: " + type + " lv" + entry.itemLevel);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // SAVE / LOAD
    // ══════════════════════════════════════════════════════════════

    public Map<String, Integer> getCurrentStock() { return currentStock; }
    public void setCurrentStock(Map<String, Integer> stock) { this.currentStock = stock; }

    public long getLastDailyRefresh() { return lastDailyRefresh; }
    public void setLastDailyRefresh(long t) { this.lastDailyRefresh = t; }

    public long getLastWeeklyRefresh() { return lastWeeklyRefresh; }
    public void setLastWeeklyRefresh(long t) { this.lastWeeklyRefresh = t; }
}
