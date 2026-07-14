package com.jipelski.mergerrealm.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A permanently-lost endless-raid party, frozen at the moment of a full wipe
 * (see RaidManager.endEndlessWithPermanentDeath). Persists in
 * DeadPartyManager's pool and reappears in future endless runs as a
 * "zombified" encounter (RaidManager.generateEndlessRoom /
 * buildZombieEnemies) — clearing it gives a chance to recover one of its
 * equipped items (resolveZombieDefeat), after which the snapshot is removed
 * from the pool for good.
 *
 * Per-slot arrays mirror RaidState's own party arrays exactly (same slot
 * indices, null/0 for an empty slot) — copied straight from RaidState at
 * time of death, no separate stat model needed.
 */
public class DeadPartySnapshot {

    public String snapshotId; // links an active zombie room back to this pool entry
    public String[] unitTypes = new String[4];
    public int[] unitLevels = new int[4];
    public int[] unitHp = new int[4];      // effective max HP at time of death
    public int[] unitDamage = new int[4];  // effective damage at time of death
    public String[] unitSprites = new String[4];
    public List<Item> equippedItems = new ArrayList<>(); // pulled out of Inventory, held here until retrieved
    public int depthReached;
    public long diedAtMs;

    public DeadPartySnapshot() {}
}
