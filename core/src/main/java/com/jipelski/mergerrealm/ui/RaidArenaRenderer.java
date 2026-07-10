package com.jipelski.mergerrealm.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.Viewport;

import com.jipelski.mergerrealm.model.RaidState;
import com.jipelski.mergerrealm.model.RaidState.CombatEvent;
import com.jipelski.mergerrealm.model.RaidState.RaidEnemy;
import com.jipelski.mergerrealm.util.EventManager;
import com.jipelski.mergerrealm.util.GoldManager;
import com.jipelski.mergerrealm.util.RaidManager;
import com.jipelski.mergerrealm.util.SpriteManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Visual combat arena for raids — Fallout Shelter style.
 *
 * Layout (portrait):
 *   ┌─────────────────────────┐
 *   │ Room 2/4                │
 *   │   [enemy] [enemy]       │  ← enemies, HP bars below
 *   │      floating dmg       │
 *   │   [front] [front]       │  ← party front row
 *   │    [back] [back]        │  ← party back row
 *   │ ⚡FURY▓▓▓░  [Potion]... │  ← control bar
 *   └─────────────────────────┘
 *
 * Animations (no spritesheets needed):
 *   - Attack: sprite lunges 26px toward opponent over 0.3s (sine ease)
 *   - Hit: red flash tint for 0.15s
 *   - Death: alpha fade over 0.5s
 *   - Damage numbers float up 44px and fade; crits are gold and larger
 *
 * The renderer consumes CombatEvents pushed by RaidManager each frame.
 * It never mutates combat state — pure presentation + touch forwarding.
 *
 * DRAW ORDER RULE (avoids ShapeRenderer/SpriteBatch interleaving bugs):
 *   1. drawShapes(sr)   — its own sr.begin()/end(), OUTSIDE any batch
 *   2. drawBatch(batch) — called INSIDE an open batch.begin()/end()
 */
public class RaidArenaRenderer {

    private static final String TAG = "RaidArenaRenderer";

    // ── Animation constants ──
    private static final float ATTACK_ANIM_DURATION = 0.30f;
    private static final float ATTACK_LUNGE_PX = 26f;
    private static final float HIT_FLASH_DURATION = 0.15f;
    private static final float DEATH_FADE_DURATION = 0.5f;
    private static final float FLOAT_TEXT_LIFETIME = 0.9f;
    private static final float FLOAT_TEXT_RISE = 44f;

    // ── Layout constants ──
    private static final float SPRITE_SIZE = 56f;
    private static final float HP_BAR_W = 48f;
    private static final float HP_BAR_H = 5f;
    private static final float CONTROL_BAR_H = 92f;
    private static final float FURY_BAR_W = 160f;
    private static final float FURY_BAR_H = 24f;

    // ── Floating damage numbers ──
    private static class FloatingText {
        float x, y, t;
        String text;
        Color color;
        boolean big;
    }
    private final List<FloatingText> floatingTexts = new ArrayList<>();

    // ── Per-entity animation state ──
    // Party (fixed 4 slots)
    private final float[] partyAttackAnim = new float[4];   // -1 = idle, else 0..1 progress
    private final float[] partyHitFlash = new float[4];
    private final float[] partyDeathFade = new float[4];    // 1 = fully visible

    // Enemies (rebuilt per room)
    private float[] enemyAttackAnim = new float[0];
    private float[] enemyHitFlash = new float[0];
    private float[] enemyDeathFade = new float[0];
    private int lastRoomIndex = -1;

    // ── Room transition banner ──
    private String bannerText = null;
    private float bannerTimer = 0f;

    // ── References ──
    private final EventManager eventManager;
    private final SpriteManager spriteManager;
    private final Viewport viewport;
    private final GlyphLayout glyphLayout = new GlyphLayout();

    public RaidArenaRenderer(EventManager eventManager, SpriteManager spriteManager,
                             Viewport viewport) {
        this.eventManager = eventManager;
        this.spriteManager = spriteManager;
        this.viewport = viewport;
        reset();
    }

    /** Call when a raid starts to clear stale animation state. */
    public void reset() {
        for (int i = 0; i < 4; i++) {
            partyAttackAnim[i] = -1f;
            partyHitFlash[i] = 0f;
            partyDeathFade[i] = 1f;
        }
        enemyAttackAnim = new float[0];
        enemyHitFlash = new float[0];
        enemyDeathFade = new float[0];
        lastRoomIndex = -1;
        floatingTexts.clear();
        bannerText = null;
        bannerTimer = 0f;
    }

    // ══════════════════════════════════════════════════════════════
    // UPDATE — advance animations, consume combat events
    // ══════════════════════════════════════════════════════════════

    public void update(float delta) {
        RaidManager rm = eventManager.getRaidManager();
        RaidState raid = rm.getActiveRaid();
        if (raid == null) return;

        // Rebuild enemy anim arrays when the room changes
        if (raid.getCurrentRoomIndex() != lastRoomIndex) {
            lastRoomIndex = raid.getCurrentRoomIndex();
            int n = raid.getActiveEnemies().size();
            enemyAttackAnim = new float[n];
            enemyHitFlash = new float[n];
            enemyDeathFade = new float[n];
            for (int i = 0; i < n; i++) {
                enemyAttackAnim[i] = -1f;
                enemyDeathFade[i] = 1f;
            }
        }

        // Consume combat events from the manager
        for (CombatEvent ev : raid.drainEvents()) {
            handleEvent(raid, ev);
        }

        // Advance party animations
        for (int i = 0; i < 4; i++) {
            if (partyAttackAnim[i] >= 0f) {
                partyAttackAnim[i] += delta / ATTACK_ANIM_DURATION;
                if (partyAttackAnim[i] >= 1f) partyAttackAnim[i] = -1f;
            }
            if (partyHitFlash[i] > 0f) partyHitFlash[i] -= delta;
            if (raid.isSlotOccupied(i) && raid.getPartyDead()[i]
                && partyDeathFade[i] > 0.35f) {
                partyDeathFade[i] = Math.max(0.35f,
                    partyDeathFade[i] - delta / DEATH_FADE_DURATION);
            }
        }

        // Advance enemy animations
        List<RaidEnemy> enemies = raid.getActiveEnemies();
        for (int i = 0; i < enemies.size() && i < enemyAttackAnim.length; i++) {
            if (enemyAttackAnim[i] >= 0f) {
                enemyAttackAnim[i] += delta / ATTACK_ANIM_DURATION;
                if (enemyAttackAnim[i] >= 1f) enemyAttackAnim[i] = -1f;
            }
            if (enemyHitFlash[i] > 0f) enemyHitFlash[i] -= delta;
            if (!enemies.get(i).isAlive() && enemyDeathFade[i] > 0f) {
                enemyDeathFade[i] = Math.max(0f,
                    enemyDeathFade[i] - delta / DEATH_FADE_DURATION);
            }
        }

        // Advance floating texts
        for (int i = floatingTexts.size() - 1; i >= 0; i--) {
            FloatingText ft = floatingTexts.get(i);
            ft.t += delta;
            if (ft.t >= FLOAT_TEXT_LIFETIME) floatingTexts.remove(i);
        }

        // Banner countdown
        if (bannerTimer > 0f) {
            bannerTimer -= delta;
            if (bannerTimer <= 0f) bannerText = null;
        }
    }

    private void handleEvent(RaidState raid, CombatEvent ev) {
        switch (ev.kind) {
            case "attack": {
                // Start attacker lunge
                if (ev.actorIsParty) {
                    if (ev.actorIdx >= 0 && ev.actorIdx < 4) partyAttackAnim[ev.actorIdx] = 0f;
                } else {
                    if (ev.actorIdx >= 0 && ev.actorIdx < enemyAttackAnim.length)
                        enemyAttackAnim[ev.actorIdx] = 0f;
                }
                // Hit flash + floating number on target
                if (ev.damage > 0) {
                    float[] pos = ev.targetIsParty
                        ? getPartyPos(ev.targetIdx)
                        : getEnemyPos(ev.targetIdx, raid.getActiveEnemies().size());
                    if (ev.targetIsParty) {
                        if (ev.targetIdx >= 0 && ev.targetIdx < 4)
                            partyHitFlash[ev.targetIdx] = HIT_FLASH_DURATION;
                    } else {
                        if (ev.targetIdx >= 0 && ev.targetIdx < enemyHitFlash.length)
                            enemyHitFlash[ev.targetIdx] = HIT_FLASH_DURATION;
                    }
                    spawnFloatingText(pos[0], pos[1] + SPRITE_SIZE * 0.7f,
                        (ev.crit ? "CRIT -" : "-") + ev.damage,
                        ev.crit ? new Color(1f, 0.82f, 0.2f, 1f)
                            : (ev.targetIsParty
                            ? new Color(1f, 0.35f, 0.35f, 1f)
                            : new Color(1f, 1f, 1f, 1f)),
                        ev.crit);
                }
                break;
            }
            case "heal": {
                float[] pos = getPartyPos(ev.targetIdx);
                spawnFloatingText(pos[0], pos[1] + SPRITE_SIZE * 0.7f,
                    "+" + ev.damage, new Color(0.35f, 1f, 0.45f, 1f), false);
                break;
            }
            case "room_clear": {
                bannerText = "ROOM CLEARED!";
                bannerTimer = 2.2f;
                break;
            }
            case "room_start": {
                bannerText = "Room " + (ev.actorIdx + 1);
                bannerTimer = 1.2f;
                break;
            }
            case "fury": {
                bannerText = "FURY x" + String.format("%.1f", ev.damage / 10f) + "!";
                bannerTimer = 1.0f;
                break;
            }
        }
    }

    private void spawnFloatingText(float x, float y, String text, Color color, boolean big) {
        FloatingText ft = new FloatingText();
        // Small horizontal jitter so stacked numbers don't overlap perfectly
        ft.x = x + (float)(Math.random() * 24 - 12);
        ft.y = y;
        ft.t = 0f;
        ft.text = text;
        ft.color = color;
        ft.big = big;
        floatingTexts.add(ft);
    }

    // ══════════════════════════════════════════════════════════════
    // LAYOUT — entity screen positions (centers)
    // ══════════════════════════════════════════════════════════════

    private float worldW() { return LayoutConfig.WORLD_WIDTH; }
    private float worldH() { return viewport.getWorldHeight(); }

    /** Enemy position for index i of n enemies (centered row, wraps to 2 rows if >4). */
    public float[] getEnemyPos(int i, int n) {
        if (n <= 0) n = 1;
        int perRow = Math.min(n, 4);
        int row = i / 4;
        int col = i % 4;
        int rowCount = (row == 0) ? Math.min(n, 4) : (n - 4);
        float spacing = Math.min(80f, (worldW() - 60f) / Math.max(1, perRow));
        float rowWidth = (rowCount - 1) * spacing;
        float x = worldW() / 2f - rowWidth / 2f + col * spacing;
        float y = worldH() * 0.62f + row * (SPRITE_SIZE + 18f);
        return new float[]{x, y};
    }

    /** Party position for slot 0-3. Slots 0,1 front (higher); 2,3 back. */
    public float[] getPartyPos(int slot) {
        boolean front = slot < 2;
        float x = worldW() / 2f + ((slot % 2 == 0) ? -70f : 70f);
        float y = front ? worldH() * 0.36f : worldH() * 0.245f;
        return new float[]{x, y};
    }

    private float getFuryBarX() { return 24f; }
    private float getFuryBarY() { return 30f; }

    /** True if (worldX, worldY) is on the Fury button/bar. Generous hit area. */
    public boolean isFuryButtonHit(float wx, float wy) {
        return wx >= getFuryBarX() - 8f && wx <= getFuryBarX() + FURY_BAR_W + 8f
            && wy >= getFuryBarY() - 14f && wy <= getFuryBarY() + FURY_BAR_H + 28f;
    }

    // Revive party
    private float reviveBtnX = 24f, reviveBtnY = 60f, reviveBtnW = 150f, reviveBtnH = 24f;
    public boolean isReviveButtonHit(float wx, float wy) {
        return wx >= reviveBtnX && wx <= reviveBtnX + reviveBtnW
            && wy >= reviveBtnY && wy <= reviveBtnY + reviveBtnH;
    }

    // ══════════════════════════════════════════════════════════════
    // DRAW PASS 1 — SHAPES (call OUTSIDE batch, does own begin/end)
    // ══════════════════════════════════════════════════════════════

    public void drawShapes(ShapeRenderer sr) {
        RaidManager rm = eventManager.getRaidManager();
        RaidState raid = rm.getActiveRaid();
        if (raid == null) return;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        // ── Control bar background ──
        sr.setColor(0.10f, 0.10f, 0.15f, 1f);
        sr.rect(16f, 16f, worldW() - 32f, CONTROL_BAR_H);

        // ── HP bars: enemies ──
        List<RaidEnemy> enemies = raid.getActiveEnemies();
        for (int i = 0; i < enemies.size(); i++) {
            RaidEnemy e = enemies.get(i);
            if (!e.isAlive() && (i >= enemyDeathFade.length || enemyDeathFade[i] <= 0f)) continue;
            float[] pos = getEnemyPos(i, enemies.size());
            drawHpBar(sr, pos[0], pos[1] - SPRITE_SIZE / 2f - 10f,
                e.hp, e.maxHp, e.boss);
        }

        // ── HP bars: party ──
        for (int i = 0; i < 4; i++) {
            if (!raid.isSlotOccupied(i)) continue;
            float[] pos = getPartyPos(i);
            drawHpBar(sr, pos[0], pos[1] - SPRITE_SIZE / 2f - 10f,
                raid.getPartyCurrentHp()[i], raid.getPartyMaxHp()[i], false);
        }

        // ── Fury bar ──
        drawFuryBar(sr, raid);

        sr.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void drawHpBar(ShapeRenderer sr, float centerX, float y,
                           int hp, int maxHp, boolean boss) {
        float w = boss ? HP_BAR_W * 1.5f : HP_BAR_W;
        float x = centerX - w / 2f;
        float ratio = maxHp > 0 ? Math.max(0f, (float) hp / maxHp) : 0f;

        // Background
        sr.setColor(0.25f, 0.08f, 0.08f, 0.9f);
        sr.rect(x, y, w, HP_BAR_H);
        // Fill: green → red
        sr.setColor(1f - ratio, ratio, 0.12f, 1f);
        sr.rect(x, y, w * ratio, HP_BAR_H);
    }

    private void drawFuryBar(ShapeRenderer sr, RaidState raid) {
        float x = getFuryBarX();
        float y = getFuryBarY();

        // Background
        sr.setColor(0.20f, 0.16f, 0.08f, 1f);
        sr.rect(x, y, FURY_BAR_W, FURY_BAR_H);

        switch (raid.getFuryPhase()) {
            case CHARGING: {
                sr.setColor(0.85f, 0.55f, 0.10f, 1f);
                sr.rect(x, y, FURY_BAR_W * raid.getFuryMeter(), FURY_BAR_H);
                break;
            }
            case READY: {
                // Pulsing full bar
                float pulse = 0.75f + 0.25f * (float) Math.sin(
                    System.currentTimeMillis() / 130.0);
                sr.setColor(1f * pulse, 0.72f * pulse, 0.12f * pulse, 1f);
                sr.rect(x, y, FURY_BAR_W, FURY_BAR_H);
                break;
            }
            case SELECTING: {
                // Multiplier slider: gradient zones + moving marker
                sr.setColor(0.45f, 0.35f, 0.10f, 1f);
                sr.rect(x, y, FURY_BAR_W, FURY_BAR_H);
                // Sweet-spot zone (right end = x3.0)
                sr.setColor(0.95f, 0.75f, 0.10f, 1f);
                sr.rect(x + FURY_BAR_W * 0.78f, y, FURY_BAR_W * 0.22f, FURY_BAR_H);
                // Marker position from current multiplier
                float t = (raid.getFuryMultiplier() - RaidManager.FURY_OSC_MIN)
                    / (RaidManager.FURY_OSC_MAX - RaidManager.FURY_OSC_MIN);
                sr.setColor(1f, 1f, 1f, 1f);
                sr.rect(x + FURY_BAR_W * t - 2f, y - 4f, 4f, FURY_BAR_H + 8f);
                break;
            }
            case ACTIVE: {
                // Draining gold bar
                float ratio = raid.getFuryActiveTimer() / RaidManager.FURY_ACTIVE_DURATION;
                sr.setColor(1f, 0.82f, 0.2f, 1f);
                sr.rect(x, y, FURY_BAR_W * Math.max(0f, ratio), FURY_BAR_H);
                break;
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // DRAW PASS 2 — SPRITES + TEXT (call INSIDE an open batch)
    // ══════════════════════════════════════════════════════════════

    public void drawBatch(SpriteBatch batch, BitmapFont font, BitmapFont fontSmall) {
        RaidManager rm = eventManager.getRaidManager();
        RaidState raid = rm.getActiveRaid();
        if (raid == null) return;

        List<RaidEnemy> enemies = raid.getActiveEnemies();

        // ── Enemy sprites ──
        for (int i = 0; i < enemies.size(); i++) {
            RaidEnemy e = enemies.get(i);
            float fade = (i < enemyDeathFade.length) ? enemyDeathFade[i] : 1f;
            if (!e.isAlive() && fade <= 0f) continue;

            float[] pos = getEnemyPos(i, enemies.size());
            float lungeY = 0f;
            if (i < enemyAttackAnim.length && enemyAttackAnim[i] >= 0f) {
                lungeY = -(float) Math.sin(Math.PI * enemyAttackAnim[i]) * ATTACK_LUNGE_PX;
            }

            boolean flashing = i < enemyHitFlash.length && enemyHitFlash[i] > 0f;
            if (flashing) batch.setColor(1f, 0.35f, 0.35f, fade);
            else batch.setColor(1f, 1f, 1f, fade);

            float size = e.boss ? SPRITE_SIZE * 1.4f : SPRITE_SIZE;
            // e.sprite is already a full "type_level" key (from raid_enemies.json) —
            // passing it through getTexture(type, level) would append a redundant
            // "_1" and always miss, falling back to the placeholder.
            Texture tex = e.sprite != null
                ? spriteManager.getTextureByKey(e.sprite)
                : spriteManager.getTexture(e.type, 1);
            batch.draw(tex, pos[0] - size / 2f, pos[1] - size / 2f + lungeY, size, size);

            // Name label
            batch.setColor(1f, 1f, 1f, 1f);
            fontSmall.setColor(e.boss
                ? new Color(1f, 0.5f, 0.3f, fade)
                : new Color(0.8f, 0.7f, 0.7f, fade));
            glyphLayout.setText(fontSmall, e.name);
            fontSmall.draw(batch, e.name,
                pos[0] - glyphLayout.width / 2f, pos[1] + size / 2f + 16f);
        }

        // ── Party sprites ──
        for (int i = 0; i < 4; i++) {
            if (!raid.isSlotOccupied(i)) continue;
            float fade = partyDeathFade[i];
            float[] pos = getPartyPos(i);

            float lungeY = 0f;
            if (partyAttackAnim[i] >= 0f) {
                lungeY = (float) Math.sin(Math.PI * partyAttackAnim[i]) * ATTACK_LUNGE_PX;
            }

            if (partyHitFlash[i] > 0f) batch.setColor(1f, 0.35f, 0.35f, fade);
            else if (raid.getPartyDead()[i]) batch.setColor(0.5f, 0.5f, 0.5f, fade);
            else batch.setColor(1f, 1f, 1f, fade);

            // sprite is already a full "type_level" key (Unit.getSprite() ==
            // UnitData.sprite_path, e.g. "archer_6") — same double-suffix bug
            // as the enemy sprite lookup below if run through getTexture(type, level).
            String sprite = raid.getPartySprites()[i];
            Texture tex = sprite != null
                ? spriteManager.getTextureByKey(sprite)
                : spriteManager.getTexture(raid.getPartyTypes()[i], raid.getPartyLevels()[i]);
            batch.draw(tex, pos[0] - SPRITE_SIZE / 2f,
                pos[1] - SPRITE_SIZE / 2f + lungeY, SPRITE_SIZE, SPRITE_SIZE);

            batch.setColor(1f, 1f, 1f, 1f);
        }
        batch.setColor(1f, 1f, 1f, 1f);

        // ── Floating damage numbers ──
        for (FloatingText ft : floatingTexts) {
            float progress = ft.t / FLOAT_TEXT_LIFETIME;
            float alpha = progress < 0.45f ? 1f : 1f - (progress - 0.45f) / 0.55f;
            float yOff = FLOAT_TEXT_RISE * progress;
            BitmapFont f = ft.big ? font : fontSmall;
            f.setColor(ft.color.r, ft.color.g, ft.color.b, alpha);
            glyphLayout.setText(f, ft.text);
            f.draw(batch, ft.text, ft.x - glyphLayout.width / 2f, ft.y + yOff);
        }
        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);

        // ── Fury bar label ──
        drawFuryLabel(batch, font, fontSmall, raid);

        // ── Room banner ──
        if (bannerText != null) {
            font.setColor(1f, 0.85f, 0.3f, Math.min(1f, bannerTimer));
            glyphLayout.setText(font, bannerText);
            font.draw(batch, bannerText,
                worldW() / 2f - glyphLayout.width / 2f, worldH() * 0.52f);
            font.setColor(Color.WHITE);
        }

        //if (raid.getDeathCount() > 0) {
        //    boolean can = gm.canAfford(GoldManager.COST_RAID_REVIVE_ALL); // pass gm in or reach via eventManager
        //    // draw button bg + "[Revive All +30G]" label
        //}
    }

    private void drawFuryLabel(SpriteBatch batch, BitmapFont font,
                               BitmapFont fontSmall, RaidState raid) {
        float x = getFuryBarX();
        float y = getFuryBarY();

        switch (raid.getFuryPhase()) {
            case CHARGING:
                fontSmall.setColor(0.75f, 0.65f, 0.4f, 1f);
                fontSmall.draw(batch, "FURY " + (int)(raid.getFuryMeter() * 100) + "%",
                    x + 4f, y + FURY_BAR_H + 16f);
                break;
            case READY:
                fontSmall.setColor(1f, 0.85f, 0.3f, 1f);
                fontSmall.draw(batch, "FURY READY — TAP!", x + 4f, y + FURY_BAR_H + 16f);
                break;
            case SELECTING:
                font.setColor(1f, 0.9f, 0.4f, 1f);
                font.draw(batch, String.format("x%.1f", raid.getFuryMultiplier()),
                    x + FURY_BAR_W + 12f, y + FURY_BAR_H);
                fontSmall.setColor(1f, 0.85f, 0.3f, 1f);
                fontSmall.draw(batch, "TAP TO LOCK!", x + 4f, y + FURY_BAR_H + 16f);
                break;
            case ACTIVE:
                fontSmall.setColor(1f, 0.85f, 0.3f, 1f);
                fontSmall.draw(batch, String.format("FURY x%.1f ACTIVE!",
                    raid.getFuryMultiplier()), x + 4f, y + FURY_BAR_H + 16f);
                break;
        }
        font.setColor(Color.WHITE);
        fontSmall.setColor(Color.WHITE);
    }
}
