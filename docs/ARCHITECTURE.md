# Architecture

```
CameraX (Preview + VideoCapture, one CameraEffect)
   │  camera frames → SurfaceTexture (OES)
   ▼
GlFilterProcessor  (SurfaceProcessor, own GL thread)
   │  Pass 1 — once per frame: composed preset shader → RGBA target (ping-pong) → HUD
   │  Pass 2 — per output: cheap blit with that output's CameraX transform
   ├─► Preview surface  (PreviewView, TextureView mode)
   ├─► Video encoder surface  (Recorder → MediaStore, + audio)
   └─► Photo: same pass 1 at full stream resolution → glReadPixels → JPEG → MediaStore
```

## Packages

| Package | Responsibility |
|---|---|
| `filters` | Effect modules (GLSL + params), presets, registry, shader composer, filter state. Pure Kotlin except the prefs-backed state store. |
| `render` | EGL, GL utilities, `FrameRenderer` (two passes), `GlFilterProcessor` (CameraX bridge, photo/thumbnail readback, adaptive quality), `HudOverlay`, `DeviceCapabilities`. |
| `camera` | `CameraController`: binding, switching, zoom/focus/exposure/torch, recording, photo save flow, UI state. |
| `media` | `MediaStorage` (MediaStore writes), `MediaRepository` (query, observe, thumbnails, ownership-aware delete). |
| `settings` | `AppSettings` + `SettingsRepository`. |
| `ui` | Theme/tokens, glass components, camera / gallery / viewer / settings screens, `AppRoot` navigation. |

## Key decisions

### Rendering: OpenGL ES 3.0 through CameraX `CameraEffect` + `SurfaceProcessor`
- **CameraX effects API.** One `CameraEffect` targeting `PREVIEW | VIDEO_CAPTURE` makes CameraX share one camera stream between both use cases and route it through our processor. The encoder receives the frames we render, so **video is filtered by construction**, not just the preview. CameraX supplies per-output transform matrices (rotation, crop, mirroring), which pass 2 applies, and the encoder timestamps come from the camera frames (`eglPresentationTimeANDROID`), keeping A/V sync with CameraX's audio.
- **OpenGL ES 3.0, not Vulkan.** Camera frames arrive as `SurfaceTexture`/OES textures and the encoder takes a `Surface`. Both are native to EGL/GLES. Vulkan would need AHardwareBuffer import plumbing for the same single-pass shader, with no visual benefit. ES 3.0 is on effectively every device that runs Android 10+.
- **Media3 effects** (`media3-effect`) are designed for transforming existing media and offline export, not a live CameraX pipeline. They would add a second pipeline for the same shader. Not used.
- **Photos.** Binding `ImageCapture` behind the same effect forces stream sharing across three use cases, which varies by device. Instead the processor renders the next camera frame through the same pass-1 shader at full stream resolution (HIGH tier) and reads it back. This guarantees the photo matches the preview pixel-for-pixel in look, at the cost of photo resolution = stream resolution (up to 4K/UHD, capped at the GPU's max texture size).

### One fused shader per preset
`ShaderComposer` concatenates each module's GLSL into **one fragment shader** with fixed stages (UV → SAMPLE → COLOR → TEMPORAL). The whole BODYCAM look is a single full-screen pass (plus a trivial blit per output) instead of about ten passes. Modules declare a minimum quality tier, and the LOW tier drops chromatic aberration (3 taps), sharpening (4 taps) and motion blur.

### Content-space HUD
The HUD is drawn into the processed frame in the camera buffer's own orientation, rotated by the same rotation the outputs apply. Every output (preview, encoder, photo) therefore shows it upright, whether CameraX bakes rotation into the buffer or writes MP4 rotation metadata.

### Adaptive quality
`DeviceCapabilities` profiles RAM and GPU (GL renderer string, max texture size) into LOW / BALANCED / HIGH, which caps the processing resolution (1280 / 1920 / 3840 long side) and selects modules. In AUTO mode the processor tracks per-frame GL cost and steps down a tier after about 3 s over a 30 fps budget.

### Lifecycle
The camera binds only while the Camera tab is visible and unbinds on leave or backgrounding. `onStop` finalises an in-progress recording, so CameraX writes a valid MP4. All GL objects live on the processor's thread and are released when CameraX closes surfaces or the activity is destroyed.

### Glass UI
Every glass element goes through one primitive, `GlassSurface`. Real backdrop blur comes from [Haze](https://github.com/chrisbanes/haze) on Android 12+ when "Background blur" is on; otherwise it falls back to a translucent tinted fill. The fill is always dark enough (50–85 % alpha) for white text over sunlit footage. All colours, radii, spacing and blur values are tokens in `ui/theme/Theme.kt`.

## Research and skills

- The spec asked to install the official `android skills add camerax` / `adaptive` skills. The Android CLI isn't available in the build environment, so **no Android skills were installed**. None of the agent skills available here cover Android or Compose. Work was based on CameraX/Compose API knowledge and is verified by compiling in CI, not by those skills.
- `developer.android.com` blocks automated fetching, so CameraX release notes couldn't be read directly. CameraX **1.4.2** (stable, with the `CameraEffect`/`SurfaceProcessor` APIs used here) was chosen as a known-stable version rather than guessing a newer number. Haze's changelog was checked to pick 1.2.2 (first release with the `hazeSource`/`hazeEffect` API).
