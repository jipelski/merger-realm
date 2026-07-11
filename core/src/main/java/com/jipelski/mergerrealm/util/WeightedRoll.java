package com.jipelski.mergerrealm.util;

import com.badlogic.gdx.Gdx;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Shared weighted-random-selection logic. Consolidates what was duplicated
 * byte-for-byte in {@code Chest.spawn()}/{@code Facility.spawn()} and
 * near-duplicated across ExplorationManager's zone-event/loot rolls — see
 * CLAUDE.md's cleanup pass.
 *
 * Weights don't need to sum to any particular total — {@link #weightedPick}
 * normalizes against their own sum, so it works equally for probabilities
 * that sum to ~1.0 (Chest/Facility) or arbitrary integer weights (exploration
 * zone tables). The sum-to-1.0 contract is a SEPARATE opt-in check
 * ({@link #validateProbabilitiesSumToOne}) — only call it where that
 * invariant actually matters.
 *
 * Fallback direction is standardized to "last entry" everywhere this is used
 * (previously inconsistent across call sites: last-entry for Chest/Facility,
 * a hardcoded constant for one ExplorationManager roll, first-entry for
 * another — this only changes behavior in the astronomically rare
 * floating-point-edge-case path, never during a normal roll).
 */
public final class WeightedRoll {

    private static final String TAG = "WeightedRoll";

    private WeightedRoll() {} // static-only

    /**
     * Validates that a list's weights sum to ~1.0 (±0.001).
     * Throws IllegalArgumentException otherwise. Only call sites with a real
     * probability contract (Chest/Facility spawn configs) should use this —
     * integer-weighted tables (e.g. exploration zone loot) must NOT, since
     * they legitimately don't sum to 1.0.
     */
    public static <T> void validateProbabilitiesSumToOne(List<T> options,
                                                          ToDoubleFunction<T> weight) {
        double total = options.stream().mapToDouble(weight).sum();
        if (Math.abs(total - 1.0) > 0.001) {
            throw new IllegalArgumentException(
                "Spawn probabilities sum to " + total + ", expected 1.0");
        }
    }

    /**
     * Picks a weighted-random element from a list. Returns null if the list
     * is null or empty (callers with their own "empty list" log message
     * should guard before calling, since this method's log line won't have
     * caller-specific context).
     */
    public static <T> T weightedPick(List<T> options, ToDoubleFunction<T> weight) {
        if (options == null || options.isEmpty()) {
            Gdx.app.log(TAG, "weightedPick: options is null or empty");
            return null;
        }

        double total = options.stream().mapToDouble(weight).sum();
        double randomValue = Math.random() * total;
        double cumulative = 0.0;

        for (T option : options) {
            cumulative += weight.applyAsDouble(option);
            // Use < not <= so randomValue landing exactly on the total is
            // caught by the fallback below rather than never matching.
            if (randomValue < cumulative) {
                return option;
            }
        }

        // Floating-point edge case (cumulative sum lands just under the
        // total) — return the last entry rather than null.
        Gdx.app.log(TAG, "weightedPick: floating point fallback triggered — returning last option");
        return options.get(options.size() - 1);
    }
}
