# (NOT) ISLAND // (01)
### Authentic Nothing OS 3.0 Dynamic Island for Android

<p align="center">
  <img src="logo.png" width="160" height="160" alt="(NOT) Island Logo" style="border-radius: 36px;" />
</p>

<p align="center">
  <b>AESTHETIC: NOTHING OS 3.0 BRUTALIST</b> &bull; <b>EQUALIZER: DOT-MATRIX RED</b> &bull; <b>TARGET: ANDROID 8.0 - 15+</b>
</p>

<p align="center">
  <a href="https://github.com/d4rxh/Nothing-Island/releases"><img src="https://img.shields.io/github/v/release/d4rxh/Nothing-Island?style=for-the-badge&color=D71920&label=RELEASE" alt="Release"/></a>
  <img src="https://img.shields.io/badge/LICENSED-MIT-white?style=for-the-badge" alt="MIT License"/>
  <img src="https://img.shields.io/badge/NOTHING%20OS-3.0%20READY-black?style=for-the-badge&logoColor=white" alt="Nothing OS 3.0 Ready"/>
</p>

---

## 🔴 Overview

**(NOT) ISLAND** is a bespoke, system-wide dynamic floating overlay engineered specifically around the front camera punch-hole of **Nothing Phone (1)**, **Nothing Phone (2)**, **Nothing Phone (2a)**, and all modern Android devices.

Unlike generic iOS Dynamic Island clones, **(NOT) ISLAND** embraces the iconic **Nothing OS 3.0 brutalist industrial aesthetic** — featuring authentic **NDot57 dot-matrix typography**, responsive red LED equalizers, OLED pitch-black containers, and micro-haptic tactile responses.

---

## ⚡ Core Features

```
┌────────────────────────────────────────────────────────┐
│  [🎵 ART]  ● [FRONT CAMERA PUNCH-HOLE]  [||| RED DOTS] │
└────────────────────────────────────────────────────────┘
                    ▲ (COMPACT ISLAND)
                    │ (Tap / Long-Press)
                    ▼
┌────────────────────────────────────────────────────────┐
│  NOTHING MEDIA // SPOTIFY                          ━   │
│  ┌─────┐  Starboy                                      │
│  │ ART │  The Weeknd                      [|||| EQUAL] │
│  └─────┘                                               │
│             [⏮️]        [ ⏸️ ]        [⏭️]              │
└────────────────────────────────────────────────────────┘
```

### 1. 🎵 Live Music Equalizer & Controller
- Automatically detects playback across **Spotify, YouTube Music, Apple Music, Telegram, Soundcloud, and Lunagram**.
- **Compact Island Mode:** Left shows circular spinning album art; right features a real-time dancing 4-column **Nothing Red Dot-Matrix Equalizer (`DotMatrixEqualizerView`)**.
- **Expanded Card Mode:** Tap to expand into a full Nothing OS 3.0 control card with marquee track title, artist name, and tactile playback buttons (`Previous`, `Play/Pause`, `Next`).

### 2. ⚡ Fast Charging Dynamic Alert
- Connecting your charger instantly triggers a smooth capsule expansion.
- Displays an animated crimson lightning bolt glyph alongside real-time battery percentage in authentic **NDot57** dot-matrix typography.

### 3. 💬 Smart Heads-Up Notifications
- Floating non-intrusive pill for **WhatsApp, Telegram, SMS, and incoming calls**.
- Briefly expands with the sender name and message preview without interrupting full-screen games or video playback.
- Automatically collapses smoothly after 4.5 seconds.

### 4. 📐 Precision Camera Punch-Hole Calibration
- **One-Tap Presets:**
  - **Nothing Phone (2 / 2a):** Centered punch-hole (`X: 0dp`, `Y: 18dp`).
  - **Nothing Phone (1):** Top-left corner punch-hole (`X: -130dp`, `Y: 16dp`).
  - **Custom Devices:** Real-time calibration sliders for Horizontal (X), Vertical (Y), Cutout Width, and Height.

### 5. 📳 Nothing Micro-Haptic Engine
- Segment ticks and haptic pops on expansion, button presses, and notification triggers.

---

## 🛡️ Privacy & Performance Guarantee

- **Zero Trackers / Zero Analytics:** 100% on-device operation.
- **Ultra-Low Memory Footprint:** Built in native Kotlin with hardware-accelerated transitions; consumes `< 25MB` RAM in the background.
- **Zero Internet Requirement:** The floating overlay and media session engine function completely offline.

---

## 🛠️ Build & Installation

### Option 1: Direct APK Download
Download the signed release APK directly from GitHub Releases:
- **[Download Latest Nothing Island APK](https://github.com/d4rxh/Nothing-Island/releases/latest)**

### Option 2: Manual Local Build
```bash
git clone https://github.com/d4rxh/Nothing-Island.git
cd Nothing-Island
./gradlew assembleRelease
```
The compiled signed APK will be located at:
`app/build/outputs/apk/release/app-release.apk`

---

## 📄 License
Licensed under the [MIT License](LICENSE).  
Copyright (c) 2026 d4rxh.
