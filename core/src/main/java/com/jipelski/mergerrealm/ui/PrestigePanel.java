package com.jipelski.mergerrealm.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;

import com.jipelski.mergerrealm.model.Item;
import com.jipelski.mergerrealm.util.EventManager;
import com.jipelski.mergerrealm.util.ExplorationManager;
import com.jipelski.mergerrealm.util.PrestigeManager;
import com.jipelski.mergerrealm.util.PrinceLevelConfig;
import com.jipelski.mergerrealm.util.RaidManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Prince Prestige panel — opened by tapping the top-bar level box. Always
 * opens (even below the prestige gate), so the player can see the upgrade
 * tree and Heirloom Vault at any time, not just once eligible.
 *
 * Three sections, top to bottom: the upgrade tree (5 rows, buy with Crowns),
 * the Heirloom Vault (protected equipment — a persistent, always-editable
 * list, NOT a reset-time prompt: items can be protected/unprotected any
 * time), and the Reset action itself (a two-state button — tap once arms a
 * "Tap again to confirm" state, tap again executes; disabled with an
 * explanation until canPrestige() is true).
 */
public class PrestigePanel {

    private static final String TAG = "PrestigePanel";

    private boolean visible = false;
    private boolean resetArmed = false;

    // ── Layout ──
    private static final float MARGIN = 24f;
    private static final float HEADER_HEIGHT = 52f;
    private static final float UPGRADE_ROW_HEIGHT = 52f;
    private static final float UPGRADE_ROW_GAP = 6f;
    private static final float SECTION_GAP = 14f;
    private static final float VAULT_ROW_HEIGHT = 24f;
    private static final float VAULT_ROW_GAP = 2f;
    private static final int MAX_VAULT_BROWSE_ROWS = 8;
    private static final float RESET_BTN_HEIGHT = 44f;

    // ── References ──
    private final EventManager eventManager;
    private final Viewport viewport;
    private final UITextureManager uiTex;
    private final GlyphLayout glyphLayout;

    // ── Touch ──
    private final Vector2 touchPos = new Vector2();

    private static class UpgradeRow {
        final String id, name, desc;
        UpgradeRow(String id, String name, String desc) {
            this.id = id; this.name = name; this.desc = desc;
        }
    }

    private final UpgradeRow[] upgrades = {
        new UpgradeRow(PrestigeManager.GEN_RATE, "Bountiful Lands", "+6% resource gen / tier"),
        new UpgradeRow(PrestigeManager.BUILD_COST, "Master Masons", "-5% build cost / tier"),
        new UpgradeRow(PrestigeManager.SPAWN_ODDS, "Noble Bloodlines", "Better facility spawn odds"),
        new UpgradeRow(PrestigeManager.START_LEVEL, "Head Start", "Higher starting Prince level"),
        new UpgradeRow(PrestigeManager.EQUIPMENT_SLOTS, "Heirloom Vault", "+1 protected item slot"),
    };

    public PrestigePanel(EventManager eventManager, Viewport viewport, UITextureManager uiTex) {
        this.eventManager = eventManager;
        this.viewport = viewport;
        this.uiTex = uiTex;
        this.glyphLayout = new GlyphLayout();
    }

    public boolean isVisible() { return visible; }
    public void open() { visible = true; resetArmed = false; }
    public void close() { visible = false; resetArmed = false; }

    // ── Layout helpers ──
    private float getWorldWidth() { return LayoutConfig.WORLD_WIDTH; }
    private float getWorldHeight() { return viewport.getWorldHeight(); }
    private float getMenuX() { return MARGIN; }
    private float getMenuWidth() { return getWorldWidth() - MARGIN * 2; }
    private float getMenuTop() { return getWorldHeight() - MARGIN; }
    private float getMenuBottom() { return MARGIN; }
    private float getContentTop() { return getMenuTop() - HEADER_HEIGHT; }

    /** Non-consumable items eligible for the Heirloom Vault (protected or not). */
    private List<Item> getEquipmentItems() {
        List<Item> result = new ArrayList<>();
        for (Item item : eventManager.getInventory().getItems()) {
            if (!item.isConsumable()) result.add(item);
        }
        return result;
    }

    // ══════════════════════════════════════════════════════════════
    // DRAWING
    // ══════════════════════════════════════════════════════════════

    public void drawBackground(ShapeRenderer sr) {
        if (!visible) return;
        Gdx.gl.glEnable(Gdx.gl.GL_BLEND);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(0f, 0f, 0f, 0.75f);
        sr.rect(0, 0, getWorldWidth(), getWorldHeight());
        sr.setColor(0.12f, 0.12f, 0.18f, 1f);
        sr.rect(getMenuX(), getMenuBottom(), getMenuWidth(), getMenuTop() - getMenuBottom());
        sr.setColor(0.18f, 0.14f, 0.2f, 1f); // purple-tinted header — royal
        sr.rect(getMenuX(), getMenuTop() - HEADER_HEIGHT, getMenuWidth(), HEADER_HEIGHT);

        PrestigeManager pm = eventManager.getPrestigeManager();

        // Upgrade row backgrounds
        float y = getContentTop() - UPGRADE_ROW_HEIGHT;
        for (UpgradeRow u : upgrades) {
            int cost = pm.getNextTierCost(u.id);
            boolean affordable = cost >= 0 && pm.canAfford(cost);
            if (affordable) sr.setColor(0.18f, 0.18f, 0.24f, 1f);
            else            sr.setColor(0.14f, 0.14f, 0.18f, 1f);
            sr.rect(getMenuX() + 8f, y, getMenuWidth() - 16f, UPGRADE_ROW_HEIGHT);
            y -= (UPGRADE_ROW_HEIGHT + UPGRADE_ROW_GAP);
        }

        // Vault rows — same iteration shape as drawContent()/handleTouchUp()
        y -= SECTION_GAP + 20f; // skip the "Heirloom Vault" section label
        List<Item> equipment = getEquipmentItems();
        int browseShown = 0;
        for (Item item : equipment) {
            boolean protectedItem = pm.isProtected(item.getId());
            if (!protectedItem && browseShown >= MAX_VAULT_BROWSE_ROWS) continue;

            if (protectedItem) sr.setColor(0.16f, 0.22f, 0.16f, 1f);
            else                sr.setColor(0.14f, 0.14f, 0.18f, 1f);
            sr.rect(getMenuX() + 8f, y, getMenuWidth() - 16f, VAULT_ROW_HEIGHT);
            y -= (VAULT_ROW_HEIGHT + VAULT_ROW_GAP);
            if (!protectedItem) browseShown++;
        }

        sr.end();
        Gdx.gl.glDisable(Gdx.gl.GL_BLEND);
    }

    public void drawContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        if (!visible) return;
        PrestigeManager pm = eventManager.getPrestigeManager();

        font.setColor(0.85f, 0.7f, 0.95f, 1f);
        font.draw(batch, "Prince Prestige", getMenuX() + 12f, getMenuTop() - 12f);
        font.setColor(Color.WHITE);
        font.draw(batch, "X", getMenuX() + getMenuWidth() - 28f, getMenuTop() - 12f);

        fontSmall.setColor(0.85f, 0.7f, 0.95f, 1f);
        fontSmall.draw(batch, "Balance: " + pm.getCrowns() + " Crowns",
            getMenuX() + 12f, getMenuTop() - 34f);

        // Upgrade rows
        float y = getContentTop() - UPGRADE_ROW_HEIGHT;
        for (UpgradeRow u : upgrades) {
            int tier = pm.getTier(u.id);
            int cost = pm.getNextTierCost(u.id);
            boolean affordable = cost >= 0 && pm.canAfford(cost);

            fontSmall.setColor(0.85f, 0.85f, 0.95f, 1f);
            fontSmall.draw(batch, u.name + "  (tier " + tier + ")",
                getMenuX() + 16f, y + UPGRADE_ROW_HEIGHT - 12f);

            fontSmall.setColor(0.55f, 0.55f, 0.62f, 1f);
            fontSmall.draw(batch, u.desc, getMenuX() + 16f, y + UPGRADE_ROW_HEIGHT - 30f);

            if (cost < 0) {
                fontSmall.setColor(0.5f, 0.5f, 0.7f, 1f);
                fontSmall.draw(batch, "MAX", getMenuX() + getMenuWidth() - 130f,
                    y + UPGRADE_ROW_HEIGHT - 20f);
            } else {
                if (affordable) fontSmall.setColor(0.75f, 0.65f, 0.95f, 1f);
                else             fontSmall.setColor(0.6f, 0.45f, 0.5f, 1f);
                fontSmall.draw(batch, cost + " Cr", getMenuX() + getMenuWidth() - 130f,
                    y + UPGRADE_ROW_HEIGHT - 20f);

                if (affordable) fontSmall.setColor(0.3f, 0.9f, 0.3f, 1f);
                else             fontSmall.setColor(0.4f, 0.4f, 0.45f, 1f);
                fontSmall.draw(batch, "[Buy]", getMenuX() + getMenuWidth() - 58f,
                    y + UPGRADE_ROW_HEIGHT - 20f);
            }
            y -= (UPGRADE_ROW_HEIGHT + UPGRADE_ROW_GAP);
        }

        // Vault section label
        y -= SECTION_GAP;
        font.setColor(0.85f, 0.7f, 0.95f, 1f);
        font.draw(batch, "Heirloom Vault (" + pm.getProtectedItemIds().size() + "/"
            + pm.getEquipmentSlotCount() + " protected)", getMenuX() + 12f, y);
        y -= 20f;

        List<Item> equipment = getEquipmentItems();
        int browseShown = 0;
        for (Item item : equipment) {
            boolean protectedItem = pm.isProtected(item.getId());
            if (!protectedItem && browseShown >= MAX_VAULT_BROWSE_ROWS) continue;

            if (protectedItem) fontSmall.setColor(Color.WHITE);
            else                fontSmall.setColor(0.65f, 0.65f, 0.7f, 1f);
            String label = (protectedItem ? "[Protected] " : "[Protect] ") + item.getName();
            fontSmall.draw(batch, label, getMenuX() + 16f, y + VAULT_ROW_HEIGHT - 7f);

            y -= (VAULT_ROW_HEIGHT + VAULT_ROW_GAP);
            if (!protectedItem) browseShown++;
        }
        if (equipment.isEmpty()) {
            fontSmall.setColor(0.5f, 0.5f, 0.55f, 1f);
            fontSmall.draw(batch, "No equipment owned yet.", getMenuX() + 16f, y + VAULT_ROW_HEIGHT - 7f);
            y -= (VAULT_ROW_HEIGHT + VAULT_ROW_GAP);
        }

        // Reset button
        y -= SECTION_GAP;
        boolean eligible = pm.canPrestige();
        float btnX = getMenuX() + 12f;
        float btnW = getMenuWidth() - 24f;
        uiTex.drawPanel(batch, eligible ? uiTex.btnActive : uiTex.btnDisabled,
            btnX, y - RESET_BTN_HEIGHT, btnW, RESET_BTN_HEIGHT);

        String label;
        if (!eligible) {
            label = ineligibilityReason();
        } else {
            label = resetArmed ? "Tap again to confirm" : "Reset for "
                + pm.earnedCrowns(eventManager.getBattleFieldManager().getLevel()) + " Crowns";
        }
        fontSmall.setColor(Color.WHITE);
        glyphLayout.setText(fontSmall, label);
        fontSmall.draw(batch, label, btnX + (btnW - glyphLayout.width) / 2f,
            y - RESET_BTN_HEIGHT / 2f + glyphLayout.height / 2f);

        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }

    private String ineligibilityReason() {
        int level = eventManager.getBattleFieldManager().getLevel();
        if (level < PrinceLevelConfig.MAX_LEVEL_FOR_PRESTIGE) {
            return "Reach level " + PrinceLevelConfig.MAX_LEVEL_FOR_PRESTIGE + " to prestige";
        }
        RaidManager raidManager = eventManager.getRaidManager();
        ExplorationManager explorationManager = eventManager.getExplorationManager();
        if (raidManager.isRaidActive() || !explorationManager.getActiveSlots().isEmpty()) {
            return "Recall your raid/exploration party first";
        }
        return "Not eligible";
    }

    // ══════════════════════════════════════════════════════════════
    // TOUCH
    // ══════════════════════════════════════════════════════════════

    public boolean handleTouchDown(int screenX, int screenY) {
        if (!visible) return false;
        return true; // modal — consume everything
    }

    public boolean handleTouchDragged(int screenX, int screenY) {
        return visible;
    }

    public boolean handleTouchUp(int screenX, int screenY) {
        if (!visible) return false;
        touchPos.set(screenX, screenY);
        viewport.unproject(touchPos);

        // Close button
        if (touchPos.x > getMenuX() + getMenuWidth() - 40f
            && touchPos.y > getMenuTop() - HEADER_HEIGHT) {
            close();
            return true;
        }
        // Tap outside panel closes
        if (touchPos.x < getMenuX() || touchPos.x > getMenuX() + getMenuWidth()
            || touchPos.y < getMenuBottom() || touchPos.y > getMenuTop()) {
            close();
            return true;
        }

        PrestigeManager pm = eventManager.getPrestigeManager();

        // Upgrade [Buy] taps
        float buyX = getMenuX() + getMenuWidth() - 62f;
        float y = getContentTop() - UPGRADE_ROW_HEIGHT;
        for (UpgradeRow u : upgrades) {
            if (touchPos.y >= y && touchPos.y <= y + UPGRADE_ROW_HEIGHT) {
                if (touchPos.x >= buyX) {
                    pm.purchase(u.id);
                }
                resetArmed = false;
                return true;
            }
            y -= (UPGRADE_ROW_HEIGHT + UPGRADE_ROW_GAP);
        }

        // Vault rows
        y -= SECTION_GAP + 20f;
        List<Item> equipment = getEquipmentItems();
        int browseShown = 0;
        for (Item item : equipment) {
            boolean protectedItem = pm.isProtected(item.getId());
            if (!protectedItem && browseShown >= MAX_VAULT_BROWSE_ROWS) continue;

            if (touchPos.y >= y && touchPos.y <= y + VAULT_ROW_HEIGHT) {
                pm.toggleProtected(item.getId());
                resetArmed = false;
                return true;
            }
            y -= (VAULT_ROW_HEIGHT + VAULT_ROW_GAP);
            if (!protectedItem) browseShown++;
        }
        if (equipment.isEmpty()) {
            y -= (VAULT_ROW_HEIGHT + VAULT_ROW_GAP);
        }

        // Reset button
        y -= SECTION_GAP;
        float btnX = getMenuX() + 12f;
        float btnW = getMenuWidth() - 24f;
        if (touchPos.x >= btnX && touchPos.x <= btnX + btnW
            && touchPos.y >= y - RESET_BTN_HEIGHT && touchPos.y <= y) {
            if (!pm.canPrestige()) {
                return true;
            }
            if (resetArmed) {
                eventManager.performPrestigeReset();
                resetArmed = false;
                close();
            } else {
                resetArmed = true;
            }
            return true;
        }

        return true;
    }
}
