# AI Resource Budgets & Usage Limits Specification

> **Purpose**: Prevent runaway API billing, token depletion, excessive battery consumption, and device thermal throttling.  
> **Target Audience**: Core backend engineers, frontend runtime developers, and automated agents.  
> **Status**: BINDING SPECIFICATION  

---

## 1. Concrete Limit Thresholds

| Metric | Target Provider | Threshold Limit | Window / Scope | Enforced By |
| :--- | :--- | :--- | :--- | :--- |
| **Max Requests per Minute (RPM)** | Google Gemini (Cloud) | **15 RPM** | Per active user session | Frontend Client & Backend Rate Limiter |
| **Daily Request Budget** | Google Gemini (Cloud) | **1,500 requests/day** | Shared pool aggregate | Backend Key Pool Manager |
| **Daily Token Budget** | Google Gemini (Cloud) | **1,000,000 tokens/day** | Shared pool aggregate | Backend Token Counter |
| **Max Concurrent Local Tasks** | Phi-3-mini (Local) | **1 concurrent inference** | Per device hardware | Native Bridge / Thread Guard |
| **Max Queued Local Requests** | Phi-3-mini (Local) | **2 requests in queue** | Per device queue | TrafficPolice Dispatch Queue |
| **Max Context Window** | Phi-3-mini (Local) | **4,096 tokens** | Per prompt context | Native Context Window Truncator |
| **Max Cloud Context Window** | Google Gemini (Cloud) | **32,768 tokens (Sliding Window)** | Per conversation | Backend Context Pruner |
| **Request Timeout** | Gemini Cloud Proxy | **12.0 seconds** | Per HTTP request | Backend ThreadPoolExecutor |
| **Local Inference Timeout** | Phi-3-mini Engine | **25.0 seconds** | Per on-device task | Native Bridge Watchdog |

---

## 2. Threshold Exceeded Behaviors

When a threshold is exceeded, the system must follow deterministic behaviors according to the table below:

| Limit Triggered | Immediate Action | Secondary Fallback | User-Facing Notification |
| :--- | :--- | :--- | :--- |
| **Cloud Rate Limit Hit (>15 RPM)** | Throttle new cloud dispatch | Automatic fallback to **Phi-3-mini (Local)** | Toast: *"Cloud query rate limit reached. Routing locally."* |
| **Daily Cloud Budget Exhausted** | Disable cloud routes for day | Fallback strictly to **Phi-3-mini (Local)** | Info Card: *"Daily cloud budget reached. Operating on local offline brain."* |
| **Local Inference Running (Concurrent = 1)** | Enqueue upcoming query (Max 2) | None (process in FIFO order) | Indicator: Thinking orb pulse / *"Generating..."* |
| **Local Queue Overflow (> 2 in queue)** | Drop newest overflow request | Do NOT dispatch | Warning: *"Device inference queue full. Please wait for current generation to finish."* |
| **Local Context Length Exceeded (>4K tokens)** | Prune oldest context history (FIFO) | Retain System Prompt & latest user prompt | Transparent (no user interruption) |
| **Cloud Request Timeout (>12s)** | Abort cloud request via AbortSignal | Transparent fallback to **Phi-3-mini (Local)** | Toast: *"Cloud response delayed. Switched to on-device engine."* |
| **Local Inference Timeout (>25s)** | Terminate native task & release lock | Free inference lock | Error: *"Local generation timed out. Try a shorter query."* |

---

## 3. Battery & Thermal Protection Policy

1. **Strict Concurrency Cap**: Under no circumstances may multiple local LLM inference threads run in parallel on mobile devices.
2. **Thermal Degradation**:
   - If device battery temperature exceeds **42°C (107.6°F)** or battery drops below **10%**, high-temperature throttle activates.
   - Heavy thinking modes are capped; local generation max tokens is reduced from 1024 to 256.
3. **Background Suspension**: If the application is backgrounded and no foreground download or speech task is active, all pending local inference must safely yield CPU cycles.

---

## 4. Verification & Testing Contracts

- All unit and integration tests must respect these limits (mock timers and mock token responses should be used in test suites).
- Backend tests in `tests/test_key_pool.py` verify 429 rate limit failover and offline fallback.
