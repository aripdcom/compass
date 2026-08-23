package com.aripd.compass

import kotlin.math.acos
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

    /**
     * Güneşin doğduğu ve battığı yönler (gerçek kuzeye göre) ile anları
     * (epok milisaniyesi, UTC).
     */
    data class RiseSet(val rise: Float, val set: Float, val riseAt: Long, val setAt: Long)

    /**
     * Güneşin merkezi ufkun 0,833° altındayken görünür: atmosferik kırılma 34′
     * yukarı kaldırır, güneşin yarıçapı da 16′ ekler. Doğuş ve batış bu yüksekliğe
     * göre tanımlıdır, tam ufka göre değil.
     */
    private const val HORIZON_DEGREES = -0.833

    /**
     * Bugün güneşin hangi yönden doğup hangi yönden batacağı.
     *
     * "Doğudan doğar" yalnızca ekinokslarda doğrudur: İstanbul'da doğuş noktası
     * yıl boyunca 57° ile 121° arasında, 64°'lik bir yay tarar. Kutup gündüzü ya
     * da gecesinde güneş ufku hiç kesmez, o zaman null döner.
     */
    fun riseSet(timeMillis: Long, latitude: Double, longitude: Double): RiseSet? {
        val latitudeRad = Math.toRadians(latitude)
        val riseAt = horizonMoment(timeMillis, latitudeRad, longitude, sunrise = true) ?: return null
        val setAt = horizonMoment(timeMillis, latitudeRad, longitude, sunrise = false) ?: return null
        val rise = horizonAzimuth(declination(riseAt), latitudeRad) ?: return null
        val set = horizonAzimuth(declination(setAt), latitudeRad) ?: return null
        return RiseSet(rise.toFloat(), (360.0 - set).toFloat(), riseAt, setAt)
    }

    /** Güneşin ufka değdiği andaki azimut (kuzeyden doğuya doğru ölçülür). */
    private fun horizonAzimuth(declination: Double, latitudeRad: Double): Double? {
        val horizon = Math.toRadians(HORIZON_DEGREES)
        val cosAzimuth = (sin(declination) - sin(latitudeRad) * sin(horizon)) /
            (cos(latitudeRad) * cos(horizon))
        if (cosAzimuth < -1.0 || cosAzimuth > 1.0) return null
        return Math.toDegrees(acos(cosAzimuth))
    }

    /**
     * Güneşin ufku kestiği an. Saat açısı formülü deklinasyona bağlı,
     * deklinasyon ise gün içinde değişiyor; tek geçişte hesaplanan an yarım
     * dakikaya varan hata veriyordu. Bulunan an için deklinasyon ve zaman
     * denklemi yeniden hesaplanıp bir kez yineleniyor — kalan hata saniyeler
     * mertebesinde.
     */
    private fun horizonMoment(
        reference: Long,
        latitudeRad: Double,
        longitude: Double,
        sunrise: Boolean
    ): Long? {
        val horizon = Math.toRadians(HORIZON_DEGREES)
        val dayStart = reference - Math.floorMod(reference, 86_400_000L)
        var moment = reference
        repeat(2) {
            val declination = declination(moment)
            val cosHourAngle = (sin(horizon) - sin(latitudeRad) * sin(declination)) /
                (cos(latitudeRad) * cos(declination))
            if (cosHourAngle < -1.0 || cosHourAngle > 1.0) return null
            val hourAngle = Math.toDegrees(acos(cosHourAngle))
            // Gerçek güneş saatinden UTC'ye: boylamın her derecesi 4 dakika,
            // üstüne zaman denklemi. Öğlen 720. dakikadır.
            val minutes = 720.0 + (if (sunrise) -4.0 * hourAngle else 4.0 * hourAngle) -
                equationOfTime(moment) - 4.0 * longitude
            moment = dayStart + (minutes * 60_000.0).toLong()
        }
        return moment
    }

    /** Julian yüzyıl: algoritmanın bütün katsayıları buna göre yazılmıştır. */
    private fun julianCentury(timeMillis: Long): Double =
        ((timeMillis / 86_400_000.0 + 2_440_587.5) - 2_451_545.0) / 36_525.0

    /**
     * Zaman denklemi (dakika): gerçek güneş saati ile ortalama saat arasındaki
     * fark. Dünya'nın yörüngesi dairesel olmadığı ve ekseni eğik olduğu için
     * güneş öğlesi yıl boyunca ±16 dakikaya varan biçimde kayar.
     */
    private fun equationOfTime(timeMillis: Long): Double {
        val t = julianCentury(timeMillis)
        val meanLongitude = (280.46646 + t * (36_000.76983 + t * 0.0003032)) % 360.0
        val meanAnomaly = Math.toRadians(357.52911 + t * (35_999.05029 - 0.0001537 * t))
        val eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val omega = Math.toRadians(125.04 - 1934.136 * t)
        val meanObliquity =
            23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val obliquity = Math.toRadians(meanObliquity + 0.00256 * cos(omega))
        val y = tan(obliquity / 2).let { it * it }
        val meanLongitudeRad = Math.toRadians(meanLongitude)
        return 4.0 * Math.toDegrees(
            y * sin(2 * meanLongitudeRad) -
                2 * eccentricity * sin(meanAnomaly) +
                4 * eccentricity * y * sin(meanAnomaly) * cos(2 * meanLongitudeRad) -
                0.5 * y * y * sin(4 * meanLongitudeRad) -
                1.25 * eccentricity * eccentricity * sin(2 * meanAnomaly)
        )
    }

    /** Güneşin o andaki deklinasyonu (radyan). */
    private fun declination(timeMillis: Long): Double {
        val t = julianCentury(timeMillis)
        val meanLongitude = (280.46646 + t * (36_000.76983 + t * 0.0003032)) % 360.0
        val meanAnomaly = Math.toRadians(357.52911 + t * (35_999.05029 - 0.0001537 * t))
        val center = sin(meanAnomaly) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * meanAnomaly) * (0.019993 - 0.000101 * t) +
            sin(3 * meanAnomaly) * 0.000289
        val omega = Math.toRadians(125.04 - 1934.136 * t)
        val apparentLongitude = Math.toRadians(meanLongitude + center - 0.00569 - 0.00478 * sin(omega))
        val meanObliquity =
            23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val obliquity = Math.toRadians(meanObliquity + 0.00256 * cos(omega))
        return asin(sin(obliquity) * sin(apparentLongitude))
    }

    fun position(timeMillis: Long, latitude: Double, longitude: Double): Position {
        val t = julianCentury(timeMillis)

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
