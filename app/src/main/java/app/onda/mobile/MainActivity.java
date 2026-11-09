package app.onda.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * App shell: one activity, five destinations, a persistent mini player.
 *
 * Each destination owns its content view and is reused when you come back —
 * previous versions rebuilt the whole tree on every visit, losing scroll
 * position and re-inflating hundreds of views. Navigation is explicit, the
 * back stack is a real stack, and every screen gets its data from the same
 * LibraryStore/PlayerController.
 */
public final class MainActivity extends Activity implements PlayerController.Listener {
    static final int TAB_HOME = 0, TAB_EXPLORE = 1, TAB_SEARCH = 2, TAB_LIBRARY = 3;

    private static MainActivity current;

    private LinearLayout root;
    private LinearLayout stage;
    private FrameLayout content;
    private final View[] destinations = new View[4];
    /**
     * Assigned in onCreate, not as a field initialiser. Field initialisers run
     * inside the constructor, before the Activity has a base context attached,
     * so LibraryStore.shared(this) there reaches getApplicationContext() with
     * nothing to return and dies with a NullPointerException — the app closed
     * before drawing a single pixel.
     */
    private LibraryStore store;
    private int tab = TAB_HOME;
    private final java.util.ArrayDeque<Integer> backStack = new java.util.ArrayDeque<>();
    private boolean popping;
    private long lastBack;

    private LinearLayout navBar;
    private final List<LinearLayout> navTabs = new ArrayList<>();
    private final List<ImageView> navGlyphs = new ArrayList<>();
    private final List<TextView> navLabels = new ArrayList<>();
    private boolean wide;

    private LinearLayout miniHost;
    private MiniPlayer mini;
    private PlayerController player;

    private Screens.Screen pushed;
    private boolean browserLoaded;
    private android.webkit.WebView web;
    private FrameLayout browserHost;
    private android.widget.ProgressBar browserProgress;
    private TextView browserTitle;
    private LinearLayout browserBar;
    private boolean browserVisible;
    private View customView;
    private android.webkit.WebChromeClient.CustomViewCallback fullscreenCallback;

    private LinearLayout undoBar;
    private static Runnable pendingUndo;

    private Screens.Home homeScreen;
    private Screens.Explore exploreScreen;
    private Screens.Search searchScreen;
    private Screens.Library libraryScreen;
    private Screens.Screen nowPlayingScreen;

    public static MainActivity current() { return current; }

    public LibraryStore library() { return store; }

    LinearLayout rootMiniHost() { return miniHost; }

    private String libraryFilter = "Tudo";

    String libraryFilter() { return libraryFilter; }

    void libraryFilterInternal(String value) { libraryFilter = value == null ? "Tudo" : value; }

    public PlayerController player() { return player; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        current = this;
        store = LibraryStore.shared(this);
        player = PlayerController.get(this);
        root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        FrameLayout shell = new FrameLayout(this);
        shell.setBackgroundColor(Ui.BG);
        shell.addView(root, new FrameLayout.LayoutParams(-1, -1));
        setContentView(shell);
        applyInsets(shell);

        stage = Ui.row(this);
        stage.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(stage);

        content = new FrameLayout(this);
        stage.addView(content, new LinearLayout.LayoutParams(0, -1, 1));

        miniHost = Ui.column(this);
        root.addView(miniHost, new LinearLayout.LayoutParams(-1, -2));

        navBar = Ui.row(this);
        navBar.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        navBar.setBackgroundColor(Ui.BG);
        root.addView(navBar, new LinearLayout.LayoutParams(-1, -2));

        addTab(Glyph.HOME, "Início");
        addTab(Glyph.SPARK, "Explorar");
        addTab(Glyph.SEARCH, "Buscar");
        addTab(Glyph.LIBRARY, "Biblioteca");

        mini = new MiniPlayer(this, player, this::openNowPlaying);
        layoutNavigation();
        selectTab(TAB_HOME, false);
        if (state != null) {
            int restored = state.getInt("tab", TAB_HOME);
            if (restored >= 0 && restored < 4) selectTab(restored, false);
        }
        if (getIntent() != null && getIntent().getBooleanExtra("open_player", false)) openNowPlaying();
    }

    private void applyInsets(View shell) {
        shell.setOnApplyWindowInsetsListener((view, insets) -> {
            int left, top, right, bottom;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                left = bars.left;
                top = bars.top;
                right = bars.right;
                bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(left, top, right, bottom);
            return insets;
        });
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
        } else {
            getWindow().setStatusBarColor(Ui.BG);
            getWindow().setNavigationBarColor(Ui.BG);
        }
    }

    // ----------------------------------------------------------- navigation
    private void addTab(String glyph, String label) {
        LinearLayout tabView = Ui.column(this);
        tabView.setGravity(Gravity.CENTER);
        tabView.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 6));
        tabView.setContentDescription(label);
        Ui.ripple(tabView, 16);
        ImageView icon = Glyph.view(this, glyph, 22, Ui.TEXT3);
        tabView.addView(icon);
        TextView text = new TextView(this);
        text.setText(label);
        text.setTextSize(10);
        text.setTextColor(Ui.TEXT3);
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, Ui.dp(this, 3), 0, 0);
        tabView.addView(text);
        int index = navTabs.size();
        tabView.setOnClickListener(v -> selectTab(index, true));
        navTabs.add(tabView);
        navGlyphs.add(icon);
        navLabels.add(text);
    }

    private void layoutNavigation() {
        wide = getResources().getConfiguration().smallestScreenWidthDp >= 600;
        if (navBar.getParent() != null) ((ViewGroup) navBar.getParent()).removeView(navBar);
        navBar.setOrientation(wide ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        navBar.setGravity(wide ? Gravity.TOP : Gravity.CENTER_VERTICAL);
        for (LinearLayout tabView : navTabs) {
            ViewGroup.LayoutParams params = tabView.getLayoutParams();
            if (params == null) params = new LinearLayout.LayoutParams(0, Ui.dp(this, 60), 1);
            if (params instanceof LinearLayout.LayoutParams) {
                ((LinearLayout.LayoutParams) params).width = wide ? Ui.dp(this, 168) : 0;
                ((LinearLayout.LayoutParams) params).height = wide ? Ui.dp(this, 60) : 0;
                ((LinearLayout.LayoutParams) params).weight = wide ? 0 : 1;
                if (wide) ((LinearLayout.LayoutParams) params).topMargin = Ui.dp(this, 4);
            }
            tabView.setLayoutParams(params);
        }
        if (wide) {
            navBar.setPadding(Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16));
            stage.addView(navBar, 0, new LinearLayout.LayoutParams(Ui.dp(this, 184), -1));
        } else {
            navBar.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
            root.addView(navBar, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config);
        layoutNavigation();
        refreshSelection();
        if (mini != null) mini.layout();
    }

    void selectTab(int index, boolean remember) {
        if (index < 0 || index >= 4) return;
        if (index == TAB_HOME && homeScreen == null) destinations[index] = null;
        if (index == TAB_EXPLORE && exploreScreen == null) destinations[index] = null;
        if (index == TAB_SEARCH && searchScreen == null) destinations[index] = null;
        if (index == TAB_LIBRARY && libraryScreen == null) destinations[index] = null;
        if (remember && !popping && tab != index) backStack.push(tab);
        tab = index;
        pushed = null;
        hideBrowser();
        content.removeAllViews();
        View view = destinations[index];
        if (view == null) {
            view = createDestination(index);
            destinations[index] = view;
        }
        content.addView(view, new FrameLayout.LayoutParams(-1, -1));
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(140).setStartDelay(0).start();
        refreshSelection();
        if (index == TAB_HOME && homeScreen != null) homeScreen.refresh();
        if (index == TAB_EXPLORE && exploreScreen != null) exploreScreen.refresh();
        if (index == TAB_LIBRARY && libraryScreen != null) libraryScreen.refresh();
        invalidateOptionsMenu();
    }

    private View createDestination(int index) {
        switch (index) {
            case TAB_HOME:
                homeScreen = new Screens.Home(this);
                return homeScreen.view();
            case TAB_EXPLORE:
                exploreScreen = new Screens.Explore(this);
                return exploreScreen.view();
            case TAB_SEARCH:
                searchScreen = new Screens.Search(this);
                return searchScreen.view();
            default:
                libraryScreen = new Screens.Library(this);
                return libraryScreen.view();
        }
    }

    private void refreshSelection() {
        int colorActive = Ui.CYAN, colorIdle = Ui.TEXT3;
        boolean inTab = pushed == null || !(pushed instanceof Screens.NowPlaying);
        for (int i = 0; i < navTabs.size(); i++) {
            boolean selected = i == tab && !browserVisible && inTab;
            navGlyphs.get(i).setImageDrawable(Glyph.drawable(
                    i == 0 ? Glyph.HOME : i == 1 ? Glyph.SPARK : i == 2 ? Glyph.SEARCH : Glyph.LIBRARY,
                    selected ? colorActive : colorIdle, false));
            navLabels.get(i).setTextColor(selected ? colorActive : colorIdle);
            navLabels.get(i).setTypeface(Typeface.create("sans-serif-medium", selected ? Typeface.BOLD : Typeface.NORMAL));
            View parent = navTabs.get(i);
            parent.setSelected(selected);
            parent.setBackground(selected ? Ui.surface(this, Ui.withAlpha(Ui.CYAN, 0x10), 16, false) : null);
        }
        if (player != null) player.state();
    }

    // ---------------------------------------------------- content screens
    void openCollection(String title, String key, String coverUrl) {
        pushScreen(new Screens.Collection(this, title, key, coverUrl));
    }

    void openArtist(String name) {
        pushScreen(new Screens.Artist(this, name));
    }

    void openPlaylistEditor(String name, LibraryStore.Track seed) {
        LibraryDialogs.edit(this, name, seed);
    }

    void openSettings() {
        pushScreen(new Screens.Settings(this));
    }

    void openEqualizer() {
        pushScreen(new Screens.Equalizer(this));
    }

    void openWelcomeSecurity() {
        pushScreen(new Screens.WelcomeSecurity(this));
    }

    void openQueue() {
        pushScreen(new Screens.Queue(this));
    }

    void openBrowser(String url) {
        showBrowser();
        if (url != null && !url.isEmpty()) web.loadUrl(url);
    }

    void openSearchWith(String query) {
        selectTab(TAB_SEARCH, true);
        if (searchScreen != null) searchScreen.search(query);
    }

    void openNowPlaying() {
        if (nowPlayingScreen == null || !nowPlayingScreen.view().isAttachedToWindow()) {
            nowPlayingScreen = new Screens.NowPlaying(this);
            nowPlayingScreen.view().setVisibility(View.VISIBLE);
        }
        player.state();
        pushScreen(nowPlayingScreen);
    }

    /** Screens are attached on top of a destination; back pops to the tab. */
    void pushScreen(Screens.Screen screen) {
        hideBrowser();
        View incoming = screen.view();
        if (incoming.getParent() instanceof ViewGroup) ((ViewGroup) incoming.getParent()).removeView(incoming);
        content.removeAllViews();
        content.addView(screen.view(), new FrameLayout.LayoutParams(-1, -1));
        boolean immersive = screen instanceof Screens.NowPlaying;
        if (immersive) {
            screen.view().setTranslationY(Ui.dp(this, 160));
            screen.view().setAlpha(0f);
            screen.view().animate().alpha(1f).translationY(0).setDuration(240)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        } else {
            screen.view().setAlpha(0f);
            screen.view().animate().alpha(1f).translationY(0).setDuration(180).start();
        }
        if (navBar != null) navBar.setVisibility(immersive ? View.GONE : View.VISIBLE);
        if (mini != null) mini.setVisible(!immersive && !(screen instanceof Screens.Queue));
        refreshSelection();
    }

    void popScreen() {
        pushed = null;
        if (mini != null) mini.setVisible(true);
        if (tab < 0) tab = TAB_HOME;
        selectTab(tab, false);
    }

    // -------------------------------------------------------- web browser
    @SuppressWarnings("SetJavaScriptEnabled")
    private void ensureBrowser() {
        if (web != null) return;
        browserHost = new FrameLayout(this);
        browserHost.setBackgroundColor(Ui.BG);
        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
        browserBar = bar;
        ImageView back = Widgets.iconButton(this, Glyph.BACK, Ui.TEXT2, 22);
        back.setContentDescription("Voltar no navegador");
        back.setOnClickListener(v -> { if (web.canGoBack()) web.goBack(); });
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        browserTitle = new TextView(this);
        browserTitle.setTextColor(Ui.TEXT);
        browserTitle.setTextSize(13);
        browserTitle.setMaxLines(1);
        browserTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bar.addView(browserTitle, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView playBg = Widgets.iconButton(this, Glyph.PLAY, Ui.CYAN, 20);
        playBg.setContentDescription("Ouvir com tela apagada");
        playBg.setOnClickListener(Widgets.debounced(v -> playCurrentVideoInBackground()));
        bar.addView(playBg, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        ImageView save = Widgets.iconButton(this, Glyph.PLUS, Ui.CYAN, 20);
        save.setContentDescription("Salvar vídeo atual");
        save.setOnClickListener(Widgets.debounced(v -> saveCurrentVideo()));
        bar.addView(save, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        ImageView close = Widgets.iconButton(this, Glyph.CLOSE, Ui.TEXT2, 20);
        close.setContentDescription("Fechar navegador");
        close.setOnClickListener(v -> { hideBrowser(); });
        bar.addView(close, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        browserHost.addView(bar, new FrameLayout.LayoutParams(-1, -2));

        browserProgress = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        browserProgress.setMax(100);
        browserHost.addView(browserProgress, new FrameLayout.LayoutParams(-1, Ui.dp(this, 2)));

        web = new android.webkit.WebView(this);
        web.setBackgroundColor(Ui.BG);
        android.webkit.WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (android.os.Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        settings.setSupportMultipleWindows(false);
        settings.setGeolocationEnabled(false);
        settings.setSaveFormData(false);
        settings.setDatabaseEnabled(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setAllowFileAccessFromFileURLs(false);
        if (android.os.Build.VERSION.SDK_INT >= 33) settings.setAlgorithmicDarkeningAllowed(true);
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new android.webkit.WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(android.webkit.WebView view, android.webkit.WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                String url = request.getUrl().toString();
                if (UrlPolicy.youtube(url)) return false;
                if (!UrlPolicy.host(url).isEmpty()) {
                    new AlertDialog.Builder(MainActivity.this).setTitle("Abrir fora do Onda?")
                            .setMessage(UrlPolicy.host(url))
                            .setPositiveButton("Abrir", (d, w) -> Widgets.external(MainActivity.this, url))
                            .setNegativeButton("Cancelar", null).show();
                } else Widgets.toast(MainActivity.this, "Este link não pode ser aberto no player.");
                return true;
            }

            @Override public void onPageStarted(android.webkit.WebView view, String url, android.graphics.Bitmap icon) {
                browserProgress.setVisibility(View.VISIBLE);
                browserTitle.setText(UrlPolicy.host(url));
            }

            @Override public void onPageFinished(android.webkit.WebView view, String url) {
                browserTitle.setText(view.getTitle() == null ? "YouTube" : view.getTitle());
            }

            @Override public void onReceivedSslError(android.webkit.WebView view,
                                                     android.webkit.SslErrorHandler handler,
                                                     android.net.http.SslError error) {
                // Never proceed past a certificate failure.
                handler.cancel();
            }

            @Override public void onReceivedHttpAuthRequest(android.webkit.WebView view,
                                                            android.webkit.HttpAuthHandler handler,
                                                            String host, String realm) {
                handler.cancel();
            }
        });
        web.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override public void onProgressChanged(android.webkit.WebView view, int value) {
                browserProgress.setProgress(value);
                browserProgress.setVisibility(value >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override public void onReceivedTitle(android.webkit.WebView view, String title) {
                if (title != null && !title.trim().isEmpty()) browserTitle.setText(title);
            }

            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) { callback.onCustomViewHidden(); return; }
                customView = view;
                fullscreenCallback = callback;
                root.setVisibility(View.GONE);
                ((FrameLayout) view.getParent() == null ? content : (ViewGroup) view.getParent()).removeView(view);
                content.addView(view, new FrameLayout.LayoutParams(-1, -1));
                getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }

            @Override public void onHideCustomView() { leaveFullscreen(); }
        });
        FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(-1, -1);
        webParams.topMargin = Ui.dp(this, 54);
        browserHost.addView(web, webParams);
        content.addView(browserHost, new FrameLayout.LayoutParams(-1, -1));
    }

    private void showBrowser() {
        ensureBrowser();
        browserVisible = true;
        content.removeAllViews();
        content.addView(browserHost, new FrameLayout.LayoutParams(-1, -1));
        web.onResume();
        web.requestFocus();
        refreshSelection();
        if (!browserLoaded) {
            browserLoaded = true;
            web.loadUrl("https://m.youtube.com");
        }
    }

    private void hideBrowser() {
        if (!browserVisible) return;
        browserVisible = false;
        web.onPause();
        web.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(m){m.pause();});", null);
        content.removeAllViews();
        navBar.setVisibility(View.VISIBLE);
        selectTab(tab, false);
    }

    boolean browserVisible() { return browserVisible; }

    private void leaveFullscreen() {
        if (customView == null) return;
        content.removeView(customView);
        customView = null;
        root.setVisibility(View.VISIBLE);
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (fullscreenCallback != null) { fullscreenCallback.onCustomViewHidden(); fullscreenCallback = null; }
    }

    private void saveCurrentVideo() {
        String url = web == null ? null : UrlPolicy.canonicalVideo(web.getUrl() == null ? "" : web.getUrl());
        if (url == null) { Widgets.toast(this, "Abra um vídeo para salvar."); return; }
        String title = web.getTitle() == null ? "Vídeo do YouTube" : web.getTitle().replaceFirst(" - YouTube$", "");
        LibraryDialogs.save(this, new LibraryStore.Track(title, url), () -> { });
    }

    private void playCurrentVideoInBackground() {
        if (web == null) return;
        String raw = web.getUrl();
        String url = raw == null ? null : UrlPolicy.canonicalVideo(raw);
        if (url == null) {
            Widgets.toast(this, "Abra um vídeo no YouTube para ouvir com a tela apagada.");
            return;
        }
        web.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(m){m.pause();});", null);
        String rawTitle = web.getTitle() == null ? "Vídeo do YouTube" : web.getTitle().replaceFirst(" - YouTube$", "").trim();
        LibraryStore.Track track = new LibraryStore.Track(rawTitle, url);
        player.playNow(track);
        Widgets.toast(this, "Reproduzindo com tela apagada…");
        openNowPlaying();
    }

    // ------------------------------------------------------------- undo
    static void undo(String message, Runnable restore) {
        if (current != null) current.showUndo(message, restore);
    }

    private void showUndo(String message, Runnable restore) {
        if (undoBar != null) root.removeView(undoBar);
        LinearLayout bar = Ui.row(this);
        undoBar = bar;
        bar.setBackground(Ui.surface(this, Ui.S3, 18, false));
        bar.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1, -2);
        barParams.setMargins(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 8));
        TextView text = Ui.meta(this, message);
        text.setTextColor(Ui.TEXT);
        bar.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        TextView action = new TextView(this);
        action.setText("Desfazer");
        action.setTextColor(Ui.CYAN);
        action.setTextSize(13);
        action.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        action.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10));
        action.setOnClickListener(v -> {
            restore.run();
            dismissUndo(bar);
        });
        bar.addView(action);
        root.addView(bar, Math.max(0, root.indexOfChild(miniHost)));
        bar.announceForAccessibility(message + ". Desfazer disponível.");
        bar.postDelayed(() -> dismissUndo(bar), 6000);
    }

    private void dismissUndo(LinearLayout bar) {
        if (bar.getParent() != null) root.removeView(bar);
        if (undoBar == bar) undoBar = null;
    }

    // ------------------------------------------------------ player hooks
    @Override public void onPlayerChanged() {
        if (mini != null) mini.update();
        if (nowPlayingScreen != null && nowPlayingScreen.view().getParent() != null) nowPlayingScreen.refresh();
        if (pushed != null && pushed != nowPlayingScreen && pushed.view().getParent() != null) {
            pushed.refresh();
        } else if (pushed == null) {
            if (tab == TAB_HOME && homeScreen != null) homeScreen.refresh();
            else if (tab == TAB_LIBRARY && libraryScreen != null) libraryScreen.refresh();
        }
    }

    @Override public void onPosition() {
        if (mini != null) mini.updatePosition();
        if (nowPlayingScreen != null && nowPlayingScreen.view().getParent() != null) nowPlayingScreen.refreshPosition();
    }

    // --------------------------------------------------------- lifecycle
    @Override protected void onStart() {
        super.onStart();
        player.addListener(this);
        player.bind();
    }

    @Override protected void onStop() {
        player.removeListener(this);
        player.unbind();
        if (web != null) web.onPause();
        super.onStop();
    }

    @Override protected void onResume() {
        super.onResume();
        if (web != null && browserVisible) web.onResume();
        if (mini != null) mini.update();
    }

    @Override protected void onPause() {
        if (web != null && !browserVisible)
            web.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(m){m.pause();});", null);
        super.onPause();
    }

    @Override public void onBackPressed() {
        if (customView != null) { leaveFullscreen(); return; }
        if (browserVisible) {
            if (web != null && web.canGoBack()) web.goBack();
            else hideBrowser();
            return;
        }
        if (content.getChildCount() > 0 && content.getChildAt(0) != destinations[tab]) {
            if (nowPlayingScreen != null && content.getChildAt(0) == nowPlayingScreen.view()) mini.collapse();
            popScreen();
            return;
        }
        if (!backStack.isEmpty()) {
            int previous = backStack.pop();
            popping = true;
            try { selectTab(previous, false); } finally { popping = false; }
            return;
        }
        if (tab != TAB_HOME) { selectTab(TAB_HOME, false); return; }
        long now = System.currentTimeMillis();
        if (now - lastBack < 2200) super.onBackPressed();
        else { lastBack = now; Widgets.toast(this, "Toque novamente para sair"); }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra("open_player", false)) openNowPlaying();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == LibraryDialogs.EDIT && result == RESULT_OK) {
            homeScreen = null;
            libraryScreen = null;
            exploreScreen = null;
            destinations[TAB_HOME] = null;
            destinations[TAB_EXPLORE] = null;
            destinations[TAB_LIBRARY] = null;
            String name = data == null ? null : data.getStringExtra("name");
            if (name != null) openCollection(name, LibraryStore.playlistKey(name), "");
            else selectTab(tab, false);
        }
    }

    @Override protected void onDestroy() {
        if (current == this) current = null;
        // Named screens hold handles to this activity; drop them so a new
        // activity instance can never be driven by a stale one.
        homeScreen = null;
        exploreScreen = null;
        searchScreen = null;
        libraryScreen = null;
        nowPlayingScreen = null;
        java.util.Arrays.fill(destinations, null);
        if (web != null) {
            if (web.getParent() instanceof ViewGroup) ((ViewGroup) web.getParent()).removeView(web);
            web.stopLoading();
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
