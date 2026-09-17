"""
Unit tests for Marvo Routing Classifier (core/classifier.py).
"""

import unittest
from core.classifier import (
    classify_intent,
    route_query,
    RouteCategory,
    ResponseIntentType,
    ClassificationResult,
)


class TestClassifier(unittest.TestCase):

    def test_empty_and_whitespace_queries(self):
        for empty_val in ["", "   ", "\t\n", None]:
            res = classify_intent(empty_val)
            self.assertEqual(res.route, RouteCategory.LOCAL)
            self.assertEqual(res.intent_type, ResponseIntentType.NONE)

    def test_device_action_commands(self):
        action_queries = [
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
            "take a selfie",
            "volume up",
            "mute",
            "play music",
            "pause song",
        ]
        for query in action_queries:
            with self.subTest(query=query):
                res = classify_intent(query)
                self.assertEqual(res.route, RouteCategory.LOCAL)
                self.assertEqual(res.intent_type, ResponseIntentType.ACTION)
                self.assertEqual(res.reason, "device_action_command")

    def test_conversational_and_smalltalk(self):
        conversational_queries = [
            "hello",
            "hi marvo",
            "hey there",
            "how are you today",
            "how are you doing",
            "good morning",
            "who are you",
            "what is your name",
            "tell me a joke",
            "thank you",
            "thanks marvo",
            "goodbye",
            "help me",
        ]
        for query in conversational_queries:
            with self.subTest(query=query):
                res = classify_intent(query)
                self.assertEqual(res.route, RouteCategory.LOCAL)
                self.assertEqual(res.intent_type, ResponseIntentType.CONVERSATION)

    def test_realtime_data_queries_escalate(self):
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

    def test_heavy_computation_and_reasoning_escalate(self):
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

    def test_high_complexity_and_length_escalate(self):
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

    def test_basic_facts_route_local_by_default(self):
        basic_facts = [
            "what is photosynthesis",
            "who was Albert Einstein",
            "where is the Eiffel Tower",
            "when was the printing press invented",
            "define gravity",
            "tell me about Mars",
        ]
        for query in basic_facts:
            with self.subTest(query=query):
                res = classify_intent(query)
                self.assertEqual(res.route, RouteCategory.LOCAL)
                self.assertEqual(res.intent_type, ResponseIntentType.INFO)
                self.assertEqual(res.reason, "basic_fact_local_lookup")

    def test_unmatched_fallback_defaults_to_local(self):
        res = classify_intent("banana avocado orange")
        self.assertEqual(res.route, RouteCategory.LOCAL)
        self.assertEqual(res.intent_type, ResponseIntentType.CONVERSATION)
        self.assertEqual(res.reason, "default_local_fallback")

    def test_pure_function_determinism_and_immutability(self):
        query = "what is the weather today in London"
        res1 = classify_intent(query)
        res2 = classify_intent(query)
        self.assertEqual(res1, res2)
        # Frozen dataclass should reject modification
        with self.assertRaises(Exception):
            res1.route = RouteCategory.LOCAL

    def test_input_clamping_on_oversized_string(self):
        huge_query = "hello " * 1000  # 6000 characters
        res = classify_intent(huge_query)
        # Should execute safely without timeout or crash
        self.assertIsInstance(res, ClassificationResult)

    def test_dict_serialization(self):
        res = classify_intent("turn on flashlight")
        d = res.to_dict()
        self.assertEqual(d["route"], "LOCAL")
        self.assertEqual(d["intent_type"], "ACTION")
        self.assertIn("device_action_command", d["reason"])
        self.assertIsInstance(d["matched_signals"], list)
        self.assertEqual(d["confidence"], 0.98)


if __name__ == "__main__":
    unittest.main()
