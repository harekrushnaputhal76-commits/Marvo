/**
 * MARVO AI — NEXT-GEN LIVE VOICE RECORDING HUD CONTROLLER (120Hz CYBER-GLASS)
 * Module: frontend/js/ui/voiceHud.js
 * 
 * Capabilities:
 * - Seamless morphing of .input-glass into Live Voice HUD
 * - Real-time Web Audio API AnalyserNode with 120Hz liquid gradient visualizer + Android WebView fallback
 * - Live transcript tracking & monospace recording timer
 * - One-tap tactile Cancel (discard) & Send (dispatch) controls
 * - Full 4-State Lifecycle Sync: Idle -> Listening -> Thinking -> Speaking
 * - Dynamic Island + Apple Siri Liquid Plasma Orb resonance
 */

(function initVoiceHudModule() {
  'use strict';

  // State
  let isRecording = false;
  let timerInterval = null;
  let elapsedSeconds = 0;
  let animFrameId = null;
  let audioContext = null;
  let analyserNode = null;
  let audioStream = null;
  let dataArray = null;

  // DOM Elements
  let inputGlass = null;
  let normalRow = null;
  let voiceHud = null;
  let btnCancel = null;
  let btnSend = null;
  let canvas = null;
  let ctx = null;
  let timerEl = null;
  let transcriptEl = null;
  let statusTextEl = null;

  function initDOMElements() {
    inputGlass = document.querySelector('.input-glass');
    normalRow = document.getElementById('inputNormalRow');
    voiceHud = document.getElementById('voiceRecordingHud');
    btnCancel = document.getElementById('btnVoiceHudCancel');
    btnSend = document.getElementById('btnVoiceHudSend');
    canvas = document.getElementById('voiceHudWaveCanvas');
    timerEl = document.getElementById('voiceHudTimer');
    transcriptEl = document.getElementById('voiceHudTranscriptText');
    statusTextEl = document.getElementById('voiceHudStatusText');

    if (canvas) {
      ctx = canvas.getContext('2d');
      // Set high-DPI canvas resolution
      const dpr = window.devicePixelRatio || 1;
      const rect = canvas.getBoundingClientRect();
      const w = rect.width > 0 ? rect.width : 280;
      const h = rect.height > 0 ? rect.height : 28;
      canvas.width = w * dpr;
      canvas.height = h * dpr;
      if (ctx) ctx.scale(dpr, dpr);
    }
  }

  // Timer Helper
  function startTimer() {
    elapsedSeconds = 0;
    if (timerEl) timerEl.textContent = '00:00';
    if (timerInterval) clearInterval(timerInterval);
    timerInterval = setInterval(() => {
      elapsedSeconds++;
      const m = String(Math.floor(elapsedSeconds / 60)).padStart(2, '0');
      const s = String(elapsedSeconds % 60).padStart(2, '0');
      if (timerEl) timerEl.textContent = `${m}:${s}`;
    }, 1000);
  }

  function stopTimer() {
    if (timerInterval) clearInterval(timerInterval);
    timerInterval = null;
    elapsedSeconds = 0;
    if (timerEl) timerEl.textContent = '00:00';
  }

  // 120Hz Liquid Waveform Visualizer
  function startVisualizer() {
    if (!canvas || !ctx) return;
    const w = canvas.width / (window.devicePixelRatio || 1);
    const h = canvas.height / (window.devicePixelRatio || 1);
    const barCount = 28;
    const barWidth = Math.max(3, (w - (barCount - 1) * 3) / barCount);

    let phase = 0;

    function renderFrame() {
      if (!isRecording) return;
      ctx.clearRect(0, 0, w, h);

      let rawVolume = 0;
      if (analyserNode && dataArray) {
        analyserNode.getByteFrequencyData(dataArray);
        let sum = 0;
        for (let i = 0; i < dataArray.length; i++) sum += dataArray[i];
        rawVolume = (sum / dataArray.length) / 255;
      }

      phase += 0.08;

      // Draw multi-layered neon gradient bars
      for (let i = 0; i < barCount; i++) {
        const x = i * (barWidth + 3);
        const centerDist = 1 - Math.abs(i - barCount / 2) / (barCount / 2);
        
        let barHeight = 4;
        if (analyserNode && dataArray) {
          const sampleIdx = Math.floor((i / barCount) * dataArray.length * 0.7);
          const freqVal = (dataArray[sampleIdx] || 0) / 255;
          barHeight = Math.max(4, freqVal * h * 0.88 * centerDist + Math.sin(phase + i * 0.4) * 3);
        } else {
          // Organic reactive wave fallback for Android WebView
          const baseWave = (Math.sin(phase + i * 0.3) + 1) * 0.5;
          const ripple = Math.sin(phase * 1.5 - i * 0.2) * 0.3;
          barHeight = Math.max(4, (baseWave + ripple) * (h * 0.75) * centerDist + 3);
        }

        const y = (h - barHeight) / 2;

        const grad = ctx.createLinearGradient(0, y, 0, y + barHeight);
        grad.addColorStop(0, '#00f0ff');
        grad.addColorStop(1, '#00ff88');

        ctx.fillStyle = grad;
        ctx.shadowColor = 'rgba(0, 255, 136, 0.45)';
        ctx.shadowBlur = 6;

        // Rounded bar
        ctx.beginPath();
        ctx.roundRect(x, y, barWidth, barHeight, 3);
        ctx.fill();
      }

      // Feed volume scale to Dynamic Island & Apple Siri Liquid Orb
      const currentVol = Math.min(100, Math.round(rawVolume * 100));
      if (window.setVoiceState) {
        window.setVoiceState('listening', currentVol);
      }
      if (window.setSiriVoiceState) {
        window.setSiriVoiceState('listening', currentVol);
      }

      animFrameId = requestAnimationFrame(renderFrame);
    }

    renderFrame();
  }

  function stopVisualizer() {
    if (animFrameId) cancelAnimationFrame(animFrameId);
    animFrameId = null;
    if (canvas && ctx) {
      const w = canvas.width / (window.devicePixelRatio || 1);
      const h = canvas.height / (window.devicePixelRatio || 1);
      ctx.clearRect(0, 0, w, h);
    }
  }

  // Web Audio Initializer
  async function setupWebAudio() {
    try {
      if (!audioContext) {
        const AudioCtx = window.AudioContext || window.webkitAudioContext;
        if (AudioCtx) audioContext = new AudioCtx();
      }
      if (audioContext && audioContext.state === 'suspended') {
        await audioContext.resume();
      }

      if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia && !analyserNode) {
        audioStream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
        analyserNode = audioContext.createAnalyser();
        analyserNode.fftSize = 64;
        analyserNode.smoothingTimeConstant = 0.8;
        const source = audioContext.createMediaStreamSource(audioStream);
        source.connect(analyserNode);
        dataArray = new Uint8Array(analyserNode.frequencyBinCount);
      }
    } catch (err) {
      console.warn('[VoiceHUD] Web Audio stream notice (using reactive fallback):', err.message);
    }
  }

  function teardownWebAudio() {
    if (audioStream) {
      try {
        audioStream.getTracks().forEach(track => track.stop());
      } catch {}
      audioStream = null;
    }
    analyserNode = null;
    dataArray = null;
  }

  // Open Voice HUD
  async function openVoiceHud() {
    initDOMElements();
    if (!inputGlass || !voiceHud) return;

    isRecording = true;
    inputGlass.classList.add('voice-hud-active');
    voiceHud.classList.remove('hidden');

    if (transcriptEl) {
      transcriptEl.textContent = 'Listening to your voice...';
      transcriptEl.style.color = 'rgba(255, 255, 255, 0.88)';
    }
    if (statusTextEl) statusTextEl.textContent = 'LISTENING...';

    startTimer();
    await setupWebAudio();
    startVisualizer();

    // Transition Dynamic Island & Apple Siri Orb to listening
    if (window.dynamicIslandInstance) {
      window.dynamicIslandInstance.open();
    }
    if (window.setVoiceState) {
      window.setVoiceState('listening', 15);
    }
    if (window.setSiriVoiceState) {
      window.setSiriVoiceState('listening', 15);
    }
    if (typeof window.openSiriOrb === 'function') {
      window.openSiriOrb();
    }
  }

  // Close Voice HUD
  function closeVoiceHud(targetState = 'idle') {
    isRecording = false;
    stopTimer();
    stopVisualizer();
    teardownWebAudio();

    if (inputGlass) inputGlass.classList.remove('voice-hud-active');
    if (voiceHud) voiceHud.classList.add('hidden');

    if (window.setVoiceState) {
      window.setVoiceState(targetState, 0);
    }
    if (window.setSiriVoiceState) {
      window.setSiriVoiceState(targetState, 0);
    }
  }

  // Update Transcript from Speech Recognition
  function updateTranscript(text) {
    if (!transcriptEl) initDOMElements();
    if (transcriptEl && text) {
      transcriptEl.textContent = text;
      transcriptEl.style.color = '#00f0ff';
    }
  }

  // Wire Button Controls
  function bindControls() {
    initDOMElements();

    // 1. Mic Button on Input Bar -> Open Voice HUD
    const btnMic = document.getElementById('btnMic');
    if (btnMic) {
      btnMic.addEventListener('click', (e) => {
        // Prevent default toggle if opening HUD
        if (!isRecording) {
          openVoiceHud();
        }
      });
    }

    // 2. Cancel Button -> Discard & Restore Input Bar
    if (btnCancel) {
      btnCancel.addEventListener('click', () => {
        closeVoiceHud('idle');
        const btnIslandClose = document.getElementById('btnIslandClose');
        if (btnIslandClose) btnIslandClose.click();
        if (typeof window.closeSiriOrb === 'function') window.closeSiriOrb();
        if (window.showToast) window.showToast('Voice recording discarded');
      });
    }

    // 3. Send Button -> Discard HUD, Enter Thinking, Send to Traffic Police
    if (btnSend) {
      btnSend.addEventListener('click', () => {
        let textToSend = '';
        if (transcriptEl && transcriptEl.textContent !== 'Listening to your voice...') {
          textToSend = transcriptEl.textContent.trim();
        }

        // Check fallback from input or dynamic island
        if (!textToSend) {
          const islandText = document.getElementById('islandResponseText');
          if (islandText && islandText.textContent && !islandText.textContent.includes('Listening')) {
            textToSend = islandText.textContent.trim();
          }
        }

        closeVoiceHud('thinking');

        // Flag that this message was submitted via voice for automated TTS spoken answer
        window.__lastInputWasVoice = true;

        const btnIslandSend = document.getElementById('btnIslandSend');
        if (btnIslandSend) {
          btnIslandSend.click();
        } else if (textToSend && window.marvo && typeof window.marvo.sendMessage === 'function') {
          window.marvo.sendMessage(textToSend);
        } else {
          const msgInput = document.getElementById('msgInput');
          if (msgInput && textToSend) {
            msgInput.value = textToSend;
            const btnSendMain = document.getElementById('btnSend');
            if (btnSendMain) btnSendMain.click();
          }
        }
      });
    }

    // 4. Hook into live transcript updates from speech recognition observer
    const observerTarget = document.getElementById('islandResponseText');
    if (observerTarget) {
      const observer = new MutationObserver(() => {
        const txt = observerTarget.textContent;
        if (txt && !txt.includes('Listening to you')) {
          updateTranscript(txt);
        }
      });
      observer.observe(observerTarget, { childList: true, characterData: true, subtree: true });
    }
  }

  // ══════════════════════════════════════════════════════════════
  // FULL 4-STATE LIFECYCLE HOOKS
  // ══════════════════════════════════════════════════════════════
  // Listen for voice state transitions across the application
  const originalSetVoiceState = window.setVoiceState;
  window.setVoiceState = function(state, volume) {
    if (typeof originalSetVoiceState === 'function') {
      try { originalSetVoiceState(state, volume); } catch {}
    } else if (window.dynamicIslandInstance) {
      try { window.dynamicIslandInstance.setVoiceState(state, volume); } catch {}
    }

    if (typeof window.setSiriVoiceState === 'function') {
      try { window.setSiriVoiceState(state, volume); } catch {}
    }

    // Synchronize UI elements with 4-state lifecycle
    if (state === 'thinking') {
      if (isRecording) closeVoiceHud('thinking');
    } else if (state === 'speaking') {
      if (isRecording) closeVoiceHud('speaking');
    } else if (state === 'idle') {
      if (isRecording) closeVoiceHud('idle');
    }
  };

  // Expose Global Controller
  window.MarvoVoiceHUD = {
    open: openVoiceHud,
    close: closeVoiceHud,
    updateTranscript,
    isRecording: () => isRecording
  };

  // Auto-bind when DOM is ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', bindControls);
  } else {
    bindControls();
  }
})();
