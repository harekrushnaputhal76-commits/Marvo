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
    from core.traffic_police import route_traffic
except Exception as err:
    logger.error(f"Failed to import core.traffic_police in chat_agent: {err}", exc_info=True)
    def route_traffic(prompt: str, **kwargs):
        from core.response_schema import RouterResponse, ResponseSource, ResponseIntentType, ErrorCode, ResponseError
        return RouterResponse(
            success=False,
            response_text="",
            source=ResponseSource.OFFLINE,
            intent_type=ResponseIntentType.NONE,
            error=ResponseError(
                code=ErrorCode.INTERNAL_ERROR,
                message="Traffic Police module unreachable.",
                retryable=True,
            ),
        )


def generate_chat_response(
    prompt: str,
    thinking_mode: str = "medium",
    session_id: str = "default",
    image_base64: str = None
) -> Dict[str, Any]:
    """
    Executes reasoning and text generation for user queries via Traffic Police.
    Eliminates silent failure swallowing and canned stand-in strings.
    Returns:
        dict: Full C4 schema response dictionary + {"type": "text", "session_id": session_id}.
    """
    res = route_traffic(
        query=prompt,
        thinking_mode=thinking_mode,
        session_id=session_id,
        image_base64=image_base64,
    )

    out = res.to_dict()
    out["type"] = "text"
    out["session_id"] = session_id
    return out

