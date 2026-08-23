package com.aripd.compass

/**
 * Ayar anahtarları ve varsayılanları. Ana ekran ile ayarlar ekranı aynı
 * `SharedPreferences` dosyasını paylaştığı için anahtarların tek bir yerde
 * durması şart; yoksa iki taraf sessizce farklı anahtar yazardı.
 */
object Prefs {

    const val KEY_NIGHT = "night"
    const val KEY_NIGHT_AUTO = "nightAuto"
    const val KEY_UNIT = "unit"
    const val KEY_KEEP_SCREEN = "keepScreen"
    const val KEY_FULLSCREEN = "fullscreen"
    const val KEY_TRUE_NORTH = "trueNorth"
    const val KEY_SMOOTHING = "smoothing"
    const val KEY_VIBRATE = "vibrate"
    const val KEY_SHOW_MAGNETIC = "showMagnetic"
    const val KEY_SHOW_LEVEL = "showLevel"
    const val KEY_SHOW_SUN = "showSun"
    const val KEY_SHOW_SUN_ARC = "showSunArc"
    const val KEY_SHOW_MOON = "showMoon"

    const val DEFAULT_NIGHT = false

    /**
     * Varsayılan kapalı: ekranın kendiliğinden kırmızıya dönmesi, beklemeyen
     * biri için bir arıza gibi görünür. İsteyen açar.
     */
    const val DEFAULT_NIGHT_AUTO = false

    /**
     * Gece modunun açıldığı güneş yüksekliği: sivil alacakaranlığın sonu.
     * Güneşin batması tek başına karanlık demek değil — ondan sonra yirmi
     * dakika kadar okumaya yetecek ışık kalır. -6°'de doğal ışık biter ve
     * göz karanlığa uyum sağlamaya başlar; kırmızıya geçmenin anı budur.
     */
    const val NIGHT_SUN_ELEVATION = -6f
    const val DEFAULT_KEEP_SCREEN = true
    const val DEFAULT_FULLSCREEN = true
    const val DEFAULT_TRUE_NORTH = true
    const val DEFAULT_VIBRATE = true
    const val DEFAULT_SHOW_MAGNETIC = true
    const val DEFAULT_SHOW_LEVEL = true
    const val DEFAULT_SHOW_SUN = true
    const val DEFAULT_SHOW_SUN_ARC = true
    const val DEFAULT_SHOW_MOON = true

    const val UNIT_DEGREE = 0
    const val UNIT_MIL = 1
    const val DEFAULT_UNIT = UNIT_DEGREE

    /**
     * Yumuşatmanın zaman sabitleri (saniye). Katsayı yerine süre tutuluyor,
     * çünkü katsayı örnekleme hızına bağlıdır: aynı 0,12 değeri 50 Hz'de 0,17
     * saniyelik, 16 Hz'de 0,5 saniyelik gecikme demektir. Süreyle ifade edilince
     * hız değişse de ibrenin hissi aynı kalır.
     *
     * Ortadaki, uygulamanın başından beri kullandığı 0,12 katsayısının 50 Hz'deki
     * karşılığıdır.
     */
    val SMOOTHING_TIME_CONSTANTS = floatArrayOf(0.35f, 0.17f, 0.08f)
    const val DEFAULT_SMOOTHING = 1

    /** NATO mili: tam çember 6400 mil. Topçuluk ve harita işlerinde kullanılır. */
    const val MILS_PER_CIRCLE = 6400f
}
