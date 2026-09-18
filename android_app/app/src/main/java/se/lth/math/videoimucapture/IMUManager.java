package se.lth.math.videoimucapture;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorAdditionalInfo;
import android.hardware.SensorEvent;
import android.hardware.SensorEventCallback;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.util.Log;

import androidx.preference.PreferenceManager;



public class IMUManager extends SensorEventCallback {
    private static final String TAG = "IMUManager";
    private static final String ACCEL_FREQUENCY_KEY = "accelerometer_sampling_frequency_hz";
    private static final String GYRO_FREQUENCY_KEY = "gyroscope_sampling_frequency_hz";
    private static final int DEFAULT_FREQUENCY_HZ = 100;
    private static final int[] ALLOWED_FREQUENCIES_HZ = {50, 100, 200, 300, 400};
    private int ACC_TYPE;
    private int GYRO_TYPE;
    private int MAG_TYPE;

    private final Context mContext;
    private int mRequestedAccelFrequencyHz = DEFAULT_FREQUENCY_HZ;
    private int mRequestedGyroFrequencyHz = DEFAULT_FREQUENCY_HZ;
    private int mAccelSamplingPeriodUs = 1_000_000 / DEFAULT_FREQUENCY_HZ;
    private int mGyroSamplingPeriodUs = 1_000_000 / DEFAULT_FREQUENCY_HZ;
    private volatile long mEstimatedAccelPeriodNs = 0;
    private volatile long mEstimatedGyroPeriodNs = 0;
    private long mPrevAccelTimestamp = 0;
    private long mPrevGyroTimestamp = 0;
    private float[] mSensorPlacement = null;
    private final ImuSynchronizer mSynchronizer = new ImuSynchronizer();

    // Sensor listeners
    private SensorManager mSensorManager;
    private Sensor mAccel;
    private Sensor mGyro;
    private Sensor mMag;

    private int linear_acc; // accuracy
    private int angular_acc;
    private int mag_acc;

    private volatile boolean mRecordingInertialData = false;
    private RecordingWriter mRecordingWriter = null;
    private HandlerThread mSensorThread;

    public IMUManager(Context activity) {
        super();
        mContext = activity.getApplicationContext();
        mSensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
        setSensorType();
        mAccel = mSensorManager.getDefaultSensor(ACC_TYPE);
        mGyro = mSensorManager.getDefaultSensor(GYRO_TYPE);
        mMag = mSensorManager.getDefaultSensor(MAG_TYPE);
        loadSamplingPreferences();
    }

    private void setSensorType() {
        if (Build.VERSION.SDK_INT >= 26)
            ACC_TYPE = Sensor.TYPE_ACCELEROMETER_UNCALIBRATED;
        else
            ACC_TYPE = Sensor.TYPE_ACCELEROMETER;
        GYRO_TYPE = Sensor.TYPE_GYROSCOPE_UNCALIBRATED;
        MAG_TYPE = Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED;
    }

    public Boolean sensorsExist() {
        return (mAccel != null) && (mGyro != null);
    }

    public synchronized void startRecording(RecordingWriter recordingWriter) {
        refreshSamplingRates();
        mSynchronizer.clear();
        mRecordingWriter = recordingWriter;
        writeMetaData();
        mRecordingInertialData = true;
    }

    public synchronized void stopRecording() {
        if (mRecordingInertialData) drainSamples(false);
        mRecordingInertialData = false;
        mSynchronizer.clear();
        mRecordingWriter = null;
    }

    @Override
    public synchronized final void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (sensor.getType() == ACC_TYPE) {
            linear_acc = accuracy;
        } else if (sensor.getType() == GYRO_TYPE) {
            angular_acc = accuracy;
        } else if (sensor.getType() == MAG_TYPE) {
            mag_acc = accuracy;
        }
    }

    private void drainSamples(boolean waitForMag) {
        ImuSynchronizer.Packet packet;
        while ((packet = mSynchronizer.poll(waitForMag && mMag != null)) != null) {
            writeData(packet);
        }
    }

    private void writeData(ImuSynchronizer.Packet packet) {
        RecordingProtos.IMUData.Builder imuBuilder =
                RecordingProtos.IMUData.newBuilder()
                        .setTimeNs(packet.time)
                        .setAccelAccuracyValue(linear_acc)
                        .setGyroAccuracyValue(angular_acc)
                        .setMagAccuracyValue(mag_acc);

        for (int i = 0 ; i < 3 ; i++) {
            imuBuilder.addGyro(packet.gyro[i]);
            imuBuilder.addAccel(packet.accel[i]);
            if (packet.mag.length >= 3) imuBuilder.addMag(packet.mag[i]);
        }
        if (packet.accel.length >= 6 && ACC_TYPE == Sensor.TYPE_ACCELEROMETER_UNCALIBRATED) {
            for (int i = 3 ; i < 6 ; i++) {
                imuBuilder.addAccelBias(packet.accel[i]);
            }
        }
        if (packet.gyro.length >= 6 && GYRO_TYPE == Sensor.TYPE_GYROSCOPE_UNCALIBRATED) {
            for (int i = 3 ; i < 6 ; i++) {
                imuBuilder.addGyroDrift(packet.gyro[i]);
            }
        }
        if (packet.mag.length >= 6 && MAG_TYPE == Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED) {
            for (int i = 3 ; i < 6 ; i++) {
                imuBuilder.addMagBias(packet.mag[i]);
            }
        }

        mRecordingWriter.queueData(imuBuilder.build());
    }

    private void writeMetaData() {
        RecordingProtos.IMUInfo.Builder builder = RecordingProtos.IMUInfo.newBuilder();
        if (mGyro != null) {
            builder.setGyroInfo(mGyro.toString()).setGyroResolution(mGyro.getResolution());
        }
        if (mAccel != null) {
            builder.setAccelInfo(mAccel.toString()).setAccelResolution(mAccel.getResolution());
        }
        if (mMag != null) {
            builder.setMagInfo(mMag.toString()).setMagResolution(mMag.getResolution());
        }
        builder.setRequestedAccelerometerFrequencyHz(mRequestedAccelFrequencyHz)
                .setRequestedGyroscopeFrequencyHz(mRequestedGyroFrequencyHz)
                .setSampleFrequency(getSensorFrequency())
                .setEstimatedGyroscopeFrequencyHz(getGyroscopeFrequency());

        //Store translation for sensor placement in device coordinate system.
        if (mSensorPlacement != null && mSensorPlacement.length >= 12) {
            builder.addPlacement(mSensorPlacement[3])
                    .addPlacement(mSensorPlacement[7])
                    .addPlacement(mSensorPlacement[11]);
        }
        mRecordingWriter.queueData(builder.build());
    }

    private long updateEstimatedPeriod(long previousTimestamp, long estimatedPeriod,
                                       long timestamp) {
        long diff = timestamp - previousTimestamp;
        if (previousTimestamp != 0 && diff > 0) {
            return estimatedPeriod == 0
                    ? diff : estimatedPeriod + ((diff - estimatedPeriod) >> 3);
        }
        return estimatedPeriod;
    }

    public float getSensorFrequency() {
        return frequencyFromPeriod(mEstimatedAccelPeriodNs);
    }

    public float getGyroscopeFrequency() {
        return frequencyFromPeriod(mEstimatedGyroPeriodNs);
    }

    public int getRequestedAccelerometerFrequencyHz() {
        return mRequestedAccelFrequencyHz;
    }

    public int getRequestedGyroscopeFrequencyHz() {
        return mRequestedGyroFrequencyHz;
    }

    private float frequencyFromPeriod(long periodNs) {
        return periodNs > 0 ? 1e9f / periodNs : 0;
    }

    @Override
    public synchronized final void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();
        if (type == ACC_TYPE) {
            mEstimatedAccelPeriodNs = updateEstimatedPeriod(
                    mPrevAccelTimestamp, mEstimatedAccelPeriodNs, event.timestamp);
            mPrevAccelTimestamp = event.timestamp;
        } else if (type == GYRO_TYPE) {
            mEstimatedGyroPeriodNs = updateEstimatedPeriod(
                    mPrevGyroTimestamp, mEstimatedGyroPeriodNs, event.timestamp);
            mPrevGyroTimestamp = event.timestamp;
        }
        // Preview must not build a backlog that is replayed at recording start.
        if (!mRecordingInertialData) return;
        if (type == ACC_TYPE) mSynchronizer.addAccel(event.timestamp, event.values);
        else if (type == GYRO_TYPE) mSynchronizer.addGyro(event.timestamp, event.values);
        else if (type == MAG_TYPE) mSynchronizer.addMag(event.timestamp, event.values);
        drainSamples(true);
    }

    @Override
    public synchronized final void onSensorAdditionalInfo(SensorAdditionalInfo info) {
        if (mSensorPlacement != null) {
            return;
        }
        if ((info.sensor == mAccel) && (info.type == SensorAdditionalInfo.TYPE_SENSOR_PLACEMENT)) {
            mSensorPlacement = info.floatValues.clone();
        }
    }

    /**
     * This will register all IMU listeners
     * https://stackoverflow.com/questions/3286815/sensoreventlistener-in-separate-thread
     */
    public void register() {
        if (mSensorThread != null) return;
        if (!sensorsExist()) {
            return;
        }
        loadSamplingPreferences();
        resetFrequencyEstimates();
        mSensorThread = new HandlerThread("Sensor thread",
                Process.THREAD_PRIORITY_MORE_FAVORABLE);
        mSensorThread.start();
        // Blocks until looper is prepared, which is fairly quick
        Handler sensorHandler = new Handler(mSensorThread.getLooper());
        boolean accelRegistered = mSensorManager.registerListener(
                this, mAccel, mAccelSamplingPeriodUs, sensorHandler);
        boolean gyroRegistered = mSensorManager.registerListener(
                this, mGyro, mGyroSamplingPeriodUs, sensorHandler);
        if (!accelRegistered || !gyroRegistered) {
            Log.e(TAG, "Failed to register IMU listener: accel=" + accelRegistered
                    + ", gyro=" + gyroRegistered);
        }
        if (mMag != null) mSensorManager.registerListener(this, mMag, Math.max(10000, mMag.getMinDelay()), sensorHandler);
    }

    public synchronized void refreshSamplingRates() {
        if (mRecordingInertialData) return;
        int oldAccelHz = mRequestedAccelFrequencyHz;
        int oldGyroHz = mRequestedGyroFrequencyHz;
        loadSamplingPreferences();
        if (mSensorThread != null && (oldAccelHz != mRequestedAccelFrequencyHz
                || oldGyroHz != mRequestedGyroFrequencyHz)) {
            unregister();
            register();
        }
    }

    private void loadSamplingPreferences() {
        mRequestedAccelFrequencyHz = readFrequencyPreference(ACCEL_FREQUENCY_KEY);
        mRequestedGyroFrequencyHz = readFrequencyPreference(GYRO_FREQUENCY_KEY);
        mAccelSamplingPeriodUs = samplingPeriodUs(mRequestedAccelFrequencyHz, mAccel);
        mGyroSamplingPeriodUs = samplingPeriodUs(mRequestedGyroFrequencyHz, mGyro);
    }

    private int readFrequencyPreference(String key) {
        String value = PreferenceManager.getDefaultSharedPreferences(mContext)
                .getString(key, Integer.toString(DEFAULT_FREQUENCY_HZ));
        try {
            int frequency = Integer.parseInt(value);
            for (int allowed : ALLOWED_FREQUENCIES_HZ) {
                if (frequency == allowed) return frequency;
            }
        } catch (NumberFormatException ignored) {
            // Fall through to the safe default if an older build stored an invalid value.
        }
        return DEFAULT_FREQUENCY_HZ;
    }

    private int samplingPeriodUs(int frequencyHz, Sensor sensor) {
        int requestedPeriodUs = Math.round(1_000_000f / frequencyHz);
        int sensorMinimumPeriodUs = sensor == null ? 0 : sensor.getMinDelay();
        return sensorMinimumPeriodUs > 0
                ? Math.max(requestedPeriodUs, sensorMinimumPeriodUs)
                : requestedPeriodUs;
    }

    private void resetFrequencyEstimates() {
        mEstimatedAccelPeriodNs = 0;
        mEstimatedGyroPeriodNs = 0;
        mPrevAccelTimestamp = 0;
        mPrevGyroTimestamp = 0;
    }

    /**
     * This will unregister all IMU listeners
     */
    public void unregister() {
        if (mSensorThread == null) return;
        if (!sensorsExist()) {
            return;
        }
        mSensorManager.unregisterListener(this, mAccel);
        mSensorManager.unregisterListener(this, mGyro);
        if (mMag != null) mSensorManager.unregisterListener(this, mMag);
        mSensorManager.unregisterListener(this);
        mSensorThread.quitSafely();
        mSensorThread = null;
        stopRecording();
    }
}
