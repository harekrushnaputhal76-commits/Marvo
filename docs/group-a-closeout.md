# Group A Closeout

Recorded 2026-09-16. The local repository does not encode A-number labels in commit metadata, so the list below is the complete Group A chain after the pre-Group-A baseline `f6127c4`, covering the requested A4-A24 work.

## Commit Inventory

1. `5489b27` - `fix(ui): mount voice indicator into assistant render tree`
2. `4592a3b` - `chore(build): add explicit web build + native sync pipeline`
3. `12ffd37` - `refactor(ui): remove legacy bottom-anchored voice indicator`
4. `b360b9b` - `test(ui): verify no regressions after legacy indicator removal`
5. `dce26eb` - `feat(ui): add viewport-fit and safe-area anchor variables`
6. `12dc3ae` - `feat(ui): add Marvo design token layer`
7. `f534aec` - `feat(ui): add top-anchored idle voice indicator`
8. `64bca3f` - `fix(ui): guarantee viewport anchoring for voice indicator`
9. `b6cc8a0` - `feat(ui): add glass depth layers to idle indicator`
10. `cd004b6` - `feat(ui): add ambient breathing motion to idle indicator`
11. `08cd0f6` - `docs: record target WebView rendering capability matrix`
12. `251b411` - `feat(ui): add runtime rendering capability detection`
13. `db04d49` - `docs: record glass blur strategy decision`
14. `920a1c0` - `feat(ui): add shared liquid merge filter definition`
15. `a9ba4b4` - `refactor(ui): mount idle indicator inside liquid filter context`
16. `5a53a80` - `feat(a11y): add reduced-motion fallback for voice indicator`
17. `89e3d58` - `perf(ui): pause and tear down idle indicator work when hidden`

There are no separate A23 or A24 commits in the repository history; Group A ends at the A22 lifecycle commit.

## Created File Inventory

| File | Single responsibility |
|---|---|
| `BUILD.md` | Documents the explicit web build and Capacitor Android sync pipeline. |
| `frontend/css/tokens.css` | Single source of truth for the Marvo indicator color, glass, motion, geometry, elevation, and later layer tokens. |
| `frontend/js/core/renderingCapabilities.js` | Runs cached startup probes for backdrop blur, typed custom properties, WebGL, and reduced motion; exposes the frozen result. |
| `frontend/js/core/idleIndicatorLifecycle.js` | Owns idle-layer visibility pause/resume, teardown registration, and the single lifecycle teardown entry point. |
| `docs/webview-capability-matrix.md` | Records Android/WebView targets and the rendering support matrix. |
| `docs/glass-blur-strategy-decision.md` | Records the Group B CSS blur/fallback/native-render-effect decision. |

Existing files were adapted rather than recreated for the indicator DOM, app state wiring, Android shell, package scripts, CSS, and legacy removal work.

## A10 Token Reference

| Group | Tokens |
|---|---|
| Colors | `--marvo-color-blue: #0072ff`; `--marvo-color-cyan: #00f0ff`; `--marvo-color-violet: #7b2cbf`; `--marvo-color-fuchsia: #c026d3`; `--marvo-color-magenta: #ff007f`; `--marvo-color-orange: #ff6b35`; `--marvo-color-amber: #ff9e00` |
| Gradient stops | `--marvo-grad-stop-blue: var(--marvo-color-blue)`; `--marvo-grad-stop-violet: var(--marvo-color-violet)`; `--marvo-grad-stop-magenta: var(--marvo-color-magenta)`; `--marvo-grad-stop-orange: var(--marvo-color-orange)` |
| Composed gradients | `--marvo-gradient-idle`; `--marvo-conic-idle`; `--marvo-gradient-thinking` |
| Glass | `--marvo-glass-bg: rgba(8, 12, 20, 0.88)`; `--marvo-glass-bg-tint: rgba(11, 16, 26, 0.85)`; `--marvo-glass-surface-opacity: 0.88`; `--marvo-glass-border-color: rgba(0, 240, 255, 0.18)`; `--marvo-glass-border-highlight: rgba(255, 255, 255, 0.20)`; `--marvo-glass-border-subtle: rgba(255, 255, 255, 0.08)`; `--marvo-glass-blur-radius: 25px`; `--marvo-glass-blur: blur(var(--marvo-glass-blur-radius)) saturate(180%)`; `--marvo-glass-shadow` |
| Motion | `--marvo-ease-spring: cubic-bezier(0.32, 0.72, 0, 1)`; `--marvo-ease-bounce: cubic-bezier(0.34, 1.56, 0.64, 1)`; `--marvo-ease-smooth: cubic-bezier(0.16, 1, 0.3, 1)`; `--marvo-duration-fast: 0.2s`; `--marvo-duration-base: 0.38s`; `--marvo-duration-slow: 0.6s` |
| Geometry | `--marvo-geo-idle-diameter: 24px`; `--marvo-geo-capsule-width: 140px`; `--marvo-geo-capsule-height: 40px`; `--marvo-geo-expanded-min-width: 280px`; `--marvo-geo-expanded-width: 92%`; `--marvo-geo-expanded-max-width: 440px`; `--marvo-geo-height-idle: 24px`; `--marvo-geo-height-listening: 40px`; `--marvo-geo-height-thinking: 40px`; `--marvo-geo-height-speaking: 40px`; `--marvo-geo-height-error: 40px`; `--marvo-geo-height-expanded: 230px` |
| Radii | `--marvo-geo-radius-idle: 50%`; `--marvo-geo-radius-capsule: 20px`; `--marvo-geo-radius-expanded: 38px`; `--marvo-geo-radius-pill: 999px` |
| Elevation | `--marvo-z-hull: 1`; `--marvo-z-card: 2`; `--marvo-z-plasma: 4`; `--marvo-z-content: 5`; `--marvo-z-edge: 6`; `--marvo-z-lens: 12`; `--marvo-z-indicator-layer: 9999`; `--marvo-z-overlay: 10000`; `--marvo-z-modal: 10001` |

Later Group A additions are intentionally separate: idle depth-layer tokens, breathing tokens, and the reduced-motion crossfade convention.

## Capability Conclusions

From [docs/webview-capability-matrix.md](webview-capability-matrix.md): Android is `minSdkVersion 24`, `compileSdkVersion 36`, `targetSdkVersion 36`, and Capacitor is `8.5.2`. The Android SDK does not pin an Android System WebView version. The realistic range is the WebView installed on Android API 24 and later through the current compatible provider on the device.

Across that range, backdrop blur, typed custom properties, conic gradients, SVG filter chains, and WebGL2 are partially supported and require runtime/device checks. Basic `clip-path` shapes are reliable on modern WebViews but partial across the full range. `clip-path: path()` is explicitly uncertain and requires a device check.

From [docs/glass-blur-strategy-decision.md](glass-blur-strategy-decision.md): use CSS backdrop blur only when `window.marvo.renderingCapabilities.backdropBlur` is true. Otherwise use static pre-blurred gradients, translucency, and noise. Keep the fallback underneath because a wrong detection must reduce softness only, not functionality. Do not add native Android `RenderEffect`: it needs native shell code, cannot blur WebView content beneath the surface without compositing work, and adds a maintenance surface.

## Deviations and Adaptations

- The assumed standalone indicator component did not exist. The implementation adapted the existing `#marvo-dynamic-island`, `DOM` cache, and `window.marvo` state surface.
- A dedicated state library did not exist. The runtime detector therefore uses the existing global state pattern and classic script loading.
- A19's liquid filter definition already existed in the legacy surface. It was tightened and centralized rather than duplicated; the old dormant consumer rule was removed for definition-only verification.
- The old indicator was a larger dynamic-island subtree with legacy expansion markup. Group A reduced the idle foundation to one indicator and later wrapped it in a fixed filter context.
- Existing app-level `visibilitychange` and background-pausing behavior was retained. The new idle lifecycle module adds one owned listener and a teardown registry instead of replacing unrelated app lifecycle code.
- A13 gradient work and A14 glass depth work landed together in the glass-depth commit; there is no separate gradient commit.
- A23 and A24 were not implemented or committed in this repository.

## Physical Device Limits

The following cannot be confirmed without a physical Motorola Edge 60 Pro and its actual configured WebView/GPU:

1. The correct safe-area camera nudge value.
2. True punch-hole alignment of the indicator against the physical camera cutout.
3. Real WebView `backdrop-filter` blur behavior and the visual quality of the SVG filter chain.
4. Actual frame timing, compositor smoothness, and battery cost of the rotating gradient, breathing pulse, and filtered wrapper.

No Group A claim treats those as physically verified.

## Hygiene Confirmation

Audited against the complete Group A diff from `f6127c4` through the final closeout state:

- No hardcoded API keys, bearer tokens, access tokens, or secrets were added.
- No placeholder response text was retained; the former `Listening to you...` assignment was removed.
- No Apple trademark or product-name text was added in the final Group A diff; the added Dynamic Island comment was removed. Older unrelated repository text is outside the Group A diff.
