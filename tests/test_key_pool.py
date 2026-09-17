"""
Unit tests for Marvo API Key Pool & Failover Layer Node 3 (core/key_pool.py).
==============================================================================
Verifies:
1. Dynamic ingestion from environment/config without hardcoding.
2. Single-key successful execution with fallback_occurred = False.
3. Automatic retry on 429 rate limit with next available key.
4. Permanent invalidation of 401/403 keys for the rest of the session.
5. Graceful offline fallback when all keys are exhausted (no error surfaced to caller).
6. Immediate short-circuiting to offline fallback on network disconnection.
7. Strictly bounded retry loop (attempts == len(valid_keys), no infinite loop).
8. Zero key leakage in log records and string representations.
9. Non-blocking asynchronous failover invocation returning Future.
"""

import os
import sys
import time
import logging
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

# Ensure repo root is importable when executed directly
_repo_root = str(Path(__file__).resolve().parent.parent)
if _repo_root not in sys.path:
    sys.path.insert(0, _repo_root)

from core.key_pool import (
    KeyPool,
    KeySlot,
    KeyHealthStatus,
    route_with_failover,
    route_with_failover_async,
    load_gemini_keys_from_env,
    DEFAULT_RATE_LIMIT_COOLDOWN_SECONDS,
    DEFAULT_FAILING_COOLDOWN_SECONDS,
    DEFAULT_FAILING_THRESHOLD,
)
from core.response_schema import (
    ResponseSource,
    ResponseIntentType,
    ErrorCode,
    ResponseError,
    RouterResponse,
)
from core.classifier import classify_intent, ResponseIntentType


class TestKeyPool(unittest.TestCase):
    """Test suite for Node 3 Key Pool, automatic failover, and graceful fallback."""

    def test_key_pool_env_loading(self):
        """Verify environment loading parses single, plural, and numbered variables."""
        mock_env = {
            "GEMINI_API_KEY": "key_alpha, key_beta",
            "GEMINI_API_KEYS": "key_beta, key_gamma",
            "GEMINI_API_KEY_1": "key_delta",
        }
        with patch.dict(os.environ, mock_env, clear=True):
            keys = load_gemini_keys_from_env()
            self.assertEqual(keys, ["key_alpha", "key_beta", "key_gamma", "key_delta"])
            pool = KeyPool(keys=keys)
            self.assertEqual(pool.total_count, 4)
            self.assertEqual(pool.valid_count, 4)
            self.assertEqual(pool.available_count, 4)

    def test_single_key_success(self):
        """Successful online execution sets fallback_occurred: False and records success."""
        pool = KeyPool(keys=["mock_key_1"])

        def mock_invoker(slot: KeySlot) -> RouterResponse:
            return RouterResponse(
                success=True,
                response_text="Online calculation complete.",
                source=ResponseSource.ONLINE,
                intent_type=ResponseIntentType.INFO,
                provider="gemini",
                model="gemini-flash-latest",
            )

        res = pool.route_with_failover(
            query="What is 25 * 25?",
            client_invoker=mock_invoker,
        )

        self.assertTrue(res.success)
        self.assertEqual(res.source, ResponseSource.ONLINE)
        self.assertFalse(res.fallback_occurred)
        self.assertFalse(res.is_fallback)
        self.assertEqual(res.response_text, "Online calculation complete.")
        self.assertEqual(pool.slots[0].success_count, 1)
        self.assertEqual(pool.slots[0].failure_count, 0)

    def test_failover_on_rate_limit_429(self):
        """
        When Key 0 hits 429 quota exhaustion, it enters cooldown and the request
        is retried on Key 1, which succeeds.
        """
        pool = KeyPool(keys=["mock_key_0", "mock_key_1"], rate_limit_cooldown_seconds=30.0)

        invoked_keys = []

        def mock_invoker(slot: KeySlot) -> RouterResponse:
            invoked_keys.append(slot.index)
            if slot.index == 0:
                return RouterResponse(
                    success=False,
                    response_text="",
                    source=ResponseSource.ONLINE,
                    error=ResponseError(
                        code=ErrorCode.RATE_LIMITED,
                        message="429 ResourceExhausted: Quota exceeded",
                        retryable=True,
                        http_status=429,
                    ),
                )
            return RouterResponse(
                success=True,
                response_text="Online response from Key 1.",
                source=ResponseSource.ONLINE,
                intent_type=ResponseIntentType.INFO,
            )

        res = pool.route_with_failover(
            query="Search recent developments in AI",
            client_invoker=mock_invoker,
        )

        self.assertTrue(res.success)
        self.assertEqual(res.source, ResponseSource.ONLINE)
        self.assertFalse(res.fallback_occurred)
        self.assertFalse(res.is_fallback)
        self.assertEqual(res.response_text, "Online response from Key 1.")
        self.assertEqual(invoked_keys, [0, 1])

        # Key 0 must be in cooldown
        self.assertTrue(pool.slots[0].rate_limited_until > time.time())
        self.assertFalse(pool.slots[0].is_available)
        self.assertTrue(pool.slots[0].is_valid)  # Still valid, just in cooldown

    def test_permanent_auth_failure_401(self):
        """
        When Key 0 fails with 401/403, it is marked INVALID and permanently skipped
        for the remainder of the session.
        """
        pool = KeyPool(keys=["bad_key_0", "good_key_1"])

        invoked_keys = []

        def mock_invoker(slot: KeySlot) -> RouterResponse:
            invoked_keys.append(slot.index)
            if slot.index == 0:
                return RouterResponse(
                    success=False,
                    response_text="",
                    source=ResponseSource.ONLINE,
                    error=ResponseError(
                        code=ErrorCode.AUTH_FAILED,
                        message="API key expired or invalid",
                        retryable=False,
                        http_status=401,
                    ),
                )
            return RouterResponse(
                success=True,
                response_text="Online response from Key 1.",
                source=ResponseSource.ONLINE,
            )

        res = pool.route_with_failover(
            query="Tell me about Mars",
            client_invoker=mock_invoker,
        )

        self.assertTrue(res.success)
        self.assertEqual(invoked_keys, [0, 1])
        # Key 0 is permanently invalid
        self.assertFalse(pool.slots[0].is_valid)
        self.assertEqual(pool.valid_count, 1)

        # On next query, Key 0 must NOT even be selected
        invoked_keys.clear()
        res2 = pool.route_with_failover(
            query="Tell me about Jupiter",
            client_invoker=mock_invoker,
        )
        self.assertTrue(res2.success)
        self.assertEqual(invoked_keys, [1])

    def test_all_keys_exhausted_triggers_graceful_offline_fallback(self):
        """
        When ALL keys fail, do not surface an error to the caller.
        Route to offline engine with fallback_occurred = True and is_fallback = True.
        """
        pool = KeyPool(keys=["key_0", "key_1"])

        def failing_invoker(slot: KeySlot) -> RouterResponse:
            return RouterResponse(
                success=False,
                response_text="",
                source=ResponseSource.ONLINE,
                error=ResponseError(
                    code=ErrorCode.RATE_LIMITED,
                    message="All quotas exhausted",
                    retryable=True,
                    http_status=429,
                ),
            )

        # 1. Query known in offline KB ("who are you")
        res_kb = pool.route_with_failover(
            query="who are you",
            client_invoker=failing_invoker,
        )
        self.assertTrue(res_kb.success)
        self.assertEqual(res_kb.source, ResponseSource.OFFLINE)
        self.assertTrue(res_kb.fallback_occurred)
        self.assertTrue(res_kb.is_fallback)
        self.assertIsNone(res_kb.error)
        self.assertTrue(len(res_kb.response_text) > 0)

        # 2. General query not in offline KB
        res_general = pool.route_with_failover(
            query="Explain topological quantum field theory",
            client_invoker=failing_invoker,
        )
        self.assertTrue(res_general.success)
        self.assertEqual(res_general.source, ResponseSource.OFFLINE)
        self.assertTrue(res_general.fallback_occurred)
        self.assertTrue(res_general.is_fallback)
        self.assertIsNone(res_general.error)
        self.assertIn("operating in offline mode", res_general.response_text.lower())

        # Verify C4 dictionary representation
        d = res_general.to_dict()
        self.assertTrue(d["success"])
        self.assertEqual(d["source"], "offline")
        self.assertTrue(d["fallback_occurred"])
        self.assertTrue(d["is_fallback"])
        self.assertIsNone(d["error"])
        self.assertEqual(d["state"], "state-speaking")

    def test_network_offline_short_circuits_to_offline_fallback(self):
        """
        If network is unreachable (503), short-circuit key attempts immediately
        and route to offline fallback without wasting time on subsequent keys.
        """
        pool = KeyPool(keys=["key_0", "key_1", "key_2"])

        invocations = []

        def mock_invoker(slot: KeySlot) -> RouterResponse:
            invocations.append(slot.index)
            return RouterResponse(
                success=False,
                response_text="",
                source=ResponseSource.ONLINE,
                error=ResponseError(
                    code=ErrorCode.NETWORK_OFFLINE,
                    message="Device appears offline",
                    retryable=True,
                    http_status=503,
                ),
            )

        res = pool.route_with_failover(
            query="What is the weather today?",
            client_invoker=mock_invoker,
        )

        self.assertTrue(res.success)
        self.assertEqual(res.source, ResponseSource.OFFLINE)
        self.assertTrue(res.fallback_occurred)
        self.assertTrue(res.is_fallback)
        # Verify it stopped at key 0 and did not loop through keys 1 and 2
        self.assertEqual(invocations, [0])

    def test_no_infinite_retry_loop_bounded_attempts(self):
        """Verify retry attempts are strictly bounded by N = len(valid_keys)."""
        pool = KeyPool(keys=["k0", "k1", "k2"])

        attempts = 0

        def failing_invoker(slot: KeySlot) -> RouterResponse:
            nonlocal attempts
            attempts += 1
            return RouterResponse(
                success=False,
                response_text="",
                source=ResponseSource.ONLINE,
                error=ResponseError(
                    code=ErrorCode.TIMEOUT,
                    message="Request timed out",
                    retryable=True,
                    http_status=504,
                ),
            )

        res = pool.route_with_failover(
            query="Complex calculation",
            client_invoker=failing_invoker,
        )

        # Attempts must equal exactly the 3 available keys, no infinite looping
        self.assertEqual(attempts, 3)
        self.assertTrue(res.success)
        self.assertTrue(res.fallback_occurred)

    def test_zero_key_leakage_in_logs(self):
        """
        CRITICAL SECURITY REQUIREMENT:
        Verify secret key strings never appear in log output or slot representations.
        """
        secret_key_token = "SECRET_SUPER_CONFIDENTIAL_KEY_9999"
        pool = KeyPool(keys=[secret_key_token])

        # Check __repr__ masking
        slot = pool.slots[0]
        self.assertNotIn(secret_key_token, repr(slot))
        self.assertNotIn(secret_key_token, str(slot))

        # Check logging during failover event
        logger = logging.getLogger("marvo.routing.key_pool")
        with self.assertLogs(logger, level="WARNING") as log_capture:
            pool.record_failure(
                0,
                ResponseError(
                    code=ErrorCode.RATE_LIMITED,
                    message="429 Quota Exceeded",
                    retryable=True,
                ),
            )

        for record in log_capture.output:
            self.assertNotIn(secret_key_token, record)
            self.assertIn("Key index 0", record)
            self.assertIn("rate-limited", record.lower())

    def test_key_health_status_transitions(self):
        """
        Verify the in-memory state machine transitions:
        HEALTHY -> RATE_LIMITED (429) -> HEALTHY (after cooldown) -> FAILING (>=3 errors) -> INVALID (401).
        """
        pool = KeyPool(
            keys=["k0"],
            rate_limit_cooldown_seconds=60.0,
            failing_cooldown_seconds=15.0,
            failing_threshold=3,
        )
        slot = pool.slots[0]

        # 1. Initial state
        self.assertEqual(slot.get_health_status(), KeyHealthStatus.HEALTHY)
        self.assertTrue(slot.is_available)

        # 2. Rate limit transition
        pool.record_failure(0, ResponseError(code=ErrorCode.RATE_LIMITED, message="429 Quota Exceeded"))
        self.assertEqual(slot.get_health_status(), KeyHealthStatus.RATE_LIMITED)
        self.assertFalse(slot.is_available)

        # 3. Rate limit expiry (lazy evaluation on demand)
        future_time = time.time() + 61.0
        self.assertEqual(slot.get_health_status(now=future_time), KeyHealthStatus.HEALTHY)

        # 4. Success restores HEALTHY
        pool.record_success(0)
        self.assertEqual(slot.get_health_status(), KeyHealthStatus.HEALTHY)
        self.assertEqual(slot.consecutive_failures, 0)

        # 5. Consecutive transient failures trigger FAILING
        for _ in range(3):
            pool.record_failure(0, ResponseError(code=ErrorCode.TIMEOUT, message="504 Timed Out"))
        self.assertEqual(slot.get_health_status(), KeyHealthStatus.FAILING)
        self.assertFalse(slot.is_available)

        # 6. FAILING cooldown expiry
        future_failing_time = time.time() + 16.0
        self.assertEqual(slot.get_health_status(now=future_failing_time), KeyHealthStatus.HEALTHY)

        # 7. AUTH_FAILED triggers permanent INVALID
        pool.record_failure(0, ResponseError(code=ErrorCode.AUTH_FAILED, message="401 Invalid Key"))
        self.assertEqual(slot.get_health_status(), KeyHealthStatus.INVALID)
        self.assertFalse(slot.is_available)
        # Cannot be restored even far in the future
        self.assertEqual(slot.get_health_status(now=time.time() + 999999), KeyHealthStatus.INVALID)

    def test_subsequent_request_skips_recently_failed_key(self):
        """
        CORE GOAL:
        If Key 0 just failed with 429 on Request 1, Request 2 (arriving immediately
        afterwards) must completely SKIP Key 0 and dispatch directly to Key 1.
        """
        pool = KeyPool(keys=["key_0", "key_1"], rate_limit_cooldown_seconds=60.0)

        call_log = []

        def mock_invoker(slot: KeySlot) -> RouterResponse:
            call_log.append((slot.index, time.time()))
            if slot.index == 0:
                return RouterResponse(
                    success=False,
                    response_text="",
                    source=ResponseSource.ONLINE,
                    error=ResponseError(
                        code=ErrorCode.RATE_LIMITED,
                        message="429 Quota Exceeded",
                        retryable=True,
                        http_status=429,
                    ),
                )
            return RouterResponse(
                success=True,
                response_text=f"Handled by Key {slot.index}",
                source=ResponseSource.ONLINE,
            )

        # Request 1: Key 0 fails -> fails over to Key 1
        res1 = pool.route_with_failover(query="First question", client_invoker=mock_invoker)
        self.assertTrue(res1.success)
        self.assertEqual(res1.response_text, "Handled by Key 1")
        self.assertEqual([idx for idx, _ in call_log], [0, 1])

        # Request 2: immediately afterwards
        call_log.clear()
        res2 = pool.route_with_failover(query="Second question", client_invoker=mock_invoker)
        self.assertTrue(res2.success)
        self.assertEqual(res2.response_text, "Handled by Key 1")
        # Key 0 must NOT have been called on Request 2!
        self.assertEqual([idx for idx, _ in call_log], [1])

    def test_all_keys_rate_limited_skips_online_call_entirely(self):
        """
        When all keys are in cooldown, the router does NOT make a doomed online call.
        It immediately dispatches to offline fallback.
        """
        pool = KeyPool(keys=["k0", "k1"], rate_limit_cooldown_seconds=60.0)

        # Put both keys into rate-limit cooldown
        pool.record_failure(0, ResponseError(code=ErrorCode.RATE_LIMITED, message="429"))
        pool.record_failure(1, ResponseError(code=ErrorCode.RATE_LIMITED, message="429"))

        mock_invoker = MagicMock()
        res = pool.route_with_failover(
            query="Explain black holes",
            client_invoker=mock_invoker,
        )

        # Online call was never invoked
        mock_invoker.assert_not_called()
        self.assertTrue(res.success)
        self.assertEqual(res.source, ResponseSource.OFFLINE)
        self.assertTrue(res.fallback_occurred)
        self.assertTrue(res.is_fallback)

    def test_no_background_threads_or_polling_introduced(self):
        """
        BATTERY / RESOURCE AUDIT:
        Confirm that KeyPool does not instantiate background polling threads or loops.
        State transitions are purely reactive upon request dispatch.
        """
        import threading

        threads_before = {t.name for t in threading.enumerate()}
        pool = KeyPool(keys=["key_a", "key_b"])
        threads_after = {t.name for t in threading.enumerate()}

        # No new thread was spawned simply by instantiating or holding the pool
        new_threads = threads_after - threads_before
        polling_threads = [name for name in new_threads if "poll" in name.lower() or "health" in name.lower()]
        self.assertEqual(polling_threads, [])

    def test_state_resets_sensibly_on_new_pool_instance(self):
        """
        Confirm tracking state resets cleanly on server/app restart without
        requiring persistent file/db cleanup.
        """
        pool1 = KeyPool(keys=["key_restart"])
        pool1.record_failure(0, ResponseError(code=ErrorCode.AUTH_FAILED, message="401 Revoked"))
        self.assertEqual(pool1.slots[0].get_health_status(), KeyHealthStatus.INVALID)

        # Simulate fresh app startup
        pool2 = KeyPool(keys=["key_restart"])
        self.assertEqual(pool2.slots[0].get_health_status(), KeyHealthStatus.HEALTHY)
        self.assertEqual(pool2.slots[0].failure_count, 0)
        self.assertEqual(pool2.slots[0].consecutive_failures, 0)
        self.assertTrue(pool2.slots[0].is_available)

    def test_async_route_with_failover(self):
        """route_with_failover_async returns a Future resolving to a RouterResponse."""
        pool = KeyPool(keys=["mock_key"])

        future = route_with_failover_async(
            query="who are you",
            pool=pool,
        )
        res = future.result(timeout=3.0)
        self.assertIsInstance(res, RouterResponse)
        self.assertTrue(res.success)


def run_standalone_suite() -> int:
    """Executes the test suite directly with formatted reporting."""
    suite = unittest.TestLoader().loadTestsFromTestCase(TestKeyPool)
    runner = unittest.TextTestRunner(verbosity=2)
    print("=" * 70)
    print("RUNNING MARVO API KEY POOL & FAILOVER (NODE 3) TEST SUITE")
    print("=" * 70)
    result = runner.run(suite)
    print("=" * 70)
    print(f"Summary: {result.testsRun} tests run, {len(result.errors)} errors, {len(result.failures)} failures.")
    return 0 if result.wasSuccessful() else 1


if __name__ == "__main__":
    sys.exit(run_standalone_suite())

