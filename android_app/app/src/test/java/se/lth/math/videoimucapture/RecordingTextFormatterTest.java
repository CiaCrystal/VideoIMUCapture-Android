package se.lth.math.videoimucapture;

import org.junit.Test;
import java.util.Arrays;
import java.util.Locale;
import se.lth.math.videoimucapture.RecordingProtos.*;
import static org.junit.Assert.*;

public class RecordingTextFormatterTest {
    @Test public void distinguishesOisSupportFromActualFrameState() {
        String camera = RecordingTextFormatter.cameraInfo(CameraInfo.newBuilder()
                .setCameraId("0").addAvailableOisModes(0).addAvailableOisDataModes(0).build());
        assertTrue(camera.contains("camera_id=0\n"));
        assertTrue(camera.contains("available_ois_modes=[0]\n"));
        assertTrue(camera.contains("available_ois_data_modes=[0]\n"));
        String frame = RecordingTextFormatter.frame(VideoFrameMetaData.newBuilder()
                .setOpticalStabilizationMode(1).setVideoStabilizationMode(0).setOisDataMode(-1).build(), true);
        assertTrue(frame.contains("actual_optical_stabilization_mode=1\n"));
        assertTrue(frame.contains("actual_video_stabilization_mode=0\n"));
        assertTrue(frame.contains("actual_ois_data_mode=-1\n"));
    }
    @Test public void imuPreservesTimestampAndUsesDecimalInEveryLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            IMUData data = IMUData.newBuilder().setTimeNs(1234567890123456789L)
                    .addAllGyro(Arrays.asList(0.0000001f, -0.02f, 0.03f))
                    .addAllAccel(Arrays.asList(0.1f, 0.2f, 9.81f))
                    .addAllMag(Arrays.asList(21.5f, -3.2f, 42.1f))
                    .addGyroDrift(0.01f).addAccelBias(0.02f).addMagBias(0.03f)
                    .setGyroAccuracyValue(3).setAccelAccuracyValue(2).setMagAccuracyValue(1).build();
            String text = RecordingTextFormatter.imu(data);
            assertTrue(text.contains("source=gyroscope,accelerometer,magnetometer\n"));
            assertTrue(text.contains("time_ns=1234567890123456789\n"));
            assertTrue(text.contains("gyroscope_rad_s=[0.0000001, -0.02, 0.03]\n"));
            assertTrue(text.contains("accelerometer_m_s2=[0.1, 0.2, 9.81]\n"));
            assertTrue(text.contains("magnetometer_uT=[21.5, -3.2, 42.1]\n"));
            assertTrue(text.contains("gyroscope_drift_rad_s=[0.01]\n"));
            assertTrue(text.contains("accelerometer_bias_m_s2=[0.02]\n"));
            assertTrue(text.contains("magnetometer_bias_uT=[0.03]\n"));
            assertTrue(text.contains("gyroscope_accuracy=3\n"));
            assertTrue(text.contains("accelerometer_accuracy=2\n"));
            assertTrue(text.contains("magnetometer_accuracy=1\n"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test public void frameRetainsExposureFocusAndAllOisSamples() {
        VideoFrameMetaData frame = VideoFrameMetaData.newBuilder().setTimeNs(1234567890L)
                .setFrameNumber(17).setExposureTimeNs(5000000).setFrameDurationNs(33333333)
                .setFrameReadoutNs(10000000).setIso(200).setFocalLengthMm(4.2f)
                .setEstFocalLengthPix(900.5f).setFocusDistanceDiopters(0.5f).setFocusLocked(true)
                .addOISSamples(VideoFrameMetaData.OISSample.newBuilder().setTimeNs(1234567800L)
                        .setXShift(-0.25f).setYShift(0.5f))
                .addOISSamples(VideoFrameMetaData.OISSample.newBuilder().setTimeNs(1234567900L)
                        .setXShift(0.125f).setYShift(-0.75f)).build();
        String text = RecordingTextFormatter.frame(frame, true);
        for (String line : new String[]{"source=camera", "video_frame_number=17", "time_ns=1234567890",
                "exposure_time_ns=5000000", "frame_duration_ns=33333333", "frame_readout_ns=10000000",
                "iso=200", "focal_length_mm=4.2", "estimated_focal_length_px=900.5",
                "focus_distance_diopters=0.5", "focus_locked=true", "ois_sample_count=2",
                "camera_ois_sample_0_time_ns=1234567800", "camera_ois_sample_0_x_shift_px=-0.25",
                "camera_ois_sample_0_y_shift_px=0.5", "camera_ois_sample_1_time_ns=1234567900",
                "camera_ois_sample_1_x_shift_px=0.125", "camera_ois_sample_1_y_shift_px=-0.75"}) {
            assertTrue(line, text.contains(line + "\n"));
        }
        String unmatched = RecordingTextFormatter.frame(frame, false);
        assertTrue(unmatched.contains("matched_to_video=false\n"));
        assertTrue(unmatched.contains("camera_frame_number=17\n"));
        assertFalse(unmatched.contains("video_frame_number="));
    }

    @Test public void cameraAndSensorInfoRetainCalibrationAndSource() {
        CameraInfo camera = CameraInfo.newBuilder().addIntrinsicParams(100.5f)
                .addOriginalIntrinsicParams(200.5f).addDistortionParams(0.0000001f)
                .setOpticalImageStabilization(true).setVideoStabilization(true).setDistortionCorrection(true)
                .setSensorOrientation(90).setFocusCalibrationValue(2).setTimestampSourceValue(1)
                .setLensPoseReferenceValue(1).addLensPoseRotation(1.0f).addLensPoseTranslation(0.01f)
                .setResolution(CameraInfo.Size.newBuilder().setWidth(1920).setHeight(1080))
                .setPreCorrectionActiveArraySize(CameraInfo.Size.newBuilder().setWidth(4000).setHeight(3000)).build();
        String text = RecordingTextFormatter.cameraInfo(camera);
        for (String line : new String[]{"intrinsic_params_fx_fy_cx_cy_s=[100.5]",
                "original_intrinsic_params_fx_fy_cx_cy_s=[200.5]", "distortion_params_k1_k2_k3_k4_k5=[0.0000001]",
                "optical_image_stabilization=true", "video_stabilization=true", "distortion_correction=true",
                "sensor_orientation_degrees=90", "focus_calibration=2", "timestamp_source=1",
                "lens_pose_reference=1", "lens_pose_rotation_x_y_z_w=[1]", "lens_pose_translation_m=[0.01]",
                "resolution_width_px=1920", "resolution_height_px=1080",
                "pre_correction_active_array_width_px=4000", "pre_correction_active_array_height_px=3000"}) {
            assertTrue(line, text.contains(line + "\n"));
        }
        IMUInfo info = IMUInfo.newBuilder().setGyroInfo("gyro\n[FAKE_SECTION]")
                .setAccelInfo("accelerometer").setMagInfo("magnetometer")
                .setGyroResolution(0.01f).setAccelResolution(0.02f).setMagResolution(0.03f)
                .setSampleFrequency(200).addPlacement(0.01f).build();
        text = RecordingTextFormatter.imuInfo(info);
        assertTrue(text.contains("gyroscope_info=gyro\\n[FAKE_SECTION]\n"));
        assertTrue(text.contains("accelerometer_info=accelerometer\n"));
        assertTrue(text.contains("magnetometer_info=magnetometer\n"));
        assertTrue(text.contains("gyroscope_resolution_rad_s=0.01\n"));
        assertTrue(text.contains("accelerometer_resolution_m_s2=0.02\n"));
        assertTrue(text.contains("magnetometer_resolution_uT=0.03\n"));
        assertTrue(text.contains("estimated_accelerometer_frequency_hz=200\n"));
        assertTrue(text.contains("accelerometer_placement_m=[0.01]\n"));
    }

    @Test public void missingAndNonFiniteDataAreNotInvented() {
        String text = RecordingTextFormatter.imu(IMUData.newBuilder().addGyro(Float.NaN)
                .addGyro(Float.POSITIVE_INFINITY).addGyro(Float.NEGATIVE_INFINITY).build());
        assertTrue(text.contains("gyroscope_rad_s=[NaN, Infinity, -Infinity]\n"));
        assertTrue(text.contains("magnetometer_uT=[]\n"));
        assertTrue(RecordingTextFormatter.header(1234567890123L).contains("start_unix_time_ms=1234567890123\n"));
        text = RecordingTextFormatter.unmatchedFrameTime(VideoFrameToTimestamp.newBuilder()
                .setTimeUs(123456789012345L).setFrameNbr(9).build());
        assertTrue(text.contains("source=video_encoder\n"));
        assertTrue(text.contains("time_us=123456789012345\n"));
        assertTrue(text.contains("video_frame_number=9\n"));
    }
}
