package app.onda.mobile;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import java.io.IOException;
import java.util.List;

public final class YoutubeAudio {
    public static final class Source {
        public final String url;
        public final String title;
        public final String uploader;
        public final String thumbnail;
        public final String extension;
        public final int bitrate;
        Source(String url, String title, String uploader, String thumbnail, String extension, int bitrate) {
            this.url = url; this.title = title; this.uploader = uploader; this.thumbnail = thumbnail;
            this.extension = extension; this.bitrate = bitrate;
        }
    }

    public static synchronized Source resolve(String pageUrl) throws Exception {
        return resolve(pageUrl, true);
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
        String extension = selected.getFormat() == MediaFormat.M4A ? "m4a" : "webm";
        String uploader = "";
        try { uploader = extractor.getUploaderName() == null ? "" : extractor.getUploaderName(); } catch (Exception ignored) { }
        String thumbnail = "";
        try { thumbnail = YoutubeSearch.bestThumbnail(extractor.getThumbnails()); } catch (Exception ignored) { }
        return new Source(selected.getContent(), extractor.getName(), uploader, thumbnail, extension,
                Math.max(0, selected.getAverageBitrate()));
    }

    static AudioStream select(List<AudioStream> streams) {
        return select(streams, true);
    }

    static AudioStream select(List<AudioStream> streams, boolean preferHigh) {
        AudioStream best = null;
        int bestScore = 0;
        boolean first = true;
        for (AudioStream stream : streams) {
            MediaFormat format = stream.getFormat();
            if (!stream.isUrl() || stream.getDeliveryMethod() != DeliveryMethod.PROGRESSIVE_HTTP
                    || !(format == MediaFormat.M4A || format == MediaFormat.WEBMA || format == MediaFormat.WEBMA_OPUS)
                    || UrlPolicy.host(stream.getContent()).isEmpty()) continue;
            int bitrate = Math.max(0, stream.getAverageBitrate());
            int score = (format == MediaFormat.M4A ? 10000 : 0) + (preferHigh ? bitrate : 1000000 - Math.min(bitrate, 1000000));
            if (first || score > bestScore) { best = stream; bestScore = score; first = false; }
        }
        return best;
    }
}
