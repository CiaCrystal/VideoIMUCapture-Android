package se.lth.math.videoimucapture;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import se.lth.math.videoimucapture.RecordingProtos.*;
import static org.junit.Assert.*;

public class KeyboardCaptureTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test public void composingCommitsDoNotDuplicateAndRepeatedLettersRemainSeparate() {
        LetterInputTracker tracker = new LetterInputTracker();
        assertEquals("A", tracker.update("a", false));
        assertEquals("", tracker.update("a", false));
        assertEquals("A", tracker.update("aa", false));
        assertEquals("", tracker.update("aa", true));
        assertEquals("Z", tracker.update("Z", true));
        assertEquals("Z", tracker.update("z", true));
        assertEquals("", tracker.update("hello", true));
        assertEquals("", tracker.update("1", true));
        assertEquals("", tracker.update("中", true));
        assertEquals("B", tracker.update("b", false));
        tracker.delete();
        assertEquals("B", tracker.update("b", false));
        tracker.reset();
        assertEquals("B", tracker.update("b", true));
    }

    @Test public void windowsUseSampleTimeAndCloseOnNextKeyOrDisable() {
        KeyboardLabels labels = new KeyboardLabels();
        labels.add(new KeyboardLabels.Event(1_000_000_000L, "A", true));
        labels.add(new KeyboardLabels.Event(1_100_000_000L, "A", true));
        labels.add(new KeyboardLabels.Event(1_150_000_000L, "", false));
        assertNull(labels.at(999_999_999L));
        assertEquals(1, labels.at(1_000_000_000L).id);
        assertEquals(1, labels.at(1_099_999_999L).id);
        assertEquals(2, labels.at(1_100_000_000L).id);
        assertNull(labels.at(1_150_000_000L));
        labels.add(new KeyboardLabels.Event(2_000_000_000L, "Z", true));
        assertNotNull(labels.at(2_199_999_999L));
        assertNull(labels.at(2_200_000_000L));
    }

    @Test public void writerLabelsDelayedOisAndImuAndResetsNextRecording() throws Exception {
        RecordingWriter writer = new RecordingWriter();
        File file = folder.newFile();
        CountDownLatch closed = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        writer.startRecording(file.toString(), (path, failure) -> { error.set(failure); closed.countDown(); });
        writer.setKeyboardEnabled(true, 1);
        writer.queueData(CameraInfo.newBuilder().setTimestampSourceValue(1).build());
        writer.recordLetter("A", 1_000_000_000L);
        writer.recordLetter("B", 1_100_000_000L);
        writer.setKeyboardEnabled(false, 1_150_000_000L);
        writer.recordLetter("C", 1_160_000_000L);
        writer.queueData(IMUData.newBuilder().setTimeNs(1_050_000_000L).build());
        writer.queueData(VideoFrameMetaData.newBuilder().setTimeNs(1_050_000_000L)
                .addOISSamples(VideoFrameMetaData.OISSample.newBuilder().setTimeNs(1_050_000_000L))
                .addOISSamples(VideoFrameMetaData.OISSample.newBuilder().setTimeNs(1_110_000_000L))
                .addOISSamples(VideoFrameMetaData.OISSample.newBuilder().setTimeNs(1_160_000_000L)).build());
        writer.stopRecording();
        assertTrue(closed.await(10, TimeUnit.SECONDS));
        assertNull(error.get());
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertEquals(2, text.split("\\[KEYBOARD_EVENT\\]", -1).length - 1);
        assertFalse(text.contains("keyboard_label=C"));
        String imu = text.substring(text.indexOf("[IMU_DATA]"), text.indexOf("[FRAME_METADATA]"));
        assertTrue(imu.contains("keyboard_label=A\nkeyboard_event_id=1\n"));
        String[] ois = text.split("\\[OIS_SAMPLE\\]");
        assertTrue(ois[1].contains("keyboard_label=A\nkeyboard_event_id=1\n"));
        assertTrue(ois[2].contains("keyboard_label=B\nkeyboard_event_id=2\n"));
        assertTrue(ois[3].contains("keyboard_label=\nkeyboard_event_id=-1\n"));
        File second = folder.newFile();
        CountDownLatch nextClosed = new CountDownLatch(1);
        writer.startRecording(second.toString(), (path, failure) -> nextClosed.countDown());
        writer.queueData(IMUData.newBuilder().setTimeNs(1_050_000_000L).build());
        writer.stopRecording();
        assertTrue(nextClosed.await(10, TimeUnit.SECONDS));
        assertFalse(new String(Files.readAllBytes(second.toPath()), StandardCharsets.UTF_8).contains("keyboard_"));
    }

    @Test public void unknownCameraClockDoesNotFabricateOisLabels() {
        KeyboardLabels labels = new KeyboardLabels();
        labels.add(new KeyboardLabels.Event(100, "A", true));
        VideoFrameMetaData frame = VideoFrameMetaData.newBuilder()
                .addOISSamples(VideoFrameMetaData.OISSample.newBuilder().setTimeNs(101)).build();
        String text = RecordingTextFormatter.frame(frame, false, labels, false);
        assertTrue(text.contains("keyboard_clock_comparable=false\nkeyboard_label=\n"));
        assertFalse(RecordingTextFormatter.frame(frame, false).contains("keyboard_"));
        assertFalse(RecordingTextFormatter.imu(IMUData.getDefaultInstance()).contains("keyboard_"));
    }
}
