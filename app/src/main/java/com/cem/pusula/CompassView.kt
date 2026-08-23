package com.cem.pusula

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

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

    /** Kadranda yazıyla gösterilen sabit nokta yönleri (kıble, Aksa, Vatikan…). */
    private var placeMarks: List<PlaceMark> = emptyList()

    /** Kaydedilen noktanın yönü; konum bilinmeden hesaplanamaz. */
    private var waypointBearing: Float? = null

    /** Ay döndürülmemiş katmanda çizildiği için yarıçapı buradan taşınır. */
    private var moonFraction: Float? = null

    /** Su terazisi gösterilsin mi; kapalıyken göbekte yalnızca küçük bir nokta olur. */
    var levelVisible: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    /** Güneşin bugün izleyeceği yol: doğuş ve batış yönleri. */
    private var sunArc: Pair<Float, Float>? = null

    /** Ayın yönü, ufkun üstünde olup olmadığı ve evresi. */
    private var moon: MoonMark? = null

    /** Güneşin yönü ve ufkun üstünde olup olmadığı. */
    private var sunBearing: Float? = null
    private var sunAboveHorizon = true

    // Su terazisi için eğim (derece). Düz tutulan telefonda ikisi de 0'dır.
    private var pitch = 0f
    private var roll = 0f

    private val dialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val majorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val minorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val northPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val southPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val magneticPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val magneticLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val qiblaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val qiblaLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val waypointPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val targetLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val levelFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val levelFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** Yürürlükteki renk düzeni; değişince bütün boyalar yeniden atanır. */
    var palette: Palette = Palette.DAY
        set(value) {
            field = value
            applyPalette()
            invalidate()
        }

    private fun applyPalette() {
        dialPaint.color = palette.dial
        majorTickPaint.color = palette.majorTick
        minorTickPaint.color = palette.minorTick
        northPaint.color = palette.needleNorth
        southPaint.color = palette.needleSouth
        markerPaint.color = palette.needleNorth
        magneticPaint.color = palette.magnetic
        magneticLabelPaint.color = palette.magnetic
        qiblaPaint.color = palette.qibla
        qiblaLabelPaint.color = palette.qibla
        sunPaint.color = palette.sun
        sunArcPaint.color = palette.sun
        moonPaint.color = palette.moon
        waypointPaint.color = palette.waypoint
        targetPaint.color = palette.target
        targetLinePaint.color = palette.target
        levelFillPaint.color = palette.levelFill
        levelFramePaint.color = palette.levelFrame
    }

    init {
        applyPalette()
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

    /** Ay işareti; evresi diskin doluluğuyla çizilir. */
    fun setMoon(mark: MoonMark?) {
        moon = mark
        invalidate()
    }

    /** Doğuş ve batış yönleri; kutup gündüzü/gecesinde null olur ve yay çizilmez. */
    fun setSunArc(riseAndSet: Pair<Float, Float>?) {
        sunArc = riseAndSet
        invalidate()
    }

    /** Güneş batmışsa disk içi boş çizilir: yön hâlâ bilgi, ama bakacak güneş yok. */
    fun setSun(degrees: Float?, aboveHorizon: Boolean) {
        sunBearing = degrees
        sunAboveHorizon = aboveHorizon
        invalidate()
    }

    fun setWaypointBearing(degrees: Float?) {
        waypointBearing = degrees
        invalidate()
    }

    fun setTargetBearing(degrees: Float?) {
        targetBearing = degrees
        invalidate()
    }

    fun setPlaceMarks(marks: List<PlaceMark>) {
        placeMarks = marks
        invalidate()
    }

    fun setTilt(pitchDegrees: Float, rollDegrees: Float) {
        pitch = pitchDegrees
        roll = rollDegrees
        invalidate()
    }

    /**
     * Ekran okuyucuya kadranın iki hareketini adıyla bildirir. Varsayılan
     * "etkinleştir" / "uzun bas" etiketleri ne yaptıklarını söylemiyor; TalkBack
     * kullanıcısı kadrana dokunmanın yönü kilitlediğini bilemezdi.
     */
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.addAction(
            AccessibilityNodeInfo.AccessibilityAction(
                AccessibilityNodeInfo.ACTION_CLICK,
                context.getString(R.string.a11y_lock_bearing)
            )
        )
        info.addAction(
            AccessibilityNodeInfo.AccessibilityAction(
                AccessibilityNodeInfo.ACTION_LONG_CLICK,
                context.getString(R.string.a11y_save_point)
            )
        )
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

        val labels = resources.getStringArray(R.array.dial_labels)
        labels.forEachIndexed { index, label ->
            canvas.save()
            canvas.rotate(index * 45f, cx, cy)
            labelPaint.color = if (index == 0) palette.northLabel else palette.label
            labelPaint.textSize = if (index % 2 == 0) radius * 0.17f else radius * 0.12f
            canvas.drawText(
                label,
                cx,
                cy - radius + radius * 0.30f - (labelPaint.ascent() + labelPaint.descent()) / 2f,
                labelPaint
            )
            canvas.restore()
        }

        // Kadran işaretleri tek listede toplanır: yazılar (M, yerler) ve semboller
        // (güneş, ay, nokta) aynı halkayı paylaştığı için yarıçapları birlikte
        // dağıtılmalı. Ayrı ele alındıklarında yönleri yakın bir yazı ile bir
        // sembol üst üste biniyordu -- ölçümde ay ile kaydedilen nokta bunu yaptı.
        magneticPaint.strokeWidth = dp(2f)
        qiblaPaint.strokeWidth = dp(2f)
        magneticLabelPaint.textSize = radius * 0.095f
        qiblaLabelPaint.textSize = radius * 0.085f

        val marks = ArrayList<RimItem>()
        magneticOffset?.let {
            val label = context.getString(R.string.magnetic_label)
            marks.add(RimItem(it, halfWidth(magneticLabelPaint.measureText(label) / 2f, radius), KIND_MAGNETIC, label))
        }
        placeMarks.forEach {
            marks.add(RimItem(it.bearing, halfWidth(qiblaLabelPaint.measureText(it.label) / 2f, radius), KIND_PLACE, it.label))
        }
        sunBearing?.let { marks.add(RimItem(it, halfWidth(dp(6f), radius), KIND_SUN, null)) }
        moon?.let { marks.add(RimItem(it.bearing, halfWidth(dp(7.5f), radius), KIND_MOON, null)) }
        waypointBearing?.let { marks.add(RimItem(it, halfWidth(dp(7f), radius), KIND_WAYPOINT, null)) }

        // Tepedeki gösterge kadran çerçevesinde `azimuth` yönüne denk gelir:
        // kadran -azimuth kadar döndüğü için o yön ekranın tepesine çıkar.
        val topMarker = RimItem(azimuth, halfWidth(dp(7.2f), radius), KIND_MAGNETIC, null)
        val fractions = RimLayout.assign(
            marks.map { RimLayout.Mark(it.bearing, it.halfWidth) },
            RimLayout.Mark(topMarker.bearing, topMarker.halfWidth),
            RIM_RADII,
            RIM_MARGIN
        )
        moonFraction = null
        marks.forEachIndexed { index, item ->
            val fraction = fractions[index]
            when (item.kind) {
                KIND_MAGNETIC ->
                    drawRimLabel(canvas, cx, cy, radius, item.bearing, item.label!!, magneticPaint, magneticLabelPaint, fraction)
                KIND_PLACE ->
                    drawRimLabel(canvas, cx, cy, radius, item.bearing, item.label!!, qiblaPaint, qiblaLabelPaint, fraction)
                KIND_SUN -> {
                    canvas.save()
                    canvas.rotate(item.bearing, cx, cy)
                    sunPaint.style = if (sunAboveHorizon) Paint.Style.FILL else Paint.Style.STROKE
                    sunPaint.strokeWidth = dp(2f)
                    canvas.drawCircle(cx, cy - radius * fraction, dp(6f), sunPaint)
                    canvas.restore()
                }
                KIND_WAYPOINT -> {
                    canvas.save()
                    canvas.rotate(item.bearing, cx, cy)
                    waypointPaint.style = Paint.Style.FILL
                    canvas.drawPath(diamond(cx, cy - radius * fraction, dp(7f)), waypointPaint)
                    canvas.restore()
                }
                // Ay döndürülmemiş katmanda çizildiği için yarıçapı saklanır.
                else -> moonFraction = fraction
            }
        }

        // Güneşin gün boyu izleyeceği yol: doğuştan batışa, güneyin üzerinden.
        // Yay tek başına hem iki uç noktayı hem de yolu anlatır ve etiket
        // eklemediği için kadranın yazı bütçesini harcamaz.
        sunArc?.let { (rise, set) ->
            sunArcPaint.strokeWidth = dp(2.5f)
            val inset = radius * SUN_ARC_RADIUS
            // Canvas açıları saat 3 yönünden başlar; kadran açısı 90° geridedir.
            canvas.drawArc(
                cx - inset, cy - inset, cx + inset, cy + inset,
                rise - 90f, (set - rise + 360f) % 360f, false, sunArcPaint
            )
            for (edge in floatArrayOf(rise, set)) {
                canvas.save()
                canvas.rotate(edge, cx, cy)
                canvas.drawLine(cx, cy - radius, cx, cy - radius * 0.93f, sunArcPaint)
                canvas.restore()
            }
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

        // Ay kadranla birlikte döndürülmez: evre şekli yönlü bir simge, kadranın
        // dibine düştüğünde 180° dönüp aynalanır ve büyüyen ay küçülen gibi
        // görünürdü. Konumu açıdan hesaplanır, simge ekrana dik çizilir.
        moon?.let { mark ->
            val angle = Math.toRadians((mark.bearing - azimuth).toDouble())
            val distance = radius * (moonFraction ?: RIM_RADII[0])
            val mx = cx + distance * sin(angle).toFloat()
            val my = cy - distance * cos(angle).toFloat()
            val discRadius = dp(7.5f)
            moonPaint.alpha = if (mark.aboveHorizon) 255 else 110
            moonPaint.style = Paint.Style.STROKE
            moonPaint.strokeWidth = dp(1.5f)
            canvas.drawCircle(mx, my, discRadius, moonPaint)
            moonPaint.style = Paint.Style.FILL
            canvas.drawPath(moonPath(mx, my, discRadius, mark.illumination, mark.waxing), moonPaint)
            moonPaint.alpha = 255
        }

        if (levelVisible) {
            drawLevel(canvas, cx, cy, radius)
        } else {
            // Terazi yokken ibrenin merkezi boş kalmasın.
            canvas.drawCircle(cx, cy, dp(4f), levelFramePaint.apply { style = Paint.Style.FILL })
            levelFramePaint.style = Paint.Style.STROKE
        }
    }

    /** Bir işaretin kadran merkezinden görülen açısal yarı genişliği (derece). */
    private fun halfWidth(halfWidthPixels: Float, radius: Float): Float =
        Math.toDegrees(atan2(halfWidthPixels.toDouble(), (radius * RIM_RADII[0]).toDouble())).toFloat()

    /**
     * Kadranla dönen bir işaret: dış çemberden içeri çizgi + ucunda etiket.
     * Her işaret türü kendi yarıçapında yazılır; aksi hâlde yönleri çakıştığında
     * (nokta tam kuzeydeyken "Nokta", "M" ve "K" gibi) etiketler üst üste biner.
     */
    private fun drawRimLabel(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        bearing: Float,
        label: String,
        linePaint: Paint,
        textPaint: Paint,
        labelFraction: Float
    ) {
        canvas.save()
        canvas.rotate(bearing, cx, cy)
        // Çizgi etiketin hemen üstünde biter: iç yarıçaplara inen etiketler
        // kenardaki çentikten kopuk görünmesin diye.
        canvas.drawLine(cx, cy - radius, cx, cy - radius * (labelFraction + 0.045f), linePaint)
        // Etiket, çizgiden bağımsız olarak verilen yarıçapa dikey ortalanır; yön
        // harfleri 0,70R civarında olduğu için etiketler onların dışında kalır.
        canvas.drawText(
            label,
            cx,
            cy - radius * labelFraction - (textPaint.ascent() + textPaint.descent()) / 2f,
            textPaint
        )
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
            if (abs(pitch) <= LEVEL_TOLERANCE && abs(roll) <= LEVEL_TOLERANCE) palette.level
            else palette.target
        canvas.drawCircle(cx + dx, cy + dy, bubbleRadius, bubblePaint)
    }

    /**
     * Ayın aydınlık kısmı: bir yanda diskin kenarı (yarım çember), öbür yanda
     * terminatör. Terminatör yarı ekseni `r(1-2f)`; f<0,5'te hilalin içine doğru,
     * f>0,5'te karanlık tarafa doğru bombeleşir, f=0,5'te düz çizgi olur.
     */
    private fun moonPath(cx: Float, cy: Float, r: Float, illumination: Float, waxing: Boolean): Path {
        val path = Path()
        val terminator = r * (1f - 2f * illumination)
        val limb = RectF(cx - r, cy - r, cx + r, cy + r)
        val term = RectF(cx - abs(terminator), cy - r, cx + abs(terminator), cy + r)
        if (waxing) {
            path.arcTo(limb, -90f, 180f, true)
            path.arcTo(term, 90f, if (terminator < 0f) 180f else -180f)
        } else {
            path.arcTo(limb, -90f, -180f, true)
            path.arcTo(term, 90f, if (terminator < 0f) -180f else 180f)
        }
        path.close()
        return path
    }

    private fun diamond(cx: Float, cy: Float, size: Float): Path {
        val path = Path()
        path.moveTo(cx, cy - size)
        path.lineTo(cx + size * 0.72f, cy)
        path.lineTo(cx, cy + size)
        path.lineTo(cx - size * 0.72f, cy)
        path.close()
        return path
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
        /**
         * Yazılı işaretlerin yerleşebileceği yarıçaplar. Yön harfleri 0,62-0,785R
         * bandını kapladığı için en içteki bile onların dışında kalır.
         */
        /**
         * İşaretlerin yerleşebileceği yarıçaplar. En içteki bile yön harflerinin
         * bandının (0,62-0,785R) dışında kalır.
         */
        private val RIM_RADII = floatArrayOf(0.94f, 0.855f, 0.79f)

        /** İki işaret arasında bırakılan asgari açısal boşluk (derece). */
        private const val RIM_MARGIN = 1.5f

        /** Güneş yayının yarıçapı; dış çemberin hemen içinde. */
        private const val SUN_ARC_RADIUS = 0.965f

        /** İşaret çizgisinin dış çemberden içeri indiği nokta. */
        private const val RIM_TICK_INNER = 0.93f

        /** Kabarcığın kenara dayandığı eğim ve "düz sayılır" eşiği (derece). */
        private const val MAX_TILT = 25f
        private const val LEVEL_TOLERANCE = 6f
    }
}
