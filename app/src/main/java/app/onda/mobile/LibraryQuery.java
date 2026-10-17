package app.onda.mobile;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Search and ordering rules shared by all local collections. */
public final class LibraryQuery {
    private LibraryQuery() {}

    public static String normalized(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
    }

    public static boolean matches(String title, String query) {
        return normalized(title).contains(normalized(query));
    }

    /** Newest first, unique ignoring accents/case, bounded, without mutating the caller. */
    public static List<String> rememberSearch(List<String> previous, String query) {
        String trimmed = query.trim();
        List<String> result = new ArrayList<>(previous);
        if (trimmed.isEmpty()) return result;
        result.removeIf(item -> normalized(item).equals(normalized(trimmed)));
        result.add(0, trimmed);
        return new ArrayList<>(result.subList(0, Math.min(12, result.size())));
    }
}
