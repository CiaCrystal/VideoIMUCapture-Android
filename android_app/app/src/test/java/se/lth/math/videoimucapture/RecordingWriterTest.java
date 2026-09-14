package se.lth.math.videoimucapture;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import se.lth.math.videoimucapture.RecordingProtos.*;
import static org.junit.Assert.*;

public class RecordingWriterTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void writes200HzSamplesAndRetainsMoreThan100UnmatchedFrames() throws Exception {
        RecordingWriter writer = new RecordingWriter();
        File file = folder.newFile("video_meta.txt");
        CountDownLatch closed = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        writer.startRecording(file.toString(), (path, failure) -> { error.set(failure); closed.countDown(); });
        for (int i = 0; i < 2000; i++) {
            writer.queueData(IMUData.newBuilder().setTimeNs(i * 5_000_000L).addGyro(0.1f).build());
            if (i % 6 == 0) writer.queueData(VideoFrameMetaData.newBuilder().setTimeNs(i * 5_000_000L).setFrameNumber(i).build());
        }
        writer.stopRecording();
        assertTrue(closed.await(10, TimeUnit.SECONDS));
        assertNull(error.get());
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertEquals(2000, text.split("\\[IMU_DATA\\]", -1).length - 1);
        assertEquals(334, text.split("\\[FRAME_METADATA\\]", -1).length - 1);
        assertTrue(text.contains("time_ns=9995000000\n"));
        assertFalse(writer.isRecording());
        // Starting again must not overwrite/reuse the old writer or replay its queue.
        File next = folder.newFile("next.txt");
        CountDownLatch nextClosed = new CountDownLatch(1);
        writer.startRecording(next.toString(), (path, failure) -> nextClosed.countDown());
        writer.stopRecording();
        assertTrue(nextClosed.await(5, TimeUnit.SECONDS));
        assertFalse(new String(Files.readAllBytes(next.toPath()), StandardCharsets.UTF_8).contains("[IMU_DATA]"));
    }

    @Test public void diskFailureReportsErrorAndDoesNotBlockProducersOrStop() throws Exception {
        RecordingWriter writer = new RecordingWriter() {
            @Override BufferedWriter openWriter(String file) {
                return new BufferedWriter(new Writer() {
                    public void write(char[] c, int off, int len) throws IOException { throw new IOException("Disk failure"); }
                    public void flush() throws IOException { throw new IOException("Disk failure"); }
                    public void close() {}
                });
            }
        };
        CountDownLatch closed = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        writer.startRecording("unused", (path, failure) -> { error.set(failure); closed.countDown(); });
        assertTrue(closed.await(5, TimeUnit.SECONDS));
        assertNotNull(error.get());
        Thread producer = new Thread(() -> {
            for (int i = 0; i < 10000; i++) writer.queueData(IMUData.getDefaultInstance());
            writer.stopRecording();
        });
        producer.setDaemon(true);
        producer.start();
        producer.join(1000);
        assertFalse("Producer/stop must not wait on a dead writer", producer.isAlive());
    }

    @Test public void queueOverloadEndsWithExplicitErrorInsteadOfDeadlock() throws Exception {
        StringWriter saved = new StringWriter();
        CountDownLatch firstFlush = new CountDownLatch(1);
        CountDownLatch allowWrite = new CountDownLatch(1);
        RecordingWriter writer = new RecordingWriter() {
            @Override BufferedWriter openWriter(String file) {
                return new BufferedWriter(saved) {
                    @Override public void flush() throws IOException {
                        firstFlush.countDown();
                        try {
                            if (!allowWrite.await(5, TimeUnit.SECONDS)) throw new IOException("Test timeout");
                        } catch (InterruptedException e) { throw new IOException(e); }
                        super.flush();
                    }
                };
            }
        };
        CountDownLatch closed = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        writer.startRecording("unused", (path, failure) -> { error.set(failure); closed.countDown(); });
        try {
            assertTrue(firstFlush.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 5000; i++) writer.queueData(IMUData.getDefaultInstance());
            writer.stopRecording();
        } finally { allowWrite.countDown(); }
        assertTrue(closed.await(10, TimeUnit.SECONDS));
        assertNotNull(error.get());
        assertTrue(saved.toString().contains("[RECORDING_ERROR]"));
    }
}
