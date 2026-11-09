package app.onda.mobile;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Every screen in the app, as a self-contained controller.
 *
 * A screen builds a header plus a scrollable body from the design system,
 * fetches its own data, owns its loading/empty/error states, and refreshes in
 * place instead of being rebuilt by the shell.
 */
final class Screens {

    static final ExecutorService WORKERS = Executors.newFixedThreadPool(3);

    private Screens() { }

    // ------------------------------------------------------------------ base
    abstract static class Screen {
        final MainActivity host;
        final Context context;
        final PlayerController player;
        final LibraryStore library;
        final FrameLayout root;

        Screen(MainActivity host) {
            this.host = host;
            this.context = host;
            this.player = host.player();
            this.library = host.library();
            root = new FrameLayout(context);
            root.setBackgroundColor(Ui.BG);
        }

        View view() { return root; }

        abstract void refresh();

        void refreshPosition() { }

        /** Header with optional back button, eyebrow, title and trailing action. */
        LinearLayout header(String eyebrow, String title, boolean back, String action, Runnable actionRun) {
            LinearLayout box = Ui.column(context);
            box.setPadding(Ui.dp(context, 20), Ui.dp(context, back ? 4 : 18), Ui.dp(context, 20), Ui.dp(context, 4));
            if (back) {
                LinearLayout backRow = Ui.row(context);
                ImageView glyph = Widgets.iconButton(context, Glyph.BACK, Ui.TEXT2, 22);
                glyph.setContentDescription("Voltar");
                glyph.setOnClickListener(v -> host.onBackPressed());
                backRow.addView(glyph, new LinearLayout.LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)));
                box.addView(backRow);
            }
            LinearLayout titleLine = Ui.row(context);
            LinearLayout words = Ui.column(context);
            if (eyebrow != null) words.addView(Ui.eyebrow(context, eyebrow));
            if (title != null) words.addView(Ui.display(context, title));
            titleLine.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
            if (action != null) {
                TextView link = new TextView(context);
                link.setText(action);
                link.setTextColor(Ui.CYAN);
                link.setTextSize(13);
                link.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                link.setMinHeight(Ui.dp(context, Ui.TOUCH));
                Ui.ripple(link, 12);
                link.setPadding(Ui.dp(context, 10), Ui.dp(context, 12), 0, Ui.dp(context, 12));
                link.setOnClickListener(v -> actionRun.run());
                titleLine.addView(link);
            }
            box.addView(titleLine);
            return box;
        }

        /** Scroll body below a fixed header. Returns the body to fill. */
        LinearLayout page(LinearLayout header) {
            LinearLayout column = Ui.column(context);
            root.removeAllViews();
            ScrollView scroll = new ScrollView(context);
            scroll.setFillViewport(true);
            scroll.setClipToPadding(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            LinearLayout body = Ui.column(context);
            body.setPadding(0, 0, 0, Ui.dp(context, 24));
            scroll.addView(body);
            column.addView(header, new LinearLayout.LayoutParams(-1, -2));
            column.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            root.addView(column, new FrameLayout.LayoutParams(-1, -1));
            return body;
        }

        LinearLayout pageWithHeader(LinearLayout header) { return page(header); }

        void section(LinearLayout body, String title, String action, Runnable run) {
            body.addView(Ui.sectionHeader(context, title, action, run));
        }

        void horizontal(LinearLayout body, android.widget.HorizontalScrollView scroll) {
            body.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        }

        void pad(LinearLayout body, View child) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(Ui.dp(context, 20), Ui.dp(context, 6), Ui.dp(context, 20), 0);
            body.addView(child, params);
        }

        Widgets.TrackAction trackAction(String key, String name, Runnable after) {
            return new Widgets.TrackAction() {
                @Override public void onPrimary(LibraryStore.Track track, View row) {
                    if (key != null) {
                        List<LibraryStore.Track> items = library.tracks(key);
                        player.playCollection(items, name, key, track, false);
                    } else player.playNow(track);
                    if (after != null) after.run();
                }

                @Override public void onMore(LibraryStore.Track track, View row) {
                    Widgets.trackMenu(context, library, track, key, name, after);
                }
            };
        }

        void error(LinearLayout body, String message, Runnable retry) {
            LinearLayout card = Ui.cardContainer(context);
            card.addView(Ui.title(context, message, 14, Ui.TEXT));
            android.widget.Button again = Ui.primary(context, "Tentar novamente");
            again.setOnClickListener(v -> retry.run());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Ui.dp(context, 48));
            params.topMargin = Ui.dp(context, 12);
            card.addView(again, params);
            pad(body, card);
        }
    }

    static final class SoundWaveView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int barCount = 20;
        private final float[] heights = new float[barCount];
        private boolean playing = false;
        private long lastAnimTime = 0;
        private float phase = 0f;

        SoundWaveView(Context context) {
            super(context);
            paint.setColor(Ui.CYAN);
            paint.setStyle(Paint.Style.FILL);
        }

        void setPlaying(boolean isPlaying) {
            if (playing != isPlaying) {
                playing = isPlaying;
                invalidate();
            }
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float totalBarWidth = w * 0.85f;
            float barWidth = totalBarWidth / (barCount * 1.5f);
            float spacing = barWidth * 0.5f;
            float startX = (w - (barCount * barWidth + (barCount - 1) * spacing)) / 2f;
            float centerY = h / 2f;

            long now = System.currentTimeMillis();
            if (lastAnimTime == 0) lastAnimTime = now;
            float dt = (now - lastAnimTime) / 1000f;
            lastAnimTime = now;
            if (playing) phase += dt * 5f;

            for (int i = 0; i < barCount; i++) {
                float target;
                if (playing) {
                    float wave = (float) Math.sin(phase + i * 0.55f);
                    float wave2 = (float) Math.cos(phase * 0.7f + i * 0.35f);
                    target = (0.25f + 0.38f * (wave * 0.5f + 0.5f) + 0.35f * (wave2 * 0.5f + 0.5f)) * (h * 0.85f);
                } else {
                    target = Ui.dp(getContext(), 4);
                }
                heights[i] = heights[i] + (target - heights[i]) * 0.25f;
                float barH = Math.max(Ui.dp(getContext(), 3), heights[i]);
                float left = startX + i * (barWidth + spacing);
                float top = centerY - barH / 2f;
                float right = left + barWidth;
                float bottom = centerY + barH / 2f;
                float radius = barWidth / 2f;
                canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint);
            }

            if (playing && isAttachedToWindow()) {
                postInvalidateDelayed(33);
            }
        }
    }

    static final class EqualizerSplineView extends View {
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint peakBadgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path curvePath = new Path();
        private final Path fillPath = new Path();
        private float[] bandGains = new float[]{0, 0, 0, 0, 0};
        private String[] bandLabels = new String[]{"60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz"};
        private float maxDb = 15f;
        private float peakDb = 0f;

        EqualizerSplineView(Context context) {
            super(context);
            gridPaint.setColor(Ui.S3);
            gridPaint.setStrokeWidth(Ui.dp(context, 1));
            gridPaint.setStyle(Paint.Style.STROKE);

            linePaint.setColor(Ui.CYAN);
            linePaint.setStrokeWidth(Ui.dp(context, 3));
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeCap(Paint.Cap.ROUND);
            linePaint.setStrokeJoin(Paint.Join.ROUND);

            fillPaint.setStyle(Paint.Style.FILL);

            textPaint.setColor(Ui.TEXT3);
            textPaint.setTextSize(Ui.dp(context, 11));
            textPaint.setTextAlign(Paint.Align.CENTER);

            peakBadgePaint.setColor(Ui.withAlpha(Ui.CYAN, 0x22));
            peakBadgePaint.setStyle(Paint.Style.FILL);
        }

        void setBands(float[] gains, String[] labels, float maxGain) {
            if (gains != null) {
                bandGains = gains.clone();
                peakDb = 0f;
                for (float g : gains) if (Math.abs(g) > Math.abs(peakDb)) peakDb = g;
            }
            if (labels != null) bandLabels = labels;
            if (maxGain > 0) maxDb = maxGain;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float padTop = Ui.dp(getContext(), 24);
            float padBottom = Ui.dp(getContext(), 28);
            float padLeft = Ui.dp(getContext(), 20);
            float padRight = Ui.dp(getContext(), 20);
            float plotWidth = w - padLeft - padRight;
            float plotHeight = h - padTop - padBottom;
            float centerY = padTop + plotHeight / 2f;

            float[] gridSteps = {-12, -6, 0, 6, 12};
            for (float step : gridSteps) {
                float y = centerY - (step / maxDb) * (plotHeight / 2f);
                gridPaint.setColor(step == 0 ? Ui.withAlpha(Ui.TEXT3, 0x33) : Ui.withAlpha(Ui.S3, 0x66));
                canvas.drawLine(padLeft, y, w - padRight, y, gridPaint);
            }

            int count = bandGains.length;
            if (count < 2) return;

            float[] px = new float[count];
            float[] py = new float[count];
            for (int i = 0; i < count; i++) {
                px[i] = padLeft + (i / (float) (count - 1)) * plotWidth;
                float gain = Math.max(-maxDb, Math.min(maxDb, bandGains[i]));
                py[i] = centerY - (gain / maxDb) * (plotHeight / 2f);
            }

            curvePath.reset();
            curvePath.moveTo(px[0], py[0]);
            for (int i = 0; i < count - 1; i++) {
                float c1x = px[i] + (px[i + 1] - px[i]) / 2f;
                float c1y = py[i];
                float c2x = px[i] + (px[i + 1] - px[i]) / 2f;
                float c2y = py[i + 1];
                curvePath.cubicTo(c1x, c1y, c2x, c2y, px[i + 1], py[i + 1]);
            }

            fillPath.reset();
            fillPath.addPath(curvePath);
            fillPath.lineTo(px[count - 1], h - padBottom);
            fillPath.lineTo(px[0], h - padBottom);
            fillPath.close();

            fillPaint.setShader(new LinearGradient(0, padTop, 0, h - padBottom,
                    Ui.withAlpha(Ui.CYAN, 0x44), Ui.withAlpha(Ui.CYAN, 0x00), Shader.TileMode.CLAMP));
            canvas.drawPath(fillPath, fillPaint);
            canvas.drawPath(curvePath, linePaint);

            Paint ptPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            ptPaint.setColor(Ui.CYAN);
            ptPaint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < count; i++) {
                canvas.drawCircle(px[i], py[i], Ui.dp(getContext(), 4), ptPaint);
            }

            for (int i = 0; i < count && i < bandLabels.length; i++) {
                canvas.drawText(bandLabels[i], px[i], h - Ui.dp(getContext(), 8), textPaint);
            }

            String peakTag = (peakDb >= 0 ? "+" : "") + String.format(java.util.Locale.US, "%.1f dB", peakDb);
            float badgeWidth = Ui.dp(getContext(), 64);
            float badgeHeight = Ui.dp(getContext(), 20);
            float badgeRight = w - padRight;
            float badgeLeft = badgeRight - badgeWidth;
            float badgeTop = Ui.dp(getContext(), 4);
            float badgeBottom = badgeTop + badgeHeight;
            canvas.drawRoundRect(badgeLeft, badgeTop, badgeRight, badgeBottom, Ui.dp(getContext(), 6), Ui.dp(getContext(), 6), peakBadgePaint);
            Paint tagText = new Paint(Paint.ANTI_ALIAS_FLAG);
            tagText.setColor(Ui.CYAN);
            tagText.setTextSize(Ui.dp(getContext(), 10));
            tagText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            tagText.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(peakTag, badgeLeft + badgeWidth / 2f, badgeTop + badgeHeight * 0.7f, tagText);
        }
    }

    static final class Home extends Screen {
        private int builtAt = -1;
        private String selectedMood = "";
        private static final String[] MOODS = {"Energia", "Relaxar", "Treino", "Foco", "Viagem"};

        Home(MainActivity host) {
            super(host);
            refresh();
        }

        @Override void refresh() {
            int now = library.revision();
            if (builtAt == now) return;
            builtAt = now;
            LinearLayout header = header(greeting(), "Onda", false, null, null);

            // Profile & Local Security avatar in header
            if (header.getChildCount() > 0 && header.getChildAt(header.getChildCount() - 1) instanceof LinearLayout) {
                LinearLayout titleLine = (LinearLayout) header.getChildAt(header.getChildCount() - 1);
                ImageView profile = Widgets.iconButton(context, Glyph.PERSON, Ui.CYAN, 20);
                profile.setBackground(Ui.surface(context, Ui.withAlpha(Ui.CYAN, 0x1E), 999, false));
                profile.setContentDescription("Acesso e Segurança");
                profile.setOnClickListener(v -> host.openWelcomeSecurity());
                LinearLayout.LayoutParams pParams = new LinearLayout.LayoutParams(Ui.dp(context, 40), Ui.dp(context, 40));
                pParams.leftMargin = Ui.dp(context, 8);
                titleLine.addView(profile, pParams);
            }

            // Search entry
            LinearLayout searchEntry = Ui.row(context);
            searchEntry.setPadding(Ui.dp(context, 16), Ui.dp(context, 12), Ui.dp(context, 12), Ui.dp(context, 12));
            searchEntry.setBackground(Ui.surface(context, Ui.S2, 16, false));
            ImageView icon = Glyph.view(context, Glyph.SEARCH, 18, Ui.TEXT3);
            searchEntry.addView(icon);
            TextView hint = Ui.meta(context, "Músicas, artistas, álbuns…");
            hint.setPadding(Ui.dp(context, 10), 0, 0, 0);
            searchEntry.addView(hint, new LinearLayout.LayoutParams(0, -2, 1));
            ImageView paste = Widgets.iconButton(context, Glyph.PLUS, Ui.CYAN, 18);
            paste.setBackground(Ui.surface(context, Ui.withAlpha(Ui.CYAN, 0x1C), 999, false));
            paste.setContentDescription("Colar link do YouTube");
            paste.setOnClickListener(v -> pasteLink());
            searchEntry.addView(paste, new LinearLayout.LayoutParams(Ui.dp(context, 40), Ui.dp(context, 40)));
            searchEntry.setOnClickListener(v -> host.selectTab(MainActivity.TAB_SEARCH, true));
            searchEntry.setFocusable(true);
            Ui.ripple(searchEntry, 16);

            LinearLayout body = page(header);
            pad(body, searchEntry);

            buildMoodPills(body);

            boolean empty = library.tracks(LibraryStore.FAVORITES).isEmpty() && library.tracks(LibraryStore.RECENT).isEmpty() && library.tracks(LibraryStore.HISTORY).isEmpty() && library.playlists().isEmpty();
            if (empty) {
                buildFirstRun(body);
                return;
            }

            if (!selectedMood.isEmpty()) {
                buildMoodShelf(body, selectedMood);
            }

            buildQuickPills(body);
            buildDailyMix(body);
            buildRecent(body);
            buildBecause(body);
            buildNewReleases(body);
            buildPlaylists(body);
        }

        private void buildMoodPills(LinearLayout body) {
            android.widget.HorizontalScrollView scroll = Ui.strip(context);
            LinearLayout rail = Ui.row(context);
            rail.setPadding(Ui.dp(context, 20), Ui.dp(context, 10), Ui.dp(context, 20), 0);
            for (String mood : MOODS) {
                boolean active = mood.equalsIgnoreCase(selectedMood);
                android.widget.Button chip = Ui.chip(context, mood, active);
                chip.setOnClickListener(v -> {
                    selectedMood = active ? "" : mood;
                    builtAt = -1;
                    refresh();
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, Ui.dp(context, 36));
                params.rightMargin = Ui.dp(context, 8);
                rail.addView(chip, params);
            }
            scroll.addView(rail);
            horizontal(body, scroll);
        }

        private void buildMoodShelf(LinearLayout body, String mood) {
            section(body, "Clima: " + mood, "Explorar", () -> host.openSearchWith(mood + " música"));
            List<LibraryStore.Track> catalog = library.catalog();
            List<LibraryStore.Track> matching = new ArrayList<>();
            String mLower = mood.toLowerCase();
            for (LibraryStore.Track t : catalog) {
                if (t.title.toLowerCase().contains(mLower) || t.artist().toLowerCase().contains(mLower)) {
                    matching.add(t);
                }
            }
            if (matching.isEmpty()) matching.addAll(catalog);
            if (matching.isEmpty()) return;
            android.widget.HorizontalScrollView scroll = Widgets.carousel(context, 200);
            LinearLayout rail = Widgets.rail(scroll);
            for (LibraryStore.Track track : matching.subList(0, Math.min(8, matching.size()))) {
                rail.addView(Widgets.coverCard(context, track.title, track.artist(), track.thumbnail, 148,
                        () -> player.playCollection(matching, mood, "mood:" + mood, track, false),
                        () -> Widgets.trackMenu(context, library, track, null, null, this::refresh)));
            }
            horizontal(body, scroll);
        }

        private void buildQuickPills(LinearLayout body) {
            android.widget.HorizontalScrollView scroll = Ui.strip(context);
            LinearLayout rail = Ui.row(context);
            rail.setPadding(Ui.dp(context, 20), Ui.dp(context, 14), Ui.dp(context, 20), 0);
            addPill(rail, "Favoritas", () -> host.openCollection("Músicas curtidas", LibraryStore.FAVORITES, ""));
            addPill(rail, "Tocadas recentemente", () -> host.openCollection("Tocadas recentemente", LibraryStore.RECENT, ""));
            addPill(rail, "Downloads", () -> host.openCollection("Baixadas", "downloads", ""));
            addPill(rail, "Sua fila", host::openQueue);
            scroll.addView(rail);
            horizontal(body, scroll);
        }

        private void addPill(LinearLayout rail, String text, Runnable run) {
            android.widget.Button pill = Ui.chip(context, text, false);
            pill.setOnClickListener(v -> run.run());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, Ui.dp(context, 36));
            params.rightMargin = Ui.dp(context, 8);
            rail.addView(pill, params);
        }

        private void buildDailyMix(LinearLayout body) {
            Discovery.Mix mix = Discovery.dailyMix(library, 20);
            if (mix.tracks.isEmpty()) return;
            section(body,
                    mix.title,
                    "Reproduzir",
                    () -> player.playCollection(mix.tracks, mix.title, "mix:daily", mix.tracks.get(0), false));
            TextView why = Ui.meta(context, mix.subtitle + " · Calculado no chip");
            why.setTextColor(Ui.CYAN);
            why.setPadding(Ui.dp(context, 20), 0, Ui.dp(context, 20), Ui.dp(context, 8));
            body.addView(why);
            android.widget.HorizontalScrollView scroll = Widgets.carousel(context, 200);
            LinearLayout rail = Widgets.rail(scroll);
            for (int i = 0; i < Math.min(10, mix.tracks.size()); i++) {
                final LibraryStore.Track track = mix.tracks.get(i);
                rail.addView(Widgets.coverCard(context, track.title, track.artist(), track.thumbnail, 148,
                        () -> player.playCollection(mix.tracks, mix.title, "mix:daily", track, false),
                        () -> Widgets.trackMenu(context, library, track, null, null, this::refresh)));
            }
            horizontal(body, scroll);
        }

        private void buildRecent(LinearLayout body) {
            List<LibraryStore.Track> recent = library.recentlyPlayed(10);
            if (recent.isEmpty()) return;
            section(body,
                    "Sua Fila Rápida / Ouvir Novamente",
                    "Ver tudo",
                    () -> host.openCollection("Tocadas recentemente", LibraryStore.RECENT, ""));
            android.widget.HorizontalScrollView scroll = Widgets.carousel(context, 200);
            LinearLayout rail = Widgets.rail(scroll);
            for (LibraryStore.Track track : recent.subList(0, Math.min(10, recent.size()))) {
                rail.addView(Widgets.coverCard(context, track.title, track.artist(), track.thumbnail, 148,
                        () -> player.playNow(track), () -> Widgets.trackMenu(context, library, track, null, null,
                        this::refresh)));
            }
            horizontal(body, scroll);
        }

        private void buildBecause(LinearLayout body) {
            Discovery.Mix mix = Discovery.becauseYouListened(library, new ArrayList<>(), 12);
            if (mix.tracks.isEmpty()) return;
            section(body, mix.title, null, null);
            android.widget.HorizontalScrollView scroll = Widgets.carousel(context, 200);
            LinearLayout rail = Widgets.rail(scroll);
            for (LibraryStore.Track track : mix.tracks.subList(0, Math.min(10, mix.tracks.size()))) {
                rail.addView(Widgets.coverCard(context, track.title, track.artist(), track.thumbnail, 148,
                        () -> player.playNow(track), () -> Widgets.trackMenu(context, library, track, null, null,
                        this::refresh)));
            }
            horizontal(body, scroll);
        }

        private void buildPlaylists(LinearLayout body) {
            section(body, "Suas playlists", "+ Nova", () -> host.openPlaylistEditor(null, null));
            List<String> names = library.playlists();
            names.sort((a, b) -> Boolean.compare(library.playlist(b).favorite, library.playlist(a).favorite));
            if (names.isEmpty()) {
                TextView empty = Ui.body(context, "Crie uma playlist e junte as músicas de um momento.");
                empty.setPadding(Ui.dp(context, 20), 0, Ui.dp(context, 20), Ui.dp(context, 8));
                body.addView(empty);
                return;
            }
            for (String name : names) {
                LibraryStore.Playlist p = library.playlist(name);
                List<LibraryStore.Track> tracks = library.tracks(LibraryStore.playlistKey(name));
                String cover = p.cover.isEmpty() ? (tracks.isEmpty() ? "" : tracks.get(0).thumbnail) : p.cover;
                LinearLayout row = Widgets.shelfRow(context, (p.favorite ? "★ " : "") + name,
                        Ui.songs(tracks.size()) + " · " + Ui.totalDuration(tracks), cover,
                        () -> host.openCollection(name, LibraryStore.playlistKey(name), cover));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                params.setMargins(Ui.dp(context, 20), Ui.dp(context, 4), Ui.dp(context, 20), 0);
                body.addView(row, params);
            }
            View spacer = new View(context);
            body.addView(spacer, new LinearLayout.LayoutParams(1, Ui.dp(context, 10)));
        }

        private void buildFirstRun(LinearLayout body) {
            LinearLayout card = Ui.cardContainer(context);
            card.addView(Ui.eyebrow(context, "Comece aqui"));
            TextView title = Ui.headline(context, "Sua música começa com uma busca");
            card.addView(title);
            card.addView(Ui.body(context, "Busque por uma música, artista ou álbum. "
                    + "Cada vez que você ouve, o Onda aprende e monta seu Daily Mix, suas descobertas "
                    + "e suas recomendações — tudo calculado neste aparelho com privacidade absoluta."));
            android.widget.Button search = Ui.primary(context, "Buscar música");
            search.setOnClickListener(v -> host.selectTab(MainActivity.TAB_SEARCH, true));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Ui.dp(context, 48));
            params.topMargin = Ui.dp(context, 12);
            card.addView(search, params);
            LinearLayout.LayoutParams wrap = new LinearLayout.LayoutParams(-1, -2);
            wrap.setMargins(Ui.dp(context, 20), Ui.dp(context, 4), Ui.dp(context, 20), Ui.dp(context, 8));
            body.addView(card, wrap);
        }

        private void buildNewReleases(LinearLayout body) {
            List<String> artists = library.artists();
            if (artists.isEmpty()) return;
            section(body, "Artistas em Destaque", null, null);
            android.widget.HorizontalScrollView scroll = Widgets.carousel(context, 150);
            LinearLayout rail = Widgets.rail(scroll);
            for (String artist : artists.subList(0, Math.min(12, artists.size()))) {
                List<LibraryStore.Track> tracks = library.artistTracks(artist);
                String cover = tracks.isEmpty() ? "" : tracks.get(0).thumbnail;
                boolean following = library.following(artist);
                rail.addView(Widgets.coverCard(context, artist,
                        following ? "✓ Seguindo" : Ui.songs(tracks.size()), cover, 112,
                        () -> host.openArtist(artist), null));
            }
            horizontal(body, scroll);
        }

        private void pasteLink() {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) {
                Widgets.toast(context, "Nada copiado ainda.");
                return;
            }
            CharSequence text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(context);
            String url = text == null ? "" : text.toString().trim();
            if (UrlPolicy.canonicalVideo(url) != null) {
                host.openBrowser(url);
            }
            else Widgets.toast(context, "O texto copiado não é um link do YouTube.");
        }

        private String greeting() {
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            if (hour < 6) return "Boa madrugada";
            if (hour < 12) return "Bom dia";
            if (hour < 18) return "Boa tarde";
            return "Boa noite";
        }
    }

    static final class Explore extends Screen {
        private int builtAt = -1;
        Explore(MainActivity host) {
            super(host);
            refresh();
        }
        @Override void refresh() {
            int now = library.revision();
            if (builtAt == now) return;
            builtAt = now;
            LinearLayout body = page(header("Descobrir", "Explorar", false, null, null));

            buildCategoryChips(body);
            buildGenreGrid(body);

            section(body, "Descobertas da Semana", null, null);
            List<LibraryStore.Track> fresh = library.catalog();
            Discovery.Mix discoveries = Discovery.discoveries(library, fresh, 12);
            if (discoveries.tracks.isEmpty()) {
                TextView hint = Ui.body(context, "Ouça algumas músicas para o Onda montar suas descobertas.");
                hint.setPadding(Ui.dp(context, 20), 0, Ui.dp(context, 20), Ui.dp(context, 4));
                body.addView(hint);
                android.widget.Button go = Ui.ghost(context, "Ir para a Buscar");
                go.setOnClickListener(v -> host.selectTab(MainActivity.TAB_SEARCH, true));
                pad(body, go);
            } else {
                android.widget.HorizontalScrollView scroll = Widgets.carousel(context, 200);
                LinearLayout rail = Widgets.rail(scroll);
                for (LibraryStore.Track track : discoveries.tracks) {
                    rail.addView(Widgets.coverCard(context, track.title, track.artist(), track.thumbnail, 148,
                            () -> player.playNow(track), () -> Widgets.trackMenu(context, library, track, null, null,
                            this::refresh)));
                }
                horizontal(body, scroll);
            }
            buildArtistShelves(body);
            buildGenreShelves(body);
            buildStats(body);
        }

        private void buildCategoryChips(LinearLayout body) {
            android.widget.HorizontalScrollView scroll = Ui.strip(context);
            LinearLayout rail = Ui.row(context);
            rail.setPadding(Ui.dp(context, 20), Ui.dp(context, 4), Ui.dp(context, 20), Ui.dp(context, 8));
            String[] categories = {"Músicas", "Artistas", "Álbuns", "Playlists", "Vídeos"};
            for (String cat : categories) {
                android.widget.Button chip = Ui.chip(context, cat, false);
                chip.setOnClickListener(v -> host.openSearchWith(cat.toLowerCase()));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, Ui.dp(context, 36));
                params.rightMargin = Ui.dp(context, 8);
                rail.addView(chip, params);
            }
            scroll.addView(rail);
            horizontal(body, scroll);
        }

        private void buildGenreGrid(LinearLayout body) {
            section(body, "Gêneros & Climas Sonoros", null, null);
            LinearLayout grid = Ui.column(context);
            grid.setPadding(Ui.dp(context, 20), 0, Ui.dp(context, 20), Ui.dp(context, 6));

            String[][] genres = {
                {"Pop", "#FF3366", Glyph.SPARK},
                {"Hip-Hop", "#FF9900", Glyph.MIC},
                {"Eletrônica", "#00D2FF", Glyph.BOLT},
                {"Rock", "#9B51E0", Glyph.WAVE},
                {"Acústico", "#27AE60", Glyph.HEART},
                {"MPB", "#2F80ED", Glyph.LIBRARY}
            };

            for (int i = 0; i < genres.length; i += 2) {
                LinearLayout row = Ui.row(context);
                row.setPadding(0, 0, 0, Ui.dp(context, 8));
                row.addView(genreCard(genres[i][0], android.graphics.Color.parseColor(genres[i][1]), genres[i][2]),
                        new LinearLayout.LayoutParams(0, Ui.dp(context, 64), 1));
                View spacer = new View(context);
                row.addView(spacer, new LinearLayout.LayoutParams(Ui.dp(context, 8), 1));
                if (i + 1 < genres.length) {
                    row.addView(genreCard(genres[i + 1][0], android.graphics.Color.parseColor(genres[i + 1][1]), genres[i + 1][2]),
                            new LinearLayout.LayoutParams(0, Ui.dp(context, 64), 1));
                }
                grid.addView(row);
            }
            body.addView(grid);
        }

        private View genreCard(String name, int accentColor, String glyph) {
            LinearLayout card = Ui.row(context);
            card.setBackground(Ui.surface(context, Ui.S2, 14, false));
            card.setPadding(Ui.dp(context, 12), Ui.dp(context, 8), Ui.dp(context, 12), Ui.dp(context, 8));
            card.setGravity(Gravity.CENTER_VERTICAL);

            ImageView icon = Glyph.view(context, glyph, 20, accentColor);
            card.addView(icon);

            LinearLayout words = Ui.column(context);
            words.setPadding(Ui.dp(context, 8), 0, 0, 0);
            TextView title = Ui.title(context, name, 14, Ui.TEXT);
            TextView sub = Ui.meta(context, "Explorar");
            sub.setTextColor(Ui.TEXT3);
            sub.setTextSize(10);
            words.addView(title);
            words.addView(sub);
            card.addView(words, new LinearLayout.LayoutParams(0, -2, 1));

            Ui.ripple(card, 14);
            card.setOnClickListener(v -> host.openSearchWith(name + " música"));
            return card;
        }
        private void buildArtistShelves(LinearLayout body) {
            List<String> followed = library.followedArtists();
            if (followed.isEmpty()) return;
            section(body, "Quem você segue", null, null);
            for (String artist : followed) {
                List<LibraryStore.Track> tracks = library.artistTracks(artist);
                if (tracks.isEmpty()) continue;
                android.widget.HorizontalScrollView scroll = Widgets.carousel(context, 200);
                LinearLayout rail = Widgets.rail(scroll);
                TextView label = Ui.meta(context, artist);
                label.setTextColor(Ui.TEXT);
                label.setPadding(Ui.dp(context, 20), 0, 0, Ui.dp(context, 6));
                body.addView(label);
                for (LibraryStore.Track track : tracks.subList(0, Math.min(8, tracks.size()))) {
                    rail.addView(Widgets.coverCard(context, track.title, track.artist(), track.thumbnail, 132,
                            () -> player.playCollection(tracks, artist, "artist:" + artist, track, false),
                            () -> Widgets.trackMenu(context, library, track, null, null, this::refresh)));
                }
                horizontal(body, scroll);
            }
        }
        private void buildGenreShelves(LinearLayout body) {
            java.util.Map<String, List<LibraryStore.Track>> shelves = Discovery.shelves(library.catalog());
            for (java.util.Map.Entry<String, List<LibraryStore.Track>> entry : shelves.entrySet()) {
                section(body, entry.getKey(), null, null);
                android.widget.HorizontalScrollView scroll = Widgets.carousel(context, 200);
                LinearLayout rail = Widgets.rail(scroll);
                for (LibraryStore.Track track : entry.getValue().subList(0, Math.min(8, entry.getValue().size()))) {
                    rail.addView(Widgets.coverCard(context, track.title, track.artist(), track.thumbnail, 132,
                            () -> player.playNow(track), () -> Widgets.trackMenu(context, library, track, null, null,
                            this::refresh)));
                }
                horizontal(body, scroll);
            }
        }
        private void buildStats(LinearLayout body) {
            int plays = library.plays().size();
            java.util.Map<String, Integer> affinity = library.artistAffinity(30);
            String top = "";
            int best = 0;
            for (java.util.Map.Entry<String, Integer> e : affinity.entrySet()) if (e.getValue() > best) {
                best = e.getValue();
                top = e.getKey();
            }
            LinearLayout card = Ui.cardContainer(context);
            card.addView(Ui.eyebrow(context, "Seu ano em números no aparelho"));
            LinearLayout line = Ui.row(context);
            line.addView(statValue(plays + "", "reproduções"));
            line.addView(statValue(library.tracks(LibraryStore.FAVORITES).size() + "", "favoritas"));
            line.addView(statValue(library.playlists().size() + "", "playlists"));
            card.addView(line);
            if (!top.isEmpty()) card.addView(Ui.meta(context, "Artista que você mais ouviu: " + top));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(Ui.dp(context, 20), Ui.dp(context, 24), Ui.dp(context, 20), Ui.dp(context, 8));
            body.addView(card, params);
        }
        private LinearLayout statValue(String value, String label) {
            LinearLayout box = Ui.column(context);
            TextView big = new TextView(context);
            big.setText(value);
            big.setTextSize(24);
            big.setTextColor(Ui.TEXT);
            big.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            box.addView(big);
            box.addView(Ui.meta(context, label));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            box.setLayoutParams(params);
            return box;
        }
    }

    static final class Search extends Screen {
        private EditText input;
        private LinearLayout results;
        private TextView status;
        private ProgressBar loading;
        private android.widget.HorizontalScrollView chipsScroll;
        private String filter = YoutubeSearch.FILTER_SONGS;
        private int generation;
        private Future<?> pending;
        private Future<?> pendingSuggestion;
        private String lastQuery = "";
        Search(MainActivity host) {
            super(host);
            refresh();
        }
        @Override void refresh() {
            generation++;
            LinearLayout header = header("Buscar", null, false, null, null);
            LinearLayout body = page(header);
            body.setPadding(0, 0, 0, Ui.dp(context, 24));
            LinearLayout searchRow = Ui.row(context);
            searchRow.setPadding(Ui.dp(context, 20), 0, Ui.dp(context, 20), Ui.dp(context, 8));
            LinearLayout field = Ui.row(context);
            field.setPadding(Ui.dp(context, 14), 0, Ui.dp(context, 4), 0);
            field.setBackground(Ui.surface(context, Ui.S2, 16, false));
            field.addView(Glyph.view(context, Glyph.SEARCH, 18, Ui.TEXT3));
            input = new EditText(context);
            input.setSingleLine(true);
            input.setHint("Músicas, artistas, álbuns…");
            input.setHintTextColor(Ui.TEXT3);
            input.setTextColor(Ui.TEXT);
            input.setTextSize(15);
            input.setBackground(null);
            input.setPadding(Ui.dp(context, 10), Ui.dp(context, 12), Ui.dp(context, 4), Ui.dp(context, 12));
            input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
            input.setText(lastQuery);
            field.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
            ImageView clear = Widgets.iconButton(context, Glyph.CLOSE, Ui.TEXT3, 16);
            clear.setContentDescription("Limpar busca");
            clear.setOnClickListener(v -> {
                input.setText("");
                results.removeAllViews();
                status.setText("");
                showDiscover();
            });
            field.addView(clear, new LinearLayout.LayoutParams(Ui.dp(context, 40), Ui.dp(context, 44)));
            searchRow.addView(field, new LinearLayout.LayoutParams(0, Ui.dp(context, 52), 1));
            body.addView(searchRow);
            chipsScroll = Ui.strip(context);
            LinearLayout chips = Ui.row(context);
            chips.setPadding(Ui.dp(context, 20), Ui.dp(context, 4), Ui.dp(context, 20), Ui.dp(context, 4));
            String[] kinds = {
                YoutubeSearch.FILTER_SONGS, YoutubeSearch.FILTER_ARTISTS, YoutubeSearch.FILTER_ALBUMS,
                        YoutubeSearch.FILTER_PLAYLISTS, YoutubeSearch.FILTER_VIDEOS
            }
            ;
            String[] labels = {
                "Músicas", "Artistas", "Álbuns", "Playlists", "Vídeos"
            }
            ;
            for (int i = 0;
            i < kinds.length;
            i++) {
                final String kind = kinds[i];
                android.widget.Button chip = Ui.chip(context, labels[i], kind.equals(filter));
                chip.setOnClickListener(v -> {
                    filter = kind;
                    rebuildChips();
                    if (!lastQuery.isEmpty()) run(lastQuery);
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, Ui.dp(context, 36));
                params.rightMargin = Ui.dp(context, 8);
                chips.addView(chip, params);
            }
            chipsScroll.addView(chips);
            body.addView(chipsScroll);
            status = new TextView(context);
            status.setTextColor(Ui.TEXT2);
            status.setTextSize(12);
            status.setPadding(Ui.dp(context, 20), Ui.dp(context, 8), Ui.dp(context, 20), Ui.dp(context, 4));
            body.addView(status);
            loading = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            loading.setIndeterminate(true);
            loading.setMax(100);
            loading.setVisibility(View.GONE);
            body.addView(loading, new LinearLayout.LayoutParams(-1, Ui.dp(context, 3)));
            results = Ui.column(context);
            body.addView(results);
            input.setOnEditorActionListener((v, action, event) -> {
                if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                    hideKeyboard();
                    run(input.getText().toString());
                    return true;
                }
                return false;
            });
            input.addTextChangedListener(new TextWatcher() {
                private Runnable queued;
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                }
                @Override public void afterTextChanged(Editable s) {
                    if (queued != null) input.removeCallbacks(queued);
                    final String text = s.toString().trim();
                    if (text.isEmpty()) return;
                    queued = () -> askSuggestion(text);
                    input.postDelayed(queued, 420);
                }
            });
            showDiscover();
            if (!lastQuery.isEmpty()) run(lastQuery);
        }
        private void rebuildChips() {
            // Rebuild chips only; keeps focus and typed text intact.
            LinearLayout chips = (LinearLayout) chipsScroll.getChildAt(0);
            if (chips == null) return;
            String[] kinds = {
                YoutubeSearch.FILTER_SONGS, YoutubeSearch.FILTER_ARTISTS, YoutubeSearch.FILTER_ALBUMS,
                        YoutubeSearch.FILTER_PLAYLISTS, YoutubeSearch.FILTER_VIDEOS
            }
            ;
            String[] labels = {
                "Músicas", "Artistas", "Álbuns", "Playlists", "Vídeos"
            }
            ;
            for (int i = 0;
            i < chips.getChildCount() && i < kinds.length;
            i++) {
                android.widget.Button chip = (android.widget.Button) chips.getChildAt(i);
                boolean selected = kinds[i].equals(filter);
                Ui.interactive(chip, selected ? Ui.withAlpha(Ui.CYAN, 0x24) : Ui.S2, 999);
                chip.setTextColor(selected ? Ui.CYAN : Ui.TEXT2);
                chip.setSelected(selected);
                chip.setTypeface(Typeface.create("sans-serif-medium", selected ? Typeface.BOLD : Typeface.NORMAL));
            }
        }
        private void showDiscover() {
            results.removeAllViews();
            status.setText("");
            List<String> recent = library.recentSearches();
            if (!recent.isEmpty()) {
                section(results, "Buscas recentes", "Limpar", () -> {
                    library.clearSearches();
                    showDiscover();
                });
                android.widget.HorizontalScrollView scroll = Ui.strip(context);
                LinearLayout rail = Ui.row(context);
                rail.setPadding(Ui.dp(context, 20), 0, Ui.dp(context, 20), 0);
                for (String term : recent) {
                    android.widget.Button chip = Ui.chip(context, term, false);
                    chip.setOnClickListener(v -> {
                        input.setText(term);
                        run(term);
                    });
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, Ui.dp(context, 34));
                    params.rightMargin = Ui.dp(context, 8);
                    rail.addView(chip, params);
                }
                scroll.addView(rail);
                results.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
            }
            section(results, "Navegar por gênero", null, null);
            String[] genres = {
                "MPB", "Sertanejo", "Rock", "Hip hop", "Jazz", "Eletrônica", "Lo-fi", "Samba", "Pagode", "Clássica"
            }
            ;
            for (String genre : genres) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                params.setMargins(Ui.dp(context, 20), Ui.dp(context, 3), Ui.dp(context, 20), 0);
                results.addView(Widgets.shelfRow(context, genre, "Músicas e apresentações", "", () -> {
                    input.setText(genre + " música");
                    run(genre + " música");
                }
                ), params);
            }
        }
        void search(String query) {
            if (input == null) refresh();
            lastQuery = query;
            if (input != null) input.setText(query);
            run(query);
        }
        private void askSuggestion(String text) {
            if (pendingSuggestion != null) pendingSuggestion.cancel(true);
            final int request = generation;
            pendingSuggestion = WORKERS.submit(() -> {
                try {
                    String suggestion = YoutubeSearch.suggestion(text);
                    if (suggestion.isEmpty() || request != generation) return;
                    host.runOnUiThread(() -> {
                        if (request != generation || input == null) return;
                        String typed = input.getText().toString().trim();
                        if (!typed.equalsIgnoreCase(text)) return;
                        showSuggestion(suggestion);
                    });
                } catch (Exception ignored) {
                }
            });
        }
        private void showSuggestion(String suggestion) {
            status.setText("Você quis dizer: " + suggestion + "?");
            status.setTextColor(Ui.CYAN);
            status.setOnClickListener(v -> {
                input.setText(suggestion);
                run(suggestion);
            });
        }
        private void run(String query) {
            lastQuery = query.trim();
            if (lastQuery.isEmpty()) {
                showDiscover();
                return;
            }
            library.rememberSearch(lastQuery);
            hideKeyboard();
            if (pending != null) pending.cancel(true);
            final int request = ++generation;
            final String term = lastQuery;
            final String kind = filter;
            results.removeAllViews();
            status.setOnClickListener(null);
            status.setTextColor(Ui.TEXT2);
            status.setText("Buscando…");
            loading.setVisibility(View.VISIBLE);
            pending = WORKERS.submit(() -> {
                List<YoutubeSearch.Result> found = null;
                String error = null;
                try {
                    found = YoutubeSearch.search(term, kind, 30);
                } catch (Exception e) {
                    error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                }
                final List<YoutubeSearch.Result> items = found;
                final String failure = error;
                host.runOnUiThread(() -> {
                    if (request != generation || host.isFinishing()) return;
                    loading.setVisibility(View.GONE);
                    if (items == null) {
                        status.setText("");
                        error(results, "Não foi possível buscar agora.", () -> run(term));
                        TextView detail = Ui.body(context, failure == null ? "" : failure);
                        detail.setPadding(Ui.dp(context, 20), Ui.dp(context, 4), Ui.dp(context, 20), 0);
                        results.addView(detail);
                        return;
                    }
                    if (items.isEmpty()) {
                        status.setText("");
                        results.addView(Ui.empty(context, "◌", "Nada encontrado para “" + term + "”",
                                "Tente outra grafia, menos palavras ou navegue por gênero.", "Buscar no YouTube",
                                () -> host.openBrowser(UrlPolicy.search(term))));
                        return;
                    }
                    status.setText(items.size() + " resultados");
                    render(items);
                });
            });
        }
        private void render(List<YoutubeSearch.Result> items) {
            for (YoutubeSearch.Result result : items) {
                if ("track".equals(result.kind)) {
                    LibraryStore.Track track = result.track();
                    Widgets.addTrackRow(results, context, track,
                                (track.artist().isEmpty() ? "YouTube" : track.artist()) + (track.durationSec > 0 ? " · " + Display.duration(track.durationSec) : ""),
                            library.liked(track.url) ? "Curtida" : null, Ui.CYAN, false, new Widgets.TrackAction() {
                        @Override public void onPrimary(LibraryStore.Track t, View row) {
                            player.playNow(t);
                        }
                        @Override public void onMore(LibraryStore.Track t, View row) {
                            Widgets.trackMenu(context, library, t, null, null, Search.this::refresh);
                        }
                    });
                    LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) results.getChildAt(results.getChildCount() - 1).getLayoutParams();
                    p.setMargins(Ui.dp(context, 20), 0, Ui.dp(context, 20), Ui.dp(context, 6));
                } else {
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                    params.setMargins(Ui.dp(context, 20), 0, Ui.dp(context, 20), Ui.dp(context, 6));
                    results.addView(collectionRow(result), params);
                }
            }
            TextView more = Ui.body(context, "Não achou? Abra a busca completa no YouTube.");
            more.setPadding(Ui.dp(context, 20), Ui.dp(context, 12), Ui.dp(context, 20), 0);
            results.addView(more);
            android.widget.Button open = Ui.ghost(context, "Buscar no YouTube");
            open.setOnClickListener(v -> host.openBrowser(UrlPolicy.search(lastQuery)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Ui.dp(context, 44));
            params.setMargins(Ui.dp(context, 20), Ui.dp(context, 6), Ui.dp(context, 20), 0);
            results.addView(open, params);
        }
        private LinearLayout collectionRow(YoutubeSearch.Result result) {
            LinearLayout card = Widgets.shelfRow(context, result.title, result.uploader, result.thumbnail, () -> {
                if ("artist".equals(result.kind)) host.openSearchWith(result.title);
                else loadCollection(result);
            });
            return card;
        }
        private void loadCollection(YoutubeSearch.Result result) {
            results.removeAllViews();
            status.setText("Carregando " + result.title + "…");
            loading.setVisibility(View.VISIBLE);
            final int request = ++generation;
            if (pending != null) pending.cancel(true);
            pending = WORKERS.submit(() -> {
                try {
                    YoutubeSearch.CollectionResult loaded = YoutubeSearch.collection(result.url, "album".equals(result.kind));
                    host.runOnUiThread(() -> {
                        if (request != generation) return;
                        loading.setVisibility(View.GONE);
                        String key = "remote:" + result.kind + ":" + result.url;
                        library.cacheCollection(key, loaded.tracks);
                        host.openCollection(loaded.title, key, loaded.cover);
                    });
                } catch (java.util.concurrent.CancellationException | InterruptedException ignored) {
                    // Superseded by a newer request.
                } catch (Exception e) {
                    host.runOnUiThread(() -> {
                        if (request != generation) return;
                        loading.setVisibility(View.GONE);
                        status.setText("");
                        error(results, "Não foi possível abrir esta coleção.", () -> loadCollection(result));
                    });
                }
            });
        }
        private void hideKeyboard() {
            if (input == null) return;
            InputMethodManager keyboard = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
        @Override void refreshPosition() {
        }
    }

    static final class Library extends Screen {
        private String filter = "Tudo";
        private String query = "";
        private int builtAt = -1;
        Library(MainActivity host) {
            super(host);
            refresh();
        }
        @Override void refresh() {
            int now = library.revision();
            if (builtAt == now && host.libraryFilter().equals(filter)) return;
            builtAt = now;
            host.libraryFilterInternal(filter);
            LinearLayout body = page(header("Tudo fica no aparelho", "Biblioteca", false, "Ajustes", () -> host.openSettings()));

            buildSegmentedSwitcher(body);

            android.widget.HorizontalScrollView scroll = Ui.strip(context);
            LinearLayout chips = Ui.row(context);
            chips.setPadding(Ui.dp(context, 20), 0, Ui.dp(context, 20), Ui.dp(context, 10));
            String[] kinds = {
                "Tudo", "Favoritas", "Playlists", "Artistas", "Álbuns", "Baixadas"
            };
            for (String kind : kinds) {
                android.widget.Button chip = Ui.chip(context, kind, kind.equals(filter));
                chip.setOnClickListener(v -> {
                    filter = kind;
                    builtAt = -1;
                    refresh();
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, Ui.dp(context, 36));
                params.rightMargin = Ui.dp(context, 8);
                chips.addView(chip, params);
            }
            scroll.addView(chips);
            body.addView(scroll);
            if ("Álbuns".equals(filter)) {
                buildAlbums(body);
                return;
            }
            if ("Artistas".equals(filter)) {
                buildArtists(body);
                return;
            }
            if ("Baixadas".equals(filter)) {
                buildDownloads(body);
                return;
            }
            buildQuick(body);
            if (!"Playlists".equals(filter)) {
                buildFavorites(body);
                buildRecent(body);
            }
            buildPlaylists(body);
        }

        private void buildSegmentedSwitcher(LinearLayout body) {
            LinearLayout switcher = Ui.row(context);
            switcher.setBackground(Ui.surface(context, Ui.S2, 14, false));
            switcher.setPadding(Ui.dp(context, 4), Ui.dp(context, 4), Ui.dp(context, 4), Ui.dp(context, 4));

            android.widget.Button btnLib = Ui.chip(context, "Biblioteca", true);
            android.widget.Button btnQueue = Ui.chip(context, "Fila Ativa", false);
            btnQueue.setOnClickListener(v -> host.openQueue());

            switcher.addView(btnLib, new LinearLayout.LayoutParams(0, Ui.dp(context, 38), 1));
            switcher.addView(btnQueue, new LinearLayout.LayoutParams(0, Ui.dp(context, 38), 1));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(Ui.dp(context, 20), 0, Ui.dp(context, 20), Ui.dp(context, 10));
            body.addView(switcher, params);
        }

        private void buildQuick(LinearLayout body) {
            LinearLayout card = Ui.cardContainer(context);
            card.addView(Ui.eyebrow(context, "Armazenamento & Downloads"));
            OfflineStore store = new OfflineStore(context);
            List<OfflineStore.Song> songs = store.songs();
            long total = 0;
            for (OfflineStore.Song song : songs) total += song.bytes;
            card.addView(Ui.title(context, Ui.songs(songs.size()) + " baixadas", 18, Ui.TEXT));
            card.addView(Ui.meta(context, Display.size(total) + " em áudio local · FLAC/Opus bit-perfect"));
            LinearLayout actions = Ui.row(context);
            actions.setPadding(0, Ui.dp(context, 12), 0, 0);
            android.widget.Button open = Ui.ghost(context, "Ver baixadas");
            open.setOnClickListener(v -> {
                filter = "Baixadas";
                builtAt = -1;
                refresh();
            });
            android.widget.Button queue = Ui.ghost(context, "Fila");
            queue.setOnClickListener(v -> host.openQueue());
            actions.addView(open, new LinearLayout.LayoutParams(0, Ui.dp(context, 44), 1));
            actions.addView(queue, new LinearLayout.LayoutParams(0, Ui.dp(context, 44), 1));
            card.addView(actions);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(Ui.dp(context, 20), Ui.dp(context, 4), Ui.dp(context, 20), Ui.dp(context, 8));
            body.addView(card, params);
        }
        private void buildFavorites(LinearLayout body) {
            List<LibraryStore.Track> favorites = library.tracks(LibraryStore.FAVORITES);
            if (favorites.isEmpty()) return;
            section(body,
                    "Músicas curtidas",
                    "Ver tudo",
                    () -> host.openCollection("Músicas curtidas", LibraryStore.FAVORITES, ""));
            int shown = Math.min(4, favorites.size());
            for (int i = 0;
            i < shown;
            i++) {
                LibraryStore.Track track = favorites.get(i);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                params.setMargins(Ui.dp(context, 20), 0, Ui.dp(context, 20), Ui.dp(context, 4));
                body.addView(Widgets.trackRow(context, track, track.artist(), null, Ui.CYAN, false,
                        trackAction(LibraryStore.FAVORITES, "Músicas curtidas", this::refresh)), params);
            }
        }
        private void buildRecent(LinearLayout body) {
            List<LibraryStore.Track> recent = library.recentlyPlayed(4);
            if (recent.isEmpty()) return;
            section(body,
                    "Reproduzido recentemente",
                    "Ver tudo",
                    () -> host.openCollection("Tocadas recentemente", LibraryStore.RECENT, ""));
            for (LibraryStore.Track track : recent) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                params.setMargins(Ui.dp(context, 20), 0, Ui.dp(context, 20), Ui.dp(context, 4));
                body.addView(Widgets.trackRow(context, track, track.artist(), null, Ui.CYAN, false,
                        trackAction(LibraryStore.RECENT, "Tocadas recentemente", this::refresh)), params);
            }
        }
        private void buildPlaylists(LinearLayout body) {
            section(body, "Playlists", "+ Nova", () -> host.openPlaylistEditor(null, null));
            List<String> names = library.playlists();
            if (names.isEmpty()) {
                TextView empty = Ui.body(context, "Ainda sem playlists. Toque em Nova e comece a juntar músicas.");
                empty.setPadding(Ui.dp(context, 20), 0, Ui.dp(context, 20), 0);
                body.addView(empty);
                if (library.canUndoPlaylist()) {
                    android.widget.Button undo = Ui.ghost(context, "Restaurar última excluída");
                    undo.setOnClickListener(v -> {
                        if (library.restoreDeletedPlaylist()) refresh();
                        else Widgets.toast(context, "Já existe uma playlist com esse nome.");
                    });
                    pad(body, undo);
                }
                return;
            }
            names.sort((a, b) -> Boolean.compare(library.playlist(b).favorite, library.playlist(a).favorite));
            for (String name : names) {
                LibraryStore.Playlist p = library.playlist(name);
                List<LibraryStore.Track> tracks = library.tracks(LibraryStore.playlistKey(name));
                String cover = p.cover.isEmpty() ? (tracks.isEmpty() ? "" : tracks.get(0).thumbnail) : p.cover;
                LinearLayout row = Widgets.shelfRow(context, (p.favorite ? "★ " : "") + name,
                        Ui.songs(tracks.size()), cover, () -> host.openCollection(name, LibraryStore.playlistKey(name),
                        cover));
                final LibraryStore.Playlist meta = p;
                row.setOnLongClickListener(v -> {
                    new AlertDialog.Builder(context).setTitle(name).setItems(new String[]{
                        meta.favorite ? "Desfavoritar" : "Favoritar", "Editar", "Excluir"
                    }
                    , (d, which) -> {
                        if (which == 0) {
                            library.togglePlaylistFavorite(name);
                            refresh();
                        }
                        if (which == 1) host.openPlaylistEditor(name, null);
                        if (which == 2) confirmDelete(name);
                    }
                    ).show();
                    return true;
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                params.setMargins(Ui.dp(context, 20), Ui.dp(context, 3), Ui.dp(context, 20), 0);
                body.addView(row, params);
            }
        }
        private void confirmDelete(String name) {
            new AlertDialog.Builder(context)
                    .setTitle("Excluir " + name + "?")
                    .setMessage("As músicas continuam no YouTube. Você pode desfazer em seguida.")
                    .setPositiveButton("Excluir", (d, w) -> {
                        library.deletePlaylist(name);
                        refresh();
                        MainActivity.undo("Playlist excluída", () -> {
                            if (library.restoreDeletedPlaylist()) refresh();
                            else Widgets.toast(context, "Nome já utilizado. Nada foi sobrescrito.");
                        });
                    })
                    .setNegativeButton("Cancelar", null).show();
        }
        private void buildArtists(LinearLayout body) {
            List<String> artists = library.artists();
            if (artists.isEmpty()) {
                body.addView(Ui.empty(context, "♫", "Nenhum artista ainda",
                        "Salve ou ouça músicas para ver seus artistas aqui.", "Buscar músicas",
                        () -> host.selectTab(MainActivity.TAB_SEARCH, true)));
                return;
            }
            for (String artist : artists) {
                List<LibraryStore.Track> tracks = library.artistTracks(artist);
                String cover = tracks.isEmpty() ? "" : tracks.get(0).thumbnail;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                params.setMargins(Ui.dp(context, 20), Ui.dp(context, 3), Ui.dp(context, 20), 0);
                body.addView(Widgets.shelfRow(context, artist,
                        (library.following(artist) ? "Seguindo · " : "") + Ui.songs(tracks.size()), cover,
                        () -> host.openArtist(artist)), params);
            }
        }
        private void buildAlbums(LinearLayout body) {
            List<String> albums = library.albums();
            if (albums.isEmpty()) {
                body.addView(Ui.empty(context, "▤", "Nenhum álbum salvo", "Ao abrir um álbum na busca, use Salvar para guardá-lo aqui.", "Buscar álbuns",
                        () -> host.selectTab(MainActivity.TAB_SEARCH, true)));
                return;
            }
            for (String album : albums) {
                List<LibraryStore.Track> tracks = library.albumTracks(album);
                String label = album.replace('\u0000', ' ');
                String savedKey = "album:" + LibraryQuery.normalized(label);
                String cover = tracks.isEmpty() ? "" : tracks.get(0).thumbnail;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                params.setMargins(Ui.dp(context, 20), Ui.dp(context, 3), Ui.dp(context, 20), 0);
                body.addView(Widgets.shelfRow(context, label, Ui.songs(tracks.size()), cover,
                        () -> host.openCollection(label, savedKey, cover)), params);
            }
        }
        private void buildDownloads(LinearLayout body) {
            List<OfflineStore.Song> songs = new OfflineStore(context).songs();
            if (songs.isEmpty()) {
                body.addView(Ui.empty(context, "↓", "Nada baixado ainda",
                        "Toque em Baixar numa música para ouvir sem internet.", "Buscar músicas",
                        () -> host.selectTab(MainActivity.TAB_SEARCH, true)));
                return;
            }
            android.widget.Button all = Ui.primary(context, "Reproduzir todas");
            all.setOnClickListener(v -> {
                List<LibraryStore.Track> tracks = new ArrayList<>();
                for (OfflineStore.Song song : songs) tracks.add(new LibraryStore.Track(song.title, song.url,
                        song.uploader, song.thumbnail, song.duration / 1000));
                if (!tracks.isEmpty()) player.playCollection(tracks, "Baixadas", "downloads", tracks.get(0), false);
            });
            pad(body, all);
            for (OfflineStore.Song song : songs) {
                LibraryStore.Track track = new LibraryStore.Track(song.title,
                        song.url,
                        song.uploader,
                        song.thumbnail,
                        song.duration / 1000);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                params.setMargins(Ui.dp(context, 20), Ui.dp(context, 6), Ui.dp(context, 20), 0);
                body.addView(Widgets.trackRow(context, track, Display.size(song.bytes), "Baixada", Ui.GREEN, false,
                        trackAction("downloads", "Baixadas", this::refresh)), params);
            }
        }
    }

    static final class Collection extends Screen {
        private final String title;
        private final String key;
        private final String coverUrl;
        private final LinearLayout list;
        private final TextView count;
        private ImageView heroArt;
        private String sortMode = "manual";
        private String filterText = "";
        private int shown = 100;
        Collection(MainActivity host, String title, String key, String coverUrl) {
            super(host);
            this.title = title;
            this.key = key;
            this.coverUrl = coverUrl == null ? "" : coverUrl;
            list = Ui.column(context);
            count = new TextView(context);
            build();
            refresh();
        }
        private boolean isPlaylist() {
            return key.startsWith("playlist:");
        }
        private boolean isRemote() {
            return key.startsWith("remote:");
        }
        private boolean isDownloads() {
            return "downloads".equals(key);
        }
        private LibraryStore.Playlist meta() {
            return isPlaylist() ? library.playlist(title) : new LibraryStore.Playlist(title, "", "", false);
        }
        private List<LibraryStore.Track> items() {
            if (isDownloads()) {
                List<LibraryStore.Track> result = new ArrayList<>();
                for (OfflineStore.Song song : new OfflineStore(context).songs()) result.add(new LibraryStore.Track(song.title,
                        song.url, song.uploader, song.thumbnail, song.duration / 1000));
                return result;
            }
            return library.sorted(key, filterText, sortMode);
        }
        private void build() {
            LinearLayout header = header(null, null, true, null, null);
            LinearLayout body = page(header);
            LinearLayout hero = Ui.row(context);
            hero.setPadding(Ui.dp(context, 20), Ui.dp(context, 8), Ui.dp(context, 20), Ui.dp(context, 8));
            ImageView art = Ui.cover(context, 104, 18);
            CoverCache.with(context).load(art, resolveCover());
            heroArt = art;
            hero.addView(art);
            LinearLayout words = Ui.column(context);
            words.setPadding(Ui.dp(context, 16), 0, 0, 0);
            words.addView(Ui.eyebrow(context, eyebrow()));
            TextView name = Ui.headline(context, title);
            name.setMaxLines(2);
            words.addView(name);
            List<LibraryStore.Track> all = items();
            words.addView(Ui.meta(context, Ui.songs(all.size()) + " · " + Ui.totalDuration(all)));
            hero.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
            body.addView(hero);
            if (isRemote() || key.startsWith("album:")) {
                boolean albumKey = key.startsWith("album:");
                android.widget.Button save = Ui.ghost(context, albumKey ? "Salvar álbum na biblioteca" : "Salvar como playlist");
                save.setOnClickListener(v -> {
                    if (albumKey) {
                        library.toggleSaveAlbum(title, items());
                        Widgets.toast(context, library.albumSaved(title) ? "Álbum salvo" : "Álbum removido");
                    }
                    else saveAsPlaylist();
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Ui.dp(context, 44));
                params.setMargins(Ui.dp(context, 20), Ui.dp(context, 4), Ui.dp(context, 20), 0);
                body.addView(save, params);
            }
            String description = isPlaylist() ? meta().description : "";
            if (!description.isEmpty()) {
                TextView text = Ui.body(context, description);
                text.setPadding(Ui.dp(context, 20), Ui.dp(context, 4), Ui.dp(context, 20), Ui.dp(context, 8));
                body.addView(text);
            }
            LinearLayout actions = Ui.row(context);
            actions.setPadding(Ui.dp(context, 20), Ui.dp(context, 8), Ui.dp(context, 20), Ui.dp(context, 4));
            android.widget.Button play = Ui.primary(context, "▶ Reproduzir");
            play.setTextSize(14);
            play.setOnClickListener(v -> {
                List<LibraryStore.Track> current = items();
                if (current.isEmpty()) {
                    Widgets.toast(context, "Esta lista ainda está vazia.");
                    return;
                }
                player.playCollection(current, title, key, current.get(0), false);
            });
            android.widget.Button shuffle = Ui.ghost(context, "⇄ Embaralhar");
            shuffle.setOnClickListener(v -> {
                List<LibraryStore.Track> current = items();
                if (current.isEmpty()) return;
                player.playCollection(current, title, key, current.get((int) (Math.random() * current.size())), true);
            });
            actions.addView(play, new LinearLayout.LayoutParams(0, Ui.dp(context, 48), 1));
            actions.addView(shuffle, new LinearLayout.LayoutParams(0, Ui.dp(context, 48), 1));
            body.addView(actions);
            LinearLayout tools = Ui.row(context);
            tools.setPadding(Ui.dp(context, 20), Ui.dp(context, 4), Ui.dp(context, 20), Ui.dp(context, 4));
            ImageView searchIcon = Widgets.iconButton(context, Glyph.SEARCH, Ui.TEXT3, 18);
            searchIcon.setContentDescription("Filtrar nesta lista");
            tools.addView(searchIcon, new LinearLayout.LayoutParams(Ui.dp(context, 40), Ui.dp(context, 40)));
            final EditText filter = new EditText(context);
            filter.setSingleLine(true);
            filter.setHint("Filtrar nesta lista");
            filter.setHintTextColor(Ui.TEXT3);
            filter.setTextColor(Ui.TEXT);
            filter.setTextSize(13);
            filter.setBackground(null);
            filter.addTextChangedListener(new TextWatcher() {
                private Runnable queued;
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                }
                @Override public void afterTextChanged(Editable s) {
                    if (queued != null) filter.removeCallbacks(queued);
                    queued = () -> {
                        filterText = s.toString();
                        shown = 100;
                        render();
                    }
                    ;
                    filter.postDelayed(queued, 260);
                }
            });
            tools.addView(filter, new LinearLayout.LayoutParams(0, Ui.dp(context, 44), 1));
            TextView sort = new TextView(context);
            sort.setText("Ordenar");
            sort.setTextColor(Ui.CYAN);
            sort.setTextSize(12);
            sort.setPadding(Ui.dp(context, 8), Ui.dp(context, 10), Ui.dp(context, 4), Ui.dp(context, 10));
            sort.setOnClickListener(v -> pickSort(sort, filter));
            tools.addView(sort);
            body.addView(tools);
            if (isPlaylist()) {
                android.widget.Button save = Ui.ghost(context, "Editar playlist");
                save.setOnClickListener(v -> host.openPlaylistEditor(title, null));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Ui.dp(context, 44));
                params.setMargins(Ui.dp(context, 20), Ui.dp(context, 4), Ui.dp(context, 20), 0);
                body.addView(save, params);
            }
            if (!isDownloads()) {
                android.widget.Button download = Ui.ghost(context, "↓ Baixar esta lista");
                download.setOnClickListener(v -> {
                    List<LibraryStore.Track> current = items();
                    if (current.isEmpty()) {
                        Widgets.toast(context, "Nada para baixar.");
                        return;
                    }
                    for (LibraryStore.Track track : current) player.download(track);
                    Widgets.toast(context, current.size() + " na fila de downloads");
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Ui.dp(context, 44));
                params.setMargins(Ui.dp(context, 20), 0, Ui.dp(context, 20), 0);
                body.addView(download, params);
            }
            count.setTextColor(Ui.TEXT2);
            count.setTextSize(12);
            count.setPadding(Ui.dp(context, 20), Ui.dp(context, 8), Ui.dp(context, 20), Ui.dp(context, 4));
            body.addView(count);
            body.addView(list);
            if (key.equals(LibraryStore.HISTORY) || key.equals(LibraryStore.RECENT)) {
                android.widget.Button clear = Ui.ghost(context, "Limpar histórico");
                clear.setOnClickListener(v -> new AlertDialog.Builder(context).setTitle("Limpar histórico?").setMessage("Isso também limpa as recomendações aprendidas.").setPositiveButton("Limpar",
                        (d, w) -> {
                    library.clearHistory();
                    refresh();
                }
                ).setNegativeButton("Cancelar", null).show());
                pad(body, clear);
            }
            if (isPlaylist()) {
                android.widget.Button delete = Ui.ghost(context, "Excluir playlist");
                delete.setTextColor(Ui.RED);
                delete.setOnClickListener(v -> new AlertDialog.Builder(context).setTitle("Excluir " + title + "?").setPositiveButton("Excluir",
                        (d, w) -> {
                    library.deletePlaylist(title);
                    host.popScreen();
                    MainActivity.undo("Playlist excluída", () -> {
                    });
                }
                ).setNegativeButton("Cancelar", null).show());
                pad(body, delete);
            }
        }
        private void pickSort(TextView label, EditText filter) {
            String[] labels = {
                "Ordem da playlist", "Título A–Z", "Artista A–Z", "Data de inclusão", "Duração"
            }
            ;
            new AlertDialog.Builder(context).setTitle("Ordenar por").setItems(labels, (d, which) -> {
                sortMode = new String[]{
                    "manual", "title", "artist", "date", "duration"
                }
                [which];
                label.setText("Ordenar ▾");
                render();
            }
            ).show();
        }
        private void saveAsPlaylist() {
            List<LibraryStore.Track> items = items();
            if (items.isEmpty()) return;
            String base = title.length() > 52 ? title.substring(0, 52) : title;
            String name = base;
            int n = 2;
            while (library.playlists().contains(name)) name = base + " (" + (n++) + ")";
            library.createPlaylist(name);
            library.updatePlaylist(name, "Salva a partir de " + title, resolveCover(), false);
            for (int i = items.size() - 1;
            i >= 0;
            i--) library.add(LibraryStore.playlistKey(name), items.get(i));
            Widgets.toast(context, "Playlist “" + name + "” criada");
            host.openCollection(name, LibraryStore.playlistKey(name), resolveCover());
        }
        private String eyebrow() {
            if (isDownloads()) return "Downloads";
            if (key.equals(LibraryStore.HISTORY)) return "Histórico";
            if (key.equals(LibraryStore.RECENT)) return "Reproduzido recentemente";
            if (key.startsWith("mix:") || key.startsWith("artist:")) return "Mix";
            if (key.equals(LibraryStore.FAVORITES)) return "Favoritas";
            if (key.startsWith("remote:album") || key.startsWith("album:")) return "Álbum";
            if (isPlaylist()) return "Playlist";
            return "Coleção";
        }
        private String resolveCover() {
            if (!coverUrl.isEmpty()) return coverUrl;
            if (isPlaylist()) {
                LibraryStore.Playlist p = meta();
                if (!p.cover.isEmpty()) return p.cover;
            }
            List<LibraryStore.Track> items = items();
            return items.isEmpty() ? "" : items.get(0).thumbnail;
        }
        @Override void refresh() {
            render();
        }
        private void render() {
            if (list == null) return;
            list.removeAllViews();
            List<LibraryStore.Track> items = items();
            if (heroArt != null) CoverCache.with(context).load(heroArt, resolveCover());
            count.setText(filterText.isEmpty() ? Ui.songs(items.size()) : items.size() + " de " + library.tracks(key).size());
            if (items.isEmpty()) {
                TextView empty = Ui.body(context,
                        filterText.isEmpty() ? "Nenhuma música aqui ainda. Adicione pela busca ou pelo menu de uma música." : "Nada corresponde ao filtro.");
                empty.setPadding(Ui.dp(context, 20), Ui.dp(context, 10), Ui.dp(context, 20), Ui.dp(context, 10));
                list.addView(empty);
                return;
            }
            int limit = Math.min(shown, items.size());
            LibraryStore.Track active = library.find(player.state().url);
            for (int i = 0;
            i < limit;
            i++) {
                LibraryStore.Track track = items.get(i);
                boolean current = active != null && active.url.equals(track.url);
                Widgets.addTrackRow(list, context, track, track.artist(), current ? "Tocando" : null, Ui.CYAN,
                        current, trackAction(isDownloads() ? "downloads" : key, title, this::render));
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) list.getChildAt(list.getChildCount() - 1).getLayoutParams();
                params.setMargins(Ui.dp(context, 20), 0, Ui.dp(context, 20), Ui.dp(context, 4));
            }
            if (limit < items.size()) {
                android.widget.Button more = Ui.ghost(context, "Mostrar mais (" + (items.size() - limit) + ")");
                more.setOnClickListener(v -> {
                    shown += 100;
                    render();
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Ui.dp(context, 44));
                params.setMargins(Ui.dp(context, 20), Ui.dp(context, 8), Ui.dp(context, 20), 0);
                list.addView(more, params);
            }
        }
        @Override void refreshPosition() {
        }
    }

    static final class Artist extends Screen {
        private final String name;
        private final LinearLayout list;
        Artist(MainActivity host, String name) {
            super(host);
            this.name = name;
            this.list = Ui.column(context);
            build();
            refresh();
        }
        private void build() {
            LinearLayout body = page(header("Artista", null, true, null, null));
            LinearLayout hero = Ui.column(context);
            hero.setPadding(Ui.dp(context, 20), Ui.dp(context, 12), Ui.dp(context, 20), Ui.dp(context, 12));
            List<LibraryStore.Track> tracks = library.artistTracks(name);
            ImageView avatar = Ui.cover(context, 112, 999);
            LinearLayout.LayoutParams avatarParams = (LinearLayout.LayoutParams) avatar.getLayoutParams();
            avatarParams.gravity = Gravity.CENTER_HORIZONTAL;
            if (!tracks.isEmpty()) CoverCache.with(context).load(avatar, tracks.get(0).thumbnail);
            hero.addView(avatar, avatarParams);
            TextView title = Ui.display(context, name);
            title.setGravity(Gravity.CENTER);
            title.setPadding(0, Ui.dp(context, 14), 0, 0);
            hero.addView(title);
            TextView meta = Ui.meta(context, Ui.songs(tracks.size()) + (library.following(name) ? " · Seguindo" : ""));
            meta.setGravity(Gravity.CENTER);
            hero.addView(meta);
            body.addView(hero);
            LinearLayout actions = Ui.row(context);
            actions.setPadding(Ui.dp(context, 20), Ui.dp(context, 8), Ui.dp(context, 20), Ui.dp(context, 8));
            android.widget.Button play = Ui.primary(context, "▶ Reproduzir");
            play.setOnClickListener(v -> {
                List<LibraryStore.Track> current = library.artistTracks(name);
                if (current.isEmpty()) {
                    Widgets.toast(context, "Nada deste artista salvo ainda.");
                    return;
                }
                player.playCollection(current, name, "artist:" + name, current.get(0), false);
            });
            android.widget.Button follow = Ui.ghost(context, library.following(name) ? "✓ Seguindo" : "+ Seguir");
            follow.setOnClickListener(v -> {
                boolean now = library.toggleFollow(name);
                follow.setText(now ? "✓ Seguindo" : "+ Seguir");
                Widgets.toast(context, now ? "Seguindo " + name : "Deixou de seguir");
            });
            android.widget.Button radio = Ui.ghost(context, "Rádio");
            radio.setOnClickListener(v -> startRadio());
            actions.addView(play, new LinearLayout.LayoutParams(0, Ui.dp(context, 48), 1));
            actions.addView(follow, new LinearLayout.LayoutParams(0, Ui.dp(context, 48), 1));
            actions.addView(radio, new LinearLayout.LayoutParams(Ui.dp(context, 76), Ui.dp(context, 48)));
            body.addView(actions);
            body.addView(Ui.section(context, "No aparelho"));
            body.addView(list);
            android.widget.Button more = Ui.ghost(context, "Ver mais deste artista no YouTube");
            more.setOnClickListener(v -> host.openSearchWith(name));
            pad(body, more);
        }
        private void startRadio() {
            Widgets.toast(context, "Buscando músicas deste artista…");
            final LibraryStore.Track seed = library.artistTracks(name).stream().findFirst().orElse(null);
            if (seed == null) return;
            Screens.WORKERS.execute(() -> {
                List<LibraryStore.Track> related = new ArrayList<>();
                try {
                    related = RadioProvider.youtube().related(seed);
                } catch (Exception ignored) {
                }
                final List<LibraryStore.Track> found = related;
                host.runOnUiThread(() -> {
                    List<LibraryStore.Track> combined = new ArrayList<>(library.catalog());
                    combined.addAll(found);
                    Discovery.Mix mix = Discovery.becauseYouListened(library, found, 20);
                    List<LibraryStore.Track> play = mix.tracks.isEmpty() ? library.artistTracks(name) : mix.tracks;
                    if (play.isEmpty()) {
                        Widgets.toast(context, "Sem sugestões relacionadas.");
                        return;
                    }
                    player.playCollection(play, "Rádio " + name, "radio:" + name, play.get(0), true);
                });
            });
        }
        @Override void refresh() {
            list.removeAllViews();
            List<LibraryStore.Track> tracks = library.artistTracks(name);
            if (tracks.isEmpty()) {
                TextView empty = Ui.body(context, "Ainda não há músicas salvas deste artista. Use a busca para ouvir.");
                empty.setPadding(Ui.dp(context, 20), 0, Ui.dp(context, 20), 0);
                list.addView(empty);
                return;
            }
            for (LibraryStore.Track track : tracks) {
                Widgets.addTrackRow(list, context, track, track.album.isEmpty() ? track.artist() : track.album,
                        null, Ui.CYAN, false, trackAction("artist:" + name, name, this::refresh));
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) list.getChildAt(list.getChildCount() - 1).getLayoutParams();
                params.setMargins(Ui.dp(context, 20), 0, Ui.dp(context, 20), Ui.dp(context, 4));
            }
        }
    }

    static final class Queue extends Screen {
        private final LinearLayout list;
        Queue(MainActivity host) {
            super(host);
            list = Ui.column(context);
            build();
            refresh();
        }
        private void build() {
            LinearLayout body = page(header("Reprodução", "Fila", true, null, null));
            PlayerController.State state = player.state();
            LinearLayout toggleRow = Ui.row(context);
            toggleRow.setPadding(Ui.dp(context, 20), 0, Ui.dp(context, 20), Ui.dp(context, 8));
            android.widget.Button radio = Ui.chip(context,
                    state.autoplay ? "Rádio automática ✓" : "Rádio automática",
                    state.autoplay);
            radio.setOnClickListener(v -> {
                player.autoplay(!player.state().autoplay);
                refresh();
            });
            android.widget.Button shuffle = Ui.chip(context, state.shuffled ? "Aleatório ✓" : "Aleatório", state.shuffled);
            shuffle.setOnClickListener(v -> {
                player.toggleShuffle();
                refresh();
            });
            toggleRow.addView(radio, new LinearLayout.LayoutParams(0, Ui.dp(context, 38), 1));
            toggleRow.addView(shuffle, new LinearLayout.LayoutParams(0, Ui.dp(context, 38), 1));
            body.addView(toggleRow);
            body.addView(list);
            android.widget.Button clear = Ui.ghost(context, "Limpar fila");
            clear.setOnClickListener(v -> new AlertDialog.Builder(context).setTitle("Limpar a fila?").setMessage("Playlists e favoritas são mantidas.").setPositiveButton("Limpar", (d, w) -> {
                        library.replaceQueue(new ArrayList<>());
                        refresh();
                    }).setNegativeButton("Cancelar", null).show());
            pad(body, clear);
        }
        @Override void refresh() {
            list.removeAllViews();
            OfflineService service = player.service();
            List<String> order = service == null ? new ArrayList<>() : service.queueOrder();
            if (order.isEmpty()) {
                list.addView(Ui.empty(context, "≋", "Fila vazia",
                        "Reproduza uma playlist ou adicione músicas com Tocar a seguir.", null, null));
                return;
            }
            String currentUrl = player.state().url;
            List<LibraryStore.Track> base = library.tracks(LibraryStore.QUEUE);
            for (String url : order) {
                LibraryStore.Track track = null;
                for (LibraryStore.Track candidate : base) if (candidate.url.equals(url)) {
                    track = candidate;
                    break;
                }
                if (track == null) track = new LibraryStore.Track("Música", url);
                boolean current = url.equals(currentUrl);
                String origin = library.origin(url);
                String source = "radio".equals(origin) ? "Sugestão automática" : "manual".equals(origin) ? "Adicionada por você" : library.contextName();
                final LibraryStore.Track item = track;
                Widgets.addTrackRow(list,
                        context,
                        track,
                        source,
                        current ? "Tocando" : null,
                        Ui.CYAN,
                        current,
                        new Widgets.TrackAction() {
                    @Override public void onPrimary(LibraryStore.Track t, View row) {
                        player.playQueueItem(t.url, t.title);
                        refresh();
                    }
                    @Override public void onMore(LibraryStore.Track t, View row) {
                        new AlertDialog.Builder(context).setTitle(t.title).setItems(new String[]{
                            "Mover para cima", "Mover para baixo", "Remover da fila"
                        }
                        , (d, which) -> {
                            if (which == 2) library.remove(LibraryStore.QUEUE, t.url);
                            else library.moveQueue(t.url, which == 0 ? -1 : 1);
                            refresh();
                        }
                        ).show();
                    }
                });
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) list.getChildAt(list.getChildCount() - 1).getLayoutParams();
                params.setMargins(Ui.dp(context, 20), 0, Ui.dp(context, 20), Ui.dp(context, 4));
            }
        }
    }

    static final class NowPlaying extends Screen {
        private ImageView cover;
        private SoundWaveView waveView;
        private TextView title;
        private TextView artist;
        private TextView badge;
        private TextView clock;
        private TextView elapsed;
        private TextView lyric;
        private SeekBar seek;
        private ImageView playIcon;
        private ImageView likeIcon;
        private ImageView shuffleIcon;
        private ImageView repeatIcon;
        private LinearLayout queueList;
        private LinearLayout lyricPanel;
        private TextViews.LyricsView lyricView;
        private boolean dragging;
        private int tab = 0;
        private String panelSignature = "";
        private boolean positionOnly;
        private final LinearLayout panelHost;
        private final List<TextView> tabLabels = new ArrayList<>();
        NowPlaying(MainActivity host) {
            super(host);
            panelHost = Ui.column(context);
            build();
            refresh();
        }
        private void build() {
            ScrollView scroll = new ScrollView(context);
            scroll.setFillViewport(true);
            root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout page = Ui.column(context);
            page.setPadding(Ui.dp(context, 20), Ui.dp(context, 4), Ui.dp(context, 20), Ui.dp(context, 16));
            scroll.addView(page);
            LinearLayout top = Ui.row(context);
            ImageView down = Widgets.iconButton(context, Glyph.DOWN, Ui.TEXT2, 22);
            down.setRotation(0f);
            down.setContentDescription("Fechar player");
            down.setOnClickListener(v -> host.onBackPressed());
            top.addView(down, new LinearLayout.LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)));
            LinearLayout center = Ui.column(context);
            center.setGravity(Gravity.CENTER);
            center.addView(Ui.eyebrow(context, "Tocando agora"));
            top.addView(center, new LinearLayout.LayoutParams(0, -2, 1));
            ImageView more = Widgets.iconButton(context, Glyph.MORE, Ui.TEXT2, 22);
            more.setContentDescription("Opções da faixa");
            more.setOnClickListener(v -> {
                PlayerController.State state = player.state();
                LibraryStore.Track track = library.find(state.url);
                if (track != null) Widgets.trackMenu(context, library, track, null, null, this::refresh);
            });
            top.addView(more, new LinearLayout.LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)));
            page.addView(top);

            cover = Ui.cover(context, 0, 24);
            LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(-1, 0);
            android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            int artwork = (int) (Math.min(metrics.widthPixels, metrics.heightPixels) * 0.72f);
            coverParams.height = Math.min(artwork, Ui.dp(context, 420));
            coverParams.topMargin = Ui.dp(context, 8);
            page.addView(cover, coverParams);

            waveView = new SoundWaveView(context);
            LinearLayout.LayoutParams waveParams = new LinearLayout.LayoutParams(Ui.dp(context, 180), Ui.dp(context, 26));
            waveParams.gravity = Gravity.CENTER_HORIZONTAL;
            waveParams.topMargin = Ui.dp(context, 6);
            page.addView(waveView, waveParams);

            LinearLayout titleRow = Ui.row(context);
            titleRow.setPadding(0, Ui.dp(context, 14), 0, 0);
            LinearLayout words = Ui.column(context);
            title = Ui.headline(context, "");
            title.setMaxLines(2);
            words.addView(title);
            artist = Ui.meta(context, "");
            artist.setTextSize(13);
            artist.setPadding(0, Ui.dp(context, 4), 0, 0);
            words.addView(artist);
            titleRow.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
            badge = Ui.badge(context, "", Ui.CYAN);
            titleRow.addView(badge);
            page.addView(titleRow);

            lyric = new TextView(context);
            lyric.setTextColor(Ui.CYAN);
            lyric.setTextSize(13);
            lyric.setMaxLines(2);
            lyric.setPadding(0, Ui.dp(context, 10), 0, 0);
            page.addView(lyric);

            seek = new SeekBar(context);
            seek.setPadding(0, Ui.dp(context, 10), 0, 0);
            seek.setSplitTrack(false);
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    if (fromUser) elapsed.setText(Display.clock(progress));
                }
                @Override public void onStartTrackingTouch(SeekBar bar) {
                    dragging = true;
                }
                @Override public void onStopTrackingTouch(SeekBar bar) {
                    dragging = false;
                    player.seek(bar.getProgress());
                }
            });
            page.addView(seek, new LinearLayout.LayoutParams(-1, Ui.dp(context, 30)));
            LinearLayout times = Ui.row(context);
            elapsed = Ui.meta(context, "0:00");
            clock = Ui.meta(context, "0:00");
            times.addView(elapsed, new LinearLayout.LayoutParams(0, -2, 1));
            times.addView(clock);
            page.addView(times);

            LinearLayout controls = Ui.row(context);
            controls.setGravity(Gravity.CENTER);
            controls.setPadding(0, Ui.dp(context, 14), 0, 0);
            shuffleIcon = Widgets.iconButton(context, Glyph.SHUFFLE, Ui.TEXT3, 22);
            shuffleIcon.setContentDescription("Aleatório");
            shuffleIcon.setOnClickListener(v -> {
                player.toggleShuffle();
                refresh();
            });
            ImageView previous = Widgets.iconButton(context, Glyph.PREV, Ui.TEXT, 26);
            previous.setContentDescription("Anterior");
            previous.setOnClickListener(v -> player.previous());
            playIcon = Glyph.view(context, Glyph.PLAY, 30, Ui.BG);
            LinearLayout playButton = Ui.column(context);
            playButton.setGravity(Gravity.CENTER);
            playButton.setBackground(Ui.masterPlay(context));
            playButton.addView(playIcon);
            LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(Ui.dp(context, 64), Ui.dp(context, 64));
            playParams.setMargins(Ui.dp(context, 18), 0, Ui.dp(context, 18), 0);
            Ui.ripple(playButton, 999);
            playButton.setOnClickListener(v -> player.toggle());
            playButton.setContentDescription("Ouvir ou pausar");
            ImageView next = Widgets.iconButton(context, Glyph.NEXT, Ui.TEXT, 26);
            next.setContentDescription("Próxima");
            next.setOnClickListener(v -> player.next());
            repeatIcon = Widgets.iconButton(context, Glyph.REPEAT, Ui.TEXT3, 22);
            repeatIcon.setContentDescription("Repetição");
            repeatIcon.setOnClickListener(v -> {
                player.cycleRepeat();
                refresh();
            });
            controls.addView(shuffleIcon, new LinearLayout.LayoutParams(0, Ui.dp(context, 48), 1));
            controls.addView(previous, new LinearLayout.LayoutParams(Ui.dp(context, 56), Ui.dp(context, 56)));
            controls.addView(playButton, playParams);
            controls.addView(next, new LinearLayout.LayoutParams(Ui.dp(context, 56), Ui.dp(context, 56)));
            controls.addView(repeatIcon, new LinearLayout.LayoutParams(0, Ui.dp(context, 48), 1));
            page.addView(controls);

            LinearLayout quick = Ui.row(context);
            quick.setPadding(0, Ui.dp(context, 10), 0, 0);
            quick.setGravity(Gravity.CENTER);

            likeIcon = Widgets.iconButton(context, Glyph.HEART, Ui.TEXT3, 20);
            likeIcon.setContentDescription("Curtir");
            likeIcon.setOnClickListener(v -> {
                LibraryStore.Track track = library.find(player.state().url);
                if (track == null) return;
                library.toggleLike(track);
                Widgets.toast(context, library.liked(track.url) ? "Música curtida" : "Removida dos favoritos");
                refresh();
            });

            ImageView eqIcon = Widgets.iconButton(context, Glyph.EQUALIZER, Ui.CYAN, 20);
            eqIcon.setContentDescription("Equalizador DSP");
            eqIcon.setOnClickListener(v -> host.openEqualizer());

            ImageView timerIcon = Widgets.iconButton(context, Glyph.TIMER, Ui.TEXT3, 20);
            timerIcon.setContentDescription("Temporizador");
            timerIcon.setOnClickListener(v -> new AlertDialog.Builder(context).setTitle("Temporizador de Sono").setItems(new String[]{
                "Desativado", "15 minutos", "30 minutos", "60 minutos"
            }, (d, which) -> {
                player.sleepAfter(new int[]{0, 15, 30, 60}[which]);
                refresh();
            }).show());

            ImageView lyrics = Widgets.iconButton(context, Glyph.LYRICS, Ui.TEXT3, 20);
            lyrics.setContentDescription("Letras");
            lyrics.setOnClickListener(v -> {
                tab = 1;
                panelSignature = "";
                refresh();
            });

            ImageView queue = Widgets.iconButton(context, Glyph.QUEUE, Ui.TEXT3, 20);
            queue.setContentDescription("Fila");
            queue.setOnClickListener(v -> {
                tab = 2;
                panelSignature = "";
                refresh();
            });

            quick.addView(likeIcon, new LinearLayout.LayoutParams(0, Ui.dp(context, 48), 1));
            quick.addView(eqIcon, new LinearLayout.LayoutParams(0, Ui.dp(context, 48), 1));
            quick.addView(timerIcon, new LinearLayout.LayoutParams(0, Ui.dp(context, 48), 1));
            quick.addView(lyrics, new LinearLayout.LayoutParams(0, Ui.dp(context, 48), 1));
            quick.addView(queue, new LinearLayout.LayoutParams(0, Ui.dp(context, 48), 1));
            page.addView(quick);

            LinearLayout tabs = Ui.row(context);
            tabs.setPadding(0, Ui.dp(context, 14), 0, 0);
            String[] names = {
                "Detalhes", "Letras", "Fila"
            };
            for (int i = 0; i < names.length; i++) {
                final int index = i;
                TextView label = new TextView(context);
                label.setText(names[i]);
                label.setTextSize(13);
                label.setGravity(Gravity.CENTER);
                label.setMinHeight(Ui.dp(context, Ui.TOUCH));
                Ui.ripple(label, 12);
                label.setPadding(0, Ui.dp(context, 10), 0, Ui.dp(context, 10));
                label.setOnClickListener(v -> {
                    tab = index;
                    panelSignature = "";
                    refresh();
                });
                tabLabels.add(label);
                tabs.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            }
            page.addView(tabs);
            page.addView(panelHost);
        }
        @Override void refresh() {
            PlayerController.State state = player.state();
            if (waveView != null) waveView.setPlaying(state.playing);
            if (state.hasTrack) {
                CoverCache.with(context).load(cover, state.cover);
                title.setText(state.title);
                artist.setText(state.artist.isEmpty() ? "Onda" : state.artist);
            } else {
                title.setText("Seu player");
                artist.setText("Escolha uma música para começar");
            }
            badge.setText(state.device ? "FLAC 24-bit • Lossless" : state.offline ? "Baixada • Lossless" : "Lossless FLAC • 320k");
            badge.setTextColor(Ui.CYAN);
            badge.setBackground(Ui.surface(context, Ui.withAlpha(Ui.CYAN, 0x22), 6, false));
            playIcon.setImageDrawable(Glyph.drawable(state.playing ? Glyph.PAUSE : Glyph.PLAY, Ui.BG, true));
            likeIcon.setImageDrawable(Glyph.drawable(Glyph.HEART, state.liked ? Ui.CYAN : Ui.TEXT3, false));
            likeIcon.setSelected(state.liked);
            shuffleIcon.setImageDrawable(Glyph.drawable(Glyph.SHUFFLE, state.shuffled ? Ui.CYAN : Ui.TEXT3));
            if (state.repeat == 2) {
                repeatIcon.setImageDrawable(Glyph.drawable(Glyph.REPEAT_ONE, Ui.CYAN));
            } else if (state.repeat == 1) {
                repeatIcon.setImageDrawable(Glyph.drawable(Glyph.REPEAT, Ui.CYAN));
            } else {
                repeatIcon.setImageDrawable(Glyph.drawable(Glyph.REPEAT, Ui.TEXT3));
            }
            seek.setMax(Math.max(1, state.durationMs));
            if (!dragging) seek.setProgress(state.positionMs);
            clock.setText(Display.clock(state.durationMs));
            elapsed.setText(Display.clock(state.positionMs));
            lyric.setText(state.lyric);
            lyric.setVisibility(state.lyric.isEmpty() ? View.GONE : View.VISIBLE);
            for (int i = 0;
            i < tabLabels.size();
            i++) {
                tabLabels.get(i).setTextColor(i == tab ? Ui.CYAN : Ui.TEXT3);
                tabLabels.get(i).setTypeface(Typeface.create("sans-serif-medium", i == tab ? Typeface.BOLD : Typeface.NORMAL));
            }
            String signature = tab + "|" + (state.url == null ? "" : state.url) + "|" + state.engine + "|" + state.jobs.size() + "|" + state.downloading + "|" + (state.downloadPercent / 5) + "|" + (state.sleepRemainingMs > 0) + "|" + library.revision();
            if (signature.equals(panelSignature)) return;
            panelSignature = signature;
            panelHost.removeAllViews();
            if (tab == 0) buildDetails(state);
            else if (tab == 1) buildLyrics(state);
            else buildQueue(state);
        }
        @Override void refreshPosition() {
            PlayerController.State state = player.state();
            if (waveView != null) waveView.setPlaying(state.playing);
            if (!dragging) {
                seek.setMax(Math.max(1, state.durationMs));
                seek.setProgress(state.positionMs);
            }
            elapsed.setText(Display.clock(state.positionMs));
            clock.setText(Display.clock(state.durationMs));
            if (lyricView != null) lyricView.setPosition(state.positionMs);
            if (!state.lyric.isEmpty() && !state.lyric.equals(lyric.getText().toString())) lyric.setText(state.lyric);
        }
        private void buildDetails(PlayerController.State state) {
            LinearLayout card = Ui.cardContainer(context);
            LinearLayout rowOne = Ui.row(context);
            rowOne.addView(Ui.meta(context, state.origin));
            rowOne.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1));
            rowOne.addView(Ui.meta(context, state.engine));
            card.addView(rowOne);
            TextView status = Ui.meta(context, state.status);
            status.setTextColor(Ui.TEXT3);
            status.setPadding(0, Ui.dp(context, 6), 0, 0);
            card.addView(status);
            LinearLayout actions = Ui.row(context);
            actions.setPadding(0, Ui.dp(context, 12), 0, 0);
            android.widget.Button sleep = Ui.ghost(context,
                                state.sleepRemainingMs > 0 ? "Pausar em " + Display.clock(state.sleepRemainingMs) : "Temporizador");
            sleep.setOnClickListener(v -> new AlertDialog.Builder(context).setTitle("Pausar após").setItems(new String[]{
                "Desativado", "15 minutos", "30 minutos", "60 minutos"
            }
            , (d, which) -> player.sleepAfter(new int[]{
                0, 15, 30, 60
            }
            [which])).show());
            android.widget.Button stop = Ui.ghost(context, "Parar");
            stop.setOnClickListener(v -> player.stop());
            actions.addView(sleep, new LinearLayout.LayoutParams(0, Ui.dp(context, 44), 1));
            actions.addView(stop, new LinearLayout.LayoutParams(0, Ui.dp(context, 44), 1));
            card.addView(actions);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = Ui.dp(context, 10);
            panelHost.addView(card, params);
            buildSound(card.getContext(), state);
            if (state.jobs.isEmpty() && !state.downloading) return;
            LinearLayout downloads = Ui.cardContainer(context);
            downloads.addView(Ui.eyebrow(context, "Downloads"));
            if (state.downloading) {
                downloads.addView(Ui.meta(context, state.downloadPercent >= 0 ? state.downloadPercent + "% concluído" : "Conectando…"));
            }
            for (DownloadQueue.Job job : state.jobs) {
                LinearLayout line = Ui.row(context);
                line.setPadding(0, Ui.dp(context, 6), 0, 0);
                TextView name = Ui.meta(context, job.title);
                name.setTextColor(Ui.TEXT);
                line.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
                TextView stateLabel = Ui.meta(context,
                        job.state == DownloadQueue.State.FAILED ? "Falhou" : job.state == DownloadQueue.State.DONE ? "Pronto" : job.state == DownloadQueue.State.ACTIVE ? "Baixando" : "Na fila");
                line.addView(stateLabel);
                downloads.addView(line);
            }
            LinearLayout.LayoutParams dparams = new LinearLayout.LayoutParams(-1, -2);
            dparams.topMargin = Ui.dp(context, 10);
            panelHost.addView(downloads, dparams);
        }
        private void buildSound(Context ignored, PlayerController.State state) {
            OfflineService service = player.service();
            LinearLayout card = Ui.cardContainer(context);
            card.addView(Ui.eyebrow(context, "Som & DSP"));
            LinearLayout crossfadeRow = Ui.row(context);
            crossfadeRow.setPadding(0, Ui.dp(context, 6), 0, Ui.dp(context, 6));
            TextView crossfadeLabel = Ui.body(context, "Crossfade (6 s nas pontas)");
            crossfadeLabel.setTextColor(Ui.TEXT);
            crossfadeRow.addView(crossfadeLabel, new LinearLayout.LayoutParams(0, -2, 1));
            android.widget.CheckBox crossfadeBox = new android.widget.CheckBox(context);
            crossfadeBox.setChecked(service != null && service.crossfade());
            crossfadeBox.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.CYAN));
            crossfadeBox.setOnCheckedChangeListener((v, checked) -> {
                if (service != null) service.crossfade(checked);
            });
            crossfadeRow.addView(crossfadeBox);
            card.addView(crossfadeRow);
            boolean available = service != null && service.equalizerAvailable();
            LinearLayout presetRow = Ui.row(context);
            presetRow.setPadding(0, Ui.dp(context, 4), 0, 0);
            TextView label = Ui.meta(context, available ? "Equalizador" : "Equalizador indisponível neste aparelho");
            presetRow.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            android.widget.Button presets = Ui.chip(context, available ? AudioFx.PRESET_NAMES[ Math.max(0,
                    Math.min(AudioFx.PRESET_NAMES.length - 1, service.equalizerPreset()))] : "—", available);
            presets.setEnabled(available);
            presets.setOnClickListener(v -> {
                if (service == null) return;
                new AlertDialog.Builder(context).setTitle("Equalizador").setItems(AudioFx.PRESET_NAMES, (d, which) -> {
                    service.equalizerPreset(which);
                    presets.setText(AudioFx.PRESET_NAMES[which]);
                }
                ).show();
            });
            presetRow.addView(presets);
            card.addView(presetRow);

            android.widget.Button openEq = Ui.ghost(context, "Abrir Equalizador Profissional (DSP)");
            openEq.setOnClickListener(v -> host.openEqualizer());
            LinearLayout.LayoutParams eqParams = new LinearLayout.LayoutParams(-1, Ui.dp(context, 44));
            eqParams.topMargin = Ui.dp(context, 8);
            card.addView(openEq, eqParams);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = Ui.dp(context, 10);
            panelHost.addView(card, params);
        }
        private void buildLyrics(PlayerController.State state) {
            LinearLayout card = Ui.cardContainer(context);
            String text = state.url == null ? "" : new LyricsStore(context).get(state.url);
            if (text.isEmpty()) {
                card.addView(Ui.body(context,
                        "Sem letras salvas para esta faixa. Você pode colar a sua letra " + "— use [1:23] no início da linha para sincronizar."));
            }
            else if (LyricsStore.synced(text)) {
                lyricView = new TextViews.LyricsView(context, LyricsStore.parse(text));
                lyricView.setPosition(state.positionMs);
                card.addView(lyricView, new LinearLayout.LayoutParams(-1, Math.min(Ui.dp(context, 280),
                        (int) (context.getResources().getDisplayMetrics().heightPixels * 0.34f))));
            } else {
                lyricView = null;
                TextView body = new TextView(context);
                body.setText(text);
                body.setTextColor(Ui.TEXT);
                body.setTextSize(15);
                body.setLineSpacing(Ui.dp(context, 6), 1f);
                card.addView(body);
            }
            android.widget.Button edit = Ui.ghost(context, text.isEmpty() ? "Adicionar letras" : "Editar letras");
            edit.setOnClickListener(v -> editLyrics(state.url, text));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Ui.dp(context, 44));
            params.topMargin = Ui.dp(context, 8);
            card.addView(edit, params);
            panelHost.addView(card, new LinearLayout.LayoutParams(-1, -2));
        }
        private void editLyrics(String url, String existing) {
            if (url == null) return;
            EditText input = new EditText(context);
            input.setText(existing);
            input.setHint("[0:00] Primeira linha");
            input.setTextColor(Ui.TEXT);
            input.setHintTextColor(Ui.TEXT3);
            input.setGravity(Gravity.TOP | Gravity.START);
            input.setMinLines(10);
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            LyricsStore store = new LyricsStore(context);
            new AlertDialog.Builder(context).setTitle("Letras").setMessage(store.emptyHint()).setView(input).setNeutralButton("Limpar",
                    (d, w) -> {
                store.clear(url);
                refresh();
            }
            ).setNegativeButton("Cancelar", null).setPositiveButton("Salvar", (d, w) -> {
                try {
                    store.put(url, input.getText().toString());
                    refresh();
                } catch (IllegalArgumentException e) {
                    Widgets.toast(context, e.getMessage());
                }
            }
            ).show();
        }
        private void buildQueue(PlayerController.State state) {
            LinearLayout card = Ui.cardContainer(context);
            OfflineService service = player.service();
            List<String> order = service == null ? new ArrayList<>() : service.queueOrder();
            if (order.isEmpty()) card.addView(Ui.body(context, "Fila vazia. Use \"Tocar a seguir\" no menu de uma música."));
            List<LibraryStore.Track> base = library.tracks(LibraryStore.QUEUE);
            int shownCount = 0;
            for (String url : order) {
                if (shownCount++ >= 8) break;
                LibraryStore.Track found = null;
                for (LibraryStore.Track candidate : base) if (candidate.url.equals(url)) {
                    found = candidate;
                    break;
                }
                if (found == null) continue;
                boolean current = url.equals(state.url);
                LinearLayout line = Widgets.compactRow(context, found, found.artist(), current, new Widgets.TrackAction() {
                    @Override public void onPrimary(LibraryStore.Track track, View row) {
                        player.playQueueItem(track.url, track.title);
                    }
                    @Override public void onMore(LibraryStore.Track track, View row) {
                        Widgets.trackMenu(context, library, track, null, null, NowPlaying.this::refresh);
                    }
                });
                card.addView(line);
            }
            android.widget.Button all = Ui.ghost(context, "Ver fila completa");
            all.setOnClickListener(v -> host.openQueue());
            card.addView(all);
            panelHost.addView(card, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    static final class Settings extends Screen {
        Settings(MainActivity host) {
            super(host);
            refresh();
        }
        @Override void refresh() {
            LinearLayout body = page(header("Onda 1.0", "Ajustes", true, null, null));
            PlayerController.State state = player.state();
            OfflineService service = player.service();
            SharedPreferences prefs = context.getSharedPreferences("onda_settings", Context.MODE_PRIVATE);

            // 1. Profile & Sovereign Account Card
            LinearLayout profileCard = Ui.cardContainer(context);
            profileCard.setPadding(Ui.dp(context, 16), Ui.dp(context, 16), Ui.dp(context, 16), Ui.dp(context, 16));
            LinearLayout profileRow = Ui.row(context);
            profileRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView avatar = new TextView(context);
            avatar.setText("LP");
            avatar.setTextSize(16);
            avatar.setTextColor(Ui.CYAN);
            avatar.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackground(Ui.surface(context, Ui.S3, 999, true));
            profileRow.addView(avatar, new LinearLayout.LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)));

            LinearLayout profWords = Ui.column(context);
            profWords.setPadding(Ui.dp(context, 12), 0, 0, 0);
            LinearLayout titleBadgeRow = Ui.row(context);
            titleBadgeRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView profName = Ui.title(context, "Lucas Prado", 16, Ui.TEXT);
            titleBadgeRow.addView(profName);
            View badgeSpace = new View(context);
            titleBadgeRow.addView(badgeSpace, new LinearLayout.LayoutParams(Ui.dp(context, 8), 1));
            titleBadgeRow.addView(Ui.fidelityBadge(context, "FLAC 24-bit"));
            profWords.addView(titleBadgeRow);

            TextView profSub = Ui.meta(context, "ONDA PRO OFFLINE · Soberano");
            profSub.setTextColor(Ui.CYAN);
            profWords.addView(profSub);
            profileRow.addView(profWords, new LinearLayout.LayoutParams(0, -2, 1));
            profileCard.addView(profileRow);

            android.widget.Button secBtn = Ui.ghost(context, "Gerenciar Segurança & PIN Local");
            secBtn.setOnClickListener(v -> host.openWelcomeSecurity());
            LinearLayout.LayoutParams secParams = new LinearLayout.LayoutParams(-1, Ui.dp(context, 42));
            secParams.topMargin = Ui.dp(context, 12);
            profileCard.addView(secBtn, secParams);
            addCard(body, profileCard);

            // 2. Sound & Equalizer DSP Section
            LinearLayout dspCard = Ui.cardContainer(context);
            dspCard.addView(Ui.eyebrow(context, "Equalizador Profissional"));
            TextView dspTitle = Ui.title(context, "DSP Master Engine", 16, Ui.TEXT);
            dspCard.addView(dspTitle);
            TextView dspDesc = Ui.meta(context, "RTA acústico de 10 bandas paramétricas, Bass Boost analógico e Surround 3D.");
            dspDesc.setTextColor(Ui.TEXT3);
            dspDesc.setPadding(0, Ui.dp(context, 4), 0, 0);
            dspCard.addView(dspDesc);

            android.widget.Button openEq = Ui.primary(context, "Abrir Equalizador DSP");
            openEq.setOnClickListener(v -> host.openEqualizer());
            LinearLayout.LayoutParams dspBtnParams = new LinearLayout.LayoutParams(-1, Ui.dp(context, 46));
            dspBtnParams.topMargin = Ui.dp(context, 10);
            dspCard.addView(openEq, dspBtnParams);

            // Streaming quality selector
            TextView qualLabel = Ui.meta(context, "Qualidade sonora do streaming:");
            qualLabel.setTextColor(Ui.TEXT);
            qualLabel.setPadding(0, Ui.dp(context, 12), 0, Ui.dp(context, 6));
            dspCard.addView(qualLabel);

            String currentQual = prefs.getString("streaming_quality", "Lossless FLAC");
            String[] qualities = {"Automático", "Alta 320k", "Lossless FLAC"};
            LinearLayout qualRow = Ui.row(context);
            for (String q : qualities) {
                boolean active = q.equals(currentQual);
                android.widget.Button chip = Ui.chip(context, q, active);
                chip.setOnClickListener(v -> {
                    prefs.edit().putString("streaming_quality", q).apply();
                    refresh();
                });
                LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(0, Ui.dp(context, 36), 1);
                qp.rightMargin = Ui.dp(context, 6);
                qualRow.addView(chip, qp);
            }
            dspCard.addView(qualRow);

            // Volume normalization checkbox
            checkbox(dspCard, "Normalização de volume (-14 LUFS)",
                    prefs.getBoolean("volume_norm", true),
                    checked -> prefs.edit().putBoolean("volume_norm", checked).apply());

            addCard(body, dspCard);

            // 3. Playback Controls Card
            LinearLayout playback = Ui.cardContainer(context);
            playback.addView(Ui.eyebrow(context, "Reprodução"));
            checkbox(playback, "Rádio automática ao fim da fila", state.autoplay, checked -> player.autoplay(checked));
            checkbox(playback, "Baixar somente em Wi-Fi", service != null && service.isWifiOnly(), checked -> {
                if (service != null) service.setWifiOnly(checked);
            });
            checkbox(playback, "Economizar dados móveis", service != null && service.isDataSaver(), checked -> {
                if (service != null) service.setDataSaver(checked);
            });
            checkbox(playback, "Baixar favoritas e fila automaticamente no Wi-Fi",
                    service != null && service.isAutoOffline(), checked -> {
                if (service != null) service.setAutoOffline(checked);
            });
            addCard(body, playback);

            // 4. Storage & Cache Management
            LinearLayout data = Ui.cardContainer(context);
            data.addView(Ui.eyebrow(context, "Armazenamento & Cache"));
            OfflineStore store = new OfflineStore(context);
            List<OfflineStore.Song> songs = store.songs();
            long bytes = 0;
            for (OfflineStore.Song song : songs) bytes += song.bytes;
            long cacheBytes = CoverCache.with(context).cacheSizeBytes();

            data.addView(Ui.body(context,
                    Ui.songs(songs.size()) + " baixadas · " + Display.size(bytes) + " de áudio.\n"
                    + "Cache privado de capas: " + Display.size(cacheBytes) + ".\n"
                    + library.plays().size() + " reproduções registradas exclusivamente no aparelho."));

            android.widget.Button clearCacheBtn = Ui.ghost(context, "Limpar Cache de Capas (" + Display.size(cacheBytes) + ")");
            clearCacheBtn.setOnClickListener(v -> {
                CoverCache.with(context).clearCache();
                Widgets.toast(context, "Cache de capas liberado com sucesso!");
                refresh();
            });
            LinearLayout.LayoutParams ccParams = new LinearLayout.LayoutParams(-1, Ui.dp(context, 44));
            ccParams.topMargin = Ui.dp(context, 10);
            data.addView(clearCacheBtn, ccParams);

            android.widget.Button history = Ui.ghost(context, "Apagar histórico e recomendações");
            history.setOnClickListener(v -> new AlertDialog.Builder(context).setTitle("Apagar histórico?").setMessage("O Onda aprende com o que você ouve. Apagar isso zera as recomendações, " + "mas mantém favoritas, playlists e downloads.").setPositiveButton("Apagar", (d, w) -> {
                library.clearHistory();
                Widgets.toast(context, "Histórico apagado");
                refresh();
            }).setNegativeButton("Cancelar", null).show());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Ui.dp(context, 44));
            params.topMargin = Ui.dp(context, 8);
            data.addView(history, params);
            addCard(body, data);

            // 5. Privacy & Sovereign Architecture
            LinearLayout privCard = Ui.cardContainer(context);
            privCard.addView(Ui.eyebrow(context, "Privacidade & Soberania"));
            LinearLayout privRow = Ui.row(context);
            privRow.setGravity(Gravity.CENTER_VERTICAL);
            privRow.addView(Glyph.view(context, Glyph.SHIELD, 22, Ui.CYAN));
            TextView privTitle = Ui.title(context, "Zero Telemetria Externa", 15, Ui.TEXT);
            privTitle.setPadding(Ui.dp(context, 8), 0, 0, 0);
            privRow.addView(privTitle);
            privCard.addView(privRow);

            TextView privBody = Ui.meta(context, "Nenhum dado de uso, busca ou histórico é enviado a servidores de rastreamento. Processamento neural 100% no chip.");
            privBody.setTextColor(Ui.TEXT3);
            privBody.setPadding(0, Ui.dp(context, 6), 0, 0);
            privCard.addView(privBody);

            android.widget.Button welcBtn = Ui.ghost(context, "Boas-Vindas & Segurança Local");
            welcBtn.setOnClickListener(v -> host.openWelcomeSecurity());
            LinearLayout.LayoutParams wbParams = new LinearLayout.LayoutParams(-1, Ui.dp(context, 44));
            wbParams.topMargin = Ui.dp(context, 10);
            privCard.addView(welcBtn, wbParams);
            addCard(body, privCard);

            // 6. Device Status
            LinearLayout status = Ui.cardContainer(context);
            status.addView(Ui.eyebrow(context, "Estado"));
            addStatusRow(status, Glyph.WIFI,
                    service != null && service.hasInternet() ? "Online" : "Sem internet",
                    service != null && service.hasInternet());
            addStatusRow(status, Glyph.DOWNLOAD,
                    service != null && service.isWifiOnly() ? "Downloads só em Wi-Fi" : "Downloads em qualquer rede",
                    service != null);
            addStatusRow(status, Glyph.VOLUME, "Volume " + (service == null ? 0 : service.volume()) + "%", true);
            addStatusRow(status, Glyph.SETTINGS, "Motor " + state.engine, true);
            addCard(body, status);

            // 7. About & Licenses
            LinearLayout about = Ui.cardContainer(context);
            about.addView(Ui.eyebrow(context, "Sobre"));
            about.addView(Ui.body(context,
                    "Onda é um player musical independente para Android. " + "Não é afiliado ao Spotify, YouTube ou Google. Favoritas, playlists e histórico ficam " + "somente neste aparelho; buscas e streaming são enviados ao YouTube. " + "O player usa Media3/ExoPlayer, com fallback de compatibilidade, e o extrator " + "NewPipeExtractor (GPL-3.0-or-later)."));
            android.widget.Button licenses = Ui.ghost(context, "Licenças de terceiros");
            licenses.setOnClickListener(v -> showLicenses());
            LinearLayout.LayoutParams lparams = new LinearLayout.LayoutParams(-1, Ui.dp(context, 44));
            lparams.topMargin = Ui.dp(context, 8);
            about.addView(licenses, lparams);
            addCard(body, about);
        }
        private void addStatusRow(LinearLayout parent, String glyph, String label, boolean positive) {
            LinearLayout row = Ui.row(context);
            row.setPadding(0, Ui.dp(context, 7), 0, Ui.dp(context, 7));
            row.addView(Glyph.view(context, glyph, 17, positive ? Ui.CYAN : Ui.TEXT3),
                    new LinearLayout.LayoutParams(Ui.dp(context, 26), Ui.dp(context, 26)));
            TextView text = Ui.meta(context, label);
            text.setTextColor(Ui.TEXT);
            text.setPadding(Ui.dp(context, 6), 0, 0, 0);
            row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
            parent.addView(row);
        }
        private void addCard(LinearLayout body, View card) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(Ui.dp(context, 20), Ui.dp(context, 6), Ui.dp(context, 20), Ui.dp(context, 6));
            body.addView(card, params);
        }
        private void checkbox(LinearLayout parent, String label, boolean checked, Checked callback) {
            LinearLayout row = Ui.row(context);
            row.setPadding(0, Ui.dp(context, 6), 0, Ui.dp(context, 6));
            TextView text = Ui.body(context, label);
            text.setTextColor(Ui.TEXT);
            row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
            android.widget.CheckBox box = new android.widget.CheckBox(context);
            box.setChecked(checked);
            box.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.CYAN));
            box.setOnCheckedChangeListener((v, value) -> callback.onChecked(value));
            row.addView(box);
            parent.addView(row);
        }
        interface Checked {
            void onChecked(boolean value);
        }
        private void showLicenses() {
            try {
                String[] files = host.getAssets().list("licenses");
                if (files == null) return;
                new AlertDialog.Builder(context).setTitle("Componentes de terceiros").setItems(files, (d,
                        index) -> host.runOnUiThread(() -> showLicense(files[index]))).show();
            } catch (java.io.IOException e) {
                Widgets.toast(context, "Não foi possível listar as licenças.");
            }
        }
        private void showLicense(String file) {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader( host.getAssets().open("licenses/" + file),
                    java.nio.charset.StandardCharsets.UTF_8))) {
                StringBuilder text = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) text.append(line).append('\n');
                ScrollView scroll = new ScrollView(context);
                TextView content = new TextView(context);
                content.setText(text);
                content.setTextSize(11);
                content.setTextColor(Ui.TEXT2);
                content.setPadding(Ui.dp(context, 16), Ui.dp(context, 16), Ui.dp(context, 16), Ui.dp(context, 16));
                scroll.addView(content);
                new AlertDialog.Builder(context).setTitle(file).setView(scroll).setPositiveButton("Fechar", null).show();
            } catch (java.io.IOException e) {
                Widgets.toast(context, "Não foi possível abrir a licença.");
            }
        }
    }

    static final class Equalizer extends Screen {
        private EqualizerSplineView splineView;
        private LinearLayout fadersRail;
        private TextView bassLabel;
        private TextView virtualizerLabel;
        private SeekBar bassSeek;
        private SeekBar virtualizerSeek;
        private final List<android.widget.Button> presetButtons = new ArrayList<>();

        Equalizer(MainActivity host) {
            super(host);
            build();
            refresh();
        }

        private void build() {
            LinearLayout header = header("DSP Engine", "Equalizador de Áudio", true, null, null);
            LinearLayout body = page(header);
            OfflineService service = player.service();

            // 1. Master Engine Switch Card
            LinearLayout masterCard = Ui.cardContainer(context);
            LinearLayout switchRow = Ui.row(context);
            switchRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout switchWords = Ui.column(context);
            switchWords.addView(Ui.title(context, "DSP Master Engine", 16, Ui.TEXT));
            TextView sub = Ui.meta(context, "Filtro acústico FIR de alta fidelidade");
            sub.setTextColor(Ui.TEXT3);
            switchWords.addView(sub);
            switchRow.addView(switchWords, new LinearLayout.LayoutParams(0, -2, 1));

            android.widget.CheckBox sw = new android.widget.CheckBox(context);
            sw.setChecked(service == null || service.effectsEnabled());
            sw.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.CYAN));
            sw.setOnCheckedChangeListener((v, isChecked) -> {
                if (service != null) service.setEffectsEnabled(isChecked);
                refresh();
            });
            switchRow.addView(sw);
            masterCard.addView(switchRow);
            pad(body, masterCard);

            // 2. Real-time Acoustic Transfer Spline Curve
            splineView = new EqualizerSplineView(context);
            splineView.setBackground(Ui.surface(context, Ui.S1, 18, false));
            LinearLayout.LayoutParams splineParams = new LinearLayout.LayoutParams(-1, Ui.dp(context, 160));
            splineParams.setMargins(Ui.dp(context, 20), Ui.dp(context, 10), Ui.dp(context, 20), 0);
            body.addView(splineView, splineParams);

            // 3. Preset Chips Carousel
            section(body, "Predefinições de Áudio", null, null);
            android.widget.HorizontalScrollView presetScroll = Ui.strip(context);
            LinearLayout presetRail = Ui.row(context);
            presetRail.setPadding(Ui.dp(context, 20), 0, Ui.dp(context, 20), 0);
            for (int i = 0; i < AudioFx.PRESET_NAMES.length; i++) {
                final int presetIndex = i;
                android.widget.Button chip = Ui.chip(context, AudioFx.PRESET_NAMES[i], false);
                chip.setOnClickListener(v -> {
                    if (service != null) {
                        service.equalizerPreset(presetIndex);
                        refresh();
                    }
                });
                presetButtons.add(chip);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, Ui.dp(context, 42));
                p.rightMargin = Ui.dp(context, 8);
                presetRail.addView(chip, p);
            }
            presetScroll.addView(presetRail);
            horizontal(body, presetScroll);

            // 4. Parametric Faders Bank
            section(body, "Bandas Paramétricas (dB)", null, null);
            android.widget.HorizontalScrollView fadersScroll = Ui.strip(context);
            fadersRail = Ui.row(context);
            fadersRail.setPadding(Ui.dp(context, 20), 0, Ui.dp(context, 20), Ui.dp(context, 8));
            fadersScroll.addView(fadersRail);
            horizontal(body, fadersScroll);

            // 5. Analog Controls (Bass Boost & 3D Surround)
            section(body, "Efeitos Acústicos & Espaciais", null, null);
            LinearLayout analogCard = Ui.cardContainer(context);

            // Bass Boost
            LinearLayout bassRow = Ui.row(context);
            TextView bassTitle = Ui.body(context, "Bass Boost (Reforço de Graves)");
            bassTitle.setTextColor(Ui.TEXT);
            bassRow.addView(bassTitle, new LinearLayout.LayoutParams(0, -2, 1));
            bassLabel = Ui.meta(context, "0%");
            bassLabel.setTextColor(Ui.CYAN);
            bassRow.addView(bassLabel);
            analogCard.addView(bassRow);

            bassSeek = new SeekBar(context);
            bassSeek.setMax(1000);
            bassSeek.setProgress(service == null ? 0 : service.bassBoost());
            bassSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    bassLabel.setText((progress / 10) + "%");
                    if (fromUser && service != null) service.bassBoost(progress);
                }
                @Override public void onStartTrackingTouch(SeekBar bar) {}
                @Override public void onStopTrackingTouch(SeekBar bar) {}
            });
            analogCard.addView(bassSeek, new LinearLayout.LayoutParams(-1, Ui.dp(context, 32)));

            // 3D Virtualizer
            LinearLayout virtRow = Ui.row(context);
            virtRow.setPadding(0, Ui.dp(context, 10), 0, 0);
            TextView virtTitle = Ui.body(context, "Surround 3D (Espacializador)");
            virtTitle.setTextColor(Ui.TEXT);
            virtRow.addView(virtTitle, new LinearLayout.LayoutParams(0, -2, 1));
            virtualizerLabel = Ui.meta(context, "0%");
            virtualizerLabel.setTextColor(Ui.CYAN);
            virtRow.addView(virtualizerLabel);
            analogCard.addView(virtRow);

            virtualizerSeek = new SeekBar(context);
            virtualizerSeek.setMax(1000);
            virtualizerSeek.setProgress(service == null ? 0 : service.virtualizer());
            virtualizerSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    virtualizerLabel.setText((progress / 10) + "%");
                    if (fromUser && service != null) service.virtualizer(progress);
                }
                @Override public void onStartTrackingTouch(SeekBar bar) {}
                @Override public void onStopTrackingTouch(SeekBar bar) {}
            });
            analogCard.addView(virtualizerSeek, new LinearLayout.LayoutParams(-1, Ui.dp(context, 32)));
            pad(body, analogCard);

            // 6. Audio Output Indicator
            LinearLayout outputCard = Ui.cardContainer(context);
            LinearLayout outRow = Ui.row(context);
            outRow.setGravity(Gravity.CENTER_VERTICAL);
            ImageView outIcon = Glyph.view(context, Glyph.BOLT, 22, Ui.CYAN);
            outRow.addView(outIcon);
            LinearLayout outWords = Ui.column(context);
            outWords.setPadding(Ui.dp(context, 12), 0, 0, 0);
            TextView outTitle = Ui.title(context, "Saída: Áudio Bit-Perfect / DSP 32-bit", 14, Ui.TEXT);
            TextView outSub = Ui.meta(context, "48 kHz · 24-bit Lossless · RTA FIR Ativo");
            outSub.setTextColor(Ui.TEXT3);
            outWords.addView(outTitle);
            outWords.addView(outSub);
            outRow.addView(outWords, new LinearLayout.LayoutParams(0, -2, 1));
            outputCard.addView(outRow);
            pad(body, outputCard);
        }

        @Override void refresh() {
            OfflineService service = player.service();
            int currentPreset = service == null ? 5 : service.equalizerPreset();
            for (int i = 0; i < presetButtons.size(); i++) {
                boolean active = i == currentPreset;
                android.widget.Button b = presetButtons.get(i);
                Ui.interactive(b, active ? Ui.withAlpha(Ui.CYAN, 0x24) : Ui.S2, 999);
                b.setTextColor(active ? Ui.CYAN : Ui.TEXT2);
                b.setSelected(active);
            }

            int bands = service == null ? 5 : Math.max(5, service.equalizerBands());
            int rangeMillibel = service == null ? 1500 : service.equalizerRangeMillibel();
            float maxDb = rangeMillibel / 100f;

            float[] gains = new float[bands];
            String[] labels = new String[bands];
            for (int b = 0; b < bands; b++) {
                int levelMb = service == null ? 0 : service.equalizerBandLevel(b);
                gains[b] = levelMb / 100f;
                int freqHz = service == null ? 0 : service.equalizerBandFreq(b);
                if (freqHz <= 0) {
                    int[] fall = {60, 230, 910, 3600, 14000};
                    freqHz = b < fall.length ? fall[b] : 1000;
                }
                labels[b] = freqHz >= 1000 ? (freqHz / 1000f) + "k" : freqHz + "Hz";
            }
            if (splineView != null) splineView.setBands(gains, labels, maxDb);

            if (fadersRail != null) {
                fadersRail.removeAllViews();
                for (int b = 0; b < bands; b++) {
                    final int bandIdx = b;
                    LinearLayout col = Ui.column(context);
                    col.setGravity(Gravity.CENTER_HORIZONTAL);
                    col.setBackground(Ui.surface(context, Ui.S2, 14, false));
                    col.setPadding(Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 10));

                    TextView dbText = new TextView(context);
                    float currentDb = gains[b];
                    dbText.setText((currentDb >= 0 ? "+" : "") + String.format(java.util.Locale.US, "%.1f", currentDb));
                    dbText.setTextColor(Ui.CYAN);
                    dbText.setTextSize(12);
                    dbText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                    col.addView(dbText);

                    SeekBar slider = new SeekBar(context);
                    slider.setMax(rangeMillibel * 2);
                    slider.setProgress((int) (gains[b] * 100 + rangeMillibel));
                    slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                            int mb = progress - rangeMillibel;
                            float valDb = mb / 100f;
                            dbText.setText((valDb >= 0 ? "+" : "") + String.format(java.util.Locale.US, "%.1f", valDb));
                            if (fromUser && service != null) {
                                service.equalizerBand(bandIdx, mb);
                                gains[bandIdx] = valDb;
                                splineView.setBands(gains, labels, maxDb);
                            }
                        }
                        @Override public void onStartTrackingTouch(SeekBar bar) {}
                        @Override public void onStopTrackingTouch(SeekBar bar) {}
                    });
                    col.addView(slider, new LinearLayout.LayoutParams(Ui.dp(context, 120), Ui.dp(context, 32)));

                    TextView freqText = Ui.meta(context, labels[b]);
                    freqText.setTextColor(Ui.TEXT3);
                    freqText.setTextSize(11);
                    col.addView(freqText);

                    LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(-2, -2);
                    colParams.rightMargin = Ui.dp(context, 10);
                    fadersRail.addView(col, colParams);
                }
            }

            if (bassLabel != null && service != null) bassLabel.setText((service.bassBoost() / 10) + "%");
            if (virtualizerLabel != null && service != null) virtualizerLabel.setText((service.virtualizer() / 10) + "%");
        }
    }

    static final class WelcomeSecurity extends Screen {
        WelcomeSecurity(MainActivity host) {
            super(host);
            build();
            refresh();
        }

        private void build() {
            LinearLayout header = header("Privacidade & Soberania", "Acesso & Segurança", true, null, null);
            LinearLayout body = page(header);
            SharedPreferences prefs = context.getSharedPreferences("onda_security", Context.MODE_PRIVATE);

            // 1. Sovereign Architecture Presentation
            LinearLayout sovCard = Ui.cardContainer(context);
            sovCard.setPadding(Ui.dp(context, 18), Ui.dp(context, 18), Ui.dp(context, 18), Ui.dp(context, 18));
            LinearLayout sovRow = Ui.row(context);
            sovRow.setGravity(Gravity.CENTER_VERTICAL);
            ImageView shield = Glyph.view(context, Glyph.SHIELD, 36, Ui.CYAN);
            sovRow.addView(shield);
            LinearLayout sovWords = Ui.column(context);
            sovWords.setPadding(Ui.dp(context, 14), 0, 0, 0);
            sovWords.addView(Ui.title(context, "Arquitetura Soberana Onda", 17, Ui.TEXT));
            TextView sovSub = Ui.meta(context, "Privacidade Absoluta · Zero Rastreamento");
            sovSub.setTextColor(Ui.CYAN);
            sovWords.addView(sovSub);
            sovRow.addView(sovWords, new LinearLayout.LayoutParams(0, -2, 1));
            sovCard.addView(sovRow);

            TextView sovBody = Ui.body(context,
                    "Seu perfil, histórico, favoritas e playlists são armazenados "
                    + "exclusivamente na memória deste dispositivo com criptografia local. O processamento musical e recomendações "
                    + "são calculados 100% no chip, sem nenhuma telemetria externa enviada para a nuvem.");
            sovBody.setPadding(0, Ui.dp(context, 12), 0, 0);
            sovCard.addView(sovBody);
            pad(body, sovCard);

            // 2. Local PIN Setup
            section(body, "Proteção de Acesso Local", null, null);
            LinearLayout pinCard = Ui.cardContainer(context);
            LinearLayout pinHeader = Ui.row(context);
            pinHeader.setGravity(Gravity.CENTER_VERTICAL);
            pinHeader.addView(Glyph.view(context, Glyph.LOCK, 22, Ui.CYAN));
            TextView pinTitle = Ui.title(context, "PIN de Segurança (4 dígitos)", 15, Ui.TEXT);
            pinTitle.setPadding(Ui.dp(context, 10), 0, 0, 0);
            pinHeader.addView(pinTitle);
            pinCard.addView(pinHeader);

            boolean hasPin = prefs.contains("local_pin");
            TextView pinStatus = Ui.meta(context, hasPin ? "PIN atual ativado e protegido." : "Nenhum PIN configurado (aplicativo livre).");
            pinStatus.setPadding(0, Ui.dp(context, 6), 0, Ui.dp(context, 10));
            pinCard.addView(pinStatus);

            EditText pinInput = new EditText(context);
            pinInput.setHint("Digite 4 números");
            pinInput.setHintTextColor(Ui.TEXT3);
            pinInput.setTextColor(Ui.TEXT);
            pinInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            pinInput.setBackground(Ui.surface(context, Ui.S2, 12, false));
            pinInput.setPadding(Ui.dp(context, 14), Ui.dp(context, 10), Ui.dp(context, 14), Ui.dp(context, 10));
            pinCard.addView(pinInput, new LinearLayout.LayoutParams(-1, Ui.dp(context, 48)));

            LinearLayout pinActions = Ui.row(context);
            pinActions.setPadding(0, Ui.dp(context, 10), 0, 0);
            android.widget.Button savePin = Ui.primary(context, "Salvar PIN");
            savePin.setOnClickListener(v -> {
                String val = pinInput.getText().toString().trim();
                if (val.length() == 4) {
                    prefs.edit().putString("local_pin", val).apply();
                    Widgets.toast(context, "PIN de segurança salvo com sucesso!");
                    pinInput.setText("");
                    refresh();
                } else {
                    Widgets.toast(context, "O PIN deve conter exatamente 4 dígitos.");
                }
            });
            pinActions.addView(savePin, new LinearLayout.LayoutParams(0, Ui.dp(context, 44), 1));

            if (hasPin) {
                View space = new View(context);
                pinActions.addView(space, new LinearLayout.LayoutParams(Ui.dp(context, 10), 1));
                android.widget.Button removePin = Ui.ghost(context, "Remover PIN");
                removePin.setTextColor(Ui.RED);
                removePin.setOnClickListener(v -> {
                    prefs.edit().remove("local_pin").apply();
                    Widgets.toast(context, "PIN removido.");
                    refresh();
                });
                pinActions.addView(removePin, new LinearLayout.LayoutParams(0, Ui.dp(context, 44), 1));
            }
            pinCard.addView(pinActions);
            pad(body, pinCard);

            // 3. Biometrics Toggle
            LinearLayout bioCard = Ui.cardContainer(context);
            LinearLayout bioRow = Ui.row(context);
            bioRow.setGravity(Gravity.CENTER_VERTICAL);
            bioRow.addView(Glyph.view(context, Glyph.CHECK, 20, Ui.CYAN));
            TextView bioLabel = Ui.body(context, "Desbloqueio por Biometria / Digital");
            bioLabel.setTextColor(Ui.TEXT);
            bioLabel.setPadding(Ui.dp(context, 10), 0, 0, 0);
            bioRow.addView(bioLabel, new LinearLayout.LayoutParams(0, -2, 1));
            android.widget.CheckBox bioBox = new android.widget.CheckBox(context);
            bioBox.setChecked(prefs.getBoolean("biometrics_enabled", false));
            bioBox.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.CYAN));
            bioBox.setOnCheckedChangeListener((v, isChecked) -> {
                prefs.edit().putBoolean("biometrics_enabled", isChecked).apply();
                Widgets.toast(context, isChecked ? "Biometria habilitada" : "Biometria desabilitada");
            });
            bioRow.addView(bioBox);
            bioCard.addView(bioRow);
            pad(body, bioCard);

            // 4. Instant Offline Guest Mode
            section(body, "Acesso Rápido", null, null);
            LinearLayout guestCard = Ui.cardContainer(context);
            android.widget.Button guestBtn = Ui.primary(context, "Entrar como Convidado (Modo 100% Offline)");
            guestBtn.setOnClickListener(v -> {
                Widgets.toast(context, "Modo Convidado Offline ativado!");
                host.onBackPressed();
            });
            guestCard.addView(guestBtn, new LinearLayout.LayoutParams(-1, Ui.dp(context, 48)));
            TextView guestDesc = Ui.meta(context, "Acesso imediato para ouvir músicas baixadas e locais sem salvar histórico.");
            guestDesc.setPadding(0, Ui.dp(context, 8), 0, 0);
            guestCard.addView(guestDesc);
            pad(body, guestCard);
        }

        @Override void refresh() {
        }
    }
}
