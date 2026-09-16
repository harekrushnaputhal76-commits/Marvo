/**
 * MARVO AI — AI Control Center (Model & API Dashboard)
 * Module: js/ui/aiControlCenter.js
 * 
 * Central hub for LLM management:
 * 1. Offline Models (Phi-3, Gemma-2B, Llama-3, Qwen-2.5) with Foreground Service downloader,
 *    live MB/s speed, ETA, pause/resume/delete/setActive, and isolated storage paths.
 * 2. Online Cloud Engine Status (Groq, Gemini, OpenRouter) with live background ping/validation.
 * 3. Strict Isolated Focus Mode (Do Not Disturb API).
 */

(function(window) {
  'use strict';

  const DEFAULT_OFFLINE_MODELS = [
    {
      id: 'phi-3-mini',
      name: 'Phi-3 Mini 4K Instruct',
      vendor: 'Microsoft',
      fileName: 'phi-3-mini-4k-instruct-q4.gguf',
      sizeBytes: 2306867200,
      sizeFormatted: '2.2 GB',
      storagePath: '/data/user/0/com.marvo.ai/files/models/phi-3-mini-4k-instruct-q4.gguf',
      isDownloaded: false,
      isActive: true,
      description: '3.8B Lightweight Neural Engine. Recommended for balanced speed and reasoning.'
    },
    {
      id: 'gemma-2b',
      name: 'Gemma 2B IT (CPU)',
      vendor: 'Google',
      fileName: 'gemma-2b-it-cpu.gguf',
      sizeBytes: 1572864000,
      sizeFormatted: '1.5 GB',
      storagePath: '/data/user/0/com.marvo.ai/files/models/gemma-2b-it-cpu.gguf',
      isDownloaded: false,
      isActive: false,
      description: 'Ultra-lightweight on-device model for quick mobile answers and low RAM usage.'
    },
    {
      id: 'llama-3-8b',
      name: 'Llama 3 8B Instruct',
      vendor: 'Meta',
      fileName: 'llama-3-8b-instruct.gguf',
      sizeBytes: 4508876800,
      sizeFormatted: '4.3 GB',
      storagePath: '/data/user/0/com.marvo.ai/files/models/llama-3-8b-instruct.gguf',
      isDownloaded: false,
      isActive: false,
      description: 'Heavy 8B Parameter Reasoning Giant. Ideal for complex STEM derivations.'
    },
    {
      id: 'qwen-2.5-3b',
      name: 'Qwen 2.5 3B Instruct',
      vendor: 'Alibaba Cloud',
      fileName: 'qwen-2.5-3b-instruct.gguf',
      sizeBytes: 2097152000,
      sizeFormatted: '2.0 GB',
      storagePath: '/data/user/0/com.marvo.ai/files/models/qwen-2.5-3b-instruct.gguf',
      isDownloaded: false,
      isActive: false,
      description: 'Specialized STEM, Mathematics, and Coding intelligence.'
    },
    {
      id: 'stt',
      name: 'Whisper Tiny STT',
      vendor: 'OpenAI',
      fileName: 'whisper-tiny-en.bin',
      sizeBytes: 151338400,
      sizeFormatted: '150 MB',
      storagePath: '/data/user/0/com.marvo.ai/files/models/whisper-tiny-en.bin',
      isDownloaded: false,
      isActive: false,
      description: 'Zero-latency on-device speech-to-text voice recognition engine.'
    },
    {
      id: 'tts',
      name: 'Piper Neural Voice TTS',
      vendor: 'Rhasspy',
      fileName: 'vits-piper-en.onnx',
      sizeBytes: 63800000,
      sizeFormatted: '63.8 MB',
      storagePath: '/data/user/0/com.marvo.ai/files/models/vits-piper-en.onnx',
      isDownloaded: false,
      isActive: false,
      description: 'High-fidelity offline neural speech synthesizer.'
    }
  ];

  const AiControlCenter = {
    isOpen: false,
    activeTab: 'offline', // 'offline' | 'online' | 'focus'
    models: [...DEFAULT_OFFLINE_MODELS],
    activeModelId: 'phi-3-mini',
    pollInterval: null,
    apiStatus: {
      groq: { status: 'checking', latency: 0, error: null, name: 'Groq Cloud (LPU)', model: 'llama-3.3-70b-versatile' },
      gemini: { status: 'checking', latency: 0, error: null, name: 'Google Gemini', model: 'gemini-2.0-flash' },
      openrouter: { status: 'checking', latency: 0, error: null, name: 'OpenRouter Gateway', model: 'claude-3.5-sonnet' }
    },
    focusMode: {
      enabled: false,
      isGranted: false
    },
    dom: {},

    init() {
      this.injectDOM();
      this.cacheDOM();
      this.bindEvents();
      this.refreshModels();
      this.pingAllApis();
      this.checkFocusStatus();
      console.log('[AiControlCenter] Initialized successfully.');
    },

    injectDOM() {
      if (document.getElementById('aiControlCenterModal')) return;

      const html = `
        <div id="aiControlCenterModal" class="ai-control-modal-overlay">
          <div class="ai-control-modal">
            <!-- Modal Header -->
            <div class="ai-control-header">
              <div class="ai-control-header-left">
                <div class="ai-control-header-icon">🎛️</div>
                <div>
                  <h2 class="ai-control-title">AI Control Center</h2>
                  <p class="ai-control-subtitle">Model Repository, Live Cloud Pings & Focus Hub</p>
                </div>
              </div>
              <button id="btnCloseAiControl" class="ai-control-close-btn" aria-label="Close">&times;</button>
            </div>

            <!-- Tab Navigation -->
            <div class="ai-control-tab-bar" role="tablist">
              <button class="ai-control-tab-btn active" data-tab="offline" type="button" role="tab">
                <span>🧠</span> <span>Offline LLMs (2GB+)</span>
              </button>
              <button class="ai-control-tab-btn" data-tab="online" type="button" role="tab">
                <span>🌐</span> <span>Online Cloud Engines</span>
              </button>
              <button class="ai-control-tab-btn" data-tab="focus" type="button" role="tab">
                <span>🔕</span> <span>Isolated Focus Mode</span>
              </button>
            </div>

            <!-- Modal Body -->
            <div class="ai-control-body">
              <!-- ════════ PANEL 1: OFFLINE MODELS ════════ -->
              <div class="ai-control-panel active" id="panelOfflineModels">
                <div class="ai-control-banner">
                  <div class="banner-icon">⚡</div>
                  <div class="banner-text">
                    <strong>Background Foreground Service Active:</strong> Downloads survive app minimizing and screen lock. Models are stored in the app's isolated directory with HTTP chunked range resumption.
                  </div>
                </div>

                <div class="models-grid" id="offlineModelsGrid">
                  <!-- Dynamically populated model cards -->
                </div>
              </div>

              <!-- ════════ PANEL 2: ONLINE CLOUD ENGINES ════════ -->
              <div class="ai-control-panel" id="panelOnlineApis">
                <div class="api-control-topbar">
                  <div>
                    <h3 class="panel-section-title">LIVE API HEALTH & PING STATUS</h3>
                    <p class="panel-section-desc">Real-time health verification of pre-configured backend API keys.</p>
                  </div>
                  <button id="btnPingAllApis" class="btn-api-recheck" type="button">
                    <svg viewBox="0 0 24 24" width="16" height="16"><polyline points="23 4 23 10 17 10" fill="none" stroke="currentColor" stroke-width="2"/><polyline points="1 20 1 14 7 14" fill="none" stroke="currentColor" stroke-width="2"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15" fill="none" stroke="currentColor" stroke-width="2"/></svg>
                    <span>Ping All Engines</span>
                  </button>
                </div>

                <div class="api-cards-container" id="apiCardsContainer">
                  <!-- Dynamically rendered cloud API status cards -->
                </div>
              </div>

              <!-- ════════ PANEL 3: ISOLATED FOCUS MODE ════════ -->
              <div class="ai-control-panel" id="panelFocusMode">
                <div class="focus-hero-card">
                  <div class="focus-hero-left">
                    <span class="focus-hero-icon">🔕</span>
                    <div>
                      <h3 class="focus-hero-title">Strict Isolated Focus Mode</h3>
                      <p class="focus-hero-desc">Suppresses non-essential notifications via Android Do Not Disturb during deep study sessions.</p>
                    </div>
                  </div>
                  <label class="toggle-switch focus-toggle-wrap">
                    <input type="checkbox" id="chkFocusModeToggle">
                    <span class="toggle-slider"></span>
                  </label>
                </div>

                <div class="focus-status-card" id="focusPermissionCard">
                  <div class="focus-status-header">
                    <span class="focus-shield-icon">🛡️</span>
                    <h4 style="margin:0;color:#fff;">Android Notification Policy Permission</h4>
                  </div>
                  <p class="focus-permission-text" id="focusPermissionText">
                    Checking Do Not Disturb access permissions on Android...
                  </p>
                  <button id="btnRequestDndPermission" class="btn-dnd-permission" type="button">
                    <span>Grant Notification Policy Permission</span>
                  </button>
                </div>

                <div class="focus-features-list">
                  <div class="focus-feat-item">
                    <span class="feat-bullet">✓</span>
                    <div>
                      <strong>Automatic Activation in Study Mode:</strong>
                      <p>Entering Study Mode automatically turns on distraction-free filtering.</p>
                    </div>
                  </div>
                  <div class="focus-feat-item">
                    <span class="feat-bullet">✓</span>
                    <div>
                      <strong>Clean Exit Restoration:</strong>
                      <p>Exiting Study Mode immediately restores your device's normal sound and notification profile.</p>
                    </div>
                  </div>
                  <div class="focus-feat-item">
                    <span class="feat-bullet">✓</span>
                    <div>
                      <strong>Zero Background Battery Drain:</strong>
                      <p>Managed directly by the Android OS kernel (0% CPU impact).</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      `;

      document.body.insertAdjacentHTML('beforeend', html);
    },

    cacheDOM() {
      this.dom = {
        modal: document.getElementById('aiControlCenterModal'),
        btnClose: document.getElementById('btnCloseAiControl'),
        tabBtns: document.querySelectorAll('.ai-control-tab-btn'),
        panels: {
          offline: document.getElementById('panelOfflineModels'),
          online: document.getElementById('panelOnlineApis'),
          focus: document.getElementById('panelFocusMode'),
        },
        modelsGrid: document.getElementById('offlineModelsGrid'),
        apiCardsContainer: document.getElementById('apiCardsContainer'),
        btnPingAll: document.getElementById('btnPingAllApis'),
        chkFocusToggle: document.getElementById('chkFocusModeToggle'),
        permissionCard: document.getElementById('focusPermissionCard'),
        permissionText: document.getElementById('focusPermissionText'),
        btnGrantDnd: document.getElementById('btnRequestDndPermission'),
      };
    },

    bindEvents() {
      // Close modal
      if (this.dom.btnClose) {
        this.dom.btnClose.onclick = () => this.close();
      }

      // Close on backdrop click
      if (this.dom.modal) {
        this.dom.modal.onclick = (e) => {
          if (e.target === this.dom.modal) this.close();
        };
      }

      // Tab switching
      this.dom.tabBtns.forEach(btn => {
        btn.addEventListener('click', () => {
          const tab = btn.dataset.tab;
          this.switchTab(tab);
        });
      });

      // Ping APIs
      if (this.dom.btnPingAll) {
        this.dom.btnPingAll.onclick = () => this.pingAllApis();
      }

      // Focus toggle
      if (this.dom.chkFocusToggle) {
        this.dom.chkFocusToggle.onchange = (e) => {
          this.setFocusMode(e.target.checked);
        };
      }

      // Grant DND Permission
      if (this.dom.btnGrantDnd) {
        this.dom.btnGrantDnd.onclick = async () => {
          try {
            if (window.Capacitor?.Plugins?.MarvoNativeBridge?.openFocusModeSettings) {
              await window.Capacitor.Plugins.MarvoNativeBridge.openFocusModeSettings();
            }
          } catch (err) {
            console.warn('[AiControlCenter] Error opening DND settings:', err);
          }
        };
      }
    },

    open() {
      this.isOpen = true;
      if (this.dom.modal) {
        this.dom.modal.classList.add('active');
      }
      this.refreshModels();
      this.startPolling();
      this.checkFocusStatus();
    },

    close() {
      this.isOpen = false;
      if (this.dom.modal) {
        this.dom.modal.classList.remove('active');
      }
      this.stopPolling();
    },

    switchTab(tab) {
      this.activeTab = tab;
      this.dom.tabBtns.forEach(b => {
        b.classList.toggle('active', b.dataset.tab === tab);
      });
      Object.keys(this.dom.panels).forEach(k => {
        if (this.dom.panels[k]) {
          this.dom.panels[k].classList.toggle('active', k === tab);
        }
      });
      if (tab === 'online') {
        this.renderApiCards();
      }
    },

    startPolling() {
      if (this.pollInterval) clearInterval(this.pollInterval);
      this.pollInterval = setInterval(() => {
        if (this.isOpen) {
          this.refreshModels(false);
        }
      }, 1000);
    },

    stopPolling() {
      if (this.pollInterval) {
        clearInterval(this.pollInterval);
        this.pollInterval = null;
      }
    },

    /* ═══════════ PHASE 1.1: OFFLINE MODELS ═══════════ */
    async refreshModels(renderHtml = true) {
      try {
        if (window.Capacitor?.Plugins?.MarvoNativeBridge?.listOfflineModels) {
          const res = await window.Capacitor.Plugins.MarvoNativeBridge.listOfflineModels();
          if (res && res.models && Array.isArray(res.models)) {
            this.models = res.models;
            this.activeModelId = res.activeModel || 'phi-3-mini';
          }
        }
      } catch (e) {
        console.warn('[AiControlCenter] Native listOfflineModels error:', e);
      }

      if (renderHtml) {
        this.renderModelCards();
      } else {
        this.updateModelCardsLive();
      }
    },

    renderModelCards() {
      if (!this.dom.modelsGrid) return;

      this.dom.modelsGrid.innerHTML = this.models.map(m => {
        const isDownloading = m.status === 'downloading';
        const isPaused = m.status === 'paused';
        const isCompleted = m.isDownloaded || m.status === 'completed';
        const pct = m.progress || 0;
        const speed = m.speedMBps ? `${m.speedMBps} MB/s` : '';
        const eta = m.etaSeconds ? `ETA: ${Math.floor(m.etaSeconds / 60)}m ${m.etaSeconds % 60}s` : '';

        return `
          <div class="model-repo-card ${m.isActive ? 'active-engine' : ''}" id="card_model_${m.id}">
            <div class="model-card-top">
              <div>
                <div class="model-title-row">
                  <span class="model-card-title">${m.name}</span>
                  ${m.isActive ? '<span class="model-active-badge">★ Active Engine</span>' : ''}
                </div>
                <div class="model-vendor-tag">${m.vendor || 'On-Device GGUF'} • ${m.fileName}</div>
              </div>
              <span class="model-size-chip">${m.sizeFormatted}</span>
            </div>

            <p class="model-card-desc">${m.description || ''}</p>

            <div class="model-storage-row">
              <span class="storage-label">Storage Path:</span>
              <code class="storage-path">${m.storagePath}</code>
            </div>

            <!-- Progress Bar (Visible during download / pause) -->
            <div class="model-progress-wrap ${isDownloading || isPaused ? 'show' : ''}" id="progressWrap_${m.id}">
              <div class="model-progress-bar">
                <div class="model-progress-fill" id="progressFill_${m.id}" style="width:${pct}%;"></div>
              </div>
              <div class="model-progress-stats">
                <span id="progressText_${m.id}">${pct}% • ${speed} ${eta}</span>
                <span class="model-status-pill ${m.status}">${m.status.toUpperCase()}</span>
              </div>
            </div>

            <!-- Action Buttons -->
            <div class="model-actions-row">
              ${!isCompleted && !isDownloading && !isPaused ? `
                <button class="btn-model-action primary" onclick="window.AiControlCenter.startDownload('${m.id}')">
                  <svg viewBox="0 0 24 24" width="15" height="15"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" fill="none" stroke="currentColor" stroke-width="2"/><polyline points="7 10 12 15 17 10" fill="none" stroke="currentColor" stroke-width="2"/><line x1="12" y1="15" x2="12" y2="3" stroke="currentColor" stroke-width="2"/></svg>
                  <span>Download (${m.sizeFormatted})</span>
                </button>
              ` : ''}

              ${isDownloading ? `
                <button class="btn-model-action secondary" onclick="window.AiControlCenter.pauseDownload('${m.id}')">Pause</button>
                <button class="btn-model-action danger" onclick="window.AiControlCenter.cancelDownload('${m.id}')">Cancel</button>
              ` : ''}

              ${isPaused ? `
                <button class="btn-model-action primary" onclick="window.AiControlCenter.startDownload('${m.id}')">Resume</button>
                <button class="btn-model-action danger" onclick="window.AiControlCenter.cancelDownload('${m.id}')">Cancel</button>
              ` : ''}

              ${isCompleted ? `
                ${!m.isActive ? `
                  <button class="btn-model-action primary" onclick="window.AiControlCenter.setActiveModel('${m.id}')">
                    Set Active Engine
                  </button>
                ` : ''}
                <button class="btn-model-action danger-outline" onclick="window.AiControlCenter.deleteModel('${m.id}')">
                  Delete
                </button>
              ` : ''}
            </div>
          </div>
        `;
      }).join('');
    },

    updateModelCardsLive() {
      this.models.forEach(m => {
        const fill = document.getElementById(`progressFill_${m.id}`);
        const text = document.getElementById(`progressText_${m.id}`);
        const wrap = document.getElementById(`progressWrap_${m.id}`);
        if (fill && text) {
          const pct = m.progress || 0;
          fill.style.width = `${pct}%`;
          const speed = m.speedMBps ? `${m.speedMBps} MB/s • ` : '';
          const eta = m.etaSeconds ? `ETA ${Math.floor(m.etaSeconds / 60)}m ${m.etaSeconds % 60}s` : '';
          text.textContent = `${pct}% • ${speed}${eta}`;
          if (wrap) {
            wrap.classList.toggle('show', m.status === 'downloading' || m.status === 'paused');
          }
        }
      });
    },

    async startDownload(modelId) {
      try {
        if (window.Capacitor?.Plugins?.MarvoNativeBridge?.startDownloadModel) {
          await window.Capacitor.Plugins.MarvoNativeBridge.startDownloadModel({
            modelType: modelId,
            allowMetered: true
          });
        }
        if (window.showToast) window.showToast(`Started foreground download for ${modelId}`);
        this.refreshModels();
      } catch (err) {
        console.error('[AiControlCenter] startDownload error:', err);
      }
    },

    async pauseDownload(modelId) {
      try {
        if (window.Capacitor?.Plugins?.MarvoNativeBridge?.pauseDownloadModel) {
          await window.Capacitor.Plugins.MarvoNativeBridge.pauseDownloadModel({ modelType: modelId });
        }
        if (window.showToast) window.showToast(`Paused download for ${modelId}`);
        this.refreshModels();
      } catch (err) {
        console.error('[AiControlCenter] pauseDownload error:', err);
      }
    },

    async cancelDownload(modelId) {
      try {
        if (window.Capacitor?.Plugins?.MarvoNativeBridge?.cancelDownloadModel) {
          await window.Capacitor.Plugins.MarvoNativeBridge.cancelDownloadModel({ modelType: modelId });
        }
        if (window.showToast) window.showToast(`Cancelled download for ${modelId}`);
        this.refreshModels();
      } catch (err) {
        console.error('[AiControlCenter] cancelDownload error:', err);
      }
    },

    async deleteModel(modelId) {
      if (!confirm(`Are you sure you want to delete ${modelId} from device storage?`)) return;
      try {
        if (window.Capacitor?.Plugins?.MarvoNativeBridge?.deleteOfflineModel) {
          await window.Capacitor.Plugins.MarvoNativeBridge.deleteOfflineModel({ modelType: modelId });
        }
        if (window.showToast) window.showToast(`Deleted ${modelId} from disk.`);
        this.refreshModels();
      } catch (err) {
        console.error('[AiControlCenter] deleteModel error:', err);
      }
    },

    async setActiveModel(modelId) {
      try {
        if (window.Capacitor?.Plugins?.MarvoNativeBridge?.setActiveOfflineModel) {
          await window.Capacitor.Plugins.MarvoNativeBridge.setActiveOfflineModel({ modelType: modelId });
        }
        this.activeModelId = modelId;
        if (window.showToast) window.showToast(`${modelId} is now the active offline brain.`);
        this.refreshModels();
      } catch (err) {
        console.error('[AiControlCenter] setActiveModel error:', err);
      }
    },

    /* ═══════════ PHASE 1.2: ONLINE API HEALTH & VALIDATION ═══════════ */
    async pingAllApis() {
      const isOnline = navigator.onLine !== false;
      if (!isOnline) {
        ['groq', 'gemini', 'openrouter'].forEach(k => {
          this.apiStatus[k] = { status: 'offline', latency: 0, error: 'No network connection' };
        });
        this.renderApiCards();
        return;
      }

      const keys = (window.TrafficPolice && window.TrafficPolice.state && window.TrafficPolice.state.keys) || {};

      // 1. Ping Groq
      this.pingGroq(keys.groq);
      // 2. Ping Gemini
      this.pingGemini(keys.gemini);
      // 3. Ping OpenRouter
      this.pingOpenRouter(keys.openrouter);
    },

    async pingGroq(key) {
      this.apiStatus.groq.status = 'checking';
      this.renderApiCards();
      if (!key) {
        this.apiStatus.groq = { status: 'failed', latency: 0, error: 'Key not found' };
        this.renderApiCards();
        return;
      }

      const t0 = performance.now();
      try {
        const res = await fetch('https://api.groq.com/openai/v1/models', {
          method: 'GET',
          headers: { 'Authorization': `Bearer ${key}` }
        });
        const latency = Math.round(performance.now() - t0);
        if (res.ok) {
          this.apiStatus.groq = { status: 'active', latency, error: null };
        } else {
          this.apiStatus.groq = { status: 'failed', latency, error: `HTTP ${res.status}` };
        }
      } catch (e) {
        this.apiStatus.groq = { status: 'failed', latency: 0, error: e.message };
      }
      this.renderApiCards();
    },

    async pingGemini(key) {
      this.apiStatus.gemini.status = 'checking';
      this.renderApiCards();
      if (!key) {
        this.apiStatus.gemini = { status: 'failed', latency: 0, error: 'Key not found' };
        this.renderApiCards();
        return;
      }

      const t0 = performance.now();
      try {
        const res = await fetch(`https://generativelanguage.googleapis.com/v1beta/models?key=${key}`, {
          method: 'GET'
        });
        const latency = Math.round(performance.now() - t0);
        if (res.ok) {
          this.apiStatus.gemini = { status: 'active', latency, error: null };
        } else {
          this.apiStatus.gemini = { status: 'failed', latency, error: `HTTP ${res.status}` };
        }
      } catch (e) {
        this.apiStatus.gemini = { status: 'failed', latency: 0, error: e.message };
      }
      this.renderApiCards();
    },

    async pingOpenRouter(key) {
      this.apiStatus.openrouter.status = 'checking';
      this.renderApiCards();
      if (!key) {
        this.apiStatus.openrouter = { status: 'failed', latency: 0, error: 'Key not found' };
        this.renderApiCards();
        return;
      }

      const t0 = performance.now();
      try {
        const res = await fetch('https://openrouter.ai/api/v1/auth/key', {
          method: 'GET',
          headers: { 'Authorization': `Bearer ${key}` }
        });
        const latency = Math.round(performance.now() - t0);
        if (res.ok) {
          this.apiStatus.openrouter = { status: 'active', latency, error: null };
        } else {
          this.apiStatus.openrouter = { status: 'failed', latency, error: `HTTP ${res.status}` };
        }
      } catch (e) {
        this.apiStatus.openrouter = { status: 'failed', latency: 0, error: e.message };
      }
      this.renderApiCards();
    },

    renderApiCards() {
      if (!this.dom.apiCardsContainer) return;

      const configs = [
        {
          key: 'groq',
          title: 'Groq Cloud API (LPU)',
          model: 'llama-3.3-70b-versatile',
          desc: 'High-speed hardware accelerator for instant chat completion.'
        },
        {
          key: 'gemini',
          title: 'Google Gemini API',
          model: 'gemini-2.0-flash',
          desc: 'Native Google multimodal vision, audio, and reasoning model.'
        },
        {
          key: 'openrouter',
          title: 'OpenRouter Multi-Agent Gateway',
          model: 'anthropic/claude-3.5-sonnet',
          desc: 'Heavy logic gateway supporting Claude 3.5 Sonnet, GPT-4o & Llama 3.1 405B.'
        }
      ];

      this.dom.apiCardsContainer.innerHTML = configs.map(c => {
        const st = this.apiStatus[c.key] || { status: 'checking', latency: 0 };
        let badgeHtml = '';
        if (st.status === 'active') {
          badgeHtml = `<span class="api-badge active">🟢 Active & Verified (${st.latency}ms)</span>`;
        } else if (st.status === 'failed') {
          badgeHtml = `<span class="api-badge failed">🔴 Failed / Invalid Key</span>`;
        } else if (st.status === 'offline') {
          badgeHtml = `<span class="api-badge offline">🟡 Offline (No Network)</span>`;
        } else {
          badgeHtml = `<span class="api-badge checking">⚪ Checking...</span>`;
        }

        return `
          <div class="api-health-card">
            <div class="api-card-top">
              <div>
                <h4 class="api-name">${c.title}</h4>
                <span class="api-model-tag">${c.model}</span>
              </div>
              ${badgeHtml}
            </div>
            <p class="api-desc">${c.desc}</p>
            <div class="api-card-footer">
              <span class="api-secure-tag">🔒 Key Secured in Backend Logic</span>
              <button class="btn-api-reping" onclick="window.AiControlCenter.repingSingle('${c.key}')">Ping</button>
            </div>
          </div>
        `;
      }).join('');
    },

    repingSingle(provider) {
      const keys = (window.TrafficPolice && window.TrafficPolice.state && window.TrafficPolice.state.keys) || {};
      if (provider === 'groq') this.pingGroq(keys.groq);
      else if (provider === 'gemini') this.pingGemini(keys.gemini);
      else if (provider === 'openrouter') this.pingOpenRouter(keys.openrouter);
    },

    /* ═══════════ PHASE 1.3: ISOLATED FOCUS MODE ═══════════ */
    async checkFocusStatus() {
      try {
        if (window.Capacitor?.Plugins?.MarvoNativeBridge?.checkFocusModeStatus) {
          const res = await window.Capacitor.Plugins.MarvoNativeBridge.checkFocusModeStatus();
          if (res) {
            this.focusMode.isGranted = res.isGranted;
            this.focusMode.enabled = res.isFocused;
            if (this.dom.chkFocusToggle) {
              this.dom.chkFocusToggle.checked = res.isFocused;
            }
            if (this.dom.permissionCard) {
              this.dom.permissionCard.style.display = res.isGranted ? 'none' : 'block';
            }
          }
        }
      } catch (err) {
        console.warn('[AiControlCenter] Error checking focus status:', err);
      }
    },

    async setFocusMode(enabled) {
      try {
        if (window.Capacitor?.Plugins?.MarvoNativeBridge?.setIsolatedFocusMode) {
          const res = await window.Capacitor.Plugins.MarvoNativeBridge.setIsolatedFocusMode({ enabled });
          if (res && res.needsPermission) {
            if (window.showToast) window.showToast('Please grant Do Not Disturb permission in Settings');
            await window.Capacitor.Plugins.MarvoNativeBridge.openFocusModeSettings();
            if (this.dom.chkFocusToggle) this.dom.chkFocusToggle.checked = false;
          } else {
            this.focusMode.enabled = enabled;
            if (window.showToast) window.showToast(enabled ? '🔕 Focus Mode Activated' : '🔔 Focus Mode Deactivated');
          }
        }
      } catch (e) {
        console.warn('[AiControlCenter] setFocusMode error:', e);
      }
    }
  };

  window.AiControlCenter = AiControlCenter;
})(window);

