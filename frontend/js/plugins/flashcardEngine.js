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

      const renderMath = (text) => {
        if (window.MathRenderer) return window.MathRenderer.renderFormattedText(text);
        return text;
      };

      this.containerEl.innerHTML = `
        <div class="flashcard-deck-view">
          <div class="flashcard-deck-header">
            <span class="flashcard-badge">${card.topic || 'Revision'}</span>
            <div class="flashcard-progress-wrap">
              <span class="flashcard-counter">Card ${this.currentIndex + 1} of ${total}</span>
              <div class="flashcard-progress-bar">
                <div class="flashcard-progress-fill" style="width: ${progressPct}%"></div>
              </div>
            </div>
          </div>

          <!-- 3D Card Scene -->
          <div class="flashcard-scene" id="flashcardScene" title="Click or tap to flip">
            <div class="flashcard-flipper ${this.isFlipped ? 'flipped' : ''}" id="flashcardFlipper">
              <!-- FRONT FACE -->
              <div class="flashcard-face flashcard-front">
                <div class="face-tag">QUESTION / CONCEPT</div>
                <div class="face-content">${renderMath(card.front)}</div>
                <div class="face-tap-hint">
                  <svg viewBox="0 0 24 24" width="14" height="14"><path d="M7 10l5 5 5-5z" fill="currentColor"/></svg>
                  Tap to Reveal Answer
                </div>
              </div>

              <!-- BACK FACE -->
              <div class="flashcard-face flashcard-back">
                <div class="face-tag">ANSWER & DERIVATION</div>
                <div class="face-content">${renderMath(card.back)}</div>
                <div class="face-tap-hint">
                  <svg viewBox="0 0 24 24" width="14" height="14"><path d="M7 14l5-5 5 5z" fill="currentColor"/></svg>
                  Tap to Flip Back
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
      if (scene) scene.onclick = () => this.toggleFlip();

      const btnFlip = document.getElementById('btnFcFlip');
      if (btnFlip) btnFlip.onclick = () => this.toggleFlip();

      const btnPrev = document.getElementById('btnFcPrev');
      if (btnPrev) btnPrev.onclick = () => this.prevCard();

      const btnNext = document.getElementById('btnFcNext');
      if (btnNext) btnNext.onclick = () => this.nextCard();
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
    }
  };

  window.FlashcardEngine = FlashcardEngine;
})(window);
