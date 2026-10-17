package app.onda.mobile;

import java.util.Locale;

/** Pure formatting shared by UI and tests, without Android dependencies. */
public final class Display {
    private Display() {}

    public static String duration(long seconds) {
        if (seconds < 0) return "—";
        long minutes = seconds / 60;
        if (minutes < 60) return String.format(Locale.ROOT, "%d:%02d", minutes, seconds % 60);
        return String.format(Locale.ROOT, "%d:%02d:%02d", minutes / 60, minutes % 60, seconds % 60);
    }

    public static String clock(long ms) {
        if (ms < 0) ms = 0;
        long totalSec = ms / 1000;
        long minutes = totalSec / 60;
        if (minutes < 60) return String.format(Locale.ROOT, "%d:%02d", minutes, totalSec % 60);
        return String.format(Locale.ROOT, "%d:%02d:%02d", minutes / 60, minutes % 60, totalSec % 60);
    }

    public static String size(long bytes) {
        if (bytes < 0) return "—";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
    }
}
