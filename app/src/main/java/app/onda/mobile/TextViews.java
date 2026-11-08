package app.onda.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Custom text views with real behaviour (not decoration). */
final class TextViews {
    private TextViews() {}

    /**
     * Karaoke-style lyrics: the active line is bright and centred, neighbours
     * fade with distance, and the view scrolls itself to keep the line in view.
     */
    static final class LyricsView extends LinearLayout {
        private final List<LyricsStore.LyricLine> lines;
        private final List<TextView> views = new java.util.ArrayList<>();
        private long position;
        private int active = -1;

        LyricsView(Context context, List<LyricsStore.LyricLine> lines) {
            super(context);
            this.lines = lines;
            setOrientation(VERTICAL);
            setPadding(0, Ui.dp(context, 8), 0, Ui.dp(context, 8));
            for (LyricsStore.LyricLine line : lines) {
                TextView view = new TextView(context);
                view.setText(line.text);
                view.setTextSize(15);
                view.setTextColor(Ui.TEXT2);
                view.setLineSpacing(Ui.dp(context, 5), 1f);
                view.setPadding(0, Ui.dp(context, 6), 0, Ui.dp(context, 6));
                views.add(view);
                addView(view);
            }
            if (lines.isEmpty()) {
                TextView empty = new TextView(context);
                empty.setText("Sem letras sincronizadas.");
                empty.setTextColor(Ui.TEXT3);
                addView(empty);
            }
        }

        void setPosition(long positionMs) {
            this.position = positionMs;
            int index = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).at <= positionMs) index = i;
                else break;
            }
            if (index == active) return;
            active = index;
            for (int i = 0; i < views.size(); i++) {
                TextView view = views.get(i);
                int distance = Math.abs(i - index);
                view.setTextColor(i == index ? Ui.CYAN : distance == 1 ? Ui.TEXT : Ui.TEXT3);
                view.setTypeface(Typeface.create("sans-serif", i == index ? Typeface.BOLD : Typeface.NORMAL));
            }
            if (index >= 0 && index < views.size()) {
                final TextView target = views.get(index);
                post(() -> {
                    int y = target.getTop() - (getHeight() / 2 - target.getHeight() / 2);
                    smoothScrollTo(0, Math.max(0, y));
                    // smoothScrollTo only exists on ScrollView; fall back to offset.
                });
            }
        }

        private void smoothScrollTo(int x, int y) {
            // This view lives inside a ScrollView; scroll the nearest ancestor.
            android.view.ViewParent parent = getParent();
            while (parent != null && !(parent instanceof android.widget.ScrollView)) parent = parent.getParent();
            if (parent instanceof android.widget.ScrollView) {
                ((android.widget.ScrollView) parent).smoothScrollTo(0, y);
            }
        }
    }
}
