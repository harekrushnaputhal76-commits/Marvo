"""
Marvo Online Router (Node 2) — online_router.py
================================================
Second-stage online escalation router in the Marvo Traffic Police routing cascade.
Handles ESCALATE-classified queries via cloud AI integrations (Google Gemini).

Architecture Guarantees:
- Non-blocking execution: offloaded via bounded ThreadPoolExecutor consistent with
  the project's existing threading model in core/voice.py and server/server.py.
- Explicit request timeout: governed by DEFAULT_ONLINE_TIMEOUT_SECONDS (12.0s).
- Contextual intent preservation: attaches classifier's intent_type from C5 so it
  survives round-trip and returns attached to RouterResponse per C4 contract.
- Single responsibility: returns structured C4 error on failure; does NOT fallback
  to offline itself (fallback orchestration is strictly Node 3 / Traffic Police).
"""

import os
import re
import logging
import concurrent.futures
from dataclasses import dataclass
from pathlib import Path
from typing import Optional, Dict, Any, Tuple, Callable

from core.response_schema import (
    ResponseSource,
    ResponseIntentType,
    ErrorCode,
    ResponseError,
    IntentPayload,
    RouterResponse,
)
from core.classifier import (
    classify_intent,
    ClassificationResult,
    RouteCategory,
)

_logger = logging.getLogger("marvo.routing.online")

# ─────────────────────────────────────────────────────────────────────────────
# 1. NAMED CONSTANTS & CONCURRENCY CONFIGURATION
# ─────────────────────────────────────────────────────────────────────────────

# Explicit timeout for online model calls (prevents unbounded network hangs)
DEFAULT_ONLINE_TIMEOUT_SECONDS: float = 12.0

# Default cloud model identifier for online escalation
DEFAULT_ONLINE_MODEL: str = "gemini-flash-latest"

# Fallback cascade models if primary model is unavailable
ONLINE_MODEL_CASCADE = [
    "gemini-flash-latest",
    "gemini-flash-lite-latest",
    "gemini-3.6-flash",
]

# Dedicated bounded thread pool for non-blocking online requests
# Consistent with concurrent.futures pattern in core/voice.py and server/server.py
_ONLINE_EXECUTOR = concurrent.futures.ThreadPoolExecutor(
    max_workers=4,
    thread_name_prefix="marvo-online-worker",
)


# ─────────────────────────────────────────────────────────────────────────────
# 2. REQUEST CONTEXT DATA CONTAINER
# ─────────────────────────────────────────────────────────────────────────────

@dataclass(frozen=True)
class OnlineRequestContext:
    """
    Context container carrying user query, parameters, and C5 intent_type
    through the entire online round-trip.
    """
    query: str
    intent_type: ResponseIntentType
    thinking_mode: str = "medium"
    session_id: str = "default"
    timeout_seconds: float = DEFAULT_ONLINE_TIMEOUT_SECONDS
    provider: str = "gemini"
    model: str = DEFAULT_ONLINE_MODEL


# ─────────────────────────────────────────────────────────────────────────────
# 3. ERROR CATEGORIZATION UTILITY
# ─────────────────────────────────────────────────────────────────────────────

def categorize_online_exception(
    exc: Exception,
    provider: str = "gemini",
    model: str = DEFAULT_ONLINE_MODEL,
) -> ResponseError:
    """
    Map raw SDK / HTTP / Socket exceptions into C4 structured ResponseError objects.
    """
    err_msg = str(exc)
    err_lower = err_msg.lower()

    if isinstance(exc, concurrent.futures.TimeoutError) or "timeout" in err_lower or "timed out" in err_lower:
        return ResponseError(
            code=ErrorCode.TIMEOUT,
            message=f"Online model request to {provider} ({model}) timed out.",
            retryable=True,
            http_status=504,
            details={"provider": provider, "model": model, "error_type": "TimeoutError"},
        )

    if "429" in err_msg or "resourceexhausted" in err_lower or "quota" in err_lower or "rate limit" in err_lower:
        return ResponseError(
            code=ErrorCode.RATE_LIMITED,
            message=f"API quota exceeded or rate limited on {provider} ({model}).",
            retryable=True,
            http_status=429,
            details={"provider": provider, "model": model, "raw_error": err_msg[:120]},
        )

    if "401" in err_msg or "403" in err_msg or "api_key_invalid" in err_lower or "permissiondenied" in err_lower:
        return ResponseError(
            code=ErrorCode.AUTH_FAILED,
            message=f"Authentication failed for {provider}. Check API key configuration.",
            retryable=False,
            http_status=401,
            details={"provider": provider, "model": model},
        )

    if "404" in err_msg or "notfound" in err_lower or "model not found" in err_lower:
        return ResponseError(
            code=ErrorCode.MODEL_NOT_FOUND,
            message=f"Requested model '{model}' not found on provider '{provider}'.",
            retryable=False,
            http_status=404,
            details={"provider": provider, "model": model},
        )

    if (
        "connectionerror" in err_lower
        or "failed to establish a new connection" in err_lower
        or "name or service not known" in err_lower
        or "getaddrinfo failed" in err_lower
        or "network is unreachable" in err_lower
    ):
        return ResponseError(
            code=ErrorCode.NETWORK_OFFLINE,
            message="Device appears offline or target API endpoint is unreachable.",
            retryable=True,
            http_status=503,
            details={"provider": provider, "model": model, "raw_error": err_msg[:120]},
        )

    return ResponseError(
        code=ErrorCode.INTERNAL_ERROR,
        message=f"Online model invocation failed: {err_msg[:160]}",
        retryable=False,
        http_status=500,
        details={"provider": provider, "model": model, "exception": type(exc).__name__},
    )


# ─────────────────────────────────────────────────────────────────────────────
# 4. INTERNAL SYNCHRONOUS INVOCATION WORKER
# ─────────────────────────────────────────────────────────────────────────────

def _invoke_gemini_client(
    context: OnlineRequestContext,
    client: Any,
) -> str:
    """
    Direct synchronous invocation of Google GenAI SDK Client within worker thread.
    """
    try:
        from google.genai import types

        config = types.GenerateContentConfig(
            temperature=0.7 if context.thinking_mode == "fast" else 0.8,
            max_output_tokens=256 if context.thinking_mode == "fast" else 512,
        )
        response = client.models.generate_content(
            model=context.model,
            contents=context.query,
            config=config,
        )
        if response and response.text:
            return response.text.strip()
        return ""
    except Exception as exc:
        _logger.warning(f"[Node 2 Online] SDK invocation error: {exc}")
        raise exc


# ─────────────────────────────────────────────────────────────────────────────
# 5. PUBLIC NON-BLOCKING ONLINE ROUTER (NODE 2)
# ─────────────────────────────────────────────────────────────────────────────

def route_online(
    query: str,
    classification: Optional[ClassificationResult] = None,
    timeout_seconds: float = DEFAULT_ONLINE_TIMEOUT_SECONDS,
    thinking_mode: str = "medium",
    session_id: str = "default",
    api_key: Optional[str] = None,
    client: Optional[Any] = None,
    model: str = DEFAULT_ONLINE_MODEL,
) -> RouterResponse:
    """
    Execute the online escalation path (Node 2) for a query.

    Non-blocking by design: offloaded to worker thread with an explicit timeout.
    Preserves intent_type from C5 classification and adheres strictly to C4.
    Does NOT perform fallback-to-offline on failure (Node 3 responsibility).

    Args:
        query: User query string.
        classification: Optional pre-computed C5 classification result.
        timeout_seconds: Maximum seconds before aborting with ErrorCode.TIMEOUT.
        thinking_mode: "fast" | "medium" | "high" model reasoning mode.
        session_id: Session identifier for context tracking.
        api_key: Optional explicit API key override (e.g. for key-pool rotation in C9).
        client: Optional pre-initialized GenAI client (for dependency injection/testing).
        model: Target model identifier.

    Returns:
        RouterResponse: Authoritative C4 response with:
            - success: True on substantive online generation; False on failure
            - source: "online"
            - intent_type: canonical intent type from C5 classification
            - error: ResponseError on failure; None on success
    """
    # 1. Obtain classification and attach context
    if classification is None:
        classification = classify_intent(query)

    context = OnlineRequestContext(
        query=query,
        intent_type=classification.intent_type,
        thinking_mode=thinking_mode,
        session_id=session_id,
        timeout_seconds=timeout_seconds,
        provider="gemini",
        model=model,
    )

    # 2. Validation: Empty or whitespace query
    if not query or not query.strip():
        return RouterResponse(
            success=False,
            response_text="",
            source=ResponseSource.ONLINE,
            intent_type=ResponseIntentType.NONE,
            provider="gemini",
            model=model,
            is_fallback=False,
            intent_payload=None,
            error=ResponseError(
                code=ErrorCode.INTERNAL_ERROR,
                message="Cannot execute online escalation for empty or whitespace query.",
                retryable=False,
                details={"query": query, "reason": "empty_input"},
            ),
        )

    # 3. Client Initialization & Authentication Check
    genai_client = client
    if genai_client is None:
        effective_key = api_key or os.environ.get("GEMINI_API_KEY")
        if not effective_key:
            _logger.warning("[Node 2 Online] Authentication failed: GEMINI_API_KEY missing.")
            return RouterResponse(
                success=False,
                response_text="",
                source=ResponseSource.ONLINE,
                intent_type=context.intent_type,
                provider="gemini",
                model=model,
                is_fallback=False,
                intent_payload=None,
                error=ResponseError(
                    code=ErrorCode.AUTH_FAILED,
                    message="No Gemini API key configured in environment or request.",
                    retryable=False,
                    http_status=401,
                    details={"provider": "gemini", "missing_variable": "GEMINI_API_KEY"},
                ),
            )
        try:
            from google import genai
            genai_client = genai.Client(api_key=effective_key)
        except Exception as auth_err:
            _logger.error(f"[Node 2 Online] Client initialization error: {auth_err}")
            return RouterResponse(
                success=False,
                response_text="",
                source=ResponseSource.ONLINE,
                intent_type=context.intent_type,
                provider="gemini",
                model=model,
                is_fallback=False,
                intent_payload=None,
                error=categorize_online_exception(auth_err, provider="gemini", model=model),
            )

    # 4. Offload to Bounded Worker Thread with Explicit Timeout
    future = _ONLINE_EXECUTOR.submit(_invoke_gemini_client, context, genai_client)

    try:
        reply_text = future.result(timeout=timeout_seconds)

        if not reply_text:
            return RouterResponse(
                success=False,
                response_text="",
                source=ResponseSource.ONLINE,
                intent_type=context.intent_type,
                provider="gemini",
                model=model,
                is_fallback=False,
                intent_payload=None,
                error=ResponseError(
                    code=ErrorCode.INTERNAL_ERROR,
                    message="Online model completed but returned an empty response.",
                    retryable=True,
                    http_status=500,
                    details={"query": query, "provider": "gemini", "model": model},
                ),
            )

        # Formulate IntentPayload if applicable
        intent_payload = None
        if context.intent_type == ResponseIntentType.INFO:
            intent_payload = IntentPayload(
                icon="🌐",
                title=query.strip().capitalize()[:30],
                body=reply_text,
                source="online_gemini",
                response_text=reply_text,
            )
        elif context.intent_type == ResponseIntentType.ACTION:
            intent_payload = IntentPayload(
                icon="⚡",
                title="Action Result",
                action_type="APP",
                status="Completed",
                response_text=reply_text,
            )

        return RouterResponse(
            success=True,
            response_text=reply_text,
            source=ResponseSource.ONLINE,
            intent_type=context.intent_type,
            provider="gemini",
            model=model,
            is_fallback=False,
            intent_payload=intent_payload,
            error=None,
        )

    except concurrent.futures.TimeoutError as timeout_err:
        _logger.error(f"[Node 2 Online] Request timed out after {timeout_seconds}s for query: {query[:50]}")
        return RouterResponse(
            success=False,
            response_text="",
            source=ResponseSource.ONLINE,
            intent_type=context.intent_type,
            provider="gemini",
            model=model,
            is_fallback=False,
            intent_payload=None,
            error=ResponseError(
                code=ErrorCode.TIMEOUT,
                message=f"Online model request timed out after {timeout_seconds} seconds.",
                retryable=True,
                http_status=504,
                details={"timeout_seconds": timeout_seconds, "provider": "gemini", "model": model},
            ),
        )

    except Exception as exc:
        _logger.error(f"[Node 2 Online] Online model execution failed: {exc}")
        return RouterResponse(
            success=False,
            response_text="",
            source=ResponseSource.ONLINE,
            intent_type=context.intent_type,
            provider="gemini",
            model=model,
            is_fallback=False,
            intent_payload=None,
            error=categorize_online_exception(exc, provider="gemini", model=model),
        )


def route_online_async(
    query: str,
    classification: Optional[ClassificationResult] = None,
    timeout_seconds: float = DEFAULT_ONLINE_TIMEOUT_SECONDS,
    thinking_mode: str = "medium",
    session_id: str = "default",
    api_key: Optional[str] = None,
    client: Optional[Any] = None,
    model: str = DEFAULT_ONLINE_MODEL,
) -> concurrent.futures.Future:
    """
    Non-blocking asynchronous invocation returning a concurrent.futures.Future.
    Allows event-driven callers or async loops to await the RouterResponse.
    """
    return _ONLINE_EXECUTOR.submit(
        route_online,
        query=query,
        classification=classification,
        timeout_seconds=timeout_seconds,
        thinking_mode=thinking_mode,
        session_id=session_id,
        api_key=api_key,
        client=client,
        model=model,
    )
