package app.onda.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

final class LibraryStore {
    static final String FAVORITES = "favorites";
    static final String HISTORY = "history";
    static final String QUEUE = "queue";
    private final SharedPreferences prefs;

    static final class Track {
        final String title;
        final String url;
        final String uploader;
        final String thumbnail;
        final long durationSec;
        final long addedAt;
        final String album, genre;
        Track(String title, String url) { this(title, url, "", "", -1); }
        Track(String title, String url, String uploader, String thumbnail, long durationSec) {
            this(title, url, uploader, thumbnail, durationSec, 0, "", "");
        }
        Track(String title, String url, String uploader, String thumbnail, long durationSec, long addedAt, String album, String genre) {
            this.title = title;
            this.url = url;
            this.uploader = uploader == null ? "" : uploader;
            this.thumbnail = thumbnail == null ? "" : thumbnail;
            this.durationSec = durationSec;
            this.addedAt = addedAt;
            this.album = album == null ? "" : album;
            this.genre = genre == null ? "" : genre;
        }
        Track dated(long date) { return new Track(title, url, uploader, thumbnail, durationSec, date, album, genre); }
    }

    LibraryStore(Context context) { prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE); }

    List<Track> tracks(String key) {
        List<Track> result = new ArrayList<>();
        try {
            JSONArray data = new JSONArray(prefs.getString(key, "[]"));
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                String url = UrlPolicy.canonicalVideo(item.optString("url"));
                if (url != null) result.add(new Track(item.optString("title", "Vídeo"), url,
                        item.optString("uploader", ""), item.optString("thumbnail", ""),
                        item.optLong("durationSec", -1), item.optLong("addedAt", 0),
                        item.optString("album", ""), item.optString("genre", "")));
            }
        } catch (JSONException ignored) { /* Preserve the original bytes on read failure. */ }
        return result;
    }

    void add(String key, Track track) {
        if (track == null || UrlPolicy.canonicalVideo(track.url) == null) return;
        List<Track> items = tracks(key);
        long date = System.currentTimeMillis();
        for (Track item : items) if (item.url.equals(track.url) && !key.equals(HISTORY)) date = item.addedAt;
        items.removeIf(item -> item.url.equals(track.url));
        items.add(0, track.dated(date));
        if (key.equals(HISTORY) && items.size() > 100) items = new ArrayList<>(items.subList(0, 100));
        write(key, items);
    }

    void remove(String key, String url) {
        List<Track> items = tracks(key);
        items.removeIf(item -> item.url.equals(url));
        write(key, items);
        if (QUEUE.equals(key)) consumeManual(url);
    }

    void clearHistory() { prefs.edit().remove(HISTORY).apply(); }

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

    void replaceQueue(List<Track> items) { write(QUEUE, items); }
    void cacheCollection(String key,List<Track> items) { if(key.startsWith("remote:")) write(key,items); }

    void moveQueue(String url, int direction) {
        moveTrack(QUEUE, url, direction);
    }

    void moveTrack(String key, String url, int direction) {
        List<Track> items = tracks(key);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).url.equals(url)) {
                int next = i + direction;
                if (next >= 0 && next < items.size()) {
                    java.util.Collections.swap(items, i, next);
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
        items.remove(moving); items.add(Math.max(0, Math.min(index, items.size())), moving); write(key, items);
    }

    List<Track> sorted(String key, String query, String mode) {
        List<Track> items = tracks(key);
        items.removeIf(item -> !LibraryQuery.matches(item.title + " " + item.uploader, query));
        if ("title".equals(mode)) items.sort((a,b) -> LibraryQuery.normalized(a.title).compareTo(LibraryQuery.normalized(b.title)));
        if ("artist".equals(mode)) items.sort((a,b) -> LibraryQuery.normalized(a.uploader).compareTo(LibraryQuery.normalized(b.uploader)));
        if ("date".equals(mode)) items.sort((a,b) -> Long.compare(b.addedAt, a.addedAt));
        return items;
    }

    static final class Playlist {
        final String name, description, cover;
        final boolean favorite;
        Playlist(String name, String description, String cover, boolean favorite) {
            this.name = name; this.description = description; this.cover = cover; this.favorite = favorite;
        }
    }
    Playlist playlist(String name) {
        try {
            JSONObject o = new JSONObject(prefs.getString("meta:" + name, "{}"));
            return new Playlist(name, o.optString("description"), o.optString("cover"), o.optBoolean("favorite"));
        } catch (JSONException e) { return new Playlist(name, "", "", false); }
    }
    void updatePlaylist(String name, String description, String cover, boolean favorite) {
        if (!playlists().contains(name)) return;
        try {
            prefs.edit().putString("meta:" + name, new JSONObject().put("description", description)
                    .put("cover", cover).put("favorite", favorite).toString()).apply();
        } catch (JSONException e) { throw new IllegalArgumentException(e); }
    }
    void togglePlaylistFavorite(String name) {
        Playlist p = playlist(name); updatePlaylist(name, p.description, p.cover, !p.favorite);
    }
    List<Track> catalog() {
        Map<String, Track> result = new LinkedHashMap<>();
        List<String> keys = new ArrayList<>(java.util.Arrays.asList(FAVORITES, HISTORY, QUEUE));
        for (String name : playlists()) keys.add(playlistKey(name));
        for (String key : keys) for (Track track : tracks(key)) result.putIfAbsent(track.url, track);
        return new ArrayList<>(result.values());
    }
    boolean liked(String url) { for (Track t : tracks(FAVORITES)) if (t.url.equals(url)) return true; return false; }
    void toggleLike(Track track) { if (liked(track.url)) remove(FAVORITES, track.url); else add(FAVORITES, track); }
    String origin(String url) { return prefs.getString("origin:" + url, "queue"); }
    void origin(String url, String origin) { prefs.edit().putString("origin:" + url, origin).apply(); }
    String contextName() { return prefs.getString("queue_context", "Sua fila"); }
    List<String> pendingManual() {
        List<String> values = new ArrayList<>();
        try { JSONArray a = new JSONArray(prefs.getString("manual_pending", "[]"));
            for (int i=0;i<a.length();i++) values.add(a.getString(i));
        } catch (JSONException ignored) { }
        return values;
    }
    void enqueueManual(Track track, boolean next) {
        appendQueue(track); origin(track.url, "manual");
        List<String> pending = pendingManual(); pending.remove(track.url);
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
        Map<String,Track> combined = new LinkedHashMap<>();
        SharedPreferences.Editor edit = prefs.edit().putString("queue_context", context);
        for (Track track : items) { combined.put(track.url, track); edit.putString("origin:" + track.url, origin); }
        for (Track track : previous) if (pending.contains(track.url) && !combined.containsKey(track.url)) {
            combined.put(track.url, track); edit.putString("origin:" + track.url, "manual");
        }
        edit.apply(); write(QUEUE, new ArrayList<>(combined.values()));
    }
    void appendRadio(Track track) { origin(track.url, "radio"); appendQueue(track); }
    void recordSkip(String url) { prefs.edit().putLong("skip:" + url, System.currentTimeMillis()).apply(); }
    boolean recentlySkipped(String url) { return System.currentTimeMillis() - prefs.getLong("skip:" + url, 0) < 7L*86400000; }

    /** Bounded one-step undo stored on-device; never overwrites a newly created playlist. */
    boolean restoreDeletedPlaylist() {
        String name = prefs.getString("undo_name", "");
        if (name.isEmpty() || playlists().contains(name)) return false;
        List<String> names = playlists(); names.add(name);
        prefs.edit().putString("playlists", new JSONArray(names).toString())
                .putString(playlistKey(name), prefs.getString("undo_tracks", "[]"))
                .putString("meta:" + name, prefs.getString("undo_meta", "{}"))
                .remove("undo_name").remove("undo_tracks").remove("undo_meta").apply();
        return true;
    }
    boolean canUndoPlaylist() { return !prefs.getString("undo_name", "").isEmpty(); }
    void restoreTrack(String key, Track track, int index) {
        List<Track> items = tracks(key);
        for (Track item : items) if (item.url.equals(track.url)) return;
        items.add(Math.max(0, Math.min(index, items.size())), track); write(key, items);
    }

    List<String> recentSearches() {
        List<String> result = new ArrayList<>();
        try {
            JSONArray data = new JSONArray(prefs.getString("recent_searches", "[]"));
            for (int i = 0; i < data.length(); i++) result.add(data.getString(i));
        } catch (JSONException ignored) { }
        return result;
    }

    void rememberSearch(String query) {
        prefs.edit().putString("recent_searches",
                new JSONArray(LibraryQuery.rememberSearch(recentSearches(), query)).toString()).apply();
    }

    void clearSearches() { prefs.edit().remove("recent_searches").apply(); }

    boolean renamePlaylist(String oldName, String newName) {
        newName = newName.trim();
        List<String> names = playlists();
        int index = names.indexOf(oldName);
        if (index < 0 || newName.isEmpty() || newName.length() > 60) return false;
        if (oldName.equals(newName)) return true;
        if (names.contains(newName)) return false;
        names.set(index, newName);
        // One preferences transaction retains order and all original track data.
        prefs.edit().putString("playlists", new JSONArray(names).toString())
                .putString(playlistKey(newName), prefs.getString(playlistKey(oldName), "[]"))
                .putString("meta:" + newName, prefs.getString("meta:" + oldName, "{}"))
                .remove("meta:" + oldName)
                .remove(playlistKey(oldName)).apply();
        return true;
    }

    private void write(String key, List<Track> items) {
        JSONArray array = new JSONArray();
        for (Track item : items) {
            try {
                array.put(new JSONObject().put("title", item.title).put("url", item.url)
                        .put("uploader", item.uploader).put("thumbnail", item.thumbnail)
                        .put("durationSec", item.durationSec).put("addedAt", item.addedAt)
                        .put("album", item.album).put("genre", item.genre));
            } catch (JSONException e) { throw new IllegalStateException(e); }
        }
        prefs.edit().putString(key, array.toString()).apply();
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
        name = name.trim();
        List<String> names = playlists();
        if (name.isEmpty() || name.length() > 60 || names.contains(name)) return false;
        names.add(name);
        prefs.edit().putString("playlists", new JSONArray(names).toString()).apply();
        return true;
    }

    void deletePlaylist(String name) {
        List<String> names = playlists();
        names.remove(name);
        prefs.edit().putString("playlists", new JSONArray(names).toString())
                .putString("undo_name", name).putString("undo_tracks", prefs.getString(playlistKey(name), "[]"))
                .putString("undo_meta", prefs.getString("meta:" + name, "{}"))
                .remove("meta:" + name)
                .remove(playlistKey(name)).apply();
    }

    static String playlistKey(String name) { return "playlist:" + name; }
}
