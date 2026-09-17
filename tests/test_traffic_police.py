"""
Unit tests for Marvo Traffic Police Central Router (core/traffic_police.py).
============================================================================
Verifies:
1. Unified single public entry point: route_traffic() routes LOCAL queries to Node 1.
2. ESCALATE queries route to Node 2/3 with key pool failover.
3. Local KB misses automatically escalate to online cascade.
4. Cloud failure/exhaustion gracefully falls back to offline engine (fallback_occurred=True).
5. Catastrophic worst-case failure handling: when both online and offline fail completely,
   returns structured C4 ResponseError (never crashes or returns fake canned strings).
6. Empty/whitespace input returns structured C4 validation error.
7. Call site unification: brain.think_and_respond, agents.chat_agent.generate_chat_response,
   and server.handle_request route through Traffic Police without bypassing logic.
8. Non-blocking asynchronous route_traffic_async execution.
"""

import os
import sys
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

# Ensure repo root is importable when executed directly
_repo_root = str(Path(__file__).resolve().parent.parent)
if _repo_root not in sys.path:
    sys.path.insert(0, _repo_root)

from core.traffic_police import (
    route_traffic,
    route_traffic_async,
)
from core.response_schema import (
    ResponseSource,
    ResponseIntentType,
    ErrorCode,
    ResponseError,
    RouterResponse,
)
from core.key_pool import KeyPool, KeySlot


class TestTrafficPolice(unittest.TestCase):
    """Test suite for master Traffic Police routing cascade and fail-safe guarantees."""

    def test_local_query_routes_to_node_1(self):
        """LOCAL-classified queries (device actions and known QA) route directly to Node 1."""
        # 1. Device Action
        res_action = route_traffic("open camera")
        self.assertTrue(res_action.success)
        self.assertEqual(res_action.source, ResponseSource.OFFLINE)
        self.assertEqual(res_action.intent_type, ResponseIntentType.ACTION)
        self.assertIn("Camera", res_action.response_text)
        self.assertFalse(res_action.fallback_occurred)

        # 2. Known local QA query
        res_qa = route_traffic("who are you")
        self.assertTrue(res_qa.success)
        self.assertEqual(res_qa.source, ResponseSource.OFFLINE)
        self.assertEqual(res_qa.intent_type, ResponseIntentType.CONVERSATION)
        self.assertFalse(res_qa.fallback_occurred)

    def test_escalate_query_routes_to_node_2_3(self):
        """ESCALATE-classified queries route to Node 2/3 Key Pool."""
        mock_pool = KeyPool(keys=["mock_key_1"])

        def mock_invoker(slot: KeySlot) -> RouterResponse:
            return RouterResponse(
                success=True,
                response_text="Real-time stock price is $145.20.",
                source=ResponseSource.ONLINE,
                intent_type=ResponseIntentType.INFO,
                provider="gemini",
                model="gemini-flash-latest",
            )

        with patch.object(mock_pool, "route_with_failover", side_effect=lambda **kwargs: mock_invoker(mock_pool.slots[0])):
            res = route_traffic(
                query="What is the current stock price of Apple today?",
                pool=mock_pool,
            )
            self.assertTrue(res.success)
            self.assertEqual(res.source, ResponseSource.ONLINE)
            self.assertEqual(res.intent_type, ResponseIntentType.INFO)
            self.assertFalse(res.fallback_occurred)
            self.assertEqual(res.response_text, "Real-time stock price is $145.20.")

    def test_local_kb_miss_escalates_to_online(self):
        """When a LOCAL query misses custom_qa.json, Traffic Police escalates to Node 2/3."""
        unknown_local_query = "what is the quantum chromodynamics gauge symmetry group"

        mock_pool = KeyPool(keys=["mock_key"])

        def mock_online_call(**kwargs):
            return RouterResponse(
                success=True,
                response_text="The gauge symmetry group of quantum chromodynamics is SU(3).",
                source=ResponseSource.ONLINE,
                intent_type=ResponseIntentType.INFO,
            )

        with patch.object(mock_pool, "route_with_failover", side_effect=mock_online_call):
            res = route_traffic(unknown_local_query, pool=mock_pool)
            self.assertTrue(res.success)
            self.assertEqual(res.source, ResponseSource.ONLINE)
            self.assertIn("SU(3)", res.response_text)

    def test_online_failure_gracefully_falls_back_to_offline(self):
        """When all online keys fail, Traffic Police returns graceful offline fallback with fallback_occurred=True."""
        exhausted_pool = KeyPool(keys=["dead_key_1", "dead_key_2"])

        # Put keys into rate-limit cooldown
        exhausted_pool.record_failure(0, ResponseError(code=ErrorCode.RATE_LIMITED, message="429"))
        exhausted_pool.record_failure(1, ResponseError(code=ErrorCode.RATE_LIMITED, message="429"))

        res = route_traffic(
            query="Calculate current orbital velocity of Mars",
            pool=exhausted_pool,
        )

        self.assertTrue(res.success)
        self.assertEqual(res.source, ResponseSource.OFFLINE)
        self.assertTrue(res.fallback_occurred)
        self.assertTrue(res.is_fallback)
        self.assertIsNone(res.error)
        self.assertIn("operating in offline mode", res.response_text.lower())

    def test_catastrophic_worst_case_both_fail(self):
        """
        WORST-CASE BEHAVIOR GUARANTEE:
        When online fails AND the offline engine throws an unhandled catastrophic exception,
        Traffic Police NEVER crashes or returns a canned string. It returns a structured
        C4 ResponseError with ErrorCode.INTERNAL_ERROR and http_status 500.
        """
        broken_pool = KeyPool(keys=["k0"])

        # Force unhandled catastrophic exception in routing
        with patch("core.traffic_police.route_with_failover", side_effect=RuntimeError("Catastrophic hardware fault")):
            res = route_traffic(
                query="What is quantum mechanics?",
                pool=broken_pool,
            )

            # Assert contract guarantees
            self.assertIsInstance(res, RouterResponse)
            self.assertFalse(res.success)
            self.assertEqual(res.response_text, "")
            self.assertEqual(res.source, ResponseSource.OFFLINE)
            self.assertTrue(res.fallback_occurred)
            self.assertTrue(res.is_fallback)
            self.assertIsNotNone(res.error)
            self.assertEqual(res.error.code, ErrorCode.INTERNAL_ERROR)
            self.assertEqual(res.error.http_status, 500)
            self.assertTrue(res.error.retryable)
            self.assertIn("Catastrophic failure in routing cascade", res.error.message)

            # Verify C4 dictionary serialization
            d = res.to_dict()
            self.assertFalse(d["success"])
            self.assertEqual(d["source"], "offline")
            self.assertEqual(d["state"], "state-error")
            self.assertEqual(d["error"]["code"], "INTERNAL_ERROR")
            self.assertEqual(d["error"]["http_status"], 500)
            self.assertTrue(d["fallback_occurred"])

    def test_empty_query_returns_c4_validation_error(self):
        """Empty or whitespace queries return structured 400 error without touching models."""
        for empty_val in ["", "   ", "\t\r\n"]:
            with self.subTest(empty_val=repr(empty_val)):
                res = route_traffic(empty_val)
                self.assertFalse(res.success)
                self.assertEqual(res.response_text, "")
                self.assertIsNotNone(res.error)
                self.assertEqual(res.error.code, ErrorCode.INTERNAL_ERROR)
                self.assertEqual(res.error.http_status, 400)
                self.assertFalse(res.error.retryable)

    def test_call_site_unification(self):
        """
        Verify all existing call sites (brain.think_and_respond, chat_agent.generate_chat_response,
        server.handle_request) invoke Traffic Police and conform to C4 schema.
        """
        # 1. core.brain: think_and_respond
        from core.brain import think_and_respond
        text, state = think_and_respond("who are you")
        self.assertTrue(len(text) > 0)
        self.assertEqual(state, "state-speaking")

        # 2. agents.chat_agent: generate_chat_response
        from agents.chat_agent import generate_chat_response
        chat_dict = generate_chat_response("open camera")
        self.assertEqual(chat_dict["type"], "text")
        self.assertTrue(chat_dict["success"])
        self.assertEqual(chat_dict["intent_type"], "ACTION")
        self.assertEqual(chat_dict["source"], "offline")

        # 3. server.server: handle_request
        from server.server import handle_request
        server_dict = handle_request("who are you")
        self.assertEqual(server_dict["type"], "text")
        self.assertTrue(server_dict["success"])
        self.assertEqual(server_dict["source"], "offline")
        self.assertIn("Marvo", server_dict["response"])

    def test_async_route_traffic(self):
        """route_traffic_async returns a Future resolving to a RouterResponse."""
        future = route_traffic_async("who are you")
        res = future.result(timeout=3.0)
        self.assertIsInstance(res, RouterResponse)
        self.assertTrue(res.success)
        self.assertEqual(res.source, ResponseSource.OFFLINE)

    def test_concurrent_non_blocking_execution(self):
        """Multiple concurrent requests execute in parallel without deadlock or thread starvation."""
        queries = ["who are you", "open camera", "hello", "set alarm for 7am", "tell me a joke"]
        futures = [route_traffic_async(q) for q in queries]
        
        # All futures must resolve within a tight timeout (concurrency guarantee)
        results = [f.result(timeout=3.0) for f in futures]
        self.assertEqual(len(results), 5)
        for res in results:
            self.assertTrue(res.success)
            self.assertEqual(res.source, ResponseSource.OFFLINE)

    def test_server_api_chat_endpoint_contract(self):
        """Test that Flask /api/chat endpoint processes requests and returns full C4 response."""
        from server.server import app
        with app.test_client() as client:
            resp = client.post("/api/chat", json={"message": "who are you"})
            self.assertEqual(resp.status_code, 200)
            data = resp.get_json()
            self.assertTrue(data.get("success"))
            self.assertEqual(data.get("source"), "offline")
            self.assertEqual(data.get("intent_type"), "CONVERSATION")
            self.assertIn("Marvo", data.get("response"))



def run_standalone_suite() -> int:
    """Executes the test suite directly with formatted reporting."""
    suite = unittest.TestLoader().loadTestsFromTestCase(TestTrafficPolice)
    runner = unittest.TextTestRunner(verbosity=2)
    print("=" * 70)
    print("RUNNING MARVO TRAFFIC POLICE CENTRAL ROUTER TEST SUITE")
    print("=" * 70)
    result = runner.run(suite)
    print("=" * 70)
    print(f"Summary: {result.testsRun} tests run, {len(result.errors)} errors, {len(result.failures)} failures.")
    return 0 if result.wasSuccessful() else 1


if __name__ == "__main__":
    sys.exit(run_standalone_suite())
