# AI Roadmap & Provider Cleanup Tasks

> **Purpose**: Cross-session progress tracker and audit log for LLM provider consolidation.  
> **Rule Reference**: See [AI_RULES.md](file:///c:/Users/GUDU/OneDrive/Desktop/Marvo-main/docs/AI_RULES.md) for architectural constraints.  
> **Last Audited**: 2026-10-05  

---

## 1. Provider & Model Consolidation Checklist

| Task ID | Item / Action | Scope | Status | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **TSK-01** | **Remove Groq Provider** | `trafficPolice.js`, `aiControlCenter.js`, `index.html`, `server.py` | `Done` | All Groq API keys, dispatchers, and UI elements removed. |
| **TSK-02** | **Remove OpenRouter Provider** | `trafficPolice.js`, `aiControlCenter.js`, `index.html`, `app.js` | `Done` | OpenRouter gateway, secondary model dropdown, and configs removed. |
| **TSK-03** | **Remove Gemma-2B Model** | `aiControlCenter.js`, `trafficPolice.js`, `app.js` | `Done` | Deleted from offline model repo and routing tables. |
| **TSK-04** | **Remove Qwen 2.5 Model** | `aiControlCenter.js`, `trafficPolice.js` | `Done` | Removed from offline model repo and storage mappings. |
| **TSK-05** | **Remove Llama-3 Model** | `aiControlCenter.js`, `trafficPolice.js`, `index.html` | `Done` | Removed from offline model list and subagent pill menus. |
| **TSK-06** | **Confirm Orb UI Untouched** | `FloatingOrbService`, dynamic island CSS/JS, avatar eyes, `tiltEngine.js` | `Done` | Strict boundary preserved; 0 lines modified in avatar/orb files. |
| **TSK-07** | **Confirm Backend Proxy in Place** | `server/server.py` (`/api/chat`), `trafficPolice.js` | `Done` | Cloud calls strictly proxied through backend; 0 client keys exposed. |
| **TSK-08** | **Establish Documentation Set** | `docs/` (`AI_RULES`, `AI_ARCHITECTURE`, `AI_LIMITS`, `AI_TASKS`) | `Done` | 4 foundational documentation standards established. |
| **TSK-09** | **Enforce Client Rate Limiter (15 RPM)** | `trafficPolice.js` client request throttle | `Done` | Implemented in `TrafficPolice.routeChat` rate-guard mechanism. |
| **TSK-10** | **Backend Key Pool Health Tracking** | `core/key_pool.py`, `core/online_router.py` | `Done` | Verified with 57 unit tests passing in `tests/`. |
| **TSK-11** | **Verify Local Model Download Pipeline** | `aiControlCenter.js` Foreground Service download | `Done` | Phi-3-mini retained with Whisper STT and Piper TTS. |
| **TSK-12** | **Real Token-by-Token Streaming** | `server.py`, `OfflineBrainManager.java`, `trafficPolice.js`, `app.js` | `Done` | Gemini SSE stream + Phi-3 native/Ollama stream live into chat bubble; removed fake delay simulation. |
| **TSK-13** | **Phi-3 In-Memory Residency & 3m Idle Unload** | `OfflineBrainManager.java`, `MarvoNativeBridge.java`, `trafficPolice.js`, `app.js` | `Done` | Load 2.2GB model once into memory on first select/message; keep resident across active chats; unload after 3+ min idle. |

---

## 2. Component Verification Matrix

| Target Component | File Path | Status | Verification Detail |
| :--- | :--- | :--- | :--- |
| **Client Router** | `frontend/js/core/trafficPolice.js` | `Verified` | Syntax verified (`node --check`). Only Gemini & Phi-3 active. |
| **Control Center** | `frontend/js/ui/aiControlCenter.js` | `Verified` | Syntax verified. Only Phi-3, STT, TTS in offline; Gemini in online. |
| **Quick Switcher** | `frontend/index.html` | `Verified` | Model pill bar shows only Gemini (Cloud) & Phi-3 (Local). |
| **App Wireup** | `frontend/app.js` | `Verified` | Cleaned dead key inputs; pill listeners bound cleanly. |
| **Backend API** | `server/server.py` | `Verified` | `/api/config` purged of removed keys; `/api/chat` operational. |
| **Protected Avatar** | `frontend/js/core/tiltEngine.js` | `Verified` | 0 modifications made. |
| **Python Unit Tests** | `tests/` | `Verified` | 57 / 57 tests passing (`OK`). |

---

## 3. Maintenance Guide for Future Sessions

1. **Before Adding Any Feature**: Check `docs/AI_RULES.md` to ensure no prohibited multi-provider patterns are reintroduced.
2. **When Touching Routing**: Adhere strictly to `docs/AI_ARCHITECTURE.md` decision trees and error cascades.
3. **When Auditing Resource Usage**: Cross-reference against thresholds defined in `docs/AI_LIMITS.md`.
4. **Update This Task List**: Toggle tasks to `In Progress` or `Done` with commit notes whenever architectural adjustments occur.
