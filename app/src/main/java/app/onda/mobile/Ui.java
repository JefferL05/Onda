package app.onda.mobile;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

/**
 * Design system "Oceanic Luminescence": dark graphite canvas, bioluminescent cyan,
 * ultraviolet accents, frosted glass and pill controls. One palette, one card language.
 */
public final class Ui {
    // Canvas / surfaces
    public static final int BG = Color.rgb(11, 14, 20);            // #0B0E14 abyssal graphite
    public static final int RAISED = Color.rgb(18, 23, 33);        // #121721 surface-raised (cards)
    public static final int PANEL = Color.rgb(25, 28, 34);         // #191C22 surface-container-low
    public static final int PANEL_HIGH = Color.rgb(29, 32, 38);    // #1D2026 surface-container
    public static final int PANEL_HIGHER = Color.rgb(39, 42, 49);  // #272A31 surface-container-high
    public static final int PANEL_HIGHEST = Color.rgb(50, 53, 60); // #32353C surface-container-highest
    // Lines
    public static final int STROKE = Color.rgb(59, 73, 76);        // #3B494C outline-variant
    public static final int STROKE_SOFT = Color.argb(18, 255, 255, 255);
    // Accents
    public static final int GREEN = Color.rgb(0, 229, 255);        // #00E5FF electric cyan (primary)
    public static final int GREEN_DARK = Color.rgb(0, 98, 110);    // #00626E on-primary-container
    public static final int VIOLET = Color.rgb(124, 77, 255);      // #7C4DFF neon ultraviolet
    public static final int VIOLET_CONTAINER = Color.rgb(82, 3, 213);
    public static final int VIOLET_LIGHT = Color.rgb(205, 189, 255);
    // Text
    public static final int WHITE = Color.rgb(241, 245, 249);      // #F1F5F9 text-primary
    public static final int MUTED = Color.rgb(148, 163, 184);      // #94A3B8 text-secondary
    public static final int MUTED_DIM = Color.rgb(71, 85, 105);    // #475569 text-muted
    // States
    public static final int OFFLINE = Color.rgb(16, 185, 129);     // #10B981
    public static final int DOWNLOADING = Color.rgb(245, 158, 11); // #F59E0B
    public static final int DANGER = Color.rgb(255, 180, 171);     // #FFB4AB
    public static final int GLASS = Color.argb(184, 18, 23, 33);   // rgba(18,23,33,0.72)

    public static final int WIDE_DP = 840;

    private Ui() {}

    private static float density = 1f;

    public static int dp(Context context, int value) {
        density = context.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
    private static int dpStatic(int value) { return Math.round(value * density); }

    /** Hairline card surface used across modules. */
    public static GradientDrawable card() { return card(16); }
    public static GradientDrawable card(int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(RAISED);
        background.setCornerRadius(dpStatic(radiusDp));
        background.setStroke(1, STROKE_SOFT);
        return background;
    }

    public static LinearLayout cardContainer(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(card(16));
        int padding = dp(context, 16);
        box.setPadding(padding, padding, padding, padding);
        return box;
    }

    public static GradientDrawable surface(Context context, int color, int radius, boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(dp(context, radius));
        drawable.setStroke(dp(context, focused ? 2 : 1), focused ? GREEN : STROKE_SOFT);
        return drawable;
    }
    public static void interactive(View view, int color, int radius) {
        Context context = view.getContext();
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, surface(context, color, radius, true));
        states.addState(new int[]{android.R.attr.state_selected}, surface(context, GREEN_DARK, radius, true));
        states.addState(new int[]{}, surface(context, color, radius, false));
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x2E00E5FF), states,
                surface(context, WHITE, radius, false)));
        view.setFocusable(true);
    }

    public static String totalDuration(java.util.List<LibraryStore.Track> tracks) {
        long total = 0; int known = 0;
        for (LibraryStore.Track t : tracks) if (t.durationSec > 0) { total += t.durationSec; known++; }
        if (known == 0) return "Duração não disponível";
        return Display.duration(total) + (known < tracks.size() ? " conhecidos" : " no total");
    }

    /** Section heading: headline-md. */
    public static TextView section(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(18);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(WHITE);
        view.setPadding(0, dp(context, 20), 0, dp(context, 8));
        return view;
    }
    /** Small uppercase label: label-sm. */
    public static TextView eyebrow(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text.toUpperCase(Locale.ROOT));
        view.setTextSize(11);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(GREEN);
        view.setLetterSpacing(0.08f);
        view.setPadding(0, dp(context, 4), 0, dp(context, 4));
        return view;
    }
    public static TextView body(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(MUTED);
        view.setLineSpacing(dp(context, 4), 1);
        return view;
    }
    /** Large headline: headline-xl-mobile. */
    public static TextView headline(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(26);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(WHITE);
        view.setLineSpacing(dp(context, 4), 1);
        return view;
    }
    /** Track title line: title-md. */
    public static TextView title(Context context, String text, int size, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(size);
        view.setTypeface(Typeface.DEFAULT, size >= 16 ? Typeface.BOLD : Typeface.NORMAL);
        view.setTextColor(color);
        view.setMaxLines(2);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return view;
    }

    /** Primary pill action: cyan fill, dark glyph (play/pause). */
    public static Button primary(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(BG);
        interactive(button, GREEN, 16);
        button.setMinHeight(dp(context, 52));
        return button;
    }
    /** Circular primary control (play/pause). */
    public static Button circle(Context context, String glyph) {
        Button button = new Button(context);
        button.setText(glyph);
        button.setAllCaps(false);
        button.setTextSize(24);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(BG);
        button.setGravity(Gravity.CENTER);
        interactive(button, GREEN, 28);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(context, 56), dp(context, 56));
        button.setLayoutParams(params);
        return button;
    }
    /** Glass pill secondary action. */
    public static Button ghost(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(GREEN);
        interactive(button, PANEL_HIGH, 14);
        button.setMinHeight(dp(context, 48));
        return button;
    }
    /** Filter chip: pill. Active = cyan fill + dark bold text. */
    public static Button chip(Context context, String text, boolean selected) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        interactive(button, selected ? GREEN : RAISED, 999);
        button.setTextColor(selected ? BG : MUTED);
        button.setSelected(selected);
        button.setMinHeight(dp(context, 40));
        return button;
    }
    /** Generic pill (status, tags). */
    public static Button pill(Context context, String text, int background, int foreground) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(foreground);
        interactive(button, background, 999);
        button.setMinHeight(dp(context, 36));
        return button;
    }
    /** Small status badge. */
    public static TextView badge(Context context, String text, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(10);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(color);
        view.setAllCaps(true);
        view.setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4));
        view.setBackground(surface(context, Color.argb(38, Color.red(color), Color.green(color), Color.blue(color)), 8, false));
        return view;
    }

    public static ImageView cover(Context context, int sizeDp, int radiusDp) {
        ImageView view = new ImageView(context);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable background = new GradientDrawable();
        background.setColor(PANEL_HIGH);
        background.setCornerRadius(dp(context, radiusDp));
        background.setStroke(1, STROKE_SOFT);
        view.setBackground(background);
        view.setImageResource(R.drawable.ic_onda);
        view.setClipToOutline(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp));
        view.setLayoutParams(params);
        return view;
    }

    public static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }
    /** Horizontal scrollable strip (carousels). */
    public static android.widget.HorizontalScrollView strip(Context context) {
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, 0);
        return scroll;
    }

    public static String formatDuration(long seconds) { return Display.duration(seconds); }
    public static String formatClock(long ms) { return Display.clock(ms); }
    public static String formatSize(long bytes) { return Display.size(bytes); }
}
