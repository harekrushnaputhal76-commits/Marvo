"""
Marvo AI — Agent Manager & Master Router
========================================
Acts as the central orchestrator:
- Intercepts incoming user prompts and performs semantic intent detection.
- Routes image creation requests to image_agent.
- Routes reasoning and conversational queries to chat_agent.
- Provides unified response formatting.
"""

import re
import logging
from typing import Dict, Any

from .chat_agent import generate_chat_response
from .image_agent import generate_image

logger = logging.getLogger("marvo.agents.manager")

# Compiled regex patterns for detecting image generation intent
_IMAGE_INTENT_PATTERNS = [
    re.compile(r"^\s*(?:please\s+)?(?:generate|create|make|produce)\s+(?:an?\s+)?(?:image|picture|photo|illustration|render|wallpaper)", re.IGNORECASE),
    re.compile(r"^\s*(?:please\s+)?(?:draw|paint|sketch|illustrate)(?:\s+me)?(?:\s+an?)?\s+", re.IGNORECASE),
    re.compile(r"^\s*(?:imagine|visualize)\s+(?:an?\s+)?(?:image\s+of\s+|a\s+|an\s+)?", re.IGNORECASE),
    re.compile(r"^\s*(?:photo|picture|wallpaper|render)\s+of\s+", re.IGNORECASE),
    re.compile(r"\b(?:generate\s+image|draw\s+this|create\s+image|draw\s+an?\s+image)\b", re.IGNORECASE),
    re.compile(r"\b(?:tasveer\s+banao|photo\s+banao|drawing\s+banao|chitra\s+banao)\b", re.IGNORECASE),
]


def is_image_intent(prompt: str) -> bool:
    """
    Returns True if the prompt is asking to generate, draw, or render an image.
    """
    if not prompt or not isinstance(prompt, str):
        return False

    clean = prompt.strip()
    for pattern in _IMAGE_INTENT_PATTERNS:
        if pattern.search(clean):
            return True

    return False


def handle_request(
    message: str,
    thinking_mode: str = "medium",
    session_id: str = "default",
    local_time: str = None
) -> Dict[str, Any]:
    """
    Master entry point for processing incoming messages.
    Inspects user intent and routes to the appropriate specialized agent.
    """
    clean_message = (message or "").strip()

    # 1. Intent Detection: Image Generation
    if is_image_intent(clean_message):
        logger.info(f"[AgentManager] Routing prompt to ImageAgent: {clean_message[:60]}")
        image_result = generate_image(clean_message)
        image_result["session_id"] = session_id
        return image_result

    # 2. Intent Detection: Conversational / Reasoning Query
    logger.info(f"[AgentManager] Routing prompt to ChatAgent: {clean_message[:60]}")
    prompt_for_chat = clean_message
    if local_time and "[Device Context:" not in prompt_for_chat:
        prompt_for_chat = f"[Device Context: User's local time is {local_time}]\n\n{clean_message}"

    chat_result = generate_chat_response(
        prompt=prompt_for_chat,
        thinking_mode=thinking_mode,
        session_id=session_id
    )
    return chat_result
