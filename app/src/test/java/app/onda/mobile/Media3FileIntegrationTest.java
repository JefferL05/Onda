package app.onda.mobile;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import android.os.Looper;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Real ExoPlayer + generated PCM file. Android audio output itself is simulated. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class Media3FileIntegrationTest {
    @Test public void realExoPlayerPreparesLocalWaveFile() throws Exception {
        int samples = 44100 * 2;
        ByteBuffer wave = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN);
        wave.put(new byte[]{'R','I','F','F'}).putInt(36 + samples * 2).put(new byte[]{'W','A','V','E'});
        wave.put(new byte[]{'f','m','t',' '}).putInt(16).putShort((short) 1).putShort((short) 1);
        wave.putInt(44100).putInt(88200).putShort((short) 2).putShort((short) 16);
        wave.put(new byte[]{'d','a','t','a'}).putInt(samples * 2);
        for (int i = 0; i < samples; i++) wave.putShort((short) (Math.sin(i * 440 * 2 * Math.PI / 44100) * 1000));
        File file = new File(RuntimeEnvironment.getApplication().getCacheDir(), "known good audio.wav");
        Files.write(file.toPath(), wave.array());
        Media3AudioEngine engine = new Media3AudioEngine(RuntimeEnvironment.getApplication());
        AtomicInteger prepared = new AtomicInteger();
        AtomicReference<String> failure = new AtomicReference<>();
        engine.listener(new Media3AudioEngine.Listener() {
            @Override public void prepared(long duration) { prepared.incrementAndGet(); }
            @Override public void completed() { }
            @Override public void buffering(boolean active) { }
            @Override public void failed(String message) { failure.set(message); }
        });
        try {
            engine.setSource(file.getAbsolutePath());
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while (prepared.get() == 0 && failure.get() == null && System.nanoTime() < deadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                Thread.sleep(10); // Await actual ExoPlayer worker threads, not a mocked READY callback.
            }
            assertNull(failure.get(), failure.get());
            assertEquals("ExoPlayer did not finish preparing", 1, prepared.get());
            assertTrue(engine.duration() >= 1900 && engine.duration() <= 2100);
            engine.play();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertTrue("Real ExoPlayer did not accept play", engine.playing());
            engine.seek(900);
            engine.pause();
            long seekDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
            while (System.nanoTime() < seekDeadline) {
                Shadows.shadowOf(Looper.getMainLooper()).idle();
                Thread.sleep(10);
            }
            assertNull(failure.get()); assertFalse(engine.playing());
            assertEquals("Seek must not initialize playback a second time", 1, prepared.get());
        } finally { engine.release(); Files.deleteIfExists(file.toPath()); }
    }
}
