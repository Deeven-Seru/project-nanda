<div align="center">

<br>

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://img.shields.io/badge/Project_Nanda-Magic_Trackpad-BB86FC?style=for-the-badge&labelColor=0A0A0A">
  <img alt="Project Nanda" src="https://img.shields.io/badge/Project_Nanda-Magic_Trackpad-BB86FC?style=for-the-badge&labelColor=0A0A0A">
</picture>

<br><br>

**Turn your Android phone into a precision wireless trackpad and keyboard for macOS.**

<br>

[![Android](https://img.shields.io/badge/Android-10%2B-34A853?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Bluetooth](https://img.shields.io/badge/Bluetooth-HID-0082FC?style=flat-square&logo=bluetooth&logoColor=white)](https://www.bluetooth.com/specifications/specs/human-interface-device-profile-1-1/)
[![macOS](https://img.shields.io/badge/macOS-Compatible-000000?style=flat-square&logo=apple&logoColor=white)](https://www.apple.com/macos/)
[![Build](https://img.shields.io/github/actions/workflow/status/Deeven-Seru/project-nanda/android.yml?style=flat-square&label=Build&logo=githubactions&logoColor=white)](https://github.com/Deeven-Seru/project-nanda/actions)
[![License](https://img.shields.io/badge/License-MIT-A0A0A0?style=flat-square)](LICENSE)

<br>

---

</div>

## Overview

Project Nanda is a native Android application that registers your phone as a Bluetooth HID (Human Interface Device) composite peripheral. Once paired, your Mac recognizes it as a standard trackpad and keyboard -- no drivers, no server app, no configuration on the host machine.

It works exactly like an Apple Magic Trackpad: pick up your phone, connect over Bluetooth, and start working.

<br>

## Features

| Capability | Details |
|---|---|
| **Cursor Control** | Single-finger relative movement with configurable sensitivity |
| **Tap to Click** | Quick tap registers as a left click |
| **Two-Finger Scroll** | Vertical scrolling with natural direction |
| **Right Click** | Two-finger tap sends a secondary click |
| **Full Keyboard** | Complete US keyboard layout including symbols, uppercase, and special keys |
| **Stealth Mode** | Adjustable screen brightness from near-black to full, with persistent preference |
| **Auto Reconnect** | Remembers the last paired Mac and reconnects automatically on launch |
| **Zero Host Setup** | No app or driver installation required on the Mac |

<br>

## Requirements

| Component | Minimum |
|---|---|
| Android | 10 (API 29) |
| Bluetooth | 4.0+ with HID Device profile support |
| Host | macOS 12 Monterey or later |
| Permissions | `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE` |

> Most Android phones manufactured after 2019 support the Bluetooth HID Device profile. Some manufacturer skins (MIUI, ColorOS) may restrict background Bluetooth services -- see [Troubleshooting](#troubleshooting) for workarounds.

<br>

## Installation

### From GitHub Releases

1. Download the latest `app-debug.apk` from the [Actions](https://github.com/Deeven-Seru/project-nanda/actions) artifacts.
2. Transfer to your Android device.
3. Enable **Install from Unknown Sources** if prompted.
4. Install and open.

### From Source

```bash
git clone https://github.com/Deeven-Seru/project-nanda.git
cd project-nanda
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

<br>

## Usage

### First-Time Setup

1. Open **Project Nanda** on your Android device.
2. Grant Bluetooth permissions when prompted.
3. Ensure Bluetooth is enabled.
4. Tap **Launch Trackpad**.
5. On your Mac, open **System Settings > Bluetooth**.
6. Find **"Project Nanda"** in the device list and click **Connect**.
7. Accept the pairing on both devices.

### Daily Use

After the initial pairing, the app remembers your Mac. Open the app, tap **Launch Trackpad**, and it connects automatically.

### Controls

| Input | Action |
|---|---|
| Single finger drag | Move cursor |
| Single finger tap | Left click |
| Two-finger drag | Scroll |
| Two-finger tap | Right click |
| Keyboard button (bottom-right) | Toggle on-screen keyboard |
| Brightness slider (bottom bar) | Adjust screen brightness |

<br>

## Architecture

```
SetupActivity                    TrackpadActivity
+-----------------------+        +---------------------------+
| Permission checks     |  --->  | Touch event processing    |
| Bluetooth validation  |        | Gesture recognition       |
| Auto-reconnect state  |        | Brightness control        |
+-----------------------+        | Keyboard input capture    |
                                 +---------------------------+
                                            |
                                            v
                                 BluetoothHidService
                                 +---------------------------+
                                 | HID Device registration   |
                                 | SDP record management     |
                                 | Report transmission       |
                                 | Auto-reconnect logic      |
                                 | Device persistence        |
                                 +---------------------------+
                                            |
                                            v
                                    HidDescriptor
                                 +---------------------------+
                                 | Report ID 1: Keyboard     |
                                 |   8-bit modifier          |
                                 |   6-key rollover          |
                                 | Report ID 2: Mouse        |
                                 |   3 buttons               |
                                 |   Relative X/Y (-127~127) |
                                 |   Scroll wheel            |
                                 +---------------------------+
```

### HID Report Format

**Mouse (Report ID 2):** 4 bytes

| Byte | Field | Range |
|---|---|---|
| 0 | Buttons (bit 0=left, bit 1=right, bit 2=middle) | 0-7 |
| 1 | Delta X | -127 to 127 |
| 2 | Delta Y | -127 to 127 |
| 3 | Scroll wheel | -127 to 127 |

**Keyboard (Report ID 1):** 8 bytes

| Byte | Field |
|---|---|
| 0 | Modifier keys (Ctrl, Shift, Alt, GUI) |
| 1 | Reserved |
| 2-7 | Key codes (6-key rollover) |

<br>

## Project Structure

```
project-nanda/
  app/
    src/main/
      java/com/harvey/magictrackpad/
        SetupActivity.kt          # Onboarding, permissions, auto-reconnect UI
        TrackpadActivity.kt       # Touch processing, keyboard capture, brightness
        BluetoothHidService.kt    # HID registration, report transmission, persistence
        HidDescriptor.kt          # Composite HID descriptor (keyboard + mouse)
      res/
        layout/
          activity_setup.xml      # Setup screen layout
          activity_trackpad.xml   # Trackpad surface with controls
        values/
          colors.xml              # Design tokens
          styles.xml              # App theme
          strings.xml             # String resources
      AndroidManifest.xml
    build.gradle
  build.gradle                    # Root build configuration
  settings.gradle                 # Project settings
  gradle.properties               # Build properties
  .github/workflows/android.yml   # CI/CD pipeline
```

<br>

## Troubleshooting

| Issue | Solution |
|---|---|
| Phone not visible in Mac Bluetooth settings | Open Android Settings > Bluetooth and ensure the device is discoverable |
| Connection drops after screen lock | Disable battery optimization for Project Nanda in Android Settings > Apps |
| Cursor movement feels too slow or fast | Sensitivity is currently set at 2.0x; a settings screen is planned |
| Keyboard not sending characters | Tap the keyboard icon in the bottom-right corner to activate |
| App crashes on launch | Ensure Bluetooth is enabled before opening the app |
| MIUI/ColorOS restrictions | Grant "Auto-start" and "Background activity" permissions in device settings |

<br>

## Roadmap

- [ ] Sensitivity and scroll speed settings UI
- [ ] Three-finger gestures (Mission Control, App Expose)
- [ ] Pinch-to-zoom support
- [ ] Multi-Mac device switching
- [ ] Signed release APK with proper app icon
- [ ] Google Play Store listing
- [ ] Haptic feedback on click and scroll
- [ ] Windows and Linux host support

<br>

## Contributing

Contributions are welcome. Please open an issue first to discuss proposed changes.

1. Fork the repository.
2. Create a feature branch.
3. Commit your changes.
4. Open a pull request.

<br>

## License

MIT License. See [LICENSE](LICENSE) for details.

<br>

---

<div align="center">

[![Built with](https://img.shields.io/badge/Built_with-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Platform](https://img.shields.io/badge/Platform-Android-34A853?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![Protocol](https://img.shields.io/badge/Protocol-Bluetooth_HID-0082FC?style=flat-square&logo=bluetooth&logoColor=white)](https://www.bluetooth.com)

**Project Nanda** -- by [Deeven Seru](https://github.com/Deeven-Seru)

</div>
