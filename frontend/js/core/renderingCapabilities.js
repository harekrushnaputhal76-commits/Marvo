'use strict';

/* Cached, enhancement-only rendering capability probes. */
(function exposeRenderingCapabilities(global) {
  let cachedCapabilities = null;

  const safeDefaults = Object.freeze({
    backdropBlur: false,
    typedCustomProperties: false,
    webgl: false,
    reducedMotion: true,
  });

  function detectBackdropBlur() {
    if (!global.CSS || typeof global.CSS.supports !== 'function') return false;
    return global.CSS.supports('backdrop-filter', 'blur(1px)')
      || global.CSS.supports('-webkit-backdrop-filter', 'blur(1px)');
  }

  function detectTypedCustomProperties() {
    if (!global.CSS || typeof global.CSS.registerProperty !== 'function') return false;
    const probeName = '--marvo-typed-property-probe';
    try {
      global.CSS.registerProperty({
        name: probeName,
        syntax: '<number>',
        inherits: false,
        initialValue: '0',
      });
      return true;
    } catch (error) {
      return false;
    }
  }

  function detectWebGL() {
    try {
      const canvas = global.document?.createElement('canvas');
      if (!canvas || typeof canvas.getContext !== 'function') return false;
      return Boolean(
        canvas.getContext('webgl2')
        || canvas.getContext('webgl')
        || canvas.getContext('experimental-webgl')
      );
    } catch (error) {
      return false;
    }
  }

  function detectReducedMotion() {
    try {
      return Boolean(global.matchMedia('(prefers-reduced-motion: reduce)').matches);
    } catch (error) {
      return true;
    }
  }

  function detect() {
    if (cachedCapabilities) return cachedCapabilities;

    try {
      cachedCapabilities = Object.freeze({
        backdropBlur: detectBackdropBlur(),
        typedCustomProperties: detectTypedCustomProperties(),
        webgl: detectWebGL(),
        reducedMotion: detectReducedMotion(),
      });
    } catch (error) {
      cachedCapabilities = safeDefaults;
    }

    return cachedCapabilities;
  }

  global.MarvoRenderingCapabilities = detect();
  global.getMarvoRenderingCapabilities = detect;
})(window);
