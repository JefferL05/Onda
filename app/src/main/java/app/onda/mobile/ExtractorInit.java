package app.onda.mobile;

import org.schabi.newpipe.extractor.NewPipe;

/** Single NewPipe init shared by search, resolve and download paths. */
public final class ExtractorInit {
    private static volatile boolean ready;

    private ExtractorInit() {}

    public static void ensure() {
        if (ready) return;
        synchronized (ExtractorInit.class) {
            if (ready) return;
            NewPipe.init(new ExtractorHttp());
            ready = true;
        }
    }
}
