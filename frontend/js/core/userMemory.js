/**
 * MARVO AI — Per-User Firestore Document (users/{uid}) & Local Cache Memory Sync
 * Module: js/core/userMemory.js
 * 
 * Production Permanent User Memory Store:
 * - 0ms latency local cache hydration (localStorage: marvo_mem_${uid})
 * - Single-fetch session sync with Firestore: users/{uid}
 * - Free-tier optimized debounced/batched writes on crispyMemory changes
 * - Token-minimal prompt injection formatter (max ~150 tokens)
 * - Exposes window.MarvoMemory for Steps 4 & 5
 */

import { db, doc, getDoc, setDoc, serverTimestamp, MarvoAuth } from './firebaseService.js';

// ═══════════════════════════════════════════════════════════════════
// 1. STATE & STORAGE HELPERS
// ═══════════════════════════════════════════════════════════════════
let currentUid = null;
let currentUserProfile = null; // { uid, email, displayName, crispyMemory: {}, updatedAt }
let debounceTimer = null;
const DEBOUNCE_DELAY_MS = 2000; // 2.0s debounce buffer to protect Free Tier quota

function getStorageKey(uid) {
  return `marvo_mem_${uid}`;
}

function saveToLocalStorage(uid, profile) {
  if (!uid || !profile) return;
  try {
    localStorage.setItem(getStorageKey(uid), JSON.stringify(profile));
  } catch (err) {
    console.warn('[MarvoMemory] localStorage save warning:', err);
  }
}

function loadFromLocalStorage(uid) {
  if (!uid) return null;
  try {
    const raw = localStorage.getItem(getStorageKey(uid));
    return raw ? JSON.parse(raw) : null;
  } catch (err) {
    console.warn('[MarvoMemory] localStorage load warning:', err);
    return null;
  }
}

// ═══════════════════════════════════════════════════════════════════
// 2. FIRESTORE SYNC (ONCE PER SESSION & DEBOUNCED WRITES)
// ═══════════════════════════════════════════════════════════════════
/**
 * Synchronize users/{uid} document from Firestore on session start
 */
async function syncFromFirestore(user) {
  if (!user || !user.uid || !db) return;
  const uid = user.uid;

  try {
    const userDocRef = doc(db, 'users', uid);
    const docSnap = await getDoc(userDocRef);

    if (docSnap.exists()) {
      const remoteData = docSnap.data() || {};
      
      // Merge remote memory with current cached memory
      currentUserProfile = {
        uid,
        email: user.email || remoteData.email || '',
        displayName: user.displayName || remoteData.displayName || 'Marvo User',
        crispyMemory: {
          ...(currentUserProfile?.crispyMemory || {}),
          ...(remoteData.crispyMemory || {})
        },
        updatedAt: remoteData.updatedAt || new Date().toISOString()
      };

      saveToLocalStorage(uid, currentUserProfile);
      console.log(`[MarvoMemory] Synced Firestore memory for users/${uid} (${Object.keys(currentUserProfile.crispyMemory).length} facts)`);
    } else {
      // New user registration in Firestore
      currentUserProfile = {
        uid,
        email: user.email || '',
        displayName: user.displayName || 'Marvo User',
        crispyMemory: currentUserProfile?.crispyMemory || {},
        updatedAt: new Date().toISOString()
      };

      await setDoc(userDocRef, {
        ...currentUserProfile,
        updatedAt: serverTimestamp()
      }, { merge: true });

      saveToLocalStorage(uid, currentUserProfile);
      console.log(`[MarvoMemory] Initialized new user document: users/${uid}`);
    }
  } catch (err) {
    console.warn('[MarvoMemory] Firestore session sync notice (using local cache):', err.message);
  }
}

/**
 * Flush debounced crispyMemory updates to Firestore
 */
async function flushToFirestore() {
  if (!currentUid || !db || !currentUserProfile) return;

  try {
    const userDocRef = doc(db, 'users', currentUid);
    await setDoc(userDocRef, {
      crispyMemory: currentUserProfile.crispyMemory || {},
      updatedAt: serverTimestamp()
    }, { merge: true });

    console.log(`[MarvoMemory] Batched sync committed to Firestore users/${currentUid}`);
  } catch (err) {
    console.warn('[MarvoMemory] Firestore write error (queued locally):', err.message);
  }
}

// ═══════════════════════════════════════════════════════════════════
// 3. PUBLIC API IMPLEMENTATION (window.MarvoMemory)
// ═══════════════════════════════════════════════════════════════════
/**
 * Returns current user's profile and memory from fast local cache
 */
function getProfile() {
  if (!currentUserProfile) {
    // Check if guest user has local facts
    const guestFacts = loadFromLocalStorage('guest');
    return {
      displayName: 'Guest User',
      crispyMemory: guestFacts?.crispyMemory || {}
    };
  }

  return {
    displayName: currentUserProfile.displayName || 'Marvo User',
    crispyMemory: { ...(currentUserProfile.crispyMemory || {}) }
  };
}

/**
 * Merges new compact key-value facts into crispyMemory,
 * updates localStorage immediately, and syncs debounced to Firestore.
 * @param {Object} newFactsObj - { key: value, ... }
 */
function updateCrispyMemory(newFactsObj) {
  if (!newFactsObj || typeof newFactsObj !== 'object') return;

  // Filter and sanitize facts
  const cleanFacts = {};
  for (const [k, v] of Object.entries(newFactsObj)) {
    if (k && v !== undefined && v !== null && String(v).trim()) {
      cleanFacts[String(k).trim()] = String(v).trim();
    }
  }

  if (Object.keys(cleanFacts).length === 0) return;

  // Ensure user profile initialized
  if (!currentUserProfile) {
    if (currentUid) {
      currentUserProfile = {
        uid: currentUid,
        displayName: 'Marvo User',
        email: '',
        crispyMemory: {},
        updatedAt: new Date().toISOString()
      };
    } else {
      // Guest memory buffer
      let guestMem = loadFromLocalStorage('guest') || { crispyMemory: {} };
      guestMem.crispyMemory = { ...(guestMem.crispyMemory || {}), ...cleanFacts };
      saveToLocalStorage('guest', guestMem);
      return guestMem.crispyMemory;
    }
  }

  // Detect actual fact changes
  let hasChanges = false;
  const existing = currentUserProfile.crispyMemory || {};
  for (const [k, v] of Object.entries(cleanFacts)) {
    if (existing[k] !== v) {
      hasChanges = true;
      existing[k] = v;
    }
  }

  if (!hasChanges) {
    return existing;
  }

  currentUserProfile.crispyMemory = existing;
  currentUserProfile.updatedAt = new Date().toISOString();

  // 1. Immediate local cache update (0ms latency)
  saveToLocalStorage(currentUid, currentUserProfile);

  // 2. Debounced Firestore write (protect free-tier quotas)
  if (debounceTimer) clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => {
    flushToFirestore();
  }, DEBOUNCE_DELAY_MS);

  return currentUserProfile.crispyMemory;
}

/**
 * Returns a compact, token-minimal bullet string of crispyMemory (max ~150 tokens)
 * formatted for direct system prompt injection.
 */
function getFormattedContext() {
  const profile = getProfile();
  const facts = profile.crispyMemory || {};
  const entries = Object.entries(facts);

  if (entries.length === 0) return "";

  // Build clean concise bullet list
  const lines = entries.map(([key, val]) => `• ${key}: ${val}`);
  const combined = lines.join('\n');

  // Cap at ~650 chars (~150 tokens) to ensure strict token budget
  const truncated = combined.length > 650 ? combined.substring(0, 650) + '...' : combined;

  return `[User Memory & Preferences]:\n${truncated}`;
}

// ═══════════════════════════════════════════════════════════════════
// 4. AUTH READY LISTENER & SESSION LIFECYCLE
// ═══════════════════════════════════════════════════════════════════
function handleAuthChange(user) {
  if (user && user.uid) {
    currentUid = user.uid;

    // 1. Instant 0ms local hydration from cache
    const cached = loadFromLocalStorage(currentUid);
    if (cached) {
      currentUserProfile = cached;
    } else {
      currentUserProfile = {
        uid: user.uid,
        email: user.email || '',
        displayName: user.displayName || 'Marvo User',
        crispyMemory: {},
        updatedAt: new Date().toISOString()
      };
    }

    // 2. Async single-fetch session sync with Firestore
    syncFromFirestore(user);
  } else {
    // User signed out: flush pending if any, then clear
    if (debounceTimer) {
      clearTimeout(debounceTimer);
      flushToFirestore();
    }
    currentUid = null;
    currentUserProfile = null;
  }
}

// Subscribe to MarvoAuth lifecycle
if (MarvoAuth && typeof MarvoAuth.onAuthReady === 'function') {
  MarvoAuth.onAuthReady((user) => {
    handleAuthChange(user);
  });
} else if (window.MarvoAuth && typeof window.MarvoAuth.onAuthReady === 'function') {
  window.MarvoAuth.onAuthReady((user) => {
    handleAuthChange(user);
  });
}

// ═══════════════════════════════════════════════════════════════════
// 5. SILENT CRISPY MEMORY EXTRACTOR (ZERO API COST / LOCAL REGEX)
// ═══════════════════════════════════════════════════════════════════
/**
 * Common state/transient words to prevent falsely matching as user names.
 */
const NAME_BLACKLIST = new Set([
  'fine', 'good', 'okay', 'ok', 'great', 'happy', 'sad', 'tired', 'busy',
  'ready', 'sorry', 'back', 'here', 'bored', 'lost', 'done', 'sure', 'just',
  'trying', 'thinking', 'asking', 'working', 'going', 'learning', 'feeling',
  'interested', 'using', 'new', 'old', 'online', 'offline', 'student',
  'engineer', 'developer', 'there', 'listening', 'speaking', 'waiting',
  'kar', 'raha', 'rahi', 'padh', 'padhai'
]);

function cleanFactValue(val) {
  if (!val) return '';
  return val
    .replace(/^["'`]+|["'`]+$/g, '') // remove surrounding quotes
    .replace(/[.!?,;]+$/g, '')         // remove trailing punctuation
    .replace(/\s+/g, ' ')             // normalize whitespace
    .trim();
}

/**
 * Silently extracts personal facts (identity, study, role, projects,
 * preferences, devices, explicit memories) across English, Hindi, and Hinglish.
 * Zero API tokens, 0ms local execution. Overwrites outdated keys cleanly.
 * 
 * @param {string} userText - User's input chat message
 * @returns {Object|null} Extracted facts dictionary or null if none detected
 */
function processUserMessage(userText) {
  if (!userText || typeof userText !== 'string') return null;
  const text = userText.trim();
  if (text.length < 3 || text.length > 1000) return null;

  const extracted = {};

  // 1. User Identity / Name
  // English: "my name is Alex", "I am Alex", "call me Alex", "I'm Alex"
  // Hindi/Hinglish: "mera naam Alex hai", "mera name Alex hai", "mujhe Alex bulao", "main Alex hoon"
  const namePatterns = [
    /(?:my name is|call me|i am called)\s+([A-Z][a-zA-Z\s]{1,25})/i,
    /(?:mera naam|mera name)\s+([A-Za-z\s]{2,25})(?:\s+hai|\s*$)/i,
    /(?:mujhe|muze)\s+([A-Za-z\s]{2,25})\s+(?:bulao|bulana|kaho)/i,
    /(?:main|mai|mein)\s+(?!ek\b)(?!kuch\b)([A-Z][a-z]{1,15}(?:\s+[A-Z][a-z]{1,15})?)\s+(?:hoon|hu|hun)/,
    /(?:i'm|i am)\s+([A-Z][a-zA-Z]{1,20}(?:\s+[A-Z][a-zA-Z]{1,20})?)(?:[.,!?;]|$)/
  ];

  for (const pat of namePatterns) {
    const match = text.match(pat);
    if (match && match[1]) {
      const candidate = cleanFactValue(match[1]);
      const words = candidate.toLowerCase().split(' ');
      const hasBlacklist = words.some(w => NAME_BLACKLIST.has(w));
      if (candidate.length >= 2 && candidate.length <= 30 && !hasBlacklist) {
        extracted['Name'] = candidate.replace(/\b\w/g, c => c.toUpperCase());
        break;
      }
    }
  }

  // 2. Study / Education
  // English: "I study Computer Science", "I am studying MBBS", "pursuing B.Tech in CSE", "student of Grade 12"
  // Hinglish/Hindi: "mein BTech kar raha hoon", "computer science padh raha hu", "mai 12th me padhta hoon"
  const studyPatterns = [
    /(?:i(?:\s+am|\s*'m)?\s+studying|i\s+study|pursuing|enrolled in)\s+([A-Za-z0-9\s.,&/\-]{3,45}?)(?:(?:\s+(?:at|in|from)\s+[A-Za-z0-9\s]+)|[.,!?;]|$)/i,
    /(?:i(?:\s+am|\s*'m)?\s+a\s+student\s+of)\s+([A-Za-z0-9\s.,&/\-]{3,45}?)[.,!?;]?$/i,
    /(?:main|mai|mein)\s+([A-Za-z0-9\s.,&/\-]{2,40}?)\s+(?:padh|study|kar)\s+(?:raha|rahi)\s+(?:hoon|hu|hun)/i,
    /([A-Za-z0-9\s.,&/\-]{2,40}?)\s+(?:ki\s+padhai\s+kar\s+raha|ka\s+student\s+hoon)/i
  ];

  for (const pat of studyPatterns) {
    const match = text.match(pat);
    if (match && match[1]) {
      const val = cleanFactValue(match[1]);
      if (val.length >= 2 && val.length <= 50) {
        extracted['Study'] = val;
        break;
      }
    }
  }

  // 3. Work / Role / Profession
  // English: "I work as a software engineer", "I am a frontend developer", "my role is DevOps lead", "I work at Microsoft"
  // Hinglish/Hindi: "mera role frontend dev hai", "mai software engineer hoon", "Google me kaam karta hoon"
  const rolePatterns = [
    /(?:i(?:\s+am|\s*'m)?\s+working\s+as\s+(?:a\s+|an\s+)?|i\s+work\s+as\s+(?:a\s+|an\s+)?|my\s+role\s+is\s+(?:a\s+|an\s+)?|i(?:\s+am|\s*'m)\s+(?:a\s+|an\s+)(?!student\b))\s*([A-Za-z0-9\s/\-]{2,40}?\s+(?:developer|engineer|designer|manager|architect|doctor|lawyer|teacher|freelancer|consultant|analyst|creator|scientist|founder|ceo|cto|intern))/i,
    /(?:i\s+work\s+at|working\s+at)\s+([A-Za-z0-9\s&]{2,35}?)(?:[.,!?;]|$)/i,
    /(?:mera\s+role|mera\s+job|mera\s+profession)\s+([A-Za-z0-9\s/\-]{3,40}?)(?:\s+hai|\s*$)/i,
    /([A-Za-z0-9\s&]{2,30}?)\s+(?:me|mein)\s+(?:kaam|job)\s+karta\s+(?:hoon|hu)/i
  ];

  for (const pat of rolePatterns) {
    const match = text.match(pat);
    if (match && match[1]) {
      const val = cleanFactValue(match[1]);
      if (val.length >= 2 && val.length <= 50 && !NAME_BLACKLIST.has(val.toLowerCase())) {
        extracted['Role'] = val;
        break;
      }
    }
  }

  // 4. Current Project / Work
  // English: "I'm building a chatbot", "working on project Marvo", "my project is an autonomous drone"
  // Hinglish/Hindi: "mera project Marvo hai", "ek AI app bana raha hoon", "working on project X"
  const projectPatterns = [
    /(?:working on|i(?:\s+am|\s*'m)?\s+building|i(?:\s+am|\s*'m)?\s+developing|my\s+project\s+is)\s+(?:a\s+|an\s+|the\s+)?([A-Za-z0-9\s_\-]{3,50}?)(?:[.,!?;]|$)/i,
    /(?:mera\s+project|project\s+name)\s+([A-Za-z0-9\s_\-]{2,40}?)(?:\s+hai|\s*$)/i,
    /(?:ek|naya)\s+([A-Za-z0-9\s_\-]{3,40}?)\s+bana\s+raha\s+(?:hoon|hu)/i
  ];

  for (const pat of projectPatterns) {
    const match = text.match(pat);
    if (match && match[1]) {
      const val = cleanFactValue(match[1]);
      if (val.length >= 2 && val.length <= 60) {
        extracted['CurrentProject'] = val;
        break;
      }
    }
  }

  // 5. Preferences & Likes
  // English: "I prefer dark mode", "I love TypeScript", "I like concise answers", "my favorite framework is React"
  // Hinglish/Hindi: "mujhe dark theme pasand hai", "mujhe python acchi lagti hai", "mera favorite language python hai"
  const prefPatterns = [
    /(?:i\s+prefer|i\s+love|i\s+like|my\s+favorite\s+[a-z]+\s+is)\s+([A-Za-z0-9\s+#.\-]{2,40}?)(?:[.,!?;]|$)/i,
    /(?:mujhe|muze)\s+([A-Za-z0-9\s+#.\-]{2,40}?)\s+(?:pasand|achha\s+lagta|acchi\s+lagti)\s+hai/i,
    /(?:mera\s+favorite|meri\s+favorite)\s+([A-Za-z0-9\s+#.\-]{2,40}?)(?:\s+hai|\s*$)/i
  ];

  for (const pat of prefPatterns) {
    const match = text.match(pat);
    if (match && match[1]) {
      const val = cleanFactValue(match[1]);
      if (val.length >= 2 && val.length <= 50) {
        extracted['Likes'] = val;
        break;
      }
    }
  }

  // 6. Devices / Hardware
  // English: "I have a MacBook M2", "my phone is Pixel 8", "I use Windows 11"
  // Hinglish/Hindi: "mere paas iPhone 15 hai", "mera laptop Dell XPS hai"
  const devicePatterns = [
    /(?:i(?:\s+have|\s*'ve)?\s+(?:a\s+|an\s+)?|my\s+phone\s+is\s+(?:a\s+)?|my\s+laptop\s+is\s+(?:a\s+)?|i\s+use\s+(?:a\s+)?)\s*([A-Za-z0-9\s]{2,30}?\s*(?:iphone[\w\s]*|macbook[\w\s]*|pixel[\w\s]*|samsung[\w\s]*|galaxy[\w\s]*|android[\w\s]*|windows[\w\s]*|linux[\w\s]*|ipad[\w\s]*|oneplus[\w\s]*|dell[\w\s]*|thinkpad[\w\s]*))/i,
    /(?:mere\s+paas|mera\s+phone|mera\s+laptop)\s+([A-Za-z0-9\s]{2,35}?)(?:\s+hai|\s*$)/i
  ];

  for (const pat of devicePatterns) {
    const match = text.match(pat);
    if (match && match[1]) {
      const val = cleanFactValue(match[1]);
      if (val.length >= 2 && val.length <= 40) {
        extracted['Devices'] = val;
        break;
      }
    }
  }

  // 7. Explicit Memory Directives
  // English: "remember that I have an exam on Friday", "please remember that I'm vegan", "note that..."
  // Hinglish/Hindi: "yaad rakhna ki kal mera presentation hai", "yaad rakhna..."
  const explicitPatterns = [
    /(?:please\s+remember\s+that|remember\s+that|keep\s+in\s+mind\s+that|note\s+that|don't\s+forget\s+that)\s+(.+?)(?:[.,!?;]|$)/i,
    /(?:yaad\s+rakhna\s+ki|yaad\s+rakhna|dhyan\s+rakhna\s+ki)\s+(.+?)(?:[.,!?;]|$)/i
  ];

  for (const pat of explicitPatterns) {
    const match = text.match(pat);
    if (match && match[1]) {
      const val = cleanFactValue(match[1]);
      if (val.length >= 3 && val.length <= 100) {
        extracted['Notes'] = val;
        break;
      }
    }
  }

  // If any fact was extracted, update crispyMemory immediately
  if (Object.keys(extracted).length > 0) {
    console.log('[MarvoMemory] Extracted silent crispy facts:', extracted);
    updateCrispyMemory(extracted);
    return extracted;
  }

  return null;
}

// ═══════════════════════════════════════════════════════════════════
// 6. GLOBAL API EXPORT
// ═══════════════════════════════════════════════════════════════════
const MarvoMemory = {
  getProfile,
  updateCrispyMemory,
  getFormattedContext,
  processUserMessage,
  getUid: () => currentUid,
  flush: flushToFirestore
};

window.MarvoMemory = MarvoMemory;

export {
  getProfile,
  updateCrispyMemory,
  getFormattedContext,
  processUserMessage,
  MarvoMemory
};
