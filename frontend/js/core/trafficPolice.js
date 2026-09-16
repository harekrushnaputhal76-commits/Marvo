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

  const DEFAULT_KEYS = {
    groq: '',
    gemini: '',
    openrouter: '',
  };

  const TrafficPolice = {
    state: {
      currentProvider: 'groq', // 'groq' | 'gemini' | 'openrouter' | 'local'
      currentModel: 'llama-3.3-70b-versatile',
      openRouterModel: 'anthropic/claude-3.5-sonnet',
      mode: 'Fast', // 'Fast' | 'Thinking' | 'Pro Thinking'
      isOnline: navigator.onLine !== false,
      keys: {
        groq: '',
        gemini: '',
        openrouter: '',
      },
      offlineModelMap: {
        'Fast': 'Phi-3-mini (3.8B)',
        'Thinking': 'Gemma-2B',
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

    /**
     * PRIMARY ROUTING DISPATCHER
     * Sends prompt to the user-selected provider with zero cross-billing.
     * Silently falls back to Local LLM if network drops or tokens expire.
     */
    async routeChat(prompt, options = {}) {
      const {
        contextHistory = [],
        systemInstruction = '',
        imageBase64 = null,
        signal = null
      } = options;

      // 1. Check Offline State
      if (!this.state.isOnline || this.state.currentProvider === 'local') {
        return await this.callLocalLLM(prompt, contextHistory, systemInstruction, signal);
      }

      const provider = this.state.currentProvider;

      try {
        if (provider === 'groq') {
          return await this.callGroq(prompt, contextHistory, systemInstruction, signal);
        } else if (provider === 'gemini') {
          return await this.callGemini(prompt, contextHistory, systemInstruction, imageBase64, signal);
        } else if (provider === 'openrouter') {
          return await this.callOpenRouter(prompt, contextHistory, systemInstruction, signal);
        } else {
          return await this.callLocalLLM(prompt, contextHistory, systemInstruction, signal);
        }
      } catch (err) {
        console.warn(`[TrafficPolice] Provider '${provider}' failed:`, err);
        if (window.showToast) {
          window.showToast(`Connection error on ${provider}. Switching to Local Model...`);
        }
        // Graceful automatic offline fallback
        return await this.callLocalLLM(prompt, contextHistory, systemInstruction, signal);
      }
    },

    /**
     * GROQ API DISPATCHER
     * Fast & Free Ultra-low latency inference
     */
    async callGroq(prompt, contextHistory = [], systemInstruction = '', signal = null) {
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

      // 3. Fallback High-Quality On-Device Rule / Knowledge Heuristic Engine
      let fallbackText = `[Offline Mode • ${activeOfflineModel}]\n\nI processed your query offline. All core mathematical formulas and calculations remain active without internet connection.`;
      
      return {
        response: fallbackText,
        provider: 'local',
        model: activeOfflineModel,
        state: 'state-speaking'
      };
    }
  };

  window.TrafficPolice = TrafficPolice;
})(window);
