package app.onda.mobile;

import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.Virtualizer;

/**
 * Real audio processing on the playback session: multi-band equalizer with
 * calibrated presets, BassBoost, 3D Virtualizer, and an optional crossfade envelope.
 *
 * Everything degrades gracefully — some devices/emulators refuse to attach an
 * Equalizer, and the player must keep working. Nothing here is simulated: if
 * the effect cannot be created, the UI reports "indisponível" and stays honest.
 */
final class AudioFx {
    static final String[] PRESET_NAMES = {
            "Personalizado", "Graves Profundos", "Eletrônica/Synth",
            "Acústico & Voz", "Rock Dinâmico", "Flat / Neutro", "Espacial 3D"
    };

    static final short[][] PRESETS = {
            {0, 0, 0, 0, 0},             // Personalizado
            {600, 450, 50, -100, -200},  // Graves Profundos
            {500, 300, -50, 250, 600},   // Eletrônica/Synth
            {-200, 100, 500, 400, 100},  // Acústico & Voz
            {450, 200, -100, 300, 550},  // Rock Dinâmico
            {0, 0, 0, 0, 0},             // Flat / Neutro
            {200, 100, 200, 400, 500},   // Espacial 3D
    };

    private Equalizer equalizer;
    private LoudnessEnhancer loudness;
    private BassBoost bassBoost;
    private Virtualizer virtualizer;
    private int session;
    private boolean available;
    private boolean enabled = true;
    private int preset = 5; // Flat / Neutro
    private float boostDb = 0f;
    private short bassStrength = 0;
    private short virtualizerStrength = 0;

    boolean attach(int audioSession) {
        if (audioSession == 0 || audioSession == session) return available;
        release();
        session = audioSession;
        try {
            equalizer = new Equalizer(0, audioSession);
            equalizer.setEnabled(enabled);
            available = true;
            if (preset >= 0) applyPreset(preset);
        } catch (RuntimeException e) {
            equalizer = null;
            available = false;
        }
        try {
            loudness = new LoudnessEnhancer(audioSession);
            loudness.setEnabled(enabled);
            loudness.setTargetGain((int) Math.max(0, boostDb) * 100);
        } catch (RuntimeException ignored) { }
        try {
            bassBoost = new BassBoost(0, audioSession);
            bassBoost.setEnabled(enabled && bassStrength > 0);
            if (bassStrength > 0) bassBoost.setStrength(bassStrength);
        } catch (RuntimeException ignored) { bassBoost = null; }
        try {
            virtualizer = new Virtualizer(0, audioSession);
            virtualizer.setEnabled(enabled && virtualizerStrength > 0);
            if (virtualizerStrength > 0) virtualizer.setStrength(virtualizerStrength);
        } catch (RuntimeException ignored) { virtualizer = null; }
        return available;
    }

    boolean available() { return available; }

    void setEnabled(boolean value) {
        enabled = value;
        try { if (equalizer != null) equalizer.setEnabled(value); } catch (RuntimeException ignored) { }
        try { if (loudness != null) loudness.setEnabled(value); } catch (RuntimeException ignored) { }
        try { if (bassBoost != null) bassBoost.setEnabled(value && bassStrength > 0); } catch (RuntimeException ignored) { }
        try { if (virtualizer != null) virtualizer.setEnabled(value && virtualizerStrength > 0); } catch (RuntimeException ignored) { }
    }

    boolean isEnabled() { return enabled; }

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
        preset = 0; // Personalizado
    }

    int bandLevel(int band) {
        try { return equalizer == null ? 0 : equalizer.getBandLevel((short) band); } catch (RuntimeException e) { return 0; }
    }

    int bandFreq(int band) {
        if (equalizer != null) {
            try {
                return equalizer.getCenterFreq((short) band) / 1000;
            } catch (RuntimeException ignored) { }
        }
        int[] fallback = {60, 230, 910, 3600, 14000};
        return band < fallback.length ? fallback[band] : 1000;
    }

    int preset() { return preset; }

    void setBoost(float decibels) {
        boostDb = Math.max(0f, Math.min(6f, decibels));
        try {
            if (loudness == null && session != 0) loudness = new LoudnessEnhancer(session);
            if (loudness != null) {
                loudness.setEnabled(enabled);
                loudness.setTargetGain((int) (boostDb * 100));
            }
        } catch (RuntimeException ignored) { }
    }

    float boost() { return boostDb; }

    void setBassBoost(short strength) {
        bassStrength = (short) Math.max(0, Math.min(1000, strength));
        if (bassBoost != null) {
            try {
                bassBoost.setEnabled(enabled && bassStrength > 0);
                bassBoost.setStrength(bassStrength);
            } catch (RuntimeException ignored) { }
        }
    }

    short getBassBoost() { return bassStrength; }

    void setVirtualizer(short strength) {
        virtualizerStrength = (short) Math.max(0, Math.min(1000, strength));
        if (virtualizer != null) {
            try {
                virtualizer.setEnabled(enabled && virtualizerStrength > 0);
                virtualizer.setStrength(virtualizerStrength);
            } catch (RuntimeException ignored) { }
        }
    }

    short getVirtualizer() { return virtualizerStrength; }

    void release() {
        try { if (equalizer != null) { equalizer.setEnabled(false); equalizer.release(); } } catch (RuntimeException ignored) { }
        try { if (loudness != null) { loudness.setEnabled(false); loudness.release(); } } catch (RuntimeException ignored) { }
        try { if (bassBoost != null) { bassBoost.setEnabled(false); bassBoost.release(); } } catch (RuntimeException ignored) { }
        try { if (virtualizer != null) { virtualizer.setEnabled(false); virtualizer.release(); } } catch (RuntimeException ignored) { }
        equalizer = null;
        loudness = null;
        bassBoost = null;
        virtualizer = null;
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
