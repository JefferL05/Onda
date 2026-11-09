package app.onda.mobile;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Persistent bottom player. Reads the shared PlayerController snapshot, so it
 * is always truthful across tabs and never sends a command into the void.
 */
final class MiniPlayer {
    private final MainActivity host;
    private final PlayerController player;
    private final Runnable expand;
    private final LinearLayout shell;
    private final LinearLayout bar;
    private final LinearLayout progressTrack;
    private final View progressFill;
    private final ImageView cover;
    private final TextView title;
    private final TextView subtitle;
    private final ImageView playIcon;
    private final ImageView nextIcon;
    private final ImageView likeIcon;

    MiniPlayer(MainActivity host, PlayerController player, Runnable expand) {
        this.host = host;
        this.player = player;
        this.expand = expand;
        Context context = host;
        shell = Ui.column(context);
        shell.setPadding(Ui.dp(context, 10), Ui.dp(context, 4), Ui.dp(context, 10), Ui.dp(context, 8));

        bar = Ui.row(context);
        bar.setPadding(Ui.dp(context, 8), Ui.dp(context, 8), Ui.dp(context, 6), Ui.dp(context, 8));
        bar.setBackground(Ui.surface(context, Ui.S3, 18, false));
        cover = Ui.cover(context, 44, 10);
        bar.addView(cover);
        LinearLayout words = Ui.column(context);
        words.setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 6), 0);
        title = Ui.title(context, "", 13, Ui.TEXT);
        title.setMaxLines(1);
        words.addView(title);
        subtitle = Ui.meta(context, "");
        subtitle.setTextSize(11);
        subtitle.setPadding(0, Ui.dp(context, 2), 0, 0);
        words.addView(subtitle);
        bar.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        likeIcon = Widgets.iconButton(context, Glyph.HEART, Ui.TEXT3, 18);
        likeIcon.setContentDescription("Curtir");
        likeIcon.setOnClickListener(v -> {
            LibraryStore.Track track = LibraryStore.shared(context).find(player.state().url);
            if (track == null) return;
            LibraryStore.shared(context).toggleLike(track);
            update();
        });
        bar.addView(likeIcon, new LinearLayout.LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)));
        playIcon = Glyph.view(context, Glyph.PLAY, 22, Ui.BG);
        LinearLayout playButton = Ui.column(context);
        playButton.setGravity(Gravity.CENTER);
        playButton.setBackground(Ui.aurora(context, 999));
        playButton.addView(playIcon);
        Ui.ripple(playButton, 999);
        playButton.setContentDescription("Ouvir ou pausar");
        playButton.setOnClickListener(v -> player.toggle());
        bar.addView(playButton, new LinearLayout.LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)));
        nextIcon = Widgets.iconButton(context, Glyph.NEXT, Ui.TEXT2, 20);
        nextIcon.setContentDescription("Próxima música");
        nextIcon.setOnClickListener(v -> player.next());
        bar.addView(nextIcon, new LinearLayout.LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)));
        bar.setOnClickListener(v -> expand.run());
        bar.setContentDescription("Abrir o player");
        Ui.ripple(bar, 18);

        progressTrack = Ui.row(context);
        progressTrack.setBackgroundColor(Ui.S1);
        progressFill = new View(context);
        progressFill.setBackgroundColor(Ui.CYAN);
        progressTrack.addView(progressFill, new LinearLayout.LayoutParams(0, Ui.dp(context, 2), 0));

        shell.addView(progressTrack, new LinearLayout.LayoutParams(-1, Ui.dp(context, 2)));
        shell.addView(bar);
        host.rootMiniHost().addView(shell, new LinearLayout.LayoutParams(-1, -2));
        update();
    }

    void layout() { update(); }

    void setVisible(boolean visible) {
        if (visible) update();
        else shell.setVisibility(View.GONE);
    }

    void collapse() {
        if (host.browserVisible()) return;
        setVisible(true);
    }

    void update() {
        PlayerController.State state = player.state();
        shell.setVisibility(state.hasTrack ? View.VISIBLE : View.GONE);
        if (!state.hasTrack) return;
        if (!state.title.equals(title.getText().toString())) title.setText(state.title);
        String detail = state.artist.isEmpty() ? state.status : state.artist;
        subtitle.setText(detail);
        cover.setTag(null);
        CoverCache.with(host).load(cover, state.cover);
        playIcon.setImageDrawable(Glyph.drawable(
                state.playing || (state.loading && state.playing) ? Glyph.PAUSE : Glyph.PLAY, Ui.BG, true));
        likeIcon.setImageDrawable(Glyph.drawable(Glyph.HEART, state.liked ? Ui.CYAN : Ui.TEXT3));
        updatePosition();
    }

    void updatePosition() {
        PlayerController.State state = player.state();
        int percent = state.progressPercent();
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) progressFill.getLayoutParams();
        int width = progressTrack.getWidth();
        params.width = Math.max(0, width * percent / 100);
        params.weight = 0;
        progressFill.setLayoutParams(params);
    }
}
