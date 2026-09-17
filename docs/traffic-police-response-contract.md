# Traffic Police Response Contract

**Recorded**: 2026-09-17  
**Authority**: Central Router Specification for Group C (Backend Routing & Key Pool)  
**Cross-Reference**: [Group B Closeout](file:///c:/Users/GUDU/OneDrive/Desktop/marvo/docs/group-b-closeout.md), [Voice Indicator State Contract](file:///c:/Users/GUDU/OneDrive/Desktop/marvo/docs/voice-indicator-state-contract.md)

---

## 1. Overview & Purpose

This contract establishes the **single, repo-wide authoritative schema** for all responses produced by the backend router (Traffic Police), model invocation pipelines, and local engines.

Prior to Group C, failure conditions in `core/brain.py`, `agents/chat_agent.py`, and `server/server.py` silently swallowed real exceptions and returned canned strings (e.g. *"I encountered a hiccup while thinking through that"*) disguised as valid HTTP 200 assistant messages. This prevented the router from detecting rate limits, authenticating key failures, or executing deterministic fallbacks.

This contract strictly enforces:
1. **Explicit generation vs. error distinction** (`success: bool` and structured `error: ResponseError`).
2. **Canonical casing and field names** matching Group B's finalized frontend contract.
3. **Exact intent card schemas** for `ACTION` and `INFO` rendering without client-side guessing.
4. **Structured failure taxonomy** that enables key-pool rotation on 429s and silent offline failover on network loss.

---

## 2. Authoritative Response Schema

Every execution path through the backend router MUST produce a payload conforming to this schema:

```json
{
  "success": true,
  "response_text": "The solar system consists of the Sun and objects bound by gravity...",
  "source": "online",
  "intent_type": "CONVERSATION",
  "provider": "gemini",
  "model": "gemini-flash-latest",
  "is_fallback": false,
  "intent_payload": null,
  "error": null
}
```

### Schema Fields Specification

| Field Name | Type | Permitted Values | Description |
|:---|:---|:---|:---|
| `success` | `boolean` | `true` \| `false` | `true` if the model or offline engine successfully produced a real response; `false` if the request failed. |
| `response_text` | `string` | non-empty on success; empty or brief summary on failure | The actual substantive content produced. **NEVER a canned apology stand-in** (e.g. never *"I had a hiccup"*, *"Brain is offline"*). |
| `source` | `string` | `"online"` \| `"offline"` | Indicates whether the answer was generated via a cloud API or local on-device inference. |
| `intent_type` | `string` | `"ACTION"` \| `"INFO"` \| `"CONVERSATION"` \| `"NONE"` | Exact canonical casing established in Group B (B15/B25). Drives indicator card presentation. |
| `provider` | `string` | `"gemini"` \| `"groq"` \| `"openrouter"` \| `"local"` \| `"knowledge_base"` | The underlying service provider that executed the request. |
| `model` | `string` | e.g. `"gemini-flash-latest"`, `"llama-3.3-70b-versatile"`, `"phi-3-mini"` | Specific model identifier used for inference. |
| `is_fallback` | `boolean` | `true` \| `false` | `true` if the primary model failed (e.g. hit quota) and a secondary or offline engine answered. |
| `intent_payload` | `object` \| `null` | `null` for `CONVERSATION` / `NONE`; structured dict for `ACTION` / `INFO` | Additional payload parameters required by Group B intent cards. |
| `error` | `object` \| `null` | `null` on success; `ResponseError` on failure | Structured error object explaining failure diagnostics. |

---

## 3. Structured Error Representation (`ResponseError`)

When `success` is `false`, `error` MUST be populated with a structured object. The router must never return a plain string or disguise errors as valid chat text.

```json
{
  "success": false,
  "response_text": "",
  "source": "online",
  "intent_type": "NONE",
  "provider": "gemini",
  "model": "gemini-flash-latest",
  "is_fallback": false,
  "intent_payload": null,
  "error": {
    "code": "RATE_LIMITED",
    "message": "Gemini API quota exceeded (429 ResourceExhausted)",
    "retryable": true,
    "http_status": 429,
    "details": {
      "model": "gemini-flash-latest",
      "key_index": 0
    }
  }
}
```

### Standard Error Codes (`error.code`)

| Error Code | HTTP Status | Retryable? | Router Action |
|:---|:---|:---|:---|
| `RATE_LIMITED` | 429 | `true` | **Rotate API key** in key pool (C9) or cascade to next model. |
| `TIMEOUT` | 408 / 504 | `true` | Cascade to next model or trigger local offline engine. |
| `NETWORK_OFFLINE` | 0 / 503 | `false` | Immediately switch to offline on-device engine (GGUF / QA). |
| `AUTH_FAILED` | 401 / 403 | `false` | Flag key as invalid; rotate key pool or notify operator. |
| `MODEL_NOT_FOUND`| 404 | `false` | Model deprecated/unavailable; failover to next model in cascade. |
| `INTERNAL_ERROR` | 500 | `false` | Unhandled exception; emit diagnostic log, do not fake response. |

---

## 4. Intent Card Payload Schemas (`intent_payload`)

When `intent_type` is `ACTION` or `INFO`, `intent_payload` contains the exact fields consumed by `frontend/app.js:3140` (`routeResponseIntent`):

### A. ACTION Intent Card (`intent_type: "ACTION"`)
Used for system tasks, app launching, hardware toggles, alarms, and timers.

```json
{
  "intent_type": "ACTION",
  "icon": "⏰",
  "title": "Set Alarm",
  "target": "Clock",
  "action_type": "APP",
  "status": "Ready",
  "response_text": "Alarm set for 7:00 AM"
}
```
* **Required**: `intent_type` (`"ACTION"`), `response_text`
* **Optional**: `icon` (glyph/symbol), `title` (action header), `target` (app/setting/contact), `action_type` (`"APP"` \| `"CALL"` \| `"TOGGLE"` \| `"NAVIGATE"`), `status` (`"Ready"` \| `"Executing"` \| `"Completed"`)

### B. INFO Intent Card (`intent_type: "INFO"`)
Used for concise informational queries (weather, definitions, system stats, quick facts).

```json
{
  "intent_type": "INFO",
  "icon": "ℹ",
  "title": "System Status",
  "body": "CPU usage is at 18%, 2.4 GB RAM available.",
  "source": "Local System",
  "data_points": [
    { "label": "CPU", "value": "18%" },
    { "label": "Free RAM", "value": "2.4 GB" }
  ],
  "response_text": "CPU usage is at 18%, 2.4 GB RAM available."
}
```
* **Required**: `intent_type` (`"INFO"`), `title`, `body` (or `response_text`)
* **Optional**: `icon`, `source` (attribution), `data_points` (array of `{ "label": string, "value": string }`)

### C. CONVERSATION / NONE (`intent_type: "CONVERSATION" | "NONE"`)
Plain conversational dialogue or reasoning query.
* `intent_payload`: `null` (renders standard conversational capsule without card overlay).

---

## 5. Python Reference Type Implementation

All backend modules in Group C will use this typed dataclass specification:

```python
from dataclasses import dataclass, field
from enum import Enum
from typing import Optional, Dict, Any, List

class ResponseSource(str, Enum):
    ONLINE = "online"
    OFFLINE = "offline"

class ResponseIntentType(str, Enum):
    ACTION = "ACTION"
    INFO = "INFO"
    CONVERSATION = "CONVERSATION"
    NONE = "NONE"

class ErrorCode(str, Enum):
    RATE_LIMITED = "RATE_LIMITED"
    TIMEOUT = "TIMEOUT"
    NETWORK_OFFLINE = "NETWORK_OFFLINE"
    AUTH_FAILED = "AUTH_FAILED"
    MODEL_NOT_FOUND = "MODEL_NOT_FOUND"
    INTERNAL_ERROR = "INTERNAL_ERROR"

@dataclass
class ResponseError:
    code: ErrorCode
    message: str
    retryable: bool = False
    http_status: Optional[int] = None
    details: Optional[Dict[str, Any]] = None

    def to_dict(self) -> Dict[str, Any]:
        return {
            "code": self.code.value if isinstance(self.code, Enum) else self.code,
            "message": self.message,
            "retryable": self.retryable,
            "http_status": self.http_status,
            "details": self.details or {}
        }

@dataclass
class IntentPayload:
    icon: Optional[str] = None
    title: Optional[str] = None
    target: Optional[str] = None
    action_type: Optional[str] = None
    status: Optional[str] = None
    body: Optional[str] = None
    source: Optional[str] = None
    data_points: Optional[List[Dict[str, str]]] = None

    def to_dict(self) -> Dict[str, Any]:
        return {k: v for k, v in self.__dict__.items() if v is not None}

@dataclass
class RouterResponse:
    success: bool
    response_text: str
    source: ResponseSource
    intent_type: ResponseIntentType = ResponseIntentType.CONVERSATION
    provider: str = "gemini"
    model: str = "gemini-flash-latest"
    is_fallback: bool = False
    intent_payload: Optional[IntentPayload] = None
    error: Optional[ResponseError] = None

    def to_dict(self) -> Dict[str, Any]:
        return {
            "success": self.success,
            "response": self.response_text,  # Backwards-compatible alias for existing frontend
            "response_text": self.response_text,
            "source": self.source.value if isinstance(self.source, Enum) else self.source,
            "intent_type": self.intent_type.value if isinstance(self.intent_type, Enum) else self.intent_type,
            "provider": self.provider,
            "model": self.model,
            "is_fallback": self.is_fallback,
            "intent_payload": self.intent_payload.to_dict() if self.intent_payload else None,
            "error": self.error.to_dict() if self.error else None,
            # Voice indicator bridge:
            "state": "state-error" if not self.success else "state-speaking"
        }
```

---

## 6. Frontend TypeScript / JSDoc Reference

```typescript
export interface RouterResponsePayload {
  success: boolean;
  response_text: string;
  response?: string; // Backwards-compatible alias
  source: 'online' | 'offline';
  intent_type: 'ACTION' | 'INFO' | 'CONVERSATION' | 'NONE';
  provider: string;
  model: string;
  is_fallback: boolean;
  intent_payload?: ActionIntentPayload | InfoIntentPayload | null;
  error?: ResponseErrorPayload | null;
  state?: string;
}

export interface ResponseErrorPayload {
  code: 'RATE_LIMITED' | 'TIMEOUT' | 'NETWORK_OFFLINE' | 'AUTH_FAILED' | 'MODEL_NOT_FOUND' | 'INTERNAL_ERROR';
  message: string;
  retryable: boolean;
  http_status?: number;
  details?: Record<string, unknown>;
}

export interface ActionIntentPayload {
  intent_type: 'ACTION';
  icon?: string;
  title?: string;
  target?: string;
  action_type?: 'APP' | 'CALL' | 'TOGGLE' | 'NAVIGATE';
  status?: 'Ready' | 'Executing' | 'Completed';
  response_text?: string;
}

export interface InfoIntentPayload {
  intent_type: 'INFO';
  icon?: string;
  title: string;
  body: string;
  source?: string;
  data_points?: Array<{ label: string; value: string }>;
  response_text?: string;
}
```
