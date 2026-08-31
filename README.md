# HamAudioLog 🎙️ — 业余无线电通联录音与盲操打点助手 (Android App)

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%2016%20(API%2036)-3DDC84.svg?style=flat-square&logo=android" alt="Android 16">
  <img src="https://img.shields.io/badge/JDK-Java%2025-ED8B00.svg?style=flat-square&logo=openjdk" alt="Java 25">
  <img src="https://img.shields.io/badge/Gradle-9.7.0-02303A.svg?style=flat-square&logo=gradle" alt="Gradle 9.7.0">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20AppWidget-4285F4.svg?style=flat-square&logo=jetpackcompose" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/AI%20Driven-Google%20Antigravity%20%7C%20Gemini-8E75B2.svg?style=flat-square&logo=google" alt="AI Driven">
</p>

一款专为业余无线电（HAM）通联环境打造的**超轻量、低干扰、免看屏盲操录音与时间锚点（Marker）助手**。在快速通联、电台竞赛或野外通联（BOTA / POTA / SOTA）嘈杂环境中，无需低头操作手机屏幕，即可精准记录关键 QSO 时间节点并随时智能回听。

> [!IMPORTANT]
> **系统要求**：本项目专为 **Android 16 (API 36)** 平台打造（`minSdk = 36`）。在低于 Android 16 的系统上安装时，系统包管理器会提示“解析错误”或拒绝安装（`INSTALL_FAILED_OLDER_SDK`）。

> 💡 **AI 代码驱动开发 (AI-Driven Development)**：本项目从零架构设计、前台多线程录音服务、动态波形渲染器到桌面小组件，全部基于 **Google Antigravity & Gemini** 进行 AI 辅助全流程驱动开发（Vibe Coding），实现无冗余、纯粹现代的 Android 原生工程。

---

## ✨ 核心特性与设计亮点

### 1. 📴 全维零视觉盲操打点 (Zero-Visual Blind Marking)
- **多途径快捷打点**：支持**物理音量按键 (Volume Down)**、**常驻前台录音通知栏快捷键**、**4x2 原生桌面小组件** 以及应用内 **下半屏超大高对比度盲触板**。
- **系统级机械感触感反馈**：调用 Android `VibratorManager` 提供短促有力的物理敲击震动反馈，视线完全无需离开电台即可确认打点成功。

### 2. 🎧 系统通知栏媒体播放控制器 (Media Playback Notification)
- **常驻通知栏控制卡片**：播放录音时自动弹出媒体控制器，实时显示录音文件名、播放状态及 `mm:ss` 进度。
- **快捷控制按键**：支持通知栏一键 **⏪ 快退 5 秒**、**▶/⏸ 播放与暂停切换**、**⏹ 停止播放**。
- **锁屏与后台无缝控制**：锁屏状态下无需解锁手机即可控制录音回放与倒带。
- **页面生命周期自适应**：离开会话详情页时自动暂停播放并清除通知栏控制器，杜绝后台声音游离或电量消耗。

### 3. ⏪ 智能前置回放引擎与全维操控 (Smart Rewind Playback)
- **智能提前 3 秒起播 (-3s Rewind)**：在嘈杂或瞬时通联中，打点操作通常发生在对方报完呼号或信号报告的瞬间。点击任意打点锚点（Marker），播放器**自动提前 3 秒（-3000ms）倒带起播**，完美还原稍纵即逝的呼号与 RST 交换细节。
- **无锚点录音完整播放**：支持即使未打点的通联录音完整播放，提供快退 5s、快进 5s、时间轴平滑拖拽（Slider Seek）与从头播放。
- **录音与播放互斥保护**：开启新录音时自动终止任何正在进行的回放，避免音频冲突。

### 4. 🛡️ 物理录音自动扫描与自愈同步机制 (Self-Healing Audio Sync)
- 启动时自动深度扫描物理存储目录（包含外部与内部存储），若发现未入库的录音文件，自动调用 `MediaMetadataRetriever` 提取时长与时间戳并补录 Room 数据库，确保录音文件 100% 不丢失。
- 详情页自动根据实际物理文件校验与动态补正有效时长。

### 5. 📈 实时动态音频波形可视化 (Live Waveform Visualizer)
- 录音过程中实时采集音频振幅分贝（dB），采用现代 Compose Canvas 平滑流动渲染波形，录音状态与现场音量一目了然。

### 6. 🧩 原生桌面快捷小组件 (Android 4x2 AppWidget)
- 支持将控制面板常驻手机桌面，无需打开 App 即可直接在主屏幕完成：
  - 🟢 一键开始录音
  - 📌 即时记录通联打点 (Mark)
  - ⏹️ 停止并保存会话
  - 📻 快速启动进入应用
  - 实时显示录音时长与打点计数
- 矢量图标动态渲染为高清 Bitmap，完美兼容各类第三方 Android Launcher。

### 7. 🗜️ 超低空间占用与高保真录音
- 采用优化编码的 AAC 单声道录音（约 14MB / 小时），支持户外数小时连续录制，超低功耗与电量消耗。

### 8. 🕒 UTC 实时时钟与会话历史管理
- 内置实时 UTC 航海天文时钟与本地时间对照显示。
- 完整的录音会话管理列表与时间轴标记详情，支持快速重命名、补录呼号/信号报告/频段/模式/备注、单点回放与音频文件分享。
- 支持将标记锚点一键导出为标准 **ADIF 3.1.7** (`.adi`) 日志文件，快速同步至第三方通联日志软件（N1MM, Log4OM, Cloudlog 等）。

### 9. 📦 自动化构建归档机制 (APK Auto-Archiving)
- 编译脚本自动在根目录 `apk_history/` 下归档每次构建的独立 APK 安装包（包含版本、类型及精确时间戳），追溯历史版本更轻松。

---

## 🛠️ 技术栈与环境规范

| 维度 | 规格 / 技术选型 |
| :--- | :--- |
| **Target OS / SDK** | **Android 16** (`compileSdk = 36`, `targetSdk = 36`, `minSdk = 36`) |
| **JDK 环境** | **Java 25** (`Java 25.0.3`) |
| **构建系统** | **Gradle 9.7.0** + Android Gradle Plugin 8.9+ |
| **编程语言** | Kotlin 2.0+ (Coroutines, Flow) |
| **UI 架构** | Jetpack Compose + Material 3 + AppWidget (RemoteViews) |
| **音频引擎** | Android AudioRecord / MediaRecorder + Jetpack Media3 (ExoPlayer) |
| **前台服务** | Foreground Service (`RECORDING` 类型) + 唤醒锁电源管理 |
| **本地持久化** | Jetpack Room 2.7+ with KSP |
| **代码生成与开发** | **AI-Driven (Google Antigravity & Gemini)** |

---

## 🚀 快速构建与安装

项目根目录下已配置好 Gradle Wrapper (Gradle 9.7.0)：

```powershell
# 编译 Debug 版本并自动归档 APK
.\gradlew.bat assembleDebug

# 编译 Release 版本并自动归档 APK
.\gradlew.bat assembleRelease

# 安装最新生成的 APK 到连接的 Android 16+ 设备
adb install -r "apk_history/<latest_apk>.apk"
```

---

## 👨‍💻 作者与技术支持

- **开发者 / 呼号**：`BG5GVV`
- **联系方式**：`BG5GVV@outlook.com`
- **代码构建方式**：`AI-Driven with Google Antigravity & Gemini ✨`

---

<p align="center">
  <b>73 & Good DX! Clean Audio, Clear Logs. 🎙️📻</b>
</p>
