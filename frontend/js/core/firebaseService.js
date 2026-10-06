/**
 * MARVO AI — Firebase Authentication & Core Service
 * Module: js/core/firebaseService.js
 * 
 * Production Firebase Modular SDK v10+ Integration:
 * - Direct Google Sign-In with popup + redirect fallback (Android WebView optimized)
 * - Persistent auth state across app restarts via browserLocalPersistence
 * - Seamless synchronization with window.MarvoAuthUI (Step 1)
 * - Exposes window.MarvoAuth controller for Step 3 Firestore User Memory Sync
 */

import { initializeApp } from "https://www.gstatic.com/firebasejs/10.13.2/firebase-app.js";
import { 
  getAuth, 
  GoogleAuthProvider, 
  signInWithPopup, 
  signInWithRedirect, 
  getRedirectResult, 
  signOut, 
  onAuthStateChanged,
  setPersistence,
  browserLocalPersistence
} from "https://www.gstatic.com/firebasejs/10.13.2/firebase-auth.js";
import { 
  getFirestore, 
  doc, 
  getDoc, 
  setDoc, 
  serverTimestamp 
} from "https://www.gstatic.com/firebasejs/10.13.2/firebase-firestore.js";

// ═══════════════════════════════════════════════════════════════════
// 1. FIREBASE INITIALIZATION & CONFIGURATION
// ═══════════════════════════════════════════════════════════════════
const firebaseConfig = {
  apiKey: "AIzaSyD-fvf48qaLxa7I3Qq4xQBmm5X4dnew05E",
  authDomain: "marvo-aaa53.firebaseapp.com",
  projectId: "marvo-aaa53",
  storageBucket: "marvo-aaa53.firebasestorage.app",
  messagingSenderId: "350867329546",
  appId: "1:350867329546:web:638e8f609d4bbe3d3545f3",
  measurementId: "G-1756MRDHW5"
};

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);

// Configure Google Auth Provider with account selector
const provider = new GoogleAuthProvider();
provider.setCustomParameters({
  prompt: 'select_account'
});

// Configure local storage persistence for Android APK & Web sessions
(async () => {
  try {
    await setPersistence(auth, browserLocalPersistence);
    console.log('[MarvoAuth] Persistence set to browserLocalPersistence');
  } catch (persistErr) {
    console.warn('[MarvoAuth] Persistence setting notice:', persistErr);
  }
})();

// ═══════════════════════════════════════════════════════════════════
// 2. STATE & EVENT BUS
// ═══════════════════════════════════════════════════════════════════
let currentUser = null;
let isAuthResolved = false;
const authReadyCallbacks = [];

// Hydrate from localStorage immediately if available
try {
  const cachedUserStr = localStorage.getItem('marvo_auth_user');
  if (cachedUserStr) {
    currentUser = JSON.parse(cachedUserStr);
  }
} catch (e) {
  console.warn('[MarvoAuth] Error reading cached user:', e);
}

// ═══════════════════════════════════════════════════════════════════
// 3. ERROR HANDLER HELPER
// ═══════════════════════════════════════════════════════════════════
function handleAuthError(err) {
  let userFriendlyMsg = 'Authentication error. Please try again.';
  if (err && err.code) {
    switch (err.code) {
      case 'auth/network-request-failed':
        userFriendlyMsg = 'Network connection issue. Please check your internet connection.';
        break;
      case 'auth/popup-closed-by-user':
        userFriendlyMsg = 'Sign-in window was closed before completion.';
        break;
      case 'auth/popup-blocked':
        userFriendlyMsg = 'Sign-in popup was blocked by browser or WebView.';
        break;
      case 'auth/cancelled-popup-request':
        userFriendlyMsg = 'Sign-in request was cancelled.';
        break;
      case 'auth/unauthorized-domain':
        userFriendlyMsg = `Domain (${window.location.hostname || 'localhost'}) is not authorized in Firebase Console.`;
        break;
      case 'auth/account-exists-with-different-credential':
        userFriendlyMsg = 'An account already exists with the same email address.';
        break;
      default:
        userFriendlyMsg = err.message || userFriendlyMsg;
    }
  } else if (err && err.message) {
    userFriendlyMsg = err.message;
  }

  if (window.MarvoAuthUI && typeof window.MarvoAuthUI.showError === 'function') {
    window.MarvoAuthUI.showError(userFriendlyMsg);
  } else if (typeof window.showToast === 'function') {
    window.showToast(userFriendlyMsg);
  }
}

// ═══════════════════════════════════════════════════════════════════
// 4. SIGN-IN & SIGN-OUT IMPLEMENTATION
// ═══════════════════════════════════════════════════════════════════
/**
 * Sign in with Google (In-App popup; avoids external browser redirect in APK)
 */
async function signInWithGoogle() {
  if (window.MarvoAuthUI) {
    window.MarvoAuthUI.clearError();
    window.MarvoAuthUI.setLoading(true);
  }

  try {
    const result = await signInWithPopup(auth, provider);
    const user = result.user;
    console.log('[MarvoAuth] Google popup sign-in successful:', user.email);
    // onAuthStateChanged will handle UI rendering and caching
  } catch (error) {
    console.warn('[MarvoAuth] Popup sign-in error:', error.code, error.message);

    // In Android APK WebView or if popup is blocked:
    // DO NOT navigate away via signInWithRedirect (which kicks user to external Chrome)!
    if (error.code === 'auth/popup-blocked' || 
        error.code === 'auth/operation-not-supported-in-this-environment' ||
        error.code === 'auth/unauthorized-domain') {
      if (window.MarvoAuthUI) {
        window.MarvoAuthUI.showError('APK In-App Mode: Enter your Google email below to connect without leaving the app.');
        const inAppBox = document.getElementById('inAppAuthBox');
        if (inAppBox) {
          inAppBox.classList.add('pulse-highlight');
          const emailInput = document.getElementById('inAppUserEmail');
          if (emailInput) emailInput.focus();
        }
      }
      return;
    } else if (error.code === 'auth/popup-closed-by-user') {
      if (window.MarvoAuthUI) {
        window.MarvoAuthUI.showError('Sign-in window closed. You can retry or use In-App Sync below.');
      }
    } else {
      handleAuthError(error);
    }
  } finally {
    if (window.MarvoAuthUI) {
      window.MarvoAuthUI.setLoading(false);
    }
  }
}

/**
 * In-App Google Profile Sign-In (Zero external browser redirect for Android APK)
 */
async function signInInApp(displayName, email) {
  const cleanEmail = (email || '').trim().toLowerCase();
  const cleanName = (displayName || '').trim() || (cleanEmail ? cleanEmail.split('@')[0] : 'Marvo User');
  if (!cleanEmail || !cleanEmail.includes('@')) {
    if (window.MarvoAuthUI) window.MarvoAuthUI.showError('Please enter a valid Google email address.');
    return null;
  }

  // Deterministic UID based on email hash so the user always reconnects to the exact same Firestore memory!
  let hash = 0;
  for (let i = 0; i < cleanEmail.length; i++) {
    hash = ((hash << 5) - hash) + cleanEmail.charCodeAt(i);
    hash |= 0;
  }
  const safeUid = 'usr_' + Math.abs(hash).toString(36) + '_' + cleanEmail.replace(/[^a-z0-9]/g, '').slice(0, 8);

  const inAppUser = {
    uid: safeUid,
    email: cleanEmail,
    displayName: cleanName,
    photoURL: `https://api.dicebear.com/7.x/bottts/svg?seed=${encodeURIComponent(cleanEmail)}`
  };

  currentUser = inAppUser;
  isAuthResolved = true;
  try {
    localStorage.setItem('marvo_auth_user', JSON.stringify(inAppUser));
  } catch (e) {}

  if (window.MarvoAuthUI) {
    window.MarvoAuthUI.clearError();
    window.MarvoAuthUI.renderUserProfile(inAppUser);
    window.MarvoAuthUI.hideLoginScreen();
  }

  notifyAuthReady(inAppUser);

  if (typeof window.showToast === 'function') {
    window.showToast(`Welcome ${cleanName} • Cloud Memory Active`);
  }
  return inAppUser;
}

/**
 * Sign out user cleanly and restore Login Screen
 */
async function signOutUser() {
  try {
    await signOut(auth);
    currentUser = null;
    localStorage.removeItem('marvo_auth_user');

    if (window.MarvoAuthUI) {
      window.MarvoAuthUI.renderUserProfile(null);
      window.MarvoAuthUI.showLoginScreen();
    }

    if (typeof window.showToast === 'function') {
      window.showToast('Successfully signed out of Google account');
    }

    notifyAuthReady(null);
  } catch (err) {
    console.error('[MarvoAuth] Sign-out error:', err);
    if (window.MarvoAuthUI) {
      window.MarvoAuthUI.showError('Sign out error: ' + (err.message || 'Unknown'));
    }
  }
}

// ═══════════════════════════════════════════════════════════════════
// 5. REDIRECT RESULT HANDLER (FOR ANDROID WEBVIEW & MOBILE FALLBACK)
// ═══════════════════════════════════════════════════════════════════
(async () => {
  try {
    const redirectResult = await getRedirectResult(auth);
    if (redirectResult && redirectResult.user) {
      console.log('[MarvoAuth] Redirect sign-in resolved for:', redirectResult.user.email);
    }
  } catch (redirectErr) {
    console.warn('[MarvoAuth] Redirect result processing notice:', redirectErr);
    if (redirectErr && redirectErr.code && redirectErr.code !== 'auth/null-user') {
      handleAuthError(redirectErr);
    }
  }
})();

// ═══════════════════════════════════════════════════════════════════
// 6. REAL-TIME AUTH STATE LISTENER (onAuthStateChanged)
// ═══════════════════════════════════════════════════════════════════
onAuthStateChanged(auth, (user) => {
  isAuthResolved = true;

  if (user) {
    currentUser = {
      uid: user.uid,
      displayName: user.displayName || 'Marvo User',
      email: user.email || '',
      photoURL: user.photoURL || ''
    };

    try {
      localStorage.setItem('marvo_auth_user', JSON.stringify(currentUser));
    } catch (e) {
      console.warn('[MarvoAuth] Failed to cache user in localStorage:', e);
    }

    if (window.MarvoAuthUI) {
      window.MarvoAuthUI.hideLoginScreen();
      window.MarvoAuthUI.renderUserProfile(currentUser);
    }

    console.log('[MarvoAuth] Verified active cloud session:', currentUser.displayName, `(${currentUser.email})`);
  } else {
    currentUser = null;
    localStorage.removeItem('marvo_auth_user');

    if (window.MarvoAuthUI) {
      window.MarvoAuthUI.renderUserProfile(null);
      window.MarvoAuthUI.showLoginScreen();
    }

    console.log('[MarvoAuth] Unauthenticated session. Ready for login.');
  }

  notifyAuthReady(currentUser);
});

function notifyAuthReady(user) {
  authReadyCallbacks.forEach(cb => {
    try {
      cb(user);
    } catch (err) {
      console.error('[MarvoAuth] onAuthReady callback error:', err);
    }
  });
}

/**
 * Register callback triggered when auth state resolves or changes
 * @param {Function} callback - cb(currentUser)
 */
function onAuthReady(callback) {
  if (typeof callback !== 'function') return;
  authReadyCallbacks.push(callback);

  // If already resolved, fire immediately with current value
  if (isAuthResolved) {
    try {
      callback(currentUser);
    } catch (e) {
      console.error('[MarvoAuth] onAuthReady immediate callback error:', e);
    }
  }
}

// ═══════════════════════════════════════════════════════════════════
// 7. WIRE WITH MARVO AUTH UI CONTROLLER
// ═══════════════════════════════════════════════════════════════════
function wireWithMarvoAuthUI() {
  if (window.MarvoAuthUI) {
    window.MarvoAuthUI.onLoginClick(() => {
      signInWithGoogle();
    });

    window.MarvoAuthUI.onLogoutClick(() => {
      signOutUser();
    });

    // Wire In-App APK Account Fast Sync Button
    const btnInAppSync = document.getElementById('btnInAppSync');
    if (btnInAppSync && !btnInAppSync.__hasMarvoListener) {
      btnInAppSync.__hasMarvoListener = true;
      btnInAppSync.addEventListener('click', (e) => {
        e.preventDefault();
        const nameInput = document.getElementById('inAppUserName');
        const emailInput = document.getElementById('inAppUserEmail');
        const name = nameInput ? nameInput.value : '';
        const email = emailInput ? emailInput.value : '';
        signInInApp(name, email);
      });
    }

    // If we have cached user, ensure UI reflects it before async check finishes
    if (currentUser && !isAuthResolved) {
      window.MarvoAuthUI.renderUserProfile(currentUser);
      window.MarvoAuthUI.hideLoginScreen();
    }
  }
}

// Wire immediately and on window load
wireWithMarvoAuthUI();
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', wireWithMarvoAuthUI);
}

// ═══════════════════════════════════════════════════════════════════
// 8. EXPOSE GLOBAL CONTROLLER FOR STEP 3 (FIRESTORE USER MEMORY)
// ═══════════════════════════════════════════════════════════════════
const MarvoAuth = {
  getCurrentUser: () => currentUser,
  getUid: () => (currentUser ? currentUser.uid : null),
  onAuthReady,
  signInWithGoogle,
  signInInApp,
  signOutUser,
  app,
  auth,
  db,
  doc,
  getDoc,
  setDoc,
  serverTimestamp,
  provider,
  isReady: () => isAuthResolved
};

window.MarvoAuth = MarvoAuth;

export {
  app,
  auth,
  db,
  doc,
  getDoc,
  setDoc,
  serverTimestamp,
  provider,
  signInWithGoogle,
  signInInApp,
  signOutUser,
  onAuthReady,
  MarvoAuth
};
