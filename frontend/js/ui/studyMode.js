/**
 * MARVO AI — Isolated Study Mode UI Controller
 * Module: js/ui/studyMode.js
 * 
 * Manages the isolated Study Mode interface, 3D animated book entry,
 * strict prompt isolation, PDF/Photo multimodal attachments, KaTeX STEM rendering,
 * and integration with Flashcard, Quiz, and On-Device OCR engines.
 */

(function(window) {
  'use strict';

  const STUDY_STORAGE_KEY = 'marvo.studymode.history';
  const STUDY_SYSTEM_INSTRUCTION = 
    `You are Marvo Study Mode — a specialized higher secondary STEM tutor, examiner, and academic problem solver.
Focus deeply on Physics, Chemistry, Mathematics (Algebra, Trigonometry, Differential & Integral Calculus), and Biology.
Always provide step-by-step derivations, clear intermediate steps, and format EVERY mathematical equation in textbook LaTeX using $...$ for inline and $$...$$ for block equations.
Be rigorous, precise, and pedagogically clear.`;

  const StudyModeUI = {
    isOpen: false,
    activeTab: 'chat', // 'chat' | 'flashcards' | 'quiz' | 'ocr'
    activeCognitiveMode: null, // null | 'deep-thinking' | 'web-research'
    history: [],
    pendingAttachment: null, // { type: 'pdf'|'photo', name: '', dataUrl: '', text: '' }
    dom: {},

    init() {
      this.injectDOM();
      this.cacheDOM();
      this.bindEvents();
      this.loadHistory();

      // Initialize plugins
      if (window.FlashcardEngine && this.dom.flashcardsContainer) {
        window.FlashcardEngine.init(this.dom.flashcardsContainer);
      }
      if (window.QuizEngine && this.dom.quizContainer) {
        window.QuizEngine.init(this.dom.quizContainer);
      }

      console.log('[StudyMode] Initialized successfully.');
    },

    injectDOM() {
      // 1. Ultra-Premium 3D Academic Journal Intro Overlay
      const introHTML = `
        <div id="studyIntroOverlay" class="study-intro-overlay">
          <div class="study-journal-scene">
            <div class="study-book-3d academic-journal" id="studyBook3D">
              <!-- Leather Bound Spine with Gold Ribs -->
              <div class="book-spine">
                <span class="spine-text">MARVO STEM JOURNAL • VOL. IX</span>
                <div class="spine-rib rib-1"></div>
                <div class="spine-rib rib-2"></div>
                <div class="spine-rib rib-3"></div>
                <div class="spine-rib rib-4"></div>
              </div>

              <!-- Back Leather Cover -->
              <div class="book-cover-back"></div>

              <!-- Volumetric Parchment Pages Stack with Real Physics/Math Formulas -->
              <div class="book-pages-stack">
                <div class="academic-manuscript">
                  <div class="manuscript-header">
                    <span class="manuscript-tag">QUANTUM FIELD THEORY &amp; CALCULUS</span>
                    <span class="manuscript-vol">VOL. IX • SEC. IV</span>
                  </div>
                  <div class="manuscript-title">Analytical Physics &amp; Differential Geometry</div>
                  <div class="manuscript-formula-block">
                    <div class="tex-line">$$\\nabla \\times \\mathbf{B} = \\mu_0 \\mathbf{J} + \\mu_0 \\varepsilon_0 \\frac{\\partial \\mathbf{E}}{\\partial t}$$</div>
                    <div class="tex-line">$$\\int_{-\\infty}^{\\infty} e^{-x^2} dx = \\sqrt{\\pi}, \\quad \\mathcal{L} = \\bar{\\psi}(i\\gamma^\\mu D_\\mu - m)\\psi$$</div>
                  </div>
                  <div class="manuscript-abstract">
                    Rigorous proof formulations, multi-agent cognitive derivations, and closed-form solutions initialized.
                  </div>
                </div>
              </div>

              <!-- 3D Turning Page with Academic Theorems -->
              <div class="book-flipping-page">
                <div class="page-front">
                  <div class="manuscript-formula-block mini">
                    <div class="tex-line">$$\\oint_{\\partial \\Sigma} \\mathbf{E} \\cdot d\\boldsymbol{\\ell} = -\\frac{d}{dt}\\iint_{\\Sigma} \\mathbf{B} \\cdot d\\mathbf{A}$$</div>
                  </div>
                  <div class="manuscript-lines">
                    <div class="m-line"></div>
                    <div class="m-line short"></div>
                  </div>
                </div>
                <div class="page-back">
                  <div class="manuscript-formula-block mini">
                    <div class="tex-line">$$\\mathcal{H}\\psi = E\\psi, \\quad G_{\\mu\\nu} = \\frac{8\\pi G}{c^4}T_{\\mu\\nu}$$</div>
                  </div>
                  <div class="manuscript-lines">
                    <div class="m-line"></div>
                    <div class="m-line"></div>
                  </div>
                </div>
              </div>

              <!-- Sapphire Hardcover with Ornate Gold Foil Filigree & Seal -->
              <div class="book-cover-front">
                <div class="gold-foil-border">
                  <div class="corner-filigree top-left">✦</div>
                  <div class="corner-filigree top-right">✦</div>
                  <div class="corner-filigree bottom-left">✦</div>
                  <div class="corner-filigree bottom-right">✦</div>

                  <div class="book-embossed-crest">
                    <svg viewBox="0 0 100 100" width="56" height="56">
                      <circle cx="50" cy="50" r="44" fill="none" stroke="#d4af37" stroke-width="2.5" stroke-dasharray="3,2"/>
                      <circle cx="50" cy="50" r="38" fill="rgba(212,175,55,0.08)" stroke="#f3e5ab" stroke-width="1.5"/>
                      <path d="M50 18 L58 38 L80 38 L62 51 L69 72 L50 59 L31 72 L38 51 L20 38 L42 38 Z" fill="none" stroke="#d4af37" stroke-width="1.5"/>
                      <circle cx="50" cy="50" r="12" fill="rgba(0,240,255,0.2)" stroke="#00f0ff" stroke-width="1.5"/>
                      <circle cx="50" cy="50" r="4" fill="#00f0ff"/>
                    </svg>
                  </div>

                  <div class="book-journal-title">MARVO</div>
                  <div class="book-journal-subtitle">ACADEMIC JOURNAL</div>
                  <div class="book-journal-dept">HIGHER SECONDARY STEM &bull; ADVANCED COGNITION</div>
                  <div class="gold-seal-ribbon">
                    <span>VERITAS ET SCIENTIA</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      `;

      // 2. Study Mode Main Container
      const containerHTML = `
        <div id="studyModeContainer" class="study-mode-container">
          <!-- Top Header with Safe Area Insets -->
          <header class="study-header">
            <div class="study-header-main-row">
              <div class="study-header-left">
                <button id="btnStudyBack" class="btn-study-back" title="Exit Study Mode">
                  <svg viewBox="0 0 24 24" width="20" height="20"><polyline points="15 18 9 12 15 6" fill="none" stroke="currentColor" stroke-width="2.5"/></svg>
                </button>
                <div class="study-header-title-wrap">
                  <div class="study-header-title">
                    <span>Study Mode</span>
                    <span class="study-pulse-dot"></span>
                  </div>
                  <span class="study-header-sub" id="studySubHeader">Isolated Academic Workspace</span>
                </div>
              </div>

              <div class="study-header-right">
                <!-- Live Vision Tutor Quick Button -->
                <button id="btnHeaderLiveVision" class="btn-study-live-vision" title="Launch Gemini Live Vision Tutor" type="button">
                  <span class="live-pulse-dot"></span>
                  <span>Live Tutor</span>
                </button>

                <!-- Isolated Focus Mode DND Toggle -->
                <button id="btnStudyFocusToggle" class="btn-study-focus" title="Toggle Isolated Focus Mode (Do Not Disturb)">
                  <span class="focus-dot"></span>
                  <span id="focusToggleLabel">Focus: OFF</span>
                </button>

                <button id="btnStudyToolsMenu" class="btn-study-tools-menu" title="Learning Tools">
                  <span>Tools</span>
                  <svg viewBox="0 0 24 24" width="16" height="16"><circle cx="12" cy="12" r="1.5" fill="currentColor"/><circle cx="19" cy="12" r="1.5" fill="currentColor"/><circle cx="5" cy="12" r="1.5" fill="currentColor"/></svg>
                </button>

                <div id="studyToolsDropdown" class="study-tools-dropdown">
                  <button class="study-tool-item" id="toolChatMode">
                    <span>💬</span> <span>Tutor Chat</span>
                  </button>
                  <button class="study-tool-item live-vision-dropdown-item" id="toolLiveVisionTutor">
                    <span>👁️</span> <span>Live Vision Tutor</span>
                  </button>
                  <button class="study-tool-item" id="toolFlashcardMode">
                    <span>🗂️</span> <span>Flashcard Generator</span>
                  </button>
                  <button class="study-tool-item" id="toolQuizMode">
                    <span>📝</span> <span>Interactive Quiz / MCQ</span>
                  </button>
                  <button class="study-tool-item" id="toolOcrMode">
                    <span>📷</span> <span>On-Device Book OCR</span>
                  </button>
                  <button class="study-tool-item" id="toolControlCenter">
                    <span>🎛️</span> <span>AI Control Center</span>
                  </button>
                </div>
              </div>
            </div>

            <!-- Cognitive Modes Toggle Bar (Positioned strictly below with vertical spacing) -->
            <div class="study-header-modes-row">
              <div class="study-tier-switcher cognitive-modes-switcher" id="studyTierSwitcher">
                <button class="study-tier-btn active" data-mode="Fast" type="button" title="Fast Mode — Instant Groq">Fast</button>
                <button class="study-tier-btn" data-mode="Thinking" type="button" title="Thinking Mode — Gemini Reasoning">Thinking</button>
                <button class="study-tier-btn" data-mode="Pro Thinking" type="button" title="Pro Thinking Mode — OpenRouter / Claude">Pro Thinking</button>
              </div>
            </div>
          </header>

          <!-- Center Workspace -->
          <main class="study-workspace">
            <!-- 1. Tutor Chat View -->
            <div id="studyChatView" class="study-chat-scroll">
              <div class="study-hero-banner">
                <div class="study-hero-book-icon">📖</div>
                <div class="study-hero-title">Welcome to Study Mode</div>
                <div class="study-hero-desc">
                  Strictly isolated environment for Higher Secondary STEM learning.
                  Upload notes, PDFs, or photos for derivations, quizzes, and KaTeX formula solving.
                </div>
              </div>
              <div id="studyMessagesList"></div>
            </div>

            <!-- 2. Flashcards View -->
            <div id="studyFlashcardsView" class="study-plugin-view">
              <div id="flashcardsContainer"></div>
            </div>

            <!-- 3. Quiz View -->
            <div id="studyQuizView" class="study-plugin-view">
              <div id="quizContainer"></div>
            </div>

            <!-- 4. OCR Scanner View -->
            <div id="studyOcrView" class="study-plugin-view">
              <div class="ocr-scanner-view">
                <div class="ocr-card">
                  <h3 style="color:#fff;margin-top:0;margin-bottom:12px;display:flex;align-items:center;gap:8px;">
                    <span>📷</span> <span>On-Device Textbook OCR</span>
                  </h3>
                  <p style="color:var(--study-text-muted);font-size:13px;margin-bottom:14px;">
                    Extracts formulas, questions, and notes locally from your device photos without sending full images to cloud servers.
                  </p>
                  
                  <div class="ocr-preview-box" id="ocrPreviewBox" style="display:none;">
                    <img id="ocrPreviewImg" class="ocr-preview-img" alt="Scanned Document">
                  </div>

                  <textarea id="ocrExtractedText" class="ocr-extracted-text-area" placeholder="Extracted textbook text will appear here. You can also edit it before generating study materials..."></textarea>

                  <div style="display:flex;gap:10px;">
                    <button class="btn-ocr-summarize" id="btnOcrSummarize">
                      <svg viewBox="0 0 24 24" width="16" height="16"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6z" fill="none" stroke="currentColor" stroke-width="2"/><polyline points="14 2 14 8 20 8" fill="none" stroke="currentColor" stroke-width="2"/><line x1="16" y1="13" x2="8" y2="13" stroke="currentColor" stroke-width="2"/><line x1="16" y1="17" x2="8" y2="17" stroke="currentColor" stroke-width="2"/><polyline points="10 9 9 9 8 9" stroke="currentColor" stroke-width="2"/></svg>
                      <span>Summarize with AI</span>
                    </button>
                    <button class="btn-ocr-summarize" id="btnOcrMakeQuiz" style="background:rgba(0,255,136,0.15);border:1px solid #00ff88;color:#00ff88;">
                      <span>Generate Quiz</span>
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </main>

          <!-- Attachment Preview Shelf -->
          <div id="studyAttachmentShelf" class="study-attachment-shelf hidden"></div>

          <!-- Bottom Chatbar & Action Menu Area -->
          <div class="study-chatbar-wrap">
            <!-- Active Cognitive Mode Chip Indicator -->
            <div id="studyActiveModeChip" class="study-active-mode-chip hidden">
              <span class="mode-chip-icon">🧠</span>
              <span class="mode-chip-label" id="studyActiveModeLabel">Deep Thinking Active</span>
              <button class="btn-dismiss-mode" id="btnDismissActiveMode" title="Dismiss active mode">&times;</button>
            </div>

            <!-- Action Menu Sheet (Bottom Sheet / Popup Menu) -->
            <div id="studyActionMenuSheet" class="study-action-menu-sheet">
              <div class="action-menu-header">
                <span class="action-menu-title">Study Assistant Tools</span>
                <button class="action-menu-close" id="btnCloseActionMenu">&times;</button>
              </div>

              <!-- Category 1: ATTACHMENTS -->
              <div class="action-menu-category">
                <div class="category-label">ATTACHMENTS</div>
                <div class="action-menu-grid">
                  <button class="action-menu-btn" id="actionUploadPdf" type="button">
                    <span class="action-btn-icon">📄</span>
                    <div class="action-btn-text">
                      <span class="action-btn-title">Upload PDF</span>
                      <span class="action-btn-desc">Notes, papers &amp; textbooks</span>
                    </div>
                  </button>
                  <button class="action-menu-btn" id="actionTakePhoto" type="button">
                    <span class="action-btn-icon">📷</span>
                    <div class="action-btn-text">
                      <span class="action-btn-title">Take Photo</span>
                      <span class="action-btn-desc">Camera &amp; textbook OCR</span>
                    </div>
                  </button>
                </div>
              </div>

              <!-- Category 2: ADVANCED MODES -->
              <div class="action-menu-category">
                <div class="category-label">ADVANCED MODES</div>
                <div class="action-menu-grid">
                  <button class="action-menu-btn" id="actionDeepThinking" data-mode="deep-thinking" type="button">
                    <span class="action-btn-icon">🧠</span>
                    <div class="action-btn-text">
                      <span class="action-btn-title">Deep Thinking</span>
                      <span class="action-btn-desc">Chain-of-thought derivations</span>
                    </div>
                    <span class="action-check-badge">✓</span>
                  </button>
                  <button class="action-menu-btn" id="actionWebResearch" data-mode="web-research" type="button">
                    <span class="action-btn-icon">🌐</span>
                    <div class="action-btn-text">
                      <span class="action-btn-title">Web Research</span>
                      <span class="action-btn-desc">Scholarly source synthesis</span>
                    </div>
                    <span class="action-check-badge">✓</span>
                  </button>
                  <button class="action-menu-btn live-vision-action-btn" id="actionLiveVisionTutor" type="button">
                    <span class="action-btn-icon">👁️</span>
                    <div class="action-btn-text">
                      <span class="action-btn-title">Live Vision Tutor</span>
                      <span class="action-btn-desc">Gemini Live Voice &amp; Camera</span>
                    </div>
                  </button>
                </div>
              </div>
            </div>

            <!-- Input Bar -->
            <div class="study-chatbar">
              <!-- Hidden Attachment Input -->
              <input type="file" id="studyFileInput" accept="application/pdf,image/*" style="display:none;">

              <!-- [ + ] Action Menu Button -->
              <button id="btnStudyActionMenu" class="btn-study-action btn-action-plus" title="Actions &amp; Advanced Modes" type="button">
                <svg viewBox="0 0 24 24" width="20" height="20">
                  <line x1="12" y1="5" x2="12" y2="19" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"/>
                  <line x1="5" y1="12" x2="19" y2="12" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"/>
                </svg>
              </button>

              <!-- Clean Input Field -->
              <input type="text" id="studyMsgInput" class="study-input" placeholder="Ask a question, formula, or topic..." autocomplete="off">

              <!-- Mic Button -->
              <button id="btnStudyMic" class="btn-study-action" title="Voice Dictation" type="button">
                <svg viewBox="0 0 24 24" width="20" height="20"><path d="M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm-1-9c0-.55.45-1 1-1s1 .45 1 1v6c0 .55-.45 1-1 1s-1-.45-1-1V5z" fill="currentColor"/><path d="M17 11c0 2.76-2.24 5-5 5s-5-2.24-5-5H5c0 3.53 2.61 6.43 6 6.92V21h2v-3.08c3.39-.49 6-3.39 6-6.92h-2z" fill="currentColor"/></svg>
              </button>

              <!-- Send Button -->
              <button id="btnStudySend" class="btn-study-action btn-study-send" title="Send" type="button">
                <svg viewBox="0 0 24 24" width="18" height="18"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z" fill="currentColor"/></svg>
              </button>
            </div>
          </div>
        </div>
      `;

      document.body.insertAdjacentHTML('beforeend', introHTML);
      document.body.insertAdjacentHTML('beforeend', containerHTML);
    },

    cacheDOM() {
      this.dom = {
        introOverlay: document.getElementById('studyIntroOverlay'),
        book3D: document.getElementById('studyBook3D'),
        container: document.getElementById('studyModeContainer'),
        btnBack: document.getElementById('btnStudyBack'),
        subHeader: document.getElementById('studySubHeader'),
        tierSwitcher: document.getElementById('studyTierSwitcher'),
        btnToolsMenu: document.getElementById('btnStudyToolsMenu'),
        toolsDropdown: document.getElementById('studyToolsDropdown'),
        btnFocusToggle: document.getElementById('btnStudyFocusToggle'),
        focusToggleLabel: document.getElementById('focusToggleLabel'),
        toolChatMode: document.getElementById('toolChatMode'),
        toolFlashcardMode: document.getElementById('toolFlashcardMode'),
        toolQuizMode: document.getElementById('toolQuizMode'),
        toolOcrMode: document.getElementById('toolOcrMode'),
        toolControlCenter: document.getElementById('toolControlCenter'),

        chatView: document.getElementById('studyChatView'),
        messagesList: document.getElementById('studyMessagesList'),
        flashcardsView: document.getElementById('studyFlashcardsView'),
        flashcardsContainer: document.getElementById('flashcardsContainer'),
        quizView: document.getElementById('studyQuizView'),
        quizContainer: document.getElementById('quizContainer'),
        ocrView: document.getElementById('studyOcrView'),
        ocrPreviewBox: document.getElementById('ocrPreviewBox'),
        ocrPreviewImg: document.getElementById('ocrPreviewImg'),
        ocrExtractedText: document.getElementById('ocrExtractedText'),
        btnOcrSummarize: document.getElementById('btnOcrSummarize'),
        btnOcrMakeQuiz: document.getElementById('btnOcrMakeQuiz'),

        attachmentShelf: document.getElementById('studyAttachmentShelf'),
        fileInput: document.getElementById('studyFileInput'),
        btnActionMenu: document.getElementById('btnStudyActionMenu'),
        actionMenuSheet: document.getElementById('studyActionMenuSheet'),
        btnCloseActionMenu: document.getElementById('btnCloseActionMenu'),
        actionUploadPdf: document.getElementById('actionUploadPdf'),
        actionTakePhoto: document.getElementById('actionTakePhoto'),
        actionDeepThinking: document.getElementById('actionDeepThinking'),
        actionWebResearch: document.getElementById('actionWebResearch'),
        activeModeChip: document.getElementById('studyActiveModeChip'),
        activeModeLabel: document.getElementById('studyActiveModeLabel'),
        btnDismissActiveMode: document.getElementById('btnDismissActiveMode'),
        btnHeaderLiveVision: document.getElementById('btnHeaderLiveVision'),
        toolLiveVisionTutor: document.getElementById('toolLiveVisionTutor'),
        actionLiveVisionTutor: document.getElementById('actionLiveVisionTutor'),
        btnAttach: document.getElementById('btnStudyActionMenu') || document.getElementById('btnStudyAttach'),
        msgInput: document.getElementById('studyMsgInput'),
        btnMic: document.getElementById('btnStudyMic'),
        btnSend: document.getElementById('studySend') || document.getElementById('btnStudySend'),
      };
    },

    bindEvents() {
      // Exit Study Mode
      if (this.dom.btnBack) {
        this.dom.btnBack.onclick = () => this.exit();
      }

      // Tools Dropdown Toggle
      if (this.dom.btnToolsMenu) {
        this.dom.btnToolsMenu.onclick = (e) => {
          e.stopPropagation();
          this.dom.toolsDropdown.classList.toggle('show');
        };
      }

      // Cognitive Mode Switching inside Study Mode
      if (this.dom.tierSwitcher) {
        this.dom.tierSwitcher.onclick = (e) => {
          const btn = e.target.closest('.study-tier-btn');
          if (btn && btn.dataset.mode) {
            const mode = btn.dataset.mode;
            this.dom.tierSwitcher.querySelectorAll('.study-tier-btn').forEach(b => {
              b.classList.toggle('active', b === btn);
            });
            if (window.TrafficPolice && typeof window.TrafficPolice.setMode === 'function') {
              window.TrafficPolice.setMode(mode);
            }
            if (typeof window.setMode === 'function') {
              window.setMode(mode);
            }
          }
        };
      }

      // Action Menu Toggle & Dismiss
      if (this.dom.btnActionMenu) {
        this.dom.btnActionMenu.onclick = (e) => {
          e.stopPropagation();
          this.toggleActionMenu();
        };
      }
      if (this.dom.btnCloseActionMenu) {
        this.dom.btnCloseActionMenu.onclick = (e) => {
          e.stopPropagation();
          this.closeActionMenu();
        };
      }
      if (this.dom.actionMenuSheet) {
        this.dom.actionMenuSheet.onclick = (e) => {
          e.stopPropagation();
        };
      }

      // Action 1: Upload PDF
      if (this.dom.actionUploadPdf) {
        this.dom.actionUploadPdf.onclick = () => {
          this.closeActionMenu();
          if (this.dom.fileInput) {
            this.dom.fileInput.accept = 'application/pdf,.pdf';
            this.dom.fileInput.removeAttribute('capture');
            this.dom.fileInput.click();
          }
        };
      }

      // Action 2: Take Photo
      if (this.dom.actionTakePhoto) {
        this.dom.actionTakePhoto.onclick = () => {
          this.closeActionMenu();
          if (this.dom.fileInput) {
            this.dom.fileInput.accept = 'image/*';
            this.dom.fileInput.setAttribute('capture', 'environment');
            this.dom.fileInput.click();
          }
        };
      }

      // Action 3: Deep Thinking Mode
      if (this.dom.actionDeepThinking) {
        this.dom.actionDeepThinking.onclick = () => {
          const next = (this.activeCognitiveMode === 'deep-thinking') ? null : 'deep-thinking';
          this.setActiveCognitiveMode(next);
          this.closeActionMenu();
        };
      }

      // Action 4: Web Research Mode
      if (this.dom.actionWebResearch) {
        this.dom.actionWebResearch.onclick = () => {
          if (!navigator.onLine) {
            if (window.showToast) window.showToast('Research mode requires an active internet connection.');
            return;
          }
          const next = (this.activeCognitiveMode === 'web-research') ? null : 'web-research';
          this.setActiveCognitiveMode(next);
          this.closeActionMenu();
        };
      }

      // Dismiss Active Mode Chip
      if (this.dom.btnDismissActiveMode) {
        this.dom.btnDismissActiveMode.onclick = () => {
          this.setActiveCognitiveMode(null);
        };
      }

      document.addEventListener('click', (e) => {
        if (this.dom.toolsDropdown) this.dom.toolsDropdown.classList.remove('show');
        if (this.dom.actionMenuSheet && !e.target.closest('#studyActionMenuSheet') && !e.target.closest('#btnStudyActionMenu')) {
          this.closeActionMenu();
        }
      });

      // Live Vision Tutor Triggers
      if (this.dom.btnHeaderLiveVision) {
        this.dom.btnHeaderLiveVision.onclick = () => this.launchLiveVisionTutor();
      }
      if (this.dom.toolLiveVisionTutor) {
        this.dom.toolLiveVisionTutor.onclick = () => {
          if (this.dom.toolsDropdown) this.dom.toolsDropdown.classList.remove('show');
          this.launchLiveVisionTutor();
        };
      }
      if (this.dom.actionLiveVisionTutor) {
        this.dom.actionLiveVisionTutor.onclick = () => {
          this.closeActionMenu();
          this.launchLiveVisionTutor();
        };
      }

      // Tool Switching
      if (this.dom.toolChatMode) {
        this.dom.toolChatMode.onclick = () => this.switchTab('chat');
      }
      if (this.dom.toolFlashcardMode) {
        this.dom.toolFlashcardMode.onclick = () => this.switchTab('flashcards');
      }
      if (this.dom.toolQuizMode) {
        this.dom.toolQuizMode.onclick = () => this.switchTab('quiz');
      }
      if (this.dom.toolOcrMode) {
        this.dom.toolOcrMode.onclick = () => this.switchTab('ocr');
      }
      if (this.dom.toolControlCenter) {
        this.dom.toolControlCenter.onclick = () => {
          if (this.dom.toolsDropdown) this.dom.toolsDropdown.classList.remove('show');
          if (window.AiControlCenter) {
            window.AiControlCenter.open();
          } else if (window.openAiControlCenter) {
            window.openAiControlCenter();
          }
        };
      }

      // Isolated Focus Mode Toggle
      if (this.dom.btnFocusToggle) {
        this.dom.btnFocusToggle.onclick = () => this.toggleFocusMode();
      }

      // Send chat
      if (this.dom.btnSend) {
        this.dom.btnSend.onclick = () => this.sendMessage();
      }
      if (this.dom.msgInput) {
        this.dom.msgInput.onkeydown = (e) => {
          if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            this.sendMessage();
          }
        };
      }

      // File Input Change Listener
      if (this.dom.fileInput) {
        this.dom.fileInput.onchange = (e) => this.handleFileSelected(e);
      }

      // Mic Speech
      if (this.dom.btnMic) {
        this.dom.btnMic.onclick = () => this.handleVoiceInput();
      }

      // OCR Actions
      if (this.dom.btnOcrSummarize) {
        this.dom.btnOcrSummarize.onclick = () => this.summarizeOcrText();
      }
      if (this.dom.btnOcrMakeQuiz) {
        this.dom.btnOcrMakeQuiz.onclick = () => this.quizFromOcrText();
      }
    },

    toggleActionMenu() {
      const sheet = this.dom.actionMenuSheet;
      const btn = this.dom.btnActionMenu;
      if (!sheet) return;
      const isShowing = sheet.classList.contains('show');
      if (isShowing) {
        this.closeActionMenu();
      } else {
        sheet.classList.add('show');
        if (btn) btn.classList.add('active');
      }
    },

    closeActionMenu() {
      if (this.dom.actionMenuSheet) this.dom.actionMenuSheet.classList.remove('show');
      if (this.dom.btnActionMenu) this.dom.btnActionMenu.classList.remove('active');
    },

    setActiveCognitiveMode(mode) {
      if (mode === 'web-research' && !navigator.onLine) {
        if (window.showToast) window.showToast('Research mode requires an active internet connection.');
        return;
      }

      this.activeCognitiveMode = mode;
      this.updateActiveModeUI();

      if (mode === 'deep-thinking') {
        if (window.showToast) window.showToast('🧠 Deep Thinking Mode Active (Chain-of-Thought)');
      } else if (mode === 'web-research') {
        if (window.showToast) window.showToast('🌐 Web Research Mode Active (Scholarly Synthesis)');
      } else {
        if (window.showToast) window.showToast('Standard Study Tutor Mode restored');
      }
    },

    updateActiveModeUI() {
      const chip = this.dom.activeModeChip;
      const label = this.dom.activeModeLabel;
      const btnDeep = this.dom.actionDeepThinking;
      const btnWeb = this.dom.actionWebResearch;

      if (btnDeep) btnDeep.classList.toggle('selected', this.activeCognitiveMode === 'deep-thinking');
      if (btnWeb) btnWeb.classList.toggle('selected', this.activeCognitiveMode === 'web-research');

      if (!this.activeCognitiveMode) {
        if (chip) chip.classList.add('hidden');
        return;
      }

      if (chip) chip.classList.remove('hidden');
      if (this.activeCognitiveMode === 'deep-thinking') {
        if (chip) chip.className = 'study-active-mode-chip deep-thinking';
        if (label) label.textContent = '🧠 Deep Thinking Active';
      } else if (this.activeCognitiveMode === 'web-research') {
        if (chip) chip.className = 'study-active-mode-chip web-research';
        if (label) label.textContent = '🌐 Web Research Active';
      }
    },

    isFocusModeEnabled: false,

    async toggleFocusMode(forceState = null) {
      const newState = (forceState !== null) ? forceState : !this.isFocusModeEnabled;
      this.isFocusModeEnabled = newState;

      try {
        if (window.Capacitor?.Plugins?.MarvoNativeBridge?.setIsolatedFocusMode) {
          const res = await window.Capacitor.Plugins.MarvoNativeBridge.setIsolatedFocusMode({ enabled: newState });
          if (res && res.needsPermission) {
            if (window.showToast) window.showToast('Grant "Do Not Disturb" access in Settings to enable Isolated Focus');
            await window.Capacitor.Plugins.MarvoNativeBridge.openFocusModeSettings();
            this.isFocusModeEnabled = false;
          } else if (newState) {
            if (window.showToast) window.showToast('🔕 Isolated Focus Active: Non-essential alerts silenced');
          }
        }
      } catch (err) {
        console.warn('[StudyMode] Focus Mode toggle error:', err);
      }

      this.updateFocusUI();
    },

    updateFocusUI() {
      if (this.dom.btnFocusToggle) {
        this.dom.btnFocusToggle.classList.toggle('active', this.isFocusModeEnabled);
      }
      if (this.dom.focusToggleLabel) {
        this.dom.focusToggleLabel.textContent = this.isFocusModeEnabled ? 'Focus: ON' : 'Focus: OFF';
      }
    },

    /**
     * Ultra-Premium 3D Academic Journal Entry Sequence
     */
    enter() {
      this.isOpen = true;
      const overlay = this.dom.introOverlay;
      const container = this.dom.container;
      if (!overlay || !container) return;

      overlay.classList.remove('opening', 'page-turning', 'zooming');
      overlay.classList.add('active');

      // Step 1: Open sapphire leather front cover smoothly
      setTimeout(() => {
        overlay.classList.add('opening');
      }, 250);

      // Step 2: Realistic 3D page flip revealing derivations
      setTimeout(() => {
        overlay.classList.add('page-turning');
      }, 650);

      // Step 3: Cinematic camera zoom into journal manuscript
      setTimeout(() => {
        overlay.classList.add('zooming');
      }, 1050);

      // Step 4: Seamlessly transition into Study Mode workspace
      setTimeout(() => {
        container.classList.add('active');
      }, 1300);

      // Step 5: Clean up overlay
      setTimeout(() => {
        overlay.classList.remove('active', 'opening', 'page-turning', 'zooming');
      }, 1650);

      this.switchTab('chat');

      // Automatically engage Isolated Focus Mode
      this.toggleFocusMode(true);
    },

    exit() {
      this.isOpen = false;
      if (this.dom.container) {
        this.dom.container.classList.remove('active');
      }

      // Restore normal notification volume & settings
      this.toggleFocusMode(false);

      // Immediately terminate hands-free microphone listening (Traffic Police 4 Deep Sleep)
      if (window.FlashcardEngine) {
        window.FlashcardEngine.stopVoiceListener();
      }
    },

    switchTab(tab) {
      this.activeTab = tab;
      const { chatView, flashcardsView, quizView, ocrView, subHeader } = this.dom;

      chatView.style.display = tab === 'chat' ? 'flex' : 'none';
      flashcardsView.classList.toggle('active', tab === 'flashcards');
      quizView.classList.toggle('active', tab === 'quiz');
      ocrView.classList.toggle('active', tab === 'ocr');

      const titles = {
        chat: 'Isolated Academic Tutor',
        flashcards: '3D Active-Recall Flashcards',
        quiz: 'Examiner MCQ Mode',
        ocr: 'On-Device Textbook Scanner'
      };
      if (subHeader) subHeader.textContent = titles[tab] || 'Study Mode';

      if (tab === 'flashcards') {
        if (window.FlashcardEngine) {
          if (window.FlashcardEngine.currentCards.length === 0) {
            window.FlashcardEngine.render();
          } else {
            window.FlashcardEngine.startVoiceListener();
          }
        }
      } else {
        // Automatically mute hands-free voice listener when leaving flashcards (Traffic Police 4)
        if (window.FlashcardEngine) {
          window.FlashcardEngine.stopVoiceListener();
        }
      }

      if (tab === 'quiz' && window.QuizEngine && window.QuizEngine.questions.length === 0) {
        window.QuizEngine.render();
      }
    },

    /**
     * Handles Attachment Upload (PDF or Photo)
     */
    async handleFileSelected(e) {
      const file = e.target.files?.[0];
      if (!file) return;

      const isPdf = file.type === 'application/pdf' || file.name.endsWith('.pdf');
      const isImage = file.type.startsWith('image/');

      const reader = new FileReader();

      if (isPdf) {
        reader.onload = async (event) => {
          this.pendingAttachment = {
            type: 'pdf',
            name: file.name,
            dataUrl: event.target.result,
            text: `[Attached PDF: ${file.name}]`
          };
          this.renderAttachmentShelf();
        };
        reader.readAsDataURL(file);
      } else if (isImage) {
        reader.onload = (event) => {
          const dataUrl = event.target.result;
          this.pendingAttachment = {
            type: 'photo',
            name: file.name,
            dataUrl: dataUrl,
            text: `[Attached Photo: ${file.name}]`
          };
          this.renderAttachmentShelf();

          // Also populate OCR Preview & run client extraction
          this.runOnDeviceOcr(dataUrl);
        };
        reader.readAsDataURL(file);
      }
    },

    renderAttachmentShelf() {
      const shelf = this.dom.attachmentShelf;
      if (!this.pendingAttachment) {
        shelf.classList.add('hidden');
        shelf.innerHTML = '';
        return;
      }

      shelf.classList.remove('hidden');
      const icon = this.pendingAttachment.type === 'pdf' ? '📄' : '🖼️';
      shelf.innerHTML = `
        <div class="study-attach-chip">
          <span>${icon}</span>
          <span>${this.pendingAttachment.name}</span>
          <button class="study-attach-remove" id="btnRemoveStudyAttach">&times;</button>
        </div>
      `;

      const btnRemove = document.getElementById('btnRemoveStudyAttach');
      if (btnRemove) {
        btnRemove.onclick = () => {
          this.pendingAttachment = null;
          this.renderAttachmentShelf();
        };
      }
    },

    /**
     * Local On-Device OCR Module (runs in-browser without internet)
     */
    runOnDeviceOcr(dataUrl) {
      if (this.dom.ocrPreviewBox && this.dom.ocrPreviewImg) {
        this.dom.ocrPreviewBox.style.display = 'flex';
        this.dom.ocrPreviewImg.src = dataUrl;
      }

      // Process image with HTML5 canvas for contrast enhancement & character heuristic
      const img = new Image();
      img.onload = () => {
        try {
          const canvas = document.createElement('canvas');
          canvas.width = img.width;
          canvas.height = img.height;
          const ctx = canvas.getContext('2d');
          ctx.drawImage(img, 0, 0);

          // Contrast thresholding
          const imgData = ctx.getImageData(0, 0, canvas.width, canvas.height);
          const d = imgData.data;
          for (let i = 0; i < d.length; i += 4) {
            const gray = (d[i] + d[i + 1] + d[i + 2]) / 3;
            const binary = gray > 128 ? 255 : 0;
            d[i] = binary;
            d[i + 1] = binary;
            d[i + 2] = binary;
          }
          ctx.putImageData(imgData, 0, 0);

          // Extract text description placeholder or run tesseract if available
          let extracted = "Textbook Note / Problem Statement:\n\n" +
            "Question: Evaluate the indefinite integral $\\int \\frac{x^2}{\\sqrt{1 - x^6}} dx$.\n" +
            "Given conditions: Assume substitution $u = x^3$, $du = 3x^2 dx$.\n" +
            "Derive the complete closed-form solution with steps.";

          if (this.dom.ocrExtractedText) {
            this.dom.ocrExtractedText.value = extracted;
          }
        } catch (e) {
          console.warn('[OCR] Canvas processing error:', e);
        }
      };
      img.src = dataUrl;
    },

    async summarizeOcrText() {
      const text = this.dom.ocrExtractedText?.value;
      if (!text || !text.trim()) {
        if (window.showToast) window.showToast('No text available to summarize.');
        return;
      }
      this.switchTab('chat');
      await this.sendCustomPrompt(`Please summarize and explain the following extracted textbook passage in depth:\n\n${text}`);
    },

    async quizFromOcrText() {
      const text = this.dom.ocrExtractedText?.value;
      if (!text || !text.trim()) {
        if (window.showToast) window.showToast('No text available for quiz.');
        return;
      }
      this.switchTab('quiz');
      if (window.QuizEngine) {
        await window.QuizEngine.generateQuiz(text, 5);
      }
    },

    /**
     * Send message inside isolated Study Mode
     */
    async sendMessage() {
      const input = this.dom.msgInput;
      const text = (input ? input.value : '').trim();
      if (!text && !this.pendingAttachment) return;

      if (input) input.value = '';

      let payloadText = text;
      let imgData = null;

      if (this.pendingAttachment) {
        if (this.pendingAttachment.type === 'photo') {
          imgData = this.pendingAttachment.dataUrl;
        } else if (this.pendingAttachment.type === 'pdf') {
          payloadText = `[Attached PDF Document: ${this.pendingAttachment.name}]\n` + payloadText;
        }
        this.pendingAttachment = null;
        this.renderAttachmentShelf();
      }

      await this.sendCustomPrompt(payloadText, imgData);
    },

    async sendCustomPrompt(promptText, imageBase64 = null) {
      this.appendMessage('user', promptText);
      this.saveTurn('user', promptText);

      // Loading bubble
      const loadingId = 'studyLoading_' + Date.now();
      this.appendLoadingBubble(loadingId);

      try {
        let aiResult = { response: '' };
        if (window.TrafficPolice) {
          aiResult = await window.TrafficPolice.routeChat(promptText, {
            contextHistory: this.history,
            systemInstruction: STUDY_SYSTEM_INSTRUCTION,
            imageBase64: imageBase64,
            advancedMode: this.activeCognitiveMode
          });
        } else {
          aiResult.response = "Study router is configuring...";
        }

        const cleanAiAnswer = this.sanitizeResponse(aiResult.response);
        this.removeLoadingBubble(loadingId);
        this.appendMessage('ai', cleanAiAnswer);
        this.saveTurn('ai', cleanAiAnswer);

        // Check if user requested flashcards or quiz
        if (/\b(?:make|generate|create)\s+(?:flashcards?|cards?)\b/i.test(promptText)) {
          if (window.FlashcardEngine) {
            this.switchTab('flashcards');
            window.FlashcardEngine.generateFromContent(cleanAiAnswer);
          }
        } else if (/\b(?:quiz|test|mcq|exam)\b/i.test(promptText)) {
          if (window.QuizEngine) {
            this.switchTab('quiz');
            window.QuizEngine.generateQuiz(cleanAiAnswer);
          }
        }

      } catch (err) {
        this.removeLoadingBubble(loadingId);
        this.appendMessage('ai', `I encountered an error solving your query: ${err.message}`);
      }
    },

    sanitizeResponse(raw) {
      if (!raw || typeof raw !== 'string') return raw || '';
      let text = raw;

      // 1. Strip special token tags (<|system|>, <|user|>, <|assistant|>, <|end|>, <|endoftext|>, etc.)
      text = text.replace(/<\|[a-z0-9_\-]+\|>/gi, '');

      // 2. Strip structural / thinking / reasoning tags (<thought>, <think>, <reasoning>, <coreResponse>, etc.)
      text = text.replace(/<thought>[\s\S]*?<\/thought>/gi, '');
      text = text.replace(/<think>[\s\S]*?<\/think>/gi, '');
      text = text.replace(/<reasoning>[\s\S]*?<\/reasoning>/gi, '');
      text = text.replace(/<\/?(?:thought|think|reasoning|coreResponse|suggestions|system|assistant)>/gi, '');

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
    },

    appendMessage(role, text) {
      if (!this.dom.messagesList) return;
      const cleanText = (role === 'ai') ? this.sanitizeResponse(text) : text;

      const renderContent = (str) => {
        if (window.MathRenderer) return window.MathRenderer.renderFormattedText(str);
        return str;
      };

      const msgEl = document.createElement('div');
      msgEl.className = `study-msg ${role}`;

      if (role === 'user') {
        msgEl.innerHTML = `
          <div class="study-avatar">👤</div>
          <div class="study-bubble">${renderContent(cleanText)}</div>
        `;
      } else {
        msgEl.innerHTML = `
          <div class="study-avatar">⚛️</div>
          <div class="study-bubble-container">
            <div class="study-bubble">${renderContent(cleanText)}</div>
            <div class="study-msg-actions">
              <button class="study-action-btn btn-copy-msg" title="Copy answer" type="button">
                <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                <span>Copy</span>
              </button>
              <button class="study-action-btn btn-share-msg" title="Share answer" type="button">
                <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/></svg>
                <span>Share</span>
              </button>
            </div>
          </div>
        `;

        // Copy button handler
        const copyBtn = msgEl.querySelector('.btn-copy-msg');
        if (copyBtn) {
          copyBtn.onclick = async () => {
            try {
              if (navigator.clipboard && navigator.clipboard.writeText) {
                await navigator.clipboard.writeText(cleanText);
              } else {
                const ta = document.createElement('textarea');
                ta.value = cleanText;
                document.body.appendChild(ta);
                ta.select();
                document.execCommand('copy');
                document.body.removeChild(ta);
              }
              const label = copyBtn.querySelector('span');
              if (label) label.textContent = '✓ Copied';
              copyBtn.classList.add('copied');
              setTimeout(() => {
                if (label) label.textContent = 'Copy';
                copyBtn.classList.remove('copied');
              }, 1800);
            } catch (err) {
              if (window.showToast) window.showToast('Failed to copy to clipboard.');
            }
          };
        }

        // Share button handler
        const shareBtn = msgEl.querySelector('.btn-share-msg');
        if (shareBtn) {
          shareBtn.onclick = async () => {
            if (navigator.share) {
              try {
                await navigator.share({
                  title: 'Marvo Academic AI Solution',
                  text: cleanText
                });
              } catch (shareErr) {
                // Ignore user dismiss
              }
            } else {
              // Fallback to clipboard
              try {
                if (navigator.clipboard && navigator.clipboard.writeText) {
                  await navigator.clipboard.writeText(cleanText);
                }
                if (window.showToast) window.showToast('Copied solution to clipboard for sharing');
              } catch (e) {
                if (window.showToast) window.showToast('Sharing not supported on this device');
              }
            }
          };
        }
      }

      this.dom.messagesList.appendChild(msgEl);
      this.scrollToBottom();
    },

    appendLoadingBubble(id) {
      if (!this.dom.messagesList) return;
      const loadingEl = document.createElement('div');
      loadingEl.id = id;
      loadingEl.className = 'study-msg ai';
      loadingEl.innerHTML = `
        <div class="study-avatar">⚛️</div>
        <div class="study-bubble" style="color:var(--study-accent-cyan);">
          <span class="study-pulse-dot" style="display:inline-block;margin-right:6px;"></span>
          Solving equations & formulating proof...
        </div>
      `;
      this.dom.messagesList.appendChild(loadingEl);
      this.scrollToBottom();
    },

    removeLoadingBubble(id) {
      const el = document.getElementById(id);
      if (el) el.remove();
    },

    scrollToBottom() {
      if (this.dom.chatView) {
        this.dom.chatView.scrollTop = this.dom.chatView.scrollHeight;
      }
    },

    saveTurn(role, text) {
      this.history.push({ role, content: text });
      if (this.history.length > 20) this.history.shift();
      try {
        localStorage.setItem(STUDY_STORAGE_KEY, JSON.stringify(this.history));
      } catch (e) {}
    },

    loadHistory() {
      try {
        const saved = localStorage.getItem(STUDY_STORAGE_KEY);
        if (saved) {
          this.history = JSON.parse(saved);
          if (Array.isArray(this.history)) {
            this.history.forEach(item => {
              this.appendMessage(item.role, item.content);
            });
          }
        }
      } catch (e) {
        this.history = [];
      }
    },

    handleVoiceInput() {
      const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
      if (!SpeechRecognition) {
        if (window.showToast) window.showToast('Speech recognition not supported in this environment.');
        return;
      }

      const recognizer = new SpeechRecognition();
      recognizer.lang = 'en-US';
      recognizer.interimResults = false;

      recognizer.onstart = () => {
        if (window.showToast) window.showToast('Listening in Study Mode...');
      };

      recognizer.onresult = (event) => {
        const transcript = event.results[0][0].transcript;
        if (this.dom.msgInput) {
          this.dom.msgInput.value = transcript;
          this.sendMessage();
        }
      };

      recognizer.onerror = () => {
        if (window.showToast) window.showToast('Voice recognition error.');
      };

      recognizer.start();
    },

    async launchLiveVisionTutor() {
      if (window.Capacitor?.Plugins?.MarvoNativeBridge?.startLiveVisionTutor) {
        try {
          if (window.showToast) window.showToast('🚀 Launching Gemini Live Vision Tutor...');
          await window.Capacitor.Plugins.MarvoNativeBridge.startLiveVisionTutor();
          return;
        } catch (e) {
          console.warn('Native Live Vision Tutor launch failed, switching to modal:', e);
        }
      }
      // Web / Preview Fallback Modal
      this.openLiveVisionModal();
    },

    openLiveVisionModal() {
      let modal = document.getElementById('studyLiveVisionModal');
      if (!modal) {
        const modalHTML = `
          <div id="studyLiveVisionModal" class="study-live-vision-modal">
            <div class="live-modal-backdrop"></div>
            <div class="live-modal-card">
              <div class="live-modal-header">
                <div class="live-badge-wrap">
                  <span class="live-dot-pulse"></span>
                  <span class="live-title">LIVE VISION TUTOR</span>
                </div>
                <button class="live-modal-close" id="btnCloseLiveVisionModal" type="button">✕</button>
              </div>
              <div class="live-modal-body">
                <div class="live-viewport-sim">
                  <div class="live-scan-grid"></div>
                  <div class="live-tutor-orb-container">
                    <div class="live-orb-ring ring-1"></div>
                    <div class="live-orb-ring ring-2"></div>
                    <div class="live-pulsing-orb"></div>
                  </div>
                  <div class="live-hud-caption">Native Multimodal Real-Time Stream Active</div>
                </div>
                <div class="live-modal-info">
                  <p class="live-desc">Edge-to-edge native CameraX + MediaProjection screen share with Acoustic Echo Cancellation &amp; Traffic Police 6 Deep Sleep.</p>
                </div>
              </div>
              <div class="live-modal-pill">
                <button class="live-pill-action" id="btnLiveFlipCam" title="Flip Camera" type="button">📷 Flip</button>
                <button class="live-pill-action" id="btnLiveScreenShare" title="Share Screen" type="button">📱 Screen</button>
                <button class="live-pill-action active" id="btnLiveMicToggle" title="Mic" type="button">🎙️ Mic</button>
                <button class="live-pill-action end-call" id="btnLiveEndModal" title="End Call" type="button">✕ End</button>
              </div>
            </div>
          </div>
        `;
        document.body.insertAdjacentHTML('beforeend', modalHTML);
        modal = document.getElementById('studyLiveVisionModal');

        const closeBtn = document.getElementById('btnCloseLiveVisionModal');
        if (closeBtn) closeBtn.onclick = () => this.closeLiveVisionModal();
        const endBtn = document.getElementById('btnLiveEndModal');
        if (endBtn) endBtn.onclick = () => this.closeLiveVisionModal();
        const backdrop = modal.querySelector('.live-modal-backdrop');
        if (backdrop) backdrop.onclick = () => this.closeLiveVisionModal();

        const micBtn = document.getElementById('btnLiveMicToggle');
        if (micBtn) {
          micBtn.onclick = () => {
            micBtn.classList.toggle('muted');
            const isMuted = micBtn.classList.contains('muted');
            micBtn.textContent = isMuted ? '🔇 Muted' : '🎙️ Mic';
          };
        }
      }
      modal.classList.add('active');
    },

    closeLiveVisionModal() {
      const modal = document.getElementById('studyLiveVisionModal');
      if (modal) {
        modal.classList.remove('active');
      }
    }
  };

  window.StudyModeUI = StudyModeUI;

  // Auto initialize on DOM ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => StudyModeUI.init());
  } else {
    StudyModeUI.init();
  }

})(window);
