package com.jipelski.mergerrealm.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
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
 * Build menu overlay. Shows unlocked facilities and storage buildings
 * that the player can build by spending tokens.
 *
 * Build costs use the token system:
 *   build_cost1 = wood tokens
 *   build_cost2 = wheat tokens
 *   build_cost3 = stone tokens
 *   build_cost4 = fire tokens
 */
public class BuildMenu {

    private static final String TAG = "BuildMenu";

    private boolean visible = false;

    // Layout constants
    private final float worldWidth;
    private final float worldHeight;
    private static final float MENU_MARGIN = 24f;
    private static final float ROW_HEIGHT = 70f;
    private static final float ICON_SIZE = 50f;
    private static final float BUTTON_WIDTH = 60f;
    private static final float BUTTON_HEIGHT = 30f;
    private static final float PADDING = 10f;

    private final EventManager eventManager;
    private final SpriteManager spriteManager;
    private final Viewport viewport;
    private final GlyphLayout glyphLayout;

    // Cached list of buildable items for the current prince level
    private final List<BuildableItem> items = new ArrayList<>();
    private int princeLevelCache = -1;

    // Temp vector
    private final Vector2 touchPos = new Vector2();

    // All buildable types in display order
    private static final String[] ALL_FACILITIES = {
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

    public boolean isVisible() {
        return visible;
    }

    public void toggle() {
        visible = !visible;
        if (visible) {
            refreshItems();
        }
    }

    public void show() {
        visible = true;
        refreshItems();
    }

    public void hide() {
        visible = false;
    }

    /**
     * Rebuilds the list of buildable items based on current prince level.
     */
    private void refreshItems() {
        int princeLvl = eventManager.getBattleFieldManager().getLevel();
        if (princeLvl == princeLevelCache && !items.isEmpty()) return;

        princeLevelCache = princeLvl;
        items.clear();

        GameDataLoader gdl = eventManager.getGameDataLoader();

        for (String type : ALL_FACILITIES) {
            boolean unlocked = PrinceLevelConfig.isFacilityUnlocked(type, princeLvl);
            GenData data = gdl.getGameData(type, 1);
            if (data == null) continue;

            BuildableItem item = new BuildableItem();
            item.type = type;
            item.unlocked = unlocked;

            if (data instanceof FacilityData) {
                FacilityData fd = (FacilityData) data;
                item.costWood = fd.getBuildCost1();
                item.costWheat = fd.getBuildCost2();
                item.costStone = fd.getBuildCost3();
                item.costFire = fd.getBuildCost4();
                item.description = fd.getDescription();
            } else if (data instanceof StorageData) {
                StorageData sd = (StorageData) data;
                item.costWood = sd.getBuild_cost1();
                item.costWheat = sd.getBuild_cost2();
                item.costStone = sd.getBuild_cost3();
                item.costFire = 0;
                item.description = sd.getDescription();
            }

            items.add(item);
        }
    }

    /**
     * Draws the menu background. Call outside batch.begin/end.
     */
    public void drawBackground(ShapeRenderer shapeRenderer) {
        if (!visible) return;

        float menuX = MENU_MARGIN;
        float menuWidth = worldWidth - MENU_MARGIN * 2;
        float menuHeight = PADDING * 2 + ROW_HEIGHT * items.size() + 40f; // +40 for header
        float menuY = (worldHeight - menuHeight) / 2f;

        // Dim background
        Gdx.gl.glEnable(Gdx.gl.GL_BLEND);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0f, 0f, 0f, 0.7f);
        shapeRenderer.rect(0, 0, worldWidth, worldHeight);

        // Menu panel
        shapeRenderer.setColor(0.14f, 0.14f, 0.2f, 1f);
        shapeRenderer.rect(menuX, menuY, menuWidth, menuHeight);

        // Draw build buttons and row backgrounds
        float rowY = menuY + menuHeight - 40f - PADDING;
        ResourceManager rm = eventManager.getResourceManager();

        for (BuildableItem item : items) {
            float rowBottom = rowY - ROW_HEIGHT + PADDING;

            if (item.unlocked) {
                // Row background
                shapeRenderer.setColor(0.18f, 0.18f, 0.26f, 1f);
                shapeRenderer.rect(menuX + PADDING, rowBottom, menuWidth - PADDING * 2, ROW_HEIGHT - 4f);

                // Build button
                boolean canAfford = canAfford(item, rm);
                boolean hasSpace = eventManager.getGridInstance().hasEmptyCell();

                float btnX = menuX + menuWidth - PADDING - BUTTON_WIDTH - 4f;
                float btnY = rowBottom + (ROW_HEIGHT - BUTTON_HEIGHT) / 2f - 2f;

                if (canAfford && hasSpace) {
                    shapeRenderer.setColor(0.2f, 0.7f, 0.2f, 1f); // green
                } else {
                    shapeRenderer.setColor(0.4f, 0.4f, 0.4f, 1f); // grey
                }
                shapeRenderer.rect(btnX, btnY, BUTTON_WIDTH, BUTTON_HEIGHT);
            } else {
                // Locked row — darker
                shapeRenderer.setColor(0.12f, 0.12f, 0.16f, 1f);
                shapeRenderer.rect(menuX + PADDING, rowBottom, menuWidth - PADDING * 2, ROW_HEIGHT - 4f);
            }

            rowY -= ROW_HEIGHT;
        }

        shapeRenderer.end();
        Gdx.gl.glDisable(Gdx.gl.GL_BLEND);
    }

    /**
     * Draws menu text and icons. Call inside batch.begin/end.
     */
    public void drawContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        if (!visible) return;

        float menuX = MENU_MARGIN;
        float menuWidth = worldWidth - MENU_MARGIN * 2;
        float menuHeight = PADDING * 2 + ROW_HEIGHT * items.size() + 40f;
        float menuY = (worldHeight - menuHeight) / 2f;

        // Header
        font.setColor(Color.WHITE);
        font.draw(batch, "Build", menuX + PADDING, menuY + menuHeight - 12f);

        // Close X
        font.draw(batch, "X", menuX + menuWidth - 24f, menuY + menuHeight - 12f);

        // Rows
        float rowY = menuY + menuHeight - 40f - PADDING;
        ResourceManager rm = eventManager.getResourceManager();

        for (BuildableItem item : items) {
            float rowBottom = rowY - ROW_HEIGHT + PADDING;

            if (item.unlocked) {
                // Icon
                Texture tex = spriteManager.getTexture(item.type, 1);
                batch.draw(tex, menuX + PADDING + 4f, rowBottom + 8f, ICON_SIZE, ICON_SIZE);

                // Name
                font.setColor(Color.WHITE);
                String name = capitalize(item.type);
                font.draw(batch, name, menuX + PADDING + ICON_SIZE + 12f, rowY - 4f);

                // Cost
                fontSmall.setColor(0.85f, 0.8f, 0.5f, 1f);
                String cost = buildCostString(item);
                fontSmall.draw(batch, cost, menuX + PADDING + ICON_SIZE + 12f, rowY - 22f);

                // Build button text
                float btnX = menuX + menuWidth - PADDING - BUTTON_WIDTH - 4f;
                float btnY = rowBottom + (ROW_HEIGHT - BUTTON_HEIGHT) / 2f - 2f;
                fontSmall.setColor(Color.WHITE);
                glyphLayout.setText(fontSmall, "Build");
                fontSmall.draw(batch, "Build",
                    btnX + (BUTTON_WIDTH - glyphLayout.width) / 2f,
                    btnY + BUTTON_HEIGHT - 8f);
            } else {
                // Locked
                font.setColor(0.4f, 0.4f, 0.5f, 1f);
                font.draw(batch, capitalize(item.type) + " — LOCKED",
                    menuX + PADDING + 8f, rowY - 12f);
            }

            rowY -= ROW_HEIGHT;
        }

        // Reset colors
        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    /**
     * Handles touch input on the menu. Returns true if the touch was consumed.
     */
    public boolean handleTouch(int screenX, int screenY) {
        if (!visible) return false;

        touchPos.set(screenX, screenY);
        viewport.unproject(touchPos);

        float menuX = MENU_MARGIN;
        float menuWidth = worldWidth - MENU_MARGIN * 2;
        float menuHeight = PADDING * 2 + ROW_HEIGHT * items.size() + 40f;
        float menuY = (worldHeight - menuHeight) / 2f;

        // Check if inside menu at all
        if (touchPos.x < menuX || touchPos.x > menuX + menuWidth
            || touchPos.y < menuY || touchPos.y > menuY + menuHeight) {
            hide();
            return true;
        }

        // Check close button (top-right area)
        if (touchPos.y > menuY + menuHeight - 40f
            && touchPos.x > menuX + menuWidth - 40f) {
            hide();
            return true;
        }

        // Check build buttons
        float rowY = menuY + menuHeight - 40f - PADDING;
        ResourceManager rm = eventManager.getResourceManager();

        for (BuildableItem item : items) {
            float rowBottom = rowY - ROW_HEIGHT + PADDING;

            if (item.unlocked) {
                float btnX = menuX + menuWidth - PADDING - BUTTON_WIDTH - 4f;
                float btnY = rowBottom + (ROW_HEIGHT - BUTTON_HEIGHT) / 2f - 2f;

                if (touchPos.x >= btnX && touchPos.x <= btnX + BUTTON_WIDTH
                    && touchPos.y >= btnY && touchPos.y <= btnY + BUTTON_HEIGHT) {
                    tryBuild(item, rm);
                    return true;
                }
            }

            rowY -= ROW_HEIGHT;
        }

        return true; // consume touch even if nothing was hit
    }

    private void tryBuild(BuildableItem item, ResourceManager rm) {
        if (!canAfford(item, rm)) {
            Gdx.app.log(TAG, "Cannot afford " + item.type);
            return;
        }
        if (!eventManager.getGridInstance().hasEmptyCell()) {
            Gdx.app.log(TAG, "Grid full — cannot build " + item.type);
            return;
        }

        // Deduct tokens
        if (!rm.decreaseTokens(item.costWood, item.costWheat, item.costStone, item.costFire)) {
            Gdx.app.log(TAG, "Token deduction failed for " + item.type);
            return;
        }

        // Spawn the facility at nearest empty cell
        int[] empty = eventManager.getGridInstance().getClosestEmptyCell(0, 0);
        if (empty != null) {
            eventManager.spawnObject(item.type, 1, empty[0], empty[1]);
            Gdx.app.log(TAG, "Built " + item.type + " at [" + empty[0] + "," + empty[1] + "]");
        }
    }

    private boolean canAfford(BuildableItem item, ResourceManager rm) {
        return rm.getAmount("wood") >= item.costWood
            && rm.getAmount("wheat") >= item.costWheat
            && rm.getAmount("stone") >= item.costStone
            && rm.getAmount("fire") >= item.costFire;
    }

    private String buildCostString(BuildableItem item) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        if (item.costWood > 0) {
            sb.append(item.costWood).append(" wood");
            first = false;
        }
        if (item.costWheat > 0) {
            if (!first) sb.append(", ");
            sb.append(item.costWheat).append(" wheat");
            first = false;
        }
        if (item.costStone > 0) {
            if (!first) sb.append(", ");
            sb.append(item.costStone).append(" stone");
            first = false;
        }
        if (item.costFire > 0) {
            if (!first) sb.append(", ");
            sb.append(item.costFire).append(" fire");
        }
        return sb.length() > 0 ? sb.toString() : "Free";
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    /**
     * Simple data holder for a buildable item.
     */
    private static class BuildableItem {
        String type;
        boolean unlocked;
        int costWood;
        int costWheat;
        int costStone;
        int costFire;
        String description;
    }
}
