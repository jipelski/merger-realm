package com.jipelski.mergerrealm.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Defines legendary evolution paths.
 *
 * A max-level unit + Amulet of Ascension → legendary variant.
 * Legendary units have maxLVL=1 (can't merge), boosted stats,
 * and unique traits that affect raids/exploration.
 *
 * Traits are stored as string identifiers — the combat/raid/exploration
 * systems check for these when calculating effects.
 */
public class LegendaryEvolution {

    /**
     * Maps base unit type → legendary unit type.
     * Only max-level units can evolve.
     */
    private static final Map<String, String> EVOLUTION_MAP = new HashMap<>();
    static {
        EVOLUTION_MAP.put("villager",    "elder_villager");
        EVOLUTION_MAP.put("woodsman",    "lumberlord");
        EVOLUTION_MAP.put("cook",        "grand_chef");
        EVOLUTION_MAP.put("prospector",  "ore_master");
        EVOLUTION_MAP.put("mercenary",   "war_veteran");
        EVOLUTION_MAP.put("carpenter",   "master_builder");
        EVOLUTION_MAP.put("knight",      "royal_knight");
        EVOLUTION_MAP.put("hunter",      "beastmaster");
        EVOLUTION_MAP.put("archer",      "shadowbow");
        EVOLUTION_MAP.put("blacksmith",  "forgemaster");
        EVOLUTION_MAP.put("bulwark",     "ironclad");
        EVOLUTION_MAP.put("monk",        "high_priest");
        EVOLUTION_MAP.put("paladin",     "archangel");
        EVOLUTION_MAP.put("griffin",     "storm_griffin");
        EVOLUTION_MAP.put("wyvern",      "venom_drake");
        EVOLUTION_MAP.put("dragon",      "elder_dragon");
        EVOLUTION_MAP.put("phoenix",     "eternal_phoenix");
    }

    /**
     * Maps legendary unit type → its unique trait identifier.
     * Traits are checked by name in combat/raid/exploration systems.
     */
    private static final Map<String, String> TRAIT_MAP = new HashMap<>();
    static {
        TRAIT_MAP.put("elder_villager", "dual_resource_food_wood");
        TRAIT_MAP.put("lumberlord",     "dual_resource_wood_iron");
        TRAIT_MAP.put("grand_chef",     "double_food_gen");
        TRAIT_MAP.put("ore_master",     "double_iron_gen");
        TRAIT_MAP.put("war_veteran",    "bonus_dismiss_rewards");
        TRAIT_MAP.put("master_builder", "double_wood_gen");
        TRAIT_MAP.put("royal_knight",   "reduce_monster_damage");
        TRAIT_MAP.put("beastmaster",    "food_gen_with_damage");
        TRAIT_MAP.put("shadowbow",      "double_attack_raid");
        TRAIT_MAP.put("forgemaster",    "double_iron_gen");
        TRAIT_MAP.put("ironclad",       "reflect_damage_raid");
        TRAIT_MAP.put("high_priest",    "heal_party_raid");
        TRAIT_MAP.put("archangel",      "curse_immune");
        TRAIT_MAP.put("storm_griffin",   "ignore_traps");
        TRAIT_MAP.put("venom_drake",    "poison_enemies");
        TRAIT_MAP.put("elder_dragon",   "highest_stats");
        TRAIT_MAP.put("eternal_phoenix", "auto_revive_raid");
    }

    /**
     * Returns the legendary type for the given base unit, or null.
     */
    public static String getLegendaryType(String baseType) {
        return EVOLUTION_MAP.get(baseType);
    }

    /**
     * Returns true if the given type is a legendary unit.
     */
    public static boolean isLegendary(String type) {
        return EVOLUTION_MAP.containsValue(type);
    }

    /**
     * Returns true if the given base type has a legendary evolution.
     */
    public static boolean canEvolve(String baseType) {
        return EVOLUTION_MAP.containsKey(baseType);
    }

    /**
     * Returns the trait identifier for a legendary unit, or null.
     */
    public static String getTrait(String legendaryType) {
        return TRAIT_MAP.get(legendaryType);
    }

    /**
     * Returns the base type that evolves into the given legendary, or null.
     */
    public static String getBaseType(String legendaryType) {
        for (Map.Entry<String, String> entry : EVOLUTION_MAP.entrySet()) {
            if (entry.getValue().equals(legendaryType)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Returns all legendary type names as an array.
     */
    public static String[] getAllLegendaryTypes() {
        return EVOLUTION_MAP.values().toArray(new String[0]);
    }
}
