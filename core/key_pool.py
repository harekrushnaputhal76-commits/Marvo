"""
Marvo API Key Pool & Failover Layer (Node 3) — key_pool.py
===========================================================
Load-balancing and automatic failover layer sitting directly in front of Node 2 (online_router).

Guarantees & Architecture:
1. Dynamic credential ingestion: reads keys from existing config/env pattern (.env, GEMINI_API_KEY,
   GEMINI_API_KEYS, GEMINI_API_KEY_*) with ZERO hardcoded keys.
2. Automatic bounded failover: on rate-limit (429) or transient error, automatically retries the SAME
   request with the next available key up to N = len(keys) attempts (NO infinite loops).
3. Graceful offline fallback: if all keys are exhausted, failing, or network is down, routes to Node 1
   (offline_router) as a graceful fallback without surfacing raw errors to caller; flags response
   with fallback_occurred = True and is_fallback = True.
4. Leak-proof logging: logs failover events by key index and error category (rate-limit vs network vs auth)
   WITHOUT ever logging the actual API key string.
5. Error differentiation:
   - Rate limit (429) -> temporary cooldown (can be reused later in session).
   - Auth failure (401/403) -> marked INVALID and permanently skipped for remainder of session.
   - Network offline (503) -> short-circuits key retries and immediately drops to offline fallback.
"""

import os
import time
import logging
import threading
import concurrent.futures
from dataclasses import dataclass
from pathlib import Path
from typing import Optional, List, Dict, Any, Callable, Set

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
from core.online_router import (
    route_online,
    DEFAULT_ONLINE_TIMEOUT_SECONDS,
    DEFAULT_ONLINE_MODEL,
)
from core.offline_router import (
    route_offline,
)

_logger = logging.getLogger("marvo.routing.key_pool")

# ─────────────────────────────────────────────────────────────────────────────
# 1. CONFIGURATION & CONSTANTS
# ─────────────────────────────────────────────────────────────────────────────

# Default cooldown duration for rate-limited (429) keys before they become eligible again
DEFAULT_RATE_LIMIT_COOLDOWN_SECONDS: float = 60.0

# Dedicated bounded thread pool for asynchronous failover routing
_FAILOVER_EXECUTOR = concurrent.futures.ThreadPoolExecutor(
    max_workers=4,
    thread_name_prefix="marvo-failover-worker",
)


# ─────────────────────────────────────────────────────────────────────────────
# 2. KEY SLOT STATE CONTAINER
# ─────────────────────────────────────────────────────────────────────────────

@dataclass
class KeySlot:
    """
    Health state container for an individual API key in the pool.
    Guarantees zero leakage: __repr__ masks the key value.
    """
    index: int
    key: str
    is_valid: bool = True               # False if 401/403 auth error (skipped for session)
    rate_limited_until: float = 0.0     # Epoch timestamp until which key is in cooldown
    failure_count: int = 0
    success_count: int = 0
    last_error_code: Optional[str] = None
    last_error_time: Optional[float] = None

    @property
    def is_available(self) -> bool:
        """True if the key is structurally valid and not currently in rate-limit cooldown."""
        if not self.is_valid:
            return False
        return time.time() >= self.rate_limited_until

    def __repr__(self) -> str:
        # Strictly mask key in all debug representations to prevent accidental log leakage
        return (
            f"KeySlot(index={self.index}, is_valid={self.is_valid}, "
            f"available={self.is_available}, failures={self.failure_count})"
        )


# ─────────────────────────────────────────────────────────────────────────────
# 3. ENVIRONMENT & CONFIG INGESTION (C2 COMPLIANT)
# ─────────────────────────────────────────────────────────────────────────────

def load_gemini_keys_from_env(
    env_file_path: Optional[Path] = None,
) -> List[str]:
    """
    Read available Gemini API keys from the project's canonical environment configuration.
    
    Supports:
    1. GEMINI_API_KEY (single key or comma-separated list)
    2. GEMINI_API_KEYS (comma or newline separated list)
    3. Numbered variables: GEMINI_API_KEY_1, GEMINI_API_KEY_2, etc.

    Guarantees:
    - Never logs key values.
    - Strips whitespace and quotes.
    - Deduplicates keys while preserving ordering.
    - Zero hardcoded keys.
    """
    # Attempt loading .env if dotenv is installed
    try:
        from dotenv import load_dotenv
        target_env = env_file_path or (Path(__file__).resolve().parent.parent / ".env")
        if target_env.is_file():
            load_dotenv(target_env, override=False)
        else:
            load_dotenv()
    except ImportError:
        pass

    raw_candidates: List[str] = []

    # 1. Primary GEMINI_API_KEY
    primary = os.environ.get("GEMINI_API_KEY", "").strip()
    if primary:
        for chunk in primary.replace(";", ",").split(","):
            cleaned = chunk.strip().strip("'\"")
            if cleaned:
                raw_candidates.append(cleaned)

    # 2. Plural GEMINI_API_KEYS
    plural = os.environ.get("GEMINI_API_KEYS", "").strip()
    if plural:
        for chunk in plural.replace(";", ",").replace("\n", ",").split(","):
            cleaned = chunk.strip().strip("'\"")
            if cleaned:
                raw_candidates.append(cleaned)

    # 3. Numbered keys GEMINI_API_KEY_1 .. GEMINI_API_KEY_10
    for i in range(1, 11):
        num_key = os.environ.get(f"GEMINI_API_KEY_{i}", "").strip().strip("'\"")
        if num_key:
            raw_candidates.append(num_key)

    # Deduplicate while preserving order
    seen: Set[str] = set()
    deduped: List[str] = []
    for k in raw_candidates:
        if k not in seen:
            seen.add(k)
            deduped.append(k)

    return deduped


# ─────────────────────────────────────────────────────────────────────────────
# 4. KEY POOL CLASS (NODE 3)
# ─────────────────────────────────────────────────────────────────────────────

class KeyPool:
    """
    Thread-safe API Key Pool with round-robin selection, failure tracking,
    differentiation of rate-limit vs auth failure, and bounded failover.
    """

    def __init__(
        self,
        keys: Optional[List[str]] = None,
        rate_limit_cooldown_seconds: float = DEFAULT_RATE_LIMIT_COOLDOWN_SECONDS,
    ):
        raw_keys = keys if keys is not None else load_gemini_keys_from_env()
        self.rate_limit_cooldown_seconds = rate_limit_cooldown_seconds
        self._lock = threading.Lock()
        self._pointer = 0

        self.slots: List[KeySlot] = [
            KeySlot(index=i, key=k) for i, k in enumerate(raw_keys)
        ]

    @property
    def total_count(self) -> int:
        """Total configured keys in pool."""
        return len(self.slots)

    @property
    def valid_count(self) -> int:
        """Count of keys not permanently invalidated by 401/403."""
        with self._lock:
            return sum(1 for s in self.slots if s.is_valid)

    @property
    def available_count(self) -> int:
        """Count of keys currently eligible (valid and not in rate-limit cooldown)."""
        with self._lock:
            return sum(1 for s in self.slots if s.is_available)

    def select_next_slot(self, exclude_indices: Optional[Set[int]] = None) -> Optional[KeySlot]:
        """
        Thread-safe selection of the next available key slot using round-robin.
        If all available keys are in cooldown, picks the valid slot with the earliest
        expiring cooldown.
        """
        exclude = exclude_indices or set()
        with self._lock:
            valid_slots = [s for s in self.slots if s.is_valid and s.index not in exclude]
            if not valid_slots:
                return None

            # First pass: look for slots where is_available is True
            available_slots = [s for s in valid_slots if s.is_available]
            if available_slots:
                # Advance pointer round-robin style
                selected = available_slots[self._pointer % len(available_slots)]
                self._pointer = (self._pointer + 1) % len(available_slots)
                return selected

            # Second pass: all remaining valid slots are in cooldown; select earliest expiring
            earliest_slot = min(valid_slots, key=lambda s: s.rate_limited_until)
            return earliest_slot

    def record_failure(
        self,
        slot_index: int,
        error: ResponseError,
    ) -> None:
        """
        Thread-safe failure recorder. Enforces:
        - 401/403 -> marks key invalid for entire session.
        - 429 / Quota -> enters temporary cooldown.
        - Zero key value leakage in log messages.
        """
        with self._lock:
            if 0 <= slot_index < len(self.slots):
                slot = self.slots[slot_index]
                slot.failure_count += 1
                slot.last_error_code = error.code.value if hasattr(error.code, "value") else str(error.code)
                slot.last_error_time = time.time()

                if error.code == ErrorCode.AUTH_FAILED:
                    slot.is_valid = False
                    _logger.error(
                        f"[KeyPool Failover] Key index {slot.index} authentication failed (401/403). "
                        f"Permanently disabled for this session."
                    )
                elif error.code == ErrorCode.RATE_LIMITED:
                    slot.rate_limited_until = time.time() + self.rate_limit_cooldown_seconds
                    _logger.warning(
                        f"[KeyPool Failover] Key index {slot.index} rate-limited (429 ResourceExhausted). "
                        f"Placed in cooldown for {self.rate_limit_cooldown_seconds:.1f}s."
                    )
                elif error.code == ErrorCode.NETWORK_OFFLINE:
                    _logger.warning(
                        f"[KeyPool Failover] Key index {slot.index} encountered network disconnection."
                    )
                elif error.code == ErrorCode.TIMEOUT:
                    _logger.warning(
                        f"[KeyPool Failover] Key index {slot.index} timed out."
                    )
                else:
                    _logger.warning(
                        f"[KeyPool Failover] Key index {slot.index} failed with {error.code}: {error.message[:80]}."
                    )

    def record_success(self, slot_index: int) -> None:
        """Thread-safe success recorder."""
        with self._lock:
            if 0 <= slot_index < len(self.slots):
                slot = self.slots[slot_index]
                slot.success_count += 1
                slot.failure_count = 0
                slot.last_error_code = None

    def route_with_failover(
        self,
        query: str,
        classification: Optional[ClassificationResult] = None,
        timeout_seconds: float = DEFAULT_ONLINE_TIMEOUT_SECONDS,
        thinking_mode: str = "medium",
        session_id: str = "default",
        model: str = DEFAULT_ONLINE_MODEL,
        client_invoker: Optional[Callable[[KeySlot], RouterResponse]] = None,
    ) -> RouterResponse:
        """
        Execute request with automatic key rotation and graceful offline fallback.

        Algorithm:
        1. Classify query intent if not pre-computed.
        2. If no valid keys in pool, immediately trigger graceful offline fallback.
        3. Iterate up to N = len(valid_keys) attempts (guarantees NO infinite loops).
        4. On success: record key success, return response with fallback_occurred = False.
        5. On rate-limit or timeout: record failure, log event (key index only), try next key.
        6. On network offline: log event, short-circuit remaining key attempts (since internet
           is down) and immediately trigger offline fallback.
        7. On auth failure: mark key invalid for session, log event, try next key.
        8. If all keys fail/exhaust: trigger Node 1 graceful offline fallback; mark
           fallback_occurred = True, is_fallback = True, and do NOT surface errors to caller.
        """
        if classification is None:
            classification = classify_intent(query)

        valid_slots_count = self.valid_count

        # Case A: Pool has 0 configured or valid keys
        if valid_slots_count == 0:
            _logger.warning(
                "[KeyPool Failover] No valid online API keys configured or remaining. "
                "Routing directly to graceful offline fallback."
            )
            return self._execute_graceful_offline_fallback(query, classification, reason="no_valid_keys")

        tried_indices: Set[int] = set()
        max_attempts = valid_slots_count

        for attempt in range(1, max_attempts + 1):
            slot = self.select_next_slot(exclude_indices=tried_indices)
            if slot is None:
                # No more eligible keys (all valid keys exhausted in cooldown or tried)
                break

            tried_indices.add(slot.index)

            # Node 2 invocation
            if client_invoker is not None:
                # Test/mock injection
                res = client_invoker(slot)
            else:
                res = route_online(
                    query=query,
                    classification=classification,
                    timeout_seconds=timeout_seconds,
                    thinking_mode=thinking_mode,
                    session_id=session_id,
                    api_key=slot.key,
                    model=model,
                )

            # Check outcome
            if res.success:
                self.record_success(slot.index)
                res.fallback_occurred = False
                res.is_fallback = False
                return res

            # Online attempt failed
            if res.error is not None:
                self.record_failure(slot.index, res.error)

                # Error-type differentiation:
                # If network itself is completely down, trying other keys will redundantly fail.
                # Short-circuit to graceful offline fallback immediately.
                if res.error.code == ErrorCode.NETWORK_OFFLINE:
                    _logger.warning(
                        f"[KeyPool Failover] Network unreachable on key index {slot.index}. "
                        "Bypassing further key retries; routing immediately to graceful offline fallback."
                    )
                    return self._execute_graceful_offline_fallback(query, classification, reason="network_offline")

            _logger.info(
                f"[KeyPool Failover] Retrying request with next available key "
                f"(completed attempt {attempt}/{max_attempts})."
            )

        # Case B: All available keys exhausted without success
        _logger.warning(
            f"[KeyPool Failover] All available keys ({len(tried_indices)}/{valid_slots_count}) "
            "exhausted or failing. Engaging graceful offline fallback."
        )
        return self._execute_graceful_offline_fallback(query, classification, reason="all_keys_exhausted")

    def _execute_graceful_offline_fallback(
        self,
        query: str,
        classification: Optional[ClassificationResult],
        reason: str,
    ) -> RouterResponse:
        """
        Graceful offline fallback handler (C7 path integration).
        Ensures NO error is surfaced to the caller. Sets fallback_occurred = True
        and is_fallback = True.
        """
        try:
            offline_res = route_offline(query, classification=classification)
            if offline_res.success:
                offline_res.fallback_occurred = True
                offline_res.is_fallback = True
                return offline_res
        except Exception as e:
            _logger.error(f"[KeyPool Failover] Exception during offline fallback execution: {e}")

        # If offline knowledge base has no exact match (KB miss on escalated query),
        # return a polite, successful fallback response rather than surfacing an unhandled error.
        intent_type = classification.intent_type if classification else ResponseIntentType.CONVERSATION
        return RouterResponse(
            success=True,
            response_text=(
                "I'm operating in offline mode right now as cloud services are currently unreachable. "
                "I can still assist you with device settings, actions, and offline inquiries."
            ),
            source=ResponseSource.OFFLINE,
            intent_type=intent_type,
            provider="offline_fallback",
            model="graceful_fallback",
            is_fallback=True,
            fallback_occurred=True,
            intent_payload=None,
            error=None,
        )


# ─────────────────────────────────────────────────────────────────────────────
# 5. SINGLETON ACCESS & PUBLIC CONVENIENCE FUNCTIONS
# ─────────────────────────────────────────────────────────────────────────────

_DEFAULT_POOL: Optional[KeyPool] = None
_POOL_LOCK = threading.Lock()


def get_default_key_pool() -> KeyPool:
    """Obtain or initialize the global KeyPool instance."""
    global _DEFAULT_POOL
    with _POOL_LOCK:
        if _DEFAULT_POOL is None:
            _DEFAULT_POOL = KeyPool()
        return _DEFAULT_POOL


def route_with_failover(
    query: str,
    classification: Optional[ClassificationResult] = None,
    timeout_seconds: float = DEFAULT_ONLINE_TIMEOUT_SECONDS,
    thinking_mode: str = "medium",
    session_id: str = "default",
    model: str = DEFAULT_ONLINE_MODEL,
    pool: Optional[KeyPool] = None,
) -> RouterResponse:
    """
    Public entry point for Node 3 failover routing.
    Dispatches through the specified or default KeyPool.
    """
    effective_pool = pool or get_default_key_pool()
    return effective_pool.route_with_failover(
        query=query,
        classification=classification,
        timeout_seconds=timeout_seconds,
        thinking_mode=thinking_mode,
        session_id=session_id,
        model=model,
    )


def route_with_failover_async(
    query: str,
    classification: Optional[ClassificationResult] = None,
    timeout_seconds: float = DEFAULT_ONLINE_TIMEOUT_SECONDS,
    thinking_mode: str = "medium",
    session_id: str = "default",
    model: str = DEFAULT_ONLINE_MODEL,
    pool: Optional[KeyPool] = None,
) -> concurrent.futures.Future:
    """
    Non-blocking asynchronous failover routing returning a Future.
    Allows event loops and concurrent callers to await the RouterResponse.
    """
    return _FAILOVER_EXECUTOR.submit(
        route_with_failover,
        query=query,
        classification=classification,
        timeout_seconds=timeout_seconds,
        thinking_mode=thinking_mode,
        session_id=session_id,
        model=model,
        pool=pool,
    )
