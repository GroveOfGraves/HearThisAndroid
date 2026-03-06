# Changelog

This file documents notable library migrations in HearThisAndroid.

---

## Library Migrations

### 1. AndroidX Migration — `com.android.support:appcompat-v7` → `androidx.appcompat:appcompat`

**Where:** `app/build.gradle`  
**Commit:** `2133000` — *"Upgrade to Android 13 (SDK 33)"* (November 2022)

| Removed | Added |
|---|---|
| `com.android.support:appcompat-v7:27.1.1` | `androidx.appcompat:appcompat:1.0.0` |

**Why:** Targeting Android 13 (SDK 33) required migrating from the old Android Support Library to AndroidX, which is the modern, maintained replacement. Google deprecated the original Support Library and all new development happens in AndroidX. The migration was performed automatically by Android Studio's migration tooling.

---

### 2. ZXing → Google Mobile Services (GMS) Vision

**Where:** `app/build.gradle`, `app/src/main/java/org/sil/hearthis/SyncActivity.java`  
**Commit:** `a571d6e` — *"New approach to scanning barcode"* (December 2022)

| Removed | Added |
|---|---|
| `com.google.zxing:core:3.1.0` | `com.google.android.gms:play-services-vision:11.8.0` |
| `com.google.zxing:android-integration:3.1.0` | |

**Why:** The ZXing integration worked by launching a separate barcode-scanner app (e.g. "Barcode Scanner") that had to be independently installed on the device. This approach stopped working on Android 12 and also created a hard dependency on a third-party app being present. GMS Vision provided an entirely self-contained barcode detector with a built-in `CameraSource`, eliminating the external app requirement and restoring compatibility.

---

### 3. GMS Vision → ML Kit Barcode Scanning + CameraX

**Where:** `app/build.gradle`, `app/src/main/java/org/sil/hearthis/SyncActivity.java`  
**Commit:** `eae995a` — *"Feat/vision & camera updates (#9)"* (March 2026)

| Removed | Added |
|---|---|
| `com.google.android.gms:play-services-vision:20.1.3` | `com.google.mlkit:barcode-scanning:17.3.0` |
| | `androidx.camera:camera-core:1.5.3` |
| | `androidx.camera:camera-camera2:1.5.3` |
| | `androidx.camera:camera-lifecycle:1.5.3` |
| | `androidx.camera:camera-view:1.5.3` |

**Why:** Google deprecated GMS Vision (play-services-vision) in favour of two dedicated, actively maintained libraries:
- **ML Kit Barcode Scanning** (`com.google.mlkit:barcode-scanning`) — the modern, standalone successor to the GMS Vision `BarcodeDetector`. It runs entirely on-device without requiring Google Play Services.
- **CameraX** (`androidx.camera:*`) — a Jetpack library that provides a consistent, lifecycle-aware camera API across all Android versions, replacing the legacy `CameraSource` API bundled with GMS Vision.

The migration also raised `minSdk` from 21 to 23, because CameraX and ML Kit require API level 23 as their minimum supported version.
