package app.onda.mobile;

import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.MockResponse;
import okio.Buffer;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class AudioDownloadTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private MockWebServer server;
    private String url;
    private final byte[] media = new byte[128 * 1024];

    @Before public void setup() throws Exception {
        media[4] = 'f'; media[5] = 't'; media[6] = 'y'; media[7] = 'p';
        server = new MockWebServer();
        server.start();
        url = server.url("/audio").toString();
    }
    @After public void cleanup() throws Exception { server.shutdown(); }
    private void response(int code, String type, byte[] data, String range) {
        MockResponse response = new MockResponse().setResponseCode(code).setHeader("Content-Type", type)
                .setBody(new Buffer().write(data));
        if (range != null) response.setHeader("Content-Range", range);
        server.enqueue(response);
    }
    @Test public void completeFilePublishedOnlyAfterTransfer() throws Exception {
        response(200, "audio/mp4", media, null);
        File target = new File(folder.getRoot(), "song.m4a");
        long size = AudioDownload.transfer(url, target, new AtomicBoolean(), (n, total) -> {});
        assertEquals(media.length, size);
        assertArrayEquals(media, Files.readAllBytes(target.toPath()));
        assertFalse(new File(target + ".part").exists());
    }
    @Test public void cancellationRemovesPartialFile() throws Exception {
        response(200, "audio/mp4", media, null);
        File target = new File(folder.getRoot(), "cancelled.m4a");
        AtomicBoolean cancellation = new AtomicBoolean();
        assertThrows(java.io.IOException.class, () -> AudioDownload.transfer(url, target, cancellation, (n, total) -> cancellation.set(true)));
        assertFalse(target.exists());
        assertFalse(new File(target + ".part").exists());
    }
    @Test public void htmlErrorIsNotStoredAsMusic() throws Exception {
        response(200, "text/html", "<html>error</html>".getBytes(), null);
        File target = new File(folder.getRoot(), "error.m4a");
        assertThrows(java.io.IOException.class, () -> AudioDownload.transfer(url, target, new AtomicBoolean(), (n, t) -> {}));
        assertFalse(target.exists());
    }
    @Test public void partialRangeCannotBeMarkedComplete() throws Exception {
        response(206, "audio/mp4", media, "bytes 0-131071/999999");
        File target = new File(folder.getRoot(), "partial.m4a");
        assertThrows(java.io.IOException.class, () -> AudioDownload.transfer(url, target, new AtomicBoolean(), (n, t) -> {}));
        assertFalse(target.exists());
    }
    @Test public void existingDownloadIsNotOverwritten() throws Exception {
        File target = folder.newFile("existing.m4a");
        Files.write(target.toPath(), new byte[]{1, 2, 3});
        assertThrows(java.io.IOException.class, () -> AudioDownload.transfer(url, target, new AtomicBoolean(), (n, t) -> {}));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(target.toPath()));
    }
    @Test public void invalidContainerCannotBeMarkedComplete() throws Exception {
        response(200, "application/octet-stream", new byte[64], null);
        File target = new File(folder.getRoot(), "invalid.m4a");
        assertThrows(java.io.IOException.class, () -> AudioDownload.transfer(url, target, new AtomicBoolean(), (n, t) -> {}));
        assertFalse(target.exists());
        assertFalse(new File(target + ".part").exists());
    }
}
