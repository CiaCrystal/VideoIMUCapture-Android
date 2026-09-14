package se.lth.math.videoimucapture;

import java.util.ArrayDeque;
import java.util.Deque;

/** Called under IMUManager's lock. Time units are nanoseconds. */
final class ImuSynchronizer {
    static final int MAX_SAMPLES = 512;
    private static final long MAG_WAIT_NS = 100_000_000L;
    private final Deque<Sample> accel = new ArrayDeque<>();
    private final Deque<Sample> gyro = new ArrayDeque<>();
    private final Deque<Sample> mag = new ArrayDeque<>();
    private long latestTime;

    static final class Sample {
        final long time;
        final float[] values;
        Sample(long time, float[] values) {
            this.time = time;
            this.values = values.clone();
        }
    }

    static final class Packet {
        final long time;
        final float[] accel, gyro, mag;
        Packet(long time, float[] accel, float[] gyro, float[] mag) {
            this.time = time;
            this.accel = accel;
            this.gyro = gyro;
            this.mag = mag;
        }
    }

    void clear() { accel.clear(); gyro.clear(); mag.clear(); latestTime = 0; }
    void addAccel(long t, float[] v) { add(accel, t, v); }
    void addGyro(long t, float[] v) { add(gyro, t, v); }
    void addMag(long t, float[] v) { add(mag, t, v); }

    private void add(Deque<Sample> queue, long t, float[] v) {
        if (v.length < 3 || (!queue.isEmpty() && t <= queue.peekLast().time)) return;
        latestTime = Math.max(latestTime, t);
        if (queue.size() == MAX_SAMPLES) queue.removeFirst();
        queue.addLast(new Sample(t, v));
    }

    Packet poll(boolean waitForMag) {
        while (!gyro.isEmpty() && !accel.isEmpty()) {
            Sample reference = gyro.peekFirst();
            if (reference.time < accel.peekFirst().time) {
                gyro.removeFirst(); // Startup/dropout: no acceleration bracket exists.
                continue;
            }
            if (reference.time > accel.peekLast().time) return null;
            boolean magReady = !mag.isEmpty() && mag.peekFirst().time <= reference.time
                    && mag.peekLast().time >= reference.time;
            if (!magReady && waitForMag && latestTime - reference.time < MAG_WAIT_NS) return null;
            float[] a = interpolate(accel, reference.time);
            float[] m = magReady ? interpolate(mag, reference.time) : new float[0];
            gyro.removeFirst();
            return new Packet(reference.time, a, reference.values, m);
        }
        return null;
    }

    private static float[] interpolate(Deque<Sample> queue, long time) {
        // Retain the closest left sample and all future samples, including exact endpoints.
        while (queue.size() > 1) {
            java.util.Iterator<Sample> it = queue.iterator();
            it.next();
            if (it.next().time > time) break;
            queue.removeFirst();
        }
        Sample left = queue.peekFirst();
        if (left.time == time) return left.values.clone();
        java.util.Iterator<Sample> it = queue.iterator();
        it.next();
        Sample right = it.next();
        float ratio = (float) (time - left.time) / (right.time - left.time);
        float[] result = new float[Math.min(left.values.length, right.values.length)];
        for (int i = 0; i < result.length; i++) result[i] = left.values[i] + (right.values[i] - left.values[i]) * ratio;
        return result;
    }

    int bufferedSamples() { return accel.size() + gyro.size() + mag.size(); }
}
