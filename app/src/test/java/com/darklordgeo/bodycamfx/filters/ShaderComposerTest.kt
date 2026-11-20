package com.darklordgeo.bodycamfx.filters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShaderComposerTest {

    /** Writes every composed shader so CI can validate it with glslangValidator. */
    @Test
    fun composesAllPresetsAndDumpsSources() {
        val deviceDir = File("build/shaders/device").apply { mkdirs() }
        val webDir = File("build/shaders/webgl").apply { mkdirs() }
        for (preset in FilterRegistry.available) {
            for (tier in QualityTier.entries) {
                val c = ShaderComposer.compose(preset, tier, externalTexture = true)
                assertTrue(c.fragmentSource.startsWith("#version 300 es"))
                assertTrue(c.fragmentSource.contains("samplerExternalOES"))
                File(deviceDir, "${preset.id}_${tier.name.lowercase()}.frag").writeText(c.fragmentSource)
                val w = ShaderComposer.compose(preset, tier, externalTexture = false)
                assertFalse(w.fragmentSource.contains("samplerExternalOES"))
                File(webDir, "${preset.id}_${tier.name.lowercase()}.frag").writeText(w.fragmentSource)
            }
        }
        File(deviceDir, "fullscreen.vert").writeText(ShaderComposer.VERTEX_SHADER)
    }

    @Test
    fun lowTierDropsExpensiveModules() {
        val low = ShaderComposer.compose(FilterRegistry.default, QualityTier.LOW)
        val high = ShaderComposer.compose(FilterRegistry.default, QualityTier.HIGH)
        assertFalse("chromatic" in low.modules)
        assertFalse("sharpen" in low.modules)
        assertTrue("chromatic" in high.modules)
        assertTrue(high.usesHistory)
        assertFalse(low.usesHistory)
    }

    @Test
    fun intensityInterpolatesFromNeutral() {
        val p = FilterRegistry.default.param("uDistortion")!!
        assertEquals(p.neutral, p.effective(p.default, 0f), 1e-6f)
        assertEquals(p.default, p.effective(p.default, 1f), 1e-6f)
        val fov = FilterRegistry.default.param("uFov")!!
        assertEquals(1f, fov.effective(fov.default, 0f), 1e-6f)
    }

    @Test
    fun registryContainsBodycamAndPlannedPresets() {
        val ids = FilterRegistry.all.map { it.id }
        assertEquals("bodycam", FilterRegistry.default.id)
        listOf("vhs", "night_vision", "thermal", "cctv", "analog_film", "camcorder", "cinematic").forEach {
            assertTrue("missing $it", it in ids)
        }
        assertTrue(FilterRegistry.available.all { it.modules.isNotEmpty() })
    }

    @Test
    fun newPresetsRegisterWithoutTouchingOtherCode() {
        val custom = FilterPreset(
            id = "test_custom",
            name = "TEST",
            description = "",
            thumbnail = PresetThumbnail(0, 0, "T"),
            modules = listOf(Effects.exposure, Effects.vignette),
            defaults = mapOf("uVignette" to 0.9f),
        )
        FilterRegistry.register(custom)
        val c = ShaderComposer.compose(FilterRegistry["test_custom"]!!, QualityTier.HIGH)
        assertEquals(listOf("uExposure", "uExposureDrift", "uVignette"), c.paramKeys.take(3))
        assertTrue("global outline appended", "uOutline" in c.paramKeys)
        assertTrue(c.fragmentSource.contains("fx_personOutline"))
        assertEquals(0.9f, custom.param("uVignette")!!.default, 1e-6f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownDefaultIsRejected() {
        FilterPreset(
            id = "bad", name = "", description = "", thumbnail = PresetThumbnail(0, 0, ""),
            modules = listOf(Effects.vignette), defaults = mapOf("uNope" to 1f),
        )
    }
}
