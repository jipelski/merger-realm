package com.jipelski.mergerrealm.util;

import com.jipelski.mergerrealm.testutil.GdxTestSupport;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeightedRollTest {

    @BeforeAll
    static void setUpGdx() {
        // weightedPick logs via Gdx.app on the null/empty-list and
        // floating-point-fallback paths — needs a live Gdx.app.
        GdxTestSupport.ensureInitialized();
    }

    // Options are their own weight, for the simplest possible fixture.
    private static final java.util.function.ToDoubleFunction<Double> IDENTITY = d -> d;

    @Test
    void weightedPick_nullList_returnsNull() {
        assertNull(WeightedRoll.weightedPick(null, IDENTITY));
    }

    @Test
    void weightedPick_emptyList_returnsNull() {
        assertNull(WeightedRoll.weightedPick(Collections.emptyList(), IDENTITY));
    }

    @Test
    void weightedPick_singleOption_alwaysReturnsIt() {
        List<Double> options = Collections.singletonList(5.0);
        for (int i = 0; i < 20; i++) {
            assertEquals(5.0, WeightedRoll.weightedPick(options, IDENTITY));
        }
    }

    @Test
    void weightedPick_zeroWeightOption_isNeverReturned() {
        // A(1) B(0) C(1) — B contributes nothing to the cumulative sum, so it
        // can never be the one that satisfies `randomValue < cumulative`.
        // Not a statistical/flaky assertion — this is deterministic given
        // weightedPick's implementation, run many times to be sure.
        List<Double> options = Arrays.asList(1.0, 0.0, 1.0);
        for (int i = 0; i < 500; i++) {
            Double picked = WeightedRoll.weightedPick(options, d -> d == 0.0 ? 0.0 : 1.0);
            assertTrue(picked == 1.0, "zero-weight option should never be picked, got " + picked);
        }
    }

    @Test
    void weightedPick_onlyOnePositiveWeight_deterministic() {
        // [0, 1, 0] — only index 1 has any weight, so it must always win.
        List<String> options = Arrays.asList("skip", "always", "skip2");
        java.util.Map<String, Double> weights = new java.util.HashMap<>();
        weights.put("skip", 0.0);
        weights.put("always", 1.0);
        weights.put("skip2", 0.0);
        for (int i = 0; i < 50; i++) {
            assertEquals("always", WeightedRoll.weightedPick(options, weights::get));
        }
    }

    @Test
    void validateProbabilitiesSumToOne_passesWithinTolerance() {
        List<Double> options = Arrays.asList(0.3, 0.3, 0.4); // sums to exactly 1.0
        WeightedRoll.validateProbabilitiesSumToOne(options, IDENTITY); // no exception

        List<Double> withinTolerance = Arrays.asList(0.3, 0.3, 0.4005); // off by 0.0005 < 0.001
        WeightedRoll.validateProbabilitiesSumToOne(withinTolerance, IDENTITY); // no exception
    }

    @Test
    void validateProbabilitiesSumToOne_throwsOutsideTolerance() {
        List<Double> options = Arrays.asList(0.3, 0.3, 0.3); // sums to 0.9, off by 0.1
        assertThrows(IllegalArgumentException.class,
            () -> WeightedRoll.validateProbabilitiesSumToOne(options, IDENTITY));
    }
}
