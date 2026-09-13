"""
Marvo AI — Agent Manager & Master Router
========================================
Acts as the central orchestrator:
- Intercepts incoming user prompts and performs aggressive image intent detection.
- Checks against: ["generate", "image", "draw", "photo", "picture", "creat", "create", "pic", "paint", "thumbnail"].
- If ANY of these words exist in the prompt, routes directly to agents.image_agent.
- Routes conversational queries to chat_agent.
- Supports creative personas: Lumina (Vision), Nexus (Code), Orion (Research), Aura (General).
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
        "Focus on visual aesthetics, composition, lighting, color palettes, and creative imagery."
    ),
    "nexus": (
        "You are Nexus, Marvo's Code & Logic Specialist. "
        "You write concise, production-grade, highly optimized code. "
        "Prioritize modern patterns, strict typing, error handling, and architectural clarity."
    ),
    "orion": (
        "You are Orion, Marvo's Deep Research Specialist. "
        "You provide rigorous, deeply researched, and analytical answers. "
        "Structure your reasoning systematically with facts and comprehensive domain depth."
    ),
    "aura": (
        "You are Aura, Marvo's Flagship General Assistant. "
        "You are charismatic, witty, articulate, empathetic, and exceptionally versatile across all tasks."
    )
}

# Explicit user-required trigger words (including typos/stems like 'creat' and 'pic')
IMAGE_TRIGGER_WORDS = [
    "generate",
    "image",
    "draw",
    "photo",
    "picture",
    "creat",
    "create",
    "pic",
    "paint",
    "thumbnail"
]

# Additional regional & contextual phrases
_ADDITIONAL_IMAGE_PATTERNS = [
    re.compile(r"\b(?:wallpaper|sketch|illustration|portrait|artwork|poster)\b", re.IGNORECASE),
    re.compile(r"\b(?:tasveer\s+banao|photo\s+banao|drawing\s+banao|chitra\s+banao|image\s+banao)\b", re.IGNORECASE),
]


def is_image_intent(prompt: str) -> bool:
    """
    Extremely aggressive image intent routing.
    If ANY of ['generate', 'image', 'draw', 'photo', 'picture', 'creat', 'create', 'pic', 'paint', 'thumbnail']
    exist anywhere in the user prompt, immediately return True.
    """
    if not prompt or not isinstance(prompt, str):
        return False

    clean = prompt.strip().lower()

    # 1. Exact match against user's required trigger word list
    for word in IMAGE_TRIGGER_WORDS:
        # Check both word boundary and substring match (catches typos like 'creat', 'create', 'pic', etc.)
        if word in clean:
            logger.info(f"[AgentManager] Aggressive image trigger matched: '{word}' in prompt: {clean[:50]}")
            return True

    # 2. Regional / auxiliary visual patterns
    for pattern in _ADDITIONAL_IMAGE_PATTERNS:
        if pattern.search(clean):
            logger.info(f"[AgentManager] Visual pattern matched in prompt: {clean[:50]}")
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
    """
    clean_message = (message or "").strip()

    # 1. Aggressive Intent Detection: Image Generation
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
