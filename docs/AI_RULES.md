# AI Architecture & Development Rules

> **Status**: ACTIVE & ENFORCED  
> **Last Updated**: 2026-10-05  
> **Scope**: All LLM, Cloud API, and Local Neural Inference integrations across Marvo.

---

## 1. Hard Rule: Strict 2-Provider Architecture

Marvo strictly permits **only two LLM providers** in the entire codebase:

| Provider | Runtime Mode | Engine / Model | Execution Target | Exposure Level |
| :--- | :--- | :--- | :--- | :--- |
| **Google Gemini** | Cloud (Online) | `gemini-2.0-flash` | Backend Proxy (`/api/chat`) | Server-side only (0% client exposure) |
| **Phi-3-mini** | Local (Offline) | `phi-3-mini-4k-instruct-q4.gguf` (3.8B) | On-Device Bridge / MediaPipe / Ollama | Local device sandbox (0% network) |

### Non-Negotiable Constraints
- **NO THIRD PROVIDER**: Groq, OpenRouter, Anthropic direct, OpenAI direct, Mistral, Cohere, etc., are **forbidden**.
- **NO ADDITIONAL LOCAL LLM**: Gemma-2B, Qwen 2.5, Llama-3, etc., are **forbidden** (STT Whisper & TTS Piper remain approved audio-only utility runtimes).
- **DOCS FIRST**: No new model or provider may be added to any branch, config, or prototype without updating and gaining approval in this document first.

---

## 2. Security & API Key Isolation Guarantee

All cloud LLM interaction must comply with the zero client-leak rule:

```
[ Frontend Client ]  ---( No API Key )--->  [ Marvo Server Proxy ]  ---( Secure Key )--->  [ Gemini Cloud ]
    (Browser / UI)                              (/api/chat)                                 (Google API)
```

### Security Checkpoints
- [x] **Zero Client Keys**: No API key, token, or secret may ever be embedded, obfuscated, or stored in `frontend/`, `localStorage`, or Android assets.
- [x] **Server Proxy Exclusivity**: All external LLM requests must dispatch through the backend proxy (`/api/chat` or `core/traffic_police.py`).
- [x] **No Raw Key Config Routes**: `/api/config` must never return private cloud API keys to client consumers.
- [x] **Safe Git Hygiene**: Real credentials live strictly in `.env` (ignored by `.gitignore`) and system environment variables.

---

## 3. Group-1 Review Process for Provider Changes

Any proposed addition, replacement, or modification of an AI provider must pass a formal three-pillar review before merging:

| Review Pillar | Criteria & Validation Requirements |
| :--- | :--- |
| **1. Security Review** | Zero client key exposure, server-only secret storage, secure payload sanitization, zero logging of user secrets. |
| **2. Battery & Thermal Review** | Zero background compute leakage, max 1 concurrent on-device inference, thermal throttling safeguards, zero CPU wake-lock runaway. |
| **3. Routing & Fallback Review** | Deterministic failover path, zero cross-billing risk, no phantom loops, graceful degraded UX when offline or rate-limited. |

---

## 4. Architectural Boundaries

- **Orb / Avatar UI Protection**: Never modify orb, Dynamic Island, Siri-orb CSS/JS, avatar eyes, or `tiltEngine.js` during LLM or router refactors.
- **Single Public Router Entry**: Centralized client routing logic lives strictly in `frontend/js/core/trafficPolice.js` and server-side cascade in `core/traffic_police.py`.
- **Honest Telemetry**: If a model fails or quota is exhausted, report real state. Never spoof output with hardcoded canned text.
