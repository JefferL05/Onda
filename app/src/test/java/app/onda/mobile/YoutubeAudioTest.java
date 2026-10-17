package app.onda.mobile;

import org.junit.Test;
import org.junit.Assume;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import java.util.Arrays;
import java.util.Collections;
import java.net.HttpURLConnection;
import java.net.URL;
import static org.junit.Assert.*;

public class YoutubeAudioTest {
    private AudioStream stream(MediaFormat format, int bitrate, DeliveryMethod method) {
        return new AudioStream.Builder().setId("test").setContent("https://example.com/audio", true)
                .setMediaFormat(format).setAverageBitrate(bitrate).setDeliveryMethod(method).build();
    }
    @Test public void prefersCompatibleM4a() {
        AudioStream m4a = stream(MediaFormat.M4A, 128, DeliveryMethod.PROGRESSIVE_HTTP);
        AudioStream webm = stream(MediaFormat.WEBMA_OPUS, 160, DeliveryMethod.PROGRESSIVE_HTTP);
        assertSame(m4a, YoutubeAudio.select(Arrays.asList(webm, m4a)));
    }
    @Test public void rejectsManifestsAndUnknownFormats() {
        assertNull(YoutubeAudio.select(Arrays.asList(stream(MediaFormat.M4A, 128, DeliveryMethod.DASH),
                stream(null, 128, DeliveryMethod.PROGRESSIVE_HTTP))));
        assertNull(YoutubeAudio.select(Collections.emptyList()));
    }
    @Test public void livePublicVideoExtractionAndMediaProbe() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("onda.liveTests"));
        YoutubeAudio.Source source = YoutubeAudio.resolve("https://www.youtube.com/watch?v=aqz-KE-bpKQ");
        assertNotNull(source.url);
        HttpURLConnection connection = (HttpURLConnection) new URL(source.url).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", ExtractorHttp.USER_AGENT);
        connection.setRequestProperty("Range", "bytes=0-65535");
        try {
            int code = connection.getResponseCode();
            assertTrue("Media HTTP " + code, code == 200 || code == 206);
            try (java.io.InputStream in = connection.getInputStream()) {
                byte[] header = new byte[16];
                int count = in.read(header);
                assertTrue("Media header", count >= 8);
                System.out.println("LIVE: stream resolved and media bytes received, format=" + source.extension);
            }
        } finally { connection.disconnect(); }
    }

    @Test public void liveCompleteAudioDownload() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("onda.liveTests"));
        YoutubeAudio.Source source = YoutubeAudio.resolve("https://www.youtube.com/watch?v=aqz-KE-bpKQ");
        java.nio.file.Path directory = java.nio.file.Files.createTempDirectory("onda-audio-test");
        java.io.File file = directory.resolve("sample." + source.extension).toFile();
        try {
            long bytes = AudioDownload.transfer(source.url, file, new java.util.concurrent.atomic.AtomicBoolean(), (n, total) -> {});
            assertTrue(bytes > 1024);
            assertEquals(bytes, file.length());
            System.out.println("LIVE: complete audio download validated, bytes=" + bytes);
        } finally {
            java.nio.file.Files.deleteIfExists(file.toPath());
            java.nio.file.Files.deleteIfExists(directory);
        }
    }
}
