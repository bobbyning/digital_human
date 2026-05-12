#
# Copyright (c) 2024-2026, Daily
#
# SPDX-License-Identifier: BSD 2-Clause License
#
"""
MuseTalk Video Service for Pipecat

Receives TTS audio frames, generates realistic face video with lip sync
using MuseTalk, and outputs synchronized video frames.

MuseTalk (https://github.com/TMElyralab/MuseTalk) is an open-source real-time
lip sync framework from Tencent. It takes a reference face image and audio
input, and outputs video frames of the face lip-syncing to the audio at 30fps+.
"""

import io
import os
import tempfile
import threading
from typing import Optional

from loguru import logger

# Pipecat frame types
from pipecat.frames.frames import (
    Frame,
    TTSAudioRawFrame,
    OutputAudioRawFrame,
    OutputImageRawFrame,
)
from pipecat.processors.frame_processor import FrameProcessor


# Try to import MuseTalk - will fail if not installed
MuseTalkAvailable = False
MuseTalkPipeline = None
try:
    from musetalk import MuseTalkPipeline

    MuseTalkAvailable = True
    logger.info("MuseTalk package found")
except ImportError:
    logger.warning(
        "MuseTalk not installed. Video avatar will be disabled.\n"
        "To enable MuseTalk:\n"
        "  git clone https://github.com/TMElyralab/MuseTalk.git\n"
        "  cd MuseTalk && pip install -e .\n"
        "Then download model weights and set MUSE_TALK_FACE_IMAGE"
    )


class MuseTalkWrapper:
    """
    Wrapper for MuseTalk inference pipeline.

    Handles model loading, face detection, audio processing, and video generation.
    """

    def __init__(
        self,
        face_image_path: str,
        device: str = "cuda",
        model_path: Optional[str] = None,
    ):
        """
        Initialize MuseTalk wrapper.

        Args:
            face_image_path: Path to reference face image
            device: Device for inference ("cuda" or "cpu")
            model_path: Optional path to MuseTalk model weights
        """
        self.face_image_path = face_image_path
        self.device = device
        self.model_path = model_path or os.environ.get("MUSE_TALK_MODEL_PATH")
        self.pipeline = None
        self._initialized = False

    def _load_face_image(self):
        """Load and validate the reference face image."""
        import cv2

        if not os.path.exists(self.face_image_path):
            raise FileNotFoundError(
                f"Face image not found: {self.face_image_path}\n"
                f"Please set MUSE_TALK_FACE_IMAGE environment variable to a valid image path."
            )

        # Load image and validate
        face_image = cv2.imread(self.face_image_path)
        if face_image is None:
            raise ValueError(f"Could not read face image: {self.face_image_path}")

        logger.info(f"Loaded face image: {self.face_image_path} - {face_image.shape}")
        return face_image

    def initialize(self):
        """Initialize the MuseTalk pipeline."""
        if not MuseTalkAvailable:
            raise RuntimeError("MuseTalk is not installed")

        if self._initialized:
            return

        logger.info(f"Initializing MuseTalk on device: {self.device}")

        # Load face image first
        face_image = self._load_face_image()

        # TODO: Configure pipeline based on MuseTalk API
        # The MuseTalkPipeline initialization depends on the exact API version.
        # Common patterns:
        #   pipeline = MuseTalkPipeline(
        #       model_path=self.model_path,
        #       device=self.device,
        #   )
        # or with face_image directly:
        #   pipeline = MuseTalkPipeline(
        #       ref_image=face_image,
        #       device=self.device,
        #   )

        try:
            # Attempt standard initialization
            self.pipeline = MuseTalkPipeline(
                model_path=self.model_path,
                device=self.device,
            )
            self._initialized = True
            logger.info("MuseTalk pipeline initialized successfully")
        except TypeError:
            # Try alternative initialization without model_path
            logger.info("Trying alternative MuseTalk initialization...")
            self.pipeline = MuseTalkPipeline(device=self.device)
            self._initialized = True

    def generate_video_frames(self, audio_bytes: bytes) -> list:
        """
        Generate video frames from audio input.

        Args:
            audio_bytes: Raw audio data

        Returns:
            List of video frames as numpy arrays
        """
        if not self._initialized:
            self.initialize()

        # Save audio to temporary file (MuseTalk typically works with file paths)
        with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as f:
            f.write(audio_bytes)
            audio_path = f.name

        try:
            # TODO: Call MuseTalk inference
            # The exact API call depends on MuseTalk version. Common patterns:

            # Pattern 1: Process from file paths
            #   video_frames = self.pipeline.process(
            #       audio_path=audio_path,
            #       ref_image_path=self.face_image_path,
            #   )

            # Pattern 2: Process with face image directly
            #   video_frames = self.pipeline(
            #       audio=audio_path,
            #       ref_image=face_image,
            #   )

            # Pattern 3: Batch processing for real-time
            #   frames = self.pipeline.inference(audio_path, ref_image_path)

            # For now, return empty list as placeholder
            # Real implementation would call the appropriate MuseTalk API
            logger.debug(f"Processing audio: {len(audio_bytes)} bytes")

            # Placeholder: return empty list until MuseTalk API is confirmed
            # TODO: Replace with actual MuseTalk inference call
            return []

        finally:
            # Clean up temp audio file
            try:
                os.unlink(audio_path)
            except OSError:
                pass


class MuseTalkVideoService(FrameProcessor):
    """
    Pipecat FrameProcessor that wraps MuseTalk for real-time lip sync video.

    Receives audio frames from TTS, generates face video with lip sync,
    and outputs video frames synchronized with the audio.
    """

    def __init__(
        self,
        face_image_path: str = "face.png",
        device: str = "cuda",
        model_path: Optional[str] = None,
        audio_buffer_seconds: float = 2.0,
    ):
        """
        Initialize MuseTalk video service.

        Args:
            face_image_path: Path to reference face image for MuseTalk
            device: Device for inference ("cuda" or "cpu")
            model_path: Optional path to MuseTalk model weights
            audio_buffer_seconds: How much audio to buffer before generating video
        """
        super().__init__()

        self.face_image_path = face_image_path
        self.device = device
        self.audio_buffer_seconds = audio_buffer_seconds
        self.wrapper: Optional[MuseTalkWrapper] = None

        # Audio buffer for accumulating TTS frames
        self._audio_buffer = bytearray()
        self._lock = threading.Lock()

        # Check if MuseTalk is available
        if MuseTalkAvailable:
            try:
                self.wrapper = MuseTalkWrapper(
                    face_image_path=face_image_path,
                    device=device,
                    model_path=model_path,
                )
                # Try lazy initialization - may fail if models not downloaded
                logger.info("MuseTalkVideoService initialized (lazy init on first use)")
            except Exception as e:
                logger.warning(f"Failed to initialize MuseTalk: {e}")
                self.wrapper = None
        else:
            logger.info("MuseTalk not available - video avatar disabled")

    async def process_frame(self, frame: Frame):
        """
        Process incoming frame. Buffers audio frames and generates video when ready.

        Args:
            frame: Incoming frame (TTSAudioRawFrame or OutputAudioRawFrame)

        Output:
            OutputImageRawFrame: Video frame with lip sync (if MuseTalk is available)
        """
        await super().process_frame(frame)

        # Check if this is an audio frame
        audio_frame = None
        if isinstance(frame, TTSAudioRawFrame):
            audio_frame = frame
        elif isinstance(frame, OutputAudioRawFrame):
            audio_frame = frame

        if audio_frame is None:
            # Pass through non-audio frames
            await self.push_frame(frame)
            return

        # Buffer the audio data
        with self._lock:
            self._audio_buffer.extend(audio_frame.audio)

        # Check if we should generate video
        # For now, generate on each frame (real-time mode)
        # TODO: Implement smarter triggering based on speech/silence detection
        should_generate = len(self._audio_buffer) > 0

        if should_generate and self.wrapper is not None:
            try:
                # Generate video from buffered audio
                with self._lock:
                    audio_data = bytes(self._audio_buffer)

                video_frames = self.wrapper.generate_video_frames(audio_data)

                # Clear buffer after processing
                self._audio_buffer.clear()

                # Output video frames
                for frame_data in video_frames:
                    # Convert numpy array to bytes and push as OutputImageRawFrame
                    import cv2

                    # Encode frame as JPEG (or use appropriate format)
                    _, buffer = cv2.imencode(".jpg", frame_data)
                    image_bytes = buffer.tobytes()

                    # Create output frame with proper timestamps
                    output_frame = OutputImageRawFrame(
                        image=image_bytes,
                        format="jpeg",
                        timestamp=audio_frame.timestamp if hasattr(audio_frame, "timestamp") else None,
                    )
                    await self.push_frame(output_frame)

            except Exception as e:
                logger.error(f"MuseTalk video generation error: {e}")
                # Continue with audio-only mode on error
        elif self.wrapper is None:
            # MuseTalk not available - just pass through audio
            pass

        # Also pass through the audio frame
        await self.push_frame(frame)

    async def cleanup(self):
        """Clean up resources."""
        if self.wrapper is not None:
            # TODO: Cleanup MuseTalk resources if needed
            pass
        await super().cleanup()


def create_musetalk_service(
    face_image_path: Optional[str] = None,
    device: Optional[str] = None,
) -> Optional[MuseTalkVideoService]:
    """
    Factory function to create MuseTalk service with environment config.

    Args:
        face_image_path: Path to face image (defaults to MUSE_TALK_FACE_IMAGE env)
        device: Device for inference (defaults to MUSE_TALK_DEVICE env)

    Returns:
        MuseTalkVideoService instance, or None if MuseTalk not available
    """
    if not MuseTalkAvailable:
        logger.info("MuseTalk not installed - returning None")
        return None

    face_path = face_image_path or os.environ.get("MUSE_TALK_FACE_IMAGE", "face.png")
    device_str = device or os.environ.get("MUSE_TALK_DEVICE", "cuda")

    try:
        return MuseTalkVideoService(
            face_image_path=face_path,
            device=device_str,
        )
    except Exception as e:
        logger.warning(f"Could not create MuseTalk service: {e}")
        return None