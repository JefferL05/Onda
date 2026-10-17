package app.onda.mobile;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

/** Media3 backend kept behind a tiny adapter so the legacy backend can remain a fallback. */
final class Media3AudioEngine {
    interface Factory { Media3AudioEngine create(Context context); }
    interface Listener {
        void prepared(long duration);
        void completed();
        void buffering(boolean active);
        void failed(String message);
        default void changed() { }
    }
    private final Player player;
    private Listener listener;
    private boolean prepared;
    private boolean released;

    Media3AudioEngine(Context context) {
        this(createPlayer(context));
    }

    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    private static ExoPlayer createPlayer(Context context) {
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(ExtractorHttp.USER_AGENT).setConnectTimeoutMs(20000).setReadTimeoutMs(30000);
        ExoPlayer result = new ExoPlayer.Builder(context.getApplicationContext())
                .setUsePlatformDiagnostics(false)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(context, http)))
                .setWakeMode(C.WAKE_MODE_LOCAL).build();
        // OfflineService is the ONLY owner of audio focus, for both playback engines.
        result.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), false);
        return result;
    }

    /** Same adapter in production and tests; only the platform Player can be substituted. */
    Media3AudioEngine(Player player) {
        this.player = player;
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (listener == null || released) return;
                listener.buffering(state == Player.STATE_BUFFERING);
                if (state == Player.STATE_READY && !prepared) {
                    prepared = true; // A seek/rebuffer may produce READY again. Never seek back again.
                    listener.prepared(duration());
                } else if (state == Player.STATE_ENDED) listener.completed();
            }
            @Override public void onIsPlayingChanged(boolean value) { if (listener != null && !released) listener.changed(); }
            @Override public void onPlayerError(androidx.media3.common.PlaybackException error) {
                if (listener != null && !released) listener.failed(error.getErrorCodeName());
            }
        });
    }
    void listener(Listener value) { listener = value; }
    void setSource(String source) {
        prepared = false;
        player.setMediaItem(MediaItem.fromUri(sourceUri(source)));
        player.prepare();
    }
    static Uri sourceUri(String source) {
        if (source == null || source.trim().isEmpty()) throw new IllegalArgumentException("Fonte de áudio vazia");
        Uri uri;
        if (source.startsWith("content://") || source.startsWith("http://") || source.startsWith("https://")
                || source.startsWith("file://")) uri = Uri.parse(source);
        else uri = Uri.fromFile(new File(source));
        return uri;
    }
    void play() { player.play(); }
    void pause() { player.pause(); }
    boolean playing() { return player.isPlaying(); }
    long duration() { return player.getDuration() < 0 ? 0 : player.getDuration(); }
    long position() { return player.getCurrentPosition(); }
    void seek(long position) { player.seekTo(position); }
    void release() { if (released) return; released = true; listener = null; player.release(); }
}
