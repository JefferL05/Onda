package app.onda.mobile;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable list and card widgets. Kept in one place so a track looks the same
 * in search results, playlists, history and the queue — and so a behavioural
 * change (long-press menu, badge rules) happens once.
 */
final class Widgets {

    interface TrackAction {
        /** Play, open, or navigate — whatever the row's primary intent is. */
        void onPrimary(LibraryStore.Track track, View row);

        default void onLongPress(LibraryStore.Track track, View row) {
            onMore(track, row);
        }

        void onMore(LibraryStore.Track track, View row);
    }

    private Widgets() {
    }

    // --------------------------------------------------------------- rows
    /** 64 dp track row: cover, title, meta, optional badge, overflow. */
    static LinearLayout trackRow(Context context, LibraryStore.Track track, String meta, String badge,
            int badgeColor, boolean highlight, TrackAction action) {
        LinearLayout row = Ui.row(context);
        row.setPadding(Ui.dp(context, 10), Ui.dp(context, 8), Ui.dp(context, 6), Ui.dp(context, 8));
        row.setBackground(Ui.surface(context, highlight ? Ui.withAlpha(Ui.CYAN, 0x14) : Ui.S1, 16, false));

        ImageView cover = Ui.cover(context, 52, 10);
        CoverCache.with(context).load(cover, track.thumbnail);
        cover.setContentDescription(null);
        row.addView(cover);

        LinearLayout words = Ui.column(context);
        words.setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 6), 0);
        LinearLayout titleLine = Ui.row(context);
        TextView title = Ui.title(context, track.title, 15, highlight ? Ui.CYAN : Ui.TEXT);
        title.setMaxLines(1);
        titleLine.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        if (badge != null && !badge.isEmpty()) {
            TextView chip = Ui.badge(context, badge, badgeColor);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.leftMargin = Ui.dp(context, 8);
            titleLine.addView(chip, params);
        }
        words.addView(titleLine);
        String detail = meta == null || meta.isEmpty() ? track.artist() : meta;
        TextView subtitle = Ui.meta(context, detail);
        subtitle.setPadding(0, Ui.dp(context, 3), 0, 0);
        words.addView(subtitle);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));

        ImageView more = iconButton(context, Glyph.MORE, Ui.TEXT3, 36);
        more.setContentDescription("Opções de " + track.title);
        more.setOnClickListener(v -> action.onMore(track, row));
        row.addView(more, new LinearLayout.LayoutParams(Ui.dp(context, 40), Ui.dp(context, 48)));

        row.setContentDescription("Reproduzir " + track.title + (detail.isEmpty() ? "" : ", " + detail));
        Ui.ripple(row, 16);
        row.setOnClickListener(v -> action.onPrimary(track, row));
        row.setOnLongClickListener(v -> {
            action.onLongPress(track, row);
            return true;
        });
        row.setFocusable(true);
        return row;
    }

    static void addTrackRow(LinearLayout parent, Context context, LibraryStore.Track track, String meta,
            String badge, int badgeColor, boolean highlight, TrackAction action) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = Ui.dp(context, 6);
        parent.addView(trackRow(context, track, meta, badge, badgeColor, highlight, action), params);
    }

    /** 44 dp compact row for dense lists (queue preview, lyrics tab). */
    static LinearLayout compactRow(Context context, LibraryStore.Track track, String meta, boolean current,
            TrackAction action) {
        LinearLayout row = Ui.row(context);
        row.setPadding(Ui.dp(context, 6), Ui.dp(context, 4), Ui.dp(context, 6), Ui.dp(context, 4));
        ImageView cover = Ui.cover(context, 40, 8);
        CoverCache.with(context).load(cover, track.thumbnail);
        row.addView(cover);
        LinearLayout words = Ui.column(context);
        words.setPadding(Ui.dp(context, 10), 0, Ui.dp(context, 6), 0);
        TextView title = Ui.title(context, track.title, 14, current ? Ui.CYAN : Ui.TEXT);
        title.setMaxLines(1);
        words.addView(title);
        TextView subtitle = Ui.meta(context, meta);
        subtitle.setTextSize(11);
        words.addView(subtitle);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        Ui.ripple(row, 12);
        row.setOnClickListener(v -> action.onPrimary(track, row));
        row.setOnLongClickListener(v -> {
            action.onLongPress(track, row);
            return true;
        });
        row.setContentDescription((current ? "Tocando agora: " : "") + track.title);
        return row;
    }

    // -------------------------------------------------------------- cards
    /** Square card for a home/explore carousel. */
    static LinearLayout coverCard(Context context, String title, String subtitle, String coverUrl,
            int widthDp, Runnable click, Runnable more) {
        LinearLayout card = Ui.column(context);
        card.setPadding(Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 12));
        card.setBackground(Ui.card(18));

        android.widget.FrameLayout artFrame = new android.widget.FrameLayout(context);
        ImageView image = Ui.cover(context, 0, 14);
        android.widget.FrameLayout.LayoutParams imageParams = new android.widget.FrameLayout.LayoutParams(
                Ui.dp(context, widthDp - 20), Ui.dp(context, widthDp - 20));
        image.setLayoutParams(imageParams);
        CoverCache.with(context).load(image, coverUrl);
        artFrame.addView(image, imageParams);

        // Circular Electric Cyan Play button overlay
        LinearLayout playOverlay = Ui.column(context);
        playOverlay.setGravity(Gravity.CENTER);
        playOverlay.setBackground(Ui.surface(context, Ui.CYAN, 999, false));
        ImageView playIcon = Glyph.view(context, Glyph.PLAY, 16, Ui.S0);
        playOverlay.addView(playIcon);
        android.widget.FrameLayout.LayoutParams playParams = new android.widget.FrameLayout.LayoutParams(
                Ui.dp(context, 34), Ui.dp(context, 34), Gravity.BOTTOM | Gravity.END);
        playParams.setMargins(0, 0, Ui.dp(context, 6), Ui.dp(context, 6));
        playOverlay.setOnClickListener(v -> {
            if (click != null)
                click.run();
        });
        playOverlay.setContentDescription("Tocar " + title);
        Ui.ripple(playOverlay, 999);
        artFrame.addView(playOverlay, playParams);

        card.addView(artFrame, new LinearLayout.LayoutParams(-2, -2));
        TextView name = Ui.title(context, title, 14, Ui.TEXT);
        name.setMaxLines(2);
        name.setPadding(0, Ui.dp(context, 10), 0, 0);
        card.addView(name);
        TextView detail = Ui.meta(context, subtitle);
        detail.setPadding(0, Ui.dp(context, 3), 0, 0);
        card.addView(detail);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(Ui.dp(context, widthDp), -2);
        params.rightMargin = Ui.dp(context, 10);
        card.setLayoutParams(params);
        Ui.ripple(card, 18);
        card.setOnClickListener(v -> {
            if (click != null)
                click.run();
        });
        if (more != null)
            card.setOnLongClickListener(v -> {
                more.run();
                return true;
            });
        return card;
    }

    /** Wide "shelf" row: cover + two lines + chevron. */
    static LinearLayout shelfRow(Context context, String title, String subtitle, String coverUrl, Runnable click) {
        LinearLayout card = Ui.row(context);
        card.setPadding(Ui.dp(context, 10), Ui.dp(context, 10), Ui.dp(context, 14), Ui.dp(context, 10));
        card.setBackground(Ui.card(18));
        ImageView image = Ui.cover(context, 56, 12);
        CoverCache.with(context).load(image, coverUrl);
        card.addView(image);
        LinearLayout words = Ui.column(context);
        words.setPadding(Ui.dp(context, 12), 0, 0, 0);
        TextView name = Ui.title(context, title, 15, Ui.TEXT);
        name.setMaxLines(1);
        words.addView(name);
        TextView detail = Ui.meta(context, subtitle);
        detail.setPadding(0, Ui.dp(context, 2), 0, 0);
        words.addView(detail);
        card.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView chevron = Glyph.view(context, Glyph.MORE, 16, Ui.TEXT3);
        card.addView(chevron);
        card.setOnClickListener(v -> click.run());
        Ui.ripple(card, 18);
        return card;
    }

    // ------------------------------------------------------------ actions
    static ImageView iconButton(Context context, String glyph, int color, int sizeDp) {
        ImageView view = Glyph.view(context, glyph, sizeDp, color);
        view.setMinimumWidth(Ui.dp(context, Ui.TOUCH));
        view.setMinimumHeight(Ui.dp(context, Ui.TOUCH));
        view.setPadding(Ui.dp(context, 8), Ui.dp(context, 8), Ui.dp(context, 8), Ui.dp(context, 8));
        Ui.roundClip(view, 999);
        Ui.ripple(view, 999);
        view.setContentDescription(null);
        return view;
    }

    /**
     * The one place a track menu is defined, so every screen offers the same verbs.
     */
    static void trackMenu(Context context, LibraryStore library, LibraryStore.Track track, String collectionKey,
            String collectionName, Runnable onChanged) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        boolean liked = library.liked(track.url);
        labels.add("Reproduzir agora");
        actions.add(() -> PlayerController.get(context).playNow(track));
        labels.add("Tocar a seguir");
        actions.add(() -> {
            PlayerController.get(context).enqueue(track, true);
            toast(context, "Vai tocar a seguir");
        });
        labels.add("Adicionar à fila");
        actions.add(() -> {
            PlayerController.get(context).enqueue(track, false);
            toast(context, "Adicionada à fila");
        });
        labels.add(liked ? "Remover dos favoritos" : "Curtir música");
        actions.add(() -> {
            library.toggleLike(track);
            toast(context, library.liked(track.url) ? "Música curtida" : "Removida dos favoritos");
            if (onChanged != null)
                onChanged.run();
        });
        labels.add("Adicionar à playlist");
        actions.add(() -> {
            if (context instanceof android.app.Activity)
                LibraryDialogs.save((android.app.Activity) context, track,
                        () -> {
                            if (onChanged != null)
                                onChanged.run();
                        });
        });
        labels.add("Baixar áudio");
        actions.add(() -> {
            PlayerController.get(context).download(track);
            toast(context, "Download na fila");
        });
        labels.add("Abrir no YouTube");
        actions.add(() -> external(context, track.url));
        if (!track.artist().isEmpty()) {
            boolean following = library.following(track.artist());
            labels.add(following ? "Deixar de seguir " + track.artist() : "Seguir " + track.artist());
            actions.add(() -> {
                boolean now = library.toggleFollow(track.artist());
                toast(context, now ? "Seguindo " + track.artist() : "Deixou de seguir");
                if (onChanged != null)
                    onChanged.run();
            });
        }
        if (collectionKey != null && collectionKey.startsWith("playlist:")) {
            labels.add("Mover para cima");
            actions.add(() -> {
                library.moveTrack(collectionKey, track.url, -1);
                if (onChanged != null)
                    onChanged.run();
            });
            labels.add("Mover para baixo");
            actions.add(() -> {
                library.moveTrack(collectionKey, track.url, 1);
                if (onChanged != null)
                    onChanged.run();
            });
            labels.add("Remover da playlist");
            actions.add(() -> {
                int index = 0;
                List<LibraryStore.Track> items = library.tracks(collectionKey);
                for (int i = 0; i < items.size(); i++)
                    if (items.get(i).url.equals(track.url))
                        index = i;
                library.remove(collectionKey, track.url);
                if (onChanged != null)
                    onChanged.run();
                toast(context, "Removida · desfazer em " + collectionName);
                final int at = index;
                final Runnable restore = () -> {
                    library.restoreTrack(collectionKey, track, at);
                    if (onChanged != null)
                        onChanged.run();
                };
                MainActivity.undo("Removida de " + collectionName, restore);
            });
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View header = menuHeader(context, track);
        builder.setCustomTitle(header);
        builder.setItems(labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run());
        builder.show();
    }

    private static View menuHeader(Context context, LibraryStore.Track track) {
        LinearLayout header = Ui.row(context);
        header.setPadding(Ui.dp(context, 20), Ui.dp(context, 16), Ui.dp(context, 20), Ui.dp(context, 8));
        ImageView cover = Ui.cover(context, 44, 10);
        CoverCache.with(context).load(cover, track.thumbnail);
        header.addView(cover);
        LinearLayout words = Ui.column(context);
        words.setPadding(Ui.dp(context, 12), 0, 0, 0);
        TextView title = Ui.title(context, track.title, 15, Ui.TEXT);
        title.setMaxLines(1);
        words.addView(title);
        words.addView(Ui.meta(context, track.artist()));
        header.addView(words);
        return header;
    }

    /** Cover + title + subtitle header, used by menus and modals. */
    static void external(Context context, String url) {
        try {
            context.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(url)));
        } catch (Exception e) {
            toast(context, "Nenhum app disponível para abrir o link.");
        }
    }

    static void toast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    /** Simple two-line dialog with a single action. */
    static void info(Context context, String title, String message) {
        new AlertDialog.Builder(context).setTitle(title).setMessage(message)
                .setPositiveButton("Entendi", null).show();
    }

    /** Horizontal carousel with cards. */
    static android.widget.HorizontalScrollView carousel(Context context, int heightDp) {
        android.widget.HorizontalScrollView scroll = Ui.strip(context);
        LinearLayout rail = Ui.row(context);
        rail.setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 6), 0);
        scroll.addView(rail, new android.widget.HorizontalScrollView.LayoutParams(-2, -2));
        scroll.setTag(rail);
        return scroll;
    }

    @SuppressWarnings("unchecked")
    static LinearLayout rail(android.widget.HorizontalScrollView scroll) {
        return (LinearLayout) scroll.getTag();
    }

    /** Thin gradient rule used to separate muted sections without a hard line. */
    static View divider(Context context, int topDp) {
        View view = new View(context);
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] { Ui.withAlpha(Ui.CYAN, 0x00), Ui.withAlpha(Ui.CYAN, 0x30), Ui.withAlpha(Ui.VIOLET, 0x00) });
        view.setBackground(drawable);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(context, 1)));
        params.topMargin = Ui.dp(context, topDp);
        params.bottomMargin = Ui.dp(context, topDp);
        view.setLayoutParams(params);
        return view;
    }

    static void setMargins(View view, int left, int top, int right, int bottom) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof LinearLayout.LayoutParams))
            return;
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) params;
        lp.setMargins(left, top, right, bottom);
        view.setLayoutParams(lp);
    }
}
