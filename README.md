HearThisAndroid
===============

Simple Scripture recording on Android Devices

Synchronizes (over local WiFi) with HearThis for Windows. Can display, record and play back Scripture.

#Building
Currently builds and works using Android Studio 2.2.1.
I have Android SDK platforms installed for 6.0 (Marshmallow/23) and 4.3 (Jelly Bean/18); possibly only the latter is needed. SDK tools I have installed are Android SDK Platform-Tools 24.0.4, Android SDK Tools 25.2.2, Android Support Library, rev 23.2.1, (Documentation for Android SDK, version 1), (Google USB Driver, rev 11), Intel X86 Emulator Accelerator (HAXM installer), 6.0.4), Android Support Repository 38.0.0, Google Repository 36). Ones in parens are those I think are most likely not needed; there may be others that could be omitted. Launching the full SDK manager shows a lot more options installed; hopefully none are relevant. I have not yet had opportunity to attempt a minimal install on a fresh system.

#Testing
HearThis Android only has minimal automated tests and I have had extreme difficulty getting even that far. I can't get any of them to work with the current version of Android Studio (not that I've spent much effort so far). Both sets ran earlier after many struggles to set up the right build configurations.

1. In app\src\test\java\org\sil\hearthis\BookButtonTest.java are some simple tests designed to run without needing an emulator or real android but directly in JUnit. One way to run these tests is to right-click BookButtonTest in the Project panel on the top left and choose "Run 'BookButtonTest'". 
To run tests in multiple files, I had to edit build configurations (Run/Edit configurations). If you do this right after a right-click on org.sil.hearthis and "Run tests in '...'" the configuration it is trying to use will be selected. I was not able to get anywhere with running tests by 'All in package', but if you choose 'all in directory' and configure the directory to be a path to <HearThisAndroid>\app\src\test\java\org\sil\hearthis, it runs the right tests. Possibly the problem is that I have the test directory in the wrong place.
Unfortunately wherever it saves the build configurations does not seem to be checked in.
2. In app\src\androidTest\java\org\sil\hearthis\MainActivityTest.java are some very minimal tests designed to run on an emulator or real device. I believe these also worked once.

The second group of tests currently all fail; my recent attempts to run the others result in reports that no tests are found to run.
There are also some tests in app\src\test\java\org\sil\hearthis\RecordActivityUnitTest.java. I am not sure these ever worked.

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

# License

HearThisAndroid is open source, using the [MIT License](http://sil.mit-license.org). It is Copyright SIL International.
