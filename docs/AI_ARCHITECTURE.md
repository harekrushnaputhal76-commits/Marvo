# AI Routing & Architecture Specification

> **Module**: Core AI Routing (`js/core/trafficPolice.js` & `core/traffic_police.py`)  
> **Topology**: Hybrid Dual-Engine MoE (Gemini Cloud + Phi-3-mini Local)  
> **Status**: APPROVED  

---

## 1. High-Level Request Flow

```
[ User Prompt / Voice Query ]
              │
              ▼
   [ Frontend UI / Chat Bar ]
              │
              ▼
  [ js/core/trafficPolice.js ] ───( Routing Decision Evaluator )
              │
      ┌───────┴────────────────────────┐
      ▼                                ▼
[ PATH A: Cloud Online ]     [ PATH B: Local Offline ]
      │                                │
      ▼                                ▼
  POST /api/chat               Native Capacitor Bridge / Ollama
      │                                │
      ▼                                ▼
  Python Backend Proxy         phi-3-mini-4k-instruct-q4.gguf
  (Key Pool / Failover)               (On-Device Sandbox)
      │                                │
      ▼                                │
  Google Gemini API                    │
      │                                │
      └──────────────┬─────────────────┘
                     ▼
          [ Response Sanitizer ]
         (Strip thinking & meta)
                     │
                     ▼
      [ UI Render / TTS Audio Engine ]
```

---

## 2. Request Flow Breakdown

| Step | Component | Responsibility | Failure Behavior |
| :--- | :--- | :--- | :--- |
| **1. Ingestion** | `frontend/app.js` | Captures user text/voice input and multimodal image attachments. | Validates non-empty input before dispatch. |
| **2. Client Router** | `frontend/js/core/trafficPolice.js` | Evaluates connectivity, user overrides, and selected provider mode. | Selects Primary Execution Target (Cloud vs Local). |
| **3. Path A: Cloud** | `POST /api/chat` (Backend Proxy) | Authenticates with `GEMINI_API_KEY` pool; passes prompt with system instructions. | If network/proxy fails, catches error and triggers Path B. |
| **4. Path B: Local** | `MarvoNativeBridge` / Ollama | Executes `Phi-3-mini (3.8B)` on local NPU/CPU without network. | Returns install prompt if GGUF model is not downloaded. |
| **5. Sanitization** | `sanitizeLlmResponse()` | Strips `<thought>`, metadata directives, entity XML, and duplicate responses. | Returns clean markdown response string. |
| **6. Presentation** | Voice Engine & Chat UI | Synthesizes voice (Piper/Cloned/WebSpeech) and updates chat bubble and orb state. | Degrades to silent text render if audio fails. |

---

## 3. Decision Matrix: Routing Criteria

Routing decisions are strictly evaluated at step 2 (`TrafficPolice.routeChat`):

| Trigger Condition | Evaluated Check | Active Route | Model Invoked |
| :--- | :--- | :--- | :--- |
| **All Cloud Toggled Off** | `areAllCloudApisToggledOff() === true` | **Local Offline** | Phi-3-mini (3.8B) |
| **Device Offline (No Network)** | `navigator.onLine === false` | **Local Offline** | Phi-3-mini (3.8B) |
| **Manual Local Selection** | `state.currentProvider === 'local'` | **Local Offline** | Phi-3-mini (3.8B) |
| **Online + Cloud Enabled** | `navigator.onLine === true` && Gemini enabled | **Cloud Online** | Google Gemini (via `/api/chat`) |
| **Web Research + Offline** | `advancedMode === 'web-research'` && offline | **Block & Toast** | Offline warning (requires internet) |

---

## 4. Fallback & Failure Cascade

```
Cloud Request Initiated (Gemini via /api/chat)
        │
        ├─► [SUCCESS 200] ──► Sanitize & Return
        │
        └─► [FAILURE] (Timeout, 429 Quota, 5xx Server Error, No Network)
                │
                ▼
        Log Warning to Console
                │
                ▼
        Transparent Fallback: Invoke callLocalLLM()
                │
                ├─► [Phi-3-mini GGUF Present] ──► Local Inference ──► Sanitize & Return
                │
                └─► [Phi-3-mini Not Present]  ──► Prompt User to Download GGUF via Drawer
```

### Guarantees
1. **Silent Fallback**: Network hiccups during cloud calls do not throw unhandled promise rejections; they cascade to on-device Phi-3-mini.
2. **Zero Fake Output**: When both cloud and local models cannot fulfill a request, Marvo displays explicit, actionable system guidance rather than fake canned output.
3. **Intent Preservation**: Context history, cognitive directives, and persona prompts are retained across fallback boundaries.
