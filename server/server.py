import os
import sys
import json
import logging
import threading
from datetime import datetime, timezone
from flask import Flask, request, jsonify, send_from_directory
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
        from core.brain import think_and_respond
        def handle_request(message, thinking_mode='medium', session_id='default', local_time=None):
            resp, state = think_and_respond(message, thinking_mode=thinking_mode, session_id=session_id)
            return {"type": "text", "response": resp, "state": state, "session_id": session_id}
    except Exception:
        def handle_request(message, thinking_mode='medium', session_id='default', local_time=None):
            return {"type": "text", "response": "Brain module is offline.", "state": "state-idle", "session_id": session_id}

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

        mode_param = data.get('mode') or data.get('thinking_mode', 'Thinking')

        # Log session context
        logging.info(f"Chat request - Session: {session_id} | Mode: {mode_param} | Msg: {user_message[:60]}")

        local_time = data.get('local_time')
        agent_persona = data.get('agent')

        # Process the message through the Agent Manager
        result = handle_request(
            message=user_message,
            thinking_mode=thinking_mode,
            mode=mode_param,
            session_id=session_id,
            local_time=local_time,
            agent=agent_persona
        )

        resp_type = result.get("type", "text")
        ai_response = result.get("response", "")
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

            return jsonify({
                "type": "text",
                "response": ai_response,
                "state": animation_state,
                "session_id": session_id
            }), 200

    except Exception as e:
        logging.error(f"Server Error during chat processing: {str(e)}", exc_info=True)
        return jsonify({
            "response": "I encountered an internal error. Please check the logs.",
            "state": "state-idle"
        }), 500


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
    app.run(host='0.0.0.0', port=port, debug=False)