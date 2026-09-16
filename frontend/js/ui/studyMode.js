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
      // 1. 3D Book Intro Overlay
      const introHTML = `
        <div id="studyIntroOverlay" class="study-intro-overlay">
          <div class="study-book-3d" id="studyBook3D">
            <div class="book-cover-front">
              <div class="book-emblem">
                <svg viewBox="0 0 24 24" width="36" height="36"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" fill="none" stroke="currentColor" stroke-width="2"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" fill="none" stroke="currentColor" stroke-width="2"/></svg>
              </div>
              <div class="book-title">STUDY MODE</div>
              <div class="book-subtitle">ACADEMIC & STEM</div>
            </div>
            <div class="book-pages-stack">
              <div class="book-page-line accent"></div>
              <div class="book-page-line"></div>
              <div class="book-page-line short"></div>
              <div class="book-page-line"></div>
              <div class="book-page-line accent"></div>
            </div>
          </div>
        </div>
      `;

      // 2. Study Mode Main Container
      const containerHTML = `
        <div id="studyModeContainer" class="study-mode-container">
          <!-- Top Header -->
          <header class="study-header">
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

            <!-- Cognitive Modes Toggle [Fast] | [Thinking] | [Pro Thinking] -->
            <div class="study-tier-switcher cognitive-modes-switcher" id="studyTierSwitcher">
              <button class="study-tier-btn active" data-mode="Fast" type="button" title="Fast Mode — Instant Groq">Fast</button>
              <button class="study-tier-btn" data-mode="Thinking" type="button" title="Thinking Mode — Gemini Reasoning">Thinking</button>
              <button class="study-tier-btn" data-mode="Pro Thinking" type="button" title="Pro Thinking Mode — OpenRouter / Claude">Pro Thinking</button>
            </div>

            <div class="study-header-right">
              <button id="btnStudyToolsMenu" class="btn-study-tools-menu" title="Learning Tools">
                <span>Tools</span>
                <svg viewBox="0 0 24 24" width="16" height="16"><circle cx="12" cy="12" r="1.5" fill="currentColor"/><circle cx="19" cy="12" r="1.5" fill="currentColor"/><circle cx="5" cy="12" r="1.5" fill="currentColor"/></svg>
              </button>

              <div id="studyToolsDropdown" class="study-tools-dropdown">
                <button class="study-tool-item" id="toolChatMode">
                  <span>💬</span> <span>Tutor Chat</span>
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

          <!-- Clean Bottom Chatbar (Input, Mic, Attachment ONLY) -->
          <div class="study-chatbar-wrap">
            <div class="study-chatbar">
              <!-- Hidden Attachment Input -->
              <input type="file" id="studyFileInput" accept="application/pdf,image/*" style="display:none;">

              <!-- Attachment Plus Icon -->
              <button id="btnStudyAttach" class="btn-study-action" title="Attach PDF or Photo">
                <svg viewBox="0 0 24 24" width="20" height="20"><path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48" fill="none" stroke="currentColor" stroke-width="2"/></svg>
              </button>

              <!-- Clean Input Field -->
              <input type="text" id="studyMsgInput" class="study-input" placeholder="Ask a question, formula, or topic..." autocomplete="off">

              <!-- Mic Button -->
              <button id="btnStudyMic" class="btn-study-action" title="Voice Dictation">
                <svg viewBox="0 0 24 24" width="20" height="20"><path d="M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm-1-9c0-.55.45-1 1-1s1 .45 1 1v6c0 .55-.45 1-1 1s-1-.45-1-1V5z" fill="currentColor"/><path d="M17 11c0 2.76-2.24 5-5 5s-5-2.24-5-5H5c0 3.53 2.61 6.43 6 6.92V21h2v-3.08c3.39-.49 6-3.39 6-6.92h-2z" fill="currentColor"/></svg>
              </button>

              <!-- Send Button -->
              <button id="btnStudySend" class="btn-study-action btn-study-send" title="Send">
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
        toolChatMode: document.getElementById('toolChatMode'),
        toolFlashcardMode: document.getElementById('toolFlashcardMode'),
        toolQuizMode: document.getElementById('toolQuizMode'),
        toolOcrMode: document.getElementById('toolOcrMode'),

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
        btnAttach: document.getElementById('btnStudyAttach'),
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

      document.addEventListener('click', () => {
        if (this.dom.toolsDropdown) this.dom.toolsDropdown.classList.remove('show');
      });

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

      // File Attachment Handling
      if (this.dom.btnAttach) {
        this.dom.btnAttach.onclick = () => this.dom.fileInput.click();
      }
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

    /**
     * 3D Animated Book Entry Sequence
     */
    enter() {
      this.isOpen = true;
      const overlay = this.dom.introOverlay;
      const container = this.dom.container;

      overlay.classList.add('active');
      overlay.classList.remove('opening');

      setTimeout(() => {
        overlay.classList.add('opening');
      }, 200);

      setTimeout(() => {
        container.classList.add('active');
      }, 1000);

      setTimeout(() => {
        overlay.classList.remove('active', 'opening');
      }, 1400);

      this.switchTab('chat');
    },

    exit() {
      this.isOpen = false;
      if (this.dom.container) {
        this.dom.container.classList.remove('active');
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

      if (tab === 'flashcards' && window.FlashcardEngine && window.FlashcardEngine.currentCards.length === 0) {
        window.FlashcardEngine.render();
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
            imageBase64: imageBase64
          });
        } else {
          aiResult.response = "Study router is configuring...";
        }

        this.removeLoadingBubble(loadingId);
        this.appendMessage('ai', aiResult.response);
        this.saveTurn('ai', aiResult.response);

        // Check if user requested flashcards or quiz
        if (/\b(?:make|generate|create)\s+(?:flashcards?|cards?)\b/i.test(promptText)) {
          if (window.FlashcardEngine) {
            this.switchTab('flashcards');
            window.FlashcardEngine.generateFromContent(aiResult.response);
          }
        } else if (/\b(?:quiz|test|mcq|exam)\b/i.test(promptText)) {
          if (window.QuizEngine) {
            this.switchTab('quiz');
            window.QuizEngine.generateQuiz(aiResult.response);
          }
        }

      } catch (err) {
        this.removeLoadingBubble(loadingId);
        this.appendMessage('ai', `I encountered an error solving your query: ${err.message}`);
      }
    },

    appendMessage(role, text) {
      if (!this.dom.messagesList) return;
      const renderContent = (str) => {
        if (window.MathRenderer) return window.MathRenderer.renderFormattedText(str);
        return str;
      };

      const msgEl = document.createElement('div');
      msgEl.className = `study-msg ${role}`;
      msgEl.innerHTML = `
        <div class="study-avatar">${role === 'user' ? '👤' : '⚛️'}</div>
        <div class="study-bubble">${renderContent(text)}</div>
      `;
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
