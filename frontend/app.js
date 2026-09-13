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
  btnAppMenu:         $('#btnAppMenu'),
  appDropdown:        $('#appDropdown'),
  btnAddToNotebook:   $('#btnAddToNotebook'),
  btnTopRenameChat:   $('#btnTopRenameChat'),
  btnTopHelp:         $('#btnTopHelp'),
  btnTopShareChat:    $('#btnTopShareChat'),
  btnTopDeleteChat:   $('#btnTopDeleteChat'),
  btnThemeDark:       $('#btnThemeDark'),
  btnThemeLight:      $('#btnThemeLight'),
  btnSettings:        $('#btnSettings'),
  settingsModal:      $('#settingsModal'),
  btnCloseSettings:   $('#btnCloseSettings'),
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

  // Avatar & 3D Eyes
  avatarZone:         $('#avatarZone'),
  face:               $('#face'),
  stateLabel:         $('#stateLabel'),

  // Chat Area & Input
  chatArea:           $('#chatArea'),
  chatMessages:       $('#chatMessages'),
  btnAttach:          $('#btnAttach'),
  attachMenu:         $('#attachMenu'),
  btnAttachImageGen:  $('#btnAttachImageGen'),
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
];

let currentSessionId = sessionStorage.getItem(SESSION_STORAGE_KEY) || generateSessionId();
let currentVoice     = localStorage.getItem(VOICE_STORAGE_KEY) || 'voice_3';
let sessionVersion   = 0;
let selectedMode     = 'medium'; // 'fast', 'medium' (Thinking), 'high' (Pro)
let activeAgent      = 'gemini'; // 'gemini', 'huggingface', 'pollinations', 'claude'
let isBusy           = false;
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
  const activeBtn = DOM.modeSelector?.querySelector('.chip-btn.active');
  if (activeBtn) {
    const text = activeBtn.querySelector('span:last-child')?.textContent?.trim();
    if (text) return text;
  }
  if (selectedMode === 'fast') return 'Fast';
  if (selectedMode === 'high' || selectedMode === 'Pro') return 'Pro';
  return 'Thinking';
}

/* Eye Expression Trigger */
function setEyeExpression(state) {
  if (!STATES.includes(state)) return;
  STATES.forEach(s => DOM.face.classList.remove(s));
  DOM.face.classList.add(state);
  DOM.stateLabel.textContent = state.replace('state-', '').toUpperCase();
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
  if (theme === 'light') {
    DOM.body.classList.add('theme-light');
  } else {
    DOM.body.classList.remove('theme-light');
  }
  await NativeStorage.set(THEME_STORAGE_KEY, theme);
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
   SETTINGS MODAL & VOICE SELECTION
   ═══════════════════════════════════════════════════════════════════ */
function openSettingsModal() {
  closeAllDropdowns();
  DOM.settingsModal.classList.add('show');
}

function closeSettingsModal() {
  DOM.settingsModal.classList.remove('show');
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
   SPEECH & AUDIO
   ═══════════════════════════════════════════════════════════════════ */
async function playSpeech(text, btnElement = null) {
  if (currentAudio) {
    try {
      currentAudio.pause();
      currentAudio.currentTime = 0;
    } catch {}
    currentAudio = null;
    document.querySelectorAll('.playing-tts').forEach(el => el.classList.remove('playing-tts'));
    DOM.face.classList.remove('speaking-mode');
    setEyeExpression('state-idle');
    return;
  }

  const cleanText = text.replace(/[*_~`#]/g, '').trim();
  if (!cleanText) return;

  if (btnElement) btnElement.classList.add('playing-tts');
  setEyeExpression('state-speaking');
  DOM.face.classList.add('speaking-mode');

  try {
    const res = await fetch(API_SPEAK, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        text: cleanText,
        voice_id: currentVoice,
        session_id: currentSessionId,
      }),
    });

    if (!res.ok) throw new Error(`Status ${res.status}`);
    const data = await res.json();

    if (data && data.audio_base64) {
      const audio = new Audio("data:audio/mp3;base64," + data.audio_base64);
      currentAudio = audio;

      audio.onended = () => {
        if (btnElement) btnElement.classList.remove('playing-tts');
        DOM.face.classList.remove('speaking-mode');
        setEyeExpression('state-idle');
        if (currentAudio === audio) currentAudio = null;
      };
      audio.onerror = () => {
        if (btnElement) btnElement.classList.remove('playing-tts');
        DOM.face.classList.remove('speaking-mode');
        setEyeExpression('state-idle');
        if (currentAudio === audio) currentAudio = null;
      };
      await audio.play();
    } else {
      fallbackWebSpeech(cleanText, btnElement);
    }
  } catch (err) {
    console.warn('[Marvo] Backend TTS failed, fallback:', err);
    fallbackWebSpeech(cleanText, btnElement);
  }
}

function fallbackWebSpeech(text, btnElement) {
  if (!('speechSynthesis' in window)) {
    if (btnElement) btnElement.classList.remove('playing-tts');
    DOM.face.classList.remove('speaking-mode');
    setEyeExpression('state-idle');
    return;
  }
  window.speechSynthesis.cancel();
  const utter = new SpeechSynthesisUtterance(text);
  utter.onend = () => {
    if (btnElement) btnElement.classList.remove('playing-tts');
    DOM.face.classList.remove('speaking-mode');
    setEyeExpression('state-idle');
  };
  utter.onerror = () => {
    if (btnElement) btnElement.classList.remove('playing-tts');
    DOM.face.classList.remove('speaking-mode');
    setEyeExpression('state-idle');
  };
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

function clearChat() {
  DOM.chatMessages.innerHTML = '';
}

/* Instant Image Generation Parsing & Card Rendering */
const IMAGE_TRIGGER_KEYWORDS = [
  'generate', 'image', 'draw', 'photo', 'picture', 'creat', 'create', 'pic', 'paint', 'thumbnail'
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

function parseImageGenerationPrompt(input) {
  if (!input) return null;
  const trimmed = input.trim();
  const lower = trimmed.toLowerCase();

  // Check against the exact trigger list
  const hasTrigger = IMAGE_TRIGGER_KEYWORDS.some(w => lower.includes(w));
  if (!hasTrigger) return null;

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
      showToast('Downloading image...');
      const filename = `marvo-art-${Date.now()}.jpg`;
      if (imageUrl.startsWith('data:')) {
        const a = document.createElement('a');
        a.href = imageUrl;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        showToast('Image saved!');
      } else {
        const resp = await fetch(imageUrl);
        const blob = await resp.blob();
        const blobUrl = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = blobUrl;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        setTimeout(() => URL.revokeObjectURL(blobUrl), 2000);
        showToast('Image downloaded!');
      }
    } catch (err) {
      console.warn('Direct blob fetch failed, opening download link', err);
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

function addMessage(text, sender) {
  if (sender === 'user') {
    const el = document.createElement('div');
    el.className = 'msg msg-user';
    el.textContent = text;
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

  const bubble = document.createElement('div');
  bubble.className = 'msg msg-ai';
  bubble.textContent = text;
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
    playSpeech(text, speakerBtn);
  });
  actionBar.appendChild(speakerBtn);

  // Copy Button
  const copyBtn = document.createElement('button');
  copyBtn.className = 'msg-action-btn action-copy';
  copyBtn.setAttribute('aria-label', 'Copy response');
  copyBtn.innerHTML = `<svg viewBox="0 0 24 24" width="14" height="14"><rect x="9" y="9" width="13" height="13" rx="2" fill="none" stroke="currentColor" stroke-width="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" fill="none" stroke="currentColor" stroke-width="2"/></svg>`;
  copyBtn.addEventListener('click', (e) => {
    e.stopPropagation();
    copyToClipboard(text, copyBtn);
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

/* ═══════════════════════════════════════════════════════════════════
   CONTEXT-AWARE BACKEND COMMUNICATION (Time, Project, & Agents Pipeline)
   ═══════════════════════════════════════════════════════════════════ */
async function sendMessage(userText) {
  if (!userText || !userText.trim() || isBusy) return;
  const cleanInput = userText.trim();
  lastUserMessage = cleanInput;
  isBusy = true;
  const requestSessionId = currentSessionId;
  const requestVersion   = sessionVersion;

  activateChatMode();
  addMessage(cleanInput, 'user');
  await saveLocalMessage(requestSessionId, 'user', cleanInput);
  await saveLocalSession(requestSessionId, cleanInput);
  updateHistorySidebar(cleanInput, requestSessionId);

  DOM.msgInput.value = '';
  DOM.btnSend.disabled = true;

  const dots = showLoading();
  const isImageRequest = parseImageGenerationPrompt(cleanInput);
  const activeModeName = getActiveModeName();
  const isHighQualityMode = activeModeName === 'Thinking' || activeModeName === 'Pro';

  if (isImageRequest) {
    const loadingLabel = isHighQualityMode
      ? 'Orchestrating high-quality generation... This may take up to 60 seconds.'
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
            <span>Thinking deeply with ${activeModeName} reasoning...</span>
          </div>
        `;
      }
    }
    setEyeExpression('state-loading');
  }

  const deviceTime = new Date().toLocaleString();

  // Silently prepend custom instructions if an Anthropic Claude-style Project is active
  let payloadMessage = cleanInput;
  if (activeProject && activeProject.instructions && activeProject.instructions.trim()) {
    payloadMessage = `[System Instructions / Persona for Project "${activeProject.name}":\n${activeProject.instructions.trim()}]\n\n[User Local Time: ${deviceTime}]\n\nUser Question: ${cleanInput}`;
  }

  try {
    const res = await fetch(API_CHAT, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        message:       payloadMessage,
        mode:          activeModeName,
        thinking_mode: selectedMode,
        local_time:    deviceTime,
        agent:         activeAgent,
        session_id:    requestSessionId,
      }),
    });

    dots.remove();
    if (!res.ok) throw new Error(`Server responded with ${res.status}`);

    const data = await res.json();
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

      // Trigger contextual eye state
      const detected = detectEyeExpression(cleanInput, aiText);
      if (detected) setEyeExpression(detected);
    }

  } catch (err) {
    if (requestSessionId !== currentSessionId || requestVersion !== sessionVersion) return;
    dots.remove();

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
      const errMsg = "I couldn't reach my cloud brain right now. Please check your internet connection.";
      addMessage(errMsg, 'ai');
      await saveLocalMessage(requestSessionId, 'ai', errMsg);
      console.error('[Marvo] Chat error:', err);
    }
  } finally {
    if (requestSessionId !== currentSessionId || requestVersion !== sessionVersion) return;
    isBusy = false;
    DOM.btnSend.disabled = false;
    DOM.msgInput.focus();
  }
}

/* ═══════════════════════════════════════════════════════════════════
   BOTTOM-DOCKED GEMINI-STYLE VOICE INTERACTION & VISUALIZER
   ═══════════════════════════════════════════════════════════════════ */
let isVoiceRecording = false;
let isVoicePaused = false;
let speechRecognizer = null;
let currentVoiceTranscript = '';
let audioCtx = null;
let micStream = null;
let analyser = null;
let visualizerAnimId = null;

function initAudioVisualizer() {
  const canvas = DOM.voiceWaveCanvas;
  if (!canvas) return;
  const ctx = canvas.getContext('2d');

  function renderWave() {
    if (!isVoiceRecording) return;
    visualizerAnimId = requestAnimationFrame(renderWave);

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    const width = canvas.width;
    const height = canvas.height;
    const time = Date.now() * 0.005;

    ctx.lineWidth = 2;
    ctx.strokeStyle = '#00f0ff';
    ctx.beginPath();

    const freq = isVoicePaused ? 0.01 : 0.05;
    const amp = isVoicePaused ? 2 : 14;

    for (let x = 0; x < width; x += 3) {
      const y = height / 2 + Math.sin(x * freq + time) * amp * Math.sin(x / width * Math.PI);
      if (x === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();
  }
  renderWave();
}

function openVoiceDock() {
  isVoiceRecording = true;
  isVoicePaused = false;
  currentVoiceTranscript = '';
  DOM.voiceTranscriptText.textContent = 'Listening to you...';
  DOM.voiceStatusText.textContent = 'Listening...';
  DOM.iconVoicePause.classList.remove('hidden');
  DOM.iconVoiceResume.classList.add('hidden');
  DOM.labelVoicePauseResume.textContent = 'Pause';
  DOM.voiceOverlay.classList.add('show');
  DOM.btnMic.classList.add('recording');
  setEyeExpression('state-listening');

  initAudioVisualizer();
  startSpeechRecognition();
}

function closeVoiceDock() {
  isVoiceRecording = false;
  isVoicePaused = false;
  DOM.voiceOverlay.classList.remove('show');
  DOM.btnMic.classList.remove('recording');
  if (visualizerAnimId) cancelAnimationFrame(visualizerAnimId);
  if (speechRecognizer) {
    try { speechRecognizer.stop(); } catch {}
  }
  if (!isBusy) setEyeExpression('state-idle');
}

function toggleVoicePauseResume() {
  if (!isVoiceRecording) return;
  isVoicePaused = !isVoicePaused;

  if (isVoicePaused) {
    DOM.voiceStatusText.textContent = 'Paused';
    DOM.iconVoicePause.classList.add('hidden');
    DOM.iconVoiceResume.classList.remove('hidden');
    DOM.labelVoicePauseResume.textContent = 'Resume';
    setEyeExpression('state-idle');
    if (speechRecognizer) {
      try { speechRecognizer.stop(); } catch {}
    }
  } else {
    DOM.voiceStatusText.textContent = 'Listening...';
    DOM.iconVoicePause.classList.remove('hidden');
    DOM.iconVoiceResume.classList.add('hidden');
    DOM.labelVoicePauseResume.textContent = 'Pause';
    setEyeExpression('state-listening');
    startSpeechRecognition();
  }
}

function submitVoiceRecording() {
  const textToSend = currentVoiceTranscript.trim();
  closeVoiceDock();
  if (textToSend) {
    sendMessage(textToSend);
  } else {
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
    speechRecognizer = new SpeechRec();
    speechRecognizer.continuous = true;
    speechRecognizer.interimResults = true;
    speechRecognizer.lang = 'en-US';

    speechRecognizer.onresult = (e) => {
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
      }
    };

    speechRecognizer.onerror = (err) => {
      console.warn('[SpeechRec] Error:', err);
    };

    speechRecognizer.onend = () => {
      if (isVoiceRecording && !isVoicePaused) {
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

function newChat() {
  currentSessionId = generateSessionId();
  persistCurrentSession();
  sessionVersion += 1;
  isBusy = false;
  DOM.btnSend.disabled = false;
  clearChat();
  highlightActiveSession();
  closeSidebar();
  DOM.msgInput.focus();
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
$('#navStudent')?.addEventListener('click', () => {
  closeSidebar();
  showToast('Student mode: Step-by-step reasoning enabled');
});
$('#navLibrary')?.addEventListener('click', () => {
  closeSidebar();
  showToast('Library opened');
});
$('#navNotebooks')?.addEventListener('click', () => {
  closeSidebar();
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
DOM.btnSettings.addEventListener('click', openSettingsModal);
DOM.btnCloseSettings.addEventListener('click', closeSettingsModal);
DOM.btnThemeDark.addEventListener('click', () => setTheme('dark'));
DOM.btnThemeLight.addEventListener('click', () => setTheme('light'));

// Capability Chips ("Fast", "Thinking", "Pro")
DOM.modeSelector.addEventListener('click', (e) => {
  const btn = e.target.closest('.chip-btn');
  if (btn && btn.dataset.mode) {
    setMode(btn.dataset.mode);
    const modeTitle = btn.querySelector('span:last-child')?.textContent?.trim() || 'Mode';
    showToast(`${modeTitle} mode activated`);
  }
});

// Send Message
DOM.btnSend.addEventListener('click', () => sendMessage(DOM.msgInput.value));
DOM.msgInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendMessage(DOM.msgInput.value);
  }
});

// Plus / Attachments Menu
DOM.btnAttach.addEventListener('click', (e) => {
  e.stopPropagation();
  const isOpen = DOM.attachMenu.classList.contains('show');
  closeAllDropdowns();
  if (!isOpen) {
    DOM.attachMenu.classList.add('show');
    DOM.btnAttach.classList.add('active');
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

DOM.attachMenu.addEventListener('click', (e) => {
  if (e.target.closest('#btnAttachImageGen')) return;
  const item = e.target.closest('.attach-item');
  if (item) {
    closeAllDropdowns();
    showToast(`${item.dataset.type} upload coming soon!`);
  }
});

DOM.btnScreenShare.addEventListener('click', () => {
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
  let normalized = modeKey;
  if (normalized === 'Fast') normalized = 'fast';
  if (normalized === 'Thinking') normalized = 'medium';
  if (normalized === 'Pro') normalized = 'high';

  selectedMode = normalized;
  NativeStorage.set(MODE_STORAGE_KEY, selectedMode);
  if (DOM.modeSelector) {
    DOM.modeSelector.querySelectorAll('.chip-btn').forEach(b => {
      if (b.dataset.mode === normalized) {
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
        DOM.msgInput.focus();
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
  const transcript = msgs.map(m => `${m.role.toUpperCase()}: ${m.content}`).join('\n\n');

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
  setupQuickNoteListeners();
  initInteractiveEyes();
  await initVoiceSelection();
  await loadActiveProject();
  setEyeExpression('state-idle');
  await persistCurrentSession();
  await loadHistorySidebar();
  await restoreCurrentSession();
  DOM.msgInput.focus();
}

initApp();

window.marvo = {
  setEyeExpression,
  sendMessage,
  newChat,
  setTheme,
  playSpeech,
  showToast,
  openProjectModal,
  saveActiveProject,
  clearActiveProject,
  setActiveAgent,
  openNotebookModal,
  openShareModal,
  get activeProject() { return activeProject; },
  get activeAgent() { return activeAgent; },
  get currentVoice() { return currentVoice; },
  get session() { return currentSessionId; },
  get mode() { return selectedMode; },
  STATES,
};
