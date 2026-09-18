# VideoIMUCapture 导出字段说明

适用范围：当前项目导出的 UTF-8 `video_meta.txt`（文件头标记 `text format v2`）。文件仍采用原有的 `[SECTION]` + `key=value` 纯文本格式；Protobuf 只作为 App 内部消息结构。

本文以 **TXT 中实际出现的字段名** 为主，同时列出内部 Protobuf 字段和数据来源。这里只描述现有导出内容，不表示所有手机都能提供这些数据。

## 1. 文件结构与通用规则

| 区块 | 内容 | 出现方式 |
|---|---|---|
| `[RECORDING]` | 录制起始系统时间 | 文件头一次 |
| `[IMU_INFO]` | 陀螺仪、加速度计、磁力计的设备信息、分辨率及频率估计 | 开始录制时一次 |
| `[IMU_DATA]` | 对齐到陀螺仪时间戳的 IMU 样本 | 持续重复 |
| `[EXPERIMENT_INFO]` | 实验标签及 AF/OIS 实验模式；当前没有 UI 的标签为空 | 开始录制时一次 |
| `[CAMERA_INFO]` | 相机、AF/OIS 静态能力、设备信息、内参与位姿 | 开始录制时一次 |
| `[CAPTURE_CONFIG]` | 本次实际构建的 Camera2 请求配置 | 开始录制时一次 |
| `[FRAME_METADATA]` | 相机逐帧时间、曝光、AF/Lens 状态、内参和样本计数 | 每条相机帧元数据 |
| `[OIS_SAMPLE]` | 帧内 OIS 位移样本 | 设备提供时重复 |
| `[LENS_INTRINSICS_SAMPLE]` | API 35+ 帧内镜头内参样本 | 设备提供时重复 |
| `[TOUCH_EVENT]` | App 窗口内 DOWN/UP/多指/取消事件 | 录制中发生触摸时重复 |
| `[FRAME_TIMESTAMP]` | 无法匹配相机元数据的编码帧时间戳 | 仅未匹配时 |
| `[RECORDING_ERROR]` | TXT 写入队列过载，录制不完整 | 仅特定错误情况下 |

每个区块由 `[区块名]` 开始，后面是 `key=value`，以空行分隔。`#` 开头为说明行。

- 整数、浮点数均以十进制文本输出；小数点为 `.`，不受手机语言影响。
- 浮点测量值内部通常为 32 位 `float`。文本位数多不代表传感器具有同等物理精度。
- 时间戳内部为 64 位整数，解析时不要先转为低精度浮点数；尤其不能用 32 位整数存储。
- 三轴向量通常为 `[x, y, z]`，四元数为 `[x, y, z, w]`，内参与畸变参数有各自顺序。
- `[]` 表示没有导出该数组的数据，不能当作 `[0, 0, 0]`。具体原因见各字段说明。
- 普通标量采用 Protobuf 默认值：`0`、`false` 或空字符串可能表示未赋值，不能一律解释为有效测量。当前新录制的实际防抖状态专门使用 `-1` 表示未报告。
- 非有限浮点数保留为 `NaN`、`Infinity`、`-Infinity`。
- 字符串中的反斜杠、回车、换行分别转义为 `\\`、`\r`、`\n`。解析键值时只在**第一个 `=`** 处分割，因为传感器信息字符串内部也有 `=`。
- 相同区块名会重复，不能把整个文件作为只允许每个 section 出现一次的普通 INI 文件读取。

### 通用 `source` 字段

| 所在区块 | `source` 的固定内容 | 含义 |
|---|---|---|
| `IMU_INFO`、`IMU_DATA` | `gyroscope,accelerometer,magnetometer` | IMU 数据类别；即使磁力计数组为空，该标签也保持不变 |
| `CAMERA_INFO`、`FRAME_METADATA`、`OIS_SAMPLE`、`LENS_INTRINSICS_SAMPLE` | `camera` | Camera2 相机数据 |
| `CAPTURE_CONFIG` | `camera_request` | App 发送的 Camera2 请求配置 |
| `EXPERIMENT_INFO` | `experiment` | 实验条件 |
| `TOUCH_EVENT` | `touchscreen` | App 窗口触摸事件 |
| `FRAME_TIMESTAMP` | `video_encoder` | 视频编码器数据 |

`RECORDING` 和 `RECORDING_ERROR` 没有 `source`。`source` 是应用添加的标签，不是 Android 返回的设备名称，也不是 Protobuf 字段。

## 2. RECORDING：录制时间

| TXT 字段 | 类型 / 单位 | 来源与含义 |
|---|---|---|
| `start_unix_time_ms` | int64 / ms | 写入线程初始化文件时的 `System.currentTimeMillis()`，即 Unix 墙上时钟时间；不是首个 IMU 样本或首个视频帧的时间戳 |

旧二进制结构中的对应概念是 `VideoCaptureData.time`（`google.protobuf.Timestamp`，seconds + nanos）；当前 TXT 直接输出毫秒整数。

## 3. IMU_INFO：传感器元数据

### 3.1 字段列表

| TXT 字段 | 内部 `IMUInfo` 字段 | 类型 / 单位 | 来源与说明 |
|---|---|---|---|
| `gyroscope_info` | `gyro_info` | string | 陀螺仪 `Sensor.toString()`，包括驱动报告的名称、厂商、类型等 |
| `gyroscope_resolution_rad_s` | `gyro_resolution` | float / rad/s | 陀螺仪 `Sensor.getResolution()`；分辨率，不是采样频率或噪声密度 |
| `accelerometer_info` | `accel_info` | string | 加速度计 `Sensor.toString()` |
| `accelerometer_resolution_m_s2` | `accel_resolution` | float / m/s² | 加速度计 `Sensor.getResolution()` |
| `magnetometer_info` | `mag_info` | string | 磁力计 `Sensor.toString()`；没有传感器时可为空字符串 |
| `magnetometer_resolution_uT` | `mag_resolution` | float / μT | 磁力计 `Sensor.getResolution()`；未赋值时为 0 |
| `estimated_accelerometer_frequency_hz` | `sample_frequency` | float / Hz | 由**加速度计事件时间戳间隔**平滑估计，开始录制时写入一次；不是陀螺仪实测频率，也不是最终 TXT 样本频率 |
| `accelerometer_placement_m` | `placement` | float[3] / m | 从加速度计 `TYPE_SENSOR_PLACEMENT` 附加信息的 3、7、11 索引提取位置平移；未提供或开始录制时尚未收到则为 `[]`，不是完整 3×4 放置矩阵 |

### 3.2 `*_info` 字符串内部内容

这些子项保留在一个描述字符串内，**不是独立的 TXT 顶层字段**。Android 实现可能增加字段，不应把 `Sensor.toString()` 当作严格稳定的结构化协议。

| 子项 | 含义 | 单位或示例 |
|---|---|---|
| `name` | 传感器驱动报告名称 | `icm42631_uncali_gyro` |
| `vendor` | 驱动报告厂商字符串 | 可能与 name 相同，不保证是可辨认的商业厂商名称 |
| `version` | 传感器实现版本 | 整数，非 Android 系统版本 |
| `type` | Android 传感器类型编号 | 本机陀螺仪 16、加速度计 35、磁力计 14 |
| `maxRange` | 最大测量范围 | 随传感器：rad/s、m/s²、μT |
| `resolution` | 测量分辨率 | 与该传感器测量值相同单位 |
| `power` | 驱动报告功耗电流 | mA |
| `minDelay` | 驱动报告最小事件周期 | μs；是能力信息，不是此次录制的实际间隔 |

可用 `1,000,000 / minDelay` 估算驱动报告的最高频率（`minDelay > 0` 时），但系统限制、请求周期、负载和驱动行为都会影响实际结果。接口定义见 [Android Sensor](https://developer.android.com/reference/android/hardware/Sensor)。

## 4. IMU_DATA：逐样本测量值

| TXT 字段 | 内部 `IMUData` 字段 | 类型 / 单位 | 来源与处理 |
|---|---|---|---|
| `time_ns` | `time_ns` | int64 / ns | 原始陀螺仪事件的 `SensorEvent.timestamp`，作为这条组合样本的参考时间 |
| `gyroscope_rad_s` | `gyro` | float[3] / rad/s | `TYPE_GYROSCOPE_UNCALIBRATED` 的 values[0..2]，三轴角速度 |
| `gyroscope_drift_rad_s` | `gyro_drift` | float[3] / rad/s | 同一陀螺仪事件 values[3..5]，驱动估计的三轴漂移/零偏；不是漂移变化率。数组不足 6 个元素时为空 |
| `gyroscope_accuracy` | `gyro_accuracy` | int / 等级 | 最近一次陀螺仪 `onAccuracyChanged` 回调值 |
| `accelerometer_m_s2` | `accel` | float[3] / m/s² | 加速度计 values[0..2] 对齐到陀螺仪时刻；含重力响应，不是去重力的线性加速度 |
| `accelerometer_bias_m_s2` | `accel_bias` | float[3] / m/s² | 未校准加速度计 values[3..5] 的零偏估计，也参与时间插值；没有该通道时为空 |
| `accelerometer_accuracy` | `accel_accuracy` | int / 等级 | 最近一次加速度计 `onAccuracyChanged` 回调值 |
| `magnetometer_uT` | `mag` | float[3] / μT | 未校准磁力计 values[0..2]，在可用时插值到陀螺仪时刻 |
| `magnetometer_bias_uT` | `mag_bias` | float[3] / μT | 磁力计 values[3..5] 的偏置估计，随磁场值一起插值；不是磁偏角 |
| `magnetometer_accuracy` | `mag_accuracy` | int / 等级 | 最近一次磁力计 `onAccuracyChanged` 回调值；磁场数组为空时，该等级不证明存在有效磁场测量 |

### 4.1 gyro-related metadata 如何理解

与陀螺仪直接有关的是：设备描述 `gyroscope_info`、分辨率 `gyroscope_resolution_rad_s`、样本时间 `time_ns`、角速度 `gyroscope_rad_s`、驱动零偏估计 `gyroscope_drift_rad_s` 和准确度等级 `gyroscope_accuracy`。

角速度不是欧拉角，也不是姿态四元数。`UNCALIBRATED` 指没有应用该接口返回的漂移补偿，不等于完全未经工厂处理的原始 ADC 数据。若下游选择使用驱动偏置补偿，可按分量计算 `angular_velocity_corrected = gyroscope_rad_s - gyroscope_drift_rad_s`；当前应用没有做这一步，不要在下游重复补偿。定义见 [Android SensorEvent](https://developer.android.com/reference/android/hardware/SensorEvent)。

`lens_pose_reference_name=GYROSCOPE` 是**相机位姿参考系标签**，不是额外的陀螺仪测量；`accelerometer_placement_m` 也不是陀螺仪的位置。

### 4.2 传感器类型、坐标轴和准确度

- 陀螺仪使用 `TYPE_GYROSCOPE_UNCALIBRATED`（16）。
- Android 8.0 / API 26 起使用 `TYPE_ACCELEROMETER_UNCALIBRATED`（35）；更早系统使用 `TYPE_ACCELEROMETER`（1），没有单独 bias 数组。
- 磁力计使用 `TYPE_MAGNETIC_FIELD_UNCALIBRATED`（14）；没有磁力计时仍可录制加速度计和陀螺仪。
- IMU 向量保持 Android 设备传感器坐标系，没有在代码中旋转到视频像素坐标系。设备自然方向下，面向屏幕，x 向右、y 向上、z 从屏幕朝外；不要因视频旋转就假定 IMU 轴也旋转了。参考 [SensorEvent 坐标定义](https://developer.android.com/reference/android/hardware/SensorEvent)。

| 准确度值 | 名称 | 解释 |
|---|---|---|
| 0 | `UNRELIABLE` | 不可靠；也可能是当前代码尚未收到准确度回调时的初始值 |
| 1 | `LOW` | 低 |
| 2 | `MEDIUM` | 中 |
| 3 | `HIGH` | 高 |

准确度等级不是方差、协方差、误差百分比或置信区间。当前实现记录的是写出时缓存的最近回调等级，不是每个历史事件独立保存的 accuracy。

### 4.3 采样、插值和缺失数据

当前加速度计和陀螺仪请求周期为 **10000 μs（100 Hz）**，普通录制和后台录制共用此设置；磁力计独立请求周期为 `max(10000, getMinDelay())` μs。请求值不保证实际达到。后文 vivo X80 样本是此前 200 Hz 版本的历史录制，不代表当前默认频率。

同步器以陀螺仪为参考。对有前后样本夹住的目标时刻，线性插值加速度和磁场（包括对应 bias 通道）；恰好同时间戳时使用该样本的副本。磁力计最多等待 100 ms 的传感器时间；无法形成插值区间时输出 `[]`，不会用零代替。停止录制时不再等待磁力计。

预览样本不积压到下一次录制；每类传感器队列最多 512 个样本。缺少加速度插值区间的陀螺仪样本不能形成完整输出，启动边缘、停录边缘或长时间传感器中断可能导致样本不输出。TXT 没有单独导出这些丢弃样本的计数。

## 5. CAMERA_INFO：相机静态信息与请求设置

### 5.1 相机与防抖能力

| TXT 字段 | 内部 `CameraInfo` 字段 | 类型 | 来源与说明 |
|---|---|---|---|
| `camera_id` | `camera_id` | string | 应用打开的 Camera2 camera ID；不能一概当作物理传感器型号 |
| `available_ois_modes` | `available_ois_modes` | int[] | `LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION`，相机报告的 OIS 控制模式 |
| `available_ois_data_modes` | `available_ois_data_modes` | int[] | API 28+ 的 `STATISTICS_INFO_AVAILABLE_OIS_DATA_MODES`；未提供则为空 |
| `available_ois_data_modes_key_available` | 同名 | bool | 区分“特征键不存在”和“键存在但模式数组为空” |
| `available_af_modes` | `available_af_modes` | int[] | `CONTROL_AF_AVAILABLE_MODES`；0=OFF、1=AUTO、2=MACRO、3=CONTINUOUS_VIDEO、4=CONTINUOUS_PICTURE、5=EDOF |
| `minimum_focus_distance_diopters` | 同名 | float / m⁻¹ | `LENS_INFO_MINIMUM_FOCUS_DISTANCE`；无值时输出空字符串，0 通常表示定焦镜头 |
| `hyperfocal_distance_diopters` | 同名 | float / m⁻¹ | `LENS_INFO_HYPERFOCAL_DISTANCE`；无值时为空 |
| `focus_distance_calibration` / `_name` | 同名 | int / string | 对焦距离标定级别的原始值及名称 |
| `supports_af_state`、`supports_lens_state`、`supports_focus_range`、`supports_af_scene_change` | 同名 | bool | 目标逐帧 CaptureResult key 是否在当前相机的可用结果键列表中 |
| `supports_lens_intrinsic_calibration`、`supports_lens_intrinsics_samples` | 同名 | bool | 普通逐帧内参及 API 35+ 帧内内参样本能力 |
| `supports_ois_data`、`supports_ois_samples` | 同名 | bool | OIS 数据模式和 OIS 样本结果键能力 |
| `device_manufacturer`、`device_model`、`android_version`、`android_api_level` | 同名 | string / int | Android `Build` 设备及系统信息 |
| `camera_hardware_level` / `_name` | 同名 | int / string | Camera2 hardware level；无值时为空 |
| `optical_image_stabilization` | `optical_image_stabilization` | bool | 应用配置的 OIS 开关状态；不是逐帧实际状态 |
| `video_stabilization` | `video_stabilization` | bool | 应用配置的 DVS 开关状态 |
| `distortion_correction` | `distortion_correction` | bool | 应用配置的畸变校正开关状态；没有对应的逐帧实际校正模式字段 |
| `sensor_orientation_degrees` | `sensor_orientation` | int / 度 | `SENSOR_ORIENTATION`，相机传感器方向，通常 0、90、180、270 |

OIS 能力数组中 `0=OFF`、`1=ON`：`[0, 1]` 表示同时声明两种模式，`[1]` 表示厂商只声明 ON，`[0]` 表示只声明 OFF，`[]` 表示没有导出能力值。安装后的 OIS 开关默认 OFF；只要当前相机声明了 ON 且请求键可用，开关就保持可操作。遇到异常的 `[1]` 时仍允许用户选择 OFF 并发送 OFF 请求，但驱动是否接受必须以逐帧 `actual_optical_stabilization_mode` 为准。只有没有声明 ON 或请求键不可用时开关才置灰。能力数组不能代替 CaptureResult 的实际状态。

### 5.2 内参与畸变

| TXT 字段 | 内部字段 | 类型 / 顺序 | 来源与说明 |
|---|---|---|---|
| `intrinsic_params_fx_fy_cx_cy_s` | `intrinsic_params` | float[5] / `[fx, fy, cx, cy, s]` | 从 `LENS_INTRINSIC_CALIBRATION` 经项目的 `FocalLengthHelper.getTransformedIntrinsic()` 缩放、方向变换得到；按项目输出图像坐标解释 |
| `original_intrinsic_params_fx_fy_cx_cy_s` | `original_intrinsic_params` | float[5] / 同上 | CameraCharacteristics 返回的原始内参，未经上述项目变换 |
| `distortion_params_k1_k2_k3_k4_k5` | `distortion_params` | float[5] | API 28+ 的 `LENS_DISTORTION`；前 3 项径向、后 2 项切向，无量纲 |

内参中 `fx/fy` 为像素焦距，`cx/cy` 为主点，`s` 为 skew 参数。可按 `K=[[fx,s,cx],[0,fy,cy],[0,0,1]]` 组织；具体坐标变换以项目实现为准，不应把它视为重新标定的结果。当前缩放假设长边保持未裁剪；动态裁剪、变焦、防抖可能使静态内参不足以描述每一帧。

**当前代码的过滤行为：**仅当原始内参数组存在且 `abs(fx)>0` 时才写出两组内参；仅当畸变数组存在且 `abs(k1)>0` 时才写出畸变。因此畸变为 `[]` 也可能是 `k1=0` 被过滤，不能据此断言镜头无畸变或驱动绝对没有参数。

### 5.3 位姿、时间源和对焦标定

| TXT 字段 | 内部字段 | 类型 / 单位 | 来源与说明 |
|---|---|---|---|
| `focus_calibration` | `focus_calibration` | int enum | `LENS_INFO_FOCUS_DISTANCE_CALIBRATION` |
| `focus_calibration_name` | 同上派生 | string | 上述枚举名称 |
| `timestamp_source` | `timestamp_source` | int enum | `SENSOR_INFO_TIMESTAMP_SOURCE` |
| `timestamp_source_name` | 同上派生 | string | 上述枚举名称 |
| `lens_pose_reference` | `lens_pose_reference` | int enum | API 28+ 的 `LENS_POSE_REFERENCE` |
| `lens_pose_reference_name` | 同上派生 | string | 上述枚举名称 |
| `lens_pose_rotation_x_y_z_w` | `lens_pose_rotation` | float[4] / 无量纲 | `LENS_POSE_ROTATION`，驱动返回的姿态四元数；项目原样保存 |
| `lens_pose_translation_m` | `lens_pose_translation` | float[3] / m | `LENS_POSE_TRANSLATION`，相对于所报告位姿参考的平移；项目原样保存 |

| 枚举 | 数值与名称 |
|---|---|
| `focus_calibration` | `0=UNCALIBRATED`、`1=APPROXIMATE`、`2=CALIBRATED` |
| `timestamp_source` | `0=UNKNOWN`、`1=REALTIME` |
| `lens_pose_reference` | `0=PRIMARY_CAMERA`、`1=GYROSCOPE`、`2=UNDEFINED` |

`REALTIME` 是 Android 的时间源枚举名，**不是 Unix 墙上时钟**。它表示相机时间可与系统 elapsed realtime 时间基准比较；`UNKNOWN` 时不能直接假设相机与 IMU 时间一致。

相机位姿不是 SLAM 估计的世界轨迹，也不能在不检查 `lens_pose_reference` 的情况下直接当作相机到 IMU 的完整外参。位姿、内参和时间源定义见 [CameraCharacteristics 官方说明](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics)。

### 5.4 图像与传感器尺寸

| TXT 字段 | 内部字段 | 类型 / 单位 | 说明 |
|---|---|---|---|
| `resolution_available` | `hasResolution()` 派生 | bool | resolution 子消息是否存在 |
| `resolution_width_px` | `resolution.width` | int / px | 按传感器方向处理后的输出图像宽度 |
| `resolution_height_px` | `resolution.height` | int / px | 输出图像高度；方向为 90/270 度时，代码交换所选视频尺寸的宽高 |
| `pre_correction_active_array_size_available` | `hasPreCorrectionActiveArraySize()` 派生 | bool | 对应尺寸子消息是否存在 |
| `pre_correction_active_array_width_px` | `pre_correction_active_array_size.width` | int / px | `SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE` 的宽度 |
| `pre_correction_active_array_height_px` | `pre_correction_active_array_size.height` | int / px | 上述矩形的高度；没有导出 left/top 原点 |

例如设置页选 1280×960，而 TXT 为 960×1280，可以是方向处理导致，不必然是分辨率配置错误。

## 5.5 EXPERIMENT_INFO 与 CAPTURE_CONFIG

`EXPERIMENT_INFO` 每次录制一条。当前 App 自动填写 `af_experiment_mode` 和 `ois_experiment_mode`；`experiment_id`、`trial_group`、`device_pose`、`support_condition`、`camera_covered`、`scene_type`、`target_type`、`notes` 尚无设置界面，因此保持为空，不会伪造实验标签。

`CAPTURE_CONFIG` 每次录制一条，记录 `config_id`、`requested_af_mode`/`_name`、`requested_af_trigger`/`_name`、`requested_focus_distance_diopters`、请求的 OIS/OIS data/DVS/AE/AWB 模式、`requested_fps_range` 和 `capture_template`/`_name`。正常 AF 采集应为 `requested_af_mode=4`（`CONTINUOUS_PICTURE`）和 `requested_af_trigger=0`（`IDLE`）；只有手动对焦模式才把 `requested_focus_distance_diopters` 视为 App 明确设置的请求。整型请求值为 `-1` 时表示该键未被请求或不可用；这组字段表示 App 请求，不能替代逐帧 CaptureResult 的实际状态。

## 6. FRAME_METADATA：逐帧元数据

| TXT 字段 | 内部 `VideoFrameMetaData` 字段 | 类型 / 单位 | 来源与说明 |
|---|---|---|---|
| `matched_to_video` | 写入器派生 | bool | 是否成功与编码视频帧时间戳匹配 |
| `time_ns` | `time_ns` | int64 / ns | `CaptureResult.SENSOR_TIMESTAMP`，相机帧曝光起始时间基准；不是整帧曝光的中心时刻 |
| `sensor_timestamp_ns` | `time_ns` 的兼容别名 | int64 / ns | 与 `time_ns` 完全相同；推荐新分析代码使用该名称 |
| `video_frame_number` | `frame_number` | int64 | 仅匹配成功时出现；替换为编码器提供的帧编号 |
| `camera_frame_number` | `camera_frame_number` | int64 | 所有相机帧均出现；保留 `CaptureResult.getFrameNumber()`，并作为高频样本的父键 |
| `exposure_time_ns` | `exposure_time_ns` | int64 / ns | `SENSOR_EXPOSURE_TIME`，曝光时长 |
| `frame_duration_ns` | `frame_duration_ns` | int64 / ns | `SENSOR_FRAME_DURATION`，相机报告的帧周期；不是曝光时长 |
| `frame_readout_ns` | `frame_readout_ns` | int64 / ns | `SENSOR_ROLLING_SHUTTER_SKEW`，首行到末行曝光开始的时间差；不是文件写入耗时 |
| `rolling_shutter_skew_ns` | `frame_readout_ns` 的兼容别名 | int64 / ns | 与 `frame_readout_ns` 相同 |
| `iso` | `iso` | int / ISO | `SENSOR_SENSITIVITY`，感光度 |
| `focal_length_mm` | `focal_length_mm` | float / mm | `LENS_FOCAL_LENGTH`，镜头焦距 |
| `aperture` | `aperture` | float / f-number | `LENS_APERTURE` |
| `estimated_focal_length_px` | `est_focal_length_pix` | float / px | 项目根据焦距、对焦距离、物理尺寸、裁剪区域和输出尺寸估算的像素焦距；不是逐帧标定得到的完整 K 矩阵 |
| `af_mode` / `_name` | `af_mode` | int / string | `CONTROL_AF_MODE` 原始值及名称 |
| `af_state` / `_name` | `af_state` | int / string | `CONTROL_AF_STATE`；包括 INACTIVE、PASSIVE_SCAN、PASSIVE_FOCUSED、ACTIVE_SCAN、FOCUSED_LOCKED、NOT_FOCUSED_LOCKED、PASSIVE_UNFOCUSED |
| `lens_state` / `_name` | `lens_state` | int / string | `LENS_STATE`；STATIONARY 或 MOVING |
| `focus_distance_diopters` | `focus_distance_diopters` | float / m⁻¹ | `LENS_FOCUS_DISTANCE`；标定可靠时可近似按距离米数 `1/value` 理解；0 通常对应无限远，但也可能是未赋值 |
| `focus_locked` | `focus_locked` | bool | 当前代码仅判断 AF 状态不是 `ACTIVE_SCAN` 或 `PASSIVE_SCAN`；true 不保证已成功合焦，也不严格等价于 Camera2 的 FOCUSED_LOCKED |
| `focus_range_diopters` | `focus_range_diopters` | float[2] | `LENS_FOCUS_RANGE`；不支持时为 `[]` |
| `af_scene_change` / `_name` | `af_scene_change` | int / string | API 28+ `CONTROL_AF_SCENE_CHANGE`；未报告时为 -1/NOT_REPORTED |
| `af_regions` | `af_regions` | int[][5] | 每项 `[left,top,right,bottom,weight]`，来自 `CONTROL_AF_REGIONS` |
| `lens_intrinsic_calibration_fx_fy_cx_cy_s` | `lens_intrinsic_calibration` | float[5] | 当前 CaptureResult 的 `LENS_INTRINSIC_CALIBRATION`，与静态 CameraCharacteristics 内参分开保存 |
| `actual_optical_stabilization_mode` | `optical_stabilization_mode` | int | `CaptureResult.LENS_OPTICAL_STABILIZATION_MODE`；-1 未报告、0 关闭、1 开启 |
| `actual_video_stabilization_mode` | `video_stabilization_mode` | int | `CONTROL_VIDEO_STABILIZATION_MODE`；-1 未报告、0 关闭、1 开启、2 预览防抖模式 |
| `actual_ois_data_mode` | `ois_data_mode` | int | API 28+ 的 `STATISTICS_OIS_DATA_MODE`；-1 未报告、0 关闭、1 开启 |
| `ois_sample_count` | `OIS_samples` 数量派生 | int | 该帧包含的 OIS 样本数；不是 IMU 样本数 |
| `lens_intrinsics_sample_count` | `lens_intrinsics_samples` 数量派生 | int | 该帧包含的 API 35+ 帧内镜头内参样本数 |

曝光、读取时序与镜头状态的 API 定义见 [CaptureResult](https://developer.android.com/reference/android/hardware/camera2/CaptureResult)。应用没有保存完整 CaptureResult，只有表中明确列出的字段。

## 7. OIS_SAMPLE：光学防抖位移样本

OIS 样本作为独立 `[OIS_SAMPLE]` 区块输出。若父帧的 `ois_sample_count=N`，`sample_index` 从 0 到 N−1；每一帧重新从 0 开始编号。

| TXT 字段 | 内部字段 | 类型 / 单位 | 含义 |
|---|---|---|---|
| `parent_camera_frame_number` | 父帧派生 | int64 | 对应 `[FRAME_METADATA].camera_frame_number` |
| `sample_index` | 数组索引 | int | 父帧内样本编号 |
| `time_ns` | `OIS_samples[i].time_ns` | int64 / ns | `OisSample.getTimestamp()`，该 OIS 样本的时间戳 |
| `x_shift_px` | `OIS_samples[i].x_shift` | float / px | x 方向的光学防抖图像位移，经项目缩放和方向变换 |
| `y_shift_px` | `OIS_samples[i].y_shift` | float / px | y 方向的位移，经同样处理 |

数据来源为 API 28+ 的 `CaptureResult.STATISTICS_OIS_SAMPLES`。Android 原始 OIS 位移以像素表达；本项目不是输出机械镜片移动的毫米数，也不是角速度。参考 [OisSample](https://developer.android.com/reference/android/hardware/camera2/params/OisSample)。

### 7.1 项目实施的坐标变换

令 Android 原始位移为 `(x, y)`，项目比例因子为 `q=FocalLengthHelper.getScale()`：

| `sensor_orientation_degrees` | TXT 中 `(x_shift_px, y_shift_px)` |
|---|---|
| 0 | `(q*x, q*y)` |
| 90 | `(-q*y, q*x)` |
| 180 | `(-q*x, -q*y)` |
| 270 | `(q*y, -q*x)` |

`q` 按输出尺寸与畸变校正前有效阵列尺寸计算：所选视频宽大于高时用 `videoWidth / preCorrectionWidth`，否则用 `videoHeight / preCorrectionHeight`。这是当前实现的缩放规则，不代表已精确补偿所有动态裁剪或变焦影响。文件没有另存原始 OIS 位移或单独的 q 字段。

### 7.2 与陀螺仪的区别

| 比较项 | Gyroscope | OIS samples |
|---|---|---|
| 数据入口 | SensorManager | Camera2 CaptureResult |
| 物理量 / 表达 | 三轴角速度，rad/s | 光学防抖造成的图像位移，px |
| 所在区块 | `IMU_DATA` | 独立 `OIS_SAMPLE` |
| 时间组织 | 每条 IMU 记录一个陀螺仪参考时间 | 每帧可以包含多个带独立时间戳的 OIS 样本 |
| 是否经过项目空间变换 | 保持设备传感器坐标轴 | 按相机方向旋转并缩放 |
| 能否互相替代 | 不能仅凭角速度直接得到完整 OIS 位移 | 不能将像素位移直接当作陀螺仪角速度 |

OIS 已开启不意味着 OIS samples 可读取。`ois_sample_count=0` 不表示防抖位移为零，更不表示 OIS 已关闭；应结合能力数组及实际状态判断。

### 7.3 LENS_INTRINSICS_SAMPLE

Android API 35+ 且相机提供 `STATISTICS_LENS_INTRINSICS_SAMPLES` 时，每个样本输出一条独立区块，包含 `parent_camera_frame_number`、`sample_index`、`time_ns`、`fx`、`fy`、`cx`、`cy`、`skew`。父帧用 `lens_intrinsics_sample_count` 标明数量；不支持时计数为 0，不输出样本区块。

### 7.4 TOUCH_EVENT

录制期间 App 窗口内的 DOWN、UP、POINTER_DOWN、POINTER_UP 和 CANCEL 事件分别输出一条，字段包括 `time_ns`、`action`/`action_name`、`x_px`、`y_px`、`pressure`、`size`、`pointer_id`、`target_label` 和 `trial_id`。时间从输入事件的 uptime 时钟换算到 elapsed realtime，以便与 `REALTIME` Camera 时间和 SensorEvent 时间对齐。

3×3 点击实验默认关闭，可通过 `Settings → Experiment → Enable 3×3 Target Grid` 开启。其下方的 `Target Selection Mode` 提供 `Random` 和 `Custom` 两种模式：随机模式沿用全部九个数字；自定义模式通过 `Custom Target Numbers` 多选 1～9，后续只从所选数字中产生目标，并且至少需要选择一个。前台录制开始时界面从左上到右下对应 `KEY_1`～`KEY_9`，每个 TXT 的 `trial_id` 从 1 开始。随机高亮的格子是本轮目标；点中该格后 `trial_id` 加 1，并在可用目标中随机选择下一格；可用目标多于一个时不会与上一轮相同，只选择一个数字时该目标会重复。落在当前高亮格内的触摸事件自动填写本轮 `target_label` 和 `trial_id`；点错、点击界面外或关闭实验开关时仍保留触摸数据，但写为 `target_label=`、`trial_id=-1`，且不推进轮次。后台采集不显示或启用该交互实验。

## 8. FRAME_TIMESTAMP：未匹配的编码帧

| TXT 字段 | 内部 `VideoFrameToTimestamp` 字段 | 类型 / 单位 | 说明 |
|---|---|---|---|
| `matched_to_camera_metadata` | 写入器派生 | bool | 该区块中固定为 false |
| `time_us` | `time_us` | int64 / μs | 视频编码输出的 presentation timestamp |
| `video_frame_number` | `frame_nbr` | int64 | 编码器记录的帧编号 |

`RecordingWriter` 以 `abs(1000*time_us - camera.time_ns) <= 10000 ns` 为匹配条件，即容差 **10 μs**。匹配成功只输出合并后的 `FRAME_METADATA`；未匹配的相机帧和编码器时间戳分别保留。队列顺序和多源到达顺序不保证整个 TXT 按时间全局排序，分析时应按区块及各自时间戳处理。

## 9. RECORDING_ERROR：录制不完整标记

| TXT 字段 | 类型 | 说明 |
|---|---|---|
| `message` | string | 当前在 4096 条写入队列满时记录的错误原因，说明存储处理跟不上、录制不完整 |

出现该区块时不要把文件当作完整采集结果。反过来，没有该区块也不能证明录制一定完整：磁盘写入本身失败时可能无法再写入错误区块，应用会通过保存回调显示错误；进程被终止也可能只留下部分文件。

## 10. 用户提供的 vivo X80 / Android 13 文件

以下是用户提供的 `video_meta.txt` 的实际值，仅代表这次录制及 camera ID 0，不推广到该机型的所有镜头或所有固件。

| 项目 | 本次实际记录 | 解读 |
|---|---|---|
| 陀螺仪 | `icm42631_uncali_gyro`，type 16 | 未校准陀螺仪接口 |
| 陀螺仪 `minDelay` | 2000 μs | 驱动报告能力对应约 500 Hz，不代表本次以 500 Hz 输出 |
| 加速度计 | `icm42631_uncali_acc`，type 35 | 未校准加速度计接口 |
| 加速度计 `minDelay` | 5000 μs | 驱动报告能力对应约 200 Hz |
| 磁力计 | `akm09918_uncali_mag`，type 14 | 未校准磁力计接口 |
| 磁力计 `minDelay` | 10000 μs | 驱动报告能力对应约 100 Hz |
| `estimated_accelerometer_frequency_hz` | 201.25284 | 录制开始时加速度计频率估计 |
| `camera_id` | `0` | 当前打开的相机 ID |
| `available_ois_modes` | `[1]` | 厂商只声明 OIS ON；新版 App 仍启用开关，并默认选择 OFF |
| `optical_image_stabilization` | `true` | 这是修改前录制文件中的旧值；新版在默认设置下应输出 `false`，用户手动开启后为 `true` |
| `actual_optical_stabilization_mode` | 302 条帧记录均为 1 | 旧文件中驱动逐帧报告 OIS 开启；请求 OFF 后仍须用该字段验证驱动是否实际关闭 |
| `available_ois_data_modes` | `[]` | 未导出 OIS sample reporting 能力 |
| `actual_ois_data_mode` | 302 条均为 -1 | 未报告 OIS 数据模式 |
| `ois_sample_count` | 302 条均为 0 | 本次没有 OIS 位移样本，不是位移恒为零 |
| `actual_video_stabilization_mode` | 302 条均为 0 | 驱动报告 DVS 关闭 |
| `timestamp_source_name` | `REALTIME` | 相机声明使用可与 IMU 比较的系统时间基准 |
| `focus_calibration_name` | `UNCALIBRATED` | 对焦距离不能当作高精度测距值 |
| `resolution_width_px / height_px` | 960 / 1280 | 已按传感器方向处理的尺寸 |

## 11. 下游解析与使用注意事项

1. 先按 section 分流，再读取该 section 的 key。`OIS_SAMPLE` 和 `LENS_INTRINSICS_SAMPLE` 用 `parent_camera_frame_number` 关联父帧，`sample_index` 仅在父帧内编号。
2. `time_ns` 与 `time_us` 换算后才能比较；Unix 起始时间不能直接与它们相减。估算输出 IMU 平均频率可用 `(N-1)*1e9/(t_last-t_first)`，同时检查相邻间隔、重复时间和长间隙。
3. `gyroscope_drift_rad_s` 是驱动的偏置估计，不是项目自行完成的 IMU 标定。该文件不包含噪声密度、随机游走或协方差矩阵。
4. 相机静态内参、设备位姿参考和逐帧像素焦距估计不能自动替代相机—IMU 联合标定。
5. 当前没有导出独立的原始加速度/磁力计事件时间戳、欧拉角、融合姿态、轨迹、GPS、温度或图像像素；图像在独立 MP4 文件中。
6. TXT 测量字段使用稳定十进制格式，但 `*_info` 是 Android 的原始描述字符串，其内部格式由系统决定。
7. 当前文件头为 v2；解析器应容忍未知字段，对旧文件中缺失的新字段标记为未知，而不是默认解释为关闭或 0。
8. 后台录制使用相机直接连接编码器，MP4 通过旋转元数据指定显示方向；提取图像时应应用该旋转，才能与 TXT 中按方向处理的尺寸和内参对应。后台模式仍输出相同字段，目录后缀为 `_background`。

## 12. 源码定位

以下链接相对于本文位置，可在仓库中直接查看。

- [recording.proto](../protobuf/recording.proto)：内部消息结构与枚举。
- [RecordingTextFormatter.java](../android_app/app/src/main/java/se/lth/math/videoimucapture/RecordingTextFormatter.java)：实际 TXT 字段名、类型转换和区块布局。
- [IMUManager.java](../android_app/app/src/main/java/se/lth/math/videoimucapture/IMUManager.java)：传感器类型、请求周期、IMU 字段填充、频率估计和 placement 提取。
- [ImuSynchronizer.java](../android_app/app/src/main/java/se/lth/math/videoimucapture/ImuSynchronizer.java)：时间对齐、插值和磁力计缺失策略。
- [Camera2Proxy.java](../android_app/app/src/main/java/se/lth/math/videoimucapture/Camera2Proxy.java)：相机元数据、实际 CaptureResult 和 OIS 样本采集。
- [FocalLengthHelper.java](../android_app/app/src/main/java/se/lth/math/videoimucapture/FocalLengthHelper.java)：像素焦距估计、内参与 OIS 位移变换。
- [RecordingWriter.java](../android_app/app/src/main/java/se/lth/math/videoimucapture/RecordingWriter.java)：帧匹配、未匹配数据和错误区块。
- [VideoEncoderCore.java](../android_app/app/src/main/java/se/lth/math/videoimucapture/VideoEncoderCore.java)：编码帧编号与时间戳来源。
