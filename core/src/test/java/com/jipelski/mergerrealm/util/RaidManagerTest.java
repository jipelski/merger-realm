package com.jipelski.mergerrealm.util;

import com.jipelski.mergerrealm.model.ActiveStatusEffect;
import com.jipelski.mergerrealm.model.Combatant;
import com.jipelski.mergerrealm.model.RaidState;
import com.jipelski.mergerrealm.testutil.EventManagerTestSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives real raid combat through RaidManager.resumeFromSave(RaidState) —
 * the injection point CLAUDE.md's "Automated testing" entry identified as
 * ready-to-use for exercising RaidManager without needing startRaid()'s
 * full grid/unit/chapter-unlock prerequisites. Uses a real chapter/node id
 * pair from the bundled raid_chapters.json (goblin_warrens/gw_outpost) so
 * resumeFromSave's currentNode/currentRooms lookup succeeds and a room-clear
 * mid-test can't NPE on a null currentRooms.
 */
class RaidManagerTest {

    private EventManager eventManager;
    private RaidManager raidManager;

    @BeforeEach
    void setUp() {
        eventManager = EventManagerTestSupport.freshEventManager();
        raidManager = eventManager.getRaidManager();
    }

    private RaidState buildState(int enemyHp, float enemyAttackSpeed, boolean stunEnemy) {
        RaidState state = new RaidState();
        state.setChapterId("goblin_warrens");
        state.setNodeId("gw_outpost");
        state.setCurrentRoomIndex(0);
        state.setEndless(false);
        state.setInTransition(false);
        state.setFuryPhase(RaidState.FuryPhase.CHARGING);

        // Party: one unit, onHitEffect wired directly on the Combatant
        // (bypassing UnitData/GameDataLoader — the point of this test is
        // the combat/status-effect pipeline, not data loading).
        state.setPartyMember(0, "test_unit_1", "archer", 5, 500, 500, 50, "archer_5");
        state.getMember(0).onHitEffect = "poison";

        Combatant enemy = new Combatant();
        enemy.id = "goblin_grunt";
        enemy.type = "goblin_grunt";
        enemy.name = "Goblin Grunt";
        enemy.hp = enemyHp;
        enemy.maxHp = enemyHp;
        enemy.baseDamage = 20;
        enemy.baseAttackSpeed = enemyAttackSpeed;
        enemy.party = false;
        if (stunEnemy) {
            enemy.effects.add(new ActiveStatusEffect("stun", 100f)); // long enough to outlast the test
        }
        List<Combatant> enemies = new ArrayList<>();
        enemies.add(enemy);
        state.setActiveEnemies(enemies);

        return state;
    }

    @Test
    void resumeFromSave_nullState_isNoOp() {
        raidManager.resumeFromSave(null);
        assertFalse(raidManager.isRaidActive());
    }

    @Test
    void resumeFromSave_legacyFormatEmptyParty_isDiscardedNotResumed() {
        // A pre-Raid-V3 save deserializes with all 4 party slots null (no
        // "party" key the new Combatant[]-based schema recognizes) — see
        // resumeFromSave's javadoc. Simulated directly since there's no way
        // to produce the actual old JSON shape from current code anymore.
        RaidState legacyShaped = new RaidState();
        legacyShaped.setChapterId("goblin_warrens");
        legacyShaped.setNodeId("gw_outpost");
        // No setPartyMember call — every slot stays null, exactly what old
        // JSON lacking a "party" key would deserialize as.

        raidManager.resumeFromSave(legacyShaped);
        assertFalse(raidManager.isRaidActive());
        assertNull(raidManager.getActiveRaid());
    }

    @Test
    void update_poisonOnHit_ticksEnemyDown_and_stunSkipsEnemyAttack() {
        // Enemy's own attack speed is short (1s) so, unstunned, it would
        // easily land a hit inside this test's window — the party staying
        // at full HP is what proves the stun is actually suppressing it,
        // not just that the enemy happened not to get a turn.
        RaidState state = buildState(200, 1f, true);
        raidManager.resumeFromSave(state);
        assertTrue(raidManager.isRaidActive());

        // Unit's attack interval is RaidState.DEFAULT_ATTACK_SPEED (2.8s);
        // 3 ticks of 1.0s crosses that threshold exactly once.
        for (int i = 0; i < 3; i++) raidManager.update(1.0f);

        Combatant enemyAfterFirstAttack = raidManager.getActiveRaid().getActiveEnemies().get(0);
        assertTrue(enemyAfterFirstAttack.hp < 200, "unit's attack should have landed");
        assertTrue(enemyAfterFirstAttack.effects.stream().anyMatch(e -> "poison".equals(e.effectId)),
            "venom-style onHitEffect should have applied poison to the enemy");
        assertEquals(0f, enemyAfterFirstAttack.attackTimer, 0.001f,
            "stun should freeze the enemy's own attack timer entirely");
        assertEquals(500, raidManager.getActiveRaid().getMember(0).hp,
            "party should be untouched — the enemy never got to attack");

        int hpAfterFirstAttack = enemyAfterFirstAttack.hp;

        // Run further ticks — poison's dot should keep chipping the enemy
        // down even between physical attacks, proving the tick pipeline
        // (not just the on-hit apply) is live end-to-end.
        for (int i = 0; i < 3; i++) raidManager.update(1.0f);

        Combatant enemyLater = raidManager.getActiveRaid().getActiveEnemies().get(0);
        assertTrue(enemyLater.hp < hpAfterFirstAttack, "poison ticks should reduce hp further over time");
        assertEquals(500, raidManager.getActiveRaid().getMember(0).hp,
            "party should still be untouched — stun continues to hold across multiple frames");
    }

    @Test
    void update_unstunnedEnemy_eventuallyDamagesParty() {
        // Control case for the stun test above — without the stun effect,
        // the same short-attack-speed enemy DOES land hits on the party.
        RaidState state = buildState(200, 1f, false);
        raidManager.resumeFromSave(state);

        for (int i = 0; i < 3; i++) raidManager.update(1.0f);

        assertTrue(raidManager.getActiveRaid().getMember(0).hp < 500,
            "an unstunned enemy with a 1s attack speed should have hit the party by now");
    }
}
