package app.onda.mobile;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class PlaybackSnapshotTest {
    private static final String URL = "https://m.youtube.com/watch?v=aqz-KE-bpKQ";
    @Test public void roundTripPreservesPositionModesAndOrderWithoutAutoplay() {
        PlaybackSnapshot snapshot = new PlaybackSnapshot();
        snapshot.url = URL; snapshot.title = "Áudio"; snapshot.position = 42000;
        snapshot.shuffle = true; snapshot.repeat = PlaybackQueue.Repeat.ONE; snapshot.order = Arrays.asList(URL);
        String json = snapshot.encode();
        PlaybackSnapshot restored = PlaybackSnapshot.decode(json);
        assertEquals(URL, restored.url); assertEquals(42000, restored.position); assertEquals("Áudio", restored.title);
        assertTrue(restored.shuffle); assertEquals(PlaybackQueue.Repeat.ONE, restored.repeat); assertEquals(snapshot.order, restored.order);
        assertFalse(json.contains("autoplay")); assertFalse(json.contains("playing"));
    }
    @Test public void missingOrCorruptSnapshotDoesNotEraseLibrary() {
        assertNull(PlaybackSnapshot.decode("").url);
        assertNull(PlaybackSnapshot.decode("{not valid}").url);
        assertNull(PlaybackSnapshot.decode("{\"version\":99}").url);
    }
    @Test public void cannotRestoreExpiringOrArbitraryMediaUrls() {
        PlaybackSnapshot restored = PlaybackSnapshot.decode("{\"version\":1,\"url\":\"https://cdn.example/audio?token=secret\",\"position\":4000,\"order\":[\"file:///private\"],\"repeat\":\"unknown\"}");
        assertNull(restored.url); assertEquals(0, restored.position); assertTrue(restored.order.isEmpty());
        assertEquals(PlaybackQueue.Repeat.OFF, restored.repeat);
    }
    @Test public void sourceSelectionAlwaysPrefersValidLocalFile() {
        assertEquals(PlaybackPolicy.Source.LOCAL, PlaybackPolicy.source(true, true));
        assertEquals(PlaybackPolicy.Source.LOCAL, PlaybackPolicy.source(true, false));
        assertEquals(PlaybackPolicy.Source.NETWORK, PlaybackPolicy.source(false, true));
        assertEquals(PlaybackPolicy.Source.UNAVAILABLE, PlaybackPolicy.source(false, false));
    }
    @Test public void invalidOrCompletedPositionRestartsAtBeginning() {
        assertEquals(0, PlaybackPolicy.resumePosition(-1, 10000));
        assertEquals(0, PlaybackPolicy.resumePosition(10000, 10000));
        assertEquals(4200, PlaybackPolicy.resumePosition(4200, 10000));
    }
}
