# 快速开始指南 ⚡

这是一个**完全独立**的Android项目，可以直接复制出去使用！

## 🎯 三步上手

### 第1步：下载模型（必须！）

```bash
# 方法A：直接下载（推荐）
wget https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin

# 方法B：使用curl
curl -L -o ggml-tiny.bin https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin

# 将模型文件放入项目
mkdir -p simple-android-realtime/app/src/main/assets/models
mv ggml-tiny.bin simple-android-realtime/app/src/main/assets/models/
```

### 第2步：打开项目

用Android Studio打开 `simple-android-realtime` 文件夹，等待同步完成。

### 第3步：运行

点击运行按钮，允许录音权限，开始使用！

---

## 📦 项目特点

✅ **完全独立** - 所有whisper.cpp源代码已内置
✅ **开箱即用** - 可复制到任意位置使用
✅ **无需子模块** - 不依赖外部仓库

## 📁 项目已包含

```
simple-android-realtime/          ← 整个文件夹可独立使用！
├── app/src/main/
│   ├── cpp/                     ← 所有C++源代码已内置
│   │   ├── whisper/             ← Whisper核心
│   │   ├── ggml/                ← GGML库
│   │   ├── whisper_jni.c        ← JNI绑定
│   │   └── CMakeLists.txt       ← 独立构建配置
│   ├── java/...                 ← Kotlin代码
│   └── assets/models/           ← 需要放模型文件
└── ...
```

## 🚀 开始使用

1. **允许录音权限**（首次启动）
2. **等待加载**（状态显示"准备就绪"）
3. **点击"开始录音"**
4. **说话** 3-10秒
5. **点击"停止录音"**
6. **查看结果**

## ⚠️ 常见问题

**Q: 提示"模型加载失败"？**
A: 检查 `app/src/main/assets/models/ggml-tiny.bin` 文件是否存在

**Q: 编译失败？**
A: Android Studio → Tools → SDK Manager → SDK Tools → 安装NDK和CMake

**Q: 识别很慢？**
A: 在真机上测试（模拟器会很慢），使用tiny模型

**Q: 可以独立使用吗？**
A: 是的！整个 `simple-android-realtime` 文件夹可以复制到任何地方

## 📖 详细文档

查看 [README.md](README.md) 了解更多配置和故障排除信息。

---

**提示**: 模型文件约75MB，首次下载可能需要几分钟。
