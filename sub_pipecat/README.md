# sub_pipecat — 基于 Pipecat 的全开源数字人方案

基于 [pipecat-ai/pipecat](https://github.com/pipecat-ai/pipecat) 构建的实时数字人系统。**全部使用开源服务，零 API Key，完全本地运行。**

## 架构

```
Android App (WebRTC)
     |  <-- WebRTC 音频 -->
     v
Pipecat Bot Server (FastAPI)
     |  Pipeline: Whisper STT → Ollama LLM → Kokoro TTS
     v
本地模型（首次运行自动下载）
```

**数据流**: 用户语音 → Whisper STT → Ollama Qwen2.5 LLM → Kokoro TTS → 音频回传 Android → 本地 Live2D 风格嘴型同步

## 技术栈（全部开源）

| 组件 | 服务 | 说明 |
|------|------|------|
| 通信 | SmallWebRTCTransport | 点对点 WebRTC，无需注册 |
| STT | Whisper (large-v3) | OpenAI 开源语音识别，GPU 加速 |
| LLM | Ollama + Qwen2.5 | 阿里巴巴开源中文大模型 |
| TTS | Kokoro (ONNX) | 本地语音合成，支持中文 |
| 头像 | Canvas Live2D 风格 | Android 端本地绘制，音频驱动嘴型 |

## 前置条件

- Python 3.11+
- NVIDIA GPU（推荐 8GB+ VRAM，运行 Whisper large-v3 + Qwen2.5 7B）
- [Ollama](https://ollama.com) 已安装

## 快速开始

### 1. 安装 Ollama 并下载模型

```bash
# 安装 Ollama: https://ollama.com 下载安装
# 下载中文 LLM 模型（约 4.7GB）
ollama pull qwen2.5:7b

# 如果 GPU 显存不足，可用小模型：
ollama pull qwen2.5:3b
```

### 2. 启动服务器

```bash
cd server/

python -m venv venv
# Windows:
venv\Scripts\activate
# macOS/Linux:
source venv/bin/activate

pip install -r requirements.txt

# 可选：创建 .env 自定义配置（不创建也能用默认值）
# cp .env.example .env

# 启动
python bot.py -t webrtc
```

首次启动时，Whisper 和 Kokoro 模型会自动下载（Whisper large-v3 约 3GB）。

### 3. Android 客户端

1. 用 Android Studio 打开 `android/` 目录
2. 等待 Gradle Sync 完成
3. 输入服务器地址（如 `http://192.168.1.100:7860`）
4. 连接 Android 设备，运行应用

### 网络配置

- **同局域网**: 使用电脑 LAN IP（如 `http://192.168.1.100:7860`）
- **USB 调试**: `adb reverse tcp:7860 tcp:7860`，然后使用 `http://localhost:7860`

## 可选配置

创建 `server/.env` 文件可自定义以下参数（均有默认值）：

```
# Whisper STT 模型（默认 large-v3，可选 tiny/base/small/medium）
WHISPER_MODEL=large-v3

# Ollama 服务地址（默认本地）
OLLAMA_BASE_URL=http://localhost:11434/v1

# LLM 模型（默认 qwen2.5:7b）
OLLAMA_MODEL=qwen2.5:7b

# TTS 声音（默认 af_heart）
KOKORO_VOICE=af_heart
```

## 项目结构

```
sub_pipecat/
├── server/               # Python 服务器
│   ├── bot.py            # Pipecat Pipeline（Whisper + Ollama + Kokoro）
│   ├── requirements.txt  # Python 依赖
│   └── .env.example      # 可选配置模板
├── android/              # Android 客户端
│   └── app/
│       └── src/main/java/com/digitalhuman/pipecat/
│           ├── MainActivity.kt           # 主界面
│           ├── VoiceClientManager.kt     # WebRTC 客户端管理
│           └── ui/
│               ├── Live2DAvatarView.kt   # Canvas 2D 动漫头像
│               ├── InCallLayout.kt       # 通话界面
│               └── ConnectScreen.kt      # 连接配置
└── README.md
```
