package com.cem.pusula

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Güneşin o an gökyüzündeki yeri: NOAA'nın güneş konumu algoritması, saf Kotlin.
 *
 * Neden işe yarıyor: güneşin yönü manyetik alandan tamamen bağımsızdır. Pusula
 * şüpheliyse (yakında metal, kalibrasyon bozuk) güneşe bakıp kadranı çapraz
 * kontrol edebilirsiniz — saat ve konum doğruysa güneşin yönü şaşmaz.
 *
 * Bütün hesap UTC üzerinden yapılır, o yüzden cihazın saat dilimi ayarının
 * yanlış olması sonucu etkilemez; yalnızca saatin kendisi doğru olmalıdır.
 */
object Sun {

    /** Azimut gerçek kuzeye göre, yükseklik ufka göre (negatifse güneş batmıştır). */
    data class Position(val azimuth: Float, val elevation: Float)

    fun position(timeMillis: Long, latitude: Double, longitude: Double): Position {
        // Julian yüzyıl: algoritmanın bütün katsayıları buna göre yazılmıştır.
        val julianDay = timeMillis / 86_400_000.0 + 2_440_587.5
        val t = (julianDay - 2_451_545.0) / 36_525.0

        // Güneşin ortalama boylamı ve ortalama anomalisi (derece)
        val meanLongitude = (280.46646 + t * (36_000.76983 + t * 0.0003032)) % 360.0
        val meanAnomaly = 357.52911 + t * (35_999.05029 - 0.0001537 * t)
        val eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)

        // Merkez denklemi: dairesel olmayan yörüngenin düzeltmesi
        val anomalyRad = Math.toRadians(meanAnomaly)
        val center = sin(anomalyRad) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * anomalyRad) * (0.019993 - 0.000101 * t) +
            sin(3 * anomalyRad) * 0.000289

        // Görünür boylam (nütasyon ve aberasyon düzeltmeli)
        val omega = Math.toRadians(125.04 - 1934.136 * t)
        val apparentLongitude = Math.toRadians(meanLongitude + center - 0.00569 - 0.00478 * sin(omega))

        // Ekliptik eğimi
        val meanObliquity = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val obliquity = Math.toRadians(meanObliquity + 0.00256 * cos(omega))

        val declination = asin(sin(obliquity) * sin(apparentLongitude))

        // Zaman denklemi (dakika): gerçek güneş saati ile ortalama saat arasındaki fark
        val y = tan(obliquity / 2).let { it * it }
        val meanLongitudeRad = Math.toRadians(meanLongitude)
        val equationOfTime = 4.0 * Math.toDegrees(
            y * sin(2 * meanLongitudeRad) -
                2 * eccentricity * sin(anomalyRad) +
                4 * eccentricity * y * sin(anomalyRad) * cos(2 * meanLongitudeRad) -
                0.5 * y * y * sin(4 * meanLongitudeRad) -
                1.25 * eccentricity * eccentricity * sin(2 * anomalyRad)
        )

        // Gerçek güneş saati doğrudan UTC'den kuruluyor: boylamın her derecesi
        // 4 dakika eder, zaman denklemi de üstüne eklenir. Saat dilimi hiç girmiyor.
        val utcMinutes = (timeMillis % 86_400_000L) / 60_000.0
        val trueSolarTime = (utcMinutes + equationOfTime + 4.0 * longitude).mod(1440.0)

        // Saat açısı: öğlen 0, öğleden sonra pozitif
        val hourAngle = Math.toRadians(trueSolarTime / 4.0 - 180.0)

        val latitudeRad = Math.toRadians(latitude)
        val elevation = asin(
            sin(latitudeRad) * sin(declination) +
                cos(latitudeRad) * cos(declination) * cos(hourAngle)
        )
        // Güneyden ölçülen azimut; kuzeye çevirmek için 180 eklenir.
        val azimuthFromSouth = atan2(
            sin(hourAngle),
            cos(hourAngle) * sin(latitudeRad) - tan(declination) * cos(latitudeRad)
        )
        val azimuth = (Math.toDegrees(azimuthFromSouth) + 180.0).mod(360.0)

        return Position(azimuth.toFloat(), Math.toDegrees(elevation).toFloat())
    }
}
