"""
Unit tests for Marvo Routing Classifier (core/classifier.py).
============================================================
Runs standalone without external dependencies via standard library unittest,
or directly as an executable test script: `python tests/test_classifier.py`.
"""

import os
import sys
import unittest
from pathlib import Path

# Ensure repo root is importable when executed directly
_repo_root = str(Path(__file__).resolve().parent.parent)
if _repo_root not in sys.path:
    sys.path.insert(0, _repo_root)

from core.classifier import (
    classify_intent,
    route_query,
    RouteCategory,
    ResponseIntentType,
    ClassificationResult,
)


class TestClassifier(unittest.TestCase):
    """Test suite covering LOCAL, ESCALATE, edge cases, and malformed inputs."""

    # ─────────────────────────────────────────────────────────────────────────
    # 1. CLEARLY LOCAL QUERIES
    # ─────────────────────────────────────────────────────────────────────────

    def test_clearly_local_device_actions(self):
        """Device actions and system toggles must route LOCALLY as ACTION intents."""
        actions = [
            "turn on flashlight",
            "turn off wifi",
            "toggle bluetooth",
            "open camera",
            "launch settings",
            "start spotify",
            "set an alarm for 7 AM",
            "set a timer for 10 minutes",
            "call mom",
            "take a screenshot",
            "volume up",
            "mute",
            "play music",
            "pause song",
        ]
        for query in actions:
            with self.subTest(query=query):
                res = classify_intent(query)
                self.assertEqual(res.route, RouteCategory.LOCAL)
                self.assertEqual(res.intent_type, ResponseIntentType.ACTION)
                self.assertEqual(res.reason, "device_action_command")

    def test_clearly_local_conversations(self):
        """Standard chit-chat, greetings, and identity questions route LOCALLY."""
        chat_queries = [
            "hello",
            "hi marvo",
            "good morning",
            "who are you",
            "what is your name",
            "tell me a joke",
            "thank you",
            "goodbye",
            "help",
        ]
        for query in chat_queries:
            with self.subTest(query=query):
                res = classify_intent(query)
                self.assertEqual(res.route, RouteCategory.LOCAL)
                self.assertEqual(res.intent_type, ResponseIntentType.CONVERSATION)

    def test_clearly_local_basic_facts(self):
        """Static basic facts route LOCALLY to query custom_qa / knowledge base."""
        static_facts = [
            "what is photosynthesis",
            "who was Albert Einstein",
            "where is the Eiffel Tower",
            "when was the printing press invented",
            "define gravity",
            "tell me about Mars",
        ]
        for query in static_facts:
            with self.subTest(query=query):
                res = classify_intent(query)
                self.assertEqual(res.route, RouteCategory.LOCAL)
                self.assertEqual(res.intent_type, ResponseIntentType.INFO)
                self.assertEqual(res.reason, "basic_fact_local_lookup")

    # ─────────────────────────────────────────────────────────────────────────
    # 2. CLEARLY ESCALATE QUERIES
    # ─────────────────────────────────────────────────────────────────────────

    def test_clearly_escalate_realtime_data(self):
        """Queries demanding real-time dynamic data must ESCALATE to cloud models."""
        realtime_queries = [
            ("what is the weather today", "weather"),
            ("tomorrow forecast for Mumbai", "forecast"),
            ("current temperature outside", "temperature"),
            ("stock price of tesla", "stock"),
            ("current bitcoin price", "bitcoin"),
            ("latest news in tech", "news"),
            ("breaking headlines today", "breaking"),
            ("cricket match score live", "score"),
            ("traffic status to downtown", "traffic"),
            ("flight status for AA100", "flight"),
        ]
        for query, expected_signal in realtime_queries:
            with self.subTest(query=query):
                res = classify_intent(query)
                self.assertEqual(res.route, RouteCategory.ESCALATE)
                self.assertEqual(res.intent_type, ResponseIntentType.INFO)
                self.assertIn("real_time", res.reason)
                self.assertTrue(any(expected_signal in s for s in res.matched_signals))

    def test_clearly_escalate_computation_and_reasoning(self):
        """Math, algorithm analysis, and code synthesis must ESCALATE to cloud models."""
        reasoning_queries = [
            "calculate the square root of 144",
            "solve the equation 2x + 5 = 15",
            "explain quantum mechanics step by step",
            "explain photosynthesis in detail",
            "pros and cons of microservices vs monolith",
            "compare and contrast python and javascript",
            "write a python script to scrape a website",
            "write a function to reverse a binary tree",
            "debug this algorithm for binary search",
            "summarize the theory of relativity",
            "translate this paragraph to Spanish",
        ]
        for query in reasoning_queries:
            with self.subTest(query=query):
                res = classify_intent(query)
                self.assertEqual(res.route, RouteCategory.ESCALATE)
                self.assertEqual(res.intent_type, ResponseIntentType.INFO)
                self.assertIn("reasoning", res.reason)

    def test_clearly_escalate_long_complex_queries(self):
        """Lengthy multi-sentence queries (> 32 words) must ESCALATE to cloud LLM."""
        long_query = (
            "I am currently trying to design a distributed consensus protocol for an edge-computing "
            "cluster where network partitions happen frequently between the nodes and battery "
            "conservation is a critical constraint so we need to minimize heartbeat messages while "
            "maintaining linearizable consistency guarantees across replicas."
        )
        res = classify_intent(long_query)
        self.assertEqual(res.route, RouteCategory.ESCALATE)
        self.assertEqual(res.intent_type, ResponseIntentType.INFO)
        self.assertIn("length", res.reason)

    # ─────────────────────────────────────────────────────────────────────────
    # 3. AMBIGUOUS & EDGE-CASE QUERIES
    # ─────────────────────────────────────────────────────────────────────────

    def test_ambiguous_greeting_with_temporal_word(self):
        """
        Edge Case 1: 'how are you doing today' contains the temporal word 'today'.
        Resolution: Falls to LOCAL + CONVERSATION.
        Acceptable Rationale: The user is exchanging social pleasantries, not
        demanding a dynamic news or weather feed. Escalating to cloud LLM would
        waste API quota and incur latency.
        """
        res = classify_intent("how are you doing today")
        self.assertEqual(res.route, RouteCategory.LOCAL)
        self.assertEqual(res.intent_type, ResponseIntentType.CONVERSATION)
        self.assertEqual(res.reason, "conversational_greeting_or_smalltalk")

    def test_ambiguous_tell_me_about_today(self):
        """
        Edge Case 2: 'tell me about today'.
        Resolution: Falls to ESCALATE + INFO.
        Acceptable Rationale: Inquires about current events, schedule, or daily news,
        which cannot be served by static local QA.
        """
        res = classify_intent("tell me about today")
        self.assertEqual(res.route, RouteCategory.ESCALATE)
        self.assertEqual(res.intent_type, ResponseIntentType.INFO)
        self.assertEqual(res.reason, "real_time_temporal_information_required")

    def test_ambiguous_outfit_advice(self):
        """
        Edge Case 3: 'what should I wear today'.
        Resolution: Falls to ESCALATE + INFO.
        Acceptable Rationale: Advice for 'today' requires real-time weather, temperature,
        and temporal reasoning.
        """
        res = classify_intent("what should I wear today")
        self.assertEqual(res.route, RouteCategory.ESCALATE)
        self.assertEqual(res.intent_type, ResponseIntentType.INFO)
        self.assertEqual(res.reason, "real_time_temporal_information_required")

    def test_ambiguous_unclassified_fallback(self):
        """
        Edge Case 4: Random gibberish or obscure noun strings like 'banana avocado orange'.
        Resolution: Falls to LOCAL + CONVERSATION.
        Acceptable Rationale: Defaulting to LOCAL prevents unbounded API spend, maintains
        instantaneous feedback, and lets on-device persona politely ask for clarification.
        """
        res = classify_intent("banana avocado orange")
        self.assertEqual(res.route, RouteCategory.LOCAL)
        self.assertEqual(res.intent_type, ResponseIntentType.CONVERSATION)
        self.assertEqual(res.reason, "default_local_fallback")

    # ─────────────────────────────────────────────────────────────────────────
    # 4. EMPTY & MALFORMED INPUTS (CRASH PREVENTION)
    # ─────────────────────────────────────────────────────────────────────────

    def test_empty_string_input(self):
        """Empty string must never crash; returns LOCAL + NONE."""
        res = classify_intent("")
        self.assertEqual(res.route, RouteCategory.LOCAL)
        self.assertEqual(res.intent_type, ResponseIntentType.NONE)
        self.assertEqual(res.reason, "empty_or_invalid_input")

    def test_whitespace_only_input(self):
        """Whitespace-only string must never crash; returns LOCAL + NONE."""
        res = classify_intent("   \t\n  ")
        self.assertEqual(res.route, RouteCategory.LOCAL)
        self.assertEqual(res.intent_type, ResponseIntentType.NONE)
        self.assertEqual(res.reason, "empty_query_after_strip")

    def test_non_string_types(self):
        """Non-string types (None, int, list, dict) must never crash; returns LOCAL + NONE."""
        for malformed in [None, 12345, 3.1415, ["unexpected", "list"], {"key": "value"}]:
            with self.subTest(malformed=malformed):
                res = classify_intent(malformed)  # type: ignore
                self.assertEqual(res.route, RouteCategory.LOCAL)
                self.assertEqual(res.intent_type, ResponseIntentType.NONE)
                self.assertEqual(res.reason, "empty_or_invalid_input")

    def test_oversized_string_clamping(self):
        """Queries exceeding MAX_QUERY_LEN (1000 chars) are clamped; execution remains fast."""
        huge_query = "hello world " * 500  # ~6,000 characters
        res = classify_intent(huge_query)
        self.assertIsInstance(res, ClassificationResult)
        # Bounded processing triggers length complexity rule without hanging
        self.assertEqual(res.route, RouteCategory.ESCALATE)
        self.assertEqual(res.intent_type, ResponseIntentType.INFO)
        self.assertEqual(res.reason, "high_query_length_and_complexity")

    # ─────────────────────────────────────────────────────────────────────────
    # 5. PURITY, IMMUTABILITY & CONVENIENCE WRAPPER
    # ─────────────────────────────────────────────────────────────────────────

    def test_pure_function_determinism(self):
        """Identical inputs must produce identical, immutable outputs."""
        query = "what is the weather today in London"
        res1 = classify_intent(query)
        res2 = classify_intent(query)
        self.assertEqual(res1, res2)
        with self.assertRaises(Exception):
            res1.route = RouteCategory.LOCAL  # Frozen dataclass check

    def test_route_query_dict_serialization(self):
        """route_query helper returns JSON-serializable dictionary with exact fields."""
        d = route_query("turn on flashlight")
        self.assertEqual(d["route"], "LOCAL")
        self.assertEqual(d["intent_type"], "ACTION")
        self.assertEqual(d["reason"], "device_action_command")
        self.assertIsInstance(d["matched_signals"], list)
        self.assertEqual(d["confidence"], 0.98)


def run_standalone_suite() -> int:
    """Executes the test suite directly with formatted reporting."""
    suite = unittest.TestLoader().loadTestsFromTestCase(TestClassifier)
    runner = unittest.TextTestRunner(verbosity=2)
    print("=" * 70)
    print("RUNNING MARVO ROUTING CLASSIFIER TEST SUITE (ZERO-DEPENDENCY RUNNER)")
    print("=" * 70)
    result = runner.run(suite)
    print("=" * 70)
    print(f"Summary: {result.testsRun} tests run, {len(result.errors)} errors, {len(result.failures)} failures.")
    return 0 if result.wasSuccessful() else 1


if __name__ == "__main__":
    sys.exit(run_standalone_suite())
