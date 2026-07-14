package com.jipelski.mergerrealm.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.Viewport;

import com.jipelski.mergerrealm.data.ChestData;
import com.jipelski.mergerrealm.data.FacilityData;
import com.jipelski.mergerrealm.data.GenData;
import com.jipelski.mergerrealm.data.MonsterData;
import com.jipelski.mergerrealm.data.PrinceData;
import com.jipelski.mergerrealm.data.ResourcePouchData;
import com.jipelski.mergerrealm.data.StorageData;
import com.jipelski.mergerrealm.data.TokenData;
import com.jipelski.mergerrealm.data.UnitData;
import com.jipelski.mergerrealm.model.FacilitySpawnConfiguration;
import com.jipelski.mergerrealm.ui.IconText.Seg;
import com.jipelski.mergerrealm.util.EventManager;
import com.jipelski.mergerrealm.util.GameDataLoader;
import com.jipelski.mergerrealm.util.GameTypes;
import com.jipelski.mergerrealm.util.SpriteManager;

import java.util.ArrayList;
import java.util.List;

/**
 * "All levels" reference panel — opened from the small info button in the
 * selection description box (see MergerRealmGame.drawInfoButton() /
 * GridInputHandler's info-button handling). Unlike the description box,
 * which shows the SELECTED INSTANCE's live state (current HP, equipment/
 * rune-boosted damage, held count), this shows the BASE stats defined for
 * every level of that TYPE — a wiki-style table built once per open() from
 * GameDataLoader, not the live GameObject.
 *
 * Deliberately does not clear the grid selection on open/close (unlike the
 * gold-chip/prestige-box convention) — the point is showing info about the
 * object the player still has selected, so it must still be selected (and
 * the description box still visible) once this panel closes.
 *
 * Unit/resource/type NAMES embedded in each level's stat line are rendered
 * as sprites, not words (2026-07-12 — the panel was too text-crowded).
 * Numeric/label text (HP/DMG/XP/Lv/"|"/"Nemesis:"/etc.) stays as text; only
 * the name tokens (unit type, resource, nemesis, chest reward, token type)
 * become icons. See the Seg/addResourceSeg/addTypeSeg helpers below.
 */
public class InfoPanel {

    private static final String TAG = "InfoPanel";

    private boolean visible = false;
    private String type;
    private int highlightLevel = -1;

    // ── Layout ──
    private static final float MARGIN = 24f;
    private static final float HEADER_HEIGHT = 52f;
    private static final float LINE_HEIGHT = 18f;
    private static final float ROW_GAP = 6f;
    private static final float ICON_SIZE = 16f;

    // ── References ──
    private final EventManager eventManager;
    private final Viewport viewport;
    private final SpriteManager spriteManager;
    private final GlyphLayout glyphLayout;

    // ── Touch / scroll — mirrors InventoryMenu's drag-to-scroll exactly
    // (touchStartY delta, 8px threshold before it counts as a scroll,
    // 0.5f damping, clamp to [0, maxScrollY]) so the feel is consistent. ──
    private final Vector2 touchPos = new Vector2();
    private boolean touchDown = false;
    private float touchStartY = 0f;
    private boolean scrolling = false;
    private float scrollY = 0f;
    private float maxScrollY = 0f;

    private static class LevelRow {
        final int level;
        final List<List<Seg>> lines;
        LevelRow(int level, List<List<Seg>> lines) { this.level = level; this.lines = lines; }
    }
    private final List<LevelRow> rows = new ArrayList<>();

    public InfoPanel(EventManager eventManager, SpriteManager spriteManager, Viewport viewport,
                      UITextureManager uiTex) {
        this.eventManager = eventManager;
        this.spriteManager = spriteManager;
        this.viewport = viewport;
        this.glyphLayout = new GlyphLayout();
        // uiTex accepted for constructor-signature symmetry with sibling
        // panels (OutfitPanel/PrestigePanel/HiddenTemplePopup) — this panel
        // draws its own rows with plain ShapeRenderer rects, same as those.
    }

    public boolean isVisible() { return visible; }

    /** currentLevel is only used to highlight that row — purely cosmetic. */
    public void open(String type, int currentLevel) {
        this.type = type;
        this.highlightLevel = currentLevel;
        buildRows();
        scrollY = 0f;
        recalculateScroll();
        visible = true;
    }

    public void close() { visible = false; }

    // ── Layout helpers ──
    private float getWorldWidth() { return LayoutConfig.WORLD_WIDTH; }
    private float getWorldHeight() { return viewport.getWorldHeight(); }
    private float getMenuX() { return MARGIN; }
    private float getMenuWidth() { return getWorldWidth() - MARGIN * 2; }
    private float getMenuTop() { return getWorldHeight() - MARGIN; }
    private float getMenuBottom() { return MARGIN; }
    private float getContentTop() { return getMenuTop() - HEADER_HEIGHT; }

    private void recalculateScroll() {
        float contentHeight = 0f;
        for (LevelRow row : rows) {
            contentHeight += row.lines.size() * LINE_HEIGHT + ROW_GAP;
        }
        float availableHeight = getContentTop() - getMenuBottom();
        maxScrollY = Math.max(0, contentHeight - availableHeight);
        scrollY = Math.min(scrollY, maxScrollY);
    }

    // ══════════════════════════════════════════════════════════════
    // ROW BUILDING — reads only GenData (base, level-defined stats), never
    // the live GameObject; built once per open(), not per frame.
    // ══════════════════════════════════════════════════════════════

    private void buildRows() {
        rows.clear();
        GameDataLoader gdl = eventManager.getGameDataLoader();
        GenData first = gdl.getGameData(type, 1);
        int maxLvl = first != null ? first.getMaxLVL() : 0;
        for (int lvl = 1; lvl <= maxLvl; lvl++) {
            GenData data = gdl.getGameData(type, lvl);
            if (data == null) continue;
            rows.add(new LevelRow(lvl, buildLines(gdl, lvl, data)));
        }
    }

    private List<List<Seg>> buildLines(GameDataLoader gdl, int lvl, GenData data) {
        List<List<Seg>> lines = new ArrayList<>();

        if (GameTypes.isUnit(type)) {
            UnitData ud = (UnitData) data;
            List<Seg> line1 = new ArrayList<>();
            line1.add(Seg.text("Lv" + lvl + ":  HP " + ud.getHp()
                + "  |  DMG " + ud.getDamage() + "  |  +" + ud.getGen_rate() + " "));
            addResourceSeg(line1, ud.getResource());
            line1.add(Seg.text("/tick"));
            if (ud.getSecondaryResource() != null && !"none".equals(ud.getSecondaryResource())) {
                line1.add(Seg.text("  |  +" + ud.getSecondaryGenRate() + " "));
                addResourceSeg(line1, ud.getSecondaryResource());
                line1.add(Seg.text("/tick"));
            }
            lines.add(line1);

            List<Seg> line2 = new ArrayList<>();
            line2.add(Seg.text("     XP " + ud.getXP_Rate() + "  |  Nemesis: "));
            addTypeSeg(line2, ud.getNemesis(), 1); // nemesis is a fixed monster type icon, not a leveled stat here
            line2.add(Seg.text(" +" + ud.getNemesis_Rate()));
            lines.add(line2);

        } else if (GameTypes.isFacility(type)) {
            List<Seg> line1 = new ArrayList<>();
            FacilityData fd = (FacilityData) data;
            line1.add(Seg.text("Lv" + lvl + ":  "));
            if (fd.getTimeCost() > 0) {
                line1.add(Seg.text("Spawns every " + fd.getTimeCost() + "s"));
            } else {
                line1.add(Seg.text("Tap cost "));
                List<Seg> tapSegs = new ArrayList<>();
                boolean hasTap = appendCostSegs(tapSegs, fd.getTapCost1(), fd.getTapCost2(), fd.getTapCost3());
                if (hasTap) line1.addAll(tapSegs); else line1.add(Seg.text("free"));
            }
            List<Seg> buildSegs = new ArrayList<>();
            boolean hasBuild = appendCostSegs(buildSegs, fd.getBuildCost1(), fd.getBuildCost2(),
                fd.getBuildCost3(), fd.getBuildCost4());
            if (hasBuild) {
                line1.add(Seg.text("  |  Build "));
                line1.addAll(buildSegs);
            }
            lines.add(line1);

            List<Seg> line2 = new ArrayList<>();
            line2.add(Seg.text("     Spawns: "));
            List<FacilitySpawnConfiguration> spawns = gdl.getSpawnConfiguration(type + "_" + lvl);
            appendSpawnTableSegs(line2, spawns);
            lines.add(line2);

        } else if (GameTypes.isStorage(type)) {
            StorageData sd = (StorageData) data;
            List<Seg> line = new ArrayList<>();
            line.add(Seg.text("Lv" + lvl + ":  +" + sd.getStorage_size() + " "));
            addResourceSeg(line, sd.getStorage_type());
            line.add(Seg.text(" capacity"));
            List<Seg> buildSegs = new ArrayList<>();
            boolean hasBuild = appendCostSegs(buildSegs, sd.getBuild_cost1(), sd.getBuild_cost2(), sd.getBuild_cost3());
            if (hasBuild) {
                line.add(Seg.text("  |  Build "));
                line.addAll(buildSegs);
            }
            lines.add(line);

        } else if (GameTypes.isMonster(type)) {
            MonsterData md = (MonsterData) data;
            List<Seg> line = new ArrayList<>();
            line.add(Seg.text("Lv" + lvl + ":  HP " + md.getHp() + "  |  DMG " + md.getDamage()
                + "  |  Reward: "));
            addTypeSeg(line, md.getReward(), 1); // reward is a chest type (e.g. "nail_chest"), chests are single-level
            lines.add(line);

        } else if (GameTypes.isChest(type)) {
            ChestData cd = (ChestData) data;
            List<Seg> line1 = new ArrayList<>();
            line1.add(Seg.text("Lv" + lvl + ":  Taps " + cd.getTap_count() + "  |  Drops: "));
            addTypeSeg(line1, cd.getToken_type(), 1); // representative token icon (e.g. "nail_token" -> nail_token_1)
            lines.add(line1);

            List<Seg> line2 = new ArrayList<>();
            line2.add(Seg.text("     Odds: "));
            List<FacilitySpawnConfiguration> drops = gdl.getSpawnConfiguration(type);
            appendSpawnTableSegs(line2, drops);
            lines.add(line2);

        } else if (GameTypes.isToken(type)) {
            TokenData td = (TokenData) data;
            List<Seg> line = new ArrayList<>();
            line.add(Seg.text("Lv" + lvl + ":  Value +" + td.getValue() + " "));
            addResourceSeg(line, td.getTokenType());
            lines.add(line);

        } else if (GameTypes.isResourcePouch(type)) {
            ResourcePouchData rd = (ResourcePouchData) data;
            List<Seg> line = new ArrayList<>();
            line.add(Seg.text("Lv" + lvl + ":  Fills +" + rd.getFill_percent() + "% "));
            addResourceSeg(line, rd.getResource_type());
            lines.add(line);

        } else if ("prince".equals(type)) {
            PrinceData pd = (PrinceData) data;
            List<Seg> line = new ArrayList<>();
            line.add(Seg.text("Lv" + lvl + ":  +" + pd.getResource_1() + " "));
            addResourceSeg(line, "food");
            line.add(Seg.text(", +" + pd.getResource_2() + " "));
            addResourceSeg(line, "wood");
            line.add(Seg.text(", +" + pd.getResource_3() + " "));
            addResourceSeg(line, "iron");
            lines.add(line);

        } else {
            List<Seg> line = new ArrayList<>();
            line.add(Seg.text("Lv" + lvl + ":  (no stat data)"));
            lines.add(line);
        }
        return lines;
    }

    /**
     * Maps a bare resource/currency word to its icon sprite key, or null when
     * the word has no icon (falls back to text). food/wood/iron point at new
     * sprites/{food,wood,iron}.png (don't exist yet — renders the game's
     * standard magenta fallback until real art is added, same convention as
     * every other missing sprite). nail/slate/ingot/relic reuse their
     * existing "*_token_1" art. "nothing"/"none"/"misc" intentionally have
     * no icon.
     */
    private String resourceIconKey(String resource) {
        if (resource == null) return null;
        switch (resource) {
            case "food": case "wood": case "iron":
                return resource;
            case "nail": case "slate": case "ingot": case "relic":
                return resource + "_token_1";
            default:
                return null;
        }
    }

    private void addResourceSeg(List<Seg> line, String resource) {
        String key = resourceIconKey(resource);
        if (key != null) line.add(Seg.icon(key)); else line.add(Seg.text(resource));
    }

    /** type + level icon, e.g. addTypeSeg(line, "goblin", 3) -> sprites/goblin_3.png. */
    private void addTypeSeg(List<Seg> line, String objType, int level) {
        line.add(Seg.icon(objType + "_" + level));
    }

    /** amounts.length must be 3 (food/wood/iron) or 4 (+ misc) — matches build/tap cost shapes. */
    private boolean appendCostSegs(List<Seg> line, int... amounts) {
        String[] labels = {"food", "wood", "iron", "misc"};
        boolean any = false;
        for (int i = 0; i < amounts.length; i++) {
            if (amounts[i] > 0) {
                if (any) line.add(Seg.text(", "));
                line.add(Seg.text(amounts[i] + " "));
                addResourceSeg(line, labels[i]);
                any = true;
            }
        }
        return any;
    }

    private void appendSpawnTableSegs(List<Seg> line, List<FacilitySpawnConfiguration> entries) {
        if (entries == null || entries.isEmpty()) {
            line.add(Seg.text("—"));
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            FacilitySpawnConfiguration e = entries.get(i);
            if (i > 0) line.add(Seg.text(", "));
            addTypeSeg(line, e.getUnitType(), e.getUnitLVL());
            line.add(Seg.text(" Lv" + e.getUnitLVL() + " "
                + Math.round(e.getSpawnProbability() * 100) + "%"));
        }
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

        float contentTop = getContentTop();
        float contentBottom = getMenuBottom();
        float y = contentTop + scrollY;
        for (LevelRow row : rows) {
            float blockHeight = row.lines.size() * LINE_HEIGHT;
            float blockBottom = y - blockHeight;
            // Bounds-skip in place of scissor clipping (no ShapeRenderer
            // scissor is used anywhere in this codebase) — mirrors
            // InventoryMenu.drawItemSlotBackgrounds's per-item visibility
            // check. Also guarantees rows can never draw over the header
            // drawn after this loop, regardless of scroll edge cases.
            if (y <= contentBottom || blockBottom >= contentTop) {
                y = blockBottom - ROW_GAP;
                continue;
            }
            if (row.level == highlightLevel) {
                sr.setColor(0.2f, 0.3f, 0.18f, 1f); // currently-selected level
            } else {
                sr.setColor(0.16f, 0.16f, 0.2f, 1f);
            }
            sr.rect(getMenuX() + 8f, blockBottom, getMenuWidth() - 16f, blockHeight);
            y = blockBottom - ROW_GAP;
        }

        // Drawn last (not first, unlike OutfitPanel's static rows) so it
        // always wins over any row that might otherwise overlap it.
        sr.setColor(0.2f, 0.18f, 0.1f, 1f);
        sr.rect(getMenuX(), getMenuTop() - HEADER_HEIGHT, getMenuWidth(), HEADER_HEIGHT);

        sr.end();
        Gdx.gl.glDisable(Gdx.gl.GL_BLEND);
    }

    public void drawContent(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        if (!visible) return;
        batch.setColor(Color.WHITE);

        // Header: type sprite in place of the type name, same "Lv N" the
        // highlighted row uses (falls back to level 1 when nothing is
        // highlighted — e.g. panel reopened without a live selection).
        float headerIconSize = HEADER_HEIGHT - 20f;
        float headerIconX = getMenuX() + 12f;
        float headerIconY = getMenuTop() - HEADER_HEIGHT / 2f - headerIconSize / 2f;
        int headerSpriteLevel = highlightLevel >= 1 ? highlightLevel : 1;
        batch.draw(spriteManager.getTexture(type, headerSpriteLevel),
            headerIconX, headerIconY, headerIconSize, headerIconSize);

        font.setColor(1f, 0.85f, 0.4f, 1f);
        font.draw(batch, " — All Levels", headerIconX + headerIconSize + 8f, getMenuTop() - 12f);
        font.setColor(Color.WHITE);
        font.draw(batch, "X", getMenuX() + getMenuWidth() - 28f, getMenuTop() - 12f);

        float contentTop = getContentTop();
        float contentBottom = getMenuBottom();
        float y = contentTop + scrollY;
        for (LevelRow row : rows) {
            float blockHeight = row.lines.size() * LINE_HEIGHT;
            float blockBottom = y - blockHeight;
            if (y <= contentBottom || blockBottom >= contentTop) {
                y = blockBottom - ROW_GAP;
                continue;
            }

            if (row.level == highlightLevel) {
                fontSmall.setColor(1f, 0.95f, 0.6f, 1f);
            } else {
                fontSmall.setColor(0.85f, 0.85f, 0.92f, 1f);
            }
            for (int i = 0; i < row.lines.size(); i++) {
                float lineY = y - i * LINE_HEIGHT - 4f;
                if (lineY <= contentBottom || lineY > contentTop) continue;
                IconText.draw(batch, fontSmall, glyphLayout, spriteManager,
                    row.lines.get(i), getMenuX() + 16f, lineY, ICON_SIZE);
            }
            y = blockBottom - ROW_GAP;
        }

        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
        batch.setColor(Color.WHITE);
    }

    // ══════════════════════════════════════════════════════════════
    // TOUCH
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
        if (!visible || !touchDown) return visible;
        touchPos.set(screenX, screenY);
        viewport.unproject(touchPos);

        float dy = touchPos.y - touchStartY;
        if (Math.abs(dy) > 8f) {
            scrolling = true;
        }
        if (scrolling) {
            scrollY += dy * 0.5f;
            scrollY = Math.max(0, Math.min(scrollY, maxScrollY));
            touchStartY = touchPos.y;
        }
        return true;
    }

    public boolean handleTouchUp(int screenX, int screenY) {
        if (!visible) return false;
        touchDown = false;
        boolean wasScrolling = scrolling;
        scrolling = false;
        if (wasScrolling) return true; // a drag-release isn't a tap

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
        return true; // consume — rows are read-only, no per-row action
    }
}
