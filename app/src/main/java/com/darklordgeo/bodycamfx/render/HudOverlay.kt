package com.darklordgeo.bodycamfx.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES30
import android.opengl.GLUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Draws the optional HUD into the processed frame. It is composited in *content space* (the
 * camera buffer's own orientation), rotated so it reads upright in the final photo / video. Because
 * it is part of the frame, preview, video and photo all carry an identical overlay.
 */
class HudOverlay {
    private val program = GlUtil.linkProgram(VERTEX, FRAGMENT)
    private val locPos = GLES30.glGetUniformLocation(program, "uPos")
    private val locTex = GLES30.glGetUniformLocation(program, "uTex")
    private val texture: Int
    private var bmpW = 1
    private var bmpH = 1
    private var lastText = ""

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = TEXT_PX
        color = Color.WHITE
        setShadowLayer(3f, 0f, 1.5f, Color.argb(200, 0, 0, 0))
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(230, 40, 30) }
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd  HH:mm:ss", Locale.US)

    init {
        val t = IntArray(1)
        GLES30.glGenTextures(1, t, 0)
        texture = t[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }

    private fun composeText(state: RenderState, nowMs: Long): Pair<String, Boolean> {
        val hud = state.hud
        val parts = mutableListOf<String>()
        val rec = hud.showRecIndicator && state.recording
        if (rec) {
            val s = ((nowMs - state.recordingStartedAtMs) / 1000).coerceAtLeast(0)
            parts += "REC %02d:%02d".format(Locale.US, s / 60, s % 60)
        }
        if (hud.showTimestamp) parts += dateFmt.format(Date(nowMs))
        if (hud.label.isNotBlank()) parts += hud.label.trim().take(24).uppercase(Locale.US)
        return parts.joinToString("   ") to rec
    }

    private fun updateBitmap(text: String, rec: Boolean) {
        val dotSpace = if (rec) TEXT_PX * 0.9f else 0f
        val w = (paint.measureText(text) + dotSpace + PAD * 2).toInt().coerceAtLeast(8)
        val h = (TEXT_PX * 1.5f).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (rec) c.drawCircle(PAD + TEXT_PX * 0.32f, h / 2f, TEXT_PX * 0.28f, dotPaint)
        c.drawText(text, PAD + dotSpace, h / 2f + TEXT_PX * 0.36f, paint)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        bmp.recycle()
        bmpW = w
        bmpH = h
    }

    /**
     * Draws into the currently bound framebuffer of size [fbW]x[fbH] (content space).
     * [rotation] = clockwise degrees that will be applied to show the content upright.
     */
    fun draw(state: RenderState, fbW: Int, fbH: Int, rotation: Int, nowMs: Long, mirror: Boolean = false) {
        if (!state.hud.enabled) return
        val (text, rec) = composeText(state, nowMs)
        if (text.isEmpty()) return
        val key = "$rec|$text"
        if (key != lastText) {
            updateBitmap(text, rec)
            lastText = key
        }

        val rot = ((rotation % 360) + 360) % 360
        val swap = rot == 90 || rot == 270
        val finalW = (if (swap) fbH else fbW).toFloat()
        val finalH = (if (swap) fbW else fbH).toFloat()
        val short = minOf(finalW, finalH)
        var hPx = short * 0.042f
        var wPx = hPx * bmpW / bmpH
        val maxW = finalW * 0.9f
        if (wPx > maxW) { hPx *= maxW / wPx; wPx = maxW }
        val m = short * 0.04f
        // Final-view corners (normalised, y down): TL, TR, BL, BR
        val fx = floatArrayOf(m / finalW, (m + wPx) / finalW, m / finalW, (m + wPx) / finalW)
        // If the final image is mirrored horizontally afterwards, pre-mirror so the text reads correctly.
        if (mirror) for (i in 0 until 4) fx[i] = 1f - fx[i]
        val fy = floatArrayOf(m / finalH, m / finalH, (m + hPx) / finalH, (m + hPx) / finalH)
        val pos = FloatArray(8)
        for (i in 0 until 4) {
            // final (rotated cw by rot) -> buffer, both normalised with y down
            val (bx, by) = when (rot) {
                90 -> fy[i] to 1f - fx[i]
                180 -> 1f - fx[i] to 1f - fy[i]
                270 -> 1f - fy[i] to fx[i]
                else -> fx[i] to fy[i]
            }
            // buffer (y down) -> NDC (GL framebuffer row 0 is the image bottom)
            pos[i * 2] = bx * 2f - 1f
            pos[i * 2 + 1] = (1f - by) * 2f - 1f
        }

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA) // bitmap is premultiplied
        GLES30.glUseProgram(program)
        GLES30.glUniform2fv(locPos, 4, pos, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
        GLES30.glUniform1i(locTex, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    fun release() {
        GLES30.glDeleteProgram(program)
        GlUtil.deleteTexture(texture)
    }

    private companion object {
        const val TEXT_PX = 42f
        const val PAD = 10f
        const val VERTEX = """#version 300 es
uniform vec2 uPos[4];
out vec2 vTex;
void main() {
    int i = gl_VertexID;
    vTex = vec2(float(i & 1), float(i >> 1));
    gl_Position = vec4(uPos[i], 0.0, 1.0);
}
"""
        const val FRAGMENT = """#version 300 es
precision mediump float;
uniform sampler2D uTex;
in vec2 vTex;
out vec4 o;
void main() { o = texture(uTex, vTex); }
"""
    }
}
