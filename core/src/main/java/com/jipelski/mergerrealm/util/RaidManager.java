package com.jipelski.mergerrealm.util;

import com.badlogic.gdx.Gdx;
import com.jipelski.mergerrealm.model.GameObject;
import com.jipelski.mergerrealm.model.Item;
import com.jipelski.mergerrealm.model.RaidState;
import com.jipelski.mergerrealm.model.RaidState.RaidEnemy;
import com.jipelski.mergerrealm.model.Unit;

import com.jipelski.mergerrealm.util.EnchantedSetManager;
import com.jipelski.mergerrealm.model.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages raid combat, node progression, and reward delivery.
 *
 * Flow:
 *   1. Player selects chapter → node → forms 4-unit party
 *   2. Units removed from grid, placed into RaidState
 *   3. Real-time auto-combat: units attack enemies on timers
 *   4. Player manually uses potions / Phoenix Feathers
 *   5. Clear all rooms → node complete → rewards + star rating
 *   6. Units returned to grid with current HP
 *
 * Combat:
 *   - Each unit attacks the first alive enemy every attackSpeed seconds
 *   - Each enemy attacks the party every attackSpeed seconds
 *   - Party damage is distributed: front[50%/30%] back[10%/10%]
 *   - Dead party members skip; damage redistributes to alive members
 */
public class RaidManager {

    private static final String TAG = "RaidManager";

    private final EventManager eventManager;

    // ── Data ──
    private Map<String, Object> chapterData;   // parsed from raid_chapters.json
    private Map<String, Object> enemyData;     // parsed from raid_enemies.json

    // ── Active raid ──
    private RaidState activeRaid = null;

    // ── Node data for current raid ──
    private List<Map<String, Object>> currentRooms;
    private Map<String, Object> currentNode;

    // ── Completion tracking ──
    // chapterId:nodeId → best star rating
    private Map<String, Integer> completionMap = new HashMap<>();

    // ── War Trophies ──
    private int warTrophies = 0;

    // ── Boss Tokens ──
    private int bossTokens = 0;

    // ── Combat log ──
    private final List<String> combatLog = new ArrayList<>();
    private static final int MAX_LOG_LINES = 100;

    //  ── Enchanted Sets ──

    private EnchantedSetManager.ActiveSetBonuses setBonuses;

    private float setHealTimer = 0f;

    // ── Pacing ──
    public static final float ENEMY_SPEED_MULT = 1.5f;      // slower enemy attacks
    public static final float TRANSITION_DURATION = 2.5f;   // pause between rooms

    // ── Fury tuning ──
    public static final float FURY_GAIN_PER_HIT = 0.05f;    // party lands a hit
    public static final float FURY_GAIN_PER_TAKEN = 0.03f;  // party takes damage
    public static final float FURY_OSC_MIN = 1.5f;
    public static final float FURY_OSC_MAX = 3.0f;
    public static final float FURY_OSC_PERIOD = 1.2f;       // full sweep seconds
    public static final float FURY_ACTIVE_DURATION = 3.0f;  // crit window

    private float furyOscTimer = 0f;

    /**
     * Handles taps on the Fury button.
     *   READY     → start the oscillating multiplier
     *   SELECTING → lock the multiplier, open the 3s crit window
     */
    public void tapFury() {
        if (!isRaidActive()) return;
        RaidState raid = activeRaid;

        switch (raid.getFuryPhase()) {
            case READY:
                furyOscTimer = 0f;
                raid.setFuryPhase(RaidState.FuryPhase.SELECTING);
                break;
            case SELECTING:
                raid.setFuryPhase(RaidState.FuryPhase.ACTIVE);
                raid.setFuryActiveTimer(FURY_ACTIVE_DURATION);
                raid.setFuryMeter(0f);
                RaidState.CombatEvent ev = new RaidState.CombatEvent();
                ev.kind = "fury";
                ev.damage = Math.round(raid.getFuryMultiplier() * 10f);
                raid.pushEvent(ev);
                addLog("FURY x" + String.format("%.1f", raid.getFuryMultiplier())
                    + " — strike now!");
                break;
            default:
                break; // CHARGING and ACTIVE ignore taps
        }
    }

    private void gainFury(float amount) {
        if (activeRaid.getFuryPhase() != RaidState.FuryPhase.CHARGING) return;
        float m = Math.min(1f, activeRaid.getFuryMeter() + amount);
        activeRaid.setFuryMeter(m);
        if (m >= 1f) {
            activeRaid.setFuryPhase(RaidState.FuryPhase.READY);
            addLog("Fury is ready!");
        }
    }

    public RaidManager(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    // ══════════════════════════════════════════════════════════════
    // DATA LOADING
    // ══════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    public void loadData(String chaptersJson, String enemiesJson) {
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            chapterData = gson.fromJson(chaptersJson, Map.class);
            enemyData = gson.fromJson(enemiesJson, Map.class);
            Gdx.app.log(TAG, "Raid data loaded");
        } catch (Exception e) {
            Gdx.app.error(TAG, "Failed to load raid data", e);
            chapterData = new HashMap<>();
            enemyData = new HashMap<>();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // STATE
    // ══════════════════════════════════════════════════════════════

    public boolean isRaidActive() { return activeRaid != null && !activeRaid.isCompleted() && !activeRaid.isFailed(); }
    public RaidState getActiveRaid() { return activeRaid; }
    public List<String> getCombatLog() { return combatLog; }
    public int getWarTrophies() { return warTrophies; }
    public void setWarTrophies(int t) { this.warTrophies = t; }
    public int getBossTokens() { return bossTokens; }
    public void setBossTokens(int t) { this.bossTokens = t; }
    public Map<String, Integer> getCompletionMap() { return completionMap; }
    public void setCompletionMap(Map<String, Integer> map) { this.completionMap = map; }

    /**
     * Restores an in-progress raid after an app relaunch (e.g. the process
     * was killed by the OS mid-raid, rather than a normal background/close).
     * Reconstructs the fields startRaid() derives from static content
     * (currentNode/currentRooms from chapterId+nodeId, setBonuses from the
     * party's still-equipped gear — equipment can't change while a unit is
     * off-grid mid-raid, so this recomputes identically to what was active
     * at raid start) around the persisted RaidState.
     *
     * combatLog / furyOscTimer / setHealTimer are intentionally NOT restored
     * — they're cosmetic/animation timing, not gameplay state that could be
     * lost. Worst case: the recent-log panel starts empty and a Holy
     * Radiance heal tick is delayed by up to one interval after resuming.
     *
     * No-op if there's no saved raid, or the raid was already resolved
     * (RaidPanel's own isCompleted()/isFailed() auto-transition to RESULTS
     * handles that case using only fields already on RaidState).
     */
    @SuppressWarnings("unchecked")
    public void resumeFromSave(RaidState restored) {
        if (restored == null) return;

        activeRaid = restored;

        String chapterId = activeRaid.getChapterId();
        String nodeId = activeRaid.getNodeId();
        for (Map<String, Object> node : getNodes(chapterId)) {
            if (nodeId.equals(node.get("id"))) {
                currentNode = node;
                currentRooms = (List<Map<String, Object>>) node.get("rooms");
                break;
            }
        }

        setBonuses = eventManager.getEnchantedSetManager()
            .checkSetBonuses(activeRaid.getPartyUnitIds());

        combatLog.clear();
        addLog("Raid resumed");

        Gdx.app.log(TAG, "Resumed active raid: " + chapterId + "/" + nodeId
            + " room " + (activeRaid.getCurrentRoomIndex() + 1));
    }

    // ══════════════════════════════════════════════════════════════
    // CHAPTER / NODE ACCESS
    // ══════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getChapters() {
        if (chapterData == null) return new ArrayList<>();
        Object chapters = chapterData.get("chapters");
        if (chapters instanceof List) return (List<Map<String, Object>>) chapters;
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getChapter(String chapterId) {
        for (Map<String, Object> ch : getChapters()) {
            if (chapterId.equals(ch.get("id"))) return ch;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getNodes(String chapterId) {
        Map<String, Object> chapter = getChapter(chapterId);
        if (chapter == null) return new ArrayList<>();
        Object nodes = chapter.get("nodes");
        if (nodes instanceof List) return (List<Map<String, Object>>) nodes;
        return new ArrayList<>();
    }

    public boolean isChapterUnlocked(String chapterId) {
        Map<String, Object> ch = getChapter(chapterId);
        if (ch == null) return false;
        int unlock = toInt(ch, "unlockLevel");
        return eventManager.getBattleFieldManager().getLevel() >= unlock;
    }

    public boolean isNodeUnlocked(String chapterId, String nodeId) {
        List<Map<String, Object>> nodes = getNodes(chapterId);
        for (Map<String, Object> node : nodes) {
            if (!nodeId.equals(node.get("id"))) continue;
            String type = (String) node.get("type");
            int order = toInt(node, "order");

            if ("main".equals(type) && order == 1) return true;
            if ("side".equals(type)) {
                // Side nodes unlock when the main node at the same order is cleared
                String mainKey = findMainNodeAtOrder(chapterId, order);
                return mainKey != null && completionMap.containsKey(chapterId + ":" + mainKey);
            }
            // Main nodes at order N require main at order N-1 cleared
            String prevMain = findMainNodeAtOrder(chapterId, order - 1);
            return prevMain != null && completionMap.containsKey(chapterId + ":" + prevMain);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private String findMainNodeAtOrder(String chapterId, int order) {
        for (Map<String, Object> node : getNodes(chapterId)) {
            if ("main".equals(node.get("type")) && toInt(node, "order") == order) {
                return (String) node.get("id");
            }
        }
        return null;
    }

    public int getBestStarRating(String chapterId, String nodeId) {
        return completionMap.getOrDefault(chapterId + ":" + nodeId, 0);
    }

    // ══════════════════════════════════════════════════════════════
    // START RAID
    // ══════════════════════════════════════════════════════════════

    /**
     * Starts a raid with the given party.
     * Units are removed from the grid and stored in RaidState.
     *
     * @param chapterId  which chapter
     * @param nodeId     which node
     * @param unitIds    array of 4 unit IDs (nulls for empty slots)
     * @return true if raid started successfully
     */
    @SuppressWarnings("unchecked")
    public boolean startRaid(String chapterId, String nodeId, String[] unitIds) {
        if (isRaidActive()) {
            Gdx.app.log(TAG, "Raid already active");
            return false;
        }

        // Validate at least 1 unit
        int count = 0;
        for (String id : unitIds) { if (id != null) count++; }
        if (count == 0) {
            Gdx.app.log(TAG, "Need at least 1 unit");
            return false;
        }

        // Find node data
        Map<String, Object> node = null;
        for (Map<String, Object> n : getNodes(chapterId)) {
            if (nodeId.equals(n.get("id"))) { node = n; break; }
        }
        if (node == null) {
            Gdx.app.log(TAG, "Node not found: " + nodeId);
            return false;
        }

        currentNode = node;
        currentRooms = (List<Map<String, Object>>) node.get("rooms");
        if (currentRooms == null || currentRooms.isEmpty()) {
            Gdx.app.log(TAG, "Node has no rooms");
            return false;
        }

        // Create raid state
        activeRaid = new RaidState();
        activeRaid.setChapterId(chapterId);
        activeRaid.setNodeId(nodeId);
        activeRaid.setCurrentRoomIndex(0);

        furyOscTimer = 0f;
        activeRaid.setFuryPhase(RaidState.FuryPhase.CHARGING);
        activeRaid.setFuryMeter(0f);
        activeRaid.setInTransition(false);

        // Reset Heal Timer
        setHealTimer = 0f;

        // Set up party — remove units from grid
        GridObjectManager gom = eventManager.getGRID_OBJECT_MANAGER();
        for (int i = 0; i < 4; i++) {
            if (unitIds[i] == null) continue;
            GameObject obj = gom.getObject(unitIds[i]);
            if (!(obj instanceof Unit)) continue;

            Unit unit = (Unit) obj;
            int effectiveDmg = eventManager.getEffectiveDamage(unitIds[i]);
            int effectiveHp = eventManager.getEffectiveMaxHp(unitIds[i]);

            activeRaid.setPartyMember(i, unitIds[i], unit.getType(), unit.getLvl(),
                effectiveHp, unit.getHp(), effectiveDmg, unit.getSprite());

            // Remove from grid (but DON'T unequip items)
            eventManager.removeUnitForRaid(unitIds[i]);
        }

        EnchantedSetManager esm = eventManager.getEnchantedSetManager();
        setBonuses = esm.checkSetBonuses(unitIds);
        if (setBonuses.hasAnyBonus()) {
            addLog("Set bonuses active!");
            if (setBonuses.dragonscale) addLog("  Dragonscale: +20% party DMG, +10% party HP");
            if (setBonuses.shadowsteel) addLog("  Shadowsteel: +30% holder DMG, -15% incoming DMG");
            if (setBonuses.holyRadiance) addLog("  Holy Radiance: party heals 5% HP every 10s");
            if (setBonuses.infernal) addLog("  Infernal: 25% reflect, +15% holder DMG");
        }

        // Apply party HP bonus from Dragonscale at raid start:
        if (setBonuses.partyHpBonus > 0) {
            for (int i = 0; i < 4; i++) {
                if (activeRaid.isSlotOccupied(i)) {
                    int boosted = Math.round(activeRaid.getPartyMaxHp()[i]
                        * (1f + setBonuses.partyHpBonus));
                    activeRaid.getPartyMaxHp()[i] = boosted;
                    activeRaid.getPartyCurrentHp()[i] = Math.min(
                        activeRaid.getPartyCurrentHp()[i], boosted);
                }
            }
        }

        // Prince Outfit HP bonus — same "locked in at formation" pattern as
        // the Dragonscale bonus above (there's no live per-tick max-HP
        // recompute anywhere in raid combat; the whole party's loadout
        // commits the moment the raid starts).
        float outfitHpMultiplier = eventManager.getOutfitManager().getRaidHpMultiplier();
        if (outfitHpMultiplier != 1f) {
            for (int i = 0; i < 4; i++) {
                if (activeRaid.isSlotOccupied(i)) {
                    int boosted = Math.round(activeRaid.getPartyMaxHp()[i] * outfitHpMultiplier);
                    activeRaid.getPartyMaxHp()[i] = boosted;
                    activeRaid.getPartyCurrentHp()[i] = Math.min(
                        activeRaid.getPartyCurrentHp()[i], boosted);
                }
            }
        }

        // Load first room enemies
        loadRoom(0);

        combatLog.clear();
        addLog("Raid started: " + node.get("name"));

        Gdx.app.log(TAG, "Raid started: " + chapterId + " / " + nodeId
            + " with " + count + " units");
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    // COMBAT UPDATE (called every frame)
    // ══════════════════════════════════════════════════════════════

    /**
     * Updates raid combat. Call every frame with delta time.
     */
    public void update(float delta) {
        if (!isRaidActive()) return;

        // Party wipe check
        if (activeRaid.isPartyWiped()) {
            activeRaid.setFailed(true);
            addLog("Party wiped! Raid failed.");
            return;
        }

        // ── Fury phase updates ──
        switch (activeRaid.getFuryPhase()) {
            case SELECTING: {
                furyOscTimer += delta;
                float phase = (furyOscTimer % FURY_OSC_PERIOD) / FURY_OSC_PERIOD;
                float tri = phase < 0.5f ? phase * 2f : 2f - phase * 2f; // triangle wave
                activeRaid.setFuryMultiplier(
                    FURY_OSC_MIN + tri * (FURY_OSC_MAX - FURY_OSC_MIN));
                break;
            }
            case ACTIVE: {
                activeRaid.setFuryActiveTimer(activeRaid.getFuryActiveTimer() - delta);
                if (activeRaid.getFuryActiveTimer() <= 0f) {
                    activeRaid.setFuryPhase(RaidState.FuryPhase.CHARGING);
                    activeRaid.setFuryMeter(0f);
                }
                break;
            }
            default: break;
        }

        // ── Room transition pause ──
        if (activeRaid.isInTransition()) {
            activeRaid.setTransitionTimer(activeRaid.getTransitionTimer() - delta);
            if (activeRaid.getTransitionTimer() <= 0f) {
                activeRaid.setInTransition(false);
                int nextRoom = activeRaid.getCurrentRoomIndex() + 1;
                if (nextRoom >= currentRooms.size()) {
                    completeNode();
                } else {
                    loadRoom(nextRoom);
                    RaidState.CombatEvent ev = new RaidState.CombatEvent();
                    ev.kind = "room_start";
                    ev.actorIdx = nextRoom;
                    activeRaid.pushEvent(ev);
                }
            }
            return; // no combat during transition
        }

        // Room cleared → begin transition
        if (activeRaid.isRoomCleared()) {
            activeRaid.setInTransition(true);
            activeRaid.setTransitionTimer(TRANSITION_DURATION);
            RaidState.CombatEvent ev = new RaidState.CombatEvent();
            ev.kind = "room_clear";
            activeRaid.pushEvent(ev);
            return;
        }

        // ── Unit attacks ──
        for (int i = 0; i < 4; i++) {
            if (!activeRaid.isSlotAlive(i)) continue;
            float[] timers = activeRaid.getPartyAttackTimer();
            float[] speeds = activeRaid.getPartyAttackSpeed();
            timers[i] += delta;
            if (timers[i] >= speeds[i]) {
                timers[i] -= speeds[i];
                unitAttack(i);
            }
        }

        // ── Enemy attacks ──
        for (int e = 0; e < activeRaid.getActiveEnemies().size(); e++) {
            RaidEnemy enemy = activeRaid.getActiveEnemies().get(e);
            if (!enemy.isAlive()) continue;
            enemy.attackTimer += delta;
            if (enemy.attackTimer >= enemy.attackSpeed) {
                enemy.attackTimer -= enemy.attackSpeed;
                enemyAttack(e, enemy);
            }
        }

        // ── Set + trait periodic heals (keep your existing Holy Radiance /
        //    high_priest logic here, but emit "heal" events for the numbers) ──
        if (setBonuses != null && setBonuses.partyHealPercent > 0
            && setBonuses.healIntervalSeconds > 0) {
            setHealTimer += delta;
            if (setHealTimer >= setBonuses.healIntervalSeconds) {
                setHealTimer -= setBonuses.healIntervalSeconds;
                // Heal all alive party members
                int[] hp = activeRaid.getPartyCurrentHp();
                int[] maxHp = activeRaid.getPartyMaxHp();
                for (int i = 0; i < 4; i++) {
                    if (activeRaid.isSlotAlive(i)) {
                        int heal = Math.round(maxHp[i] * setBonuses.partyHealPercent);
                        hp[i] = Math.min(maxHp[i], hp[i] + heal);
                    }
                }
                addLog("Holy Radiance heals the party!");
            }
        }
    }

    private void unitAttack(int slot) {
        // Find first alive enemy + its index
        RaidEnemy target = null;
        int targetIdx = -1;
        java.util.List<RaidEnemy> enemies = activeRaid.getActiveEnemies();
        for (int i = 0; i < enemies.size(); i++) {
            if (enemies.get(i).isAlive()) { target = enemies.get(i); targetIdx = i; break; }
        }
        if (target == null) return;

        int dmg = activeRaid.getPartyDamage()[slot];

        // Legendary trait: shadowbow double attack
        String unitType = activeRaid.getPartyTypes()[slot];
        if ("shadowbow".equals(unitType)) dmg *= 2;

        // Enchanted set bonuses (keep your existing code)
        if (setBonuses != null) {
            if (setBonuses.partyDamageBonus > 0)
                dmg = Math.round(dmg * (1f + setBonuses.partyDamageBonus));
            if (setBonuses.holderDamageBonus[slot] > 0)
                dmg = Math.round(dmg * (1f + setBonuses.holderDamageBonus[slot]));
        }

        // Prince Outfit damage bonus — live, read fresh every attack (unlike
        // the HP bonus above, this joins the existing chain of per-attack
        // multipliers instead of being locked in at formation), so an
        // outfit swap mid-raid takes effect on the very next hit.
        dmg = Math.round(dmg * eventManager.getOutfitManager().getRaidDamageMultiplier());

        // ── Fury crit window ──
        boolean crit = false;
        if (activeRaid.getFuryPhase() == RaidState.FuryPhase.ACTIVE) {
            dmg = Math.round(dmg * activeRaid.getFuryMultiplier());
            crit = true;
        }

        target.hp -= dmg;
        if (target.hp < 0) target.hp = 0;

        // Fury charges from landed hits
        gainFury(FURY_GAIN_PER_HIT);

        // Emit presentation event
        RaidState.CombatEvent ev = new RaidState.CombatEvent();
        ev.kind = "attack";
        ev.actorIsParty = true;  ev.actorIdx = slot;
        ev.targetIsParty = false; ev.targetIdx = targetIdx;
        ev.damage = dmg;         ev.crit = crit;
        activeRaid.pushEvent(ev);

        if (target.hp <= 0) {
            addLog(TextUtil.capitalize(activeRaid.getPartyTypes()[slot])
                + " defeated " + target.name + (crit ? " with a CRIT!" : "!"));
        }
    }

    private void enemyAttack(int enemyIdx, RaidEnemy enemy) {
        int totalDmg = enemy.damage;

        // Legendary trait: royal_knight damage reduction
        for (int i = 0; i < 4; i++) {
            if (activeRaid.isSlotAlive(i)
                && "royal_knight".equals(activeRaid.getPartyTypes()[i])) {
                totalDmg = (int)(totalDmg * 0.8f);
                break;
            }
        }

        // Enchanted set: Shadowsteel party damage reduction
        if (setBonuses != null && setBonuses.partyDamageReduction > 0) {
            totalDmg = Math.round(totalDmg * (1f - setBonuses.partyDamageReduction));
        }

        // Distribute across alive slots (keep your redistribution logic)
        int[] hp = activeRaid.getPartyCurrentHp();
        boolean[] dead = activeRaid.getPartyDead();

        float totalShare = 0f;
        float[] shares = new float[4];
        for (int i = 0; i < 4; i++) {
            if (activeRaid.isSlotAlive(i)) {
                shares[i] = RaidState.DAMAGE_DISTRIBUTION[i];
                totalShare += shares[i];
            }
        }
        if (totalShare > 0 && totalShare < 0.99f) {
            float scale = 1f / totalShare;
            for (int i = 0; i < 4; i++) shares[i] *= scale;
        }

        for (int i = 0; i < 4; i++) {
            if (!activeRaid.isSlotAlive(i)) continue;
            int dmg = Math.round(totalDmg * shares[i]);
            if (dmg <= 0) continue;

            // Ironclad reflect (keep your existing trait code)
            if ("ironclad".equals(activeRaid.getPartyTypes()[i])) {
                enemy.hp -= (int)(dmg * 0.10f);
            }
            // Infernal set reflect
            if (setBonuses != null && setBonuses.reflectPercent > 0) {
                enemy.hp -= Math.round(dmg * setBonuses.reflectPercent);
                if (enemy.hp < 0) enemy.hp = 0;
            }

            hp[i] -= dmg;

            // Fury charges from damage taken
            gainFury(FURY_GAIN_PER_TAKEN);

            // Emit presentation event (one per damaged slot)
            RaidState.CombatEvent ev = new RaidState.CombatEvent();
            ev.kind = "attack";
            ev.actorIsParty = false;  ev.actorIdx = enemyIdx;
            ev.targetIsParty = true;  ev.targetIdx = i;
            ev.damage = dmg;
            activeRaid.pushEvent(ev);

            if (hp[i] <= 0) {
                hp[i] = 0;
                dead[i] = true;
                activeRaid.setDeathCount(activeRaid.getDeathCount() + 1);
                addLog(TextUtil.capitalize(activeRaid.getPartyTypes()[i]) + " has fallen!");

                // eternal_phoenix auto-revive (keep your existing code)
                if ("eternal_phoenix".equals(activeRaid.getPartyTypes()[i])) {
                    dead[i] = false;
                    hp[i] = activeRaid.getPartyMaxHp()[i] / 2;
                    activeRaid.setDeathCount(activeRaid.getDeathCount() - 1);
                    addLog("Eternal Phoenix rises from the ashes!");
                    activeRaid.getPartyTypes()[i] = "phoenix_revived";
                }
            }
        }

        // high_priest passive heal (keep existing; optionally emit "heal" events)

    }

    // ══════════════════════════════════════════════════════════════
    // ROOM LOADING
    // ══════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private void loadRoom(int roomIndex) {
        if (roomIndex >= currentRooms.size()) return;

        Map<String, Object> room = currentRooms.get(roomIndex);
        List<String> enemyKeys = (List<String>) room.get("enemies");
        List<RaidEnemy> enemies = new ArrayList<>();

        for (String key : enemyKeys) {
            Map<String, Object> eData = getEnemyData(key);
            if (eData == null) continue;

            String name = (String) eData.get("name");
            int hp = toInt(eData, "hp");
            int dmg = toInt(eData, "damage");
            float speed = toFloat(eData, "attackSpeed");
            boolean boss = Boolean.TRUE.equals(eData.get("boss"));

            String sprite = (String) eData.get("sprite");
            enemies.add(new RaidEnemy(key, name, sprite, hp, dmg,
                speed * ENEMY_SPEED_MULT, boss));
        }

        activeRaid.setCurrentRoomIndex(roomIndex);
        activeRaid.setActiveEnemies(enemies);

        addLog("Room " + (roomIndex + 1) + "/" + currentRooms.size()
            + " — " + enemies.size() + " enemies");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getEnemyData(String key) {
        Object data = enemyData.get(key);
        if (data instanceof Map) return (Map<String, Object>) data;
        return null;
    }

    // ══════════════════════════════════════════════════════════════
    // NODE COMPLETION
    // ══════════════════════════════════════════════════════════════

    private void completeNode() {
        activeRaid.setCompleted(true);
        int stars = activeRaid.getStarRating();

        String key = activeRaid.getChapterId() + ":" + activeRaid.getNodeId();
        int bestPrev = completionMap.getOrDefault(key, 0);
        boolean firstClear = bestPrev == 0;

        if (stars > bestPrev) {
            completionMap.put(key, stars);
        }

        // Award trophies
        int trophies;
        if (firstClear) {
            trophies = toInt(currentNode, "firstClearTrophies");
        } else {
            trophies = toInt(currentNode, "repeatTrophies");
        }

        // Star bonus on repeats
        if (!firstClear) {
            float bonus = 1f + (stars - 1) * 0.5f; // 1★=1x, 2★=1.5x, 3★=2x
            trophies = Math.round(trophies * bonus);
        }

        activeRaid.addTrophies(trophies);
        warTrophies += trophies;

        // Boss token
        int tokenReward = toInt(currentNode, "bossTokenReward");
        if (tokenReward > 0) {
            bossTokens += tokenReward;
            activeRaid.setBossTokens(tokenReward);
        }

        // Enchanted equipment drop
        double enchChance = toDouble(currentNode, "enchantedDropChance");
        if (enchChance > 0 && Math.random() < enchChance) {
            String set = (String) currentNode.get("enchantedSet");
            EnchantedSetManager esm = eventManager.getEnchantedSetManager();
            Item enchantedItem = esm.createRandomPiece(set);
            if (enchantedItem != null) {
                eventManager.getInventory().addItem(enchantedItem);
                activeRaid.setEnchantedDrop(enchantedItem.getName());
                activeRaid.getItemsFound().add(enchantedItem.getId());
                addLog("Enchanted drop: " + enchantedItem.getName()
                    + " (" + EnchantedSetManager.getSetDisplayName(set) + " set)");
            }
        }

        // First-clear Gold
        GoldManager gm = eventManager.getGoldManager();
        String nodeType = (String) currentNode.get("type");
        if (firstClear) {
            gm.onRaidNodeFirstClear(activeRaid.getChapterId(),
                activeRaid.getNodeId(), nodeType != null ? nodeType : "main");
        }

        // First 3-star Gold
        if (stars == 3 && bestPrev < 3) {
            gm.onRaidFirst3Star(activeRaid.getChapterId(), activeRaid.getNodeId());
        }

        addLog("NODE COMPLETE! " + stars + "★ — +" + trophies + " War Trophies"
            + (firstClear ? " (First Clear!)" : " (Repeat)"));

        Gdx.app.log(TAG, "Node complete: " + key + " — " + stars + "★, +"
            + trophies + " trophies");
    }

    // ══════════════════════════════════════════════════════════════
    // MANUAL ACTIONS (potion, phoenix feather)
    // ══════════════════════════════════════════════════════════════

    /**
     * Uses a potion on a party member during raid.
     * Consumes the potion from inventory.
     */
    public boolean usePotion(int slot, String potionId) {
        if (!isRaidActive()) return false;

        // Validate + apply BEFORE consuming the item — previously this consumed
        // the potion first, so a rejected heal (e.g. target already at full HP)
        // still silently burned it with no effect. RaidPanel already pre-checks
        // this same condition before calling, so this is currently a latent
        // guard rather than a reachable-through-the-UI bug.
        if (!activeRaid.usePotion(slot)) return false;

        Inventory inv = eventManager.getInventory();
        Item potion = inv.useConsumable(potionId);
        addLog("Used " + (potion != null ? potion.getName() : "potion") + " on "
            + TextUtil.capitalize(activeRaid.getPartyTypes()[slot]));
        return true;
    }

    /**
     * Uses a Phoenix Feather to revive a fallen party member.
     */
    public boolean usePhoenixFeather(int slot, String featherId) {
        if (!isRaidActive()) return false;

        // Validate + apply BEFORE consuming the item — see usePotion above.
        if (!activeRaid.usePhoenixFeather(slot)) return false;

        Inventory inv = eventManager.getInventory();
        Item feather = inv.useConsumable(featherId);
        addLog("Phoenix Feather revives "
            + TextUtil.capitalize(activeRaid.getPartyTypes()[slot]) + "!");
        return true;
    }

    // ══════════════════════════════════════════════════════════════
    // END RAID — return units to grid
    // ══════════════════════════════════════════════════════════════

    /**
     * Ends the raid and returns surviving units to the grid.
     * Call after the player dismisses the results screen.
     */
    public void endRaid() {
        if (activeRaid == null) return;

        String[] unitIds = activeRaid.getPartyUnitIds();
        String[] types = activeRaid.getPartyTypes();
        int[] levels = activeRaid.getPartyLevels();
        int[] currentHp = activeRaid.getPartyCurrentHp();
        boolean[] dead = activeRaid.getPartyDead();

        for (int i = 0; i < 4; i++) {
            if (unitIds[i] == null) continue;

            if (dead[i]) {
                // Dead unit — check if it should be lost permanently
                // (Non-max units lv3-6 are lost; max-level survives at 1HP)
                // For simplicity, return all with 1 HP
                eventManager.respawnUnitWithId(unitIds[i], types[i], levels[i], 1);
                addLog(TextUtil.capitalize(types[i]) + " returned barely alive");
            } else {
                // Alive — return with current HP
                eventManager.respawnUnitWithId(unitIds[i], types[i], levels[i], currentHp[i]);
            }
        }

        activeRaid = null;
        currentNode = null;
        currentRooms = null;

        Gdx.app.log(TAG, "Raid ended — units returned to grid");
    }

    /**
     * Abandons the raid. Marks it failed so the results screen can display;
     * units are returned to the grid later when the player taps Continue
     * (which calls endRaid()).
     */
    public void abandonRaid() {
        if (activeRaid == null) return;
        addLog("Raid abandoned!");
        activeRaid.setFailed(true);
    }

    // ══════════════════════════════════════════════════════════════
    // COMBAT LOG
    // ══════════════════════════════════════════════════════════════

    private void addLog(String text) {
        combatLog.add(text);
        if (combatLog.size() > MAX_LOG_LINES) {
            combatLog.remove(0);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    private int toInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Double) return ((Double) val).intValue();
        if (val instanceof Integer) return (Integer) val;
        if (val instanceof Number) return ((Number) val).intValue();
        return 0;
    }

    private float toFloat(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Double) return ((Double) val).floatValue();
        if (val instanceof Float) return (Float) val;
        if (val instanceof Number) return ((Number) val).floatValue();
        return 1.0f;
    }

    private double toDouble(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Double) return (Double) val;
        if (val instanceof Number) return ((Number) val).doubleValue();
        return 0.0;
    }

}
