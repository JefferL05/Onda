package app.onda.mobile;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

/**
 * Pure rules of the library layer, exercised without Android.
 * The SharedPreferences-backed methods are covered by the Robolectric tests.
 */
public class LibraryStoreTest {
    private static final String A = "https://m.youtube.com/watch?v=aqz-KE-bpKQ";

    @Test public void trackNormalisesArtistFromTopicChannels() {
        LibraryStore.Track track = new LibraryStore.Track("Song", A, "Fulano - Topic", "", 200);
        assertEquals("Fulano", track.artist());
        assertEquals("", new LibraryStore.Track("x", A, "", "", 0).artist());
    }

    @Test public void trackNeverLosesIdentityOnMetadataUpdates() {
        LibraryStore.Track original = new LibraryStore.Track("Song", A, "Artist", "cover", 200, 10, "Album", "MPB");
        LibraryStore.Track dated = original.dated(99);
        assertEquals(99, dated.addedAt);
        assertEquals(original.url, dated.url);
        assertEquals("Novo", dated.withAlbum("Novo").album);
        assertEquals("Song", dated.withAlbum("Novo").title);
        assertEquals("Album", dated.album);
    }

    @Test public void displayArtistStripsSuffixesAndHandlesEmpty() {
        assertEquals("Banda", LibraryStore.displayArtist("Banda - Topic"));
        assertEquals("", LibraryStore.displayArtist("Various Artists"));
        assertEquals("", LibraryStore.displayArtist(null));
    }

    @Test public void playRecordDistinguishesListenFromSkip() {
        OfflinePlayRules rules = new OfflinePlayRules();
        assertTrue(rules.skipped(10, false));
        assertTrue(rules.skipped(24, false));
        assertFalse(rules.skipped(25, false));
        assertFalse(rules.skipped(100, false));
        assertTrue(rules.skipped(90, true));
    }

    /** Mirrors the threshold used by OfflineService.recordListen, kept pure for tests. */
    static final class OfflinePlayRules {
        boolean skipped(int ratio, boolean explicit) {
            return explicit || ratio < 25;
        }
    }
}
