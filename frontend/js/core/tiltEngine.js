/**
 * ================================================================
 * MARVO AI — TiltEngine (v2: Frame-Independent LERP, Orientation Compensation, Idle Auto-Pause)
 * High-performance, battery-efficient gyroscope parallax engine with matrix3d GPU compositing.
 * ================================================================
 */

class TiltEngine {
  constructor(opts = {}) {
    this.maxTilt = opts.maxTilt ?? 16;
    this.lerpSpeed = opts.smoothing ?? 6;       // per-second convergence rate
    this.frameInterval = 1000 / (opts.fps ?? 30); // ~30fps throttle
    this.idleMs = opts.idleMs ?? 2000;         // auto-pause loop if idle
    this.onUpdate = typeof opts.onUpdate === 'function' ? opts.onUpdate : () => {};

    this.targetX = 0;
    this.targetY = 0;
    this.currentX = 0;
    this.currentY = 0;
    this.rafId = null;
    this.lastFrame = 0;
    this.idleTimer = null;
    this.active = false;
    this.suspendedByIdle = false;

    this.boundOrientation = this.handleOrientation.bind(this);
    this.boundVisibility = this.handleVisibility.bind(this);
    this.loop = this.loop.bind(this);

    // Hook into Capacitor native lifecycle when available
    try {
      if (window.Capacitor?.Plugins?.App?.addListener) {
        window.Capacitor.Plugins.App.addListener('appStateChange', ({ isActive }) => {
          if (isActive) {
            if (this.active) this.start();
          } else {
            this.stop();
          }
        });
      }
    } catch (e) {
      console.warn('[TiltEngine] Capacitor App listener error:', e);
    }
  }

  clamp(v, m) {
    return v < -m ? -m : v > m ? m : v;
  }

  // Compensate for landscape/portrait so tilt axes stay correct
  getScreenAngle() {
    return (window.screen?.orientation?.angle ?? window.orientation ?? 0);
  }

  handleOrientation(e) {
    if (e.beta === null || e.gamma === null) return;
    let beta = e.beta > 90 ? 90 : e.beta < -90 ? -90 : e.beta;
    let gamma = e.gamma;

    const angle = this.getScreenAngle();
    if (angle === 90) {
      const temp = beta; beta = gamma; gamma = -temp;
    } else if (angle === -90 || angle === 270) {
      const temp = beta; beta = -gamma; gamma = temp;
    } else if (angle === 180) {
      beta = -beta; gamma = -gamma;
    }

    // Default resting phone angle in hand is ~45 degrees
    this.targetX = this.clamp(beta - 45, this.maxTilt);
    this.targetY = this.clamp(gamma, this.maxTilt);

    // Reset idle timer — real sensor data is flowing
    if (this.idleTimer) clearTimeout(this.idleTimer);
    if (this.suspendedByIdle) {
      this.suspendedByIdle = false;
      this.resumeLoop();
    }
    this.idleTimer = setTimeout(() => this.pauseLoopIdle(), this.idleMs);
  }

  pauseLoopIdle() {
    // Device is stationary — stop RAF, keep listener alive (near-zero cost)
    this.suspendedByIdle = true;
    if (this.rafId !== null) {
      cancelAnimationFrame(this.rafId);
      this.rafId = null;
    }
  }

  resumeLoop() {
    if (!this.active || this.rafId !== null) return;
    this.lastFrame = performance.now();
    this.rafId = requestAnimationFrame(this.loop);
  }

  loop(ts) {
    if (!this.active || this.suspendedByIdle) return;
    const dt = ts - this.lastFrame;
    if (dt >= this.frameInterval) {
      this.lastFrame = ts;
      // Frame-rate-independent exponential smoothing
      const alpha = 1 - Math.exp(-this.lerpSpeed * (dt / 1000));
      this.currentX += (this.targetX - this.currentX) * alpha;
      this.currentY += (this.targetY - this.currentY) * alpha;

      if (Math.abs(this.targetX - this.currentX) > 0.01 || Math.abs(this.targetY - this.currentY) > 0.01) {
        this.onUpdate({ pitch: this.currentX, roll: this.currentY });
      }
    }
    this.rafId = requestAnimationFrame(this.loop);
  }

  handleVisibility() {
    if (document.hidden) {
      this.stop();
    } else {
      this.start();
    }
  }

  async start() {
    if (this.active) return;
    this.active = true;
    this.suspendedByIdle = false;

    const DME = window.DeviceOrientationEvent;
    if (typeof DME?.requestPermission === 'function') {
      try {
        const res = await DME.requestPermission();
        if (res !== 'granted') {
          this.active = false;
          return;
        }
      } catch {
        this.active = false;
        return;
      }
    }

    window.addEventListener('deviceorientation', this.boundOrientation, { passive: true });
    document.addEventListener('visibilitychange', this.boundVisibility);
    this.lastFrame = performance.now();
    this.rafId = requestAnimationFrame(this.loop);
  }

  stop() {
    this.active = false;
    this.suspendedByIdle = false;
    window.removeEventListener('deviceorientation', this.boundOrientation);
    if (this.idleTimer) {
      clearTimeout(this.idleTimer);
      this.idleTimer = null;
    }
    if (this.rafId !== null) {
      cancelAnimationFrame(this.rafId);
      this.rafId = null;
    }
  }

  destroy() {
    this.stop();
    document.removeEventListener('visibilitychange', this.boundVisibility);
  }
}

/**
 * GPU-composited transform via matrix3d (avoids style recalc / layout)
 */
function applyTilt(el, pitch, roll) {
  if (!el) return;
  const rx = -pitch * (Math.PI / 180);
  const ry = roll * (Math.PI / 180);
  const cosX = Math.cos(rx), sinX = Math.sin(rx);
  const cosY = Math.cos(ry), sinY = Math.sin(ry);

  // Combined rotateX * rotateY as matrix3d, with perspective baked in (d=1000)
  const m = [
    cosY, 0, sinY, 0,
    sinX * sinY, cosX, -sinX * cosY, 0,
    -cosX * sinY, sinX, cosX * cosY, 0,
    0, 0, 0, 1
  ];
  el.style.transform = `perspective(1000px) matrix3d(${m.join(',')})`;
}

// Global attachment for modular usage across Marvo
window.TiltEngine = TiltEngine;
window.applyTilt = applyTilt;

