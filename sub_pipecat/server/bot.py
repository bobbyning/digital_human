#
# Copyright (c) 2024-2026, Daily
#
# SPDX-License-Identifier: BSD 2-Clause License
#
"""
Pipecat Digital Human Bot (Open Source Edition)

Pipeline:
    Transport Input -> Whisper STT -> Context Aggregator (user) ->
    Ollama LLM -> Kokoro TTS ->
    Transport Output -> Context Aggregator (assistant)

All services are fully open-source. No API keys required.
"""

import os
import sys

# Add MuseTalk to Python path
_musetalk_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "MuseTalk")
if os.path.isdir(_musetalk_dir) and _musetalk_dir not in sys.path:
    sys.path.insert(0, _musetalk_dir)

from dotenv import load_dotenv
from loguru import logger

from pipecat.audio.vad.silero import SileroVADAnalyzer
from pipecat.frames.frames import LLMRunFrame
from pipecat.pipeline.pipeline import Pipeline
from pipecat.pipeline.runner import PipelineRunner
from pipecat.pipeline.task import PipelineParams, PipelineTask
from pipecat.processors.aggregators.llm_context import LLMContext
from pipecat.processors.aggregators.llm_response_universal import (
    LLMContextAggregatorPair,
    LLMUserAggregatorParams,
)
from pipecat.runner.run import main
from pipecat.runner.utils import create_transport
from pipecat.runner.types import RunnerArguments
# NOTE: Import paths below are based on pipecat-ai>=0.0.106 extras naming
# (whisper, ollama, kokoro). If imports fail, verify the exact module paths
# with: pip show pipecat-ai && python -c "from pipecat.services.whisper import ..."
from pipecat.services.whisper.stt import WhisperSTTService
from pipecat.services.ollama.llm import OLLamaLLMService
from pipecat.services.kokoro.tts import KokoroTTSService
# Language enums — these may live under each service or under a shared
# pipecat module. Adjust if your pipecat version places them elsewhere.
from pipecat.services.whisper.stt import Language as WhisperLanguage
from pipecat.services.kokoro.tts import Language as KokoroLanguage
from pipecat.transports.base_transport import BaseTransport, TransportParams

load_dotenv(override=True)

# ── MuseTalk Avatar (optional) ─────────────────────────────────────────
musetalk = None
try:
    from musetalk_service import MuseTalkVideoService

    musetalk = MuseTalkVideoService(
        face_image_path=os.environ.get("MUSE_TALK_FACE_IMAGE", "face.png"),
        device=os.environ.get("MUSE_TALK_DEVICE", "cuda"),
    )
    logger.info("MuseTalk avatar service initialized")
except ImportError:
    logger.warning("MuseTalk not installed. Running audio-only mode.")


# Audio + Video WebRTC transport (for MuseTalk avatar)
transport_params = {
    "webrtc": lambda: TransportParams(
        audio_in_enabled=True,
        audio_out_enabled=True,
        video_out_enabled=True,
        video_out_is_live=True,
        video_out_width=512,
        video_out_height=512,
    ),
}


async def run_bot(transport: BaseTransport, runner_args: RunnerArguments):
    """Run the digital human bot pipeline."""

    logger.info("Starting digital human bot (open-source edition)")

    # ── STT (Speech-to-Text) — Whisper ─────────────────────────────────
    stt = WhisperSTTService(
        model_path=os.environ.get("WHISPER_MODEL", "large-v3"),
        language=WhisperLanguage.ZH,
        device="cuda",
    )

    # ── LLM — Ollama ───────────────────────────────────────────────────
    llm = OLLamaLLMService(
        base_url=os.environ.get("OLLAMA_BASE_URL", "http://localhost:11434/v1"),
        model=os.environ.get("OLLAMA_MODEL", "qwen2.5:7b"),
        system_instruction="""\
你是一个友好的数字人助手。你用自然、口语化的中文与用户交流。
请遵循以下规则：
1. 用简洁自然的口语回答问题，不要使用书面语或过于正式的表达。
2. 不要使用表情符号。
3. 不要使用项目符号或编号列表，因为你的回答会被语音播报。
4. 保持回答简短，每次回答控制在两三句话以内。
5. 如果用户问候你，友好地回应并询问有什么可以帮忙的。""",
    )

    # ── TTS (Text-to-Speech) — Kokoro ──────────────────────────────────
    tts = KokoroTTSService(
        voice_id=os.environ.get("KOKORO_VOICE", "af_heart"),
        language=KokoroLanguage.ZH,
    )

    # ── Context ────────────────────────────────────────────────────────
    context = LLMContext()

    user_aggregator, assistant_aggregator = LLMContextAggregatorPair(
        context,
        user_params=LLMUserAggregatorParams(
            vad_analyzer=SileroVADAnalyzer(),
        ),
    )

    # ── Pipeline ───────────────────────────────────────────────────────
    pipeline_steps = [
        transport.input(),
        stt,
        user_aggregator,
        llm,
        tts,
    ]

    # Add MuseTalk avatar video generation if available
    if musetalk:
        pipeline_steps.append(musetalk)

    pipeline_steps.extend([
        transport.output(),
        assistant_aggregator,
    ])

    pipeline = Pipeline(pipeline_steps)

    task = PipelineTask(
        pipeline,
        params=PipelineParams(
            enable_metrics=True,
            enable_usage_metrics=True,
        ),
        idle_timeout_secs=runner_args.pipeline_idle_timeout_secs,
    )

    # ── Event handlers ─────────────────────────────────────────────────
    @transport.event_handler("on_client_connected")
    async def on_client_connected(transport, client):
        logger.info("Client connected")
        # Start conversation - let LLM follow system instructions
        await task.queue_frames([LLMRunFrame()])

    @transport.event_handler("on_client_disconnected")
    async def on_client_disconnected(transport, client):
        logger.info("Client disconnected")
        await task.cancel()

    # ── Run ────────────────────────────────────────────────────────────
    runner = PipelineRunner(handle_sigint=runner_args.handle_sigint)
    await runner.run(task)


async def bot(runner_args: RunnerArguments):
    """Main bot entry point compatible with Pipecat Cloud and development runner."""

    transport = await create_transport(runner_args, transport_params)
    await run_bot(transport, runner_args)


if __name__ == "__main__":
    main()
