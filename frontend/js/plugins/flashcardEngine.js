/**
 * MARVO AI — 3D Auto Flashcards Generator Engine
 * Module: js/plugins/flashcardEngine.js
 * 
 * Generates and renders 3D interactive, active-recall flashcards with
 * smooth CSS3 3D flips, progress tracking, and KaTeX math formulas.
 */

(function(window) {
  'use strict';

  const FlashcardEngine = {
    currentCards: [],
    currentIndex: 0,
    isFlipped: false,
    containerEl: null,

    /**
     * Initializes the engine with target DOM container.
     */
    init(containerEl) {
      this.containerEl = containerEl;
    },

    /**
     * Generates flashcards by sending structured prompt to TrafficPolice.
     * @param {string} sourceText - Study text, topic, or OCR extracted content
     * @param {number} count - Desired number of cards (default: 6)
     */
    async generateFromContent(sourceText, count = 6) {
      if (!sourceText || !sourceText.trim()) {
        throw new Error("No content or topic provided for flashcard generation.");
      }

      const prompt = `Generate exactly ${count} educational flashcards based on the following study content. 
Strict requirement: Return ONLY a valid JSON array of objects with NO markdown fences, NO extra text.
Each object must have:
"front": "Clear concept, question, or term (use LaTeX with $ or $$ for math)",
"back": "Concise, high-yield explanation, formula, or answer (use LaTeX for equations)",
"topic": "Specific subtopic or law"

Content:
${sourceText}`;

      let rawResponse = "";
      if (window.TrafficPolice) {
        const result = await window.TrafficPolice.routeChat(prompt, {
          systemInstruction: "You are a specialized flashcard generator for STEM and Higher Secondary exams. Return purely raw JSON."
        });
        rawResponse = result.response;
      }

      const cards = this.parseFlashcardJson(rawResponse, sourceText);
      this.loadCards(cards);
      return cards;
    },

    /**
     * Parses JSON response with robust fallback.
     */
    parseFlashcardJson(rawText, fallbackTopic = "Science & Math") {
      try {
        // Strip markdown code fences if model enclosed in ```json
        let cleaned = rawText.replace(/```json/gi, '').replace(/```/g, '').trim();
        const jsonMatch = cleaned.match(/\[\s*\{[\s\S]*\}\s*\]/);
        if (jsonMatch) {
          cleaned = jsonMatch[0];
        }
        const parsed = JSON.parse(cleaned);
        if (Array.isArray(parsed) && parsed.length > 0) {
          return parsed;
        }
      } catch (err) {
        console.warn('[FlashcardEngine] JSON parse failed, utilizing heuristic parser:', err);
      }

      // Heuristic fallback if model returned formatted bullets
      const cards = [];
      const lines = rawText.split('\n').filter(l => l.trim().length > 0);
      let currentCard = { front: '', back: '', topic: fallbackTopic.slice(0, 30) };

      for (let line of lines) {
        if (/^(?:Q|Front|Concept|Question):\s*/i.test(line)) {
          if (currentCard.front && currentCard.back) cards.push(currentCard);
          currentCard = { front: line.replace(/^(?:Q|Front|Concept|Question):\s*/i, ''), back: '', topic: 'Core Concept' };
        } else if (/^(?:A|Back|Answer|Explanation):\s*/i.test(line)) {
          currentCard.back = line.replace(/^(?:A|Back|Answer|Explanation):\s*/i, '');
        }
      }
      if (currentCard.front && currentCard.back) cards.push(currentCard);

      if (cards.length === 0) {
        cards.push({
          front: "Key Concept: " + fallbackTopic.slice(0, 50),
          back: rawText.slice(0, 200) || "Review study materials for detailed derivation.",
          topic: "Study Review"
        });
      }

      return cards;
    },

    loadCards(cards) {
      this.currentCards = cards;
      this.currentIndex = 0;
      this.isFlipped = false;
      this.render();
    },

    render() {
      if (!this.containerEl) return;
      if (!this.currentCards || this.currentCards.length === 0) {
        this.containerEl.innerHTML = `
          <div class="flashcard-empty-state">
            <div class="empty-icon">🗂️</div>
            <h3>No Flashcards Loaded</h3>
            <p>Upload a PDF, snap a textbook photo, or enter a topic to generate active-recall 3D flashcards.</p>
          </div>
        `;
        return;
      }

      const card = this.currentCards[this.currentIndex];
      const total = this.currentCards.length;
      const progressPct = Math.round(((this.currentIndex + 1) / total) * 100);
      const isBookmarked = this.isCardBookmarked(card);
      const bookmarkCount = this.getBookmarks().length;

      const renderMath = (text) => {
        if (window.MathRenderer) return window.MathRenderer.renderFormattedText(text);
        return text;
      };

      this.containerEl.innerHTML = `
        <div class="flashcard-deck-view">
          <div class="flashcard-deck-header">
            <div class="deck-mode-tabs">
              <button class="deck-tab-btn ${!this.isViewingBookmarks ? 'active' : ''}" id="btnDeckAll">All (${this.allCards.length || total})</button>
              <button class="deck-tab-btn ${this.isViewingBookmarks ? 'active' : ''}" id="btnDeckSaved">⭐ Saved (${bookmarkCount})</button>
            </div>

            <!-- Hands-Free Voice Indicator Chip -->
            <button class="fc-voice-chip active" id="fcVoiceIndicator" title="Hands-Free Voice Active (Say 'Flip', 'Next', 'Back')">
              <span class="fc-voice-dot"></span>
              <span>Voice: "Flip" / "Next"</span>
            </button>
            <div class="flashcard-progress-wrap">
              <span class="flashcard-counter">Card ${this.currentIndex + 1} of ${total}</span>
              <div class="flashcard-progress-bar">
                <div class="flashcard-progress-fill" style="width: ${progressPct}%"></div>
              </div>
            </div>
          </div>

          <!-- 3D Card Scene with Touch Swipe -->
          <div class="flashcard-scene" id="flashcardScene" title="Click or swipe left/right to navigate, say 'Flip' to reveal answer">
            <div class="flashcard-flipper ${this.isFlipped ? 'flipped' : ''}" id="flashcardFlipper">
              <!-- FRONT FACE -->
              <div class="flashcard-face flashcard-front">
                <div class="face-header-bar">
                  <span class="face-tag">QUESTION / CONCEPT</span>
                  <button class="fc-bookmark-btn ${isBookmarked ? 'bookmarked' : ''}" id="btnFcBookmarkFront" title="Save for revision">
                    ${isBookmarked ? '⭐ Saved' : '🔖 Bookmark'}
                  </button>
                </div>
                <div class="face-content">${renderMath(card.front)}</div>
                <div class="face-tap-hint">
                  <svg viewBox="0 0 24 24" width="14" height="14"><path d="M7 10l5 5 5-5z" fill="currentColor"/></svg>
                  Tap to Reveal Answer &bull; Swipe Left for Next
                </div>
              </div>

              <!-- BACK FACE -->
              <div class="flashcard-face flashcard-back">
                <div class="face-header-bar">
                  <span class="face-tag">ANSWER & DERIVATION</span>
                  <button class="fc-bookmark-btn ${isBookmarked ? 'bookmarked' : ''}" id="btnFcBookmarkBack" title="Save for revision">
                    ${isBookmarked ? '⭐ Saved' : '🔖 Bookmark'}
                  </button>
                </div>
                <div class="face-content">${renderMath(card.back)}</div>
                <div class="face-tap-hint">
                  <svg viewBox="0 0 24 24" width="14" height="14"><path d="M7 14l5-5 5 5z" fill="currentColor"/></svg>
                  Tap to Flip Back &bull; Swipe Right for Prev
                </div>
              </div>
            </div>
          </div>

          <!-- Deck Action Dock -->
          <div class="flashcard-dock">
            <button class="fc-dock-btn" id="btnFcPrev" ${this.currentIndex === 0 ? 'disabled' : ''}>
              <svg viewBox="0 0 24 24" width="16" height="16"><polyline points="15 18 9 12 15 6" fill="none" stroke="currentColor" stroke-width="2"/></svg>
              <span>Prev</span>
            </button>
            <button class="fc-dock-btn fc-flip-btn" id="btnFcFlip">
              <svg viewBox="0 0 24 24" width="16" height="16"><path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67" fill="none" stroke="currentColor" stroke-width="2"/></svg>
              <span>Flip Card</span>
            </button>
            <button class="fc-dock-btn" id="btnFcNext" ${this.currentIndex === total - 1 ? 'disabled' : ''}>
              <span>Next</span>
              <svg viewBox="0 0 24 24" width="16" height="16"><polyline points="9 18 15 12 9 6" fill="none" stroke="currentColor" stroke-width="2"/></svg>
            </button>
          </div>
        </div>
      `;

      // Bind interactions
      const scene = document.getElementById('flashcardScene');
      if (scene) {
        scene.onclick = (e) => {
          if (e.target.closest('.fc-bookmark-btn')) return;
          this.toggleFlip();
        };

        // Touch Swipe Navigation
        let startX = 0;
        let startY = 0;
        scene.addEventListener('touchstart', (e) => {
          if (e.touches && e.touches.length > 0) {
            startX = e.touches[0].clientX;
            startY = e.touches[0].clientY;
          }
        }, { passive: true });

        scene.addEventListener('touchend', (e) => {
          if (e.changedTouches && e.changedTouches.length > 0) {
            const endX = e.changedTouches[0].clientX;
            const endY = e.changedTouches[0].clientY;
            const diffX = endX - startX;
            const diffY = endY - startY;

            // Horizontal swipe threshold 45px (with lower vertical variance)
            if (Math.abs(diffX) > 45 && Math.abs(diffX) > Math.abs(diffY)) {
              if (diffX < 0) {
                // Swipe Left -> Next
                this.nextCard();
              } else {
                // Swipe Right -> Prev
                this.prevCard();
              }
            }
          }
        }, { passive: true });
      }

      // Bookmark Button handlers
      const btnBmFront = document.getElementById('btnFcBookmarkFront');
      const btnBmBack = document.getElementById('btnFcBookmarkBack');
      if (btnBmFront) btnBmFront.onclick = (e) => { e.stopPropagation(); this.toggleBookmark(card); };
      if (btnBmBack) btnBmBack.onclick = (e) => { e.stopPropagation(); this.toggleBookmark(card); };

      // Deck Filter Tabs
      const btnAll = document.getElementById('btnDeckAll');
      const btnSaved = document.getElementById('btnDeckSaved');
      if (btnAll) btnAll.onclick = () => this.showAllDeck();
      if (btnSaved) btnSaved.onclick = () => this.showBookmarksDeck();

      const btnFlip = document.getElementById('btnFcFlip');
      if (btnFlip) btnFlip.onclick = () => this.toggleFlip();

      const btnPrev = document.getElementById('btnFcPrev');
      if (btnPrev) btnPrev.onclick = () => this.prevCard();

      const btnNext = document.getElementById('btnFcNext');
      if (btnNext) btnNext.onclick = () => this.nextCard();

      const voiceChip = document.getElementById('fcVoiceIndicator');
      if (voiceChip) {
        voiceChip.onclick = () => {
          if (this.isVoiceActive) this.stopVoiceListener();
          else this.startVoiceListener();
        };
      }

      // Automatically engage low-power offline voice commands
      this.startVoiceListener();
    },

    // ═══════════ BOOKMARK REPOSITORY ═══════════
    BOOKMARK_STORAGE_KEY: 'marvo_bookmarked_flashcards',
    allCards: [],
    isViewingBookmarks: false,

    getBookmarks() {
      try {
        const raw = localStorage.getItem(this.BOOKMARK_STORAGE_KEY);
        return raw ? JSON.parse(raw) : [];
      } catch (e) {
        return [];
      }
    },

    isCardBookmarked(card) {
      if (!card) return false;
      const bms = this.getBookmarks();
      return bms.some(b => b.front === card.front && b.back === card.back);
    },

    toggleBookmark(card) {
      if (!card) return;
      let bms = this.getBookmarks();
      const idx = bms.findIndex(b => b.front === card.front && b.back === card.back);
      if (idx >= 0) {
        bms.splice(idx, 1);
        if (window.showToast) window.showToast('Bookmark removed from repository');
      } else {
        bms.unshift({
          front: card.front,
          back: card.back,
          topic: card.topic || 'Revision',
          savedAt: Date.now()
        });
        if (window.showToast) window.showToast('⭐ Card saved to Bookmark Repository!');
      }

      try {
        localStorage.setItem(this.BOOKMARK_STORAGE_KEY, JSON.stringify(bms));
      } catch (e) {}

      if (this.isViewingBookmarks) {
        this.currentCards = bms;
        if (this.currentIndex >= this.currentCards.length) {
          this.currentIndex = Math.max(0, this.currentCards.length - 1);
        }
      }
      this.render();
    },

    showAllDeck() {
      if (!this.isViewingBookmarks) return;
      this.isViewingBookmarks = false;
      this.currentCards = this.allCards.length > 0 ? this.allCards : this.currentCards;
      this.currentIndex = 0;
      this.isFlipped = false;
      this.render();
    },

    showBookmarksDeck() {
      const bms = this.getBookmarks();
      if (bms.length === 0) {
        if (window.showToast) window.showToast('No saved flashcards yet. Tap 🔖 Bookmark to save cards.');
        return;
      }
      this.isViewingBookmarks = true;
      if (this.allCards.length === 0) {
        this.allCards = [...this.currentCards];
      }
      this.currentCards = bms;
      this.currentIndex = 0;
      this.isFlipped = false;
      this.render();
    },

    toggleFlip() {
      this.isFlipped = !this.isFlipped;
      const flipper = document.getElementById('flashcardFlipper');
      if (flipper) {
        flipper.classList.toggle('flipped', this.isFlipped);
      }
    },

    nextCard() {
      if (this.currentIndex < this.currentCards.length - 1) {
        this.currentIndex++;
        this.isFlipped = false;
        this.render();
      }
    },

    prevCard() {
      if (this.currentIndex > 0) {
        this.currentIndex--;
        this.isFlipped = false;
        this.render();
      }
    },

    /* ═══════════ PHASE 3: HANDS-FREE VOICE FLASHCARDS ═══════════ */
    voiceRecognition: null,
    isVoiceActive: false,

    startVoiceListener() {
      if (this.isVoiceActive) return;
      const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
      if (!SpeechRecognition) {
        console.warn('[FlashcardEngine] SpeechRecognition API not supported on this browser');
        return;
      }

      try {
        const recognition = new SpeechRecognition();
        recognition.continuous = true;
        recognition.interimResults = false;
        recognition.lang = 'en-US';

        recognition.onstart = () => {
          this.isVoiceActive = true;
          this.updateVoiceBadge(true);
          console.log('[FlashcardEngine] Hands-Free Voice Listener active. Say "Flip", "Next", "Back".');
        };

        recognition.onresult = (event) => {
          for (let i = event.resultIndex; i < event.results.length; ++i) {
            if (event.results[i].isFinal) {
              const transcript = event.results[i][0].transcript.trim().toLowerCase();
              console.log('[FlashcardEngine] Voice keyword heard:', transcript);

              if (transcript.includes('flip') || transcript.includes('answer') || transcript.includes('turn') || transcript.includes('show') || transcript.includes('reveal')) {
                this.toggleFlip();
                if (window.showToast) window.showToast('Voice: Card Flipped');
              } else if (transcript.includes('next') || transcript.includes('forward') || transcript.includes('skip') || transcript.includes('ahead')) {
                this.nextCard();
                if (window.showToast) window.showToast('Voice: Next Card');
              } else if (transcript.includes('back') || transcript.includes('previous') || transcript.includes('prev')) {
                this.prevCard();
                if (window.showToast) window.showToast('Voice: Previous Card');
              }
            }
          }
        };

        recognition.onerror = (err) => {
          console.warn('[FlashcardEngine] Voice recognition warning:', err.error);
          if (err.error === 'not-allowed' || err.error === 'service-not-allowed') {
            this.stopVoiceListener();
          }
        };

        recognition.onend = () => {
          // Restart if still active and cards are mounted
          if (this.isVoiceActive && this.currentCards && this.currentCards.length > 0) {
            try {
              recognition.start();
            } catch (e) {
              this.isVoiceActive = false;
              this.updateVoiceBadge(false);
            }
          } else {
            this.isVoiceActive = false;
            this.updateVoiceBadge(false);
          }
        };

        recognition.start();
        this.voiceRecognition = recognition;
      } catch (err) {
        console.warn('[FlashcardEngine] Failed to initialize voice listener:', err);
      }
    },

    stopVoiceListener() {
      this.isVoiceActive = false;
      if (this.voiceRecognition) {
        try {
          this.voiceRecognition.abort();
        } catch (e) {}
        this.voiceRecognition = null;
      }
      this.updateVoiceBadge(false);
      console.log('[FlashcardEngine] Hands-Free Voice Listener stopped (Traffic Police Deep Sleep enforced).');
    },

    updateVoiceBadge(active) {
      const chip = document.getElementById('fcVoiceIndicator');
      if (chip) {
        chip.classList.toggle('active', active);
        chip.innerHTML = active
          ? `<span class="fc-voice-dot"></span> <span>Voice: "Flip" / "Next"</span>`
          : `<span class="fc-voice-dot off"></span> <span>Voice Muted</span>`;
      }
    }
  };

  window.FlashcardEngine = FlashcardEngine;
})(window);
