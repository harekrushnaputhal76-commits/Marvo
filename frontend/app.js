'use strict';

/* ================================================================
   MARVO — app.js
   Flagship Native Android UI/UX (Gemini-Inspired)
   - 3D Animated Realistic Eyes with 10+ Advanced Expressions
   - Bottom-Docked Voice UI with Real-Time Audio Visualizer & Waveform
   - Immersive Native Edge-to-Edge Fullscreen (Capacitor StatusBar)
   - Gemini-Style Capability Toggles ("Fast", "Thinking", "Pro")
   - Advanced Side Drawer & 3-Dot Kebab Action Menu
   - Context-Awareness (Local Date, Time & Day Silent Injection)
   - Native Device Local Storage Persistence (@capacitor/preferences)
   - Production Live Backend (https://marvo-kshm.onrender.com)
   ================================================================ */

/* ═══════ CONFIGURATION ═══════ */
const API_BASE = 'https://marvo-kshm.onrender.com';
const API_CHAT = `${API_BASE}/api/chat`;
const API_SPEAK = `${API_BASE}/api/speak`;
const API_PREVIEW_VOICE = `${API_BASE}/api/preview_voice`;
const API_SESSIONS = `${API_BASE}/api/sessions`;

const SESSION_STORAGE_KEY = 'marvo.currentSessionId';
const THEME_STORAGE_KEY   = 'marvo.theme';
const VOICE_STORAGE_KEY   = 'marvo.voice';
const SESSIONS_STORAGE_KEY = 'marvo.sessions';
const CHAT_PREFIX          = 'marvo.chat.';
const AGENT_STORAGE_KEY    = 'marvo.activeAgent';
const MODE_STORAGE_KEY     = 'marvo.selectedMode';
const NOTEBOOK_STORAGE_KEY = 'marvo.notebook';
const QUICK_NOTE_STORAGE_KEY = 'marvo.quickNote';

/* ═══════ DOM CACHE ═══════ */
const $ = (sel) => document.querySelector(sel);
const DOM = {
  body:               document.body,
  sidebar:            $('#sidebar'),
  sidebarOverlay:     $('#sidebarOverlay'),
  btnHamburger:       $('#btnHamburger'),
  btnCloseSidebar:    $('#btnCloseSidebar'),
  btnNewChat:         $('#btnNewChat'),
  btnNewProject:      $('#btnNewProject'),
  modeSelector:       $('#modeSelector'),
  historyList:        $('#chatHistoryList'),
  historyContextMenu: $('#historyContextMenu'),
  btnRenameChat:      $('#btnRenameChat'),
  btnDeleteChat:      $('#btnDeleteChat'),

  // Creative Agents & Collapsible Accordion
  sidebarAgentsGrid:      $('#sidebarAgentsGrid'),
  btnToggleAgents:        $('#btnToggleAgents'),
  agentsAccordionContent: $('#agentsAccordionContent'),
  agentsActiveIndicator:  $('#agentsActiveIndicator'),

  // Top Bar & Menus
  tierSwitcher:       $('#tierSwitcher'),
  btnTierFast:        $('#btnTierFast'),
  btnTierThinking:    $('#btnTierThinking'),
  btnTierPro:         $('#btnTierPro'),
  btnAppMenu:         $('#btnAppMenu'),
  appDropdown:        $('#appDropdown'),
  btnAddToNotebook:   $('#btnAddToNotebook'),
  btnTopRenameChat:   $('#btnTopRenameChat'),
  btnTopHelp:         $('#btnTopHelp'),
  btnTopShareChat:    $('#btnTopShareChat'),
  btnTopDeleteChat:   $('#btnTopDeleteChat'),
  btnThemeDark:       $('#btnThemeDark'),
  btnThemeLight:      $('#btnThemeLight'),
  btnThemeMenu:       $('#btnThemeMenu'),
  btnSettings:        $('#btnSettings'),
  settingsModal:      $('#settingsModal'),
  btnCloseSettings:   $('#btnCloseSettings'),
  settingsTabBar:     $('#settingsTabBar'),
  btnOpenStorageFolder: $('#btnOpenStorageFolder'),
  downloadsHistoryList: $('#downloadsHistoryList'),
  navDownloads:       $('#navDownloads'),
  navAiControlCenter: $('#navAiControlCenter'),
  btnOpenAiControlCenterBadge: $('#btnOpenAiControlCenterBadge'),
  btnAppAiControlCenter: $('#btnAppAiControlCenter'),
  btnLaunchAiControlCenter: $('#btnLaunchAiControlCenter'),
  eyeColorSection:    $('#eyeColorSection'),
  characterThemesGrid: $('#characterThemesGrid'),
  topbarTitle:        $('#topbarTitle'),

  // Phase 5 Modals
  helpModal:            $('#helpModal'),
  btnCloseHelpModal:    $('#btnCloseHelpModal'),
  notebookModal:        $('#notebookModal'),
  btnCloseNotebookModal:$('#btnCloseNotebookModal'),
  btnSaveChatToNotebook:$('#btnSaveChatToNotebook'),
  notebookNotesList:    $('#notebookNotesList'),
  notebookQuickNote:    $('#notebookQuickNote'),
  btnClearQuickNote:    $('#btnClearQuickNote'),
  notebookNoteStatus:   $('#notebookNoteStatus'),
  shareModal:           $('#shareModal'),
  btnCloseShareModal:   $('#btnCloseShareModal'),
  btnSharePDF:          $('#btnSharePDF'),
  btnShareText:         $('#btnShareText'),
  shareSummaryText:     $('#shareSummaryText'),
  sparkModal:           $('#sparkModal'),
  btnCloseSparkModal:   $('#btnCloseSparkModal'),
  btnAcknowledgeSpark:  $('#btnAcknowledgeSpark'),
  navSpark:             $('#navSpark'),

  // Projects System & Creative Studio
  activeProjectBadge:   $('#activeProjectBadge'),
  activeProjectName:    $('#activeProjectName'),
  btnExitProject:       $('#btnExitProject'),
  projectModal:         $('#projectModal'),
  btnCloseProjectModal: $('#btnCloseProjectModal'),
  projectNameInput:     $('#projectNameInput'),
  projectPromptInput:   $('#projectPromptInput'),
  btnSaveProject:       $('#btnSaveProject'),
  btnCancelProject:     $('#btnCancelProject'),
  btnClearProject:      $('#btnClearProject'),
  navImageGen:          $('#navImageGen'),

  // Avatar & 3D Eyes & Reactive Characters
  avatarZone:         $('#avatarZone'),
  face:               $('#face'),
  stateLabel:         $('#stateLabel'),
  characterAvatar:    $('#characterAvatar'),
  characterImg:       $('#characterImg'),
  characterNameBadge: $('#characterNameBadge'),
  characterStateBadge:$('#characterStateBadge'),
  characterAura:      $('#characterAura'),

  // Chat Area & Multimodal Input
  chatArea:           $('#chatArea'),
  chatMessages:       $('#chatMessages'),
  attachmentPreviewShelf: $('#attachmentPreviewShelf'),
  fileUploadInput:    $('#fileUploadInput'),
  btnAttach:          $('#btnAttach'),
  attachMenu:         $('#attachMenu'),
  btnAttachImageGen:  $('#btnAttachImageGen'),
  btnAttachPhoto:     $('#btnAttachPhoto'),
  btnAttachVideo:     $('#btnAttachVideo'),
  btnAttachPDF:       $('#btnAttachPDF'),
  btnAttachFiles:     $('#btnAttachFiles'),
  msgInput:           $('#msgInput'),
  btnSend:            $('#btnSend'),
  btnMic:             $('#btnMic'),
  btnScreenShare:     $('#btnScreenShare'),

  // Bottom-Docked Voice UI
  voiceOverlay:         $('#voiceOverlay'),
  voiceStatusText:      $('#voiceStatusText'),
  voiceWaveCanvas:      $('#voiceWaveCanvas'),
  voiceBars:            $('#voiceBars'),
  voiceTranscriptBox:   $('#voiceTranscriptBox'),
  voiceTranscriptText:  $('#voiceTranscriptText'),
  btnVoiceClose:        $('#btnVoiceClose'),
  btnVoiceCancel:       $('#btnVoiceCancel'),
  btnVoicePauseResume:  $('#btnVoicePauseResume'),
  iconVoicePause:       $('#iconVoicePause'),
  iconVoiceResume:      $('#iconVoiceResume'),
  labelVoicePauseResume:$('#labelVoicePauseResume'),
  btnVoiceSend:         $('#btnVoiceSend'),
  siriOrbDock:          $('#siri-orb-dock'),
  siriOrbWrap:          $('#siri-orb-wrap'),
  siriOrb:              $('#siri-orb'),
  siriOrbLabel:         $('#siri-orb-label'),
  // Apple 2026 Dynamic Island & Voice UI
  cameraIsland:         $('#marvo-camera-island'),
  prismLightArc:        $('.prism-light-arc'),
  islandResponseContent:$('#island-response-content'),
  islandResponseText:   $('#islandResponseText'),
  islandStatusPill:     $('#islandStatusPill'),
  islandWaveCanvas:     $('#islandWaveCanvas'),
  islandPlasma:         $('#islandPlasmaWrapper'),
  btnIslandClose:       $('#btnIslandClose'),
  btnIslandCancel:      $('#btnIslandCancel'),
  btnIslandPauseResume: $('#btnIslandPauseResume'),
  iconIslandPause:      $('#iconIslandPause'),
  iconIslandResume:     $('#iconIslandResume'),
  labelIslandPauseResume:$('#labelIslandPauseResume'),
  btnIslandSend:        $('#btnIslandSend'),
  appRoot:              $('#app-root'),

  // Unified aliases ensuring seamless voice assistant compatibility
  voiceOverlay:         $('#marvo-camera-island'),
  voiceStatusText:      $('#islandStatusPill'),
  voiceWaveCanvas:      $('#islandWaveCanvas'),
  voiceTranscriptBox:   $('#island-response-content'),
  voiceTranscriptText:  $('#islandResponseText'),
  btnVoiceClose:        $('#btnIslandClose'),
  btnVoiceCancel:       $('#btnIslandCancel'),
  btnVoicePauseResume:  $('#btnIslandPauseResume'),
  iconVoicePause:       $('#iconIslandPause'),
  iconVoiceResume:      $('#iconIslandResume'),
  labelVoicePauseResume:$('#labelIslandPauseResume'),
  btnVoiceSend:         $('#btnIslandSend'),

  // Toast
  appToast:           $('#appToast'),
};

/* ═══════ NATIVE STORAGE (@capacitor/preferences + localStorage fallback) ═══════ */
const NativeStorage = {
  async get(key) {
    try {
      if (window.Capacitor?.Plugins?.Preferences) {
        const res = await window.Capacitor.Plugins.Preferences.get({ key });
        if (res && res.value !== null && res.value !== undefined) {
          return res.value;
        }
      }
    } catch (e) {
      console.warn('[NativeStorage] Capacitor get error:', e);
    }
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  },

  async set(key, value) {
    try {
      if (window.Capacitor?.Plugins?.Preferences) {
        await window.Capacitor.Plugins.Preferences.set({ key, value: String(value) });
        return;
      }
    } catch (e) {
      console.warn('[NativeStorage] Capacitor set error:', e);
    }
    try {
      localStorage.setItem(key, String(value));
    } catch {}
  },

  async remove(key) {
    try {
      if (window.Capacitor?.Plugins?.Preferences) {
        await window.Capacitor.Plugins.Preferences.remove({ key });
        return;
      }
    } catch (e) {
      console.warn('[NativeStorage] Capacitor remove error:', e);
    }
    try {
      localStorage.removeItem(key);
    } catch {}
  },

  async getJSON(key, defaultVal = null) {
    const raw = await this.get(key);
    if (!raw) return defaultVal;
    try {
      return JSON.parse(raw);
    } catch {
      return defaultVal;
    }
  },

  async setJSON(key, value) {
    await this.set(key, JSON.stringify(value));
  }
};

/* ═══════════════════════════════════════════════════════════════════
   STEP 30: LONG-TERM PERSONAL MEMORY SERVICE (marvo_memory.json)
   ═══════════════════════════════════════════════════════════════════ */
const MemoryManager = {
  STORAGE_KEY: 'marvo_memory.json',
  TOGGLE_KEY: 'marvo.memory.enabled',

  async isEnabled() {
    const val = await NativeStorage.get(this.TOGGLE_KEY);
    return val === null || val === undefined || val === 'true' || val === true;
  },

  async setEnabled(enabled) {
    await NativeStorage.set(this.TOGGLE_KEY, String(enabled));
    if (window.Capacitor?.Plugins?.MarvoNativeBridge?.setMemoryEnabled) {
      try {
        await window.Capacitor.Plugins.MarvoNativeBridge.setMemoryEnabled({ enabled });
      } catch (e) {
        console.warn('[MemoryManager] Native bridge set error:', e);
      }
    }
  },

  async getMemoryStore() {
    if (window.Capacitor?.Plugins?.MarvoNativeBridge?.getMemoryFacts) {
      try {
        const res = await window.Capacitor.Plugins.MarvoNativeBridge.getMemoryFacts();
        if (res && Array.isArray(res.facts)) {
          return {
            version: 1,
            updated_at: new Date().toISOString(),
            facts: res.facts
          };
        }
      } catch (e) {
        console.warn('[MemoryManager] Native bridge get error:', e);
      }
    }
    const store = await NativeStorage.getJSON(this.STORAGE_KEY, null);
    if (store && Array.isArray(store.facts)) return store;
    return {
      version: 1,
      updated_at: new Date().toISOString(),
      facts: []
    };
  },

  async getAllFacts() {
    const store = await this.getMemoryStore();
    return store.facts || [];
  },

  async saveFact(factText, category = 'personal') {
    if (!factText || !factText.trim()) return false;
    const cleanFact = factText.trim();
    const enabled = await this.isEnabled();
    if (!enabled) return false;

    if (window.Capacitor?.Plugins?.MarvoNativeBridge?.saveMemoryFact) {
      try {
        await window.Capacitor.Plugins.MarvoNativeBridge.saveMemoryFact({ fact: cleanFact, category });
      } catch (e) {
        console.warn('[MemoryManager] Native save error:', e);
      }
    }

    const store = await this.getMemoryStore();
    const lower = cleanFact.toLowerCase();
    const exists = store.facts.some(f => (f.fact || '').toLowerCase() === lower);
    if (exists) return true;

    store.facts.push({
      id: `mem_${Date.now()}_${store.facts.length + 1}`,
      fact: cleanFact,
      category,
      timestamp: Date.now()
    });
    store.updated_at = new Date().toISOString();
    await NativeStorage.setJSON(this.STORAGE_KEY, store);
    console.info('[MemoryManager] Permanent fact saved:', cleanFact);
    return true;
  },

  async deleteFact(id) {
    if (window.Capacitor?.Plugins?.MarvoNativeBridge?.deleteMemoryFact) {
      try {
        await window.Capacitor.Plugins.MarvoNativeBridge.deleteMemoryFact({ id });
      } catch (e) {}
    }
    const store = await this.getMemoryStore();
    store.facts = store.facts.filter(f => f.id !== id);
    store.updated_at = new Date().toISOString();
    await NativeStorage.setJSON(this.STORAGE_KEY, store);
    return true;
  },

  async clearAll() {
    if (window.Capacitor?.Plugins?.MarvoNativeBridge?.clearAllMemory) {
      try {
        await window.Capacitor.Plugins.MarvoNativeBridge.clearAllMemory();
      } catch (e) {}
    }
    await NativeStorage.setJSON(this.STORAGE_KEY, { version: 1, updated_at: new Date().toISOString(), facts: [] });
  },

  async extractAndSaveFacts(userText) {
    if (!userText || !(await this.isEnabled())) return;
    const clean = userText.trim();

    const patterns = [
      /^(?:please\s+)?(?:always\s+)?remember\s+(?:that\s+|this\s*:?\s*)?(.+)$/i,
      /^(?:note\s+that|keep\s+in\s+mind\s+that)\s+(.+)$/i,
      /^(?:yaad\s+rakhna|dhyaan\s+rakhna)\s+(?:ki\s+)?(.+)$/i,
      /^(?:my\s+(?:favorite|favourite|best|dog|cat|car|job|city|friend|brother|sister)\s+[^is]+is\s+)(.+)$/i,
      /^(?:i\s+(?:live|reside|stay|work|study)\s+in\s+)(.+)$/i,
      /^(?:i\s+(?:love|like|prefer|hate|dislike|am\s+allergic\s+to)\s+)(.+)$/i,
      /^(?:mera\s+favourite|meri\s+favourite|mujhe)\s+(.+?)(?:\s+pasand\s+hai|\s+accha\s+lagta\s+hai)$/i
    ];

    for (const p of patterns) {
      const match = clean.match(p);
      if (match && match[1] && match[1].trim().length > 3) {
        await this.saveFact(`User stated: ${clean}`, 'personal');
        break;
      }
    }
  },

  async getPromptContext() {
    const enabled = await this.isEnabled();
    if (!enabled) return '';
    const facts = await this.getAllFacts();
    if (!facts.length) return '';

    const list = facts.map(f => `- ${f.fact}`).join('\n');
    return `[LONG_TERM_USER_MEMORY:\nThe following permanent facts were established by the user in previous conversations. Inherently remember and respect them without narrating source mechanisms:\n${list}\n]`;
  }
};



/* ═══════════════════════════════════════════════════════════════════
   STEP 30: AI CONTROL ROOM DASHBOARD REGISTRY & RENDERER
   ═══════════════════════════════════════════════════════════════════ */
const AI_CONTROL_REGISTRY = [
  {
    id: "subfolder_memory_context",
    name: "Memory & Context",
    icon: "🧠",
    badge: "Persistent",
    description: "Long-term persistence, user profile recollection, and adaptive context windows",
    toggles: [
      {
        key: "marvo.memory.enabled",
        label: "Long-Term Personal Memory",
        desc: "Automatically extract and retain permanent facts about the user in marvo_memory.json across all sessions",
        default: true,
        onChange: async (val) => {
          await MemoryManager.setEnabled(val);
        }
      },
      {
        key: "marvo.context.enabled",
        label: "Contextual Conversation History",
        desc: "Inject rolling 10-message dialog turns into inference requests for seamless conversational continuity",
        default: true
      },
      {
        key: "marvo.privacy.boundary",
        label: "Strict Privacy Boundary",
        desc: "Enforce zero unprompted narration of user profile information unless explicitly requested",
        default: true
      },
      {
        key: "marvo.super_student_mode",
        label: "Super Student Mode (CHSE Odisha 12th Science)",
        desc: "Forcefully instruct Marvo to act as an expert PCMB academic tutor for Class 12 Higher Secondary Science (CHSE Odisha)",
        default: false,
        onChange: (val) => {
          localStorage.setItem('marvo.studentMode', String(val));
          updateStudentModeUI(val);
        }
      }
    ],
    customActions: [
      {
        id: "btnManageMemoryDialog",
        label: "Manage Saved Facts",
        icon: "📋",
        action: async () => {
          await showMemoryManagementDialog();
        }
      }
    ]
  },
  {
    id: "subfolder_vision_camera",
    name: "Vision & Camera",
    icon: "👁️",
    badge: "Live View",
    description: "Real-time visual comprehension, camera viewfinders, and Gemini Live multimodal streaming",
    toggles: [
      {
        key: "marvo.vision.enabled",
        label: "Live Vision Mode",
        desc: "Enable real-time camera viewfinder dock and periodic frame capture for visual intelligence",
        default: true,
        onChange: (val) => {
          const btn = $('#btnLiveVision');
          if (btn) btn.style.display = val ? 'flex' : 'none';
        }
      },
      {
        key: "marvo.camera.front_default",
        label: "Default to Front Camera",
        desc: "Prefer front selfie camera for face and reaction awareness instead of rear camera",
        default: false
      },
      {
        key: "marvo.vision.continuous",
        label: "Continuous Frame Ingestion",
        desc: "Periodically capture low-latency frames (every 3s) while Live Vision viewfinder is active",
        default: true
      },
      {
        key: "marvo.ocr_math_scanner_enabled",
        label: "OCR & Math Scanner",
        desc: "Enable camera viewfinder mathematical formula detection, LaTeX formatting, and step-by-step problem solver",
        default: true,
        onChange: (val) => {
          const mathBox = $('#liveVisionMathBox');
          if (mathBox) mathBox.classList.toggle('hidden', !val);
          const mathBtn = $('#btnOcrMathSolve');
          if (mathBtn) mathBtn.style.display = val ? 'flex' : 'none';
        }
      },
      {
        key: "marvo.vision.gyro_parallax",
        label: "Gyroscope Eye Gaze Tracking",
        desc: "Tilt phone to make Marvo's eyes smoothly track device orientation",
        default: true,
        onChange: (val) => {
          if (window.mainTiltEngine) {
            if (val) {
              window.mainTiltEngine.start();
            } else {
              window.mainTiltEngine.stop();
              document.documentElement.style.setProperty('--tilt-x', '0px');
              document.documentElement.style.setProperty('--tilt-y', '0px');
            }
          }
          if (typeof GyroParallaxManager !== 'undefined') {
            if (val) GyroParallaxManager.start();
            else GyroParallaxManager.stop();
          }
        }
      }
    ]
  },
  {
    id: "subfolder_voice_output",
    name: "Voice & Output",
    icon: "🎙️",
    badge: "Neural TTS",
    description: "Speech synthesis, voice recognition sequencing, and audio response modes",
    toggles: [
      {
        key: "marvo.voice.tts_enabled",
        label: "Spoken Voice Responses (TTS)",
        desc: "Read AI replies aloud using Piper offline neural voice or Android Speech synthesis",
        default: true
      },
      {
        key: "marvo.voice.strict_seq",
        label: "Strict Audio Sequencing",
        desc: "Strictly enforce Idle -> Listening -> Processing -> Speaking sequence to prevent audio overlap",
        default: true
      },
      {
        key: "marvo.voice.core_only",
        label: "One-Breath TTS Mode",
        desc: "Only narrate the essential core answer (<coreResponse>) and skip lengthy markdown tables or lists in speech",
        default: true
      },
      {
        key: "marvo.voice.smart_interrupt",
        label: "Smart Speech Interrupt",
        desc: "Immediately halt active TTS playback when user starts speaking a new command",
        default: true
      },
      {
        key: "marvo.voice.whisper_mode",
        label: "Whisper Mode",
        desc: "Detect quiet user speech and respond in a gentle, lowered volume hushed tone",
        default: true
      }
    ]
  },
  {
    id: "subfolder_experimental_features",
    name: "Experimental Features",
    icon: "⚡",
    badge: "Labs",
    description: "Upcoming neural features, local engines, and automated background schedulers",
    toggles: [
      {
        key: "marvo.smart_replies.enabled",
        label: "Smart Reply Suggestions",
        desc: "Display contextual follow-up query chips above the input bar after every AI response",
        default: true,
        onChange: (val) => {
          const container = $('#smartReplyContainer');
          if (container && !val) {
            container.classList.add('hidden');
          }
        }
      },
      {
        key: "marvo.math.katex_enabled",
        label: "Beautiful Math Rendering",
        desc: "Render textbook-quality LaTeX formulas ($$..$$ and $..$) using KaTeX engine",
        default: true
      },
      {
        key: "marvo.chess.enabled",
        label: "Neural Chess Engine (Preview)",
        desc: "Enable local board state evaluation and offline chess gameplay in Playground",
        default: false
      },
      {
        key: "marvo.offline.failover",
        label: "Offline Phi-3 Dynamic Failover",
        desc: "Automatically route prompts to on-device GGUF neural engine when network connectivity drops",
        default: true
      },
      {
        key: "marvo.routines.enabled",
        label: "Automated Daily Routines",
        desc: "Enable automated morning briefings and periodic device health checks",
        default: false
      }
    ]
  }
];

async function renderAiControlRoom() {
  const container = document.getElementById('aiControlRoomContainer');
  if (!container) return;

  container.innerHTML = '';

  for (let i = 0; i < AI_CONTROL_REGISTRY.length; i++) {
    const cat = AI_CONTROL_REGISTRY[i];
    const isFirst = i === 0;

    const card = document.createElement('div');
    card.className = 'control-category-card';

    // Header Button
    const headerBtn = document.createElement('button');
    headerBtn.type = 'button';
    headerBtn.className = `category-header-btn ${isFirst ? 'expanded' : ''}`;
    headerBtn.id = `btnToggle_${cat.id}`;
    headerBtn.innerHTML = `
      <div class="category-title-left">
        <span class="category-emoji">${cat.icon}</span>
        <span class="category-name">${cat.name}</span>
        <span class="category-badge">${cat.badge}</span>
      </div>
      <svg class="category-chevron" viewBox="0 0 24 24" width="18" height="18"><polyline points="6 9 12 15 18 9" fill="none" stroke="currentColor" stroke-width="2"/></svg>
    `;

    // Toggles Content
    const content = document.createElement('div');
    content.className = `category-toggles-content ${isFirst ? 'open' : ''}`;
    content.id = `content_${cat.id}`;

    const descP = document.createElement('p');
    descP.className = 'category-subdesc';
    descP.textContent = cat.description;
    content.appendChild(descP);

    for (const tog of cat.toggles) {
      const row = document.createElement('div');
      row.className = 'apple-toggle-row';

      const info = document.createElement('div');
      info.className = 'toggle-info';
      info.innerHTML = `
        <span class="toggle-label">${tog.label}</span>
        <span class="toggle-desc">${tog.desc}</span>
      `;

      const switchLabel = document.createElement('label');
      switchLabel.className = 'apple-switch';

      const input = document.createElement('input');
      input.type = 'checkbox';
      input.dataset.key = tog.key;

      const rawVal = await NativeStorage.get(tog.key);
      const isChecked = rawVal === null || rawVal === undefined ? tog.default : rawVal === 'true' || rawVal === true;
      input.checked = isChecked;

      input.addEventListener('change', async () => {
        const val = input.checked;
        await NativeStorage.set(tog.key, String(val));
        if (typeof tog.onChange === 'function') {
          try { await tog.onChange(val); } catch (e) {}
        }
        showToast(`${tog.label}: ${val ? 'Enabled' : 'Disabled'}`);
      });

      const slider = document.createElement('span');
      slider.className = 'apple-slider';

      switchLabel.appendChild(input);
      switchLabel.appendChild(slider);

      row.appendChild(info);
      row.appendChild(switchLabel);
      content.appendChild(row);
    }

    if (cat.customActions && cat.customActions.length > 0) {
      const actRow = document.createElement('div');
      actRow.className = 'category-action-row';
      for (const act of cat.customActions) {
        const actBtn = document.createElement('button');
        actBtn.type = 'button';
        actBtn.className = 'btn-card-action';
        actBtn.id = act.id;
        actBtn.innerHTML = `<span>${act.icon}</span> <span>${act.label}</span>`;
        actBtn.addEventListener('click', act.action);
        actRow.appendChild(actBtn);
      }
      content.appendChild(actRow);
    }

    headerBtn.addEventListener('click', () => {
      const isOpen = content.classList.toggle('open');
      headerBtn.classList.toggle('expanded', isOpen);
    });

    card.appendChild(headerBtn);
    card.appendChild(content);
    container.appendChild(card);
  }
}

async function showMemoryManagementDialog() {
  const modal = document.getElementById('memoryModal');
  const listEl = document.getElementById('memoryFactsList');
  if (!modal || !listEl) return;

  const facts = await MemoryManager.getAllFacts();
  listEl.innerHTML = '';

  if (!facts.length) {
    listEl.innerHTML = '<div class="memory-empty-state">No permanent memories recorded yet.<br>State facts in conversation (e.g., "Remember that my favorite book is Dune") to populate this vault.</div>';
  } else {
    for (const f of facts) {
      const card = document.createElement('div');
      card.className = 'memory-fact-card';
      const timeStr = f.timestamp ? new Date(f.timestamp).toLocaleDateString() : '';
      card.innerHTML = `
        <div class="fact-text-wrap">
          <span class="fact-text">${escapeHtml(f.fact)}</span>
          <div class="fact-meta">
            <span class="fact-category-tag">${escapeHtml(f.category || 'personal')}</span>
            <span class="fact-time-tag">${timeStr}</span>
          </div>
        </div>
        <button class="btn-delete-fact" title="Delete this memory" data-id="${f.id}">
          <svg viewBox="0 0 24 24" width="14" height="14"><polyline points="3 6 5 6 21 6" fill="none" stroke="currentColor" stroke-width="2"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" fill="none" stroke="currentColor" stroke-width="2"/></svg>
        </button>
      `;

      card.querySelector('.btn-delete-fact').addEventListener('click', async () => {
        await MemoryManager.deleteFact(f.id);
        card.remove();
        if (!listEl.children.length) {
          listEl.innerHTML = '<div class="memory-empty-state">No permanent memories recorded yet.</div>';
        }
        showToast('Memory forgotten');
      });

      listEl.appendChild(card);
    }
  }

  modal.classList.remove('hidden');
}

function initMemoryModalListeners() {
  $('#btnCloseMemoryModal')?.addEventListener('click', () => {
    $('#memoryModal')?.classList.add('hidden');
  });

  $('#btnClearAllMemories')?.addEventListener('click', async () => {
    if (confirm('Are you sure you want Marvo to forget all permanently stored facts?')) {
      await MemoryManager.clearAll();
      const listEl = document.getElementById('memoryFactsList');
      if (listEl) listEl.innerHTML = '<div class="memory-empty-state">No permanent memories recorded yet.</div>';
      showToast('All memories cleared');
    }
  });
}

/* ═══════ LOCAL CHAT PERSISTENCE HELPERS ═══════ */
async function saveLocalMessage(sessionId, role, content) {
  if (!sessionId || !content) return;
  try {
    const key = CHAT_PREFIX + sessionId;
    const msgs = (await NativeStorage.getJSON(key, [])) || [];
    msgs.push({ role, content, timestamp: Date.now() });
    await NativeStorage.setJSON(key, msgs);
  } catch (err) {
    console.warn('[NativeStorage] Save message error:', err);
  }
}

async function getLocalMessages(sessionId) {
  if (!sessionId) return [];
  try {
    return (await NativeStorage.getJSON(CHAT_PREFIX + sessionId, [])) || [];
  } catch (err) {
    console.warn('[NativeStorage] Get messages error:', err);
    return [];
  }
}

async function saveLocalSession(sessionId, title) {
  if (!sessionId) return;
  try {
    let sessions = (await NativeStorage.getJSON(SESSIONS_STORAGE_KEY, [])) || [];
    const cleanTitle = (title && title.trim()) ? title.trim().slice(0, 60) : 'New Conversation';
    const existingIndex = sessions.findIndex(s => s.session_id === sessionId);
    if (existingIndex >= 0) {
      if (title && title.trim()) sessions[existingIndex].title = cleanTitle;
      sessions[existingIndex].updated_at = Date.now();
      const item = sessions.splice(existingIndex, 1)[0];
      sessions.unshift(item);
    } else {
      sessions.unshift({
        session_id: sessionId,
        title: cleanTitle,
        updated_at: Date.now(),
      });
    }
    if (sessions.length > 100) sessions = sessions.slice(0, 100);
    await NativeStorage.setJSON(SESSIONS_STORAGE_KEY, sessions);
  } catch (err) {
    console.warn('[NativeStorage] Save session error:', err);
  }
}

async function deleteLocalSession(sessionId) {
  if (!sessionId) return;
  try {
    let sessions = (await NativeStorage.getJSON(SESSIONS_STORAGE_KEY, [])) || [];
    sessions = sessions.filter(s => s.session_id !== sessionId);
    await NativeStorage.setJSON(SESSIONS_STORAGE_KEY, sessions);
    await NativeStorage.remove(CHAT_PREFIX + sessionId);
  } catch (err) {
    console.warn('[NativeStorage] Delete session error:', err);
  }
}

async function renameLocalSession(sessionId, newTitle) {
  if (!sessionId || !newTitle) return;
  try {
    let sessions = (await NativeStorage.getJSON(SESSIONS_STORAGE_KEY, [])) || [];
    const target = sessions.find(s => s.session_id === sessionId);
    if (target) {
      target.title = newTitle.trim().slice(0, 60);
      target.updated_at = Date.now();
      await NativeStorage.setJSON(SESSIONS_STORAGE_KEY, sessions);
    }
  } catch (err) {
    console.warn('[NativeStorage] Rename session error:', err);
  }
}

/* ═══════ 3D EYE EXPRESSIONS ENGINE (15+ Advanced States) ═══════ */
const STATES = [
  'state-idle', 'state-listening', 'state-loading', 'state-processing', 'state-speaking',
  'state-sleepy', 'state-crying', 'state-winking', 'state-angry', 'state-surprised',
  'state-laughing', 'state-thinking', 'state-scanning', 'state-dizzy', 'state-error',
  'state-happy', 'state-confused', 'state-excited', 'state-searching', 'state-sad',
  'state-love', 'state-proud', 'state-nervous', 'state-calm', 'state-bored',
  'state-amazed', 'state-grateful', 'state-mysterious', 'state-playful', 'state-serious',
  'state-shy', 'state-confident', 'state-creative', 'state-calculating', 'state-remembering',
  'state-learning', 'state-explaining', 'state-warning', 'state-success', 'state-greeting',
  'state-farewell', 'state-joking', 'state-sarcastic', 'state-empathetic', 'state-motivating',
  'state-questioning', 'state-answering', 'state-coding', 'state-music', 'state-weather',
  'state-news', 'state-math', 'state-science', 'state-history', 'state-philosophy', 'state-gaming',
  // 15 New Advanced States (Phase 3)
  'state-suspicious', 'state-heart-eyes', 'state-focused', 'state-scared', 'state-glitch',
  'state-stargazing', 'state-dead', 'state-eyeroll', 'state-hypnotized', 'state-ninja',
  'state-overheating',
  // 10 Core Character States (Step 26)
  'state-typing', 'state-funny', 'state-singing', 'state-sleeping',
];

let currentSessionId = sessionStorage.getItem(SESSION_STORAGE_KEY) || generateSessionId();
let currentVoice     = localStorage.getItem(VOICE_STORAGE_KEY) || 'voice_3';
let sessionVersion   = 0;
let selectedMode     = 'medium'; // 'fast', 'medium' (Thinking), 'high' (Pro)
let activeAgent      = 'gemini'; // 'gemini', 'huggingface', 'pollinations', 'claude'
let isBusy           = false;
let currentChatAbortController = null;
let hasInteracted    = false;
let contextTargetSessionId = null;
let currentAudio     = null;
let lastUserMessage  = '';

const AGENT_PLACEHOLDERS = {
  gemini:       'Ask Gemini anything...',
  huggingface:  'Describe visual to generate with Hugging Face (Pro)...',
  pollinations: 'Describe visual to generate instantly with Pollinations (Fast)...',
  claude:       'Ask Claude to code, debug, or solve complex logic...',
  // Backward compatibility aliases
  aura:         'Ask Gemini anything...',
  nexus:        'Ask Claude to code, debug, or analyze...',
  lumina:       'Describe visual to generate with Hugging Face (Pro)...',
  orion:        'Ask Gemini anything...',
};

const AGENT_DISPLAY_NAMES = {
  gemini:       'Gemini',
  huggingface:  'Hugging Face',
  pollinations: 'Pollinations',
  claude:       'Claude',
  aura:         'Gemini',
  nexus:        'Claude',
  lumina:       'Hugging Face',
  orion:        'Gemini',
};

function getActiveModeName() {
  return (selectedMode === 'pro' || selectedMode === 'high') ? 'Pro' : 'Fast';
}

const CHARACTER_PROFILES = {
  'char-spiderman': {
    name: 'Spider-Man',
    idleImg: 'assets/characters/spiderman-idle.jpg',
    activeImg: 'assets/characters/spiderman-active.jpg',
    thinkingImg: 'assets/characters/spiderman-idle.jpg',
  },
  'char-monk': {
    name: 'Zen Monk',
    idleImg: 'assets/characters/monk-idle.jpg',
    activeImg: 'assets/characters/monk-active.jpg',
    thinkingImg: 'assets/characters/monk-vista.jpg',
  },
  'char-ironman': {
    name: 'Iron Man',
    idleImg: 'assets/characters/ironman.svg',
    activeImg: 'assets/characters/ironman.svg',
    thinkingImg: 'assets/characters/ironman.svg',
  },
  'char-hulk': {
    name: 'Hulk',
    idleImg: 'assets/characters/hulk.svg',
    activeImg: 'assets/characters/hulk.svg',
    thinkingImg: 'assets/characters/hulk.svg',
  },
  'char-batman': {
    name: 'Batman',
    idleImg: 'assets/characters/batman.svg',
    activeImg: 'assets/characters/batman.svg',
    thinkingImg: 'assets/characters/batman.svg',
  },
  'char-wolverine': {
    name: 'Wolverine',
    idleImg: 'assets/characters/wolverine.svg',
    activeImg: 'assets/characters/wolverine.svg',
    thinkingImg: 'assets/characters/wolverine.svg',
  },
  'char-deadpool': {
    name: 'Deadpool',
    idleImg: 'assets/characters/deadpool.svg',
    activeImg: 'assets/characters/deadpool.svg',
    thinkingImg: 'assets/characters/deadpool.svg',
  },
  'char-naruto': {
    name: 'Naruto',
    idleImg: 'assets/characters/naruto.svg',
    activeImg: 'assets/characters/naruto.svg',
    thinkingImg: 'assets/characters/naruto.svg',
  },
  'char-goku': {
    name: 'Goku',
    idleImg: 'assets/characters/goku.svg',
    activeImg: 'assets/characters/goku.svg',
    thinkingImg: 'assets/characters/goku.svg',
  },
  'char-cyberpunk': {
    name: 'Cyberpunk',
    idleImg: 'assets/characters/cyberpunk.svg',
    activeImg: 'assets/characters/cyberpunk.svg',
    thinkingImg: 'assets/characters/cyberpunk.svg',
  },
};

let currentTheme = 'dark';
let currentCharacterState = 'state-idle';

/* Eye & Character Reactive Expression Trigger */
function setEyeExpression(state) {
  if (!STATES.includes(state)) return;
  currentCharacterState = state;

  STATES.forEach(s => {
    if (DOM.face) DOM.face.classList.remove(s);
    if (DOM.characterAvatar) DOM.characterAvatar.classList.remove(s);
  });
  if (DOM.face) DOM.face.classList.add(state);
  if (DOM.characterAvatar) DOM.characterAvatar.classList.add(state);

  const stateClean = state.replace('state-', '').toUpperCase();
  if (DOM.stateLabel) DOM.stateLabel.textContent = stateClean;
  if (DOM.characterStateBadge) DOM.characterStateBadge.textContent = stateClean;

  // React character images based on state (e.g. Spider-Man & Monk user photos)
  if (CHARACTER_PROFILES[currentTheme] && DOM.characterImg) {
    const prof = CHARACTER_PROFILES[currentTheme];
    if (state === 'state-speaking' || state === 'state-singing') {
      DOM.characterImg.src = prof.activeImg || prof.idleImg;
    } else if (state === 'state-thinking' || state === 'state-typing') {
      DOM.characterImg.src = prof.thinkingImg || prof.idleImg;
    } else {
      DOM.characterImg.src = prof.idleImg;
    }
  }
}

/* Contextual Keyword Detector */
const _EX_MAP = [
  ['state-crying',      ['cry','crying','tears','weep','heartbroken','sorrow','sad','dukhi','rona','aansu','miss you']],
  ['state-laughing',    ['haha','hehe','lol','lmao','rofl','funny','hilarious','joke','hasna','mazaak','laugh']],
  ['state-winking',     ['wink','winking','flirt','secret','just between us','naughty','chupa rustam','ishara','smart']],
  ['state-surprised',   ['wow','omg','what?!','unbelievable','shocking','surprise','really?','sach me','kya baat']],
  ['state-angry',       ['angry','gussa','hate','furious','rage','annoyed','terrible','worst','stupid','bakwas']],
  ['state-sleepy',      ['sleep','sleepy','tired','exhausted','bedtime','good night','yawning','neend','so jao']],
  ['state-scanning',    ['scan','scanning','analyze','inspect','search database','diagnose','check file','analyzing']],
  ['state-dizzy',       ['dizzy','headache','spinning','chakkar','round and round']],
  ['state-thinking',    ['think','reasoning','algorithm','logic','pondering','plan','calculate','sochna','solve']],
  ['state-coding',      ['code','coding','program','debug','function','variable','script','python','javascript','html','css','api','developer','github']],
  ['state-math',        ['math','calculate','equation','formula','algebra','geometry','calculus','percent','multiply','sum']],
  ['state-music',       ['music','song','sing','melody','guitar','piano','rapper','album','spotify','concert']],
  ['state-weather',     ['weather','mausam','temperature','rain','sunny','cloudy','storm','snow']],
  ['state-heart-eyes',  ['love','pyaar','ishq','dil','heart','romantic','valentine','crush','baby','darling','adore','sweetheart']],
  ['state-suspicious',  ['suspicious','sus','lie','lying','fake','doubt','shak','sach batao','imposter','conspiracy','cheat']],
  ['state-confused',    ['confused','what do you mean','samajh nahi','kya keh rahe','puzzled','bewildered','lost']],
  ['state-focused',     ['focus','concentrate','target','aim','precise','attention','dhyan','goal','mission','priority']],
  ['state-scared',      ['scared','fear','ghost','horror','afraid','spooky','darr','bhut','creep','nightmare','terrified']],
  ['state-glitch',      ['glitch','bug','matrix','corrupt','lag','crash','broken','cyberpunk','hacked']],
  ['state-stargazing',  ['star','galaxy','space','universe','cosmos','astronomy','telescope','sky','planet','tara','chand']],
  ['state-dead',        ['dead','offline','rip','shutdown','power off','khatam','sleep forever','mar gaya','turn off']],
  ['state-eyeroll',     ['eyeroll','roll eyes','whatever','duh','annoying','chup','irritating','bakwaas']],
  ['state-hypnotized',  ['hypnotize','hypnotized','trance','mesmerize','spell','magic','vash','illusion','mind blown']],
  ['state-bored',       ['bored','boring','bore ho raha','meh','tiresome','uninteresting','so boring']],
  ['state-ninja',       ['ninja','stealth','shadow','samurai','assassin','martial art','shinobi','silent','knife']],
  ['state-overheating', ['overheat','overheating','hot','burning','fire','aag','garam','smoke','meltdown','explosion','too hot']],
];

function detectEyeExpression(userText, aiText) {
  const combo = `${userText} ${aiText}`.toLowerCase();
  for (const [state, keywords] of _EX_MAP) {
    for (const kw of keywords) {
      if (combo.includes(kw)) return state;
    }
  }
  return null;
}

/* ═══════════════════════════════════════════════════════════════════
   TOAST NOTIFICATION HELPER
   ═══════════════════════════════════════════════════════════════════ */
let toastTimeout = null;
function showToast(msg, duration = 2400) {
  if (!DOM.appToast) return;
  DOM.appToast.textContent = msg;
  DOM.appToast.classList.add('show');
  if (toastTimeout) clearTimeout(toastTimeout);
  toastTimeout = setTimeout(() => {
    DOM.appToast.classList.remove('show');
  }, duration);
}

/* ═══════════════════════════════════════════════════════════════════
   THEME & FULLSCREEN STATUS BAR INITIALIZATION
   ═══════════════════════════════════════════════════════════════════ */
async function setTheme(theme) {
  currentTheme = theme || 'dark';

  // Clear all previous theme classes
  DOM.body.classList.remove('theme-light');
  Object.keys(CHARACTER_PROFILES).forEach(k => {
    DOM.body.classList.remove(`theme-${k}`);
  });

  if (currentTheme === 'light') {
    DOM.body.classList.add('theme-light');
    if (DOM.face) DOM.face.classList.remove('hidden');
    if (DOM.characterAvatar) DOM.characterAvatar.classList.add('hidden');
    if (DOM.eyeColorSection) DOM.eyeColorSection.style.display = 'block';
  } else if (currentTheme === 'dark') {
    if (DOM.face) DOM.face.classList.remove('hidden');
    if (DOM.characterAvatar) DOM.characterAvatar.classList.add('hidden');
    if (DOM.eyeColorSection) DOM.eyeColorSection.style.display = 'block';
  } else if (CHARACTER_PROFILES[currentTheme]) {
    const profile = CHARACTER_PROFILES[currentTheme];
    DOM.body.classList.add(`theme-${currentTheme}`);
    if (DOM.face) DOM.face.classList.add('hidden');
    if (DOM.characterAvatar) {
      DOM.characterAvatar.classList.remove('hidden');
      if (DOM.characterImg) DOM.characterImg.src = profile.idleImg;
      if (DOM.characterNameBadge) DOM.characterNameBadge.textContent = profile.name;
      if (DOM.characterStateBadge) DOM.characterStateBadge.textContent = 'IDLE';
    }
    if (DOM.eyeColorSection) DOM.eyeColorSection.style.display = 'none';
  }

  // Update active state in UI buttons
  document.querySelectorAll('.theme-choice-card').forEach(c => {
    c.classList.toggle('active', c.dataset.theme === currentTheme);
  });
  document.querySelectorAll('.character-card-btn').forEach(c => {
    c.classList.toggle('active', c.dataset.theme === currentTheme);
  });

  await NativeStorage.set(THEME_STORAGE_KEY, currentTheme);
  closeAllDropdowns();
}

async function initTheme() {
  const saved = (await NativeStorage.get(THEME_STORAGE_KEY)) || 'dark';
  await setTheme(saved);
}

async function initStatusBar() {
  if (window.Capacitor?.Plugins?.StatusBar) {
    try {
      await window.Capacitor.Plugins.StatusBar.setOverlaysWebView({ overlay: true });
      await window.Capacitor.Plugins.StatusBar.setBackgroundColor({ color: '#00000000' });
      await window.Capacitor.Plugins.StatusBar.setStyle({ style: 'DARK' });
    } catch (err) {
      console.warn('[StatusBar] Init error:', err);
    }
  }
}

/* ═══════════════════════════════════════════════════════════════════
   CLAUDE-STYLE "PROJECTS" SYSTEM (Custom System Context & Persona)
   ═══════════════════════════════════════════════════════════════════ */
const ACTIVE_PROJECT_KEY = 'marvo.activeProject';
let activeProject = null;

async function loadActiveProject() {
  try {
    const data = await NativeStorage.getJSON(ACTIVE_PROJECT_KEY, null);
    if (data && data.name && data.name.trim()) {
      activeProject = {
        name: data.name.trim(),
        instructions: (data.instructions || '').trim(),
      };
      renderActiveProjectBadge();
    } else {
      activeProject = null;
      renderActiveProjectBadge();
    }
  } catch (err) {
    console.warn('[Projects] Failed to load active project:', err);
  }
}

function renderActiveProjectBadge() {
  if (!DOM.activeProjectBadge) return;
  if (activeProject && activeProject.name) {
    DOM.activeProjectBadge.classList.remove('hidden');
    if (DOM.activeProjectName) DOM.activeProjectName.textContent = activeProject.name;
    DOM.activeProjectBadge.title = `Project "${activeProject.name}" is active. Click to edit.`;
    DOM.btnClearProject?.classList.remove('hidden');
  } else {
    DOM.activeProjectBadge.classList.add('hidden');
    DOM.btnClearProject?.classList.add('hidden');
  }
}

function openProjectModal() {
  closeAllDropdowns();
  closeSidebar();
  if (activeProject) {
    if (DOM.projectNameInput) DOM.projectNameInput.value = activeProject.name || '';
    if (DOM.projectPromptInput) DOM.projectPromptInput.value = activeProject.instructions || '';
    DOM.btnClearProject?.classList.remove('hidden');
  } else {
    if (DOM.projectNameInput) DOM.projectNameInput.value = '';
    if (DOM.projectPromptInput) DOM.projectPromptInput.value = '';
    DOM.btnClearProject?.classList.add('hidden');
  }
  DOM.projectModal?.classList.add('show');
  DOM.projectNameInput?.focus();
}

function closeProjectModal() {
  DOM.projectModal?.classList.remove('show');
}

async function saveActiveProject() {
  const name = DOM.projectNameInput ? DOM.projectNameInput.value.trim() : '';
  const instructions = DOM.projectPromptInput ? DOM.projectPromptInput.value.trim() : '';

  if (!name) {
    showToast('Please enter a project name');
    DOM.projectNameInput?.focus();
    return;
  }

  activeProject = { name, instructions };
  await NativeStorage.setJSON(ACTIVE_PROJECT_KEY, activeProject);
  renderActiveProjectBadge();
  closeProjectModal();
  showToast(`Project "${name}" activated!`);
}

async function clearActiveProject() {
  activeProject = null;
  await NativeStorage.remove(ACTIVE_PROJECT_KEY);
  renderActiveProjectBadge();
  closeProjectModal();
  showToast('Project cleared. Standard Marvo brain active.');
}

/* ═══════════════════════════════════════════════════════════════════
   SETTINGS MODAL & CATEGORIZED TABS & OFFLINE BRAIN DOWNLOADER
   ═══════════════════════════════════════════════════════════════════ */
let downloadPollTimer = null;

function openSettingsModal(initialTab = 'themes') {
  closeAllDropdowns();
  DOM.settingsModal.classList.add('show');
  switchSettingsTab(initialTab);
}

function closeSettingsModal() {
  DOM.settingsModal.classList.remove('show');
  stopDownloadPolling();
}

function switchSettingsTab(tabName) {
  const tabs = document.querySelectorAll('.settings-tab-btn');
  tabs.forEach(btn => {
    btn.classList.toggle('active', btn.dataset.tab === tabName);
  });

  document.querySelectorAll('.settings-tab-panel').forEach(panel => {
    panel.classList.remove('active');
  });

  let panelId;
  if (tabName === 'ai-control') {
    panelId = 'tabPanelAiControl';
  } else {
    panelId = `tabPanel${tabName.charAt(0).toUpperCase() + tabName.slice(1)}`;
  }
  const targetPanel = document.getElementById(panelId);
  if (targetPanel) {
    targetPanel.classList.add('active');
  }

  if (tabName === 'brain') {
    startDownloadPolling();
  } else {
    stopDownloadPolling();
  }

  if (tabName === 'downloads') {
    renderDownloadsHistory();
    renderDownloadedStorageViewer();
  }
}

async function startDownloadPolling() {
  stopDownloadPolling();
  await updateDownloadCard();
  downloadPollTimer = setInterval(updateDownloadCard, 1200);
}

function stopDownloadPolling() {
  if (downloadPollTimer) {
    clearInterval(downloadPollTimer);
    downloadPollTimer = null;
  }
}

function applyCardState(cardConfig, data, defaultTotMb) {
  const { badge, bar, text, btnDl, btnPause, btnCancel, readyLabel, idleLabel } = cardConfig;
  if (!badge || !bar || !text || !btnDl) return;

  const status = ((data && data.status) || 'idle').toLowerCase();
  const progress = (data && data.progress) || 0;
  const isReady = (data && data.isReady) || false;
  const dlMb = Math.round(((data && data.downloadedBytes) || 0) / (1024 * 1024));
  const totMb = Math.round(((data && data.totalBytes) || 0) / (1024 * 1024)) || defaultTotMb;

  if (isReady || status === 'completed') {
    badge.textContent = 'Model Ready (Active)';
    badge.textContent = '★ Active Engine';
    badge.className = 'offline-status-badge status-ready';
    bar.style.width = '100%';
    bar.classList.add('ready');
    text.textContent = `~${totMb} MB verified & active in /models/`;
    btnDl.textContent = readyLabel || 'Model Ready';
    btnDl.disabled = true;
    btnDl.textContent = 'Delete Model';
    btnDl.className = 'btn-offline-action danger';
    btnDl.disabled = false;
    if (btnPause) btnPause.style.display = 'none';
    if (btnCancel) btnCancel.style.display = 'none';
  } else if (status === 'downloading') {
    badge.textContent = `Downloading... (${progress}%)`;
    badge.className = 'offline-status-badge status-downloading';
    bar.style.width = progress + '%';
    bar.classList.remove('ready');
    text.textContent = `${dlMb} MB / ${totMb} MB (${progress}%)`;
    btnDl.textContent = 'Downloading in Background...';
    btnDl.disabled = false;
    btnDl.innerHTML = `<span class="spinner-inline"></span> <span>Downloading... ${progress}%</span>`;
    btnDl.className = 'btn-offline-action progress-mode';
    btnDl.disabled = true;
    if (btnPause) { btnPause.style.display = 'inline-block'; btnPause.textContent = 'Pause'; }
    if (btnCancel) { btnCancel.style.display = 'inline-block'; }
  } else if (status === 'paused' || status === 'paused_wifi') {
    badge.textContent = 'Paused';
    badge.className = 'offline-status-badge status-paused';
    bar.style.width = progress + '%';
    bar.classList.remove('ready');
    text.textContent = `${dlMb} MB / ${totMb} MB (Paused)`;
    btnDl.textContent = 'Resume Download';
    btnDl.className = 'btn-offline-action primary';
    btnDl.disabled = false;
    if (btnPause) { btnPause.style.display = 'none'; }
    if (btnCancel) { btnCancel.style.display = 'inline-block'; }
  } else {
    badge.textContent = 'Not Downloaded';
    badge.className = 'offline-status-badge status-idle';
    bar.style.width = '0%';
    bar.classList.remove('ready');
    text.textContent = `Requires ~${defaultTotMb}MB storage`;
    btnDl.textContent = idleLabel || 'Download Model';
    btnDl.className = 'btn-offline-action primary';
    btnDl.disabled = false;
    if (btnPause) btnPause.style.display = 'none';
    if (btnCancel) btnCancel.style.display = 'none';
  }
}

async function updateDownloadCard() {
  try {
    if (!window.Capacitor?.Plugins?.MarvoNativeBridge) return;

    let multiProgress = null;
    if (window.Capacitor.Plugins.MarvoNativeBridge.getMultiModelProgress) {
      try {
        multiProgress = await window.Capacitor.Plugins.MarvoNativeBridge.getMultiModelProgress();
      } catch (e) {
        console.warn('[OfflineBrain] getMultiModelProgress fallback:', e);
      }
    }

    // 1. LLM Model (Phi-3 Mini 4K)
    const llmData = (multiProgress && multiProgress.llm) ||
      (await window.Capacitor.Plugins.MarvoNativeBridge.getModelDownloadProgress());
    applyCardState({
      badge: document.getElementById('offlineBrainStatusBadge'),
      bar: document.getElementById('offlineProgressBar'),
      text: document.getElementById('offlineProgressText'),
      btnDl: document.getElementById('btnDownloadBrain'),
      btnPause: document.getElementById('btnPauseBrain'),
      btnCancel: document.getElementById('btnCancelBrain'),
      readyLabel: 'Offline Active',
      idleLabel: 'Download Offline Brain'
    }, llmData, 2200);

    // 2. STT Model (Whisper Tiny)
    if (multiProgress && multiProgress.stt) {
      applyCardState({
        badge: document.getElementById('offlineSttStatusBadge'),
        bar: document.getElementById('offlineSttProgressBar'),
        text: document.getElementById('offlineSttProgressText'),
        btnDl: document.getElementById('btnDownloadStt'),
        btnPause: document.getElementById('btnPauseStt'),
        btnCancel: document.getElementById('btnCancelStt'),
        readyLabel: 'STT Ready',
        idleLabel: 'Download Whisper STT'
      }, multiProgress.stt, 150);
    }

    // 3. TTS Model (Piper ONNX)
    if (multiProgress && multiProgress.tts) {
      applyCardState({
        badge: document.getElementById('offlineTtsStatusBadge'),
        bar: document.getElementById('offlineTtsProgressBar'),
        text: document.getElementById('offlineTtsProgressText'),
        btnDl: document.getElementById('btnDownloadTts'),
        btnPause: document.getElementById('btnPauseTts'),
        btnCancel: document.getElementById('btnCancelTts'),
        readyLabel: 'TTS Ready',
        idleLabel: 'Download Piper TTS'
      }, multiProgress.tts, 100);
    }

  } catch (err) {
    console.warn('[OfflineBrain] updateDownloadCard error:', err);
  }
}

function initDownloadCardControls() {
  const setupModelControls = (modelType, btnDlId, btnPauseId, btnCancelId, modelLabel) => {
    const btnDl = document.getElementById(btnDlId);
    const btnPause = document.getElementById(btnPauseId);
    const btnCancel = document.getElementById(btnCancelId);

    if (btnDl) {
      btnDl.addEventListener('click', async () => {
        if (btnDl.textContent && btnDl.textContent.includes('Delete')) {
          if (!confirm(`Delete ${modelLabel} from device storage?`)) return;
          try {
            if (window.Capacitor?.Plugins?.MarvoNativeBridge?.deleteOfflineModel) {
              await window.Capacitor.Plugins.MarvoNativeBridge.deleteOfflineModel({ modelType });
            }
            if (window.AiControlCenter?.deleteModel) {
              window.AiControlCenter.deleteModel(modelType === 'llm' ? 'phi-3-mini' : modelType);
            }
            showToast(`Deleted ${modelLabel}`);
            updateDownloadCard();
          } catch (e) {
            showToast('Delete error: ' + e.message);
          }
          return;
        }

        try {
          btnDl.innerHTML = '<span class="spinner-inline"></span> <span>Downloading... 1%</span>';
          btnDl.className = 'btn-offline-action progress-mode';
          if (window.Capacitor?.Plugins?.MarvoNativeBridge) {
            showToast(`Starting ${modelLabel} download in background...`);
            if (window.Capacitor.Plugins.MarvoNativeBridge.startTypedModelDownload) {
              await window.Capacitor.Plugins.MarvoNativeBridge.startTypedModelDownload({ modelType, allowMetered: true });
            } else {
              await window.Capacitor.Plugins.MarvoNativeBridge.startModelDownload({ allowMetered: true });
            }
            startDownloadPolling();
            updateDownloadCard();
          }
        } catch (e) {
          showToast('Download error: ' + e.message);
        }
      });
    }

    if (btnPause) {
      btnPause.addEventListener('click', async () => {
        try {
          if (window.Capacitor?.Plugins?.MarvoNativeBridge) {
            if (window.Capacitor.Plugins.MarvoNativeBridge.pauseTypedModelDownload) {
              await window.Capacitor.Plugins.MarvoNativeBridge.pauseTypedModelDownload({ modelType });
            } else {
              await window.Capacitor.Plugins.MarvoNativeBridge.pauseModelDownload();
            }
            showToast(`${modelLabel} download paused.`);
            updateDownloadCard();
          }
        } catch (e) {
          showToast('Pause error: ' + e.message);
        }
      });
    }

    if (btnCancel) {
      btnCancel.addEventListener('click', async () => {
        try {
          if (window.Capacitor?.Plugins?.MarvoNativeBridge) {
            if (window.Capacitor.Plugins.MarvoNativeBridge.cancelTypedModelDownload) {
              await window.Capacitor.Plugins.MarvoNativeBridge.cancelTypedModelDownload({ modelType });
            } else {
              await window.Capacitor.Plugins.MarvoNativeBridge.cancelModelDownload();
            }
            showToast(`${modelLabel} download canceled.`);
            updateDownloadCard();
          }
        } catch (e) {
          showToast('Cancel error: ' + e.message);
        }
      });
    }
  };

  // LLM Controls
  setupModelControls('llm', 'btnDownloadBrain', 'btnPauseBrain', 'btnCancelBrain', 'Offline Brain (Phi-3)');
  // STT Controls
  setupModelControls('stt', 'btnDownloadStt', 'btnPauseStt', 'btnCancelStt', 'Whisper STT');
  // TTS Controls
  setupModelControls('tts', 'btnDownloadTts', 'btnPauseTts', 'btnCancelTts', 'Piper TTS');
}

async function initVoiceSelection() {
  const savedVoice = await NativeStorage.get(VOICE_STORAGE_KEY);
  if (savedVoice) currentVoice = savedVoice;

  const targetRadio = document.querySelector(`input[name="marvoVoiceRadio"][value="${currentVoice}"]`);
  if (targetRadio) targetRadio.checked = true;

  document.querySelectorAll('input[name="marvoVoiceRadio"]').forEach(radio => {
    radio.addEventListener('change', async (e) => {
      if (e.target.checked) {
        currentVoice = e.target.value;
        await NativeStorage.set(VOICE_STORAGE_KEY, currentVoice);
        showToast(`Voice set to ${e.target.parentElement.querySelector('.voice-name').textContent}`);
      }
    });
  });

  // Play Sample button click listener
  document.querySelectorAll('.btn-voice-sample').forEach(btn => {
    btn.addEventListener('click', async (e) => {
      e.preventDefault();
      e.stopPropagation();

      const voiceId = btn.dataset.voice;
      if (currentAudio) {
        try {
          currentAudio.pause();
          currentAudio.currentTime = 0;
        } catch {}
        currentAudio = null;
      }

      try {
        const res = await fetch(API_PREVIEW_VOICE, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ voice_id: voiceId }),
        });

        if (!res.ok) return;
        const data = await res.json();
        if (data && data.audio_base64) {
          const audio = new Audio("data:audio/mp3;base64," + data.audio_base64);
          currentAudio = audio;
          audio.onplay = () => {
            btn.classList.add('playing-sample');
            setEyeExpression('state-speaking');
            DOM.face.classList.add('speaking-mode');
          };
          audio.onended = () => {
            btn.classList.remove('playing-sample');
            DOM.face.classList.remove('speaking-mode');
            setEyeExpression('state-idle');
            if (currentAudio === audio) currentAudio = null;
          };
          audio.onerror = () => {
            btn.classList.remove('playing-sample');
            DOM.face.classList.remove('speaking-mode');
            setEyeExpression('state-idle');
            if (currentAudio === audio) currentAudio = null;
          };
          audio.play().catch(() => {
            btn.classList.remove('playing-sample');
            DOM.face.classList.remove('speaking-mode');
            setEyeExpression('state-idle');
          });
        }
      } catch (err) {
        console.error('[Marvo] Voice preview error:', err);
        btn.classList.remove('playing-sample');
      }
    });
  });
}

/* ═══════════════════════════════════════════════════════════════════
   SESSION MANAGEMENT
   ═══════════════════════════════════════════════════════════════════ */
function generateSessionId() {
  const ts = Date.now().toString(36);
  const rnd = Math.random().toString(36).slice(2, 8);
  return `s_${ts}_${rnd}`;
}

async function persistCurrentSession() {
  try {
    sessionStorage.setItem(SESSION_STORAGE_KEY, currentSessionId);
  } catch {}
  await NativeStorage.set(SESSION_STORAGE_KEY, currentSessionId);
}

/* ═══════════════════════════════════════════════════════════════════
   STEP 29: APPLE INTELLIGENCE XML TAG CLEANER & AUDIO SYNC
   ═══════════════════════════════════════════════════════════════════ */
/**
 * Step 29: Strips raw Apple Intelligence XML tags (<coreResponse>, </coreResponse>,
 * <image...>, </image>, <imageCollection...>, </imageCollection>, <key_entity...>, </key_entity>)
 * before rendering text to the chat or passing it to speech engines.
 */
function stripAppleXmlTags(text) {
  if (!text || typeof text !== 'string') return text || '';
  if (typeof window.sanitizeLlmResponse === 'function') {
    return window.sanitizeLlmResponse(text);
  }
  return text
    .replace(/\[(?:DEVICE_STATE|APPLE_INTELLIGENCE_DIRECTIVES|ACADEMIC DIRECTIVE|PEDAGOGICAL_INSTRUCTION|GROUND TRUTH|SYSTEM INSTRUCTION|SYSTEM)[^\]]*\]/gi, '')
    .replace(/\[[A-Z0-9_]+:[^\]]*\]/gi, '')
    .replace(/<thought>[\s\S]*?<\/thought>/gi, '')
    .replace(/<think>[\s\S]*?<\/think>/gi, '')
    .replace(/<\/?(?:thought|think|coreResponse|suggestions|system|assistant)>/gi, '')
    .replace(/<\|[a-z0-9_\-]+\|>/gi, '')
    .replace(/<imageCollection[^>]*>[\s\S]*?<\/imageCollection>/gi, '')
    .replace(/<image[^>]*\/?>/gi, '')
    .replace(/<\/?image>/gi, '')
    .replace(/<key_entity[^>]*>/gi, '')
    .replace(/<\/key_entity>/gi, '')
    .replace(/###\s*🤖\s*Marvo Offline Brain Active\s*/gi, '')
    .replace(/-\s*\*\*Offline Mode\*\*:\s*Active[^\n]*\n?/gi, '')
    .replace(/-\s*\*\*Capabilities\*\*:[^\n]*\n?/gi, '')
    .replace(/###\s*🧠\s*Offline AI Brain\s*/gi, '')
    .trim();
}

/* ═══════════════════════════════════════════════════════════════════
   SPEECH & AUDIO
   ═══════════════════════════════════════════════════════════════════ */
let isWhisperDetected = false;

function stopSpeech() {
  if (currentAudio) {
    try {
      currentAudio.pause();
      currentAudio.currentTime = 0;
    } catch {}
    currentAudio = null;
  }
  if ('speechSynthesis' in window && window.speechSynthesis.speaking) {
    try { window.speechSynthesis.cancel(); } catch {}
  }
  document.querySelectorAll('.playing-tts').forEach(el => el.classList.remove('playing-tts'));
  DOM.face?.classList.remove('speaking-mode');
  setEyeExpression('state-idle');
  if (window.setVoiceState) window.setVoiceState('idle', 0);
}

async function playSpeech(text, btnElement = null) {
  if (currentAudio) {
    stopSpeech();
    return;
  }

  // Ensure speech recognition never overlaps with speaking
  if (speechRecognizer) {
    try { speechRecognizer.stop(); } catch {}
  }

  const cleanText = stripAppleXmlTags(text).replace(/[*_~`#]/g, '').trim();
  if (!cleanText) return;

  // Step 32: Whisper Mode Volume / Pitch adjustment
  const isWhisperMode = ((await NativeStorage.get('marvo.voice.whisper_mode')) !== 'false') && isWhisperDetected;
  const targetVolume = isWhisperMode ? 0.35 : 1.0;
  const targetPitch  = isWhisperMode ? 0.85 : 1.0;
  if (isWhisperMode) {
    showToast('🤫 Responding in Whisper Mode');
    isWhisperDetected = false;
  }

  if (btnElement) btnElement.classList.add('playing-tts');
  setEyeExpression('state-speaking');
  DOM.face?.classList.add('speaking-mode');
  if (window.dynamicIslandInstance) {
    window.dynamicIslandInstance.setResponseText(cleanText);
    window.dynamicIslandInstance.setVoiceState('speaking', 65);
  }
  if (window.setVoiceState) window.setVoiceState('speaking', 60);

  try {
    const res = await fetch(API_SPEAK, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        text: cleanText,
        voice_id: currentVoice,
        session_id: currentSessionId,
        whisper: isWhisperMode,
        volume: targetVolume,
        pitch: targetPitch
      }),
    });

    if (!res.ok) throw new Error(`Status ${res.status}`);
    const data = await res.json();

    if (data && data.audio_base64) {
      const audio = new Audio("data:audio/mp3;base64," + data.audio_base64);
      audio.volume = targetVolume;
      currentAudio = audio;

      audio.onended = () => {
        if (btnElement) btnElement.classList.remove('playing-tts');
        DOM.face.classList.remove('speaking-mode');
        setEyeExpression('state-idle');
        if (window.setVoiceState) window.setVoiceState('idle', 0);
        if (window.dynamicIslandInstance && window.dynamicIslandInstance.active) {
          setTimeout(() => {
            if (window.dynamicIslandInstance && !isVoiceRecording) {
              window.dynamicIslandInstance.close();
            }
          }, 2600);
        }
        if (currentAudio === audio) currentAudio = null;
      };
      audio.onerror = () => {
        if (btnElement) btnElement.classList.remove('playing-tts');
        DOM.face.classList.remove('speaking-mode');
        setEyeExpression('state-idle');
        if (window.setVoiceState) window.setVoiceState('idle', 0);
        if (currentAudio === audio) currentAudio = null;
      };
      await audio.play();
    } else {
      fallbackWebSpeech(cleanText, btnElement, targetVolume, targetPitch);
    }
  } catch (err) {
    console.warn('[Marvo] Backend TTS failed, fallback:', err);
    fallbackWebSpeech(cleanText, btnElement, targetVolume, targetPitch);
  }
}

function fallbackWebSpeech(text, btnElement, volume = 1.0, pitch = 1.0) {
  if (!('speechSynthesis' in window)) {
    if (btnElement) btnElement.classList.remove('playing-tts');
    DOM.face.classList.remove('speaking-mode');
    setEyeExpression('state-idle');
    if (window.setVoiceState) window.setVoiceState('idle', 0);
    return;
  }
  window.speechSynthesis.cancel();
  const utter = new SpeechSynthesisUtterance(text);
  utter.volume = volume;
  utter.pitch = pitch;
  utter.onend = () => {
    if (btnElement) btnElement.classList.remove('playing-tts');
    DOM.face.classList.remove('speaking-mode');
    setEyeExpression('state-idle');
    if (window.setVoiceState) window.setVoiceState('idle', 0);
    if (window.dynamicIslandInstance && window.dynamicIslandInstance.active) {
      setTimeout(() => {
        if (window.dynamicIslandInstance && !isVoiceRecording) {
          window.dynamicIslandInstance.close();
        }
      }, 2600);
    }
  };
  utter.onerror = () => {
    if (btnElement) btnElement.classList.remove('playing-tts');
    DOM.face.classList.remove('speaking-mode');
    setEyeExpression('state-idle');
    if (window.setVoiceState) window.setVoiceState('idle', 0);
  };
  if (window.dynamicIslandInstance) {
    window.dynamicIslandInstance.setResponseText(text);
    window.dynamicIslandInstance.setVoiceState('speaking', 65);
  }
  if (window.setVoiceState) window.setVoiceState('speaking', 60);
  window.speechSynthesis.speak(utter);
}

/* ═══════════════════════════════════════════════════════════════════
   CHAT RENDERING & ACTION BAR
   ═══════════════════════════════════════════════════════════════════ */
function copyToClipboard(text, btnElement) {
  navigator.clipboard.writeText(text).then(() => {
    if (btnElement) {
      btnElement.classList.add('copied');
      setTimeout(() => btnElement.classList.remove('copied'), 2000);
    }
    showToast('Copied to clipboard!');
  }).catch(() => {
    showToast('Failed to copy');
  });
}

function scrollToBottom() {
  DOM.chatArea.scrollTop = DOM.chatArea.scrollHeight;
}

function activateChatMode() {
  if (!hasInteracted) {
    hasInteracted = true;
    DOM.body.classList.add('chat-active');
  }
}

/* ═══════════════════════════════════════════════════════════════════
   UNIFIED DOWNLOADS & STORAGE (/storage/emulated/0/Download/Marvo/)
   ═══════════════════════════════════════════════════════════════════ */
const DOWNLOADS_STORAGE_KEY = 'marvo.downloadsHistory';

async function saveFileToUnifiedFolder({ base64Data, fileName, mimeType = 'image/jpeg', textContent = null }) {
  const safeName = fileName || `marvo_${Date.now()}.jpg`;
  try {
    if (window.Capacitor?.Plugins?.MarvoNativeBridge) {
      const res = await window.Capacitor.Plugins.MarvoNativeBridge.saveToUnifiedDownloads({
        base64Data,
        fileName: safeName,
        mimeType,
        textContent
      });
      await recordDownloadHistory(res.fileName || safeName, res.filePath || '/storage/emulated/0/Download/Marvo/');
      showToast('Saved to /storage/emulated/0/Download/Marvo/');
      return true;
    }
  } catch (err) {
    console.warn('[Downloads] Native bridge save error:', err);
  }

  // Web Browser fallback
  try {
    const a = document.createElement('a');
    a.download = safeName;
    a.href = base64Data || ('data:text/plain;charset=utf-8,' + encodeURIComponent(textContent || ''));
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    await recordDownloadHistory(safeName, 'Downloads/Marvo');
    showToast('Image downloaded!');
    return true;
  } catch (e) {
    console.error('[Downloads] Fallback failed:', e);
    showToast('Failed to save file');
    return false;
  }
}

async function recordDownloadHistory(fileName, path) {
  try {
    let list = (await NativeStorage.getJSON(DOWNLOADS_STORAGE_KEY, [])) || [];
    list.unshift({
      name: fileName,
      path: path || '/storage/emulated/0/Download/Marvo/',
      time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      timestamp: Date.now()
    });
    if (list.length > 50) list = list.slice(0, 50);
    await NativeStorage.setJSON(DOWNLOADS_STORAGE_KEY, list);
    renderDownloadsHistory();
  } catch (e) {
    console.warn('[Downloads] History error:', e);
  }
}

async function renderDownloadsHistory() {
  const container = DOM.downloadsHistoryList || document.getElementById('downloadsHistoryList');
  if (!container) return;
  const list = (await NativeStorage.getJSON(DOWNLOADS_STORAGE_KEY, [])) || [];
  if (!list.length) {
    container.innerHTML = '<div class="downloads-empty-hint">No saved files yet. Generate an image or export a chat to see it here!</div>';
    return;
  }
  container.innerHTML = list.slice(0, 10).map(item => `
    <div class="download-history-item">
      <div style="display:flex;flex-direction:column;">
        <span class="download-item-title">${escapeHtml(item.name)}</span>
        <span class="download-item-time">${escapeHtml(item.time)} • ${escapeHtml(item.path)}</span>
      </div>
      <span style="font-size:11px;color:var(--accent);font-weight:600;">Saved</span>
    </div>
  `).join('');
}

async function renderDownloadedStorageViewer() {
  const container = document.getElementById('downloadedStorageViewer');
  if (!container) return;

  // Initialize master offline ecosystem toggle
  const toggle = document.getElementById('toggleOfflineEcosystem');
  if (toggle && !toggle._hasListener) {
    toggle._hasListener = true;
    const isEnabled = localStorage.getItem('marvo.offline.ecosystem') !== 'false';
    toggle.checked = isEnabled;
    toggle.addEventListener('change', () => {
      localStorage.setItem('marvo.offline.ecosystem', toggle.checked.toString());
      showToast(toggle.checked ? '⚡ Offline Ecosystem & Local RAG: Active' : '☁️ Cloud Priority Active: Offline Fallback Silenced');
    });
  }

  let items = [];

  // 1. Check native offline models via MarvoNativeBridge
  try {
    if (window.Capacitor?.Plugins?.MarvoNativeBridge?.listOfflineModels) {
      const res = await window.Capacitor.Plugins.MarvoNativeBridge.listOfflineModels();
      if (res && res.models) {
        const list = Array.isArray(res.models) ? res.models : JSON.parse(res.models);
        list.forEach(m => {
          if (m.isDownloaded || m.status === 'completed' || m.downloadedBytes > 0) {
            items.push({
              type: 'model',
              id: m.id,
              name: m.name || m.fileName,
              badge: 'GGUF MODEL',
              size: m.sizeFormatted || `${Math.round((m.downloadedBytes || m.sizeBytes || 0) / (1024 * 1024))} MB`,
              path: m.storagePath || `/data/user/0/com.marvo.ai/files/models/${m.fileName || m.id}`
            });
          }
        });
      }
    }
  } catch (err) {
    console.warn('[StorageViewer] Error querying native models:', err);
  }

  // Fallback to AiControlCenter or offline models if native list returned empty in browser preview
  if (!items.length && window.AiControlCenter?.models) {
    window.AiControlCenter.models.forEach(m => {
      if (m.isDownloaded || m.progress === 100) {
        items.push({
          type: 'model',
          id: m.id,
          name: m.name,
          badge: 'GGUF MODEL',
          size: m.sizeFormatted,
          path: m.storagePath
        });
      }
    });
  }

  // 2. Check Local RAG indexed document
  if (window.RagEngine?.hasActiveDocument && window.RagEngine.hasActiveDocument()) {
    const doc = window.RagEngine.getActiveDocument();
    items.push({
      type: 'rag',
      id: doc.docId || 'active_rag_doc',
      name: doc.fileName || 'Local Textbook Document',
      badge: 'RAG VECTOR DB',
      size: `${doc.totalChunks || 1} chunks indexed`,
      path: `SQLite: marvo_rag.db [${doc.totalChunks || 1} vectors]`
    });
  }

  if (!items.length) {
    container.innerHTML = `
      <div class="downloads-empty-hint">
        No offline models or RAG documents stored yet.
        <br><span style="font-size:11px;opacity:0.75;">Download Phi-3/Gemma in AI Control Center or ingest a PDF in Study Mode to see it here.</span>
      </div>
    `;
    return;
  }

  container.innerHTML = items.map(item => `
    <div class="storage-item-card" data-storage-id="${escapeHtml(item.id)}" data-storage-type="${escapeHtml(item.type)}">
      <div class="storage-item-left">
        <div class="storage-item-title-row">
          <span class="storage-item-name">${escapeHtml(item.name)}</span>
          <span class="storage-item-badge ${item.type}">${escapeHtml(item.badge)}</span>
        </div>
        <div class="storage-item-meta">
          <span class="file-size">${escapeHtml(item.size)}</span>
          <span>&bull;</span>
          <span style="overflow:hidden;text-overflow:ellipsis;max-width:200px;">${escapeHtml(item.path)}</span>
        </div>
      </div>
      <button type="button" class="btn-delete-storage" title="Delete and free storage">
        <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
        <span>Delete</span>
      </button>
    </div>
  `).join('');

  // Attach delete handlers
  container.querySelectorAll('.storage-item-card').forEach(card => {
    const btn = card.querySelector('.btn-delete-storage');
    const id = card.dataset.storageId;
    const type = card.dataset.storageType;
    if (btn) {
      btn.onclick = async () => {
        if (!confirm(`Reclaim storage: delete "${id}"?`)) return;
        btn.disabled = true;
        btn.innerHTML = '<span>Deleting...</span>';

        if (type === 'rag') {
          if (window.RagEngine?.clearActiveDocument) {
            window.RagEngine.clearActiveDocument();
          }
          showToast('🗑️ Cleared RAG document from local storage');
        } else if (type === 'model') {
          try {
            if (window.Capacitor?.Plugins?.MarvoNativeBridge?.deleteOfflineModel) {
              await window.Capacitor.Plugins.MarvoNativeBridge.deleteOfflineModel({ modelType: id });
            }
            if (window.AiControlCenter?.refreshModels) {
              window.AiControlCenter.refreshModels();
            }
            showToast(`🗑️ Deleted ${id} from device storage`);
          } catch (err) {
            console.error('[StorageViewer] Delete error:', err);
            showToast(`Delete failed: ${err.message}`);
          }
        }
        await renderDownloadedStorageViewer();
      };
    }
  });
}

/* Instant Image Generation Parsing & Card Rendering */
const IMAGE_GENERATION_PATTERNS = [
  /\b(?:generate|create|make|draw|paint|render|sketch|illustrate|design)\b.*\b(?:image|picture|photo|art|poster|thumbnail|illustration|visual)\b/i,
  /\b(?:generate|create|make|draw|paint|render|sketch|illustrate|design)\b\s+(?:a|an|the)?\s*.*\b(?:portrait|scene|character|logo|cover|banner)\b/i,
  /\b(?:draw|paint|sketch|illustrate|create)\s+(?:a|an|the)?\s*.*\b(?:of|for|with|showing)\b/i,
  /\b(?:image|picture|photo|art|poster|thumbnail|illustration|visual)\s+(?:of|for|showing|about)\b/i
];

function cleanPromptForDisplay(raw) {
  if (!raw) return 'Visual generation';
  let t = String(raw);
  // Strip bracketed system instructions or time
  t = t.replace(/\[.*?\]/gs, '');
  t = t.replace(/^(?:user\s+question|user|question|prompt)\s*:\s*/i, '').trim();
  const lines = t.split('\n').map(l => l.trim()).filter(Boolean);
  if (lines.length) t = lines[lines.length - 1];
  t = t.replace(/^(?:user\s+question|user|question|prompt)\s*:\s*/i, '').trim();
  t = t.replace(/^(?:please\s+)?(?:generate|create|creat|make|produce|render|draw|paint|sketch)\s+(?:an?\s+)?(?:image|picture|pic|photo|thumbnail)?\s*(?:of|for|showing|about)?\s*/i, '');
  t = t.replace(/\s+(?:image|picture|photo|pic|drawing)$/i, '');
  t = t.trim().replace(/^["']|["']$/g, '');
  return t || 'Visual generation';
}

const CONVERSATIONAL_GREETING_REGEX = /^(?:hi+|hello|hey+|namaste|pranam|good\s+(?:morning|afternoon|evening)|how\s+are\s+you|what\s+is|who\s+are\s+you|kya\s+haal\s+hai|kaise\s+ho)\b/i;

function parseImageGenerationPrompt(input) {
  if (!input) return null;
  const trimmed = input.trim();
  if (!trimmed) return null;
  // Strict guard: Greetings and casual banter must never trigger Image Generation
  if (CONVERSATIONAL_GREETING_REGEX.test(trimmed)) return null;

  const explicitMatch = IMAGE_GENERATION_PATTERNS.some((pattern) => pattern.test(trimmed));
  if (!explicitMatch) return null;

  const cleaned = cleanPromptForDisplay(trimmed);
  return cleaned || 'a futuristic visual concept';
}

function renderImageMessageCard(promptText, imageUrl) {
  const card = document.createElement('div');
  card.className = 'msg-image-card';
  const isBase64 = imageUrl && imageUrl.startsWith('data:');
  const cleanPrompt = cleanPromptForDisplay(promptText);

  card.innerHTML = `
    <div class="msg-image-header">
      <div class="msg-image-tag">
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="3"/><circle cx="8.5" cy="8.5" r="1.5" fill="currentColor"/><path d="M21 15l-5-5L5 21"/></svg>
        <span>Marvo Visual AI ${isBase64 ? '(SDXL HD)' : ''}</span>
      </div>
      <span style="font-size:11px;color:var(--text-dim);font-weight:600;">1024 x 1024</span>
    </div>
    <div class="msg-image-wrap">
      <div class="msg-image-skeleton">
        <span style="width:22px;height:22px;border:2px solid var(--accent);border-top-color:transparent;border-radius:50%;animation:iris-spin 0.8s linear infinite;"></span>
        <span>Rendering visual generation...</span>
      </div>
      <img class="msg-image-img" src="${imageUrl}" alt="${escapeHtml(cleanPrompt)}" loading="lazy" />
    </div>
    <p class="msg-image-prompt-text">"${escapeHtml(cleanPrompt)}"</p>
    <button class="msg-image-download-btn action-download-img" type="button" title="Download Image">
      <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
      <span>Download Image</span>
    </button>
    <div class="msg-image-footer">
      <button class="msg-image-action-btn action-copy-prompt" type="button" title="Copy Clean Prompt">
        <svg viewBox="0 0 24 24" width="12" height="12"><rect x="9" y="9" width="13" height="13" rx="2" fill="none" stroke="currentColor" stroke-width="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" fill="none" stroke="currentColor" stroke-width="2"/></svg>
        <span>Copy Prompt</span>
      </button>
      <a href="${imageUrl}" target="_blank" rel="noopener noreferrer" class="msg-image-action-btn action-view-full" title="Open Full Size">
        <svg viewBox="0 0 24 24" width="12" height="12"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" fill="none" stroke="currentColor" stroke-width="2"/><polyline points="15 3 21 3 21 9" fill="none" stroke="currentColor" stroke-width="2"/><line x1="10" y1="14" x2="21" y2="3" stroke="currentColor" stroke-width="2"/></svg>
        <span>Full Size</span>
      </a>
    </div>
  `;

  const img = card.querySelector('.msg-image-img');
  const skeleton = card.querySelector('.msg-image-skeleton');
  const copyBtn = card.querySelector('.action-copy-prompt');
  const downloadBtn = card.querySelector('.action-download-img');

  img.onload = () => {
    if (skeleton) skeleton.style.display = 'none';
    img.classList.add('loaded');
  };

  img.onerror = () => {
    img.style.display = 'none';
    if (skeleton) {
      skeleton.style.display = 'flex';
      skeleton.style.animation = 'none';
      skeleton.style.background = 'rgba(15, 20, 30, 0.9)';
      skeleton.innerHTML = `
        <div class="msg-image-fail-card">
          <div class="fail-icon-badge">
            <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="#ff0055" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="8" x2="12" y2="12"/>
              <line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
          </div>
          <span class="fail-title">Generation Refresh Needed</span>
          <span class="fail-desc">Service busy or refreshing. Tap below to reload.</span>
          <button class="retry-img-btn" type="button">
            <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
            <span>Reload Visual</span>
          </button>
        </div>
      `;
      skeleton.querySelector('.retry-img-btn')?.addEventListener('click', () => {
        img.style.display = 'block';
        skeleton.style.display = 'flex';
        skeleton.style.animation = '';
        skeleton.style.background = '';
        skeleton.innerHTML = `
          <span style="width:22px;height:22px;border:2px solid var(--accent);border-top-color:transparent;border-radius:50%;animation:iris-spin 0.8s linear infinite;"></span>
          <span>Refreshing generation...</span>
        `;
        const cleanBase = imageUrl.split('&t=')[0].split('?t=')[0];
        const sep = cleanBase.includes('?') ? '&' : '?';
        img.src = `${cleanBase}${sep}seed=${Math.floor(Math.random() * 1000000)}&t=${Date.now()}`;
      });
    }
  };

  copyBtn?.addEventListener('click', (e) => {
    e.stopPropagation();
    copyToClipboard(cleanPrompt, copyBtn);
    showToast('Prompt copied!');
  });

  downloadBtn?.addEventListener('click', async (e) => {
    e.stopPropagation();
    try {
      showToast('Saving to Marvo storage...');
      const filename = `marvo-art-${Date.now()}.jpg`;

      if (imageUrl.startsWith('data:')) {
        await saveFileToUnifiedFolder({
          base64Data: imageUrl,
          fileName: filename,
          mimeType: 'image/jpeg'
        });
      } else {
        const resp = await fetch(imageUrl);
        const blob = await resp.blob();
        const reader = new FileReader();
        reader.onload = async () => {
          await saveFileToUnifiedFolder({
            base64Data: reader.result,
            fileName: filename,
            mimeType: 'image/jpeg'
          });
        };
        reader.readAsDataURL(blob);
      }
    } catch (err) {
      console.warn('Direct save failed, fallback to download link', err);
      const a = document.createElement('a');
      a.href = imageUrl;
      a.download = `marvo-art-${Date.now()}.jpg`;
      a.target = '_blank';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
    }
  });

  return card;
}

let attachedFiles = [];

function initMultimodalAttachments() {
  const input = DOM.fileUploadInput;
  if (!input) return;

  input.addEventListener('change', async (e) => {
    const files = Array.from(e.target.files || []);
    if (!files.length) return;

    for (const file of files) {
      await processAttachment(file);
    }
    renderAttachmentShelf();
    input.value = '';
  });

  DOM.btnAttachPhoto?.addEventListener('click', (e) => {
    e.stopPropagation();
    closeAllDropdowns();
    if (input) {
      input.accept = 'image/*';
      input.click();
    }
  });

  DOM.btnAttachVideo?.addEventListener('click', (e) => {
    e.stopPropagation();
    closeAllDropdowns();
    if (input) {
      input.accept = 'video/*';
      input.click();
    }
  });

  DOM.btnAttachPDF?.addEventListener('click', (e) => {
    e.stopPropagation();
    closeAllDropdowns();
    if (input) {
      input.accept = 'application/pdf';
      input.click();
    }
  });

  DOM.btnAttachFiles?.addEventListener('click', (e) => {
    e.stopPropagation();
    closeAllDropdowns();
    if (input) {
      input.accept = '*/*';
      input.click();
    }
  });
}

async function processAttachment(file) {
  return new Promise((resolve) => {
    const isImg = file.type.startsWith('image/');
    const isVid = file.type.startsWith('video/');
    const isPdf = file.type === 'application/pdf';

    const reader = new FileReader();
    reader.onload = (evt) => {
      attachedFiles.push({
        name: file.name,
        size: formatFileSize(file.size),
        type: file.type || 'file',
        dataUrl: evt.target.result,
        isImage: isImg,
        isVideo: isVid,
        isPdf: isPdf
      });
      resolve();
    };
    reader.onerror = () => resolve();
    reader.readAsDataURL(file);
  });
}

function formatFileSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function renderAttachmentShelf() {
  const shelf = DOM.attachmentPreviewShelf;
  if (!shelf) return;
  shelf.innerHTML = '';

  if (attachedFiles.length === 0) {
    shelf.classList.add('hidden');
    return;
  }

  shelf.classList.remove('hidden');
  attachedFiles.forEach((file, index) => {
    const chip = document.createElement('div');
    chip.className = 'attachment-chip';

    if (file.isImage) {
      chip.innerHTML = `
        <img src="${file.dataUrl}" class="attachment-chip-thumb" alt="${escapeHtml(file.name)}" />
        <div class="attachment-chip-info">
          <span class="attachment-chip-name">${escapeHtml(file.name)}</span>
          <span class="attachment-chip-size">${file.size}</span>
        </div>
        <button type="button" class="attachment-chip-remove" data-index="${index}" aria-label="Remove">&times;</button>
      `;
    } else {
      const icon = file.isPdf ? 'PDF' : (file.isVideo ? 'VID' : 'FILE');
      chip.innerHTML = `
        <div class="attachment-chip-icon">
          <span style="font-size:9.5px;font-weight:700;">${icon}</span>
        </div>
        <div class="attachment-chip-info">
          <span class="attachment-chip-name">${escapeHtml(file.name)}</span>
          <span class="attachment-chip-size">${file.size}</span>
        </div>
        <button type="button" class="attachment-chip-remove" data-index="${index}" aria-label="Remove">&times;</button>
      `;
    }
    shelf.appendChild(chip);
  });

  shelf.querySelectorAll('.attachment-chip-remove').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const idx = parseInt(btn.dataset.index, 10);
      if (!isNaN(idx)) {
        attachedFiles.splice(idx, 1);
        renderAttachmentShelf();
      }
    });
  });
}

// Step 31: Super Student Mode UI synchronizer
function updateStudentModeUI(isActive) {
  const badge = document.getElementById('studentModeBadge');
  if (badge) {
    badge.textContent = isActive ? 'ON' : 'OFF';
    badge.style.background = isActive ? 'rgba(0, 255, 136, 0.2)' : 'rgba(255, 255, 255, 0.1)';
    badge.style.color = isActive ? '#00ff88' : 'var(--text-dim)';
  }
  const navBtn = document.getElementById('navStudent');
  if (navBtn) {
    navBtn.classList.toggle('active', !!isActive);
  }
}

// Step 31: Apple Intelligence Smart Reply Chips rendered above input bar
async function displaySmartReplyChips(aiText, userPrompt = '') {
  const container = document.getElementById('smartReplyContainer');
  const chipsContainer = document.getElementById('smartReplyChips');
  if (!container || !chipsContainer) return;

  const isEnabled = (await NativeStorage.get('marvo.smart_replies.enabled')) !== 'false';
  if (!isEnabled || !aiText) {
    container.classList.add('hidden');
    chipsContainer.innerHTML = '';
    return;
  }

  let suggestions = [];

  // 1. Try to extract from <suggestions> tag in raw text before stripping
  const suggestionsMatch = typeof aiText === 'string' ? aiText.match(/<suggestions>([\s\S]*?)<\/suggestions>/i) : null;
  if (suggestionsMatch && suggestionsMatch[1]) {
    try {
      const parsed = JSON.parse(suggestionsMatch[1].trim());
      if (Array.isArray(parsed) && parsed.length > 0) {
        suggestions = parsed.map(s => String(s).trim()).filter(Boolean).slice(0, 3);
      }
    } catch (e) {
      const lines = suggestionsMatch[1].split('\n').map(l => l.replace(/^[-*0-9.)]+\s*/, '').trim()).filter(Boolean);
      if (lines.length > 0) suggestions = lines.slice(0, 3);
    }
  }

  // 2. Dynamic contextual fallback based on content and Super Student mode
  if (suggestions.length === 0) {
    const isStudent = (await NativeStorage.get('marvo.super_student_mode')) === 'true' || localStorage.getItem('marvo.studentMode') === 'true';
    const lower = (String(aiText) + ' ' + (userPrompt || '')).toLowerCase();

    if (isStudent) {
      if (lower.includes('formula') || lower.includes('equation') || lower.includes('deriv') || lower.includes('calculate')) {
        suggestions = ['Show step-by-step derivation', 'CHSE exam numerical problem', 'Explain variables & SI units'];
      } else if (lower.includes('physics') || lower.includes('optics') || lower.includes('electric') || lower.includes('current') || lower.includes('wave') || lower.includes('magnetic')) {
        suggestions = ['CHSE 12th Physics numerical', 'State laws & definitions', 'Important 3-mark question'];
      } else if (lower.includes('chemistry') || lower.includes('reaction') || lower.includes('organic') || lower.includes('compound') || lower.includes('acid')) {
        suggestions = ['Write reaction mechanism', 'IUPAC name & conditions', 'Board exam conversion'];
      } else if (lower.includes('biology') || lower.includes('cell') || lower.includes('genetics') || lower.includes('dna') || lower.includes('plant') || lower.includes('human')) {
        suggestions = ['Key points for 5-mark answer', 'Differentiate key terms', 'Provide labeled diagram steps'];
      } else if (lower.includes('math') || lower.includes('calculus') || lower.includes('integral') || lower.includes('derivative') || lower.includes('matrix')) {
        suggestions = ['Solve with full step-by-step LaTeX', 'Alternative solving method', 'Try a similar CHSE question'];
      } else {
        suggestions = ['Explain according to CHSE syllabus', 'Give exam-oriented example', 'Show formula breakdown'];
      }
    } else {
      if (lower.includes('```') || lower.includes('def ') || lower.includes('function') || lower.includes('const ') || lower.includes('class ') || lower.includes('code')) {
        suggestions = ['Explain this code step-by-step', 'Add comments & optimize logic', 'Show practical usage example'];
      } else if (lower.includes('result') || lower.includes('equation') || lower.includes('calculation') || lower.includes('$$') || lower.includes('math')) {
        suggestions = ['Show step-by-step calculation', 'Explain the underlying formula', 'Try another problem'];
      } else if (lower.includes('tl;dr') || lower.includes('key takeaways') || lower.includes('summary')) {
        suggestions = ['Explain in more detail', 'Give real-world applications', 'What are the next steps?'];
      } else if (lower.includes('writing tools') || lower.includes('proofread') || lower.includes('rewritten')) {
        suggestions = ['Make it more formal', 'Make it concise', 'Translate to Hindi / Odia'];
      } else {
        suggestions = ['Tell me more', 'Explain simply', 'Give an example'];
      }
    }
  }

  chipsContainer.innerHTML = '';
  suggestions.slice(0, 3).forEach(chipText => {
    const chipBtn = document.createElement('button');
    chipBtn.type = 'button';
    chipBtn.className = 'smart-reply-chip';
    chipBtn.innerHTML = `
      <svg viewBox="0 0 24 24" width="12" height="12"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" fill="currentColor"/></svg>
      <span>${escapeHtml(chipText)}</span>
    `;
    chipBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      container.classList.add('hidden');
      if (DOM.msgInput) DOM.msgInput.value = chipText;
      if (!isBusy) {
        sendMessage(chipText);
      }
    });
    chipsContainer.appendChild(chipBtn);
  });

  if (suggestions.length > 0) {
    container.classList.remove('hidden');
  }
}

// Step 32: KaTeX Mathematical & Textbook Science Typography Renderer
function renderFormattedAiResponse(rawText) {
  if (!rawText || typeof rawText !== 'string') return '';
  if (window.MathRenderer && typeof window.MathRenderer.renderFormattedText === 'function') {
    return window.MathRenderer.renderFormattedText(rawText);
  }

  const mathEnabled = localStorage.getItem('marvo.math.katex_enabled') !== 'false';
  if (!mathEnabled) {
    return escapeHtml(rawText).replace(/\n/g, '<br>');
  }

  let text = rawText;
  const codeBlocks = [];
  const mathBlocks = [];
  const mathInlines = [];

  // 1. Stash fenced code blocks (```lang ... ```)
  text = text.replace(/```([a-zA-Z0-9_-]*)\n([\s\S]*?)```/g, (match, lang, code) => {
    const idx = codeBlocks.length;
    codeBlocks.push(`<pre><code class="language-${lang || 'plaintext'}">${escapeHtml(code.trim())}</code></pre>`);
    return `%%CODEBLOCK_${idx}%%`;
  });

  // 2. Stash inline code (`code`)
  text = text.replace(/`([^`\n]+)`/g, (match, code) => {
    const idx = codeBlocks.length;
    codeBlocks.push(`<code>${escapeHtml(code)}</code>`);
    return `%%CODEBLOCK_${idx}%%`;
  });

  // 3. Stash block LaTeX: $$...$$ or \[...\]
  text = text.replace(/(?:\$\$([\s\S]*?)\$\$|\\\[([\s\S]*?)\\\])/g, (match, tex1, tex2) => {
    const tex = (tex1 || tex2 || '').trim();
    const idx = mathBlocks.length;
    let rendered = '';
    if (window.katex && typeof window.katex.renderToString === 'function') {
      try {
        rendered = window.katex.renderToString(tex, { displayMode: true, throwOnError: false });
      } catch (err) {
        rendered = `<div class="math-fallback-block">$$${escapeHtml(tex)}$$</div>`;
      }
    } else {
      rendered = `<div class="math-fallback-block">$$${escapeHtml(tex)}$$</div>`;
    }
    mathBlocks.push(rendered);
    return `\n%%MATHBLOCK_${idx}%%\n`;
  });

  // 4. Stash inline LaTeX: $...$ or \(...\)
  text = text.replace(/(?:\$([^\$\n\r]+?)\$|\\\(([\s\S]*?)\\\))/g, (match, tex1, tex2) => {
    const tex = (tex1 || tex2 || '').trim();
    // Exclude plain currency amounts like $50, $10.99
    if (!tex || /^\d+(?:[.,]\d+)?$/.test(tex)) {
      return match;
    }
    const idx = mathInlines.length;
    let rendered = '';
    if (window.katex && typeof window.katex.renderToString === 'function') {
      try {
        rendered = window.katex.renderToString(tex, { displayMode: false, throwOnError: false });
      } catch (err) {
        rendered = `<span class="math-fallback-inline">$${escapeHtml(tex)}$</span>`;
      }
    } else {
      rendered = `<span class="math-fallback-inline">$${escapeHtml(tex)}$</span>`;
    }
    mathInlines.push(rendered);
    return `%%MATHINLINE_${idx}%%`;
  });

  // 5. Escape HTML in surrounding text to prevent XSS
  text = escapeHtml(text);

  // 6. Safe Markdown Typography
  text = text.replace(/^###\s+(.+)$/gm, '<h4>$1</h4>');
  text = text.replace(/^##\s+(.+)$/gm, '<h3>$1</h3>');
  text = text.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  text = text.replace(/\*([^*]+)\*/g, '<em>$1</em>');
  text = text.replace(/^\s*[-*]\s+(.+)$/gm, '<li>$1</li>');
  text = text.replace(/\n\n+/g, '<br><br>');
  text = text.replace(/\n/g, '<br>');

  // Step 33 Apple Intelligence: Smart Actionable Item Extraction
  text = text.replace(/\b(?:(?:at\s+)?\d{1,2}(?::\d{2})?\s*(?:am|pm)|tomorrow(?:\s+morning|\s+evening|\s+night)?|today|yesterday|(?:on\s+)?(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\s+\d{1,2}(?:st|nd|rd|th)?|\b(?:remind me to|task:|reminder:)\s+[a-zA-Z0-9\s]{4,28})\b/gi, (match) => {
    return `<span class="apple-action-chip"><span class="chip-icon">⚡</span>${match}</span>`;
  });

  // 7. Unpack math placeholders
  text = text.replace(/%%MATHBLOCK_(\d+)%%/g, (match, idx) => mathBlocks[parseInt(idx, 10)] || '');
  text = text.replace(/%%MATHINLINE_(\d+)%%/g, (match, idx) => mathInlines[parseInt(idx, 10)] || '');

  // 8. Unpack code blocks
  text = text.replace(/%%CODEBLOCK_(\d+)%%/g, (match, idx) => codeBlocks[parseInt(idx, 10)] || '');

  return text;
}

function addMessage(text, sender, attachments = []) {
  if (sender === 'system' || sender === 'developer') return null;
  if (sender === 'user') {
    const el = document.createElement('div');
    el.className = 'msg msg-user';

    if (attachments && attachments.length > 0) {
      const attachWrap = document.createElement('div');
      attachWrap.className = 'msg-user-attachments';
      attachments.forEach(att => {
        if (att.isImage) {
          const img = document.createElement('img');
          img.src = att.dataUrl;
          img.className = 'user-attached-thumb';
          img.style.cssText = 'max-width:180px;max-height:140px;border-radius:10px;margin-bottom:6px;display:block;';
          attachWrap.appendChild(img);
        } else {
          const doc = document.createElement('div');
          doc.style.cssText = 'font-size:11px;opacity:0.85;margin-bottom:4px;display:flex;align-items:center;gap:4px;';
          doc.innerHTML = `<span>📎</span><strong>${escapeHtml(att.name)}</strong> (${att.size})`;
          attachWrap.appendChild(doc);
        }
      });
      el.appendChild(attachWrap);
    }

    if (text && text.trim()) {
      const textSpan = document.createElement('div');
      textSpan.textContent = text;
      el.appendChild(textSpan);
    }
    DOM.chatMessages.appendChild(el);
    scrollToBottom();
    return el;
  }

  // Check if this message is an Image Generation result
  if (typeof text === 'string' && text.startsWith('__IMAGE_GEN__:')) {
    const parts = text.split(':');
    const promptText = decodeURIComponent(parts[1] || 'Generated image');
    const imageUrl   = decodeURIComponent(parts.slice(2).join(':') || '');

    const wrapper = document.createElement('div');
    wrapper.className = 'msg-ai-wrapper';
    const card = renderImageMessageCard(promptText, imageUrl);
    wrapper.appendChild(card);
    DOM.chatMessages.appendChild(wrapper);
    scrollToBottom();
    return wrapper;
  }

  // AI Message with ChatGPT-style Action Bar
  const wrapper = document.createElement('div');
  wrapper.className = 'msg-ai-wrapper';

  // Step 29: Strip raw Apple Intelligence XML tags before displaying to user
  const sanitizedText = stripAppleXmlTags(text);

  const bubble = document.createElement('div');
  bubble.className = 'msg msg-ai';
  // Step 32: Textbook Science KaTeX Math & Markdown Rendering
  bubble.innerHTML = renderFormattedAiResponse(sanitizedText);
  wrapper.appendChild(bubble);

  const actionBar = document.createElement('div');
  actionBar.className = 'msg-action-bar';

  // Speaker Button
  const speakerBtn = document.createElement('button');
  speakerBtn.className = 'msg-action-btn action-speaker';
  speakerBtn.setAttribute('aria-label', 'Read aloud');
  speakerBtn.innerHTML = `<svg viewBox="0 0 24 24" width="14" height="14"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" fill="currentColor"/><path d="M15.54 8.46a5 5 0 0 1 0 7.07M19.07 4.93a10 10 0 0 1 0 14.14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>`;
  speakerBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    playSpeech(sanitizedText, speakerBtn);
  });
  actionBar.appendChild(speakerBtn);

  // Copy Button
  const copyBtn = document.createElement('button');
  copyBtn.className = 'msg-action-btn action-copy';
  copyBtn.setAttribute('aria-label', 'Copy response');
  copyBtn.innerHTML = `<svg viewBox="0 0 24 24" width="14" height="14"><rect x="9" y="9" width="13" height="13" rx="2" fill="none" stroke="currentColor" stroke-width="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" fill="none" stroke="currentColor" stroke-width="2"/></svg>`;
  copyBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    copyToClipboard(sanitizedText, copyBtn);
  });
  actionBar.appendChild(copyBtn);

  // Regenerate Button
  const retryBtn = document.createElement('button');
  retryBtn.className = 'msg-action-btn action-retry';
  retryBtn.setAttribute('aria-label', 'Regenerate response');
  retryBtn.innerHTML = `<svg viewBox="0 0 24 24" width="14" height="14"><polyline points="1 4 1 10 7 10" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>`;
  retryBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    if (lastUserMessage && !isBusy) sendMessage(lastUserMessage);
  });
  actionBar.appendChild(retryBtn);

  wrapper.appendChild(actionBar);

  DOM.chatMessages.appendChild(wrapper);
  scrollToBottom();
  return wrapper;
}

function showLoading() {
  const el = document.createElement('div');
  el.className = 'msg-ai-wrapper';
  el.innerHTML = `
    <div class="msg msg-ai" style="display:flex;align-items:center;gap:6px;padding:12px 18px;">
      <span style="width:7px;height:7px;border-radius:50%;background:var(--accent);animation:sound-wave 1s infinite alternate;"></span>
      <span style="width:7px;height:7px;border-radius:50%;background:var(--accent);animation:sound-wave 1s infinite alternate 0.2s;"></span>
      <span style="width:7px;height:7px;border-radius:50%;background:var(--accent);animation:sound-wave 1s infinite alternate 0.4s;"></span>
    </div>
  `;
  DOM.chatMessages.appendChild(el);
  scrollToBottom();
  return el;
}

function setStopButtonState(active) {
  if (!DOM.btnSend) return;
  if (active) {
    DOM.btnSend.classList.add('btn-stop');
    DOM.btnSend.setAttribute('aria-label', 'Stop generation');
    DOM.btnSend.setAttribute('title', 'Stop generation');
    DOM.btnSend.innerHTML = `<svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><rect x="5" y="5" width="14" height="14" rx="2"/></svg>`;
    DOM.btnSend.disabled = false;
  } else {
    DOM.btnSend.classList.remove('btn-stop');
    DOM.btnSend.setAttribute('aria-label', 'Send Message');
    DOM.btnSend.setAttribute('title', 'Send Message');
    DOM.btnSend.innerHTML = `<svg viewBox="0 0 24 24" width="18" height="18"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z" fill="currentColor"/></svg>`;
    DOM.btnSend.disabled = false;
  }
}

function abortCurrentChat() {
  if (currentChatAbortController) {
    try { currentChatAbortController.abort(); } catch (e) {}
    currentChatAbortController = null;
  }
  if (currentAudio) {
    try { currentAudio.pause(); } catch (e) {}
    currentAudio = null;
  }
  if (typeof isVoiceRecording !== 'undefined' && isVoiceRecording) {
    stopVoiceRecording(true);
  }
  setEyeExpression('state-idle');
  setStopButtonState(false);
  isBusy = false;
  showToast('Generation stopped');
}

/* ═══════════════════════════════════════════════════════════════════
   CONTEXT-AWARE BACKEND COMMUNICATION (Time, Project, & Agents Pipeline)
   ═══════════════════════════════════════════════════════════════════ */
async function sendMessage(userText) {
  const cleanInput = (userText || '').trim();
  const currentAttachments = [...attachedFiles];
  if (!cleanInput && currentAttachments.length === 0) return;
  if (isBusy) return;

  // Step 31: Hide smart reply chips on sending message
  const smartContainer = document.getElementById('smartReplyContainer');
  if (smartContainer) smartContainer.classList.add('hidden');

  lastUserMessage = cleanInput;
  isBusy = true;
  const requestSessionId = currentSessionId;
  const requestVersion   = sessionVersion;

  activateChatMode();
  addMessage(cleanInput, 'user', currentAttachments);
  await saveLocalMessage(requestSessionId, 'user', cleanInput || `[${currentAttachments.length} Attachment(s)]`);
  await saveLocalSession(requestSessionId, cleanInput || `[${currentAttachments.length} Attachment(s)]`);
  updateHistorySidebar(cleanInput || 'Attachment Query', requestSessionId);

  DOM.msgInput.value = '';
  attachedFiles = [];
  renderAttachmentShelf();
  currentChatAbortController = new AbortController();
  setStopButtonState(true);

  const dots = showLoading();
  const isImageRequest = parseImageGenerationPrompt(cleanInput);
  const activeModeName = getActiveModeName();
  const isHighQualityMode = activeModeName === 'Pro';

  if (isImageRequest) {
    const loadingLabel = isHighQualityMode
      ? 'Orchestrating high-quality generation with SDXL 4K...'
      : 'Generating instant visual with Pollinations...';

    dots.innerHTML = `
      <div class="msg-image-loading-card">
        <div class="msg-image-shimmer-preview">
          <div class="image-shimmer-inner">
            <span class="image-loading-spinner"></span>
            <span class="image-loading-label">${loadingLabel}</span>
            <span class="image-loading-prompt">"${escapeHtml(cleanPromptForDisplay(isImageRequest))}"</span>
          </div>
        </div>
      </div>
    `;
    setEyeExpression('state-creative');
  } else {
    if (isHighQualityMode) {
      const loadingMsgEl = dots.querySelector('.msg-ai');
      if (loadingMsgEl) {
        loadingMsgEl.innerHTML = `
          <div style="display:flex;align-items:center;gap:8px;font-size:13px;color:var(--text-secondary);">
            <span style="width:8px;height:8px;border-radius:50%;background:var(--accent);animation:sound-wave 1s infinite alternate;"></span>
            <span>Thinking deeply with Pro model...</span>
          </div>
        `;
      }
    }
    setEyeExpression('state-loading');
  }

  // Step 28 Apple Intelligence Feature 3: Device Action Intents (Local Android Execution)
  if (window.Capacitor?.Plugins?.MarvoNativeBridge?.executeDeviceIntent) {
    try {
      const intentRes = await window.Capacitor.Plugins.MarvoNativeBridge.executeDeviceIntent({ query: cleanInput });
      if (intentRes && intentRes.executed) {
        dots.remove();
        setEyeExpression('state-success');
        const replyMsg = intentRes.message || 'Action executed successfully.';
        addMessage(replyMsg, 'ai');
        await saveLocalMessage(requestSessionId, 'ai', replyMsg);
        updateHistorySidebar(cleanInput, requestSessionId);
        setStopButtonState(false);
        currentChatAbortController = null;
        isBusy = false;
        return;
      }
    } catch (intentErr) {
      console.warn('[DeviceIntent] Check error:', intentErr);
    }
  }

  const deviceTime = new Date().toLocaleString();
  const deviceStateMeta = `[DEVICE_STATE: current_time="${deviceTime}", focused_app="Marvo AI Assistant", response_mode="${activeModeName || 'fast'}"]`;

  // Step 29: Apple Intelligence 10 Directives Injection
  const appleIntelligenceDirectives = `[APPLE_INTELLIGENCE_DIRECTIVES:
1. One Breath: Answer the essential response in <coreResponse>...</coreResponse> (100-250 tokens).
2. The Exhale: Place extended lists, tables, and narrative depth OUTSIDE and AFTER </coreResponse>.
3. Visual Richness: Mark hero items with <image style="hero"> and catalog sets with <imageCollection style="catalog">.
4. Entity Citation: Use <key_entity id="..."> tags for key data entities.
5. Speech Disambiguation: For ambiguous requests, output ask_user_to_pick options.
6. Missing Property Respect: If a fact is not known, explicitly state it is missing; never guess or hallucinate.
7. Compound Request Handling: Decompose compound queries sequentially.
8. Device State Awareness: Be aware of current_time, focused_app, and response_mode without narrating them.
9. Strict Privacy Boundaries: Never narrate source mechanisms or say "Based on your...". State facts directly.
10. Dynamic Tool Routing: Check math_calculation and device_expert needs before core generation.
11. Contextual Follow-up Chips: Conclude your final response with exactly 2 or 3 short follow-up question chips wrapped inside <suggestions>["Question 1", "Question 2", "Question 3"]</suggestions>.]`;

  // Step 30: Long-Term Personal Memory Fact Extraction
  try {
    await MemoryManager.extractAndSaveFacts(cleanInput);
  } catch (memErr) {
    console.warn('[MemoryManager] Fact extraction error:', memErr);
  }

  // Step 30: Invisible Long-Term Memory Prompt Injection
  const memoryPromptBlock = await MemoryManager.getPromptContext();

  // Silently prepend custom instructions if an Anthropic Claude-style Project is active
  let payloadMessage = `${deviceStateMeta}\n${appleIntelligenceDirectives}\n\n${cleanInput}`;
  if (memoryPromptBlock) {
    payloadMessage = `${memoryPromptBlock}\n\n${payloadMessage}`;
  }

  // Step 30: Multimodal Context Frame Capture
  let activeLiveVisionFrame = null;

  if (currentAttachments.length > 0) {
    const attachMeta = currentAttachments.map(f => `[Attached ${f.type || 'file'}: ${f.name} (${f.size})]`).join('\n');
    payloadMessage = `${attachMeta}\n\n${payloadMessage}`;
  }

  // Step 28 Apple Intelligence Feature 1: Smart Rewrite & Proofreading
  const isRewriteRequest = /^(?:\/rewrite|\/proofread|rewrite:|proofread:)\s*/i.test(cleanInput);
  if (isRewriteRequest) {
    const rawToRewrite = cleanInput.replace(/^(?:\/rewrite|\/proofread|rewrite:|proofread:)\s*/i, '').trim();
    payloadMessage = `[Apple Intelligence Writing Tools Instruction: You are Apple Intelligence Writing Tools. Proofread and rewrite the following text for clarity, professional flow, and impeccable grammar. Output ONLY the polished rewritten text without introductory conversational commentary:\n\n${rawToRewrite}]`;
  }

  // Step 28 Apple Intelligence Feature 2: TL;DR Summarizer
  const isExplicitTldr = /^(?:\/tldr|tl;dr:)\s*/i.test(cleanInput);
  const wordCount = cleanInput.trim().split(/\s+/).length;
  if (isExplicitTldr) {
    const rawTldr = cleanInput.replace(/^(?:\/tldr|tl;dr:)\s*/i, '').trim();
    payloadMessage = `[Apple Intelligence Instruction: Provide a structured response starting with a '### 📌 TL;DR Key Takeaways' section containing exactly 3 crisp, informative bullet points summarizing the core essence, followed by the comprehensive detailed breakdown:\n\n${rawTldr}]`;
  } else if (wordCount > 300 && !isImageRequest && !cleanInput.includes('```') && !isRewriteRequest) {
    payloadMessage = `[Apple Intelligence Instruction: The user prompt is extensive (${wordCount} words). Begin your response with a concise '### 📌 TL;DR Key Takeaways' section with 3 high-impact bullet points, followed by the detailed explanation.]\n\n${payloadMessage}`;
  }

  // Step 27 & 28 & 31: Contextual Privacy (Clean Slate) vs Super Student Mode (CHSE Odisha 12th Science)
  const isStudentActive = (await NativeStorage.get('marvo.super_student_mode')) === 'true' || localStorage.getItem('marvo.studentMode') === 'true';
  if (isStudentActive) {
    payloadMessage = `[Academic Directive: The user is a Class 12 Higher Secondary Science student (PCMB) under the CHSE Odisha state board. Act as an expert academic tutor. Provide highly accurate, concise, and syllabus-relevant answers for Physics, Chemistry, Mathematics, and Biology. Decline non-academic banter politely.]\n\n${payloadMessage}`;
  } else if (activeProject && activeProject.instructions && activeProject.instructions.trim()) {
    payloadMessage = `[System Instructions / Persona for Project "${activeProject.name}":\n${activeProject.instructions.trim()}]\n\n[User Local Time: ${deviceTime}]\n\nUser Question: ${payloadMessage}`;
  }

  // Step 27 & Step 30: Rolling 10 message context cache (Respected by Contextual History toggle)
  const isContextEnabled = (await NativeStorage.get('marvo.context.enabled')) !== 'false';
  const recentHistory = isContextEnabled ? (await getLocalMessages(requestSessionId)) : [];
  const contextHistory = (recentHistory || []).slice(-10).map(m => ({
    role: m.role,
    content: m.content
  }));

  // Step 28: Strict Hybrid Router Failsafe - Instant Offline Routing
  if (!navigator.onLine && !isImageRequest) {
    console.info('[HybridRouter] Device is offline. Bypassing network fetch and routing directly to Local GGUF Brain.');
    if (window.Capacitor?.Plugins?.MarvoNativeBridge?.triggerOfflineQuery) {
      setEyeExpression('state-thinking');
      try {
        const offRes = await window.Capacitor.Plugins.MarvoNativeBridge.triggerOfflineQuery({ query: cleanInput });
        dots.remove();
        if (offRes && offRes.response) {
          const offText = offRes.response;
          setEyeExpression('state-speaking');
          addMessage(offText, 'ai');
          await saveLocalMessage(requestSessionId, 'ai', offText);
          updateHistorySidebar(cleanInput, requestSessionId);
          displaySmartReplyChips(offText, cleanInput);
          setStopButtonState(false);
          currentChatAbortController = null;
          isBusy = false;
          return;
        }
      } catch (nativeErr) {
        console.warn('[OfflineBrain] Direct offline invocation error:', nativeErr);
      }
    }
  }

  let chatFetchTimeout = null;
  try {
    chatFetchTimeout = setTimeout(() => {
      if (currentChatAbortController) {
        try { currentChatAbortController.abort('timeout'); } catch (e) {}
      }
    }, 35000);

    let data;
    if (window.TrafficPolice && activeAgent !== 'huggingface' && activeAgent !== 'pollinations') {
      const routed = await window.TrafficPolice.routeChat(payloadMessage, {
        contextHistory: contextHistory,
        imageBase64: activeLiveVisionFrame,
        signal: currentChatAbortController.signal
      });
      data = {
        type: 'text',
        response: routed.response,
        state: routed.state || 'state-speaking',
        provider: routed.provider,
        model: routed.model
      };
    } else {
      const res = await fetch(API_CHAT, {
        method: 'POST',
        signal: currentChatAbortController.signal,
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          message:          payloadMessage,
          mode:             activeModeName,
          thinking_mode:    selectedMode,
          local_time:       deviceTime,
          agent:            isStudentActive ? 'student' : activeAgent,
          session_id:       requestSessionId,
          context_history:  contextHistory,
          is_student_mode:  isStudentActive,
          image_base64:     activeLiveVisionFrame,
          multimodal_image: activeLiveVisionFrame
        }),
      });
      if (!res.ok) throw new Error(`Server responded with ${res.status}`);
      data = await res.json();
    }

    clearTimeout(chatFetchTimeout);
    dots.remove();
    if (requestSessionId !== currentSessionId || requestVersion !== sessionVersion) return;

    // Handle Structured Backend Agent Response (type === 'image' vs 'text')
    if (data.type === 'image' && data.content) {
      const imageContent = data.content;
      const promptUsed = data.prompt || cleanInput;
      const aiState = data.state || 'state-amazed';

      setEyeExpression(aiState);
      const storageContent = `__IMAGE_GEN__:${encodeURIComponent(promptUsed)}:${encodeURIComponent(imageContent)}`;
      addMessage(storageContent, 'ai');
      await saveLocalMessage(requestSessionId, 'ai', storageContent);
      updateHistorySidebar(cleanInput, requestSessionId);
    } else {
      const aiText  = data.response || 'No response received.';
      const aiState = data.state || 'state-speaking';

      setEyeExpression(aiState);
      addMessage(aiText, 'ai');
      await saveLocalMessage(requestSessionId, 'ai', aiText);
      updateHistorySidebar(cleanInput, requestSessionId);
      displaySmartReplyChips(aiText, cleanInput);

      // Trigger contextual eye state
      const detected = detectEyeExpression(cleanInput, aiText);
      if (detected) setEyeExpression(detected);
    }

  } catch (err) {
    if (chatFetchTimeout) clearTimeout(chatFetchTimeout);
    if (requestSessionId !== currentSessionId || requestVersion !== sessionVersion) return;
    dots.remove();

    if (err && err.name === 'AbortError') {
      setEyeExpression('state-idle');
      return;
    }

    // Resilient Hybrid Routing: Try Local Offline GGUF Brain
    let offlineSuccess = false;
    try {
      if (!isImageRequest && window.Capacitor?.Plugins?.MarvoNativeBridge?.triggerOfflineQuery) {
        setEyeExpression('state-thinking');
        const offRes = await window.Capacitor.Plugins.MarvoNativeBridge.triggerOfflineQuery({ query: cleanInput });
        if (offRes && offRes.response) {
          const offText = offRes.response;
          setEyeExpression('state-speaking');
          addMessage(offText, 'ai');
          await saveLocalMessage(requestSessionId, 'ai', offText);
          updateHistorySidebar(cleanInput, requestSessionId);
          displaySmartReplyChips(offText, cleanInput);
          offlineSuccess = true;
        }
      }
    } catch (nativeErr) {
      console.warn('[OfflineBrain] Hybrid fallback error:', nativeErr);
    }

    if (!offlineSuccess) {
      // Resilient client-side fallback if image creation encounters network error
      if (isImageRequest) {
        showToast('Offline visual generator fallback active');
        const seed = Math.floor(Math.random() * 1000000);
        const fallbackUrl = `https://image.pollinations.ai/prompt/${encodeURIComponent(isImageRequest)}?width=1024&height=1024&nologo=true&seed=${seed}`;
        const storageContent = `__IMAGE_GEN__:${encodeURIComponent(isImageRequest)}:${encodeURIComponent(fallbackUrl)}`;
        setEyeExpression('state-amazed');
        addMessage(storageContent, 'ai');
        await saveLocalMessage(requestSessionId, 'ai', storageContent);
        updateHistorySidebar(cleanInput, requestSessionId);
      } else {
        setEyeExpression('state-error');
        const errMsg = "I'm having difficulty connecting right now and the offline brain is not ready yet. Please check your internet connection.";
        addMessage(errMsg, 'ai');
        await saveLocalMessage(requestSessionId, 'ai', errMsg);
        console.error('[Marvo] Chat error:', err);
      }
    }
  } finally {
    setStopButtonState(false);
    currentChatAbortController = null;
    if (!currentAudio && !window.speechSynthesis?.speaking) {
      if (window.setVoiceState) window.setVoiceState('idle', 0);
    }
    if (requestSessionId !== currentSessionId || requestVersion !== sessionVersion) return;
    isBusy = false;
  }
}

/* ═══════════════════════════════════════════════════════════════════
   APPLE INTELLIGENCE / 2026 DYNAMIC ISLAND - ORGANIC LIQUID CONTROLLER
   - Strict Backend Preservation: 100% untouched Gemini/Whisper/Audio routing
   - SVG Gooey Surface Tension Physics & 120fps hardware acceleration
   - Motorola Punch-hole Camera Anchor (top: 32px; left: 50%)
   - Siri Plasma Edge, Rainbow Prism Arc & 3-State Waveform Visualizer
   - Premium Taptic Feedback & Touch/Swipe-up Gestures
   ═══════════════════════════════════════════════════════════════════ */

const SIRI_STATES = {
  idle: {
    colors: ['#8B5CF6', '#3B82F6', '#EC4899', '#22D3EE'],
    glow: 'rgba(139,92,246,.45)',
    glow2: 'rgba(59,130,246,.25)',
    label: 'Ready'
  },
  listening: {
    colors: ['#22D3EE', '#34D399', '#3B82F6', '#8B5CF6'],
    glow: 'rgba(34,211,238,.55)',
    glow2: 'rgba(52,211,153,.28)',
    label: 'Listening…'
  },
  thinking: {
    colors: ['#F472B6', '#F59E0B', '#8B5CF6', '#3B82F6'],
    glow: 'rgba(245,158,11,.55)',
    glow2: 'rgba(244,114,182,.30)',
    label: 'Thinking…'
  },
  speaking: {
    colors: ['#EC4899', '#8B5CF6', '#22D3EE', '#3B82F6'],
    glow: 'rgba(236,72,153,.60)',
    glow2: 'rgba(139,92,246,.32)',
    label: 'Responding…'
  }
};

const HapticFeedback = {
  confirm() {
    try {
      if (window.Capacitor?.Plugins?.Haptics) {
        window.Capacitor.Plugins.Haptics.impact({ style: 'MEDIUM' });
      } else if (navigator.vibrate) {
        navigator.vibrate(25);
      }
    } catch (e) {}
  },
  reject() {
    try {
      if (window.Capacitor?.Plugins?.Haptics) {
        window.Capacitor.Plugins.Haptics.impact({ style: 'LIGHT' });
      } else if (navigator.vibrate) {
        navigator.vibrate(15);
      }
    } catch (e) {}
  },
  tick() {
    try {
      if (window.Capacitor?.Plugins?.Haptics) {
        window.Capacitor.Plugins.Haptics.selectionStart();
      } else if (navigator.vibrate) {
        navigator.vibrate(8);
      }
    } catch (e) {}
  }
};

// Cubic ease for organic, non-linear volume response
const easeInOutCubic = x => (x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2);

class DynamicIslandManager {
  constructor() {
    this.island = document.getElementById('marvo-camera-island');
    this.appRoot = document.getElementById('app-root') || document.querySelector('.main');
    this.statusPill = document.getElementById('islandStatusPill');
    this.responseText = document.getElementById('islandResponseText');
    this.canvas = document.getElementById('islandWaveCanvas');
    this.ctx = this.canvas ? this.canvas.getContext('2d') : null;
    this.state = 'idle'; // 'idle' | 'listening' | 'thinking' | 'speaking'
    this.volume = 0;
    this.smoothVolume = 0;
    this.active = false;
    this.animId = null;
    this.touchStartY = 0;
    this.expandTimer = null;

    this._bindTouchGestures();
    this._bindButtons();
    this._bindVisibility();
  }

  getIsland() {
    if (!this.island) this.island = document.getElementById('marvo-camera-island');
    return this.island;
  }

  getAppRoot() {
    if (!this.appRoot) this.appRoot = document.getElementById('app-root') || document.querySelector('.main');
    return this.appRoot;
  }

  open() {
    const el = this.getIsland();
    const root = this.getAppRoot();
    if (!el) return;

    this.active = true;
    el.classList.add('island-open');
    el.classList.remove('state-idle');

    // Screen push-back depth effect on main OS root container
    if (root) {
      root.classList.add('island-pushed-back');
    }
    document.body.classList.add('island-active');

    // Organic liquid stretch into card
    if (this.expandTimer) clearTimeout(this.expandTimer);
    requestAnimationFrame(() => {
      el.classList.add('expanded', 'active');
    });

    // Taptic confirmation when island expands
    this.expandTimer = setTimeout(() => {
      if (this.active) {
        HapticFeedback.confirm();
      }
    }, 380);

    this.setVoiceState('listening', 0);
    this._startVisualizerLoop();
  }

  close() {
    const el = this.getIsland();
    const root = this.getAppRoot();
    this.active = false;

    if (this.expandTimer) {
      clearTimeout(this.expandTimer);
      this.expandTimer = null;
    }

    if (el) {
      el.classList.remove('expanded', 'active', 'state-listening', 'state-thinking', 'state-speaking');
      el.classList.add('state-idle');
      setTimeout(() => {
        if (!this.active) el.classList.remove('island-open');
      }, 480);
    }

    // Restore root OS container
    if (root) {
      root.classList.remove('island-pushed-back');
    }
    document.body.classList.remove('island-active');

    // Taptic rejection on dismiss
    HapticFeedback.reject();

    if (this.animId) {
      cancelAnimationFrame(this.animId);
      this.animId = null;
    }
    this.state = 'idle';
    this.volume = 0;
    this.smoothVolume = 0;
  }

  setAudioLevel(rms) {
    if (!this.active || this.state !== 'listening') return;
    const raw = Math.min(100, Math.max(0, rms * 340));
    this.smoothVolume += (raw - this.smoothVolume) * 0.35;
    this.volume = Math.round(this.smoothVolume);
  }

  setVoiceState(state, volume = 0) {
    const el = this.getIsland();
    const validStates = ['idle', 'listening', 'thinking', 'speaking'];
    const s = validStates.includes(state) ? state : 'idle';
    const prevState = this.state;
    this.state = s;
    this.volume = Math.max(0, Math.min(100, volume));

    // Tick haptic on transition from thinking to speaking
    if (prevState === 'thinking' && s === 'speaking') {
      HapticFeedback.tick();
    }

    if (el) {
      el.classList.remove('state-idle', 'state-listening', 'state-thinking', 'state-speaking');
      el.classList.add(`state-${s}`);
    }

    const stateLabels = {
      idle: 'Ready',
      listening: 'Listening…',
      thinking: 'Thinking…',
      speaking: 'Responding…'
    };

    if (!this.statusPill) this.statusPill = document.getElementById('islandStatusPill');
    if (this.statusPill) {
      this.statusPill.textContent = stateLabels[s] || 'Marvo';
    }
    const legacyStatus = document.getElementById('voiceStatusText');
    if (legacyStatus) {
      legacyStatus.textContent = stateLabels[s] || 'Marvo';
    }
  }

  setResponseText(text) {
    if (!this.responseText) this.responseText = document.getElementById('islandResponseText');
    if (this.responseText && text) {
      this.responseText.textContent = text;
    }
    const legacyTranscript = document.getElementById('voiceTranscriptText');
    if (legacyTranscript && text) {
      legacyTranscript.textContent = text;
    }
  }

  _bindTouchGestures() {
    const el = this.getIsland();
    if (!el) return;

    el.addEventListener('touchstart', (e) => {
      this.touchStartY = e.touches[0].clientY;
    }, { passive: true });

    el.addEventListener('touchend', (e) => {
      const touchEndY = e.changedTouches[0].clientY;
      const deltaY = touchEndY - this.touchStartY;
      // Swipe-up upwards by > 25px snaps back to camera
      if (deltaY < -25 && this.active) {
        if (typeof closeVoiceDock === 'function') {
          closeVoiceDock();
        } else {
          this.close();
        }
      }
    }, { passive: true });
  }

  _bindButtons() {
    const btnClose = document.getElementById('btnIslandClose');
    if (btnClose) {
      btnClose.onclick = () => {
        if (typeof closeVoiceDock === 'function') closeVoiceDock();
        else this.close();
      };
    }
    const btnCancel = document.getElementById('btnIslandCancel');
    if (btnCancel) {
      btnCancel.onclick = () => {
        if (typeof closeVoiceDock === 'function') closeVoiceDock();
        else this.close();
      };
    }
    const btnPause = document.getElementById('btnIslandPauseResume');
    if (btnPause) {
      btnPause.onclick = () => {
        if (typeof toggleVoicePauseResume === 'function') toggleVoicePauseResume();
      };
    }
    const btnSend = document.getElementById('btnIslandSend');
    if (btnSend) {
      btnSend.onclick = () => {
        if (typeof submitVoiceRecording === 'function') submitVoiceRecording();
      };
    }
  }

  _bindVisibility() {
    document.addEventListener('visibilitychange', () => {
      if (document.hidden) {
        if (this.animId) {
          cancelAnimationFrame(this.animId);
          this.animId = null;
        }
      } else if (this.active) {
        this._startVisualizerLoop();
      }
    });
  }

  _startVisualizerLoop() {
    if (this.animId) cancelAnimationFrame(this.animId);

    const render = () => {
      if (!this.active) return;
      this.animId = requestAnimationFrame(render);

      if (!this.canvas) this.canvas = document.getElementById('islandWaveCanvas');
      if (!this.canvas) return;
      if (!this.ctx) this.ctx = this.canvas.getContext('2d');
      const ctx = this.ctx;
      if (!ctx) return;

      const width = this.canvas.width;
      const height = this.canvas.height;
      ctx.clearRect(0, 0, width, height);

      const time = Date.now() * 0.004;

      // 3-State Plasma Waveform Visualizer
      // iOS Palette: Cyan (#64D2FF), Pink (#FF375F), Violet (#5E5CE6)
      if (this.state === 'thinking') {
        // Thinking: Ambient CSS conic-glow handles spinning aura; waveform stays minimal
        return;
      }

      let baseAmp = 3;
      if (this.state === 'listening') {
        baseAmp = Math.max(4, Math.min(26, (this.volume / 100) * 28));
      } else if (this.state === 'speaking') {
        baseAmp = 12 + Math.sin(time * 3) * 6;
      }

      const waves = [
        { color: 'rgba(100, 210, 255, 0.95)', speed: 1.1, phase: 0, ampMult: 1.0, width: 2.5 },
        { color: 'rgba(255, 55, 95, 0.85)', speed: -1.3, phase: Math.PI / 3, ampMult: 0.75, width: 2.0 },
        { color: 'rgba(94, 92, 230, 0.80)', speed: 0.8, phase: Math.PI / 1.5, ampMult: 0.55, width: 1.8 }
      ];

      waves.forEach(w => {
        ctx.beginPath();
        ctx.lineWidth = w.width;
        ctx.strokeStyle = w.color;
        ctx.shadowColor = w.color;
        ctx.shadowBlur = 6;

        for (let x = 0; x < width; x += 4) {
          const envelope = Math.sin((x / width) * Math.PI);
          const y = height / 2 + Math.sin(x * 0.035 + time * w.speed + w.phase) * baseAmp * w.ampMult * envelope;
          if (x === 0) ctx.moveTo(x, y);
          else ctx.lineTo(x, y);
        }
        ctx.stroke();
      });
      ctx.shadowBlur = 0;
    };

    render();
  }

  // Compatibility aliases
  start() { this.open(); }
  stop() { this.close(); }
  activate() { this.open(); }
  deactivate() { this.close(); }
  destroy() { this.close(); }
}

let dynamicIslandInstance = null;
let siriOrbInstance = null;

function initDynamicIsland() {
  if (!dynamicIslandInstance) {
    dynamicIslandInstance = new DynamicIslandManager();
    siriOrbInstance = dynamicIslandInstance;
    window.dynamicIslandInstance = dynamicIslandInstance;
    window.siriOrbInstance = dynamicIslandInstance;
    window.setVoiceState = (state, volume) => dynamicIslandInstance.setVoiceState(state, volume);
  }
}

function initSiriOrb() {
  initDynamicIsland();
}

let isVoiceRecording = false;
let isVoicePaused = false;
let speechRecognizer = null;
let currentVoiceTranscript = '';
let audioCtx = null;
let micStream = null;
let analyser = null;
let visualizerAnimId = null;

let micRmsSum = 0;
let micRmsCount = 0;

async function initAudioVisualizer() {
  initSiriOrb();
  const canvas = DOM.voiceWaveCanvas;
  const ctx = canvas ? canvas.getContext('2d') : null;

  micRmsSum = 0;
  micRmsCount = 0;

  try {
    if (!audioCtx) {
      audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    }
    if (audioCtx.state === 'suspended') {
      await audioCtx.resume();
    }
    if (!micStream && navigator.mediaDevices?.getUserMedia) {
      micStream = await navigator.mediaDevices.getUserMedia({ audio: true });
    }
    if (micStream && !analyser) {
      analyser = audioCtx.createAnalyser();
      analyser.fftSize = 256;
      const source = audioCtx.createMediaStreamSource(micStream);
      source.connect(analyser);
    }
  } catch (audioErr) {
    console.warn('[Visualizer] AudioContext mic stream error:', audioErr);
  }

  const dataArray = analyser ? new Uint8Array(analyser.frequencyBinCount) : null;

  function renderWave() {
    if (!isVoiceRecording) return;
    visualizerAnimId = requestAnimationFrame(renderWave);

    let dynamicAmp = 14;
    if (analyser && dataArray) {
      analyser.getByteTimeDomainData(dataArray);
      let sumSquares = 0;
      for (let i = 0; i < dataArray.length; i++) {
        const norm = (dataArray[i] - 128) / 128;
        sumSquares += norm * norm;
      }
      const rms = Math.sqrt(sumSquares / dataArray.length);
      micRmsSum += rms;
      micRmsCount++;
      dynamicAmp = Math.max(4, Math.min(32, rms * 80));

      // Directly feed real microphone frequency/volume to Apple Siri Orb!
      // Directly feed real microphone frequency/volume to Dynamic Island!
      if (window.dynamicIslandInstance && isVoiceRecording && !isVoicePaused) {
        window.dynamicIslandInstance.setAudioLevel(rms);
      }
      if (window.setVoiceState && isVoiceRecording) {
        const rawVol = Math.min(100, Math.max(0, rms * 350));
        window.setVoiceState(isVoicePaused ? 'idle' : 'listening', isVoicePaused ? 0 : rawVol);
      }
    }

    if (canvas && ctx && canvas.width > 1) {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      const width = canvas.width;
      const height = canvas.height;
      const time = Date.now() * 0.005;
      const freq = isVoicePaused ? 0.01 : 0.045;
      const amp = isVoicePaused ? 2 : dynamicAmp;

      const waves = [
        { color: 'rgba(0, 240, 255, 0.9)', speed: 1.0, phase: 0, ampMult: 1.0, lineWidth: 2.5 },
        { color: 'rgba(255, 0, 128, 0.75)', speed: -1.3, phase: Math.PI / 3, ampMult: 0.7, lineWidth: 2.0 },
        { color: 'rgba(147, 51, 234, 0.65)', speed: 0.8, phase: Math.PI / 1.5, ampMult: 0.5, lineWidth: 1.8 }
      ];

      waves.forEach(w => {
        ctx.beginPath();
        ctx.lineWidth = w.lineWidth;
        ctx.strokeStyle = w.color;
        ctx.shadowColor = w.color;
        ctx.shadowBlur = isVoicePaused ? 2 : 8;

        for (let x = 0; x < width; x += 3) {
          const envelope = Math.sin((x / width) * Math.PI);
          const y = height / 2 + Math.sin(x * freq + time * w.speed + w.phase) * amp * w.ampMult * envelope;
          if (x === 0) ctx.moveTo(x, y);
          else ctx.lineTo(x, y);
        }
        ctx.stroke();
      });
      ctx.shadowBlur = 0;
    }
  }
  renderWave();
}

function openVoiceDock() {
  // Stop any active TTS audio immediately
  stopSpeech();

  isVoiceRecording = true;
  isVoicePaused = false;
  currentVoiceTranscript = '';
  DOM.voiceTranscriptText.textContent = 'Listening to you...';
  if (DOM.voiceTranscriptText) DOM.voiceTranscriptText.textContent = 'Listening to you...';
  if (DOM.voiceStatusText) DOM.voiceStatusText.textContent = 'Listening...';
  DOM.iconVoicePause.classList.remove('hidden');
  DOM.iconVoiceResume.classList.add('hidden');
  DOM.labelVoicePauseResume.textContent = 'Pause';
  DOM.voiceOverlay.classList.add('show');
  if (DOM.iconVoicePause) DOM.iconVoicePause.classList.remove('hidden');
  if (DOM.iconVoiceResume) DOM.iconVoiceResume.classList.add('hidden');
  if (DOM.labelVoicePauseResume) DOM.labelVoicePauseResume.textContent = 'Pause';
  DOM.btnMic.classList.add('recording');
  setEyeExpression('state-listening');

  initDynamicIsland();
  if (window.dynamicIslandInstance) {
    window.dynamicIslandInstance.open();
  }
  if (window.setVoiceState) {
    window.setVoiceState('listening', 0);
  }

  initAudioVisualizer();
  startSpeechRecognition();
}

let speechSilenceTimer = null;

function resetSpeechSilenceTimer() {
  if (speechSilenceTimer) clearTimeout(speechSilenceTimer);
  speechSilenceTimer = setTimeout(() => {
    if (isVoiceRecording && !isVoicePaused && currentVoiceTranscript.trim()) {
      console.log('[SpeechRec] 2s silence detected. Auto-submitting speech.');
      submitVoiceRecording();
    }
  }, 2000);
}

function closeVoiceDock() {
  isVoiceRecording = false;
  isVoicePaused = false;
  if (speechSilenceTimer) {
    clearTimeout(speechSilenceTimer);
    speechSilenceTimer = null;
  }
  DOM.voiceOverlay.classList.remove('show');
  DOM.btnMic.classList.remove('recording');
  if (visualizerAnimId) cancelAnimationFrame(visualizerAnimId);
  if (speechRecognizer) {
    try { speechRecognizer.stop(); } catch {}
  }
  // Clean up mic audio stream to prevent battery drain
  if (micStream) {
    try { micStream.getTracks().forEach(t => t.stop()); } catch {}
    micStream = null;
    analyser = null;
  }
  if (window.dynamicIslandInstance) {
    window.dynamicIslandInstance.close();
  }
  if (window.setVoiceState) {
    window.setVoiceState('idle', 0);
  }
  if (!isBusy) setEyeExpression('state-idle');
}

function toggleVoicePauseResume() {
  if (!isVoiceRecording) return;
  isVoicePaused = !isVoicePaused;

  if (isVoicePaused) {
    if (speechSilenceTimer) {
      clearTimeout(speechSilenceTimer);
      speechSilenceTimer = null;
    }
    if (DOM.voiceStatusText) DOM.voiceStatusText.textContent = 'Paused';
    DOM.iconVoicePause.classList.add('hidden');
    DOM.iconVoiceResume.classList.remove('hidden');
    DOM.labelVoicePauseResume.textContent = 'Resume';
    setEyeExpression('state-idle');
    if (window.setVoiceState) window.setVoiceState('idle', 0);
    if (speechRecognizer) {
      try { speechRecognizer.stop(); } catch {}
    }
  } else {
    if (DOM.voiceStatusText) DOM.voiceStatusText.textContent = 'Listening...';
    DOM.iconVoicePause.classList.remove('hidden');
    DOM.iconVoiceResume.classList.add('hidden');
    DOM.labelVoicePauseResume.textContent = 'Pause';
    setEyeExpression('state-listening');
    if (window.setVoiceState) window.setVoiceState('listening', 0);
    startSpeechRecognition();
  }
}

function submitVoiceRecording() {
  if (speechSilenceTimer) {
    clearTimeout(speechSilenceTimer);
    speechSilenceTimer = null;
  }

  // Detect whisper if average speech volume was very quiet (< 0.04 RMS)
  if (micRmsCount > 10) {
    const avgRms = micRmsSum / micRmsCount;
    if (avgRms > 0.005 && avgRms < 0.040) {
      isWhisperDetected = true;
      console.log('[WhisperMode] Quiet speech detected, avg RMS:', avgRms.toFixed(4));
    } else {
      isWhisperDetected = false;
    }
  }

  const textToSend = currentVoiceTranscript.trim();
  if (textToSend) {
    // Keep Dynamic Island open and transition: Listening -> Thinking
    isVoiceRecording = false;
    if (visualizerAnimId) cancelAnimationFrame(visualizerAnimId);
    if (speechRecognizer) {
      try { speechRecognizer.stop(); } catch {}
    }
    if (micStream) {
      try { micStream.getTracks().forEach(t => t.stop()); } catch {}
      micStream = null;
      analyser = null;
    }
    DOM.btnMic?.classList.remove('recording');
    setEyeExpression('state-thinking');
    if (window.dynamicIslandInstance) {
      window.dynamicIslandInstance.setVoiceState('thinking', 20);
    } else if (window.setVoiceState) {
      window.setVoiceState('thinking', 20);
    }
    sendMessage(textToSend);
  } else {
    closeVoiceDock();
    setEyeExpression('state-idle');
    if (window.setVoiceState) window.setVoiceState('idle', 0);
    showToast('No speech detected');
  }
}

function startSpeechRecognition() {
  const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SpeechRec) {
    DOM.voiceTranscriptText.textContent = "Voice input isn't supported in this browser. Please type your message.";
    return;
  }

  try {
    if (speechRecognizer) {
      try { speechRecognizer.onend = null; speechRecognizer.stop(); } catch {}
      speechRecognizer = null;
    }

    speechRecognizer = new SpeechRec();
    speechRecognizer.continuous = true;
    speechRecognizer.interimResults = true;
    speechRecognizer.lang = 'en-US';

    // Step 32: Smart Voice Interrupt on Speech Onset
    speechRecognizer.onspeechstart = async () => {
      const interruptEnabled = (await NativeStorage.get('marvo.voice.smart_interrupt')) !== 'false';
      if (interruptEnabled && (currentAudio || ('speechSynthesis' in window && window.speechSynthesis.speaking))) {
        console.log('[SmartInterrupt] Interrupting TTS on speech start');
        stopSpeech();
        setEyeExpression('state-listening');
      }
    };

    speechRecognizer.onresult = (e) => {
      // Step 32: Smart Voice Interrupt on incoming results
      NativeStorage.get('marvo.voice.smart_interrupt').then(val => {
        if (val !== 'false' && (currentAudio || ('speechSynthesis' in window && window.speechSynthesis.speaking))) {
          console.log('[SmartInterrupt] Interrupting TTS on speech result');
          stopSpeech();
          setEyeExpression('state-listening');
        }
      });

      let interim = '';
      for (let i = e.resultIndex; i < e.results.length; ++i) {
        if (e.results[i].isFinal) {
          currentVoiceTranscript += ' ' + e.results[i][0].transcript;
        } else {
          interim += e.results[i][0].transcript;
        }
      }
      const display = (currentVoiceTranscript + ' ' + interim).trim();
      if (display) {
        DOM.voiceTranscriptText.textContent = display;
        resetSpeechSilenceTimer();
      }
    };

    speechRecognizer.onerror = (err) => {
      console.warn('[SpeechRec] Error:', err);
      if (err.error === 'not-allowed') {
        showToast('Microphone access denied');
        closeVoiceDock();
      }
    };

    speechRecognizer.onend = () => {
      if (isVoiceRecording && !isVoicePaused && speechRecognizer) {
        try { speechRecognizer.start(); } catch {}
      }
    };

    speechRecognizer.start();
  } catch (e) {
    console.warn('[SpeechRec] Start error:', e);
  }
}

/* ═══════════════════════════════════════════════════════════════════
   SIDEBAR & CHAT HISTORY MANAGEMENT
   ═══════════════════════════════════════════════════════════════════ */
function openSidebar() {
  DOM.sidebar.classList.add('open');
  DOM.sidebarOverlay.classList.add('show');
}

function closeSidebar() {
  DOM.sidebar.classList.remove('open');
  DOM.sidebarOverlay.classList.remove('show');
  closeAllDropdowns();
}

function highlightActiveSession() {
  DOM.historyList.querySelectorAll('li').forEach(li => {
    li.classList.toggle('active', li.dataset.sid === currentSessionId);
  });
}

function createHistoryItem(text, sessionId) {
  const li = document.createElement('li');
  li.dataset.sid = sessionId;
  if (sessionId === currentSessionId) li.classList.add('active');

  const titleSpan = document.createElement('span');
  titleSpan.className = 'history-title';
  titleSpan.textContent = text.length > 28 ? text.slice(0, 28) + '…' : text;
  titleSpan.title = text;

  const moreBtn = document.createElement('button');
  moreBtn.className = 'history-more-btn';
  moreBtn.setAttribute('aria-label', 'Options');
  moreBtn.innerHTML = `<svg viewBox="0 0 24 24" width="14" height="14"><circle cx="12" cy="5" r="1.8" fill="currentColor"/><circle cx="12" cy="12" r="1.8" fill="currentColor"/><circle cx="12" cy="19" r="1.8" fill="currentColor"/></svg>`;

  moreBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    contextTargetSessionId = sessionId;
    DOM.historyContextMenu.style.top = `${e.clientY + 8}px`;
    DOM.historyContextMenu.style.left = `${Math.min(e.clientX, window.innerWidth - 180)}px`;
    DOM.historyContextMenu.classList.add('show');
  });

  li.addEventListener('click', (e) => {
    if (e.target.closest('.history-more-btn')) return;
    loadSessionHistory(sessionId);
  });

  li.appendChild(titleSpan);
  li.appendChild(moreBtn);
  return li;
}

async function loadSessionHistory(sessionId) {
  currentSessionId = sessionId;
  await persistCurrentSession();
  sessionVersion += 1;
  isBusy = false;
  DOM.btnSend.disabled = false;
  clearChat();
  highlightActiveSession();

  // 1. Instant local load
  const localMsgs = await getLocalMessages(sessionId);
  if (localMsgs && localMsgs.length > 0) {
    localMsgs.forEach(m => addMessage(m.content, m.role === 'user' ? 'user' : 'ai'));
    activateChatMode();
  }

  // 2. Background sync with backend
  try {
    const res = await fetch(`${API_BASE}/api/history/${encodeURIComponent(sessionId)}`);
    if (res.ok) {
      const data = await res.json();
      if (sessionId === currentSessionId && data.messages && data.messages.length > localMsgs.length) {
        clearChat();
        data.messages.forEach(m => addMessage(m.content, m.role === 'user' ? 'user' : 'ai'));
        activateChatMode();
      }
    }
  } catch {}
  closeSidebar();
}

function updateHistorySidebar(text, sessionId = currentSessionId) {
  const existing = DOM.historyList.querySelector(`li[data-sid="${sessionId}"]`);
  if (existing) {
    const title = existing.querySelector('.history-title');
    if (title) {
      title.textContent = text.length > 28 ? text.slice(0, 28) + '…' : text;
      title.title = text;
    }
    DOM.historyList.prepend(existing);
    highlightActiveSession();
    return;
  }
  const li = createHistoryItem(text, sessionId);
  DOM.historyList.prepend(li);
  highlightActiveSession();
}

async function loadHistorySidebar() {
  try {
    const localSessions = await NativeStorage.getJSON(SESSIONS_STORAGE_KEY, []);
    if (Array.isArray(localSessions) && localSessions.length > 0) {
      DOM.historyList.innerHTML = '';
      localSessions.forEach(s => DOM.historyList.appendChild(createHistoryItem(s.title || 'Conversation', s.session_id)));
      highlightActiveSession();
    }
  } catch {}

  try {
    const res = await fetch(API_SESSIONS);
    if (!res.ok) return;
    const remoteSessions = await res.json();
    if (Array.isArray(remoteSessions) && remoteSessions.length > 0) {
      const localSessions = (await NativeStorage.getJSON(SESSIONS_STORAGE_KEY, [])) || [];
      const sessionMap = new Map();
      localSessions.forEach(s => sessionMap.set(s.session_id, s));
      remoteSessions.forEach(s => {
        if (!sessionMap.has(s.session_id)) {
          sessionMap.set(s.session_id, { session_id: s.session_id, title: s.title, updated_at: Date.now() });
        }
      });
      const merged = Array.from(sessionMap.values());
      await NativeStorage.setJSON(SESSIONS_STORAGE_KEY, merged);
      DOM.historyList.innerHTML = '';
      merged.forEach(s => DOM.historyList.appendChild(createHistoryItem(s.title || 'Conversation', s.session_id)));
      highlightActiveSession();
    }
  } catch {}
}

async function restoreCurrentSession() {
  const savedSessionId = await NativeStorage.get(SESSION_STORAGE_KEY);
  if (savedSessionId) currentSessionId = savedSessionId;

  const localMsgs = await getLocalMessages(currentSessionId);
  if (localMsgs && localMsgs.length > 0) {
    localMsgs.forEach(m => addMessage(m.content, m.role === 'user' ? 'user' : 'ai'));
    activateChatMode();
  }
  highlightActiveSession();
}

function clearChat() {
  if (DOM.chatMessages) {
    DOM.chatMessages.innerHTML = '';
  }
  hasInteracted = false;
  DOM.body.classList.remove('chat-active');
  const smartContainer = document.getElementById('smartReplyContainer');
  if (smartContainer) smartContainer.style.display = 'none';
  if (typeof setEyeExpression === 'function') {
    setEyeExpression('state-idle');
  }
}

function newChat() {
  currentSessionId = generateSessionId();
  persistCurrentSession();
  sessionVersion += 1;
  isBusy = false;
  if (DOM.msgInput) DOM.msgInput.value = '';
  attachedFiles = [];
  renderAttachmentShelf();
  if (DOM.btnSend) DOM.btnSend.disabled = false;
  clearChat();
  highlightActiveSession();
  closeSidebar();
  showToast('New Chat started');
}

/* ═══════════════════════════════════════════════════════════════════
   DROPDOWN & CONTEXT MENU MANAGEMENT
   ═══════════════════════════════════════════════════════════════════ */
function closeAllDropdowns() {
  DOM.appDropdown?.classList.remove('show');
  DOM.historyContextMenu?.classList.remove('show');
  DOM.attachMenu?.classList.remove('show');
  DOM.btnAttach?.classList.remove('active');
}

document.addEventListener('click', (e) => {
  if (e.target.closest('#btnNewChat') || e.target.closest('.btn-new-chat')) {
    newChat();
    return;
  }
  if (!e.target.closest('.menu-container') && !e.target.closest('.dropdown-menu') && !e.target.closest('.attach-container')) {
    closeAllDropdowns();
  }
});

/* ═══════════════════════════════════════════════════════════════════
   EVENT BINDINGS
   ═══════════════════════════════════════════════════════════════════ */

// ── Sidebar Triggers ──
DOM.btnHamburger.addEventListener('click', openSidebar);
DOM.btnCloseSidebar.addEventListener('click', closeSidebar);
DOM.sidebarOverlay.addEventListener('click', closeSidebar);
DOM.btnNewChat.addEventListener('click', newChat);
DOM.btnNewProject.addEventListener('click', () => {
  openProjectModal();
});

// ── Claude-Style Project Workspace Events ──
DOM.activeProjectBadge?.addEventListener('click', (e) => {
  if (e.target.closest('#btnExitProject')) return;
  openProjectModal();
});
DOM.btnExitProject?.addEventListener('click', (e) => {
  e.stopPropagation();
  clearActiveProject();
});
DOM.btnCloseProjectModal?.addEventListener('click', closeProjectModal);
DOM.btnCancelProject?.addEventListener('click', closeProjectModal);
DOM.btnSaveProject?.addEventListener('click', saveActiveProject);
DOM.btnClearProject?.addEventListener('click', clearActiveProject);
DOM.projectModal?.addEventListener('click', (e) => {
  if (e.target === DOM.projectModal) closeProjectModal();
});

// ── Creative Studio Image Generator ──
DOM.navImageGen?.addEventListener('click', () => {
  closeSidebar();
  DOM.msgInput.value = 'Draw ';
  DOM.msgInput.focus();
  showToast('Describe what you want to draw! (e.g. "Draw a cyberpunk robot")');
});

// Sidebar menu navigation items
$('#navSearchChat')?.addEventListener('click', () => {
  closeSidebar();
  const query = prompt('Search past chats:');
  if (query && query.trim()) {
    showToast(`Filtering for "${query.trim()}"`);
  }
});
$('#navSpark')?.addEventListener('click', () => {
  closeSidebar();
  showToast('Spark (Beta) neural acceleration active!');
});
function updateStudentModeUI(isActive) {
  const badge = document.getElementById('studentModeBadge');
  if (badge) {
    badge.textContent = isActive ? 'ON' : 'OFF';
    badge.classList.toggle('active', isActive);
  }
}

$('#navStudent')?.addEventListener('click', () => {
  const currentState = localStorage.getItem('marvo.studentMode') === 'true';
  const newState = !currentState;
  localStorage.setItem('marvo.studentMode', newState.toString());
  updateStudentModeUI(newState);
  closeSidebar();
  if (newState) {
    showToast('🎓 Student Mode: CHSE Class 12 Science Tutor Active');
  } else {
    showToast('Student Mode Disabled. Standard Marvo Active');
  }
});
$('#navLibrary')?.addEventListener('click', () => {
  closeSidebar();
  openNotebookModal();
  showToast('Library opened');
});
$('#navNotebooks')?.addEventListener('click', () => {
  closeSidebar();
  openNotebookModal();
  showToast('Notebooks opened');
});

// Creative Studio Placeholders
document.querySelectorAll('.placeholder-item').forEach(btn => {
  btn.addEventListener('click', () => {
    closeSidebar();
    const feat = btn.dataset.feature || 'Feature';
    showToast(`${feat} is coming soon in the next update!`);
  });
});

// ── Top Right 3-Dots Menu ──
DOM.btnAppMenu.addEventListener('click', (e) => {
  e.stopPropagation();
  const isOpen = DOM.appDropdown.classList.contains('show');
  closeAllDropdowns();
  if (!isOpen) DOM.appDropdown.classList.add('show');
});

DOM.btnAddToNotebook.addEventListener('click', openNotebookModal);
$('#navNotebooks')?.addEventListener('click', () => {
  closeSidebar();
  openNotebookModal();
});
DOM.btnSaveChatToNotebook?.addEventListener('click', saveChatToNotebook);

DOM.btnTopRenameChat.addEventListener('click', async () => {
  closeAllDropdowns();
  const newName = prompt('Enter new conversation name:');
  if (newName && newName.trim()) {
    await renameLocalSession(currentSessionId, newName.trim());
    updateHistorySidebar(newName.trim(), currentSessionId);
    showToast('Conversation renamed');
  }
});

DOM.btnTopHelp.addEventListener('click', () => {
  closeAllDropdowns();
  openModal(DOM.helpModal);
});

DOM.btnTopShareChat.addEventListener('click', openShareModal);
DOM.btnSharePDF?.addEventListener('click', handleSharePDF);
DOM.btnShareText?.addEventListener('click', handleShareText);

// Spark Beta Modal
DOM.navSpark?.addEventListener('click', () => {
  closeSidebar();
  openModal(DOM.sparkModal);
});
DOM.btnAcknowledgeSpark?.addEventListener('click', () => {
  closeModal(DOM.sparkModal);
});

DOM.btnTopDeleteChat.addEventListener('click', async () => {
  closeAllDropdowns();
  if (confirm('Delete this conversation?')) {
    await deleteLocalSession(currentSessionId);
    newChat();
    loadHistorySidebar();
    showToast('Conversation deleted');
  }
});

// Universal Close Button Handler for all modals
document.querySelectorAll('.modal-close-btn').forEach(btn => {
  btn.addEventListener('click', (e) => {
    e.stopPropagation();
    const overlay = btn.closest('.settings-modal-overlay');
    if (overlay) closeModal(overlay);
  });
});

// Universal Backdrop Click Handler
document.querySelectorAll('.settings-modal-overlay').forEach(overlay => {
  overlay.addEventListener('click', (e) => {
    if (e.target === overlay) closeModal(overlay);
  });
});

// Universal Escape Key Handler
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') {
    document.querySelectorAll('.settings-modal-overlay.show').forEach(closeModal);
    closeAllDropdowns();
  }
});

// Settings & Theme
DOM.btnSettings?.addEventListener('click', () => openSettingsModal('themes'));
DOM.btnCloseSettings?.addEventListener('click', closeSettingsModal);
DOM.btnThemeDark?.addEventListener('click', () => setTheme('dark'));
DOM.btnThemeLight?.addEventListener('click', () => setTheme('light'));
DOM.btnThemeMenu?.addEventListener('click', () => {
  closeAllDropdowns();
  openSettingsModal('themes');
});
DOM.navDownloads?.addEventListener('click', () => {
  closeSidebar();
  openSettingsModal('downloads');
});

// AI Control Center Open Handlers
DOM.navAiControlCenter?.addEventListener('click', () => {
  closeSidebar();
  if (window.AiControlCenter) {
    window.AiControlCenter.open();
  }
});
DOM.btnOpenAiControlCenterBadge?.addEventListener('click', () => {
  closeSidebar();
  if (window.AiControlCenter) {
    window.AiControlCenter.open();
  }
});
DOM.btnAppAiControlCenter?.addEventListener('click', () => {
  closeAllDropdowns();
  if (window.AiControlCenter) {
    window.AiControlCenter.open();
  }
});
DOM.btnLaunchAiControlCenter?.addEventListener('click', () => {
  closeSettingsModal();
  if (window.AiControlCenter) {
    window.AiControlCenter.open();
  }
});

// Settings Tab Bar Navigation
DOM.settingsTabBar?.addEventListener('click', (e) => {
  const btn = e.target.closest('.settings-tab-btn');
  if (btn && btn.dataset.tab) {
    switchSettingsTab(btn.dataset.tab);
  }
});

// Step 29: Settings Nested Folders ([ 🎭 Characters ], [ 👁️ 3D Eye ], [ 🎮 Playground ])
const folderConfigs = [
  { btnId: 'btnToggleFolderCharacters', contentId: 'folderContentCharacters' },
  { btnId: 'btnToggleFolderEye', contentId: 'folderContentEye' },
  { btnId: 'btnToggleFolderPlayground', contentId: 'folderContentPlayground' },
  // Backward compatibility
  { btnId: 'btnToggleAppearanceSubfolder', contentId: 'appearanceSubfolderContent' }
];

folderConfigs.forEach(({ btnId, contentId }) => {
  const btn = document.getElementById(btnId);
  const content = document.getElementById(contentId);
  btn?.addEventListener('click', () => {
    const isOpen = content?.classList.toggle('open');
    btn.classList.toggle('expanded', isOpen);
  });
});

// Step 29: [ ♟️ Chess ] Local Engine Placeholder Action
document.getElementById('btnPreviewChess')?.addEventListener('click', () => {
  showToast('♟️ Local Chess Lab placeholder active. On-device Stockfish NNUE engine arriving in Step 30!');
});

// Theme Choice Cards (3D Eye - Dark/Light)
document.querySelectorAll('.theme-choice-card').forEach(card => {
  card.addEventListener('click', () => {
    const th = card.dataset.theme;
    if (th) {
      setTheme(th);
      showToast(`${th.charAt(0).toUpperCase() + th.slice(1)} 3D Eye Theme active`);
    }
  });
});

// Character Theme Cards (10+ Reactive Avatars)
document.querySelectorAll('.character-card-btn').forEach(card => {
  card.addEventListener('click', () => {
    const themeKey = card.dataset.theme || (card.dataset.char ? `char-${card.dataset.char}` : null);
    if (themeKey) {
      setTheme(themeKey);
      const charName = card.querySelector('.char-card-name')?.textContent || themeKey;
      showToast(`${charName} character theme active`);
    }
  });
});

// Fast vs Pro Tier Switcher
DOM.tierSwitcher?.addEventListener('click', (e) => {
  const btn = e.target.closest('.tier-btn');
  if (btn && (btn.dataset.tier || btn.dataset.mode)) {
    const tier = btn.dataset.tier || btn.dataset.mode;
    setMode(tier);
    showToast(`${tier.charAt(0).toUpperCase() + tier.slice(1)} mode active`);
  }
});

// Open Unified Storage Folder
DOM.btnOpenStorageFolder?.addEventListener('click', async () => {
  try {
    if (window.Capacitor?.Plugins?.MarvoNativeBridge?.openUnifiedDownloadsFolder) {
      await window.Capacitor.Plugins.MarvoNativeBridge.openUnifiedDownloadsFolder();
    } else {
      showToast('Path: /storage/emulated/0/Download/Marvo/');
    }
  } catch (err) {
    showToast('Path: /storage/emulated/0/Download/Marvo/');
  }
});

// Capability Chips ("Fast", "Thinking", "Pro") backward compatibility
DOM.modeSelector?.addEventListener('click', (e) => {
  const btn = e.target.closest('.chip-btn');
  if (btn && btn.dataset.mode) {
    setMode(btn.dataset.mode);
    const modeTitle = btn.querySelector('span:last-child')?.textContent?.trim() || 'Mode';
    showToast(`${modeTitle} mode activated`);
  }
});

// Send Message / Stop Button
DOM.btnSend.addEventListener('click', () => {
  if (isBusy) {
    abortCurrentChat();
  } else {
    sendMessage(DOM.msgInput.value);
  }
});
DOM.msgInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    if (!isBusy) {
      sendMessage(DOM.msgInput.value);
    }
  }
});

// Gemini-style Input Dock & Dynamic Typing States
DOM.msgInput.addEventListener('focus', () => {
  const dock = document.querySelector('.input-glass');
  if (dock) dock.classList.add('active-typing');
  if (!isBusy) setEyeExpression('state-typing');
});
DOM.msgInput.addEventListener('input', () => {
  const dock = document.querySelector('.input-glass');
  if (DOM.msgInput.value.trim().length > 0) {
    if (dock) dock.classList.add('active-typing');
    if (!isBusy) setEyeExpression('state-typing');
  } else {
    if (dock) dock.classList.remove('active-typing');
    if (!isBusy) setEyeExpression('state-idle');
  }
});
DOM.msgInput.addEventListener('blur', () => {
  const dock = document.querySelector('.input-glass');
  if (dock && DOM.msgInput.value.trim().length === 0) {
    dock.classList.remove('active-typing');
  }
  if (!isBusy && currentCharacterState === 'state-typing') {
    setEyeExpression('state-idle');
  }
});

// Plus / Attachments Menu
DOM.btnAttach?.addEventListener('click', (e) => {
  e.stopPropagation();
  const isOpen = DOM.attachMenu?.classList.contains('show');
  closeAllDropdowns();
  if (!isOpen) {
    DOM.attachMenu?.classList.add('show');
    DOM.btnAttach?.classList.add('active');
  }
});

DOM.btnAttachImageGen?.addEventListener('click', (e) => {
  e.stopPropagation();
  closeAllDropdowns();
  const currentVal = DOM.msgInput.value.trim();
  if (!currentVal || !currentVal.toLowerCase().startsWith('generate an image of')) {
    DOM.msgInput.value = 'Generate an image of ';
  }
  DOM.msgInput.focus();
  const len = DOM.msgInput.value.length;
  DOM.msgInput.setSelectionRange(len, len);
  showToast('Describe what you want Marvo to draw and press Send!');
});

DOM.attachMenu?.addEventListener('click', (e) => {
  if (e.target.closest('#btnAttachImageGen')) return;
  const item = e.target.closest('.attach-item');
  if (item) {
    closeAllDropdowns();
    const type = item.dataset.type;
    if (type === 'PDF') {
      if (DOM.fileUploadInput) {
        DOM.fileUploadInput.accept = 'application/pdf';
        DOM.fileUploadInput.click();
      }
    } else if (type === 'Photo') {
      if (DOM.fileUploadInput) {
        DOM.fileUploadInput.accept = 'image/*';
        DOM.fileUploadInput.click();
      }
    } else {
      showToast(`${item.dataset.type} upload selected!`);
    }
  }
});

DOM.btnScreenShare?.addEventListener('click', () => {
  showToast('Live screen analysis coming soon!');
});

// Bottom-Docked Voice UI Controls
DOM.btnMic.addEventListener('click', () => {
  if (isVoiceRecording) closeVoiceDock();
  else openVoiceDock();
});

DOM.btnVoiceClose.addEventListener('click', closeVoiceDock);
DOM.btnVoiceCancel.addEventListener('click', closeVoiceDock);
DOM.btnVoicePauseResume.addEventListener('click', toggleVoicePauseResume);
DOM.btnVoiceSend.addEventListener('click', submitVoiceRecording);

// History Context Menu (Sidebar)
DOM.btnRenameChat.addEventListener('click', async () => {
  if (!contextTargetSessionId) return;
  const newName = prompt('Enter new conversation name:');
  if (newName && newName.trim()) {
    await renameLocalSession(contextTargetSessionId, newName.trim());
    updateHistorySidebar(newName.trim(), contextTargetSessionId);
    showToast('Renamed');
  }
  closeAllDropdowns();
});

DOM.btnDeleteChat.addEventListener('click', async () => {
  if (!contextTargetSessionId) return;
  if (confirm('Delete this conversation?')) {
    const target = contextTargetSessionId;
    await deleteLocalSession(target);
    const li = DOM.historyList.querySelector(`li[data-sid="${target}"]`);
    if (li) li.remove();
    if (target === currentSessionId) newChat();
    showToast('Deleted');
  }
  closeAllDropdowns();
});

// Global Escape Key
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') {
    if (isVoiceRecording) closeVoiceDock();
    closeSidebar();
    closeAllDropdowns();
    closeSettingsModal();
  }
});

/* ═══════════════════════════════════════════════════════════════════
   INTERACTIVE 3D EYES (Parallax & Touch/Hover Physics)
   ═══════════════════════════════════════════════════════════════════ */
function initInteractiveEyes() {
  const face = DOM.face;
  const avatarZone = DOM.avatarZone;
  if (!face || !avatarZone) return;

  let targetX = 0;
  let targetY = 0;
  let currentX = 0;
  let currentY = 0;
  let animId = null;

  function updateParallax() {
    currentX += (targetX - currentX) * 0.15;
    currentY += (targetY - currentY) * 0.15;

    document.documentElement.style.setProperty('--eye-look-x', `${currentX.toFixed(2)}px`);
    document.documentElement.style.setProperty('--eye-look-y', `${currentY.toFixed(2)}px`);

    if (Math.abs(targetX - currentX) > 0.08 || Math.abs(targetY - currentY) > 0.08) {
      animId = requestAnimationFrame(updateParallax);
    } else {
      animId = null;
    }
  }

  function handlePointerMove(clientX, clientY) {
    const rect = face.getBoundingClientRect();
    const centerX = rect.left + rect.width / 2;
    const centerY = rect.top + rect.height / 2;

    const maxOffset = 12;
    const normX = Math.max(-1, Math.min(1, (clientX - centerX) / (window.innerWidth / 2)));
    const normY = Math.max(-1, Math.min(1, (clientY - centerY) / (window.innerHeight / 2)));

    targetX = normX * maxOffset;
    targetY = normY * maxOffset;

    if (!animId) {
      animId = requestAnimationFrame(updateParallax);
    }
  }

  window.addEventListener('mousemove', (e) => {
    handlePointerMove(e.clientX, e.clientY);
  }, { passive: true });

  window.addEventListener('touchmove', (e) => {
    if (e.touches && e.touches.length > 0) {
      handlePointerMove(e.touches[0].clientX, e.touches[0].clientY);
    }
  }, { passive: true });

  window.addEventListener('mouseleave', () => {
    targetX = 0;
    targetY = 0;
    if (!animId) animId = requestAnimationFrame(updateParallax);
  });
  window.addEventListener('touchend', () => {
    targetX = 0;
    targetY = 0;
    if (!animId) animId = requestAnimationFrame(updateParallax);
  });

  // Depth simulation (Z-axis scale on hover/touch)
  avatarZone.addEventListener('mouseenter', () => face.classList.add('eye-focused'));
  avatarZone.addEventListener('mouseleave', () => face.classList.remove('eye-focused'));
  avatarZone.addEventListener('touchstart', () => face.classList.add('eye-focused'), { passive: true });
  avatarZone.addEventListener('touchend', () => face.classList.remove('eye-focused'), { passive: true });

  // Poke / Click Reaction
  avatarZone.addEventListener('click', () => {
    const reactions = ['state-surprised', 'state-winking', 'state-laughing'];
    const randomReaction = reactions[Math.floor(Math.random() * reactions.length)];
    const previousLabel = DOM.stateLabel.textContent.toLowerCase();
    const prevClass = 'state-' + previousLabel;

    setEyeExpression(randomReaction);
    face.classList.add('eye-focused');

    setTimeout(() => {
      face.classList.remove('eye-focused');
      if (!isBusy) {
        setEyeExpression(STATES.includes(prevClass) ? prevClass : 'state-idle');
      }
    }, 1400);
  });
}

function initDeviceTilt() {
  if (window.__marvoDeviceTiltInitialized) return;
  if (!('DeviceOrientationEvent' in window)) return;
  window.__marvoDeviceTiltInitialized = true;

  const EngineClass = window.TiltEngine || (typeof TiltEngine !== 'undefined' ? TiltEngine : null);
  if (!EngineClass) return;

  const root = document.documentElement;

  const engine = new EngineClass({
    maxTilt: 25,
    smoothing: 10,
    fps: 60,
    idleMs: 1800,
    onUpdate: ({ pitch, roll, tiltX, tiltY }) => {
      // 60FPS Hardware-Accelerated Gyroscope tracking exclusively for Marvo's Main Eyes
      // Universal coverage across ALL eye colors, themes, and expressions
      const tx = tiltX !== undefined ? tiltX : Math.max(-12, Math.min(12, roll * 0.48));
      const ty = tiltY !== undefined ? tiltY : Math.max(-12, Math.min(12, pitch * 0.48));

      root.style.setProperty('--eye-tilt-x', `${tx.toFixed(2)}px`);
      root.style.setProperty('--eye-tilt-y', `${ty.toFixed(2)}px`);
      root.style.setProperty('--tilt-x', `${tx.toFixed(2)}px`);
      root.style.setProperty('--tilt-y', `${ty.toFixed(2)}px`);
    }
  });

  window.mainTiltEngine = engine;
  const isEnabled = localStorage.getItem('marvo.vision.gyro_parallax') !== 'false';
  if (isEnabled) {
    engine.start();
  }
}

/* ═══════════════════════════════════════════════════════════════════
   CUSTOMIZATION SETTINGS (Eye Color Swatches & Expression Playground)
   ═══════════════════════════════════════════════════════════════════ */
const EYE_COLORS = {
  'neon-blue': {
    primary: '#00f0ff',
    secondary: '#0072ff',
    glow: 'rgba(0, 240, 255, 0.4)',
    accentGlow: 'rgba(0, 240, 255, 0.16)'
  },
  'cyber-red': {
    primary: '#ff0055',
    secondary: '#d90429',
    glow: 'rgba(255, 0, 85, 0.45)',
    accentGlow: 'rgba(255, 0, 85, 0.18)'
  },
  'emerald': {
    primary: '#00ff88',
    secondary: '#00aa55',
    glow: 'rgba(0, 255, 136, 0.45)',
    accentGlow: 'rgba(0, 255, 136, 0.18)'
  },
  'amethyst': {
    primary: '#b5179e',
    secondary: '#7209b7',
    glow: 'rgba(181, 23, 158, 0.45)',
    accentGlow: 'rgba(181, 23, 158, 0.18)'
  },
  'white': {
    primary: '#ffffff',
    secondary: '#94a3b8',
    glow: 'rgba(255, 255, 255, 0.35)',
    accentGlow: 'rgba(255, 255, 255, 0.15)'
  }
};

const EYE_COLOR_STORAGE_KEY = 'marvo.eyeColor';

async function setEyeColor(colorKey, save = true) {
  const config = EYE_COLORS[colorKey] || EYE_COLORS['neon-blue'];
  const root = document.documentElement;

  root.style.setProperty('--primary-eye-color', config.primary);
  root.style.setProperty('--secondary-eye-color', config.secondary);
  root.style.setProperty('--eye-glow', config.glow);
  root.style.setProperty('--accent', config.primary);
  root.style.setProperty('--accent-glow', config.glow);
  root.style.setProperty('--accent-glow-subtle', config.accentGlow);

  document.querySelectorAll('.color-swatch-btn').forEach(btn => {
    btn.classList.toggle('active', btn.dataset.color === colorKey);
  });

  if (save) {
    await NativeStorage.set(EYE_COLOR_STORAGE_KEY, colorKey);
  }
}

async function initEyeCustomization() {
  const savedColor = (await NativeStorage.get(EYE_COLOR_STORAGE_KEY)) || 'neon-blue';
  await setEyeColor(savedColor, false);

  // Swatch buttons
  document.querySelectorAll('.color-swatch-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
      const colorKey = btn.dataset.color;
      await setEyeColor(colorKey, true);
      showToast(`Eye theme set to ${btn.title}`);
    });
  });

  // Expression playground
  document.querySelectorAll('.btn-exp-play').forEach(btn => {
    btn.addEventListener('click', () => {
      const exp = btn.dataset.exp;
      document.querySelectorAll('.btn-exp-play').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      setEyeExpression(exp);
      showToast(`Previewing ${exp.replace('state-', '')}`);
    });
  });
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

/* ═══════════════════════════════════════════════════════════════════
   MODAL CONTROLLERS & CLOSERS
   ═══════════════════════════════════════════════════════════════════ */
function openModal(modalEl) {
  if (!modalEl) return;
  modalEl.classList.add('show');
}

function closeModal(modalEl) {
  if (!modalEl) return;
  modalEl.classList.remove('show');
}

/* ═══════════════════════════════════════════════════════════════════
   CREATIVE AGENTS PERSONAS CONTROLLER
   ═══════════════════════════════════════════════════════════════════ */
function setMode(modeKey) {
  let raw = (modeKey || 'Fast').trim();
  let normalized = raw.toLowerCase();
  let canonicalMode = 'Fast';

  if (normalized === 'fast') {
    canonicalMode = 'Fast';
  } else if (normalized === 'thinking' || normalized === 'medium') {
    canonicalMode = 'Thinking';
  } else if (normalized.includes('pro') || normalized === 'high') {
    canonicalMode = 'Pro Thinking';
  }

  selectedMode = canonicalMode;
  NativeStorage.set(MODE_STORAGE_KEY, selectedMode);

  if (window.TrafficPolice && typeof window.TrafficPolice.setMode === 'function') {
    window.TrafficPolice.setMode(canonicalMode);
  }

  if (DOM.tierSwitcher) {
    DOM.tierSwitcher.classList.remove('tier-fast', 'tier-thinking', 'tier-pro');
    if (canonicalMode === 'Fast') DOM.tierSwitcher.classList.add('tier-fast');
    else if (canonicalMode === 'Thinking') DOM.tierSwitcher.classList.add('tier-thinking');
    else if (canonicalMode === 'Pro Thinking') DOM.tierSwitcher.classList.add('tier-pro');
  }

  if (DOM.btnTierFast) {
    const active = canonicalMode === 'Fast';
    DOM.btnTierFast.classList.toggle('active', active);
    DOM.btnTierFast.setAttribute('aria-checked', active ? 'true' : 'false');
  }
  if (DOM.btnTierThinking) {
    const active = canonicalMode === 'Thinking';
    DOM.btnTierThinking.classList.toggle('active', active);
    DOM.btnTierThinking.setAttribute('aria-checked', active ? 'true' : 'false');
  }
  if (DOM.btnTierPro) {
    const active = canonicalMode === 'Pro Thinking';
    DOM.btnTierPro.classList.toggle('active', active);
    DOM.btnTierPro.setAttribute('aria-checked', active ? 'true' : 'false');
  }

  if (DOM.modeSelector) {
    DOM.modeSelector.querySelectorAll('.chip-btn').forEach(b => {
      const bMode = (b.dataset.mode || '').toLowerCase();
      if (bMode === normalized || b.dataset.mode === canonicalMode) {
        b.classList.add('active');
      } else {
        b.classList.remove('active');
      }
    });
  }
}

function setActiveAgent(agentName, persist = true) {
  const key = (agentName || 'gemini').toLowerCase();
  activeAgent = key;
  if (DOM.sidebarAgentsGrid) {
    DOM.sidebarAgentsGrid.querySelectorAll('.agent-chip-btn').forEach(btn => {
      if (btn.dataset.agent === key) {
        btn.classList.add('active');
      } else {
        btn.classList.remove('active');
      }
    });
  }
  if (DOM.agentsActiveIndicator) {
    DOM.agentsActiveIndicator.textContent = AGENT_DISPLAY_NAMES[key] || (key.charAt(0).toUpperCase() + key.slice(1));
  }
  const placeholder = AGENT_PLACEHOLDERS[key] || 'Ask Marvo anything...';
  if (DOM.msgInput) {
    DOM.msgInput.placeholder = placeholder;
  }
  if (persist) {
    NativeStorage.set(AGENT_STORAGE_KEY, key);
  }
}

async function initAgents() {
  const saved = (await NativeStorage.get(AGENT_STORAGE_KEY)) || 'gemini';
  setActiveAgent(saved, false);

  // Accordion Toggle for De-clutter UX (Collapsed by default)
  if (DOM.btnToggleAgents && DOM.agentsAccordionContent) {
    DOM.btnToggleAgents.addEventListener('click', (e) => {
      e.stopPropagation();
      const isCollapsed = DOM.agentsAccordionContent.classList.contains('collapsed');
      if (isCollapsed) {
        DOM.agentsAccordionContent.classList.remove('collapsed');
        DOM.btnToggleAgents.setAttribute('aria-expanded', 'true');
      } else {
        DOM.agentsAccordionContent.classList.add('collapsed');
        DOM.btnToggleAgents.setAttribute('aria-expanded', 'false');
      }
    });
  }

  if (DOM.sidebarAgentsGrid) {
    DOM.sidebarAgentsGrid.addEventListener('click', (e) => {
      const btn = e.target.closest('.agent-chip-btn');
      if (btn && btn.dataset.agent) {
        const agent = btn.dataset.agent;
        setActiveAgent(agent, true);
        const name = btn.querySelector('.agent-chip-name')?.textContent || agent;

        // Synchronize mode toggle with the authentic agent
        if (agent === 'huggingface') {
          setMode('high');
        } else if (agent === 'pollinations') {
          setMode('fast');
        } else if (agent === 'claude') {
          setMode('medium');
        }

        showToast(`${name} persona active`);
        closeSidebar();
      }
    });
  }
}

/* ═══════════════════════════════════════════════════════════════════
   MODE SELECTION CONTROLLER
   ═══════════════════════════════════════════════════════════════════ */
async function initModeSelection() {
  const saved = (await NativeStorage.get(MODE_STORAGE_KEY)) || 'medium';
  setMode(saved);
}

/* ═══════════════════════════════════════════════════════════════════
   NOTEBOOK & SHARE MODAL ACTIONS
   ═══════════════════════════════════════════════════════════════════ */
async function loadQuickNote() {
  if (!DOM.notebookQuickNote) return;
  const note = (await NativeStorage.get(QUICK_NOTE_STORAGE_KEY)) || '';
  DOM.notebookQuickNote.value = note;
  if (DOM.notebookNoteStatus) DOM.notebookNoteStatus.textContent = note ? 'Auto-saved locally' : '';
}

let quickNoteDebounceTimer = null;
function setupQuickNoteListeners() {
  if (DOM.notebookQuickNote) {
    DOM.notebookQuickNote.addEventListener('input', () => {
      if (DOM.notebookNoteStatus) DOM.notebookNoteStatus.textContent = 'Saving...';
      clearTimeout(quickNoteDebounceTimer);
      quickNoteDebounceTimer = setTimeout(async () => {
        await NativeStorage.set(QUICK_NOTE_STORAGE_KEY, DOM.notebookQuickNote.value);
        if (DOM.notebookNoteStatus) DOM.notebookNoteStatus.textContent = 'Auto-saved locally';
      }, 400);
    });
  }

  if (DOM.btnClearQuickNote) {
    DOM.btnClearQuickNote.addEventListener('click', async () => {
      if (DOM.notebookQuickNote) DOM.notebookQuickNote.value = '';
      await NativeStorage.set(QUICK_NOTE_STORAGE_KEY, '');
      if (DOM.notebookNoteStatus) DOM.notebookNoteStatus.textContent = 'Cleared';
      showToast('Scratchpad cleared');
    });
  }
}

async function openNotebookModal() {
  closeAllDropdowns();
  openModal(DOM.notebookModal);
  await loadQuickNote();
  await renderNotebookNotes();
}

async function renderNotebookNotes() {
  if (!DOM.notebookNotesList) return;
  const raw = await NativeStorage.get(NOTEBOOK_STORAGE_KEY);
  let notes = [];
  try {
    notes = raw ? JSON.parse(raw) : [];
  } catch {
    notes = [];
  }

  if (!notes || notes.length === 0) {
    DOM.notebookNotesList.innerHTML = `
      <p style="text-align:center;color:var(--text-dim);font-size:13px;padding:24px 0;">
        No saved notes yet. Click the button above to save the current conversation!
      </p>
    `;
    return;
  }

  DOM.notebookNotesList.innerHTML = notes.map((n) => `
    <div class="notebook-note-item">
      <div class="notebook-note-header">
        <span class="notebook-note-title">${escapeHtml(n.title || 'Saved Note')}</span>
        <span class="notebook-note-date">${escapeHtml(n.date || '')}</span>
      </div>
      <div class="notebook-note-snippet">${escapeHtml(n.content || '')}</div>
    </div>
  `).join('');
}

async function saveChatToNotebook() {
  const msgs = await getLocalMessages(currentSessionId);
  if (!msgs || msgs.length === 0) {
    showToast('No messages to save in this conversation');
    return;
  }

  const raw = await NativeStorage.get(NOTEBOOK_STORAGE_KEY);
  let notes = [];
  try { notes = raw ? JSON.parse(raw) : []; } catch { notes = []; }

  const sessions = await getLocalSessions();
  const curr = sessions.find(s => s.id === currentSessionId);
  const title = curr?.title || 'Chat Note';
  const transcript = msgs.map(m => `${m.role.toUpperCase()}: ${m.content}`).join('\n\n');

  notes.unshift({
    title,
    date: new Date().toLocaleDateString(),
    content: transcript.length > 300 ? transcript.slice(0, 300) + '...' : transcript,
    fullText: transcript,
  });

  if (notes.length > 50) notes = notes.slice(0, 50);
  await NativeStorage.set(NOTEBOOK_STORAGE_KEY, JSON.stringify(notes));
  await renderNotebookNotes();
  showToast('Saved to Notebook!');
}

async function openShareModal() {
  closeAllDropdowns();
  const msgs = await getLocalMessages(currentSessionId);
  const sessions = await getLocalSessions();
  const curr = sessions.find(s => s.id === currentSessionId);
  const title = curr?.title || 'Current Chat';
  if (DOM.shareSummaryText) {
    DOM.shareSummaryText.textContent = `Share "${title}" (${msgs.length} message${msgs.length === 1 ? '' : 's'}).`;
  }
  openModal(DOM.shareModal);
}

async function handleSharePDF() {
  closeModal(DOM.shareModal);
  window.print();
}

async function handleShareText() {
  closeModal(DOM.shareModal);
  const msgs = await getLocalMessages(currentSessionId);
  if (!msgs || msgs.length === 0) {
    showToast('No messages in this chat to share');
    return;
  }
  const transcript = msgs.map(m => `${m.role.toUpperCase()}: ${stripAppleXmlTags(m.content)}`).join('\n\n');

  // Step 29: Wire up @capacitor/share to trigger native Android share sheet
  try {
    if (window.Capacitor?.Plugins?.Share?.share) {
      await window.Capacitor.Plugins.Share.share({
        title: 'Marvo AI Conversation',
        text: transcript,
        dialogTitle: 'Share Marvo Conversation'
      });
      return;
    }
  } catch (capErr) {
    console.warn('[Share] Capacitor Share failed, falling back to Web Share:', capErr);
  }

  if (navigator.share) {
    try {
      await navigator.share({
        title: 'Marvo AI Conversation',
        text: transcript,
      });
      return;
    } catch {}
  }

  copyToClipboard(transcript, null);
  showToast('Conversation copied to clipboard!');
}

/* ═══════════════════════════════════════════════════════════════════
   APP INITIALIZATION
   ═══════════════════════════════════════════════════════════════════ */
async function initApp() {
  await initStatusBar();
  await initTheme();
  await initEyeCustomization();
  await initAgents();
  await initModeSelection();
  initMultimodalAttachments();
  await renderDownloadsHistory();
  setupQuickNoteListeners();
  initInteractiveEyes();
  initDeviceTilt();
  await initVoiceSelection();
  initDownloadCardControls();
  // Step 31: Super Student Mode (CHSE Odisha 12th Science) initialization
  const studentVal = await NativeStorage.get('marvo.super_student_mode');
  const isStudentActive = studentVal === 'true' || localStorage.getItem('marvo.studentMode') === 'true';
  updateStudentModeUI(isStudentActive);

  $('#navStudent')?.addEventListener('click', async () => {
    const current = (await NativeStorage.get('marvo.super_student_mode')) === 'true' || localStorage.getItem('marvo.studentMode') === 'true';
    const next = !current;
    await NativeStorage.set('marvo.super_student_mode', String(next));
    localStorage.setItem('marvo.studentMode', String(next));
    updateStudentModeUI(next);
    const toggleInput = document.querySelector('input[data-key="marvo.super_student_mode"]');
    if (toggleInput) toggleInput.checked = next;
    showToast(next ? '🎓 Super Student Mode (CHSE 12th Science) Enabled' : 'Super Student Mode Disabled');
  });

  setEyeExpression('state-idle');
  await persistCurrentSession();
  await loadHistorySidebar();
  await restoreCurrentSession();

  // Step 30: AI Control Room & Long-Term Memory Initializations
  await renderAiControlRoom();
  initMemoryModalListeners();

  // Traffic Police & Model Quick-Switch Initialization
  await initTrafficPoliceAndModelUI();
}

/* ================================================================
   PHASE 1: AGGRESSIVE PAGE VISIBILITY & BATTERY MANAGEMENT (0% CPU)
   ================================================================ */
const ActiveIntervalRegistry = {
  trackedIntervals: new Set(),
  track(id) {
    if (id) this.trackedIntervals.add(id);
    return id;
  },
  untrack(id) {
    if (id) this.trackedIntervals.delete(id);
  },
  clearAll() {
    this.trackedIntervals.forEach(id => {
      try { clearInterval(id); } catch (e) {}
    });
    this.trackedIntervals.clear();
  }
};

// Wrap native setInterval/clearInterval to guarantee tracking
const _nativeSetInterval = window.setInterval;
const _nativeClearInterval = window.clearInterval;
window.setInterval = function(fn, ms, ...args) {
  const id = _nativeSetInterval(fn, ms, ...args);
  ActiveIntervalRegistry.track(id);
  return id;
};
window.clearInterval = function(id) {
  ActiveIntervalRegistry.untrack(id);
  return _nativeClearInterval(id);
};

document.addEventListener('visibilitychange', () => {
  if (document.hidden) {
    console.log('[BatterySaver] App minimized -> Enforcing 0% CPU: Halting intervals, animations, gyroscope & media streams');
    document.body.classList.add('page-paused', 'app-background-paused');

    // 1. Immediately halt all active intervals
    ActiveIntervalRegistry.clearAll();
    if (typeof stopDownloadPolling === 'function') {
      stopDownloadPolling();
    }
    if (typeof downloadPollTimer !== 'undefined' && downloadPollTimer) {
      clearInterval(downloadPollTimer);
      downloadPollTimer = null;
    }

    // 2. Kill WebRTC Mic audio stream and voice visualizer
    if (typeof isVoiceRecording !== 'undefined' && isVoiceRecording) {
      closeVoiceDock();
    }

    // 3. Halt Siri Plasma animations
    const siriPlasma = document.getElementById('siriPlasmaContainer');
    if (siriPlasma) siriPlasma.classList.add('siri-paused');
    // 3. Halt Dynamic Island animations & speech
    if (window.dynamicIslandInstance) window.dynamicIslandInstance.close();
    if (window.siriOrbInstance) window.siriOrbInstance.stop();

    // 4. Halt 3D Gyroscope sensor
    if (window.mainTiltEngine) {
      window.mainTiltEngine.stop();
    }
    if (typeof GyroParallaxManager !== 'undefined') {
      GyroParallaxManager.stop();
    }

    // 5. Cancel canvas visualizer animation frames
    if (typeof visualizerAnimId !== 'undefined' && visualizerAnimId) {
      cancelAnimationFrame(visualizerAnimId);
      visualizerAnimId = null;
    }

  } else {
    console.log('[BatterySaver] App restored to foreground');
    document.body.classList.remove('page-paused', 'app-background-paused');

    // Resume Gyroscope if enabled in user settings
    const gyroEnabled = localStorage.getItem('marvo.vision.gyro_parallax') !== 'false';
    if (gyroEnabled && window.mainTiltEngine) {
      window.mainTiltEngine.start();
    }
    if (typeof GyroParallaxManager !== 'undefined') {
      if (gyroEnabled) GyroParallaxManager.start();
    }

    if (DOM.settingsModal?.classList.contains('show')) {
      const activeTab = document.querySelector('.settings-tab-btn.active')?.dataset.tab;
      if (activeTab === 'brain') {
        startDownloadPolling();
      }
    }
  }
});

/* ================================================================
   DYNAMIC MODEL SELECTION & TRAFFIC POLICE FRONTEND UI
   ================================================================ */
async function initTrafficPoliceAndModelUI() {
  if (window.TrafficPolice) {
    await window.TrafficPolice.init();

    // Populate Sidebar API Keys
    const cfgGroq = $('#cfgGroqKey');
    const cfgGemini = $('#cfgGeminiKey');
    const cfgOpenRouter = $('#cfgOpenRouterKey');

    if (cfgGroq) cfgGroq.value = window.TrafficPolice.state.keys.groq || '';
    if (cfgGemini) cfgGemini.value = window.TrafficPolice.state.keys.gemini || '';
    if (cfgOpenRouter) cfgOpenRouter.value = window.TrafficPolice.state.keys.openrouter || '';

    // Password visibility toggle buttons
    document.querySelectorAll('.btn-toggle-key').forEach(btn => {
      btn.onclick = () => {
        const targetId = btn.getAttribute('data-target');
        const input = document.getElementById(targetId);
        if (input) {
          input.type = input.type === 'password' ? 'text' : 'password';
        }
      };
    });

    // Save API Keys Button
    const btnSaveKeys = $('#btnSaveApiConfig');
    if (btnSaveKeys) {
      btnSaveKeys.onclick = () => {
        const groq = cfgGroq?.value || '';
        const gemini = cfgGemini?.value || '';
        const openrouter = cfgOpenRouter?.value || '';

        window.TrafficPolice.setKeys({ groq, gemini, openrouter });
        showToast('API Keys saved successfully!');
      };
    }

    // Chatbar Model Quick-Switch Dropdown
    const btnModelPill = $('#btnModelPill');
    const modelPillMenu = $('#modelPillMenu');
    const modelPillLabel = $('#modelPillLabel');
    const subagentPillWrap = $('#subagentPillWrap');
    const btnSubagentPill = $('#btnSubagentPill');
    const subagentPillMenu = $('#subagentPillMenu');
    const subagentPillLabel = $('#subagentPillLabel');

    const labels = {
      groq: 'Groq (Fast & Free)',
      gemini: 'Gemini (Google Native)',
      openrouter: 'OpenRouter (Multi-Agent)',
      local: 'Local LLM (Offline Engine)'
    };

    const updatePillLabels = () => {
      const provider = window.TrafficPolice.state.currentProvider;
      if (modelPillLabel) modelPillLabel.textContent = labels[provider] || provider;

      // Update active options in menu
      document.querySelectorAll('.model-pill-opt').forEach(opt => {
        opt.classList.toggle('active', opt.dataset.provider === provider);
      });

      // Show or hide secondary OpenRouter subagent dropdown
      if (subagentPillWrap) {
        subagentPillWrap.classList.toggle('hidden', provider !== 'openrouter');
      }

      if (subagentPillLabel) {
        const currentModel = window.TrafficPolice.state.openRouterModel;
        const subNames = {
          'anthropic/claude-3.5-sonnet': 'Claude 3.5 Sonnet',
          'openai/gpt-4o': 'GPT-4o',
          'meta-llama/llama-3.1-405b-instruct': 'Llama 3.1 405B',
          'meta-llama/llama-3.3-70b-instruct': 'Llama 3.3 70B',
          'deepseek/deepseek-r1': 'DeepSeek R1',
          'google/gemini-2.0-flash-001': 'Gemini 2.0 Flash'
        };
        subagentPillLabel.textContent = subNames[currentModel] || currentModel.split('/').pop();
      }

      document.querySelectorAll('.subagent-pill-opt').forEach(opt => {
        opt.classList.toggle('active', opt.dataset.model === window.TrafficPolice.state.openRouterModel);
      });
    };

    updatePillLabels();

    if (btnModelPill) {
      btnModelPill.onclick = (e) => {
        e.stopPropagation();
        modelPillMenu?.classList.toggle('show');
        subagentPillMenu?.classList.remove('show');
      };
    }

    document.querySelectorAll('.model-pill-opt').forEach(opt => {
      opt.onclick = () => {
        const provider = opt.dataset.provider;
        window.TrafficPolice.setProvider(provider);
        modelPillMenu?.classList.remove('show');
        updatePillLabels();
        showToast(`Model switched to ${labels[provider] || provider}`);
      };
    });

    if (btnSubagentPill) {
      btnSubagentPill.onclick = (e) => {
        e.stopPropagation();
        subagentPillMenu?.classList.toggle('show');
        modelPillMenu?.classList.remove('show');
      };
    }

    document.querySelectorAll('.subagent-pill-opt').forEach(opt => {
      opt.onclick = () => {
        const model = opt.dataset.model;
        window.TrafficPolice.setOpenRouterModel(model);
        subagentPillMenu?.classList.remove('show');
        updatePillLabels();
        showToast(`Sub-agent: ${opt.querySelector('.sub-model-name')?.textContent || model}`);
      };
    });

    document.addEventListener('click', () => {
      modelPillMenu?.classList.remove('show');
      subagentPillMenu?.classList.remove('show');
    });

    window.TrafficPolice.onStateChange(() => {
      updatePillLabels();
    });
  }

  // Study Mode Trigger (Hamburger menu button)
  $('#navStudyMode')?.addEventListener('click', () => {
    // Close sidebar
    DOM.sidebar?.classList.remove('open');
    DOM.sidebarOverlay?.classList.remove('show');
    if (window.StudyModeUI) {
      window.StudyModeUI.enter();
    }
  });

  // AI Control Center Hub Initializer
  if (window.AiControlCenter && typeof window.AiControlCenter.init === 'function') {
    window.AiControlCenter.init();
  }

  // Initialize Apple Siri Fluid Orb
  initSiriOrb();
}

window.openAiControlCenter = function() {
  if (window.AiControlCenter) {
    window.AiControlCenter.open();
  }
};

initApp();

window.marvo = {
  setEyeExpression,
  sendMessage,
  newChat,
  setTheme,
  playSpeech,
  showToast,
  openAiControlCenter: () => window.openAiControlCenter(),
  openProjectModal,
  saveActiveProject,
  clearActiveProject,
  setActiveAgent,
  openNotebookModal,
  openShareModal,
  setVoiceState: (s, v) => window.setVoiceState && window.setVoiceState(s, v),
  get siriOrb() { return window.siriOrbInstance; },
  get activeProject() { return activeProject; },
  get activeAgent() { return activeAgent; },
  get currentVoice() { return currentVoice; },
  get session() { return currentSessionId; },
  get mode() { return selectedMode; },
  STATES,
};
