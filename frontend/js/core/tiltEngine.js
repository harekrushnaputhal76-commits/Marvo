/**
 * ================================================================
 * MARVO AI — TiltEngine (Ultra-Reactive 60FPS LERP & Aggressive Battery Saver)
 * 
 * - Zero-Lag 60FPS Mathematical Exponential LERP
 * - Decoupled Sensor Event Listener (No DOM Thrashing in Sensor Loop)
 * - Orientation Compensation (Portrait / Landscape)
 * - Zero Background Drain: Completely detaches DeviceOrientation & cancels RAF
 * - Idle Auto-Pause: 0% CPU consumption when phone is stationary
 * ================================================================
 */

class TiltEngine {
  constructor(opts = {}) {
    this.maxTilt = opts.maxTilt ?? 25;
    this.lerpSpeed = opts.smoothing ?? 10;        // Per-second convergence rate (snappy & buttery smooth)
    this.frameInterval = 1000 / (opts.fps ?? 60); // 60fps high-reactivity loop
    this.idleMs = opts.idleMs ?? 1800;           // Auto-pause loop if stationary
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
            const enabled = localStorage.getItem('marvo.vision.gyro_parallax') !== 'false';
            if (enabled) this.start();
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
    // Device is stationary — stop RAF, keep listener alive (0% CPU cost)
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
    const dt = Math.min((ts - this.lastFrame) / 1000, 0.05); // Capped at 50ms to prevent jumps
    if (dt >= (this.frameInterval / 1000)) {
      this.lastFrame = ts;
      // Mathematical frame-rate-independent exponential smoothing LERP
      const alpha = 1 - Math.exp(-this.lerpSpeed * dt);
      this.currentX += (this.targetX - this.currentX) * alpha;
      this.currentY += (this.targetY - this.currentY) * alpha;

      if (Math.abs(this.targetX - this.currentX) > 0.005 || Math.abs(this.targetY - this.currentY) > 0.005) {
        // Map roll to X gaze offset and pitch to Y gaze offset (clamped to [-12px, 12px])
        const tiltX = Math.max(-12, Math.min(12, this.currentY * 0.48));
        const tiltY = Math.max(-12, Math.min(12, this.currentX * 0.48));
        this.onUpdate({ pitch: this.currentX, roll: this.currentY, tiltX, tiltY });
      }
    }
    this.rafId = requestAnimationFrame(this.loop);
  }

  handleVisibility() {
    if (document.hidden) {
      this.stop();
    } else {
      const enabled = localStorage.getItem('marvo.vision.gyro_parallax') !== 'false';
      if (enabled) {
        this.start();
      }
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
