package app.onda.mobile;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class LyricsSyncTest {
    @Test public void detectsTimestamps() {
        assertEquals(83000, LyricsStore.timestamp("[1:23] verso"));
        assertEquals(5000, LyricsStore.timestamp("[0:05]"));
        assertEquals(-1, LyricsStore.timestamp("sem marcação"));
        assertEquals(-1, LyricsStore.timestamp("[1:75] inválido"));
        assertEquals(-1, LyricsStore.timestamp("[2] sem segundos"));
        assertEquals("verso", LyricsStore.body("[1:23] verso"));
    }

    @Test public void parseSortsAndKeepsText() {
        List<LyricsStore.LyricLine> lines = LyricsStore.parse("[0:10] segunda\n[0:03] primeira");
        assertEquals(2, lines.size());
        assertEquals(3000, lines.get(0).at);
        assertEquals("primeira", lines.get(0).text);
        assertEquals(10000, lines.get(1).at);
    }

    @Test public void syncedOnlyWhenAtLeastOneLineHasTimestamp() {
        assertTrue(LyricsStore.synced("[0:01] a\nb"));
        assertFalse(LyricsStore.synced("a\nb"));
    }
}
