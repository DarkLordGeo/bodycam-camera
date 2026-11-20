# Bodycam FX

An Android camera app that renders a realistic **BODYCAM** look (wide fisheye lens, small-sensor noise, cool muted grade, auto-exposure breathing) in real time on the GPU. The look is applied identically to the **live preview, recorded video and saved photos**. The filter engine is modular: new looks are configuration, not new camera code.

> The optional recording HUD is a stylistic effect. It shows no agency names, badge or serial numbers, and footage made with it must not be presented as genuine law-enforcement evidence.

## Install

1. Open the [latest release](https://github.com/DarkLordGeo/bodycam-camera/releases/latest) on your phone.
2. Download `BodycamFX-<n>.apk` and open it. Allow "install unknown apps" for your browser if asked.
3. Every build is signed with the same development key, so newer builds install over older ones.

Requires Android 10 (API 29) or newer and OpenGL ES 3.0.

## Features

- **Camera**: live filtered preview, photo, video with audio, front/rear switching (double-tap the preview also flips), flashlight, pinch and preset zoom (including the ultra-wide lens where available), tap to focus/meter with an exposure slider, 720p/1080p/4K, bitrate presets, recording timer.
- **Filters**: BODYCAM (fully tuned), NATURAL, and first-pass CCTV and NIGHT VISION built purely from shared effect modules. VHS, Thermal, Analog Film, Camcorder, Cinematic and Retro Digital are registered as "soon". The carousel thumbnails are real processed renders of the live camera frame. Tap the active filter to open the adjustment sheet (intensity plus every parameter).
- **Gallery**: three-column grid of this app's photos and videos, a full-screen viewer with pinch zoom, swipe and video playback, share, and delete with confirmation (ownership-aware, with the Android consent dialog when needed). It updates live through a MediaStore observer.
- **Storage**: `Pictures/BODYCAM/Photos`, `Pictures/BODYCAM/Selfies`, `Movies/BODYCAM/Recordings` in shared storage, so captures appear in Google Photos immediately.
- **Settings**: resolution, video quality, audio, default filter and intensity, processing quality (Auto/High/Balanced/Low), selfie mirroring, HUD (on/off, timestamp, REC, label), flashlight memory, haptics, glass blur, thumbnail behaviour, storage paths, and device/GPU info.
- **UI**: dark glassmorphism design system (`ui/theme`, `ui/components/Glass.kt`) with real backdrop blur on Android 12+ (Haze) and a tinted fallback.

## Build

The CI workflow (`.github/workflows/build.yml`) does the following on every push to `main`:
1. Runs the filter-engine unit tests, which also dump every composed shader.
2. Validates every shader with `glslangValidator`.
3. Builds the release APK and publishes it as a GitHub Release.
4. Records Compose screenshots with Robolectric + Roborazzi and attaches them to the same release.

To build locally you need Android Studio (or the SDK with platform 35) and JDK 17:

```bash
./gradlew :app:assembleRelease          # APK in app/build/outputs/apk/release/
./gradlew :app:testReleaseUnitTest      # filter engine tests
./gradlew :app:testDebugUnitTest -Pscreenshots=true --tests "com.darklordgeo.bodycamfx.ui.*"  # screenshots -> app/build/screenshots
```

## Docs

- [Architecture and technical decisions](docs/ARCHITECTURE.md)
- [Adding a new filter](docs/ADDING_FILTERS.md)
- [BODYCAM parameters](docs/BODYCAM_PARAMETERS.md)
- [Testing status and known limitations](docs/TESTING.md)
