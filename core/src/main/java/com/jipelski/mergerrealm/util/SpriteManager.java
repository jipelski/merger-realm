package com.jipelski.mergerrealm.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Loads and caches sprite textures on demand.
 * Sprites are resolved by type + level using the naming convention:
 *   sprites/type_level.png  (e.g. sprites/archer_3.png)
 *
 * A fallback texture is returned for any missing file so the game
 * never crashes on a missing sprite — it just looks wrong.
 */
public class SpriteManager {

    private static final String TAG = "SpriteManager";
    private static final String SPRITES_DIR = "sprites/";

    private final Map<String, Texture> cache = new HashMap<>();
    // Keys confirmed to have no PNG on disk. Kept SEPARATE from `cache` (not
    // pointed at `fallback`) so SpriteManager.dispose()'s `for (Texture tex :
    // cache.values()) tex.dispose();` can't double-dispose the shared
    // fallback texture. Bundled assets don't change at runtime, so caching a
    // miss for the whole session is safe — without this, every frame a
    // missing sprite is on screen re-runs Gdx.files.internal(...).exists()
    // (file I/O) and logs, for as long as placeholder art is in use.
    private final Set<String> missing = new HashSet<>();
    private Texture fallback;

    // ── Procedural placeholder generation (2026-07-14) ──
    //
    // No real art exists yet for any equipment/consumable item, the 4
    // enchanted set pieces, or the Food/Wood/Iron/Gold/Trophy/Boss-Token
    // currency icons — all would otherwise fall to the single flat magenta
    // `fallback` below, indistinguishable from each other. Same idea
    // UITextureManager already uses for UI chrome (a Pixmap drawn at
    // runtime instead of a hand-authored file) — extended here, but scoped
    // ONLY to the specific keys below via isPlaceholderEligible(), so the
    // existing flat-magenta fallback for everything else (units/facilities/
    // monsters/etc. still pending real art) is completely unchanged.
    //
    // Placeholder, not final art — if a real PNG is later dropped into
    // sprites/, the "real file found" branch above this one still wins
    // automatically (these keys are never added to `missing`), zero code
    // change needed, same swap-in convention SoundManager's placeholder
    // tones already established for audio.

    // Longer/more-specific prefixes MUST come before shorter ones they'd
    // otherwise also match as a false-positive startsWith — e.g.
    // "amulet_of_ascension_1" also starts with "amulet_". LinkedHashMap
    // preserves this insertion order for generatePlaceholderSprite()'s scan.
    private static final Map<String, Color> ITEM_BASE_COLORS = new LinkedHashMap<>();
    static {
        ITEM_BASE_COLORS.put("amulet_of_ascension", new Color(0.9f, 0.75f, 0.2f, 1f));  // bright gold
        // Match InventoryMenu's RUNE_COLOR_* constants exactly, so a
        // fragment's icon reads as the same color its rune later tints a
        // unit with.
        ITEM_BASE_COLORS.put("rune_fragment_might", new Color(0.9f, 0.3f, 0.3f, 1f));
        ITEM_BASE_COLORS.put("rune_fragment_vitality", new Color(0.3f, 0.9f, 0.3f, 1f));
        ITEM_BASE_COLORS.put("rune_fragment_fortune", new Color(0.9f, 0.85f, 0.2f, 1f));
        ITEM_BASE_COLORS.put("rune_fragment_swiftness", new Color(0.3f, 0.7f, 0.9f, 1f));
        ITEM_BASE_COLORS.put("phoenix_feather", new Color(0.85f, 0.45f, 0.1f, 1f));  // orange
        ITEM_BASE_COLORS.put("ancient_map", new Color(0.6f, 0.5f, 0.35f, 1f));       // tan
        ITEM_BASE_COLORS.put("sword", new Color(0.75f, 0.15f, 0.15f, 1f));           // red
        ITEM_BASE_COLORS.put("shield", new Color(0.15f, 0.35f, 0.75f, 1f));          // blue
        ITEM_BASE_COLORS.put("amulet", new Color(0.55f, 0.15f, 0.75f, 1f));          // purple
        ITEM_BASE_COLORS.put("potion", new Color(0.1f, 0.6f, 0.55f, 1f));            // teal
    }
    private static final Color ENCHANTED_COLOR = new Color(0.55f, 0.25f, 0.7f, 1f); // violet

    private static final Map<String, Color> CURRENCY_COLORS = new HashMap<>();
    static {
        CURRENCY_COLORS.put("food", new Color(0.25f, 0.75f, 0.3f, 1f));       // green
        CURRENCY_COLORS.put("wood", new Color(0.55f, 0.35f, 0.15f, 1f));      // brown
        CURRENCY_COLORS.put("iron", new Color(0.55f, 0.58f, 0.62f, 1f));      // steel grey
        CURRENCY_COLORS.put("gold", new Color(0.95f, 0.8f, 0.2f, 1f));        // golden yellow
        CURRENCY_COLORS.put("trophy", new Color(0.8f, 0.55f, 0.2f, 1f));      // amber/bronze
        CURRENCY_COLORS.put("boss_token", new Color(0.5f, 0.1f, 0.15f, 1f));  // dark crimson
    }

    private static final int ITEM_ICON_PX = 48;
    private static final int CURRENCY_ICON_PX = 32;
    private static final int MAX_ITEM_TIER = 5; // items.json's tiers are always 1-5

    private boolean isPlaceholderEligible(String key) {
        if (CURRENCY_COLORS.containsKey(key)) return true;
        if (key.startsWith(EnchantedSetManager.ENCHANTED_PREFIX)) return true;
        for (String prefix : ITEM_BASE_COLORS.keySet()) {
            if (key.startsWith(prefix + "_")) return true;
        }
        return false;
    }

    private Texture generatePlaceholderSprite(String key) {
        Color currencyColor = CURRENCY_COLORS.get(key);
        if (currencyColor != null) {
            return generateCurrencyIcon(key, currencyColor);
        }
        if (key.startsWith(EnchantedSetManager.ENCHANTED_PREFIX)) {
            // Enchanted gear is already end-tier content — always render at
            // max brightness, no level to parse from the key (these are
            // always level 1; enchanted_sets.json has no per-tier variants).
            return generateItemIcon(key, ENCHANTED_COLOR, MAX_ITEM_TIER);
        }
        for (Map.Entry<String, Color> entry : ITEM_BASE_COLORS.entrySet()) {
            String prefix = entry.getKey();
            if (key.startsWith(prefix + "_")) {
                return generateItemIcon(key, entry.getValue(), parseTrailingLevel(key, prefix));
            }
        }
        // Unreachable given isPlaceholderEligible() gates every caller, but
        // never return null from a texture getter.
        return generateItemIcon(key, Color.LIGHT_GRAY, 1);
    }

    /** Parses the trailing "_N" level suffix after a known prefix, or 1 if absent/unparseable. */
    private int parseTrailingLevel(String key, String prefix) {
        try {
            return Integer.parseInt(key.substring(prefix.length() + 1));
        } catch (Exception e) {
            return 1;
        }
    }

    /**
     * Bordered-rect placeholder for an equipment/consumable item — same
     * visual family as UITextureManager's UI-chrome placeholders.
     * Brightness scales up with tier (duller at low tiers, shinier at high)
     * so higher-tier gear looks more powerful even before real art lands.
     */
    private Texture generateItemIcon(String key, Color baseColor, int tier) {
        float t = Math.max(0f, Math.min(1f, (tier - 1) / (float) (MAX_ITEM_TIER - 1)));
        float brightness = 0.55f + 0.45f * t; // 0.55 at tier1 .. 1.0 at tier5
        Color fill = new Color(
            Math.min(1f, baseColor.r * brightness + (1f - brightness) * 0.15f),
            Math.min(1f, baseColor.g * brightness + (1f - brightness) * 0.15f),
            Math.min(1f, baseColor.b * brightness + (1f - brightness) * 0.15f),
            1f);
        Color border = new Color(
            Math.min(1f, fill.r + 0.25f), Math.min(1f, fill.g + 0.25f), Math.min(1f, fill.b + 0.25f), 1f);

        Pixmap px = new Pixmap(ITEM_ICON_PX, ITEM_ICON_PX, Pixmap.Format.RGBA8888);
        px.setColor(fill);
        px.fill();
        px.setColor(border);
        px.drawRectangle(0, 0, ITEM_ICON_PX, ITEM_ICON_PX);
        px.drawRectangle(1, 1, ITEM_ICON_PX - 2, ITEM_ICON_PX - 2);
        Texture tex = new Texture(px);
        px.dispose();
        Gdx.app.log(TAG, "Generated placeholder item icon: " + key);
        return tex;
    }

    /**
     * Filled-circle "coin" placeholder for a currency/resource icon — the
     * round shape alone visually separates currency from equipment
     * (bordered rects) at a glance, on top of the color difference.
     */
    private Texture generateCurrencyIcon(String key, Color color) {
        Color border = new Color(
            Math.max(0f, color.r - 0.2f), Math.max(0f, color.g - 0.2f), Math.max(0f, color.b - 0.2f), 1f);

        Pixmap px = new Pixmap(CURRENCY_ICON_PX, CURRENCY_ICON_PX, Pixmap.Format.RGBA8888);
        int c = CURRENCY_ICON_PX / 2;
        int r = CURRENCY_ICON_PX / 2 - 1;
        px.setColor(border);
        px.fillCircle(c, c, r);
        px.setColor(color);
        px.fillCircle(c, c, r - 2);
        Texture tex = new Texture(px);
        px.dispose();
        Gdx.app.log(TAG, "Generated placeholder currency icon: " + key);
        return tex;
    }

    public void loadFallback() {
        FileHandle fh = Gdx.files.internal(SPRITES_DIR + "default_tile.png");
        if (fh.exists()) {
            fallback = new Texture(fh);
        } else {
            // 1x1 white pixel as last resort
            com.badlogic.gdx.graphics.Pixmap px = new com.badlogic.gdx.graphics.Pixmap(1, 1,
                com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
            px.setColor(1, 0, 1, 1); // magenta = missing texture
            px.fill();
            fallback = new Texture(px);
            px.dispose();
        }
        cache.put("default_tile", fallback);
    }

    /**
     * Returns the texture for the given type and level.
     * e.g. getTexture("archer", 3) → sprites/archer_3.png
     */
    public Texture getTexture(String type, int level) {
        String key = type + "_" + level;
        return getTextureByKey(key);
    }

    /**
     * Returns the texture for a string key (e.g. "default_tile").
     */
    public Texture getTextureByKey(String key) {
        Texture tex = cache.get(key);
        if (tex != null) return tex;

        // Already confirmed missing this session — skip the file-exists
        // check and log spam that would otherwise repeat every frame this
        // key is requested.
        if (missing.contains(key)) return fallback;

        // Try to load
        FileHandle fh = Gdx.files.internal(SPRITES_DIR + key + ".png");
        if (fh.exists()) {
            tex = new Texture(fh);
            tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            cache.put(key, tex);
            return tex;
        }

        // Distinguishable procedural placeholder for items/enchanted pieces/
        // currency icons specifically — see the block comment above `missing`.
        // Never added to `missing`, so a real PNG dropped in later is picked
        // up automatically by the "real file found" branch above, with no
        // code change.
        if (isPlaceholderEligible(key)) {
            tex = generatePlaceholderSprite(key);
            cache.put(key, tex);
            return tex;
        }

        missing.add(key);
        Gdx.app.log(TAG, "Sprite not found: " + key + ".png — using fallback");
        return fallback;
    }

    /**
     * Resolves a sprite for a GameObject based on its type and level.
     * Handles the naming convention: type_level.png
     */
    public Texture getTextureForObject(String type, int level) {
        return getTexture(type, level);
    }

    public Texture getDefaultTile() {
        return fallback;
    }

    public void dispose() {
        for (Texture tex : cache.values()) {
            tex.dispose();
        }
        cache.clear();
        missing.clear();
        fallback = null;
    }
}
