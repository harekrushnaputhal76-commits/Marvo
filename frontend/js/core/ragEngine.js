/**
 * MARVO AI — Local RAG & Document Retrieval Engine
 * Module: js/core/ragEngine.js
 * 
 * Provides on-device document ingestion, chunking, and semantic/TF-IDF
 * vector similarity search. Integrates seamlessly with native Android
 * SQLite LocalRagEngine via MarvoNativeBridge, with client-side fallback.
 */

(function(window) {
  'use strict';

  const STOP_WORDS = new Set([
    'the', 'is', 'at', 'which', 'on', 'and', 'a', 'an', 'in', 'to', 'for',
    'of', 'or', 'by', 'with', 'this', 'that', 'it', 'as', 'are', 'from', 'be',
    'was', 'were', 'has', 'have', 'had', 'can', 'could', 'will', 'would', 'shall', 'should'
  ]);

  const RagEngine = {
    activeDocument: null,
    isIndexing: false,
    localChunks: [],

    init() {
      console.log('[RagEngine] Initialized.');
    },

    hasActiveDocument() {
      return this.activeDocument !== null;
    },

    getActiveDocument() {
      return this.activeDocument;
    },

    /**
     * Ingests a File object (PDF, TXT, MD).
     * @param {File} file
     * @returns {Promise<{docId: string, fileName: string, totalChunks: number}>}
     */
    async ingestFile(file) {
      if (!file) throw new Error('No file provided for RAG ingestion.');

      this.isIndexing = true;
      const fileName = file.name;
      const fileType = file.type || (fileName.endsWith('.pdf') ? 'application/pdf' : 'text/plain');

      try {
        const fileData = await this.readFile(file);
        let extractedText = '';

        if (typeof fileData === 'string' && fileData.startsWith('data:')) {
          // If binary / data URL
          extractedText = this.extractTextFromDataUri(fileData, fileName);
        } else {
          extractedText = String(fileData);
        }

        if (!extractedText || extractedText.trim().length === 0) {
          extractedText = `[Document: ${fileName} contains no raw text. Indexed for reference.]`;
        }

        // 1. Try Native Android Bridge
        if (window.Capacitor?.Plugins?.MarvoNativeBridge?.ingestDocument) {
          try {
            const nativeRes = await window.Capacitor.Plugins.MarvoNativeBridge.ingestDocument({
              fileName,
              content: extractedText,
              fileType
            });
            this.activeDocument = {
              docId: nativeRes.docId || 'doc_' + Date.now(),
              fileName,
              totalChunks: nativeRes.totalChunks || 1,
              sampleText: extractedText.slice(0, 500)
            };
            this.isIndexing = false;
            return this.activeDocument;
          } catch (bridgeErr) {
            console.warn('[RagEngine] Native bridge ingestion error, falling back to local:', bridgeErr);
          }
        }

        // 2. Client-Side Chunking & Vector Fallback
        const chunks = this.chunkText(extractedText, 350, 40);
        this.localChunks = chunks.map((chunkText, idx) => ({
          chunkIndex: idx + 1,
          chunkText,
          fileName,
          tokens: this.tokenize(chunkText)
        }));

        this.activeDocument = {
          docId: 'doc_local_' + Date.now(),
          fileName,
          totalChunks: this.localChunks.length,
          sampleText: extractedText.slice(0, 500)
        };

        this.isIndexing = false;
        return this.activeDocument;
      } catch (err) {
        this.isIndexing = false;
        throw err;
      }
    },

    /**
     * Contextual Search across local vector database.
     * @param {string} query
     * @param {number} topK
     * @returns {Promise<Array<{chunkIndex: number, chunkText: string, score: number, fileName: string}>>}
     */
    async search(query, topK = 3) {
      if (!query || !query.trim() || !this.hasActiveDocument()) {
        return [];
      }

      // 1. Check Native Android Bridge
      if (window.Capacitor?.Plugins?.MarvoNativeBridge?.queryRag) {
        try {
          const res = await window.Capacitor.Plugins.MarvoNativeBridge.queryRag({
            query: query.trim(),
            topK
          });
          if (res && Array.isArray(res.results)) {
            return res.results;
          }
        } catch (bridgeErr) {
          console.warn('[RagEngine] Native RAG query error, using local fallback:', bridgeErr);
        }
      }

      // 2. Client-Side Cosine Vector Search
      const queryTokens = this.tokenize(query);
      if (Object.keys(queryTokens).length === 0) return [];

      const scored = [];
      for (const chunk of this.localChunks) {
        const sim = this.computeCosineSimilarity(queryTokens, chunk.tokens);
        if (sim > 0.08) {
          scored.push({
            chunkIndex: chunk.chunkIndex,
            chunkText: chunk.chunkText,
            score: Math.round(sim * 1000) / 1000,
            fileName: chunk.fileName
          });
        }
      }

      scored.sort((a, b) => b.score - a.score);
      return scored.slice(0, topK);
    },

    /**
     * Builds grounded prompt augmenting user query with retrieved document context.
     * @param {string} userQuery
     * @param {Array} retrievedChunks
     * @returns {string} Augmented system prompt context
     */
    buildGroundedContext(userQuery, retrievedChunks) {
      if (!retrievedChunks || retrievedChunks.length === 0) {
        return '';
      }

      const docName = this.activeDocument?.fileName || 'Active Notes';
      let contextStr = `\n\n═══════════ LOCAL GROUND TRUTH DOCUMENT CONTEXT: "${docName}" ═══════════\n`;
      contextStr += `The following verified excerpts were retrieved from the student's local notes with high semantic relevance:\n\n`;

      retrievedChunks.forEach((item, i) => {
        contextStr += `[Reference Excerpt ${i + 1} | Section ${item.chunkIndex} | Relevance: ${item.score}]\n`;
        contextStr += `${item.chunkText.trim()}\n\n`;
      });

      contextStr += `═══════════ PEDAGOGICAL INSTRUCTION ═══════════\n`;
      contextStr += `1. Prioritize the above document excerpts as the primary ground truth for answering.\n`;
      contextStr += `2. Cite the specific excerpt or section when stating definitions, theorems, or data from the document.\n`;
      contextStr += `3. If the student asks something not present in the document, clarify what is in the document and supplement with standard scientific principles.`;

      return contextStr;
    },

    /**
     * Clears active document and wipes temporary cache.
     */
    async clearActiveDocument() {
      this.activeDocument = null;
      this.localChunks = [];

      if (window.Capacitor?.Plugins?.MarvoNativeBridge?.clearRagCache) {
        try {
          await window.Capacitor.Plugins.MarvoNativeBridge.clearRagCache();
        } catch (e) {
          console.warn('[RagEngine] Native cache clear error:', e);
        }
      }
    },

    // --- Helpers ---
    readFile(file) {
      return new Promise((resolve, reject) => {
        const reader = new FileReader();
        const isPdf = file.name.endsWith('.pdf') || file.type === 'application/pdf';

        if (isPdf) {
          reader.onload = () => resolve(reader.result); // Data URL
          reader.onerror = reject;
          reader.readAsDataURL(file);
        } else {
          reader.onload = () => resolve(reader.result); // Plain text
          reader.onerror = reject;
          reader.readAsText(file);
        }
      });
    },

    chunkText(text, targetWords = 350, overlapWords = 40) {
      if (!text) return [];
      const words = text.split(/\s+/);
      if (words.length <= targetWords) return [text.trim()];

      const chunks = [];
      let start = 0;
      while (start < words.length) {
        const end = Math.min(start + targetWords, words.length);
        chunks.push(words.slice(start, end).join(' '));
        if (end >= words.length) break;
        start += (targetWords - overlapWords);
      }
      return chunks;
    },

    tokenize(text) {
      const counts = {};
      if (!text) return counts;
      const clean = text.toLowerCase().replace(/[^a-z0-9_\-\s]/g, ' ');
      const words = clean.split(/\s+/);
      for (const w of words) {
        if (w.length >= 2 && !STOP_WORDS.has(w)) {
          counts[w] = (counts[w] || 0) + 1;
        }
      }
      return counts;
    },

    computeCosineSimilarity(q, d) {
      let dot = 0;
      let normQ = 0;
      let normD = 0;

      for (const [token, count] of Object.entries(q)) {
        normQ += count * count;
        if (d[token]) {
          dot += count * d[token];
        }
      }

      for (const count of Object.values(d)) {
        normD += count * count;
      }

      if (normQ === 0 || normD === 0) return 0;
      return dot / (Math.sqrt(normQ) * Math.sqrt(normD));
    },

    extractTextFromDataUri(dataUri, fileName) {
      try {
        const base64 = dataUri.split(',')[1];
        const binaryStr = atob(base64);
        // Extract plain ASCII strings from PDF stream
        const matches = binaryStr.match(/\(([^\(\)]{3,})\)\s*(?:Tj|TJ)/g);
        if (matches && matches.length > 0) {
          return matches
            .map(m => m.replace(/^[(\s]+|[)\s\w]+$/g, ''))
            .filter(t => t.length > 2)
            .join(' ');
        }
      } catch (e) {
        console.warn('[RagEngine] Fallback text decode for PDF data:', e);
      }
      return `Document: ${fileName}\n[Extracted local binary stream prepared for semantic matching]`;
    }
  };

  window.RagEngine = RagEngine;

  // Auto-initialize
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => RagEngine.init());
  } else {
    RagEngine.init();
  }

})(window);
