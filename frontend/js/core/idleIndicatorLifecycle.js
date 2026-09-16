'use strict';

(function exposeIdleIndicatorLifecycle(global) {
  let mounted = false;
  let visibilityHandler = null;
  const teardownCallbacks = new Set();

  function syncVisibilityState() {
    global.document?.documentElement?.classList.toggle(
      'marvo-idle-paused',
      Boolean(global.document?.hidden)
    );
  }

  function mount() {
    if (mounted) return;
    visibilityHandler = syncVisibilityState;
    global.document?.addEventListener('visibilitychange', visibilityHandler);
    syncVisibilityState();
    mounted = true;
  }

  function registerTeardown(callback) {
    if (typeof callback !== 'function') return () => {};
    teardownCallbacks.add(callback);
    return () => teardownCallbacks.delete(callback);
  }

  function teardown() {
    if (!mounted) return;
    if (visibilityHandler) {
      global.document?.removeEventListener('visibilitychange', visibilityHandler);
      visibilityHandler = null;
    }
    teardownCallbacks.forEach((callback) => {
      try {
        callback();
      } catch (error) {
        console.warn('[IdleIndicatorLifecycle] teardown callback failed:', error);
      }
    });
    teardownCallbacks.clear();
    global.document?.documentElement?.classList.remove('marvo-idle-paused');
    mounted = false;
  }

  global.MarvoIdleIndicatorLifecycle = Object.freeze({
    mount,
    registerTeardown,
    teardown,
  });

  mount();
})(window);
