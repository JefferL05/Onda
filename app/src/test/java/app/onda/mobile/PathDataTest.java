package app.onda.mobile;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression cover for the launch crash in 1.0.0.
 *
 * Every icon in the app is drawn from path data on the first frame. The old
 * parser required spaces between numbers and crashed on "l4.6-4.6" and on
 * repeated arc parameter sets — which is exactly the shape of Glyph.SPARK and
 * Glyph.SEARCH, so MainActivity died while building its tab bar.
 */
public class PathDataTest {

    /** Records commands so assertions can talk about the drawing, not the bytes. */
    private static final class Recorder implements PathData.Sink {
        final List<String> calls = new ArrayList<>();
        final List<float[]> points = new ArrayList<>();

        @Override
        public void moveTo(float x, float y) {
            calls.add("M");
            points.add(new float[] { x, y });
        }

        @Override
        public void lineTo(float x, float y) {
            calls.add("L");
            points.add(new float[] { x, y });
        }

        @Override
        public void cubicTo(float x1, float y1, float x2, float y2, float x, float y) {
            calls.add("C");
            points.add(new float[] { x1, y1, x2, y2, x, y });
        }

        @Override
        public void close() {
            calls.add("Z");
        }

        int count(String call) {
            int n = 0;
            for (String c : calls)
                if (c.equals(call))
                    n++;
            return n;
        }
    }

    private static Recorder parse(String data) {
        Recorder recorder = new Recorder();
        PathData.parse(data, recorder);
        return recorder;
    }

    // ------------------------------------------------- the crash itself
    @Test
    public void negativeNumberRunsIntoTheNextOneWithoutASeparator() {
        Recorder r = parse("M12 3.6 13.8 9l5.4 1.8-5.4 1.8L12 18l-1.8-5.4L4.8 10.8 10.2 9Z");
        assertTrue("glyph must produce geometry", r.calls.size() > 4);
        assertEquals(1, r.count("M"));
        assertTrue(r.count("L") >= 6);
        assertEquals(1, r.count("Z"));
    }

    @Test
    public void repeatedArcParameterSetsInsideOneCommand() {
        Recorder r = parse("M10.8 4.2a6.6 6.6 0 1 1 0 13.2 6.6 6.6 0 0 1 0-13.2Z M15.6 15.6 20.2 20.2");
        assertEquals(2, r.count("M"));
        assertEquals("two arcs plus the closing pair", 4, r.count("C"));
        assertEquals(1, r.count("Z"));
    }

    @Test
    public void everyIconInTheAppParses() {
        String[] icons = {
                Glyph.HOME, Glyph.EXPLORE, Glyph.SEARCH, Glyph.LIBRARY, Glyph.BACK, Glyph.CLOSE, Glyph.MORE,
                Glyph.PLAY, Glyph.PAUSE, Glyph.NEXT, Glyph.PREV, Glyph.SHUFFLE, Glyph.REPEAT,
                Glyph.REPEAT_ONE, Glyph.HEART, Glyph.HEART_BORDER, Glyph.PLUS, Glyph.DOWNLOAD, Glyph.DOWNLOAD_DONE,
                Glyph.RADIO, Glyph.QUEUE, Glyph.LYRICS, Glyph.SPARK, Glyph.VOLUME, Glyph.DOWN, Glyph.WIFI,
                Glyph.SETTINGS, Glyph.EQUALIZER, Glyph.BOLT, Glyph.PERSON, Glyph.SHIELD,
                Glyph.WAVE, Glyph.CHECK, Glyph.LOCK, Glyph.MIC, Glyph.TIMER,
                Glyph.CAST, Glyph.SHARE, Glyph.THUMB_DOWN
        };
        for (String icon : icons) {
            Recorder r = parse(icon);
            assertFalse("no geometry for " + icon, r.calls.isEmpty());
            assertTrue("at least one subpath start for " + icon, r.count("M") >= 1);
        }
    }

    // --------------------------------------------------------- semantics
    @Test
    public void relativeCommandsAccumulateFromTheCurrentPoint() {
        Recorder r = parse("M10 10l5-5h5v5");
        assertEquals(10f, r.points.get(0)[0], 0.001f);
        assertEquals(10f, r.points.get(0)[1], 0.001f);
        assertEquals(15f, r.points.get(1)[0], 0.001f);
        assertEquals(5f, r.points.get(1)[1], 0.001f);
        assertEquals(20f, r.points.get(2)[0], 0.001f);
        assertEquals(5f, r.points.get(2)[1], 0.001f);
        assertEquals(20f, r.points.get(3)[0], 0.001f);
        assertEquals(10f, r.points.get(3)[1], 0.001f);
    }

    @Test
    public void movetoRepeatsAsLineto() {
        Recorder r = parse("M2 2 4 4 6 2");
        assertEquals(1, r.count("M"));
        assertEquals(2, r.count("L"));
    }

    @Test
    public void closeReturnsToTheStartOfTheSubpath() {
        Recorder r = parse("M4 4l6 0Z l0 6");
        // after Z the pen is back at (4,4), so the relative line ends at (4,10)
        float[] last = r.points.get(r.points.size() - 1);
        assertEquals(4f, last[0], 0.001f);
        assertEquals(10f, last[1], 0.001f);
    }

    @Test
    public void arcHonoursEndpointsAndFlags() {
        Recorder full = parse("M4 12a10 10 0 1 1 16 0");
        Recorder half = parse("M4 12a10 10 0 0 1 16 0");
        assertTrue("large arc needs more segments", full.count("C") > half.count("C"));
        float[] end = full.points.get(full.points.size() - 1);
        assertEquals(20f, end[end.length - 2], 0.01f);
        assertEquals(12f, end[end.length - 1], 0.01f);
    }

    @Test
    public void arcWithZeroRadiusDegradesToALine() {
        Recorder r = parse("M2 2A0 5 0 0 1 8 8");
        assertEquals(1, r.count("L"));
        assertEquals(0, r.count("C"));
    }

    @Test
    public void smoothCurveReflectsThePreviousControlPoint() {
        Recorder r = parse("M0 0C2 0 4 2 4 4S8 8 8 4");
        float[] cubic = r.points.get(r.points.size() - 1);
        // previous control was (4,2) at point (4,4) → reflected (4,6)
        assertEquals(4f, cubic[0], 0.001f);
        assertEquals(6f, cubic[1], 0.001f);
    }

    // ------------------------------------------------------ robustness
    @Test
    public void malformedDataStopsInsteadOfThrowing() {
        String[] junk = { "", "   ", "M", "M 5", "L 4 4", "M4 4Q", "M4 4a6 6 0", "M4,4L,", "&%$#",
                "M4 4 C 1 2 3", "M1e 2 3", "M4 4z 9", null };
        for (String data : junk) {
            Recorder r = new Recorder();
            PathData.parse(data, r); // must not throw
        }
    }

    @Test
    public void neverLoopsForever() {
        String[] tricky = { "M4 4L", "M4 4h", "M4 4H-", "M4 4.a", "M0 0S", "M0 0T", "M0 0aaaaaaaa" };
        for (String data : tricky) {
            Recorder r = new Recorder();
            PathData.parse(data, r); // a hang here would time the build out
        }
    }

    @Test
    public void scientificNotationAndPlusSignsAreAccepted() {
        Recorder r = parse("M1e1 2e+1L+4 +5");
        assertEquals(10f, r.points.get(0)[0], 0.001f);
        assertEquals(20f, r.points.get(0)[1], 0.001f);
        assertEquals(4f, r.points.get(1)[0], 0.001f);
        assertEquals(5f, r.points.get(1)[1], 0.001f);
    }
}
