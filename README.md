# Westboy Camera — Source Package

Native source for both platforms, built from the improved prompt. **This
was written and organized in a sandbox without Xcode or Android Studio,
so it has not been compiled or run on a device.** Treat it as a strong,
architecturally-correct starting point to open, build, and debug in the
real IDEs — not a drop-in finished app.

## Layout

```
ios/WestboyTechCamera/
  App/        AppDelegate, App entry, audio-session setup
  Camera/     AVFoundation session, preview, GPU frame processor, exporter
  Audio/      Background-track playback/monitoring (no mic)
  Filters/    Core Image filter graph (4 looks)
  UI/         SwiftUI screens: splash, camera HUD, record button, dock, settings
  Theme/      Colors, glassmorphism modifier, branding strings

android/app/src/main/java/com/westboytech/camera/
  camera/     CameraX session, MediaCodec/MediaMuxer export (no mic)
  audio/      ExoPlayer background-track playback/monitoring + Visualizer tap
  filters/    OpenGL ES shader-based filter renderer, FilterType enum
  ui/         Compose screens: splash, camera screen, record button, dock, settings
  theme/      Colors, glass panel modifier, branding strings
android/app/src/main/res/raw/
  filter_vertex.glsl, filter_fragment.glsl   the 4 shader looks
```

## What's real and wired up
- **1080p cap, 60fps-preferred capture** on both platforms (`AVCaptureSession` preset + `activeFormat` search on iOS; `Quality.FHD` + `Camera2Interop` FPS range on Android).
- **Zero microphone capture, by construction** — neither `CameraManager` (iOS or Android) ever attaches an audio input. Grep either codebase for `RECORD_AUDIO` or `AVCaptureDeviceInput(.audio` and you'll find nothing, which is the point.
- **Background-track monitoring + muxing on a shared timeline** — `VideoExportWriter` (iOS) / `VideoMuxer` (Android) read the same track file that's playing live and write it into the container against the first video frame's timestamp.
- **Same filter code path for preview and export** — `FilterEngine`/`FilterRenderer` are called from both the live frame callback and the export writer, so what's on screen matches the output file.
- **Gestures, record-button morph/glow, waveform, filter dock, watermark, splash, credits screen** — implemented per the UI spec on both platforms.

## What still needs real-IDE work before this runs
1. **Runtime permission flow** — CAMERA permission request UI isn't wired into `MainActivity`/`CameraScreen` init (Android) or the SwiftUI view lifecycle beyond the `requestAccessAndConfigure` call (iOS). Needs the standard permission-denied UI states too.
2. **Encoder wiring on Android** — `VideoMuxer.inputSurface()` needs to be connected to `FilterRenderer`'s EGL output surface, and `drainVideoEncoder()` needs to run on a dedicated thread pumped by frame availability. This glue (an `EncoderSurfaceRenderer` combining `FilterRenderer` + `VideoMuxer` + an `EGLDisplay`/`EGLSurface` pair) is the single most fiddly piece of the whole app and is exactly where you'll spend real debugging time.
3. **Asset bundling** — sample track files (`neon_drive.m4a` etc.) referenced by `TrackPickerView`/`TrackPickerSheet` aren't included. Bring your own royalty-free/owned assets and confirm licensing before shipping.
4. **App icons, `strings.xml`/`Theme.WestboyCamera` style, Info.plist camera-usage description** — standard project scaffolding not included here.
5. **Save/share flow** — both platforms currently just print the output file path on export completion; hook up `PHPhotoLibrary`/`MediaStore` writes and a share sheet.
6. **Testing on real hardware** — frame-rate/format negotiation, zoom ranges, and torch availability all vary by device; the "prefer 60fps, else 30fps" and format-search logic should be validated against your actual target device list.

## Suggested next step
Open `ios/` in Xcode as a new SwiftUI App project (drop these files in, matching the folder groups), and `android/` in Android Studio as a Gradle project — then tackle item 2 above first, since nothing records to disk until that's connected.

## Building an APK from GitHub (no local Android Studio needed)

The project now includes everything Gradle needs to build standalone —
root `settings.gradle.kts`/`build.gradle.kts`, `gradle.properties`, and
minimal resources (`strings.xml`, `themes.xml`, an adaptive launcher
icon) so the manifest resolves cleanly — plus a GitHub Actions workflow
at `.github/workflows/build-apk.yml` that builds a debug APK on every
push to `main` (or on demand).

**Steps:**
1. Create a new GitHub repo and push this whole folder to it (the
   `android/`, `ios/`, and `.github/` folders should all sit at the repo
   root, as they do in this zip).
2. Go to the repo's **Actions** tab — the "Build Android APK" workflow
   runs automatically on push, or click **Run workflow** to trigger it
   manually.
3. When the run finishes (green check), open it and download the
   **westboy-camera-debug-apk** artifact from the run summary page — that
   zip contains `app-debug.apk`.
4. Sideload that APK onto a device, or install via `adb install app-debug.apk`.

Notes:
- This is a **debug** build (unsigned, debuggable) — fine for testing,
  not for Play Store distribution. A release build needs a signing
  config (keystore) added as GitHub secrets, which isn't set up here.
- The workflow only builds the **Android** app — there's no CI path for
  the iOS project since that requires a macOS runner and Apple signing
  credentials; iOS still needs Xcode locally.
- The build will still hit the unfinished pieces in item 2 above (the
  encoder/EGL wiring) at *runtime*, not compile time — so the APK should
  build and install, but recording-to-file won't work until that glue
  code is added.

