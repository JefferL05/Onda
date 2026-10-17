package app.onda.mobile;

final class LyricsRules {
    private LyricsRules() { }
    static String normalize(String value) { return value == null ? "" : value.trim(); }
    static boolean canStore(String value, int max) {
        String normalized = normalize(value);
        return !normalized.isEmpty() && normalized.length() <= max;
    }
}
