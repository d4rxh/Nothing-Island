# (NOT) ISLAND
### System-Wide Dynamic Overlay Engine for Nothing OS 3.0 & Modern Android

<p align="center">
  <img src="logo.png" width="140" height="140" alt="(NOT) Island Logo" style="border-radius: 28px;" />
</p>

<p align="center">
  <b>AESTHETIC: NOTHING OS 3.0 BRUTALIST</b> &bull; <b>EQUALIZER: DOT-MATRIX RED</b> &bull; <b>TARGET: ANDROID 8.0 - 15+</b>
</p>

<p align="center">
  <a href="https://github.com/d4rxh/Nothing-Island/releases"><img src="https://img.shields.io/github/v/release/d4rxh/Nothing-Island?style=for-the-badge&color=D71920&label=RELEASE%20v1.0.12" alt="Release"/></a>
  <img src="https://img.shields.io/badge/LICENSED-MIT-white?style=for-the-badge" alt="MIT License"/>
  <img src="https://img.shields.io/badge/NOTHING%20OS-3.0%20READY-black?style=for-the-badge&logoColor=white" alt="Nothing OS 3.0 Ready"/>
  <img src="https://img.shields.io/badge/DATA%20PRIVACY-100%25%20ON--DEVICE-green?style=for-the-badge" alt="100% On-Device"/>
  <img src="https://img.shields.io/badge/NET%20ACCESS-ZERO%20PERMISSIONS-red?style=for-the-badge" alt="Zero Internet"/>
</p>

---

## [ // OVERVIEW ]

**(NOT) ISLAND** is a high-performance, system-level dynamic overlay runtime engineered specifically for the front camera punch-hole of **Nothing Phone (1)**, **Nothing Phone (2)**, **Nothing Phone (2a)**, and all modern Android devices running Android 8.0 through Android 15+.

Unlike conventional iOS Dynamic Island mimics that rely on bloated animation libraries, non-native web wrappers, or intrusive ad networks, **(NOT) ISLAND** is built from the ground up to honor the **Nothing OS 3.0 industrial brutalist design language**:
* **NDot57 Dot-Matrix Typography:** Authentic dot-matrix text rendering throughout the application and overlay capsules.
* **Pure OLED Pitch Black:** Zero-gradient `#000000` capsules with razor-thin `#222222` contrast borders for seamless bezel camouflage.
* **4-Column Red LED Dot Equalizer:** Custom software canvas rendering a 4-column dot-matrix audio spectrum visualizer synchronized with active media playback.
* **Zero Overhead & Ephemeral State:** 100% Kotlin native implementation operating strictly in local memory with zero background CPU drain during idle.

---

## [ // ARCHITECTURE & EVENT PIPELINE ]

```
                      ┌───────────────────────────────────────┐
                      │    SYSTEM EVENT SOURCES (ANDROID)     │
                      └──────────────────┬────────────────────┘
                                         │
        ┌────────────────────────────────┼────────────────────────────────┐
        ▼                                ▼                                ▼
┌───────────────────────┐   ┌──────────────────────────┐   ┌─────────────────────────┐
│ MediaSessionManager   │   │  NotificationListener    │   │ BatteryBroadcastRecv    │
│ Active Playback / Meta│   │  WhatsApp, SMS, Calls    │   │ AC / USB Charging Glyph │
└───────────┬───────────┘   └────────────┬─────────────┘   └────────────┬────────────┘
            │                            │                              │
            └────────────────────────┐   │   ┌──────────────────────────┘
                                     ▼   ▼   ▼
                        ┌───────────────────────────────┐
                        │   IslandOverlayManager (Core) │
                        │   Thread-Safe State Machine   │
                        └───────────────┬───────────────┘
                                        │
           ┌────────────────────────────┴───────────────────────────┐
           ▼                                                        ▼
┌──────────────────────────────────────┐  ┌──────────────────────────────────────────────────┐
│  IslandRootLayout (Custom Touch Host)│  │  NothingIslandAccessibilityService              │
│  - Edge Touch Interception           │  │  - WindowManager TYPE_ACCESSIBILITY_OVERLAY      │
│  - Orientation Observer (Landscape)  │  │  - Safe Lock Screen Rendering Without PIN Block │
└──────────────────┬───────────────────┘  └─────────────────────────┬────────────────────────┘
                   │                                                │
                   └────────────────────────┬───────────────────────┘
                                            ▼
                        ┌──────────────────────────────────────┐
                        │       NOTHING OS 3.0 CAPSULE         │
                        │    COMPACT  ◄───►  EXPANDED          │
                        └──────────────────────────────────────┘
```

---

## [ // KEY CAPABILITIES & SUBSYSTEMS ]

### 1. Persistent Media Player & Isolated Dot-Matrix Equalizer
* **Dual-State Compact Capsule:**
  * **Active Playback (`MUSIC_COMPACT`):** Circular spinning album art on the left + dancing 4-column Nothing Red Dot-Matrix Equalizer on the right.
  * **Paused Playback (`MUSIC_PAUSED_COMPACT`):** When playback is paused via the island controls or third-party apps, the island stays persistent in compact mode with frozen indicator dots and album art, preventing abrupt layout collapses.
* **Equalizer Isolation Subsystem:**
  * During incoming notifications or phone calls, the music equalizer animator is strictly suspended (`compactEqualizer.isPlaying = false`) and hidden. Notifications display their respective app icon and sender credentials with zero visual collision.
* **Expanded Media Console (`EXPANDED`):**
  * Tapping the compact pill seamlessly expands into a 290dp Nothing OS brutalist control card featuring marquee track titles, artist metadata, a dedicated Play/Pause toggle (displaying `PLAY` when paused, `PAUSE` when playing), track skipping controls, and interactive waveform scrubbers.

### 2. Intelligent Landscape Camera Notch Anchor
* **Orientation & Cutout Awareness:**
  * In standard Android window managers, status bar overlays drift awkwardly to the upper bezel when the screen rotates to landscape, drifting far away from the physical camera punch-hole and obstructing in-game HUDs (PUBG, BGMI, Call of Duty).
* **Hardware Notch Anchoring Engine:**
  * `IslandOverlayManager` listens to display orientation changes and queries real-time hardware `DisplayCutout` coordinates.
  * In landscape mode (`ROTATION_90` or `ROTATION_270`), the island dynamically anchors around the physical camera hole on the left or right vertical bezel. It never moves to the top long edge, maintaining full camera concealment even during intense gaming and fullscreen video playback.

### 3. Edge-Disallow Touch Interception Pipeline
* **Android Notification Shade Conflict Resolution:**
  * Overlays positioned near top display notches frequently suffer from touch hijacking, where Android's native status bar intercepts downward taps as pull-down notification shade gestures.
* **Deterministic Event Claiming:**
  * Custom `IslandRootLayout` invokes `requestDisallowInterceptTouchEvent(true)` on `ACTION_DOWN` within capsule bounds, ensuring status-bar edge taps reliably register to expand or collapse the island without triggering the system shade.
* **Gesture Navigation Support:**
  * Horizontal swipe gestures allow quick dismiss/minimize of active alerts.

### 4. Lock Screen Rendering via Native Accessibility Overlay
* **Non-Blocking Keyguard Security:**
  * Standard full-screen lock screen activities create dangerous security bottlenecks by intercepting back gestures, biometric prompts, and PIN keyboards.
* **TYPE_ACCESSIBILITY_OVERLAY Architecture:**
  * Leverages Android's native `AccessibilityService` window overlay layer.
  * Renders floating alerts and charging glyphs directly over the Lock Screen while completely bypassing and preserving all system biometric prompts, pattern locks, and emergency dialers.

### 5. Bezel-Flush "Attach To Top" Mode
* **iPhone-Style Bezel Integration:**
  * Toggle between a floating island capsule or an integrated notch design that connects seamlessly with the physical top bezel.
  * In attached mode, top corner radii flatten (`0dp`) while bottom corners maintain fluid curvature, simulating a built-in hardware notch.

---

## [ // HARDWARE CALIBRATION & PRESETS ]

The application features a real-time calibration dashboard allowing micrometer-precise alignment to any device's front camera punch-hole:

| Parameter | Range | Description |
| :--- | :--- | :--- |
| **Horizontal (X) Offset** | `-120 dp` to `+120 dp` | Shifts island horizontally (e.g. left corner for Phone 1, center for Phone 2). |
| **Vertical (Y) Position** | `0 dp` to `60 dp` | Adjusts top margin to align flush with display cutout. |
| **Island Minimum Width** | `70 dp` to `220 dp` | Defines compact resting width. |
| **Island Pill Height** | `28 dp` to `50 dp` | Controls vertical thickness of the capsule. |
| **Corner Radius** | `8 dp` to `26 dp` | Fine-tunes squircle vs circular pill curvature. |
| **Cutout Diameter** | `16 dp` to `44 dp` | Calibrates center punch-hole clearance spacer. |

### Built-in Hardware Profiles:
* **Nothing Phone (2 / 2a):** `X: 0 dp`, `Y: 14 dp`, `Width: 110 dp`, `Height: 36 dp`, `Radius: 20 dp`, `Cutout: 28 dp`
* **Nothing Phone (1):** `X: -75 dp`, `Y: 14 dp`, `Width: 110 dp`, `Height: 36 dp`, `Radius: 20 dp`, `Cutout: 28 dp`

---

## [ // DATA PRIVACY & ZERO-NETWORK SECURITY AUDIT ]

**(NOT) ISLAND** is engineered with a strict zero-trust, privacy-first foundation:

```xml
<!-- ANDROIDMANIFEST.XML PERMISSION AUDIT -->
<!-- STRICTLY ABSENT: android.permission.INTERNET -->
<!-- STRICTLY ABSENT: android.permission.ACCESS_NETWORK_STATE -->
<!-- STRICTLY ABSENT: android.permission.ACCESS_WIFI_STATE -->
```

1. **Kernel-Enforced Zero Network Access:**
   * The app manifest contains **NO internet permission**. The Linux/Android OS kernel physically denies all socket creation, HTTP requests, and network packets from this process UID.
2. **Zero Telemetry / Trackers:**
   * No Google Firebase Analytics, no Crashlytics, no Mixpanel, and no advertising SDKs.
3. **100% On-Device Ephemeral Processing:**
   * Media metadata (song titles, artist names) and notification previews are read via Android's local `NotificationListenerService` and `MediaSessionManager`, rendered directly to hardware-accelerated memory buffers, and immediately discarded upon collapse. No data is ever written to disk or transmitted off the device.

---

## [ // PRODUCTION BUILD & INSTALLATION ]

### Prerequisites
* Android 8.0 (API level 26) or higher.
* Android Studio Ladybug (2024.2+) or JDK 17 with Gradle 8.9+.

### Build From Source
```bash
# Clone the repository
git clone https://github.com/d4rxh/Nothing-Island.git
cd Nothing-Island

# Build debug or release APK
./gradlew assembleRelease
```

### Direct Download
Grab the latest signed production APK directly from GitHub Releases:
* **Release:** [Nothing Island v1.0.12](https://github.com/d4rxh/Nothing-Island/releases/tag/v1.0.12)
* **Direct APK Download:** **[Nothing-Island-v1.0.12.apk](https://github.com/d4rxh/Nothing-Island/releases/download/v1.0.12/Nothing-Island-v1.0.12.apk)**

---

## [ // TECH STACK ]

* **Core Language:** 100% Kotlin
* **Design Language:** Nothing OS 3.0 Brutalist / NDot57
* **Window Subsystem:** `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` + `TYPE_ACCESSIBILITY_OVERLAY`
* **Layout In Cutout Mode:** `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`
* **Audio Visualizer:** Custom hardware-accelerated `DotMatrixEqualizerView`
* **CI/CD:** Automated GitHub Actions with multi-scheme Keystore V1/V2/V3 signing

---

## [ // LICENSE ]
Distributed under the [MIT License](LICENSE). Developed with precision for the Nothing community.
