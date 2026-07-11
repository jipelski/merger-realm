package com.jipelski.mergerrealm.util;

/**
 * Callback interface for game events that the renderer needs to react to.
 */
public interface GameEventListener {
    /**
     * Called when the grid expands due to a Prince level up.
     * The renderer should recalculate grid layout and update input bounds.
     */
    void onGridExpanded();

    // ── Tutorial hooks ──
    // Default no-op bodies so no other implementer of this interface needs
    // to change. MergerRealmGame (the sole listener) forwards each of these
    // to TutorialManager so interactive tutorial steps can advance off real
    // gameplay actions instead of a separate, parallel observer mechanism.

    /** A unit was successfully spawned by tapping/holding a facility. */
    default void onUnitSpawnedFromFacility(String facilityType) {}

    /** Two same-type, same-level objects were merged into a higher level one. */
    default void onUnitMerged(String type, int newLevel) {}

    /** A unit was dragged onto the Prince and dismissed for leadership XP. */
    default void onUnitDismissedToPrince(String type) {}

    /** A monster spawned onto the grid via its nemesis counter threshold. */
    default void onMonsterSpawned(String type) {}

    /** The Prince gained a level (fired once per level, even on multi-level-ups). */
    default void onLevelUp(int newLevel) {}
}
