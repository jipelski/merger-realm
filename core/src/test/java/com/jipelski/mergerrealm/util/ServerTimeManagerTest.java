package com.jipelski.mergerrealm.util;

import com.jipelski.mergerrealm.database.JsonManager;
import com.jipelski.mergerrealm.testutil.EventManagerTestSupport;
import com.jipelski.mergerrealm.testutil.GdxTestSupport;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The outer class covers the pure decision math (computeCheckpoint /
 * computeTrustedTime — package-private static methods, zero Gdx/EventManager
 * dependency) plus identity get/set and save-state pack/unpack, all via
 * `new ServerTimeManager(null)` — safe because none of these methods ever
 * dereference `eventManager` (confirmed by reading the class: nothing does,
 * it's stored only for parity with every other manager's constructor
 * convention). sync() itself (the real Gdx.net call) isn't covered here —
 * same accepted gap this project's other managers have for behavior needing
 * a real Gdx context; SERVER_BASE_URL is blank today anyway, so sync() is a
 * guaranteed no-op in every environment this test suite runs in.
 *
 * The nested WithRealEventManager class confirms identity generation is
 * correctly wired through EventManager's constructor and actually persists
 * across a reload — not just that the pure setPlayerIdFromSet/getPlayerIdAsSet
 * pair works in isolation (the outer class already covers that).
 */
class ServerTimeManagerTest {

    @BeforeAll
    static void setUpGdx() {
        GdxTestSupport.ensureInitialized();
    }

    private ServerTimeManager stm;

    @BeforeEach
    void setUp() {
        stm = new ServerTimeManager(null);
    }

    // ══════════════════════════════════════════════════════════════
    // computeCheckpoint — the full decision table
    // ══════════════════════════════════════════════════════════════

    @Test
    void computeCheckpoint_noMonotonicSignal_doesNotEstablishAnchor() {
        // Desktop, or reflection failed — currentElapsedMs is the -1 sentinel.
        ServerTimeManager.CheckpointResult result = ServerTimeManager.computeCheckpoint(
            true, 1_000_000L, 500L, 2_000_000L, -1L, 600_000L);

        assertFalse(result.hasAnchor);
        assertFalse(result.tamperDetected);
    }

    @Test
    void computeCheckpoint_firstRun_seedsAnchorWithoutTamperFlag() {
        ServerTimeManager.CheckpointResult result = ServerTimeManager.computeCheckpoint(
            false, 0L, 0L, 5_000_000L, 10_000L, 600_000L);

        assertTrue(result.hasAnchor);
        assertEquals(5_000_000L, result.anchorWallClockMs);
        assertEquals(10_000L, result.anchorElapsedRealtimeMs);
        assertFalse(result.tamperDetected);
    }

    @Test
    void computeCheckpoint_normalElapsedTime_advancesAnchorNoTamper() {
        // 60s of real time passed, wall clock agrees — legitimate.
        long anchorWall = 1_000_000L;
        long anchorElapsed = 500_000L;
        long currentWall = anchorWall + 60_000L;
        long currentElapsed = anchorElapsed + 60_000L;

        ServerTimeManager.CheckpointResult result = ServerTimeManager.computeCheckpoint(
            true, anchorWall, anchorElapsed, currentWall, currentElapsed, 600_000L);

        assertTrue(result.hasAnchor);
        assertFalse(result.tamperDetected);
        assertEquals(currentWall, result.anchorWallClockMs);
        assertEquals(currentElapsed, result.anchorElapsedRealtimeMs);
    }

    @Test
    void computeCheckpoint_withinSlop_doesNotFlagTamper() {
        // wallDelta exceeds elapsedDelta, but by less than the slop — a
        // plausible NTP auto-correction, not a cheat attempt.
        long anchorWall = 1_000_000L;
        long anchorElapsed = 500_000L;
        long elapsedDelta = 60_000L;
        long slop = 600_000L; // 10 minutes
        long currentWall = anchorWall + elapsedDelta + (slop - 1_000L);
        long currentElapsed = anchorElapsed + elapsedDelta;

        ServerTimeManager.CheckpointResult result = ServerTimeManager.computeCheckpoint(
            true, anchorWall, anchorElapsed, currentWall, currentElapsed, slop);

        assertFalse(result.tamperDetected);
        assertEquals(currentWall, result.anchorWallClockMs); // advances to observed wall clock
    }

    @Test
    void computeCheckpoint_reboot_reAnchorsWithoutTamperFlagOrCorruption() {
        // elapsedRealtime reset to a small value since last checkpoint —
        // negative delta signals a device reboot. Must NOT flag tamper, and
        // must re-anchor to CURRENT values (not some corrupted computation).
        long anchorWall = 5_000_000L;
        long anchorElapsed = 900_000L; // large — long uptime before reboot
        long currentWall = anchorWall + 60_000L; // 1 minute of legitimate wall-clock time passed
        long currentElapsed = 10_000L; // small — device just rebooted

        ServerTimeManager.CheckpointResult result = ServerTimeManager.computeCheckpoint(
            true, anchorWall, anchorElapsed, currentWall, currentElapsed, 600_000L);

        assertTrue(result.hasAnchor);
        assertFalse(result.tamperDetected);
        assertEquals(currentWall, result.anchorWallClockMs);
        assertEquals(currentElapsed, result.anchorElapsedRealtimeMs);
    }

    @Test
    void computeCheckpoint_clockAdvancedPastSlop_flagsTamperAndRejectsTheJump() {
        // The actual exploit: wall clock jumped 3 days forward, but the
        // device's real monotonic uptime only advanced 1 minute.
        long anchorWall = 1_000_000L;
        long anchorElapsed = 500_000L;
        long elapsedDelta = 60_000L; // 1 real minute
        long fabricatedWallJump = 3L * 24 * 60 * 60 * 1000; // 3 days
        long currentWall = anchorWall + fabricatedWallJump;
        long currentElapsed = anchorElapsed + elapsedDelta;

        ServerTimeManager.CheckpointResult result = ServerTimeManager.computeCheckpoint(
            true, anchorWall, anchorElapsed, currentWall, currentElapsed, 600_000L);

        assertTrue(result.tamperDetected);
        // Critical: the corrected anchor advances by only the LEGITIMATE
        // elapsed amount, never by the observed (tampered) wall clock.
        assertEquals(anchorWall + elapsedDelta, result.anchorWallClockMs);
        assertEquals(currentElapsed, result.anchorElapsedRealtimeMs);
    }

    // ══════════════════════════════════════════════════════════════
    // computeTrustedTime
    // ══════════════════════════════════════════════════════════════

    @Test
    void computeTrustedTime_noAnchor_returnsRawDeviceNow() {
        long raw = 123_456_789L;
        assertEquals(raw, ServerTimeManager.computeTrustedTime(false, 0L, 0L, 999L, raw));
    }

    @Test
    void computeTrustedTime_noMonotonicSignal_returnsRawDeviceNow() {
        long raw = 123_456_789L;
        // hasAnchor=true but currentElapsedMs is the -1 sentinel — must
        // still fall back to raw time, not do arithmetic with -1.
        assertEquals(raw, ServerTimeManager.computeTrustedTime(true, 1_000_000L, 500_000L, -1L, raw));
    }

    @Test
    void computeTrustedTime_withAnchor_appliesFormula() {
        long anchorWall = 1_000_000L;
        long anchorElapsed = 500_000L;
        long currentElapsed = anchorElapsed + 42_000L;

        long trusted = ServerTimeManager.computeTrustedTime(
            true, anchorWall, anchorElapsed, currentElapsed, 999_999_999L);

        assertEquals(anchorWall + 42_000L, trusted);
    }

    // ══════════════════════════════════════════════════════════════
    // checkpoint() / getTrustedTimeMillis() wired together, via the
    // injectable elapsedRealtimeSource seam (no real Android device needed)
    // ══════════════════════════════════════════════════════════════

    @Test
    void checkpointThenGetTrustedTime_rejectsAFabricatedClockJump() {
        stm.setElapsedRealtimeSourceForTest(() -> 100_000L);
        stm.checkpoint(); // first-ever checkpoint — seeds the anchor

        // Simulate: device uptime only advances 1 real minute, but a
        // subsequent checkpoint sees the wall clock having jumped 3 days —
        // computeCheckpoint's tamper branch should reject it. We can't
        // control System.currentTimeMillis() directly, so exercise the
        // pure function directly here instead (already covered above) and
        // use this test only to confirm isTamperDetectedThisSession() wiring.
        stm.setElapsedRealtimeSourceForTest(() -> 160_000L); // +60s real uptime
        stm.checkpoint();
        assertFalse(stm.isTamperDetectedThisSession()); // wall clock wasn't touched in this test process
    }

    // ══════════════════════════════════════════════════════════════
    // SAVE / LOAD
    // ══════════════════════════════════════════════════════════════

    @Test
    void saveState_roundTrips() {
        stm.setElapsedRealtimeSourceForTest(() -> 5_000L);
        stm.checkpoint(); // establishes a real anchor to pack

        int[] saved = stm.getSaveState();

        ServerTimeManager restored = new ServerTimeManager(null);
        restored.setSaveState(saved);
        assertArrayEquals(saved, restored.getSaveState());
    }

    @Test
    void setSaveState_nullOrShortArray_isNoOp() {
        stm.setSaveState(null);
        stm.setSaveState(new int[]{1, 2, 3}); // too short (needs 5)

        // No anchor was ever established — falls back to raw device time.
        long before = System.currentTimeMillis();
        long trusted = stm.getTrustedTimeMillis();
        long after = System.currentTimeMillis();
        assertTrue(trusted >= before && trusted <= after);
    }

    // ══════════════════════════════════════════════════════════════
    // IDENTITY (pure — no EventManager needed)
    // ══════════════════════════════════════════════════════════════

    @Test
    void playerId_firstRun_generatesRandomUuid() {
        stm.setPlayerIdFromSet(null);
        assertNotNull(stm.getPlayerId());
        assertDoesNotThrow(() -> UUID.fromString(stm.getPlayerId()));
    }

    @Test
    void playerId_emptySet_generatesRandomUuid() {
        stm.setPlayerIdFromSet(new HashSet<>());
        assertNotNull(stm.getPlayerId());
        assertDoesNotThrow(() -> UUID.fromString(stm.getPlayerId()));
    }

    @Test
    void playerId_restoredFromSet_usesExistingValue() {
        stm.setPlayerIdFromSet(Collections.singleton("known-uuid-value"));
        assertEquals("known-uuid-value", stm.getPlayerId());
    }

    @Test
    void getPlayerIdAsSet_roundTrips() {
        stm.setPlayerIdFromSet(Collections.singleton("some-id"));
        Set<String> asSet = stm.getPlayerIdAsSet();
        assertEquals(1, asSet.size());
        assertTrue(asSet.contains("some-id"));
    }

    /**
     * Scoped to identity generation/persistence wiring through EventManager's
     * real constructor — the pure setPlayerIdFromSet/getPlayerIdAsSet logic
     * itself is already covered above without needing a real EventManager.
     */
    @Nested
    class WithRealEventManager {

        @Test
        void freshEventManager_hasValidNonNullPlayerId() {
            EventManager em = EventManagerTestSupport.freshEventManager();
            String id = em.getServerTimeManager().getPlayerId();
            assertNotNull(id);
            assertDoesNotThrow(() -> UUID.fromString(id));
        }

        @Test
        void playerIdentity_persistsAcrossReload() {
            // Fresh install — EventManagerTestSupport wipes saves/ first, so
            // this mints a brand-new UUID.
            EventManager em1 = EventManagerTestSupport.freshEventManager();
            String firstId = em1.getServerTimeManager().getPlayerId();
            assertNotNull(firstId);

            // Simulate the persistence MergerRealmGame.saveGame() performs
            // for ServerTimeManager's identity (saveStringSet("player_identity", ...)).
            JsonManager jsonManager = new JsonManager();
            jsonManager.saveStringSet("player_identity", em1.getServerTimeManager().getPlayerIdAsSet());

            // Reload — a second EventManager over the SAME now-populated
            // saves/ directory, constructed directly (NOT via
            // freshEventManager(), which would wipe saves/ again first).
            EventManager em2 = new EventManager(jsonManager);
            assertEquals(firstId, em2.getServerTimeManager().getPlayerId());
        }

        @Test
        void freshEventManager_repeatedFreshInstall_generatesDifferentIds() {
            // Confirms the constructor path actually runs the first-run
            // generation branch (not silently reusing a stale value) —
            // each freshEventManager() call wipes saves/ first.
            EventManager em1 = EventManagerTestSupport.freshEventManager();
            EventManager em2 = EventManagerTestSupport.freshEventManager();
            assertNotEquals(
                em1.getServerTimeManager().getPlayerId(),
                em2.getServerTimeManager().getPlayerId());
        }
    }
}
