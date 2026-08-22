package com.cem.pusula

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Kadranı çizen basit özel View. Kuzey ekranda sabit kalsın diye kadranın
 * tamamı azimut kadar ters yönde döndürülür.
 */
class CompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var azimuth = 0f

    private val dialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#33FFFFFF")
    }
    private val majorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFFFFF")
        style = Paint.Style.STROKE
    }
    private val minorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55FFFFFF")
        style = Paint.Style.STROKE
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F0F3F6")
        textAlign = Paint.Align.CENTER
    }
    private val northPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E5484D")
        style = Paint.Style.FILL
    }
    private val southPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F0F3F6")
        style = Paint.Style.FILL
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E5484D")
        style = Paint.Style.FILL
    }

    private val needle = Path()

    fun setAzimuth(degrees: Float) {
        azimuth = degrees
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - dp(12f)
        if (radius <= 0f) return

        dialPaint.strokeWidth = dp(1.5f)
        majorTickPaint.strokeWidth = dp(2f)
        minorTickPaint.strokeWidth = dp(1f)
        labelPaint.textSize = radius * 0.16f

        // Ekranın tepesindeki sabit gösterge (telefonun baktığı yön)
        canvas.drawPath(triangle(cx, cy - radius - dp(2f), dp(9f)), markerPaint)

        canvas.save()
        canvas.rotate(-azimuth, cx, cy)

        canvas.drawCircle(cx, cy, radius, dialPaint)
        canvas.drawCircle(cx, cy, radius * 0.72f, dialPaint)

        for (angle in 0 until 360 step 5) {
            val major = angle % 45 == 0
            val paint = if (major) majorTickPaint else minorTickPaint
            val tickLength = if (major) radius * 0.10f else radius * 0.05f
            canvas.save()
            canvas.rotate(angle.toFloat(), cx, cy)
            canvas.drawLine(cx, cy - radius, cx, cy - radius + tickLength, paint)
            canvas.restore()
        }

        val labels = arrayOf("K", "KD", "D", "GD", "G", "GB", "B", "KB")
        labels.forEachIndexed { index, label ->
            canvas.save()
            canvas.rotate(index * 45f, cx, cy)
            labelPaint.color =
                if (index == 0) Color.parseColor("#E5484D") else Color.parseColor("#F0F3F6")
            labelPaint.textSize = if (index % 2 == 0) radius * 0.17f else radius * 0.12f
            canvas.drawText(
                label,
                cx,
                cy - radius + radius * 0.30f - (labelPaint.ascent() + labelPaint.descent()) / 2f,
                labelPaint
            )
            canvas.restore()
        }

        // İbre: kuzey yarısı kırmızı, güney yarısı beyaz
        val needleLength = radius * 0.62f
        val needleWidth = radius * 0.09f
        needle.reset()
        needle.moveTo(cx, cy - needleLength)
        needle.lineTo(cx - needleWidth, cy)
        needle.lineTo(cx + needleWidth, cy)
        needle.close()
        canvas.drawPath(needle, northPaint)

        needle.reset()
        needle.moveTo(cx, cy + needleLength)
        needle.lineTo(cx - needleWidth, cy)
        needle.lineTo(cx + needleWidth, cy)
        needle.close()
        canvas.drawPath(needle, southPaint)

        canvas.restore()

        canvas.drawCircle(cx, cy, dp(4f), dialPaint.apply { style = Paint.Style.FILL })
        dialPaint.style = Paint.Style.STROKE
    }

    private fun triangle(cx: Float, tipY: Float, size: Float): Path {
        val path = Path()
        path.moveTo(cx, tipY + size)
        path.lineTo(cx - size * 0.8f, tipY - size * 0.4f)
        path.lineTo(cx + size * 0.8f, tipY - size * 0.4f)
        path.close()
        return path
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
