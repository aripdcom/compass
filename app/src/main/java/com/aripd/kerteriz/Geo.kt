package com.aripd.kerteriz

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Açı ve yön hesapları. Android'e hiç dokunmaz, bu yüzden doğrudan JVM
 * testlerinden çağrılabilir — uygulamanın matematiği ekran koduna karışmasın diye
 * buraya ayrıldı.
 */
object Geo {

    /**
     * İki nokta arasındaki başlangıç açısı (great-circle), gerçek kuzeye göre.
     * Kıble tanımı da budur: hedefe giden en kısa yolun çıkış yönü.
     */
    fun bearing(
        latitude: Double,
        longitude: Double,
        destLatitude: Double,
        destLongitude: Double
    ): Float {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(destLatitude)
        val deltaLon = Math.toRadians(destLongitude - longitude)
        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        return normalize(Math.toDegrees(atan2(y, x)).toFloat())
    }

    /** Açıyı [0, 360) aralığına indirir. */
    fun normalize(degrees: Float): Float = (degrees % 360f + 360f) % 360f

    /**
     * `from` yönünden `to` yönüne dönmek için gereken açı, **[-180, 180)**.
     * Pozitifse saat yönünde, yani sağa dönülür.
     *
     * Tam yarım turda (-180) döner, (+180) değil. İkisi de aynı fiziksel dönüş
     * olduğu için bu bir tercih meselesidir; ekranda "180° sola" yazar.
     */
    fun difference(from: Float, to: Float): Float = ((to - from + 540f) % 360f) - 180f

    /** İki yön arasındaki açı farkının büyüklüğü, [0, 180]. */
    fun separation(a: Float, b: Float): Float = kotlin.math.abs(difference(a, b))

    /**
     * Açıların vektörel ortalaması. Sayısal ortalama 359° ile 1° için 180°
     * verirdi; doğrusu 0°'dir.
     */
    fun meanBearing(bearings: List<Float>): Float {
        var x = 0.0
        var y = 0.0
        bearings.forEach {
            val radians = Math.toRadians(it.toDouble())
            x += cos(radians)
            y += sin(radians)
        }
        return normalize(Math.toDegrees(atan2(y, x)).toFloat())
    }

    /** NATO mili: tam çember 6400. */
    fun degreesToMils(degrees: Float): Int =
        (normalize(degrees) * MILS_PER_CIRCLE / 360f).roundToInt() % MILS_PER_CIRCLE.toInt()

    fun milsToDegrees(mils: Float): Float = mils * 360f / MILS_PER_CIRCLE

    /**
     * Gerçek kuzeye göre verilmiş bir açıyı kadranın çerçevesine çevirir.
     * Kadran manyetik kuzeye göreyse sapma kadar geri alınır.
     */
    fun toDialFrame(trueBearing: Float, declination: Float?, useTrueNorth: Boolean): Float =
        if (useTrueNorth) normalize(trueBearing)
        else normalize(trueBearing - (declination ?: 0f))

    const val MILS_PER_CIRCLE = 6400f
}
