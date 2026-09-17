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

# ── Classifier: Fast heuristic intent & routing classifier ─────────
from core.classifier import (
    classify_intent,
    route_query,
    RouteCategory,
    ResponseIntentType,
    ClassificationResult,
)

# ── Schema: Authoritative C4 response and error schemas ───────────
from core.response_schema import (
    ResponseSource,
    ResponseIntentType,
    ErrorCode,
    ResponseError,
    IntentPayload,
    RouterResponse,
)

# ── Router: Offline-first router (Node 1) ──────────────────────────
from core.offline_router import (
    route_offline,
    query_local_knowledge_base,
)

# ── Router: Online escalation router (Node 2) ──────────────────────
from core.online_router import (
    route_online,
    route_online_async,
    DEFAULT_ONLINE_TIMEOUT_SECONDS,
    DEFAULT_ONLINE_MODEL,
    OnlineRequestContext,
    categorize_online_exception,
)

# ── Router: Key Pool & Failover Layer (Node 3) ─────────────────────
from core.key_pool import (
    KeyPool,
    KeySlot,
    route_with_failover,
    route_with_failover_async,
    get_default_key_pool,
    load_gemini_keys_from_env,
    DEFAULT_RATE_LIMIT_COOLDOWN_SECONDS,
)

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
    "classify_intent",
    "route_query",
    "RouteCategory",
    "ResponseIntentType",
    "ClassificationResult",
    "ResponseSource",
    "ErrorCode",
    "ResponseError",
    "IntentPayload",
    "RouterResponse",
    "route_offline",
    "query_local_knowledge_base",
    "route_online",
    "route_online_async",
    "DEFAULT_ONLINE_TIMEOUT_SECONDS",
    "DEFAULT_ONLINE_MODEL",
    "OnlineRequestContext",
    "categorize_online_exception",
    "KeyPool",
    "KeySlot",
    "route_with_failover",
    "route_with_failover_async",
    "get_default_key_pool",
    "load_gemini_keys_from_env",
    "DEFAULT_RATE_LIMIT_COOLDOWN_SECONDS",
]

