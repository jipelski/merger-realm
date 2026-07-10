package com.jipelski.mergerrealm.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Single source of truth for every object-type grouping in the game.
 *
 * Before this class the same type lists were duplicated as switch-cases and
 * HashSets across EventManager, MergerRealmGame, GridInputHandler, BuildMenu
 * and the panels. Those copies drifted — most notably MergerRealmGame's unit
 * check omitted legendaries (Bug A: legendaries could fight but never got the
 * red combat highlight). Everything now delegates here.
 *
 * When adding a new type: add it to the relevant set below and the whole game
 * (highlights, combat routing, spawn/remove/dismiss switches once migrated,
 * build menu) picks it up. This is the "~2 places" the CLAUDE.md add-a-type
 * note refers to: the data JSON + this file.
 *
 * All sets are unmodifiable — never mutate them at runtime.
 */
public final class GameTypes {

    private GameTypes() {} // static-only

    // ── Base units (17) ──
    public static final Set<String> BASE_UNITS = unmodifiable(
        "villager", "woodsman", "cook", "prospector", "mercenary",
        "carpenter", "knight", "hunter", "archer", "blacksmith",
        "bulwark", "monk", "paladin", "griffin", "wyvern",
        "dragon", "phoenix"
    );

    // ── Legendary units (17) ──
    // Also enumerated in LegendaryEvolution's EVOLUTION_MAP values; keep in sync.
    public static final Set<String> LEGENDARY_UNITS = unmodifiable(
        "elder_villager", "lumberlord", "grand_chef", "ore_master",
        "war_veteran", "master_builder", "royal_knight", "beastmaster",
        "shadowbow", "forgemaster", "ironclad", "high_priest",
        "archangel", "storm_griffin", "venom_drake", "elder_dragon",
        "eternal_phoenix"
    );

    // ── All units (base + legendary) ──
    // Replaces EventManager.UNIT_TYPES and MergerRealmGame.isUnitType().
    public static final Set<String> UNITS = union(BASE_UNITS, LEGENDARY_UNITS);

    // ── Facilities (9) ──
    public static final Set<String> FACILITIES = unmodifiable(
        "homestead", "lodge", "tavernboard", "barracks",
        "archeryrange", "forge", "monastery", "griffinnest", "dragonslair"
    );

    // ── Periodic (timer-based) facilities ──
    // Mirrors EventManager.PERIODIC_FACILITIES; a subset of FACILITIES.
    public static final Set<String> PERIODIC_FACILITIES = unmodifiable(
        "tavernboard", "griffinnest", "dragonslair"
    );

    // ── Storage buildings (merge lv1→5, adjust caps only) ──
    // NOTE: code uses silo/timberyard/ironvault. Ensure data/storage.json
    // matches these keys (the legacy sawmill/quarry/ironmine file will return
    // null from getGameData and the build cards will silently not appear).
    public static final Set<String> STORAGE = unmodifiable(
        "silo", "timberyard", "ironvault"
    );

    // ── Monsters / invaders (5) ──
    public static final Set<String> MONSTERS = unmodifiable(
        "gremlin", "troll", "orc", "wraith", "demon"
    );

    // ── Chests (4) ──
    public static final Set<String> CHESTS = unmodifiable(
        "nail_chest", "slate_chest", "ingot_chest", "relic_chest"
    );

    // ── Tokens (4) ──
    public static final Set<String> TOKENS = unmodifiable(
        "nail_token", "slate_token", "ingot_token", "relic_token"
    );

    // ── Resource pouches (mergeable lv1->4; dismiss to Prince fills food/wood/iron) ──
    public static final Set<String> RESOURCE_POUCHES = unmodifiable(
        "food_pouch", "wood_pouch", "iron_pouch"
    );

    // ══════════════════════════════════════════════════════════════
    // PREDICATES — use these everywhere instead of local switch/case
    // ══════════════════════════════════════════════════════════════

    public static boolean isUnit(String type)     { return type != null && UNITS.contains(type); }
    public static boolean isBaseUnit(String type) { return type != null && BASE_UNITS.contains(type); }
    public static boolean isLegendary(String type){ return type != null && LEGENDARY_UNITS.contains(type); }
    public static boolean isFacility(String type) { return type != null && FACILITIES.contains(type); }
    public static boolean isPeriodicFacility(String type) {
        return type != null && PERIODIC_FACILITIES.contains(type);
    }
    public static boolean isStorage(String type)  { return type != null && STORAGE.contains(type); }
    public static boolean isMonster(String type)  { return type != null && MONSTERS.contains(type); }
    public static boolean isChest(String type)    { return type != null && CHESTS.contains(type); }
    public static boolean isToken(String type)    { return type != null && TOKENS.contains(type); }
    public static boolean isResourcePouch(String type) {
        return type != null && RESOURCE_POUCHES.contains(type);
    }

    // ══════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════

    private static Set<String> unmodifiable(String... items) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(items)));
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> s = new HashSet<>(a);
        s.addAll(b);
        return Collections.unmodifiableSet(s);
    }
}
