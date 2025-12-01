# Simple Android Realtime Whisper Demo

这是一个简单的Android应用，使用whisper.cpp实现实时语音识别功能。

## 功能特点

- 实时录音
- 语音转文字
- 简洁的UI界面
- 支持多种语言（自动检测）

## 使用方法

### 1. 准备模型文件

首先需要下载Whisper模型文件并放入assets目录：

```bash
# 下载tiny模型（推荐在手机上使用）
cd /path/to/whisper.cpp
sh ./models/download-ggml-model.sh tiny

# 将模型文件复制到assets目录
mkdir -p examples/simple-android-realtime/app/src/main/assets/models
cp models/ggml-tiny.bin examples/simple-android-realtime/app/src/main/assets/models/
```

你也可以使用其他模型，如：
- `ggml-base.bin` - 更好的准确性，但速度较慢
- `ggml-small.bin` - 更高的准确性，需要更多内存

### 2. 编译运行

使用Android Studio打开项目：

1. 打开Android Studio
2. 选择 "Open an Existing Project"
3. 导航到 `whisper.cpp/examples/simple-android-realtime`
4. 等待Gradle同步完成
5. 连接Android设备或启动模拟器
6. 点击运行按钮

或者使用命令行：

```bash
cd examples/simple-android-realtime
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 3. 使用应用

1. 启动应用后，首次运行会请求录音权限，请允许
2. 应用会自动加载Whisper模型（可能需要几秒钟）
3. 点击"开始录音"按钮开始录音
4. 说话
5. 点击"停止录音"按钮停止录音
6. 应用会自动进行语音识别，并在屏幕上显示识别结果

## 系统要求

- Android 8.0 (API 26) 或更高版本
- 录音权限
- 至少500MB可用内存（用于加载模型）

## 支持的架构

- ARM64 (arm64-v8a)
- ARMv7 (armeabi-v7a)
- x86
- x86_64

## 技术栈

- Kotlin
- Android SDK
- whisper.cpp (JNI)
- Coroutines（异步处理）

## 项目结构

```
simple-android-realtime/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/whisper/realtime/
│   │       │   ├── MainActivity.kt          # 主Activity
│   │       │   ├── WhisperLib.kt           # Whisper JNI包装类
│   │       │   └── AudioRecorder.kt        # 音频录制工具
│   │       ├── jni/
│   │       │   ├── CMakeLists.txt          # CMake构建配置
│   │       │   └── whisper_jni.c           # JNI实现
│   │       ├── res/                        # 资源文件
│   │       └── assets/models/              # 模型文件目录
│   └── build.gradle
├── build.gradle
└── settings.gradle
```

## 注意事项

1. **模型文件必须放在正确位置**：确保将`ggml-tiny.bin`放入`app/src/main/assets/models/`目录
2. **首次加载较慢**：模型加载需要几秒钟时间，请耐心等待
3. **录音时长**：建议每次录音5-30秒，太短可能识别不准，太长会占用过多内存
4. **性能**：在低端设备上，识别速度可能较慢，建议使用tiny或base模型

## 故障排除

### 模型加载失败
- 检查模型文件是否在正确位置：`app/src/main/assets/models/ggml-tiny.bin`
- 检查模型文件是否完整（应该约75MB）

### 编译错误
- 确保使用CMake 3.22.1或更高版本
- 检查NDK是否正确安装

### 运行时崩溃
- 检查logcat日志中的错误信息
- 确保设备有足够的内存

## 许可证

本项目遵循whisper.cpp的MIT许可证。
