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
                .setFrameNumber(17).setCameraFrameNumber(99).setCameraFrameNumberAvailable(true)
                .setExposureTimeNs(5000000).setFrameDurationNs(33333333)
                .setFrameReadoutNs(10000000).setIso(200).setFocalLengthMm(4.2f)
                .setAperture(1.8f).setEstFocalLengthPix(900.5f).setFocusDistanceDiopters(0.5f)
                .setFocusLocked(true).setAfMode(4).setAfState(1).setLensState(1)
                .addFocusRangeDiopters(0.25f).addFocusRangeDiopters(0.75f).setAfSceneChange(1)
                .addAfRegions(VideoFrameMetaData.AFRegion.newBuilder().setLeft(1).setTop(2)
                        .setRight(3).setBottom(4).setWeight(999))
                .addAllLensIntrinsicCalibration(Arrays.asList(800f, 801f, 400f, 300f, 0f))
                .addOISSamples(VideoFrameMetaData.OISSample.newBuilder().setTimeNs(1234567800L)
                        .setXShift(-0.25f).setYShift(0.5f)
                        .setTargetLabel("KEY_7").setTrialId(12))
                .addOISSamples(VideoFrameMetaData.OISSample.newBuilder().setTimeNs(1234567900L)
                        .setXShift(0.125f).setYShift(-0.75f).setTrialId(-1))
                .addLensIntrinsicsSamples(VideoFrameMetaData.LensIntrinsicsSample.newBuilder()
                        .setTimeNs(1234567850L)
                        .addAllIntrinsics(Arrays.asList(800f, 801f, 400.5f, 300.5f, 0f))).build();
        String text = RecordingTextFormatter.frame(frame, true);
        for (String line : new String[]{"source=camera", "video_frame_number=17", "camera_frame_number=99",
                "time_ns=1234567890", "sensor_timestamp_ns=1234567890",
                "exposure_time_ns=5000000", "frame_duration_ns=33333333", "frame_readout_ns=10000000",
                "rolling_shutter_skew_ns=10000000", "iso=200", "focal_length_mm=4.2", "aperture=1.8",
                "estimated_focal_length_px=900.5", "af_mode=4", "af_mode_name=CONTINUOUS_PICTURE",
                "af_state=1", "af_state_name=PASSIVE_SCAN", "lens_state=1", "lens_state_name=MOVING",
                "focus_distance_diopters=0.5", "focus_locked=true", "focus_range_diopters=[0.25, 0.75]",
                "af_scene_change=1", "af_scene_change_name=DETECTED", "af_regions=[[1, 2, 3, 4, 999]]",
                "lens_intrinsic_calibration_fx_fy_cx_cy_s=[800, 801, 400, 300, 0]",
                "ois_sample_count=2", "lens_intrinsics_sample_count=1", "sample_index=0",
                "parent_camera_frame_number=99", "x_shift_px=-0.25", "y_shift_px=0.5",
                "target_label=KEY_7", "trial_id=12",
                "fx=800", "fy=801", "cx=400.5", "cy=300.5", "skew=0"}) {
            assertTrue(line, text.contains(line + "\n"));
        }
        assertEquals(2, text.split("\\[OIS_SAMPLE\\]", -1).length - 1);
        assertTrue(text.contains("target_label=\ntrial_id=-1\n"));
        assertEquals(1, text.split("\\[LENS_INTRINSICS_SAMPLE\\]", -1).length - 1);
        String unmatched = RecordingTextFormatter.frame(frame, false);
        assertTrue(unmatched.contains("matched_to_video=false\n"));
        assertTrue(unmatched.contains("camera_frame_number=99\n"));
        assertFalse(unmatched.contains("video_frame_number="));
    }

    @Test public void formatsCaptureExperimentAndTouchCollections() {
        String config = RecordingTextFormatter.captureConfig(CaptureConfig.newBuilder()
                .setConfigId(0).setRequestedAfMode(4).setRequestedAfTrigger(0)
                .setRequestedFocusDistanceAvailable(true)
                .setRequestedFocusDistanceDiopters(0.5f).setRequestedOpticalStabilizationMode(0)
                .setRequestedOisDataMode(-1).setRequestedVideoStabilizationMode(0)
                .setRequestedAeMode(1).setRequestedAwbMode(1).addRequestedFpsRange(30)
                .addRequestedFpsRange(30).setCaptureTemplate(3).build());
        assertTrue(config.contains("[CAPTURE_CONFIG]\n"));
        assertTrue(config.contains("requested_af_mode_name=CONTINUOUS_PICTURE\n"));
        assertTrue(config.contains("requested_af_trigger=0\n"));
        assertTrue(config.contains("requested_af_trigger_name=IDLE\n"));
        assertTrue(config.contains("requested_fps_range=[30, 30]\n"));
        assertTrue(config.contains("capture_template_name=TEMPLATE_RECORD\n"));

        String experiment = RecordingTextFormatter.experimentInfo(ExperimentInfo.newBuilder()
                .setAfExperimentMode("CONTINUOUS_VIDEO").setOisExperimentMode("OFF").build());
        assertTrue(experiment.contains("[EXPERIMENT_INFO]\n"));
        assertTrue(experiment.contains("af_experiment_mode=CONTINUOUS_VIDEO\n"));
        assertTrue(experiment.contains("camera_covered=\n"));

        String touch = RecordingTextFormatter.touchEvent(TouchEvent.newBuilder().setTimeNs(123L)
                .setAction(0).setXPx(12.5f).setYPx(42.5f).setPressure(0.75f).setSize(0.1f)
                .setPointerId(2).setTargetLabel("KEY_5").setTrialId(37).build());
        for (String line : new String[]{"[TOUCH_EVENT]", "time_ns=123", "action=0", "action_name=DOWN",
                "x_px=12.5", "y_px=42.5", "pressure=0.75", "size=0.1", "pointer_id=2",
                "target_label=KEY_5", "trial_id=37"}) assertTrue(line, touch.contains(line + "\n"));
    }

    @Test public void cameraAndSensorInfoRetainCalibrationAndSource() {
        CameraInfo camera = CameraInfo.newBuilder().addIntrinsicParams(100.5f)
                .addOriginalIntrinsicParams(200.5f).addDistortionParams(0.0000001f)
                .addAvailableAfModes(4).setMinimumFocusDistanceAvailable(true)
                .setMinimumFocusDistanceDiopters(10f).setHyperfocalDistanceAvailable(true)
                .setHyperfocalDistanceDiopters(0.2f).setFocusDistanceCalibrationAvailable(true)
                .setFocusDistanceCalibration(2).setSupportsAfState(true).setSupportsLensState(true)
                .setSupportsFocusRange(true).setSupportsLensIntrinsicCalibration(true)
                .setDeviceManufacturer("Google").setDeviceModel("Pixel").setAndroidVersion("15")
                .setAndroidApiLevel(35).setCameraHardwareLevelAvailable(true).setCameraHardwareLevel(1)
                .setOpticalImageStabilization(true).setVideoStabilization(true).setDistortionCorrection(true)
                .setSensorOrientation(90).setFocusCalibrationValue(2).setTimestampSourceValue(1)
                .setLensPoseReferenceValue(1).addLensPoseRotation(1.0f).addLensPoseTranslation(0.01f)
                .setResolution(CameraInfo.Size.newBuilder().setWidth(1920).setHeight(1080))
                .setPreCorrectionActiveArraySize(CameraInfo.Size.newBuilder().setWidth(4000).setHeight(3000)).build();
        String text = RecordingTextFormatter.cameraInfo(camera);
        for (String line : new String[]{"intrinsic_params_fx_fy_cx_cy_s=[100.5]",
                "original_intrinsic_params_fx_fy_cx_cy_s=[200.5]", "distortion_params_k1_k2_k3_k4_k5=[0.0000001]",
                "optical_image_stabilization=true", "video_stabilization=true", "distortion_correction=true",
                "available_af_modes=[4]", "minimum_focus_distance_diopters=10",
                "hyperfocal_distance_diopters=0.2", "focus_distance_calibration=2",
                "focus_distance_calibration_name=CALIBRATED", "supports_af_state=true",
                "supports_lens_state=true", "supports_focus_range=true",
                "supports_lens_intrinsic_calibration=true", "device_manufacturer=Google",
                "device_model=Pixel", "android_version=15", "android_api_level=35",
                "camera_hardware_level=1", "camera_hardware_level_name=FULL",
                "sensor_orientation_degrees=90", "focus_calibration=2", "timestamp_source=1",
                "lens_pose_reference=1", "lens_pose_rotation_x_y_z_w=[1]", "lens_pose_translation_m=[0.01]",
                "resolution_width_px=1920", "resolution_height_px=1080",
                "pre_correction_active_array_width_px=4000", "pre_correction_active_array_height_px=3000"}) {
            assertTrue(line, text.contains(line + "\n"));
        }
        IMUInfo info = IMUInfo.newBuilder().setGyroInfo("gyro\n[FAKE_SECTION]")
                .setAccelInfo("accelerometer").setMagInfo("magnetometer")
                .setGyroResolution(0.01f).setAccelResolution(0.02f).setMagResolution(0.03f)
                .setRequestedAccelerometerFrequencyHz(300)
                .setRequestedGyroscopeFrequencyHz(400)
                .setSampleFrequency(299.5f).setEstimatedGyroscopeFrequencyHz(398.5f)
                .addPlacement(0.01f).build();
        text = RecordingTextFormatter.imuInfo(info);
        assertTrue(text.contains("gyroscope_info=gyro\\n[FAKE_SECTION]\n"));
        assertTrue(text.contains("accelerometer_info=accelerometer\n"));
        assertTrue(text.contains("magnetometer_info=magnetometer\n"));
        assertTrue(text.contains("gyroscope_resolution_rad_s=0.01\n"));
        assertTrue(text.contains("accelerometer_resolution_m_s2=0.02\n"));
        assertTrue(text.contains("magnetometer_resolution_uT=0.03\n"));
        assertTrue(text.contains("requested_accelerometer_frequency_hz=300\n"));
        assertTrue(text.contains("requested_gyroscope_frequency_hz=400\n"));
        assertTrue(text.contains("estimated_accelerometer_frequency_hz=299.5\n"));
        assertTrue(text.contains("estimated_gyroscope_frequency_hz=398.5\n"));
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
