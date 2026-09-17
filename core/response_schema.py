"""
Marvo Traffic Police Response Schemas — response_schema.py
===========================================================
Authoritative typed dataclasses and enums representing the single source of truth
for backend router responses, error representations, and intent payloads.

Conforms strictly to docs/traffic-police-response-contract.md (C4).
"""

from dataclasses import dataclass, field
from enum import Enum
from typing import Optional, Dict, Any, List


class ResponseSource(str, Enum):
    """Execution source of the response."""
    ONLINE = "online"
    OFFLINE = "offline"


class ResponseIntentType(str, Enum):
    """
    Canonical intent types establishing UI presentation contracts (Group B & C4).
    Must match exact uppercase enum values: ACTION, INFO, CONVERSATION, NONE.
    """
    ACTION = "ACTION"
    INFO = "INFO"
    CONVERSATION = "CONVERSATION"
    NONE = "NONE"


class ErrorCode(str, Enum):
    """Standardized failure taxonomy for router decision making and failover."""
    RATE_LIMITED = "RATE_LIMITED"
    TIMEOUT = "TIMEOUT"
    NETWORK_OFFLINE = "NETWORK_OFFLINE"
    AUTH_FAILED = "AUTH_FAILED"
    MODEL_NOT_FOUND = "MODEL_NOT_FOUND"
    INTERNAL_ERROR = "INTERNAL_ERROR"


@dataclass
class ResponseError:
    """
    Structured error representation returned when a model or local engine fails.
    Prevents silent failure anti-patterns and canned apology strings.
    """
    code: ErrorCode
    message: str
    retryable: bool = False
    http_status: Optional[int] = None
    details: Optional[Dict[str, Any]] = None

    def to_dict(self) -> Dict[str, Any]:
        """Convert error representation to a JSON-serializable dictionary."""
        return {
            "code": self.code.value if isinstance(self.code, Enum) else self.code,
            "message": self.message,
            "retryable": self.retryable,
            "http_status": self.http_status,
            "details": self.details or {},
        }


@dataclass
class IntentPayload:
    """
    Structured data payload consumed by Group B intent cards in the frontend.
    """
    icon: Optional[str] = None
    title: Optional[str] = None
    target: Optional[str] = None
    action_type: Optional[str] = None
    status: Optional[str] = None
    body: Optional[str] = None
    source: Optional[str] = None
    data_points: Optional[List[Dict[str, str]]] = None
    response_text: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        """Convert intent payload to a dictionary excluding None fields."""
        return {k: v for k, v in self.__dict__.items() if v is not None}


@dataclass
class RouterResponse:
    """
    Central response object produced by every execution path through the router.
    Strictly distinguishes genuine generation from failure conditions.
    """
    success: bool
    response_text: str
    source: ResponseSource
    intent_type: ResponseIntentType = ResponseIntentType.CONVERSATION
    provider: str = "knowledge_base"
    model: str = "custom_qa"
    is_fallback: bool = False
    intent_payload: Optional[IntentPayload] = None
    error: Optional[ResponseError] = None

    def to_dict(self) -> Dict[str, Any]:
        """
        Convert to JSON-serializable dictionary matching the C4 contract and
        frontend interface expectations.
        """
        return {
            "success": self.success,
            "response": self.response_text,  # Backwards-compatible alias for existing frontend
            "response_text": self.response_text,
            "source": self.source.value if isinstance(self.source, Enum) else self.source,
            "intent_type": self.intent_type.value if isinstance(self.intent_type, Enum) else self.intent_type,
            "provider": self.provider,
            "model": self.model,
            "is_fallback": self.is_fallback,
            "intent_payload": self.intent_payload.to_dict() if self.intent_payload else None,
            "error": self.error.to_dict() if self.error else None,
            # Voice indicator bridge:
            "state": "state-error" if not self.success else "state-speaking",
        }
