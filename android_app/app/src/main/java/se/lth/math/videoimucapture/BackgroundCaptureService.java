package se.lth.math.videoimucapture;

import android.app.*;
import android.content.Intent;
import android.content.Context;
import android.os.*;
import android.media.MediaScannerConnection;
import android.util.Size;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Owns camera, encoder and IMU independently of any Activity or visible Surface. */
public final class BackgroundCaptureService extends Service {
    public static final String STOP = "se.lth.math.videoimucapture.STOP_BACKGROUND";
    private static final String CHANNEL = "background_capture";
    private static final int NOTIFICATION = 1001;
    private static volatile boolean active;
    private static volatile boolean ready;
    public static boolean isActive() { return active; }
    public static boolean isReady() { return ready; }
    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread thread;
    private Handler worker;
    private PowerManager.WakeLock wakeLock;
    private Camera2Proxy camera;
    private IMUManager imu;
    private VideoEncoderCore encoder;
    private RecordingWriter writer;
    private File directory;
    private boolean stopping;
    private boolean initialized;
    private boolean cameraReady;
    private Exception failure;

    public static void startFrom(CameraCaptureActivity activity) {
        if (active) return;
        active = true;
        ready = false;
        activity.releaseCamera();
        activity.getmImuManager().unregister();
        try {
            ContextCompat.startForegroundService(activity, new Intent(activity, BackgroundCaptureService.class));
        } catch (RuntimeException e) {
            active = false;
            Toast.makeText(activity, "Cannot start background capture: " + e.getMessage(), Toast.LENGTH_LONG).show();
            activity.recreate();
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager.class).createNotificationChannel(
                    new NotificationChannel(CHANNEL, "Video and IMU recording", NotificationManager.IMPORTANCE_LOW));
        }
        startForeground(NOTIFICATION, notification("Starting video + IMU (100 Hz)", true));
        thread = new HandlerThread("BackgroundCapture");
        thread.start();
        worker = new Handler(thread.getLooper());
    }

    private Notification notification(String text, boolean ongoing) {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, CameraCaptureActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_start_record).setContentTitle("VideoIMUCapture")
                .setContentText(text).setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setOngoing(ongoing).setContentIntent(open).setOnlyAlertOnce(true);
        if (ongoing) builder.addAction(R.drawable.ic_stop_record, "Stop and save",
                PendingIntent.getService(this, 1, new Intent(this, BackgroundCaptureService.class).setAction(STOP),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return builder.build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) {
            worker.post(() -> stopCapture(null));
        } else {
            active = true;
            worker.post(this::startCapture);
        }
        return START_NOT_STICKY; // Never reopen the camera automatically after process termination.
    }

    @android.annotation.SuppressLint("WakelockTimeout")
    private void startCapture() {
        if (initialized || stopping) return;
        initialized = true;
        try {
            wakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, "VideoIMUCapture:recording");
            wakeLock.acquire();
            directory = new File(getExternalFilesDir(null),
                    new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US).format(new Date()) + "_background");
            if (!directory.mkdirs()) throw new java.io.IOException("Cannot create output directory");
            writer = new RecordingWriter();
            writer.startRecording(new File(directory, "video_meta.txt").toString(), (path, error) -> worker.post(() -> {
                if (!stopping) stopCapture(error == null ? new IllegalStateException("TXT writer stopped") : error);
                finish(error);
            }));
            CameraSettingsManager settings = new CameraSettingsManager(getApplicationContext());
            camera = new Camera2Proxy(getApplicationContext(), settings);
            camera.configureCamera();
            Size size = settings.getVideoSize();
            encoder = new VideoEncoderCore(size.getWidth(), size.getHeight(),
                    CameraUtils.calcBitRate(size.getWidth(), size.getHeight(), 30),
                    new File(directory, "video_recording.mp4").toString(), writer, camera.getSensorOrientation());
            camera.setRecordingSurface(encoder.getInputSurface(), () -> worker.post(() -> {
                if (stopping) return;
                cameraReady = true;
                ready = true;
                main.post(() -> getSystemService(NotificationManager.class).notify(NOTIFICATION,
                        notification("Recording video + IMU (100 Hz). Tap Stop and save to finish.", true)));
            }), error -> worker.post(() -> stopCapture(error)));
            imu = new IMUManager(getApplicationContext());
            if (!imu.sensorsExist()) throw new IllegalStateException("Accelerometer or gyroscope unavailable");
            imu.register();
            imu.startRecording(writer);
            camera.startRecordingCaptureResult(writer);
            camera.openCamera();
            worker.post(drain);
            worker.postDelayed(() -> {
                if (!cameraReady && !stopping) stopCapture(new IllegalStateException("Camera startup timed out"));
            }, 10_000);
        } catch (Exception e) { stopCapture(e); }
    }

    private final Runnable drain = new Runnable() {
        @Override public void run() {
            if (stopping) return;
            try {
                encoder.drainEncoder(false);
                worker.postDelayed(this, 10);
            } catch (Exception e) { stopCapture(e); }
        }
    };

    private void stopCapture(Exception error) {
        if (failure == null) failure = error;
        if (stopping) return;
        stopping = true;
        worker.removeCallbacks(drain);
        // Stop producers, drain encoded tail timestamps, then close TXT last.
        try { if (camera != null) camera.releaseCamera(); }
        catch (Exception e) { if (failure == null) failure = e; }
        try { if (imu != null) imu.unregister(); }
        catch (Exception e) { if (failure == null) failure = e; }
        try { if (encoder != null) encoder.drainEncoder(true); }
        catch (Exception e) { if (failure == null) failure = e; }
        try { if (encoder != null) encoder.release(); }
        catch (Exception e) { if (failure == null) failure = e; }
        if (writer != null && writer.isRecording()) writer.stopRecording();
        else finish(failure);
    }

    private volatile boolean finished;
    private volatile boolean destroyed;
    private void finish(Exception error) {
        if (finished) return;
        finished = true;
        if (failure == null) failure = error;
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (directory != null) MediaScannerConnection.scanFile(getApplicationContext(),
                new String[]{new File(directory, "video_meta.txt").toString(), new File(directory, "video_recording.mp4").toString()},
                new String[]{"text/plain", "video/mp4"}, null);
        String result = failure == null ? "Saved: " + directory : "Recording incomplete: " + failure.getMessage();
        ready = false;
        main.post(() -> {
            stopForeground(true);
            getSystemService(NotificationManager.class).notify(NOTIFICATION + 1, notification(result, false));
            Toast.makeText(getApplicationContext(), result, Toast.LENGTH_LONG).show();
            stopSelf();
            if (destroyed) active = false;
        });
        thread.quitSafely();
    }

    @Override public void onDestroy() {
        destroyed = true;
        if (finished) active = false;
        if (worker != null && !finished) worker.post(() -> stopCapture(new IllegalStateException("Service stopped")));
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
