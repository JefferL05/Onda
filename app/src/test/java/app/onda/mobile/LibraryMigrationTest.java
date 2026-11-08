package app.onda.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Upgrading must never destroy the library. These tests write the exact JSON
 * the 0.9.0 build produced and read it back through the new store.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 26)
public class LibraryMigrationTest {
    private static final String A = "https://m.youtube.com/watch?v=aqz-KE-bpKQ";
    private static final String B = "https://m.youtube.com/watch?v=dQw4w9WgXcQ&list=x&t=4";
    private Context context;

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        for (String prefs : new String[]{"library", "offline", "playback", "lyrics"})
            context.getSharedPreferences(prefs, 0).edit().clear().commit();
    }

    @Test public void legacyLibraryUpgradesWithoutLosingAnything() {
        SharedPreferences prefs = context.getSharedPreferences("library", 0);
        prefs.edit()
            .putString("favorites", "[{\"title\":\"Favorita\",\"url\":\"" + A + "\",\"uploader\":\"Artista\","
                    + "\"thumbnail\":\"https://i.ytimg.com/a.jpg\",\"durationSec\":200,\"addedAt\":5}]")
            .putString("queue", "[{\"title\":\"Na fila\",\"url\":\"" + A + "\",\"uploader\":\"\",\"thumbnail\":\"\","
                    + "\"durationSec\":-1,\"addedAt\":0}]")
            .putString("playlists", "[\"Minha lista\"]")
            .putString("playlist:Minha lista", "[{\"title\":\"Faixa\",\"url\":\"" + B + "\"}]")
            .putString("meta:Minha lista", "{\"description\":\"desc\",\"cover\":\"\",\"favorite\":true}")
            .putString("origin:" + A, "manual")
            .putLong("skip:" + A, System.currentTimeMillis())
            .putString("recent_searches", "[\"djavan\"]")
            .putString("queue_context", "Minha lista")
            .commit();

        LibraryStore store = new LibraryStore(context);
        assertEquals(1, store.tracks(LibraryStore.FAVORITES).size());
        assertEquals("Favorita", store.tracks(LibraryStore.FAVORITES).get(0).title);
        assertEquals(1, store.tracks(LibraryStore.QUEUE).size());
        assertEquals(1, store.playlists().size());
        assertEquals(1, store.tracks(LibraryStore.playlistKey("Minha lista")).size());
        assertTrue(store.playlist("Minha lista").favorite);
        assertEquals("manual", store.origin(A));
        assertTrue(store.recentlySkipped(A));
        assertEquals("djavan", store.recentSearches().get(0));
        assertEquals("Minha lista", store.contextName());
        assertEquals(2, prefs.getInt("schema", 0));
    }

    @Test public void classicWatchUrlWithExtraParamsIsCanonicalisedNotDropped() {
        List<LibraryStore.Track> tracks = new LibraryStore(context).tracks(LibraryStore.FAVORITES);
        SharedPreferences prefs = context.getSharedPreferences("library", 0);
        prefs.edit().putString("favorites", "[{\"title\":\"B\",\"url\":\"" + B + "\"}]").commit();
        LibraryStore store = new LibraryStore(context);
        List<LibraryStore.Track> favorites = store.tracks(LibraryStore.FAVORITES);
        assertEquals(1, favorites.size());
        assertEquals("https://m.youtube.com/watch?v=dQw4w9WgXcQ", favorites.get(0).url);
        assertTrue(tracks.isEmpty());
    }

    @Test public void corruptJsonLeavesTheOtherCollectionsIntact() {
        SharedPreferences prefs = context.getSharedPreferences("library", 0);
        prefs.edit()
            .putString("favorites", "{not an array}")
            .putString("playlists", "[\"Boa\"]")
            .commit();
        LibraryStore store = new LibraryStore(context);
        assertTrue(store.tracks(LibraryStore.FAVORITES).isEmpty());
        assertEquals(1, store.playlists().size());
        // The corrupt value itself is preserved on disk for diagnosis/recovery.
        assertEquals("{not an array}", prefs.getString("favorites", null));
    }

    @Test public void schemaMarkerIsAdditiveOnly() {
        SharedPreferences prefs = context.getSharedPreferences("library", 0);
        prefs.edit().putString("favorites", "[]").commit();
        new LibraryStore(context);
        assertEquals("[]", prefs.getString("favorites", null));
        assertEquals(2, prefs.getInt("schema", 0));
    }
}
