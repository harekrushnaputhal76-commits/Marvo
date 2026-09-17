"""
Marvo Routing Classifier — classifier.py
=========================================
Fast, synchronous, heuristic-based intent & routing classifier that decides
whether a query can be handled LOCALLY (on-device, custom QA, device intents,
chit-chat) or must ESCALATE to online cloud intelligence (real-time data,
complex logic, multi-step computation).

Execution Guarantees:
- Zero network I/O
- Zero model calls (neither local LLM nor cloud API)
- Pure function execution: query string -> typed ClassificationResult
- Deterministic O(N) worst-case time bound over bounded string length (N <= 1000)
- Full conformance with C4 contract (docs/traffic-police-response-contract.md)
"""

from dataclasses import dataclass, field
from enum import Enum
import re
from typing import Dict, Any, Tuple, Set


class RouteCategory(str, Enum):
    """Primary routing destination for Traffic Police."""
    LOCAL = "LOCAL"
    ESCALATE = "ESCALATE"


class ResponseIntentType(str, Enum):
    """
    Canonical intent types establishing UI presentation contracts (Group B & C4).
    Matches exact casing: ACTION, INFO, CONVERSATION, NONE.
    """
    ACTION = "ACTION"
    INFO = "INFO"
    CONVERSATION = "CONVERSATION"
    NONE = "NONE"


@dataclass(frozen=True)
class ClassificationResult:
    """
    Typed, immutable result returned by classify_intent.

    Attributes:
        route: RouteCategory.LOCAL or RouteCategory.ESCALATE.
        intent_type: ResponseIntentType (ACTION, INFO, CONVERSATION, NONE).
        reason: Explanatory rationale token for routing diagnostics & telemetry.
        matched_signals: Tuple of specific keywords, tokens, or pattern names matched.
        confidence: Heuristic confidence score (0.0 to 1.0).
    """
    route: RouteCategory
    intent_type: ResponseIntentType
    reason: str
    matched_signals: Tuple[str, ...] = field(default_factory=tuple)
    confidence: float = 1.0

    def to_dict(self) -> Dict[str, Any]:
        """Convert classification result to a JSON-serializable dictionary."""
        return {
            "route": self.route.value,
            "intent_type": self.intent_type.value,
            "reason": self.reason,
            "matched_signals": list(self.matched_signals),
            "confidence": self.confidence,
        }


# ─────────────────────────────────────────────────────────────────────────────
# COMPILED HEURISTIC PATTERNS & TOKEN SETS
# ─────────────────────────────────────────────────────────────────────────────

# Maximum bounded input length to guarantee strictly linear O(N) execution
MAX_QUERY_LEN = 1000

# Query complexity threshold (word count and character count)
MAX_LOCAL_WORD_COUNT = 32
MAX_LOCAL_CHAR_COUNT = 200

# 1. Device Action / Local Command Patterns -> LOCAL + ACTION
ACTION_REGEXES = [
    re.compile(r"^(?:open|launch|start|run)\s+([a-z0-9_.\-\s]+)$", re.IGNORECASE),
    re.compile(r"^(?:turn|switch)\s+(?:on|off)\s+([a-z0-9_.\-\s]+)$", re.IGNORECASE),
    re.compile(r"^(?:enable|disable|toggle)\s+([a-z0-9_.\-\s]+)$", re.IGNORECASE),
    re.compile(r"^(?:set|create)\s+(?:an?\s+)?(?:alarm|timer|reminder|countdown)\b", re.IGNORECASE),
    re.compile(r"^(?:call|dial|phone|text|sms|message)\s+([a-z0-9_.\-\s]+)$", re.IGNORECASE),
    re.compile(r"^(?:play|pause|resume|stop|skip|next|previous)\s*(?:song|music|track|audio|video)?\b", re.IGNORECASE),
    re.compile(r"^(?:mute|unmute|silence)\b", re.IGNORECASE),
    re.compile(r"^(?:increase|decrease|raise|lower|adjust)\s+(?:the\s+)?(?:volume|brightness|sound)\b", re.IGNORECASE),
    re.compile(r"^(?:volume|brightness)\s+(?:up|down|max|min|\d+%?)\b", re.IGNORECASE),
    re.compile(r"^(?:take|capture)\s+(?:a\s+)?(?:photo|picture|screenshot|selfie|snapshot)\b", re.IGNORECASE),
    re.compile(r"^(?:turn\s+on\s+|enable\s+)?flashlight\b", re.IGNORECASE),
    re.compile(r"^(?:clear|clean)\s+(?:recent\s+apps|cache|notifications|memory)\b", re.IGNORECASE),
]

# 2. Conversational / Small Talk / Identity Patterns -> LOCAL + CONVERSATION
CONVERSATION_REGEXES = [
    re.compile(r"^(?:hi|hello|hey|howdy|hola|sup|yo|greetings)(?:\s+marvo)?(?:[!.? ]*)$", re.IGNORECASE),
    re.compile(r"\bhow are you(?:\s+doing)?(?:\s+today)?\b", re.IGNORECASE),
    re.compile(r"\bhow(?:'s|\s+is)\s+(?:it\s+going|your\s+day|everything)\b", re.IGNORECASE),
    re.compile(r"\bgood\s+(?:morning|afternoon|evening|night|day)\b", re.IGNORECASE),
    re.compile(r"\bwho\s+(?:are\s+you|created\s+you|made\s+you|built\s+you)\b", re.IGNORECASE),
    re.compile(r"\bwhat\s+is\s+your\s+name\b", re.IGNORECASE),
    re.compile(r"\bwhat\s+can\s+you\s+do\b", re.IGNORECASE),
    re.compile(r"\btell\s+me\s+a\s+(?:joke|riddle|pun|story)\b", re.IGNORECASE),
    re.compile(r"^(?:thank\s+you|thanks|thx|much\s+appreciated|appreciate\s+it)(?:[!.? ]*)$", re.IGNORECASE),
    re.compile(r"^(?:bye|goodbye|see\s+ya|catch\s+you\s+later)(?:[!.? ]*)$", re.IGNORECASE),
    re.compile(r"^(?:help|help\s+me|what\s+are\s+your\s+features)(?:[!.? ]*)$", re.IGNORECASE),
]

# 3. Real-Time Data Domain Terms -> ESCALATE + INFO
REALTIME_DOMAIN_TERMS: Set[str] = {
    "weather", "forecast", "temperature", "humidity", "rain", "snow",
    "climate", "wind", "storm", "hurricane", "tornado",
    "stock", "stocks", "shares", "crypto", "cryptocurrency", "bitcoin",
    "btc", "ethereum", "eth", "nasdaq", "dow", "sp500", "market", "ticker",
    "score", "scores", "standings", "match", "tournament", "ipl", "fifa",
    "news", "headlines", "headline", "breaking",
    "traffic", "transit", "flight", "flights", "delay", "delays",
    "price", "prices", "exchange", "rate", "currency", "inflation",
}

# Real-Time Temporal Signals -> ESCALATE (when combined with informational queries)
REALTIME_TEMPORAL_TERMS: Set[str] = {
    "today", "tonight", "tomorrow", "yesterday", "current", "currently",
    "latest", "recent", "recently", "now", "live", "right now", "upcoming",
}

# 4. Heavy Computation & Deep Reasoning Terms -> ESCALATE + INFO
REASONING_TERMS: Set[str] = {
    "calculate", "computation", "compute", "solve", "derivative", "integral",
    "differential", "equation", "algebra", "trigonometry", "matrix",
    "algorithm", "debug", "refactor", "implement", "optimize",
    "summarize", "proof", "prove", "evaluate", "translate", "transcribe",
    "analyze", "analysis", "compare", "comparison", "benchmark",
}

REASONING_PHRASES = [
    re.compile(r"\bstep\s+by\s+step\b", re.IGNORECASE),
    re.compile(r"\bin\s+detail\b", re.IGNORECASE),
    re.compile(r"\bin[\s-]depth\b", re.IGNORECASE),
    re.compile(r"\bpros\s+and\s+cons\b", re.IGNORECASE),
    re.compile(r"\bcompare\s+and\s+contrast\b", re.IGNORECASE),
    re.compile(r"\bwrite\s+(?:a\s+)?(?:code|script|program|function|class|algorithm|regex)\b", re.IGNORECASE),
    re.compile(r"\bwrite\s+(?:a\s+)?(?:essay|speech|article|cover\s+letter|resume|story|poem)\b", re.IGNORECASE),
    re.compile(r"\bhow\s+do\s+i\s+(?:code|program|implement|derive|prove)\b", re.IGNORECASE),
    re.compile(r"\bpython\s+(?:code|script|function)\b", re.IGNORECASE),
]

# 5. Basic Informational Query Starters -> Default LOCAL + INFO
BASIC_INFO_PREFIXES = [
    re.compile(r"^(?:what|who|when|where|why|how|which)\b", re.IGNORECASE),
    re.compile(r"^(?:define|meaning\s+of|explain|tell\s+me\s+about)\b", re.IGNORECASE),
]


# ─────────────────────────────────────────────────────────────────────────────
# CLASSIFICATION LOGIC (PURE FUNCTION)
# ─────────────────────────────────────────────────────────────────────────────

def classify_intent(query: str) -> ClassificationResult:
    """
    Pure synchronous heuristic intent & routing classifier.

    Evaluates user input strictly in-memory through pre-compiled regular
    expressions and bounded token set lookups. Never performs I/O or model calls.

    Args:
        query: Raw input string from voice STT or chat interface.

    Returns:
        ClassificationResult: Frozen dataclass with:
            - route: RouteCategory (LOCAL or ESCALATE)
            - intent_type: ResponseIntentType (ACTION, INFO, CONVERSATION, NONE)
            - reason: Deterministic heuristic rationale token
            - matched_signals: Specific keywords/regexes that triggered the classification
            - confidence: Heuristic confidence score (0.0 to 1.0)
    """
    # 1. Bounded sanitization & normalization
    if not query or not isinstance(query, str):
        return ClassificationResult(
            route=RouteCategory.LOCAL,
            intent_type=ResponseIntentType.NONE,
            reason="empty_or_invalid_input",
            matched_signals=(),
            confidence=1.0,
        )

    # Enforce maximum length bound for O(N) linear time guarantee
    clamped = query[:MAX_QUERY_LEN].strip()
    if not clamped:
        return ClassificationResult(
            route=RouteCategory.LOCAL,
            intent_type=ResponseIntentType.NONE,
            reason="empty_query_after_strip",
            matched_signals=(),
            confidence=1.0,
        )

    # Normalize whitespace & casing
    normalized = " ".join(clamped.lower().split())

    # Fast token set extraction for O(1) set membership lookups
    tokens = set(re.findall(r"\b[a-z0-9_'-]+\b", normalized))
    token_count = len(normalized.split())

    # 2. Priority Check: Device Action / Local OS Command
    # Device actions (e.g. "turn on flashlight", "open camera", "set alarm")
    # are handled locally on device via native Android intents or systemops.
    for pattern in ACTION_REGEXES:
        match = pattern.search(normalized)
        if match:
            matched_text = match.group(0)
            return ClassificationResult(
                route=RouteCategory.LOCAL,
                intent_type=ResponseIntentType.ACTION,
                reason="device_action_command",
                matched_signals=(f"pattern:{pattern.pattern}", f"match:{matched_text}"),
                confidence=0.98,
            )

    # 3. Priority Check: Conversational Small Talk / Greeting
    # E.g. "hello", "how are you today", "who are you", "tell me a joke".
    # Evaluated before temporal words to ensure "how are you today" does not
    # accidentally escalate on the word "today".
    for pattern in CONVERSATION_REGEXES:
        if pattern.search(normalized):
            return ClassificationResult(
                route=RouteCategory.LOCAL,
                intent_type=ResponseIntentType.CONVERSATION,
                reason="conversational_greeting_or_smalltalk",
                matched_signals=(f"pattern:{pattern.pattern}",),
                confidence=0.95,
            )

    # 4. Check for Real-Time Data Signals -> ESCALATE
    realtime_domain_hits = tokens & REALTIME_DOMAIN_TERMS
    realtime_temporal_hits = tokens & REALTIME_TEMPORAL_TERMS

    # If query mentions a real-time domain (weather, stocks, news, score, etc.),
    # it always requires live data.
    if realtime_domain_hits:
        signals = sorted(list(realtime_domain_hits | realtime_temporal_hits))
        return ClassificationResult(
            route=RouteCategory.ESCALATE,
            intent_type=ResponseIntentType.INFO,
            reason="real_time_domain_data_required",
            matched_signals=tuple(signals),
            confidence=0.95,
        )

    # If query mentions temporal terms ("today", "latest", "current", "now")
    # in an informational question context, escalate to online search.
    if realtime_temporal_hits:
        is_info_starter = any(p.search(normalized) for p in BASIC_INFO_PREFIXES)
        if is_info_starter:
            signals = sorted(list(realtime_temporal_hits))
            return ClassificationResult(
                route=RouteCategory.ESCALATE,
                intent_type=ResponseIntentType.INFO,
                reason="real_time_temporal_information_required",
                matched_signals=tuple(signals),
                confidence=0.90,
            )

    # 5. Check for Heavy Computation & Deep Reasoning Signals -> ESCALATE
    reasoning_hits = tokens & REASONING_TERMS
    matched_phrases = [p.pattern for p in REASONING_PHRASES if p.search(normalized)]

    if reasoning_hits or matched_phrases:
        signals = sorted(list(reasoning_hits)) + [f"phrase:{p}" for p in matched_phrases]
        return ClassificationResult(
            route=RouteCategory.ESCALATE,
            intent_type=ResponseIntentType.INFO,
            reason="heavy_reasoning_or_computation_required",
            matched_signals=tuple(signals),
            confidence=0.92,
        )

    # 6. Check for Query Length / High Complexity Threshold -> ESCALATE
    # Long, multi-sentence prompts exceed the scope of local QA / on-device cache.
    if token_count >= MAX_LOCAL_WORD_COUNT or len(normalized) >= MAX_LOCAL_CHAR_COUNT:
        return ClassificationResult(
            route=RouteCategory.ESCALATE,
            intent_type=ResponseIntentType.INFO,
            reason="high_query_length_and_complexity",
            matched_signals=(f"word_count:{token_count}", f"char_count:{len(normalized)}"),
            confidence=0.88,
        )

    # 7. Basic Informational Query Pattern -> Default LOCAL (custom_qa / knowledge base)
    # Queries like "what is photosynthesis", "capital of france", "define gravity"
    # are routed to LOCAL first for instant retrieval from custom QA / offline knowledge base.
    for pattern in BASIC_INFO_PREFIXES:
        if pattern.search(normalized):
            return ClassificationResult(
                route=RouteCategory.LOCAL,
                intent_type=ResponseIntentType.INFO,
                reason="basic_fact_local_lookup",
                matched_signals=(f"prefix:{pattern.pattern}",),
                confidence=0.85,
            )

    # 8. Universal Default -> LOCAL + CONVERSATION
    # General chat, short inputs, and unclassified queries default to LOCAL
    # to maintain low latency, save battery, and preserve cloud quota.
    return ClassificationResult(
        route=RouteCategory.LOCAL,
        intent_type=ResponseIntentType.CONVERSATION,
        reason="default_local_fallback",
        matched_signals=(),
        confidence=0.80,
    )


def route_query(query: str) -> Dict[str, Any]:
    """
    Convenience wrapper returning the classification as a dictionary.
    """
    return classify_intent(query).to_dict()
