"""
Marvo Voice — core/voice.py
===========================
Cloud-Ready Client-Server TTS Architecture:
  - Online Neural TTS: Microsoft Edge-TTS with 5 character voices.
  - Returns Base64-encoded audio directly to callers.
  - Zero local audio playback on the server.
  - Immediate temporary file cleanup.
  - STT Stub: SpeechRecognition with ambient noise adjustment.

Prerequisites:
  pip install edge-tts
"""

import os
import base64
import asyncio
import logging
import tempfile
import threading
from pathlib import Path

_logger = logging.getLogger("marvo.voice")

# ──────────────────────────────────────────────────────────────────────
# 5 CHARACTER EDGE-TTS VOICES & INTRO SAMPLES
# ──────────────────────────────────────────────────────────────────────
VOICES = {
    "voice_1": "hi-IN-SwaraNeural",      # Sweet Female Hindi
    "voice_2": "hi-IN-MadhurNeural",     # Professional Male Hindi
    "voice_3": "en-US-AriaNeural",       # Smart Female US (Default)
    "voice_4": "en-GB-RyanNeural",       # Deep Male UK
    "voice_5": "en-AU-NatashaNeural",    # Energetic Female AU
}

VOICE_SAMPLES = {
    "voice_1": "नमस्ते! मैं स्वरा हूँ। मैं आपकी किस प्रकार सहायता कर सकती हूँ?",
    "voice_2": "नमस्कार! मैं मधुर हूँ। मार्वो सिस्टम में आपका स्वागत है।",
    "voice_3": "Hello! I am Aria, your smart and witty desktop assistant.",
    "voice_4": "Good day! I'm Ryan. Let's get straight down to business.",
    "voice_5": "G'day mate! I'm Natasha, ready and excited to help you out!",
}

# ──────────────────────────────────────────────────────────────────────
# EDGE-TTS SYNTHESIS (ASYNC HELPER)
# ──────────────────────────────────────────────────────────────────────
async def _synthesize_edge_tts(text: str, voice_name: str, output_path: str):
    import edge_tts
    communicate = edge_tts.Communicate(text, voice_name)
    await communicate.save(output_path)


# ──────────────────────────────────────────────────────────────────────
# BASE64 AUDIO GENERATION (CLIENT-SERVER ARCHITECTURE)
# ──────────────────────────────────────────────────────────────────────
def generate_audio_base64(text: str, voice_id: str = "voice_3") -> str:
    """
    Synthesizes speech using Microsoft Edge-TTS, encodes the MP3 audio
    to a Base64 string, immediately removes the temporary file, and returns
    the Base64 payload for client-side HTML5 playback.
    """
    if not text or not text.strip():
        return ""

    voice_name = VOICES.get(voice_id, VOICES["voice_3"])
    temp_dir = tempfile.gettempdir()
    temp_file = os.path.join(temp_dir, f"marvo_tts_{os.getpid()}_{threading.get_ident()}.mp3")

    try:
        try:
            loop = asyncio.get_running_loop()
        except RuntimeError:
            loop = None

        if loop and loop.is_running():
            import concurrent.futures
            with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
                pool.submit(lambda: asyncio.run(_synthesize_edge_tts(text.strip(), voice_name, temp_file))).result()
        else:
            asyncio.run(_synthesize_edge_tts(text.strip(), voice_name, temp_file))

        if os.path.isfile(temp_file) and os.path.getsize(temp_file) > 0:
            with open(temp_file, "rb") as f:
                audio_bytes = f.read()
            return base64.b64encode(audio_bytes).decode("utf-8")
        return ""
    except Exception as e:
        _logger.error(f"Edge-TTS base64 generation failed: {e}")
        return ""
    finally:
        if os.path.isfile(temp_file):
            try:
                os.remove(temp_file)
            except OSError:
                pass


def speak(text: str, voice_id: str = "voice_3") -> str:
    """
    Generates and returns base64 MP3 audio string for client-side playback.
    Maintains backwards-compatible interface with server endpoints.
    """
    return generate_audio_base64(text, voice_id=voice_id)


def play_sample(voice_id: str = "voice_3") -> str:
    """Generates base64 MP3 audio string for character sample preview."""
    sample_text = VOICE_SAMPLES.get(voice_id, VOICE_SAMPLES["voice_3"])
    return generate_audio_base64(sample_text, voice_id=voice_id)


def get_sample_audio_base64(voice_id: str = "voice_3") -> str:
    """Explicit alias for character sample preview base64 audio."""
    return play_sample(voice_id=voice_id)


# ──────────────────────────────────────────────────────────────────────
# SPEECH-TO-TEXT (STT)
# ──────────────────────────────────────────────────────────────────────
def set_voice_rate(rate: int):
    """Stub for backwards compatibility."""
    pass


def set_voice_volume(volume: float):
    """Stub for backwards compatibility."""
    pass


def listen(timeout: int = 5, phrase_limit: int = 10) -> str:
    """Capture microphone audio and return recognized text."""
    try:
        import speech_recognition as sr
        recognizer = sr.Recognizer()
        recognizer.energy_threshold = 300
        recognizer.dynamic_energy_threshold = True

        with sr.Microphone() as source:
            recognizer.adjust_for_ambient_noise(source, duration=0.5)
            audio = recognizer.listen(source, timeout=timeout, phrase_time_limit=phrase_limit)

        try:
            return recognizer.recognize_google(audio)
        except sr.UnknownValueError:
            return ""
        except Exception:
            return ""
    except Exception as e:
        _logger.error(f"STT error: {e}")
        return ""


# ──────────────────────────────────────────────────────────────────────
# MARVOVOICE WRAPPER CLASS
# ──────────────────────────────────────────────────────────────────────
class MarvoVoice:
    """Wrapper class providing voice selection, base64 audio generation, and listening."""
    VOICES = VOICES
    VOICE_SAMPLES = VOICE_SAMPLES
    generate_audio_base64 = staticmethod(generate_audio_base64)
    get_sample_audio_base64 = staticmethod(get_sample_audio_base64)
    speak = staticmethod(speak)
    play_sample = staticmethod(play_sample)
    listen = staticmethod(listen)


marvo_voice = MarvoVoice()
