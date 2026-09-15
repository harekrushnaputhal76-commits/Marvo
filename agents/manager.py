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

# Agent Persona System Directives (Authentic AI Powerhouses)
PERSONA_INSTRUCTIONS = {
    "marvo": (
        "You are Marvo, a highly intelligent, polite, and professional personal AI assistant. "
        "Provide highly precise, concise, and professional answers. No extra chatting, rambling, or nonsense. "
        "Address the user respectfully as 'Boss' or 'Sir'. "
        "CRITICAL PRIVACY RULE: Treat this session as a completely fresh, neutral conversation with zero assumptions about who the user is. "
        "Never bring up personal facts, user names, or personal locations unprompted. "
        "Only if explicitly asked about user identity, they are Harekrushna Puthal (Guddu, Boss) in Talakia, Oupada, Balasore, Odisha, India."
    ),
    "gemini": (
        "You are Marvo powered by Gemini. You are a highly intelligent, polite, and professional personal AI assistant. "
        "Provide highly precise, concise, and professional answers. Address the user respectfully as 'Boss' or 'Sir'."
    ),
    "student": (
        "You are an academic tutor. The user is a Class 12 Higher Secondary Science student (Physics, Chemistry, Mathematics, Biology) "
        "under the CHSE Odisha board. Only discuss studies, solve problems concisely, and refuse non-academic banter. "
        "Address the user respectfully as Sir."
    ),
    "claude": (
        "You are Claude, Anthropic's state-of-the-art coding and logic assistant integrated into Marvo. "
        "You are polite, precise, and highly capable across software engineering and deep reasoning."
    ),
    "huggingface": (
        "You are Hugging Face Pro Visual Specialist. "
        "You craft professional photography prompts, high-resolution aesthetic styling, and hyper-detailed SDXL / Flux visuals."
    ),
    "pollinations": (
        "You are Pollinations Fast Image Specialist. "
        "You provide instant image generation, rapid concept sketching, and dynamic visual ideation."
    ),
    # Backward compatibility aliases
    "aura": (
        "You are Marvo, a polite and helpful assistant powered by Gemini intelligence."
    ),
    "nexus": (
        "You are Claude, Anthropic's coding and logic specialist integrated into Marvo."
    ),
    "lumina": (
        "You are Hugging Face Pro Visual Specialist."
    ),
    "orion": (
        "You are Gemini Deep Research Specialist."
    ),
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
    mode: str = None,
    session_id: str = "default",
    local_time: str = None,
    agent: str = None
) -> Dict[str, Any]:
    """
    Master entry point for processing incoming messages.
    Inspects user intent and routes to the appropriate specialized agent.
    Accepts mode ('Fast', 'Thinking', 'Pro') and passes to image_agent.
    """
    clean_message = (message or "").strip()
    agent_key = (agent or "").strip().lower()

    # Determine effective mode (Fast, Thinking, Pro)
    raw_mode = (mode or thinking_mode or "Thinking").strip()
    if raw_mode.lower() == "fast":
        effective_mode = "Fast"
    elif raw_mode.lower() in ["pro", "high"]:
        effective_mode = "Pro"
    else:
        effective_mode = "Thinking"

    # If the user specifically targeted an image persona, force matching mode
    if agent_key == "pollinations":
        effective_mode = "Fast"
    elif agent_key == "huggingface":
        effective_mode = "Pro"

    # 1. Aggressive Intent Detection: Image Generation
    if is_image_intent(clean_message) or agent_key in ["huggingface", "pollinations"]:
        logger.info(f"[AgentManager] Routing prompt to ImageAgent: {clean_message[:60]} (Mode: {effective_mode})")
        image_result = generate_image(clean_message, mode=effective_mode)
        image_result["session_id"] = session_id
        return image_result

    # 2. Intent Detection: Conversational / Reasoning Query
    logger.info(f"[AgentManager] Routing prompt to ChatAgent: {clean_message[:60]} (Agent: {agent_key}, Mode: {effective_mode})")
    prompt_for_chat = clean_message

    # Inject Persona context if provided and not already overridden by Project
    persona_text = PERSONA_INSTRUCTIONS.get(agent_key, "")

    prefix_parts = []
    if persona_text and "[System Instructions / Persona" not in prompt_for_chat:
        prefix_parts.append(f"[{persona_text}]")

    if local_time and "[Device Context:" not in prompt_for_chat and "[User Local Time:" not in prompt_for_chat:
        prefix_parts.append(f"[Device Context: User's local time is {local_time}]")

    if prefix_parts:
        prompt_for_chat = f"{''.join(prefix_parts)}\n\n{clean_message}"

    chat_result = generate_chat_response(
        prompt=prompt_for_chat,
        thinking_mode=effective_mode.lower(),
        session_id=session_id
    )
    return chat_result
