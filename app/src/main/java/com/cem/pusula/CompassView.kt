package com.cem.pusula

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.min

/**
 * Kadranı çizen basit özel View. Kuzey ekranda sabit kalsın diye kadranın
 * tamamı azimut kadar ters yönde döndürülür. Kadranla birlikte dönen işaretler
 * (manyetik kuzey, kıble, kilitli hedef) döndürme içinde, ekrana sabit olanlar
 * (üstteki gösterge, su terazisi) dışında çizilir.
 */
class CompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var azimuth = 0f

    /** Kadranın kuzeyine göre manyetik kuzeyin açısal farkı; null ise işaretlenmez. */
    private var magneticOffset: Float? = null

    /** Kilitlenen yön, kadranla aynı çerçevede (gerçek kuzey biliniyorsa ona göre). */
    private var targetBearing: Float? = null

    /** Kâbe yönü; yalnızca konum bilindiğinde dolu olur. */
    private var qiblaBearing: Float? = null

    // Su terazisi için eğim (derece). Düz tutulan telefonda ikisi de 0'dır.
    private var pitch = 0f
    private var roll = 0f

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
    private val magneticPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_MAGNETIC
        style = Paint.Style.STROKE
    }
    private val magneticLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_MAGNETIC
        textAlign = Paint.Align.CENTER
    }
    private val qiblaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_QIBLA
        style = Paint.Style.STROKE
    }
    private val qiblaLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_QIBLA
        textAlign = Paint.Align.CENTER
    }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_TARGET
        style = Paint.Style.FILL
    }
    private val targetLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_TARGET
        style = Paint.Style.STROKE
    }
    private val levelFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF101418")
        style = Paint.Style.FILL
    }
    private val levelFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#66FFFFFF")
        style = Paint.Style.STROKE
    }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val needle = Path()

    fun setAzimuth(degrees: Float) {
        azimuth = degrees
        invalidate()
    }

    fun setMagneticNorthOffset(degrees: Float?) {
        magneticOffset = degrees
        invalidate()
    }

    fun setTargetBearing(degrees: Float?) {
        targetBearing = degrees
        invalidate()
    }

    fun setQiblaBearing(degrees: Float?) {
        qiblaBearing = degrees
        invalidate()
    }

    fun setTilt(pitchDegrees: Float, rollDegrees: Float) {
        pitch = pitchDegrees
        roll = rollDegrees
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

        // Manyetik kuzey işareti: kadran gerçek kuzeye göre döndüğünde manyetik
        // kuzey sapma kadar yanda kalır; nerede olduğunu göstermek için işaretlenir.
        magneticOffset?.let { offset ->
            magneticPaint.strokeWidth = dp(2f)
            magneticLabelPaint.textSize = radius * 0.11f
            drawRimLabel(canvas, cx, cy, radius, offset, "M", magneticPaint, magneticLabelPaint)
        }

        // Kıble: konumdan hesaplanan Kâbe yönü, gerçek kuzeye göre.
        qiblaBearing?.let { bearing ->
            qiblaPaint.strokeWidth = dp(2f)
            qiblaLabelPaint.textSize = radius * 0.10f
            drawRimLabel(canvas, cx, cy, radius, bearing, "Kıble", qiblaPaint, qiblaLabelPaint)
        }

        // Kilitli hedef: nişan alınacak yön. Kadranın içine uzanan çizgi,
        // telefonu bu çizgi boyunca döndürüp yönü tutmayı kolaylaştırır.
        targetBearing?.let { bearing ->
            canvas.save()
            canvas.rotate(bearing, cx, cy)
            targetLinePaint.strokeWidth = dp(2.5f)
            canvas.drawLine(cx, cy - radius, cx, cy - radius * 0.34f, targetLinePaint)
            canvas.drawPath(triangle(cx, cy - radius + dp(1f), dp(7f)), targetPaint)
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

        drawLevel(canvas, cx, cy, radius)
    }

    /** Kadranla dönen bir işaret: dış çemberden içeri kısa çizgi + altında etiket. */
    private fun drawRimLabel(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        bearing: Float,
        label: String,
        linePaint: Paint,
        textPaint: Paint
    ) {
        canvas.save()
        canvas.rotate(bearing, cx, cy)
        canvas.drawLine(cx, cy - radius, cx, cy - radius * 0.86f, linePaint)
        canvas.drawText(label, cx, cy - radius * 0.86f + textPaint.textSize, textPaint)
        canvas.restore()
    }

    /**
     * Kadranın göbeğindeki su terazisi. Pusula yatayken doğru okur; kabarcık
     * gerçek terazideki gibi yukarıda kalan tarafa kaçar, ortalanınca telefon düzdür.
     */
    private fun drawLevel(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val frameRadius = radius * 0.20f
        val bubbleRadius = frameRadius * 0.34f
        canvas.drawCircle(cx, cy, frameRadius, levelFillPaint)
        levelFramePaint.strokeWidth = dp(1.5f)
        canvas.drawCircle(cx, cy, frameRadius, levelFramePaint)
        canvas.drawCircle(cx, cy, bubbleRadius * 1.35f, levelFramePaint)

        // pitch: üst kenar kalkınca negatif — kabarcık yukarı gitmeli.
        // roll: sol kenar kalkınca pozitif — kabarcık sola gitmeli.
        val scale = (frameRadius - bubbleRadius) / MAX_TILT
        val dx = -roll.coerceIn(-MAX_TILT, MAX_TILT) * scale
        val dy = pitch.coerceIn(-MAX_TILT, MAX_TILT) * scale
        bubblePaint.color =
            if (abs(pitch) <= LEVEL_TOLERANCE && abs(roll) <= LEVEL_TOLERANCE) COLOR_LEVEL
            else COLOR_TARGET
        canvas.drawCircle(cx + dx, cy + dy, bubbleRadius, bubblePaint)
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

    companion object {
        /** Kadran işaretlerinin renkleri; ekrandaki yazılar da bunlarla eşleşir. */
        val COLOR_MAGNETIC = Color.parseColor("#4C9AFF")
        val COLOR_QIBLA = Color.parseColor("#3DD68C")
        val COLOR_TARGET = Color.parseColor("#F5B841")

        /** Kabarcık: ortalanınca nötr beyaz, kaçınca hedef sarısı. */
        private val COLOR_LEVEL = Color.parseColor("#F0F3F6")

        /** Kabarcığın kenara dayandığı eğim ve "düz sayılır" eşiği (derece). */
        private const val MAX_TILT = 25f
        private const val LEVEL_TOLERANCE = 6f
    }
}
