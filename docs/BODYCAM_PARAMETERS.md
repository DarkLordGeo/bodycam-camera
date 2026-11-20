# BODYCAM parameters

All values are adjustable in the app (tap the active filter). The **intensity** slider scales every parameter between its neutral value (no effect) and the value below.

| Group | Parameter | Uniform | Range | Default | What it simulates |
|---|---|---|---|---|---|
| Lens | Lens distortion | `uDistortion` | 0–1 | 0.18 | Barrel distortion (r²). |
| Lens | Fisheye | `uFisheye` | 0–1 | 0.34 | r⁴ term: the edges bend much more than the centre, like an ultra-wide body camera. |
| Lens | Corner fill | `uLensFill` | 0–1 | 0.45 | 0 = no centre zoom; the lens pulls in area outside the sensor, which becomes rounded dark corners. 1 = zoom until the frame is filled (the centre gets magnified and looks stretched). |
| Lens | Zoom crop | `uFov` | 0.75–1 | 1.0 | Extra digital crop. Real extra width comes from the ultra-wide lens, which BODYCAM selects automatically when available. |
| Lens | Chromatic aberration | `uChromatic` | 0–1 | 0.45 | Lateral CA growing with r²: red fringes outward, blue inward, toward the corners. Balanced/High tiers. |
| Lens | Highlight bloom | `uBloom` | 0–1 | 0.45 | Bright sources glow and bleed into their surroundings (6-tap ring). Balanced/High tiers. |
| Lens | Vignette | `uVignette` | 0–1 | 0.55 | Smooth optical falloff toward the corners. |
| Detail | Compression | `uCompression` | 0–1 | 0.35 | Codec artefacts: chroma smeared across 8×8 blocks plus faint luma blocking. |
| Detail | Sharpening | `uSharpen` | 0–1 | 0.75 | 4-tap unsharp mask. Mimics aggressive in-camera sharpening with faint halos. Balanced/High tiers. |
| Detail | Sensor noise | `uSensorNoise` | 0–1 | 0.48 | Per-pixel luminance noise plus blotchy chroma noise, concentrated in the shadows. |
| Detail | Low-light response | `uLowLight` | 0–1 | 0.60 | Sensor gain in the dark: lifts shadows (power curve), increases shadow noise, desaturates shadows and raises a blue-grey black level. |
| Detail | Grain | `uGrain` | 0–1 | 0.40 | Fine, animated digital grain, strongest in the mid-tones. |
| Detail | Grain size | `uGrainSize` | 1–3 | 1.3 | Grain cell size in pixels. |
| Tone | Exposure | `uExposure` | −2–2 EV | +0.05 | Global exposure in stops. |
| Tone | Exposure fluctuation | `uExposureDrift` | 0–1 | 0.40 | Slow auto-exposure "breathing" (three incommensurate sines, at most ±0.35 EV). |
| Tone | Contrast | `uContrast` | 0.5–1.8 | 1.24 | Contrast around mid-grey. |
| Tone | Black crush | `uBlackPoint` | 0–0.15 | 0.02 | Raises the black point (crushed shadows). |
| Tone | Highlight roll-off | `uShoulder` | 0–1 | 0.25 | Compresses highlights above 0.75 instead of hard clipping. |
| Color | Temperature | `uTemperature` | −1–1 | −0.18 | Cool (−) / warm (+) white balance, luminance-preserving. |
| Color | Tint | `uTint` | −1–1 | +0.18 | Green (+) / magenta (−). Fixed-WB small sensors drift green under mixed light. |
| Color | Saturation | `uSaturation` | 0–1.6 | 0.66 | Muted colour typical of body-worn footage. |
| Grade | Shadow hue / tint | `uShadowHue`, `uShadowAmount` | 0–1 | 0.48 / 0.45 | Teal-cyan shadows. |
| Grade | Highlight hue / tint | `uHighlightHue`, `uHighlightAmount` | 0–1 | 0.11 / 0.15 | Slightly warm highlights. |
| Motion | Motion blur | `uMotionBlur` | 0–0.8 | 0 (off) | Frame feedback with the previous processed frame. Optional; adds ghosting on fast motion. Balanced/High tiers. |

Processing order (one shader pass): lens (+ dark out-of-frame corners) → chromatic aberration → compression → sharpen → bloom → exposure → white balance → tone/saturation → split tone → sensor noise and low light → grain → vignette → motion blur → people outline (global) → (HUD).

## Tuning notes

- The defaults were checked by running the exact composed shader on reference photos (normal and underexposed by about 2.2 stops) in WebGL2. The underexposed pass led to the sensor-gain lift and stronger shadow noise, because the first version crushed night scenes to black, which reads as a filter rather than a camera.
- They have **not** yet been tuned against footage from a real phone's sensor. Expect to adjust noise and distortion to taste on your device.

## Front camera

Front cameras are far narrower than a body camera, so wide-lens presets get an automatic boost on the selfie camera: distortion ×1.6 + 0.06, fisheye ×1.5 + 0.08, corner fill ×0.8, sensor noise ×1.25, grain ×1.2 and sharpening +0.1. The sliders keep showing your base values.

## People outline (global)

On-device ML Kit selfie segmentation runs on a 640×360 analysis stream. The mask is uploaded to the GPU and the shader draws an outer outline (plus a faint glow) where the mask edge is, following the lens distortion. It is burned into preview, video and photos. Colour (red/amber/green/cyan/white) and width live in Settings → People outline. It's a global module, so every preset gets it.
