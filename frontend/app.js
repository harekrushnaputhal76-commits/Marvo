'use strict';

/* ================================================================
   MARVO — app.js
   Frontend bridge to Flask backend with advanced interactive UI:
   - 5-Character Voice Selection System with Previews
   - Backend TTS integration via /api/speak
   - Context menus & dropdowns (auto-close outside)
   - Dynamic Themes (Dark / Light)
   - Settings Modal
   - Multi-session chat history
   ================================================================ */

/* ═══════ CONFIGURATION ═══════ */
const API_BASE = (window.location.origin && window.location.origin !== 'null')
  ? window.location.origin.replace(/\/$/, '')
  : 'http://127.0.0.1:5000';
const API_CHAT = `${API_BASE}/api/chat`;
const API_SPEAK = `${API_BASE}/api/speak`;
const API_PREVIEW_VOICE = `${API_BASE}/api/preview_voice`;
const API_SESSIONS = `${API_BASE}/api/sessions`;
const SESSION_STORAGE_KEY = 'marvo.currentSessionId';
const THEME_STORAGE_KEY = 'marvo.theme';
const VOICE_STORAGE_KEY = 'marvo.voice';

/* ═══════ DOM CACHE ═══════ */
const $ = (sel) => document.querySelector(sel);
const DOM = {
  body:               document.body,
  sidebar:            $('#sidebar'),
  sidebarOverlay:     $('#sidebarOverlay'),
  btnHamburger:       $('#btnHamburger'),
  btnCloseSidebar:    $('#btnCloseSidebar'),
  btnNewChat:         $('#btnNewChat'),
  modeSelector:       $('#modeSelector'),
  modeBadge:          $('#modeBadge'),
  historyList:        $('#chatHistoryList'),
  historyContextMenu: $('#historyContextMenu'),
  btnRenameChat:      $('#btnRenameChat'),
  btnDeleteChat:      $('#btnDeleteChat'),
  btnAppMenu:         $('#btnAppMenu'),
  appDropdown:        $('#appDropdown'),
  btnThemeDark:       $('#btnThemeDark'),
  btnThemeLight:      $('#btnThemeLight'),
  btnSettings:        $('#btnSettings'),
  settingsModal:      $('#settingsModal'),
  btnCloseSettings:   $('#btnCloseSettings'),
  topbarTitle:        $('#topbarTitle'),
  avatarZone:         $('#avatarZone'),
  face:               $('#face'),
  stateLabel:         $('#stateLabel'),
  chatArea:           $('#chatArea'),
  chatMessages:       $('#chatMessages'),
  btnAttach:          $('#btnAttach'),
  attachMenu:         $('#attachMenu'),
  msgInput:           $('#msgInput'),
  btnSend:            $('#btnSend'),
  btnMic:             $('#btnMic'),
  btnScreenShare:     $('#btnScreenShare'),
  voiceOverlay:       $('#voiceOverlay'),
  btnVoiceCancel:     $('#btnVoiceCancel'),
};

/* ═══════ STATE ═══════ */
const STATES = [
  'state-idle','state-listening','state-loading','state-processing','state-speaking',
  'state-thinking','state-confused','state-happy','state-excited','state-searching',
  'state-angry','state-sad','state-surprised','state-curious','state-focused',
  'state-laughing','state-sleepy','state-alert','state-scared','state-love',
  'state-proud','state-nervous','state-calm','state-bored','state-amazed',
  'state-grateful','state-mysterious','state-playful','state-serious','state-shy',
  'state-confident','state-creative','state-calculating','state-remembering','state-learning',
  'state-explaining','state-warning','state-error','state-success','state-greeting',
  'state-farewell','state-joking','state-sarcastic','state-empathetic','state-motivating',
  'state-questioning','state-answering','state-coding','state-music','state-weather',
  'state-news','state-math','state-science','state-history','state-philosophy','state-gaming',
];

let currentSessionId = sessionStorage.getItem(SESSION_STORAGE_KEY) || generateSessionId();
let currentVoice     = localStorage.getItem(VOICE_STORAGE_KEY) || 'voice_3';
let sessionVersion   = 0;
let selectedMode     = 'medium';
let isRecording      = false;
let isBusy           = false;
let hasInteracted    = false;
let contextTargetSessionId = null;
let currentAudio     = null;

/* ═══════════════════════════════════════════════════════════════════
   THEME MANAGEMENT
   ═══════════════════════════════════════════════════════════════════ */

function setTheme(theme) {
  if (theme === 'light') {
    DOM.body.classList.add('theme-light');
  } else {
    DOM.body.classList.remove('theme-light');
  }
  localStorage.setItem(THEME_STORAGE_KEY, theme);
  closeAllDropdowns();
}

function initTheme() {
  const saved = localStorage.getItem(THEME_STORAGE_KEY) || 'dark';
  setTheme(saved);
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

function initVoiceSelection() {
  const targetRadio = document.querySelector(`input[name="marvoVoiceRadio"][value="${currentVoice}"]`);
  if (targetRadio) {
    targetRadio.checked = true;
  }

  // Radio button change listener
  document.querySelectorAll('input[name="marvoVoiceRadio"]').forEach(radio => {
    radio.addEventListener('change', (e) => {
      if (e.target.checked) {
        currentVoice = e.target.value;
        localStorage.setItem(VOICE_STORAGE_KEY, currentVoice);
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

        if (!res.ok) {
          console.warn('[Marvo] /api/preview_voice returned', res.status);
          return;
        }

        const data = await res.json();
        if (data && data.audio_base64) {
          const audio = new Audio("data:audio/mp3;base64," + data.audio_base64);
          currentAudio = audio;

          audio.onplay = () => {
            btn.classList.add('playing-sample');
            setEyeExpression('state-speaking');
            DOM.face.classList.add('speaking-mode');
            document.querySelectorAll('.eye').forEach(el => el.classList.add('speaking-mode'));
          };
          audio.onended = () => {
            btn.classList.remove('playing-sample');
            DOM.face.classList.remove('speaking-mode');
            document.querySelectorAll('.eye').forEach(el => el.classList.remove('speaking-mode'));
            setEyeExpression('state-idle');
            if (currentAudio === audio) currentAudio = null;
          };
          audio.onerror = () => {
            btn.classList.remove('playing-sample');
            DOM.face.classList.remove('speaking-mode');
            document.querySelectorAll('.eye').forEach(el => el.classList.remove('speaking-mode'));
            setEyeExpression('state-idle');
            if (currentAudio === audio) currentAudio = null;
          };

          audio.play().catch(err => {
            console.warn('[Marvo] Sample play error:', err);
            btn.classList.remove('playing-sample');
            DOM.face.classList.remove('speaking-mode');
            document.querySelectorAll('.eye').forEach(el => el.classList.remove('speaking-mode'));
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

function persistCurrentSession() {
  sessionStorage.setItem(SESSION_STORAGE_KEY, currentSessionId);
}

/* ═══════════════════════════════════════════════════════════════════
   EYE EXPRESSION ENGINE
   ═══════════════════════════════════════════════════════════════════ */

function setEyeExpression(state) {
  if (!STATES.includes(state)) return;
  STATES.forEach(s => DOM.face.classList.remove(s));
  DOM.face.classList.add(state);
  DOM.stateLabel.textContent = state.replace('state-', '').toUpperCase();
}

/* ═══════════════════════════════════════════════════════════════════
   CONTEXTUAL EYE EXPRESSION DETECTOR
   Maps keywords in user query + AI response → eye state.
   First match wins. Order = priority (most specific first).
   ═══════════════════════════════════════════════════════════════════ */
const _EX_MAP = [
  ['state-coding',      ['code','coding','program','debug','function','variable','script','python','javascript','html','css','api','compile','syntax','developer','github','repository','algorithm']],
  ['state-math',        ['math','calculate','equation','formula','algebra','geometry','calculus','percent','multiply','divide','sum','average','integral','derivative','factorial','trigonometry']],
  ['state-science',     ['science','physics','chemistry','biology','atom','molecule','experiment','hypothesis','quantum','electron','gravity','evolution','dna','cell','organism']],
  ['state-history',     ['history','historical','ancient','war','civilization','empire','dynasty','century','independence','revolution','itihas','mughal','british']],
  ['state-philosophy',  ['philosophy','philosophical','meaning of life','consciousness','existence','morality','ethics','plato','aristotle','socrates','metaphysics','epistemology']],
  ['state-gaming',      ['game','gaming','gamer','xbox','playstation','minecraft','fortnite','pubg','valorant','esports','fps','rpg','gta','controller']],
  ['state-music',       ['music','song','sing','melody','guitar','piano','rapper','album','playlist','spotify','gaana','bollywood song','concert','beat','rhythm','dj']],
  ['state-weather',     ['weather','mausam','temperature','rain','barish','sunny','cloudy','storm','humidity','forecast','wind','tornado','snow','barf']],
  ['state-news',        ['news','khabar','headline','breaking','report','journalist','media','current affairs','politics','election','government','sarkar']],
  ['state-love',        ['love','pyaar','ishq','dil','heart','romantic','valentine','crush','relationship','mohabbat','baby','sweetheart','darling']],
  ['state-angry',       ['angry','gussa','naraz','hate','stupid','worst','bakwas','bekar','chutiya','idiot','annoying','frustrated','irritated']],
  ['state-sad',         ['sad','dukhi','udaas','cry','rona','depressed','lonely','miss you','heartbreak','pain','dard','tanha','grief']],
  ['state-happy',       ['happy','khush','maza','amazing','awesome','great','wonderful','fantastic','celebrate','party','yay','woohoo','congratulations','badhai']],
  ['state-excited',     ['excited','excited','wow','incredible','unbelievable','mind blowing','insane','epic','legendary','phenomenal']],
  ['state-laughing',    ['haha','lol','lmao','rofl','funny','hilarious','joke','mazaak','comedy','hehe','xd','laughing','hasna']],
  ['state-joking',      ['kidding','just joking','mazaak','prank','troll','meme','pun']],
  ['state-sarcastic',   ['sarcasm','sarcastic','oh really','sure sure','yeah right','obviously','wow genius','no kidding']],
  ['state-confused',    ['confused','samajh nahi','kya matlab','what do you mean','i don\'t understand','huh','unclear','confusing','pata nahi','nahi samjha']],
  ['state-curious',     ['curious','interesting','tell me more','really','sach mein','wonder','fascinating','intriguing','how come','why is']],
  ['state-surprised',   ['surprised','shocking','oh my god','omg','unbelievable','no way','kya baat','seriously','what the','are you serious']],
  ['state-scared',      ['scary','darr','horror','ghost','bhoot','creepy','nightmare','terrifying','afraid','phobia','haunted']],
  ['state-thinking',    ['think','socho','consider','hmm','let me think','maybe','possibly','perhaps','vichar','sochna']],
  ['state-searching',   ['search','find','look up','dhundho','khojo','google','lookup','where is','kahan hai','locate']],
  ['state-calculating', ['calculate','compute','how much','kitna','kitne','total','percentage','ratio','estimate','count','convert']],
  ['state-explaining',  ['explain','samjhao','describe','elaborate','detail','breakdown','how does','kaise hota','tell me about','what is']],
  ['state-warning',     ['warning','danger','careful','savdhan','khabardar','caution','risk','avoid','harmful','toxic','beware']],
  ['state-error',       ['error','galti','wrong','mistake','bug','issue','problem','crash','fail','broken','fix']],
  ['state-success',     ['success','done','complete','hogaya','perfect','nailed it','achieved','accomplished','sorted','fixed']],
  ['state-greeting',    ['hello','hi','hey','namaste','assalam','kaise ho','how are you','good morning','good evening','good night','sup','yo','salam']],
  ['state-farewell',    ['bye','goodbye','alvida','see you','tata','good night','take care','chal','phir milte','talk later']],
  ['state-motivating',  ['motivate','inspire','keep going','you can','himmat','hosla','never give up','believe','strength','power','champion']],
  ['state-empathetic',  ['sorry','feel bad','tough time','hard','difficult','struggle','understand','i know','it\'s okay','theek hai','don\'t worry','koi baat nahi']],
  ['state-grateful',    ['thank','shukriya','dhanyavaad','thanks','appreciate','grateful','meharbani','obliged']],
  ['state-confident',   ['confident','sure','definitely','absolutely','pakka','bilkul','zaroor','certainly','no doubt','guaranteed']],
  ['state-creative',    ['create','design','imagine','kalpana','art','draw','paint','story','poem','kavita','write','compose','invent','brainstorm']],
  ['state-mysterious',  ['mystery','secret','raaz','hidden','unknown','enigma','puzzle','riddle','clue','paranormal','conspiracy']],
  ['state-playful',     ['play','khelo','fun','masti','chill','vibe','enjoy','entertainment','timepass']],
  ['state-serious',     ['serious','important','critical','urgent','zaruri','emergency','attention','focus','concentrate','matter']],
  ['state-shy',         ['shy','sharmana','blush','embarrassed','awkward','hesitant']],
  ['state-proud',       ['proud','achievement','accomplished','garv','mera beta','well done','bravo','excellent','outstanding']],
  ['state-nervous',     ['nervous','anxious','tension','worried','stressed','panic','fear','ghabrahat','fikar']],
  ['state-calm',        ['calm','relax','peaceful','shanti','meditation','breathe','zen','tranquil','soothing','quiet']],
  ['state-bored',       ['bored','boring','bor','nothing to do','kuch nahi','dull','tedious','monotonous','yawn']],
  ['state-amazed',      ['amazing','incredible','spectacular','breathtaking','magnificent','extraordinary','kamaal','zabardast','shandar']],
  ['state-sleepy',      ['sleepy','neend','tired','thak gaya','exhausted','drowsy','yawning','rest','sona hai']],
  ['state-alert',       ['alert','urgent','immediately','jaldi','abhi','now','hurry','quick','asap','emergency','turant']],
  ['state-remembering', ['remember','yaad','recall','memory','nostalgia','purani','those days','bachpan','past']],
  ['state-learning',    ['learn','seekho','study','padhai','course','tutorial','lesson','practice','education','school','college']],
  ['state-questioning', ['why','kyu','kyun','kaun','who','which','when','kab','where','kahan','how','kaise','what if']],
  ['state-answering',   ['answer','jawab','solution','samadhan','result','output','response']],
  ['state-focused',     ['focus','concentrate','dhyan','attention','ek chiz','priority','target','goal','aim']],
];

function detectEyeExpression(userText, aiText) {
  const combined = (userText + ' ' + aiText).toLowerCase();
  for (let i = 0; i < _EX_MAP.length; i++) {
    const [state, keywords] = _EX_MAP[i];
    for (let k = 0; k < keywords.length; k++) {
      if (combined.includes(keywords[k])) return state;
    }
  }
  return null;
}

/* ═══════════════════════════════════════════════════════════════════
   AVATAR FLOAT-UP & CHAT AREA
   ═══════════════════════════════════════════════════════════════════ */

function activateChatMode() {
  hasInteracted = true;
  DOM.body.classList.add('chat-active');
}

function clearChat() {
  DOM.chatMessages.innerHTML = '';
  hasInteracted = false;
  DOM.body.classList.remove('chat-active');
  setEyeExpression('state-idle');
}

/* ═══════════════════════════════════════════════════════════════════
   BACKEND TTS INTEGRATION (/api/speak)
   ═══════════════════════════════════════════════════════════════════ */

async function playSpeech(text, btn) {
  const cleanText = text.replace(/[*_~`#>]/g, '').trim();
  if (!cleanText) return;

  if (currentAudio) {
    try {
      currentAudio.pause();
      currentAudio.currentTime = 0;
    } catch {}
    currentAudio = null;
  }

  try {
    const res = await fetch(API_SPEAK, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        text: cleanText,
        voice_id: currentVoice,
      }),
    });

    if (!res.ok) {
      console.warn('[Marvo] /api/speak returned', res.status);
      return;
    }

    const data = await res.json();
    if (data && data.audio_base64) {
      const audio = new Audio("data:audio/mp3;base64," + data.audio_base64);
      currentAudio = audio;

      audio.onplay = () => {
        if (btn) btn.classList.add('playing-audio');
        setEyeExpression('state-speaking');
        DOM.face.classList.add('speaking-mode');
        document.querySelectorAll('.eye').forEach(el => el.classList.add('speaking-mode'));
      };
      audio.onended = () => {
        if (btn) btn.classList.remove('playing-audio');
        DOM.face.classList.remove('speaking-mode');
        document.querySelectorAll('.eye').forEach(el => el.classList.remove('speaking-mode'));
        setEyeExpression('state-idle');
        if (currentAudio === audio) currentAudio = null;
      };
      audio.onerror = () => {
        if (btn) btn.classList.remove('playing-audio');
        DOM.face.classList.remove('speaking-mode');
        document.querySelectorAll('.eye').forEach(el => el.classList.remove('speaking-mode'));
        setEyeExpression('state-idle');
        if (currentAudio === audio) currentAudio = null;
      };

      audio.play().catch(err => {
        console.warn('[Marvo] Speech playback error:', err);
        if (btn) btn.classList.remove('playing-audio');
        DOM.face.classList.remove('speaking-mode');
        document.querySelectorAll('.eye').forEach(el => el.classList.remove('speaking-mode'));
        setEyeExpression('state-idle');
      });
    }
  } catch (err) {
    console.error('[Marvo] Speech error:', err);
    if (btn) btn.classList.remove('playing-audio');
    DOM.face.classList.remove('speaking-mode');
    document.querySelectorAll('.eye').forEach(el => el.classList.remove('speaking-mode'));
    setEyeExpression('state-idle');
  }
}

/* ═══════════════════════════════════════════════════════════════════
   CHAT RENDERING
   ═══════════════════════════════════════════════════════════════════ */

function addMessage(text, sender) {
  const el = document.createElement('div');
  el.className = `msg msg-${sender}`;
  el.textContent = text;

  // If AI message, append inline speaker button wired to /api/speak
  if (sender === 'ai') {
    const speakerBtn = document.createElement('button');
    speakerBtn.className = 'msg-speaker';
    speakerBtn.setAttribute('aria-label', 'Read Aloud');
    speakerBtn.setAttribute('title', 'Read aloud');
    speakerBtn.innerHTML = `<svg viewBox="0 0 24 24" width="14" height="14"><polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" fill="currentColor"/><path d="M15.54 8.46a5 5 0 0 1 0 7.07M19.07 4.93a10 10 0 0 1 0 14.14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>`;

    speakerBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      playSpeech(text, speakerBtn);
    });

    el.appendChild(speakerBtn);
  }

  DOM.chatMessages.appendChild(el);
  scrollToBottom();
}

function showLoading() {
  const el = document.createElement('div');
  el.className = 'loading-dots';
  el.innerHTML = '<span></span><span></span><span></span>';
  DOM.chatMessages.appendChild(el);
  scrollToBottom();
  return el;
}

function scrollToBottom() {
  requestAnimationFrame(() => {
    DOM.chatMessages.scrollTop = DOM.chatMessages.scrollHeight;
  });
}

/* ═══════════════════════════════════════════════════════════════════
   BACKEND COMMUNICATION
   ═══════════════════════════════════════════════════════════════════ */

async function sendMessage(userText) {
  if (!userText.trim() || isBusy) return;
  isBusy = true;
  const requestSessionId = currentSessionId;
  const requestVersion   = sessionVersion;

  activateChatMode();
  addMessage(userText, 'user');
  DOM.msgInput.value = '';
  DOM.btnSend.disabled = true;

  const dots = showLoading();
  setEyeExpression('state-loading');

  try {
    const res = await fetch(API_CHAT, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        message:       userText,
        thinking_mode: selectedMode,
        session_id:    requestSessionId,
      }),
    });

    dots.remove();

    if (!res.ok) {
      throw new Error(`Server responded with ${res.status}`);
    }

    const data = await res.json();
    const aiText  = data.response || 'No response received.';
    const aiState = data.state || 'state-speaking';

    if (requestSessionId !== currentSessionId || requestVersion !== sessionVersion) return;

    setEyeExpression(aiState);
    const aiMsgEl = addMessage(aiText, 'ai');
    updateHistorySidebar(userText, requestSessionId);

    // Contextual eye expression based on AI response + user query keywords
    const contextState = detectEyeExpression(userText, aiText);
    if (contextState) setEyeExpression(contextState);

    // Auto-play speech for AI response; audio onplay/onended dynamically syncs eye animation
    const speakerBtn = aiMsgEl ? aiMsgEl.querySelector('.msg-speaker') : null;
    playSpeech(aiText, speakerBtn);

  } catch (err) {
    if (requestSessionId !== currentSessionId || requestVersion !== sessionVersion) return;

    dots.remove();
    setEyeExpression('state-idle');
    addMessage(
      "I couldn't connect to my brain right now. Make sure the server is running on port 5000.",
      'ai'
    );
    console.error('[Marvo] API Error:', err);
  } finally {
    if (requestSessionId !== currentSessionId || requestVersion !== sessionVersion) return;
    isBusy = false;
    DOM.btnSend.disabled = false;
    DOM.msgInput.focus();
  }
}

/* ═══════════════════════════════════════════════════════════════════
   DROPDOWN & CONTEXT MENU MANAGEMENT (AUTO-CLOSE)
   ═══════════════════════════════════════════════════════════════════ */

function closeAllDropdowns() {
  DOM.appDropdown.classList.remove('show');
  DOM.attachMenu.classList.remove('show');
  DOM.historyContextMenu.classList.remove('show');
  document.querySelectorAll('.history-more-btn.active').forEach(b => b.classList.remove('active'));
}

// Click outside handler
document.addEventListener('click', (e) => {
  if (!e.target.closest('.menu-container') &&
      !e.target.closest('.attach-container') &&
      !e.target.closest('.history-context-menu') &&
      !e.target.closest('.history-more-btn')) {
    closeAllDropdowns();
  }
  if (e.target === DOM.settingsModal) {
    closeSettingsModal();
  }
});

/* ═══════════════════════════════════════════════════════════════════
   SIDEBAR & CHAT HISTORY CONTEXT MENU
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
}

function highlightActiveSession() {
  DOM.historyList.querySelectorAll('li').forEach(li => {
    li.classList.toggle('active', li.dataset.sid === currentSessionId);
  });
}

function openHistoryContextMenu(e, sessionId, moreBtn) {
  e.stopPropagation();
  closeAllDropdowns();

  contextTargetSessionId = sessionId;
  moreBtn.classList.add('active');

  const rect = moreBtn.getBoundingClientRect();
  const menu = DOM.historyContextMenu;

  menu.style.top = `${rect.bottom + 4}px`;
  menu.style.left = `${Math.min(rect.left, window.innerWidth - 145)}px`;
  menu.classList.add('show');
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
    openHistoryContextMenu(e, sessionId, moreBtn);
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
  persistCurrentSession();
  sessionVersion += 1;
  isBusy = false;
  DOM.btnSend.disabled = false;
  clearChat();
  highlightActiveSession();

  try {
    const res = await fetch(`${API_BASE}/api/history/${encodeURIComponent(sessionId)}`);
    if (!res.ok) throw new Error(`Server status ${res.status}`);
    const data = await res.json();
    if (sessionId !== currentSessionId) return;

    const msgs = data.messages || [];
    msgs.forEach(m => {
      addMessage(m.content, m.role === 'user' ? 'user' : 'ai');
    });

    if (msgs.length > 0) {
      activateChatMode();
    }
  } catch (err) {
    console.error('[Marvo] History load error:', err);
  }
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
    const res = await fetch(API_SESSIONS);
    if (!res.ok) return;
    const sessions = await res.json();
    DOM.historyList.innerHTML = '';
    sessions.forEach(session => {
      DOM.historyList.appendChild(createHistoryItem(session.title, session.session_id));
    });
    highlightActiveSession();
  } catch (err) {
    console.error('[Marvo] Error loading sessions:', err);
  }
}

async function restoreCurrentSession() {
  try {
    const res = await fetch(`${API_BASE}/api/history/${encodeURIComponent(currentSessionId)}`);
    if (!res.ok) return;
    const data = await res.json();
    const msgs = data.messages || [];
    msgs.forEach(m => {
      addMessage(m.content, m.role === 'user' ? 'user' : 'ai');
    });
    if (msgs.length > 0) {
      activateChatMode();
    }
    highlightActiveSession();
  } catch (err) {
    console.error('[Marvo] Restore session error:', err);
  }
}

/* Rename Chat */
DOM.btnRenameChat.addEventListener('click', () => {
  if (!contextTargetSessionId) return;
  const li = DOM.historyList.querySelector(`li[data-sid="${contextTargetSessionId}"]`);
  const titleSpan = li?.querySelector('.history-title');
  const currentTitle = titleSpan ? titleSpan.textContent : '';

  const newTitle = prompt('Enter new chat name:', currentTitle);
  if (newTitle && newTitle.trim()) {
    if (titleSpan) {
      titleSpan.textContent = newTitle.trim();
      titleSpan.title = newTitle.trim();
    }
  }
  closeAllDropdowns();
});

/* Delete Chat */
DOM.btnDeleteChat.addEventListener('click', () => {
  if (!contextTargetSessionId) return;
  const confirmDelete = confirm('Are you sure you want to delete this chat?');
  if (!confirmDelete) {
    closeAllDropdowns();
    return;
  }

  const li = DOM.historyList.querySelector(`li[data-sid="${contextTargetSessionId}"]`);
  if (li) li.remove();

  if (contextTargetSessionId === currentSessionId) {
    newChat();
  }
  closeAllDropdowns();
});

/* ═══════════════════════════════════════════════════════════════════
   THINKING MODE
   ═══════════════════════════════════════════════════════════════════ */

function setThinkingMode(mode) {
  selectedMode = mode;
  DOM.modeSelector.querySelectorAll('.mode-btn').forEach(btn => {
    btn.classList.toggle('active', btn.dataset.mode === mode);
  });
  DOM.modeBadge.textContent = mode.charAt(0).toUpperCase() + mode.slice(1);
}

/* ═══════════════════════════════════════════════════════════════════
   VOICE OVERLAY & RECORDING
   ═══════════════════════════════════════════════════════════════════ */

function openVoiceOverlay() {
  isRecording = true;
  DOM.voiceOverlay.classList.add('show');
  DOM.btnMic.classList.add('recording');
  setEyeExpression('state-listening');
}

function closeVoiceOverlay() {
  isRecording = false;
  DOM.voiceOverlay.classList.remove('show');
  DOM.btnMic.classList.remove('recording');
  if (!isBusy) setEyeExpression('state-idle');
}

function startVoiceCapture() {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;

  if (!SpeechRecognition) {
    openVoiceOverlay();
    setTimeout(() => {
      closeVoiceOverlay();
      activateChatMode();
      addMessage("Voice input isn't supported in this browser. Try Chrome or Edge.", 'ai');
    }, 1800);
    return;
  }

  const recognition = new SpeechRecognition();
  recognition.lang = 'en-US';
  recognition.interimResults = false;
  recognition.maxAlternatives = 1;

  openVoiceOverlay();

  recognition.onresult = (e) => {
    const transcript = e.results[0][0].transcript;
    closeVoiceOverlay();
    if (transcript.trim()) {
      DOM.msgInput.value = transcript;
      sendMessage(transcript);
    }
  };

  recognition.onerror = () => closeVoiceOverlay();
  recognition.onend = () => closeVoiceOverlay();

  try {
    recognition.start();
  } catch {
    closeVoiceOverlay();
  }
}

/* ═══════════════════════════════════════════════════════════════════
   EVENT BINDINGS
   ═══════════════════════════════════════════════════════════════════ */

// ── Sidebar ──
DOM.btnHamburger.addEventListener('click', openSidebar);
DOM.btnCloseSidebar.addEventListener('click', closeSidebar);
DOM.sidebarOverlay.addEventListener('click', closeSidebar);
DOM.btnNewChat.addEventListener('click', newChat);

// ── Top Right App Menu ──
DOM.btnAppMenu.addEventListener('click', (e) => {
  e.stopPropagation();
  const isOpen = DOM.appDropdown.classList.contains('show');
  closeAllDropdowns();
  if (!isOpen) DOM.appDropdown.classList.add('show');
});

DOM.btnThemeDark.addEventListener('click', () => setTheme('dark'));
DOM.btnThemeLight.addEventListener('click', () => setTheme('light'));
DOM.btnSettings.addEventListener('click', openSettingsModal);
DOM.btnCloseSettings.addEventListener('click', closeSettingsModal);

// ── Attachment Menu ──
DOM.btnAttach.addEventListener('click', (e) => {
  e.stopPropagation();
  const isOpen = DOM.attachMenu.classList.contains('show');
  closeAllDropdowns();
  if (!isOpen) DOM.attachMenu.classList.add('show');
});

DOM.attachMenu.addEventListener('click', (e) => {
  const item = e.target.closest('.attach-item');
  if (item) {
    const type = item.dataset.type;
    closeAllDropdowns();
    alert(`${type} upload feature coming soon!`);
  }
});

// ── Live Screen Share Demo ──
DOM.btnScreenShare.addEventListener('click', () => {
  alert('Live screen sharing feature coming soon!');
});

// ── Thinking Mode ──
DOM.modeSelector.addEventListener('click', (e) => {
  const btn = e.target.closest('.mode-btn');
  if (btn) setThinkingMode(btn.dataset.mode);
});

// ── Send Message ──
DOM.btnSend.addEventListener('click', () => sendMessage(DOM.msgInput.value));
DOM.msgInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    sendMessage(DOM.msgInput.value);
  }
});

// ── Voice Input ──
DOM.btnMic.addEventListener('click', () => {
  if (isRecording) {
    closeVoiceOverlay();
  } else {
    startVoiceCapture();
  }
});
DOM.btnVoiceCancel.addEventListener('click', closeVoiceOverlay);

// ── Escape Key Closes Overlays ──
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape') {
    if (isRecording) closeVoiceOverlay();
    closeSidebar();
    closeAllDropdowns();
    closeSettingsModal();
  }
});

/* ═══════════════════════════════════════════════════════════════════
   INITIALIZATION
   ═══════════════════════════════════════════════════════════════════ */
initTheme();
initVoiceSelection();
setEyeExpression('state-idle');
persistCurrentSession();
loadHistorySidebar();
restoreCurrentSession();
DOM.msgInput.focus();

window.marvo = {
  setEyeExpression,
  sendMessage,
  newChat,
  setTheme,
  playSpeech,
  get currentVoice() { return currentVoice; },
  set currentVoice(val) { currentVoice = val; },
  get session() { return currentSessionId; },
  get mode()    { return selectedMode; },
  STATES,
};
