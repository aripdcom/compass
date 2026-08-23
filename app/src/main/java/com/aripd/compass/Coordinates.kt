package com.aripd.compass

/**
 * Metinden koordinat okur.
 *
 * Uygulama konum paylaşabiliyordu ama alamıyordu; oysa paylaşmanın karşılığı
 * almaktır. Başkasının gönderdiği "41.0, 29.0", bir `geo:` bağlantısı ya da bir
 * harita adresi doğrudan nokta olarak kaydedilebilsin diye burası var.
 *
 * Android'e hiç dokunmaz — `Uri` bile kullanılmaz, çünkü o JVM testlerinde
 * çalışmayan bir Android sınıfı. Ayrıştırma elle yapılıyor ve sınanabiliyor.
 */
object Coordinates {

    /** Enlem ve boylam; sırasıyla [-90, 90] ve [-180, 180]. */
    data class Point(val latitude: Double, val longitude: Double)

    /**
     * Metnin içinden ilk geçerli koordinat çiftini çıkarır, bulamazsa null.
     *
     * Tanınan biçimler:
     * - Ondalık çift: `41.0, 29.0` — ayraç virgül ya da boşluk
     * - `geo:41.0,29.0` ve `geo:0,0?q=41.0,29.0(Ad)`
     * - Sorgu değiştirgeleri: `?q=`, `mlat=`/`mlon=`, `ll=`, `daddr=`
     * - OpenStreetMap ve Google Maps bağlantılarındaki `#map=17/41.0/29.0`
     *   ve `@41.0,29.0,17z` parçaları
     * - Derece-dakika-saniye: `41°00'30"K 29°08'12"D`
     *
     * Bağlantıların içindeki koordinat, adresin geri kalanındaki sayılardan
     * (yakınlaştırma düzeyi, kimlikler) ayırt edilebilsin diye önce adlandırılmış
     * değiştirgeler denenir; ancak onlar yoksa serbest sayı taraması yapılır.
     */
    fun parse(text: String?): Point? {
        if (text.isNullOrBlank()) return null
        return fromDms(text)
            ?: fromNamedParameters(text)
            ?: fromPathSegments(text)
            ?: fromPlainPair(text)
    }

    /**
     * Koordinatın yanındaki ad, varsa. `geo:` adreslerinde parantez içinde,
     * harita bağlantılarında `q=` değerinin koordinattan sonraki parçasında
     * gelir. Bulunamazsa null; çağıran kendi varsayılan adını kullanır.
     */
    fun label(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val parenthesised = LABEL.find(text)?.groupValues?.get(1)?.trim()
        if (!parenthesised.isNullOrEmpty()) return decode(parenthesised)
        return null
    }

    /** Adres kodlamasındaki `%20` ve `+` gibi kaçışları çözer. */
    private fun decode(value: String): String {
        val plain = value.replace('+', ' ')
        return PERCENT.replace(plain) { match ->
            match.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: match.value
        }
    }

    /** `mlat=41.0&mlon=29.0`, `q=41.0,29.0`, `ll=`, `daddr=` gibi değiştirgeler. */
    private fun fromNamedParameters(text: String): Point? {
        latitudeParameter(text)?.let { latitude ->
            longitudeParameter(text)?.let { longitude ->
                return point(latitude, longitude)
            }
        }
        for (key in PAIR_KEYS) {
            val value = parameter(text, key) ?: continue
            fromPlainPair(value)?.let { return it }
        }
        return null
    }

    /** `#map=17/41.0/29.0` ve `@41.0,29.0,17z` — yakınlaştırma sayısı atılır. */
    private fun fromPathSegments(text: String): Point? {
        MAP_FRAGMENT.find(text)?.let { match ->
            return point(match.groupValues[1].toDoubleOrNull(), match.groupValues[2].toDoubleOrNull())
        }
        AT_SEGMENT.find(text)?.let { match ->
            return point(match.groupValues[1].toDoubleOrNull(), match.groupValues[2].toDoubleOrNull())
        }
        return null
    }

    /**
     * İlk iki sayıyı enlem-boylam olarak okur.
     *
     * `geo:` adreslerindeki `0,0?q=...` kalıbı bilerek elenir: oradaki sıfırlar
     * "konum belirtilmedi" demektir, gerçek koordinat sorgunun içindedir.
     */
    private fun fromPlainPair(text: String): Point? {
        val cleaned = GEO_NULL_PREFIX.replace(text, "")
        val numbers = NUMBER.findAll(cleaned).map { it.value }.take(2).toList()
        if (numbers.size < 2) return null
        return point(numbers[0].toDoubleOrNull(), numbers[1].toDoubleOrNull())
    }

    /** `41°00'30"K 29°08'12"D` — yarımküre harfi yönü belirler. */
    private fun fromDms(text: String): Point? {
        val matches = DMS.findAll(text).take(2).toList()
        if (matches.size < 2) return null
        val first = dmsValue(matches[0])
        val second = dmsValue(matches[1])
        val firstIsLatitude = matches[0].groupValues[4].uppercase() in LATITUDE_LETTERS
        return if (firstIsLatitude) point(first, second) else point(second, first)
    }

    private fun dmsValue(match: MatchResult): Double {
        val degrees = match.groupValues[1].toDouble()
        val minutes = match.groupValues[2].toDoubleOrNull() ?: 0.0
        val seconds = match.groupValues[3].toDoubleOrNull() ?: 0.0
        val magnitude = degrees + minutes / 60.0 + seconds / 3600.0
        return if (match.groupValues[4].uppercase() in NEGATIVE_LETTERS) -magnitude else magnitude
    }

    private fun latitudeParameter(text: String): Double? =
        LATITUDE_KEYS.firstNotNullOfOrNull { parameter(text, it)?.toDoubleOrNull() }

    private fun longitudeParameter(text: String): Double? =
        LONGITUDE_KEYS.firstNotNullOfOrNull { parameter(text, it)?.toDoubleOrNull() }

    private fun parameter(text: String, key: String): String? =
        Regex("""[?&#]$key=([^&#\s]+)""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)

    /** Aralık dışındaki değerler koordinat değildir; sessizce reddedilir. */
    private fun point(latitude: Double?, longitude: Double?): Point? {
        if (latitude == null || longitude == null) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return Point(latitude, longitude)
    }

    /**
     * Koordinat olabilecek sayı: en çok üç basamaklı tam kısım, isteğe bağlı
     * ondalık. Basamak sınırı bağlantı içindeki yılları ve kimlikleri eler;
     * ondalıksız olanın ardından rakam ya da nokta gelmemeli, yoksa "17z"nin
     * içinden 17 çıkardı.
     */
    private val NUMBER = Regex("""[-+]?\d{1,3}\.\d+|[-+]?\d{1,3}(?![\d.])""")

    private val GEO_NULL_PREFIX = Regex("""geo:0(?:\.0+)?,0(?:\.0+)?""", RegexOption.IGNORE_CASE)
    private val MAP_FRAGMENT = Regex("""map=\d+(?:\.\d+)?/(-?\d+\.\d+)/(-?\d+\.\d+)""", RegexOption.IGNORE_CASE)
    private val AT_SEGMENT = Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)""")
    /**
     * Derece-dakika-saniye; dakika ve saniye isteğe bağlı. Yarımküre harfi
     * zorunlu, çünkü işareti yalnızca o belirler.
     */
    private val DMS = Regex("""(\d{1,3})[°º]\s*(\d{1,2})?['′]?\s*([\d.]+)?["″]?\s*([NSEWKGDBnsewkgdb])""")

    private val LABEL = Regex("""\(([^)]{1,60})\)""")
    private val PERCENT = Regex("""%([0-9A-Fa-f]{2})""")

    private val LATITUDE_KEYS = listOf("mlat", "lat")
    private val LONGITUDE_KEYS = listOf("mlon", "lon", "lng")
    private val PAIR_KEYS = listOf("q", "ll", "daddr", "saddr", "query", "center")

    /**
     * Yarımküre harfleri yalnızca İngilizce ve Türkçe için tanınır: N/S/E/W ve
     * K/G/D/B. Diğer dillerin harfleri karışıyor — Almancada "O" doğu (Ost)
     * demekken İspanyolcada batı (Oeste) demek, ve yanlış tahmin sessizce yanlış
     * yarımküreye götürür. Tanımamak, yanlış tanımaktan iyidir.
     */
    private val LATITUDE_LETTERS = setOf("N", "S", "K", "G")

    /** Negatif yön harfleri: güney ve batı. */
    private val NEGATIVE_LETTERS = setOf("S", "W", "G", "B")
}
