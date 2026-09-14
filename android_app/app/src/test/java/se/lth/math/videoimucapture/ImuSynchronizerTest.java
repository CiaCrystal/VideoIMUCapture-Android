package se.lth.math.videoimucapture;

import org.junit.Test;
import static org.junit.Assert.*;

public class ImuSynchronizerTest {
    private float[] values(float x) { return new float[]{x, 0, 0}; }

    @Test public void preserves100HzTimingWithSlowerMagnetometer() {
        ImuSynchronizer sync = new ImuSynchronizer();
        int count = 0;
        long previous = -10_000_000L;
        for (int i = 0; i <= 1000; i++) {
            long t = i * 10_000_000L;
            sync.addAccel(t, values(i));
            sync.addGyro(t, values(i));
            if (i % 2 == 0) sync.addMag(t, values(i));
            ImuSynchronizer.Packet packet;
            while ((packet = sync.poll(true)) != null) {
                assertEquals(10_000_000L, packet.time - previous);
                assertEquals(packet.gyro[0], packet.accel[0], 0);
                assertEquals(packet.gyro[0], packet.mag[0], 0);
                previous = packet.time;
                count++;
            }
        }
        assertEquals(1001, count);
    }

    @Test public void drains200HzGyroWith50HzMagAndCopiesEventValues() {
        ImuSynchronizer sync = new ImuSynchronizer();
        int count = 0;
        long previous = -1;
        for (int i = 0; i <= 2000; i++) {
            long t = i * 5_000_000L;
            float[] value = values(i);
            sync.addAccel(t, value);
            sync.addGyro(t, value);
            if (i % 4 == 0) sync.addMag(t, value);
            value[0] = -999; // SensorEvent arrays are reused by Android.
            ImuSynchronizer.Packet packet;
            while ((packet = sync.poll(true)) != null) {
                assertTrue(packet.time > previous);
                assertEquals(packet.time / 5_000_000f, packet.gyro[0], 0.01f);
                assertEquals(packet.gyro[0], packet.accel[0], 0.01f);
                assertEquals(packet.gyro[0], packet.mag[0], 0.01f);
                previous = packet.time;
                count++;
            }
        }
        assertEquals(2001, count);
    }

    @Test public void interpolatesEndpointsAndWaitsForAcceleration() {
        ImuSynchronizer sync = new ImuSynchronizer();
        sync.addAccel(10, values(1));
        sync.addGyro(15, values(7));
        assertNull(sync.poll(false));
        sync.addAccel(20, values(3));
        assertEquals(2, sync.poll(false).accel[0], 0);
        sync.addGyro(20, values(8));
        assertEquals(3, sync.poll(false).accel[0], 0);
    }

    @Test public void absentMagnetometerCannotBlockImuForever() {
        ImuSynchronizer sync = new ImuSynchronizer();
        sync.addAccel(0, values(1));
        sync.addGyro(0, values(2));
        assertNull(sync.poll(true));
        sync.addAccel(100_000_000, values(3));
        ImuSynchronizer.Packet packet = sync.poll(true);
        assertNotNull(packet);
        assertEquals(0, packet.mag.length);
    }

    @Test public void buffersAreBoundedAndNewRecordingHasNoOldSamples() {
        ImuSynchronizer sync = new ImuSynchronizer();
        for (int i = 0; i < 10000; i++) {
            sync.addAccel(i, values(i)); sync.addGyro(i, values(i)); sync.addMag(i, values(i));
        }
        assertTrue(sync.bufferedSamples() <= 3 * ImuSynchronizer.MAX_SAMPLES);
        sync.clear();
        assertEquals(0, sync.bufferedSamples());
        assertNull(sync.poll(false));
    }
}
