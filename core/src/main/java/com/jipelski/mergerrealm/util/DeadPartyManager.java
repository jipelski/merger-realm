package com.jipelski.mergerrealm.util;

import com.jipelski.mergerrealm.model.DeadPartySnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the pool of parties permanently lost to a wiped Endless Gauntlet run
 * (see RaidManager.endEndlessWithPermanentDeath). Entries reappear as
 * "zombified" encounters in future endless runs (RaidManager.
 * generateEndlessRoom) and are removed once the player beats that encounter
 * (RaidManager.resolveZombieDefeat).
 *
 * Owned by EventManager, constructed/loaded the same way as PrestigeManager/
 * OutfitManager/AdManager. Persisted whole via JsonManager.saveDeadPartyPool/
 * loadDeadPartyPool under save key "dead_party_pool" — plain Gson list, no
 * extra bookkeeping needed since DeadPartySnapshot (and the Items it holds)
 * are already Gson-serializable POJOs.
 */
public class DeadPartyManager {

    private final EventManager eventManager;
    private final List<DeadPartySnapshot> pool = new ArrayList<>();

    public DeadPartyManager(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    public List<DeadPartySnapshot> getPool() { return pool; }

    public void setPool(List<DeadPartySnapshot> loaded) {
        pool.clear();
        if (loaded != null) pool.addAll(loaded);
    }

    public void addSnapshot(DeadPartySnapshot snap) {
        pool.add(snap);
    }

    public void remove(DeadPartySnapshot snap) {
        pool.remove(snap);
    }

    public DeadPartySnapshot findById(String id) {
        if (id == null) return null;
        for (DeadPartySnapshot s : pool) {
            if (id.equals(s.snapshotId)) return s;
        }
        return null;
    }
}
