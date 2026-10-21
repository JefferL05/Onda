package app.onda.mobile;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.List;
import java.util.Locale;

/** Professional player: cover, transport, modes, queue and download management. */
public final class OfflineActivity extends Activity {
    private OfflineStore store;
    private OfflineService service;
    private boolean bound, seeking;
    private int shownRevision = -1;
    private String queueSig = "", downloadSig = "", panelSig = "";
    private TextView downloadStatus, trackTitle, trackArtist, trackBadges, playbackStatus, timing;
    private ProgressBar progress;
    private ProgressBar miniProgress;
    private SeekBar seek;
    private Button toggle, cancel;
    private Button shuffle, repeat, sleep, radio, favorite;
    private Button lyricsButton, tabDetails, tabLyrics, tabQueue;
    private LinearLayout panel;
    private ImageView cover;
    private LinearLayout queuePreview;
    private LinearLayout downloadList;
    private LinearLayout list;
    private LinearLayout localList;
    private final Runnable refresh = this::update;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((OfflineService.LocalBinder) binder).service();
            service.addListener(refresh);
            queueSig = ""; downloadSig = ""; shownRevision = -1;
            update();
        }
        @Override public void onServiceDisconnected(ComponentName name) { service = null; }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new OfflineStore(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 12));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(Ui.dp(this, 20) + bars.left, Ui.dp(this, 12) + bars.top,
                        Ui.dp(this, 20) + bars.right, Ui.dp(this, 12) + bars.bottom);
            } else v.setPadding(Ui.dp(this, 20), Ui.dp(this, 12) + insets.getSystemWindowInsetTop(),
                    Ui.dp(this, 20), Ui.dp(this, 12) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        ScrollView fullPage = new ScrollView(this);
        fullPage.setFillViewport(true);
        fullPage.addView(root);
        setContentView(fullPage);
        Button back = Ui.ghost(this, "‹");
        back.setContentDescription("Voltar");
        back.setPadding(dp(10), 0, dp(10), 0);
        root.addView(back);
        root.addView(Ui.eyebrow(this, "Tocando agora"));

        LinearLayout hero = column();
        hero.setPadding(dp(18), dp(16), dp(18), dp(18));
        hero.setBackground(Ui.card(24));
        cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(Ui.card(24));
        cover.setClipToOutline(true);
        cover.setContentDescription("Capa da música atual");
        int coverSize = Ui.dp(this, 240);
        LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(coverSize, coverSize, 0);
        coverParams.gravity = Gravity.CENTER_HORIZONTAL;
        hero.addView(cover, coverParams);

        trackBadges = new TextView(this);
        trackBadges.setTextSize(11);
        trackBadges.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        trackBadges.setTextColor(Ui.GREEN);
        trackBadges.setGravity(Gravity.CENTER_HORIZONTAL);
        trackBadges.setPadding(0, dp(12), 0, dp(8));
        hero.addView(trackBadges);

        trackTitle = new TextView(this);
        trackTitle.setText("Seu player");
        trackTitle.setTextSize(24);
        trackTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        trackTitle.setTextColor(Color.WHITE);
        trackTitle.setGravity(Gravity.CENTER_HORIZONTAL);
        trackTitle.setMaxLines(2);
        trackTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        hero.addView(trackTitle);

        trackArtist = new TextView(this);
        trackArtist.setTextSize(14);
        trackArtist.setTextColor(Ui.MUTED);
        trackArtist.setGravity(Gravity.CENTER_HORIZONTAL);
        trackArtist.setMaxLines(1);
        trackArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        hero.addView(trackArtist);

        LinearLayout heroActions = row();
        heroActions.setGravity(Gravity.CENTER_HORIZONTAL);
        heroActions.setPadding(0, dp(12), 0, 0);
        favorite = Ui.ghost(this, "♥ Favoritar");
        favorite.setOnClickListener(v -> {
            if (service != null && service.hasTrack()) {
                LibraryStore.Track track = service.currentTrack();
                if (track == null) {
                    Toast.makeText(this, "Esta música está na biblioteca do celular.", Toast.LENGTH_SHORT).show();
                    return;
                }
                new LibraryStore(this).add(LibraryStore.FAVORITES, track);
                Toast.makeText(this, "Música salva nos favoritos", Toast.LENGTH_SHORT).show();
            }
        });
        heroActions.addView(favorite);
        hero.addView(heroActions);

        seek = new SeekBar(this);
        seek.setContentDescription("Posição da música");
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) { }
            @Override public void onStartTrackingTouch(SeekBar bar) { seeking = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) { if (service != null) service.seek(bar.getProgress()); seeking = false; }
        });
        hero.addView(seek, new LinearLayout.LayoutParams(-1, dp(32)));
        timing = new TextView(this);
        timing.setText("0:00 / 0:00");
        timing.setTextSize(11);
        timing.setTextColor(Ui.MUTED);
        timing.setGravity(Gravity.CENTER);
        timing.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        hero.addView(timing);
        playbackStatus = new TextView(this);
        playbackStatus.setTextSize(11);
        playbackStatus.setTextColor(Ui.MUTED);
        playbackStatus.setGravity(Gravity.CENTER);
        playbackStatus.setPadding(0, 0, 0, dp(8));
        hero.addView(playbackStatus);

        LinearLayout controls = Ui.row(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(0, dp(8), 0, dp(4));
        shuffle = Ui.ghost(this, "⇄");
        shuffle.setTextSize(18);
        shuffle.setContentDescription("Aleatório");
        shuffle.setOnClickListener(v -> { if (service != null) service.toggleShuffle(); });
        Button previous = Ui.ghost(this, "⏮");
        previous.setTextSize(20);
        previous.setContentDescription("Música anterior");
        previous.setOnClickListener(v -> command(OfflineService.PREVIOUS));
        toggle = Ui.circle(this, "▶");
        toggle.setTextSize(26);
        toggle.setContentDescription("Ouvir ou pausar");
        toggle.setOnClickListener(v -> command(OfflineService.TOGGLE));
        Button next = Ui.ghost(this, "⏭");
        next.setTextSize(20);
        next.setContentDescription("Próxima música");
        next.setOnClickListener(v -> command(OfflineService.NEXT));
        repeat = Ui.ghost(this, "↻");
        repeat.setTextSize(18);
        repeat.setContentDescription("Repetição");
        repeat.setOnClickListener(v -> { if (service != null) service.cycleRepeat(); });
        controls.addView(shuffle, new LinearLayout.LayoutParams(0, dp(52), 1));
        controls.addView(previous, new LinearLayout.LayoutParams(0, dp(52), 1));
        controls.addView(toggle, new LinearLayout.LayoutParams(dp(72), dp(72)));
        controls.addView(next, new LinearLayout.LayoutParams(0, dp(52), 1));
        controls.addView(repeat, new LinearLayout.LayoutParams(0, dp(52), 1));
        hero.addView(controls);

        radio = Ui.ghost(this, "Rádio automática: desligada");
        radio.setOnClickListener(v -> {
            if (service == null) return;
            boolean enabled = !service.autoplay();
            service.autoplay(enabled);
            Toast.makeText(this, enabled ? "Ao acabar a fila, tocaremos sugestões parecidas."
                    : "Fila terminará como está.", Toast.LENGTH_SHORT).show();
        });
        hero.addView(radio);

        // Tabs: Detalhes / Letras / Fila
        LinearLayout tabs = row();
        tabs.setPadding(0, dp(8), 0, 0);
        tabDetails = Ui.chip(this, "Detalhes", true);
        tabLyrics = Ui.chip(this, "Letras", false);
        tabQueue = Ui.chip(this, "Fila", false);
        tabDetails.setOnClickListener(v -> showPanel(0));
        tabLyrics.setOnClickListener(v -> showPanel(1));
        tabQueue.setOnClickListener(v -> showPanel(2));
        tabs.addView(tabDetails, new LinearLayout.LayoutParams(0, dp(40), 1));
        tabs.addView(tabLyrics, new LinearLayout.LayoutParams(0, dp(40), 1));
        tabs.addView(tabQueue, new LinearLayout.LayoutParams(0, dp(40), 1));
        hero.addView(tabs);
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        hero.addView(panel);
        root.addView(hero);
        showPanel(0);

        root.addView(Ui.section(this, "A seguir"));
        queuePreview = new LinearLayout(this);
        queuePreview.setOrientation(LinearLayout.VERTICAL);
        root.addView(queuePreview);
        Button fullQueue = Ui.ghost(this, "Ver fila completa");
        fullQueue.setOnClickListener(v -> showQueue());
        root.addView(fullQueue);

        root.addView(Ui.section(this, "Downloads"));
        downloadStatus = new TextView(this);
        downloadStatus.setTextSize(13);
        downloadStatus.setTextColor(Ui.MUTED);
        root.addView(downloadStatus);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, Ui.dp(this, 6)));
        cancel = Ui.ghost(this, "Cancelar download atual");
        cancel.setOnClickListener(v -> { if (service != null) service.cancelDownload(); });
        cancel.setVisibility(View.GONE);
        root.addView(cancel);
        downloadList = new LinearLayout(this);
        downloadList.setOrientation(LinearLayout.VERTICAL);
        root.addView(downloadList);
        LinearLayout downloadSettings = Ui.row(this);
        Button wifi = Ui.ghost(this, "Configurar downloads");
        wifi.setOnClickListener(v -> showDownloadSettings());
        Button retryAll = Ui.ghost(this, "Tentar falhas");
        retryAll.setOnClickListener(v -> {
            if (service == null) return;
            for (DownloadQueue.Job job : service.downloadJobs()) {
                if (job.state == DownloadQueue.State.FAILED) service.retryDownload(job.id);
            }
        });
        downloadSettings.addView(wifi, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        downloadSettings.addView(retryAll, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        root.addView(downloadSettings);

        root.addView(Ui.section(this, "Baixadas · disponíveis sem internet"));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        root.addView(Ui.section(this, "Músicas no celular"));
        localList = new LinearLayout(this);
        localList.setOrientation(LinearLayout.VERTICAL);
        root.addView(localList);
        Button scan = Ui.ghost(this, "Atualizar biblioteca do celular");
        scan.setOnClickListener(v -> scanLocal());
        root.addView(scan);
        renderLibrary();
        scanLocal();
    }

    @Override protected void onStart() { super.onStart(); bound = bindService(new Intent(this, OfflineService.class), connection, BIND_AUTO_CREATE); }
    @Override protected void onStop() {
        if (service != null) service.removeListener(refresh);
        if (bound) unbindService(connection);
        bound = false; service = null;
        super.onStop();
    }

    private void update() {
        if (service == null || isFinishing()) return;
        downloadStatus.setText(service.status());
        progress.setVisibility(service.downloading() ? View.VISIBLE : View.GONE);
        progress.setIndeterminate(service.progress() < 0);
        if (service.progress() >= 0) progress.setProgress(service.progress());
        cancel.setVisibility(service.downloading() ? View.VISIBLE : View.GONE);
        trackTitle.setText(service.title());
        trackArtist.setText(service.uploader().isEmpty() ? "Toque em Ouvir para começar" : service.uploader());
        OfflineStore.Song local = service.currentId() == null ? null : store.find(service.currentId());
        String badges = "";
        if (local != null) badges += "↓ Baixada  ";
        if (service.loading()) badges += "… Carregando  ";
        else if (service.playing()) badges += "▶ Tocando  ";
        else if (service.hasTrack()) badges += "⏸ Pausado  ";
        if (!service.hasTrack()) badges = "Nenhuma música selecionada";
        else if (service.isDeviceTrack()) badges += "· No celular";
        else if (local == null && service.hasTrack()) badges += "· Online";
        trackBadges.setText(badges.trim());
        playbackStatus.setText(service.playerStatus() + " · " + service.engineName());
        CoverCache.with(this).load(cover, service.cover());
        toggle.setText(service.playing() || (service.loading() && service.playRequested()) ? "⏸" : "▶");
        toggle.setEnabled(service.hasTrack());
        seek.setEnabled(service.duration() > 0);
        seek.setMax(Math.max(1, service.duration()));
        if (!seeking) seek.setProgress(service.position());
        timing.setText(Display.clock(service.position()) + "  ·  " + service.duration() + " ms");
        shuffle.setText(service.shuffled() ? "⇄" : "⇄");
        shuffle.setTextColor(service.shuffled() ? Ui.GREEN : Ui.MUTED);
        shuffle.setSelected(service.shuffled());
        repeat.setText(service.repeat() == PlaybackQueue.Repeat.ONE ? "↻1"
                : service.repeat() == PlaybackQueue.Repeat.ALL ? "↻" : "↻");
        repeat.setTextColor(service.repeat() == PlaybackQueue.Repeat.OFF ? Ui.MUTED : Ui.GREEN);
        repeat.setSelected(service.repeat() != PlaybackQueue.Repeat.OFF);
        if (sleep != null) sleep.setText(service.sleepRemaining() > 0
                ? "Pausar em " + Display.clock(service.sleepRemaining()) : "Temporizador");
        radio.setText(service.autoplay() ? "Rádio automática: ✓ ligada" : "Rádio automática: desligada");
        if (lyricsButton != null) {
            lyricsButton.setEnabled(service.currentUrl() != null);
            lyricsButton.setText(new LyricsStore(this).has(service.currentUrl()) ? "Letras salvas" : "Adicionar letras");
        }
        if (tabDetails != null) {
            int active = tabDetails.isSelected() ? 0 : tabLyrics.isSelected() ? 1 : 2;
            String panelState = (service.currentUrl() == null ? "" : service.currentUrl()) + "|" + service.originLabel()
                    + "|" + service.engineName() + "|" + service.queueOrder().toString()
                    + "|" + (new LyricsStore(this).has(service.currentUrl()) ? "1" : "0")
                    + "|" + (service.sleepRemaining() / 60000);
            if (!panelState.equals(panelSig)) { panelSig = panelState; showPanel(active); }
        }
        // Heavy list rebuilds run only when their content actually changed; texts/progress update every tick.
        String queue = service.queueOrder().toString() + "|" + service.currentUrl() + "|" + service.title();
        if (!queue.equals(queueSig)) { queueSig = queue; renderQueuePreview(); }
        StringBuilder downloads = new StringBuilder().append(service.downloading()).append('|');
        for (DownloadQueue.Job job : service.downloadJobs())
            downloads.append(job.id).append(job.state).append(service.progress() / 5).append(';');
        if (!downloads.toString().equals(downloadSig)) { downloadSig = downloads.toString(); renderDownloadQueue(); }
        if (shownRevision != service.revision()) { shownRevision = service.revision(); renderLibrary(); }
    }

    /** In-hero tab panel: 0 = Detalhes, 1 = Letras, 2 = Fila. */
    private void showPanel(int index) {
        tabDetails.setSelected(index == 0);
        tabLyrics.setSelected(index == 1);
        tabQueue.setSelected(index == 2);
        tabDetails.setTextColor(index == 0 ? Ui.BG : Ui.MUTED);
        tabLyrics.setTextColor(index == 1 ? Ui.BG : Ui.MUTED);
        tabQueue.setTextColor(index == 2 ? Ui.BG : Ui.MUTED);
        panel.removeAllViews();
        if (index == 0) {
            panel.addView(Ui.badge(this, service == null ? "Sem sessão" : service.engineName(), Ui.GREEN));
            if (service != null) {
                TextView info = new TextView(this);
                info.setText(service.originLabel());
                info.setTextSize(12);
                info.setTextColor(Ui.MUTED);
                panel.addView(info);
            }
            LinearLayout actions = Ui.row(this);
            sleep = Ui.ghost(this, "Temporizador");
            sleep.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Pausar após")
                    .setItems(new String[]{"Desativado", "15 minutos", "30 minutos", "60 minutos"}, (d, which) -> {
                        if (service != null) service.sleepAfter(new int[]{0, 15, 30, 60}[which]);
                    }).show());
            Button stop = Ui.ghost(this, "Parar");
            stop.setOnClickListener(v -> { if (service != null) service.stopPlayer(); });
            actions.addView(sleep, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
            actions.addView(stop, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
            panel.addView(actions);
        } else if (index == 1) {
            lyricsButton = Ui.ghost(this, "Adicionar letras");
            lyricsButton.setOnClickListener(v -> showLyrics());
            panel.addView(lyricsButton);
            if (service != null && service.currentUrl() != null) {
                String text = new LyricsStore(this).get(service.currentUrl());
                TextView lyrics = new TextView(this);
                lyrics.setText(text.isEmpty() ? "Sem letras salvas para esta faixa." : text);
                lyrics.setTextSize(14);
                lyrics.setTextColor(text.isEmpty() ? Ui.MUTED : Color.WHITE);
                lyrics.setLineSpacing(Ui.dp(this, 6), 1);
                lyrics.setPadding(0, Ui.dp(this, 8), 0, 0);
                panel.addView(lyrics);
            } else panel.addView(Ui.body(this, "Abra uma música para salvar letras."));
        } else {
            if (service != null) {
                List<String> order = service.queueOrder();
                if (order.isEmpty()) panel.addView(Ui.body(this, "Fila vazia."));
                List<LibraryStore.Track> base = new LibraryStore(this).tracks(LibraryStore.QUEUE);
                int shownCount = 0;
                for (String url : order) {
                    if (shownCount >= 5) break;
                    LibraryStore.Track found = null;
                    for (LibraryStore.Track item : base) if (item.url.equals(url)) { found = item; break; }
                    if (found == null) continue;
                    LinearLayout item = Ui.row(this);
                    item.setPadding(0, Ui.dp(this, 10), 0, 0);
                    ImageView image = Ui.cover(this, 36, 8);
                    CoverCache.with(this).load(image, found.thumbnail);
                    item.addView(image);
                    LinearLayout words = new LinearLayout(this);
                    words.setOrientation(LinearLayout.VERTICAL);
                    words.setPadding(Ui.dp(this, 10), 0, 0, 0);
                    TextView name = new TextView(this);
                    name.setText(found.title);
                    name.setTextSize(13);
                    name.setTextColor(Color.WHITE);
                    name.setMaxLines(1);
                    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    words.addView(name);
                    words.addView(Ui.badge(this, url.equals(service.currentUrl()) ? "Tocando" : "Na fila",
                            url.equals(service.currentUrl()) ? Ui.GREEN : Ui.VIOLET_LIGHT));
                    item.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
                    panel.addView(item);
                    shownCount++;
                }
            } else panel.addView(Ui.body(this, "Sem sessão de reprodução."));
        }
    }

    private void scanLocal() {
        if (!LocalMediaStore.allowed(this)) {
            if (Build.VERSION.SDK_INT >= 23) requestPermissions(new String[]{LocalMediaStore.permission()}, 42);
            localList.removeAllViews();
            TextView permission = new TextView(this);
            permission.setText("Permita acesso à música para listar os arquivos do celular.");
            permission.setTextColor(Ui.MUTED);
            localList.addView(permission);
            return;
        }
        localList.removeAllViews();
        TextView loading = new TextView(this);
        loading.setText("Lendo a biblioteca local…");
        loading.setTextColor(Ui.MUTED);
        localList.addView(loading);
        new Thread(() -> {
            List<LocalMediaStore.Track> tracks = LocalMediaStore.scan(this);
            runOnUiThread(() -> renderLocalTracks(tracks));
        }, "onda-local-scan").start();
    }

    private void renderLocalTracks(List<LocalMediaStore.Track> tracks) {
        if (isFinishing() || isDestroyed()) return;
        localList.removeAllViews();
        if (tracks.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Nenhuma música encontrada no armazenamento do celular.");
            empty.setTextColor(Ui.MUTED);
            localList.addView(empty);
            return;
        }
        TextView summary = new TextView(this);
        summary.setText(tracks.size() + " músicas locais");
        summary.setTextColor(Ui.MUTED);
        localList.addView(summary);
        for (LocalMediaStore.Track track : tracks) {
            LinearLayout card = Ui.cardContainer(this);
            LinearLayout row = Ui.row(this);
            ImageView cover = Ui.cover(this, 52, 8);
            CoverCache.with(this).load(cover, track.cover);
            cover.setContentDescription("Capa de " + track.title);
            row.addView(cover);
            LinearLayout text = new LinearLayout(this);
            text.setOrientation(LinearLayout.VERTICAL);
            text.setPadding(Ui.dp(this, 12), 0, 0, 0);
            TextView title = new TextView(this);
            title.setText(track.title);
            title.setTextColor(Color.WHITE);
            title.setTextSize(16);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            text.addView(title);
            TextView meta = new TextView(this);
            meta.setText(track.artist + " · " + track.album + " · " + Display.clock(track.duration));
            meta.setTextColor(Ui.MUTED);
            meta.setTextSize(12);
            meta.setMaxLines(1);
            meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
            text.addView(meta);
            row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
            card.addView(row);
            Button play = Ui.primary(this, "Ouvir no Onda");
            play.setOnClickListener(v -> startForegroundService(new Intent(this, OfflineService.class)
                    .setAction(OfflineService.PLAY_LOCAL).putExtra("uri", track.uri)
                    .putExtra("title", track.title).putExtra("artist", track.artist)
                    .putExtra("album", track.album).putExtra("cover", track.cover)
                    .putExtra("duration", track.duration)));
            card.addView(play);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = Ui.dp(this, 10);
            localList.addView(card, params);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 42 && results.length > 0
                && results[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) scanLocal();
    }

    private void showLyrics() {
        if (service == null || service.currentUrl() == null) return;
        final String selectedUrl = service.currentUrl();
        LyricsStore store = new LyricsStore(this);
        EditText input = new EditText(this);
        input.setHint("Cole letras que você possui ou está autorizado a usar");
        input.setText(store.get(selectedUrl));
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Ui.MUTED);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(10);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        new AlertDialog.Builder(this).setTitle("Letras")
                .setMessage("O Onda não busca nem copia letras automaticamente. Salve somente texto autorizado.")
                .setView(input).setNeutralButton("Limpar", (d, w) -> { store.put(selectedUrl, ""); update(); })
                .setNegativeButton("Cancelar", null).setPositiveButton("Salvar", (d, w) -> {
                    try { store.put(selectedUrl, input.getText().toString()); update(); }
                    catch (IllegalArgumentException e) { Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show(); }
                }).show();
    }

    private void renderQueuePreview() {
        queuePreview.removeAllViews();
        if (service == null) return;
        List<String> order = service.queueOrder();
        if (order.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Fila vazia. Adicione músicas pela busca ou biblioteca.");
            empty.setTextColor(Ui.MUTED);
            queuePreview.addView(empty);
            return;
        }
        LibraryStore library = new LibraryStore(this);
        List<LibraryStore.Track> tracks = library.tracks(LibraryStore.QUEUE);
        int currentIndex = order.indexOf(service.currentUrl());
        for (int offset = 0; offset < Math.min(3, order.size() - Math.max(0, currentIndex)); offset++) {
            int index = Math.max(0, currentIndex) + offset;
            if (index >= order.size()) break;
            String url = order.get(index);
            LibraryStore.Track found = null;
            for (LibraryStore.Track track : tracks) if (track.url.equals(url)) { found = track; break; }
            final String title = found == null ? "Música" : found.title;
            LinearLayout row = Ui.row(this);
            TextView name = new TextView(this);
            name.setText((index == currentIndex ? "●  " : (index + 1) + ".  ") + title);
            name.setTextColor(index == currentIndex ? Ui.GREEN : Color.WHITE);
            name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            final String target = url;
            name.setOnClickListener(v -> startForegroundService(new Intent(this, OfflineService.class)
                    .setAction(OfflineService.PLAY_TRACK).putExtra("url", target).putExtra("title", title)));
            row.addView(name, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
            queuePreview.addView(row);
        }
    }

    private void renderDownloadQueue() {
        downloadList.removeAllViews();
        if (service == null) return;
        List<DownloadQueue.Job> jobs = service.downloadJobs();
        if (jobs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Nenhum download na fila. Use Baixar nas músicas.");
            empty.setTextColor(Ui.MUTED);
            empty.setTextSize(13);
            downloadList.addView(empty);
            return;
        }
        for (DownloadQueue.Job job : jobs) {
            LinearLayout card = Ui.cardContainer(this);
            TextView title = new TextView(this);
            title.setText(job.title);
            title.setTextColor(Color.WHITE);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.addView(title);
            String detail;
            if (job.state == DownloadQueue.State.ACTIVE) {
                detail = service.progress() >= 0 ? "Baixando · " + service.progress() + "%" : "Baixando…";
            } else if (job.state == DownloadQueue.State.WAITING) {
                detail = service.isWifiOnly() && !service.onWifi() ? "Na fila · aguardando Wi-Fi" : "Na fila";
            } else if (job.state == DownloadQueue.State.FAILED) {
                detail = "Falhou · " + (job.error.isEmpty() ? "toque em Tentar" : job.error);
            } else {
                detail = "Concluído";
            }
            TextView info = new TextView(this);
            info.setText(detail);
            info.setTextColor(Ui.MUTED);
            info.setTextSize(12);
            card.addView(info);
            LinearLayout actions = Ui.row(this);
            if (job.state == DownloadQueue.State.FAILED) {
                Button retry = Ui.ghost(this, "Tentar");
                retry.setOnClickListener(v -> service.retryDownload(job.id));
                actions.addView(retry, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
            }
            if (job.state == DownloadQueue.State.WAITING || job.state == DownloadQueue.State.FAILED) {
                Button remove = Ui.ghost(this, "Remover");
                remove.setOnClickListener(v -> service.cancelDownload(job.id));
                actions.addView(remove, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
            }
            if (job.state == DownloadQueue.State.ACTIVE) {
                Button cancelJob = Ui.ghost(this, "Cancelar");
                cancelJob.setOnClickListener(v -> service.cancelDownload(job.id));
                actions.addView(cancelJob, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
            }
            if (actions.getChildCount() > 0) card.addView(actions);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = Ui.dp(this, 10);
            downloadList.addView(card, params);
        }
    }

    private void renderLibrary() {
        list.removeAllViews();
        List<OfflineStore.Song> songs = store.songs();
        long total = 0;
        for (OfflineStore.Song song : songs) total += song.bytes;
        TextView summary = new TextView(this);
        summary.setText(songs.size() + " arquivos · " + Display.size(total));
        summary.setTextColor(Ui.MUTED);
        summary.setTextSize(13);
        list.addView(summary);
        if (songs.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Ainda não há downloads. Use a busca para encontrar músicas e toque em Baixar.");
            empty.setTextColor(Color.WHITE);
            list.addView(empty);
            return;
        }
        Button playAll = Ui.primary(this, "Ouvir todas as baixadas");
        playAll.setOnClickListener(v -> {
            LibraryStore library = new LibraryStore(this);
            java.util.ArrayList<LibraryStore.Track> tracks = new java.util.ArrayList<>();
            for (OfflineStore.Song song : songs) tracks.add(new LibraryStore.Track(song.title, song.url,
                    song.uploader, song.thumbnail, song.duration / 1000));
            library.replaceQueue(tracks);
            startForegroundService(new Intent(this, OfflineService.class).setAction(OfflineService.PLAY)
                    .putExtra("id", songs.get(0).id));
        });
        list.addView(playAll);
        for (OfflineStore.Song song : songs) {
            LinearLayout card = Ui.cardContainer(this);
            LinearLayout top = Ui.row(this);
            ImageView thumb = Ui.cover(this, 52, 8);
            CoverCache.with(this).load(thumb, song.thumbnail);
            thumb.setContentDescription("Capa de " + song.title);
            top.addView(thumb);
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(Ui.dp(this, 12), 0, 0, 0);
            TextView name = new TextView(this);
            name.setText(song.title);
            name.setTextColor(Color.WHITE);
            name.setTextSize(16);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(name);
            TextView meta = new TextView(this);
            meta.setText((song.uploader.isEmpty() ? "Download" : song.uploader) + " · "
                    + Display.clock(song.duration) + " · " + Display.size(song.bytes));
            meta.setTextColor(Ui.MUTED);
            meta.setTextSize(12);
            texts.addView(meta);
            top.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
            card.addView(top);
            LinearLayout actions = Ui.row(this);
            Button play = Ui.primary(this, "Ouvir");
            play.setOnClickListener(v -> {
                LibraryStore library = new LibraryStore(this);
                library.appendQueue(new LibraryStore.Track(song.title, song.url,
                        song.uploader, song.thumbnail, song.duration / 1000));
                startForegroundService(new Intent(this, OfflineService.class).setAction(OfflineService.PLAY)
                        .putExtra("id", song.id));
            });
            Button delete = Ui.ghost(this, "Excluir");
            delete.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Excluir o áudio baixado?")
                    .setMessage(song.title + "\nOs favoritos e playlists serão mantidos.")
                    .setPositiveButton("Excluir", (d, w) -> {
                        if (service != null && song.id.equals(service.currentId())) service.stopPlayer();
                        try { store.delete(song); renderLibrary(); }
                        catch (Exception e) { Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show(); }
                    }).setNegativeButton("Cancelar", null).show());
            actions.addView(play, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
            actions.addView(delete, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
            card.addView(actions);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = Ui.dp(this, 10);
            list.addView(card, params);
        }
    }

    private void command(String action) { startForegroundService(new Intent(this, OfflineService.class).setAction(action)); }

    private int dp(int value) { return Ui.dp(this, value); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }

    private void showDownloadSettings() {
        if (service == null) return;
        boolean[] wifiOnly = {service.isWifiOnly()};
        boolean[] saver = {service.isDataSaver()};
        boolean[] autoOffline = {service.isAutoOffline()};
        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        android.widget.CheckBox wifiBox = new android.widget.CheckBox(this);
        wifiBox.setText("Baixar somente em Wi-Fi");
        wifiBox.setTextColor(Color.WHITE);
        wifiBox.setChecked(wifiOnly[0]);
        wifiBox.setOnCheckedChangeListener((v, checked) -> wifiOnly[0] = checked);
        android.widget.CheckBox saverBox = new android.widget.CheckBox(this);
        saverBox.setText("Economizar dados (qualidade menor)");
        saverBox.setTextColor(Color.WHITE);
        saverBox.setChecked(saver[0]);
        saverBox.setOnCheckedChangeListener((v, checked) -> saver[0] = checked);
        android.widget.CheckBox autoBox = new android.widget.CheckBox(this);
        autoBox.setText("Baixar favoritos e fila automaticamente no Wi-Fi");
        autoBox.setTextColor(Color.WHITE);
        autoBox.setChecked(autoOffline[0]);
        autoBox.setOnCheckedChangeListener((v, checked) -> autoOffline[0] = checked);
        options.addView(wifiBox);
        options.addView(saverBox);
        options.addView(autoBox);
        TextView hint = new TextView(this);
        hint.setText("A fila guarda até 50 músicas. Falhas podem ser retomadas até 3 vezes.");
        hint.setTextColor(Ui.MUTED);
        options.addView(hint);
        new AlertDialog.Builder(this).setTitle("Downloads").setView(options)
                .setPositiveButton("Salvar", (d, w) -> {
                    service.setWifiOnly(wifiOnly[0]);
                    service.setDataSaver(saver[0]);
                    service.setAutoOffline(autoOffline[0]);
                })
                .setNegativeButton("Cancelar", null).show();
    }

    private void showQueue() {
        if (service == null) return;
        List<String> order = service.queueOrder();
        if (order.isEmpty()) { Toast.makeText(this, "Adicione músicas pela biblioteca", Toast.LENGTH_SHORT).show(); return; }
        LibraryStore library = new LibraryStore(this);
        List<LibraryStore.Track> tracks = library.tracks(LibraryStore.QUEUE);
        String[] titles = new String[order.size()];
        for (int i = 0; i < order.size(); i++) {
            String title = "Música";
            for (LibraryStore.Track track : tracks) if (track.url.equals(order.get(i))) { title = track.title; break; }
            titles[i] = (order.get(i).equals(service.currentUrl()) ? "●  " : "") + title;
        }
        new AlertDialog.Builder(this).setTitle("Fila de reprodução").setItems(titles, (d, which) ->
                startForegroundService(new Intent(this, OfflineService.class).setAction(OfflineService.PLAY_TRACK)
                        .putExtra("url", order.get(which)).putExtra("title", titles[which].replaceFirst("^●  ", "")))).show();
    }
}
