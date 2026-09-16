# Android WebView Capability Matrix

Recorded 2026-09-16 for the Marvo Android target.

## Build facts

- `minSdkVersion`: 24, from `android/variables.gradle:2` and consumed by `android/app/build.gradle:8`.
- `compileSdkVersion`: 36, from `android/variables.gradle:3` and consumed by `android/app/build.gradle:5`.
- `targetSdkVersion`: 36, from `android/variables.gradle:4` and consumed by `android/app/build.gradle:9`.
- Capacitor: `8.5.2` for `@capacitor/android`, `@capacitor/core`, and CLI, from `package.json:22-31`.

## Realistic WebView range

The Gradle files constrain Android API level, not the separately updated Android System WebView package. The app can therefore run with the WebView available on Android API 24 and later, through the current WebView installed on the device. There is no exact Chromium/WebView version range in this repository. The lower end is an old, device-dependent Chromium WebView; the upper end is the device's current compatible WebView. Exact feature support needs a device check.

Capacitor 8.5.2 does not turn that into a single pinned WebView version. Android System WebView and Chrome updates, OEM images, GPU drivers, and WebView provider selection can change the effective renderer independently of this app build.

## Rendering matrix

| Feature | Assessment for the supported device range | Basis / action |
|---|---|---|
| `backdrop-filter` blur | Partially supported | Reliable on newer Chromium WebViews, but not guaranteed across the API 24-era lower end and OEM providers. Gate with `CSS.supports`; verify on the oldest target device. |
| Registered typed custom properties (`@property` / `CSS.registerProperty`) | Partially supported | Modern Chromium WebViews support them; older WebViews may parse neither the at-rule nor interpolation. Gate with a support query and retain a static fallback. |
| Conic gradients | Partially supported | Available in modern Chromium WebViews, but the minimum API 24 device may carry an older provider. Gate or use a static gradient fallback. |
| SVG filter chains | Partially supported | Basic SVG filters are broadly available, but chained/composited filters vary in performance and behavior by renderer/GPU. Device-check the actual chain. |
| WebGL2 | Partially supported | The API may exist, but context creation can fail because of GPU, driver, policy, or WebView configuration. Probe `webgl2` context creation at runtime. |
| `clip-path` with basic shapes | Supported on modern WebViews; partial across the full range | `circle()`, `ellipse()`, and inset-style shapes are generally available in current Chromium WebViews; verify the lower-end provider with `CSS.supports`. |
| `clip-path` with a `path()` | Uncertain; requires device check | Support and interpolation have historically varied more than basic shapes. Do not assume availability from the Android SDK level; probe the target WebView. |

This matrix is a planning record, not a claim that every API 24 device has the same renderer. Runtime probes remain authoritative for the running device.
