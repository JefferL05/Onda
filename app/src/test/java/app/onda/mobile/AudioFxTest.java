package app.onda.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class AudioFxTest {
    @Test public void envelopeIsFullInTheMiddleAndFadesAtEdges() {
        assertEquals(0f, AudioFx.envelope(0, 100000, 5000), 0.001f);
        assertEquals(0.5f, AudioFx.envelope(2500, 100000, 5000), 0.001f);
        assertEquals(1f, AudioFx.envelope(50000, 100000, 5000), 0.001f);
        assertEquals(0.5f, AudioFx.envelope(97500, 100000, 5000), 0.001f);
        assertEquals(1f, AudioFx.envelope(50000, 100000, 0), 0.001f);
        assertEquals(1f, AudioFx.envelope(50000, 0, 5000), 0.001f);
    }

    @Test public void envelopeNeverExceedsHalfTheTrack() {
        // A track shorter than twice the fade cannot have a gap in the middle.
        float peak = AudioFx.envelope(1000, 4000, 6000);
        assertTrue(peak >= 0f && peak <= 1f);
    }
}
