/**
 * MARVO AI — Math & Equation Typography Renderer
 * Module: js/utils/mathRenderer.js
 * 
 * Provides flawless LaTeX rendering for Higher Secondary Science (Physics, Chemistry)
 * and Advanced Mathematics (Differential Calculus, Integral Calculus, Linear Algebra).
 * Seamlessly integrates KaTeX across Regular Chat, Study Mode, Quizzes, and Flashcards.
 */

(function(window) {
  'use strict';

  const MathRenderer = {
    // Escape HTML special chars
    escapeHtml(str) {
      if (typeof str !== 'string') return '';
      return str
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
    },

    /**
     * Renders a pure LaTeX string directly to HTML using KaTeX.
     * @param {string} tex - LaTeX code string
     * @param {boolean} displayMode - True for block/display equations, false for inline
     * @returns {string} HTML string
     */
    renderLatex(tex, displayMode = false) {
      if (!tex || typeof tex !== 'string') return '';
      const trimmed = tex.trim();

      if (window.katex && typeof window.katex.renderToString === 'function') {
        try {
          return window.katex.renderToString(trimmed, {
            displayMode: !!displayMode,
            throwOnError: false,
            trust: true,
            strict: false
          });
        } catch (err) {
          console.warn('[MathRenderer] KaTeX error:', err);
          const tag = displayMode ? 'div' : 'span';
          return `<${tag} class="math-fallback ${displayMode ? 'block' : 'inline'}">${this.escapeHtml(trimmed)}</${tag}>`;
        }
      }

      // KaTeX not yet loaded fallback
      const tag = displayMode ? 'div' : 'span';
      return `<${tag} class="math-fallback ${displayMode ? 'block' : 'inline'}">${displayMode ? '$$' : '$'}${this.escapeHtml(trimmed)}${displayMode ? '$$' : '$'}</${tag}>`;
    },

    /**
     * Parses Markdown + LaTeX text and transforms math expressions into rendered KaTeX HTML.
     * Preserves code blocks, formatting, lists, tables, and paragraphs.
     * @param {string} rawText 
     * @returns {string} Fully rendered HTML
     */
    renderFormattedText(rawText) {
      if (!rawText || typeof rawText !== 'string') return '';

      // Check if user disabled KaTeX in settings
      const mathDisabled = localStorage.getItem('marvo.math.katex_enabled') === 'false';
      if (mathDisabled) {
        return this.escapeHtml(rawText).replace(/\n/g, '<br>');
      }

      let text = rawText;
      const codeBlocks = [];
      const mathBlocks = [];
      const mathInlines = [];

      // 1. Stash fenced code blocks (```lang ... ```)
      text = text.replace(/```([a-zA-Z0-9_-]*)\n([\s\S]*?)```/g, (match, lang, code) => {
        const idx = codeBlocks.length;
        codeBlocks.push(`<pre><code class="language-${lang || 'plaintext'}">${this.escapeHtml(code.trim())}</code></pre>`);
        return `%%CODEBLOCK_${idx}%%`;
      });

      // 2. Stash inline code (`code`)
      text = text.replace(/`([^`\n]+)`/g, (match, code) => {
        const idx = codeBlocks.length;
        codeBlocks.push(`<code>${this.escapeHtml(code)}</code>`);
        return `%%CODEBLOCK_${idx}%%`;
      });

      // 3. Stash block LaTeX: $$...$$ or \[...\] or \begin{equation}...\end{equation}
      text = text.replace(/(?:\$\$([\s\S]*?)\$\$|\\\[([\s\S]*?)\\\]|\\begin\{equation\*?\}([\s\S]*?)\\end\{equation\*?\})/g, (match, t1, t2, t3) => {
        const tex = (t1 || t2 || t3 || '').trim();
        const idx = mathBlocks.length;
        const rendered = this.renderLatex(tex, true);
        mathBlocks.push(rendered);
        return `\n%%MATHBLOCK_${idx}%%\n`;
      });

      // 4. Stash inline LaTeX: $...$ or \(...\)
      text = text.replace(/(?:\$([^\$\n\r]+?)\$|\\\(([\s\S]*?)\\\))/g, (match, t1, t2) => {
        const tex = (t1 || t2 || '').trim();
        // Ignore plain currency e.g. $50, $100.00
        if (!tex || /^\d+(?:[.,]\d+)?$/.test(tex)) {
          return match;
        }
        const idx = mathInlines.length;
        const rendered = this.renderLatex(tex, false);
        mathInlines.push(rendered);
        return `%%MATHINLINE_${idx}%%`;
      });

      // 5. Escape HTML in remaining plain text
      text = this.escapeHtml(text);

      // 6. Markdown typography & formatting
      text = text.replace(/^####\s+(.+)$/gm, '<h5>$1</h5>');
      text = text.replace(/^###\s+(.+)$/gm, '<h4>$1</h4>');
      text = text.replace(/^##\s+(.+)$/gm, '<h3>$1</h3>');
      text = text.replace(/^#\s+(.+)$/gm, '<h2>$1</h2>');
      text = text.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
      text = text.replace(/\*([^*]+)\*/g, '<em>$1</em>');
      text = text.replace(/~~([^~]+)~~/g, '<del>$1</del>');
      text = text.replace(/^\s*[-*]\s+(.+)$/gm, '<li>$1</li>');
      text = text.replace(/(<li>[\s\S]*?<\/li>)/g, '<ul>$1</ul>');
      text = text.replace(/<\/ul>\s*<ul>/g, ''); // consolidate consecutive lists
      text = text.replace(/\n\n+/g, '<br><br>');
      text = text.replace(/\n/g, '<br>');

      // 7. Restore Inline LaTeX
      text = text.replace(/%%MATHINLINE_(\d+)%%/g, (_, idx) => {
        return mathInlines[Number(idx)] || '';
      });

      // 8. Restore Block LaTeX
      text = text.replace(/%%MATHBLOCK_(\d+)%%/g, (_, idx) => {
        return `<div class="katex-display-wrapper">${mathBlocks[Number(idx)] || ''}</div>`;
      });

      // 9. Restore Code Blocks
      text = text.replace(/%%CODEBLOCK_(\d+)%%/g, (_, idx) => {
        return codeBlocks[Number(idx)] || '';
      });

      return text;
    },

    /**
     * Traverses a DOM element and renders any pending LaTeX text or re-renders math.
     * @param {HTMLElement} element 
     */
    renderElement(element) {
      if (!element) return;
      if (window.renderMathInElement && typeof window.renderMathInElement === 'function') {
        try {
          window.renderMathInElement(element, {
            delimiters: [
              { left: '$$', right: '$$', display: true },
              { left: '\\[', right: '\\]', display: true },
              { left: '$', right: '$', display: false },
              { left: '\\(', right: '\\)', display: false }
            ],
            throwOnError: false
          });
        } catch (e) {
          console.warn('[MathRenderer] renderMathInElement failed:', e);
        }
      }
    }
  };

  window.MathRenderer = MathRenderer;
})(window);
