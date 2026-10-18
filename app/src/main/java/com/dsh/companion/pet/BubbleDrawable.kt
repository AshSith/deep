package com.dsh.companion.pet

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** Rounded bubble with a small pointer at the bottom. */
class BubbleDrawable(
    private val fillColor: Int,
    private val strokeColor: Int,
    private val density: Float,
) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val path = Path()
    private val rect = RectF()

    override fun draw(canvas: Canvas) {
        val tail = 9f * density
        val inset = density
        rect.set(
            bounds.left + inset,
            bounds.top + inset,
            bounds.right - inset,
            bounds.bottom - tail,
        )
        val radius = 16f * density

        fill.color = fillColor
        canvas.drawRoundRect(rect, radius, radius, fill)

        path.reset()
        val cx = bounds.centerX().toFloat()
        path.moveTo(cx - 7f * density, rect.bottom - 1f)
        path.lineTo(cx, bounds.bottom - 1f)
        path.lineTo(cx + 7f * density, rect.bottom - 1f)
        path.close()
        canvas.drawPath(path, fill)

        stroke.color = strokeColor
        canvas.drawRoundRect(rect, radius, radius, stroke)
    }

    override fun setAlpha(alpha: Int) {
        fill.alpha = alpha
        stroke.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
        stroke.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
