package app.onda.mobile;

import android.app.*;
import android.content.Intent;
import android.widget.Toast;
import java.util.*;

final class LibraryDialogs {
    static final int EDIT = 52;
    static void save(Activity activity, LibraryStore.Track track, Runnable changed) {
        if (track == null) return;
        LibraryStore library = new LibraryStore(activity);
        List<String> names = library.playlists();
        List<String> labels = new ArrayList<>(Arrays.asList("Curtir música", "Criar playlist"));
        labels.addAll(names);
        new AlertDialog.Builder(activity).setTitle("Adicionar a…").setItems(labels.toArray(new String[0]), (d, which) -> {
            if (which == 1) { edit(activity, null, track); return; }
            library.add(which == 0 ? LibraryStore.FAVORITES : LibraryStore.playlistKey(names.get(which-2)), track);
            Toast.makeText(activity, "Música salva", Toast.LENGTH_SHORT).show(); changed.run();
        }).show();
    }
    static void edit(Activity activity, String name, LibraryStore.Track track) {
        Intent intent = new Intent(activity, PlaylistEditActivity.class).putExtra("name", name);
        if (track != null) intent.putExtra("track_url", track.url).putExtra("track_title", track.title)
                .putExtra("track_artist", track.uploader).putExtra("track_cover", track.thumbnail).putExtra("track_duration", track.durationSec);
        activity.startActivityForResult(intent, EDIT);
    }
}
