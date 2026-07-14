package com.jipelski.mergerrealm.testutil;

import com.jipelski.mergerrealm.database.JsonManager;
import com.jipelski.mergerrealm.util.EventManager;

import java.io.File;

/**
 * Constructs a real EventManager for tests — the full manager graph
 * (ResourceManager, GameDataLoader, RuneSystem, GoldManager, Inventory,
 * BattleFieldManager, ExplorationManager, RaidManager, TrophyShop, ...),
 * reading the real bundled assets/data JSON via the test fixture wired up
 * in core/build.gradle (sourceSets.test.resources + test.workingDir).
 *
 * Safe to call repeatedly: clears the fixture's local "saves/" directory
 * first, so every call starts from a guaranteed-clean slate regardless of
 * what a previous test (or a previous `core:test` run — the fixture
 * directory under build/ isn't wiped between runs by default) may have
 * written there. See JsonManager.readJson(): it only ever deletes a save
 * file if one already exists locally AND fails integrity verification —
 * starting from an empty directory never reaches that branch at all, so
 * this isn't guarding against destructive behavior, just test isolation.
 */
public final class EventManagerTestSupport {

    private EventManagerTestSupport() {}

    public static EventManager freshEventManager() {
        GdxTestSupport.ensureInitialized();
        clearLocalSaves();
        return new EventManager(new JsonManager());
    }

    private static void clearLocalSaves() {
        // user.dir is set to core/build/resources/test by test.workingDir —
        // confirmed via DiagnosticAssetPathTest before this class was written.
        File saves = new File(System.getProperty("user.dir"), "saves");
        deleteRecursively(saves);
    }

    private static void deleteRecursively(File file) {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }
}
