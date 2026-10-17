package app.onda.mobile;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.util.ArrayList;
import java.util.List;

/** Read-only view of music already owned by the device. */
final class LocalMediaStore {
    static final class Track {
        final String uri, title, artist, album, cover;
        final long duration, size;
        Track(String uri, String title, String artist, String album, String cover, long duration, long size) {
            this.uri = uri; this.title = title; this.artist = artist; this.album = album;
            this.cover = cover; this.duration = duration; this.size = size;
        }
    }

    private LocalMediaStore() { }

    static String permission() {
        return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    static boolean allowed(Context context) {
        return Build.VERSION.SDK_INT < 23 || context.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED;
    }

    static List<Track> scan(Context context) {
        List<Track> result = new ArrayList<>();
        if (!allowed(context)) return result;
        ContentResolver resolver = context.getContentResolver();
        String[] columns = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.ALBUM_ID};
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String sort = MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC";
        try (Cursor cursor = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, columns,
                selection, null, sort)) {
            if (cursor == null) return result;
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE);
            int albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                String uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, String.valueOf(id)).toString();
                String albumId = cursor.getString(albumIdColumn);
                String cover = albumId == null ? "" : Uri.withAppendedPath(
                        Uri.parse("content://media/external/audio/albumart"), albumId).toString();
                result.add(new Track(uri, safe(cursor.getString(titleColumn), "Sem título"),
                        safe(cursor.getString(artistColumn), "Artista desconhecido"),
                        safe(cursor.getString(albumColumn), "Álbum desconhecido"), cover,
                        cursor.getLong(durationColumn), cursor.getLong(sizeColumn)));
            }
        } catch (SecurityException ignored) { }
        return result;
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() || "<unknown>".equalsIgnoreCase(value) ? fallback : value;
    }
}
