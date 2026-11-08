package app.onda.mobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * On-device discovery: mixes, radio and "related" picks derived from what the
 * user actually played.
 *
 * This is deliberately *not* an opaque model. Every recommendation carries a
 * reason ("porque você ouviu X", "artista que você segue"), ranking is
 * deterministic, and it is testable. Signals: recent plays weighted by how
 * much of each track was heard, favourites, follows, and skips (negative).
 * Related items from the source are a bonus signal, never invented metadata.
 */
final class Discovery {
    static final int REASON_LIBRARY = 0;
    static final int REASON_ARTIST = 1;
    static final int REASON_RELATED = 2;
    static final int REASON_AFFINITY = 3;
    static final int REASON_FRESH = 4;

    static final class Pick {
        final LibraryStore.Track track;
        final int reason;
        final String because;
        final int score;

        Pick(LibraryStore.Track track, int reason, String because, int score) {
            this.track = track;
            this.reason = reason;
            this.because = because;
            this.score = score;
        }
    }

    static final class Mix {
        final String title;
        final String subtitle;
        final List<LibraryStore.Track> tracks;

        Mix(String title, String subtitle, List<LibraryStore.Track> tracks) {
            this.title = title;
            this.subtitle = subtitle;
            this.tracks = tracks;
        }
    }

    private Discovery() {}

    /** Library candidates ranked for a seed track. Used by radio and "related". */
    static List<LibraryStore.Track> around(LibraryStore.Track seed, List<LibraryStore.Track> candidates,
                             Set<String> related, List<LibraryStore.Track> recent,
                             Set<String> liked, Set<String> skipped, int limit) {
        Set<String> excluded = new HashSet<>(skipped);
        if (seed != null) excluded.add(seed.url);
        for (LibraryStore.Track track : recent) excluded.add(track.url);

        List<Pick> picks = new ArrayList<>();
        String seedArtist = seed == null ? "" : artistKey(seed);
        String seedGenre = seed == null ? "" : LibraryQuery.normalized(seed.genre);

        for (LibraryStore.Track candidate : candidates) {
            if (candidate == null || excluded.contains(candidate.url)) continue;
            if (UrlPolicy.canonicalVideo(candidate.url) == null) continue;
            if (candidate.durationSec == 0 || candidate.durationSec > 1800) continue;
            String artist = artistKey(candidate);
            boolean sameArtist = !seedArtist.isEmpty() && seedArtist.equals(artist);
            boolean sameGenre = !seedGenre.isEmpty() && seedGenre.equals(LibraryQuery.normalized(candidate.genre));
            boolean isRelated = related.contains(candidate.url);
            if (!sameArtist && !sameGenre && !isRelated) continue;
            int score = (sameArtist ? 60 : 0) + (sameGenre ? 100 : 0) + (isRelated ? 34 : 0)
                    + (liked.contains(candidate.url) ? 12 : 0);
            int reason = sameGenre ? REASON_AFFINITY : sameArtist ? REASON_ARTIST : REASON_RELATED;
            String because = sameGenre ? "Mesmo gênero" : sameArtist ? "Mesmo artista" : "Relacionado a " + shortName(seed);
            picks.add(new Pick(candidate, reason, because, score));
        }
        picks.sort((a, b) -> Integer.compare(b.score, a.score));
        return diversifyPicks(picks, limit);
    }

    /** Daily Mix: strongest artists first, at most two tracks each, never a repeat of the last hour. */
    static Mix dailyMix(LibraryStore library, int limit) {
        Map<String, Integer> affinity = library.artistAffinity(45);
        List<LibraryStore.Track> catalog = library.catalog();
        List<Pick> picks = new ArrayList<>();
        Set<String> recentUrls = new HashSet<>();
        for (LibraryStore.Track track : library.recentlyPlayed(6)) recentUrls.add(track.url);
        Set<String> liked = new HashSet<>();
        for (LibraryStore.Track track : library.tracks(LibraryStore.FAVORITES)) liked.add(track.url);
        Set<String> skipped = library.dislikedUrls();
        for (LibraryStore.Track track : catalog) {
            if (skipped.contains(track.url) || recentUrls.contains(track.url)) continue;
            int weight = affinity.getOrDefault(track.artist(), 0);
            int listens = library.listenCount(track.url);
            int score = weight * 10 + listens * 6 + (liked.contains(track.url) ? 18 : 0);
            if (score <= 0) continue;
            picks.add(new Pick(track, REASON_AFFINITY,
                    weight > 0 ? "Você ouve " + shortName(track) + " com frequência" : "Da sua biblioteca", score));
        }
        picks.sort((a, b) -> Integer.compare(b.score, a.score));
        List<LibraryStore.Track> tracks = diversifyPicks(picks, limit);
        String top = strongestArtist(affinity);
        return new Mix("Daily Mix", top.isEmpty() ? "Do seu gosto" : "Baseado em " + top, tracks);
    }

    /** Descobertas: songs you haven't played yet, from artists you already like. */
    static Mix discoveries(LibraryStore library, List<LibraryStore.Track> freshCandidates, int limit) {
        Map<String, Integer> affinity = library.artistAffinity(60);
        Set<String> heard = new HashSet<>();
        for (LibraryStore.Play play : library.plays()) heard.add(play.url);
        List<Pick> picks = new ArrayList<>();
        for (LibraryStore.Track track : freshCandidates) {
            if (heard.contains(track.url)) continue;
            int weight = affinity.getOrDefault(track.artist(), 0);
            if (weight == 0) continue;
            String because = "Novo para você · " + shortName(track);
            picks.add(new Pick(track, REASON_FRESH, because, weight * 10 + 5));
        }
        picks.sort((a, b) -> Integer.compare(b.score, a.score));
        return new Mix("Descobertas da semana", "Músicas novas de artistas que você ouve",
                diversifyPicks(picks, limit));
    }

    /** "Porque você ouviu X": related tracks around the most recent strong listen. */
    static Mix becauseYouListened(LibraryStore library, List<LibraryStore.Track> relatedFromSeed, int limit) {
        List<LibraryStore.Track> recent = library.recentlyPlayed(1);
        if (recent.isEmpty()) return new Mix("Porque você ouviu", "", new ArrayList<>());
        LibraryStore.Track seed = recent.get(0);
        List<LibraryStore.Track> picks = around(seed, library.catalog(), urls(relatedFromSeed), recent,
                urls(library.tracks(LibraryStore.FAVORITES)), library.dislikedUrls(), limit);
        return new Mix("Porque você ouviu " + shortName(seed), "Sugestões a partir dessa faixa", picks);
    }

    /** Keeps a mix from becoming five songs by the same artist back to back. */
    static List<LibraryStore.Track> diversifyPicks(List<Pick> picks, int limit) {
        Map<String, Integer> counts = new HashMap<>();
        List<LibraryStore.Track> result = new ArrayList<>();
        for (Pick pick : picks) {
            if (result.size() >= limit) break;
            String artist = artistKey(pick.track);
            int used = counts.getOrDefault(artist, 0);
            if (!artist.isEmpty() && used >= 2) continue;
            counts.put(artist, used + 1);
            result.add(pick.track);
        }
        // If diversity starved the list (a library with one artist), fill the
        // remainder with a looser cap rather than hand back a two-song mix.
        Map<String, Integer> relaxed = new HashMap<>();
        for (LibraryStore.Track track : result) {
            String artist = artistKey(track);
            relaxed.put(artist, relaxed.getOrDefault(artist, 0) + 1);
        }
        for (Pick pick : picks) {
            if (result.size() >= limit) break;
            if (result.contains(pick.track)) continue;
            String artist = artistKey(pick.track);
            if (!artist.isEmpty() && relaxed.getOrDefault(artist, 0) >= 4) continue;
            relaxed.put(artist, relaxed.getOrDefault(artist, 0) + 1);
            result.add(pick.track);
        }
        return result;
    }

    static List<LibraryStore.Track> tracks(List<Pick> picks) {
        List<LibraryStore.Track> result = new ArrayList<>();
        for (Pick pick : picks) result.add(pick.track);
        return result;
    }

    static Set<String> urls(List<LibraryStore.Track> tracks) {
        Set<String> result = new HashSet<>();
        for (LibraryStore.Track track : tracks) result.add(track.url);
        return result;
    }

    static String artistKey(LibraryStore.Track track) {
        return LibraryQuery.normalized(track.artist()).trim();
    }

    static String shortName(LibraryStore.Track track) {
        String name = track.title == null ? "" : track.title;
        int dash = name.indexOf(" - ");
        if (dash > 2 && dash < 40) name = name.substring(0, dash);
        return name.length() > 40 ? name.substring(0, 38) + "…" : name;
    }

    private static String strongestArtist(Map<String, Integer> affinity) {
        String best = "";
        int bestWeight = 0;
        for (Map.Entry<String, Integer> entry : affinity.entrySet()) {
            if (entry.getValue() > bestWeight) { bestWeight = entry.getValue(); best = entry.getKey(); }
        }
        return best;
    }

    /** Genre shelves for Explore, derived from what's actually in the library. */
    static Map<String, List<LibraryStore.Track>> shelves(List<LibraryStore.Track> catalog) {
        Map<String, List<LibraryStore.Track>> result = new LinkedHashMap<>();
        for (LibraryStore.Track track : catalog) {
            String genre = track.genre.trim();
            if (genre.isEmpty()) continue;
            result.computeIfAbsent(genre, k -> new ArrayList<>()).add(track);
        }
        // Deterministic ordering keeps the shelves stable between visits.
        List<String> keys = new ArrayList<>(result.keySet());
        Collections.sort(keys);
        Map<String, List<LibraryStore.Track>> ordered = new LinkedHashMap<>();
        for (String key : keys) ordered.put(key, result.get(key));
        return ordered;
    }
}
