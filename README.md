HearThisAndroid
===============

Simple Scripture recording on Android Devices

Synchronizes (over local WiFi) with HearThis for Windows. Can display, record and play back Scripture.

#Building
Currently builds and works using Android Studio 2.2.1.
I have Android SDK platforms installed for 6.0 (Marshmallow/23) and 4.3 (Jelly Bean/18); possibly only the latter is needed. SDK tools I have installed are Android SDK Platform-Tools 24.0.4, Android SDK Tools 25.2.2, Android Support Library, rev 23.2.1, (Documentation for Android SDK, version 1), (Google USB Driver, rev 11), Intel X86 Emulator Accelerator (HAXM installer), 6.0.4), Android Support Repository 38.0.0, Google Repository 36). Ones in parens are those I think are most likely not needed; there may be others that could be omitted. Launching the full SDK manager shows a lot more options installed; hopefully none are relevant. I have not yet had opportunity to attempt a minimal install on a fresh system.

#Testing
The automated test suite has been significantly expanded and all tests are expected to pass with the current configuration.

**Unit tests** (no device or emulator required) live in `app/src/test/java/org/sil/hearthis/` and use JUnit 4 with Robolectric for any tests that need Android context. To run them in Android Studio, right-click the `org.sil.hearthis` package under that directory and choose *Run tests in 'org.sil.hearthis'*.

- `BookButtonTest.java` — tests BookButton state and progress logic
- `RecordActivityUnitTest.java` — tests the scroll-position calculation in RecordActivity (plain JUnit, no Android context needed)
- `AcceptFileHandlerTest.java` — tests the HTTP file upload handler
- `HearThisPreferencesTest.java` — tests preference read/write persistence
- `LevelMeterViewTest.java` — tests the level update throttle logic in the audio meter
- `RealScriptProviderTest.java` — tests the scripture data parsing logic

**Instrumentation tests** (require an emulator or connected Android device) live in `app/src/androidTest/java/org/sil/hearthis/`. To run them, right-click the package under that directory and choose *Run tests in 'org.sil.hearthis'*.

- `MainActivityTest.java` — tests that the main activity launches and resolves to the correct next screen
- `BookSelectionTest.java` — tests navigation from the book chooser to the chapter chooser
- `ProjectSelectionTest.java` — tests project listing and selection
- `RecordActivityTest.java` — tests loading, navigation, the recording workflow, and state persistence
- `SyncActivityTest.java` — tests the sync screen UI and SyncService integration

Shared test utilities (`TestFileSystem`, `TestScriptProvider`) are in `app/src/sharedTest/java/` and are automatically included in both test source sets.

### Http Server

The application was using a deprecated http server library. We updated the server with a new library called NanoHTTPD. This allows the server to be future-proof.

Along with updating the server library we ensured that the server ran more efficiently. Tasks we looked into and updated are the following:

- Guard Against Double Start and Stop in SyncServer
- Fix Path Traversal Vulnerability in File Handlers
- Remove Static Listener Memory Leaks
- Make Notification Listener List Thread Safe
- Fix UI Thread Violations in SyncActivity
- Improve File Upload Error Handling
- Improve Resource and Stream Management
- Remove Hardcoded Device Name
- Improve Server Lifecycle Management

### Internationalization

Updated application to support various languages. The application currently supports the following:

- English
- Spanish
- French
- German
- Chinese (simplified)

### Edge-to-edge

Making the application compliant with Android visual constraints, including dynamically changing the status bar to fit within the screen layout.

### Warnings

Walked through all the files cleaning up the warnings, mainly being newer code standards, lambda functions instead of function definitions, and updating deprecated libraries and functions.

### Camera and Scanning

The application was using a deprecated `play-services-vision` (GMS Vision) library for QR code scanning. This was replaced with two modern Jetpack and ML Kit libraries.

- **ML Kit Barcode Scanning** (`com.google.mlkit:barcode-scanning`): An on-device barcode scanning library that does not require Google Play Services to be installed on the device. It replaced `play-services-vision`, which Google has deprecated in favour of the standalone ML Kit suite.
- **CameraX** (`androidx.camera:camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`): A Jetpack camera library that correctly manages the camera lifecycle and simplifies camera integration. It replaced the legacy `Camera` and `CameraSource` APIs that were coupled to the old GMS Vision workflow.

These changes required raising the minimum SDK from 21 to 23, as both CameraX and ML Kit require at least API 23.

### Build System

The build system was significantly updated to target API 36. A series of incremental changes were made across several commits.

- Upgraded Android Gradle Plugin from 7.3.1 to 9.1.0
- Upgraded Gradle wrapper from 9.2.1 to 9.3.1
- Updated `compileSdk` and `targetSdk` from 33 to 36
- Updated `minSdk` from 18 to 21 in the initial Android 15 update, then further to 23 when the camera and scanning libraries were added (see Camera and Scanning section above)
- Replaced the deprecated `jcenter()` Maven repository with `mavenCentral()`
- Removed Jetifier, which is no longer needed now that all dependencies use AndroidX-native artifacts
- Added Java 17 source and target compatibility via `compileOptions`
- Added an explicit `namespace` declaration to `app/build.gradle`, which is required by newer AGP versions
- Removed `useLibrary 'org.apache.http.legacy'`, which was only needed by the old Apache HTTP server and became unnecessary after switching to NanoHTTPD
- Cleaned up stale and deprecated entries in `gradle.properties`

### Testing

The test suite was significantly expanded and modernised alongside the library and API upgrades.

New unit tests that run locally without a device or emulator (using Robolectric):

- `AcceptFileHandlerTest.java` — tests the HTTP file upload handler, including path traversal protection
- `HearThisPreferencesTest.java` — tests that application preferences are correctly persisted and retrieved
- `LevelMeterViewTest.java` — tests the level update throttle logic in the audio level meter view

New instrumentation tests that run on a device or emulator:

- `BookSelectionTest.java` — tests the navigation flow from the book chooser to the chapter chooser
- `ProjectSelectionTest.java` — tests project listing and selection in `ChooseProjectActivity`
- `RecordActivityTest.java` — covers loading, navigation, the recording workflow, and state persistence in `RecordActivity`
- `SyncActivityTest.java` — tests initial UI state, CameraX initialisation, and `SyncService` integration in `SyncActivity`

A shared source set at `src/sharedTest/java` was introduced. `TestFileSystem` and `TestScriptProvider` were previously duplicated between the unit and instrumentation test directories; they now live in one place and are included in both via `sourceSets` configuration in `app/build.gradle`.

Test library changes:

- Replaced Mockito with Robolectric 4.14.1 and Java dynamic proxies for cleaner, warning-free unit testing
- Updated all `androidx.test` libraries to versions compatible with API 36 (`espresso-core` 3.7.0, `junit-ext` 1.3.0, `uiautomator` 2.3.0)
- Added `espresso-intents` for testing intent-based navigation flows between activities

### Audio suggestion

The audio functionality could be updated. On one specific device the chapter view would lag and then have audio errors. We believe that the efficiency of the audio functionality should be improved, though it does work. If this is needed for Google Play we are not sure, but it is the next big thing to update.

# License

HearThisAndroid is open source, using the [MIT License](http://sil.mit-license.org). It is Copyright SIL International.
