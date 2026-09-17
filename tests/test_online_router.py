"""
Unit tests for Marvo Online Escalation Router Node 2 (core/online_router.py).
=============================================================================
Verifies:
1. Successful online query execution sets source: "online" and preserves C5 intent_type.
2. Contextual round-trip survival: intent_type survives through worker thread.
3. Missing or invalid authentication produces structured ErrorCode.AUTH_FAILED (401).
4. Request timeouts trigger structured ErrorCode.TIMEOUT (504) with retryable=True.
5. Rate limits (429/ResourceExhausted) trigger ErrorCode.RATE_LIMITED (429) with retryable=True.
6. Network disconnections trigger ErrorCode.NETWORK_OFFLINE (503) with retryable=True.
7. Empty/whitespace queries return structured ErrorCode.INTERNAL_ERROR without crashing.
8. Anti-pattern prevention: Node 2 NEVER returns canned apology strings or falls back to offline.
9. Async variant route_online_async returns Future resolving to valid RouterResponse.
"""

import os
import sys
import time
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

# Ensure repo root is importable when executed directly
_repo_root = str(Path(__file__).resolve().parent.parent)
if _repo_root not in sys.path:
    sys.path.insert(0, _repo_root)

from core.online_router import (
    route_online,
    route_online_async,
    DEFAULT_ONLINE_TIMEOUT_SECONDS,
    DEFAULT_ONLINE_MODEL,
    OnlineRequestContext,
    categorize_online_exception,
)
from core.response_schema import (
    ResponseSource,
    ResponseIntentType,
    ErrorCode,
    RouterResponse,
)
from core.classifier import classify_intent, ClassificationResult, RouteCategory


class DummyGenAIResponse:
    """Mock Gemini SDK GenerateContentResponse."""
    def __init__(self, text: str):
        self.text = text


class DummyGenAIClient:
    """Mock Google GenAI Client with customizable response or failure behavior."""
    def __init__(self, response_text: str = "This is a live online response.", raise_exc: Exception = None, delay: float = 0.0):
        self.response_text = response_text
        self.raise_exc = raise_exc
        self.delay = delay
        self.models = MagicMock()
        self.models.generate_content.side_effect = self._generate_content

    def _generate_content(self, model: str, contents: str, config: any = None):
        if self.delay > 0:
            time.sleep(self.delay)
        if self.raise_exc:
            raise self.raise_exc
        return DummyGenAIResponse(self.response_text)


class TestOnlineRouter(unittest.TestCase):
    """Test suite for Node 2 online routing, non-blocking execution, and C4 contract enforcement."""

    def test_online_successful_mock_response(self):
        """Mock online generation produces success: True, source: 'online', and carries intent_type."""
        mock_client = DummyGenAIClient(response_text="Paris is the capital of France.")
        res = route_online(
            query="What is the capital of France?",
            client=mock_client,
            timeout_seconds=5.0,
        )

        self.assertIsInstance(res, RouterResponse)
        self.assertTrue(res.success)
        self.assertEqual(res.source, ResponseSource.ONLINE)
        self.assertEqual(res.intent_type, ResponseIntentType.INFO)
        self.assertEqual(res.response_text, "Paris is the capital of France.")
        self.assertIsNone(res.error)
        self.assertFalse(res.is_fallback)
        self.assertEqual(res.provider, "gemini")

        # C4 dict serialization checks
        d = res.to_dict()
        self.assertTrue(d["success"])
        self.assertEqual(d["source"], "online")
        self.assertEqual(d["intent_type"], "INFO")
        self.assertEqual(d["response"], "Paris is the capital of France.")
        self.assertEqual(d["state"], "state-speaking")
        self.assertIsNone(d["error"])

    def test_intent_type_context_survival(self):
        """Verify intent_type from C5 classification survives through the entire worker thread."""
        test_matrix = [
            ("what is the current temperature in Tokyo", ResponseIntentType.INFO),
            ("open the settings and turn on bluetooth", ResponseIntentType.ACTION),
            ("tell me a story about space", ResponseIntentType.CONVERSATION),
        ]
        for query, expected_intent in test_matrix:
            with self.subTest(query=query):
                classification = classify_intent(query)
                mock_client = DummyGenAIClient(response_text=f"Response for: {query}")
                res = route_online(
                    query=query,
                    classification=classification,
                    client=mock_client,
                )
                self.assertTrue(res.success)
                self.assertEqual(res.intent_type, classification.intent_type)
                self.assertEqual(res.intent_type, expected_intent)

    def test_auth_failure_when_key_missing(self):
        """
        When no API key is provided or present in environment,
        returns structured ErrorCode.AUTH_FAILED with HTTP 401.
        """
        with patch.dict(os.environ, {}, clear=True):
            res = route_online(
                query="What is the latest quantum computing paper?",
                api_key="",
                client=None,
            )
            self.assertFalse(res.success)
            self.assertEqual(res.response_text, "")
            self.assertEqual(res.source, ResponseSource.ONLINE)
            self.assertIsNotNone(res.error)
            self.assertEqual(res.error.code, ErrorCode.AUTH_FAILED)
            self.assertEqual(res.error.http_status, 401)
            self.assertFalse(res.error.retryable)
            self.assertIn("No Gemini API key configured", res.error.message)

    def test_timeout_triggers_504_error(self):
        """
        When request exceeds timeout_seconds, worker triggers ErrorCode.TIMEOUT (504)
        with retryable=True.
        """
        slow_client = DummyGenAIClient(delay=0.5)
        res = route_online(
            query="Analyze this massive codebase",
            client=slow_client,
            timeout_seconds=0.05,  # Strict short timeout
        )
        self.assertFalse(res.success)
        self.assertEqual(res.response_text, "")
        self.assertEqual(res.source, ResponseSource.ONLINE)
        self.assertIsNotNone(res.error)
        self.assertEqual(res.error.code, ErrorCode.TIMEOUT)
        self.assertEqual(res.error.http_status, 504)
        self.assertTrue(res.error.retryable)
        self.assertIn("timed out", res.error.message.lower())

    def test_rate_limited_429_triggers_retryable_error(self):
        """Quota exceeded or rate limit triggers ErrorCode.RATE_LIMITED (429) with retryable=True."""
        rate_limit_exc = Exception("429 ResourceExhausted: Resource has been exhausted (e.g. check quota).")
        rate_limited_client = DummyGenAIClient(raise_exc=rate_limit_exc)

        res = route_online(
            query="Summarize today's headlines",
            client=rate_limited_client,
        )
        self.assertFalse(res.success)
        self.assertEqual(res.source, ResponseSource.ONLINE)
        self.assertIsNotNone(res.error)
        self.assertEqual(res.error.code, ErrorCode.RATE_LIMITED)
        self.assertEqual(res.error.http_status, 429)
        self.assertTrue(res.error.retryable)

    def test_network_offline_triggers_503_error(self):
        """Network unreachable or DNS failure triggers ErrorCode.NETWORK_OFFLINE (503)."""
        net_exc = ConnectionError("Failed to establish a new connection: [Errno 11001] getaddrinfo failed")
        net_err_client = DummyGenAIClient(raise_exc=net_exc)

        res = route_online(
            query="What is the stock price of NVDA",
            client=net_err_client,
        )
        self.assertFalse(res.success)
        self.assertEqual(res.source, ResponseSource.ONLINE)
        self.assertIsNotNone(res.error)
        self.assertEqual(res.error.code, ErrorCode.NETWORK_OFFLINE)
        self.assertEqual(res.error.http_status, 503)
        self.assertTrue(res.error.retryable)

    def test_empty_input_returns_internal_error(self):
        """Empty or whitespace input returns structured error without invoking network/client."""
        for empty_q in ["", "   ", "\t\n"]:
            with self.subTest(empty_q=repr(empty_q)):
                mock_client = DummyGenAIClient()
                res = route_online(query=empty_q, client=mock_client)
                self.assertFalse(res.success)
                self.assertEqual(res.response_text, "")
                self.assertEqual(res.source, ResponseSource.ONLINE)
                self.assertEqual(res.intent_type, ResponseIntentType.NONE)
                self.assertIsNotNone(res.error)
                self.assertEqual(res.error.code, ErrorCode.INTERNAL_ERROR)
                self.assertFalse(res.error.retryable)
                # Verify client was never touched
                mock_client.models.generate_content.assert_not_called()

    def test_no_offline_fallback_inside_node_2(self):
        """
        SINGLE RESPONSIBILITY & ANTI-PATTERN PREVENTION:
        Node 2 must NEVER fall back to offline when online fails.
        Fallback orchestration belongs exclusively to Node 3 / Traffic Police.
        """
        failing_client = DummyGenAIClient(raise_exc=Exception("500 Internal Server Error"))
        res = route_online(
            query="who are you",  # Known in custom_qa.json, but route_online must NOT touch it
            client=failing_client,
        )
        self.assertFalse(res.success)
        # Source must remain ONLINE (not offline)
        self.assertEqual(res.source, ResponseSource.ONLINE)
        self.assertFalse(res.is_fallback)
        self.assertEqual(res.response_text, "")
        self.assertIsNotNone(res.error)

        # Confirm canned apology strings are strictly absent
        d = res.to_dict()
        self.assertFalse(d["success"])
        self.assertEqual(d["source"], "online")
        self.assertEqual(d["response_text"], "")
        self.assertNotIn("I'm sorry", d["response"])
        self.assertNotIn("something went wrong", d["response"].lower())

    def test_async_route_online_future(self):
        """route_online_async must return a Future resolving to a valid RouterResponse."""
        mock_client = DummyGenAIClient(response_text="Async computation complete.")
        future = route_online_async(
            query="Calculate 50 * 50",
            client=mock_client,
        )
        res = future.result(timeout=3.0)
        self.assertIsInstance(res, RouterResponse)
        self.assertTrue(res.success)
        self.assertEqual(res.response_text, "Async computation complete.")
        self.assertEqual(res.source, ResponseSource.ONLINE)


def run_standalone_suite() -> int:
    """Executes the test suite directly with formatted reporting."""
    suite = unittest.TestLoader().loadTestsFromTestCase(TestOnlineRouter)
    runner = unittest.TextTestRunner(verbosity=2)
    print("=" * 70)
    print("RUNNING MARVO ONLINE ROUTER (NODE 2) TEST SUITE")
    print("=" * 70)
    result = runner.run(suite)
    print("=" * 70)
    print(f"Summary: {result.testsRun} tests run, {len(result.errors)} errors, {len(result.failures)} failures.")
    return 0 if result.wasSuccessful() else 1


if __name__ == "__main__":
    sys.exit(run_standalone_suite())
