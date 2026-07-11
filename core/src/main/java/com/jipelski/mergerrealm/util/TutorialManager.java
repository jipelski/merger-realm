package com.jipelski.mergerrealm.util;

import com.badlogic.gdx.Gdx;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.jipelski.mergerrealm.database.JsonManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Drives the first-time-player tutorial: an ordered sequence of steps
 * (INTERACTIVE steps advance on a real game event; TEXT steps advance on a
 * Next tap), plus one-shot contextual tips for features that appear later
 * (first monster, first Explore/Raid/Inventory open). Step/tip copy is
 * data-driven from assets/data/tutorial_steps.json; this class only owns
 * progress tracking and trigger matching — rendering (cards, buttons, the
 * highlight ring) lives in ui/TutorialOverlay + MergerRealmGame.
 *
 * Persistence: this class loads its own state at construction, but does NOT
 * write to disk itself — it exposes getSaveState()/getTipsSeen() the same
 * way BattleFieldManager/GoldManager expose scalars, and MergerRealmGame.
 * saveGame() persists them on the existing ~5s debounced autosave cadence
 * (gameTick() sets saveDirty=true every second). This matches the
 * established pattern (gold_state/progression) instead of adding a second,
 * eager write-on-every-action path. Never rename "tutorial_state" /
 * "tutorial_tips_seen" — see CLAUDE.md's persistence rules.
 */
public class TutorialManager {

    private static final String TAG = "TutorialManager";

    public enum StepType { TEXT, INTERACTIVE }

    public static class TutorialStep {
        public String id;
        public StepType type = StepType.TEXT;
        public String title = "";
        public String body = "";
        /** Event key this step advances on, e.g. "spawn:homestead", "merge:any", "dismiss:any". Null for TEXT steps (advance via Next). */
        public String trigger;
        public int requiredCount = 1;
        /**
         * Semantic target for the highlight ring, resolved to a screen rect
         * by MergerRealmGame — e.g. "cell:homestead", "cell:prince",
         * "cells:mergeable", "hud:resources", "hud:xpbar", "wallgate",
         * "buildbutton". Null/empty means no ring.
         */
        public String target;
    }

    private final Gson gson = new Gson();
    private final List<TutorialStep> steps = new ArrayList<>();
    private final Map<String, TutorialStep> tips = new HashMap<>();
    private final Set<String> tipsSeen = new HashSet<>();

    private int currentStep = 0;
    private int progressCount = 0;
    private boolean completed = false;
    private boolean active = false;
    private String activeTipId = null;

    public TutorialManager(JsonManager jsonManager) {
        loadDefinitions(jsonManager);
        loadProgress(jsonManager);
    }

    // ── Lifecycle ──

    /** Call once at startup. Resumes an in-progress tutorial, or no-ops if already completed/skipped. */
    public void start() {
        if (!completed && !steps.isEmpty()) {
            active = true;
            Gdx.app.log(TAG, "Tutorial active at step " + currentStep + "/" + steps.size());
        }
    }

    public void skip() {
        if (!active) return;
        active = false;
        completed = true;
        Gdx.app.log(TAG, "Tutorial skipped at step " + currentStep);
    }

    /** Advances a TEXT step on its Next button. No-op for INTERACTIVE steps — those only advance via a matching event. */
    public void next() {
        if (!active) return;
        TutorialStep step = getCurrentStep();
        if (step == null || step.type != StepType.TEXT) return;
        advance();
    }

    public void back() {
        if (!active || currentStep <= 0) return;
        currentStep--;
        progressCount = 0;
    }

    public boolean isActive() { return active; }
    public boolean isFirstStep() { return currentStep <= 0; }
    public boolean isLastStep() { return currentStep >= steps.size() - 1; }

    public TutorialStep getCurrentStep() {
        if (!active || currentStep < 0 || currentStep >= steps.size()) return null;
        return steps.get(currentStep);
    }

    // ── Contextual tips ──

    public TutorialStep getActiveTip() {
        return activeTipId != null ? tips.get(activeTipId) : null;
    }

    public void dismissActiveTip() {
        if (activeTipId == null) return;
        tipsSeen.add(activeTipId);
        activeTipId = null;
    }

    // ── Event hooks (called by MergerRealmGame, which implements GameEventListener) ──

    public void onUnitSpawnedFromFacility(String facilityType) {
        matchTrigger("spawn:" + facilityType);
    }

    public void onUnitMerged(String type, int newLevel) {
        matchTrigger("merge:any");
    }

    public void onUnitDismissedToPrince(String type) {
        matchTrigger("dismiss:any");
    }

    public void onLevelUp(int newLevel) {
        matchTrigger("levelup:any");
    }

    public void onMonsterSpawned(String type) {
        maybeShowTip("tip_combat");
    }

    /** panelId e.g. "explore", "raid", "inventory" — looks up tip "tip_<panelId>". */
    public void onPanelFirstOpened(String panelId) {
        maybeShowTip("tip_" + panelId);
    }

    // ── Persistence accessors (pulled by MergerRealmGame.saveGame()) ──

    public int[] getSaveState() {
        return new int[]{ completed ? 1 : 0, currentStep };
    }

    public Set<String> getTipsSeen() {
        return tipsSeen;
    }

    // ── Internal ──

    private void matchTrigger(String eventKey) {
        if (!active) return;
        TutorialStep step = getCurrentStep();
        if (step == null || step.type != StepType.INTERACTIVE) return;
        if (step.trigger == null || !step.trigger.equals(eventKey)) return;

        progressCount++;
        Gdx.app.log(TAG, "Tutorial step '" + step.id + "' progress "
            + progressCount + "/" + step.requiredCount);
        if (progressCount >= step.requiredCount) {
            advance();
        }
    }

    private void advance() {
        currentStep++;
        progressCount = 0;
        if (currentStep >= steps.size()) {
            active = false;
            completed = true;
            Gdx.app.log(TAG, "Tutorial completed");
        }
    }

    private void maybeShowTip(String tipId) {
        if (active) return;               // don't interrupt the main tutorial
        if (activeTipId != null) return;  // one tip on screen at a time
        if (tipsSeen.contains(tipId)) return;
        if (!tips.containsKey(tipId)) return;
        activeTipId = tipId;
    }

    // ── Data loading ──

    @SuppressWarnings("unchecked")
    private void loadDefinitions(JsonManager jsonManager) {
        String json = jsonManager.readRawJson("tutorial_steps");
        if (json == null) {
            Gdx.app.error(TAG, "tutorial_steps.json missing — tutorial disabled");
            return;
        }
        try {
            Map<String, Object> root = gson.fromJson(json,
                new TypeToken<Map<String, Object>>() {}.getType());
            if (root == null) return;

            Object stepsObj = root.get("steps");
            if (stepsObj instanceof List) {
                for (Object o : (List<?>) stepsObj) {
                    if (o instanceof Map) steps.add(parseStep((Map<String, Object>) o));
                }
            }
            Object tipsObj = root.get("tips");
            if (tipsObj instanceof List) {
                for (Object o : (List<?>) tipsObj) {
                    if (o instanceof Map) {
                        TutorialStep tip = parseStep((Map<String, Object>) o);
                        tips.put(tip.id, tip);
                    }
                }
            }
            Gdx.app.log(TAG, "Loaded " + steps.size() + " tutorial steps, " + tips.size() + " tips");
        } catch (Exception e) {
            Gdx.app.error(TAG, "Failed to parse tutorial_steps.json", e);
        }
    }

    private TutorialStep parseStep(Map<String, Object> m) {
        TutorialStep step = new TutorialStep();
        step.id = str(m, "id", "step");
        step.type = "interactive".equalsIgnoreCase(str(m, "type", "text"))
            ? StepType.INTERACTIVE : StepType.TEXT;
        step.title = str(m, "title", "");
        step.body = str(m, "body", "");
        step.trigger = str(m, "trigger", null);
        step.requiredCount = toInt(m, "requiredCount", 1);
        step.target = str(m, "target", null);
        return step;
    }

    private String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v != null ? v.toString() : def;
    }

    private int toInt(Map<String, Object> m, String key, int def) {
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        return def;
    }

    private void loadProgress(JsonManager jsonManager) {
        int[] t = jsonManager.loadArray("tutorial_state");
        if (t == null || t.length < 2) {
            completed = false;
            currentStep = 0;
        } else {
            completed = t[0] == 1;
            currentStep = Math.max(0, t[1]);
            if (currentStep >= steps.size()) {
                // Defensive: saved progress from a longer step list than
                // what's currently loaded — treat as completed rather than
                // index out of range.
                completed = true;
            }
        }
        Set<String> seen = jsonManager.loadStringSet("tutorial_tips_seen");
        if (seen != null) tipsSeen.addAll(seen);
    }
}
