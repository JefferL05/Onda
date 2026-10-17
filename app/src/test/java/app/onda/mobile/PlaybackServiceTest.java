package app.onda.mobile;

import android.content.*;
import android.media.AudioManager;
import android.os.Looper;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.shadows.util.DataSource;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import static org.junit.Assert.*;

/** Android lifecycle simulation. This does not claim physical screen-lock/Bluetooth testing. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
@LooperMode(LooperMode.Mode.PAUSED)
public class PlaybackServiceTest {
    private static final String A = "https://m.youtube.com/watch?v=aqz-KE-bpKQ";
    private static final String B = "https://m.youtube.com/watch?v=dQw4w9WgXcQ";
    private ServiceController<OfflineService> controller;
    private OfflineService service;
    private final List<ShadowMediaPlayer> players = new ArrayList<>();

    @Before public void setup() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        for (String prefs : new String[]{"offline", "playback", "library"}) context.getSharedPreferences(prefs, 0).edit().clear().commit();
        seed(context, A); seed(context, B);
        new LibraryStore(context).replaceQueue(Arrays.asList(new LibraryStore.Track("Primeira", A), new LibraryStore.Track("Segunda", B)));
        ShadowMediaPlayer.setCreateListener((player, shadow) -> {
            shadow.setInvalidStateBehavior(ShadowMediaPlayer.InvalidStateBehavior.ASSERT);
            players.add(shadow);
        });
        create();
    }
    private void seed(Context context, String url) throws Exception {
        OfflineStore store = new OfflineStore(context);
        String id = OfflineStore.id(url);
        java.io.File file = store.file(id, "m4a");
        Files.write(file.toPath(), new byte[16]);
        store.save(new OfflineStore.Song(id, "Faixa " + id, url, "m4a", 16, 120000));
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(file.getAbsolutePath()), new ShadowMediaPlayer.MediaInfo(120000, 0));
    }
    private void create() {
        controller = Robolectric.buildService(OfflineService.class).create(); service = controller.get();
        // These tests explicitly cover the production fallback, not Media3 playback.
        service.media3Factory = context -> { throw new IllegalStateException("Forced initialization failure for legacy regression"); };
    }
    private void play(String url) {
        service.onStartCommand(new Intent(service, OfflineService.class).setAction(OfflineService.PLAY_TRACK)
                .putExtra("url", url).putExtra("title", "Faixa"), 0, 1);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
    @After public void cleanup() { if (controller != null) controller.destroy(); }

    @Test public void detachingUiDoesNotStopStartedPlaybackAndNoisyPauses() {
        Runnable listener = () -> {};
        service.addListener(listener);
        play(A); assertTrue(service.playerStatus(), service.playing());
        service.removeListener(listener);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        assertTrue(service.playing()); assertTrue(service.position() >= 3000);
        service.sendBroadcast(new Intent(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse(service.playing());
    }
    @Test public void completionAdvancesAndEndOfQueueStops() {
        play(A);
        players.get(players.size() - 1).invokeCompletionListener();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(B, service.currentUrl()); assertTrue(service.playing());
        players.get(players.size() - 1).invokeCompletionListener();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse(service.playing()); assertFalse(service.loading());
    }
    @Test public void recreationRestoresPositionButNeverStartsAudio() {
        play(A); service.seek(42000); Shadows.shadowOf(Looper.getMainLooper()).idle(); service.pause();
        service.cycleRepeat(); service.toggleShuffle();
        controller.destroy(); controller = null; create();
        assertEquals(A, service.currentUrl()); assertEquals(42000, service.position());
        assertFalse(service.playing()); assertFalse(service.loading());
        assertTrue(service.shuffled()); assertEquals(PlaybackQueue.Repeat.ALL, service.repeat());
    }
    @Test public void stopDuringPreparationIgnoresLatePreparedCallback() {
        OfflineStore store = new OfflineStore(service);
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(store.file(OfflineStore.id(A), "m4a").getAbsolutePath()),
                new ShadowMediaPlayer.MediaInfo(120000, -1));
        play(A); assertTrue(service.loading());
        ShadowMediaPlayer old = players.get(players.size() - 1);
        service.stopPlayer();
        old.invokePreparedListener();
        assertFalse(service.playing()); assertFalse(service.loading());
    }
    @Test public void serviceMigrationPreservesFavoritesPlaylistsAndDownloadedFiles() {
        LibraryStore library = new LibraryStore(service);
        library.add(LibraryStore.FAVORITES, new LibraryStore.Track("Favorita", A));
        library.createPlaylist("Minha lista"); library.add(LibraryStore.playlistKey("Minha lista"), new LibraryStore.Track("Faixa", B));
        play(A); controller.destroy(); controller = null; create();
        assertEquals(1, new LibraryStore(service).tracks(LibraryStore.FAVORITES).size());
        assertEquals(1, new LibraryStore(service).tracks(LibraryStore.playlistKey("Minha lista")).size());
        assertEquals(2, new OfflineStore(service).songs().size());
        assertFalse(service.playing());
    }
    @Test public void focusLossPausesAndDeniedFocusNeverStartsAudio() {
        AudioManager audio = service.getSystemService(AudioManager.class);
        play(A); assertTrue(service.playing());
        Shadows.shadowOf(audio).getLastAudioFocusRequest().listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
        assertFalse(service.playing());
        Shadows.shadowOf(audio).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED);
        service.resume(); assertFalse(service.playing());
        Shadows.shadowOf(audio).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        service.resume(); assertTrue(service.playing());
    }
    @Test public void notificationPublishesMediaTokenAndPreviousPlayNextActions() {
        play(A);
        android.app.Notification notification = Shadows.shadowOf(service).getLastForegroundNotification();
        assertNotNull(notification);
        assertEquals(android.app.Notification.VISIBILITY_PUBLIC, notification.visibility);
        assertNotNull(notification.extras.getParcelable(android.app.Notification.EXTRA_MEDIA_SESSION));
        assertEquals("Anterior", notification.actions[0].title.toString());
        assertEquals("Pausar", notification.actions[1].title.toString());
        assertEquals("Próxima", notification.actions[2].title.toString());
    }
    @Test public void sleepTimerPausesInsteadOfAdvancingOrDestroyingQueue() {
        play(A); service.sleepAfter(1);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61));
        assertFalse(service.playing()); assertEquals(A, service.currentUrl());
        assertEquals(0, service.sleepRemaining()); assertEquals(2, service.queueOrder().size());
    }
}
