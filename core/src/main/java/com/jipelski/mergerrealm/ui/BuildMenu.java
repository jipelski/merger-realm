package com.jipelski.mergerrealm.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;

import com.jipelski.mergerrealm.data.FacilityData;
import com.jipelski.mergerrealm.data.GenData;
import com.jipelski.mergerrealm.data.StorageData;
import com.jipelski.mergerrealm.util.EventManager;
import com.jipelski.mergerrealm.util.GameDataLoader;
import com.jipelski.mergerrealm.util.PrinceLevelConfig;
import com.jipelski.mergerrealm.util.ResourceManager;
import com.jipelski.mergerrealm.util.SpriteManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Horizontal scrollable build menu that slides up from the bottom.
 * Shows building cards in a strip. Swipe left/right to scroll.
 *
 * Layout of each card:
 * +------------------+
 * |    [ICON 48x48]  |
 * |    Archery Range  |
 * |   25 wood tokens  |
 * |    [ BUILD ]      |
 * +------------------+
 */
public class BuildMenu {

    private static final String TAG = "BuildMenu";

    private boolean visible = false;

    // Layout
    private final float worldWidth;
    private final float worldHeight;
    public static final float MENU_HEIGHT = 160f;
    private static final float CARD_WIDTH = 110f;
    private static final float CARD_HEIGHT = 140f;
    private static final float CARD_GAP = 8f;
    private static final float CARD_PADDING = 6f;
    private static final float ICON_SIZE = 44f;
    private static final float BTN_WIDTH = 60f;
    private static final float BTN_HEIGHT = 24f;
    private static final float MENU_PADDING = 8f;

    // References
    private final EventManager eventManager;
    private final SpriteManager spriteManager;
    private final Viewport viewport;
    private final GlyphLayout glyphLayout;

    // Scroll state
    private float scrollX = 0f;
    private float maxScrollX = 0f;
    private boolean scrolling = false;
    private float scrollTouchStartX = 0f;
    private float scrollStartOffset = 0f;

    // Cached items
    private final List<BuildableItem> items = new ArrayList<>();
    private int princeLevelCache = -1;

    // Touch
    private final Vector2 touchPos = new Vector2();

    // All buildable types in display order
    private static final String[] ALL_BUILDABLES = {
        "archeryrange", "sawmill", "farmhouse", "quarry",
        "barracks", "ironmine", "griffinnest", "monastery"
    };

    public BuildMenu(EventManager eventManager, SpriteManager spriteManager,
                     Viewport viewport, float worldWidth, float worldHeight) {
        this.eventManager = eventManager;
        this.spriteManager = spriteManager;
        this.viewport = viewport;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.glyphLayout = new GlyphLayout();
    }

    public boolean isVisible() { return visible; }

    public void toggle() {
        visible = !visible;
        if (visible) {
            refreshItems();
            scrollX = 0f;
        }
    }

    public void show() {
        visible = true;
        refreshItems();
        scrollX = 0f;
    }

    public void hide() {
        visible = false;
        scrolling = false;
    }

    public float getMenuHeight() {
        return MENU_HEIGHT;
    }

    private void refreshItems() {
        int princeLvl = eventManager.getBattleFieldManager().getLevel();
        if (princeLvl == princeLevelCache && !items.isEmpty()) return;

        princeLevelCache = princeLvl;
        items.clear();

        GameDataLoader gdl = eventManager.getGameDataLoader();

        for (String type : ALL_BUILDABLES) {
            GenData data = gdl.getGameData(type, 1);
            if (data == null) continue;

            BuildableItem item = new BuildableItem();
            item.type = type;
            item.unlocked = PrinceLevelConfig.isFacilityUnlocked(type, princeLvl);

            if (data instanceof FacilityData) {
                FacilityData fd = (FacilityData) data;
                item.costWood = fd.getBuildCost1();
                item.costWheat = fd.getBuildCost2();
                item.costStone = fd.getBuildCost3();
                item.costFire = fd.getBuildCost4();
            } else if (data instanceof StorageData) {
                StorageData sd = (StorageData) data;
                item.costWood = sd.getBuild_cost1();
                item.costWheat = sd.getBuild_cost2();
                item.costStone = sd.getBuild_cost3();
                item.costFire = 0;
            }

            items.add(item);
        }

        // Calculate max scroll
        float totalWidth = items.size() * (CARD_WIDTH + CARD_GAP) - CARD_GAP + MENU_PADDING * 2;
        maxScrollX = Math.max(0, totalWidth - worldWidth);
    }

    /**
     * Draws card backgrounds and buttons. Call OUTSIDE batch.begin/end.
     */
    public void drawBackground(ShapeRenderer sr) {
        if (!visible) return;

        float menuY = 0;

        sr.begin(ShapeRenderer.ShapeType.Filled);

        // Menu background
        sr.setColor(0.1f, 0.1f, 0.16f, 1f);
        sr.rect(0, menuY, worldWidth, MENU_HEIGHT);

        // Top border line
        sr.setColor(0.3f, 0.3f, 0.4f, 1f);
        sr.rect(0, menuY + MENU_HEIGHT - 2f, worldWidth, 2f);

        // Draw each card
        ResourceManager rm = eventManager.getResourceManager();
        float startX = MENU_PADDING - scrollX;
        float cardY = menuY + MENU_PADDING;

        for (BuildableItem item : items) {
            float cardX = startX;

            // Skip if fully off screen
            if (cardX + CARD_WIDTH < 0 || cardX > worldWidth) {
                startX += CARD_WIDTH + CARD_GAP;
                continue;
            }

            if (item.unlocked) {
                // Card background
                sr.setColor(0.18f, 0.18f, 0.26f, 1f);
                sr.rect(cardX, cardY, CARD_WIDTH, CARD_HEIGHT);

                // Build button
                float btnX = cardX + (CARD_WIDTH - BTN_WIDTH) / 2f;
                float btnY = cardY + CARD_PADDING;

                boolean canAfford = canAfford(item, rm);
                boolean hasSpace = eventManager.getGridInstance().hasEmptyCell();

                if (canAfford && hasSpace) {
                    sr.setColor(0.2f, 0.65f, 0.2f, 1f);
                } else {
                    sr.setColor(0.35f, 0.35f, 0.35f, 1f);
                }
                sr.rect(btnX, btnY, BTN_WIDTH, BTN_HEIGHT);
            } else {
                // Locked card
                sr.setColor(0.12f, 0.12f, 0.16f, 1f);
                sr.rect(cardX, cardY, CARD_WIDTH, CARD_HEIGHT);
            }

            startX += CARD_WIDTH + CARD_GAP;
        }

        sr.end();
    }

    /**
     * Draws card text and icons. Call INSIDE batch.begin/end.
     */
    public void drawContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        if (!visible) return;

        float menuY = 0;
        float startX = MENU_PADDING - scrollX;
        float cardY = menuY + MENU_PADDING;

        for (BuildableItem item : items) {
            float cardX = startX;

            // Skip if off screen
            if (cardX + CARD_WIDTH < 0 || cardX > worldWidth) {
                startX += CARD_WIDTH + CARD_GAP;
                continue;
            }

            float centerX = cardX + CARD_WIDTH / 2f;

            if (item.unlocked) {
                // Icon
                Texture tex = spriteManager.getTexture(item.type, 1);
                float iconX = centerX - ICON_SIZE / 2f;
                float iconY = cardY + CARD_HEIGHT - CARD_PADDING - ICON_SIZE;
                batch.draw(tex, iconX, iconY, ICON_SIZE, ICON_SIZE);

                // Name
                font.setColor(Color.WHITE);
                String name = capitalize(item.type);
                glyphLayout.setText(fontSmall, name);
                fontSmall.setColor(Color.WHITE);
                fontSmall.draw(batch, name, centerX - glyphLayout.width / 2f,
                    iconY - 4f);

                // Cost
                String cost = shortCostString(item);
                fontSmall.setColor(0.85f, 0.8f, 0.5f, 1f);
                glyphLayout.setText(fontSmall, cost);
                fontSmall.draw(batch, cost, centerX - glyphLayout.width / 2f,
                    iconY - 20f);

                // Build button text
                float btnX = cardX + (CARD_WIDTH - BTN_WIDTH) / 2f;
                float btnY = cardY + CARD_PADDING;
                fontSmall.setColor(Color.WHITE);
                glyphLayout.setText(fontSmall, "Build");
                fontSmall.draw(batch, "Build",
                    btnX + (BTN_WIDTH - glyphLayout.width) / 2f,
                    btnY + BTN_HEIGHT - 6f);
            } else {
                // Locked icon (use fallback/default)
                Texture tex = spriteManager.getDefaultTile();
                float iconX = centerX - ICON_SIZE / 2f;
                float iconY = cardY + CARD_HEIGHT - CARD_PADDING - ICON_SIZE;

                batch.setColor(0.3f, 0.3f, 0.3f, 0.5f);
                batch.draw(tex, iconX, iconY, ICON_SIZE, ICON_SIZE);
                batch.setColor(1f, 1f, 1f, 1f);

                // Name
                fontSmall.setColor(0.4f, 0.4f, 0.5f, 1f);
                String name = capitalize(item.type);
                glyphLayout.setText(fontSmall, name);
                fontSmall.draw(batch, name, centerX - glyphLayout.width / 2f,
                    iconY - 4f);

                // Locked label
                fontSmall.setColor(0.5f, 0.3f, 0.3f, 1f);
                glyphLayout.setText(fontSmall, "LOCKED");
                fontSmall.draw(batch, "LOCKED", centerX - glyphLayout.width / 2f,
                    iconY - 20f);
            }

            startX += CARD_WIDTH + CARD_GAP;
        }

        // Reset
        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    // ── Touch handling ──

    /**
     * Handles touch down on the menu. Returns true if consumed.
     */
    public boolean touchDown(float worldX, float worldY) {
        if (!visible) return false;
        if (worldY > MENU_HEIGHT) return false;

        scrolling = true;
        scrollTouchStartX = worldX;
        scrollStartOffset = scrollX;
        return true;
    }

    /**
     * Handles drag for horizontal scrolling. Returns true if consumed.
     */
    public boolean touchDragged(float worldX, float worldY) {
        if (!scrolling) return false;

        float dx = scrollTouchStartX - worldX;
        scrollX = MathUtils.clamp(scrollStartOffset + dx, 0, maxScrollX);
        return true;
    }

    /**
     * Handles touch up — checks if a build button was tapped.
     * Returns true if consumed.
     */
    public boolean touchUp(float worldX, float worldY) {
        if (!scrolling) return false;
        scrolling = false;

        // Only register as a tap if finger didn't move much (not a scroll)
        float dragDistance = Math.abs(worldX - scrollTouchStartX);
        if (dragDistance > 10f) return true; // was a scroll, consume but don't act

        if (worldY > MENU_HEIGHT) return false;

        // Check if a build button was hit
        float startX = MENU_PADDING - scrollX;
        float cardY = MENU_PADDING;
        ResourceManager rm = eventManager.getResourceManager();

        for (BuildableItem item : items) {
            float cardX = startX;

            if (item.unlocked) {
                float btnX = cardX + (CARD_WIDTH - BTN_WIDTH) / 2f;
                float btnY = cardY + CARD_PADDING;

                if (worldX >= btnX && worldX <= btnX + BTN_WIDTH
                    && worldY >= btnY && worldY <= btnY + BTN_HEIGHT) {
                    tryBuild(item, rm);
                    return true;
                }
            }

            startX += CARD_WIDTH + CARD_GAP;
        }

        return true;
    }

    /**
     * Check if a touch Y coordinate is within the menu area.
     */
    public boolean isInMenuArea(float worldY) {
        return visible && worldY <= MENU_HEIGHT;
    }

    // ── Building logic ──

    private void tryBuild(BuildableItem item, ResourceManager rm) {
        if (!canAfford(item, rm)) {
            Gdx.app.log(TAG, "Cannot afford " + item.type);
            return;
        }
        if (!eventManager.getGridInstance().hasEmptyCell()) {
            Gdx.app.log(TAG, "Grid full — cannot build " + item.type);
            return;
        }

        if (!rm.decreaseTokens(item.costWood, item.costWheat, item.costStone, item.costFire)) {
            Gdx.app.log(TAG, "Token deduction failed for " + item.type);
            return;
        }

        int[] empty = eventManager.getGridInstance().getClosestEmptyCell(0, 0);
        if (empty != null) {
            eventManager.spawnObject(item.type, 1, empty[0], empty[1]);
            Gdx.app.log(TAG, "Built " + item.type + " at [" + empty[0] + "," + empty[1] + "]");
            // Refresh to update affordability
            princeLevelCache = -1;
            refreshItems();
        }
    }

    private boolean canAfford(BuildableItem item, ResourceManager rm) {
        return rm.getAmount("wood") >= item.costWood
            && rm.getAmount("wheat") >= item.costWheat
            && rm.getAmount("stone") >= item.costStone
            && rm.getAmount("fire") >= item.costFire;
    }

    private String shortCostString(BuildableItem item) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        if (item.costWood > 0) {
            sb.append(item.costWood).append("W");
            first = false;
        }
        if (item.costWheat > 0) {
            if (!first) sb.append(" ");
            sb.append(item.costWheat).append("Wh");
            first = false;
        }
        if (item.costStone > 0) {
            if (!first) sb.append(" ");
            sb.append(item.costStone).append("S");
            first = false;
        }
        if (item.costFire > 0) {
            if (!first) sb.append(" ");
            sb.append(item.costFire).append("F");
        }
        return sb.length() > 0 ? sb.toString() : "Free";
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private static class BuildableItem {
        String type;
        boolean unlocked;
        int costWood;
        int costWheat;
        int costStone;
        int costFire;
    }
}
