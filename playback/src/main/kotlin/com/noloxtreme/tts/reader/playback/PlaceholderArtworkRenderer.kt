package com.noloxtreme.tts.reader.playback

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

/** Renders the deterministic title placeholder to a bitmap for media artwork. */
object PlaceholderArtworkRenderer {
    private const val WIDTH = 300
    private const val HEIGHT = 450
    private const val CORNER_RADIUS = 12f

    fun renderPng(title: String, sha256: String): ByteArray {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TitlePlaceholder.backgroundColorArgb(sha256)
        }
        canvas.drawRoundRect(
            RectF(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat()),
            CORNER_RADIUS,
            CORNER_RADIUS,
            background
        )
        val initials = TitlePlaceholder.initials(title)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            typeface = Typeface.create("serif", Typeface.BOLD)
            textSize = 132f
            textAlign = Paint.Align.CENTER
        }
        val baseline = (HEIGHT / 2f) -
            ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(initials, WIDTH / 2f, baseline, textPaint)
        return bitmap.toPng()
    }

    private fun Bitmap.toPng(): ByteArray {
        val output = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.PNG, 100, output)
        recycle()
        return output.toByteArray()
    }
}
