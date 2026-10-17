package app.onda.mobile;

import org.junit.Test;
import org.junit.Assume;
import org.schabi.newpipe.extractor.Image;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class SearchRulesTest {
    private Image image(String url, int height) {
        return new Image(url, height, height * 16 / 9, Image.ResolutionLevel.fromHeight(height));
    }
    @Test public void prefersLargestHttpsThumbnail() {
        String best = YoutubeSearch.bestThumbnail(Arrays.asList(
                image("http://insecure.example/cover.jpg", 1000),
                image("https://i.ytimg.com/vi/abc/hqdefault.jpg", 360),
                image("https://i.ytimg.com/vi/abc/maxresdefault.jpg", 720)));
        assertEquals("https://i.ytimg.com/vi/abc/maxresdefault.jpg", best);
    }
    @Test public void rejectsEmptyOrInsecureThumbnails() {
        assertEquals("", YoutubeSearch.bestThumbnail(Collections.emptyList()));
        assertEquals("", YoutubeSearch.bestThumbnail(
                Collections.singletonList(image("", 100))));
    }
    @Test public void downloadQueueDedupsAndLimitsRetries() {
        DownloadQueue queue = new DownloadQueue(3);
        String url = "https://m.youtube.com/watch?v=aqz-KE-bpKQ";
        assertSame(queue.enqueue(url, "Faixa"), queue.enqueue(url, "Faixa"));
        queue.markActive(OfflineStore.id(url));
        queue.markFailed(OfflineStore.id(url), "rede");
        assertTrue(queue.retry(OfflineStore.id(url)));
        queue.markFailed(OfflineStore.id(url), "rede");
        queue.retry(OfflineStore.id(url));
        queue.markFailed(OfflineStore.id(url), "rede");
        assertFalse(queue.retry(OfflineStore.id(url)));
    }
    @Test public void wifiOnlyGatesPendingJobs() {
        DownloadQueue queue = new DownloadQueue();
        queue.enqueue("https://m.youtube.com/watch?v=aqz-KE-bpKQ", "Faixa");
        assertNull(queue.nextPending(true, false));
        assertNotNull(queue.nextPending(true, true));
        assertNotNull(queue.nextPending(false, false));
    }
    @Test public void durationFormattingCoversHours() {
        assertEquals("3:05", Display.duration(185));
        assertEquals("1:02:03", Display.duration(3723));
        assertEquals("—", Display.duration(-1));
    }
    @Test public void liveNativeSearchReturnsCovers() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("onda.liveTests"));
        java.util.List<YoutubeSearch.Result> results = YoutubeSearch.search("Tom Jobim Aguas de Marco", YoutubeSearch.FILTER_SONGS, 5);
        assertFalse(results.isEmpty());
        YoutubeSearch.Result first = results.get(0);
        assertNotNull(UrlPolicy.canonicalVideo(first.url));
        assertFalse(first.title.isEmpty());
        System.out.println("LIVE: search returned " + results.size() + " items, first=" + first.title);
    }
}
