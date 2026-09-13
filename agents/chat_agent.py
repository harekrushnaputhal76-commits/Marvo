"""
Marvo AI — Chat & Reasoning Agent
==================================
Handles LLM conversation, multi-modal contextual reasoning, thinking modes,
and personality orchestration using Google Gemini and offline fallback.
"""

import logging
from typing import Tuple, Dict, Any

logger = logging.getLogger("marvo.agents.chat")

try:
    from core.brain import think_and_respond
except Exception as err:
    logger.error(f"Failed to import core.brain in chat_agent: {err}", exc_info=True)
    def think_and_respond(prompt: str, thinking_mode: str = 'medium', session_id: str = 'default') -> Tuple[str, str]:
        return "I couldn't access my reasoning core right now.", "state-idle"


def generate_chat_response(
    prompt: str,
    thinking_mode: str = "medium",
    session_id: str = "default"
) -> Dict[str, Any]:
    """
    Executes reasoning and text generation for user queries.
    Returns:
        dict: {
            "type": "text",
            "response": "<ai_text>",
            "state": "<animation_state>",
            "session_id": "<session_id>"
        }
    """
    try:
        response_text, state = think_and_respond(
            prompt,
            thinking_mode=thinking_mode,
            session_id=session_id
        )
        return {
            "type": "text",
            "response": response_text,
            "state": state or "state-speaking",
            "session_id": session_id
        }
    except Exception as e:
        logger.error(f"[ChatAgent] Error generating chat response: {e}", exc_info=True)
        return {
            "type": "text",
            "response": "I encountered a hiccup while thinking through that. Let's try again.",
            "state": "state-error",
            "session_id": session_id
        }

