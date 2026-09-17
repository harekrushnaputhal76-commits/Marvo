# Voice Indicator State Contract

## 1. Existing State Hooks & Stores Inventory

From the Group A codebase architecture analysis, the following state hooks/stores were identified:

| Store / Hook | Location | Scope | Evaluation & Role |
|---|---|---|---|
| `DynamicIslandManager.prototype.setVoiceState` / `window.setVoiceState` | `frontend/app.js` (lines 3173-3240, 3415) | Voice Indicator Lifecycle & Dynamic Indicator | **Authoritative State Source.** Directly manages the indicator lifecycle, volume integration, active modes, haptic feedback, and audio visualizer loop. |
| `setEyeExpression` / `const STATES` | `frontend/app.js` (lines 813-831, 860-910) | Central Avatar Eye Expression Engine | **Auxiliary Avatar Expression.** Controls 60+ CSS classes on the decorative 3D eye canvas (`#marvoEyes`), e.g. `state-happy`, `state-winking`. Not authoritative for voice indicator geometry/morphing. |
| `isVoiceRecording` / `isVoicePaused` | `frontend/app.js` (lines 3424-3425) | Web Speech / AudioContext flags | **Input Capture Primitives.** Low-level boolean flags indicating microphone hardware activity; producers trigger `setVoiceState` accordingly. |
| `AssistantActivity.java` Native State Machine | `android/app/.../AssistantActivity.java` | Native Android TTS & SpeechRecognizer | **Native Platform Layer.** Runs native TTS/STT if invoked in pure native mode; web indicator in WebView operates independently via Capacitor bridge. |

**Authoritative Source Decision:**
`DynamicIslandManager` (accessible via `window.setVoiceState` and `window.dynamicIslandInstance`) is the **single authoritative source of truth** for the voice indicator. It has active producers wired to every phase of audio capture, processing, streaming, and speech synthesis.

---

## 2. Complete State Set Definition

The voice indicator state space is strictly bounded to five states (no additional states permitted at this layer):

```javascript
/**
 * Authoritative Voice Indicator States
 * @readonly
 * @enum {string}
 */
const VoiceIndicatorState = Object.freeze({
  IDLE: 'idle',           // Camera-anchored resting state (24x24px dot)
  LISTENING: 'listening', // Active microphone streaming; reactive to audio volume
  THINKING: 'thinking',   // Request sent to backend/TrafficPolice; awaiting response
  SPEAKING: 'speaking',   // TTS playback / streaming response active
  ERROR: 'error'          // Failure / timeout condition; auto-dismisses to idle
});
```

---

## 3. Valid State Transitions Matrix

Valid transitions between states are strictly controlled as shown in the transition matrix below:

| From State | To State | Trigger / Producer Event | Valid? |
|---|---|---|---|
| `idle` | `listening` | Mic button tapped (`openVoiceDock`), wake word detected | **YES** |
| `idle` | `thinking` | Direct text submit while idle (`sendMessage`) | **YES** |
| `idle` | `error` | System initialization failure / mic permission denied | **YES** |
| `idle` | `speaking` | Background notification / spontaneous TTS response | **YES** |
| `listening` | `thinking` | Speech silence timer expired (2s) / manual send tap (`submitVoiceRecording`) | **YES** |
| `listening` | `idle` | Voice dock closed / cancelled (`closeVoiceDock`), pause toggled | **YES** |
| `listening` | `error` | Mic stream interrupted / SpeechRecognition error (`not-allowed`) | **YES** |
| `listening` | `listening` | Volume / RMS audio level update (volume payload changes) | **YES** |
| `thinking` | `speaking` | First audio chunk / TTS playback start (`playSpeech`) | **YES** |
| `thinking` | `idle` | Chat abort / text-only response complete without TTS | **YES** |
| `thinking` | `error` | Network error / API failure / TrafficPolice rejection | **YES** |
| `speaking` | `idle` | Audio playback complete (`onended`) / `stopSpeech` / user dismissal | **YES** |
| `speaking` | `listening` | User barge-in / interrupt triggered (`onspeechstart`) | **YES** |
| `speaking` | `error` | Audio playback failure / audio context decode error | **YES** |
| `speaking` | `speaking` | Amplitude update during streaming TTS | **YES** |
| `error` | `idle` | Error flash timeout (~1.5s auto-reset) or user tap | **YES** |
| `*` (any) | `idle` | Force reset / session navigation / teardown | **YES** |

Any transition not explicitly listed in the table above is invalid and will be rejected or safely defaulted to `idle`.

---

## 4. State Transition Producers Inventory

| State | Primary Producers | File & Line Reference | Reachable Today? |
|---|---|---|---|
| `idle` | `closeVoiceDock` | `frontend/app.js:3538` | **YES** |
| `idle` | `toggleVoicePauseResume` (pause branch) | `frontend/app.js:3557` | **YES** |
| `idle` | `playSpeech` (cleanup & onended) | `frontend/app.js:1581, 1642, 1656, 1674, 1685, 1698` | **YES** |
| `idle` | `abortCurrentChat` | `frontend/app.js:2928` | **YES** |
| `idle` | `submitVoiceRecording` (empty speech fallback) | `frontend/app.js:3613` | **YES** |
| `idle` | Error state auto-dismiss timer (~1.5s) | `frontend/app.js:3235` | **YES** |
| `listening` | `openVoiceDock` | `frontend/app.js:3497` | **YES** |
| `listening` | `renderWave` (live mic RMS) | `frontend/app.js:3438` | **YES** |
| `listening` | `toggleVoicePauseResume` (resume branch) | `frontend/app.js:3567` | **YES** |
| `listening` | `startSpeechRecognition.onspeechstart` barge-in | `frontend/app.js:3649` | **YES** |
| `thinking` | `submitVoiceRecording` | `frontend/app.js:3605, 3607` | **YES** |
| `speaking` | `playSpeech` (audio playback start) | `frontend/app.js:1614, 1704` | **YES** |
| `error` | Dynamic error handler / permission rejection | `frontend/app.js:3682` | **PARTIAL** (triggers toast & closeDock; direct `setVoiceState('error')` call can be invoked via `window.setVoiceState('error')`) |

---

## 5. Orthogonal Response Intent Dimension

While `VoiceIndicatorState` (`idle`, `listening`, `thinking`, `speaking`, `error`) manages the foundational lifecycle and hardware audio loop, incoming model responses carry an **orthogonal intent dimension**:

```javascript
/**
 * Authoritative Response Intent Types
 * @readonly
 * @enum {string}
 */
const ResponseIntentType = Object.freeze({
  ACTION: 'ACTION',             // Short confirmation card (e.g. alarm set, call placed)
  INFO: 'INFO',                 // Multiline structured info card (facts, weather, summary)
  CONVERSATION: 'CONVERSATION', // Base speech capsule without card overlay (default)
  NONE: 'NONE'                  // Non-response or reset state
});
```

### Canonical Casing & Fallback Rules
1. **Canonical Casing**: All intent types are normalized to uppercase strings (`ACTION`, `INFO`, `CONVERSATION`, `NONE`).
2. **Case Insensitivity**: Consumers and routing switches parse incoming string inputs with `.trim().toUpperCase()`.
3. **Safe Fallback**: Any missing, `null`, `undefined`, or unrecognized intent type (e.g., `"unknown"`, `"widget"`) automatically falls back to `ResponseIntentType.CONVERSATION` (the base conversational capsule). No unhandled crashes or blank states can occur.

---

## 6. Priority Order & Simultaneous-State Conflict Resolution

When visual states, transitions, and intent cards intersect, the visual indicator enforces a deterministic priority hierarchy:

1. **`ERROR` (Priority 1 — Highest)**:
   - Always preempts active cards and conversations.
   - Any in-flight `ACTION` or `INFO` intent card or timer is cancelled immediately.
   - Any incoming intent card is rejected while indicator is in `error` state.
2. **`ACTION` / `INFO` Intent Cards (Priority 2)**:
   - When a structured intent arrives, it transitions the container to the card's target geometry with spring physics.
   - **Mutual Exclusivity**: `showActionIntentCard` hides any active `INFO` card; `showInfoIntentCard` hides any active `ACTION` card. Exactly one card can render at any instant.
   - If an intent card arrives while base speaking is transitioning to idle, the card immediately captures the shape and resets idle timers.
   - Once dismissed (timeout, tap outside, or swipe up), the shape smoothly returns to the base state (`idle` or `speaking`).
3. **`CONVERSATION` / `NONE` (Priority 3)**:
   - Renders the base conversational capsule (`speaking`, `thinking`, `listening`) without card overlays.
4. **`IDLE` (Priority 4 — Lowest)**:
   - Camera punch-hole resting circle (24×24px).

### Single Active Hull Guarantee
The liquid filter context (`#marvo-liquid-filter-context`) contains strictly **one** root indicator element (`#marvo-dynamic-island`). Card elements are internal flex children toggled via `opacity` and `hidden` properties; they never render as separate sibling DOM nodes. This eliminates double-hull or overlapping blob visual artifacts under the gooey filter.

