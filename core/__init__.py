"""
Marvo Core Package — __init__.py
================================
Exposes the brain and voice modules so external files (server.py, etc.)
can import cleanly:
    from core import think_and_respond, speak, listen
    from core.brain import think_and_respond
    from core.voice import speak
"""

# ── Brain: AI reasoning engine ──────────────────────────────────────
from core.brain import think_and_respond

# ── Voice: Offline TTS and STT stubs ────────────────────────────────
from core.voice import speak, listen, set_voice_rate, set_voice_volume, marvo_voice

# Package-level metadata
__version__ = "1.0.0"
__author__  = "Marvo AI"
__all__     = [
    "think_and_respond",
    "speak",
    "listen",
    "set_voice_rate",
    "set_voice_volume",
    "marvo_voice",
]

