package app.onda.mobile;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;

/**
 * Onda design system, version 2 — "Corrente" (the current).
 *
 * One visual language for every surface: an abyssal ocean canvas, tiered
 * graphite surfaces, a cyan→violet aurora saved for things that matter
 * (now playing, the primary action, live state) and a strict type scale.
 *
 * Everything is drawn in code, so the app ships with no resource pipeline and
 * every interactive state (pressed, focused, selected, disabled) is coherent.
 */
public final class Ui {

    // ------------------------------------------------------------- palette
    public static final int BG          = 0xFF07090D;  // abyss
    public static final int S1          = 0xFF0E1117;  // card
    public static final int S2          = 0xFF151922;  // raised / chips
    public static final int S3          = 0xFF1D222C;  // highest
    public static final int LINE        = 0x18FFFFFF;  // hairline
    public static final int LINE_STRONG = 0x33FFFFFF;

    public static final int TEXT        = 0xFFF4F6FA;
    public static final int TEXT2       = 0xFF9AA5B8;
    public static final int TEXT3       = 0xFF5F6A7C;

    public static final int CYAN        = 0xFF22E0FF;  // primary
    public static final int CYAN_DEEP   = 0xFF0B7C8C;
    public static final int VIOLET      = 0xFF8B6BFF;  // secondary
    public static final int VIOLET_SOFT = 0xFFC9BCFF;
    public static final int GREEN       = 0xFF38E0A0;  // offline / positive
    public static final int AMBER       = 0xFFF5B14C;  // downloading
    public static final int RED         = 0xFFFF7A7A;
    public static final int GLASS       = 0xE60E1117;

    // Legacy aliases kept for existing call sites.
    public static final int RAISED = S2, PANEL = S2, PANEL_HIGH = S3, PANEL_HIGHER = S3,
            PANEL_HIGHEST = S3, STROKE = LINE_STRONG, STROKE_SOFT = LINE, WHITE = TEXT,
            MUTED = TEXT2, MUTED_DIM = TEXT3, GREEN_DARK = CYAN_DEEP,
            VIOLET_CONTAINER = VIOLET, VIOLET_LIGHT = VIOLET_SOFT,
            OFFLINE = GREEN, DOWNLOADING = AMBER, DANGER = RED;

    public static final int WIDE_DP = 840;
    public static final int TOUCH = 48;

    private Ui() {}

    private static float density = 1f;

    public static int dp(Context context, int value) {
        density = context.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
    private static int dpStatic(int value) { return Math.round(value * density); }

    // ------------------------------------------------------------ shapes
    public static GradientDrawable card() { return card(20); }

    public static GradientDrawable card(int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(S1);
        background.setCornerRadius(dpStatic(radiusDp));
        background.setStroke(1, LINE);
        return background;
    }

    public static GradientDrawable surface(Context context, int color, int radius, boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radius));
        drawable.setStroke(dp(context, focused ? 2 : 1), focused ? CYAN : LINE);
        return drawable;
    }

    /** The Onda signature: cyan→violet diagonal, used for the one true primary action. */
    public static GradientDrawable aurora(Context context, int radius) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{CYAN, VIOLET});
        drawable.setCornerRadius(dp(context, radius));
        return drawable;
    }

    public static GradientDrawable auroraSoft(Context context, int radius, int alpha) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{withAlpha(CYAN, alpha), withAlpha(VIOLET, alpha)});
        drawable.setCornerRadius(dp(context, radius));
        return drawable;
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    // ------------------------------------------------- interaction
    public static void interactive(View view, int color, int radius) {
        Context context = view.getContext();
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, surface(context, color, radius, true));
        states.addState(new int[]{android.R.attr.state_selected}, surface(context, color, radius, true));
        states.addState(new int[]{android.R.attr.state_pressed}, surface(context, blend(color, CYAN, 0.10f), radius, false));
        states.addState(new int[]{-android.R.attr.state_enabled}, surface(context, S1, radius, false));
        states.addState(new int[]{}, surface(context, color, radius, false));
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(CYAN, 0x38)), states,
                surface(context, TEXT, radius, false)));
        view.setFocusable(true);
        view.setSoundEffectsEnabled(true);
    }

    /** Soft press feedback without changing the visual identity of custom backgrounds. */
    public static void ripple(View view, int radius) {
        Context context = view.getContext();
        Drawable content = view.getBackground();
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(CYAN, 0x30)),
                content, surface(context, TEXT, radius, false)));
        view.setFocusable(true);
    }

    public static int blend(int base, int over, float amount) {
        float a = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(
                Math.round(Color.red(base) * (1 - a) + Color.red(over) * a),
                Math.round(Color.green(base) * (1 - a) + Color.green(over) * a),
                Math.round(Color.blue(base) * (1 - a) + Color.blue(over) * a));
    }

    public static void roundClip(View view, int radiusDp) {
        final int radius = dp(view.getContext(), radiusDp);
        view.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline outline) {
                outline.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        view.setClipToOutline(true);
    }

    // ----------------------------------------------------------- type
    /** Display — 30 sp, tight. Screen titles. */
    public static TextView display(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(30);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(TEXT);
        view.setLetterSpacing(-0.02f);
        view.setLineSpacing(dp(context, 2), 1f);
        return view;
    }

    /** Headline — 22 sp semibold. Section anchors, collection titles. */
    public static TextView headline(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(22);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(TEXT);
        view.setLetterSpacing(-0.01f);
        view.setLineSpacing(dp(context, 3), 1f);
        return view;
    }

    /** Section heading — 17 sp semibold, with generous air above. */
    public static TextView section(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(17);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(TEXT);
        view.setPadding(0, dp(context, 24), 0, dp(context, 12));
        return view;
    }

    /** Eyebrow — 10 sp, tracked caps, cyan. The "where am I" line. */
    public static TextView eyebrow(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text.toUpperCase(Locale.ROOT));
        view.setTextSize(10);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(CYAN);
        view.setLetterSpacing(0.16f);
        view.setPadding(0, dp(context, 4), 0, dp(context, 6));
        return view;
    }

    /** Body — 14 sp regular, comfortable leading. */
    public static TextView body(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(TEXT2);
        view.setLineSpacing(dp(context, 5), 1f);
        return view;
    }

    /** Meta — 12 sp. Artist names, counts, durations. */
    public static TextView meta(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(12);
        view.setTextColor(TEXT2);
        view.setMaxLines(1);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return view;
    }

    public static TextView title(Context context, String text, int size, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(size);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(color);
        view.setMaxLines(2);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return view;
    }

    // -------------------------------------------------------- controls
    /** Primary CTA — aurora fill, ink text. Exactly one per screen region. */
    public static Button primary(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(BG);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(0xFFFFFFFF, 0x40)),
                aurora(context, 999), aurora(context, 999)));
        button.setMinHeight(dp(context, 52));
        button.setPadding(dp(context, 22), 0, dp(context, 22), 0);
        button.setGravity(Gravity.CENTER);
        return button;
    }

    /** Circular primary transport control. */
    public static Button circle(Context context, String glyph) {
        Button button = new Button(context);
        button.setText(glyph);
        button.setAllCaps(false);
        button.setTextSize(24);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(BG);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(0xFFFFFFFF, 0x40)),
                aurora(context, 999), aurora(context, 999)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(context, 64), dp(context, 64));
        button.setLayoutParams(params);
        button.setContentDescription(button.getText());
        return button;
    }

    /** Ghost / secondary — quiet surface, cyan label. */
    public static Button ghost(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(CYAN);
        interactive(button, S2, 16);
        button.setMinHeight(dp(context, TOUCH));
        button.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));
        button.setAllCaps(false);
        button.setMaxLines(2);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return button;
    }

    /** Filter chip. Active = cyan wash + bright text (not a hard block). */
    public static Button chip(Context context, String text, boolean selected) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTypeface(Typeface.create("sans-serif-medium", selected ? Typeface.BOLD : Typeface.NORMAL));
        interactive(button, selected ? withAlpha(CYAN, 0x24) : S2, 999);
        button.setTextColor(selected ? CYAN : TEXT2);
        button.setSelected(selected);
        button.setMinHeight(dp(context, 40));
        button.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        return button;
    }

    public static Button pill(Context context, String text, int background, int foreground) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(12);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(foreground);
        interactive(button, background, 999);
        button.setMinHeight(dp(context, 34));
        button.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        return button;
    }

    /** Tiny status badge. */
    public static TextView badge(Context context, String text, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(9);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(color);
        view.setAllCaps(true);
        view.setLetterSpacing(0.08f);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 7), dp(context, 3), dp(context, 7), dp(context, 3));
        view.setBackground(surface(context, withAlpha(color, 0x22), 6, false));
        return view;
    }

    // ----------------------------------------------------------- media
    public static ImageView cover(Context context, int sizeDp, int radiusDp) {
        ImageView view = new ImageView(context);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable background = new GradientDrawable();
        background.setColor(S3);
        background.setCornerRadius(dp(context, radiusDp));
        view.setBackground(background);
        view.setImageResource(R.drawable.ic_onda);
        roundClip(view, radiusDp);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp));
        view.setLayoutParams(params);
        return view;
    }

    /** Square cover that fills its parent width. */
    public static ImageView coverFill(Context context, int radiusDp) {
        ImageView view = new ImageView(context);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable background = new GradientDrawable();
        background.setColor(S3);
        background.setCornerRadius(dp(context, radiusDp));
        view.setBackground(background);
        view.setImageResource(R.drawable.ic_onda);
        roundClip(view, radiusDp);
        return view;
    }

    // -------------------------------------------------------- layout
    public static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    public static LinearLayout column(Context context) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    public static android.widget.HorizontalScrollView strip(Context context) {
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        return scroll;
    }

    /** Standard card with card background and generous inner padding. */
    public static LinearLayout cardContainer(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(card(20));
        int padding = dp(context, 16);
        box.setPadding(padding, padding, padding, padding);
        return box;
    }

    /** Section header: title on the left, optional quiet action on the right. */
    public static LinearLayout sectionHeader(Context context, String title, String action, Runnable run) {
        LinearLayout header = row(context);
        header.setPadding(0, dp(context, 20), 0, dp(context, 8));
        TextView text = section(context, title);
        text.setPadding(0, 0, 0, 0);
        header.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        if (action != null) {
            Button link = new Button(context);
            link.setText(action);
            link.setAllCaps(false);
            link.setTextSize(12);
            link.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            link.setTextColor(CYAN);
            link.setBackground(null);
            link.setPadding(dp(context, 8), 0, dp(context, 0), 0);
            link.setMinWidth(0);
            link.setMinimumWidth(0);
            link.setMinHeight(dp(context, 40));
            link.setOnClickListener(v -> run.run());
            header.addView(link);
        }
        return header;
    }

    /** Empty state: glyph, headline, explanation, optional action. */
    public static LinearLayout empty(Context context, String glyph, String title, String detail, String action, Runnable run) {
        LinearLayout box = column(context);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(context, 24), dp(context, 40), dp(context, 24), dp(context, 40));
        TextView icon = new TextView(context);
        icon.setText(glyph);
        icon.setTextSize(34);
        icon.setTextColor(withAlpha(CYAN, 0xA0));
        icon.setGravity(Gravity.CENTER);
        box.addView(icon);
        TextView heading = headline(context, title);
        heading.setGravity(Gravity.CENTER);
        heading.setPadding(0, dp(context, 14), 0, dp(context, 6));
        box.addView(heading);
        TextView text = body(context, detail);
        text.setGravity(Gravity.CENTER);
        box.addView(text);
        if (action != null) {
            Button button = primary(context, action);
            button.setOnClickListener(v -> run.run());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(context, 48));
            params.topMargin = dp(context, 18);
            box.addView(button, params);
        }
        return box;
    }

    public static String formatDuration(long seconds) { return Display.duration(seconds); }
    public static String formatClock(long ms) { return Display.clock(ms); }
    public static String formatSize(long bytes) { return Display.size(bytes); }

    public static String totalDuration(List<LibraryStore.Track> tracks) {
        long total = 0;
        int known = 0;
        for (LibraryStore.Track t : tracks) if (t.durationSec > 0) { total += t.durationSec; known++; }
        if (known == 0) return "duração indisponível";
        String base = Display.duration(total);
        return known < tracks.size() ? "~" + base : base;
    }

    /** Plural helper: songs(n) → "1 música" / "12 músicas". */
    public static String songs(int count) { return count + (count == 1 ? " música" : " músicas"); }
}
