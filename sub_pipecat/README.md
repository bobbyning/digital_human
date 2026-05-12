# sub_pipecat — 基于 Pipecat 的数字人方案

基于 [pipecat-ai/pipecat](https://github.com/pipecat-ai/pipecat) 构建的实时数字人系统，支持语音对话和头像视频输出。

## 架构

```
Android App (Daily WebRTC)
     |  <-- WebRTC 音频/视频 -->
     v
Pipecat Bot Server (FastAPI)
     |  Pipeline: STT → LLM → TTS → Simli Avatar
     v
外部 API: DeepSeek, Fish Audio, Deepgram, Simli, Daily.co
```

**数据流**: 用户语音 → Deepgram STT → DeepSeek LLM → Fish Audio TTS → Simli 头像视频 → WebRTC 回传 Android

## 技术栈

| 组件 | 服务 | 说明 |
|------|------|------|
| 通信 | Daily.co WebRTC | 实时音视频传输 |
| STT | Deepgram | 语音转文字（支持中文） |
| LLM | DeepSeek | 中文大语言模型 |
| TTS | Fish Audio | 中文语音合成 |
| 头像 | Simli | 实时数字人视频生成 |
| 客户端 | Kotlin + Compose | Android 原生应用 |

## 快速开始

### 服务器端

```bash
cd server/

# 创建虚拟环境
python -m venv venv
# Windows:
venv\Scripts\activate
# macOS/Linux:
source venv/bin/activate

# 安装依赖
pip install -r requirements.txt

# 配置环境变量
cp .env.example .env
# 编辑 .env 填入 API Key

# 启动（默认端口 7860）
python bot.py -t daily
```

### Android 客户端

1. 用 Android Studio 打开 `android/` 目录
2. 等待 Gradle Sync 完成
3. 修改服务器地址（默认 `http://192.168.1.100:7860`）
4. 连接 Android 设备，运行应用

### 网络配置

- **同局域网**: 使用电脑 LAN IP（如 `http://192.168.1.100:7860`）
- **USB 调试**: `adb reverse tcp:7860 tcp:7860`，然后使用 `http://localhost:7860`
- **云端部署**: 使用服务器公网 IP

## API Key 获取

| 服务 | 注册地址 |
|------|----------|
| Daily.co | https://dashboard.daily.co/ |
| Deepgram | https://console.deepgram.com/ |
| DeepSeek | https://platform.deepseek.com/ |
| Fish Audio | https://fish.audio/ |
| Simli | https://www.simli.com/ |

## 项目结构

```
sub_pipecat/
├── server/               # Python 服务器
│   ├── bot.py            # Pipecat Pipeline 定义
│   ├── requirements.txt  # Python 依赖
│   └── .env.example      # 环境变量模板
├── android/              # Android 客户端
│   └── app/
│       └── src/main/java/com/digitalhuman/pipecat/
│           ├── MainActivity.kt           # 主界面
│           ├── VoiceClientManager.kt     # 客户端连接管理
│           └── ui/                       # Compose UI 组件
└── README.md
```
