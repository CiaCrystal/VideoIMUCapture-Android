package se.lth.math.videoimucapture;

import java.math.BigDecimal;
import java.util.List;

import se.lth.math.videoimucapture.RecordingProtos.CameraInfo;
import se.lth.math.videoimucapture.RecordingProtos.IMUData;
import se.lth.math.videoimucapture.RecordingProtos.IMUInfo;
import se.lth.math.videoimucapture.RecordingProtos.VideoFrameMetaData;
import se.lth.math.videoimucapture.RecordingProtos.VideoFrameToTimestamp;

/** Stable, explicit text schema; does not depend on protobuf debug/toString output. */
final class RecordingTextFormatter {
    private RecordingTextFormatter() {}

    static String header(long unixTimeMs) {
        return "# VideoIMUCapture text format v1; UTF-8\n"
                + "# Numeric values are decimal; vectors are [x, y, z] unless specified.\n"
                + "# time_ns/time_us use the capture clock, not Unix time.\n"
                + "# IMU time_ns is the gyroscope timestamp; accel and mag are aligned by interpolation.\n"
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

    static String frame(VideoFrameMetaData data, boolean matchedToVideo) {
        StringBuilder out = section("FRAME_METADATA", "camera");
        field(out, "matched_to_video", matchedToVideo);
        field(out, "time_ns", data.getTimeNs());
        // For unmatched records this is the camera capture number, not an encoded video index.
        field(out, matchedToVideo ? "video_frame_number" : "camera_frame_number", data.getFrameNumber());
        field(out, "exposure_time_ns", data.getExposureTimeNs());
        field(out, "frame_duration_ns", data.getFrameDurationNs());
        field(out, "frame_readout_ns", data.getFrameReadoutNs());
        field(out, "iso", data.getIso());
        field(out, "focal_length_mm", data.getFocalLengthMm());
        field(out, "estimated_focal_length_px", data.getEstFocalLengthPix());
        field(out, "focus_distance_diopters", data.getFocusDistanceDiopters());
        field(out, "focus_locked", data.getFocusLocked());
        field(out, "ois_sample_count", data.getOISSamplesCount());
        for (int i = 0; i < data.getOISSamplesCount(); i++) {
            VideoFrameMetaData.OISSample sample = data.getOISSamples(i);
            String prefix = "camera_ois_sample_" + i;
            field(out, prefix + "_time_ns", sample.getTimeNs());
            field(out, prefix + "_x_shift_px", sample.getXShift());
            field(out, prefix + "_y_shift_px", sample.getYShift());
        }
        return out.append('\n').toString();
    }

    static String unmatchedFrameTime(VideoFrameToTimestamp data) {
        StringBuilder out = section("FRAME_TIMESTAMP", "video_encoder");
        field(out, "matched_to_camera_metadata", false);
        field(out, "time_us", data.getTimeUs());
        field(out, "video_frame_number", data.getFrameNbr());
        return out.append('\n').toString();
    }
}
