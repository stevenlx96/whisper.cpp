# Simple Android Realtime Whisper Demo

这是一个**独立的**Android应用，使用whisper.cpp实现实时语音识别功能。

## ✨ 特点

- ✅ **完全独立** - 所有whisper.cpp源代码已内置，无需外部依赖
- ✅ **开箱即用** - 可直接复制到任何位置独立使用
- ✅ **实时录音** - 使用AudioRecord实现实时音频捕获
- ✅ **语音转文字** - 基于OpenAI Whisper模型
- ✅ **简洁UI** - 最小化设计，易于理解和扩展
- ✅ **多语言支持** - 自动检测语言
- ✅ **多架构支持** - arm64-v8a, armeabi-v7a, x86, x86_64

## 📦 项目特性

**这是一个独立项目**，可以单独复制出去使用，不依赖whisper.cpp仓库。所有必需的源代码都已包含在项目中：

```
simple-android-realtime/
├── app/
│   └── src/main/
│       ├── cpp/                    # 包含所有C/C++源代码
│       │   ├── whisper/           # Whisper核心代码
│       │   │   ├── include/       # whisper.h
│       │   │   └── src/           # whisper.cpp
│       │   ├── ggml/              # GGML机器学习库
│       │   │   ├── include/       # GGML头文件
│       │   │   └── src/           # GGML源文件
│       │   ├── whisper_jni.c      # JNI绑定
│       │   └── CMakeLists.txt     # 独立CMake配置
│       └── java/com/whisper/realtime/
│           ├── MainActivity.kt    # 主界面
│           ├── WhisperLib.kt     # JNI包装
│           └── AudioRecorder.kt  # 录音工具
└── ...
```

## 🚀 快速开始

### 1. 下载模型文件（必须！）

首次使用需要下载Whisper模型：

```bash
# 方法1: 从whisper.cpp仓库下载
git clone https://github.com/ggml-org/whisper.cpp.git
cd whisper.cpp
sh ./models/download-ggml-model.sh tiny

# 方法2: 直接下载（推荐）
wget https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin

# 将模型文件放入项目的assets目录
mkdir -p simple-android-realtime/app/src/main/assets/models
cp ggml-tiny.bin simple-android-realtime/app/src/main/assets/models/
```

**推荐模型**：
- `ggml-tiny.bin` (75MB) - 最快，适合移动设备 ⭐
- `ggml-base.bin` (142MB) - 更准确
- `ggml-small.bin` (466MB) - 高准确度，需要更多内存

### 2. 在Android Studio中打开

1. 打开Android Studio
2. File → Open
3. 选择 `simple-android-realtime` 目录
4. 等待Gradle同步完成

### 3. 编译运行

**使用Android Studio**：
1. 连接Android设备或启动模拟器
2. 点击运行按钮（绿色三角形）

**使用命令行**：
```bash
cd simple-android-realtime
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 4. 使用应用

1. 首次启动会请求录音权限，请允许
2. 等待模型加载（状态显示"准备就绪"）
3. 点击"开始录音"按钮
4. 对着麦克风说话（建议5-30秒）
5. 点击"停止录音"
6. 等待识别完成，结果会显示在屏幕上

## 📋 系统要求

- Android 8.0 (API 26) 或更高
- 至少 500MB 可用内存
- 麦克风权限
- 推荐真机测试（模拟器可能较慢）

## 🛠 技术栈

- **语言**: Kotlin + C/C++
- **UI**: Android Views (传统布局)
- **异步**: Kotlin Coroutines
- **音频**: AudioRecord API
- **ML引擎**: whisper.cpp + GGML
- **构建**: Gradle + CMake

## 📂 项目结构详解

```
simple-android-realtime/
├── app/
│   ├── build.gradle                    # 应用级Gradle配置
│   ├── proguard-rules.pro             # ProGuard规则
│   └── src/main/
│       ├── AndroidManifest.xml         # Android清单
│       ├── cpp/                        # C/C++源代码（完整独立）
│       │   ├── CMakeLists.txt         # CMake构建脚本
│       │   ├── whisper_jni.c          # JNI接口实现
│       │   ├── whisper/               # Whisper源码
│       │   │   ├── include/whisper.h  # Whisper头文件
│       │   │   └── src/               # Whisper实现
│       │   └── ggml/                  # GGML源码
│       │       ├── include/           # GGML头文件
│       │       └── src/               # GGML实现
│       ├── java/com/whisper/realtime/
│       │   ├── MainActivity.kt        # 主Activity
│       │   ├── WhisperLib.kt         # Whisper JNI封装
│       │   └── AudioRecorder.kt      # 音频录制工具
│       ├── res/                       # 资源文件
│       │   ├── layout/               # 布局文件
│       │   └── values/               # 字符串、颜色等
│       └── assets/models/            # 模型文件目录（需手动添加）
├── build.gradle                       # 项目级Gradle配置
├── settings.gradle                    # Gradle设置
├── gradle.properties                  # Gradle属性
├── .gitignore                        # Git忽略规则
├── README.md                         # 本文件
└── QUICKSTART.md                     # 快速入门指南
```

## ⚙️ 自定义配置

### 修改采样率
编辑 `AudioRecorder.kt`:
```kotlin
private const val SAMPLE_RATE = 16000  // Whisper标准
```

### 修改线程数
编辑 `MainActivity.kt`:
```kotlin
val result = whisperContext?.transcribe(audioData, numThreads = 4)
```

### 修改语言
编辑 `whisper_jni.c`:
```c
params.language = "zh";  // 指定中文，或用"auto"自动检测
```

### 更换模型
将不同的模型文件放入 `assets/models/` 并修改 `MainActivity.kt`:
```kotlin
private const val MODEL_PATH = "models/ggml-base.bin"
```

## 🐛 故障排除

### 问题1: 模型加载失败
```
错误: 模型加载失败: Failed to initialize Whisper context
```
**解决方法**:
- 确认模型文件在 `app/src/main/assets/models/ggml-tiny.bin`
- 检查文件大小是否正确（tiny模型约75MB）
- 确保模型文件完整下载，未损坏

### 问题2: 编译错误
```
错误: CMake错误或NDK未找到
```
**解决方法**:
- 在Android Studio中: Tools → SDK Manager → SDK Tools
- 勾选并安装: NDK, CMake
- Sync Project with Gradle Files

### 问题3: 运行时崩溃
```
错误: java.lang.UnsatisfiedLinkError
```
**解决方法**:
- Clean Project (Build → Clean Project)
- Rebuild Project (Build → Rebuild Project)
- 检查logcat查看详细错误信息

### 问题4: 识别结果为空
**可能原因**:
- 录音时间太短（少于1秒）
- 环境噪音过大
- 麦克风权限未授予

**解决方法**:
- 录音至少3-5秒
- 在安静环境测试
- 检查应用权限设置

### 问题5: 识别速度慢
**优化建议**:
- 使用tiny模型而非base/small
- 在真机上测试（模拟器性能差）
- 减少录音时长
- 使用release build而非debug

## 📝 常见问题

**Q: 这个项目可以独立使用吗？**
A: 是的！所有whisper.cpp源代码都已包含，可以复制到任何地方独立编译运行。

**Q: 需要联网吗？**
A: 不需要。模型加载后完全离线运行。

**Q: 支持哪些语言？**
A: Whisper支持99种语言，包括中文、英文、日文等。设置为"auto"可自动检测。

**Q: 可以用于生产环境吗？**
A: 可以，但建议：
- 添加错误处理和用户反馈
- 优化UI/UX
- 添加录音时长限制
- 考虑电池和性能优化

**Q: 如何更新whisper.cpp版本？**
A: 需要手动从最新的whisper.cpp仓库复制源文件到 `app/src/main/cpp/` 目录。

**Q: 为什么选择这种方式而不是submodule？**
A: 这种方式让项目完全自包含，便于分发和部署，不需要额外的git操作。

## 🔧 开发建议

1. **性能优化**:
   - 使用native-debug构建类型调试，release构建部署
   - 考虑使用流式识别（需要修改代码）
   - 添加VAD（语音活动检测）减少无效识别

2. **功能扩展**:
   - 添加录音可视化（波形图）
   - 支持从文件导入音频
   - 添加识别历史记录
   - 支持导出识别结果

3. **代码改进**:
   - 添加单元测试
   - 实现更好的错误处理
   - 使用Jetpack Compose重写UI
   - 添加设置页面（选择模型、语言等）

## 📄 许可证

本项目遵循MIT许可证，与whisper.cpp保持一致。

## 🙏 致谢

- [whisper.cpp](https://github.com/ggml-org/whisper.cpp) - 核心引擎
- [OpenAI Whisper](https://github.com/openai/whisper) - 原始模型

## 📧 支持

如有问题或建议，欢迎提issue或PR。

---

**重要提示**: 首次使用前务必下载模型文件到 `app/src/main/assets/models/` 目录！
