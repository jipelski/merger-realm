package com.jipelski.mergerrealm.util;

import com.badlogic.gdx.Gdx;
import com.jipelski.mergerrealm.model.GameObject;
import com.jipelski.mergerrealm.model.Item;
import com.jipelski.mergerrealm.model.RaidState;
import com.jipelski.mergerrealm.model.RaidState.RaidEnemy;
import com.jipelski.mergerrealm.model.Unit;

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
                effectiveHp, unit.getHp(), effectiveDmg);

            // Remove from grid (but DON'T unequip items)
            eventManager.removeUnitForRaid(unitIds[i]);
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

        // Check for party wipe
        if (activeRaid.isPartyWiped()) {
            activeRaid.setFailed(true);
            addLog("Party wiped! Raid failed.");
            Gdx.app.log(TAG, "Raid failed — party wiped");
            return;
        }

        // Check if room is cleared
        if (activeRaid.isRoomCleared()) {
            int nextRoom = activeRaid.getCurrentRoomIndex() + 1;
            if (nextRoom >= currentRooms.size()) {
                // Node complete!
                completeNode();
                return;
            }
            loadRoom(nextRoom);
            addLog("Room " + (nextRoom + 1) + " — enemies incoming!");
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
        for (RaidEnemy enemy : activeRaid.getActiveEnemies()) {
            if (!enemy.isAlive()) continue;

            enemy.attackTimer += delta;
            if (enemy.attackTimer >= enemy.attackSpeed) {
                enemy.attackTimer -= enemy.attackSpeed;
                enemyAttack(enemy);
            }
        }
    }

    private void unitAttack(int slot) {
        // Find first alive enemy
        RaidEnemy target = null;
        for (RaidEnemy e : activeRaid.getActiveEnemies()) {
            if (e.isAlive()) { target = e; break; }
        }
        if (target == null) return;

        int dmg = activeRaid.getPartyDamage()[slot];

        // Legendary trait: double_attack_raid (shadowbow)
        String unitType = activeRaid.getPartyTypes()[slot];
        if ("shadowbow".equals(unitType)) {
            dmg *= 2;
        }

        target.hp -= dmg;
        if (target.hp <= 0) {
            target.hp = 0;
            addLog(capitalize(activeRaid.getPartyTypes()[slot]) + " defeated " + target.name + "!");
        }
    }

    private void enemyAttack(RaidEnemy enemy) {
        int totalDmg = enemy.damage;

        // Legendary trait: reduce_monster_damage (royal_knight)
        for (int i = 0; i < 4; i++) {
            if (activeRaid.isSlotAlive(i) && "royal_knight".equals(activeRaid.getPartyTypes()[i])) {
                totalDmg = (int)(totalDmg * 0.8f);
                break;
            }
        }

        // Distribute damage across party positions
        int[] hp = activeRaid.getPartyCurrentHp();
        boolean[] dead = activeRaid.getPartyDead();

        // Calculate alive distribution
        float totalShare = 0f;
        float[] shares = new float[4];
        for (int i = 0; i < 4; i++) {
            if (activeRaid.isSlotAlive(i)) {
                shares[i] = RaidState.DAMAGE_DISTRIBUTION[i];
                totalShare += shares[i];
            }
        }

        // Redistribute dead shares proportionally
        if (totalShare > 0 && totalShare < 0.99f) {
            float scale = 1f / totalShare;
            for (int i = 0; i < 4; i++) shares[i] *= scale;
        }

        for (int i = 0; i < 4; i++) {
            if (!activeRaid.isSlotAlive(i)) continue;
            int dmg = Math.round(totalDmg * shares[i]);
            if (dmg <= 0) continue;

            // Legendary trait: reflect_damage_raid (ironclad)
            if ("ironclad".equals(activeRaid.getPartyTypes()[i])) {
                int reflected = (int)(dmg * 0.10f);
                enemy.hp -= reflected;
            }

            hp[i] -= dmg;
            if (hp[i] <= 0) {
                hp[i] = 0;
                dead[i] = true;
                activeRaid.setDeathCount(activeRaid.getDeathCount() + 1);
                addLog(capitalize(activeRaid.getPartyTypes()[i]) + " has fallen!");

                // Legendary trait: auto_revive_raid (eternal_phoenix)
                if ("eternal_phoenix".equals(activeRaid.getPartyTypes()[i])) {
                    dead[i] = false;
                    hp[i] = activeRaid.getPartyMaxHp()[i] / 2;
                    activeRaid.setDeathCount(activeRaid.getDeathCount() - 1);
                    addLog("Eternal Phoenix rises from the ashes!");
                    // Only auto-revive once — change type to prevent re-trigger
                    activeRaid.getPartyTypes()[i] = "phoenix_revived";
                }
            }
        }

        // Legendary trait: heal_party_raid (high_priest) — passive healing
        for (int i = 0; i < 4; i++) {
            if (activeRaid.isSlotAlive(i) && "high_priest".equals(activeRaid.getPartyTypes()[i])) {
                for (int j = 0; j < 4; j++) {
                    if (activeRaid.isSlotAlive(j) && hp[j] < activeRaid.getPartyMaxHp()[j]) {
                        int heal = (int)(activeRaid.getPartyMaxHp()[j] * 0.02f);
                        hp[j] = Math.min(activeRaid.getPartyMaxHp()[j], hp[j] + heal);
                    }
                }
                break; // only one priest heal per tick
            }
        }
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

            enemies.add(new RaidEnemy(key, name, hp, dmg, speed, boss));
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
            // Create enchanted item — placeholder for now
            String set = (String) currentNode.get("enchantedSet");
            activeRaid.setEnchantedDrop(set);
            addLog("Enchanted equipment found! (" + capitalize(set) + " set)");
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

        Inventory inv = eventManager.getInventory();
        Item potion = inv.useConsumable(potionId);
        if (potion == null) return false;

        if (activeRaid.usePotion(slot)) {
            addLog("Used " + potion.getName() + " on "
                + capitalize(activeRaid.getPartyTypes()[slot]));
            return true;
        }
        return false;
    }

    /**
     * Uses a Phoenix Feather to revive a fallen party member.
     */
    public boolean usePhoenixFeather(int slot, String featherId) {
        if (!isRaidActive()) return false;

        Inventory inv = eventManager.getInventory();
        Item feather = inv.useConsumable(featherId);
        if (feather == null) return false;

        if (activeRaid.usePhoenixFeather(slot)) {
            addLog("Phoenix Feather revives "
                + capitalize(activeRaid.getPartyTypes()[slot]) + "!");
            return true;
        }
        return false;
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
                eventManager.returnUnitFromRaid(unitIds[i], types[i], levels[i], 1);
                addLog(capitalize(types[i]) + " returned barely alive");
            } else {
                // Alive — return with current HP
                eventManager.returnUnitFromRaid(unitIds[i], types[i], levels[i], currentHp[i]);
            }
        }

        activeRaid = null;
        currentNode = null;
        currentRooms = null;

        Gdx.app.log(TAG, "Raid ended — units returned to grid");
    }

    /**
     * Abandons the raid. Units return with current HP.
     */
    public void abandonRaid() {
        if (activeRaid == null) return;
        addLog("Raid abandoned!");
        activeRaid.setFailed(true);
        endRaid();
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

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
