package app.onda.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * On-device library.
 *
 * Single source of truth for favourites, playlists, history, queue, followed
 * artists and listening behaviour. Every collection is a JSON array under a
 * SharedPreferences key, parsed once into memory and invalidated on write —
 * the previous version re-parsed JSON on every call, which made large
 * playlists quadratic.
 *
 * All legacy keys ("favorites", "history", "queue", "playlist:<name>",
 * "playlists", "meta:<name>", "origin:*", "skip:*", "recent_searches",
 * "manual_pending", "queue_context") keep their exact format, so an upgrade
 * installs over the previous version without touching user data. The schema
 * marker is additive only.
 */
final class LibraryStore {
    static final String FAVORITES = "favorites";
    static final String HISTORY = "history";
    static final String QUEUE = "queue";
    static final String RECENT = "recent";

    private static final int SCHEMA = 2;
    private static final int MAX_HISTORY = 200;
    private static final int MAX_RECENT = 100;
    private static final int MAX_PLAYS = 800;

    static final class Track {
        final String title, url, uploader, thumbnail, album, genre;
        final long durationSec, addedAt;

        Track(String title, String url) { this(title, url, "", "", -1); }

        Track(String title, String url, String uploader, String thumbnail, long durationSec) {
            this(title, url, uploader, thumbnail, durationSec, 0, "", "");
        }

        Track(String title, String url, String uploader, String thumbnail, long durationSec,
              long addedAt, String album, String genre) {
            this.title = title == null || title.trim().isEmpty() ? "Áudio" : title;
            this.url = url;
            this.uploader = uploader == null ? "" : uploader;
            this.thumbnail = thumbnail == null ? "" : thumbnail;
            this.durationSec = durationSec;
            this.addedAt = addedAt;
            this.album = album == null ? "" : album;
            this.genre = genre == null ? "" : genre;
        }

        Track dated(long date) {
            return new Track(title, url, uploader, thumbnail, durationSec, date, album, genre);
        }

        Track withAlbum(String value) {
            return new Track(title, url, uploader, thumbnail, durationSec, addedAt, value, genre);
        }

        /** Display artist: YouTube's " - Topic" channels are stripped. */
        String artist() {
            String value = uploader.replaceAll("(?i)\\s*-\\s*topic$", "").trim();
            if (value.isEmpty() || value.equalsIgnoreCase("Various Artists")) return "";
            return value;
        }
    }

    /** One listening event, used by discovery. Bounded and local only. */
    static final class Play {
        final String url;
        final long at;
        final int ratio;      // 0..100 of the track actually heard
        final boolean skipped;

        Play(String url, long at, int ratio, boolean skipped) {
            this.url = url;
            this.at = at;
            this.ratio = Math.max(0, Math.min(100, ratio));
            this.skipped = skipped;
        }
    }

    static final class Playlist {
        final String name, description, cover;
        final boolean favorite;

        Playlist(String name, String description, String cover, boolean favorite) {
            this.name = name;
            this.description = description;
            this.cover = cover;
            this.favorite = favorite;
        }
    }

    private static volatile LibraryStore shared;

    private final SharedPreferences prefs;
    private final Map<String, List<Track>> cache = new ConcurrentHashMap<>();
    private final Map<String, Track> index = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger revision = new java.util.concurrent.atomic.AtomicInteger();

    /**
     * One instance per process. Every screen and the player share it, so the
     * memoised collections are always consistent and a write invalidates the
     * same view of the data for everybody.
     */
    static LibraryStore shared(Context context) {
        LibraryStore instance = shared;
        if (instance == null) {
            synchronized (LibraryStore.class) {
                instance = shared;
                if (instance == null) {
                    instance = new LibraryStore(context.getApplicationContext());
                    shared = instance;
                }
            }
        }
        return instance;
    }

    LibraryStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("library", Context.MODE_PRIVATE);
        migrate();
    }

    /** Increments on every write; screens compare it to avoid needless rebuilds. */
    int revision() { return revision.get(); }

    // ------------------------------------------------------------- schema
    private void migrate() {
        int version = prefs.getInt("schema", 1);
        if (version >= SCHEMA) return;
        SharedPreferences.Editor edit = prefs.edit();
        // v2: additive only. History becomes bounded and de-duplicated the first
        // time it is read; nothing is rewritten here so a downgrade loses nothing.
        edit.putInt("schema", SCHEMA);
        edit.apply();
    }

    // ------------------------------------------------------------ reading
    List<Track> tracks(String key) {
        List<Track> cached = cache.get(key);
        if (cached != null) return new ArrayList<>(cached);
        List<Track> result = new ArrayList<>();
        try {
            JSONArray data = new JSONArray(prefs.getString(key, "[]"));
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                String url = UrlPolicy.canonicalVideo(item.optString("url"));
                if (url == null || !seen.add(url)) continue;
                result.add(new Track(item.optString("title", "Áudio"), url,
                        item.optString("uploader", ""), item.optString("thumbnail", ""),
                        item.optLong("durationSec", -1), item.optLong("addedAt", 0),
                        item.optString("album", ""), item.optString("genre", "")));
            }
        } catch (JSONException ignored) { /* keep the original bytes; never destroy on read */ }
        cache.put(key, result);
        return new ArrayList<>(result);
    }

    Track find(String url) {
        String canonical = UrlPolicy.canonicalVideo(url == null ? "" : url);
        if (canonical == null) return null;
        Track cached = index.get(canonical);
        if (cached != null) return cached;
        for (Track track : catalog()) {
            index.putIfAbsent(track.url, track);
            if (track.url.equals(canonical)) return track;
        }
        return null;
    }

    boolean contains(String key, String url) {
        for (Track track : tracks(key)) if (track.url.equals(url)) return true;
        return false;
    }

    // ------------------------------------------------------------ writing
    void add(String key, Track track) {
        if (track == null || UrlPolicy.canonicalVideo(track.url) == null) return;
        List<Track> items = tracks(key);
        long date = System.currentTimeMillis();
        for (Track item : items) if (item.url.equals(track.url) && !key.equals(HISTORY)) date = item.addedAt;
        items.removeIf(item -> item.url.equals(track.url));
        items.add(0, track.dated(date));
        if (key.equals(HISTORY)) items = new ArrayList<>(items.subList(0, Math.min(MAX_HISTORY, items.size())));
        if (key.equals(RECENT)) items = new ArrayList<>(items.subList(0, Math.min(MAX_RECENT, items.size())));
        write(key, items);
    }

    void remove(String key, String url) {
        List<Track> items = tracks(key);
        items.removeIf(item -> item.url.equals(url));
        write(key, items);
        if (FAVORITES.equals(key)) likes.put(url, Boolean.FALSE);
        if (QUEUE.equals(key)) consumeManual(url);
    }

    void clearHistory() {
        write(HISTORY, new ArrayList<>());
        write(RECENT, new ArrayList<>());
    }

    void appendQueue(Track track) {
        List<Track> items = tracks(QUEUE);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).url.equals(track.url)) {
                Track current = items.get(i);
                String uploader = track.uploader.isEmpty() ? current.uploader : track.uploader;
                String thumbnail = track.thumbnail.isEmpty() ? current.thumbnail : track.thumbnail;
                long duration = track.durationSec < 0 ? current.durationSec : track.durationSec;
                String title = track.title.equals("Áudio do YouTube") && !current.title.isEmpty()
                        ? current.title : track.title;
                items.set(i, new Track(title, track.url, uploader, thumbnail, duration,
                        current.addedAt, track.album.isEmpty() ? current.album : track.album,
                        track.genre.isEmpty() ? current.genre : track.genre));
                write(QUEUE, items);
                return;
            }
        }
        items.add(track);
        write(QUEUE, items);
    }

    void replaceQueue(List<Track> items) { write(QUEUE, dedup(items)); }

    void cacheCollection(String key, List<Track> items) {
        if (key != null && key.startsWith("remote:")) write(key, dedup(items));
    }

    void moveQueue(String url, int direction) { moveTrack(QUEUE, url, direction); }

    void moveTrack(String key, String url, int direction) {
        List<Track> items = tracks(key);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).url.equals(url)) {
                int next = i + direction;
                if (next >= 0 && next < items.size()) {
                    Collections.swap(items, i, next);
                    write(key, items);
                }
                return;
            }
        }
    }

    void moveTo(String key, String url, int index) {
        List<Track> items = tracks(key);
        Track moving = null;
        for (Track item : items) if (item.url.equals(url)) { moving = item; break; }
        if (moving == null) return;
        items.remove(moving);
        items.add(Math.max(0, Math.min(index, items.size())), moving);
        write(key, items);
    }

    List<Track> sorted(String key, String query, String mode) {
        List<Track> items = tracks(key);
        items.removeIf(item -> !LibraryQuery.matches(item.title + " " + item.uploader + " " + item.album, query));
        if ("title".equals(mode)) items.sort((a, b) -> LibraryQuery.normalized(a.title).compareTo(LibraryQuery.normalized(b.title)));
        if ("artist".equals(mode)) items.sort((a, b) -> LibraryQuery.normalized(a.uploader).compareTo(LibraryQuery.normalized(b.uploader)));
        if ("date".equals(mode)) items.sort((a, b) -> Long.compare(b.addedAt, a.addedAt));
        if ("duration".equals(mode)) items.sort((a, b) -> Long.compare(b.durationSec, a.durationSec));
        return items;
    }

    // ----------------------------------------------------------- playlists
    Playlist playlist(String name) {
        try {
            JSONObject o = new JSONObject(prefs.getString("meta:" + name, "{}"));
            return new Playlist(name, o.optString("description"), o.optString("cover"), o.optBoolean("favorite"));
        } catch (JSONException e) {
            return new Playlist(name, "", "", false);
        }
    }

    void updatePlaylist(String name, String description, String cover, boolean favorite) {
        if (!playlists().contains(name)) return;
        try {
            prefs.edit().putString("meta:" + name, new JSONObject().put("description", description == null ? "" : description)
                    .put("cover", cover == null ? "" : cover).put("favorite", favorite).toString()).apply();
            revision.incrementAndGet();
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
    }

    void togglePlaylistFavorite(String name) {
        Playlist p = playlist(name);
        updatePlaylist(name, p.description, p.cover, !p.favorite);
    }

    List<String> playlists() {
        List<String> names = new ArrayList<>();
        try {
            JSONArray data = new JSONArray(prefs.getString("playlists", "[]"));
            for (int i = 0; i < data.length(); i++) names.add(data.getString(i));
        } catch (JSONException ignored) { }
        return names;
    }

    boolean createPlaylist(String name) {
        if (name == null) return false;
        name = name.trim();
        List<String> names = playlists();
        if (name.isEmpty() || name.length() > 60 || names.contains(name)) return false;
        names.add(name);
        prefs.edit().putString("playlists", new JSONArray(names).toString()).apply();
        return true;
    }

    boolean renamePlaylist(String oldName, String newName) {
        if (newName == null) return false;
        newName = newName.trim();
        List<String> names = playlists();
        int at = names.indexOf(oldName);
        if (at < 0 || newName.isEmpty() || newName.length() > 60) return false;
        if (oldName.equals(newName)) return true;
        if (names.contains(newName)) return false;
        names.set(at, newName);
        // One transaction: order, tracks, metadata. Nothing half-renamed.
        prefs.edit().putString("playlists", new JSONArray(names).toString())
                .putString(playlistKey(newName), prefs.getString(playlistKey(oldName), "[]"))
                .putString("meta:" + newName, prefs.getString("meta:" + oldName, "{}"))
                .remove("meta:" + oldName)
                .remove(playlistKey(oldName)).apply();
        cache.remove(playlistKey(oldName));
        return true;
    }

    void deletePlaylist(String name) {
        List<String> names = playlists();
        names.remove(name);
        prefs.edit().putString("playlists", new JSONArray(names).toString())
                .putString("undo_name", name)
                .putString("undo_tracks", prefs.getString(playlistKey(name), "[]"))
                .putString("undo_meta", prefs.getString("meta:" + name, "{}"))
                .remove("meta:" + name)
                .remove(playlistKey(name)).apply();
        cache.remove(playlistKey(name));
    }

    boolean canUndoPlaylist() { return !prefs.getString("undo_name", "").isEmpty(); }

    /** Bounded one-step undo; never overwrites a playlist that exists again. */
    boolean restoreDeletedPlaylist() {
        String name = prefs.getString("undo_name", "");
        if (name.isEmpty() || playlists().contains(name)) return false;
        List<String> names = playlists();
        names.add(name);
        prefs.edit().putString("playlists", new JSONArray(names).toString())
                .putString(playlistKey(name), prefs.getString("undo_tracks", "[]"))
                .putString("meta:" + name, prefs.getString("undo_meta", "{}"))
                .remove("undo_name").remove("undo_tracks").remove("undo_meta").apply();
        cache.remove(playlistKey(name));
        return true;
    }

    void restoreTrack(String key, Track track, int index) {
        List<Track> items = tracks(key);
        for (Track item : items) if (item.url.equals(track.url)) return;
        items.add(Math.max(0, Math.min(index, items.size())), track);
        write(key, items);
    }

    static String playlistKey(String name) { return "playlist:" + name; }

    // ------------------------------------------------- favorites & catalog
    private final Map<String, Boolean> likes = new ConcurrentHashMap<>();

    boolean liked(String url) {
        if (url == null) return false;
        Boolean cached = likes.get(url);
        if (cached != null) return cached;
        boolean value = contains(FAVORITES, url);
        likes.put(url, value);
        return value;
    }

    void toggleLike(Track track) {
        boolean was = liked(track.url);
        if (was) remove(FAVORITES, track.url);
        else add(FAVORITES, track);
        likes.put(track.url, !was);
        revision.incrementAndGet();
    }

    /** Every known track, de-duplicated, newest metadata wins. */
    List<Track> catalog() {
        Map<String, Track> result = new LinkedHashMap<>();
        List<String> keys = new ArrayList<>();
        Collections.addAll(keys, FAVORITES, HISTORY, RECENT, QUEUE);
        for (String name : playlists()) keys.add(playlistKey(name));
        for (String key : keys) for (Track track : tracks(key)) result.putIfAbsent(track.url, track);
        return new ArrayList<>(result.values());
    }

    /** Fast lookup: url → track over the whole catalog. Rebuilt lazily. */
    private Map<String, Track> catalogIndex() {
        Map<String, Track> result = new LinkedHashMap<>();
        for (Track track : catalog()) result.put(track.url, track);
        return result;
    }

    // --------------------------------------------------- artists & albums
    List<String> artists() {
        Set<String> names = new LinkedHashSet<>();
        for (Track track : catalog()) if (!track.artist().isEmpty()) names.add(track.artist());
        names.addAll(followedArtists());
        List<String> list = new ArrayList<>(names);
        list.sort((a, b) -> LibraryQuery.normalized(a).compareTo(LibraryQuery.normalized(b)));
        return list;
    }

    List<Track> artistTracks(String artist) {
        List<Track> result = new ArrayList<>();
        String key = LibraryQuery.normalized(artist);
        for (Track track : catalog()) if (LibraryQuery.normalized(track.artist()).equals(key)) result.add(track);
        return result;
    }

    List<String> followedArtists() { return stringList("followed_artists"); }

    boolean following(String artist) {
        String key = LibraryQuery.normalized(artist);
        for (String name : followedArtists()) if (LibraryQuery.normalized(name).equals(key)) return true;
        return false;
    }

    boolean toggleFollow(String artist) {
        if (artist == null || artist.trim().isEmpty()) return false;
        List<String> list = followedArtists();
        String key = LibraryQuery.normalized(artist);
        boolean removed = list.removeIf(name -> LibraryQuery.normalized(name).equals(key));
        if (!removed) list.add(artist.trim());
        prefs.edit().putString("followed_artists", new JSONArray(list).toString()).apply();
        return !removed;
    }

    List<String> albums() {
        Map<String, String> byKey = new LinkedHashMap<>();
        for (Track track : catalog()) {
            if (track.album.trim().isEmpty()) continue;
            byKey.putIfAbsent(LibraryQuery.normalized(track.album),
                    track.album + (track.artist().isEmpty() ? "" : "\u0000" + track.artist()));
        }
        List<String> result = new ArrayList<>(byKey.values());
        result.sort((a, b) -> LibraryQuery.normalized(a).compareTo(LibraryQuery.normalized(b)));
        return result;
    }

    List<Track> albumTracks(String albumLabel) {
        String[] parts = albumLabel.split("\u0000", 2);
        String album = LibraryQuery.normalized(parts[0]);
        String artist = parts.length > 1 ? LibraryQuery.normalized(parts[1]) : "";
        List<Track> result = new ArrayList<>();
        for (Track track : catalog()) {
            if (!LibraryQuery.normalized(track.album).equals(album)) continue;
            if (!artist.isEmpty() && !LibraryQuery.normalized(track.artist()).equals(artist)) continue;
            result.add(track);
        }
        return result;
    }

    boolean albumSaved(String albumLabel) {
        String key = LibraryQuery.normalized(albumLabel.replace('\u0000', ' '));
        for (String saved : stringList("saved_albums"))
            if (LibraryQuery.normalized(saved.replace('\u0000', ' ')).equals(key)) return true;
        return false;
    }

    boolean toggleSaveAlbum(String albumLabel, List<Track> tracks) {
        List<String> saved = stringList("saved_albums");
        String key = LibraryQuery.normalized(albumLabel.replace('\u0000', ' '));
        boolean existed = saved.removeIf(name -> LibraryQuery.normalized(name.replace('\u0000', ' ')).equals(key));
        if (!existed) {
            saved.add(albumLabel);
            // Saving an album saves its tracks so the collection survives edits upstream.
            for (int i = tracks.size() - 1; i >= 0; i--) add("album:" + key, tracks.get(i));
        } else {
            write("album:" + key, new ArrayList<>());
        }
        prefs.edit().putString("saved_albums", new JSONArray(saved).toString()).apply();
        return !existed;
    }

    // ------------------------------------------------------- queue origins
    String origin(String url) { return prefs.getString("origin:" + url, "queue"); }

    void origin(String url, String origin) { prefs.edit().putString("origin:" + url, origin).apply(); }

    String contextName() { return prefs.getString("queue_context", "Sua fila"); }

    List<String> pendingManual() { return stringList("manual_pending"); }

    void enqueueManual(Track track, boolean next) {
        appendQueue(track);
        origin(track.url, "manual");
        List<String> pending = pendingManual();
        pending.remove(track.url);
        pending.add(next ? 0 : pending.size(), track.url);
        prefs.edit().putString("manual_pending", new JSONArray(pending).toString()).apply();
    }

    void consumeManual(String url) {
        List<String> pending = pendingManual();
        if (pending.remove(url)) prefs.edit().putString("manual_pending", new JSONArray(pending).toString()).apply();
    }

    void beginContext(List<Track> items, String context, String origin) {
        List<String> pending = pendingManual();
        List<Track> previous = tracks(QUEUE);
        Map<String, Track> combined = new LinkedHashMap<>();
        SharedPreferences.Editor edit = prefs.edit().putString("queue_context", context == null ? "Sua fila" : context);
        for (Track track : items) {
            combined.put(track.url, track);
            edit.putString("origin:" + track.url, origin);
        }
        for (Track track : previous) {
            if (pending.contains(track.url) && !combined.containsKey(track.url)) {
                combined.put(track.url, track);
                edit.putString("origin:" + track.url, "manual");
            }
        }
        edit.apply();
        write(QUEUE, new ArrayList<>(combined.values()));
    }

    void appendRadio(Track track) { origin(track.url, "radio"); appendQueue(track); }

    // ---------------------------------------------------------- behaviour
    void recordSkip(String url) { prefs.edit().putLong("skip:" + url, System.currentTimeMillis()).apply(); }

    boolean recentlySkipped(String url) {
        long time = 0;
        try {
            time = prefs.getLong("skip:" + url, 0);
        } catch (ClassCastException ignored) {
            try {
                time = Long.parseLong(prefs.getString("skip:" + url, "0"));
            } catch (Exception ignored2) {
                time = 0;
            }
        }
        return time > 0 && Math.abs(System.currentTimeMillis() - time) < 7L * 86400000;
    }

    /**
     * Records a finished or abandoned listen. `ratio` is how much of the track
     * was heard; under 25% (or an explicit skip) counts as a skip, which keeps
     * it out of discovery for a week. Bounded log, never leaves the device.
     */
    void recordPlay(Track track, int ratio, boolean skipped) {
        if (track == null || UrlPolicy.canonicalVideo(track.url) == null) return;
        boolean negative = skipped || ratio < 25;
        if (negative) recordSkip(track.url);
        else {
            prefs.edit().remove("skip:" + track.url).apply();
            add(RECENT, track);
        }
        List<Play> plays = plays();
        plays.add(0, new Play(track.url, System.currentTimeMillis(), ratio, negative));
        if (plays.size() > MAX_PLAYS) plays = new ArrayList<>(plays.subList(0, MAX_PLAYS));
        JSONArray array = new JSONArray();
        try {
            for (Play play : plays) array.put(new JSONObject().put("url", play.url).put("at", play.at)
                    .put("ratio", play.ratio).put("skipped", play.skipped));
            prefs.edit().putString("plays", array.toString()).apply();
        } catch (JSONException ignored) { }
    }

    List<Play> plays() {
        List<Play> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString("plays", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                String url = UrlPolicy.canonicalVideo(o.optString("url"));
                if (url != null) result.add(new Play(url, o.optLong("at", 0), o.optInt("ratio", 0),
                        o.optBoolean("skipped", false)));
            }
        } catch (JSONException ignored) { }
        return result;
    }

    List<Track> recentlyPlayed(int limit) {
        List<Track> recent = tracks(RECENT);
        if (recent.isEmpty()) recent = tracks(HISTORY);
        return recent.subList(0, Math.min(limit, recent.size()));
    }

    /** Artists heard in the last N days, weighted by how many complete listens. */
    Map<String, Integer> artistAffinity(int days) {
        long since = System.currentTimeMillis() - days * 86400000L;
        Map<String, Integer> weight = new HashMap<>();
        Map<String, Track> known = catalogIndex();
        for (Play play : plays()) {
            if (play.at < since || play.skipped) continue;
            Track track = known.get(play.url);
            if (track == null || track.artist().isEmpty()) continue;
            weight.merge(track.artist(), 1 + play.ratio / 50, Integer::sum);
        }
        for (Track track : tracks(FAVORITES)) {
            if (!track.artist().isEmpty()) weight.merge(track.artist(), 4, Integer::sum);
        }
        return weight;
    }

    Set<String> dislikedUrls() {
        Set<String> result = new HashSet<>();
        for (Play play : plays()) if (play.skipped) result.add(play.url);
        return result;
    }

    int listenCount(String url) {
        int count = 0;
        for (Play play : plays()) if (play.url.equals(url) && !play.skipped) count++;
        return count;
    }

    // ------------------------------------------------------- recent searches
    List<String> recentSearches() { return stringList("recent_searches"); }

    void rememberSearch(String query) {
        prefs.edit().putString("recent_searches",
                new JSONArray(LibraryQuery.rememberSearch(recentSearches(), query)).toString()).apply();
    }

    void clearSearches() { prefs.edit().remove("recent_searches").apply(); }

    // --------------------------------------------------------------- utils
    List<String> stringList(String key) {
        List<String> values = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(key, "[]"));
            for (int i = 0; i < array.length(); i++) values.add(array.getString(i));
        } catch (JSONException ignored) { }
        return values;
    }

    private static List<Track> dedup(List<Track> items) {
        Map<String, Track> unique = new LinkedHashMap<>();
        for (Track track : items) {
            if (track != null && UrlPolicy.canonicalVideo(track.url) != null) unique.put(track.url, track);
        }
        return new ArrayList<>(unique.values());
    }

    private void write(String key, List<Track> items) {
        JSONArray array = new JSONArray();
        for (Track item : items) {
            try {
                array.put(new JSONObject().put("title", item.title).put("url", item.url)
                        .put("uploader", item.uploader).put("thumbnail", item.thumbnail)
                        .put("durationSec", item.durationSec).put("addedAt", item.addedAt)
                        .put("album", item.album).put("genre", item.genre));
            } catch (JSONException e) {
                throw new IllegalStateException(e);
            }
        }
        prefs.edit().putString(key, array.toString()).apply();
        List<Track> snapshot = new ArrayList<>(items);
        cache.put(key, snapshot);
        for (Track track : snapshot) if (index.size() < 4000) index.put(track.url, track);
        if (FAVORITES.equals(key)) {
            likes.clear();
            for (Track track : snapshot) likes.put(track.url, Boolean.TRUE);
        }
        revision.incrementAndGet();
    }

    /** Exposed for tests: drop memoised collections after external writes. */
    void invalidate() { cache.clear(); index.clear(); }

    static String displayArtist(String uploader) {
        return new Track("", "", uploader, "", 0).artist();
    }

    static String oneDecimal(long value) { return String.format(Locale.ROOT, "%d", value); }
}
