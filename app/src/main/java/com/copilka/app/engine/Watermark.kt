package com.copilka.app.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

/**
 * Ненавязчивый водяной знак бесплатной версии.
 * Ставится только на изображения и PDF, только в углу, полупрозрачным.
 */
object Watermark {

    private const val ALPHA = 78
    private const val PADDING_RATIO = 0.03f

    /** Рисует подпись в правом нижнем углу битмапа. */
    fun applyTo(bitmap: Bitmap, text: String): Bitmap {
        val target = if (bitmap.isMutable) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(target)
        val textSize = (target.width.coerceAtMost(target.height) / 16f).coerceIn(18f, 90f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = ALPHA
            this.textSize = textSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            setShadowLayer(textSize / 12f, 2f, 2f, Color.argb(ALPHA, 0, 0, 0))
        }
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        val padding = target.width * PADDING_RATIO
        val x = target.width - bounds.width() - padding
        val y = target.height - padding - bounds.height() / 2f
        canvas.drawText(text, x.coerceAtLeast(padding), y, paint)
        return target
    }

    /** Рисует подпись на странице PDF (координаты — в пунктах). */
    fun applyToPdfCanvas(canvas: Canvas, pageWidth: Int, pageHeight: Int, text: String) {
        val textSize = (pageWidth.coerceAtMost(pageHeight) / 22f).coerceIn(10f, 26f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            alpha = ALPHA
            this.textSize = textSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        val padding = pageWidth * 0.03f
        canvas.drawText(text, pageWidth - bounds.width() - padding, pageHeight - padding, paint)
    }
}
