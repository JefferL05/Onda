package app.onda.mobile;

import java.util.*;

/** Transparent ranking: actual metadata and declared related items, never fabricated genre/BPM. */
final class RadioPlanner {
    static List<LibraryStore.Track> choose(LibraryStore.Track seed, List<LibraryStore.Track> candidates,
            Set<String> related, List<LibraryStore.Track> recent, Set<String> liked, Set<String> skipped, int limit) {
        Set<String> excluded = new HashSet<>(skipped); excluded.add(seed.url);
        for (LibraryStore.Track track : recent) excluded.add(track.url);
        Map<String,LibraryStore.Track> unique = new LinkedHashMap<>();
        for (LibraryStore.Track track : candidates) {
            if (track == null || excluded.contains(track.url) || UrlPolicy.canonicalVideo(track.url) == null) continue;
            if (track.durationSec == 0 || track.durationSec > 1800) continue;
            if (score(seed, track, related, liked) > 0) unique.putIfAbsent(track.url, track);
        }
        List<LibraryStore.Track> ranked = new ArrayList<>(unique.values());
        ranked.sort((a,b) -> Integer.compare(score(seed,b,related,liked),score(seed,a,related,liked)));
        List<LibraryStore.Track> result = new ArrayList<>();
        Map<String,Integer> counts = new HashMap<>();
        String last = artist(seed), before = recent.isEmpty() ? "" : artist(recent.get(0));
        while (!ranked.isEmpty() && result.size() < limit) {
            LibraryStore.Track chosen = null;
            for (LibraryStore.Track candidate : ranked) {
                String artist = artist(candidate);
                if (!artist.isEmpty() && (counts.getOrDefault(artist,0) >= 2 || (artist.equals(last) && artist.equals(before)))) continue;
                chosen = candidate; break;
            }
            if (chosen == null) break; // Better to stop than claim diversity with repeated artists.
            ranked.remove(chosen); result.add(chosen);
            String artist = artist(chosen); counts.put(artist, counts.getOrDefault(artist,0)+1);
            before = last; last = artist;
        }
        return result;
    }
    static String artist(LibraryStore.Track t) { return LibraryQuery.normalized(t.uploader).replaceFirst(" - topic$", ""); }
    private static int score(LibraryStore.Track seed, LibraryStore.Track candidate, Set<String> related, Set<String> liked) {
        boolean sameArtist = !artist(seed).isEmpty() && artist(seed).equals(artist(candidate));
        boolean sameGenre = !seed.genre.isEmpty() && LibraryQuery.normalized(seed.genre).equals(LibraryQuery.normalized(candidate.genre));
        if (!sameArtist && !sameGenre && !related.contains(candidate.url)) return 0;
        return (sameArtist ? 60 : 0) + (sameGenre ? 100 : 0) + (related.contains(candidate.url) ? 30 : 0) + (liked.contains(candidate.url) ? 10 : 0);
    }
}
