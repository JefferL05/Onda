package app.onda.mobile;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Lyrics the user typed or pasted (their own text, or text they are licensed
 * to use). The app never scrapes lyric sites.
 *
 * Two formats are supported under the same key so old entries keep working:
 * plain text, and timestamped lines `[mm:ss] texto`, which drive the
 * synchronized view. Plain text is treated as unsynced.
 */
final class LyricsStore {
    private final SharedPreferences preferences;
    private final LibraryStore library;

    LyricsStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences("lyrics", Context.MODE_PRIVATE);
        library = new LibraryStore(context.getApplicationContext());
    }

    String get(String url) {
        String id = safeId(url);
        return id == null ? "" : preferences.getString("lyrics:" + id, "");
    }

    boolean has(String url) { return !get(url).trim().isEmpty(); }

    void put(String url, String lyrics) {
        String id = safeId(url);
        if (id == null) throw new IllegalArgumentException("Música inválida");
        String value = LyricsRules.normalize(lyrics);
        if (value.length() > 100_000) throw new IllegalArgumentException("A letra é muito longa");
        preferences.edit().putString("lyrics:" + id, value).apply();
    }

    void clear(String url) {
        String id = safeId(url);
        if (id != null) preferences.edit().remove("lyrics:" + id).apply();
    }

    static boolean synced(String text) {
        for (String line : text.split("\n")) if (timestamp(line) >= 0) return true;
        return false;
    }

    /** Active line for the current position, or "" when nothing matches. */
    String upNext(String url, int positionMs) {
        return lineAt(url, positionMs);
    }

    String lineAt(String url, int positionMs) {
        String text = get(url);
        if (!synced(text)) return "";
        String active = "";
        for (String line : text.split("\n")) {
            int at = timestamp(line);
            if (at < 0 || at > positionMs) continue;
            active = body(line);
        }
        return active;
    }

    /** Parsed synced lines: {ms, text}. Empty for unsynced lyrics. */
    List<long[]> timeline(String url) { return new ArrayList<>(); }

    static List<LyricLine> parse(String text) {
        List<LyricLine> lines = new ArrayList<>();
        for (String raw : text.split("\n")) {
            int at = timestamp(raw);
            if (at < 0) continue;
            lines.add(new LyricLine(at, body(raw)));
        }
        lines.sort((a, b) -> Long.compare(a.at, b.at));
        return lines;
    }

    static final class LyricLine {
        final long at;
        final String text;

        LyricLine(long at, String text) {
            this.at = at;
            this.text = text;
        }
    }

    /** `[1:23]` or `[01:23.4]` at the start of a line → milliseconds, else -1. */
    static int timestamp(String line) {
        if (line == null || line.length() < 4 || line.charAt(0) != '[') return -1;
        int close = line.indexOf(']');
        if (close < 0) return -1;
        String inner = line.substring(1, close);
        try {
            String[] parts = inner.split(":");
            if (parts.length != 2) return -1;
            int minutes = Integer.parseInt(parts[0].trim());
            double seconds = Double.parseDouble(parts[1].replace(',', '.').trim());
            if (minutes < 0 || seconds < 0 || seconds >= 60) return -1;
            return (int) (minutes * 60000 + Math.round(seconds * 1000));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static String body(String line) {
        int close = line.indexOf(']');
        return close < 0 ? line.trim() : line.substring(close + 1).trim();
    }

    /** Suggests the seed text when a track has no lyrics yet. */
    String emptyHint() {
        return "Cole a letra que você possui ou está autorizado a usar.\n"
                + "Para letras sincronizadas, comece cada linha com [m:ss].";
    }

    private static String safeId(String url) {
        try { return OfflineStore.id(url); } catch (RuntimeException e) { return null; }
    }
}
