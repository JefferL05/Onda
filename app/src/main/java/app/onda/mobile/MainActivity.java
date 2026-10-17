package app.onda.mobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import android.widget.ImageView;

public final class MainActivity extends Activity {
    private static final int BG = Ui.BG, PANEL = Ui.PANEL, GREEN = Ui.GREEN, MUTED = Ui.MUTED;
    private LinearLayout root;
    private LinearLayout stage, nav, undoBar;
    private boolean wide;
    private String collectionName, collectionKey, searchText = "", libraryFilter = "Todas";
    private final java.util.Map<String,String> remoteCovers=new java.util.HashMap<>();
    private final java.util.Set<String> partialCollections=new java.util.HashSet<>();
    private FrameLayout content;
    private FrameLayout screen;
    private LinearLayout browser;
    private WebView web;
    private ProgressBar progress;
    private TextView browserTitle;
    private TextView shield;
    private LinearLayout miniPlayer;
    private TextView miniTitle;
    private ImageView miniCover;
    private Button miniToggle;
    private OfflineService playback;
    private boolean playbackBound;
    private final Runnable playbackChanged = this::updateNavigation;
    private final android.content.ServiceConnection playbackConnection = new android.content.ServiceConnection() {
        @Override public void onServiceConnected(android.content.ComponentName name, android.os.IBinder binder) {
            playback = ((OfflineService.LocalBinder) binder).service();
            playback.addListener(playbackChanged);
            updateNavigation();
        }
        @Override public void onServiceDisconnected(android.content.ComponentName name) { playback = null; updateNavigation(); }
    };
    private LibraryStore.Track lastVideo;
    private final List<Button> navButtons = new ArrayList<>();
    private final ExecutorService searchWorkers = Executors.newFixedThreadPool(2);
    private Future<?> pendingSearch;
    private int searchGeneration;
    private String searchFilter = YoutubeSearch.FILTER_SONGS;
    private View customView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private LibraryStore library;
    private String page = "home";
    private boolean browserLoaded;
    private volatile boolean blocking = true;
    private final AtomicInteger blocked = new AtomicInteger();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        library = new LibraryStore(this);
        blocking = getPreferences(MODE_PRIVATE).getBoolean("blocking", true);
        screen = new FrameLayout(this);
        screen.setBackgroundColor(BG);
        root = column();
        screen.addView(root, new FrameLayout.LayoutParams(-1, -1));
        setContentView(screen);
        screen.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        LinearLayout heading = row();
        heading.setPadding(dp(20), dp(10), dp(16), dp(8));
        TextView brand = label("onda", 30, GREEN);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.addView(brand, new LinearLayout.LayoutParams(0, dp(48), 1));
        heading.addView(button("Sobre", this::showAbout));
        root.addView(heading);
        stage = row();
        root.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));
        content = new FrameLayout(this);
        stage.addView(content, new LinearLayout.LayoutParams(0, -1, 1));
        setupBrowser();
        setupMiniPlayer();
        nav = row();
        nav.setPadding(dp(8), dp(4), dp(8), dp(4));
        nav.setBackgroundColor(PANEL);
        addNav(nav, "Início", this::showHome);
        addNav(nav, "Buscar", this::showSearch);
        addNav(nav, "Sua Biblioteca", this::showLibrary);
        layoutNavigation();
        showHome();
        if (state != null && web.restoreState(state) != null) {
            browserLoaded = true;
            if ("browser".equals(state.getString("page"))) showBrowser();
        }
    }

    private void addNav(LinearLayout nav, String title, Runnable click) {
        Button b = button(title, click);
        b.setTextSize(13);
        b.setContentDescription(title);
        b.setPadding(0, 0, 0, 0);
        navButtons.add(b);
        nav.addView(b, new LinearLayout.LayoutParams(0, dp(52), 1));
    }

    private void layoutNavigation() {
        wide = getResources().getConfiguration().screenWidthDp >= Ui.WIDE_DP;
        if (nav.getParent() != null) ((ViewGroup)nav.getParent()).removeView(nav);
        nav.setOrientation(wide ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        nav.setGravity(wide ? Gravity.TOP : Gravity.CENTER_VERTICAL);
        for (Button b : navButtons) b.setLayoutParams(wide ? new LinearLayout.LayoutParams(-1,dp(56))
                : new LinearLayout.LayoutParams(0,dp(56),1));
        if (wide) stage.addView(nav,0,new LinearLayout.LayoutParams(dp(192),-1));
        else root.addView(nav,new LinearLayout.LayoutParams(-1,dp(64)));
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config); layoutNavigation();
    }

    private void setupMiniPlayer() {
        miniPlayer = row();
        miniPlayer.setPadding(dp(12), dp(8), dp(8), dp(8));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(28, 52, 44));
        background.setCornerRadius(dp(18));
        background.setStroke(2, Color.rgb(64, 96, 86));
        miniPlayer.setBackground(background);
        Ui.interactive(miniPlayer, Ui.GREEN_DARK, 18);
        miniPlayer.setContentDescription("Expandir player");
        miniPlayer.setOnClickListener(v -> openOffline());
        miniCover = new ImageView(this);
        miniCover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        miniCover.setImageDrawable(null);
        miniCover.setContentDescription("Capa da música atual");
        miniCover.setOnClickListener(v -> openOffline());
        miniPlayer.addView(miniCover, new LinearLayout.LayoutParams(dp(52), dp(52)));
        miniTitle = label("", 13, Color.WHITE);
        miniTitle.setMaxLines(2);
        miniTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        miniTitle.setOnClickListener(v -> openOffline());
        miniTitle.setFocusable(true);
        miniTitle.setPadding(dp(10), 0, dp(6), 0);
        miniPlayer.addView(miniTitle, new LinearLayout.LayoutParams(0, -2, 1));
        miniToggle = button("Ouvir", () -> startForegroundService(new Intent(this, OfflineService.class).setAction(OfflineService.TOGGLE)));
        miniPlayer.addView(miniToggle);
        miniPlayer.addView(button("⏭", () -> startForegroundService(new Intent(this, OfflineService.class).setAction(OfflineService.NEXT))));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.leftMargin = dp(12); params.rightMargin = dp(12); params.bottomMargin = dp(8);
        root.addView(miniPlayer, params);
        miniPlayer.setVisibility(View.GONE);
    }

    private void updateNavigation() {
        int selected = page.equals("home") ? 0 : (page.equals("search") || page.equals("browser")) ? 1 : 2;
        for (int i = 0; i < navButtons.size(); i++) {
            Button button = navButtons.get(i);
            button.setTextColor(i == selected ? GREEN : MUTED);
            button.setSelected(i == selected);
            button.setTypeface(Typeface.DEFAULT, i == selected ? Typeface.BOLD : Typeface.NORMAL);
        }
        if (miniPlayer != null) {
            boolean hasTrack = playback != null && playback.hasTrack();
            miniPlayer.setVisibility(hasTrack ? View.VISIBLE : View.GONE);
            if (hasTrack) {
                String artist = playback.uploader();
                miniTitle.setText(playback.title() + "\n" + (artist.isEmpty() ? "" : artist + " · ") + playback.playerStatus());
                miniToggle.setText(playback.playing() || (playback.loading() && playback.playRequested()) ? "⏸" : "▶");
                miniToggle.setContentDescription(playback.playing() ? "Pausar" : "Ouvir");
                CoverCache.with(this).load(miniCover, playback.cover());
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupBrowser() {
        browser = column();
        LinearLayout toolbar = row();
        toolbar.setPadding(dp(8), 0, dp(8), 0);
        toolbar.addView(button("‹", () -> { if (web.canGoBack()) web.goBack(); }));
        browserTitle = label("YouTube", 13, Color.WHITE);
        browserTitle.setMaxLines(1);
        browserTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        toolbar.addView(browserTitle, new LinearLayout.LayoutParams(0, dp(48), 1));
        toolbar.addView(button("Salvar", this::saveCurrent));
        toolbar.addView(button("⋮", this::browserMenu));
        browser.addView(toolbar);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        browser.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
        shield = label("", 11, MUTED);
        shield.setPadding(dp(16), dp(3), dp(16), dp(3));
        browser.addView(shield);
        LinearLayout queueControls = row();
        queueControls.addView(button("Ouvir com tela apagada", () -> {
            LibraryStore.Track track = currentVideo();
            if (track == null) toast("Abra um vídeo para ouvir o áudio."); else playNative(track);
        }), new LinearLayout.LayoutParams(0, dp(52), 2));
        queueControls.addView(button("Fila", this::showQueue), new LinearLayout.LayoutParams(0, dp(52), 1));
        browser.addView(queueControls);
        web = new WebView(this);
        web.setBackgroundColor(BG);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSafeBrowsingEnabled(true);
        settings.setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                String url = request.getUrl().toString();
                if (UrlPolicy.youtube(url)) return false;
                if (!UrlPolicy.host(url).isEmpty()) {
                    new AlertDialog.Builder(MainActivity.this).setTitle("Abrir fora do Onda?")
                            .setMessage(UrlPolicy.host(url))
                            .setPositiveButton("Abrir navegador", (d, w) -> external(url))
                            .setNegativeButton("Cancelar", null).show();
                } else toast("Este link não pode ser aberto no player.");
                return true;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (blocking && !request.isForMainFrame() && UrlPolicy.blocked(request.getUrl().toString())) {
                    blocked.incrementAndGet();
                    return new WebResourceResponse("text/plain", "UTF-8", 204, "No Content",
                            java.util.Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
                }
                return null;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
                browserTitle.setText(UrlPolicy.host(url));
            }
            @Override public void onPageFinished(WebView view, String url) {
                updateShield();
                rememberVideo();
            }
            @Override public void doUpdateVisitedHistory(WebView view, String url, boolean reload) {
                rememberVideo();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                                                   android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) {
                    progress.setVisibility(View.GONE);
                    toast("Não foi possível carregar. Verifique a conexão e use Recarregar.");
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
                progress.setVisibility(value >= 100 ? View.GONE : View.VISIBLE);
                updateShield();
            }
            @Override public void onReceivedTitle(WebView view, String title) {
                browserTitle.setText(title);
                rememberVideo();
            }
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) { callback.onCustomViewHidden(); return; }
                customView = view;
                fullscreenCallback = callback;
                root.setVisibility(View.GONE);
                screen.addView(view, new FrameLayout.LayoutParams(-1, -1));
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            @Override public void onHideCustomView() { leaveFullscreen(); }
        });
        browser.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private void updateShield() {
        shield.setText(blocking ? "Proteção básica · " + blocked.get() + " requisições bloqueadas"
                : "Proteção básica desativada");
    }

    private LibraryStore.Track currentVideo() {
        String url = UrlPolicy.canonicalVideo(web.getUrl() == null ? "" : web.getUrl());
        if (url == null) return null;
        String title = web.getTitle();
        if (title == null || title.trim().isEmpty()) title = "Vídeo do YouTube";
        return new LibraryStore.Track(title.replaceFirst(" - YouTube$", ""), url);
    }

    private void rememberVideo() {
        LibraryStore.Track track = currentVideo();
        if (track != null) {
            if (page.equals("browser") && web.hasWindowFocus() && playback != null && playback.playing()
                    && !track.url.equals(playback.currentUrl())) playback.pause();
            library.add(LibraryStore.HISTORY, track);
            lastVideo = track;
            updateNavigation();
        }
    }

    private void saveCurrent() {
        LibraryStore.Track track = currentVideo();
        if (track == null) { toast("Abra um vídeo para salvar na biblioteca."); return; }
        saveTrack(track);
    }

    private void saveTrack(LibraryStore.Track track) {
        LibraryDialogs.save(this, track, () -> {});
    }

    private void createPlaylist(LibraryStore.Track track) {
        LibraryDialogs.edit(this, null, track);
    }

    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request == LibraryDialogs.EDIT && result == RESULT_OK && data != null) {
            String name = data.getStringExtra("name"); showCollection(name,LibraryStore.playlistKey(name));
        }
    }

    private void undo(String message, Runnable restore) {
        if (undoBar != null) root.removeView(undoBar);
        final LinearLayout bar = row(); undoBar = bar; bar.setBackgroundColor(Ui.PANEL_HIGH); bar.setPadding(dp(12),0,dp(12),0);
        TextView text = label(message,13,Color.WHITE); bar.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        bar.addView(button("Desfazer",()->{restore.run(); root.removeView(bar); if (undoBar==bar) undoBar=null;}));
        root.addView(bar,root.indexOfChild(miniPlayer));
        bar.announceForAccessibility(message + ". Desfazer disponível.");
        bar.postDelayed(()->{root.removeView(bar); if(undoBar==bar) undoBar=null;},10000);
    }

    private void browserMenu() {
        String[] items = {"Recarregar", "Abrir link do YouTube", "Compartilhar vídeo",
                blocking ? "Desativar proteção básica" : "Ativar proteção básica", "Adicionar à fila", "Baixar áudio", "Músicas baixadas", "Ouvir com tela apagada"};
        new AlertDialog.Builder(this).setTitle("Navegador").setItems(items, (d, which) -> {
            if (which == 0) web.reload();
            if (which == 1) {
                EditText address = input("https://www.youtube.com/watch?v=...");
                new AlertDialog.Builder(this).setTitle("Abrir vídeo").setView(address)
                        .setPositiveButton("Abrir", (dialog, w) -> {
                            String url = address.getText().toString().trim();
                            if (UrlPolicy.youtube(url)) open(url); else toast("Use um link HTTPS do YouTube.");
                        }).setNegativeButton("Cancelar", null).show();
            }
            if (which == 2) {
                LibraryStore.Track track = currentVideo();
                if (track == null) { toast("Abra um vídeo primeiro."); return; }
                Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, track.title + "\n" + track.url);
                startActivity(Intent.createChooser(share, "Compartilhar"));
            }
            if (which == 3) {
                blocking = !blocking;
                getPreferences(MODE_PRIVATE).edit().putBoolean("blocking", blocking).apply();
                updateShield();
                web.reload();
            }
            if (which == 4) {
                LibraryStore.Track track = currentVideo();
                if (track == null) { toast("Abra um vídeo primeiro."); return; }
                library.enqueueManual(track,false);
                toast("Vídeo incluído na fila local");
            }
            if (which == 5) {
                LibraryStore.Track track = currentVideo();
                if (track == null) toast("Abra um vídeo antes de baixar.");
                else downloadAudio(track);
            }
            if (which == 6) openOffline();
            if (which == 7) { LibraryStore.Track track = currentVideo(); if (track != null) playNative(track); else toast("Abra um vídeo primeiro."); }
        }).show();
    }

    private void showHome() {
        LinearLayout body = nativePage("home");
        body.addView(Ui.eyebrow(this, "Seu som, seu espaço"));
        body.addView(title("Entre na sua onda."));
        body.addView(paragraph("O que você gosta. O que vem a seguir."));
        card(body, "▶  Encontrar uma música", "Busca nativa com capas, fila e download", this::showSearch);
        List<String> names=library.playlists();
        if(!names.isEmpty()) {
            body.addView(title("Suas playlists"));
            names.sort((a,b)->Boolean.compare(library.playlist(b).favorite,library.playlist(a).favorite));
            for(int i=0;i<Math.min(5,names.size());i++) playlistCard(body,names.get(i));
        }
        if (!library.tracks(LibraryStore.FAVORITES).isEmpty()) card(body,"Músicas curtidas",
                library.tracks(LibraryStore.FAVORITES).size()+" músicas",()->showCollection("Músicas curtidas",LibraryStore.FAVORITES));
        card(body, "Downloads e arquivos do celular", "Sua música mesmo sem internet", this::openOffline);
        body.addView(title("Escolha o clima"));
        LinearLayout moods = row();
        for (String mood : new String[]{"Foco", "Treino", "Relaxar"}) {
            final String term = "música " + mood;
            Button b = button(mood, () -> {
                searchText=term; showSearch();
            });
            moods.addView(b, new LinearLayout.LayoutParams(0, dp(56), 1));
        }
        body.addView(moods);
        List<LibraryStore.Track> history = library.tracks(LibraryStore.HISTORY);
        if (!history.isEmpty()) {
            body.addView(title("Continue ouvindo"));
            for (int i = 0; i < Math.min(4, history.size()); i++) {
                LibraryStore.Track track = history.get(i);
                trackRow(body,track,track.uploader,()->playNative(track),()->trackMenu(track,null,null));
            }
        }
        if(!history.isEmpty()) body.addView(button("Ver histórico recente",()->showCollection("Tocadas recentemente",LibraryStore.HISTORY)));
    }

    private void showSearch() {
        LinearLayout body = nativePage("search");
        body.addView(Ui.eyebrow(this, "Buscar"));
        body.addView(title("O que vamos ouvir?"));
        body.addView(paragraph("Busca nativa no YouTube, com capas. Sem abrir o navegador."));
        EditText query = input("Música, artista ou álbum");
        query.setText(searchText);
        query.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            public void onTextChanged(CharSequence s,int start,int before,int count) { searchText=s.toString(); }
            public void afterTextChanged(Editable s) { }
        });
        query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        body.addView(query, new LinearLayout.LayoutParams(-1, dp(60)));
        LinearLayout filters = row();
        String[] kinds={YoutubeSearch.FILTER_SONGS,YoutubeSearch.FILTER_ARTISTS,YoutubeSearch.FILTER_ALBUMS,YoutubeSearch.FILTER_PLAYLISTS,YoutubeSearch.FILTER_VIDEOS};
        String[] labels={"Músicas","Artistas","Álbuns","Playlists","Vídeos"};
        for(int i=0;i<kinds.length;i++) {
            String kind=kinds[i]; Button chip=Ui.chip(this,labels[i],kind.equals(searchFilter));
            chip.setOnClickListener(v->{searchFilter=kind;showSearch();});
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(112),dp(48)); params.rightMargin=dp(8); filters.addView(chip,params);
        }
        android.widget.HorizontalScrollView filterScroll=new android.widget.HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false); filterScroll.addView(filters); body.addView(filterScroll);
        TextView status = label("", 13, MUTED);
        body.addView(status);
        ProgressBar loading = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loading.setMax(100);
        loading.setIndeterminate(true);
        loading.setVisibility(View.GONE);
        body.addView(loading, new LinearLayout.LayoutParams(-1, dp(4)));
        LinearLayout results = column();
        body.addView(results);
        Runnable submit = () -> {
            String text = query.getText().toString().trim();
            if (text.isEmpty()) { query.setError("Digite sua busca"); return; }
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(query.getWindowToken(), 0);
            runNativeSearch(text, status, loading, results);
        };
        query.setOnEditorActionListener((v, action, event) -> {
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) { submit.run(); return true; }
            return false;
        });
        Button searchButton = Ui.primary(this, "Buscar  →");
        searchButton.setOnClickListener(v -> submit.run());
        body.addView(searchButton,body.indexOfChild(status));
        body.addView(button("Buscar também na minha biblioteca",()->showLibraryMatches(searchText)));
        if (!library.recentSearches().isEmpty()) {
            body.addView(title("Buscas recentes"));
            for (String term : library.recentSearches()) {
                Button recent = button("↗  " + term, () -> {
                    query.setText(term);
                    runNativeSearch(term, status, loading, results);
                });
                recent.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                body.addView(recent);
            }
            body.addView(button("Limpar buscas recentes", () -> { library.clearSearches(); showSearch(); }));
        }
        body.addView(title("Descobrir"));
        for (String genre : new String[]{"MPB", "Rock", "Hip hop", "Jazz", "Eletrônica", "Lo-fi"}) {
            final String term = genre + " música";
            card(body, genre, "Buscar músicas e apresentações", () -> {
                query.setText(term);
                library.rememberSearch(term);
                runNativeSearch(term, status, loading, results);
            });
        }
        if(!searchText.trim().isEmpty()) runNativeSearch(searchText,status,loading,results);
    }

    private void showRemoteCollection(YoutubeSearch.Result result) {
        LinearLayout body=nativePage("remote"); body.addView(title(result.title)); body.addView(paragraph("Carregando músicas…"));
        int request=searchGeneration;
        pendingSearch=searchWorkers.submit(()->{
            try {
                YoutubeSearch.CollectionResult loaded=YoutubeSearch.collection(result.url,"album".equals(result.kind));
                runOnUiThread(()->{
                    if(request!=searchGeneration||isDestroyed()) return;
                    String key="remote:"+result.kind+":"+result.url;
                    library.cacheCollection(key,loaded.tracks); remoteCovers.put(key,loaded.cover);
                    if(loaded.truncated) partialCollections.add(key); else partialCollections.remove(key);
                    showCollection(loaded.title,key);
                });
            } catch(Exception e) {
                runOnUiThread(()->{if(request!=searchGeneration||isDestroyed())return;
                    body.removeAllViews();body.addView(title("Não foi possível abrir a coleção"));
                    body.addView(paragraph("O serviço pode estar indisponível. Você pode tentar novamente."));
                    body.addView(button("Tentar novamente",()->showRemoteCollection(result)));body.addView(button("Voltar à busca",this::showSearch));});
            }
        });
    }

    private LinearLayout trackRow(LinearLayout parent,LibraryStore.Track track,String subtitle,Runnable play,Runnable more) {
        LinearLayout card=Ui.cardContainer(this); card.setOrientation(LinearLayout.HORIZONTAL);card.setGravity(Gravity.CENTER_VERTICAL);
        ImageView image=Ui.cover(this,56,12); image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        CoverCache.with(this).load(image,track.thumbnail);card.addView(image);
        LinearLayout words=column();words.setPadding(dp(12),0,dp(6),0);
        TextView name=label(track.title,16,Color.WHITE);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END); words.addView(name);
        TextView detail=label(subtitle==null?"":subtitle,13,MUTED);detail.setMaxLines(2);words.addView(detail);
        card.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        Ui.interactive(card,PANEL,16);card.setOnClickListener(v->play.run());card.setContentDescription("Ouvir "+track.title+". "+subtitle);
        Button options=button("⋮",more);options.setContentDescription("Opções de "+track.title);card.addView(options,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);parent.addView(card,p);return card;
    }

    private void trackMenu(LibraryStore.Track track,String name,String key) {
        List<String> choices=new ArrayList<>();
        Collections.addAll(choices,"Ouvir agora","Tocar a seguir","Adicionar à fila",library.liked(track.url)?"Remover curtida":"Curtir música","Adicionar à playlist","Baixar áudio","Ver vídeo");
        if(key!=null) Collections.addAll(choices,"Mover para cima","Mover para baixo","Remover desta coleção");
        new AlertDialog.Builder(this).setTitle(track.title).setItems(choices.toArray(new String[0]),(d,which)->{
            if(which==0) { if(key==null) playNative(track);else playContext(library.tracks(key),name,key,track,false); }
            if(which==1||which==2) {library.enqueueManual(track,which==1);toast(which==1?"Vai tocar a seguir":"Adicionada à sua fila");}
            if(which==3) {library.toggleLike(track);toast(library.liked(track.url)?"Música curtida":"Curtida removida");}
            if(which==4) saveTrack(track);
            if(which==5) downloadAudio(track);
            if(which==6) open(track.url);
            if(which==7||which==8) {library.moveTrack(key,track.url,which==7?-1:1);showCollection(name,key);}
            if(which==9) {
                List<LibraryStore.Track> previous=library.tracks(key);int index=0;
                for(int i=0;i<previous.size();i++) if(previous.get(i).url.equals(track.url))index=i;
                final int oldIndex=index;library.remove(key,track.url);showCollection(name,key);
                undo("Música removida",()->{library.restoreTrack(key,track,oldIndex);showCollection(name,key);});
            }
        }).show();
    }

    private void playlistCard(LinearLayout body,String name) {
        LibraryStore.Playlist p=library.playlist(name);List<LibraryStore.Track> tracks=library.tracks(LibraryStore.playlistKey(name));
        String cover=p.cover.isEmpty()?(tracks.isEmpty()?"":tracks.get(0).thumbnail):p.cover;
        LibraryStore.Track display=new LibraryStore.Track(name,"", "",cover,-1);
        trackRow(body,display,(p.favorite?"★ Favorita · ":"")+tracks.size()+" músicas · "+Ui.totalDuration(tracks),
                ()->showCollection(name,LibraryStore.playlistKey(name)),()->new AlertDialog.Builder(this).setTitle(name)
                        .setItems(new String[]{"Abrir",p.favorite?"Desfavoritar":"Favoritar","Editar"},(d,w)->{
                            if(w==0)showCollection(name,LibraryStore.playlistKey(name));
                            if(w==1){library.togglePlaylistFavorite(name);showLibrary();}
                            if(w==2)LibraryDialogs.edit(this,name,null);
                        }).show());
    }

    private void showLibraryMatches(String query) {
        LinearLayout body=nativePage("library");body.addView(title("Resultados na biblioteca"));
        int found=0;
        for(String name:library.playlists()) if(LibraryQuery.matches(name+" "+library.playlist(name).description,query)){playlistCard(body,name);found++;}
        for(LibraryStore.Track track:library.catalog()) if(LibraryQuery.matches(track.title+" "+track.uploader+" "+track.album,query)) {
            trackRow(body,track,track.uploader,()->playNative(track),()->trackMenu(track,null,null));found++;
        }
        if(found==0)body.addView(paragraph("Nenhum resultado salvo para esta busca."));
        body.addView(button("Voltar à biblioteca",this::showLibrary));
    }

    private void runNativeSearch(String term, TextView status, ProgressBar loading, LinearLayout results) {
        library.rememberSearch(term);
        if (pendingSearch != null) pendingSearch.cancel(true);
        int request = ++searchGeneration;
        results.removeAllViews();
        status.setText("Buscando " + term + "...");
        loading.setVisibility(View.VISIBLE);
        final String filter = searchFilter;
        pendingSearch = searchWorkers.submit(() -> {
            List<YoutubeSearch.Result> items;
            String error = null;
            try { items = YoutubeSearch.search(term, filter, 20); }
            catch (Exception e) {
                items = null;
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final List<YoutubeSearch.Result> found = items;
            final String failure = error;
            runOnUiThread(() -> {
                if (request != searchGeneration || isFinishing()) return;
                loading.setVisibility(View.GONE);
                if (found == null) {
                    String message = "Não foi possível buscar agora. " + (failure == null ? "" : failure);
                    if (message.length() > 300) message = message.substring(0, 300);
                    status.setText(message);
                    Button retry = Ui.primary(this, "Tentar novamente");
                    retry.setOnClickListener(v -> runNativeSearch(term, status, loading, results));
                    results.addView(retry);
                    Button openWeb = button("Abrir busca no navegador", () -> open(UrlPolicy.search(term)));
                    results.addView(openWeb);
                    return;
                }
                if (found.isEmpty()) {
                    status.setText("Nenhum resultado para " + term + ". Tente outro nome.");
                    return;
                }
                status.setText(found.size() + " resultados para " + term);
                for (YoutubeSearch.Result result : found) addSearchCard(results, result);
                Button openWeb = button("Não achou? Abrir no YouTube", () -> open(UrlPolicy.search(term)));
                results.addView(openWeb);
            });
        });
    }

    private void addSearchCard(LinearLayout parent, YoutubeSearch.Result result) {
        if(!"track".equals(result.kind)) {
            LinearLayout card=Ui.cardContainer(this); LinearLayout top=row(); ImageView image=Ui.cover(this,72,12);
            CoverCache.with(this).load(image,result.thumbnail); top.addView(image);
            TextView name=label(result.title+"\n"+result.uploader,16,Color.WHITE); name.setPadding(dp(12),0,0,0);
            top.addView(name,new LinearLayout.LayoutParams(0,-2,1)); card.addView(top);
            card.addView(button("artist".equals(result.kind)?"Buscar músicas deste artista":"Abrir coleção",()->{
                if("artist".equals(result.kind)) {searchText=result.title;searchFilter=YoutubeSearch.FILTER_SONGS;showSearch();}
                else showRemoteCollection(result);
            }));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);parent.addView(card,p);return;
        }
        LinearLayout card = Ui.cardContainer(this);
        LinearLayout top = Ui.row(this);
        ImageView cover = Ui.cover(this, 64, 10);
        CoverCache.with(this).load(cover, result.thumbnail);
        cover.setContentDescription("Capa de " + result.title);
        top.addView(cover);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, 0, 0);
        TextView title = label(result.title, 16, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(title);
        String subtitle = (result.uploader.isEmpty() ? "YouTube" : result.uploader)
                + (result.durationSec > 0 ? " · " + Ui.formatDuration(result.durationSec) : "");
        TextView detail = label(subtitle, 13, MUTED);
        detail.setMaxLines(1);
        detail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(detail);
        top.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(top);
        LinearLayout actions = Ui.row(this);
        Button play = Ui.primary(this, "Ouvir");
        play.setOnClickListener(v -> playNative(new LibraryStore.Track(result.title, result.url,
                result.uploader, result.thumbnail, result.durationSec)));
        Button queue = button("+ Fila", () -> {
            library.enqueueManual(new LibraryStore.Track(result.title, result.url,
                    result.uploader, result.thumbnail, result.durationSec),false);
            toast("Música incluída na fila");
        });
        Button download = button("Baixar", () -> downloadAudio(new LibraryStore.Track(result.title,
                result.url, result.uploader, result.thumbnail, result.durationSec)));
        actions.addView(play, new LinearLayout.LayoutParams(0, dp(52), 1));
        actions.addView(queue, new LinearLayout.LayoutParams(0, dp(52), 1));
        actions.addView(download, new LinearLayout.LayoutParams(0, dp(52), 1));
        card.addView(actions);
        card.addView(button("Curtir / playlist / tocar a seguir",()->trackMenu(new LibraryStore.Track(result.title,result.url,result.uploader,result.thumbnail,result.durationSec),null,null)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        parent.addView(card, params);
    }

    private void showLibrary() {
        LinearLayout body = nativePage("library");
        body.addView(Ui.eyebrow(this,"Seu acervo")); body.addView(title("Sua Biblioteca"));
        EditText query=input("Buscar nas músicas e playlists salvas");body.addView(query);
        body.addView(button("Buscar na biblioteca",()->showLibraryMatches(query.getText().toString())));
        LinearLayout filters=row(); android.widget.HorizontalScrollView scroller=new android.widget.HorizontalScrollView(this);
        for(String kind:new String[]{"Todas","Playlists","Favoritas","Artistas","Álbuns"}) {
            Button chip=Ui.chip(this,kind,kind.equals(libraryFilter));chip.setOnClickListener(v->{libraryFilter=kind;showLibrary();});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(108),dp(48));p.rightMargin=dp(8);filters.addView(chip,p);
        }
        scroller.addView(filters);scroller.setHorizontalScrollBarEnabled(false);body.addView(scroller);
        if("Todas".equals(libraryFilter)) {
            card(body,"Músicas curtidas",library.tracks(LibraryStore.FAVORITES).size()+" músicas",()->showCollection("Músicas curtidas",LibraryStore.FAVORITES));
            card(body,"Tocadas recentemente","Seu histórico neste aparelho",()->showCollection("Tocadas recentemente",LibraryStore.HISTORY));
            card(body,"Fila de reprodução","Suas escolhas primeiro",this::showQueue);
            card(body,"Downloads e biblioteca do celular","Ouvir mesmo sem internet",this::openOffline);
        }
        if("Artistas".equals(libraryFilter)||"Álbuns".equals(libraryFilter)) {
            java.util.Map<String,List<LibraryStore.Track>> groups=new java.util.TreeMap<>();
            boolean albums="Álbuns".equals(libraryFilter);
            for(LibraryStore.Track track:library.catalog()) {
                String group=albums?track.album:track.uploader;
                if(!group.trim().isEmpty())groups.computeIfAbsent(group,k->new ArrayList<>()).add(track);
            }
            for(java.util.Map.Entry<String,List<LibraryStore.Track>> entry:groups.entrySet()) {
                String key="remote:"+(albums?"album":"artist")+":library:"+entry.getKey();library.cacheCollection(key,entry.getValue());
                card(body,entry.getKey(),entry.getValue().size()+" músicas salvas",()->showCollection(entry.getKey(),key));
            }
            if(groups.isEmpty())body.addView(paragraph("Ainda não há metadados salvos nesta categoria. Artistas usam o nome de artista/canal fornecido pela fonte."));
            body.addView(button("Ver álbuns e músicas do aparelho",this::openOffline));
            return;
        }
        body.addView(button("+ Criar playlist",()->createPlaylist(null)));
        List<String> names=library.playlists();names.sort((a,b)->Boolean.compare(library.playlist(b).favorite,library.playlist(a).favorite));
        int count=0;
        for(String name:names) {
            if("Favoritas".equals(libraryFilter)&&!library.playlist(name).favorite)continue;
            playlistCard(body,name);count++;
        }
        if(count==0)body.addView(paragraph("Favoritas".equals(libraryFilter)?"Marque uma playlist com a estrela para encontrá-la aqui.":"Crie sua primeira playlist. Adicione músicas pelo menu de cada resultado."));
        if(library.canUndoPlaylist())body.addView(button("Restaurar última playlist excluída",()->{
            if(library.restoreDeletedPlaylist())showLibrary();else toast("Já existe uma playlist com esse nome.");
        }));
    }

    private void showCollection(String name, String key) {
        collectionName=name; collectionKey=key;
        LinearLayout body = nativePage("collection");
        body.addView(button("‹  Biblioteca", this::showLibrary));
        boolean playlist=key.startsWith("playlist:");
        LibraryStore.Playlist info=library.playlist(name);
        List<LibraryStore.Track> allTracks=library.tracks(key);
        String cover=playlist?info.cover:remoteCovers.getOrDefault(key,"");
        if(cover.isEmpty()&&!allTracks.isEmpty())cover=allTracks.get(0).thumbnail;
        LinearLayout hero=row();ImageView image=Ui.cover(this,112,20);CoverCache.with(this).load(image,cover);hero.addView(image);
        LinearLayout headings=column();headings.setPadding(dp(16),0,0,0);
        headings.addView(Ui.eyebrow(this,key.startsWith("remote:album")?"Álbum":playlist?"Playlist":"Coleção"));
        headings.addView(title(name));headings.addView(label(allTracks.size()+" músicas · "+Ui.totalDuration(allTracks),13,MUTED));
        hero.addView(headings,new LinearLayout.LayoutParams(0,-2,1));body.addView(hero);
        if(playlist&&!info.description.isEmpty())body.addView(paragraph(info.description));
        if(partialCollections.contains(key))body.addView(paragraph("Prévia: até 100 faixas carregadas. Esta coleção tem mais músicas na fonte."));
        if(playlist) {
            body.addView(button(info.favorite?"★ Playlist favorita":"☆ Favoritar playlist",()->{library.togglePlaylistFavorite(name);showCollection(name,key);}));
            body.addView(button("Editar nome, descrição e capa",()->LibraryDialogs.edit(this,name,null)));
        } else if(key.startsWith("remote:")) {
            final String art=cover;
            body.addView(button("Salvar estas músicas como playlist",()->{
                String base=name.length()>52?name.substring(0,52):name;
                String saved=base;int n=2;while(library.playlists().contains(saved))saved=base+" ("+(n++)+")";
                library.createPlaylist(saved);library.updatePlaylist(saved,"Salva a partir de "+name,art,false);
                for(int i=allTracks.size()-1;i>=0;i--)library.add(LibraryStore.playlistKey(saved),allTracks.get(i));
                showCollection(saved,LibraryStore.playlistKey(saved));
            }));
        }
        EditText filter = input("Filtrar nesta coleção");
        body.addView(filter, new LinearLayout.LayoutParams(-1, dp(56)));
        String[] sortMode = {"manual"};
        Button sort = button("Ordem da playlist", () -> {});
        body.addView(sort);
        LinearLayout actions = row();
        actions.addView(button("Reproduzir", () -> startQueue(library.sorted(key, filter.getText().toString(), sortMode[0]), false)),
                new LinearLayout.LayoutParams(0, dp(48), 1));
        actions.addView(button("Embaralhar", () -> startQueue(library.sorted(key, filter.getText().toString(), sortMode[0]), true)),
                new LinearLayout.LayoutParams(0, dp(48), 1));
        body.addView(actions);
        Button downloadAll = Ui.ghost(this, "↓  Baixar lista");
        downloadAll.setOnClickListener(v -> {
            List<LibraryStore.Track> all = library.sorted(key, filter.getText().toString(), sortMode[0]);
            if (all.isEmpty()) { toast("Nada para baixar nesta lista."); return; }
            if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 30);
            }
            for (LibraryStore.Track track : all) {
                startForegroundService(new Intent(this, OfflineService.class).setAction(OfflineService.DOWNLOAD)
                        .putExtra("url", track.url).putExtra("title", track.title));
            }
            openOffline();
        });
        body.addView(downloadAll);
        TextView count = label("", 12, MUTED);
        body.addView(count);
        LinearLayout list = column();
        body.addView(list);
        final int[] shown = {60};
        final int[] filterGeneration = {0};
        final Runnable[] debounced = {null};
        final Runnable[] renderBox = {null};
        Runnable render = () -> {
            list.removeAllViews();
            List<LibraryStore.Track> items = library.sorted(key, filter.getText().toString(), sortMode[0]);
            count.setText(items.size() + " de " + library.tracks(key).size() + " músicas");
            if (items.isEmpty()) list.addView(paragraph("Nenhuma música encontrada. Use a busca para descobrir e salvar."));
            // Paginated render: 60 rows at a time caps main-thread inflation and cover bursts on big playlists.
            int limit = Math.min(shown[0], items.size());
            for (int rowIndex = 0; rowIndex < limit; rowIndex++) {
                LibraryStore.Track track = items.get(rowIndex);
                LinearLayout entry = Ui.cardContainer(this);
                LinearLayout top = Ui.row(this);
                ImageView thumb = Ui.cover(this, 52, 8);
                CoverCache.with(this).load(thumb, track.thumbnail);
                thumb.setContentDescription("Capa de " + track.title);
                top.addView(thumb);
                LinearLayout texts = new LinearLayout(this);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setPadding(dp(12), 0, 0, 0);
                TextView text = label(track.title, 15, Color.WHITE);
                text.setMaxLines(2);
                text.setEllipsize(android.text.TextUtils.TruncateAt.END);
                text.setFocusable(true);
                text.setOnClickListener(v -> playContext(items,name,key,track,false));
                texts.addView(text);
                boolean downloaded = new OfflineStore(this).find(OfflineStore.id(track.url)) != null;
                String meta = (track.uploader.isEmpty() ? "" : track.uploader + " · ")
                        + (track.durationSec > 0 ? Ui.formatDuration(track.durationSec) + " · " : "")
                        + (downloaded ? "↓ Baixada" : "Online");
                TextView sub = label(meta, 12, MUTED);
                sub.setMaxLines(1);
                sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
                texts.addView(sub);
                top.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
                Button more = button("⋮", () -> trackMenu(track,name,key));
                more.setContentDescription("Opções de " + track.title);
                top.addView(more);
                entry.addView(top);
                Ui.interactive(entry,PANEL,16);entry.setOnClickListener(v->playContext(items,name,key,track,false));
                if(playlist) {
                    entry.setOnLongClickListener(v->{
                        if(!"manual".equals(sortMode[0])||filter.length()>0){toast("Use a ordem da playlist sem filtro para arrastar.");return true;}
                        return v.startDragAndDrop(android.content.ClipData.newPlainText("Faixa",track.url),new View.DragShadowBuilder(v),track.url,0);
                    });
                    entry.setOnDragListener((v,event)->{
                        if(!(event.getLocalState() instanceof String))return false;
                        if(event.getAction()==android.view.DragEvent.ACTION_DROP) {
                            List<LibraryStore.Track> original=library.tracks(key);int target=0;
                            for(int i=0;i<original.size();i++)if(original.get(i).url.equals(track.url))target=i;
                            library.moveTo(key,(String)event.getLocalState(),target);showCollection(name,key);
                        }
                        return true;
                    });
                }
                LinearLayout.LayoutParams entryParams = new LinearLayout.LayoutParams(-1, -2);
                entryParams.bottomMargin = dp(10);
                list.addView(entry, entryParams);
            }
            if (limit < items.size()) {
                Button more = button("Mostrar mais (" + (items.size() - limit) + ")", () -> { shown[0] += 60; renderBox[0].run(); });
                list.addView(more);
            }
        };
        renderBox[0] = render;
        sort.setOnClickListener(v -> {
            String[] labels={"Ordem da playlist","Título A–Z","Artista A–Z","Data de inclusão"};
            new AlertDialog.Builder(this).setTitle("Ordenar").setItems(labels,(d,which)->{
                sortMode[0]=new String[]{"manual","title","artist","date"}[which];sort.setText(labels[which]);shown[0]=60;render.run();
            }).show();
        });
        filter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            // Debounced: typing only schedules a render 300ms after the last keystroke.
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                int request = ++filterGeneration[0];
                shown[0] = 60;
                if (debounced[0] != null) filter.removeCallbacks(debounced[0]);
                debounced[0] = () -> { if (request == filterGeneration[0]) render.run(); };
                filter.postDelayed(debounced[0], 300);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        render.run();
        if (key.equals(LibraryStore.HISTORY)) {
            body.addView(button("Limpar histórico", () -> new AlertDialog.Builder(this)
                    .setTitle("Limpar histórico local?")
                    .setPositiveButton("Limpar", (d, w) -> { library.clearHistory(); showCollection(name, key); })
                    .setNegativeButton("Cancelar", null).show()));
        } else if (key.startsWith("playlist:")) {
            body.addView(button("Excluir playlist", () -> new AlertDialog.Builder(this)
                    .setTitle("Excluir esta playlist?").setMessage("Os vídeos do YouTube não serão excluídos.")
                    .setPositiveButton("Excluir", (d, w) -> {
                        library.deletePlaylist(name); showLibrary();
                        undo("Playlist excluída",()->{if(library.restoreDeletedPlaylist())showLibrary();else toast("Nome já utilizado. Nada foi sobrescrito.");});
                    })
                    .setNegativeButton("Cancelar", null).show()));
        }
    }

    private void downloadAudio(LibraryStore.Track track) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 30);
        }
        Intent intent = new Intent(this, OfflineService.class).setAction(OfflineService.DOWNLOAD)
                .putExtra("url", track.url).putExtra("title", track.title);
        startForegroundService(intent);
        openOffline();
    }

    private void openOffline() {
        web.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(m){m.pause();});", null);
        startActivity(new Intent(this, OfflineActivity.class));
    }

    private void playNative(LibraryStore.Track track) {
        library.beginContext(Collections.singletonList(track),"Faixa · "+track.title,"single");
        startTrack(track,true,false);
    }
    private void startTrack(LibraryStore.Track track,boolean startContext,boolean shuffle) {
        web.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(m){m.pause();});", null);
        library.appendQueue(track);
        startForegroundService(new Intent(this, OfflineService.class).setAction(OfflineService.PLAY_TRACK)
                .putExtra("url", track.url).putExtra("title", track.title)
                .putExtra("uploader", track.uploader).putExtra("thumbnail", track.thumbnail)
                .putExtra("durationSec", track.durationSec).putExtra("context_start",startContext).putExtra("shuffle",shuffle));
        openOffline();
    }
    private void playContext(List<LibraryStore.Track> items,String name,String key,LibraryStore.Track selected,boolean shuffle) {
        if(items.isEmpty()||selected==null){toast("Adicione músicas para começar.");return;}
        library.beginContext(items,name,key.startsWith("remote:album")?"album":"playlist");
        startTrack(selected,true,shuffle);
    }

    private void searchYoutube(String query) {
        library.rememberSearch(query);
        open(UrlPolicy.search(query));
    }

    private List<LibraryStore.Track> filteredTracks(String key, String query, boolean alphabetical) {
        List<LibraryStore.Track> items = library.tracks(key);
        items.removeIf(track -> !LibraryQuery.matches(track.title, query));
        if (alphabetical) items.sort((a, b) -> LibraryQuery.normalized(a.title).compareTo(LibraryQuery.normalized(b.title)));
        return items;
    }

    private void renamePlaylist(String oldName) {
        EditText name = input("Nome da playlist");
        name.setText(oldName);
        name.setSelectAllOnFocus(true);
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(60)});
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Renomear playlist").setView(name)
                .setPositiveButton("Salvar", null).setNegativeButton("Cancelar", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = name.getText().toString().trim();
            if (!library.renamePlaylist(oldName, value)) { name.setError("Digite um nome válido e diferente das outras playlists."); return; }
            dialog.dismiss();
            showCollection(value, LibraryStore.playlistKey(value));
        }));
        dialog.show();
    }

    private void startQueue(List<LibraryStore.Track> items, boolean shuffle) {
        if (items.isEmpty()) { toast("Nenhum vídeo para adicionar à fila."); return; }
        LibraryStore.Track first=items.get(shuffle?new java.util.Random().nextInt(items.size()):0);
        playContext(items,collectionName==null?"Sua fila":collectionName,collectionKey==null?"queue":collectionKey,first,shuffle);
    }

    private void showQueue() {
        LinearLayout body = nativePage("queue");
        body.addView(button("‹  Biblioteca", this::showLibrary));
        body.addView(title("Sua próxima onda"));
        body.addView(paragraph("Suas adições manuais têm prioridade. As sugestões automáticas aparecem identificadas."));
        if(playback!=null) {
            body.addView(label(playback.originLabel(),13,GREEN));
            body.addView(button(playback.autoplay()?"Rádio automática: ligada":"Rádio automática: desligada",()->{playback.autoplay(!playback.autoplay());showQueue();}));
        }
        List<LibraryStore.Track> baseItems = library.tracks(LibraryStore.QUEUE);
        List<LibraryStore.Track> items = new ArrayList<>();
        if (playback != null) {
            for (String url : playback.queueOrder()) {
                for (LibraryStore.Track item : baseItems) if (item.url.equals(url)) { items.add(item); break; }
            }
            if(playback.shuffled())body.addView(paragraph("Ordem aleatória ativa. Reorganizar manualmente desativa o aleatório."));
        } else items.addAll(baseItems);
        if (items.isEmpty()) body.addView(paragraph("Abra uma coleção e toque em Iniciar fila, ou adicione um vídeo pelo menu ⋮."));
        for (int index = 0; index < items.size(); index++) {
            LibraryStore.Track track = items.get(index);
            boolean current = playback != null && track.url.equals(playback.currentUrl());
            LinearLayout entry = row();
            String origin=library.origin(track.url);
            String source="radio".equals(origin)?"Sugestão automática":"manual".equals(origin)?"Adicionada por você":library.contextName();
            TextView name = label((current ? "●  " : (index + 1) + ".  ") + track.title+"\n"+source, 15, current ? GREEN : Color.WHITE);
            name.setPadding(dp(8), dp(12), dp(8), dp(12));
            name.setFocusable(true);
            name.setOnClickListener(v -> startTrack(track,false,false));
            entry.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            Button options = button("⋮", () -> new AlertDialog.Builder(this).setTitle(track.title)
                    .setItems(new String[]{"Mover para cima", "Mover para baixo", "Remover da fila"}, (d, which) -> {
                        if (which == 2) library.remove(LibraryStore.QUEUE, track.url);
                        else {
                            if (playback != null && playback.shuffled()) {
                                library.replaceQueue(items);
                                playback.toggleShuffle();
                            }
                            library.moveQueue(track.url, which == 0 ? -1 : 1);
                        }
                        showQueue();
                    }).show());
            options.setContentDescription("Organizar " + track.title);
            entry.addView(options);
            body.addView(entry);
        }
        if (!items.isEmpty()) body.addView(button("Limpar fila", () -> new AlertDialog.Builder(this)
                .setTitle("Limpar a fila local?").setMessage("Suas playlists e favoritos serão mantidos.")
                .setPositiveButton("Limpar", (d, w) -> { library.replaceQueue(new ArrayList<>()); showQueue(); })
                .setNegativeButton("Cancelar", null).show()));
    }

    private void showBrowser() {
        page = "browser";
        content.removeAllViews();
        content.addView(browser, new FrameLayout.LayoutParams(-1, -1));
        web.onResume();
        updateNavigation();
        if (!browserLoaded) { browserLoaded = true; web.loadUrl("https://m.youtube.com"); }
    }

    private void open(String url) {
        if (!UrlPolicy.youtube(url)) { toast("Endereço do YouTube inválido."); return; }
        View focus = getCurrentFocus();
        if (focus != null) ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(), 0);
        browserLoaded = true;
        showBrowser();
        web.loadUrl(url);
    }

    private LinearLayout nativePage(String name) {
        searchGeneration++;
        if(pendingSearch!=null)pendingSearch.cancel(true);
        if (page.equals("browser")) web.onPause();
        page = name;
        updateNavigation();
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = column();
        int horizontal=22+Math.max(0,(getResources().getConfiguration().screenWidthDp-(wide?192:0)-920)/2);
        body.setPadding(dp(horizontal), dp(16), dp(horizontal), dp(24));
        scroll.addView(body);
        content.addView(scroll);
        return body;
    }

    private void showAbout() {
        new AlertDialog.Builder(this).setTitle("Onda · versão 0.8.0")
                .setMessage("Navegador musical independente para Android. Não é afiliado ao Spotify, YouTube ou Brave.\n\n"
                        + "Favoritos, playlists e histórico são locais. Não há cadastro Onda nem sincronização. "
                        + "O YouTube recebe as visitas e buscas e pode salvar seus próprios cookies.\n\n"
                        + "A proteção bloqueia uma lista pequena de domínios de publicidade e rastreamento. "
                        + "Anúncios servidos pelo próprio YouTube podem aparecer.\n\n"
                        + "O player nativo mantém áudio online ou baixado com a tela apagada, fila automática, repetição e aleatório. "
                        + "No navegador, escolha Ouvir com tela apagada para transferir ao player. Downloads usam NewPipe Extractor 0.26.5 (GPL-3.0-or-later) "
                        + "e dependem da disponibilidade do vídeo no YouTube. Arquivos M4A/WebM ficam no armazenamento privado do app. "
                        + "Desinstalar o Onda apaga os downloads.")
                .setNeutralButton("Licenças", (d, w) -> showLicenses())
                .setPositiveButton("Entendi", null).show();
    }

    private void showLicenses() {
        try {
            String[] files = getAssets().list("licenses");
            new AlertDialog.Builder(this).setTitle("Componentes de terceiros").setItems(files, (d, index) -> {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                        getAssets().open("licenses/" + files[index]), java.nio.charset.StandardCharsets.UTF_8))) {
                    StringBuilder text = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) text.append(line).append('\n');
                    ScrollView scroll = new ScrollView(this);
                    TextView content = label(text.toString(), 12, Color.WHITE);
                    content.setPadding(dp(16), dp(16), dp(16), dp(16));
                    scroll.addView(content);
                    new AlertDialog.Builder(this).setTitle(files[index]).setView(scroll).setPositiveButton("Fechar", null).show();
                } catch (java.io.IOException e) { toast("Não foi possível abrir a licença."); }
            }).show();
        } catch (java.io.IOException e) { toast("Não foi possível listar as licenças."); }
    }

    private void external(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (android.content.ActivityNotFoundException e) { toast("Nenhum navegador disponível."); }
    }

    private void leaveFullscreen() {
        if (customView == null) return;
        screen.removeView(customView);
        customView = null;
        root.setVisibility(View.VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (fullscreenCallback != null) { fullscreenCallback.onCustomViewHidden(); fullscreenCallback = null; }
    }

    @Override public void onBackPressed() {
        if (customView != null) { leaveFullscreen(); return; }
        if (page.equals("browser") && web.canGoBack()) { web.goBack(); return; }
        if (page.equals("collection") || page.equals("queue")) { showLibrary(); return; }
        if (!page.equals("home")) { showHome(); return; }
        super.onBackPressed();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
        out.putString("page", page);
    }
    @Override protected void onStart() {
        super.onStart();
        playbackBound = bindService(new Intent(this, OfflineService.class), playbackConnection, BIND_AUTO_CREATE);
    }
    @Override protected void onStop() {
        if (playback != null) playback.removeListener(playbackChanged);
        if (playbackBound) unbindService(playbackConnection);
        playbackBound = false; playback = null;
        super.onStop();
    }
    @Override protected void onPause() {
        web.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(m){m.pause();});", null);
        web.onPause(); CookieManager.getInstance().flush(); super.onPause();
    }
    @Override protected void onResume() { super.onResume(); if (web != null && page.equals("browser")) web.onResume(); }
    @Override protected void onDestroy() {
        if (pendingSearch != null) pendingSearch.cancel(true);
        searchWorkers.shutdownNow();
        if (web != null) { browser.removeView(web); web.stopLoading(); web.destroy(); }
        super.onDestroy();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private TextView label(String text, int size, int color) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(color); v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    private TextView title(String text) {
        TextView v = label(text, 27, Color.WHITE); v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setPadding(0, dp(20), 0, dp(12)); return v;
    }
    private TextView paragraph(String text) {
        TextView v = label(text, 14, MUTED); v.setLineSpacing(dp(4), 1); v.setPadding(0, dp(4), 0, dp(20)); return v;
    }
    private Button button(String text, Runnable action) {
        Button b = Ui.ghost(this,text);
        b.setTextSize(13); b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(dp(48));
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setOnClickListener(v -> action.run()); return b;
    }
    private EditText input(String hint) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setHint(hint); e.setTextColor(Color.WHITE);
        e.setTextSize(16); e.setHintTextColor(MUTED); e.setPadding(dp(14), dp(8), dp(14), dp(8)); return e;
    }
    private void card(LinearLayout parent, String title, String subtitle, Runnable action) {
        LinearLayout box = column(); box.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable background = new GradientDrawable(); background.setColor(PANEL); background.setCornerRadius(dp(18));
        box.setBackground(background);
        Ui.interactive(box,PANEL,18);
        TextView heading = label(title, 18, Color.WHITE); heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        box.addView(heading);
        TextView detail = label(subtitle, 13, MUTED); detail.setPadding(0, dp(7), 0, 0); box.addView(detail);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(12);
        parent.addView(box, params); box.setOnClickListener(v -> action.run()); box.setFocusable(true);
    }
}
