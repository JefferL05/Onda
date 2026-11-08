package app.onda.mobile;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Onda's icon set, drawn as geometry instead of bitmaps.
 *
 * A 24×24 grid, 1.8 stroke, round joins: one family, crisp at any density and
 * tintable at runtime. Glyphs are Path data so they stay tiny and resolution
 * independent; filled glyphs (transport) use the same grid.
 */
public final class Glyph {

    // ------------------------------------------------------------ path data
    public static final String HOME      = "M4 11.2 12 4l8 7.2M6 10v9.4h4.4v-5.2h3.2v5.2H18V10";
    public static final String SEARCH    = "M10.8 4.2a6.6 6.6 0 1 1 0 13.2 6.6 6.6 0 0 1 0-13.2Z M15.6 15.6 20.2 20.2";
    public static final String LIBRARY   = "M5 5v14M9.5 5v14M14 6.2l4.6 13.2";
    public static final String BACK      = "M15 5 8 12l7 7";
    public static final String CLOSE     = "M6 6 18 18M18 6 6 18";
    public static final String MORE      = "M12 5.4a1.3 1.3 0 1 1 0 2.6 1.3 1.3 0 0 1 0-2.6ZM12 10.7a1.3 1.3 0 1 1 0 2.6 1.3 1.3 0 0 1 0-2.6ZM12 16a1.3 1.3 0 1 1 0 2.6 1.3 1.3 0 0 1 0-2.6Z";

    public static final String PLAY      = "M8 5.2v13.6L19.2 12Z";
    public static final String PAUSE     = "M8.4 5.2h2.6v13.6H8.4ZM13 5.2h2.6v13.6H13Z";
    public static final String NEXT      = "M6.4 5.6v12.8L15.4 12ZM16.6 5.6h1.8v12.8h-1.8Z";
    public static final String PREV      = "M17.6 5.6v12.8L8.6 12ZM5.6 5.6h1.8v12.8H5.6Z";
    public static final String SHUFFLE   = "M4 7h2.6l8 10H20M4 17h2.6L15 7h5M16.4 4.6 20 7l-3.6 2.4M16.4 14.6 20 17l-3.6 2.4";
    public static final String REPEAT    = "M6 8.4h9.6a2.4 2.4 0 0 1 2.4 2.4v.8M18 15.6H8.4A2.4 2.4 0 0 1 6 13.2v-.8M15.6 5.8 18.4 8.4l-2.8 2.6M8.4 18.2 5.6 15.6l2.8-2.6";
    public static final String REPEAT_ONE= "M6 8.4h9.6a2.4 2.4 0 0 1 2.4 2.4v.8M18 15.6H8.4A2.4 2.4 0 0 1 6 13.2v-.8M15.6 5.8 18.4 8.4l-2.8 2.6M8.4 18.2 5.6 15.6l2.8-2.6M12 9.6v4.8M10.8 10.6 12 9.6";

    public static final String HEART     = "M12 19.6C9.1 17.8 5.2 14.7 5.2 10.4A3.6 3.6 0 0 1 12 8.2a3.6 3.6 0 0 1 6.8 2.2c0 4.3-3.9 7.4-6.8 9.2Z";
    public static final String PLUS      = "M12 5v14M5 12h14";
    public static final String DOWNLOAD  = "M12 4v10.4M7.4 10.2 12 14.8l4.6-4.6M5 19.4h14";
    public static final String RADIO     = "M12 4.6a7.4 7.4 0 1 1 0 14.8 7.4 7.4 0 0 1 0-14.8ZM12 8.4a3.6 3.6 0 1 1 0 7.2 3.6 3.6 0 0 1 0-7.2ZM12 11.3a.7.7 0 1 1 0 1.4.7.7 0 0 1 0-1.4Z";

    public static final String QUEUE     = "M4 7h10M4 12h10M4 17h7M16 16.4v-8l4-1v7.6M16 16.4a1.8 1.8 0 1 0 0 .1Z";
    public static final String LYRICS    = "M5 6.6h9.4M5 11h6.8M14 14.2h5M16.4 6 14 12.4l3.6-1.6 1.4 3.2";
    public static final String SPARK     = "M12 3.6 13.8 9l5.4 1.8-5.4 1.8L12 18l-1.8-5.4L4.8 10.8 10.2 9Z";
    public static final String VOLUME    = "M5 9.6h3l4-3.4v11.6l-4-3.4H5ZM15 9a4.2 4.2 0 0 1 0 6M17.4 7a7 7 0 0 1 0 10";
    public static final String DOWN      = "M12 5v13M6.6 12.6 12 18l5.4-5.4";
    public static final String WIFI      = "M3.6 9.6a13 13 0 0 1 16.8 0M6.6 13a8.4 8.4 0 0 1 10.8 0M9.6 16.4a3.8 3.8 0 0 1 4.8 0M12 19.2v.2";
    public static final String SETTINGS  = "M12 9a3 3 0 1 1 0 6 3 3 0 0 1 0-6ZM19.2 12a7.2 7.2 0 0 0-.2-1.6l2-1.6-1.8-3.1-2.4 1a7.2 7.2 0 0 0-2.8-1.6L13.6 2h-3.2l-.4 2.6a7.2 7.2 0 0 0-2.8 1.6l-2.4-1L3 8.8l2 1.6a7.2 7.2 0 0 0 0 3.2l-2 1.6 1.8 3.1 2.4-1a7.2 7.2 0 0 0 2.8 1.6l.4 2.6h3.2l.4-2.6a7.2 7.2 0 0 0 2.8-1.6l2.4 1 1.8-3.1-2-1.6a7.2 7.2 0 0 0 .2-1.6Z";
    public static final String EQUALIZER = "M4 19v-6M4 9V5M12 19v-2M12 13V5M20 19v-10M20 5v0M2 13h4M10 17h4M18 9h4";
    public static final String BOLT      = "M13 2 4 14h6l-1 8 9-12h-6l1-8Z";
    public static final String PERSON    = "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM4 20a8 8 0 0 1 16 0";
    public static final String SHIELD    = "M12 3 4 6v6c0 5 3.4 9.7 8 11 4.6-1.3 8-6 8-11V6l-8-3Z";
    public static final String WAVE      = "M3 12h2l2-6 3 12 3-10 2 6 2-3 2 1h3";
    public static final String CHECK     = "M5 13l4 4L19 7";
    public static final String LOCK      = "M7 11V7a5 5 0 0 1 10 0v4M5 11h14v10H5z";
    public static final String MIC       = "M12 2a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3zM19 10v1a7 7 0 0 1-14 0v-1M12 18v4M8 22h8";
    public static final String TIMER     = "M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8 8 8 0 0 1-8 8zm1-13h-2v6l5.25 3.15.75-1.23-4-2.37z";

    private Glyph() {}

    // ------------------------------------------------------------- drawing
    /**
     * Parses path data into a Path. Results are cached: the same icon string is
     * reused across tabs, rows and rebuilds, and parsing on every list row was
     * pure waste. Parse failures yield an empty path — never an exception — so a
     * malformed glyph cannot take the process down.
     */
    static Path path(String data) {
        if (data == null) return new Path();
        Path cached = PATHS.get(data);
        if (cached != null) return cached;
        final Path path = new Path();
        try {
            PathData.parse(data, new PathData.Sink() {
                @Override public void moveTo(float x, float y) { path.moveTo(x, y); }
                @Override public void lineTo(float x, float y) { path.lineTo(x, y); }
                @Override public void cubicTo(float x1, float y1, float x2, float y2, float x, float y) {
                    path.cubicTo(x1, y1, x2, y2, x, y);
                }
                @Override public void close() { path.close(); }
            });
        } catch (RuntimeException ignored) { /* draw nothing rather than crash */ }
        PATHS.put(data, path);
        return path;
    }

    private static final java.util.Map<String, Path> PATHS = new java.util.concurrent.ConcurrentHashMap<>();

    public static Drawable drawable(String data, int color, boolean filled) {
        return new Ink(data, color, filled, 1.8f);
    }

    public static Drawable drawable(String data, int color) { return drawable(data, color, false); }

    public static Drawable filled(String data, int color) { return drawable(data, color, true); }

    /** Icon on a 24 unit grid, scaled to `sizeDp`. */
    public static android.widget.ImageView view(android.content.Context context, String data, int sizeDp, int color) {
        android.widget.ImageView view = new android.widget.ImageView(context);
        view.setImageDrawable(drawable(data, color));
        view.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        int px = dp(context, sizeDp);
        view.setLayoutParams(new android.widget.LinearLayout.LayoutParams(px, px));
        view.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    public static int dp(android.content.Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    /** Stroke/fill drawable over the 24 unit grid. */
    public static final class Ink extends Drawable {
        private final Path path;
        private final Paint paint;
        private final boolean filled;

        Ink(String data, int color, boolean filled, float stroke) {
            this.path = path(data);
            this.filled = filled;
            this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            this.paint.setColor(color);
            this.paint.setStrokeWidth(stroke);
            this.paint.setStrokeCap(Paint.Cap.ROUND);
            this.paint.setStrokeJoin(Paint.Join.ROUND);
            this.paint.setStyle(filled ? Paint.Style.FILL : Paint.Style.STROKE);
        }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float scale = Math.min(bounds.width(), bounds.height()) / 24f;
            int save = canvas.save();
            canvas.translate(bounds.left + (bounds.width() - 24 * scale) / 2f,
                    bounds.top + (bounds.height() - 24 * scale) / 2f);
            canvas.scale(scale, scale);
            canvas.drawPath(path, paint);
            canvas.restoreToCount(save);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }

        @Override public void setColorFilter(ColorFilter colorFilter) { paint.setColorFilter(colorFilter); }

        @Override public int getOpacity() { return filled ? PixelFormat.TRANSLUCENT : PixelFormat.TRANSLUCENT; }

        @Override public int getIntrinsicWidth() { return 24; }

        @Override public int getIntrinsicHeight() { return 24; }
    }

    /** Rounded-rect helper for custom artwork (album placeholders, wave strips). */
    static void roundRect(Canvas canvas, Paint paint, RectF rect, float radius) {
        canvas.drawRoundRect(rect, radius, radius, paint);
    }
}
