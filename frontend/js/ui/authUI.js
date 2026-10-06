/**
 * MARVO AI — Dedicated Auth UI & User Profile Component
 * Module: js/ui/authUI.js
 * 
 * Provides:
 * - Theme-matched Google Login Screen Overlay (Cyberpunk Neon Emerald & Cyan Glassmorphism)
 * - Header & Sidebar User Profile Avatar, Cloud Memory Active Badge & Dropdown Menu
 * - window.MarvoAuthUI API controller ready for Firebase Auth wiring (Step 2)
 */

(function(window) {
  'use strict';

  // Internal state
  let currentUser = null;
  let isLoadingState = false;
  const loginCallbacks = [];
  const logoutCallbacks = [];

  // Cached DOM elements
  let elements = {};

  function queryElements() {
    return {
      overlay: document.getElementById('marvoAuthOverlay'),
      card: document.querySelector('.marvo-auth-card'),
      btnGoogleLogin: document.getElementById('btnGoogleLogin'),
      googleBtnText: document.getElementById('googleBtnText'),
      googleBtnSpinner: document.getElementById('googleBtnSpinner'),
      btnDismissAuth: document.getElementById('btnDismissAuth'),
      btnContinueGuest: document.getElementById('btnContinueGuest'),
      errorBanner: document.getElementById('authErrorBanner'),
      errorMessage: document.getElementById('authErrorMessage'),
      btnDismissError: document.getElementById('btnDismissAuthError'),

      // Topbar Profile
      btnUserProfile: document.getElementById('btnUserProfile'),
      userProfileDropdown: document.getElementById('userProfileDropdown'),
      headerUserAvatarWrap: document.getElementById('headerUserAvatarWrap'),
      headerUserAvatarImg: document.getElementById('headerUserAvatarImg'),
      headerUserAvatarPlaceholder: document.getElementById('headerUserAvatarPlaceholder'),
      headerUserStatusRing: document.getElementById('headerUserStatusRing'),
      headerUserNamePill: document.getElementById('headerUserNamePill'),

      // Dropdown Profile
      dropdownUserAvatarImg: document.getElementById('dropdownUserAvatarImg'),
      dropdownUserAvatarPlaceholder: document.getElementById('dropdownUserAvatarPlaceholder'),
      dropdownUserStatusDot: document.getElementById('dropdownUserStatusDot'),
      dropdownUserName: document.getElementById('dropdownUserName'),
      dropdownUserEmail: document.getElementById('dropdownUserEmail'),
      dropdownCloudBadge: document.getElementById('dropdownCloudBadge'),
      dropdownCloudBadgeText: document.getElementById('dropdownCloudBadgeText'),
      btnProfileSignInPrompt: document.getElementById('btnProfileSignInPrompt'),
      btnProfileSignOut: document.getElementById('btnProfileSignOut'),

      // Sidebar Profile
      sidebarUserCard: document.getElementById('sidebarUserCard'),
      sidebarUserAvatarImg: document.getElementById('sidebarUserAvatarImg'),
      sidebarUserAvatarPlaceholder: document.getElementById('sidebarUserAvatarPlaceholder'),
      sidebarUserStatusDot: document.getElementById('sidebarUserStatusDot'),
      sidebarUserName: document.getElementById('sidebarUserName'),
      sidebarUserSub: document.getElementById('sidebarUserSub'),
      btnSidebarAuthAction: document.getElementById('btnSidebarAuthAction')
    };
  }

  /**
   * Show the sleek glassmorphic login overlay
   */
  function showLoginScreen() {
    const el = elements.overlay || document.getElementById('marvoAuthOverlay');
    if (!el) return;

    clearError();
    el.classList.remove('hidden');
    // Force browser reflow to guarantee CSS transition triggers
    void el.offsetWidth;
    el.classList.add('active');
    document.body.classList.add('auth-overlay-open');
    closeUserProfileDropdown();
  }

  /**
   * Hide the login overlay
   */
  function hideLoginScreen() {
    const el = elements.overlay || document.getElementById('marvoAuthOverlay');
    if (!el) return;

    el.classList.remove('active');
    document.body.classList.remove('auth-overlay-open');
    setTimeout(() => {
      if (!el.classList.contains('active')) {
        el.classList.add('hidden');
      }
    }, 320);
  }

  /**
   * Check if login screen is currently visible
   */
  function isLoginScreenVisible() {
    const el = elements.overlay || document.getElementById('marvoAuthOverlay');
    return el ? el.classList.contains('active') && !el.classList.contains('hidden') : false;
  }

  /**
   * Set loading spinner state on the Google Login button
   * @param {boolean} isLoading
   */
  function setLoading(isLoading) {
    isLoadingState = !!isLoading;
    const btn = elements.btnGoogleLogin || document.getElementById('btnGoogleLogin');
    const text = elements.googleBtnText || document.getElementById('googleBtnText');
    const spinner = elements.googleBtnSpinner || document.getElementById('googleBtnSpinner');

    if (btn) {
      btn.disabled = isLoadingState;
      if (isLoadingState) {
        btn.classList.add('loading');
      } else {
        btn.classList.remove('loading');
      }
    }

    if (spinner) {
      if (isLoadingState) {
        spinner.classList.remove('hidden');
      } else {
        spinner.classList.add('hidden');
      }
    }

    if (text) {
      text.textContent = isLoadingState ? 'Connecting with Google...' : 'Continue with Google';
    }
  }

  /**
   * Display an error message in the dedicated banner
   * @param {string} message
   */
  function showError(message) {
    const banner = elements.errorBanner || document.getElementById('authErrorBanner');
    const msgEl = elements.errorMessage || document.getElementById('authErrorMessage');
    if (!banner || !msgEl) return;

    if (!message) {
      clearError();
      return;
    }

    msgEl.textContent = message;
    banner.classList.remove('hidden');
    banner.classList.remove('shake-animate');
    void banner.offsetWidth; // re-trigger animation
    banner.classList.add('shake-animate');
  }

  /**
   * Clear and hide error banner
   */
  function clearError() {
    const banner = elements.errorBanner || document.getElementById('authErrorBanner');
    const msgEl = elements.errorMessage || document.getElementById('authErrorMessage');
    if (banner) banner.classList.add('hidden');
    if (msgEl) msgEl.textContent = '';
  }

  /**
   * Render User Profile state in Header and Sidebar
   * @param {Object|null} user - { uid, displayName, email, photoURL }
   */
  function renderUserProfile(user) {
    currentUser = user || null;
    const isLoggedIn = !!(currentUser && currentUser.uid);

    // 1. Header Profile Pill & Avatar
    const headerImg = elements.headerUserAvatarImg || document.getElementById('headerUserAvatarImg');
    const headerPlaceholder = elements.headerUserAvatarPlaceholder || document.getElementById('headerUserAvatarPlaceholder');
    const headerRing = elements.headerUserStatusRing || document.getElementById('headerUserStatusRing');
    const headerName = elements.headerUserNamePill || document.getElementById('headerUserNamePill');
    const headerBtn = elements.btnUserProfile || document.getElementById('btnUserProfile');

    if (isLoggedIn) {
      const displayName = currentUser.displayName || 'Marvo User';
      const firstName = displayName.split(' ')[0] || 'User';
      const initial = (displayName.charAt(0) || 'U').toUpperCase();

      if (headerName) headerName.textContent = firstName;
      if (headerBtn) {
        headerBtn.setAttribute('title', `${displayName} • Cloud Memory Active`);
        headerBtn.classList.add('logged-in');
      }

      if (currentUser.photoURL && headerImg) {
        headerImg.src = currentUser.photoURL;
        headerImg.classList.remove('hidden');
        if (headerPlaceholder) headerPlaceholder.classList.add('hidden');
      } else if (headerPlaceholder) {
        headerPlaceholder.textContent = initial;
        headerPlaceholder.classList.remove('hidden');
        if (headerImg) headerImg.classList.add('hidden');
      }

      if (headerRing) headerRing.classList.add('online');
    } else {
      if (headerName) headerName.textContent = 'Sign In';
      if (headerBtn) {
        headerBtn.setAttribute('title', 'Sign In with Google • Sync Cloud Memory');
        headerBtn.classList.remove('logged-in');
      }

      if (headerImg) {
        headerImg.classList.add('hidden');
        headerImg.src = '';
      }
      if (headerPlaceholder) {
        headerPlaceholder.innerHTML = `<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>`;
        headerPlaceholder.classList.remove('hidden');
      }

      if (headerRing) headerRing.classList.remove('online');
    }

    // 2. Header Dropdown Card
    const dropdownImg = elements.dropdownUserAvatarImg || document.getElementById('dropdownUserAvatarImg');
    const dropdownPlaceholder = elements.dropdownUserAvatarPlaceholder || document.getElementById('dropdownUserAvatarPlaceholder');
    const dropdownName = elements.dropdownUserName || document.getElementById('dropdownUserName');
    const dropdownEmail = elements.dropdownUserEmail || document.getElementById('dropdownUserEmail');
    const dropdownBadge = elements.dropdownCloudBadge || document.getElementById('dropdownCloudBadge');
    const dropdownBadgeText = elements.dropdownCloudBadgeText || document.getElementById('dropdownCloudBadgeText');
    const btnSignOut = elements.btnProfileSignOut || document.getElementById('btnProfileSignOut');
    const btnSignInPrompt = elements.btnProfileSignInPrompt || document.getElementById('btnProfileSignInPrompt');

    if (isLoggedIn) {
      const displayName = currentUser.displayName || 'Marvo User';
      const initial = (displayName.charAt(0) || 'U').toUpperCase();

      if (dropdownName) dropdownName.textContent = displayName;
      if (dropdownEmail) dropdownEmail.textContent = currentUser.email || 'Cloud Account';

      if (currentUser.photoURL && dropdownImg) {
        dropdownImg.src = currentUser.photoURL;
        dropdownImg.classList.remove('hidden');
        if (dropdownPlaceholder) dropdownPlaceholder.classList.add('hidden');
      } else if (dropdownPlaceholder) {
        dropdownPlaceholder.textContent = initial;
        dropdownPlaceholder.classList.remove('hidden');
        if (dropdownImg) dropdownImg.classList.add('hidden');
      }

      if (dropdownBadge) {
        dropdownBadge.classList.remove('offline');
      }
      if (dropdownBadgeText) {
        dropdownBadgeText.textContent = 'Cloud Memory Active';
      }

      if (btnSignOut) btnSignOut.classList.remove('hidden');
      if (btnSignInPrompt) btnSignInPrompt.classList.add('hidden');
    } else {
      if (dropdownName) dropdownName.textContent = 'Guest User';
      if (dropdownEmail) dropdownEmail.textContent = 'Local storage only';

      if (dropdownImg) {
        dropdownImg.classList.add('hidden');
        dropdownImg.src = '';
      }
      if (dropdownPlaceholder) {
        dropdownPlaceholder.innerHTML = `<svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>`;
        dropdownPlaceholder.classList.remove('hidden');
      }

      if (dropdownBadge) {
        dropdownBadge.classList.add('offline');
      }
      if (dropdownBadgeText) {
        dropdownBadgeText.textContent = 'Local Storage Only';
      }

      if (btnSignOut) btnSignOut.classList.add('hidden');
      if (btnSignInPrompt) btnSignInPrompt.classList.remove('hidden');
    }

    // 3. Sidebar Profile Card
    const sideImg = elements.sidebarUserAvatarImg || document.getElementById('sidebarUserAvatarImg');
    const sidePlaceholder = elements.sidebarUserAvatarPlaceholder || document.getElementById('sidebarUserAvatarPlaceholder');
    const sideDot = elements.sidebarUserStatusDot || document.getElementById('sidebarUserStatusDot');
    const sideName = elements.sidebarUserName || document.getElementById('sidebarUserName');
    const sideSub = elements.sidebarUserSub || document.getElementById('sidebarUserSub');
    const sideBtn = elements.btnSidebarAuthAction || document.getElementById('btnSidebarAuthAction');

    if (isLoggedIn) {
      const displayName = currentUser.displayName || 'Marvo User';
      const initial = (displayName.charAt(0) || 'U').toUpperCase();

      if (sideName) sideName.textContent = displayName;
      if (sideSub) sideSub.textContent = 'Cloud Memory Active';
      if (sideDot) sideDot.classList.add('online');

      if (currentUser.photoURL && sideImg) {
        sideImg.src = currentUser.photoURL;
        sideImg.classList.remove('hidden');
        if (sidePlaceholder) sidePlaceholder.classList.add('hidden');
      } else if (sidePlaceholder) {
        sidePlaceholder.textContent = initial;
        sidePlaceholder.classList.remove('hidden');
        if (sideImg) sideImg.classList.add('hidden');
      }

      if (sideBtn) {
        sideBtn.title = 'Sign Out';
        sideBtn.innerHTML = `<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>`;
      }
    } else {
      if (sideName) sideName.textContent = 'Guest User';
      if (sideSub) sideSub.textContent = 'Offline Storage';
      if (sideDot) sideDot.classList.remove('online');

      if (sideImg) {
        sideImg.classList.add('hidden');
        sideImg.src = '';
      }
      if (sidePlaceholder) {
        sidePlaceholder.innerHTML = `<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>`;
        sidePlaceholder.classList.remove('hidden');
      }

      if (sideBtn) {
        sideBtn.title = 'Sign In with Google';
        sideBtn.innerHTML = `<svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/><polyline points="10 17 15 12 10 7"/><line x1="15" y1="12" x2="3" y2="12"/></svg>`;
      }
    }
  }

  function toggleUserProfileDropdown() {
    const dropdown = elements.userProfileDropdown || document.getElementById('userProfileDropdown');
    if (!dropdown) return;

    const isOpen = dropdown.classList.contains('show');
    if (isOpen) {
      closeUserProfileDropdown();
    } else {
      // Close other open menus
      document.getElementById('appDropdown')?.classList.remove('show');
      document.getElementById('historyContextMenu')?.classList.remove('show');
      document.getElementById('attachMenu')?.classList.remove('show');
      dropdown.classList.add('show');
    }
  }

  function closeUserProfileDropdown() {
    const dropdown = elements.userProfileDropdown || document.getElementById('userProfileDropdown');
    if (dropdown) dropdown.classList.remove('show');
  }

  function triggerLogin() {
    if (isLoadingState) return;

    if (loginCallbacks.length === 0) {
      console.log('[MarvoAuthUI] Google Sign-In clicked (Step 2 Firebase Auth listener ready)');
      if (typeof window.showToast === 'function') {
        window.showToast('Google Sign-In ready for Firebase wiring (Step 2)');
      }
      return;
    }

    loginCallbacks.forEach(cb => {
      try { cb(); } catch (err) { console.error('[MarvoAuthUI] Login callback error:', err); }
    });
  }

  function triggerLogout() {
    closeUserProfileDropdown();

    if (logoutCallbacks.length === 0) {
      console.log('[MarvoAuthUI] Sign Out clicked (Step 2 Firebase Auth listener ready)');
      renderUserProfile(null);
      if (typeof window.showToast === 'function') {
        window.showToast('Signed out of cloud session');
      }
      return;
    }

    logoutCallbacks.forEach(cb => {
      try { cb(); } catch (err) { console.error('[MarvoAuthUI] Logout callback error:', err); }
    });
  }

  /**
   * Register a callback for Google Login click
   * @param {Function} callback
   */
  function onLoginClick(callback) {
    if (typeof callback === 'function') {
      loginCallbacks.push(callback);
    }
  }

  /**
   * Register a callback for Logout click
   * @param {Function} callback
   */
  function onLogoutClick(callback) {
    if (typeof callback === 'function') {
      logoutCallbacks.push(callback);
    }
  }

  function init() {
    elements = queryElements();

    // 1. Google Login button click on overlay
    elements.btnGoogleLogin?.addEventListener('click', (e) => {
      e.preventDefault();
      triggerLogin();
    });

    // 2. Dismiss / Guest Mode buttons on overlay
    elements.btnDismissAuth?.addEventListener('click', (e) => {
      e.preventDefault();
      hideLoginScreen();
    });

    elements.btnContinueGuest?.addEventListener('click', (e) => {
      e.preventDefault();
      hideLoginScreen();
      if (typeof window.showToast === 'function') {
        window.showToast('Local on-device engine active (Guest Mode)');
      }
    });

    // 3. Error banner dismiss
    elements.btnDismissError?.addEventListener('click', (e) => {
      e.preventDefault();
      clearError();
    });

    // 4. Header User Profile button click
    elements.btnUserProfile?.addEventListener('click', (e) => {
      e.stopPropagation();
      toggleUserProfileDropdown();
    });

    // 5. Dropdown actions
    elements.btnProfileSignInPrompt?.addEventListener('click', (e) => {
      e.preventDefault();
      closeUserProfileDropdown();
      showLoginScreen();
    });

    elements.btnProfileSignOut?.addEventListener('click', (e) => {
      e.preventDefault();
      triggerLogout();
    });

    // 6. Sidebar action handlers
    elements.btnSidebarAuthAction?.addEventListener('click', (e) => {
      e.preventDefault();
      e.stopPropagation();
      if (currentUser) {
        triggerLogout();
      } else {
        showLoginScreen();
      }
    });

    elements.sidebarUserCard?.addEventListener('click', (e) => {
      if (e.target.closest('#btnSidebarAuthAction')) return;
      if (!currentUser) {
        showLoginScreen();
      } else {
        toggleUserProfileDropdown();
      }
    });

    // 7. Click outside to dismiss profile dropdown
    document.addEventListener('click', (e) => {
      if (!e.target.closest('#userProfileHeaderWrap')) {
        closeUserProfileDropdown();
      }
    });

    // 8. ESC key closes overlay and dropdown
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') {
        closeUserProfileDropdown();
      }
    });

    // Close profile dropdown when main app menu is clicked
    document.getElementById('btnAppMenu')?.addEventListener('click', () => {
      closeUserProfileDropdown();
    });

    // Render initial guest / offline profile
    renderUserProfile(null);
  }

  // Ensure DOM elements are bound when ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

  // Public Controller Export
  const MarvoAuthUI = {
    showLoginScreen,
    hideLoginScreen,
    isLoginScreenVisible,
    setLoading,
    showError,
    clearError,
    renderUserProfile,
    onLoginClick,
    onLogoutClick,
    getUser: () => currentUser,
    toggleUserProfileDropdown,
    closeUserProfileDropdown
  };

  window.MarvoAuthUI = MarvoAuthUI;

})(window);

