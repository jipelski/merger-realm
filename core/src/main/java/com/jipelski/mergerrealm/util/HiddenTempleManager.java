package com.jipelski.mergerrealm.util;

import com.badlogic.gdx.Gdx;

import com.jipelski.mergerrealm.data.GenData;
import com.jipelski.mergerrealm.model.FacilitySpawnConfiguration;
import com.jipelski.mergerrealm.model.Item;

import java.util.ArrayList;

/**
 * Rolls and delivers the one-off "Hidden Temple" reward triggered by
 * consuming an ancient_map item (see InventoryMenu's item-tap handling and
 * HiddenTemplePopup). Unlike DailyLoginManager this has no streak and no
 * persisted state — an ancient_map's presence in Inventory already IS the
 * gating state, so every call to openTemple() is an independent roll with
 * nothing to save/load.
 *
 * Mirrors DailyLoginManager.claim()'s proven shape: a flat Gold grant plus
 * one WeightedRoll pick from a config table (assets/data/hidden_temple_config.json,
 * key "hidden_temple"), delivered via the same "spawn at closest empty cell,
 * else queue via Wall Gate" pattern ExplorationManager.collectResults() and
 * DailyLoginManager.claim() already use — a full grid never loses a reward.
 * On top of that shared shape, openTemple() also rolls an independent bonus
 * chance at an enchanted-set piece, the same "independent Math.random() <
 * rate check, not part of the weighted table" idiom
 * ExplorationManager.checkRareDrops() uses for its rare finds, and the same
 * delivery call (EnchantedSetManager.createRandomPiece + Inventory.addItem)
 * RaidManager.completeNode() already uses for its own enchanted drop.
 */
public class HiddenTempleManager {

    private static final String TAG = "HiddenTempleManager";

    private static final String CONFIG_KEY = "hidden_temple";
    private static final int TEMPLE_GOLD = 20;
    private static final double ENCHANTED_BONUS_CHANCE = 0.20;

    private final EventManager eventManager;

    public HiddenTempleManager(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    /** Result of a single openTemple() call, for the UI to display. */
    public static class TempleReward {
        public final int gold;
        public final String rewardType;
        public final int rewardLevel;
        public final boolean queued;
        public final String enchantedItemName; // null if the bonus roll missed

        TempleReward(int gold, String rewardType, int rewardLevel, boolean queued,
                     String enchantedItemName) {
            this.gold = gold;
            this.rewardType = rewardType;
            this.rewardLevel = rewardLevel;
            this.queued = queued;
            this.enchantedItemName = enchantedItemName;
        }
    }

    /**
     * Rolls and delivers the Temple reward. Callers (HiddenTemplePopup) are
     * responsible for consuming the ancient_map item first via
     * Inventory.useConsumable — this method does not touch the item itself,
     * it only grants the reward.
     */
    public TempleReward openTemple() {
        eventManager.getGoldManager().addGold(TEMPLE_GOLD, "hidden_temple");

        ArrayList<FacilitySpawnConfiguration> table =
            eventManager.getGameDataLoader().getSpawnConfiguration(CONFIG_KEY);

        String rewardType = null;
        int rewardLevel = 1;
        boolean queued = false;

        if (table != null && !table.isEmpty()) {
            WeightedRoll.validateProbabilitiesSumToOne(table, FacilitySpawnConfiguration::getSpawnProbability);
            FacilitySpawnConfiguration picked =
                WeightedRoll.weightedPick(table, FacilitySpawnConfiguration::getSpawnProbability);

            rewardType = picked.getUnitType();
            rewardLevel = picked.getUnitLVL();

            int[] cell = eventManager.getGridInstance().getClosestEmptyCell(0, 0);
            if (cell != null) {
                eventManager.spawnObject(rewardType, rewardLevel, cell[0], cell[1]);
            } else {
                eventManager.getBattleFieldManager().addToQueue(
                    new GenData(rewardType, "Hidden Temple reward", rewardLevel));
                queued = true;
            }
        } else {
            Gdx.app.log(TAG, "openTemple: no reward table for key=" + CONFIG_KEY);
        }

        String enchantedItemName = null;
        if (Math.random() < ENCHANTED_BONUS_CHANCE) {
            EnchantedSetManager esm = eventManager.getEnchantedSetManager();
            Item enchantedItem = esm.createRandomPiece("any");
            if (enchantedItem != null) {
                eventManager.getInventory().addItem(enchantedItem);
                enchantedItemName = enchantedItem.getName();
            }
        }

        Gdx.app.log(TAG, "Temple opened: +" + TEMPLE_GOLD + " Gold"
            + (rewardType != null ? ", " + rewardType + " lv" + rewardLevel
                + (queued ? " (queued)" : "") : "")
            + (enchantedItemName != null ? ", bonus: " + enchantedItemName : ""));

        return new TempleReward(TEMPLE_GOLD, rewardType, rewardLevel, queued, enchantedItemName);
    }
}
