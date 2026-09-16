# Marvo Build & Deployment Guide

This document outlines the repeatable pipeline to sync web frontend changes into the native Android application and produce an installable APK.

---

## 1. Architecture Overview

- **Frontend (`frontend/`)**: Static vanilla web application (`index.html`, `style.css`, `app.js`, etc.).
- **Capacitor Configuration (`capacitor.config.json`)**: Configured with `"webDir": "frontend"`.
- **Native Android Shell (`android/`)**: Android Studio Gradle project hosting Capacitor bridge, native plugins, and `AssistantActivity`.
- **Native Web Assets (`android/app/src/main/assets/public/`)**: Destination directory populated by Capacitor sync.

---

## 2. Prerequisites

1. **Node.js & npm**: Node 18+ and npm 9+.
2. **Java Development Kit (JDK)**: OpenJDK 17 or 21 (configured in `JAVA_HOME`).
3. **Android SDK**: Android SDK Platform 34, Build-Tools 34.0.0+, and platform-tools (`adb`).
4. **Android SDK Location**: Defined via `ANDROID_HOME` environment variable or in `android/local.properties`:
   ```properties
   sdk.dir=/path/to/Android/Sdk
   ```

---

## 3. Command Sequence (Source Change to Installable APK)

### Step 1: Synchronize Web Changes to Native Android Assets

Whenever changes are made to `frontend/`:

```bash
npm run build
```

*Note:* `npm run build` runs `cap sync android` under the hood. This:
1. Copies assets from `frontend/` to `android/app/src/main/assets/public/`.
2. Updates `android/app/src/main/assets/capacitor.config.json` and plugin metadata.
3. Synchronizes native Capacitor plugin bindings.

Alternatively, to only sync without npm:
```bash
npx cap sync android
```

### Step 2: Build the Android Debug APK

Navigate to the `android/` directory and compile with Gradle:

**On Linux / macOS / Git Bash:**
```bash
cd android
./gradlew assembleDebug
```

**On Windows PowerShell:**
```powershell
cd android
.\gradlew assembleDebug
```

The generated APK will be located at:
```
android/app/build/outputs/apk/debug/app-debug.apk
```

### Step 3: Install APK to Connected Device

Ensure your device (e.g., Motorola Edge 60 Pro) is connected with USB Debugging enabled:

```bash
adb devices
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

---

## 4. One-Liner Quick Build & Deploy

From the project root:

```bash
npm run build && cd android && ./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 5. Build Artifacts & Git Tracking

- **Ignored Build Artifacts**:
  - `android/.gradle/` (Gradle cache)
  - `android/app/build/`, `android/build/` (Compiled bytecode, intermediates, APKs)
  - `android/local.properties` (Machine-specific SDK paths)
  - `*.apk`, `*.aab` (Distribution packages)
- **Tracked Required Source**:
  - `frontend/` (Full web source)
  - `android/app/src/` (Native Java code, layouts, drawables, manifests)
  - `capacitor.config.json` (Capacitor runtime configuration)
  - `package.json` & `package-lock.json` (Dependency definitions)
