package com.darklordgeo.bodycamfx.filters

/**
 * The shared library of effect modules. Presets pick from these and override parameter defaults.
 * Every module here is neutral (no visible change) when its parameters sit at `neutral`.
 */
object Effects {

    /** Barrel + fisheye distortion with frame-filling normalisation and a field-of-view crop. */
    val lens = EffectModule(
        id = "lens",
        stage = Stage.UV,
        params = listOf(
            ParamSpec("uDistortion", "Lens distortion", 0f, 1f, 0.2f, 0f, "Lens"),
            ParamSpec("uFisheye", "Fisheye", 0f, 1f, 0.1f, 0f, "Lens"),
            ParamSpec("uFov", "Field of view", 0.75f, 1f, 0.96f, 1f, "Lens"),
        ),
        glsl = """
            vec2 fx_lens(vec2 p) {
                float aspect = uResolution.x / uResolution.y;
                vec2 d = p - 0.5;
                d.x *= aspect;
                float r2 = dot(d, d);
                vec2 corner = vec2(0.5 * aspect, 0.5);
                float rm2 = dot(corner, corner);
                // Normalise so the corners always land inside the sensor image: centre bulges,
                // edges compress, and no black borders appear.
                float f  = 1.0 + uDistortion * r2  + uFisheye * r2 * r2;
                float fm = 1.0 + uDistortion * rm2 + uFisheye * rm2 * rm2;
                d *= (f / fm) * uFov;
                d.x /= aspect;
                return d + 0.5;
            }
        """.trimIndent(),
        call = "uv = fx_lens(uv);",
    )

    /** Radial lateral chromatic aberration (red pushed out, blue pulled in toward the edges). */
    val chromatic = EffectModule(
        id = "chromatic",
        stage = Stage.SAMPLE,
        params = listOf(ParamSpec("uChromatic", "Chromatic aberration", 0f, 1f, 0.25f, 0f, "Lens")),
        glsl = """
            vec3 fx_chromatic(vec2 p) {
                vec2 d = p - 0.5;
                float r2 = dot(d, d) * 4.0;
                vec2 off = d * (uChromatic * 0.012 * r2);
                return vec3(sampleRaw(p + off).r, sampleRaw(p).g, sampleRaw(p - off).b);
            }
        """.trimIndent(),
        call = "color = fx_chromatic(uv);",
        minTier = QualityTier.BALANCED,
    )

    /** Small-radius unsharp mask: the crisp, slightly haloed look of in-camera digital sharpening. */
    val sharpen = EffectModule(
        id = "sharpen",
        stage = Stage.COLOR,
        params = listOf(ParamSpec("uSharpen", "Sharpening", 0f, 1f, 0.5f, 0f, "Detail")),
        glsl = """
            vec3 fx_sharpen(vec3 c, vec2 p) {
                vec2 px = 1.0 / uResolution;
                vec3 n = sampleRaw(p + vec2(px.x, 0.0)) + sampleRaw(p - vec2(px.x, 0.0))
                       + sampleRaw(p + vec2(0.0, px.y)) + sampleRaw(p - vec2(0.0, px.y));
                vec3 high = c - n * 0.25;
                return c + high * (uSharpen * 1.6);
            }
        """.trimIndent(),
        call = "color = fx_sharpen(color, uv);",
        minTier = QualityTier.BALANCED,
    )

    /** Exposure in stops plus slow auto-exposure "breathing", like a small sensor hunting for exposure. */
    val exposure = EffectModule(
        id = "exposure",
        stage = Stage.COLOR,
        params = listOf(
            ParamSpec("uExposure", "Exposure", -2f, 2f, 0f, 0f, "Tone"),
            ParamSpec("uExposureDrift", "Exposure fluctuation", 0f, 1f, 0.25f, 0f, "Tone"),
        ),
        glsl = """
            vec3 fx_exposure(vec3 c) {
                float t = uTime;
                float drift = 0.55 * sin(t * 0.37) + 0.30 * sin(t * 1.13 + 1.7) + 0.15 * sin(t * 2.71 + 0.4);
                return c * exp2(uExposure + drift * uExposureDrift * 0.35);
            }
        """.trimIndent(),
        call = "color = fx_exposure(color);",
    )

    /** White balance as temperature (blue/amber) and tint (magenta/green), luminance-preserving. */
    val whiteBalance = EffectModule(
        id = "whiteBalance",
        stage = Stage.COLOR,
        params = listOf(
            ParamSpec("uTemperature", "Temperature", -1f, 1f, 0f, 0f, "Color"),
            ParamSpec("uTint", "Tint", -1f, 1f, 0f, 0f, "Color"),
        ),
        glsl = """
            vec3 fx_whiteBalance(vec3 c) {
                vec3 wb = vec3(1.0 + uTemperature * 0.16, 1.0 + uTint * 0.10, 1.0 - uTemperature * 0.16);
                return c * wb / luma(wb);
            }
        """.trimIndent(),
        call = "color = fx_whiteBalance(color);",
    )

    /** Contrast around mid-grey with a soft highlight shoulder, then saturation. */
    val toneColor = EffectModule(
        id = "toneColor",
        stage = Stage.COLOR,
        params = listOf(
            ParamSpec("uContrast", "Contrast", 0.5f, 1.8f, 1f, 1f, "Tone"),
            ParamSpec("uBlackPoint", "Black crush", 0f, 0.15f, 0f, 0f, "Tone"),
            ParamSpec("uShoulder", "Highlight roll-off", 0f, 1f, 0f, 0f, "Tone"),
            ParamSpec("uSaturation", "Saturation", 0f, 1.6f, 1f, 1f, "Color"),
        ),
        glsl = """
            vec3 fx_toneColor(vec3 c) {
                c = max(c, 0.0);
                c = max(c - uBlackPoint, 0.0) / (1.0 - uBlackPoint);
                // contrast in a perceptual-ish (gamma 1/2.2 already applied by the camera) domain
                c = (c - 0.45) * uContrast + 0.45;
                // highlight shoulder: compress values above 0.75 instead of hard clipping
                vec3 over = max(c - 0.75, 0.0);
                c = c - over + over / (1.0 + over * uShoulder * 4.0);
                float l = luma(c);
                return mix(vec3(l), c, uSaturation);
            }
        """.trimIndent(),
        call = "color = fx_toneColor(max(color, 0.0));",
    )

    /** Split-toning: separate hue for shadows and highlights (a preset's colour grade). */
    val splitTone = EffectModule(
        id = "splitTone",
        stage = Stage.COLOR,
        params = listOf(
            ParamSpec("uShadowHue", "Shadow hue", 0f, 1f, 0.5f, 0.5f, "Grade"),
            ParamSpec("uShadowAmount", "Shadow tint", 0f, 1f, 0f, 0f, "Grade"),
            ParamSpec("uHighlightHue", "Highlight hue", 0f, 1f, 0.1f, 0.1f, "Grade"),
            ParamSpec("uHighlightAmount", "Highlight tint", 0f, 1f, 0f, 0f, "Grade"),
        ),
        glsl = """
            vec3 fx_splitTone(vec3 c) {
                float l = clamp(luma(c), 0.0, 1.0);
                vec3 sh = hue2rgb(uShadowHue) - 0.5;
                vec3 hi = hue2rgb(uHighlightHue) - 0.5;
                c += sh * (uShadowAmount * 0.12) * (1.0 - smoothstep(0.0, 0.55, l));
                c += hi * (uHighlightAmount * 0.10) * smoothstep(0.45, 1.0, l);
                return c;
            }
        """.trimIndent(),
        call = "color = fx_splitTone(color);",
    )

    /** Monochrome with a phosphor/LCD tint (night vision, CCTV). */
    val monochrome = EffectModule(
        id = "monochrome",
        stage = Stage.COLOR,
        params = listOf(
            ParamSpec("uMonoAmount", "Monochrome", 0f, 1f, 1f, 0f, "Color"),
            ParamSpec("uMonoHue", "Tint hue", 0f, 1f, 0.33f, 0.33f, "Color"),
            ParamSpec("uMonoTint", "Tint strength", 0f, 1f, 0.8f, 0f, "Color"),
        ),
        glsl = """
            vec3 fx_monochrome(vec3 c) {
                float l = luma(c);
                vec3 tint = mix(vec3(1.0), hue2rgb(uMonoHue) * 1.6, uMonoTint);
                return mix(c, vec3(l) * tint, uMonoAmount);
            }
        """.trimIndent(),
        call = "color = fx_monochrome(color);",
    )

    /**
     * Small-sensor noise: luminance + blotchy chroma noise concentrated in the shadows, plus
     * low-light behaviour (shadow desaturation and a lifted, noisy black level).
     */
    val sensorNoise = EffectModule(
        id = "sensorNoise",
        stage = Stage.COLOR,
        params = listOf(
            ParamSpec("uSensorNoise", "Sensor noise", 0f, 1f, 0.3f, 0f, "Detail"),
            ParamSpec("uLowLight", "Low-light response", 0f, 1f, 0.4f, 0f, "Detail"),
        ),
        glsl = """
            vec3 fx_sensorNoise(vec3 c) {
                float l = clamp(luma(c), 0.0, 1.0);
                float shadow = 1.0 - smoothstep(0.0, 0.55, l);
                vec2 fc = gl_FragCoord.xy;
                float lumN = hash13(vec3(fc, uFrame)) - 0.5;
                vec2 cc = fc * 0.5 + vec2(uFrame * 17.0, uFrame * 31.0);
                vec3 chromaN = vec3(valueNoise(cc), valueNoise(cc + 41.3), valueNoise(cc + 87.1)) - 0.5;
                float amount = uSensorNoise * (0.025 + 0.11 * shadow * (0.5 + uLowLight));
                c += (vec3(lumN) * 0.65 + chromaN * 0.55) * amount;
                // low light: shadows lose colour and the black level lifts slightly toward blue-grey
                c = mix(c, vec3(luma(c)), uLowLight * shadow * 0.45);
                c += vec3(0.006, 0.008, 0.012) * uLowLight;
                return c;
            }
        """.trimIndent(),
        call = "color = fx_sensorNoise(color);",
    )

    /** Fine digital grain, strongest in the mid-tones, at a configurable grain size. */
    val grain = EffectModule(
        id = "grain",
        stage = Stage.COLOR,
        params = listOf(
            ParamSpec("uGrain", "Grain", 0f, 1f, 0.3f, 0f, "Detail"),
            ParamSpec("uGrainSize", "Grain size", 1f, 3f, 1.4f, 1.4f, "Detail"),
        ),
        glsl = """
            vec3 fx_grain(vec3 c) {
                vec2 p = gl_FragCoord.xy / uGrainSize + vec2(uFrame * 13.17, uFrame * 7.31);
                float g = (valueNoise(p) + valueNoise(p * 1.9 + 3.7)) * 0.5 - 0.5;
                float l = clamp(luma(c), 0.0, 1.0);
                float mid = 1.0 - abs(l * 2.0 - 1.0);
                return c + g * uGrain * 0.11 * (0.45 + 0.55 * mid);
            }
        """.trimIndent(),
        call = "color = fx_grain(color);",
    )

    /** Optical vignette with a soft falloff. */
    val vignette = EffectModule(
        id = "vignette",
        stage = Stage.COLOR,
        params = listOf(ParamSpec("uVignette", "Vignette", 0f, 1f, 0.3f, 0f, "Lens")),
        glsl = """
            vec3 fx_vignette(vec3 c) {
                vec2 d = vUv - 0.5;
                d.x *= uResolution.x / uResolution.y;
                float r = length(d) / length(vec2(0.5 * uResolution.x / uResolution.y, 0.5));
                float v = 1.0 - smoothstep(0.35, 1.05, r) * 0.85;
                return c * mix(1.0, v, uVignette);
            }
        """.trimIndent(),
        call = "color = fx_vignette(color);",
    )

    /** Frame-feedback motion blur: blends with the previous processed frame. */
    val motionBlur = EffectModule(
        id = "motionBlur",
        stage = Stage.TEMPORAL,
        params = listOf(ParamSpec("uMotionBlur", "Motion blur", 0f, 0.8f, 0f, 0f, "Motion")),
        glsl = """
            vec3 fx_motionBlur(vec3 c) {
                vec3 prev = texture(uHistory, vUv).rgb;
                return mix(c, prev, clamp(uMotionBlur, 0.0, 0.8));
            }
        """.trimIndent(),
        call = "color = fx_motionBlur(color);",
        minTier = QualityTier.BALANCED,
    )
}
