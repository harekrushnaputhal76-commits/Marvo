"""
Marvo AI — Agents Package
==========================
Modular agent architecture coordinating specialized AI agents:
- manager: Master router detecting user intent and routing tasks.
- chat_agent: Core reasoning and conversational text intelligence.
- image_agent: Dual-stage image generation pipeline (Hugging Face + Pollinations fallback).
"""

from .manager import handle_request, is_image_intent
from .chat_agent import generate_chat_response
from .image_agent import generate_image

__all__ = [
    "handle_request",
    "is_image_intent",
    "generate_chat_response",
    "generate_image",
]

