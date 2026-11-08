package app.onda.mobile;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class DiscoveryTest {
    private static final String SEED = "https://m.youtube.com/watch?v=aaaaaaaaaaa";
    private static LibraryStore.Track track(String id, String artist, String genre, long duration) {
        return new LibraryStore.Track("Song " + id, "https://m.youtube.com/watch?v=" + id, artist, "", duration, 0, "", genre);
    }

    @Test public void aroundPrefersGenreThenArtistThenRelated() {
        LibraryStore.Track seed = track(SEED.substring(SEED.length() - 11), "Artista", "MPB", 200);
        List<LibraryStore.Track> candidates = Arrays.asList(
                track("bbbbbbbbbbb", "Outro", "MPB", 200),
                track("ccccccccccc", "Artista", "", 200),
                track("ddddddddddd", "Outro", "", 200));
        Set<String> related = new HashSet<>(Collections.singletonList("https://m.youtube.com/watch?v=ddddddddddd"));
        List<LibraryStore.Track> picks = Discovery.around(seed, candidates, related, new ArrayList<>(),
                new HashSet<>(), new HashSet<>(), 5);
        assertEquals(3, picks.size());
        assertEquals("https://m.youtube.com/watch?v=bbbbbbbbbbb", picks.get(0).url); // genre wins
    }

    @Test public void aroundExcludesSeedSkipsAndRecent() {
        LibraryStore.Track seed = track("aaaaaaaaaaa", "Artista", "MPB", 200);
        LibraryStore.Track same = track("bbbbbbbbbbb", "Artista", "", 200);
        List<LibraryStore.Track> picks = Discovery.around(seed, Arrays.asList(seed, same),
                Collections.emptySet(), Collections.singletonList(same), Collections.emptySet(),
                Collections.emptySet(), 5);
        assertTrue(picks.isEmpty());
    }

    @Test public void diversifyNeverRepeatsOneArtistThreeTimes() {
        List<Discovery.Pick> picks = new ArrayList<>();
        for (int i = 0; i < 5; i++) picks.add(new Discovery.Pick(track("bbbbbbbbbb" + i, "Um", "", 200), 1, "", 50));
        picks.add(new Discovery.Pick(track("ccccccccccc", "Dois", "", 200), 1, "", 40));
        picks.add(new Discovery.Pick(track("ddddddddddd", "Tres", "", 200), 1, "", 30));
        List<LibraryStore.Track> result = Discovery.diversifyPicks(picks, 6);
        int one = 0;
        for (LibraryStore.Track t : result) if ("Um".equals(t.artist())) one++;
        assertTrue("one artist must not dominate a six-song mix", one <= 4);
        assertEquals("still fills the mix", 6, result.size());
    }

    @Test public void shortNameTrimsAtArtistDash() {
        assertEquals("Song", Discovery.shortName(new LibraryStore.Track("Song - Artist", SEED, "", "", 1)));
        assertTrue(Discovery.shortName(new LibraryStore.Track(
                "a very long title that goes on and on and on forever", SEED, "", "", 1)).length() <= 40);
    }

    @Test public void rankRewardsQueryCoverageAndSongLength() {
        YoutubeSearch.Result good = result("Artista - Amor de Verão", 210);
        YoutubeSearch.Result podcast = result("Amor de Verão podcast episódio 200", 5400);
        YoutubeSearch.Result unrelated = result("Outra coisa", 210);
        assertTrue(YoutubeSearch.rank(good, "amor de verão") > YoutubeSearch.rank(podcast, "amor de verão"));
        assertTrue(YoutubeSearch.rank(good, "amor de verão") > YoutubeSearch.rank(unrelated, "amor de verão"));
    }

    private YoutubeSearch.Result result(String title, long duration) {
        return new YoutubeSearch.Result("x", title, "Artista", SEED, "", duration);
    }
}
