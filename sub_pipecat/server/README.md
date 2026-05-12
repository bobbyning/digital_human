# Pipecat Digital Human Server (Open Source Edition)

基于 [pipecat](https://github.com/pipecat-ai/pipecat) 的数字人服务端。使用完全开源的服务，无需任何 API Key。

## 技术栈

- **STT**: Whisper (本地语音识别)
- **LLM**: Ollama (本地大语言模型)
- **TTS**: Kokoro (开源语音合成)
- **Avatar**: MuseTalk (可选，真人头像唇形同步)
- **传输**: WebRTC (音视频通话)

## 前置条件

- Python 3.11+
- GPU (8GB+ VRAM 推荐，支持 CUDA 加速)
- [Ollama](https://ollama.ai/) 已安装并运行

## 快速开始

### 1. 安装 Ollama 并下载模型

```bash
# 安装 Ollama (macOS/Linux)
curl -fsSL https://ollama.ai/install.sh | sh

# Windows: 从 https://ollama.ai/download 下载

# 启动 Ollama 服务
ollama serve

# 在另一个终端下载 LLM 模型 (qwen2.5:7b 需要约 4.5GB)
ollama pull qwen2.5:7b
```

### 2. 创建虚拟环境并安装依赖

```bash
cd server
python -m venv venv
source venv/bin/activate   # Windows: venv\Scripts\activate
pip install -r requirements.txt
```

### 3. (可选) 配置环境变量

```bash
cp .env.example .env
```

如需自定义，可编辑 `.env` 文件：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| WHISPER_MODEL | large-v3 | Whisper 模型大小 |
| OLLAMA_BASE_URL | http://localhost:11434/v1 | Ollama API 地址 |
| OLLAMA_MODEL | qwen2.5:7b | LLM 模型名称 |
| KOKORO_VOICE | af_heart | Kokoro 语音音色 |

### 4. 启动服务

```bash
python bot.py -t webrtc
```

启动后，终端会输出一个 WebRTC 连接 URL，可在浏览器中打开进行测试。

## 测试

在另一个终端使用 [WebRTC Test Page](https://webrtc.github.io/samples/src/content/getusermedia/gum/) 或直接访问服务输出的 URL 进行测试。

## 性能优化

- 使用 GPU 加速 Whisper 和 Ollama
- 确保有足够的系统内存和 GPU VRAM
- Kokoro TTS 可以在 CPU 上运行

## MuseTalk 真人头像 (可选)

MuseTalk 可以生成真实感更强的人脸视频，带唇形同步。

### 安装步骤

```bash
# 1. 克隆 MuseTalk 仓库
git clone https://github.com/TMElyralab/MuseTalk.git
cd MuseTalk

# 2. 安装依赖 (可能需要额外的 CUDA 环境)
pip install -e .

# 3. 下载模型权重
# 参考 MuseTalk 仓库的说明下载必要的模型文件

# 4. 准备一张参考人脸图片
# 最好是正面照，脸部清晰，光照均匀

# 5. 配置环境变量
# 在 .env 文件中设置:
# MUSE_TALK_FACE_IMAGE=/path/to/your/face.png
# MUSE_TALK_DEVICE=cuda  # 或 cpu (较慢)
```

### 已知问题

- MuseTalk 需要较新的 GPU (V100/A100 或更高)
- 模型权重需要单独下载
- 如果未安装 MuseTalk，服务会回退到纯音频模式

## 故障排除

- 确保 Ollama 服务正在运行 (`ollama serve`)
- 检查 GPU 驱动和 CUDA 是否正确安装
- 首次运行会下载 Whisper 模型，请耐心等待
- 如果 MuseTalk 报错，检查模型权重是否正确安装
