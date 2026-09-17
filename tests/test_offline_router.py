"""
Unit tests for Marvo Offline Router Node 1 (core/offline_router.py).
===================================================================
Verifies:
1. Genuine offline generation sets source: "offline" and intent_type from C5.
2. Device actions produce valid ACTION intent payloads without network calls.
3. Offline misses return structured ResponseError (never canned strings).
4. File/parsing failures return structured ResponseError (retryable=True).
5. Absolute no-network guarantee by inspection.
"""

import os
import sys
import unittest
from pathlib import Path

# Ensure repo root is importable when executed directly
_repo_root = str(Path(__file__).resolve().parent.parent)
if _repo_root not in sys.path:
    sys.path.insert(0, _repo_root)

from core.offline_router import route_offline, query_local_knowledge_base
from core.response_schema import (
    ResponseSource,
    ResponseIntentType,
    ErrorCode,
    RouterResponse,
)
from core.classifier import classify_intent, RouteCategory


class TestOfflineRouter(unittest.TestCase):
    """Test suite for Node 1 offline routing and C4 contract enforcement."""

    def test_offline_successful_kb_match(self):
        """Known queries in custom_qa.json must produce success: True, source: 'offline'."""
        test_queries = [
            ("who are you", ResponseIntentType.CONVERSATION),
            ("hello", ResponseIntentType.CONVERSATION),
            ("tell me a joke", ResponseIntentType.CONVERSATION),
            ("what is mole", ResponseIntentType.INFO),
        ]
        for q, expected_intent in test_queries:
            with self.subTest(query=q):
                res = route_offline(q)
                self.assertIsInstance(res, RouterResponse)
                self.assertTrue(res.success)
                self.assertEqual(res.source, ResponseSource.OFFLINE)
                self.assertEqual(res.intent_type, expected_intent)
                self.assertTrue(len(res.response_text) > 0)
                self.assertIsNone(res.error)
                self.assertEqual(res.provider, "knowledge_base")
                self.assertEqual(res.model, "custom_qa")

                # Verify dict serialization adheres to C4 schema
                d = res.to_dict()
                self.assertTrue(d["success"])
                self.assertEqual(d["source"], "offline")
                self.assertEqual(d["intent_type"], expected_intent.value)
                self.assertIsNone(d["error"])
                self.assertEqual(d["state"], "state-speaking")

    def test_offline_device_action_command(self):
        """Action queries produce success: True, intent_type: ACTION, and valid IntentPayload."""
        res = route_offline("open camera")
        self.assertTrue(res.success)
        self.assertEqual(res.source, ResponseSource.OFFLINE)
        self.assertEqual(res.intent_type, ResponseIntentType.ACTION)
        self.assertIn("Opening Camera", res.response_text)
        self.assertIsNone(res.error)
        self.assertIsNotNone(res.intent_payload)
        self.assertEqual(res.intent_payload.action_type, "APP")
        self.assertEqual(res.intent_payload.title, "Camera")

        d = res.to_dict()
        self.assertEqual(d["intent_type"], "ACTION")
        self.assertEqual(d["intent_payload"]["target"], "camera")

    def test_offline_kb_miss_returns_structured_error(self):
        """
        C3 ANTI-PATTERN PREVENTION:
        When a query is NOT found in custom_qa.json, the router must NEVER
        return a canned apology string. It must return success: False with a
        structured ResponseError and retryable: True.
        """
        unknown_query = "what is the quantum chromodynamics gauge symmetry group"
        res = route_offline(unknown_query)

        self.assertFalse(res.success)
        self.assertEqual(res.response_text, "")
        self.assertEqual(res.source, ResponseSource.OFFLINE)
        self.assertIsNotNone(res.error)
        self.assertEqual(res.error.code, ErrorCode.MODEL_NOT_FOUND)
        self.assertTrue(res.error.retryable)
        self.assertIn("No matching answer found", res.error.message)

        # Confirm canned string is strictly absent
        d = res.to_dict()
        self.assertFalse(d["success"])
        self.assertEqual(d["response_text"], "")
        self.assertNotIn("couldn't find that in my local memory", d["response"])
        self.assertNotIn("I'm currently offline", d["response"])
        self.assertEqual(d["error"]["code"], "MODEL_NOT_FOUND")
        self.assertTrue(d["error"]["retryable"])

    def test_offline_empty_input_returns_structured_error(self):
        """Empty queries must return structured error without crashing."""
        for empty_q in ["", "   ", "\t\n"]:
            with self.subTest(empty_q=repr(empty_q)):
                res = route_offline(empty_q)
                self.assertFalse(res.success)
                self.assertEqual(res.response_text, "")
                self.assertEqual(res.intent_type, ResponseIntentType.NONE)
                self.assertIsNotNone(res.error)
                self.assertEqual(res.error.code, ErrorCode.INTERNAL_ERROR)
                self.assertFalse(res.error.retryable)

    def test_offline_missing_kb_file_returns_structured_error(self):
        """Missing or inaccessible KB file returns structured error with retryable: True."""
        bad_path = Path("non_existent_kb_dir/missing.json")
        res = route_offline("who are you", kb_path=bad_path)

        self.assertFalse(res.success)
        self.assertEqual(res.response_text, "")
        self.assertIsNotNone(res.error)
        self.assertEqual(res.error.code, ErrorCode.INTERNAL_ERROR)
        self.assertTrue(res.error.retryable)
        self.assertIn("Offline knowledge base read failure", res.error.message)

    def test_no_network_guarantee_by_inspection(self):
        """
        Verify by inspection that core.offline_router has ZERO imports
        capable of making HTTP/network requests.
        """
        import core.offline_router as mod
        import inspect

        source_code = inspect.getsource(mod)

        forbidden_network_modules = [
            "requests",
            "urllib.request",
            "aiohttp",
            "httpx",
            "http.client",
            "socket",
            "google.genai",
        ]

        for forbidden in forbidden_network_modules:
            self.assertNotIn(
                f"import {forbidden}",
                source_code,
                f"Violation: {forbidden} is imported in offline_router.py!",
            )
            self.assertNotIn(
                f"from {forbidden}",
                source_code,
                f"Violation: {forbidden} is imported from in offline_router.py!",
            )

    def test_offline_async_execution(self):
        """route_offline_async returns a Future that resolves non-blocking on worker thread."""
        from core.offline_router import route_offline_async
        future = route_offline_async("who are you")
        self.assertFalse(future.done() and not future.running())
        res = future.result(timeout=2.0)
        self.assertTrue(res.success)
        self.assertEqual(res.source, ResponseSource.OFFLINE)
        self.assertEqual(res.intent_type, ResponseIntentType.CONVERSATION)

    def test_offline_kb_caching(self):
        """Knowledge base caching returns identical results rapidly across multiple calls."""
        res1 = route_offline("who are you")
        res2 = route_offline("who are you")
        self.assertEqual(res1.response_text, res2.response_text)
        self.assertEqual(res1.source, res2.source)



def run_standalone_suite() -> int:
    """Executes the test suite directly with formatted reporting."""
    suite = unittest.TestLoader().loadTestsFromTestCase(TestOfflineRouter)
    runner = unittest.TextTestRunner(verbosity=2)
    print("=" * 70)
    print("RUNNING MARVO OFFLINE ROUTER (NODE 1) TEST SUITE")
    print("=" * 70)
    result = runner.run(suite)
    print("=" * 70)
    print(f"Summary: {result.testsRun} tests run, {len(result.errors)} errors, {len(result.failures)} failures.")
    return 0 if result.wasSuccessful() else 1


if __name__ == "__main__":
    sys.exit(run_standalone_suite())
