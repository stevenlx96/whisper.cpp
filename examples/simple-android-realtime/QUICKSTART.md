# 快速开始指南

## 1. 下载模型（必须！）

```bash
# 在whisper.cpp根目录执行
cd /path/to/whisper.cpp

# 下载tiny模型（最快，推荐移动设备使用）
sh ./models/download-ggml-model.sh tiny

# 复制模型到app的assets目录
mkdir -p examples/simple-android-realtime/app/src/main/assets/models
cp models/ggml-tiny.bin examples/simple-android-realtime/app/src/main/assets/models/
```

## 2. 在Android Studio中打开项目

1. 打开Android Studio
2. 选择 File -> Open
3. 选择 `whisper.cpp/examples/simple-android-realtime` 目录
4. 等待Gradle同步完成（首次可能需要下载依赖）

## 3. 运行

1. 连接Android设备（推荐）或启动模拟器
2. 点击运行按钮（绿色三角形）
3. 首次运行会请求录音权限，请允许
4. 等待模型加载完成（状态显示"准备就绪"）
5. 点击"开始录音"按钮，说话，然后点击"停止录音"
6. 等待几秒，识别结果会显示在屏幕上

## 常见问题

**Q: 提示"模型加载失败"？**
A: 确保你已经下载了模型文件并放在 `app/src/main/assets/models/ggml-tiny.bin`

**Q: 编译失败？**
A: 确保安装了NDK和CMake。在Android Studio中：Tools -> SDK Manager -> SDK Tools -> 勾选NDK和CMake

**Q: 运行很慢？**
A: tiny模型已经是最快的了。如果还是慢，可能是设备性能限制。

**Q: 识别不准确？**
A: 可以尝试使用base或small模型，但会更慢。
