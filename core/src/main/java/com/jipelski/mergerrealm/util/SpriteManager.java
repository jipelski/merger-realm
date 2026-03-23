package com.jipelski.mergerrealm.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;

import java.util.HashMap;
import java.util.Map;

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
    private Texture fallback;

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

        // Try to load
        FileHandle fh = Gdx.files.internal(SPRITES_DIR + key + ".png");
        if (fh.exists()) {
            tex = new Texture(fh);
            tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            cache.put(key, tex);
            return tex;
        }

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
        fallback = null;
    }
}
