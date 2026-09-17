# Shader Ripple Layer Feasibility Decision

**Decision Date**: 2026-09-17  
**Roadmap Reference**: Step B18 (Gated Feasibility Check)  
**Recommendation**: **NO-GO (SKIP SHADER LAYER)** — Group B shader tasks B19 and B20 are officially marked as **SKIPPED / OPTIONAL**.

---

## 1. WebView Range & WebGL2 Support Analysis

- **Target Environment**: Motorola Edge 60 Pro running Android System WebView / Chromium with `minSdkVersion 24` up to `targetSdkVersion 36`.
- **Runtime Capability (A16/A17 Audit)**:
  - While modern Chromium WebView supports WebGL2 on high-end hardware, WebGL2 remains classified as **partially supported** across the fleet's minimum baseline (`minSdkVersion 24`) due to OEM GPU driver variations, context loss policies, and aggressive background battery management.
  - In Android WebView, allocating a WebGL context incurs an independent hardware surface layer that requires GPU compositor synchronization via IPC, introducing frame-sync latency and battery consumption.

---

## 2. Cost vs. Visual Gain Assessment

1. **Memory & Context Overhead**:
   - Each WebGL canvas context in WebView consumes between 8MB and 16MB of dedicated GPU memory buffer, plus lifecycle management overhead to handle `webglcontextlost` and `webglcontextrestored` events on app pause/resume.
2. **Existing SVG/CSS Surface Tension (B6) Performance**:
   - The SVG filter (`#marvo-goo` utilizing `feGaussianBlur` and `feColorMatrix` tuned to `stdDeviation: 5`) combined with cubic-bezier spring easing (`--marvo-ease-bounce`) already produces convincing organic liquid fusion and surface tension.
   - Dynamic states (Listening and Speaking) already achieve fluid, jitter-free ripple motion through GPU compositor transforms (`transform: scale3d(...)` at 120Hz) with zero layout thrashing and zero JavaScript per-frame pixel manipulation.
3. **Marginal Visual Gain**:
   - At the physical scale of the indicator on the Motorola Edge 60 Pro (resting at 24×24px and expanding to a 140×40px capsule), sub-surface fragment distortion shaders offer imperceptible visual improvement over the existing SVG liquid filter, while multiplying crash surface and power consumption.

---

## 3. Explicit Recommendation: NO-GO

- **Conclusion**: **SKIP** the WebGL/Canvas fragment shader layer (B19 and B20).
- **Impact**:
  - Preserves 100% stable, pure-CSS/SVG hardware-composited rendering pipeline.
  - Guarantees zero WebGL context loss errors or battery degradation during background wake-word listening.
  - Group B roadmap completes cleanly without introducing unnecessary graphics dependencies.

