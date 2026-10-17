package app.onda.mobile;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.media.*;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.*;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiManager;
import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/** One download worker and one unified local/online player, independent of activities. */
public final class OfflineService extends Service {
    static final String DOWNLOAD = "onda.DOWNLOAD", PLAY = "onda.PLAY", TOGGLE = "onda.TOGGLE",
            STOP = "onda.STOP", CANCEL = "onda.CANCEL", PLAY_TRACK = "onda.PLAY_TRACK",
            NEXT = "onda.NEXT", PREVIOUS = "onda.PREVIOUS", PAUSE = "onda.PAUSE",
            PLAY_LOCAL = "onda.PLAY_LOCAL", PLAY_QUEUE_ITEM="onda.PLAY_QUEUE_ITEM";
    private static final int NOTIFICATION = 300;
    private static final String CHANNEL = "offline";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService resolver = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final LocalBinder binder = new LocalBinder();
    private final DownloadQueue downloadQueue = new DownloadQueue();
    private String activeDownloadId;
    private String activeDownloadTitle = "";
    private boolean wifiOnly;
    private boolean dataSaver;
    private ConnectivityManager.NetworkCallback networkCallback;
    private OfflineStore store;
    private LibraryStore library;
    private LyricsStore lyrics;
    private SharedPreferences preferences;
    private SharedPreferences libraryPreferences;
    private final PlaybackQueue queue = new PlaybackQueue();
    private SharedPreferences.OnSharedPreferenceChangeListener queueListener;
    private MediaPlayer player;
    private Media3AudioEngine media3;
    Media3AudioEngine.Factory media3Factory = Media3AudioEngine::new;
    RadioProvider radioProvider = RadioProvider.youtube();
    private boolean radioLoading;
    private boolean media3Active = true;
    private MediaSession session;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private LibraryStore.Track current;
    private boolean ready, downloading, destroyed, loading, online, wantsPlay, buffering;
    private String localUri, localTitle, localArtist, localCover;
    private String localAlbum="";
    private final List<LocalMediaStore.Track> localTracks=new ArrayList<>();
    private final PlaybackQueue localOrder=new PlaybackQueue();
    private long localDuration;
    private int savedPosition, generation, ticks;
    private long sleepDeadline;
    private Future<?> pendingResolve;
    private PowerManager.WakeLock preparationLock;
    private WifiManager.WifiLock wifiLock;
    private String status, playerStatus = "Nenhum áudio selecionado";
    private int progress = -1, revision;
    private final Set<Runnable> listeners = new HashSet<>();
    private PowerManager.WakeLock downloadLock;
    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { pause(); }
    };

    final class LocalBinder extends Binder { OfflineService service() { return OfflineService.this; } }
    @Override public IBinder onBind(Intent intent) { return binder; }

    @Override public void onCreate() {
        super.onCreate();
        store = new OfflineStore(this);
        library = new LibraryStore(this);
        lyrics = new LyricsStore(this);
        preferences = getSharedPreferences("playback", MODE_PRIVATE);
        libraryPreferences = getSharedPreferences("library", MODE_PRIVATE);
        syncQueue();
        PlaybackSnapshot snapshot = PlaybackSnapshot.decode(preferences.getString("snapshot", ""));
        queue.restore(snapshot.order, snapshot.shuffle, snapshot.repeat, snapshot.url);
        queue.restoreCursor(snapshot.contextCursor);
        if (snapshot.url != null) {
            current = new LibraryStore.Track(snapshot.title, snapshot.url);
            savedPosition = snapshot.position;
            playerStatus = "Sessão restaurada · toque em Ouvir";
        }
        queueListener = (prefs, key) -> {
            if (LibraryStore.QUEUE.equals(key) || "manual_pending".equals(key) || (key != null && key.startsWith("origin:"))) {
                syncQueue(); persist(); updateSession(); emit();
            }
        };
        libraryPreferences.registerOnSharedPreferenceChangeListener(queueListener);
        status = store.status();
        wifiOnly = preferences.getBoolean("wifiOnly", false);
        dataSaver = preferences.getBoolean("dataSaver", false);
        restoreDownloadQueue();
        if (preferences.getBoolean("autoOffline", false)) main.postDelayed(this::queueAutomaticOffline, 1500);
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL, "Música e downloads", NotificationManager.IMPORTANCE_LOW));
        audio = getSystemService(AudioManager.class);
        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(change -> { if (change < 0) pause(); }, main).build();
        session = new MediaSession(this, "Onda");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { resume(); }
            @Override public void onPause() { pause(); }
            @Override public void onStop() { stopPlayer(); }
            @Override public void onSeekTo(long pos) { seek((int) pos); }
            @Override public void onSkipToNext() { next(false); }
            @Override public void onSkipToPrevious() { previous(); }
            @Override public void onSkipToQueueItem(long id) {
                List<String> order = queueOrder();
                if (id >= 0 && id < order.size()) playQueueItem(order.get((int) id));
            }
        });
        Bundle slots = new Bundle();
        slots.putBoolean("android.media.playback.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_PREVIOUS", true);
        slots.putBoolean("android.media.playback.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_NEXT", true);
        session.setExtras(slots);
        updateSession();
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(noisy, filter);
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(android.net.Network network) { main.post(() -> pumpDownloads()); }
            @Override public void onLost(android.net.Network network) { main.post(() -> emit()); }
        };
        try { getSystemService(ConnectivityManager.class).registerDefaultNetworkCallback(networkCallback); }
        catch (Exception ignored) { networkCallback = null; }
        main.post(tick);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (DOWNLOAD.equals(action)) enqueueDownload(intent.getStringExtra("url"), intent.getStringExtra("title"));
        else if (PLAY.equals(action)) play(intent.getStringExtra("id"));
        else if (PLAY_TRACK.equals(action)) {
            playTrack(intent.getStringExtra("url"),
                intent.getStringExtra("title"), intent.getStringExtra("uploader"),
                intent.getStringExtra("thumbnail"), intent.getLongExtra("durationSec", -1));
            if(intent.getBooleanExtra("context_start",false)) {
                queue.shuffle(false);queue.shuffle(intent.getBooleanExtra("shuffle",false));persist();updateSession();emit();
            }
        }
        else if (PLAY_LOCAL.equals(action)) {
            if(intent.hasExtra("album_queue")) restoreLocalQueue(intent.getStringExtra("album_queue"));
            playLocal(intent.getStringExtra("uri"), intent.getStringExtra("title"),
                intent.getStringExtra("artist"), intent.getStringExtra("album"), intent.getStringExtra("cover"),
                intent.getLongExtra("duration", 0));
        }
        else if (TOGGLE.equals(action)) { if (playing() || (loading && wantsPlay)) pause(); else resume(); }
        else if (NEXT.equals(action)) next(false);
        else if (PREVIOUS.equals(action)) previous();
        else if (PAUSE.equals(action)) pause();
        else if (PLAY_QUEUE_ITEM.equals(action)) playQueueItem(intent.getStringExtra("url"));
        else if (STOP.equals(action)) stopPlayer();
        else if (CANCEL.equals(action)) cancelDownload();
        return START_NOT_STICKY;
    }

    void addListener(Runnable callback) { listeners.add(callback); }
    void removeListener(Runnable callback) { listeners.remove(callback); }
    boolean downloading() { return downloading; }
    String status() { return status; }
    int progress() { return progress; }
    int revision() { return revision; }
    String playerStatus() { return buffering && wantsPlay ? "Carregando áudio…" : playerStatus; }
    String title() { return localUri != null ? localTitle : current == null ? "Seu player" : current.title; }
    String uploader() { return localUri != null ? localArtist : current == null ? "" : current.uploader; }
    String cover() { return localUri != null ? localCover : current == null ? "" : current.thumbnail; }
    LibraryStore.Track currentTrack() { return current; }
    String currentId() { return current == null ? null : OfflineStore.id(current.url); }
    String currentUrl() { return localUri == null && current != null ? current.url : null; }
    boolean isDeviceTrack() { return localUri != null; }
    String engineName() { return media3Active ? "Media3" : "Compatibilidade (MediaPlayer)"; }
    boolean autoplay() { return preferences.getBoolean("autoplay", false); } // Radio is opt-in: queue end stops by default.
    void autoplay(boolean value) {
        preferences.edit().putBoolean("autoplay", value).apply();
        if (!value && radioLoading) stopPlayer();
        emit();
    }
    String originLabel() {
        if (isDeviceTrack()) return localAlbum.isEmpty()?"Arquivo do celular":"Álbum · "+localAlbum;
        if (current == null) return library.contextName();
        String origin = library.origin(current.url);
        return "radio".equals(origin) ? "Rádio · sugestão automática" : "manual".equals(origin) ? "Sua fila · adicionada por você" : library.contextName();
    }
    void volume(int percent) { audio.setStreamVolume(AudioManager.STREAM_MUSIC,
            Math.round(Math.max(0,Math.min(100,percent)) * audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 100f), 0); }
    int volume() { int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC); return max == 0 ? 0 : audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max; }
    boolean hasTrack() { return current != null || localUri != null; }
    boolean loading() { return loading; }
    boolean playRequested() { return wantsPlay; }
    boolean shuffled() { return queue.shuffled(); }
    PlaybackQueue.Repeat repeat() { return queue.repeat(); }
    List<String> queueOrder() { return localUri==null?queue.order():localOrder.order(); }
    String queueTitle(String url) {
        if(url!=null&&url.startsWith("content://")) {for(LocalMediaStore.Track t:localTracks)if(t.uri.equals(url))return t.title;return "Arquivo local";}
        for(LibraryStore.Track t:library.tracks(LibraryStore.QUEUE))if(t.url.equals(url))return t.title;
        return "Música";
    }
    private void playQueueItem(String url) {
        if(url!=null&&url.startsWith("content://")) {if(!playLocalQueued(url))finishIdle();}
        else if(UrlPolicy.canonicalVideo(url==null?"":url)!=null)select(url,0);
        else finishIdle();
    }
    boolean playing() { return ready && (media3Active ? media3 != null && media3.playing() : player != null && player.isPlaying()); }
    int duration() { return ready ? (media3Active && media3 != null ? boundedMs(media3.duration()) : player != null ? player.getDuration() : 0) : boundedMs(localDuration); }
    int position() { return ready ? (media3Active && media3 != null ? boundedMs(media3.position()) : player != null ? player.getCurrentPosition() : savedPosition) : savedPosition; }
    private static int boundedMs(long ms) { return (int) Math.max(0, Math.min(Integer.MAX_VALUE, ms)); }
    private boolean hasEngine() { return media3Active ? media3 != null : player != null; }
    private boolean hasActivePlayback() { return loading || hasEngine(); }

    private void syncQueue() {
        List<String> urls = new ArrayList<>();
        Set<String> manual = new HashSet<>();
        for (LibraryStore.Track track : library.tracks(LibraryStore.QUEUE)) {
            urls.add(track.url); if ("manual".equals(library.origin(track.url))) manual.add(track.url);
        }
        queue.sync(urls);
        queue.prioritize(manual, library.pendingManual());
    }
    private LibraryStore.Track track(String url) {
        for (LibraryStore.Track track : library.tracks(LibraryStore.QUEUE)) if (track.url.equals(url)) return track;
        if (current != null && current.url.equals(url)) return current;
        OfflineStore.Song local = store.find(OfflineStore.id(url));
        return new LibraryStore.Track(local == null ? "Áudio do YouTube" : local.title, url);
    }
    private void persist() {
        if (preferences == null) return;
        PlaybackSnapshot snapshot = new PlaybackSnapshot();
        snapshot.url = currentUrl(); snapshot.title = title(); snapshot.position = position();
        snapshot.order = queue.order(); snapshot.shuffle = queue.shuffled(); snapshot.repeat = queue.repeat();
        snapshot.contextCursor = queue.cursor();
        preferences.edit().putString("snapshot", snapshot.encode()).apply();
    }
    void toggleShuffle() { queue.shuffle(!queue.shuffled());localOrder.shuffle(queue.shuffled()); persist(); updateSession(); emit(); }
    void cycleRepeat() { queue.repeat(PlaybackQueue.Repeat.values()[(queue.repeat().ordinal() + 1) % 3]);localOrder.repeat(queue.repeat()); persist(); updateSession(); emit(); }
    void sleepAfter(int minutes) {
        sleepDeadline = minutes <= 0 ? 0 : SystemClock.elapsedRealtime() + minutes * 60000L;
        emit();
    }
    long sleepRemaining() { return sleepDeadline == 0 ? 0 : Math.max(0, sleepDeadline - SystemClock.elapsedRealtime()); }

    private void download(String url) {
        enqueueDownload(url, null);
    }

    void enqueueDownload(String url, String title) {
        String canonical = UrlPolicy.canonicalVideo(url == null ? "" : url);
        if (canonical == null) { status = "Link inválido. Abra um vídeo antes de baixar."; finishIdle(); return; }
        String id = OfflineStore.id(canonical);
        if (store.find(id) != null) { status = "Este áudio já está disponível em Baixadas."; finishIdle(); return; }
        try {
            DownloadQueue.Job job = downloadQueue.enqueue(canonical, title == null ? "Áudio do YouTube" : title);
            persistDownloadQueue();
            status = "Na fila de downloads (" + downloadQueue.pendingCount() + " pendentes)";
            emit();
            pumpDownloads();
        } catch (IllegalArgumentException e) {
            status = "Link inválido. Abra um vídeo antes de baixar."; finishIdle();
        } catch (IllegalStateException e) {
            status = "Fila de downloads cheia (50). Conclua ou cancele itens."; finishIdle();
        }
    }

    void enqueueTracks(List<LibraryStore.Track> tracks) {
        int added = 0;
        for (LibraryStore.Track track : tracks) {
            try {
                String canonical = UrlPolicy.canonicalVideo(track.url);
                if (canonical == null) continue;
                if (store.find(OfflineStore.id(canonical)) != null) continue;
                downloadQueue.enqueue(canonical, track.title);
                added++;
            } catch (Exception ignored) { }
        }
        if (added > 0) { persistDownloadQueue(); status = added + " músicas na fila de downloads"; }
        else status = "Nada novo para baixar: itens já estão salvos ou são inválidos.";
        emit();
        pumpDownloads();
    }

    List<DownloadQueue.Job> downloadJobs() { return downloadQueue.list(); }
    boolean isWifiOnly() { return wifiOnly; }
    boolean isDataSaver() { return dataSaver; }
    void setWifiOnly(boolean enabled) {
        wifiOnly = enabled;
        preferences.edit().putBoolean("wifiOnly", enabled).apply();
        emit();
        pumpDownloads();
    }
    void setDataSaver(boolean enabled) {
        dataSaver = enabled;
        preferences.edit().putBoolean("dataSaver", enabled).apply();
        emit();
    }

    boolean isAutoOffline() { return preferences.getBoolean("autoOffline", false); }
    void setAutoOffline(boolean enabled) {
        preferences.edit().putBoolean("autoOffline", enabled).apply();
        if (enabled) queueAutomaticOffline();
        emit();
    }
    private void queueAutomaticOffline() {
        if (!isAutoOffline()) return;
        List<LibraryStore.Track> tracks = new ArrayList<>();
        tracks.addAll(library.tracks(LibraryStore.FAVORITES));
        tracks.addAll(library.tracks(LibraryStore.QUEUE));
        enqueueTracks(tracks);
    }

    boolean onWifi() {
        ConnectivityManager connectivity = getSystemService(ConnectivityManager.class);
        NetworkCapabilities network = connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        return network != null && network.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    boolean hasInternet() {
        ConnectivityManager connectivity = getSystemService(ConnectivityManager.class);
        NetworkCapabilities network = connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        return network != null && network.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void restoreDownloadQueue() {
        try {
            org.json.JSONArray array = new org.json.JSONArray(preferences.getString("download_jobs", "[]"));
            for (int i = 0; i < array.length(); i++) {
                org.json.JSONObject item = array.getJSONObject(i);
                String url = UrlPolicy.canonicalVideo(item.optString("url"));
                if (url == null) continue;
                try {
                    DownloadQueue.Job job = downloadQueue.enqueue(url, item.optString("title", "Áudio do YouTube"));
                    job.attempts = item.optInt("attempts", 0);
                    if (job.attempts >= 3) job.state = DownloadQueue.State.FAILED;
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
    }

    private void persistDownloadQueue() {
        try {
            org.json.JSONArray array = new org.json.JSONArray();
            for (DownloadQueue.Job job : downloadQueue.list()) {
                if (job.state == DownloadQueue.State.DONE) continue;
                array.put(new org.json.JSONObject().put("url", job.url).put("title", job.title)
                        .put("attempts", job.attempts));
            }
            preferences.edit().putString("download_jobs", array.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void pumpDownloads() {
        if (destroyed || downloading) return;
        if (!hasInternet()) {
            if (downloadQueue.pendingCount() > 0) status = "Sem internet. Os downloads continuam quando a conexão voltar.";
            emit();
            return;
        }
        if (wifiOnly && !onWifi()) {
            if (downloadQueue.pendingCount() > 0) status = "Aguardando Wi-Fi para baixar (" + downloadQueue.pendingCount() + " na fila)";
            emit();
            refreshNotification();
            return;
        }
        DownloadQueue.Job job = downloadQueue.nextPending(wifiOnly, onWifi() || !wifiOnly);
        if (job == null) return;
        startJob(job);
    }

    private void startJob(DownloadQueue.Job job) {
        downloading = true;
        activeDownloadId = job.id;
        activeDownloadTitle = job.title;
        downloadQueue.markActive(job.id);
        cancelled.set(false);
        progress = -1;
        status = "Obtendo áudio do YouTube…";
        store.status("Download interrompido. Use Baixar áudio para tentar novamente.");
        refreshNotification();
        emit();
        downloadLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Onda:Download");
        downloadLock.acquire(30 * 60 * 1000L);
        final boolean preferHigh = !dataSaver;
        worker.execute(() -> {
            synchronized (OfflineService.class) {
            File file = null;
            String message;
            boolean success = false;
            boolean wasCancelled = false;
            try {
                store.recoverInterrupted();
                YoutubeAudio.Source source = YoutubeAudio.resolve(job.url, preferHigh);
                if (cancelled.get()) throw new java.io.IOException("Download cancelado.");
                file = store.file(job.id, source.extension);
                long bytes = AudioDownload.transfer(source.url, file, cancelled, (received, total) -> main.post(() -> {
                    if (destroyed) return;
                    progress = total > 0 ? (int) (received * 100 / total) : -1;
                    downloadQueue.markProgress(job.id, progress);
                    status = "Baixando " + source.title + " · " + (received / 1024) + " KB";
                    emit();
                    refreshNotification();
                }));
                long duration;
                MediaMetadataRetriever metadata = new MediaMetadataRetriever();
                try {
                    metadata.setDataSource(file.getAbsolutePath());
                    String value = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    duration = value == null ? 0 : Long.parseLong(value);
                    if (duration <= 0) throw new java.io.IOException("O Android não reconheceu o áudio baixado.");
                } finally { metadata.release(); }
                if (cancelled.get()) throw new java.io.IOException("Download cancelado.");
                LibraryStore.Track meta = track(job.url);
                store.save(new OfflineStore.Song(job.id, source.title, job.url, source.extension,
                        meta.uploader.isEmpty() ? source.uploader : meta.uploader,
                        meta.thumbnail.isEmpty() ? source.thumbnail : meta.thumbnail, bytes, duration));
                success = true;
                message = "Download concluído: " + source.title;
            } catch (Exception e) {
                wasCancelled = cancelled.get();
                message = wasCancelled ? "Download cancelado." : "Não foi possível baixar "
                        + job.title + ". " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                if (message.length() > 350) message = message.substring(0, 350);
            } finally {
                if (!success && file != null) file.delete();
                if (downloadLock != null && downloadLock.isHeld()) downloadLock.release();
            }
            final String finalMessage = message;
            final boolean ok = success;
            final boolean cancelledNow = wasCancelled;
            store.status(finalMessage);
            main.post(() -> {
                if (destroyed) return;
                if (ok) { downloadQueue.markDone(job.id); }
                else if (cancelledNow) { downloadQueue.remove(job.id); }
                else { downloadQueue.markFailed(job.id, finalMessage); }
                persistDownloadQueue();
                downloading = false;
                activeDownloadId = null;
                progress = -1;
                status = finalMessage;
                revision++;
                if (!ok && !cancelledNow) emit();
                finishIdle();
                main.post(() -> pumpDownloads());
            });
            }
        });
    }

    void cancelDownload() {
        if (activeDownloadId != null) { cancelDownload(activeDownloadId); return; }
        if (!downloading) return;
        cancelled.set(true);
        status = "Cancelando download…";
        emit();
    }

    void cancelDownload(String id) {
        DownloadQueue.Job active = downloadQueue.active();
        if (active != null && active.id.equals(id)) {
            cancelled.set(true);
            status = "Cancelando download…";
            emit();
            return;
        }
        if (downloadQueue.remove(id)) { persistDownloadQueue(); status = "Download removido da fila."; finishIdle(); }
    }

    void retryDownload(String id) {
        if (downloadQueue.retry(id)) { persistDownloadQueue(); status = "Download na fila novamente."; emit(); pumpDownloads(); }
    }

    void clearFinishedDownloads() {
        downloadQueue.pruneFinished();
        persistDownloadQueue();
        emit();
    }

    private void play(String id) {
        OfflineStore.Song song = store.find(id);
        if (song == null) { status = "Arquivo offline não encontrado."; finishIdle(); return; }
        playTrack(song.url, song.title);
    }

    private void playLocal(String uri, String title, String artist, String album, String cover, long duration) {
        if (uri == null || uri.trim().isEmpty()) { status = "Arquivo local inválido."; finishIdle(); return; }
        int resumeAt = uri.equals(localUri) ? position() : 0;
        releasePlayer();
        media3Active = true;
        current = null;
        localUri = uri;
        localTitle = title == null || title.isEmpty() ? "Música local" : title;
        localArtist = artist == null ? "" : artist;
        localCover = cover == null ? "" : cover;
        localDuration = duration;
        localAlbum=album==null?"":album;
        localOrder.select(uri); localOrder.repeat(queue.repeat());
        savedPosition = resumeAt;
        loading = true; wantsPlay = true; online = false;
        playerStatus = "Preparando música local…";
        session.setActive(true);
        refreshNotification();
        updateSession();
        emit();
        beginPreparationDeadline(generation);
        preparePlayer(uri, false, generation);
    }

    private void restoreLocalQueue(String json) {
        localTracks.clear();List<String> urls=new ArrayList<>();
        try {
            org.json.JSONArray items=new org.json.JSONArray(json);
            for(int i=0;i<items.length();i++) {
                org.json.JSONObject item=items.getJSONObject(i);String uri=item.getString("uri");
                if(!uri.startsWith("content://media/"))continue;
                localTracks.add(new LocalMediaStore.Track(uri,item.optString("title"),item.optString("artist"),item.optString("album"),
                        item.optString("cover"),item.optLong("duration"),item.optLong("size")));urls.add(uri);
            }
        } catch(Exception ignored) { }
        localOrder.sync(urls);localOrder.shuffle(false);queue.shuffle(false);
    }
    private boolean playLocalQueued(String uri) {
        for(LocalMediaStore.Track t:localTracks) if(t.uri.equals(uri)) {playLocal(t.uri,t.title,t.artist,t.album,t.cover,t.duration);return true;}
        return false;
    }

    private void playTrack(String url, String title) {
        playTrack(url, title, "", "", -1);
    }

    private void playTrack(String url, String title, String uploader, String thumbnail, long durationSec) {
        String canonical = UrlPolicy.canonicalVideo(url == null ? "" : url);
        if (canonical == null) { status = "Música inválida."; finishIdle(); return; }
        localUri = null; localTitle = ""; localArtist = ""; localCover = ""; localDuration = 0;
        LibraryStore.Track existing = null;
        for (LibraryStore.Track candidate : library.tracks(LibraryStore.QUEUE)) {
            if (candidate.url.equals(canonical)) { existing = candidate; break; }
        }
        String name = title == null || title.trim().isEmpty()
                ? (existing == null ? "Áudio do YouTube" : existing.title) : title.trim();
        String up = uploader == null ? "" : uploader.trim();
        if (up.isEmpty() && existing != null) up = existing.uploader;
        String thumb = thumbnail == null ? "" : thumbnail;
        if (thumb.isEmpty() && existing != null) thumb = existing.thumbnail;
        long dur = durationSec < 0 && existing != null ? existing.durationSec : durationSec;
        LibraryStore.Track track = new LibraryStore.Track(name, canonical, up, thumb, dur);
        library.appendQueue(track);
        syncQueue();
        int resumeAt = canonical.equals(currentUrl()) ? position() : 0;
        current = track;
        select(canonical, resumeAt);
    }

    private void select(String url, int at) {
        LibraryStore.Track selected = track(url);
        releasePlayer();
        media3Active = true;
        current = selected; queue.select(url); savedPosition = Math.max(0, at);
        library.consumeManual(url);
        localUri = null; localTitle = ""; localArtist = ""; localCover = ""; localDuration = 0;
        loading = true; wantsPlay = true;
        session.setActive(true);
        playerStatus = "Preparando música…";
        refreshNotification();
        updateSession(); persist(); emit();
        int request = generation;
        beginPreparationDeadline(request);
        OfflineStore.Song local = store.find(OfflineStore.id(url));
        ConnectivityManager connectivity = getSystemService(ConnectivityManager.class);
        NetworkCapabilities network = connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        PlaybackPolicy.Source source = PlaybackPolicy.source(local != null,
                network != null && network.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
        if (source == PlaybackPolicy.Source.LOCAL) {
            online = false;
            preparePlayer(store.file(local.id, local.extension).getAbsolutePath(), false, request);
        } else if (source == PlaybackPolicy.Source.UNAVAILABLE) {
            failPlayback("Sem internet e esta música ainda não foi baixada. Escolha uma faixa com o selo Baixada.");
        } else {
            online = true;
            playerStatus = "Conectando ao áudio do YouTube…";
            refreshNotification(); emit();
            pendingResolve = resolver.submit(() -> {
                try {
                    YoutubeAudio.Source remote = YoutubeAudio.resolve(url, !dataSaver);
                    main.post(() -> {
                        if (destroyed || request != generation) return;
                        LibraryStore.Track enriched = track(url);
                        current = new LibraryStore.Track(
                                remote.title == null || remote.title.isEmpty() ? enriched.title : remote.title,
                                url,
                                enriched.uploader.isEmpty() ? remote.uploader : enriched.uploader,
                                enriched.thumbnail.isEmpty() ? remote.thumbnail : enriched.thumbnail,
                                enriched.durationSec);
                        preparePlayer(remote.url, true, request);
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        if (!destroyed && request == generation) failPlayback("Não foi possível carregar o áudio online. Verifique a internet ou tente baixar a faixa. " + shortError(e));
                    });
                }
            });
        }
    }

    private void beginPreparationDeadline(int request) {
        releasePreparation();
        preparationLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Onda:Prepare");
        preparationLock.acquire(90000);
        main.postDelayed(() -> {
            if (!destroyed && generation == request && loading)
                failPlayback("Tempo de carregamento esgotado. Toque em Ouvir para tentar novamente.");
        }, 90000);
    }

    private void preparePlayer(String source, boolean remote, int request) {
        if (destroyed || request != generation) return;
        if (media3Active) { prepareMedia3(source, remote, request); return; }
        loading = true;
        player = new MediaPlayer();
        MediaPlayer active = player;
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        if (remote) acquireWifi();
        player.setOnPreparedListener(p -> {
            if (p != player || request != generation) return;
            ready = true; loading = false; buffering = false;
            releasePreparation();
            savedPosition = PlaybackPolicy.resumePosition(savedPosition, p.getDuration());
            Runnable prepared = () -> {
                if (request != generation || p != player) return;
                if (wantsPlay) resume(); else pause();
            };
            if (savedPosition > 0) {
                p.setOnSeekCompleteListener(ignored -> { p.setOnSeekCompleteListener(null); prepared.run(); });
                p.seekTo(savedPosition);
            } else prepared.run();
        });
        player.setOnCompletionListener(p -> {
            if (p != player || request != generation) return;
            savedPosition = 0;
            if (sleepDeadline != 0 && SystemClock.elapsedRealtime() >= sleepDeadline) {
                sleepDeadline = 0; pause(); return;
            }
            next(true);
        });
        player.setOnInfoListener((p, what, extra) -> {
            if (p != player) return false;
            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START || what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                buffering = what == MediaPlayer.MEDIA_INFO_BUFFERING_START;
                updateSession(); emit();
            }
            return false;
        });
        player.setOnErrorListener((p, what, extra) -> {
            if (p == player && request == generation) failPlayback(remote
                    ? "A reprodução online falhou. Toque em Ouvir para reconectar."
                    : "O arquivo baixado não pôde ser reproduzido. Exclua e baixe novamente.");
            return true;
        });
        try {
            if (remote) active.setDataSource(this, Uri.parse(source), Collections.singletonMap("User-Agent", ExtractorHttp.USER_AGENT));
            else if (source.startsWith("content://")) active.setDataSource(this, Uri.parse(source));
            else active.setDataSource(source);
            active.prepareAsync();
        } catch (Exception e) { failPlayback("Falha ao preparar a música. " + shortError(e)); }
    }

    private void prepareMedia3(String source, boolean remote, int request) {
        try {
            media3 = media3Factory.create(this);
            final Media3AudioEngine active = media3;
            if (remote) acquireWifi();
            active.listener(new Media3AudioEngine.Listener() {
                @Override public void prepared(long duration) {
                    if (destroyed || request != generation || active != media3) return;
                    ready = true; loading = false; buffering = false; releasePreparation();
                    savedPosition = PlaybackPolicy.resumePosition(savedPosition, boundedMs(duration));
                    if (savedPosition > 0) active.seek(savedPosition);
                    if (wantsPlay) resume(); else pause();
                }
                @Override public void completed() {
                    if (!destroyed && request == generation && active == media3) {
                        savedPosition = 0;
                        if (sleepDeadline != 0 && SystemClock.elapsedRealtime() >= sleepDeadline) {
                            sleepDeadline = 0; pause(); return;
                        }
                        next(true);
                    }
                }
                @Override public void buffering(boolean value) {
                    if (destroyed || request != generation || active != media3) return;
                    buffering = value; updateSession(); emit();
                }
                @Override public void changed() {
                    if (destroyed || request != generation || active != media3) return;
                    updateSession(); refreshNotification(); emit();
                }
                @Override public void failed(String message) {
                    if (destroyed || request != generation || active != media3) return;
                    savedPosition = Math.max(savedPosition, boundedMs(active.position()));
                    // Release after ExoPlayer finishes dispatching this event; stale callbacks are guarded.
                    main.post(() -> {
                        if (!destroyed && request == generation && active == media3)
                            fallbackToLegacy(source, remote, request, message);
                    });
                }
            });
            active.setSource(source);
        } catch (RuntimeException error) {
            fallbackToLegacy(source, remote, request, error.getClass().getSimpleName());
        }
    }

    private void fallbackToLegacy(String source, boolean remote, int request, String reason) {
        if (destroyed || request != generation) return;
        Media3AudioEngine old = media3;
        media3 = null;
        if (old != null) old.release();
        media3Active = false;
        ready = false; loading = true; buffering = false;
        playerStatus = "Tentando player de compatibilidade…";
        android.util.Log.w("OndaPlayback", "Media3 fallback: " + reason);
        beginPreparationDeadline(request);
        refreshNotification(); emit();
        preparePlayer(source, remote, request);
    }

    private String shortError(Exception e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return message.substring(0, Math.min(180, message.length()));
    }

    private void failPlayback(String message) {
        // MediaPlayer is already in Error; never ask it for its position here.
        releasePlayer();
        playerStatus = message; wantsPlay = false;
        persist(); updateSession(); finishIdle();
    }

    void next(boolean naturalEnd) {
        if (localUri != null) {
            String next=localOrder.next(naturalEnd);
            if(next!=null&&playLocalQueued(next))return;
            if (naturalEnd) {
                if (queue.repeat() == PlaybackQueue.Repeat.ONE) { seek(0); resume(); }
                else { releasePlayer(); savedPosition = 0; playerStatus = "Música local concluída"; updateSession(); finishIdle(); }
            }
            return;
        }
        syncQueue();
        if (!naturalEnd && current != null && ready && position() < 30000) library.recordSkip(current.url);
        String next = queue.next(naturalEnd);
        if (next != null) { select(next, 0); return; }
        if (autoplay() && current != null) { startRadio(); return; }
        if (naturalEnd) {
            releasePlayer(); savedPosition = 0; playerStatus = "Fim da fila · toque em Ouvir para repetir a faixa";
            persist(); updateSession(); finishIdle();
        } else if (!ready && !loading) finishIdle();
    }

    private void startRadio() {
        if (radioLoading || current == null) return;
        LibraryStore.Track seed = current;
        releasePlayer();
        radioLoading = true; loading = true; wantsPlay = true;
        playerStatus = "Buscando sugestões por artista e relações do YouTube…";
        session.setActive(true); refreshNotification(); emit();
        int request = generation;
        beginPreparationDeadline(request);
        List<LibraryStore.Track> catalog = library.catalog();
        List<LibraryStore.Track> recent = library.tracks(LibraryStore.HISTORY);
        recent = new ArrayList<>(recent.subList(0, Math.min(30, recent.size())));
        Set<String> likes = new HashSet<>(), skips = new HashSet<>();
        for (LibraryStore.Track t : library.tracks(LibraryStore.FAVORITES)) likes.add(t.url);
        for (LibraryStore.Track t : catalog) if (library.recentlySkipped(t.url)) skips.add(t.url);
        final List<LibraryStore.Track> history = recent;
        pendingResolve = resolver.submit(() -> {
            List<LibraryStore.Track> candidates = new ArrayList<>(catalog);
            Set<String> related = new HashSet<>();
            try {
                for (LibraryStore.Track t : radioProvider.related(seed)) {
                    candidates.add(t); related.add(t.url);
                    if (library.recentlySkipped(t.url)) skips.add(t.url);
                }
            } catch (Exception ignored) { /* Local metadata remains usable offline. */ }
            List<LibraryStore.Track> recommendations = RadioPlanner.choose(seed, candidates, related, history, likes, skips, 5);
            main.post(() -> {
                if (destroyed || request != generation || !radioLoading || !autoplay()) return;
                radioLoading = false; releasePreparation();
                for (LibraryStore.Track t : recommendations) library.appendRadio(t);
                syncQueue();
                String next = queue.next(false); // A manually queued track always wins, including during this request.
                if (next != null && wantsPlay) select(next, 0);
                else {
                    loading = false;
                    playerStatus = recommendations.isEmpty() ? "Sem sugestões com relação disponível. Escolha outra música." : "Sugestões prontas · reprodução pausada";
                    persist(); updateSession(); finishIdle();
                }
            });
        });
    }

    void previous() {
        if (ready && position() > 3000) { seek(0); return; }
        if (localUri != null) {
            String previous=localOrder.previous();if(previous!=null&&playLocalQueued(previous))return;
            savedPosition = 0;
            if (ready) seek(0); else resume();
            return;
        }
        syncQueue();
        String previous = queue.previous();
        if (previous != null) select(previous, 0);
        else if (current != null) select(current.url, 0);
        else finishIdle();
    }

    void resume() {
        wantsPlay = true;
        if (loading) { playerStatus = "Carregando música…"; updateSession(); emit(); return; }
        if (!ready || (media3Active ? media3 == null : player == null)) {
            if (localUri != null) playLocal(localUri, localTitle, localArtist, localAlbum, localCover, localDuration);
            else if (current != null) select(current.url, savedPosition);
            else { status = "Escolha uma música ou uma playlist para começar."; finishIdle(); }
            return;
        }
        refreshNotification();
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            wantsPlay = false; playerStatus = "Áudio indisponível durante uma chamada ou outro aplicativo";
            releaseWifi(); updateSession(); emit(); return;
        }
        if (online) acquireWifi();
        if (media3Active && media3 != null) media3.play(); else player.start();
        playerStatus = online ? "Reproduzindo online" : localUri != null ? "Reproduzindo · arquivo do celular" : "Reproduzindo · arquivo baixado";
        if (current != null) library.add(LibraryStore.HISTORY, current);
        updateSession();
        refreshNotification();
        emit();
    }

    void pause() {
        wantsPlay = false;
        if (media3Active && media3 != null) media3.pause();
        else if (ready && player != null && player.isPlaying()) player.pause();
        if (ready) savedPosition = position();
        audio.abandonAudioFocusRequest(focus);
        releaseWifi();
        playerStatus = "Pausado";
        persist(); updateSession(); refreshNotification(); emit();
    }

    void seek(int milliseconds) {
        savedPosition = ready ? Math.max(0, Math.min(duration(), milliseconds)) : Math.max(0, milliseconds);
        if (ready && media3Active && media3 != null) media3.seek(savedPosition);
        else if (ready && player != null) player.seekTo(savedPosition);
        PlaybackSnapshot snapshot = PlaybackSnapshot.decode(preferences.getString("snapshot", ""));
        snapshot.position = savedPosition;
        preferences.edit().putString("snapshot", snapshot.encode()).apply();
        updateSession(); emit();
    }

    void stopPlayer() {
        if (ready) savedPosition = position();
        releasePlayer(); sleepDeadline = 0; playerStatus = "Parado · toque em Ouvir para continuar";
        persist(); updateSession(); finishIdle();
    }

    private void releasePlayer() {
        generation++; ready = false; loading = false; wantsPlay = false; buffering = false; radioLoading = false;
        if (pendingResolve != null) { pendingResolve.cancel(true); pendingResolve = null; }
        releasePreparation(); releaseWifi();
        if (media3 != null) { media3.release(); media3 = null; }
        if (player != null) { player.release(); player = null; }
        if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
        if (session != null) session.setActive(false);
    }

    private void releasePreparation() { if (preparationLock != null && preparationLock.isHeld()) preparationLock.release(); }
    private void acquireWifi() {
        // WIFI_MODE_FULL is enough for audio streaming; HIGH_PERF disables power save and drains battery.
        if (wifiLock == null) wifiLock = ((WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE))
                .createWifiLock(WifiManager.WIFI_MODE_FULL, "Onda:Streaming");
        if (!wifiLock.isHeld()) wifiLock.acquire();
    }
    private void releaseWifi() { if (wifiLock != null && wifiLock.isHeld()) wifiLock.release(); }

    private void updateSession() {
        if (session == null) return;
        List<MediaSession.QueueItem> entries = new ArrayList<>();
        List<String> order = localUri != null ? localOrder.order() : queue.order();
        List<LibraryStore.Track> tracks = library.tracks(LibraryStore.QUEUE);
        for (int i = 0; i < order.size(); i++) {
            String url = order.get(i), title = "Música";
            for (LibraryStore.Track candidate : tracks) if (candidate.url.equals(url)) { title = candidate.title; break; }
            if(localUri!=null) for(LocalMediaStore.Track candidate:localTracks) if(candidate.uri.equals(url)){title=candidate.title;break;}
            entries.add(new MediaSession.QueueItem(new MediaDescription.Builder().setMediaId(url).setTitle(title).build(), i));
        }
        session.setQueue(entries); session.setQueueTitle("Fila do Onda");
        MediaMetadata.Builder metadata = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title())
                .putLong(MediaMetadata.METADATA_KEY_DURATION, duration());
        if (!uploader().isEmpty()) {
            metadata.putString(MediaMetadata.METADATA_KEY_ARTIST, uploader());
            metadata.putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, uploader());
        }
        session.setMetadata(metadata.build());
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM;
        if (ready) actions |= PlaybackState.ACTION_SEEK_TO;
        if ((localUri == null && queue.next(false) != null)||(localUri!=null&&localOrder.next(false)!=null)) actions |= PlaybackState.ACTION_SKIP_TO_NEXT;
        if (hasTrack()) actions |= PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        int state = !hasTrack() ? PlaybackState.STATE_NONE : (loading || buffering) && wantsPlay
                ? PlaybackState.STATE_BUFFERING : playing() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        session.setPlaybackState(new PlaybackState.Builder().setActions(actions)
                .setActiveQueueItemId(order.indexOf(localUri==null?currentUrl():localUri))
                .setState(state, position(), playing() && !buffering ? 1 : 0, SystemClock.elapsedRealtime()).build());
    }

    private PendingIntent action(String action) {
        return PendingIntent.getService(this, action.hashCode(), new Intent(this, OfflineService.class).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void refreshNotification() {
        boolean media = hasActivePlayback();
        if (destroyed || (!downloading && !media)) return;
        Notification.Builder notification = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_onda)
                .setContentTitle(media ? title() : "Onda · download")
                .setContentText(media ? playerStatus() : status).setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, OfflineActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                .setOngoing(downloading || playing() || (loading && wantsPlay));
        if (downloading && !media) {
            notification.setProgress(100, Math.max(progress, 0), progress < 0);
            notification.addAction(new Notification.Action.Builder(null, "Cancelar download", action(CANCEL)).build());
            int pending = downloadQueue.pendingCount();
            if (pending > 1) notification.setSubText((pending - 1) + " na fila");
        }
        if (media) {
            notification.addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_previous), "Anterior", action(PREVIOUS)).build());
            boolean pause = playing() || (loading && wantsPlay);
            notification.addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this,
                    pause ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play), pause ? "Pausar" : "Ouvir", action(TOGGLE)).build());
            notification.addAction(new Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_next), "Próxima", action(NEXT)).build());
            notification.addAction(new Notification.Action.Builder(null, "Parar", action(STOP)).build());
            notification.setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2));
            if (downloading) {
                int pending = downloadQueue.pendingCount();
                notification.setSubText(pending > 1 ? "Download em andamento · " + (pending - 1) + " na fila"
                        : "Download em andamento");
            }
        }
        if (Build.VERSION.SDK_INT >= 29) {
            int type = (downloading ? ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC : 0)
                    | (media ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0);
            startForeground(NOTIFICATION, notification.build(), type);
        } else startForeground(NOTIFICATION, notification.build());
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (downloading && media) {
            manager.notify(NOTIFICATION + 1, new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_onda)
                    .setContentTitle("Onda · download").setContentText(status).setOnlyAlertOnce(true)
                    .setProgress(100, Math.max(progress, 0), progress < 0).setOngoing(true)
                    .addAction(new Notification.Action.Builder(null, "Cancelar", action(CANCEL)).build()).build());
        } else manager.cancel(NOTIFICATION + 1);
    }

    private void finishIdle() {
        emit();
        if (!downloading && !hasActivePlayback()) {
            getSystemService(NotificationManager.class).cancel(NOTIFICATION + 1);
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
        }
        else refreshNotification();
    }
    private void emit() { for (Runnable listener : new ArrayList<>(listeners)) listener.run(); }
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            boolean active = playing();
            boolean busy = downloading || active || (loading && wantsPlay) || sleepDeadline != 0;
            if (sleepDeadline != 0 && SystemClock.elapsedRealtime() >= sleepDeadline) {
                sleepDeadline = 0; pause(); playerStatus = "Pausado pelo temporizador"; refreshNotification();
            }
            if (ready) savedPosition = position();
            // Persist only while state actually changes: playing/downloading. Paused position is saved on pause().
            if (current != null && (++ticks % 5 == 0) && (active || downloading)) persist();
            else ticks++;
            if (hasTrack() || downloading) emit();
            // Adaptive cadence: 1s while playing, 2s while busy, 5s idle. Paused-with-track stays alive but quiet.
            main.postDelayed(this, active ? 1000 : busy ? 2000 : 5000);
        }
    };
    @Override public void onTimeout(int startId, int fgsType) { cancelDownload(); stopPlayer(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }
    @Override public void onDestroy() {
        persist(); persistDownloadQueue(); destroyed = true; cancelled.set(true); listeners.clear();
        libraryPreferences.unregisterOnSharedPreferenceChangeListener(queueListener);
        try {
            if (networkCallback != null) getSystemService(ConnectivityManager.class)
                    .unregisterNetworkCallback(networkCallback);
        } catch (Exception ignored) { }
        worker.shutdownNow(); resolver.shutdownNow(); main.removeCallbacksAndMessages(null);
        releasePlayer(); session.release(); unregisterReceiver(noisy);
        getSystemService(NotificationManager.class).cancel(NOTIFICATION + 1);
        if (downloadLock != null && downloadLock.isHeld()) downloadLock.release();
        super.onDestroy();
    }
}
