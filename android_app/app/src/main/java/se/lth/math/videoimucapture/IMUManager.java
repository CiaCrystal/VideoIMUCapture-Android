package se.lth.math.videoimucapture;

import android.app.Activity;
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



public class IMUManager extends SensorEventCallback {
    private static final String TAG = "IMUManager";
    private int ACC_TYPE;
    private int GYRO_TYPE;
    private int MAG_TYPE;

    private final int mSensorRate = 10000; // microseconds: request 100 Hz
    private volatile long mEstimatedSensorRate = 0;
    private long mPrevTimestamp = 0;
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
        mSensorManager = (SensorManager) activity.getSystemService(Context.SENSOR_SERVICE);
        setSensorType();
        mAccel = mSensorManager.getDefaultSensor(ACC_TYPE);
        mGyro = mSensorManager.getDefaultSensor(GYRO_TYPE);
        mMag = mSensorManager.getDefaultSensor(MAG_TYPE);
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
        builder.setSampleFrequency(getSensorFrequency());

        //Store translation for sensor placement in device coordinate system.
        if (mSensorPlacement != null && mSensorPlacement.length >= 12) {
            builder.addPlacement(mSensorPlacement[3])
                    .addPlacement(mSensorPlacement[7])
                    .addPlacement(mSensorPlacement[11]);
        }
        mRecordingWriter.queueData(builder.build());
    }

    private void updateSensorRate(SensorEvent event) {
        long diff = event.timestamp - mPrevTimestamp;
        if (mPrevTimestamp != 0 && diff > 0) {
            mEstimatedSensorRate = mEstimatedSensorRate == 0 ? diff : mEstimatedSensorRate + ((diff - mEstimatedSensorRate) >> 3);
        }
        mPrevTimestamp = event.timestamp;
    }

    public float getSensorFrequency() {
        return mEstimatedSensorRate > 0 ? 1e9f / mEstimatedSensorRate : 0;
    }

    @Override
    public synchronized final void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();
        if (type == ACC_TYPE) updateSensorRate(event);
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
        mSensorThread = new HandlerThread("Sensor thread",
                Process.THREAD_PRIORITY_MORE_FAVORABLE);
        mSensorThread.start();
        // Blocks until looper is prepared, which is fairly quick
        Handler sensorHandler = new Handler(mSensorThread.getLooper());
        mSensorManager.registerListener(this, mAccel, mSensorRate, sensorHandler);
        mSensorManager.registerListener(this, mGyro, mSensorRate, sensorHandler);
        if (mMag != null) mSensorManager.registerListener(this, mMag, Math.max(10000, mMag.getMinDelay()), sensorHandler);
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
