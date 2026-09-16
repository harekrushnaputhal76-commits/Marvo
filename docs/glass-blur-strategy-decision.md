# Group B Glass Blur Strategy

Recorded 2026-09-16 from the A16 WebView matrix and A17 runtime flags.

## Decision

Use a progressive-enhancement CSS path. The default visual contract is the fallback approximation; the reliable WebView path adds native `backdrop-filter` blur over the same translucent surface.

### Primary path: reliable WebView blur

Choose this path when `window.marvo.renderingCapabilities.backdropBlur === true`. Apply the expanded surface's existing translucent glass background and a restrained `backdrop-filter: blur(...)` (with the vendor-prefixed form where needed). Keep the fallback surface underneath so the result remains readable if blur is visually weak.

### Fallback path: no reliable blur

When `backdropBlur` is false, use pre-blurred gradient layers baked into the CSS surface, translucent token-backed fills, and a static noise texture or precomputed noise layer. These are paint-time assets already present in the stylesheet or packaged assets, so the fallback adds no recurring animation, filter, canvas, or per-frame computation. It should preserve contrast and depth without depending on blur support.

The fallback is the safe visual baseline: if detection is wrong and blur is unavailable despite a true flag, the translucent/pre-blurred layers still render beneath the failed enhancement. If detection is false on a device that could blur, the app loses softness but remains visually coherent and functional.

### Native Android render-effect layer

Do not add a native Android `RenderEffect` path for Group B. It requires native shell code, cannot blur WebView content beneath the surface without an explicit compositing bridge, and adds a separate maintenance surface across Android/WebView lifecycles. The device range is already renderer-dependent, and the CSS fallback handles unsupported blur without that cost. Reconsider only if profiling on a real device demonstrates a user-visible requirement that CSS cannot meet.

## Exact switching logic

1. Read the cached A17 flag `window.marvo.renderingCapabilities.backdropBlur` once when the expanded surface is created.
2. `true`: enable the CSS backdrop-blur enhancement over the fallback layers.
3. `false`: leave the backdrop-blur enhancement disabled and use only the static pre-blurred gradients, translucency, and noise layers.
4. If detection is wrong, the fallback remains underneath and the failure is limited to missing softness or an ineffective blur enhancement; no functional state or interaction depends on the result.

No expanded-state implementation is included in this record.
