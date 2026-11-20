# Testing status

Be clear about what was and wasn't verified. **No physical Android device or emulator was available while building this.** Nothing here has been run on a phone yet.

## Verified automatically (every CI build)

| Check | How | Result |
|---|---|---|
| App compiles (Kotlin, Compose, CameraX 1.4.2, Haze) | `assembleRelease` on GitHub Actions | ✅ |
| Filter engine: composition, tier filtering, intensity math, registry, preset validation | JVM unit tests (`ShaderComposerTest`) | ✅ |
| Every composed shader (all presets × all tiers) is valid GLSL ES 3.00 with `GL_OES_EGL_image_external_essl3` | `glslangValidator` in CI | ✅ |
| BODYCAM look on real photos, normal and about 2.2 stops underexposed | Composed shader run in WebGL2 (headless Chromium) on reference images | ✅ visually reviewed, led to the low-light retune |
| UI layout: camera chrome over bright/dark footage, recording state, adjustment sheet, empty gallery, settings, delete dialog | Robolectric + Roborazzi screenshots attached to each release | see release assets |

## Needs a real device (not yet done)

Use this checklist after installing the APK:

- [ ] Permission flow: deny camera → prompt card; grant → preview appears. Deny microphone → video records silently.
- [ ] Live preview runs smoothly with BODYCAM; Settings → About shows fps.
- [ ] Photo (rear + front): saved upright in the gallery app; selfies mirrored only when "Mirror selfies" is on.
- [ ] Video: filter visible in the recorded file; audio in sync; portrait and landscape recordings play upright.
- [ ] HUD on: identical overlay in preview, video and photo, upright in both orientations.
- [ ] Switch cameras repeatedly; background/foreground the app during preview and during recording (the recording should finalise and save).
- [ ] Ultra-wide: BODYCAM jumps to 0.5–0.6× on phones with an ultra-wide lens.
- [ ] Gallery: new captures appear without a refresh; delete photo/video with confirmation; viewer zoom/swipe/playback/share.
- [ ] Media visible in Google Photos under BODYCAM folders.
- [ ] 4K on a mid-range device: Auto quality should step down if frames are slow.

## Known limitations

- **Photo resolution = video stream resolution** (up to 4K, capped by GPU texture size). It does not use the sensor's full still resolution, because photos come from the same GPU frame as the preview to guarantee an identical look.
- **Front camera HUD with mirroring off**: the preview is always mirrored, so HUD text reads backwards in the viewfinder (saved media is correct).
- **The app UI is portrait-locked.** Landscape capture is supported (media and HUD orientation follow the physical orientation, and control icons rotate), but screens don't re-layout in landscape.
- **Live-preview glass blur** captures the TextureView preview through Haze on Android 12+. If a device doesn't render it, panels show the tinted fallback. It can be disabled in Settings to save battery.
- **Gallery shows media created by this install.** Without broad media permissions, Android 10+ hides files from a previous install. Deleting such files goes through the system consent dialog.
- **Video rotation** relies on CameraX handling the `VideoCapture` target rotation through the effect pipeline. This needs confirming on device (checklist above).
- VHS, Thermal, Analog Film, Camcorder, Cinematic and Retro Digital are registered as "soon" placeholders. CCTV and Night Vision are first-pass looks.
- APKs are signed with a committed **development** key. Replace it before any store release.
