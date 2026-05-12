# Pipecat Digital Human Server

基于 [pipecat](https://github.com/pipecat-ai/pipecat) 的数字人服务端。集成了 Daily WebRTC 传输、DeepSeek 大语言模型、Fish Audio 语音合成、Deepgram 语音识别以及 Simli 数字人形象。

## 前置条件

- Python 3.11+
- 以下服务的 API Key：
  - Daily.co
  - Deepgram
  - DeepSeek
  - Fish Audio
  - Simli

## 快速开始

### 1. 创建虚拟环境并安装依赖

```bash
cd server
python -m venv venv
source venv/bin/activate   # Windows: venv\Scripts\activate
pip install -r requirements.txt
```

### 2. 配置环境变量

```bash
cp .env.example .env
```

编辑 `.env` 文件，填入你的 API Key。

### 3. 启动服务

```bash
python bot.py -t daily
```

启动后，终端会输出一个 Daily.co 房间 URL，可在浏览器中打开进行测试。

## API Key 获取链接

| 服务 | 用途 | 链接 |
|------|------|------|
| Daily.co | WebRTC 传输 | https://www.daily.co/ |
| Deepgram | 语音识别 (STT) | https://console.deepgram.com/ |
| DeepSeek | 大语言模型 (LLM) | https://platform.deepseek.com/ |
| Fish Audio | 语音合成 (TTS) | https://fish.audio/ |
| Simli | 数字人形象 | https://www.simli.com/ |

## 测试

启动服务后，使用 [Daily Web Client](https://web.daily.co/) 输入终端输出的房间 URL 即可与数字人对话。
