package com.jipelski.mergerrealm.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;

import com.jipelski.mergerrealm.database.JsonManager;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads and caches short one-shot SFX cues, mirroring SpriteManager's
 * lazy-load-and-cache shape (see SpriteManager's own header comment): a
 * fixed set of keys, resolved to files on demand, with a missing file
 * treated as a silent no-op rather than a crash — the audio analogue of
 * SpriteManager's magenta-fallback texture.
 *
 * Resolution order per key: sfx/&lt;key&gt;.ogg, then sfx/&lt;key&gt;.wav — so a
 * real .ogg can be dropped in later to replace a placeholder .wav with no
 * code change. See assets/sfx/README.md for the full placeholder-to-real-audio
 * handoff note.
 *
 * Persistence: this class loads its own mute/volume state at construction,
 * but does NOT write to disk itself — same pattern as TutorialManager (see
 * its persistence javadoc): it exposes getSaveState(), and
 * MergerRealmGame.saveGame() persists it on the existing autosave cadence.
 * Save key "audio_settings" — never rename, see CLAUDE.md's persistence rules.
 * No mute UI exists yet; this only wires the manager + persistence so a
 * future toggle has something to call.
 */
public class SoundManager {

    private static final String TAG = "SoundManager";
    private static final String SFX_DIR = "sfx/";

    public enum SfxId {
        MERGE("merge"),
        SPAWN("spawn"),
        DISMISS("dismiss"),
        LEVEL_UP("levelup"),
        MONSTER("monster"),
        REWARD("reward"),
        CLICK("click");

        final String key;
        SfxId(String key) { this.key = key; }
    }

    private final Map<SfxId, Sound> cache = new EnumMap<>(SfxId.class);
    // Confirmed-missing ids, kept separate from `cache` so dispose()'s
    // Sound.dispose() loop can't be handed a null/foreign entry — same
    // separation SpriteManager keeps between its `cache` and `missing` set.
    private final Set<SfxId> missing = new HashSet<>();

    private boolean muted;
    private float masterVolume = 1.0f;
    // Set around offline-catch-up processing so a burst of merges/spawns/
    // level-ups replayed from a long-offline gap doesn't play a wall of
    // sounds on launch — see MergerRealmGame.processOfflineProgress() call
    // sites.
    private boolean suppressed = false;

    public SoundManager(JsonManager jsonManager) {
        loadSettings(jsonManager);
    }

    /** Call once at startup, after loadSettings — preloads every SfxId. */
    public void load() {
        for (SfxId id : SfxId.values()) {
            loadOne(id);
        }
    }

    private void loadOne(SfxId id) {
        FileHandle fh = Gdx.files.internal(SFX_DIR + id.key + ".ogg");
        if (!fh.exists()) {
            fh = Gdx.files.internal(SFX_DIR + id.key + ".wav");
        }
        if (fh.exists()) {
            cache.put(id, Gdx.audio.newSound(fh));
        } else {
            missing.add(id);
            Gdx.app.log(TAG, "SFX not found: " + id.key + ".ogg/.wav — will play silently");
        }
    }

    /** Plays a cue by id. No-ops silently if muted, suppressed, or the file is missing. */
    public void play(SfxId id) {
        if (id == null || muted || suppressed) return;
        Sound sound = cache.get(id);
        if (sound == null) {
            if (!missing.contains(id)) {
                // Wasn't preloaded (shouldn't normally happen — load() covers
                // every SfxId) — try once now, same lazy-resolve fallback
                // SpriteManager uses for ids outside its usual convention.
                loadOne(id);
                sound = cache.get(id);
            }
            if (sound == null) return;
        }
        sound.play(masterVolume);
    }

    public void setSuppressed(boolean suppressed) { this.suppressed = suppressed; }
    public boolean isSuppressed() { return suppressed; }

    public boolean isMuted() { return muted; }
    public void setMuted(boolean muted) { this.muted = muted; }

    public float getMasterVolume() { return masterVolume; }
    public void setMasterVolume(float volume) {
        this.masterVolume = Math.max(0f, Math.min(1f, volume));
    }

    /** Packed as {muted(0/1), volume*100} — same int[]-via-saveArray convention as gold_state. */
    public int[] getSaveState() {
        return new int[]{ muted ? 1 : 0, Math.round(masterVolume * 100) };
    }

    private void loadSettings(JsonManager jsonManager) {
        int[] saved = jsonManager.loadArray("audio_settings");
        if (saved == null || saved.length < 2) {
            muted = false;
            masterVolume = 1.0f;
            return;
        }
        muted = saved[0] == 1;
        masterVolume = Math.max(0f, Math.min(1f, saved[1] / 100f));
    }

    public void dispose() {
        for (Sound sound : cache.values()) {
            sound.dispose();
        }
        cache.clear();
        missing.clear();
    }
}
