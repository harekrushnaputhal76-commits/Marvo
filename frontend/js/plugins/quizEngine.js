/**
 * MARVO AI — Interactive Exam-Style Quiz & MCQ Engine
 * Module: js/plugins/quizEngine.js
 * 
 * Serves MCQs one-by-one with immediate active response handling,
 * tracks score, and generates an in-depth Result & Analysis report
 * with step-by-step mathematical explanations.
 */

(function(window) {
  'use strict';

  const QuizEngine = {
    questions: [],
    currentIndex: 0,
    userAnswers: [],
    containerEl: null,
    isComplete: false,

    init(containerEl) {
      this.containerEl = containerEl;
    },

    /**
     * Generates questions using the active LLM router.
     */
    async generateQuiz(topicOrContent, count = 5) {
      if (!topicOrContent || !topicOrContent.trim()) {
        throw new Error("No study material or topic provided for quiz generation.");
      }

      const prompt = `Act as an expert academic examiner. Generate exactly ${count} multiple choice questions (MCQ) based on the following content.
Strict requirement: Return ONLY a valid JSON array of objects with NO markdown fences, NO preamble.
Each object must strictly match this schema:
{
  "question": "Clear problem statement (use LaTeX for math/science equations like $E=mc^2$ or $\\\\int x dx$)",
  "options": [
    "Option A",
    "Option B",
    "Option C",
    "Option D"
  ],
  "correctIndex": 0, // Integer 0, 1, 2, or 3 corresponding to correct option
  "explanation": "Deep, step-by-step derivation and conceptual breakdown explaining why the correct option is right."
}

Content to test:
${topicOrContent}`;

      let rawResponse = "";
      if (window.TrafficPolice) {
        const result = await window.TrafficPolice.routeChat(prompt, {
          systemInstruction: "You are an examiner for competitive STEM examinations (CHSE/CBSE/JEE/NEET). Return raw JSON array only."
        });
        rawResponse = result.response;
      }

      const questions = this.parseQuizJson(rawResponse, topicOrContent);
      this.loadQuestions(questions);
      return questions;
    },

    /**
     * Safely parses JSON quiz response with heuristic fallback.
     */
    parseQuizJson(rawText, fallbackTopic) {
      try {
        let cleaned = rawText.replace(/```json/gi, '').replace(/```/g, '').trim();
        const jsonMatch = cleaned.match(/\[\s*\{[\s\S]*\}\s*\]/);
        if (jsonMatch) cleaned = jsonMatch[0];
        const parsed = JSON.parse(cleaned);
        if (Array.isArray(parsed) && parsed.length > 0) {
          return parsed;
        }
      } catch (err) {
        console.warn('[QuizEngine] Quiz JSON parsing failed, using fallback generator:', err);
      }

      // Fallback sample questions if JSON was malformed
      return [
        {
          question: `Regarding ${fallbackTopic.slice(0, 40)}: Which of the following statements represents the fundamental governing principle?`,
          options: [
            "It varies directly with the applied gradient",
            "It remains invariant under conservation of energy",
            "It decays exponentially over boundary conditions",
            "It is independent of initial frame parameters"
          ],
          correctIndex: 1,
          explanation: "Fundamental physics dictates that total energy in an isolated system remains constant over time according to the Law of Conservation of Energy."
        }
      ];
    },

    loadQuestions(questions) {
      this.questions = questions;
      this.currentIndex = 0;
      this.userAnswers = new Array(questions.length).fill(null);
      this.isComplete = false;
      this.render();
    },

    render() {
      if (!this.containerEl) return;
      if (!this.questions || this.questions.length === 0) {
        this.containerEl.innerHTML = `
          <div class="quiz-empty-state">
            <div class="empty-icon">📝</div>
            <h3>Exam Mode Ready</h3>
            <p>Upload your notes, snap a photo, or request a topic to begin a 1-by-1 exam style quiz.</p>
          </div>
        `;
        return;
      }

      if (this.isComplete) {
        this.renderResults();
        return;
      }

      const q = this.questions[this.currentIndex];
      const total = this.questions.length;
      const progressPct = Math.round(((this.currentIndex) / total) * 100);

      const renderMath = (text) => {
        if (window.MathRenderer) return window.MathRenderer.renderFormattedText(text);
        return text;
      };

      const letters = ['A', 'B', 'C', 'D'];

      this.containerEl.innerHTML = `
        <div class="quiz-exam-view">
          <!-- Exam Progress Header -->
          <div class="quiz-header">
            <div class="quiz-meta">
              <span class="quiz-badge">EXAM MODE</span>
              <span class="quiz-count">Question ${this.currentIndex + 1} of ${total}</span>
            </div>
            <div class="quiz-progress-bar">
              <div class="quiz-progress-fill" style="width: ${progressPct}%"></div>
            </div>
          </div>

          <!-- Question Body -->
          <div class="quiz-question-card">
            <div class="quiz-q-num">Q${this.currentIndex + 1}</div>
            <div class="quiz-q-text">${renderMath(q.question)}</div>
          </div>

          <!-- Options Grid (Served 1 by 1) -->
          <div class="quiz-options-list">
            ${q.options.map((opt, idx) => `
              <button class="quiz-option-btn" data-index="${idx}">
                <span class="opt-letter">${letters[idx]}</span>
                <span class="opt-text">${renderMath(opt)}</span>
              </button>
            `).join('')}
          </div>
        </div>
      `;

      // Bind option clicks
      const buttons = this.containerEl.querySelectorAll('.quiz-option-btn');
      buttons.forEach(btn => {
        btn.onclick = () => {
          const selectedIdx = parseInt(btn.getAttribute('data-index'), 10);
          this.handleAnswer(selectedIdx);
        };
      });
    },

    handleAnswer(selectedIdx) {
      this.userAnswers[this.currentIndex] = selectedIdx;
      
      // Briefly animate selection before moving to next
      const selectedBtn = this.containerEl.querySelector(`[data-index="${selectedIdx}"]`);
      if (selectedBtn) {
        selectedBtn.classList.add('selected');
      }

      setTimeout(() => {
        if (this.currentIndex < this.questions.length - 1) {
          this.currentIndex++;
          this.render();
        } else {
          this.isComplete = true;
          this.render();
        }
      }, 350);
    },

    /**
     * Renders detailed Result & Analysis screen
     */
    renderResults() {
      let correctCount = 0;
      this.questions.forEach((q, idx) => {
        if (this.userAnswers[idx] === q.correctIndex) {
          correctCount++;
        }
      });

      const total = this.questions.length;
      const scorePct = Math.round((correctCount / total) * 100);
      const letters = ['A', 'B', 'C', 'D'];

      const renderMath = (text) => {
        if (window.MathRenderer) return window.MathRenderer.renderFormattedText(text);
        return text;
      };

      let badgeColor = '#00f0ff';
      let verdict = 'Good Effort!';
      if (scorePct >= 80) {
        badgeColor = '#00ff88';
        verdict = 'Excellent Mastery! 🎯';
      } else if (scorePct < 50) {
        badgeColor = '#ff0055';
        verdict = 'Needs Revision 📚';
      }

      this.containerEl.innerHTML = `
        <div class="quiz-results-view">
          <div class="quiz-score-banner">
            <div class="score-circle" style="border-color: ${badgeColor};">
              <span class="score-number">${scorePct}%</span>
              <span class="score-sub">${correctCount}/${total} Correct</span>
            </div>
            <div class="score-verdict" style="color: ${badgeColor};">${verdict}</div>
          </div>

          <div class="analysis-section-title">
            <span>DETAILED QUESTION-BY-QUESTION ANALYSIS</span>
          </div>

          <div class="analysis-list">
            ${this.questions.map((q, idx) => {
              const userChoice = this.userAnswers[idx];
              const isCorrect = userChoice === q.correctIndex;
              return `
                <div class="analysis-card ${isCorrect ? 'correct' : 'incorrect'}">
                  <div class="analysis-card-header">
                    <span class="q-num-pill">Question ${idx + 1}</span>
                    <span class="q-status-badge ${isCorrect ? 'pass' : 'fail'}">
                      ${isCorrect ? '✓ Correct (+1.0)' : '✗ Incorrect (0.0)'}
                    </span>
                  </div>

                  <div class="analysis-q-prompt">${renderMath(q.question)}</div>

                  <div class="analysis-choices">
                    <div class="choice-row your-choice ${isCorrect ? 'is-correct' : 'is-wrong'}">
                      <strong>Your Answer:</strong> ${userChoice !== null ? `${letters[userChoice]}. ${renderMath(q.options[userChoice])}` : 'Skipped'}
                    </div>
                    ${!isCorrect ? `
                      <div class="choice-row correct-choice">
                        <strong>Correct Answer:</strong> ${letters[q.correctIndex]}. ${renderMath(q.options[q.correctIndex])}
                      </div>
                    ` : ''}
                  </div>

                  <!-- Deep Step-by-Step Explanation -->
                  <div class="analysis-explanation">
                    <div class="exp-title">💡 Deep Step-by-Step Derivation & Explanation:</div>
                    <div class="exp-content">${renderMath(q.explanation)}</div>
                  </div>
                </div>
              `;
            }).join('')}
          </div>

          <div class="quiz-results-actions">
            <button class="quiz-action-btn primary" id="btnRestartQuiz">
              <svg viewBox="0 0 24 24" width="16" height="16"><polyline points="1 4 1 10 7 10" fill="none" stroke="currentColor" stroke-width="2"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10" fill="none" stroke="currentColor" stroke-width="2"/></svg>
              <span>Retake Quiz</span>
            </button>
          </div>
        </div>
      `;

      const btnRestart = document.getElementById('btnRestartQuiz');
      if (btnRestart) {
        btnRestart.onclick = () => {
          this.currentIndex = 0;
          this.userAnswers = new Array(this.questions.length).fill(null);
          this.isComplete = false;
          this.render();
        };
      }
    }
  };

  window.QuizEngine = QuizEngine;
})(window);
