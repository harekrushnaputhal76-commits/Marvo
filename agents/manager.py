"""
Marvo AI — Agent Manager & Master Router
========================================
Acts as the central orchestrator:
- Intercepts incoming user prompts and performs semantic intent detection.
- Routes image creation requests to image_agent (detecting "generate", "image", "draw", "thumbnail", "photo", etc.).
- Routes reasoning and conversational queries to chat_agent.
- Supports creative personas: Lumina (Vision), Nexus (Code), Orion (Research), Aura (General).
- Provides unified response formatting.
"""

import re
import logging
from typing import Dict, Any

from .chat_agent import generate_chat_response
from .image_agent import generate_image

logger = logging.getLogger("marvo.agents.manager")

# Agent Persona System Directives
PERSONA_INSTRUCTIONS = {
    "lumina": (
        "You are Lumina, Marvo's Vision & Art Specialist. "
        "You embody high visual creativity, artistic flair, and aesthetic mastery. "
        "When describing or suggesting ideas, focus on visual aesthetics, composition, lighting, color palettes, and cinematic wonder."
    ),
    "nexus": (
        "You are Nexus, Marvo's Code & Logic Specialist. "
        "You write concise, production-grade, highly optimized code. "
        "Prioritize modern patterns, strict typing, error handling, algorithmic elegance, and architectural clarity."
    ),
    "orion": (
        "You are Orion, Marvo's Deep Research Specialist. "
        "You provide rigorous, deeply researched, and analytical answers. "
        "Structure your reasoning systematically with facts, structured breakdowns, and comprehensive domain depth."
    ),
    "aura": (
        "You are Aura, Marvo's Flagship General Assistant. "
        "You are charismatic, witty, articulate, empathetic, and exceptionally versatile across all tasks."
    )
}

# Compiled regex patterns for detecting image generation intent
_IMAGE_INTENT_PATTERNS = [
    # Explicit command starts
    re.compile(r"^\s*(?:please\s+)?(?:generate|create|make|produce|render)\s+(?:an?\s+)?(?:image|picture|photo|illustration|render|wallpaper|thumbnail|drawing|artwork|poster)", re.IGNORECASE),
    re.compile(r"^\s*(?:please\s+)?(?:draw|paint|sketch|illustrate|design)(?:\s+me)?(?:\s+an?)?\s+", re.IGNORECASE),
    re.compile(r"^\s*(?:imagine|visualize)\s+(?:an?\s+)?(?:image\s+of\s+|a\s+|an\s+)?", re.IGNORECASE),
    re.compile(r"^\s*(?:photo|picture|wallpaper|render|thumbnail)\s+of\s+", re.IGNORECASE),

    # Keywords inside query
    re.compile(r"\b(?:generate\s+image|draw\s+this|create\s+image|draw\s+an?\s+image|make\s+an?\s+image)\b", re.IGNORECASE),
    re.compile(r"\b(?:thumbnail\s+for|create\s+thumbnail|design\s+thumbnail|youtube\s+thumbnail)\b", re.IGNORECASE),
    re.compile(r"\b(?:photo\s+of|realistic\s+photo|generate\s+photo|create\s+photo)\b", re.IGNORECASE),
    re.compile(r"\b(?:generate\s+a?\s*wallpaper|drawing\s+of|sketch\s+of)\b", re.IGNORECASE),

    # Hindi / Hinglish keywords
    re.compile(r"\b(?:tasveer\s+banao|photo\s+banao|drawing\s+banao|chitra\s+banao|image\s+banao|photo\s+kheecho)\b", re.IGNORECASE),
]


def is_image_intent(prompt: str) -> bool:
    """
    Returns True if the prompt is asking to generate, draw, render an image, thumbnail, or photo.
    """
    if not prompt or not isinstance(prompt, str):
        return False

    clean = prompt.strip().lower()

    # Direct pattern checks
    for pattern in _IMAGE_INTENT_PATTERNS:
        if pattern.search(clean):
            return True

    # Standalone keyword triggers in generative/visual contexts
    if re.search(r"\b(?:thumbnail)\b", clean):
        # Almost all requests containing "thumbnail" in an AI chat are asking for image generation
        return True

    if re.search(r"^(?:draw|sketch|paint)\s+", clean):
        return True

    # If prompt starts with "generate ..." or "create ..." and contains visual nouns
    if re.search(r"^(?:generate|create|make)\b", clean) and re.search(r"\b(?:image|photo|picture|wallpaper|portrait|illustration|avatar|poster)\b", clean):
        return True

    # Check for direct phrase "photo of" or "image of"
    if "photo of" in clean or "image of" in clean or "picture of" in clean:
        return True

    return False


def handle_request(
    message: str,
    thinking_mode: str = "medium",
    session_id: str = "default",
    local_time: str = None,
    agent: str = None
) -> Dict[str, Any]:
    """
    Master entry point for processing incoming messages.
    Inspects user intent and routes to the appropriate specialized agent.
    Supports agent personas (Lumina, Nexus, Orion, Aura).
    """
    clean_message = (message or "").strip()

    # 1. Intent Detection: Image Generation
    if is_image_intent(clean_message):
        logger.info(f"[AgentManager] Routing prompt to ImageAgent: {clean_message[:60]}")
        image_result = generate_image(clean_message)
        image_result["session_id"] = session_id
        return image_result

    # 2. Intent Detection: Conversational / Reasoning Query
    logger.info(f"[AgentManager] Routing prompt to ChatAgent: {clean_message[:60]} (Agent: {agent})")
    prompt_for_chat = clean_message

    # Inject Persona context if provided and not already overridden by Project
    persona_key = (agent or "").strip().lower()
    persona_text = PERSONA_INSTRUCTIONS.get(persona_key, "")

    prefix_parts = []
    if persona_text and "[System Instructions / Persona" not in prompt_for_chat:
        prefix_parts.append(f"[{persona_text}]")

    if local_time and "[Device Context:" not in prompt_for_chat and "[User Local Time:" not in prompt_for_chat:
        prefix_parts.append(f"[Device Context: User's local time is {local_time}]")

    if prefix_parts:
        prompt_for_chat = f"{''.join(prefix_parts)}\n\n{clean_message}"

    chat_result = generate_chat_response(
        prompt=prompt_for_chat,
        thinking_mode=thinking_mode,
        session_id=session_id
    )
    return chat_result
