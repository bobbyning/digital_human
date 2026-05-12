# sub_pipecat — 基于 Pipecat 的全开源数字人方案

基于 [pipecat-ai/pipecat](https://github.com/pipecat-ai/pipecat) 构建的实时数字人系统。**全部使用开源服务，零 API Key，完全本地运行。**

## 架构

```
Android App (WebRTC)
     |  <-- WebRTC 音频/视频 -->
     v
Pipecat Bot Server (FastAPI)
     |  Pipeline: Whisper STT → Ollama LLM → Kokoro TTS → MuseTalk Avatar
     v
本地模型（首次运行自动下载）
```

**数据流**: 用户语音 → Whisper STT → Ollama Qwen2.5 LLM → Kokoro TTS → MuseTalk 真人嘴型视频 → WebRTC 回传 Android

## 技术栈（全部开源）

| 组件 | 服务 | 说明 |
|------|------|------|
| 通信 | SmallWebRTCTransport | 点对点 WebRTC，无需注册 |
| STT | Whisper (large-v3) | OpenAI 开源语音识别，GPU 加速 |
| LLM | Ollama + Qwen2.5 | 阿里巴巴开源中文大模型 |
| TTS | Kokoro (ONNX) | 本地语音合成，支持中文 |
| 真人头像 | MuseTalk 1.5 (MIT) | 腾讯开源实时嘴型同步，30fps+ |

## 前置条件

- Python 3.11+
- NVIDIA GPU（推荐 8GB+ VRAM，运行 Whisper + Qwen2.5 + MuseTalk）
- [Ollama](https://ollama.com) 已安装

## 快速开始

### 1. 安装 Ollama 并下载模型

```bash
ollama pull qwen2.5:7b
```

### 2. 安装 MuseTalk（真人头像）

```bash
git clone https://github.com/TMElyralab/MuseTalk.git
cd MuseTalk
pip install -e .

# 下载模型权重（按 MuseTalk README 说明操作）
# 准备一张参考人脸照片，如 face.png
```

> MuseTalk 是可选的。未安装时自动降级为纯音频模式，不影响其他功能。

### 3. 启动服务器

```bash
cd server/

python -m venv venv
# Windows: venv\Scripts\activate
# macOS/Linux: source venv/bin/activate

pip install -r requirements.txt

# 可选配置
# cp .env.example .env
# 编辑 .env 设置 MUSE_TALK_FACE_IMAGE=../face.png

python bot.py -t webrtc
```

首次启动时，Whisper 和 Kokoro 模型会自动下载。

### 4. Android 客户端

1. 用 Android Studio 打开 `android/` 目录
2. 等待 Gradle Sync 完成
3. 输入服务器地址（如 `http://192.168.1.100:7860`）
4. 连接 Android 设备，运行应用

### 网络配置

- **同局域网**: 使用电脑 LAN IP（如 `http://192.168.1.100:7860`）
- **USB 调试**: `adb reverse tcp:7860 tcp:7860`，然后使用 `http://localhost:7860`

## 可选配置

创建 `server/.env` 文件可自定义参数（均有默认值）：

```
WHISPER_MODEL=large-v3
OLLAMA_BASE_URL=http://localhost:11434/v1
OLLAMA_MODEL=qwen2.5:7b
KOKORO_VOICE=af_heart

# MuseTalk 真人头像
MUSE_TALK_FACE_IMAGE=face.png
MUSE_TALK_DEVICE=cuda
```

## 项目结构

```
sub_pipecat/
├── server/               # Python 服务器
│   ├── bot.py            # Pipecat Pipeline 定义
│   ├── musetalk_service.py # MuseTalk 真人头像服务
│   ├── requirements.txt  # Python 依赖
│   └── .env.example      # 可选配置模板
├── android/              # Android 客户端
│   └── app/
│       └── src/main/java/com/digitalhuman/pipecat/
│           ├── MainActivity.kt           # 主界面
│           ├── VoiceClientManager.kt     # WebRTC 客户端管理
│           └── ui/
│               ├── InCallLayout.kt       # 通话界面（视频渲染）
│               └── ConnectScreen.kt      # 连接配置
└── README.md
```
