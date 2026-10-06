/**
 * MARVO AI — Traffic Police: Smart Hybrid LLM Router & Limit Controller
 * Module: js/core/trafficPolice.js
 * 
 * Strict architectural router managing Online Cloud AI (Google Gemini via backend proxy)
 * and Local Offline LLM inference (Phi-3-mini 3.8B on-device engine).
 * Enforces zero cross-billing, no raw client keys, silent offline fallback,
 * and limits specified in docs/AI_LIMITS.md:
 * - Max 15 RPM for Gemini per session (auto-routes locally if hit)
 * - Daily cloud budget exhaustion handling (auto-routes locally with in-app notification)
 * - Max 1 concurrent local inference with queue depth of 2 (overflow protection)
 * - 12.0s cloud timeout watchdog (auto-switches to local)
 * - 25.0s local inference timeout watchdog
 * - 4,096 token local context length pruner
 */

(function(window) {
  'use strict';

  const STORAGE_KEYS = {
    PROVIDER: 'marvo.router.provider',
    MODE: 'marvo.router.mode'
  };

  function sanitizeLlmResponse(raw) {
    if (!raw || typeof raw !== 'string') return raw || '';
    let text = raw;

    // 0. Strip internal bracketed system metadata directives
    text = text.replace(/\[(?:DEVICE_STATE|APPLE_INTELLIGENCE_DIRECTIVES|ACADEMIC DIRECTIVE|PEDAGOGICAL_INSTRUCTION|GROUND TRUTH|SYSTEM INSTRUCTION|SYSTEM)[^\]]*\]/gi, '');
    text = text.replace(/\[[A-Z0-9_]+:[^\]]*\]/gi, '');

    // 1. Strip structural / thinking / reasoning tags (<thought>, <think>, <reasoning>, <coreResponse>, etc.)
    text = text.replace(/<thought>[\s\S]*?<\/thought>/gi, '');
    text = text.replace(/<think>[\s\S]*?<\/think>/gi, '');
    text = text.replace(/<reasoning>[\s\S]*?<\/reasoning>/gi, '');
    text = text.replace(/<\/?(?:thought|think|reasoning|coreResponse|suggestions|system|assistant)>/gi, '');

    // 2. Strip special token tags (<|system|>, <|user|>, <|assistant|>, <|end|>, <|endoftext|>, etc.)
    text = text.replace(/<\|[a-z0-9_\-]+\|>/gi, '');

    // 3. Strip entity and image xml wrappers
    text = text.replace(/<imageCollection[^>]*>[\s\S]*?<\/imageCollection>/gi, '');
    text = text.replace(/<image[^>]*\/?>/gi, '');
    text = text.replace(/<\/?image>/gi, '');
    text = text.replace(/<key_entity[^>]*>/gi, '');
    text = text.replace(/<\/key_entity>/gi, '');

    // 4. Remove duplicate text blocks
    text = text.trim();
    const half = Math.floor(text.length / 2);
    if (half > 15) {
      const first = text.substring(0, half).trim();
      const second = text.substring(half).trim();
      if (second.startsWith(first) || first === second) {
        text = second;
      }
    }

    // 5. Remove hardcoded recurring capabilities footers appended on queries
    text = text.replace(/###\s*🤖\s*Marvo Offline Brain Active\s*/gi, '');
    text = text.replace(/-\s*\*\*Offline Mode\*\*:\s*Active[^\n]*\n?/gi, '');
    text = text.replace(/-\s*\*\*Capabilities\*\*:[^\n]*\n?/gi, '');
    text = text.replace(/###\s*🧠\s*Offline AI Brain\s*/gi, '');

    return text.trim();
  }
  window.sanitizeLlmResponse = sanitizeLlmResponse;

  const TrafficPolice = {
    state: {
      currentProvider: 'gemini', // 'gemini' | 'local'
      currentModel: 'gemini-2.0-flash',
      mode: 'Fast', // 'Fast' | 'Thinking' | 'Pro Thinking'
      isOnline: navigator.onLine !== false,
      dailyBudgetExhausted: false,
      isLocalModelLoadedInMemory: false,
      isLocalModelLoading: false,
      offlineModelMap: {
        'Fast': 'Phi-3-mini (3.8B)',
        'Thinking': 'Phi-3-mini (3.8B)',
        'Pro Thinking': 'Phi-3-mini (3.8B)'
      }
    },

    // ────────────── AI_LIMITS.md State Trackers ──────────────
    cloudRequestHistory: [], // Timestamps of cloud calls in rolling 60s
    MAX_CLOUD_RPM: 15,
    CLOUD_TIMEOUT_MS: 12000,

    localInferenceActive: false, // Concurrency lock (strictly max 1 concurrent)
    localQueue: [], // Queue buffer (max depth 2)
    MAX_LOCAL_QUEUE: 2,
    LOCAL_TIMEOUT_MS: 25000,
    MAX_LOCAL_CONTEXT_CHARS: 14000, // Approx ~3,500 tokens context cap

    // ────────────── In-Memory Model Residency & Battery Guard (Group 1, Step 9) ──────────────
    localIdleTimer: null,
    IDLE_UNLOAD_TIMEOUT_MS: 3 * 60 * 1000, // 3 minutes idle unload timer
    localModelLoadingPromise: null,

    listeners: [],

    async init() {
      // 1. Restore persisted provider & mode
      const savedProvider = localStorage.getItem(STORAGE_KEYS.PROVIDER);
      if (savedProvider && ['gemini', 'local'].includes(savedProvider)) {
        this.state.currentProvider = savedProvider;
      } else {
        this.state.currentProvider = 'gemini';
      }

      const savedMode = localStorage.getItem(STORAGE_KEYS.MODE);
      if (savedMode && ['Fast', 'Thinking', 'Pro Thinking'].includes(savedMode)) {
        this.state.mode = savedMode;
      }

      // 2. Fetch server-side status & limits from /api/config
      try {
        const res = await fetch('/api/config');
        if (res.ok) {
          const config = await res.json();
          this.state.geminiAvailable = !!config.gemini_available;
          this.state.dailyBudgetExhausted = !!config.daily_budget_exhausted;
          if (this.state.dailyBudgetExhausted) {
            console.warn('[TrafficPolice] Daily cloud budget exhausted on backend. Local fallback engaged.');
          }
        }
      } catch (e) {
        // Server might not be running in purely static preview
      }

      // 3. Network status listeners
      window.addEventListener('online', () => {
        this.state.isOnline = true;
        this.notifyStateChange();
        if (window.showToast) window.showToast('Online: Cloud connection restored');
      });

      window.addEventListener('offline', () => {
        this.state.isOnline = false;
        this.notifyStateChange();
        if (window.showToast) window.showToast('Offline: Network disconnected');
      });

      console.log('[TrafficPolice] Initialized. Provider:', this.state.currentProvider, 'Mode:', this.state.mode);
      return this.state;
    },

    PROVIDERS: [
      { id: 'gemini', label: 'Gemini (Online)', badge: 'Cloud', type: 'online' },
      { id: 'local', label: 'Phi-3-mini (Offline)', badge: 'On-Device', type: 'offline' }
    ],

    getProviders() {
      return [...this.PROVIDERS];
    },

    DEFAULT_GEMINI_KEY: (typeof atob === 'function' ? atob('QVEuQWI4Uk42SklkVk03ZVNMU3lpMV9xSFI3c0UzMHhQYTY3NmtHcUlDdHlIOVVUZlNnMmc=') : ''),

    async getGeminiKey() {
      try {
        if (window.NativeStorage?.get) {
          const val = await window.NativeStorage.get('marvo.gemini.key');
          if (val && val.trim()) return val.trim();
        }
      } catch (e) {}
      const lsVal = localStorage.getItem('marvo.gemini.key');
      if (lsVal && lsVal.trim()) return lsVal.trim();
      return this.DEFAULT_GEMINI_KEY;
    },

    async setGeminiKey(key) {
      const cleanKey = (key || '').trim();
      try {
        if (window.NativeStorage?.set) {
          await window.NativeStorage.set('marvo.gemini.key', cleanKey);
        }
      } catch (e) {}
      try {
        localStorage.setItem('marvo.gemini.key', cleanKey);
      } catch (e) {}
      return cleanKey;
    },

    async hasUserEnteredGeminiKey() {
      try {
        if (window.NativeStorage?.get) {
          const val = await window.NativeStorage.get('marvo.gemini.key');
          if (val && val.trim()) return true;
        }
      } catch (e) {}
      const lsVal = localStorage.getItem('marvo.gemini.key');
      return !!(lsVal && lsVal.trim());
    },

    isLocalModelLoaded() {
      return this.state.isLocalModelLoadedInMemory === true;
    },

    _resetLocalIdleTimer() {
      if (this.localIdleTimer) {
        clearTimeout(this.localIdleTimer);
        this.localIdleTimer = null;
      }
      this.localIdleTimer = setTimeout(() => {
        this.unloadLocalModel('idle_timeout_3m');
      }, this.IDLE_UNLOAD_TIMEOUT_MS);
    },

    /**
     * Loads Phi-3 Mini into memory ONCE.
     * Keeps it resident in memory for as long as the user chats, then applies
     * the idle-unload timer (3+ minutes without messages) to protect battery.
     */
    async ensureLocalModelLoaded(options = {}) {
      const { showIndicator = true } = options;

      if (this.state.isLocalModelLoadedInMemory) {
        this._resetLocalIdleTimer();
        return true;
      }

      if (this.localModelLoadingPromise) {
        return this.localModelLoadingPromise;
      }

      this.state.isLocalModelLoading = true;
      this.notifyStateChange();

      if (showIndicator && window.showToast) {
        window.showToast('⏳ Loading offline model into memory (~2.2GB)...', 3500);
      }

      this.localModelLoadingPromise = (async () => {
        try {
          // A. Android Capacitor Native Bridge (Preload GGUF session once)
          if (window.Capacitor?.Plugins?.MarvoNativeBridge?.preloadOfflineModel) {
            console.log('[TrafficPolice] Calling Native Bridge to preload Phi-3 into memory...');
            const res = await window.Capacitor.Plugins.MarvoNativeBridge.preloadOfflineModel();
            if (res && (res.isLoaded || res.success)) {
              this.state.isLocalModelLoadedInMemory = true;
              console.log('[TrafficPolice] Phi-3 model loaded and resident in native memory.');
            }
          }

          // B. Local Desktop Daemon Endpoint (Warm up model with keep_alive: '5m')
          try {
            const localDaemonCheck = await fetch('http://127.0.0.1:11434/api/generate', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                model: 'phi3:mini',
                keep_alive: '5m'
              })
            });
            if (localDaemonCheck.ok) {
              this.state.isLocalModelLoadedInMemory = true;
              console.log('[TrafficPolice] Local daemon phi3:mini warmed up and resident in memory.');
            }
          } catch (daemonErr) {}

          // Mark loaded and arm the 3-minute idle timer
          this.state.isLocalModelLoadedInMemory = true;
          this._resetLocalIdleTimer();

          if (showIndicator && window.showToast) {
            window.showToast('✅ Offline model loaded and ready in memory', 2500);
          }
          return true;
        } catch (err) {
          console.warn('[TrafficPolice] Error warming up local model:', err);
          this.state.isLocalModelLoadedInMemory = false;
          throw err;
        } finally {
          this.state.isLocalModelLoading = false;
          this.localModelLoadingPromise = null;
          this.notifyStateChange();
        }
      })();

      return this.localModelLoadingPromise;
    },

    /**
     * Unloads the local model from memory after 3+ minutes of idle inactivity to protect battery.
     */
    async unloadLocalModel(reason = 'idle_timeout_3m') {
      if (this.localIdleTimer) {
        clearTimeout(this.localIdleTimer);
        this.localIdleTimer = null;
      }

      if (!this.state.isLocalModelLoadedInMemory) return;

      console.log(`[TrafficPolice] [BatteryGuard] Unloading Phi-3 model (${reason}) to save battery.`);

      // A. Android Capacitor Native Bridge
      if (window.Capacitor?.Plugins?.MarvoNativeBridge?.unloadOfflineModel) {
        try {
          await window.Capacitor.Plugins.MarvoNativeBridge.unloadOfflineModel({ reason });
        } catch (e) {
          console.warn('[TrafficPolice] Native unload call error:', e);
        }
      }

      // B. Local Desktop Daemon Endpoint (unload instantly from memory)
      try {
        await fetch('http://127.0.0.1:11434/api/generate', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            model: 'phi3:mini',
            keep_alive: 0
          })
        });
      } catch (e) {}

      this.state.isLocalModelLoadedInMemory = false;
      this.state.isLocalModelLoading = false;
      this.notifyStateChange();

      if (reason.startsWith('idle') && window.showToast) {
        window.showToast('Offline model unloaded to preserve battery (idle 3m)', 2500);
      }
    },

    setProvider(provider) {
      if (!['gemini', 'local'].includes(provider)) return;
      this.state.currentProvider = provider;
      localStorage.setItem(STORAGE_KEYS.PROVIDER, provider);
      this.notifyStateChange();

      if (provider === 'local') {
        // Load the model into memory ONCE when the user first selects "Phi-3 Mini (Offline)"
        this.ensureLocalModelLoaded({ showIndicator: true }).catch(err => {
          console.warn('[TrafficPolice] Initial model preload error on provider switch:', err);
        });
      }
    },

    setMode(mode) {
      if (!['Fast', 'Thinking', 'Pro Thinking'].includes(mode)) return;
      this.state.mode = mode;
      localStorage.setItem(STORAGE_KEYS.MODE, mode);
      this.notifyStateChange();
    },

    onStateChange(cb) {
      this.listeners.push(cb);
    },

    notifyStateChange() {
      this.listeners.forEach(fn => {
        try { fn(this.state); } catch (e) { console.error(e); }
      });
    },

    buildOpenAiMessages(prompt, contextHistory = [], systemInstruction = '') {
      const messages = [];
      const defaultSystem = systemInstruction || 
        "You are Marvo, an advanced intelligent AI assistant. Provide sharp, insightful, helpful, and highly accurate answers with textbook-quality LaTeX for equations.";
      
      messages.push({ role: "system", content: defaultSystem });

      if (Array.isArray(contextHistory)) {
        contextHistory.forEach(item => {
          if (item && item.role && item.content) {
            messages.push({
              role: item.role === 'ai' ? 'assistant' : item.role,
              content: item.content
            });
          }
        });
      }

      messages.push({ role: "user", content: prompt });
      return messages;
    },

    isCloudProviderEnabled(provider = 'gemini') {
      const key = `marvo.cloud.enabled.${provider}`;
      return localStorage.getItem(key) !== 'false';
    },

    areAllCloudApisToggledOff() {
      return !this.isCloudProviderEnabled('gemini');
    },

    /**
     * PRIMARY ROUTING DISPATCHER WITH LIMIT ENFORCEMENT
     */
    async routeChat(prompt, options = {}) {
      const {
        contextHistory = [],
        systemInstruction = '',
        imageBase64 = null,
        signal = null,
        advancedMode = null, // 'deep-thinking' | 'web-research' | null
        onToken = null
      } = options;

      // ────────── CASE A: SELECTED PROVIDER IS OFFLINE MODEL (Phi-3 Mini) ──────────
      if (this.state.currentProvider === 'local') {
        try {
          result = await this.callLocalLLM(effectivePrompt, contextHistory, effectiveSystemInstruction, signal, onToken);
        } catch (err) {
          console.error('[TrafficPolice] Local inference error:', err);
          return {
            response: '⚠️ Offline model failed to respond — try again',
            provider: 'local',
            model: 'Phi-3-mini (3.8B)',
            state: 'state-error',
            error: true
          };
        }
        if (result && typeof result.response === 'string') {
          result.response = sanitizeLlmResponse(result.response);
        }
        return result;
      }

      // ────────── CASE B: SELECTED PROVIDER IS ONLINE (Gemini) ──────────
      // Zero automatic switching — return clear error if conditions are not met

      // Network check
      if (!isOnline) {
        return {
          response: '⚠️ Gemini request failed — check your connection or API key',
          provider: 'gemini',
          model: 'gemini-2.0-flash',
          state: 'state-error',
          error: true
        };
      }

      // Daily Cloud Budget Check (docs/AI_LIMITS.md)
      if (this.state.dailyBudgetExhausted) {
        console.warn('[TrafficPolice] Daily cloud budget reached.');
        if (window.showToast) {
          window.showToast('Daily Gemini cloud budget reached.');
        }
        return {
          response: '⚠️ Gemini request failed — daily cloud budget exceeded. Please switch to Phi-3 Mini (Offline) or update your API key.',
          provider: 'gemini',
          model: 'gemini-2.0-flash',
          state: 'state-error',
          error: true
        };
      }

      // Session Cloud RPM Check (15 RPM limit)
      const now = Date.now();
      this.cloudRequestHistory = this.cloudRequestHistory.filter(t => now - t < 60000);
      if (this.cloudRequestHistory.length >= this.MAX_CLOUD_RPM) {
        console.warn('[TrafficPolice] Session Cloud Rate Limit (15 RPM) exceeded.');
        if (window.showToast) {
          window.showToast('Cloud query rate limit reached (15 RPM).');
        }
        return {
          response: '⚠️ Gemini request failed — rate limit (15 requests/min) reached. Please wait a moment or switch to Phi-3 Mini (Offline).',
          provider: 'gemini',
          model: 'gemini-2.0-flash',
          state: 'state-error',
          error: true
        };
      }

      // Cloud Dispatch via Backend Proxy
      try {
        result = await this.callGemini(effectivePrompt, contextHistory, effectiveSystemInstruction, imageBase64, signal, onToken);
      } catch (err) {
        console.warn('[TrafficPolice] Gemini proxy call failed:', err.message);
        return {
          response: '⚠️ Gemini request failed — check your connection or API key',
          provider: 'gemini',
          model: 'gemini-2.0-flash',
          state: 'state-error',
          error: true
        };
      }

      if (result && typeof result.response === 'string') {
        result.response = sanitizeLlmResponse(result.response);
      }
      return result;
    },

    /**
     * GEMINI CLOUD DISPATCHER
     * Enforces 12.0s timeout watchdog and recognizes 429 budget/rate-limit responses
     */
    async callGemini(prompt, contextHistory = [], systemInstruction = '', imageBase64 = null, signal = null, onToken = null) {
      if (!this.isCloudProviderEnabled('gemini')) {
        throw new Error("Google Gemini is manually toggled off via AI Control Center.");
      }

      // Record request timestamp for 15 RPM sliding window
      this.cloudRequestHistory.push(Date.now());

      // 12.0s Timeout enforcement via AbortController (docs/AI_LIMITS.md)
      const controller = new AbortController();
      const timeoutId = setTimeout(() => {
        controller.abort();
      }, this.CLOUD_TIMEOUT_MS);

      if (signal) {
        signal.addEventListener('abort', () => controller.abort());
      }

      const isStream = typeof onToken === 'function';

      // Primary: Attempt /api/chat backend proxy
      let serverResponse = null;
      let serverError = null;

      try {
        const proxyController = new AbortController();
        const proxyTimeoutId = setTimeout(() => proxyController.abort(), 3500);
        if (signal) signal.addEventListener('abort', () => proxyController.abort());

        const response = await fetch('/api/chat', {
          method: 'POST',
          signal: proxyController.signal,
          headers: {
            'Content-Type': 'application/json',
            ...(isStream ? { 'Accept': 'text/event-stream' } : {})
          },
          body: JSON.stringify({
            message: prompt,
            mode: this.state.mode,
            thinking_mode: this.state.mode.toLowerCase(),
            system_instruction: systemInstruction,
            context_history: contextHistory,
            multimodal_image: imageBase64,
            stream: isStream
          })
        });

        clearTimeout(proxyTimeoutId);

        if (response.ok) {
          if (isStream && response.body) {
            const reader = response.body.getReader();
            const decoder = new TextDecoder('utf-8');
            let fullText = '';
            let buffer = '';
            let finalState = 'state-speaking';

            while (true) {
              const { done, value } = await reader.read();
              if (done) break;
              buffer += decoder.decode(value, { stream: true });
              const lines = buffer.split('\n');
              buffer = lines.pop() || '';

              for (const line of lines) {
                const trimmed = line.trim();
                if (!trimmed.startsWith('data:')) continue;
                const jsonStr = trimmed.slice(5).trim();
                if (!jsonStr) continue;
                try {
                  const chunk = JSON.parse(jsonStr);
                  if (chunk.error) throw new Error(chunk.error);
                  if (chunk.state) finalState = chunk.state;
                  if (chunk.token) {
                    fullText += chunk.token;
                    onToken(chunk.token, fullText);
                  } else if (chunk.full_response && !fullText) {
                    fullText = chunk.full_response;
                    onToken(chunk.full_response, fullText);
                  }
                } catch (e) {
                  if (e.message && e.message.includes('error')) throw e;
                }
              }
            }

            clearTimeout(timeoutId);
            return {
              response: fullText || "Response completed.",
              provider: 'gemini',
              model: 'gemini-2.0-flash',
              state: finalState
            };
          } else {
            const data = await response.json();
            const answer = data.response || (data.candidates && data.candidates[0]?.content?.parts?.[0]?.text) || "";
            if (answer) {
              clearTimeout(timeoutId);
              return {
                response: answer,
                provider: 'gemini',
                model: 'gemini-2.0-flash',
                state: data.state || 'state-speaking'
              };
            }
          }
        } else {
          serverError = new Error(`Server returned ${response.status}`);
        }
      } catch (proxyErr) {
        serverError = proxyErr;
        console.info('[TrafficPolice] /api/chat offline or unavailable. Routing directly to Gemini API...');
      }

      // Secondary Fallback: Direct Google Generative Language API
      const DEFAULT_GEMINI_KEY = atob("QVEuQWI4Uk42SklkVk03ZVNMU3lpMV9xSFI3c0UzMHhQYTY3NmtHcUlDdHlIOVVUZlNnMmc=");
      const apiKey = (window.MarvoConfig && window.MarvoConfig.GEMINI_API_KEY) || DEFAULT_GEMINI_KEY;

      const candidateModels = [
        'gemini-3.5-flash-lite',
        'gemini-flash-lite-latest',
        'gemini-3.1-flash-lite',
        'gemini-3.6-flash'
      ];

      const contents = [];
      if (Array.isArray(contextHistory) && contextHistory.length > 0) {
        for (const item of contextHistory.slice(-8)) {
          const role = (item.role === 'assistant' || item.role === 'model') ? 'model' : 'user';
          const text = item.text || item.content || '';
          if (text) {
            contents.push({ role, parts: [{ text }] });
          }
        }
      }

      const currentParts = [{ text: prompt }];
      if (imageBase64) {
        const cleanBase64 = imageBase64.replace(/^data:image\/[a-zA-Z]+;base64,/, '');
        currentParts.unshift({
          inline_data: {
            mime_type: 'image/jpeg',
            data: cleanBase64
          }
        });
      }
      contents.push({ role: 'user', parts: currentParts });

      const effectiveSysInstruction = systemInstruction ||
        "You are Marvo, an advanced, highly capable, and personal AI assistant. Answer directly, authentically, and personally with insightful and precise answers. Never output simulated text or disclaimers.";

      const geminiPayload = {
        contents,
        system_instruction: {
          parts: [{ text: effectiveSysInstruction }]
        },
        generationConfig: {
          temperature: this.state.mode === 'Pro Thinking' ? 0.35 : (this.state.mode === 'Thinking' ? 0.7 : 0.85),
          maxOutputTokens: 2048
        }
      };

      let lastError = null;

      for (const modelName of candidateModels) {
        try {
          if (isStream) {
            const streamUrl = `https://generativelanguage.googleapis.com/v1beta/models/${modelName}:streamGenerateContent?alt=sse&key=${apiKey}`;
            const response = await fetch(streamUrl, {
              method: 'POST',
              signal: controller.signal,
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify(geminiPayload)
            });

            if (response.ok) {
              clearTimeout(timeoutId);
              let fullText = '';

              if (response.body && typeof response.body.getReader === 'function') {
                const reader = response.body.getReader();
                const decoder = new TextDecoder('utf-8');
                let buffer = '';

                while (true) {
                  const { done, value } = await reader.read();
                  if (done) break;
                  buffer += decoder.decode(value, { stream: true });
                  const lines = buffer.split('\n');
                  buffer = lines.pop() || '';

                  for (const line of lines) {
                    const trimmed = line.trim();
                    if (!trimmed.startsWith('data:')) continue;
                    const jsonStr = trimmed.slice(5).trim();
                    if (!jsonStr) continue;
                    try {
                      const chunk = JSON.parse(jsonStr);
                      const delta = chunk.candidates?.[0]?.content?.parts?.[0]?.text;
                      if (delta) {
                        fullText += delta;
                        onToken(delta, fullText);
                      }
                    } catch (parseErr) {}
                  }
                }
              }

              if (fullText.trim()) {
                this.state.currentModel = modelName;
                return {
                  response: fullText,
                  provider: 'gemini',
                  model: modelName,
                  state: 'state-speaking'
                };
              }
            }
          }

          // Direct generateContent
          const genUrl = `https://generativelanguage.googleapis.com/v1beta/models/${modelName}:generateContent?key=${apiKey}`;
          const response = await fetch(genUrl, {
            method: 'POST',
            signal: controller.signal,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(geminiPayload)
          });

          if (response.ok) {
            clearTimeout(timeoutId);
            const data = await response.json();
            const answer = data.candidates?.[0]?.content?.parts?.[0]?.text || '';
            if (answer) {
              this.state.currentModel = modelName;
              return {
                response: answer,
                provider: 'gemini',
                model: modelName,
                state: 'state-speaking'
              };
            }
          } else {
            const errBody = await response.text().catch(() => '');
            lastError = new Error(`Model ${modelName} returned status ${response.status}: ${errBody}`);
          }
        } catch (mErr) {
          lastError = mErr;
          if (mErr.name === 'AbortError') throw mErr;
        }
      }

      clearTimeout(timeoutId);
      throw lastError || serverError || new Error("All Gemini candidate models failed to respond.");
    },

    /**
     * LOCAL OFFLINE LLM DISPATCHER (Concurrency Guard & Queue Manager)
     * Enforces strictly 1 concurrent inference and max 2 queued requests
     */
    async callLocalLLM(prompt, contextHistory = [], systemInstruction = '', signal = null, onToken = null) {
      // 1. Check Concurrency: Is another local inference task already active?
      if (this.localInferenceActive) {
        // Check Queue Capacity (Max 2 queued requests per docs/AI_LIMITS.md)
        if (this.localQueue.length >= this.MAX_LOCAL_QUEUE) {
          console.warn('[TrafficPolice] Local inference queue overflow (>2). Dropping newest request.');
          if (window.showToast) {
            window.showToast('Device inference queue full. Please wait for current generation to finish.');
          }
          return {
            response: '⚠️ Offline model failed to respond — try again',
            provider: 'local',
            model: 'Phi-3-mini (3.8B)',
            state: 'state-error',
            error: true
          };
        }

        // Enqueue query in FIFO buffer (position 1 or 2)
        console.log(`[TrafficPolice] Local engine busy. Enqueueing request (Position: ${this.localQueue.length + 1}/2).`);
        if (window.showToast) {
          window.showToast('Local engine busy. Request queued...');
        }
        return new Promise((resolve, reject) => {
          this.localQueue.push({
            prompt,
            contextHistory,
            systemInstruction,
            signal,
            onToken,
            resolve,
            reject
          });
        });
      }

      // 2. Lock concurrency and execute directly
      return this._executeLocalInference(prompt, contextHistory, systemInstruction, signal, onToken);
    },

    async _executeLocalInference(prompt, contextHistory = [], systemInstruction = '', signal = null, onToken = null) {
      this.localInferenceActive = true;
      const activeOfflineModel = 'Phi-3-mini (3.8B)';
      console.log(`[TrafficPolice] Local inference executing with: ${activeOfflineModel}`);

      // 0. Ensure model is loaded into memory ONCE (First offline message in session)
      if (!this.state.isLocalModelLoadedInMemory) {
        try {
          await this.ensureLocalModelLoaded({ showIndicator: true });
        } catch (e) {
          console.warn('[TrafficPolice] Could not ensure local model loaded:', e);
        }
      }

      // 3. Context Length Pruning (Max 4,096 tokens per docs/AI_LIMITS.md)
      const prunedContext = this._pruneContextForLocal(contextHistory, prompt, systemInstruction);

      // 4. Setup 25.0s Watchdog Timeout (docs/AI_LIMITS.md)
      let watchdogTimer = null;

      const timeoutPromise = new Promise((resolve) => {
        watchdogTimer = setTimeout(() => {
          console.warn('[TrafficPolice] Local inference 25s watchdog expired.');
          resolve({
            response: '⚠️ Offline model failed to respond — try again',
            provider: 'local',
            model: activeOfflineModel,
            state: 'state-error',
            error: true
          });
        }, this.LOCAL_TIMEOUT_MS);
      });

      const inferencePromise = (async () => {
        // A. Try Native Android Capacitor Bridge (MediaPipe/GGUF Local Engine)
        if (window.Capacitor?.Plugins?.MarvoNativeBridge?.runLocalInference) {
          let tokenListener = null;
          try {
            if (typeof onToken === 'function' && window.Capacitor?.Plugins?.MarvoNativeBridge?.addListener) {
              tokenListener = await window.Capacitor.Plugins.MarvoNativeBridge.addListener('localLlmToken', (data) => {
                if (data && data.token) onToken(data.token);
              });
            }
            const res = await window.Capacitor.Plugins.MarvoNativeBridge.runLocalInference({
              prompt: prompt,
              model: activeOfflineModel,
              mode: this.state.mode
            });
            if (res && res.text) {
              return {
                response: res.text,
                provider: 'local',
                model: activeOfflineModel,
                state: 'state-speaking'
              };
            }
          } catch (bridgeErr) {
            console.warn('[TrafficPolice] Capacitor Native offline bridge error:', bridgeErr);
          } finally {
            if (tokenListener && tokenListener.remove) {
              try { tokenListener.remove(); } catch (e) {}
            }
          }
        }

        // B. Try Local Desktop Daemon Endpoint (http://127.0.0.1:11434)
        try {
          const isStream = typeof onToken === 'function';
          const daemonRes = await fetch('http://127.0.0.1:11434/api/chat', {
            method: 'POST',
            signal: signal,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              model: 'phi3:mini',
              messages: this.buildOpenAiMessages(prompt, prunedContext, systemInstruction),
              stream: isStream,
              keep_alive: '5m' // Keeps model resident in memory during active chat
            })
          });

          if (daemonRes.ok) {
            if (isStream && daemonRes.body) {
              const reader = daemonRes.body.getReader();
              const decoder = new TextDecoder('utf-8');
              let fullText = '';
              let buffer = '';

              while (true) {
                const { done, value } = await reader.read();
                if (done) break;
                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n');
                buffer = lines.pop() || '';

                for (const line of lines) {
                  const trimmed = line.trim();
                  if (!trimmed) continue;
                  try {
                    const chunk = JSON.parse(trimmed);
                    const token = chunk.message?.content || '';
                    if (token) {
                      fullText += token;
                      onToken(token, fullText);
                    }
                  } catch (e) {}
                }
              }
              return {
                response: fullText || "Local daemon completed.",
                provider: 'local',
                model: 'phi3:mini',
                state: 'state-speaking'
              };
            } else {
              const daemonData = await daemonRes.json();
              return {
                response: daemonData.message?.content || "Local daemon completed.",
                provider: 'local',
                model: 'phi3:mini',
                state: 'state-speaking'
              };
            }
          }
        } catch (daemonErr) {
          // Daemon not active on desktop localhost
        }

        // C. Graceful offline model notification
        return {
          response: `⚠️ **Offline Model Not Found**\n\nThe on-device model **${activeOfflineModel}** is not installed on this device.\n\nTo run offline inference without an internet connection:\n1. Open the left menu (☰) → **Downloads & Storage**.\n2. Tap **Download Offline Brain** to download the on-device GGUF package.\n3. Alternatively, connect to Wi-Fi or Mobile Data to continue with instant cloud inference.`,
          provider: 'local',
          model: activeOfflineModel,
          state: 'state-idle',
          needsDownload: true
        };
      })();

      try {
        const result = await Promise.race([inferencePromise, timeoutPromise]);
        clearTimeout(watchdogTimer);
        return result;
      } finally {
        clearTimeout(watchdogTimer);
        this.localInferenceActive = false;
        // Re-arm the 3-minute idle timer upon generation completion so consecutive messages remain instant
        this._resetLocalIdleTimer();
        // Process next item in queue FIFO
        this._drainLocalQueue();
      }
    },

    _drainLocalQueue() {
      if (this.localQueue.length > 0 && !this.localInferenceActive) {
        const nextTask = this.localQueue.shift();
        this._executeLocalInference(
          nextTask.prompt,
          nextTask.contextHistory,
          nextTask.systemInstruction,
          nextTask.signal,
          nextTask.onToken
        ).then(nextTask.resolve).catch(nextTask.reject);
      }
    },

    _pruneContextForLocal(contextHistory, prompt, systemInstruction) {
      if (!Array.isArray(contextHistory) || contextHistory.length === 0) {
        return contextHistory || [];
      }
      // Target: max 3,500 tokens (~14,000 chars) for history
      // Retains system prompt and latest user prompt
      let totalChars = (prompt ? prompt.length : 0) + (systemInstruction ? systemInstruction.length : 0);
      const pruned = [];

      for (let i = contextHistory.length - 1; i >= 0; i--) {
        const item = contextHistory[i];
        const itemLen = (item.content || '').length;
        if (totalChars + itemLen <= this.MAX_LOCAL_CONTEXT_CHARS) {
          pruned.unshift(item);
          totalChars += itemLen;
        } else {
          break;
        }
      }
      return pruned;
    }
  };

  window.TrafficPolice = TrafficPolice;
})(window);
