package se.lth.math.videoimucapture;

import java.math.BigDecimal;
import java.util.List;

import se.lth.math.videoimucapture.RecordingProtos.CameraInfo;
import se.lth.math.videoimucapture.RecordingProtos.CaptureConfig;
import se.lth.math.videoimucapture.RecordingProtos.ExperimentInfo;
import se.lth.math.videoimucapture.RecordingProtos.IMUData;
import se.lth.math.videoimucapture.RecordingProtos.IMUInfo;
import se.lth.math.videoimucapture.RecordingProtos.TouchEvent;
import se.lth.math.videoimucapture.RecordingProtos.VideoFrameMetaData;
import se.lth.math.videoimucapture.RecordingProtos.VideoFrameToTimestamp;

/** Stable, explicit text schema; does not depend on protobuf debug/toString output. */
final class RecordingTextFormatter {
    private RecordingTextFormatter() {}

    static String header(long unixTimeMs) {
        return "# VideoIMUCapture text format v2; UTF-8\n"
                + "# Numeric values are decimal; vectors are [x, y, z] unless specified.\n"
                + "# time_ns/sensor_timestamp_ns/time_us use monotonic capture clocks, not Unix time.\n"
                + "# IMU time_ns is the gyroscope timestamp; accel and mag are aligned by interpolation.\n"
                + "# Touch time_ns is converted from the input-event uptime clock to elapsed realtime.\n"
                + "# Missing/late magnetometer readings are []; they do not stop accel/gyro recording.\n"
                + "# Actual stabilization modes: -1=not reported, 0=off, 1=on (DVS 2=preview).\n"
                + "# Camera state values use -1 when the CaptureResult key/value is not reported.\n"
                + "# Accuracy: 0=UNRELIABLE, 1=LOW, 2=MEDIUM, 3=HIGH.\n"
                + "# Empty arrays mean unavailable; scalar defaults follow the protobuf schema.\n"
                + "[RECORDING]\nstart_unix_time_ms=" + unixTimeMs + "\n\n";
    }

    private static StringBuilder section(String type, String source) {
        return new StringBuilder("[").append(type).append("]\nsource=").append(source).append('\n');
    }

    private static String decimal(float number) {
        return Float.isNaN(number) || Float.isInfinite(number)
                ? Float.toString(number)
                : new BigDecimal(Float.toString(number)).stripTrailingZeros().toPlainString();
    }

    private static void field(StringBuilder out, String name, Object value) {
        out.append(name).append('=');
        if (value instanceof Float) {
            out.append(decimal((Float) value));
        } else if (value instanceof String) {
            out.append(((String) value).replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n"));
        } else {
            out.append(value);
        }
        out.append('\n');
    }

    private static void optionalFloat(StringBuilder out, String name, float value, boolean available) {
        if (available) field(out, name, value);
        else field(out, name, "");
    }

    private static void vector(StringBuilder out, String name, List<Float> values) {
        out.append(name).append("=[");
        for (int i = 0; i < values.size(); i++) {
            if (i != 0) out.append(", ");
            out.append(decimal(values.get(i)));
        }
        out.append("]\n");
    }

    static String imu(IMUData data) {
        StringBuilder out = section("IMU_DATA", "gyroscope,accelerometer,magnetometer");
        field(out, "time_ns", data.getTimeNs());
        vector(out, "gyroscope_rad_s", data.getGyroList());
        vector(out, "gyroscope_drift_rad_s", data.getGyroDriftList());
        field(out, "gyroscope_accuracy", data.getGyroAccuracyValue());
        vector(out, "accelerometer_m_s2", data.getAccelList());
        vector(out, "accelerometer_bias_m_s2", data.getAccelBiasList());
        field(out, "accelerometer_accuracy", data.getAccelAccuracyValue());
        vector(out, "magnetometer_uT", data.getMagList());
        vector(out, "magnetometer_bias_uT", data.getMagBiasList());
        field(out, "magnetometer_accuracy", data.getMagAccuracyValue());
        return out.append('\n').toString();
    }

    static String imuInfo(IMUInfo data) {
        StringBuilder out = section("IMU_INFO", "gyroscope,accelerometer,magnetometer");
        field(out, "gyroscope_info", data.getGyroInfo());
        field(out, "gyroscope_resolution_rad_s", data.getGyroResolution());
        field(out, "accelerometer_info", data.getAccelInfo());
        field(out, "accelerometer_resolution_m_s2", data.getAccelResolution());
        field(out, "magnetometer_info", data.getMagInfo());
        field(out, "magnetometer_resolution_uT", data.getMagResolution());
        field(out, "estimated_accelerometer_frequency_hz", data.getSampleFrequency());
        vector(out, "accelerometer_placement_m", data.getPlacementList());
        return out.append('\n').toString();
    }

    static String cameraInfo(CameraInfo data) {
        StringBuilder out = section("CAMERA_INFO", "camera");
        field(out, "camera_id", data.getCameraId());
        field(out, "available_ois_modes", data.getAvailableOisModesList());
        field(out, "available_ois_data_modes", data.getAvailableOisDataModesList());
        field(out, "available_ois_data_modes_key_available", data.getAvailableOisDataModesKeyAvailable());
        field(out, "available_af_modes", data.getAvailableAfModesList());
        optionalFloat(out, "minimum_focus_distance_diopters", data.getMinimumFocusDistanceDiopters(),
                data.getMinimumFocusDistanceAvailable());
        optionalFloat(out, "hyperfocal_distance_diopters", data.getHyperfocalDistanceDiopters(),
                data.getHyperfocalDistanceAvailable());
        field(out, "focus_distance_calibration", data.getFocusDistanceCalibrationAvailable()
                ? data.getFocusDistanceCalibration() : "");
        field(out, "focus_distance_calibration_name", data.getFocusDistanceCalibrationAvailable()
                ? focusCalibrationName(data.getFocusDistanceCalibration()) : "");
        field(out, "supports_af_state", data.getSupportsAfState());
        field(out, "supports_lens_state", data.getSupportsLensState());
        field(out, "supports_focus_range", data.getSupportsFocusRange());
        field(out, "supports_af_scene_change", data.getSupportsAfSceneChange());
        field(out, "supports_lens_intrinsic_calibration", data.getSupportsLensIntrinsicCalibration());
        field(out, "supports_lens_intrinsics_samples", data.getSupportsLensIntrinsicsSamples());
        field(out, "supports_ois_data", data.getSupportsOisData());
        field(out, "supports_ois_samples", data.getSupportsOisSamples());
        field(out, "device_manufacturer", data.getDeviceManufacturer());
        field(out, "device_model", data.getDeviceModel());
        field(out, "android_version", data.getAndroidVersion());
        field(out, "android_api_level", data.getAndroidApiLevel());
        field(out, "camera_hardware_level", data.getCameraHardwareLevelAvailable()
                ? data.getCameraHardwareLevel() : "");
        field(out, "camera_hardware_level_name", data.getCameraHardwareLevelAvailable()
                ? hardwareLevelName(data.getCameraHardwareLevel()) : "");
        vector(out, "intrinsic_params_fx_fy_cx_cy_s", data.getIntrinsicParamsList());
        vector(out, "original_intrinsic_params_fx_fy_cx_cy_s", data.getOriginalIntrinsicParamsList());
        vector(out, "distortion_params_k1_k2_k3_k4_k5", data.getDistortionParamsList());
        field(out, "optical_image_stabilization", data.getOpticalImageStabilization());
        field(out, "video_stabilization", data.getVideoStabilization());
        field(out, "distortion_correction", data.getDistortionCorrection());
        field(out, "sensor_orientation_degrees", data.getSensorOrientation());
        field(out, "focus_calibration", data.getFocusCalibrationValue());
        field(out, "focus_calibration_name", data.getFocusCalibration().name());
        field(out, "timestamp_source", data.getTimestampSourceValue());
        field(out, "timestamp_source_name", data.getTimestampSource().name());
        field(out, "lens_pose_reference", data.getLensPoseReferenceValue());
        field(out, "lens_pose_reference_name", data.getLensPoseReference().name());
        vector(out, "lens_pose_rotation_x_y_z_w", data.getLensPoseRotationList());
        vector(out, "lens_pose_translation_m", data.getLensPoseTranslationList());
        field(out, "resolution_available", data.hasResolution());
        field(out, "resolution_width_px", data.getResolution().getWidth());
        field(out, "resolution_height_px", data.getResolution().getHeight());
        field(out, "pre_correction_active_array_size_available", data.hasPreCorrectionActiveArraySize());
        field(out, "pre_correction_active_array_width_px", data.getPreCorrectionActiveArraySize().getWidth());
        field(out, "pre_correction_active_array_height_px", data.getPreCorrectionActiveArraySize().getHeight());
        return out.append('\n').toString();
    }

    static String captureConfig(CaptureConfig data) {
        StringBuilder out = section("CAPTURE_CONFIG", "camera_request");
        field(out, "config_id", data.getConfigId());
        field(out, "requested_af_mode", data.getRequestedAfMode());
        field(out, "requested_af_mode_name", afModeName(data.getRequestedAfMode()));
        optionalFloat(out, "requested_focus_distance_diopters",
                data.getRequestedFocusDistanceDiopters(), data.getRequestedFocusDistanceAvailable());
        field(out, "requested_optical_stabilization_mode", data.getRequestedOpticalStabilizationMode());
        field(out, "requested_ois_data_mode", data.getRequestedOisDataMode());
        field(out, "requested_video_stabilization_mode", data.getRequestedVideoStabilizationMode());
        field(out, "requested_ae_mode", data.getRequestedAeMode());
        field(out, "requested_awb_mode", data.getRequestedAwbMode());
        field(out, "requested_fps_range", data.getRequestedFpsRangeList());
        field(out, "capture_template", data.getCaptureTemplate());
        field(out, "capture_template_name", data.getCaptureTemplate() == 3 ? "TEMPLATE_RECORD" : "UNKNOWN");
        return out.append('\n').toString();
    }

    static String experimentInfo(ExperimentInfo data) {
        StringBuilder out = section("EXPERIMENT_INFO", "experiment");
        field(out, "experiment_id", data.getExperimentId());
        field(out, "trial_group", data.getTrialGroup());
        field(out, "device_pose", data.getDevicePose());
        field(out, "support_condition", data.getSupportCondition());
        field(out, "af_experiment_mode", data.getAfExperimentMode());
        field(out, "ois_experiment_mode", data.getOisExperimentMode());
        field(out, "camera_covered", data.getCameraCoveredAvailable() ? data.getCameraCovered() : "");
        field(out, "scene_type", data.getSceneType());
        field(out, "target_type", data.getTargetType());
        field(out, "notes", data.getNotes());
        return out.append('\n').toString();
    }

    static String touchEvent(TouchEvent data) {
        StringBuilder out = section("TOUCH_EVENT", "touchscreen");
        field(out, "time_ns", data.getTimeNs());
        field(out, "action", data.getAction());
        field(out, "action_name", touchActionName(data.getAction()));
        field(out, "x_px", data.getXPx());
        field(out, "y_px", data.getYPx());
        field(out, "pressure", data.getPressure());
        field(out, "size", data.getSize());
        field(out, "pointer_id", data.getPointerId());
        field(out, "target_label", data.getTargetLabel());
        field(out, "trial_id", data.getTrialId());
        return out.append('\n').toString();
    }

    static String frame(VideoFrameMetaData data, boolean matchedToVideo) {
        StringBuilder out = section("FRAME_METADATA", "camera");
        field(out, "matched_to_video", matchedToVideo);
        field(out, "time_ns", data.getTimeNs());
        field(out, "sensor_timestamp_ns", data.getTimeNs());
        long cameraFrameNumber = data.getCameraFrameNumberAvailable()
                ? data.getCameraFrameNumber() : data.getFrameNumber();
        field(out, "camera_frame_number", cameraFrameNumber);
        if (matchedToVideo) field(out, "video_frame_number", data.getFrameNumber());
        field(out, "exposure_time_ns", data.getExposureTimeNs());
        field(out, "frame_duration_ns", data.getFrameDurationNs());
        field(out, "frame_readout_ns", data.getFrameReadoutNs());
        field(out, "rolling_shutter_skew_ns", data.getFrameReadoutNs());
        field(out, "iso", data.getIso());
        field(out, "focal_length_mm", data.getFocalLengthMm());
        field(out, "aperture", data.getAperture());
        field(out, "estimated_focal_length_px", data.getEstFocalLengthPix());
        field(out, "af_mode", data.getAfMode());
        field(out, "af_mode_name", afModeName(data.getAfMode()));
        field(out, "af_state", data.getAfState());
        field(out, "af_state_name", afStateName(data.getAfState()));
        field(out, "lens_state", data.getLensState());
        field(out, "lens_state_name", lensStateName(data.getLensState()));
        field(out, "focus_distance_diopters", data.getFocusDistanceDiopters());
        field(out, "focus_locked", data.getFocusLocked());
        vector(out, "focus_range_diopters", data.getFocusRangeDioptersList());
        field(out, "af_scene_change", data.getAfSceneChange());
        field(out, "af_scene_change_name", afSceneChangeName(data.getAfSceneChange()));
        out.append("af_regions=[");
        for (int i = 0; i < data.getAfRegionsCount(); i++) {
            if (i != 0) out.append(", ");
            VideoFrameMetaData.AFRegion region = data.getAfRegions(i);
            out.append('[').append(region.getLeft()).append(", ").append(region.getTop())
                    .append(", ").append(region.getRight()).append(", ").append(region.getBottom())
                    .append(", ").append(region.getWeight()).append(']');
        }
        out.append("]\n");
        vector(out, "lens_intrinsic_calibration_fx_fy_cx_cy_s",
                data.getLensIntrinsicCalibrationList());
        field(out, "actual_optical_stabilization_mode", data.getOpticalStabilizationMode());
        field(out, "actual_video_stabilization_mode", data.getVideoStabilizationMode());
        field(out, "actual_ois_data_mode", data.getOisDataMode());
        field(out, "ois_sample_count", data.getOISSamplesCount());
        field(out, "lens_intrinsics_sample_count", data.getLensIntrinsicsSamplesCount());
        out.append('\n');
        for (int i = 0; i < data.getOISSamplesCount(); i++) {
            VideoFrameMetaData.OISSample sample = data.getOISSamples(i);
            out.append(section("OIS_SAMPLE", "camera"));
            field(out, "parent_camera_frame_number", cameraFrameNumber);
            field(out, "sample_index", i);
            field(out, "time_ns", sample.getTimeNs());
            field(out, "x_shift_px", sample.getXShift());
            field(out, "y_shift_px", sample.getYShift());
            out.append('\n');
        }
        for (int i = 0; i < data.getLensIntrinsicsSamplesCount(); i++) {
            VideoFrameMetaData.LensIntrinsicsSample sample = data.getLensIntrinsicsSamples(i);
            out.append(section("LENS_INTRINSICS_SAMPLE", "camera"));
            field(out, "parent_camera_frame_number", cameraFrameNumber);
            field(out, "sample_index", i);
            field(out, "time_ns", sample.getTimeNs());
            field(out, "fx", floatAt(sample.getIntrinsicsList(), 0));
            field(out, "fy", floatAt(sample.getIntrinsicsList(), 1));
            field(out, "cx", floatAt(sample.getIntrinsicsList(), 2));
            field(out, "cy", floatAt(sample.getIntrinsicsList(), 3));
            field(out, "skew", floatAt(sample.getIntrinsicsList(), 4));
            out.append('\n');
        }
        return out.toString();
    }

    private static Object floatAt(List<Float> values, int index) {
        return index < values.size() ? values.get(index) : "";
    }

    static String afModeName(int value) {
        switch (value) {
            case 0: return "OFF";
            case 1: return "AUTO";
            case 2: return "MACRO";
            case 3: return "CONTINUOUS_VIDEO";
            case 4: return "CONTINUOUS_PICTURE";
            case 5: return "EDOF";
            case -1: return "NOT_REPORTED";
            default: return "UNKNOWN";
        }
    }

    private static String afStateName(int value) {
        switch (value) {
            case 0: return "INACTIVE";
            case 1: return "PASSIVE_SCAN";
            case 2: return "PASSIVE_FOCUSED";
            case 3: return "ACTIVE_SCAN";
            case 4: return "FOCUSED_LOCKED";
            case 5: return "NOT_FOCUSED_LOCKED";
            case 6: return "PASSIVE_UNFOCUSED";
            case -1: return "NOT_REPORTED";
            default: return "UNKNOWN";
        }
    }

    private static String lensStateName(int value) {
        switch (value) {
            case 0: return "STATIONARY";
            case 1: return "MOVING";
            case -1: return "NOT_REPORTED";
            default: return "UNKNOWN";
        }
    }

    private static String afSceneChangeName(int value) {
        switch (value) {
            case 0: return "NOT_DETECTED";
            case 1: return "DETECTED";
            case -1: return "NOT_REPORTED";
            default: return "UNKNOWN";
        }
    }

    private static String focusCalibrationName(int value) {
        switch (value) {
            case 0: return "UNCALIBRATED";
            case 1: return "APPROXIMATE";
            case 2: return "CALIBRATED";
            default: return "UNKNOWN";
        }
    }

    private static String hardwareLevelName(int value) {
        switch (value) {
            case 0: return "LIMITED";
            case 1: return "FULL";
            case 2: return "LEGACY";
            case 3: return "LEVEL_3";
            case 4: return "EXTERNAL";
            default: return "UNKNOWN";
        }
    }

    private static String touchActionName(int value) {
        switch (value) {
            case 0: return "DOWN";
            case 1: return "UP";
            case 5: return "POINTER_DOWN";
            case 6: return "POINTER_UP";
            case 3: return "CANCEL";
            default: return "UNKNOWN";
        }
    }

    static String unmatchedFrameTime(VideoFrameToTimestamp data) {
        StringBuilder out = section("FRAME_TIMESTAMP", "video_encoder");
        field(out, "matched_to_camera_metadata", false);
        field(out, "time_us", data.getTimeUs());
        field(out, "video_frame_number", data.getFrameNbr());
        return out.append('\n').toString();
    }
}
