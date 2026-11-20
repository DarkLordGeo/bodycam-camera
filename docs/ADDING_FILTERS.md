# Adding a filter

A filter is a `FilterPreset`: an ordered list of reusable `EffectModule`s plus default values. The camera, renderer, recorder, carousel, adjustment sheet and persistence all read presets from `FilterRegistry`, so **adding a filter touches no camera or UI code**.

## 1. Compose from existing modules (most filters)

```kotlin
// filters/presets/CinematicPreset.kt
object CinematicPreset {
    val preset = FilterPreset(
        id = "cinematic",                       // lower_snake_case, unique
        name = "CINEMATIC",
        description = "Teal-orange grade with soft highlight roll-off.",
        thumbnail = PresetThumbnail(0xFF2E7F86, 0xFF3A1E0E, "CI"),  // shown until a live thumbnail renders
        modules = listOf(
            Effects.exposure, Effects.whiteBalance, Effects.toneColor,
            Effects.splitTone, Effects.grain, Effects.vignette,
        ),
        defaults = mapOf(                        // only override what differs from module defaults
            "uExposureDrift" to 0f,
            "uContrast" to 1.1f, "uShoulder" to 0.7f, "uSaturation" to 0.9f,
            "uShadowHue" to 0.52f, "uShadowAmount" to 0.6f,
            "uHighlightHue" to 0.08f, "uHighlightAmount" to 0.5f,
            "uGrain" to 0.15f, "uVignette" to 0.25f,
        ),
    )
}
```

Then in `FilterRegistry.init`, replace the planned entry (or add one):

```kotlin
register(CinematicPreset.preset)
```

Every parameter automatically appears in the adjustment sheet, is persisted per preset, and is scaled by the intensity slider (from the module's `neutral` value to your value).

`capabilities` controls behaviour: `OVERLAY` allows the HUD, `TEMPORAL` signals history use, and `PREFERS_WIDE_LENS` switches to the widest zoom when selected.

## 2. Write a new effect module (new kinds of processing)

Add it to `Effects.kt`:

```kotlin
val scanlines = EffectModule(
    id = "scanlines",
    stage = Stage.COLOR,
    params = listOf(ParamSpec("uScanlines", "Scanlines", 0f, 1f, 0.4f, 0f, "Analog")),
    glsl = """
        vec3 fx_scanlines(vec3 c) {
            float line = 0.5 + 0.5 * sin(gl_FragCoord.y * 3.14159);
            return c * mix(1.0, 0.75 + 0.25 * line, uScanlines);
        }
    """.trimIndent(),
    call = "color = fx_scanlines(color);",
    minTier = QualityTier.LOW,
)
```

Rules:
- **Stage.** `UV` returns a new `uv` (distortion), `SAMPLE` replaces the texture read (only one per preset), `COLOR` transforms `color` in list order, `TEMPORAL` blends with `uHistory` (the previous frame).
- **Available in GLSL.** `sampleRaw(uv)`, `luma()`, `hash13()`, `valueNoise()`, `hue2rgb()`, `uResolution`, `uTime`, `uFrame`, `vUv` (output uv), `uv` (source uv).
- **Params.** Keys must look like `uName`, be unique within a preset, and are declared as `uniform float` by the composer. Do not declare them yourself.
- **Neutral.** Each param's `neutral` must make the module a no-op, so intensity 0 means "off".
- **Cost.** Taps of `sampleRaw` are the main cost. Set `minTier` to `BALANCED`/`HIGH` for multi-tap effects so low-end devices skip them.

## 3. Verify

- `./gradlew :app:testReleaseUnitTest` composes every preset at every tier and writes the sources to `app/build/shaders/`. CI validates them with `glslangValidator`.
- To preview a look without a phone, open a composed `app/build/shaders/webgl/*.frag` (the sampler2D variant) in any WebGL2 harness with a photo bound to `uInput` and an identity `uStMatrix`.
