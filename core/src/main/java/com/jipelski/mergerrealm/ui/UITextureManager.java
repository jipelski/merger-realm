package com.jipelski.mergerrealm.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages all UI textures and NinePatches.
 * Generates colored placeholder textures if actual art files don't exist,
 * so the game runs visually with or without final art.
 *
 * Replace placeholders by dropping real PNGs into android/assets/ui/
 * with matching filenames. NinePatch files must end in .9.png and have
 * the 1px black border defining stretch regions.
 */
public class UITextureManager {

    private static final String TAG = "UITextureManager";
    private static final String UI_DIR = "ui/";

    private final Map<String, Texture> textures = new HashMap<>();
    private final Map<String, NinePatch> ninePatches = new HashMap<>();

    // ── Direct access for frequently used textures ──
    public Texture cellEmpty;
    public Texture cellSelected;
    public Texture cellDrag;
    public Texture cellOccupied;
    public Texture barXpBg;
    public Texture barXpFill;
    public Texture barNemesisBg;
    public Texture barNemesisFill;
    public Texture popupOverlay;

    // ── NinePatches ──
    public NinePatch panelDark;
    public NinePatch panelMedium;
    public NinePatch panelLight;
    public NinePatch btnNormal;
    public NinePatch btnActive;
    public NinePatch btnDisabled;
    public NinePatch cardBg;
    public NinePatch cardLocked;
    public NinePatch popupPanel;

    public void load() {
        Gdx.app.log(TAG, "Loading UI textures...");

        // ── Grid cells ──
        cellEmpty = loadOrGenerate("cell_empty", 16, 16,
            new Color(0.2f, 0.2f, 0.28f, 1f), new Color(0.25f, 0.25f, 0.32f, 1f));
        cellSelected = loadOrGenerate("cell_selected", 16, 16,
            new Color(0.2f, 0.2f, 0.28f, 1f), new Color(0.2f, 0.85f, 0.2f, 1f));
        cellDrag = loadOrGenerate("cell_drag", 16, 16,
            new Color(0.35f, 0.35f, 0.15f, 1f), new Color(0.45f, 0.45f, 0.2f, 1f));
        cellOccupied = loadOrGenerate("cell_occupied", 16, 16,
            new Color(0.25f, 0.25f, 0.35f, 1f), new Color(0.3f, 0.3f, 0.4f, 1f));

        // ── Progress bars ──
        barXpBg = generateSolid("bar_xp_bg", 4, 4, new Color(0.1f, 0.1f, 0.15f, 1f));
        barXpFill = generateSolid("bar_xp_fill", 4, 4, new Color(0.3f, 0.7f, 0.3f, 1f));
        barNemesisBg = generateSolid("bar_nemesis_bg", 4, 4, new Color(0.1f, 0.1f, 0.15f, 1f));
        barNemesisFill = generateSolid("bar_nemesis_fill", 4, 4, new Color(0.8f, 0.25f, 0.25f, 1f));

        // ── Popup overlay (semi-transparent black) ──
        popupOverlay = generateSolid("popup_overlay", 4, 4, new Color(0f, 0f, 0f, 0.7f));

        // ── NinePatches ──
        panelDark = loadOrGenerateNinePatch("panel_dark",
            new Color(0.14f, 0.14f, 0.2f, 1f), new Color(0.22f, 0.22f, 0.3f, 1f));
        panelMedium = loadOrGenerateNinePatch("panel_medium",
            new Color(0.18f, 0.18f, 0.26f, 1f), new Color(0.26f, 0.26f, 0.36f, 1f));
        panelLight = loadOrGenerateNinePatch("panel_light",
            new Color(0.22f, 0.22f, 0.3f, 1f), new Color(0.3f, 0.3f, 0.42f, 1f));

        btnNormal = loadOrGenerateNinePatch("btn_normal",
            new Color(0.2f, 0.5f, 0.2f, 1f), new Color(0.25f, 0.6f, 0.25f, 1f));
        btnActive = loadOrGenerateNinePatch("btn_active",
            new Color(0.25f, 0.6f, 0.25f, 1f), new Color(0.3f, 0.7f, 0.3f, 1f));
        btnDisabled = loadOrGenerateNinePatch("btn_disabled",
            new Color(0.25f, 0.25f, 0.3f, 1f), new Color(0.3f, 0.3f, 0.35f, 1f));

        cardBg = loadOrGenerateNinePatch("card_bg",
            new Color(0.18f, 0.18f, 0.26f, 1f), new Color(0.26f, 0.26f, 0.36f, 1f));
        cardLocked = loadOrGenerateNinePatch("card_locked",
            new Color(0.12f, 0.12f, 0.16f, 1f), new Color(0.18f, 0.18f, 0.22f, 1f));

        popupPanel = loadOrGenerateNinePatch("popup_panel",
            new Color(0.16f, 0.16f, 0.24f, 1f), new Color(0.35f, 0.35f, 0.5f, 1f));

        Gdx.app.log(TAG, "UI textures loaded (" + textures.size() + " textures, "
            + ninePatches.size() + " ninepatches)");
    }

    // ── Loading helpers ──

    /**
     * Tries to load a texture from ui/ directory.
     * If not found, generates a placeholder with the given colors.
     */
    private Texture loadOrGenerate(String name, int width, int height,
                                   Color fillColor, Color borderColor) {
        FileHandle file = Gdx.files.internal(UI_DIR + name + ".png");
        Texture tex;
        if (file.exists()) {
            tex = new Texture(file);
            Gdx.app.log(TAG, "Loaded: " + name);
        } else {
            tex = generateBorderedRect(name, width, height, fillColor, borderColor);
            Gdx.app.log(TAG, "Generated placeholder: " + name);
        }
        textures.put(name, tex);
        return tex;
    }

    /**
     * Tries to load a NinePatch from ui/ directory.
     * File should be named name.9.png with proper 1px border.
     * If not found, generates a placeholder NinePatch.
     */
    private NinePatch loadOrGenerateNinePatch(String name, Color fillColor, Color borderColor) {
        FileHandle file = Gdx.files.internal(UI_DIR + name + ".9.png");
        NinePatch patch;
        if (file.exists()) {
            Texture tex = new Texture(file);
            textures.put(name, tex);
            // Standard 4px borders — adjust if your art uses different sizes
            patch = new NinePatch(tex, 4, 4, 4, 4);
            Gdx.app.log(TAG, "Loaded NinePatch: " + name);
        } else {
            // Generate a simple bordered rectangle as placeholder
            Texture tex = generateBorderedRect(name, 12, 12, fillColor, borderColor);
            textures.put(name, tex);
            patch = new NinePatch(tex, 3, 3, 3, 3);
            Gdx.app.log(TAG, "Generated placeholder NinePatch: " + name);
        }
        ninePatches.put(name, patch);
        return patch;
    }

    /**
     * Generates a solid color texture.
     */
    private Texture generateSolid(String name, int width, int height, Color color) {
        Pixmap px = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        px.setColor(color);
        px.fill();
        Texture tex = new Texture(px);
        px.dispose();
        textures.put(name, tex);
        return tex;
    }

    /**
     * Generates a filled rectangle with a 1px border.
     */
    private Texture generateBorderedRect(String name, int width, int height,
                                         Color fillColor, Color borderColor) {
        Pixmap px = new Pixmap(width, height, Pixmap.Format.RGBA8888);

        // Fill
        px.setColor(fillColor);
        px.fill();

        // Border (1px)
        px.setColor(borderColor);
        px.drawRectangle(0, 0, width, height);

        Texture tex = new Texture(px);
        px.dispose();
        return tex;
    }

    // ── Drawing helpers ──

    /**
     * Draws a NinePatch panel at the given position and size.
     */
    public void drawPanel(SpriteBatch batch, NinePatch panel, float x, float y,
                          float width, float height) {
        panel.draw(batch, x, y, width, height);
    }

    /**
     * Draws a progress bar (background + fill).
     */
    public void drawProgressBar(SpriteBatch batch, Texture bg, Texture fill,
                                float x, float y, float width, float height,
                                float ratio) {
        batch.draw(bg, x, y, width, height);
        if (ratio > 0) {
            batch.draw(fill, x, y, width * Math.min(ratio, 1f), height);
        }
    }

    public void dispose() {
        for (Texture tex : textures.values()) {
            tex.dispose();
        }
        textures.clear();
        ninePatches.clear();
        Gdx.app.log(TAG, "UI textures disposed");
    }
}
