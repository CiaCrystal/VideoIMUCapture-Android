package se.lth.math.videoimucapture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RecordingExporterTest {
    @Test public void publicPathKeepsRecordingFolderAndTxtName() {
        assertEquals("Download/VideoIMUCapture/2026_09_18_18_03_38_689/video_meta.txt",
                RecordingExporter.publicRelativePath("2026_09_18_18_03_38_689")
                        + "/video_meta.txt");
    }
}
