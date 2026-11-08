package app.onda.mobile;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * One connection to the playback service, shared by every screen.
 *
 * The activity used to bind in onStart/onStop and each screen kept its own
 * assumptions about the service. Now there is a single owner, a snapshot that
 * any view can read synchronously, and a listener set that fires on real
 * changes (not on a 1 s poll).
 */
final class PlayerController {
    interface Listener {
        /** Playback, queue or download state changed. */
        void onPlayerChanged();

        /** Position advanced; cheap to handle, fired once a second while playing. */
        default void onPosition() { }
    }

    /** Immutable view of the player, safe to read from the main thread at any time. */
    static final class State {
        static final State IDLE = new State();

        String title = "";
        String artist = "";
        String cover = "";
        String url;
        String badge = "";
        String origin = "";
        String engine = "";
        String status = "Nada tocando";
        String lyric = "";
        boolean hasTrack;
        boolean playing;
        boolean loading;
        boolean buffering;
        boolean offline;
        boolean device;
        boolean liked;
        boolean shuffled;
        boolean autoplay;
        int repeat;          // 0 off, 1 all, 2 one
        int durationMs;
        int positionMs;
        long sleepRemainingMs;
        int downloadPercent = -1;
        boolean downloading;
        int queueSize;
        int queueIndex;
        List<DownloadQueue.Job> jobs = new ArrayList<>();
        int revision;

        int progressPercent() {
            return durationMs <= 0 ? 0 : Math.max(0, Math.min(100, (int) (positionMs * 100L / durationMs)));
        }
    }

    private static PlayerController instance;

    static PlayerController get(Context context) {
        if (instance == null) {
            synchronized (PlayerController.class) {
                if (instance == null) instance = new PlayerController(context.getApplicationContext());
            }
        }
        return instance;
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<>();
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (service == null || listeners.isEmpty()) return;
            State snapshot = state();
            if (snapshot.playing) for (Listener listener : new ArrayList<>(listeners)) listener.onPosition();
            main.postDelayed(this, Math.max(500, 1000 - (System.currentTimeMillis() % 1000)));
        }
    };
    private final Runnable changed = () -> {
        state = null;
        for (Listener listener : new ArrayList<>(listeners)) listener.onPlayerChanged();
    };
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((OfflineService.LocalBinder) binder).service();
            service.addListener(changed);
            bound = true;
            state = null;
            changed.run();
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            if (service != null) service.removeListener(changed);
            service = null;
            bound = false;
            state = null;
            changed.run();
        }
    };

    private OfflineService service;
    private boolean bound;
    private int bindCount;
    private State state;

    private PlayerController(Context context) {
        this.context = context;
    }

    void bind() {
        bindCount++;
        if (bindCount > 1) return;
        try {
            context.bindService(new Intent(context, OfflineService.class), connection, Context.BIND_AUTO_CREATE);
        } catch (RuntimeException ignored) { }
        main.removeCallbacks(ticker);
        main.postDelayed(ticker, 1000);
    }

    void unbind() {
        bindCount = Math.max(0, bindCount - 1);
        if (bindCount > 0) return;
        main.removeCallbacks(ticker);
        if (service != null) service.removeListener(changed);
        if (bound) {
            try { context.unbindService(connection); } catch (RuntimeException ignored) { }
        }
        bound = false;
        service = null;
        state = null;
    }

    void addListener(Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    void removeListener(Listener listener) { listeners.remove(listener); }

    OfflineService service() { return service; }

    boolean connected() { return service != null; }

    State state() {
        State current = state;
        if (current != null) {
            // Position advances continuously; the heavier fields stay cached
            // until the service reports a real change.
            if (service != null) {
                current.positionMs = service.position();
                current.durationMs = service.duration();
                if (current.url != null) current.lyric = new LyricsStore(context).lineAt(current.url, current.positionMs);
            }
            return current;
        }
        State next = snapshot();
        state = next;
        return next;
    }

    private State snapshot() {
        State s = new State();
        OfflineService player = service;
        if (player == null) return s;
        s.title = player.title();
        s.artist = player.uploader();
        s.cover = player.cover();
        s.url = player.currentUrl();
        s.hasTrack = player.hasTrack();
        s.playing = player.playing();
        s.loading = player.loading();
        s.buffering = player.buffering();
        s.device = player.isDeviceTrack();
        s.offline = s.device || new OfflineStore(context).has(player.currentId());
        s.liked = s.url != null && LibraryStore.shared(context).liked(s.url);
        s.shuffled = player.shuffled();
        s.autoplay = player.autoplay();
        s.repeat = player.repeat().ordinal();
        s.durationMs = player.duration();
        s.positionMs = player.position();
        s.sleepRemainingMs = player.sleepRemaining();
        s.origin = player.originLabel();
        s.status = player.playerStatus();
        s.engine = player.engineName();
        s.badge = !s.hasTrack ? "" : s.offline ? "Baixada" : "Online";
        s.downloading = player.downloading();
        s.downloadPercent = player.progress();
        s.jobs = player.downloadJobs();
        s.queueSize = player.queueOrder().size();
        s.queueIndex = player.queueOrder().indexOf(s.url);
        s.revision = player.revision();
        s.lyric = s.url == null ? "" : new LyricsStore(context).upNext(s.url, s.positionMs);
        return s;
    }

    // ------------------------------------------------------------- commands
    void toggle() { send(OfflineService.TOGGLE); }
    void next() { send(OfflineService.NEXT); }
    void previous() { send(OfflineService.PREVIOUS); }
    void stop() { send(OfflineService.STOP); }

    void seek(int ms) {
        if (service != null) service.seek(ms);
    }

    void toggleShuffle() { if (service != null) service.toggleShuffle(); }
    void cycleRepeat() { if (service != null) service.cycleRepeat(); }
    void autoplay(boolean enabled) { if (service != null) service.autoplay(enabled); }
    void sleepAfter(int minutes) { if (service != null) service.sleepAfter(minutes); }
    void setVolume(int percent) { if (service != null) service.volume(percent); }
    void pauseDownloads() { if (service != null) service.pauseDownloads(); }
    void cancelDownload(String id) { if (service != null) service.cancelDownload(id); }
    void retryDownload(String id) { if (service != null) service.retryDownload(id); }
    void clearFinished() { if (service != null) service.clearFinishedDownloads(); }

    void playNow(LibraryStore.Track track) {
        playTrack(track, true);
    }

    void playTrack(LibraryStore.Track track, boolean contextStart) {
        if (track == null || UrlPolicy.canonicalVideo(track.url) == null) return;
        LibraryStore.shared(context).appendQueue(track);
        context.startForegroundService(new Intent(context, OfflineService.class)
                .setAction(OfflineService.PLAY_TRACK)
                .putExtra("url", track.url).putExtra("title", track.title)
                .putExtra("uploader", track.uploader).putExtra("thumbnail", track.thumbnail)
                .putExtra("durationSec", track.durationSec)
                .putExtra("context_start", contextStart));
    }

    void playCollection(List<LibraryStore.Track> items, String name, String key, LibraryStore.Track selected,
                        boolean shuffle) {
        if (items == null || items.isEmpty() || selected == null) return;
        LibraryStore library = LibraryStore.shared(context);
        library.beginContext(items, name, key != null && key.startsWith("remote:album") ? "album" : "playlist");
        context.startForegroundService(new Intent(context, OfflineService.class)
                .setAction(OfflineService.PLAY_TRACK)
                .putExtra("url", selected.url).putExtra("title", selected.title)
                .putExtra("uploader", selected.uploader).putExtra("thumbnail", selected.thumbnail)
                .putExtra("durationSec", selected.durationSec)
                .putExtra("context_start", true).putExtra("shuffle", shuffle));
    }

    void playQueueItem(String url, String title) {
        context.startForegroundService(new Intent(context, OfflineService.class)
                .setAction(OfflineService.PLAY_TRACK).putExtra("url", url).putExtra("title", title));
    }

    void enqueue(LibraryStore.Track track, boolean next) {
        if (track == null) return;
        LibraryStore.shared(context).enqueueManual(track, next);
    }

    void download(LibraryStore.Track track) {
        if (track == null) return;
        context.startForegroundService(new Intent(context, OfflineService.class)
                .setAction(OfflineService.DOWNLOAD)
                .putExtra("url", track.url).putExtra("title", track.title));
    }

    void downloadAll(List<LibraryStore.Track> tracks) {
        if (tracks == null || tracks.isEmpty()) return;
        LibraryStore.shared(context).replaceQueue(new ArrayList<>(tracks));
        for (LibraryStore.Track track : tracks) download(track);
    }

    void playLocal(String uri, String title, String artist, String album, String cover, long duration) {
        context.startForegroundService(new Intent(context, OfflineService.class)
                .setAction(OfflineService.PLAY_LOCAL).putExtra("uri", uri).putExtra("title", title)
                .putExtra("artist", artist).putExtra("album", album).putExtra("cover", cover)
                .putExtra("duration", duration));
    }

    private void send(String action) {
        try {
            context.startForegroundService(new Intent(context, OfflineService.class).setAction(action));
        } catch (RuntimeException ignored) { }
    }
}
