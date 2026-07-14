package com.jipelski.mergerrealm.util;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.net.HttpRequestBuilder;
import com.google.gson.Gson;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Anonymous player identity + a trusted-clock anchor, closing the "advance
 * the phone clock, close the app, reopen it" exploit that every time-gated
 * reward (offline progress, exploration timers, daily-login streak, trophy
 * refresh, dead-party timestamps) is otherwise vulnerable to.
 *
 * There is no real backend yet (it lives in a separate, not-yet-started
 * repo) — SERVER_BASE_URL is deliberately left blank below. sync() no-ops
 * until that's filled in; see the TODO at its declaration for the one seam
 * that needs touching once the backend exists. This mirrors AdManager's
 * "works fully standalone, one clearly-marked integration seam" shape.
 *
 * TRUST MODEL — one anchor, one formula. Persisted state is a single pair
 * (anchorWallClockMs, anchorElapsedRealtimeMs): "wall-clock had this value
 * at the moment the device's monotonic elapsed-time counter read that
 * value." getTrustedTimeMillis() is anchorWallClockMs + (elapsedNow -
 * anchorElapsedRealtimeMs). Both a successful server sync() AND a routine
 * checkpoint() refresh this same anchor — a sync just replaces its
 * provenance with an authoritative value, nothing else about how it's read
 * changes. (An earlier draft kept a separate "server offset" branch that
 * turned out to be exploitable by a clock change AFTER a sync — this single-
 * anchor design doesn't have that gap.)
 *
 * The monotonic elapsed-time reading is Android's SystemClock.elapsedRealtime()
 * (via reflection — see getElapsedRealtimeMs()), NOT LibGDX's cross-platform
 * TimeUtils.nanoTime(): nanoTime doesn't survive a process restart at all,
 * and this exploit's whole pattern IS a process restart, so it would give
 * zero protection. elapsedRealtime() survives app-close (resets only on a
 * full device reboot — an accepted, stated gap, see checkpoint()). It's
 * Android-only; on desktop (lwjgl3, a dev-only testing target, not a real
 * player surface) no anchor is ever established and getTrustedTimeMillis()
 * transparently falls back to raw device time — same as before this class
 * existed, not a regression.
 *
 * Persistence, two keys, no new JsonManager methods needed:
 *   player_identity   — a single anonymous UUID via the saveStringSet/
 *                        loadStringSet 0-or-1-entry-set idiom, the exact
 *                        pattern OutfitManager already uses for
 *                        equipped_outfit.
 *   server_time_state — getSaveState()/setSaveState(int[]) packing the
 *                        anchor into 5 ints, mirroring AdManager's own
 *                        int[]-packing shape exactly.
 */
public class ServerTimeManager {

    private static final String TAG = "ServerTimeManager";

    // TODO: point this at the backend repo's real address once it exists —
    // this is the ONLY line that needs to change to go from "local-only
    // anti-cheat" to "server-verified." Left blank deliberately; sync()
    // no-ops immediately while it's blank. Assumed contract for whoever
    // builds the backend: GET {url}/v1/time -> {"time": <epochMillis>}.
    private static final String SERVER_BASE_URL = "";

    // Wide enough to absorb ordinary NTP auto-correction / DST jumps,
    // narrow enough to catch a real multi-hour-or-more clock-advance cheat.
    // A tunable judgment call — may need real-world tuning once this can
    // actually be observed against live traffic, which isn't possible yet.
    private static final long TAMPER_SLOP_MS = 10 * 60 * 1000L;

    private final EventManager eventManager;
    private final Gson gson = new Gson();

    private String playerId;

    // ── The anchor — see class javadoc's TRUST MODEL section ──
    private boolean hasAnchor = false;
    private long anchorWallClockMs = 0;
    private long anchorElapsedRealtimeMs = 0;

    // Ephemeral, per-session only — not persisted. Exposed as a hook for a
    // future UI/telemetry pass; nothing consumes it yet.
    private boolean tamperDetectedThisSession = false;

    // Injectable so tests can feed synthetic elapsedRealtime sequences
    // (reboot, tamper, normal) without a real Android device — see
    // setElapsedRealtimeSourceForTest(). Defaults to the real reflective read.
    private LongSupplier elapsedRealtimeSource = this::reflectElapsedRealtime;

    public ServerTimeManager(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    // ══════════════════════════════════════════════════════════════
    // IDENTITY
    // ══════════════════════════════════════════════════════════════

    public String getPlayerId() { return playerId; }

    /** Restores the persisted UUID, or mints a fresh one on first-ever run. */
    public void setPlayerIdFromSet(Set<String> saved) {
        if (saved != null && !saved.isEmpty()) {
            this.playerId = saved.iterator().next();
        } else {
            this.playerId = UUID.randomUUID().toString();
        }
    }

    /** 0-or-1-element set — same saveStringSet/loadStringSet idiom OutfitManager uses for equipped_outfit. */
    public Set<String> getPlayerIdAsSet() {
        Set<String> result = new HashSet<>();
        if (playerId != null) result.add(playerId);
        return result;
    }

    // ══════════════════════════════════════════════════════════════
    // TRUSTED TIME
    // ══════════════════════════════════════════════════════════════

    /** The one method the rest of the codebase should call instead of raw System.currentTimeMillis(). */
    public long getTrustedTimeMillis() {
        long currentElapsed = elapsedRealtimeSource.getAsLong();
        return computeTrustedTime(hasAnchor, anchorWallClockMs, anchorElapsedRealtimeMs,
            currentElapsed, System.currentTimeMillis());
    }

    public boolean isTamperDetectedThisSession() { return tamperDetectedThisSession; }

    /**
     * Called once per cold-launch/resume, BEFORE processOfflineProgress()
     * reads trusted time. Refreshes the anchor via computeCheckpoint()'s
     * decision table — see that method for the full branch-by-branch
     * reasoning (first-run, reboot, tamper, normal).
     */
    public void checkpoint() {
        long currentWall = System.currentTimeMillis();
        long currentElapsed = elapsedRealtimeSource.getAsLong();

        CheckpointResult result = computeCheckpoint(hasAnchor, anchorWallClockMs, anchorElapsedRealtimeMs,
            currentWall, currentElapsed, TAMPER_SLOP_MS);

        this.hasAnchor = result.hasAnchor;
        this.anchorWallClockMs = result.anchorWallClockMs;
        this.anchorElapsedRealtimeMs = result.anchorElapsedRealtimeMs;
        this.tamperDetectedThisSession = result.tamperDetected;

        if (result.tamperDetected) {
            Gdx.app.log(TAG, "Clock tamper suspected — wall clock jumped further than "
                + "device uptime allows; ignoring the jump for trusted-time purposes.");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // PURE DECISION MATH — no Gdx/EventManager dependency, directly
    // unit-testable (package-private so the test class, same package, can
    // call these without reflection).
    // ══════════════════════════════════════════════════════════════

    /** Result of computeCheckpoint() — the new anchor + whether tamper was detected this call. */
    static final class CheckpointResult {
        final boolean hasAnchor;
        final long anchorWallClockMs;
        final long anchorElapsedRealtimeMs;
        final boolean tamperDetected;

        CheckpointResult(boolean hasAnchor, long anchorWallClockMs, long anchorElapsedRealtimeMs,
                          boolean tamperDetected) {
            this.hasAnchor = hasAnchor;
            this.anchorWallClockMs = anchorWallClockMs;
            this.anchorElapsedRealtimeMs = anchorElapsedRealtimeMs;
            this.tamperDetected = tamperDetected;
        }
    }

    /**
     * The full checkpoint decision table:
     *   1. No monotonic signal this platform/run (currentElapsedMs < 0,
     *      i.e. desktop or reflection failed) — can't anchor or detect
     *      anything; caller ends up with hasAnchor=false.
     *   2. No prior anchor (first-ever checkpoint / fresh install) — seed
     *      it, skip detection this session.
     *   3. elapsedDelta < 0 — a device reboot happened since last
     *      checkpoint (elapsedRealtime resets to a small value on reboot).
     *      No trustworthy prior reference to compare against; re-anchor
     *      fresh, no tamper flag. Stated, accepted gap: "change clock ->
     *      reboot -> reopen" bypasses local detection entirely — not
     *      solvable without a server.
     *   4. (wallDelta - elapsedDelta) > tamperSlopMs — implausible forward
     *      jump in wall clock vs. real elapsed device time. Reject the
     *      jump: advance the anchor's wall-clock component by ONLY the
     *      legitimate elapsed amount, never by the observed (tampered)
     *      wall clock — this is what makes getTrustedTimeMillis() actually
     *      reject the jump instead of silently adopting it.
     *   5. Otherwise — wall clock and monotonic elapsed time agree within
     *      slop; legitimate. Advance the anchor to the observed wall clock
     *      (self-corrects minor real-world drift over normal sessions).
     */
    static CheckpointResult computeCheckpoint(boolean hasAnchor, long anchorWallClockMs,
                                               long anchorElapsedMs, long currentWallClockMs,
                                               long currentElapsedMs, long tamperSlopMs) {
        if (currentElapsedMs < 0) {
            return new CheckpointResult(false, currentWallClockMs, currentElapsedMs, false);
        }
        if (!hasAnchor) {
            return new CheckpointResult(true, currentWallClockMs, currentElapsedMs, false);
        }

        long elapsedDelta = currentElapsedMs - anchorElapsedMs;
        if (elapsedDelta < 0) {
            return new CheckpointResult(true, currentWallClockMs, currentElapsedMs, false);
        }

        long wallDelta = currentWallClockMs - anchorWallClockMs;
        if (wallDelta - elapsedDelta > tamperSlopMs) {
            long correctedWallClock = anchorWallClockMs + elapsedDelta;
            return new CheckpointResult(true, correctedWallClock, currentElapsedMs, true);
        }

        return new CheckpointResult(true, currentWallClockMs, currentElapsedMs, false);
    }

    /** The trust formula itself — see class javadoc's TRUST MODEL section. */
    static long computeTrustedTime(boolean hasAnchor, long anchorWallClockMs, long anchorElapsedMs,
                                    long currentElapsedMs, long rawDeviceNow) {
        if (!hasAnchor || currentElapsedMs < 0) return rawDeviceNow;
        return anchorWallClockMs + (currentElapsedMs - anchorElapsedMs);
    }

    // ══════════════════════════════════════════════════════════════
    // SERVER SYNC (no-op until SERVER_BASE_URL is filled in)
    // ══════════════════════════════════════════════════════════════

    private static final class ServerTimeResponse {
        long time;
    }

    /**
     * Fire-and-forget async HTTP GET to SERVER_BASE_URL + "/v1/time". No-ops
     * immediately if the URL is blank (today, always). On success, refreshes
     * the SAME anchor checkpoint() maintains — see applySyncResult().
     * X-Player-Id doubles as the anonymous registration mechanism; no
     * separate registration endpoint is assumed needed for v1.
     */
    public void sync() {
        if (SERVER_BASE_URL == null || SERVER_BASE_URL.isEmpty()) return;

        final long requestSentAt = System.currentTimeMillis();
        Net.HttpRequest request = new HttpRequestBuilder()
            .newRequest()
            .method(Net.HttpMethods.GET)
            .url(SERVER_BASE_URL + "/v1/time")
            .header("X-Player-Id", playerId)
            .timeout(5000)
            .build();

        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                final long requestReceivedAt = System.currentTimeMillis();
                try {
                    String body = httpResponse.getResultAsString();
                    ServerTimeResponse parsed = gson.fromJson(body, ServerTimeResponse.class);
                    if (parsed == null) return;
                    long roundTripMs = requestReceivedAt - requestSentAt;
                    final long estimatedTrueNow = parsed.time + roundTripMs / 2;
                    final long elapsedAtReceipt = elapsedRealtimeSource.getAsLong();
                    // This callback runs on a LibGDX worker thread, not the
                    // render thread — marshal the anchor update back via
                    // postRunnable rather than mutating fields directly here.
                    Gdx.app.postRunnable(() -> applySyncResult(estimatedTrueNow, elapsedAtReceipt));
                } catch (Exception e) {
                    Gdx.app.log(TAG, "Server time sync: malformed response, ignoring");
                }
            }

            @Override
            public void failed(Throwable t) {
                Gdx.app.log(TAG, "Server time sync failed (offline, or no backend yet): "
                    + (t != null ? t.getMessage() : "unknown"));
            }

            @Override
            public void cancelled() {
                Gdx.app.log(TAG, "Server time sync cancelled");
            }
        });
    }

    /** Runs on the render thread (via Gdx.app.postRunnable from sync()'s callback). */
    private void applySyncResult(long estimatedTrueNow, long elapsedAtReceipt) {
        if (elapsedAtReceipt < 0) {
            // No monotonic signal on this platform (desktop) — a server
            // offset alone isn't safe to anchor on, since a later clock
            // change would defeat it with nothing to cross-check against.
            // Intentionally do nothing; see class javadoc's desktop note.
            return;
        }
        this.hasAnchor = true;
        this.anchorWallClockMs = estimatedTrueNow;
        this.anchorElapsedRealtimeMs = elapsedAtReceipt;
        this.tamperDetectedThisSession = false; // a confirmed server sync clears local suspicion
        Gdx.app.log(TAG, "Server time sync OK — anchor refreshed");
    }

    // ══════════════════════════════════════════════════════════════
    // ANDROID elapsedRealtime() — via reflection, deliberately not a
    // platform-interface (see class javadoc). Injectable for tests.
    // ══════════════════════════════════════════════════════════════

    // Cached once (not per-call) — Class.forName/getMethod are comparatively
    // expensive, and getTrustedTimeMillis() can be called many times per
    // frame (UI display code included). null means "not yet resolved";
    // FAILED_SENTINEL means "resolution was tried and failed" (desktop, or
    // a genuinely broken reflection environment) — resolution is only ever
    // attempted once, not retried every call.
    private static volatile Method elapsedRealtimeMethod;
    private static volatile boolean elapsedRealtimeResolutionFailed = false;

    private long reflectElapsedRealtime() {
        if (Gdx.app.getType() != Application.ApplicationType.Android) return -1;
        if (elapsedRealtimeResolutionFailed) return -1;
        try {
            if (elapsedRealtimeMethod == null) {
                elapsedRealtimeMethod = Class.forName("android.os.SystemClock").getMethod("elapsedRealtime");
            }
            return (long) elapsedRealtimeMethod.invoke(null);
        } catch (Exception e) {
            elapsedRealtimeResolutionFailed = true;
            return -1;
        }
    }

    /** Test-only seam — lets tests inject synthetic elapsed-time sequences without a real Android device. */
    void setElapsedRealtimeSourceForTest(LongSupplier source) {
        this.elapsedRealtimeSource = source;
    }

    // ══════════════════════════════════════════════════════════════
    // SAVE / LOAD
    // ══════════════════════════════════════════════════════════════

    /** { hasAnchor(0/1), anchorWallClockHigh32, anchorWallClockLow32, anchorElapsedHigh32, anchorElapsedLow32 } */
    public int[] getSaveState() {
        return new int[] {
            hasAnchor ? 1 : 0,
            (int) (anchorWallClockMs >>> 32), (int) (anchorWallClockMs),
            (int) (anchorElapsedRealtimeMs >>> 32), (int) (anchorElapsedRealtimeMs)
        };
    }

    public void setSaveState(int[] state) {
        if (state == null || state.length < 5) return;
        hasAnchor = state[0] != 0;
        anchorWallClockMs = ((long) state[1] << 32) | (state[2] & 0xFFFFFFFFL);
        anchorElapsedRealtimeMs = ((long) state[3] << 32) | (state[4] & 0xFFFFFFFFL);
    }
}
