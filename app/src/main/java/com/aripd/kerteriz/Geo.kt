package com.aripd.kerteriz

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

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

    /**
     * Loksodrom kerterizi: baştan sona **aynı** açıyla gidilince hedefe varılan
     * yolun açısı.
     *
     * [bearing] en kısa yolu verir ama o yolun pruvası adım adım değişir —
     * dümende tutulacak tek bir sayı çıkmaz. Loksodrom biraz uzundur, buna
     * karşılık bir kez verilir ve varana kadar geçerlidir; seyir haritası
     * geleneği bu yüzden onu kullanır.
     *
     * Aradaki fark küçümsenecek gibi değil: New York'tan Lizbon'a en kısa yol
     * 70°'den çıkar, sabit pruva 92°'dir.
     *
     * Hesap WGS-84 elipsoidinde, kürede değil. Uygulamanın mesafeleri zaten
     * `Location.distanceBetween`'den, yani o elipsoitten geliyor. Kürede
     * hesaplamanın bedeli ölçüldü: İstanbul'dan Kâbe'ye kerteriz altı dakika,
     * en kötü hâlde on bir dakika kayıyor.
     */
    fun rhumbBearing(
        latitude: Double,
        longitude: Double,
        destLatitude: Double,
        destLongitude: Double
    ): Float {
        val course = rhumbCourse(
            Math.toRadians(latitude),
            Math.toRadians(destLatitude),
            shortestLongitude(destLongitude - longitude)
        )
        return normalize(Math.toDegrees(course).toFloat())
    }

    /**
     * Loksodrom boyunca yürünen yol (metre): [rhumbBearing]'in verdiği açıyla
     * gidilince kat edilen uzunluk, en kısa yol değil.
     *
     * Ayrı yazılmak zorunda. Horn Burnu'ndan Agulhas Burnu'na en kısa yol
     * 6722 km, sabit pruvayla 7110 km; sabit pruvayı yazıp yanına en kısa
     * yolun uzunluğunu koymak 389 kilometre yanlış söylemek olurdu.
     */
    fun rhumbDistance(
        latitude: Double,
        longitude: Double,
        destLatitude: Double,
        destLongitude: Double
    ): Float {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(destLatitude)
        val deltaLon = shortestLongitude(destLongitude - longitude)
        val northward = cos(rhumbCourse(lat1, lat2, deltaLon))
        // Tam doğu-batı yolunda meridyen payı sıfırdır ve bölme anlamını
        // yitirir; orada yol doğrudan enlem çemberi üzerindeki uzunluktur.
        return if (abs(northward) > EAST_WEST_LIMIT) {
            abs((meridianArc(lat2) - meridianArc(lat1)) / northward).toFloat()
        } else {
            (abs(deltaLon) * primeVertical(lat1) * cos(lat1)).toFloat()
        }
    }

    /**
     * Loksodromun açısı (radyan). Mercator'un uzatılmış enleminde loksodrom düz
     * bir çizgidir; açısı da bu yüzden tek bir `atan2`'ye iniyor.
     *
     * Kutupta uzatılmış enlem sonsuza gider ve `atan2` bunu doğru okur: kuzey
     * kutbuna 0°, güney kutbuna 180°. İki nokta da aynı kutuptaysa sonsuzdan
     * sonsuz çıkarılır ve NaN kalır — orası tek bir noktadır, aradaki yol
     * sıfırdır; fark sıfır sayılınca [rhumbDistance] de sıfır verir.
     */
    private fun rhumbCourse(latitude: Double, destLatitude: Double, deltaLon: Double): Double {
        val stretched = isometricLatitude(destLatitude) - isometricLatitude(latitude)
        return atan2(deltaLon, if (stretched.isNaN()) 0.0 else stretched)
    }

    /**
     * Mercator'un izometrik enlemi: enlemi, üzerinde sabit pruvanın düz çizgi
     * olduğu ölçeğe taşır. Sondaki `e` terimi elipsoit düzeltmesidir, kürede
     * karşılığı yoktur.
     */
    private fun isometricLatitude(latitude: Double): Double {
        // Kutup elle ayrılıyor: `tan` orada sonsuza gider ama Double'da
        // `tan(PI/2)` sonsuz çıkmaz, 1,6×10¹⁶ çıkar ve logaritması makul
        // görünen sessizce yanlış bir sayı verirdi.
        if (latitude >= POLE) return Double.POSITIVE_INFINITY
        if (latitude <= -POLE) return Double.NEGATIVE_INFINITY
        val eccentric = ECCENTRICITY * sin(latitude)
        return ln(tan(PI / 4 + latitude / 2)) -
            ECCENTRICITY / 2 * ln((1 + eccentric) / (1 - eccentric))
    }

    /**
     * Ekvatordan verilen enleme meridyen boyunca uzunluk (metre).
     *
     * Elipsoidin meridyeni çember değil elips olduğu için kapalı biçimi yok;
     * seri e⁸'e kadar alındı. Bu hâliyle çeyrek meridyende (10.002 km) hata
     * onda bir milimetrenin altında kalıyor.
     */
    private fun meridianArc(latitude: Double): Double =
        EQUATORIAL_RADIUS * (1 - ECCENTRICITY_SQUARED) * (
            ARC_0 * latitude -
                ARC_2 * sin(2 * latitude) +
                ARC_4 * sin(4 * latitude) -
                ARC_6 * sin(6 * latitude) +
                ARC_8 * sin(8 * latitude)
            )

    /**
     * Birinci dikey kesitin eğrilik yarıçapı. Enlem çemberinin yarıçapı bunun
     * kosinüs katıdır; doğu-batı yolunun uzunluğu oradan çıkıyor.
     */
    private fun primeVertical(latitude: Double): Double =
        EQUATORIAL_RADIUS / sqrt(1 - ECCENTRICITY_SQUARED * sin(latitude) * sin(latitude))

    /**
     * Boylam farkını kısa yoldan verir (radyan): tarih çizgisinin iki yanı
     * komşudur, 179° doğu ile 179° batı arasında 358° değil 2° vardır.
     */
    private fun shortestLongitude(degrees: Double): Double {
        val radians = Math.toRadians(degrees)
        return when {
            radians > PI -> radians - 2 * PI
            radians < -PI -> radians + 2 * PI
            else -> radians
        }
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
    fun separation(a: Float, b: Float): Float = abs(difference(a, b))

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

    /** Deniz mili: 1929'daki uluslararası anlaşmadan beri tam olarak 1852 metre. */
    const val METERS_PER_NAUTICAL_MILE = 1852f

    // WGS-84. Android'in `Location.distanceBetween`'i de bu elipsoidi ölçüyor;
    // loksodrom ondan ayrı bir yer şekli kullanırsa iki mesafe tutmaz.
    private const val EQUATORIAL_RADIUS = 6378137.0
    private const val FLATTENING = 1 / 298.257223563
    private const val ECCENTRICITY_SQUARED = FLATTENING * (2 - FLATTENING)
    private const val ECCENTRICITY_4 = ECCENTRICITY_SQUARED * ECCENTRICITY_SQUARED
    private const val ECCENTRICITY_6 = ECCENTRICITY_4 * ECCENTRICITY_SQUARED
    private const val ECCENTRICITY_8 = ECCENTRICITY_6 * ECCENTRICITY_SQUARED
    private val ECCENTRICITY = sqrt(ECCENTRICITY_SQUARED)

    // Meridyen yayı serisinin katsayıları (e²'nin kuvvetlerine göre).
    private const val ARC_0 = 1 + 3 * ECCENTRICITY_SQUARED / 4 + 45 * ECCENTRICITY_4 / 64 +
        175 * ECCENTRICITY_6 / 256 + 11025 * ECCENTRICITY_8 / 16384
    private const val ARC_2 = 3 * ECCENTRICITY_SQUARED / 8 + 15 * ECCENTRICITY_4 / 32 +
        525 * ECCENTRICITY_6 / 1024 + 2205 * ECCENTRICITY_8 / 4096
    private const val ARC_4 = 15 * ECCENTRICITY_4 / 256 + 105 * ECCENTRICITY_6 / 1024 +
        2205 * ECCENTRICITY_8 / 16384
    private const val ARC_6 = 35 * ECCENTRICITY_6 / 3072 + 315 * ECCENTRICITY_8 / 12288
    private const val ARC_8 = 315 * ECCENTRICITY_8 / 131072

    private const val POLE = PI / 2

    /**
     * Bunun altında kalan meridyen payı doğu-batı sayılır. Sınır kılı kırk
     * yarıyor: 10⁻⁸'de iki enlem arasında en fazla yirmi santimetre kalıyor
     * (ekvatorda; kutba doğru daha az), yani hangi kola düşerse düşsün sonuç
     * metrenin altında aynı.
     */
    private const val EAST_WEST_LIMIT = 1e-8
}
