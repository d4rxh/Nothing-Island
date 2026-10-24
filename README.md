# Nothing Island

A minimal, system-wide dynamic overlay engineered for Nothing OS and modern Android devices.

<p align="center">
  <img src="logo.png" width="120" height="120" alt="Nothing Island Logo" style="border-radius: 24px;" />
</p>

<p align="center">
  <a href="https://github.com/d4rxh/Nothing-Island/releases"><img src="https://img.shields.io/github/v/release/d4rxh/Nothing-Island?style=flat-square&color=D71920&label=Release%20v1.0.16" alt="Release"/></a>
  <img src="https://img.shields.io/badge/License-MIT-black?style=flat-square" alt="MIT License"/>
  <img src="https://img.shields.io/badge/Android-8.0%20--%2015+-black?style=flat-square" alt="Android Support"/>
  <img src="https://img.shields.io/badge/Privacy-100%25%20On--Device-green?style=flat-square" alt="100% On-Device"/>
  <img src="https://img.shields.io/badge/Internet-Zero%20Permissions-black?style=flat-square" alt="Zero Internet"/>
</p>

---

## Overview

Nothing Island is an open-source, system-level dynamic overlay runtime tailored for front camera punch-holes on Nothing Phone (1), Nothing Phone (2), Nothing Phone (2a), and other modern Android devices.

Designed around Nothing OS principles:
- Authentic NDot57 dot-matrix typography.
- Deep pitch black surfaces with minimal contrast borders.
- Dedicated 4-column red LED equalizer canvas.
- 100% native Kotlin implementation with zero idle background CPU overhead.

---

## Features

### Media Controls & Visualizer
- Shows current song title, artist, and album art from any media player (Spotify, YouTube, Apple Music, SoundCloud).
- Real-time 4-column red LED equalizer visualizer during active playback.
- Expanding card exposes full media controls: previous, play/pause, next, and progress.
- Dismissing or closing the media player clears the island immediately.

### Incoming & Ongoing Calls
- Displays incoming and active phone calls with caller name and number.
- Ongoing duration timer counter.
- One-tap Answer and Decline actions.

### Built-in Timer & Stopwatch
- Embedded stopwatch with lap timing.
- Countdown timer with real-time remaining display.
- Accurate timekeeping backed by system monotonic clocks, maintaining consistency across screen off and process recreation.

### Turn-by-Turn Navigation
- Compact turn direction indicator, maneuver instructions, and distance to next turn when navigation is active (Google Maps).
- Tapping the island opens the navigation application.

### Device & Connectivity Events
- Real-time charging status and battery percentage indicator upon plugging in.
- Bluetooth device connection and disconnection alerts.
- Headset plug/unplug events.
- Ringer mode changes (Silent, Vibrate, Normal).

### Inline Quick Reply
- Message preview for incoming notifications (WhatsApp, Telegram, SMS, Signal).
- Inline reply text box inside the expanded island card, allowing direct replies without leaving the current app.

### Hardware Notch Alignment & Bezel Integration
- **Attach to Top Bezel**: Mounts flush against the top display border with zero gap or shadow halo, creating an integrated notch aesthetic.
- **Landscape Notch Anchoring**: During landscape gaming or fullscreen video, the island dynamically anchors around the physical camera hole along the short edge, keeping in-game controls and HUDs unobstructed.

### Lock Screen Visibility
- Renders cleanly over the lock screen using Android's native accessibility overlay, without obstructing PIN, password, or biometric unlock prompts.

---

## Calibration & Hardware Profiles

Fine-tune the overlay position and geometry to match any front camera punch-hole:

| Parameter | Range | Description |
| :--- | :--- | :--- |
| Horizontal Offset | -120 dp to +120 dp | Positions the island horizontally |
| Vertical Offset | 0 dp to 60 dp | Adjusts top margin to align with display cutout |
| Island Width | 70 dp to 220 dp | Resting compact width |
| Island Height | 28 dp to 50 dp | Vertical capsule thickness |
| Corner Radius | 8 dp to 26 dp | Capsule corner curvature |
| Cutout Spacer | 16 dp to 44 dp | Camera punch-hole clearance spacer |

### Default Profiles
- **Nothing Phone (2 / 2a)**: X: 0 dp, Y: 14 dp, Width: 110 dp, Height: 36 dp, Radius: 20 dp, Cutout: 28 dp
- **Nothing Phone (1)**: X: -75 dp, Y: 14 dp, Width: 110 dp, Height: 36 dp, Radius: 20 dp, Cutout: 28 dp

---

## Privacy & Security

Nothing Island is built with a zero-trust, privacy-first architecture:
- **Zero Internet Permissions**: `android.permission.INTERNET` is strictly omitted from the manifest. The Android operating system kernel denies all network access to the app UID.
- **Zero Telemetry**: No third-party SDKs, analytics, trackers, or crash reporters.
- **On-Device Only**: Notification snippets and media titles are processed solely in volatile local memory and discarded immediately upon dismissal.

---

## Download & Installation

Signed production APKs are available on GitHub Releases:

- **Latest Release**: [Nothing Island v1.0.16](https://github.com/d4rxh/Nothing-Island/releases/tag/v1.0.16)
- **Direct APK**: [Nothing-Island-v1.0.16.apk](https://github.com/d4rxh/Nothing-Island/releases/download/v1.0.16/Nothing-Island-v1.0.16.apk)

### Required Permissions
- **Display over other apps**: Needed to render the floating punch-hole capsule.
- **Notification access**: Needed to display media metadata and notifications.
- **Accessibility service (optional)**: Needed to display the island over the lock screen.

---

## Building from Source

### Requirements
- Android Studio Ladybug or later / JDK 17
- Android SDK 35 with build-tools 35.0.0
- Gradle 8.9+

### Commands
```bash
git clone https://github.com/d4rxh/Nothing-Island.git
cd Nothing-Island
./gradlew assembleRelease
```

The signed production APK will be generated at:
`app/build/outputs/apk/release/app-release.apk`

---

## License

This project is licensed under the [MIT License](LICENSE).
