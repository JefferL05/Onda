package app.onda.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class OfflineStore {
    private final SharedPreferences prefs;
    private final File directory;

    static final class Song {
        final String id, title, url, extension;
        final String uploader, thumbnail;
        final long bytes, duration;
        Song(String id, String title, String url, String extension, long bytes, long duration) {
            this(id, title, url, extension, "", "", bytes, duration);
        }
        Song(String id, String title, String url, String extension, String uploader, String thumbnail,
             long bytes, long duration) {
            this.id = id; this.title = title; this.url = url; this.extension = extension;
            this.uploader = uploader == null ? "" : uploader;
            this.thumbnail = thumbnail == null ? "" : thumbnail;
            this.bytes = bytes; this.duration = duration;
        }
    }

    OfflineStore(Context context) {
        prefs = context.getSharedPreferences("offline", Context.MODE_PRIVATE);
        directory = new File(context.getFilesDir(), "audio");
        if (!directory.exists()) directory.mkdirs();
    }

    static String id(String url) {
        String canonical = UrlPolicy.canonicalVideo(url);
        if (canonical == null) throw new IllegalArgumentException("Vídeo inválido");
        return canonical.substring(canonical.indexOf("v=") + 2);
    }

    File file(String id, String extension) {
        if (!id.matches("[A-Za-z0-9_-]{11}") || !(extension.equals("m4a") || extension.equals("webm"))) throw new IllegalArgumentException("Arquivo inválido");
        return new File(directory, id + "." + extension);
    }

    List<Song> songs() {
        List<Song> result = new ArrayList<>();
        for (String key : prefs.getAll().keySet()) {
            if (!key.startsWith("song:")) continue;
            try {
                JSONObject item = new JSONObject(prefs.getString(key, "{}"));
                Song song = new Song(item.getString("id"), item.getString("title"), item.getString("url"),
                        item.getString("extension"), item.optString("uploader", ""),
                        item.optString("thumbnail", ""), item.getLong("bytes"), item.getLong("duration"));
                File file = file(song.id, song.extension);
                if (file.isFile() && file.length() == song.bytes) result.add(song);
            } catch (Exception ignored) { /* Damaged metadata is not a playable download. */ }
        }
        result.sort((a, b) -> LibraryQuery.normalized(a.title).compareTo(LibraryQuery.normalized(b.title)));
        return result;
    }

    Song find(String id) { for (Song song : songs()) if (song.id.equals(id)) return song; return null; }

    void save(Song song) throws Exception {
        JSONObject json = new JSONObject().put("id", song.id).put("title", song.title).put("url", song.url)
                .put("extension", song.extension).put("uploader", song.uploader)
                .put("thumbnail", song.thumbnail).put("bytes", song.bytes).put("duration", song.duration);
        if (!prefs.edit().putString("song:" + song.id, json.toString()).commit()) throw new IOException("Não foi possível salvar a biblioteca offline.");
    }

    void delete(Song song) throws IOException {
        File file = file(song.id, song.extension);
        if (file.exists() && !file.delete()) throw new IOException("Não foi possível excluir o arquivo.");
        prefs.edit().remove("song:" + song.id).apply();
    }

    void recoverInterrupted() {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName();
            // A metadata mismatch must never delete a user's completed download.
            if (name.matches("[A-Za-z0-9_-]{11}\\.(m4a|webm)\\.part")) file.delete();
        }
    }
    String status() { return prefs.getString("status", "Abra um vídeo e use Baixar áudio para ouvir sem internet."); }
    void status(String status) { prefs.edit().putString("status", status).apply(); }
}
