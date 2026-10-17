package app.onda.mobile;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class PlaybackQueueTest {
    private PlaybackQueue queue(String... urls) {
        PlaybackQueue queue = new PlaybackQueue(new Random(7)); queue.sync(Arrays.asList(urls)); return queue;
    }
    @Test public void normalCompletionAdvancesAndStopsAtEnd() {
        PlaybackQueue q = queue("a", "b", "c");
        q.select("a"); assertEquals("b", q.next(true));
        q.select("c"); assertNull(q.next(true)); assertEquals("b", q.previous());
    }
    @Test public void repeatOneDoesNotTrapManualSkip() {
        PlaybackQueue q = queue("a", "b"); q.select("a"); q.repeat(PlaybackQueue.Repeat.ONE);
        assertEquals("a", q.next(true)); assertEquals("b", q.next(false));
    }
    @Test public void repeatAllWrapsBothDirections() {
        PlaybackQueue q = queue("a", "b"); q.repeat(PlaybackQueue.Repeat.ALL);
        q.select("b"); assertEquals("a", q.next(true));
        q.select("a"); assertEquals("b", q.previous());
    }
    @Test public void singleTrackOnlyRepeatsWhenRequested() {
        PlaybackQueue q = queue("a"); q.select("a"); assertNull(q.next(true));
        q.repeat(PlaybackQueue.Repeat.ONE); assertEquals("a", q.next(true));
        q.repeat(PlaybackQueue.Repeat.ALL); assertEquals("a", q.next(false));
    }
    @Test public void shuffleVisitsEveryTrackExactlyOnce() {
        PlaybackQueue q = queue("a", "b", "c", "d", "e"); q.select("c"); q.shuffle(true);
        assertEquals("c", q.order().get(0));
        Set<String> visited = new HashSet<>(); visited.add("c");
        String next;
        while ((next = q.next(true)) != null) { assertTrue(visited.add(next)); q.select(next); }
        assertEquals(5, visited.size());
        q.shuffle(false); assertEquals(Arrays.asList("a", "b", "c", "d", "e"), q.order());
    }
    @Test public void queueEditsPreserveTrackIdentityAndRemoveDeletedItems() {
        PlaybackQueue q = queue("a", "b", "c"); q.select("b");
        q.sync(Arrays.asList("c", "b", "d"));
        assertEquals("b", q.current()); assertEquals("d", q.next(true));
        q.sync(Arrays.asList("c", "d")); assertEquals("c", q.next(true));
        q.sync(Collections.emptyList()); assertNull(q.next(true));
    }
    @Test public void restoringShuffleKeepsPermutationAndSanitizesStaleEntries() {
        PlaybackQueue q = queue("a", "b", "c");
        q.restore(Arrays.asList("c", "a", "a", "removed"), true, PlaybackQueue.Repeat.ALL, "c");
        assertEquals(Arrays.asList("c", "a", "b"), q.order());
        assertEquals("a", q.next(true)); assertEquals("b", q.previous());
    }
    @Test public void emptyQueueAndExternalCurrentDoNotCrash() {
        PlaybackQueue q = queue(); q.repeat(PlaybackQueue.Repeat.ALL); assertNull(q.next(true)); assertNull(q.previous());
        q.sync(Arrays.asList("a", "a", "b")); q.select("elsewhere");
        assertEquals(Arrays.asList("a", "b"), q.order()); assertEquals("a", q.next(false));
    }
}
