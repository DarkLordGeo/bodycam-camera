# BODYCAM parameters

All values are adjustable in the app (tap the active filter). The **intensity** slider scales every parameter between its neutral value (no effect) and the value below.

| Group | Parameter | Uniform | Range | Default | What it simulates |
|---|---|---|---|---|---|
| Lens | Lens distortion | `uDistortion` | 0–1 | 0.27 | Barrel distortion (r²). The frame is normalised so corners stay inside the image: the centre bulges and the edges bend, with no black borders. |
| Lens | Fisheye | `uFisheye` | 0–1 | 0.14 | Extra r⁴ term: the stronger edge curvature of very wide action/body cameras. |
| Lens | Field of view | `uFov` | 0.75–1 | 0.97 | Crop factor. 1 = full sensor. Real extra width comes from the ultra-wide lens, which BODYCAM selects automatically when the phone has one (Settings → "Use ultra-wide lens"). |
| Lens | Chromatic aberration | `uChromatic` | 0–1 | 0.30 | Lateral CA growing with r²: red fringes outward, blue inward, toward the corners. Balanced/High tiers. |
| Lens | Vignette | `uVignette` | 0–1 | 0.35 | Smooth optical falloff toward the corners. |
| Detail | Sharpening | `uSharpen` | 0–1 | 0.55 | 4-tap unsharp mask. Mimics aggressive in-camera sharpening with faint halos. Balanced/High tiers. |
| Detail | Sensor noise | `uSensorNoise` | 0–1 | 0.35 | Per-pixel luminance noise plus blotchy chroma noise, concentrated in the shadows. |
| Detail | Low-light response | `uLowLight` | 0–1 | 0.50 | Sensor gain in the dark: lifts shadows (power curve), increases shadow noise, desaturates shadows and raises a blue-grey black level. |
| Detail | Grain | `uGrain` | 0–1 | 0.30 | Fine, animated digital grain, strongest in the mid-tones. |
| Detail | Grain size | `uGrainSize` | 1–3 | 1.3 | Grain cell size in pixels. |
| Tone | Exposure | `uExposure` | −2–2 EV | +0.05 | Global exposure in stops. |
| Tone | Exposure fluctuation | `uExposureDrift` | 0–1 | 0.30 | Slow auto-exposure "breathing" (three incommensurate sines, at most ±0.35 EV). |
| Tone | Contrast | `uContrast` | 0.5–1.8 | 1.16 | Contrast around mid-grey. |
| Tone | Black crush | `uBlackPoint` | 0–0.15 | 0.015 | Raises the black point (crushed shadows). |
| Tone | Highlight roll-off | `uShoulder` | 0–1 | 0.40 | Compresses highlights above 0.75 instead of hard clipping. |
| Color | Temperature | `uTemperature` | −1–1 | −0.12 | Cool (−) / warm (+) white balance, luminance-preserving. |
| Color | Tint | `uTint` | −1–1 | +0.14 | Green (+) / magenta (−). Fixed-WB small sensors drift green under mixed light. |
| Color | Saturation | `uSaturation` | 0–1.6 | 0.76 | Muted colour typical of body-worn footage. |
| Grade | Shadow hue / tint | `uShadowHue`, `uShadowAmount` | 0–1 | 0.48 / 0.35 | Teal-cyan shadows. |
| Grade | Highlight hue / tint | `uHighlightHue`, `uHighlightAmount` | 0–1 | 0.11 / 0.20 | Slightly warm highlights. |
| Motion | Motion blur | `uMotionBlur` | 0–0.8 | 0 (off) | Frame feedback with the previous processed frame. Optional; adds ghosting on fast motion. Balanced/High tiers. |

Processing order (one shader pass): lens → chromatic aberration → sharpen → exposure → white balance → tone/saturation → split tone → sensor noise and low light → grain → vignette → motion blur → (HUD).

## Tuning notes

- The defaults were checked by running the exact composed shader on reference photos (normal and underexposed by about 2.2 stops) in WebGL2. The underexposed pass led to the sensor-gain lift and stronger shadow noise, because the first version crushed night scenes to black, which reads as a filter rather than a camera.
- They have **not** yet been tuned against footage from a real phone's sensor. Expect to adjust noise and distortion to taste on your device.
