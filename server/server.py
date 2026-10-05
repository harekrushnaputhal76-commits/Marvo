import os
import sys
import json
import logging
import threading
from datetime import datetime, timezone
from flask import Flask, request, jsonify, send_from_directory, Response
from flask_cors import CORS

# Ensure the project root (marvo/) is on sys.path so 'core' package is importable
# regardless of which directory the server is launched from.
_project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _project_root not in sys.path:
    sys.path.insert(0, _project_root)

_sessions_dir = os.path.join(_project_root, 'sessions')
os.makedirs(_sessions_dir, exist_ok=True)

_frontend_dir = os.path.join(_project_root, 'frontend')

# Safe loading for .env using python-dotenv
try:
    from dotenv import load_dotenv
    _env_path = os.path.join(_project_root, '.env')
    if os.path.isfile(_env_path):
        load_dotenv(_env_path, override=True)
    else:
        load_dotenv()
except Exception:
    pass

import urllib.parse

# 1. Direct connection to your AI's Agent Manager & Core Brain
try:
    from agents.manager import handle_request
except Exception as _agent_err:
    logging.error(f"Failed to import agents.manager: {_agent_err}", exc_info=True)
    try:
        from core.traffic_police import route_traffic
        def handle_request(message, thinking_mode='medium', session_id='default', local_time=None, **kwargs):
            res = route_traffic(message, thinking_mode=thinking_mode, session_id=session_id)
            d = res.to_dict()
            d["type"] = "text"
            d["session_id"] = session_id
            d["response"] = res.response_text or (res.error.message if res.error else "")
            d["state"] = "state-speaking" if res.success else "state-error"
            return d
    except Exception:
        from core.response_schema import RouterResponse, ResponseSource, ResponseIntentType, ErrorCode, ResponseError
        def handle_request(message, thinking_mode='medium', session_id='default', local_time=None, **kwargs):
            err_res = RouterResponse(
                success=False,
                response_text="",
                source=ResponseSource.OFFLINE,
                intent_type=ResponseIntentType.NONE,
                error=ResponseError(
                    code=ErrorCode.INTERNAL_ERROR,
                    message="Traffic Police routing module is currently unavailable.",
                    retryable=True,
                ),
            )
            d = err_res.to_dict()
            d["type"] = "text"
            d["session_id"] = session_id or "default"
            d["response"] = ""
            d["state"] = "state-error"
            return d

# 2. Voice Module Integration
try:
    from core.voice import marvo_voice
except Exception as _voice_err:
    logging.error(f"Failed to import core.voice: {_voice_err}", exc_info=True)
    class DummyVoice:
        @staticmethod
        def speak(text, voice_id='voice_3'):
            return ""
        @staticmethod
        def generate_audio_base64(text, voice_id='voice_3'):
            return ""
        @staticmethod
        def play_sample(voice_id='voice_3'):
            return ""
        @staticmethod
        def get_sample_audio_base64(voice_id='voice_3'):
            return ""
    marvo_voice = DummyVoice()

# 3. Logging Setup (Dual: file logging + stdout for Render/Gunicorn console)
log_path = os.path.join(_project_root, 'logs', 'apperror.log')
os.makedirs(os.path.dirname(log_path), exist_ok=True)
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler(log_path, encoding='utf-8'),
        logging.StreamHandler(sys.stdout)
    ]
)

# Initialize Flask with explicit frontend static & template folders
app = Flask(
    __name__,
    static_folder=_frontend_dir,
    static_url_path='',
    template_folder=_frontend_dir
)
CORS(app)  # Allows cross-origin requests securely


@app.route('/')
@app.route('/index.html')
def serve_index():
    """Serves the frontend single-page interface."""
    return send_from_directory(_frontend_dir, 'index.html')


@app.route('/<path:filename>')
def serve_static(filename):
    """Serves frontend static assets (style.css, app.js, etc.)."""
    if filename.startswith('api/'):
        return jsonify({"error": "Endpoint not found"}), 404
    file_path = os.path.join(_frontend_dir, filename)
    if os.path.isfile(file_path):
        return send_from_directory(_frontend_dir, filename)
    return send_from_directory(_frontend_dir, 'index.html')


@app.route('/health')
@app.route('/healthz')
def health_check():
    """Health check endpoint for Render zero-downtime deployment & uptime monitors."""
    return jsonify({"status": "healthy", "service": "marvo-ai"}), 200


# ─────────────────────────────────────────────────────────────────────────────
# AI_LIMITS.md ENFORCEMENT: PER-SESSION RPM & DAILY BUDGET
# ─────────────────────────────────────────────────────────────────────────────
_limits_lock = threading.Lock()
_session_request_history = {}  # session_id -> list of float timestamps (last 60s)
_daily_budget_state = {
    "date": datetime.now(timezone.utc).strftime("%Y-%m-%d"),
    "request_count": 0,
    "token_count": 0,
}

MAX_SESSION_RPM = 15
DAILY_REQUEST_BUDGET = 1500
DAILY_TOKEN_BUDGET = 1000000


def _check_and_update_limits(session_id: str):
    """
    Evaluates limits specified in docs/AI_LIMITS.md:
    1. Per-session request-rate cap (15 RPM)
    2. Daily request and token budget for shared Gemini key
    Returns: (allowed: bool, error_payload: Optional[dict], http_status: int)
    """
    now = datetime.now(timezone.utc).timestamp()
    today_str = datetime.now(timezone.utc).strftime("%Y-%m-%d")

    with _limits_lock:
        # 1. Reset daily budget if UTC date changed
        if _daily_budget_state["date"] != today_str:
            _daily_budget_state["date"] = today_str
            _daily_budget_state["request_count"] = 0
            _daily_budget_state["token_count"] = 0

        # 2. Check Daily Budget Limit (1,500 requests or 1,000,000 tokens)
        if (_daily_budget_state["request_count"] >= DAILY_REQUEST_BUDGET or 
            _daily_budget_state["token_count"] >= DAILY_TOKEN_BUDGET):
            return False, {
                "error": "Daily cloud budget reached. Operating on local offline brain.",
                "daily_budget_exhausted": True,
                "fallback_to_local": True,
                "type": "text",
                "response": "ℹ️ **Daily Cloud Budget Reached**\n\nDaily cloud budget reached. Operating on local offline brain.",
                "state": "state-idle"
            }, 429

        # 3. Check Session RPM (15 requests in rolling 60 seconds)
        timestamps = _session_request_history.get(session_id, [])
        cutoff = now - 60.0
        timestamps = [t for t in timestamps if t > cutoff]
        _session_request_history[session_id] = timestamps

        if len(timestamps) >= MAX_SESSION_RPM:
            return False, {
                "error": "Cloud query rate limit reached. Routing locally.",
                "rate_limited": True,
                "fallback_to_local": True,
                "type": "text",
                "response": "⚠️ **Cloud Rate Limit (15 RPM) Reached**\n\nCloud query rate limit reached. Routing locally.",
                "state": "state-idle"
            }, 429

        # Record this request timestamp and increment request count
        timestamps.append(now)
        _session_request_history[session_id] = timestamps
        _daily_budget_state["request_count"] += 1
        return True, None, 200


def _record_token_usage(prompt: str, response_text: str):
    """Approximates token consumption (~4 chars per token) and updates daily budget."""
    approx_tokens = (len(prompt or '') + len(response_text or '')) // 4
    with _limits_lock:
        _daily_budget_state["token_count"] += approx_tokens


@app.route('/api/config', methods=['GET'])
def get_config():
    """Returns active model provider configuration and resource limits from environment."""
    with _limits_lock:
        used_reqs = _daily_budget_state["request_count"]
        used_tokens = _daily_budget_state["token_count"]
        exhausted = (used_reqs >= DAILY_REQUEST_BUDGET or used_tokens >= DAILY_TOKEN_BUDGET)
    return jsonify({
        "gemini_available": bool(os.environ.get("GEMINI_API_KEY", "")),
        "daily_requests_used": used_reqs,
        "daily_requests_budget": DAILY_REQUEST_BUDGET,
        "daily_tokens_used": used_tokens,
        "daily_tokens_budget": DAILY_TOKEN_BUDGET,
        "daily_budget_exhausted": exhausted,
        "hf_api_key": os.environ.get("HF_API_KEY", "")
    }), 200


def _session_path(session_id):
    if not isinstance(session_id, str) or not session_id or os.path.basename(session_id) != session_id:
        return None
    return os.path.join(_sessions_dir, f'{session_id}.json')


def _read_session(session_id):
    path = _session_path(session_id)
    if path is None or not os.path.isfile(path):
        return None
    try:
        with open(path, 'r', encoding='utf-8') as session_file:
            session = json.load(session_file)
    except (OSError, json.JSONDecodeError):
        return None
    if not isinstance(session, dict) or not isinstance(session.get('messages'), list):
        return {'session_id': session_id, 'messages': []}
    return session


def _save_session(session_id, messages):
    path = _session_path(session_id)
    if path is None:
        raise ValueError('Invalid session ID')
    session = {
        'session_id': session_id,
        'messages': messages,
        'updated_at': datetime.now(timezone.utc).isoformat()
    }
    with open(path, 'w', encoding='utf-8') as session_file:
        json.dump(session, session_file, ensure_ascii=False, indent=2)


@app.route('/api/chat', methods=['POST'])
def chat_endpoint():
    """
    Receives user message and session metadata from frontend.
    Handles dynamic date/time queries locally to save API tokens,
    otherwise queries the AI brain, saves history, and returns response.
    """
    try:
        data = request.json or {}
        user_message = data.get('message', '').strip()

        # Parse extended payload from frontend
        thinking_mode = data.get('thinking_mode', 'medium')  # fast | medium | high
        session_id = data.get('session_id')

        # Fallback to auto-generated session ID if invalid
        if not session_id or _session_path(session_id) is None:
            session_id = f"s_{int(datetime.now(timezone.utc).timestamp())}"

        # Security Check: Ignore empty messages to save CPU cycles
        if not user_message:
            return jsonify({"error": "Empty message"}), 400

        # AI_LIMITS.md enforcement: Check per-session 15 RPM and daily budget
        allowed, limit_err, status_code = _check_and_update_limits(session_id)
        if not allowed:
            logging.warning(f"[RateLimit] Session {session_id} hit limit: {limit_err.get('error')}")
            return jsonify(limit_err), status_code

        mode_param = data.get('mode') or data.get('thinking_mode', 'Thinking')

        # Log session context
        logging.info(f"Chat request - Session: {session_id} | Mode: {mode_param} | Msg: {user_message[:60]}")

        local_time = data.get('local_time')
        agent_persona = data.get('agent')
        image_base64 = data.get('image_base64') or data.get('multimodal_image')

        # Real Token-by-Token Streaming Support via Server-Sent Events (SSE)
        stream_requested = bool(data.get('stream')) or (request.headers.get('Accept') == 'text/event-stream')
        if stream_requested:
            from agents.manager import is_image_request
            if is_image_request(user_message):
                img_result = handle_request(
                    message=user_message,
                    thinking_mode=thinking_mode,
                    mode=mode_param,
                    session_id=session_id,
                    local_time=local_time,
                    agent=agent_persona,
                    image_base64=image_base64
                )
                def img_stream():
                    if img_result.get("type") == "image":
                        yield f"data: {json.dumps({'type': 'image', 'content': img_result.get('content'), 'prompt': img_result.get('prompt', user_message), 'done': True, 'state': 'state-amazed'})}\n\n"
                    else:
                        yield f"data: {json.dumps({'token': img_result.get('response', ''), 'done': True, 'full_response': img_result.get('response', ''), 'state': 'state-speaking'})}\n\n"
                return Response(img_stream(), mimetype='text/event-stream', headers={'Cache-Control': 'no-cache', 'X-Accel-Buffering': 'no'})

            def generate_gemini_sse():
                try:
                    from google import genai
                    from google.genai import types
                    from core.brain import SYSTEM_PROMPT

                    active_key = data.get('api_key') or os.environ.get('GEMINI_API_KEY')
                    if not active_key:
                        try:
                            from core.key_pool import KeyPool
                            active_key = KeyPool.get_instance().get_active_key_value()
                        except Exception:
                            pass

                    if not active_key:
                        yield f"data: {json.dumps({'error': 'No Gemini API key available.', 'done': True})}\n\n"
                        return

                    client = genai.Client(api_key=active_key)
                    sys_inst = data.get('system_instruction') or SYSTEM_PROMPT

                    cfg = types.GenerateContentConfig(
                        system_instruction=sys_inst,
                        temperature=0.7 if thinking_mode == "fast" else 0.8,
                        max_output_tokens=1024 if thinking_mode == "high" else 512,
                    )

                    models_to_try = ["gemini-2.0-flash", "gemini-flash-latest", "gemini-flash-lite-latest"]
                    stream_obj = None
                    last_err = None

                    for m in models_to_try:
                        try:
                            stream_obj = client.models.generate_content_stream(
                                model=m,
                                contents=user_message,
                                config=cfg,
                            )
                            break
                        except Exception as m_err:
                            last_err = m_err
                            continue

                    if stream_obj is None:
                        raise last_err or Exception("All Gemini streaming models failed.")

                    full_text = ""
                    for chunk in stream_obj:
                        txt = chunk.text or ""
                        if txt:
                            full_text += txt
                            yield f"data: {json.dumps({'token': txt, 'done': False})}\n\n"

                    # Record token usage for budget tracking
                    _record_token_usage(user_message, full_text)

                    # Persist session history
                    session = _read_session(session_id) or {'session_id': session_id, 'messages': []}
                    session['messages'].extend([
                        {'role': 'user', 'content': user_message},
                        {'role': 'assistant', 'content': full_text}
                    ])
                    _save_session(session_id, session['messages'])

                    yield f"data: {json.dumps({'token': '', 'done': True, 'full_response': full_text, 'state': 'state-speaking', 'session_id': session_id})}\n\n"

                except Exception as exc:
                    logging.error(f"[GeminiStream] Error during streaming: {exc}", exc_info=True)
                    yield f"data: {json.dumps({'error': str(exc), 'done': True, 'state': 'state-error'})}\n\n"

            return Response(
                generate_gemini_sse(),
                mimetype='text/event-stream',
                headers={
                    'Cache-Control': 'no-cache',
                    'X-Accel-Buffering': 'no',
                    'Connection': 'keep-alive',
                    'Access-Control-Allow-Origin': '*'
                }
            )

        # Process the message through the Agent Manager (Synchronous non-streaming path)
        result = handle_request(
            message=user_message,
            thinking_mode=thinking_mode,
            mode=mode_param,
            session_id=session_id,
            local_time=local_time,
            agent=agent_persona,
            image_base64=image_base64
        )

        resp_type = result.get("type", "text")
        ai_response = result.get("response", "")

        # Record approximate token usage for daily token budget tracking
        _record_token_usage(user_message, ai_response)
        animation_state = result.get("state", "state-speaking")
        image_content = result.get("content")

        # Save conversation history
        session = _read_session(session_id) or {
            'session_id': session_id,
            'messages': []
        }

        if resp_type == "image" and image_content:
            prompt_used = result.get("prompt", user_message)
            session['messages'].extend([
                {'role': 'user', 'content': user_message},
                {
                    'role': 'assistant',
                    'content': f"__IMAGE_GEN__:{urllib.parse.quote(prompt_used)}:{urllib.parse.quote(image_content)}",
                    'type': 'image',
                    'image_url': image_content,
                    'prompt': prompt_used
                }
            ])
            _save_session(session_id, session['messages'])

            return jsonify({
                "type": "image",
                "content": image_content,
                "prompt": prompt_used,
                "source": result.get("source", "pollinations"),
                "response": ai_response,
                "state": animation_state,
                "session_id": session_id
            }), 200

        else:
            session['messages'].extend([
                {'role': 'user', 'content': user_message},
                {'role': 'assistant', 'content': ai_response}
            ])
            _save_session(session_id, session['messages'])

            # Merge full C4 response contract fields
            payload = {
                "type": "text",
                "success": result.get("success", True),
                "response": ai_response,
                "response_text": result.get("response_text", ai_response),
                "source": result.get("source", "online"),
                "intent_type": result.get("intent_type", "CONVERSATION"),
                "provider": result.get("provider", "gemini"),
                "model": result.get("model", "gemini-flash-latest"),
                "is_fallback": result.get("is_fallback", False),
                "fallback_occurred": result.get("fallback_occurred", False),
                "intent_payload": result.get("intent_payload"),
                "error": result.get("error"),
                "state": animation_state,
                "session_id": session_id
            }
            return jsonify(payload), 200

    except Exception as e:
        logging.error(f"Server Error during chat processing: {str(e)}", exc_info=True)
        from core.response_schema import RouterResponse, ResponseSource, ResponseIntentType, ErrorCode, ResponseError
        err_res = RouterResponse(
            success=False,
            response_text="",
            source=ResponseSource.OFFLINE,
            intent_type=ResponseIntentType.NONE,
            error=ResponseError(
                code=ErrorCode.INTERNAL_ERROR,
                message=f"Server error during chat processing: {str(e)}",
                retryable=True,
                http_status=500,
            ),
        )
        d = err_res.to_dict()
        d["type"] = "text"
        d["session_id"] = session_id or "default"
        d["response"] = ""
        d["state"] = "state-error"
        return jsonify(d), 500


@app.route('/api/speak', methods=['POST'])
def speak_endpoint():
    """
    Accepts { "text": "...", "voice_id": "..." }, calls marvo_voice.generate_audio_base64(text, voice_id),
    and returns a JSON response: { "status": "success", "audio_base64": "<base64_string>" }.
    """
    try:
        data = request.json or {}
        text = data.get('text', '').strip()
        voice_id = data.get('voice_id', 'voice_3')

        if not text:
            return jsonify({"status": "error", "error": "Empty text"}), 400

        audio_base64 = marvo_voice.generate_audio_base64(text, voice_id)
        if not audio_base64:
            return jsonify({"status": "error", "error": "Audio generation failed"}), 500

        return jsonify({
            "status": "success",
            "audio_base64": audio_base64,
            "voice_id": voice_id
        }), 200

    except Exception as e:
        logging.error(f"Error in /api/speak: {str(e)}", exc_info=True)
        return jsonify({"status": "error", "error": str(e)}), 500


@app.route('/api/preview_voice', methods=['POST'])
def preview_voice_endpoint():
    """
    Accepts { "voice_id": "..." }, calls marvo_voice.get_sample_audio_base64(voice_id),
    and returns a JSON response: { "status": "success", "audio_base64": "<base64_string>" }.
    """
    try:
        data = request.json or {}
        voice_id = data.get('voice_id', 'voice_3')

        audio_base64 = marvo_voice.get_sample_audio_base64(voice_id)
        if not audio_base64:
            return jsonify({"status": "error", "error": "Voice preview generation failed"}), 500

        return jsonify({
            "status": "success",
            "audio_base64": audio_base64,
            "voice_id": voice_id
        }), 200

    except Exception as e:
        logging.error(f"Error in /api/preview_voice: {str(e)}", exc_info=True)
        return jsonify({"status": "error", "error": str(e)}), 500


@app.route('/api/sessions', methods=['GET'])
def sessions_endpoint():
    """Returns a list of saved sessions sorted by last updated."""
    sessions = []
    try:
        for filename in os.listdir(_sessions_dir):
            if not filename.endswith('.json') or filename == 'userdata.json':
                continue
            session_id = filename[:-5]
            session = _read_session(session_id)
            if session is None:
                continue
            messages = session.get('messages', [])
            first_message = next(
                (message.get('content', '') for message in messages
                 if message.get('role') == 'user'),
                'New chat'
            )
            sessions.append({
                'session_id': session_id,
                'title': first_message,
                'updated_at': session.get('updated_at', '')
            })
        sessions.sort(key=lambda s: s.get('updated_at', ''), reverse=True)
        return jsonify(sessions), 200
    except Exception as e:
        logging.error(f"Error listing sessions: {e}")
        return jsonify([]), 200


@app.route('/api/history/<session_id>', methods=['GET'])
def history_endpoint(session_id):
    """Returns the message history for a specific session."""
    session = _read_session(session_id)
    if session is None:
        return jsonify({'session_id': session_id, 'messages': []}), 200
    return jsonify(session), 200


if __name__ == '__main__':
    port = int(os.environ.get('PORT', 5000))
    app.run(host='0.0.0.0', port=port, debug=False, threaded=True)