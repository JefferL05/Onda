package app.onda.mobile;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

/** Shared professional styling: one palette, one card language, accessible touch targets. */
public final class Ui {
    public static final int BG = Color.rgb(11, 15, 18);
    public static final int PANEL = Color.rgb(22, 29, 34);
    public static final int PANEL_HIGH = Color.rgb(30, 40, 47);
    public static final int STROKE = Color.rgb(48, 63, 72);
    public static final int GREEN = Color.rgb(117, 242, 188);
    public static final int GREEN_DARK = Color.rgb(16, 60, 44);
    public static final int MUTED = Color.rgb(158, 174, 180);
    public static final int WHITE = Color.WHITE;
    public static final int WIDE_DP = 840;

    private Ui() {}

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable card() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(PANEL);
        background.setCornerRadius(28);
        background.setStroke(2, STROKE);
        return background;
    }

    public static LinearLayout cardContainer(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(card());
        int padding = dp(context, 16);
        box.setPadding(padding, padding, padding, padding);
        return box;
    }

    public static GradientDrawable surface(Context context, int color, int radius, boolean focused) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(dp(context, radius));
        drawable.setStroke(dp(context, focused ? 2 : 1), focused ? GREEN : STROKE);
        return drawable;
    }
    public static void interactive(View view, int color, int radius) {
        Context context = view.getContext();
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, surface(context, color, radius, true));
        states.addState(new int[]{android.R.attr.state_selected}, surface(context, GREEN_DARK, radius, true));
        states.addState(new int[]{}, surface(context, color, radius, false));
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3375f2bc), states,
                surface(context, WHITE, radius, false)));
        view.setFocusable(true);
    }
    public static String totalDuration(java.util.List<LibraryStore.Track> tracks) {
        long total = 0; int known = 0;
        for (LibraryStore.Track t : tracks) if (t.durationSec > 0) { total += t.durationSec; known++; }
        if (known == 0) return "Duração não disponível";
        return Display.duration(total) + (known < tracks.size() ? " conhecidos" : " no total");
    }

    public static TextView section(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(20);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(WHITE);
        view.setPadding(0, dp(context, 18), 0, dp(context, 8));
        return view;
    }

    public static TextView eyebrow(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text.toUpperCase(Locale.ROOT));
        view.setTextSize(11);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(GREEN);
        view.setLetterSpacing(0.08f);
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

    public static Button primary(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(Color.rgb(6, 20, 15));
        GradientDrawable background = new GradientDrawable();
        background.setColor(GREEN);
        background.setCornerRadius(dp(context, 16));
        interactive(button, GREEN, 16);
        button.setMinHeight(dp(context, 52));
        return button;
    }

    public static Button ghost(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(GREEN);
        interactive(button, PANEL, 12);
        button.setMinHeight(dp(context, 48));
        return button;
    }

    public static Button chip(Context context, String text, boolean selected) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        button.setTextColor(selected ? Color.rgb(6, 20, 15) : GREEN);
        GradientDrawable background = new GradientDrawable();
        background.setColor(selected ? GREEN : PANEL_HIGH);
        background.setCornerRadius(dp(context, 20));
        background.setStroke(2, selected ? GREEN : STROKE);
        interactive(button, selected ? GREEN_DARK : PANEL_HIGH, 20);
        button.setTextColor(selected ? WHITE : MUTED);
        button.setSelected(selected);
        button.setMinHeight(dp(context, 48));
        return button;
    }

    public static ImageView cover(Context context, int sizeDp, int radiusDp) {
        ImageView view = new ImageView(context);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable background = new GradientDrawable();
        background.setColor(PANEL_HIGH);
        background.setCornerRadius(dp(context, radiusDp));
        background.setStroke(2, STROKE);
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

    public static String formatDuration(long seconds) {
        return Display.duration(seconds);
    }

    public static String formatClock(long ms) {
        return Display.clock(ms);
    }

    public static String formatSize(long bytes) {
        return Display.size(bytes);
    }
}
