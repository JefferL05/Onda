package app.onda.mobile;

final class PlaybackPolicy {
    enum Source { LOCAL, NETWORK, UNAVAILABLE }
    static Source source(boolean localFileValid, boolean networkAvailable) {
        return localFileValid ? Source.LOCAL : networkAvailable ? Source.NETWORK : Source.UNAVAILABLE;
    }
    static int resumePosition(int saved, int duration) {
        if (duration <= 0) return Math.max(0, saved);
        return saved >= duration ? 0 : Math.max(0, saved);
    }
}
