#
# Copyright (c) 2024-2026, Daily
#
# SPDX-License-Identifier: BSD 2-Clause License
#
"""
Pipecat Digital Human Bot

Pipeline:
    Transport Input -> Deepgram STT -> Context Aggregator (user) ->
    DeepSeek LLM -> Fish Audio TTS -> Simli Avatar ->
    Transport Output -> Context Aggregator (assistant)
"""

import os

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
from pipecat.services.deepgram.stt import DeepgramSTTService
from pipecat.services.deepseek.llm import DeepSeekLLMService
from pipecat.services.fish.tts import FishAudioTTSService
from pipecat.services.simli.video import SimliVideoService
from pipecat.transports.base_transport import BaseTransport, TransportParams
from pipecat.transports.daily.transport import DailyParams

load_dotenv(override=True)


# We use lambdas to defer transport parameter creation until the transport
# type is selected at runtime.
transport_params = {
    "daily": lambda: DailyParams(
        audio_in_enabled=True,
        audio_out_enabled=True,
        video_out_enabled=True,
        video_out_is_live=True,
        video_out_width=512,
        video_out_height=512,
    ),
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

    logger.info("Starting digital human bot")

    # ── STT (Speech-to-Text) ───────────────────────────────────────────
    stt = DeepgramSTTService(
        api_key=os.environ["DEEPGRAM_API_KEY"],
        language="zh",
    )

    # ── TTS (Text-to-Speech) ───────────────────────────────────────────
    tts = FishAudioTTSService(
        api_key=os.environ["FISH_API_KEY"],
        voice_id=os.environ["FISH_VOICE_ID"],
    )

    # ── Avatar (Video) ─────────────────────────────────────────────────
    simli = SimliVideoService(
        api_key=os.environ["SIMLI_API_KEY"],
        face_id=os.environ["SIMLI_FACE_ID"],
    )

    # ── LLM (Large Language Model) ─────────────────────────────────────
    llm = DeepSeekLLMService(
        api_key=os.environ["DEEPSEEK_API_KEY"],
        settings=DeepSeekLLMService.Settings(
            model="deepseek-chat",
            system_instruction="""\
你是一个友好的数字人助手。你用自然、口语化的中文与用户交流。
请遵循以下规则：
1. 用简洁自然的口语回答问题，不要使用书面语或过于正式的表达。
2. 不要使用表情符号。
3. 不要使用项目符号或编号列表，因为你的回答会被语音播报。
4. 保持回答简短，每次回答控制在两三句话以内。
5. 如果用户问候你，友好地回应并询问有什么可以帮忙的。""",
        ),
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
    pipeline = Pipeline(
        [
            transport.input(),
            stt,
            user_aggregator,
            llm,
            tts,
            simli,
            transport.output(),
            assistant_aggregator,
        ]
    )

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
