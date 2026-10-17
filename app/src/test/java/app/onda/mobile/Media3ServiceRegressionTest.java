package app.onda.mobile;

import android.content.*;
import android.media.AudioManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Looper;
import androidx.media3.common.Player;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.shadows.util.DataSource;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
@LooperMode(LooperMode.Mode.PAUSED)
public class Media3ServiceRegressionTest {
    static final String A = "https://m.youtube.com/watch?v=aqz-KE-bpKQ";
    static final String B = "https://m.youtube.com/watch?v=dQw4w9WgXcQ";
    ServiceController<OfflineService> controller;
    OfflineService service;
    final List<ControlledMedia3Player> engines = new ArrayList<>();

    @Before public void setup() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        for (String prefs : new String[]{"offline", "playback", "library"}) context.getSharedPreferences(prefs, 0).edit().clear().commit();
        OfflineStore store = new OfflineStore(context);
        for (String url : Arrays.asList(A, B)) {
            String id = OfflineStore.id(url); File file = store.file(id, "m4a");
            Files.write(file.toPath(), new byte[16]);
            store.save(new OfflineStore.Song(id, "Test", url, "m4a", 16, 120000));
            ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(file.getAbsolutePath()), new ShadowMediaPlayer.MediaInfo(120000, 0));
        }
        new LibraryStore(context).replaceQueue(Arrays.asList(new LibraryStore.Track("A", A), new LibraryStore.Track("B", B)));
        controller = Robolectric.buildService(OfflineService.class).create(); service = controller.get();
        service.media3Factory = c -> {
            ControlledMedia3Player engine = new ControlledMedia3Player(); engines.add(engine);
            return new Media3AudioEngine(engine.player);
        };
    }
    @After public void cleanup() { if (controller != null) controller.destroy(); }
    void play(String url) {
        service.onStartCommand(new Intent(service, OfflineService.class).setAction(OfflineService.PLAY_TRACK)
                .putExtra("url", url).putExtra("title", "Test"), 0, 1);
    }
    ControlledMedia3Player active() { return engines.get(engines.size() - 1); }
    void idle() { Shadows.shadowOf(Looper.getMainLooper()).idle(); }

    @Test public void downloadedMusicStartsWithoutRecreatingTheEngine() {
        play(A); ControlledMedia3Player engine = active();
        assertEquals("file", engine.mediaItem.localConfiguration.uri.getScheme());
        engine.state(Player.STATE_READY);
        assertTrue(service.playerStatus(), service.playing());
        assertEquals(1, engines.size()); assertEquals(1, engine.prepareCalls);
        service.pause(); service.resume();
        assertTrue(service.playing()); assertEquals(1, engines.size());
    }
    @Test public void readyAfterSeekOrRebufferNeverRewindsOrResumesAPausedTrack() {
        play(A); ControlledMedia3Player engine = active(); engine.state(Player.STATE_READY);
        service.seek(42000); service.pause();
        int plays = engine.playCalls;
        engine.position = 60000;
        engine.state(Player.STATE_BUFFERING); engine.state(Player.STATE_READY);
        assertEquals(1, engine.seekCalls); assertEquals(60000, service.position());
        assertEquals(plays, engine.playCalls); assertFalse(service.playing());
    }
    @Test public void media3KeepsMediaNotificationWhenADownloadIsAlreadyPresent() {
        play(A); active().state(Player.STATE_READY);
        service.enqueueDownload(A, "Already downloaded");
        assertTrue(service.playing());
        assertFalse(Shadows.shadowOf(service).isForegroundStopped());
        android.app.Notification n = Shadows.shadowOf(service).getLastForegroundNotification();
        assertEquals("Test", n.extras.getString(android.app.Notification.EXTRA_TITLE));
        assertEquals("Pausar", n.actions[1].title.toString());
    }
    @Test public void fallbackPreservesPositionAndPauseIntent() {
        play(A); ControlledMedia3Player engine = active(); engine.state(Player.STATE_READY);
        service.seek(37000); service.pause(); engine.fail(); idle();
        assertTrue(engine.released); assertEquals("Compatibilidade (MediaPlayer)", service.engineName());
        assertFalse(service.loading()); assertFalse(service.playing()); assertEquals(37000, service.position());
        service.resume(); assertTrue(service.playing());
    }
    @Test public void callbacksFromReplacedEngineCannotRestartOrSkipTheNewTrack() {
        play(A); ControlledMedia3Player old = active();
        play(B); ControlledMedia3Player next = active(); next.state(Player.STATE_READY);
        old.state(Player.STATE_READY); old.fail(); old.state(Player.STATE_ENDED); idle();
        assertEquals(B, service.currentUrl()); assertTrue(service.playing()); assertEquals(2, engines.size());
    }
    @Test public void contentMusicDoesNotWriteNullIntoYoutubeHistoryAndCanResumeAfterStop() {
        String uri = "content://media/external/audio/media/42";
        service.onStartCommand(new Intent(service, OfflineService.class).setAction(OfflineService.PLAY_LOCAL)
                .putExtra("uri", uri).putExtra("title", "Local file").putExtra("duration", 120000L), 0, 1);
        active().state(Player.STATE_READY);
        assertTrue(service.playing()); assertTrue(service.isDeviceTrack());
        assertEquals("content", active().mediaItem.localConfiguration.uri.getScheme());
        assertTrue(new LibraryStore(service).tracks(LibraryStore.HISTORY).isEmpty());
        service.seek(24000); service.stopPlayer(); service.resume(); active().state(Player.STATE_READY);
        assertTrue(service.playing()); assertEquals(24000, service.position());
        active().state(Player.STATE_ENDED);
        assertFalse(service.playing()); assertTrue(service.isDeviceTrack());
        assertNull(service.currentUrl()); // Never jumps into an unrelated YouTube queue.
    }
    @Test public void focusLossDuringBufferingAlsoCancelsPlayWhenReady() {
        play(A); active().state(Player.STATE_READY);
        active().playing = false; active().state(Player.STATE_BUFFERING);
        AudioManager audio = service.getSystemService(AudioManager.class);
        Shadows.shadowOf(audio).getLastAudioFocusRequest().listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);
        assertFalse(active().requested); active().state(Player.STATE_READY); assertFalse(service.playing());
    }
    @Test public void completeDownloadsSurviveMissingOrMismatchedMetadata() throws Exception {
        OfflineStore store = new OfflineStore(service);
        File orphan = store.file("abcdefghijk", "m4a"); Files.write(orphan.toPath(), new byte[16]);
        File partial = new File(orphan + ".part"); Files.write(partial.toPath(), new byte[2]);
        store.recoverInterrupted(); assertTrue(orphan.exists()); assertFalse(partial.exists());
    }
    @Test public void pathWithSpacesAndContentUrisStayIntact() {
        assertEquals(Uri.fromFile(new File("/data/user/0/app/files/a #b.m4a")), Media3AudioEngine.sourceUri("/data/user/0/app/files/a #b.m4a"));
        assertEquals(Uri.parse("content://media/external/audio/media/1"), Media3AudioEngine.sourceUri("content://media/external/audio/media/1"));
    }
}
