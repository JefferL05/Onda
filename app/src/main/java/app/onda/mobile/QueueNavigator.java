package app.onda.mobile;

import java.util.List;

/** Explicit navigation only: no background autoplay or media extraction. */
public final class QueueNavigator {
    private QueueNavigator() {}

    public static int adjacent(List<String> urls, String currentUrl, int direction) {
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("direction");
        if (urls.isEmpty()) return -1;
        int index = urls.indexOf(currentUrl);
        if (index < 0) return direction > 0 ? 0 : -1;
        int next = index + direction;
        return next >= 0 && next < urls.size() ? next : -1;
    }
}
