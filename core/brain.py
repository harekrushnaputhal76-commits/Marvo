"""
Marvo Brain — brain.py
======================
The AI reasoning engine. Connects to Google Gemini for online inference
and falls back to a local knowledge base when offline or quota-exceeded.

Data Flow:
  1. User message → think_and_respond(msg, thinking_mode, session_id)
  2. Online path  → Multi-model Gemini cascade (gemini-flash-latest, etc.)
  3. Offline path → custom_qa.json lookup → (response_text, state)
  4. Returned to server.py → JSON to frontend → eye animation
"""

import os
import re
import json
import logging
from pathlib import Path
from collections import OrderedDict

# ──────────────────────────────────────────────────────────────────────
# ENV LOADING
# ──────────────────────────────────────────────────────────────────────
from dotenv import load_dotenv

_project_root = Path(__file__).resolve().parent.parent  # marvo/

_env_path = _project_root / ".env"
if _env_path.is_file():
    load_dotenv(_env_path, override=True)
else:
    load_dotenv()

# ──────────────────────────────────────────────────────────────────────
# API KEY CONFIGURATION
# Dynamically loaded from .env or system environment via os.environ.get()
# ──────────────────────────────────────────────────────────────────────
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY")

# ──────────────────────────────────────────────────────────────────────
# GOOGLE GENAI SDK SETUP
# Multi-model cascade: If one model hits quota (429) or is deprecated (404),
# automatically fail over to the next model seamlessly.
# ──────────────────────────────────────────────────────────────────────
from google import genai
from google.genai import types

MODELS = [
    "gemini-flash-latest",
    "gemini-flash-lite-latest",
    "gemini-3.6-flash",
]

_client = None

# LRU Cache for multi-session chat history (Max 10 sessions to prevent RAM leaks)
_MAX_SESSIONS = 10
_chat_sessions = OrderedDict()

# ──────────────────────────────────────────────────────────────────────
# SYSTEM PROMPT — Polite, Intelligent & Friendly Assistant Persona
# ──────────────────────────────────────────────────────────────────────
SYSTEM_PROMPT = """You are Marvo, a highly intelligent, polite, and helpful personal AI assistant. Provide concise, optimized, and friendly answers. Never be rude or overly sarcastic. If the user greets you, respond warmly and ask how you can help today.

GUIDELINES:
1. Always maintain a warm, welcoming, and courteous tone.
2. If the user greets you (e.g. "hi", "hello", "hey", "namaste"), greet them warmly and ask how you can assist them today.
3. Be concise and optimized. Give clear, directly useful answers without fluff or unhelpful filler.
4. Never be sarcastic, dismissive, or abrasive. Treat every question with respect and helpfulness.
5. Support multilingual / Hinglish questions naturally and fluently whenever addressed in Hindi/Hinglish.
6. Provide accurate, high-intelligence reasoning and structured markdown formatting when helpful."""

_KB_PATH = _project_root / "knowledge_base" / "custom_qa.json"
_logger = logging.getLogger("marvo.brain")


def _get_client():
    """Lazily initialize or return the Gemini client."""
    global _client
    if _client is not None:
        return _client

    key = os.environ.get("GEMINI_API_KEY") or GEMINI_API_KEY
    if not key:
        _logger.warning("No Gemini API key found. Set GEMINI_API_KEY in your .env file.")
        return None

    try:
        _client = genai.Client(api_key=key)
        return _client
    except Exception as e:
        _logger.error(f"Failed to create Gemini client: {e}")
        return None


def _get_mode_config(thinking_mode: str):
    """Return max_tokens and temperature tuned for the selected thinking mode."""
    mode = (thinking_mode or "medium").lower()
    if mode == "fast":
        return 256, 0.7
    elif mode == "high":
        return 1024, 0.85
    return 512, 0.8


def _get_or_create_chat(model_name: str, session_id: str = "default", thinking_mode: str = "medium"):
    """
    Get or create a chat session for the given model and session_id.
    Maintains LRU order and caps active sessions at _MAX_SESSIONS.
    """
    client = _get_client()
    if client is None:
        return None

    cache_key = f"{session_id}::{model_name}"

    if cache_key in _chat_sessions:
        _chat_sessions.move_to_end(cache_key)
        return _chat_sessions[cache_key]

    max_tokens, temp = _get_mode_config(thinking_mode)

    try:
        new_chat = client.chats.create(
            model=model_name,
            config=types.GenerateContentConfig(
                system_instruction=SYSTEM_PROMPT,
                temperature=temp,
                max_output_tokens=max_tokens,
            ),
        )
        if len(_chat_sessions) >= _MAX_SESSIONS:
            _chat_sessions.popitem(last=False)

        _chat_sessions[cache_key] = new_chat
        return new_chat
    except Exception as e:
        _logger.warning(f"Failed to create chat with model '{model_name}': {e}")
        return None


# ──────────────────────────────────────────────────────────────────────
# OFFLINE FALLBACK
# ──────────────────────────────────────────────────────────────────────
def offline_fallback_response(query: str) -> tuple[str, str]:
    """Search custom_qa.json for a matching local answer."""
    cleaned = re.sub(r"[^\w\s]", "", query.lower()).strip()
    if not cleaned:
        return ("I didn't catch that. Say something?", "state-idle")

    try:
        if _KB_PATH.is_file():
            with open(_KB_PATH, "r", encoding="utf-8") as f:
                kb_data = json.load(f)

            # 1. Exact or substring match
            for entry in kb_data:
                q_raw = entry.get("q", "").lower()
                q_clean = re.sub(r"[^\w\s]", "", q_raw).strip()
                if q_clean and (cleaned == q_clean or cleaned in q_clean or q_clean in cleaned):
                    ans = entry.get("a", "")
                    if ans:
                        return (ans, "state-speaking")

            # 2. Token overlap match
            query_words = set(cleaned.split())
            best_match = None
            best_score = 0
            for entry in kb_data:
                q_words = set(re.sub(r"[^\w\s]", "", entry.get("q", "").lower()).split())
                overlap = len(query_words & q_words)
                if overlap > best_score and overlap >= 1:
                    best_score = overlap
                    best_match = entry.get("a", "")

            if best_match and best_score >= 1:
                return (best_match, "state-speaking")

    except Exception as e:
        _logger.error(f"Offline KB read error: {e}")

    return (
        "I'm currently offline and couldn't find that in my local memory. "
        "Check your connection and try again — I'm at full power when online.",
        "state-idle"
    )


# ──────────────────────────────────────────────────────────────────────
# MAIN PUBLIC ENTRY POINT
# ──────────────────────────────────────────────────────────────────────
def think_and_respond(
    user_message: str,
    thinking_mode: str = "medium",
    session_id: str = "default"
) -> tuple[str, str]:
    """
    Process a user message and return (response_text, animation_state).
    Uses a multi-model failover cascade so quota exhaustion on one model
    automatically falls through to available models before going offline.
    """
    if not user_message or not user_message.strip():
        return ("I didn't catch that. Could you say something?", "state-idle")

    # Try each model in the cascade
    for model_name in MODELS:
        chat = _get_or_create_chat(model_name, session_id=session_id, thinking_mode=thinking_mode)
        if chat is None:
            continue

        try:
            response = chat.send_message(user_message.strip())
            reply_text = response.text.strip() if response and response.text else ""

            if reply_text:
                return (reply_text, "state-speaking")

        except Exception as e:
            err_str = str(e)
            _logger.warning(f"Model '{model_name}' failed ({err_str[:60]}), failing over to next model...")
            cache_key = f"{session_id}::{model_name}"
            _chat_sessions.pop(cache_key, None)
            continue  # Try next model

    # If all online models fail or offline, trigger local knowledge base
    _logger.info("All online models exhausted or unreachable. Triggering offline fallback.")
    return offline_fallback_response(user_message)
