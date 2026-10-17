package app.onda.mobile;
import org.junit.Test;
import static org.junit.Assert.*;
public class LyricsStoreTest {
    @Test public void lyricsRulesAreExplicitAndBounded() {
        assertEquals("verso\nrefrão", LyricsRules.normalize("  verso\nrefrão  "));
        assertTrue(LyricsRules.canStore("letra", 100));
        assertFalse(LyricsRules.canStore("", 100));
        assertFalse(LyricsRules.canStore("letra", 3));
    }
}
