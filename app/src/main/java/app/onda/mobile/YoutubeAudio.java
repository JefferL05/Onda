package app.onda.mobile;

import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamExtractor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves a video into playable audio. Picks the highest-quality compatible
 * progressive stream, normalises metadata, and exposes alternate audio
 * qualities so the player can try the next one when a source goes stale.
 */
public final class YoutubeAudio {
    public static final class Source {
        public final String url;
        public final String title;
        public final String uploader;
        public final String thumbnail;
        public final String extension;
        public final int bitrate;
        public final String album;
        public final long durationSec;
        public final List<String> alternates;

        Source(String url, String title, String uploader, String thumbnail, String extension, int bitrate,
               String album, long durationSec, List<String> alternates) {
            this.url = url;
            this.title = title;
            this.uploader = uploader;
            this.thumbnail = thumbnail;
            this.extension = extension;
            this.bitrate = bitrate;
            this.album = album == null ? "" : album;
            this.durationSec = durationSec;
            this.alternates = alternates == null ? new ArrayList<>() : alternates;
        }
    }

    public static synchronized Source resolve(String pageUrl) throws Exception {
        return resolve(pageUrl, true);
    }

    /** Re-resolves after a source failure, preferring a different quality. */
    public static synchronized Source resolveAlternate(String pageUrl, int preferBitrate) throws Exception {
        String canonical = UrlPolicy.canonicalVideo(pageUrl);
        if (canonical == null) throw new IOException("Link inválido.");
        ExtractorInit.ensure();
        StreamExtractor extractor = ServiceList.YouTube.getStreamExtractor(canonical);
        extractor.fetchPage();
        List<AudioStream> streams = extractor.getAudioStreams();
        AudioStream selected = selectNearest(streams, preferBitrate);
        if (selected == null) throw new IOException("Este vídeo não oferece um arquivo de áudio compatível.");
        return build(extractor, streams, selected);
    }

    public static synchronized Source resolve(String pageUrl, boolean preferHigh) throws Exception {
        String canonical = UrlPolicy.canonicalVideo(pageUrl);
        if (canonical == null) throw new IOException("Abra um vídeo do YouTube antes de baixar.");
        ExtractorInit.ensure();
        StreamExtractor extractor = ServiceList.YouTube.getStreamExtractor(canonical);
        extractor.fetchPage();
        List<AudioStream> streams = extractor.getAudioStreams();
        AudioStream selected = select(streams, preferHigh);
        if (selected == null) throw new IOException("Este vídeo não oferece um arquivo de áudio compatível para download.");
        return build(extractor, streams, selected);
    }

    private static Source build(StreamExtractor extractor, List<AudioStream> streams, AudioStream selected) {
        String extension = selected.getFormat() == MediaFormat.M4A ? "m4a" : "webm";
        String uploader = "";
        try { uploader = extractor.getUploaderName() == null ? "" : extractor.getUploaderName(); } catch (Exception ignored) { }
        String thumbnail = "";
        try { thumbnail = YoutubeSearch.bestThumbnail(extractor.getThumbnails()); } catch (Exception ignored) { }
        String title = "Áudio do YouTube";
        try { title = extractor.getName(); } catch (Exception ignored) { }
        String album = "";
        try {
            String category = extractor.getCategory();
            // Only treat clearly musical categories as album-bearing metadata.
            if (category != null) {
                String value = category.trim();
                if (value.equalsIgnoreCase("Music") || value.equalsIgnoreCase("Música")) album = value;
            }
        } catch (Exception ignored) { }
        long duration = 0;
        try { duration = extractor.getLength(); } catch (Exception ignored) { }
        List<String> alternates = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (AudioStream stream : streams) {
            if (!compatible(stream) || stream.getContent().equals(selected.getContent())) continue;
            if (seen.put(stream.getContent(), Boolean.TRUE) == null && alternates.size() < 4) alternates.add(stream.getContent());
        }
        return new Source(selected.getContent(), title, uploader, thumbnail, extension,
                Math.max(0, selected.getAverageBitrate()), album, duration, alternates);
    }

    static boolean compatible(AudioStream stream) {
        if (stream == null || !stream.isUrl()) return false;
        MediaFormat format = stream.getFormat();
        return stream.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP
                && (format == MediaFormat.M4A || format == MediaFormat.WEBMA || format == MediaFormat.WEBMA_OPUS)
                && !UrlPolicy.host(stream.getContent()).isEmpty();
    }

    static AudioStream select(List<AudioStream> streams) {
        return select(streams, true);
    }

    static AudioStream select(List<AudioStream> streams, boolean preferHigh) {
        AudioStream best = null;
        int bestScore = Integer.MIN_VALUE;
        if (streams == null) return null;
        for (AudioStream stream : streams) {
            if (!compatible(stream)) continue;
            int bitrate = Math.max(0, stream.getAverageBitrate());
            // M4A/AAC is the most compatible container on Android; then bitrate.
            int score = (stream.getFormat() == MediaFormat.M4A ? 20000 : 0)
                    + (preferHigh ? bitrate : Math.max(0, 200000 - Math.min(bitrate, 200000)));
            if (best == null || score > bestScore) { best = stream; bestScore = score; }
        }
        return best;
    }

    /** Closest compatible stream by bitrate, avoiding an exact repeat of the failed one. */
    static AudioStream selectNearest(List<AudioStream> streams, int bitrate) {
        AudioStream best = null;
        int bestDelta = Integer.MAX_VALUE;
        if (streams == null) return null;
        for (AudioStream stream : streams) {
            if (!compatible(stream)) continue;
            int delta = Math.abs(Math.max(0, stream.getAverageBitrate()) - bitrate);
            if (best == null || delta < bestDelta) { best = stream; bestDelta = delta; }
        }
        return best;
    }

    /** True when a resolved CDN URL is still within its signed lifetime. */
    static boolean looksExpired(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            String query = uri.getRawQuery();
            if (query == null) return false;
            for (String pair : query.split("&")) {
                if (!pair.startsWith("expire=")) continue;
                long expiry = Long.parseLong(pair.substring(7));
                return expiry * 1000L < System.currentTimeMillis() + 5000L;
            }
        } catch (Exception ignored) { }
        return false;
    }
}
