"""
Marvo Offline Router (Node 1) — offline_router.py
==================================================
First-stage offline-first router in the Marvo Traffic Police routing cascade.
Handles LOCAL-classified queries entirely on-device with ZERO network I/O.

Guarantees:
- Zero network I/O: no HTTP client, socket, or external API is imported or reachable.
- Exact conformance with C4 response contract (docs/traffic-police-response-contract.md).
- Never returns canned apology strings on failure; returns structured ResponseError.
- Sets source: "offline" and propagates intent_type from C5 classification.
"""

import os
import re
import json
import logging
import threading
import concurrent.futures
from pathlib import Path
from typing import Optional, Dict, Any, Tuple

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

_logger = logging.getLogger("marvo.routing.offline")

# Path to the local knowledge base (custom_qa.json)
_project_root = Path(__file__).resolve().parent.parent
DEFAULT_KB_PATH = _project_root / "knowledge_base" / "custom_qa.json"

# In-memory KB cache to prevent blocking file I/O on every request
_KB_CACHE: Dict[str, Tuple[float, Any]] = {}
_KB_CACHE_LOCK = threading.Lock()

# Worker thread pool for asynchronous offline routing
_OFFLINE_EXECUTOR = concurrent.futures.ThreadPoolExecutor(
    max_workers=2,
    thread_name_prefix="marvo-offline-worker",
)


def _load_kb_data(kb_path: Path) -> Any:
    """
    Load knowledge base data from disk with in-memory caching.
    Re-reads from disk only if file modification time (mtime) changes.
    Guarantees thread-safe access without blocking subsequent reads.
    """
    if not kb_path.is_file():
        raise FileNotFoundError(f"Offline knowledge base file not found at: {kb_path}")

    path_str = str(kb_path.resolve())
    mtime = kb_path.stat().st_mtime

    with _KB_CACHE_LOCK:
        if path_str in _KB_CACHE:
            cached_mtime, cached_data = _KB_CACHE[path_str]
            if cached_mtime == mtime:
                return cached_data

    with open(kb_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    with _KB_CACHE_LOCK:
        _KB_CACHE[path_str] = (mtime, data)

    return data


# ─────────────────────────────────────────────────────────────────────────────
# 1. CORE OFFLINE MODEL ENGINE (C3 BUG-FIXED: CLEAN MISS / ERROR SIGNALS)
# ─────────────────────────────────────────────────────────────────────────────

def query_local_knowledge_base(query: str, kb_path: Path = DEFAULT_KB_PATH) -> Optional[str]:
    """
    Search custom_qa.json for a matching local answer using the project's
    existing offline matching algorithm (exact/substring + token overlap).

    C3 Fix: Rather than swallowing exceptions and returning a canned apology
    string, this pure local engine:
      - Returns substantive answer string on a genuine match.
      - Returns None on a clean miss (allowing the router to detect failure).
      - Raises exceptions on I/O or JSON corruption for structured error capture.

    Args:
        query: Raw query string.
        kb_path: Path to custom_qa.json file.

    Returns:
        Optional[str]: Matched substantive text, or None if no match found.
    """
    if not query or not isinstance(query, str):
        return None

    cleaned = re.sub(r"[^\w\s]", "", query.lower()).strip()
    if not cleaned:
        return None

    kb_data = _load_kb_data(kb_path)

    if not isinstance(kb_data, list):
        raise ValueError(f"Corrupt knowledge base format in {kb_path}: expected list of entries")

    # 1. Exact or substring match
    for entry in kb_data:
        q_raw = entry.get("q", "").lower()
        q_clean = re.sub(r"[^\w\s]", "", q_raw).strip()
        if q_clean and (cleaned == q_clean or cleaned in q_clean or q_clean in cleaned):
            ans = entry.get("a", "")
            if ans:
                return ans

    # 2. Token overlap match (filtering stop words to prevent false-positive matches on 'what is', 'who is', etc.)
    query_words = set(cleaned.split())
    stop_words = {
        "what", "is", "are", "a", "an", "the", "who", "where", "when",
        "how", "why", "you", "your", "me", "my", "to", "in", "of", "for",
        "on", "can", "do", "does", "did", "tell", "i", "it", "this", "that",
    }
    content_query_words = query_words - stop_words
    best_match = None
    best_score = 0
    for entry in kb_data:
        q_words = set(re.sub(r"[^\w\s]", "", entry.get("q", "").lower()).split())
        content_q_words = q_words - stop_words
        if content_query_words and content_q_words:
            overlap = len(content_query_words & content_q_words)
            if overlap > best_score and overlap >= 1:
                best_score = overlap
                best_match = entry.get("a", "")

    if best_match and best_score >= 1:
        return best_match

    return None


# ─────────────────────────────────────────────────────────────────────────────
# 2. OFFLINE ROUTING WRAPPER (NODE 1)
# ─────────────────────────────────────────────────────────────────────────────

def route_offline(
    query: str,
    classification: Optional[ClassificationResult] = None,
    kb_path: Path = DEFAULT_KB_PATH,
) -> RouterResponse:
    """
    Execute the offline routing path (Node 1) for a query.

    Evaluates LOCAL queries through local action handlers or the offline
    knowledge base. Adheres strictly to the C4 contract.

    Args:
        query: The user query string.
        classification: Optional pre-computed C5 classification result. If None,
                        classify_intent(query) is computed.
        kb_path: Optional custom Path to custom_qa.json (for testing/mocking).

    Returns:
        RouterResponse: Structured C4 response with:
            - success: bool (True only on genuine local generation)
            - response_text: substantive answer on success; "" on failure
            - source: ResponseSource.OFFLINE ("offline")
            - intent_type: canonical intent type from C5 classification
            - error: ResponseError on failure; None on success
    """
    # Obtain or use C5 classification
    if classification is None:
        classification = classify_intent(query)

    intent_type = classification.intent_type

    # ── Path A: Empty or Invalid Input ──────────────────────────────────
    if not query or not query.strip():
        return RouterResponse(
            success=False,
            response_text="",
            source=ResponseSource.OFFLINE,
            intent_type=ResponseIntentType.NONE,
            provider="local",
            model="validator",
            is_fallback=False,
            intent_payload=None,
            error=ResponseError(
                code=ErrorCode.INTERNAL_ERROR,
                message="Cannot execute offline path for empty or whitespace query.",
                retryable=False,
                details={"query": query, "reason": "empty_input"},
            ),
        )

    # ── Path B: Device Action Commands (LOCAL + ACTION) ─────────────────
    if intent_type == ResponseIntentType.ACTION:
        clean_query = query.strip()
        action_match = re.search(
            r"^(?:open|launch|start|run|turn\s+on|turn\s+off|toggle|enable|disable|set|take)\s+(.+)$",
            clean_query,
            re.IGNORECASE,
        )
        target = action_match.group(1).strip() if action_match else clean_query
        target_title = target.title()

        if re.search(r"^(?:open|launch|start|run)\b", clean_query, re.IGNORECASE):
            action_type = "APP"
            resp_text = f"Opening {target_title}"
            icon = "📱"
        elif re.search(r"^(?:set)\s+(?:an?\s+)?(?:alarm|timer)\b", clean_query, re.IGNORECASE):
            action_type = "APP"
            resp_text = f"Alarm set for {target}"
            icon = "⏰"
        elif re.search(r"^(?:take)\s+(?:a\s+)?(?:screenshot|photo|selfie)\b", clean_query, re.IGNORECASE):
            action_type = "APP"
            resp_text = f"Capturing {target}"
            icon = "📸"
        else:
            action_type = "TOGGLE"
            resp_text = f"Toggled {target_title}"
            icon = "⚡"

        return RouterResponse(
            success=True,
            response_text=resp_text,
            source=ResponseSource.OFFLINE,
            intent_type=ResponseIntentType.ACTION,
            provider="local",
            model="systemops",
            is_fallback=False,
            intent_payload=IntentPayload(
                icon=icon,
                title=target_title,
                target=target,
                action_type=action_type,
                status="Ready",
                response_text=resp_text,
            ),
            error=None,
        )

    # ── Path C: Local Knowledge Base Lookup (INFO / CONVERSATION) ───────
    try:
        ans = query_local_knowledge_base(query, kb_path=kb_path)
    except Exception as e:
        _logger.error(f"[Node 1 Offline] Knowledge base read exception: {e}")
        # Structured error shape from C4; caller can decide on online escalation
        return RouterResponse(
            success=False,
            response_text="",
            source=ResponseSource.OFFLINE,
            intent_type=intent_type,
            provider="knowledge_base",
            model="custom_qa",
            is_fallback=False,
            intent_payload=None,
            error=ResponseError(
                code=ErrorCode.INTERNAL_ERROR,
                message=f"Offline knowledge base read failure: {e}",
                retryable=True,
                details={"kb_path": str(kb_path), "exception": str(e)},
            ),
        )

    # Successful offline answer found
    if ans:
        intent_payload = None
        if intent_type == ResponseIntentType.INFO:
            intent_payload = IntentPayload(
                icon="ℹ",
                title=query.strip().capitalize()[:30],
                body=ans,
                source="offline_kb",
                response_text=ans,
            )

        return RouterResponse(
            success=True,
            response_text=ans,
            source=ResponseSource.OFFLINE,
            intent_type=intent_type,
            provider="knowledge_base",
            model="custom_qa",
            is_fallback=False,
            intent_payload=intent_payload,
            error=None,
        )

    # ── Path D: Knowledge Base Miss (No Local Answer Found) ─────────────
    # Per C4: Strictly return structured error with retryable=True so the caller
    # (Traffic Police / C11) can escalate to online cascade. Never return canned strings.
    return RouterResponse(
        success=False,
        response_text="",
        source=ResponseSource.OFFLINE,
        intent_type=intent_type,
        provider="knowledge_base",
        model="custom_qa",
        is_fallback=False,
        intent_payload=None,
        error=ResponseError(
            code=ErrorCode.MODEL_NOT_FOUND,
            message="No matching answer found in local offline knowledge base.",
            retryable=True,
            details={"query": query, "reason": "kb_miss"},
        ),
    )


def route_offline_async(
    query: str,
    classification: Optional[ClassificationResult] = None,
    kb_path: Path = DEFAULT_KB_PATH,
) -> concurrent.futures.Future:
    """
    Non-blocking asynchronous offline routing returning a Future.
    Offloads CPU/disk processing to the dedicated offline worker pool.
    """
    return _OFFLINE_EXECUTOR.submit(
        route_offline,
        query=query,
        classification=classification,
        kb_path=kb_path,
    )

