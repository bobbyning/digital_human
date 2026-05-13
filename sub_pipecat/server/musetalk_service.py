#
# Copyright (c) 2024-2026, Daily
#
# SPDX-License-Identifier: BSD 2-Clause License
#
"""
MuseTalk Video Service for Pipecat 1.1.0

Receives TTS audio frames, generates realistic face video with lip sync
using MuseTalk, and outputs synchronized video frames.

MuseTalk (https://github.com/TMElyralab/MuseTalk) is an open-source real-time
lip sync framework from Tencent. It takes a reference face image and audio
input, and outputs video frames of the face lip-syncing to the audio at 25fps.

This service integrates MuseTalk's inference pipeline (VAE, UNet, Whisper,
face parsing, face detection) with Pipecat's FrameProcessor API.
"""

import copy
import io
import math
import os
import pickle
import tempfile
import threading
from typing import Optional

import cv2
import numpy as np
import torch
from loguru import logger

# Pipecat frame types
from pipecat.frames.frames import (
    Frame,
    OutputImageRawFrame,
    TTSAudioRawFrame,
)
from pipecat.processors.frame_processor import FrameDirection, FrameProcessor


# ---------------------------------------------------------------------------
# Lazy imports for heavy MuseTalk dependencies
# ---------------------------------------------------------------------------

MuseTalkAvailable = False
_load_all_model = None
_AudioProcessor = None
_FaceParsing = None
_get_landmark_and_bbox = None
_get_image_prepare_material = None
_get_image_blending = None
_datagen = None
_read_imgs = None

try:
    from musetalk.utils.utils import (
        load_all_model,
        datagen,
    )
    from musetalk.utils.audio_processor import AudioProcessor
    from musetalk.utils.face_parsing import FaceParsing
    from musetalk.utils.preprocessing import (
        get_landmark_and_bbox,
        read_imgs,
    )
    from musetalk.utils.blending import (
        get_image_prepare_material,
        get_image_blending,
    )

    _load_all_model = load_all_model
    _AudioProcessor = AudioProcessor
    _FaceParsing = FaceParsing
    _get_landmark_and_bbox = get_landmark_and_bbox
    _get_image_prepare_material = get_image_prepare_material
    _get_image_blending = get_image_blending
    _datagen = datagen
    _read_imgs = read_imgs

    MuseTalkAvailable = True
    logger.info("MuseTalk package found")
except ImportError as e:
    logger.warning(
        "MuseTalk not installed. Video avatar will be disabled. "
        f"Import error: {e}\n"
        "To enable MuseTalk:\n"
        "  cd MuseTalk && pip install -r requirements.txt\n"
        "  pip install openmim && mim install mmengine mmcv mmdet mmpose\n"
        "Then download model weights."
    )


# ---------------------------------------------------------------------------
# Default paths
# ---------------------------------------------------------------------------

MUSE_TALK_DIR = os.path.dirname(os.path.abspath(__file__))
MUSE_TALK_MODELS_DIR = os.path.join(MUSE_TALK_DIR, "MuseTalk", "models")

DEFAULT_UNET_CONFIG_V15 = os.path.join(MUSE_TALK_MODELS_DIR, "musetalkV15", "musetalk.json")
DEFAULT_UNET_MODEL_V15 = os.path.join(MUSE_TALK_MODELS_DIR, "musetalkV15", "unet.pth")
DEFAULT_VAE_PATH = os.path.join(MUSE_TALK_MODELS_DIR, "sd-vae")
DEFAULT_WHISPER_DIR = os.path.join(MUSE_TALK_MODELS_DIR, "whisper")

DEFAULT_DWPOSE_CONFIG = os.path.join(
    MUSE_TALK_DIR, "MuseTalk", "musetalk", "utils", "dwpose",
    "rtmpose-l_8xb32-270e_coco-ubody-wholebody-384x288.py"
)
DEFAULT_DWPOSE_CHECKPOINT = os.path.join(MUSE_TALK_MODELS_DIR, "dwpose", "dw-ll_ucoco_384.pth")

DEFAULT_FACE_PARSE_RESNET = os.path.join(MUSE_TALK_MODELS_DIR, "face-parse-bisent", "resnet18-5c106cde.pth")
DEFAULT_FACE_PARSE_MODEL = os.path.join(MUSE_TALK_MODELS_DIR, "face-parse-bisent", "79999_iter.pth")


class MuseTalkWrapper:
    """
    Wrapper for MuseTalk inference pipeline.

    Handles model loading, face detection, audio processing, and video generation.
    Uses MuseTalk's v15 pipeline (UNet + VAE + Whisper + Face Parsing) for
    real-time lip-synced face video generation.
    """

    def __init__(
        self,
        face_image_path: str,
        device: str = "cuda",
        fps: int = 25,
        batch_size: int = 8,
        version: str = "v15",
        bbox_shift: int = 0,
        extra_margin: int = 10,
        audio_padding_length_left: int = 2,
        audio_padding_length_right: int = 2,
        parsing_mode: str = "jaw",
    ):
        """
        Initialize MuseTalk wrapper.

        Args:
            face_image_path: Path to reference face image
            device: Device for inference ("cuda" or "cpu")
            fps: Target frames per second for output video
            batch_size: Batch size for UNet inference
            version: MuseTalk version ("v15" recommended)
            bbox_shift: Bounding box shift value for face crop
            extra_margin: Extra margin for face cropping (v15 only)
            audio_padding_length_left: Left padding length for audio
            audio_padding_length_right: Right padding length for audio
            parsing_mode: Face parsing mode ("raw", "jaw", "neck")
        """
        self.face_image_path = face_image_path
        self.device_str = device
        self.device = torch.device(device if torch.cuda.is_available() else "cpu")
        self.fps = fps
        self.batch_size = batch_size
        self.version = version
        self.bbox_shift = bbox_shift
        self.extra_margin = extra_margin
        self.audio_padding_length_left = audio_padding_length_left
        self.audio_padding_length_right = audio_padding_length_right
        self.parsing_mode = parsing_mode

        self._initialized = False

        # Model components (loaded lazily)
        self.vae = None
        self.unet = None
        self.pe = None
        self.timesteps = None
        self.audio_processor = None
        self.whisper = None
        self.weight_dtype = None
        self.face_parser = None

        # Precomputed face data
        self.coord_list_cycle = []
        self.frame_list_cycle = []
        self.input_latent_list_cycle = []
        self.mask_list_cycle = []
        self.mask_coords_list_cycle = []

    def initialize(self):
        """Load all models and prepare the reference face."""
        if self._initialized:
            return

        if not MuseTalkAvailable:
            raise RuntimeError("MuseTalk dependencies are not installed")

        logger.info(f"Initializing MuseTalk v15 on device: {self.device}")

        # --- 1. Load VAE, UNet, Positional Encoding ---
        if self.version == "v15":
            unet_config = DEFAULT_UNET_CONFIG_V15
            unet_model_path = DEFAULT_UNET_MODEL_V15
        else:
            unet_config = os.path.join(MUSE_TALK_MODELS_DIR, "musetalk", "musetalk.json")
            unet_model_path = os.path.join(MUSE_TALK_MODELS_DIR, "musetalk", "pytorch_model.bin")

        self.vae, self.unet, self.pe = _load_all_model(
            unet_model_path=unet_model_path,
            vae_type="sd-vae",
            unet_config=unet_config,
            device=self.device,
        )
        self.timesteps = torch.tensor([0], device=self.device)

        # Use float16 for faster inference
        self.pe = self.pe.half().to(self.device)
        self.vae.vae = self.vae.vae.half().to(self.device)
        self.unet.model = self.unet.model.half().to(self.device)
        self.weight_dtype = self.unet.model.dtype

        logger.info("VAE, UNet, PE loaded")

        # --- 2. Load Whisper for audio feature extraction ---
        from transformers import WhisperModel

        self.audio_processor = _AudioProcessor(
            feature_extractor_path=DEFAULT_WHISPER_DIR
        )
        self.whisper = WhisperModel.from_pretrained(DEFAULT_WHISPER_DIR)
        self.whisper = self.whisper.to(device=self.device, dtype=self.weight_dtype).eval()
        self.whisper.requires_grad_(False)
        logger.info("Whisper model loaded")

        # --- 3. Load face parser ---
        try:
            self.face_parser = _FaceParsing(
                left_cheek_width=90,
                right_cheek_width=90,
            )
            logger.info("Face parser loaded")
        except Exception as e:
            logger.warning(
                f"Face parser model could not be loaded: {e}\n"
                "Face parsing is required for high-quality blending. "
                "Run: python download_musetalk_models.py"
            )
            raise RuntimeError(
                "Face parsing model (79999_iter.pth) is missing or corrupted. "
                "Run: python E:/github_project/digital_human/sub_pipecat/server/download_musetalk_models.py"
            ) from e

        # --- 4. Prepare reference face ---
        self._prepare_face()

        self._initialized = True
        logger.info(
            f"MuseTalk initialized: {len(self.frame_list_cycle)} reference frames, "
            f"fps={self.fps}, device={self.device}"
        )

    def _prepare_face(self):
        """
        Prepare the reference face image: detect landmarks, compute bounding
        boxes, encode VAE latents, and precompute blend masks.
        """
        if not os.path.exists(self.face_image_path):
            raise FileNotFoundError(
                f"Face image not found: {self.face_image_path}\n"
                "Please set MUSE_TALK_FACE_IMAGE environment variable."
            )

        # Use the face image as a single-frame "video"
        face_img = cv2.imread(self.face_image_path)
        if face_img is None:
            raise ValueError(f"Could not read face image: {self.face_image_path}")

        logger.info(f"Loaded face image: {face_img.shape}")

        # Detect face landmarks and bounding box
        img_list = [self.face_image_path]
        coord_list, frame_list = _get_landmark_and_bbox(img_list, self.bbox_shift)

        # Filter out invalid detections
        coord_placeholder = (0.0, 0.0, 0.0, 0.0)
        valid_coords = []
        valid_frames = []
        input_latent_list = []

        for i, (bbox, frame) in enumerate(zip(coord_list, frame_list)):
            if bbox == coord_placeholder:
                logger.warning(f"Frame {i}: no face detected, skipping")
                continue

            x1, y1, x2, y2 = bbox
            if x2 - x1 <= 0 or y2 - y1 <= 0:
                logger.warning(f"Frame {i}: invalid bbox {bbox}, skipping")
                continue

            # For v15, add extra margin to bottom of face crop
            if self.version == "v15":
                y2 = y2 + self.extra_margin
                y2 = min(y2, frame.shape[0])
                bbox = [x1, y1, x2, y2]

            crop_frame = frame[y1:y2, x1:x2]
            resized_crop_frame = cv2.resize(
                crop_frame, (256, 256), interpolation=cv2.INTER_LANCZOS4
            )
            latents = self.vae.get_latents_for_unet(resized_crop_frame)

            valid_coords.append(list(bbox))
            valid_frames.append(frame)
            input_latent_list.append(latents)

        if not valid_coords:
            raise RuntimeError(
                f"No valid face detected in {self.face_image_path}. "
                "The image should contain a clearly visible face."
            )

        # Create forward+reverse cycle for smooth looping
        self.frame_list_cycle = valid_frames + valid_frames[::-1]
        self.coord_list_cycle = valid_coords + valid_coords[::-1]
        self.input_latent_list_cycle = input_latent_list + input_latent_list[::-1]

        # Precompute blend masks for each frame
        self.mask_list_cycle = []
        self.mask_coords_list_cycle = []

        for i, frame in enumerate(self.frame_list_cycle):
            x1, y1, x2, y2 = self.coord_list_cycle[i]

            if self.version == "v15":
                mode = self.parsing_mode
            else:
                mode = "raw"

            mask, crop_box = _get_image_prepare_material(
                frame, [x1, y1, x2, y2], fp=self.face_parser, mode=mode
            )
            self.mask_list_cycle.append(mask)
            self.mask_coords_list_cycle.append(crop_box)

        logger.info(
            f"Face preparation complete: {len(self.frame_list_cycle)} frames in cycle"
        )

    @torch.no_grad()
    def generate_video_frames(self, audio_path: str) -> list:
        """
        Generate lip-synced video frames from an audio file.

        Args:
            audio_path: Path to WAV audio file

        Returns:
            List of numpy arrays (BGR, uint8) representing video frames
        """
        if not self._initialized:
            self.initialize()

        # --- Extract audio features ---
        whisper_input_features, librosa_length = self.audio_processor.get_audio_feature(
            audio_path, weight_dtype=self.weight_dtype
        )
        whisper_chunks = self.audio_processor.get_whisper_chunk(
            whisper_input_features,
            self.device,
            self.weight_dtype,
            self.whisper,
            librosa_length,
            fps=self.fps,
            audio_padding_length_left=self.audio_padding_length_left,
            audio_padding_length_right=self.audio_padding_length_right,
        )

        video_num = len(whisper_chunks)
        if video_num == 0:
            logger.warning("No audio chunks produced - silence or empty audio")
            return []

        logger.debug(f"Audio produced {video_num} chunks at {self.fps} fps")

        # --- Run UNet inference batch by batch ---
        gen = _datagen(
            whisper_chunks,
            self.input_latent_list_cycle,
            self.batch_size,
        )

        res_frame_list = []
        for whisper_batch, latent_batch in gen:
            audio_feature_batch = self.pe(whisper_batch.to(self.device))
            latent_batch = latent_batch.to(device=self.device, dtype=self.unet.model.dtype)

            pred_latents = self.unet.model(
                latent_batch,
                self.timesteps,
                encoder_hidden_states=audio_feature_batch,
            ).sample
            pred_latents = pred_latents.to(device=self.device, dtype=self.vae.vae.dtype)
            recon = self.vae.decode_latents(pred_latents)
            res_frame_list.extend(recon)

        # --- Blend generated face regions back onto the reference frame ---
        final_frames = []
        num_cycle = len(self.coord_list_cycle)

        for i, res_frame in enumerate(res_frame_list):
            cycle_idx = i % num_cycle
            bbox = self.coord_list_cycle[cycle_idx]
            ori_frame = copy.deepcopy(self.frame_list_cycle[cycle_idx])

            x1, y1, x2, y2 = bbox
            try:
                res_frame_resized = cv2.resize(
                    res_frame.astype(np.uint8), (x2 - x1, y2 - y1)
                )
            except Exception:
                continue

            mask = self.mask_list_cycle[cycle_idx]
            mask_crop_box = self.mask_coords_list_cycle[cycle_idx]
            combine_frame = _get_image_blending(
                ori_frame, res_frame_resized, bbox, mask, mask_crop_box
            )
            final_frames.append(combine_frame)

        return final_frames


class MuseTalkVideoService(FrameProcessor):
    """
    Pipecat FrameProcessor that wraps MuseTalk for real-time lip sync video.

    Receives audio frames from TTS, generates face video with lip sync,
    and outputs video frames synchronized with the audio.

    The audio is buffered until a full utterance is received (indicated by a
    non-audio frame or buffer size threshold), then processed through MuseTalk
    to generate video frames at the configured FPS.
    """

    def __init__(
        self,
        face_image_path: str = "face.png",
        device: str = "cuda",
        fps: int = 25,
        batch_size: int = 8,
        audio_buffer_seconds: float = 2.0,
        sample_rate: int = 24000,
        num_channels: int = 1,
    ):
        """
        Initialize MuseTalk video service.

        Args:
            face_image_path: Path to reference face image for MuseTalk
            device: Device for inference ("cuda" or "cpu")
            fps: Output video frames per second (default 25, matching MuseTalk)
            batch_size: Batch size for UNet inference
            audio_buffer_seconds: How much audio to buffer before generating video.
                Set to 0 to generate on every audio chunk.
            sample_rate: Expected audio sample rate from TTS
            num_channels: Expected number of audio channels
        """
        super().__init__()

        self.face_image_path = face_image_path
        self.device = device
        self.fps = fps
        self.batch_size = batch_size
        self.audio_buffer_seconds = audio_buffer_seconds
        self.sample_rate = sample_rate
        self.num_channels = num_channels
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
                    fps=fps,
                    batch_size=batch_size,
                )
                logger.info("MuseTalkVideoService created (lazy init on first use)")
            except Exception as e:
                logger.warning(f"Failed to create MuseTalk wrapper: {e}")
                self.wrapper = None
        else:
            logger.info("MuseTalk not available - video avatar disabled")

    def _ensure_initialized(self):
        """Lazily initialize the MuseTalk pipeline on first use."""
        if self.wrapper is not None and not self.wrapper._initialized:
            logger.info("Initializing MuseTalk pipeline (first use)...")
            self.wrapper.initialize()

    def _flush_audio_buffer(self, audio_frame) -> list:
        """
        Process buffered audio through MuseTalk and return video frames.

        Args:
            audio_frame: The last audio frame (for metadata like sample_rate)

        Returns:
            List of OutputImageRawFrame objects
        """
        if self.wrapper is None:
            return []

        with self._lock:
            if len(self._audio_buffer) == 0:
                return []
            audio_data = bytes(self._audio_buffer)
            self._audio_buffer.clear()

        # Ensure pipeline is initialized
        self._ensure_initialized()

        # Write audio to a temporary WAV file for MuseTalk
        # Audio from pipecat TTS is raw PCM bytes (16-bit signed LE)
        tmp_wav = None
        try:
            import wave

            tmp_wav = tempfile.NamedTemporaryFile(suffix=".wav", delete=False)
            tmp_wav_path = tmp_wav.name

            # Get sample rate from the frame if available
            sr = getattr(audio_frame, "sample_rate", self.sample_rate)
            ch = getattr(audio_frame, "num_channels", self.num_channels)

            with wave.open(tmp_wav_path, "wb") as wf:
                wf.setnchannels(ch)
                wf.setsampwidth(2)  # 16-bit
                wf.setframerate(sr)
                wf.writeframes(audio_data)

            # Run MuseTalk inference
            video_frames = self.wrapper.generate_video_frames(tmp_wav_path)

            # Convert to OutputImageRawFrame
            output_frames = []
            for frame_data in video_frames:
                # Encode frame as JPEG
                _, buffer = cv2.imencode(".jpg", frame_data, [cv2.IMWRITE_JPEG_QUALITY, 90])
                image_bytes = buffer.tobytes()

                h, w = frame_data.shape[:2]
                output_frame = OutputImageRawFrame(
                    image=image_bytes,
                    size=(w, h),
                    format="JPEG",
                )
                output_frames.append(output_frame)

            logger.debug(f"MuseTalk produced {len(output_frames)} video frames from {len(audio_data)} bytes audio")
            return output_frames

        except Exception as e:
            logger.error(f"MuseTalk video generation error: {e}")
            import traceback
            traceback.print_exc()
            return []
        finally:
            if tmp_wav is not None:
                try:
                    os.unlink(tmp_wav.name)
                except OSError:
                    pass

    async def process_frame(self, frame: Frame, direction: FrameDirection):
        """
        Process incoming frame. Buffers audio frames and generates video when
        enough audio is accumulated.

        Args:
            frame: Incoming frame (TTSAudioRawFrame or other)
            direction: Frame direction (upstream or downstream)
        """
        await super().process_frame(frame, direction)

        # Check if this is a TTS audio frame
        if not isinstance(frame, TTSAudioRawFrame):
            # Non-audio frame: flush any buffered audio and pass through
            if len(self._audio_buffer) > 0 and self.wrapper is not None:
                video_frames = self._flush_audio_buffer(frame)
                for vf in video_frames:
                    await self.push_frame(vf, direction)
            await self.push_frame(frame, direction)
            return

        # Buffer the audio data
        with self._lock:
            self._audio_buffer.extend(frame.audio)

        # Estimate buffered audio duration
        sr = getattr(frame, "sample_rate", self.sample_rate)
        ch = getattr(frame, "num_channels", self.num_channels)
        bytes_per_second = sr * ch * 2  # 16-bit = 2 bytes
        if bytes_per_second > 0:
            buffered_seconds = len(self._audio_buffer) / bytes_per_second
        else:
            buffered_seconds = 0

        # Generate video when we have enough audio buffered
        should_generate = (
            self.audio_buffer_seconds <= 0
            or buffered_seconds >= self.audio_buffer_seconds
        )

        if should_generate and self.wrapper is not None:
            video_frames = self._flush_audio_buffer(frame)
            for vf in video_frames:
                await self.push_frame(vf, direction)

        # Always pass through the audio frame
        await self.push_frame(frame, direction)

    async def cleanup(self):
        """Clean up resources."""
        if self.wrapper is not None:
            self.wrapper = None
        await super().cleanup()


def create_musetalk_service(
    face_image_path: Optional[str] = None,
    device: Optional[str] = None,
    fps: int = 25,
    batch_size: int = 8,
) -> Optional[MuseTalkVideoService]:
    """
    Factory function to create MuseTalk service with environment config.

    Args:
        face_image_path: Path to face image (defaults to MUSE_TALK_FACE_IMAGE env, then face.png)
        device: Device for inference (defaults to MUSE_TALK_DEVICE env, then cuda)
        fps: Output video FPS
        batch_size: UNet inference batch size

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
            fps=fps,
            batch_size=batch_size,
        )
    except Exception as e:
        logger.warning(f"Could not create MuseTalk service: {e}")
        return None
