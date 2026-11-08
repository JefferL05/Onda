package app.onda.mobile;

import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;

/**
 * Real audio processing on the playback session: a 5-band equalizer with
 * presets and an optional crossfade envelope.
 *
 * Everything degrades gracefully — some devices/emulators refuse to attach an
 * Equalizer, and the player must keep working. Nothing here is simulated: if
 * the effect cannot be created, the UI reports "indisponível" and stays honest.
 */
final class AudioFx {
    static final String[] PRESET_NAMES = {"Flat", "Graves", "Voz", "Rock", "Eletrônica", "Jazz"};
    static final short[][] PRESETS = {
            {0, 0, 0, 0, 0},
            {600, 400, 0, -100, -200},
            {-200, 200, 500, 300, 0},
            {400, 200, -100, 300, 500},
            {500, 200, 0, 200, 600},
            {300, 100, 100, 200, 300},
    };

    private Equalizer equalizer;
    private LoudnessEnhancer loudness;
    private int session;
    private boolean available;
    private int preset = -1;
    private float boostDb = 0f;

    boolean attach(int audioSession) {
        if (audioSession == 0 || audioSession == session) return available;
        release();
        session = audioSession;
        try {
            equalizer = new Equalizer(0, audioSession);
            equalizer.setEnabled(true);
            available = true;
            if (preset >= 0) applyPreset(preset);
        } catch (RuntimeException e) {
            equalizer = null;
            available = false;
        }
        try {
            loudness = new LoudnessEnhancer(audioSession);
            loudness.setEnabled(true);
            loudness.setTargetGain((int) Math.max(0, boostDb) * 100);
        } catch (RuntimeException ignored) { }
        return available;
    }

    boolean available() { return available; }

    /** Number of bands the device actually exposes (usually 5), 0 when unavailable. */
    int bands() {
        try { return equalizer == null ? 0 : equalizer.getNumberOfBands(); } catch (RuntimeException e) { return 0; }
    }

    int[] bandRange(int band) {
        try {
            int[] range = equalizer.getBandFreqRange((short) band);
            return new int[]{range[0], range[1]};
        } catch (RuntimeException e) { return new int[]{0, 0}; }
    }

    int levelRangeMillibel() {
        try { return equalizer == null ? 1500 : equalizer.getBandLevelRange()[1]; } catch (RuntimeException e) { return 1500; }
    }

    void applyPreset(int index) {
        preset = Math.max(0, Math.min(PRESET_NAMES.length - 1, index));
        if (equalizer == null) return;
        try {
            int count = Math.min(equalizer.getNumberOfBands(), PRESETS[preset].length);
            for (short band = 0; band < count; band++) {
                equalizer.setBandLevel(band, (short) Math.max(PRESETS[preset][band], -levelRangeMillibel()));
            }
        } catch (RuntimeException ignored) { }
    }

    void setBand(int band, int millibel) {
        if (equalizer == null) return;
        try {
            equalizer.setBandLevel((short) band, (short) Math.max(-1500, Math.min(levelRangeMillibel(), millibel)));
        } catch (RuntimeException ignored) { }
        preset = -1;
    }

    int bandLevel(int band) {
        try { return equalizer == null ? 0 : equalizer.getBandLevel((short) band); } catch (RuntimeException e) { return 0; }
    }

    int preset() { return preset; }

    void setBoost(float decibels) {
        boostDb = Math.max(0f, Math.min(6f, decibels));
        try {
            if (loudness == null && session != 0) loudness = new LoudnessEnhancer(session);
            if (loudness != null) {
                loudness.setEnabled(true);
                loudness.setTargetGain((int) (boostDb * 100));
            }
        } catch (RuntimeException ignored) { }
    }

    float boost() { return boostDb; }

    void release() {
        try { if (equalizer != null) { equalizer.setEnabled(false); equalizer.release(); } } catch (RuntimeException ignored) { }
        try { if (loudness != null) { loudness.setEnabled(false); loudness.release(); } } catch (RuntimeException ignored) { }
        equalizer = null;
        loudness = null;
        session = 0;
        available = false;
    }

    /** Linear volume for a crossfade envelope: 1 at centre, 0 at the edges. */
    static float envelope(long positionMs, long durationMs, long fadeMs) {
        if (durationMs <= 0 || fadeMs <= 0) return 1f;
        fadeMs = Math.min(fadeMs, durationMs / 2);
        if (positionMs < fadeMs) return clamp(positionMs / (float) fadeMs);
        if (positionMs > durationMs - fadeMs) return clamp((durationMs - positionMs) / (float) fadeMs);
        return 1f;
    }

    static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
}
