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
}
