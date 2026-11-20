package com.darklordgeo.bodycamfx.filters

/**
 * Fuses a preset's effect modules into one fragment shader so the whole look costs a single
 * full-screen pass, regardless of how many effects a preset uses.
 */
object ShaderComposer {

    data class Composed(
        val fragmentSource: String,
        /** Param keys present in the shader (modules dropped for the tier are excluded). */
        val paramKeys: List<String>,
        val usesHistory: Boolean,
        val modules: List<String>,
    )

    /**
     * @param externalTexture true on device (camera frames arrive as samplerExternalOES);
     *        false produces a plain sampler2D variant used by tests and the WebGL preview harness.
     */
    fun compose(
        preset: FilterPreset,
        tier: QualityTier,
        externalTexture: Boolean = true,
        globals: List<EffectModule> = Effects.globals,
    ): Composed {
        val active = (preset.modules + globals.filter { g -> preset.modules.none { it.id == g.id } })
            .filter { it.minTier.ordinal <= tier.ordinal }
        val params = active.flatMap { it.params }
        val dupes = params.groupBy { it.key }.filter { it.value.size > 1 }.keys
        require(dupes.isEmpty()) { "Preset '${preset.id}' has duplicate param keys: $dupes" }

        val sb = StringBuilder()
        sb.appendLine("#version 300 es")
        if (externalTexture) sb.appendLine("#extension GL_OES_EGL_image_external_essl3 : require")
        sb.appendLine("precision highp float;")
        sb.appendLine("precision highp int;")
        sb.appendLine(if (externalTexture) "uniform samplerExternalOES uInput;" else "uniform sampler2D uInput;")
        sb.appendLine(PRELUDE)
        params.forEach { sb.appendLine("uniform float ${it.key};") }
        active.forEach { m ->
            sb.appendLine("// ---- module: ${m.id}")
            sb.appendLine(m.glsl)
        }

        sb.appendLine("void main() {")
        sb.appendLine("    vec2 uv = vUv;")
        active.filter { it.stage == Stage.UV }.forEach { sb.appendLine("    ${it.call}") }
        // Anything the lens pulls in from outside the sensor image fades to black (fisheye corners).
        sb.appendLine("    vec2 oob = max(max(-uv, uv - 1.0), 0.0);")
        sb.appendLine("    float frameMask = 1.0 - smoothstep(0.0, 0.04, max(oob.x, oob.y));")
        sb.appendLine("    uv = clamp(uv, 0.0, 1.0);")
        val sampler = active.lastOrNull { it.stage == Stage.SAMPLE }
        sb.appendLine("    vec3 color = sampleRaw(uv);")
        if (sampler != null) sb.appendLine("    ${sampler.call}")
        sb.appendLine("    color *= frameMask;")
        active.filter { it.stage == Stage.COLOR }.forEach { sb.appendLine("    ${it.call}") }
        active.filter { it.stage == Stage.TEMPORAL }.forEach { sb.appendLine("    ${it.call}") }
        active.filter { it.stage == Stage.OVERLAY }.forEach { sb.appendLine("    ${it.call}") }
        sb.appendLine("    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);")
        sb.appendLine("}")

        return Composed(
            fragmentSource = sb.toString(),
            paramKeys = params.map { it.key },
            usesHistory = active.any { it.temporal },
            modules = active.map { it.id },
        )
    }

    /** Full-screen triangle without vertex buffers (gl_VertexID). */
    const val VERTEX_SHADER = """#version 300 es
out vec2 vUv;
void main() {
    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    vUv = p;
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
"""

    private const val PRELUDE = """
uniform mat4 uStMatrix;
uniform sampler2D uHistory;
uniform vec2 uResolution;
uniform float uTime;
uniform float uFrame;
uniform sampler2D uPersonMask;
uniform float uMaskRotation;
uniform float uMaskValid;
in vec2 vUv;
out vec4 fragColor;

vec3 sampleRaw(vec2 p) {
    return texture(uInput, (uStMatrix * vec4(clamp(p, 0.0, 1.0), 0.0, 1.0)).xy).rgb;
}
float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
uint pcgHash(uint v) {
    uint s = v * 747796405u + 2891336453u;
    uint w = ((s >> ((s >> 28u) + 4u)) ^ s) * 277803737u;
    return (w >> 22u) ^ w;
}
float hash13(vec3 p) {
    uvec3 q = uvec3(ivec3(floor(p)) + 32768);
    return float(pcgHash(q.x + pcgHash(q.y + pcgHash(q.z)))) * (1.0 / 4294967295.0);
}
float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash13(vec3(i, 0.0));
    float b = hash13(vec3(i + vec2(1.0, 0.0), 0.0));
    float c = hash13(vec3(i + vec2(0.0, 1.0), 0.0));
    float d = hash13(vec3(i + vec2(1.0, 1.0), 0.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
vec3 hue2rgb(float h) {
    return clamp(abs(fract(h + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
}
"""
}
