package app.onda.mobile;

import android.content.Context;
import android.content.SharedPreferences;

/** User-provided/licensed lyrics only; the app does not scrape lyric sites. */
final class LyricsStore {
    private final SharedPreferences preferences;
    LyricsStore(Context context) { preferences = context.getSharedPreferences("lyrics", Context.MODE_PRIVATE); }
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
    private static String safeId(String url) { try { return OfflineStore.id(url); } catch (RuntimeException e) { return null; } }
}
