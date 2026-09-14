# VideoIMUCapture-Android
Android application for capturing video and IMU data useful for 3D reconstruction using SLAM and Structure from Motion techniques.


<img src="images/Capture.png" width="33%" border="1" ><img src="images/Settings.png" width="33%" border="1" ><img src="images/Warning_small.png" width="33%" border="1" >

# Description
This Android application is a data collection tool for researchers working with Simultaneous Localization and Mapping (SLAM) and Structure from Motion (SfM).

It records Camera Frames at ~30Hz and requests Inertial Measurement Unit (IMU) data at 100Hz synchronized to the same clock, given that the [Android device supports it](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#SENSOR_INFO_TIMESTAMP_SOURCE).
The camera frames are stored to a H.264/MP4 video file and the frame meta data together with IMU data is stored in a readable UTF-8 `video_meta.txt` file.

A major problem with modern smartphones and 3D reconstruction is that all have Optical Image Stabilization (OIS), which means different camera parameters for each frame.
Furthermore, on many Android devices it cannot be disabled and a rare few actually supply the data of the lens movement.
VideoIMUCapture shows a clear warning if you have this feature on during recording and includes settings for both Optical Image Stabilization and Digital Video Stabilization (DVS).

This code is forked from [mobile-sensor-ar-logger](https://github.com/OSUPCVLab/mobile-ar-sensor-logger) which in turn is based on the [grafika](https://github.com/google/grafika/blob/master/app/src/main/java/com/android/grafika/CameraCaptureActivity.java) project.
For the video capture it uses the Camera2 API.

# Features
- Captures camera frames at ~30Hz to H.264/MP4.
- Requests accelerometer and gyroscope data at 100 Hz (10000 microseconds); actual rates depend on the device. Magnetometer sampling is requested separately at up to 100 Hz.
- Synchronized clock, assuming [the device supports it](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#SENSOR_INFO_TIMESTAMP_SOURCE).
- Stores IMU data, sensor information, camera parameters and frame meta data in a decimal text file with explicit sensor names and units.
- Display warning if OIS or DVS is enabled since this affects the camera parameters.
- Settings menu for configuring video resolution, OIS, DVS, Auto focus and Auto exposure.

# Install
To install on your Android device go to the [Release page](https://github.com/DavidGillsjo/VideoIMUCapture-Android/releases) from your Android device browser and download the latest `.apk` file. You will need to give your browser permission to install the application, but Android should guide you through the necessary steps.

# Calibration
To use the data for 3D reconstruction you will need to calibrate the IMU and Camera, see [Calibration README](calibration/README.md) for help.

# Background video and IMU capture

The main capture screen has a separate **后台采集（视频 + IMU）** button. Configure the camera first, stop any ordinary recording, then tap this button while the app is visible. Wait until the screen/notification reports that recording has started; you can then press Home, switch apps, or lock the screen. A camera foreground service owns the camera, encoder, IMU and TXT writer, with a partial wake lock during the recording. It does not depend on the activity's preview surface. The recording screen displays status instead of live preview in this mode.

Stop using **Stop and save** in the recording notification, or return to the app and tap **停止后台采集并保存**. Wait for the saved notification. Both `video_recording.mp4` and `video_meta.txt` are stored in the app's external files directory under a timestamped folder ending in `_background`. IMU sampling is requested at **100 Hz** in both modes. The original round record button still starts ordinary foreground preview recording, which stops when the activity pauses.

Background video feeds the Camera2 surface directly into the H.264 encoder and uses the MP4 orientation hint; its pixel buffer is not rotated by the activity's OpenGL renderer. When extracting images downstream, honor the MP4 rotation metadata to match the oriented dimensions/intrinsics in TXT. OIS settings and frame metadata are collected by the same camera code as ordinary recording; encoder timestamps continue to be matched to camera metadata.

Allow camera permission and notifications. Background capture must be started from the visible app; there is no automatic recording on boot or process restart. Camera revocation/disconnection, storage failure or a system force-stop can interrupt a recording; force-stop/process termination cannot guarantee finalized MP4/TXT. Phone power-management settings may also restrict long recordings. The service shows capture failures rather than silently restarting the camera. See [Android camera foreground services](https://developer.android.com/develop/background-work/services/fgs/service-types#camera).

Device acceptance check: record for 10 seconds with the app visible, 20 seconds on Home, then 20 seconds with the screen locked; stop from the notification. Check MP4 duration/playback and TXT frame/IMU timestamps across both transitions. Repeat returning to the app during recording and starting a second recording after saving. Build/unit tests do not substitute for this camera/lock-screen test on the target device.

# Build in Android Studio
Open the `android_app` directory, which contains `settings.gradle`, rather than the repository root.

The build uses Android Gradle Plugin **8.7.3**, Gradle Wrapper **8.9**, and **JDK 17 or 21** (the local setup uses JDK 21). Use an Android Studio version that supports AGP 8.7, such as Ladybug 2024.2.1. Install **Android SDK Platform 35** and **SDK Build-Tools 34.0.0** in SDK Manager. `compileSdk` is 35; `targetSdk` remains 30 and `minSdk` remains 24, so upgrading the build tools does not opt into newer Android runtime/storage behavior. Java source/bytecode compatibility remains Java 8.

1. In **Settings > Build, Execution, Deployment > Build Tools > Gradle**, use the project's Gradle Wrapper and select JDK 17 or 21 as **Gradle JDK**. The project selects `GRADLE_LOCAL_JAVA_HOME`, which reads `java.home` from `android_app/.gradle/config.properties`. This local file is ignored by Git; on another computer choose your installed JDK in the IDE. Do not select JDK 8 or 11 for this build.
2. Check the SDK location in SDK Manager. Android Studio records it in `android_app/local.properties` as `sdk.dir`; this machine-specific file is not committed.
3. Run **File > Sync Project with Gradle Files** and wait for dependency downloads.
4. Select the `debug` build variant, then **Build > Build Bundle(s) / APK(s) > Build APK(s)** (the submenu name varies by IDE version).
5. Find the installable debug APK at `android_app/app/build/outputs/apk/debug/app-debug.apk`, or connect a phone with USB debugging and click **Run**.

For command-line builds, set `JAVA_HOME` to JDK 17 or 21, enter `android_app`, and run `gradlew.bat :app:assembleDebug :app:testDebugUnitTest` on Windows (`./gradlew` on Linux/macOS). The IDE Gradle JDK selection does not set the shell's `JAVA_HOME`.

Dependencies resolve through Google Maven and Maven Central. Google Services, Crashlytics and Protobuf build plugins have been upgraded with AGP; `BuildConfig` generation is explicitly enabled and the app namespace is declared in the module build file. The Firebase configuration remains in `android_app/app/google-services.json`.

Compatibility references: [AGP 8.7 requirements](https://developer.android.com/build/releases/agp-8-7-0-release-notes), [Android Gradle JDK selection](https://developer.android.com/build/jdks).

# Read Text File
New recordings contain `video_meta.txt`. Open it with a text editor; no protobuf decoder is required. Each record starts with a section name and contains `key=value` lines, followed by a blank line. Numbers use decimal notation with a period as the decimal separator, independent of the phone's locale. Timestamps remain exact integer values. Strings escape backslashes, carriage returns and newlines as `\\`, `\r` and `\n`.

Record types are `RECORDING`, `IMU_INFO`, `IMU_DATA`, `CAMERA_INFO`, `FRAME_METADATA` and (when unmatched) `FRAME_TIMESTAMP`. The `source` line identifies the sensor or subsystem. IMU vectors are ordered x, y, z: `gyroscope_rad_s`, `accelerometer_m_s2` and `magnetometer_uT`, with corresponding drift/bias and accuracy fields. `IMU_INFO` includes the device-reported sensor names/vendor information and resolutions. IMU samples use the gyroscope timestamp; acceleration and magnetic field values are interpolated onto that timestamp, not independent raw samples at that exact instant. The reported estimated frequency is measured from accelerometer callbacks.

Camera records retain intrinsics, distortion, stabilization, lens pose, resolution, exposure, ISO, focus and individual OIS samples. Matched frame metadata uses `video_frame_number`. Unmatched camera metadata is retained with `matched_to_video=false` and `camera_frame_number`; unmatched encoder timestamps are retained separately. Match status is explicit so these records are not mistaken for encoded video frames.

`start_unix_time_ms` is wall-clock time; sensor/frame timestamps use the capture clock. Empty arrays indicate unavailable values. Scalar defaults retain the existing protobuf schema semantics (a zero does not necessarily mean the device supplied a measurement). Non-finite floats, if present, are written as `NaN` or `Infinity`, not fabricated numeric values.

Example IMU record excerpt (illustrative values):
```text
[IMU_DATA]
source=gyroscope,accelerometer,magnetometer
time_ns=123456789012345
gyroscope_rad_s=[0.01, -0.02, 0.03]
gyroscope_drift_rad_s=[0, 0, 0]
gyroscope_accuracy=3
accelerometer_m_s2=[0.1, 0.2, 9.81]
accelerometer_bias_m_s2=[0, 0, 0]
accelerometer_accuracy=3
magnetometer_uT=[21.5, -3.2, 42.1]
magnetometer_bias_uT=[0, 0, 0]
magnetometer_accuracy=3
```

Text files are larger than binary protobuf files. The existing calibration scripts below still expect legacy `.pb3` input and cannot directly read the new TXT format. Protobuf remains an internal message representation; new recordings no longer export binary `.pb3` metadata. Existing recordings are not converted automatically.

The accelerometer/gyroscope request period is **10000 microseconds (100 Hz)**. Preview samples are not buffered for later recording. The synchronizer drains all ready samples on every sensor callback and bounds each sensor queue to 512 samples. Magnetometer interpolation waits at most 100 ms of sensor time; if no bracket is available, `magnetometer_uT=[]` and its bias array is empty. At stop, ready acceleration/gyro pairs are saved without waiting for magnetometer data. Unbracketed acceleration/gyro samples at startup, after a long sensor dropout, or at the recording end cannot be synchronized and are not fabricated.

TXT data is flushed at startup and at least once per second while the writer is progressing. After the file closes the app refreshes Android's media index and shows its path, so it can appear over USB/MTP. The writer does not block sensor callbacks on a full queue: overload ends TXT recording with an explicit error instead of hanging; a failed/incomplete recording must not be used as a complete dataset. Unmatched camera metadata is saved even when more than 100 frames accumulate. New recording is blocked while the previous TXT is still closing.

**A grey OIS switch describes Camera2 control availability for the currently opened camera, not whether the phone has mechanical stabilization.** Its summary explains whether the API exposes no control, OFF only, or ON only. OIS sample reporting is a separate capability. `CAMERA_INFO` now includes `camera_id`, `available_ois_modes` and `available_ois_data_modes`; `[0]` means only OFF is advertised, `[0, 1]` includes ON. Each `FRAME_METADATA` record includes `actual_optical_stabilization_mode`, `actual_video_stabilization_mode` and `actual_ois_data_mode`: -1 means not reported, 0 means OFF, 1 means ON (video stabilization can also report 2 for preview stabilization). The original camera-level stabilization booleans describe requested settings; the per-frame values describe the driver results. The application cannot force an OIS mode that the vendor does not expose through Camera2.

# Read Legacy Protobuf File
Examples on python scripts reading the protobuf file can be found the the [calibration](calibration) folder, for example [data2statistics.py](calibration/data2statistics.py). You need `protoc` to compile a python module first, this is already done in the calibration docker image.
You may compile it yourself like this
```bash
#Go to git repo
cd <some_path>/VideoIMUCapture-Android

#Install protoc
wget -nv "https://github.com/protocolbuffers/protobuf/releases/download/v3.13.0/protoc-3.13.0-linux-x86_64.zip" -O protoc.zip &&\  
sudo unzip protoc.zip -d /usr/local &&\
rm protoc.zip

# Build module - Alternative  1
# Add to pythonpath
mkdir proto_python 
protoc --python_out=proto_python protobuf/recording.proto
export PYTHONPATH="$(pwd)/proto_python:${PYTHONPATH}"

# Build module - Alternative  2
# Just place the parser in your own project.
protoc --python_out=<your_project_dir> protobuf/recording.proto

# Other Python dependencies
pip3 install protobuf pyquaternion

#Run script
python3 calibration/data2statistics.py <datafolder>/<datetime>/video_meta.pb3
```

# Feedback
If you find any bugs or have feature requests, please create an [issue](https://github.com/DavidGillsjo/VideoIMUCapture-Android/issues) on this Github page.
