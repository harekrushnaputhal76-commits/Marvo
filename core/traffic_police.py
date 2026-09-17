"""
Marvo Traffic Police Central Router — traffic_police.py
========================================================
Central authoritative router connecting:
- C5: Intent and Routing Classifier (core/classifier.py)
- C7: Offline-First Node 1 (core/offline_router.py)
- C8/C9: Online Escalation Node 2 + Key Pool Failover Node 3 (core/key_pool.py & core/online_router.py)

Guarantees & Invariants:
1. Unified Public Entry Point: All application call sites (server.py, agents, brain.py)
   funnel through `route_traffic()`, eliminating all bypass code paths.
2. Strict C4 Contract Conformance: ALWAYS returns an authoritative `RouterResponse`
   conforming to docs/traffic-police-response-contract.md.
3. Catastrophic Fail-Safe Boundary: If every online key fails AND the offline engine
   itself fails (worst-case scenario), returns a structured ErrorCode.INTERNAL_ERROR
   ResponseError rather than crashing or disguising the failure as canned text.
"""

import logging
import concurrent.futures
from typing import Optional, Dict, Any

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
from core.offline_router import (
    route_offline,
)
from core.key_pool import (
    route_with_failover,
    get_default_key_pool,
    KeyPool,
)
from core.online_router import (
    DEFAULT_ONLINE_TIMEOUT_SECONDS,
    DEFAULT_ONLINE_MODEL,
)

_logger = logging.getLogger("marvo.routing.traffic_police")

# Dedicated thread pool for async execution
_TRAFFIC_POLICE_EXECUTOR = concurrent.futures.ThreadPoolExecutor(
    max_workers=4,
    thread_name_prefix="marvo-traffic-worker",
)


def route_traffic(
    query: str,
    session_id: str = "default",
    thinking_mode: str = "medium",
    timeout_seconds: float = DEFAULT_ONLINE_TIMEOUT_SECONDS,
    model: str = DEFAULT_ONLINE_MODEL,
    pool: Optional[KeyPool] = None,
    image_base64: Optional[str] = None,
) -> RouterResponse:
    """
    Master Traffic Police entry point for Marvo AI.

    Cascade Flow:
    1. Validation: Clean query. If empty, return structured C4 validation error.
    2. C5 Classifier: Evaluates heuristics in microseconds with zero network calls.
       Produces RouteCategory (LOCAL vs ESCALATE) and ResponseIntentType (ACTION, INFO, CONVERSATION).
    3. RouteCategory.LOCAL:
       - Dispatches to Node 1 (core/offline_router.py).
       - If offline match found: returns substantive offline answer or action payload.
       - If offline KB misses (not found locally): escalates to Node 3 / Key Pool for cloud answer.
    4. RouteCategory.ESCALATE:
       - Dispatches directly to Node 3 Key Pool with automatic key rotation and failover.
       - If all online keys fail or network is down: gracefully falls back to Node 1 offline engine.
    5. Catastrophic Worst-Case Boundary:
       - If online fails AND offline fails: returns structured C4 error (500) without crashing
         or returning canned apology strings.
    """
    clean_query = (query or "").strip()

    # Step 1: Input Validation
    if not clean_query:
        return RouterResponse(
            success=False,
            response_text="",
            source=ResponseSource.OFFLINE,
            intent_type=ResponseIntentType.NONE,
            provider="traffic_police",
            model="validator",
            is_fallback=False,
            fallback_occurred=False,
            intent_payload=None,
            error=ResponseError(
                code=ErrorCode.INTERNAL_ERROR,
                message="Cannot route empty or whitespace query.",
                retryable=False,
                http_status=400,
                details={"query": query, "reason": "empty_input"},
            ),
        )

    classification: Optional[ClassificationResult] = None

    try:
        # Step 2: Intent & Routing Classification (C5)
        classification = classify_intent(clean_query)
        _logger.info(
            f"[Traffic Police] Query: '{clean_query[:50]}' -> "
            f"Route: {classification.route.value}, Intent: {classification.intent_type.value}"
        )

        # Step 3: Branch A — LOCAL Execution (Node 1 Offline-First)
        if classification.route == RouteCategory.LOCAL:
            offline_res = route_offline(clean_query, classification=classification)
            if offline_res.success:
                _logger.info(f"[Traffic Police] Handled locally via Node 1: {clean_query[:50]}")
                return offline_res

            # Local KB miss: escalate to online cascade (Node 3) as fallback
            _logger.info(
                f"[Traffic Police] Local engine returned miss for '{clean_query[:50]}'. "
                f"Escalating to online cascade (Node 3)..."
            )
            online_res = route_with_failover(
                query=clean_query,
                classification=classification,
                timeout_seconds=timeout_seconds,
                thinking_mode=thinking_mode,
                session_id=session_id,
                model=model,
                pool=pool,
            )
            return online_res

        # Step 4: Branch B — ESCALATE Execution (Node 2 / Node 3 Key Pool)
        online_res = route_with_failover(
            query=clean_query,
            classification=classification,
            timeout_seconds=timeout_seconds,
            thinking_mode=thinking_mode,
            session_id=session_id,
            model=model,
            pool=pool,
        )
        return online_res

    except Exception as catastrophic_err:
        # Step 5: Catastrophic Worst-Case Fail-Safe Boundary
        # Ensures that even under complete hardware/filesystem/network collapse,
        # the response ALWAYS adheres to the C4 contract without crashing or disguised strings.
        _logger.critical(
            f"[Traffic Police] Catastrophic routing failure: {catastrophic_err}",
            exc_info=True,
        )
        intent = classification.intent_type if classification else ResponseIntentType.CONVERSATION
        return RouterResponse(
            success=False,
            response_text="",
            source=ResponseSource.OFFLINE,
            intent_type=intent,
            provider="traffic_police",
            model="fail_safe",
            is_fallback=True,
            fallback_occurred=True,
            intent_payload=None,
            error=ResponseError(
                code=ErrorCode.INTERNAL_ERROR,
                message=f"Catastrophic failure in routing cascade: {catastrophic_err}",
                retryable=True,
                http_status=500,
                details={
                    "catastrophic": True,
                    "exception_type": type(catastrophic_err).__name__,
                    "error": str(catastrophic_err),
                },
            ),
        )


def route_traffic_async(
    query: str,
    session_id: str = "default",
    thinking_mode: str = "medium",
    timeout_seconds: float = DEFAULT_ONLINE_TIMEOUT_SECONDS,
    model: str = DEFAULT_ONLINE_MODEL,
    pool: Optional[KeyPool] = None,
    image_base64: Optional[str] = None,
) -> concurrent.futures.Future:
    """
    Non-blocking asynchronous Traffic Police routing returning a Future.
    """
    return _TRAFFIC_POLICE_EXECUTOR.submit(
        route_traffic,
        query=query,
        session_id=session_id,
        thinking_mode=thinking_mode,
        timeout_seconds=timeout_seconds,
        model=model,
        pool=pool,
        image_base64=image_base64,
    )
