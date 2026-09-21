package com.aripd.kerteriz

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ayın yeri ve evresi.
 *
 * Güneşten belirgin biçimde zor: güneşin yörüngesi tek bir elipsle iyi
 * yaklaşılırken ay, Dünya ile Güneş arasında sürekli çekiştiği için düzensiz
 * hareket eder. Aşağıdaki bozulma (perturbation) terimleri olmadan hata 2°'ye
 * kadar çıkar; en büyük ikisi evection (1,274°) ve variation (0,658°) terimleridir.
 * Terimlerle birlikte hata derecenin yüzde birkaçında kalır — pusula için fazlasıyla.
 *
 * Ayın paralaksı da güneşinkinin aksine ihmal edilemez (~1°), çünkü ay yakındır:
 * gözlemci Dünya'nın merkezinde değil yüzeyindedir. Yükseklik buna göre düzeltilir,
 * yoksa ufka yakın ayın "doğdu mu battı mı" kararı yanlış çıkabilir.
 *
 * Kaynak: Paul Schlyter, "How to compute planetary positions".
 */
object Moon {

    /** Azimut gerçek kuzeye göre; yükseklik ufka göre; aydınlık oran 0..1. */
    data class Position(
        val azimuth: Float,
        val elevation: Float,
        val illumination: Float,
        val waxing: Boolean
    )

    fun position(timeMillis: Long, latitude: Double, longitude: Double): Position {
        // Schlyter'in gün sayısı: sıfır noktası 2000 Ocak 0,0 = 31 Aralık 1999 00:00 UT
        // (JD 2451543,5). Yaygın hata burada JD 2451545,0'i (1 Ocak 2000 öğlen)
        // kullanmaktır; 1,5 günlük kayma ayın yerini 18° şaşırtır.
        val d = timeMillis / 86_400_000.0 - 10_956.0

        // --- Güneş: hem evre hem de yıldız zamanı için gerekiyor ---
        val ws = 282.9404 + 4.70935e-5 * d
        val es = 0.016709 - 1.151e-9 * d
        val ms = rev(356.0470 + 0.9856002585 * d)
        val eSun = ms + degrees(es) * sinDeg(ms) * (1 + es * cosDeg(ms))
        val xvSun = cosDeg(eSun) - es
        val yvSun = sqrt(1 - es * es) * sinDeg(eSun)
        val sunLongitude = rev(degrees(atan2(yvSun, xvSun)) + ws)
        val sunMeanLongitude = rev(ws + ms)

        // --- Ayın yörünge öğeleri ---
        val n = rev(125.1228 - 0.0529538083 * d)   // çıkış düğümü
        val i = 5.1454
        val w = rev(318.0634 + 0.1643573223 * d)   // enberi argümanı
        val a = 60.2666                            // Dünya yarıçapı cinsinden
        val e = 0.054900
        val mm = rev(115.3654 + 13.0649929509 * d) // ortalama anomali

        // Kepler denklemi: ayın dışmerkezliği büyük olduğu için bir kez yinelenir
        var eccentric = mm + degrees(e) * sinDeg(mm) * (1 + e * cosDeg(mm))
        eccentric -= (eccentric - degrees(e) * sinDeg(eccentric) - mm) /
            (1 - e * cosDeg(eccentric))

        val x = a * (cosDeg(eccentric) - e)
        val y = a * sqrt(1 - e * e) * sinDeg(eccentric)
        val distance = sqrt(x * x + y * y)
        val trueAnomaly = degrees(atan2(y, x))

        // Ekliptik koordinatlar
        val vw = trueAnomaly + w
        val xec = distance * (cosDeg(n) * cosDeg(vw) - sinDeg(n) * sinDeg(vw) * cosDeg(i))
        val yec = distance * (sinDeg(n) * cosDeg(vw) + cosDeg(n) * sinDeg(vw) * cosDeg(i))
        val zec = distance * sinDeg(vw) * sinDeg(i)

        var longitudeEc = rev(degrees(atan2(yec, xec)))
        var latitudeEc = degrees(atan2(zec, sqrt(xec * xec + yec * yec)))

        // --- Bozulmalar ---
        val lm = rev(n + w + mm)                 // ayın ortalama boylamı
        val dm = rev(lm - sunMeanLongitude)      // ortalama uzanım
        val f = rev(lm - n)                      // enlem argümanı

        longitudeEc += (-1.274 * sinDeg(mm - 2 * dm)          // evection
            + 0.658 * sinDeg(2 * dm)                          // variation
            - 0.186 * sinDeg(ms)                              // yıllık denklem
            - 0.059 * sinDeg(2 * mm - 2 * dm)
            - 0.057 * sinDeg(mm - 2 * dm + ms)
            + 0.053 * sinDeg(mm + 2 * dm)
            + 0.046 * sinDeg(2 * dm - ms)
            + 0.041 * sinDeg(mm - ms)
            - 0.035 * sinDeg(dm)                              // paralaktik denklem
            - 0.031 * sinDeg(mm + ms)
            - 0.015 * sinDeg(2 * f - 2 * dm)
            + 0.011 * sinDeg(mm - 4 * dm))
        latitudeEc += (-0.173 * sinDeg(f - 2 * dm)
            - 0.055 * sinDeg(mm - f - 2 * dm)
            - 0.046 * sinDeg(mm + f - 2 * dm)
            + 0.033 * sinDeg(f + 2 * dm)
            + 0.017 * sinDeg(2 * mm + f))

        // --- Ekliptikten ekvatora, oradan ufka ---
        val obliquity = 23.4393 - 3.563e-7 * d
        val xeq = cosDeg(longitudeEc) * cosDeg(latitudeEc)
        val yeq = sinDeg(longitudeEc) * cosDeg(latitudeEc) * cosDeg(obliquity) -
            sinDeg(latitudeEc) * sinDeg(obliquity)
        val zeq = sinDeg(longitudeEc) * cosDeg(latitudeEc) * sinDeg(obliquity) +
            sinDeg(latitudeEc) * cosDeg(obliquity)
        val rightAscension = rev(degrees(atan2(yeq, xeq)))
        val declination = degrees(atan2(zeq, sqrt(xeq * xeq + yeq * yeq)))

        val utHours = (timeMillis % 86_400_000L) / 3_600_000.0
        val siderealTime = rev(rev(sunMeanLongitude + 180.0) + utHours * 15.0 + longitude)
        val hourAngle = rev(siderealTime - rightAscension)

        val hx = cosDeg(hourAngle) * cosDeg(declination)
        val hy = sinDeg(hourAngle) * cosDeg(declination)
        val hz = sinDeg(declination)
        val horizontalX = hx * sinDeg(latitude) - hz * cosDeg(latitude)
        val horizontalZ = hx * cosDeg(latitude) + hz * sinDeg(latitude)

        val azimuth = rev(degrees(atan2(hy, horizontalX)) + 180.0)
        val geocentricAltitude = degrees(asin(horizontalZ))
        // Paralaks: gözlemci yüzeyde olduğu için ay merkeze göre biraz alçakta görünür
        val parallax = degrees(asin(1.0 / distance))
        val altitude = geocentricAltitude - parallax * cosDeg(geocentricAltitude)

        // --- Evre: aydan güneşe olan uzanım ---
        val elongation = rev(longitudeEc - sunLongitude)
        val illumination = ((1 - cosDeg(elongation)) / 2).toFloat()

        return Position(
            azimuth.toFloat(),
            altitude.toFloat(),
            illumination.coerceIn(0f, 1f),
            elongation < 180.0
        )
    }

    private fun rev(angle: Double): Double = (angle % 360.0 + 360.0) % 360.0
    private fun degrees(radians: Double): Double = Math.toDegrees(radians)
    private fun sinDeg(angle: Double): Double = sin(Math.toRadians(angle))
    private fun cosDeg(angle: Double): Double = cos(Math.toRadians(angle))
}

/** Kadrana çizilecek ay işareti. */
data class MoonMark(
    val bearing: Float,
    val aboveHorizon: Boolean,
    val illumination: Float,
    val waxing: Boolean
)
