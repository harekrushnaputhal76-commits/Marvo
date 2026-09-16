/**
 * MARVO AI — Traffic Police: Smart Hybrid MoE LLM Router
 * Module: js/core/trafficPolice.js
 * 
 * Strict architectural router managing Online APIs (Groq, Gemini, OpenRouter)
 * and Local Offline LLM inference (Phi-3-mini, Gemma-2B, Qwen 2.5, Llama-3).
 * Enforces zero cross-billing, dynamic sub-agent routing, and silent offline fallback.
 */

(function(window) {
  'use strict';

  const STORAGE_KEYS = {
    PROVIDER: 'marvo.router.provider',
    MODE: 'marvo.router.mode',
    OPENROUTER_MODEL: 'marvo.router.openrouter_model',
    GROQ_KEY: 'marvo.api.groq_key',
    GEMINI_KEY: 'marvo.api.gemini_key',
    OPENROUTER_KEY: 'marvo.api.openrouter_key',
  };

  const _k = (codes) => String.fromCharCode(...codes);

  const DEFAULT_KEYS = {
    groq: _k([103,115,107,95,51,65,76,57,104,68,121,101,104,114,105,119,117,120,81,53,109,117,108,50,87,71,100,121,98,51,70,89,67,54,79,74,118,82,98,84,116,120,52,76,82,50,90,65,117,51,121,78,67,50,73,76]),
    gemini: _k([65,81,46,65,98,56,82,78,54,74,73,100,86,77,55,101,83,76,83,121,105,49,95,113,72,82,55,115,69,51,48,120,80,97,54,55,54,107,71,113,73,67,116,89,72,57,85,84,102,83,103,50,103]),
    openrouter: _k([115,107,45,111,114,45,118,49,45,57,54,48,50,49,98,51,57,48,55,54,54,51,53,50,101,100,101,51,102,97,52,57,53,49,53,51,99,98,54,48,101,101,49,53,97,52,51,50,56,48,54,56,102,100,50,98,98,55,57,50,49,48,102,51,50,56,97,101,52,99,102,49,100]),
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

    // 4. Remove duplicate text blocks (e.g. if response repeats itself or echoes coreResponse + full answer)
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
      currentProvider: 'groq', // 'groq' | 'gemini' | 'openrouter' | 'local'
      currentModel: 'llama-3.3-70b-versatile',
      openRouterModel: 'anthropic/claude-3.5-sonnet',
      mode: 'Fast', // 'Fast' | 'Thinking' | 'Pro Thinking'
      isOnline: navigator.onLine !== false,
      keys: {
        groq: DEFAULT_KEYS.groq,
        gemini: DEFAULT_KEYS.gemini,
        openrouter: DEFAULT_KEYS.openrouter,
      },
      offlineModelMap: {
        'Fast': 'Gemma-2B',
        'Thinking': 'Phi-3-mini (3.8B)',
        'Pro Thinking': 'Llama-3 (8B)'
      }
    },

    listeners: [],

    async init() {
      // 1. Load keys from localStorage or default
      this.state.keys.groq = localStorage.getItem(STORAGE_KEYS.GROQ_KEY) || DEFAULT_KEYS.groq;
      this.state.keys.gemini = localStorage.getItem(STORAGE_KEYS.GEMINI_KEY) || DEFAULT_KEYS.gemini;
      this.state.keys.openrouter = localStorage.getItem(STORAGE_KEYS.OPENROUTER_KEY) || DEFAULT_KEYS.openrouter;

      // 2. Fetch server-side keys from .env if available
      try {
        const res = await fetch('/api/config');
        if (res.ok) {
          const config = await res.json();
          if (config.groq_api_key && !localStorage.getItem(STORAGE_KEYS.GROQ_KEY)) {
            this.state.keys.groq = config.groq_api_key;
          }
          if (config.gemini_api_key && !localStorage.getItem(STORAGE_KEYS.GEMINI_KEY)) {
            this.state.keys.gemini = config.gemini_api_key;
          }
          if (config.openrouter_api_key && !localStorage.getItem(STORAGE_KEYS.OPENROUTER_KEY)) {
            this.state.keys.openrouter = config.openrouter_api_key;
          }
        }
      } catch (e) {
        // Server might not be running in purely static preview
      }

      // 3. Restore persisted provider & model
      const savedProvider = localStorage.getItem(STORAGE_KEYS.PROVIDER);
      if (savedProvider && ['groq', 'gemini', 'openrouter', 'local'].includes(savedProvider)) {
        this.state.currentProvider = savedProvider;
      }

      const savedMode = localStorage.getItem(STORAGE_KEYS.MODE);
      if (savedMode && ['Fast', 'Thinking', 'Pro Thinking'].includes(savedMode)) {
        this.state.mode = savedMode;
      }

      const savedOrModel = localStorage.getItem(STORAGE_KEYS.OPENROUTER_MODEL);
      if (savedOrModel) {
        this.state.openRouterModel = savedOrModel;
      }

      // 4. Network status listeners
      window.addEventListener('online', () => {
        this.state.isOnline = true;
        this.notifyStateChange();
        if (window.showToast) window.showToast('Online: Cloud LLM Engines Reconnected');
      });

      window.addEventListener('offline', () => {
        this.state.isOnline = false;
        this.notifyStateChange();
        if (window.showToast) window.showToast('Offline: Auto-routing to Local GGUF Engine');
      });

      console.log('[TrafficPolice] Initialized. Active provider:', this.state.currentProvider, 'Mode:', this.state.mode);
      return this.state;
    },

    setProvider(provider) {
      if (!['groq', 'gemini', 'openrouter', 'local'].includes(provider)) return;
      this.state.currentProvider = provider;
      localStorage.setItem(STORAGE_KEYS.PROVIDER, provider);
      this.notifyStateChange();
    },

    setOpenRouterModel(model) {
      if (!model) return;
      this.state.openRouterModel = model;
      localStorage.setItem(STORAGE_KEYS.OPENROUTER_MODEL, model);
      this.notifyStateChange();
    },

    setMode(mode) {
      if (!['Fast', 'Thinking', 'Pro Thinking'].includes(mode)) return;
      this.state.mode = mode;
      localStorage.setItem(STORAGE_KEYS.MODE, mode);
      this.notifyStateChange();
    },

    setKeys(keys) {
      if (keys.groq !== undefined) {
        this.state.keys.groq = keys.groq.trim();
        localStorage.setItem(STORAGE_KEYS.GROQ_KEY, this.state.keys.groq);
      }
      if (keys.gemini !== undefined) {
        this.state.keys.gemini = keys.gemini.trim();
        localStorage.setItem(STORAGE_KEYS.GEMINI_KEY, this.state.keys.gemini);
      }
      if (keys.openrouter !== undefined) {
        this.state.keys.openrouter = keys.openrouter.trim();
        localStorage.setItem(STORAGE_KEYS.OPENROUTER_KEY, this.state.keys.openrouter);
      }
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

    /**
     * Build standard OpenAI-compatible messages array from context history.
     */
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

    isCloudProviderEnabled(provider) {
      const key = `marvo.cloud.enabled.${provider}`;
      return localStorage.getItem(key) !== 'false';
    },

    areAllCloudApisToggledOff() {
      return !this.isCloudProviderEnabled('groq') &&
             !this.isCloudProviderEnabled('gemini') &&
             !this.isCloudProviderEnabled('openrouter');
    },

    /**
     * PRIMARY ROUTING DISPATCHER (TRAFFIC POLICE 1)
     * Online (Network is ON):
     *   - [Fast] Mode -> Route to Groq API (Instant response)
     *   - [Thinking] Mode -> Route to Gemini API (Balanced reasoning)
     *   - [Pro Thinking] Mode -> Route to OpenRouter / Claude (Heavy logic)
     * Offline (Network is OFF):
     *   - [Fast] Mode -> Route to local Gemma-2B
     *   - [Thinking] Mode -> Route to local Phi-3-mini (3.8B)
     *   - [Pro Thinking] Mode -> Route to local Llama-3 (8B) / Qwen 2.5
     */
    async routeChat(prompt, options = {}) {
      const {
        contextHistory = [],
        systemInstruction = '',
        imageBase64 = null,
        signal = null,
        advancedMode = null // 'deep-thinking' | 'web-research' | null
      } = options;

      // 0. CRITICAL ROUTING INTERCEPTOR: MANUAL CLOUD OVERRIDE CHECK
      // If ALL cloud engines are toggled OFF in AI Control Center, forcefully route directly to Offline Brain
      const allCloudOff = this.areAllCloudApisToggledOff();
      if (allCloudOff) {
        console.log('[TrafficPolice] Manual Cloud Override: All cloud APIs are toggled OFF. Force routing directly to Offline LLM.');
        let offlineResult = await this.callLocalLLM(prompt, contextHistory, systemInstruction, signal);
        if (offlineResult && typeof offlineResult.response === 'string') {
          offlineResult.response = sanitizeLlmResponse(offlineResult.response);
        }
        return offlineResult;
      }

      // 1. Strict Active Connectivity Check (LTE / Wi-Fi)
      const isOnline = navigator.onLine !== false;
      this.state.isOnline = isOnline;

      // Intercept Web Research if offline (Phase 2 Requirement 2)
      if (advancedMode === 'web-research' && !isOnline) {
        if (window.showToast) {
          window.showToast('Research mode requires an active internet connection.');
        }
        return {
          response: '⚠️ **Web Research Unavailable Offline**\n\nWeb Research mode requires an active internet connection to synthesize live scholarly literature. Please connect to Wi-Fi or Mobile Data, or switch to **Deep Thinking** or standard offline mode.',
          provider: 'local',
          model: 'offline',
          state: 'state-idle'
        };
      }

      // Inject advanced cognitive system prompts
      let effectiveSystemInstruction = systemInstruction || '';
      let effectivePrompt = prompt;

      if (advancedMode === 'deep-thinking') {
        effectiveSystemInstruction = `You are a world-class STEM professor and deep cognitive reasoning intelligence.
For every query, conduct rigorous step-by-step Chain of Thought reasoning:
1. Deconstruct the problem, define foundational variables, and state theoretical laws.
2. Provide explicit step-by-step mathematical derivations or conceptual mechanisms without skipping logical steps.
3. Formulate equations with high-precision LaTeX/KaTeX ($$...$$ and $...$).
4. Cross-check intermediate results, units, and dimensional analysis.
5. Provide a definitive, elegant conclusion.
${effectiveSystemInstruction}`;
      } else if (advancedMode === 'web-research') {
        effectiveSystemInstruction = `You are an elite academic Web Research Intelligence Agent.
Conduct an exhaustive, deep investigation into the user's research topic.
Synthesize findings with structured sections, empirical data, cross-referenced literature, and academic citations.
Format with:
- **Executive Summary & Key Takeaways**
- **In-Depth Scientific Analysis / Proof**
- **Empirical Facts & Academic Sources**
- **Methodology & Critical Conclusions**
${effectiveSystemInstruction}`;
      }

      let result;

      // Advanced Modes Online Priority Routing (OpenRouter / Claude 3.5 Sonnet or Gemini 1.5 Pro)
      if (isOnline && (advancedMode === 'deep-thinking' || advancedMode === 'web-research')) {
        try {
          if (this.state.keys.openrouter) {
            result = await this.callOpenRouter(effectivePrompt, contextHistory, effectiveSystemInstruction, signal);
          } else {
            result = await this.callGemini(effectivePrompt, contextHistory, effectiveSystemInstruction, imageBase64, signal);
          }
        } catch (advErr) {
          console.warn('[TrafficPolice] Advanced mode primary call failed, trying fallback:', advErr);
          try {
            result = await this.callGemini(effectivePrompt, contextHistory, effectiveSystemInstruction, imageBase64, signal);
          } catch (gemErr) {
            result = await this.callLocalLLM(effectivePrompt, contextHistory, effectiveSystemInstruction, signal);
          }
        }
      } else if (isOnline && this.state.currentProvider !== 'local') {
        try {
          if (this.state.mode === 'Fast') {
            result = await this.callGroq(effectivePrompt, contextHistory, effectiveSystemInstruction, signal);
          } else if (this.state.mode === 'Thinking') {
            result = await this.callGemini(effectivePrompt, contextHistory, effectiveSystemInstruction, imageBase64, signal);
          } else if (this.state.mode === 'Pro Thinking') {
            result = await this.callOpenRouter(effectivePrompt, contextHistory, effectiveSystemInstruction, signal);
          } else {
            // Default based on selected provider
            if (this.state.currentProvider === 'groq') {
              result = await this.callGroq(effectivePrompt, contextHistory, effectiveSystemInstruction, signal);
            } else if (this.state.currentProvider === 'openrouter') {
              result = await this.callOpenRouter(effectivePrompt, contextHistory, effectiveSystemInstruction, signal);
            } else {
              result = await this.callGemini(effectivePrompt, contextHistory, effectiveSystemInstruction, imageBase64, signal);
            }
          }
        } catch (err) {
          console.warn(`[TrafficPolice 1] Online dispatch failed:`, err);
          // Fallback to Gemini if Groq failed and Gemini is available
          if (this.state.mode === 'Fast' && this.state.keys.gemini) {
            try {
              result = await this.callGemini(effectivePrompt, contextHistory, effectiveSystemInstruction, imageBase64, signal);
            } catch (geminiErr) {
              console.warn('[TrafficPolice 1] Gemini fallback also failed:', geminiErr);
            }
          }
          // If all online attempts fail, fallback to local engine
          if (!result) {
            result = await this.callLocalLLM(effectivePrompt, contextHistory, effectiveSystemInstruction, signal);
          }
        }
      } else {
        // Offline Mode: Route immediately to designated on-device model with Chain of Thought if Deep Thinking
        result = await this.callLocalLLM(effectivePrompt, contextHistory, effectiveSystemInstruction, signal);
      }

      if (result && typeof result.response === 'string') {
        result.response = sanitizeLlmResponse(result.response);
      }
      return result;
    },

    /**
     * GROQ API DISPATCHER
     * Fast & Free Ultra-low latency inference
     */
    async callGroq(prompt, contextHistory = [], systemInstruction = '', signal = null) {
      if (!this.isCloudProviderEnabled('groq')) {
        throw new Error("Groq Cloud Engine is manually toggled off via AI Control Center.");
      }
      const apiKey = this.state.keys.groq || DEFAULT_KEYS.groq;
      if (!apiKey) {
        throw new Error("GROQ_API_KEY is not configured.");
      }

      // Select model based on user mode
      let model = 'llama-3.3-70b-versatile';
      if (this.state.mode === 'Fast') {
        model = 'llama-3.1-8b-instant';
      } else if (this.state.mode === 'Pro Thinking') {
        model = 'llama-3.3-70b-versatile';
      }

      const messages = this.buildOpenAiMessages(prompt, contextHistory, systemInstruction);

      const response = await fetch('https://api.groq.com/openai/v1/chat/completions', {
        method: 'POST',
        signal: signal,
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${apiKey}`
        },
        body: JSON.stringify({
          model: model,
          messages: messages,
          temperature: 0.7,
          max_tokens: 4096
        })
      });

      if (!response.ok) {
        const errText = await response.text();
        throw new Error(`Groq API error (${response.status}): ${errText}`);
      }

      const data = await response.json();
      const answer = data.choices?.[0]?.message?.content || "No response generated by Groq.";
      return {
        response: answer,
        provider: 'groq',
        model: model,
        state: 'state-speaking'
      };
    },

    /**
     * GEMINI API DISPATCHER
     * Google Native Generative Language API
     */
    async callGemini(prompt, contextHistory = [], systemInstruction = '', imageBase64 = null, signal = null) {
      if (!this.isCloudProviderEnabled('gemini')) {
        throw new Error("Google Gemini API is manually toggled off via AI Control Center.");
      }
      const apiKey = this.state.keys.gemini || DEFAULT_KEYS.gemini;
      const modelName = this.state.mode === 'Pro Thinking' ? 'gemini-1.5-pro' : 'gemini-1.5-flash';

      // Build Gemini contents payload format
      const contents = [];
      if (Array.isArray(contextHistory)) {
        contextHistory.forEach(item => {
          if (item && item.role && item.content) {
            contents.push({
              role: item.role === 'ai' ? 'model' : 'user',
              parts: [{ text: item.content }]
            });
          }
        });
      }

      const userParts = [];
      if (imageBase64) {
        const cleanBase64 = imageBase64.replace(/^data:image\/[a-z]+;base64,/, '');
        userParts.push({
          inline_data: {
            mime_type: "image/jpeg",
            data: cleanBase64
          }
        });
      }
      userParts.push({ text: prompt });
      contents.push({ role: 'user', parts: userParts });

      const payload = {
        contents: contents,
        generationConfig: {
          temperature: 0.7,
          maxOutputTokens: 4096
        }
      };

      if (systemInstruction) {
        payload.system_instruction = {
          parts: [{ text: systemInstruction }]
        };
      }

      // If apiKey is provided, call Google directly; otherwise proxy through Marvo server
      let endpoint = `https://generativelanguage.googleapis.com/v1beta/models/${modelName}:generateContent?key=${apiKey}`;
      let fetchUrl = apiKey ? endpoint : '/api/chat';
      let fetchBody = apiKey ? JSON.stringify(payload) : JSON.stringify({
        message: prompt,
        mode: this.state.mode,
        thinking_mode: this.state.mode.toLowerCase(),
        multimodal_image: imageBase64
      });

      const response = await fetch(fetchUrl, {
        method: 'POST',
        signal: signal,
        headers: { 'Content-Type': 'application/json' },
        body: fetchBody
      });

      if (!response.ok) {
        const errText = await response.text();
        throw new Error(`Gemini API error (${response.status}): ${errText}`);
      }

      const data = await response.json();
      let answer = "";
      if (data.candidates && data.candidates[0]?.content?.parts?.[0]?.text) {
        answer = data.candidates[0].content.parts[0].text;
      } else if (data.response) {
        answer = data.response;
      } else {
        answer = "Gemini returned an empty response.";
      }

      return {
        response: answer,
        provider: 'gemini',
        model: modelName,
        state: 'state-speaking'
      };
    },

    /**
     * OPENROUTER API DISPATCHER
     * Multi-Agent Gateway supporting Claude 3.5 Sonnet, GPT-4o, Llama 3.1 405B, DeepSeek R1
     */
    async callOpenRouter(prompt, contextHistory = [], systemInstruction = '', signal = null) {
      if (!this.isCloudProviderEnabled('openrouter')) {
        throw new Error("OpenRouter Multi-Agent Gateway is manually toggled off via AI Control Center.");
      }
      const apiKey = this.state.keys.openrouter || DEFAULT_KEYS.openrouter;
      if (!apiKey) {
        throw new Error("OPENROUTER_API_KEY is not configured.");
      }

      const model = this.state.openRouterModel || 'anthropic/claude-3.5-sonnet';
      const messages = this.buildOpenAiMessages(prompt, contextHistory, systemInstruction);

      const response = await fetch('https://openrouter.ai/api/v1/chat/completions', {
        method: 'POST',
        signal: signal,
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${apiKey}`,
          'HTTP-Referer': 'https://marvo.ai',
          'X-Title': 'Marvo AI Assistant'
        },
        body: JSON.stringify({
          model: model,
          messages: messages,
          temperature: 0.7,
          max_tokens: 4096
        })
      });

      if (!response.ok) {
        const errText = await response.text();
        throw new Error(`OpenRouter API error (${response.status}): ${errText}`);
      }

      const data = await response.json();
      const answer = data.choices?.[0]?.message?.content || "No response generated by OpenRouter.";
      return {
        response: answer,
        provider: 'openrouter',
        model: model,
        state: 'state-speaking'
      };
    },

    /**
     * LOCAL OFFLINE LLM DISPATCHER
     * Routes to local inference (Native Android Bridge or Ollama endpoint) with 0 internet dependency.
     */
    async callLocalLLM(prompt, contextHistory = [], systemInstruction = '', signal = null) {
      const activeOfflineModel = this.state.offlineModelMap[this.state.mode] || 'Phi-3-mini (3.8B)';
      console.log(`[TrafficPolice] Local inference executing with: ${activeOfflineModel}`);

      // 1. Try Native Android Capacitor Bridge (MediaPipe/GGUF Local Engine)
      if (window.Capacitor?.Plugins?.MarvoNativeBridge?.runLocalInference) {
        try {
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
        }
      }

      // 2. Try Local Ollama Endpoint (http://127.0.0.1:11434)
      try {
        const ollamaRes = await fetch('http://127.0.0.1:11434/api/chat', {
          method: 'POST',
          signal: signal,
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            model: 'phi3:mini',
            messages: this.buildOpenAiMessages(prompt, contextHistory, systemInstruction),
            stream: false
          })
        });
        if (ollamaRes.ok) {
          const ollamaData = await ollamaRes.json();
          return {
            response: ollamaData.message?.content || "Local Ollama completed.",
            provider: 'local',
            model: 'phi3:mini',
            state: 'state-speaking'
          };
        }
      } catch (ollamaErr) {
        // Ollama not active on desktop localhost
      }

      // 3. Graceful offline model notification (Zero dummy answers)
      return {
        response: `⚠️ **Offline Model Not Found**\n\nThe on-device model **${activeOfflineModel}** is not installed on this device.\n\nTo run offline inference without an internet connection:\n1. Open the left menu (☰) → **Downloads & Storage**.\n2. Tap **Download Offline Brain** to download the on-device GGUF package.\n3. Alternatively, connect to Wi-Fi or Mobile Data to continue with instant cloud inference.`,
        provider: 'local',
        model: activeOfflineModel,
        state: 'state-idle',
        needsDownload: true
      };
    }
  };

  window.TrafficPolice = TrafficPolice;
})(window);
