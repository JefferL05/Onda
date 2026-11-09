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
 * Onda's icon set, powered by official Google Fonts Material Symbols / Material Icons models.
 *
 * All icons conform to the standard 24x24 grid. Rendered as crisp, resolution-independent
 * vector paths with Paint.Style.FILL (as specified by Google Fonts Material Symbols) and
 * Path.FillType.EVEN_ODD for perfect compound path cut-outs.
 */
public final class Glyph {

    // ------------------------------------------------------------ path data (Google Fonts Material Symbols)
    public static final String HOME         = "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z";
    public static final String EXPLORE      = "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm-5.5-2.5l7.51-3.49L17.5 6.5 9.99 9.99 6.5 17.5zm5.5-6.6c.61 0 1.1.49 1.1 1.1s-.49 1.1-1.1 1.1-1.1-.49-1.1-1.1.49-1.1 1.1-1.1z";
    public static final String SEARCH       = "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z";
    public static final String LIBRARY      = "M20 2H8c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-2 5h-3v5.5c0 1.38-1.12 2.5-2.5 2.5S10 13.88 10 12.5s1.12-2.5 2.5-2.5c.57 0 1.08.19 1.5.51V5h4v2zM4 6H2v14c0 1.1.9 2 2 2h14v-2H4V6z";
    public static final String BACK         = "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z";
    public static final String CLOSE        = "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z";
    public static final String MORE         = "M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z";

    public static final String PLAY         = "M8 5v14l11-7z";
    public static final String PAUSE        = "M6 19h4V5H6v14zm8-14v14h4V5h-4z";
    public static final String NEXT         = "M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z";
    public static final String PREV         = "M6 6h2v12H6zm3.5 6l8.5 6V6z";
    public static final String SHUFFLE      = "M10.59 9.17L5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 5.41 20 17.96 7.46 20 9.5V4h-5.5zm.33 9.41l-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l-2.04 2.04-3.13-3.13z";
    public static final String REPEAT       = "M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z";
    public static final String REPEAT_ONE   = "M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4zm-4-2V9h-1l-2 1v1h1.5v4H13z";

    public static final String HEART        = "M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z";
    public static final String HEART_BORDER = "M16.5 3c-1.74 0-3.41.81-4.5 2.09C10.91 3.81 9.24 3 7.5 3 4.42 3 2 5.42 2 8.5c0 3.78 3.4 6.86 8.55 11.54L12 21.35l1.45-1.32C18.6 15.36 22 12.28 22 8.5 22 5.42 19.58 3 16.5 3zm-4.4 15.55l-.1.1-.1-.1C7.14 14.24 4 11.39 4 8.5 4 6.5 5.5 5 7.5 5c1.54 0 3.04.99 3.57 2.36h1.87C13.46 5.99 14.96 5 16.5 5c2 0 3.5 1.5 3.5 3.5 0 2.89-3.14 5.74-7.9 10.05z";
    public static final String PLUS         = "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z";
    public static final String DOWNLOAD     = "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z";
    public static final String DOWNLOAD_DONE= "M20.13 5.41l-1.41-1.41-9.19 9.19-4.24-4.24-1.41 1.41 5.65 5.65 10.6-10.6zM5 19v2h14v-2H5z";
    public static final String RADIO        = "M3.24 6.15C2.51 6.43 2 7.17 2 8v12c0 1.1.9 2 2 2h16c1.11 0 2-.9 2-2V8c0-1.11-.89-2-2-2H8.3l8.26-3.34L15.88 1 3.24 6.15zM7 20c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm13-8h-2v-2h-2v2H4V8h16v4z";

    public static final String QUEUE        = "M15 6H3v2h12V6zm0 4H3v2h12v-2zM3 16h8v-2H3v2zM17 6v8.18c-.31-.11-.65-.18-1-.18-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3V8h3V6h-5z";
    public static final String LYRICS       = "M15 4H5v16h14V8h-4V4zm-2 14c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-7H8V9h8v2zm0-4H8V5h8v2z";
    public static final String SPARK        = "M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9zm-7.5.5L9 4 6.5 9.5 1 12l5.5 2.5L9 20l2.5-5.5L17 12l-5.5-2.5zM19 15l-1.25 2.75L15 19l2.75 1.25L19 23l1.25-2.75L23 19l-2.75-1.25L19 15z";
    public static final String VOLUME       = "M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z";
    public static final String DOWN         = "M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6 1.41-1.41z";
    public static final String WIFI         = "M12 4C7.31 4 3.07 5.9 0 8.98L12 21 24 8.98A16.88 16.88 0 0 0 12 4zm0 6c-2.76 0-5.26 1.12-7.07 2.93L12 20.09l7.07-7.16A9.97 9.97 0 0 0 12 10z";
    public static final String SETTINGS     = "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z";
    public static final String EQUALIZER    = "M10 20h4V4h-4v16zm-6 0h4v-8H4v8zM16 9v11h4V9h-4z";
    public static final String BOLT         = "M11 21h-1l1-7H7.5c-.88 0-.33-.75-.31-.78C8.48 10.94 10.42 7.54 13.01 3h1l-1 7h3.51c.4 0 .62.19.4.54L11 21z";
    public static final String PERSON       = "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z";
    public static final String SHIELD       = "M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm0 10.99h7c-.53 4.12-3.28 7.79-7 8.94V12H5V6.3l7-3.11v8.8z";
    public static final String WAVE         = "M12 3v10.55c-.59-.34-1.27-.55-2-.55-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4V7h4V3h-6z";
    public static final String CHECK        = "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z";
    public static final String LOCK         = "M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z";
    public static final String MIC          = "M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z";
    public static final String TIMER        = "M15 1H9v2h6V1zm-4 13h2V8h-2v6zm8.03-6.61l1.42-1.42c-.43-.51-.9-.99-1.41-1.41l-1.42 1.42C16.07 4.74 14.12 4 12 4c-4.97 0-9 4.03-9 9s4.02 9 9 9 9-4.03 9-9c0-2.12-.74-4.07-1.97-5.61zM12 20c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z";
    public static final String CAST         = "M21 3H3c-1.1 0-2 .9-2 2v3h2V5h18v14h-7v2h7c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zM1 18v3h3c0-1.66-1.34-3-3-3zm0-4v2c2.76 0 5 2.24 5 5h2c0-3.87-3.13-7-7-7zm0-4v2c4.97 0 9 4.03 9 9h2c0-6.08-4.93-11-11-11z";
    public static final String SHARE        = "M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92 1.61 0 2.92-1.31 2.92-2.92s-1.31-2.92-2.92-2.92z";
    public static final String THUMB_DOWN   = "M15 3H6c-.83 0-1.54.5-1.84 1.22l-3.02 7.05c-.09.23-.14.47-.14.73v2c0 1.1.9 2 2 2h6.31l-.95 4.57-.03.32c0 .41.17.79.44 1.06L9.83 23l6.59-6.59c.36-.36.58-.86.58-1.41V5c0-1.1-.9-2-2-2zm4 0v12h4V3h-4z";

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
        path.setFillType(Path.FillType.EVEN_ODD);
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

    public static Drawable drawable(String data, int color) { return drawable(data, color, true); }

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

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

        @Override public int getIntrinsicWidth() { return 24; }

        @Override public int getIntrinsicHeight() { return 24; }
    }

    /** Rounded-rect helper for custom artwork (album placeholders, wave strips). */
    static void roundRect(Canvas canvas, Paint paint, RectF rect, float radius) {
        canvas.drawRoundRect(rect, radius, radius, paint);
    }
}
