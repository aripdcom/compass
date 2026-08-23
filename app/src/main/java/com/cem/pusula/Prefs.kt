package com.cem.pusula

/**
 * Ayar anahtarları ve varsayılanları. Ana ekran ile ayarlar ekranı aynı
 * `SharedPreferences` dosyasını paylaştığı için anahtarların tek bir yerde
 * durması şart; yoksa iki taraf sessizce farklı anahtar yazardı.
 */
object Prefs {

    const val KEY_NIGHT = "night"
    const val KEY_UNIT = "unit"
    const val KEY_KEEP_SCREEN = "keepScreen"
    const val KEY_TRUE_NORTH = "trueNorth"
    const val KEY_SMOOTHING = "smoothing"
    const val KEY_VIBRATE = "vibrate"
    const val KEY_SHOW_MAGNETIC = "showMagnetic"
    const val KEY_SHOW_LEVEL = "showLevel"
    const val KEY_SHOW_SUN = "showSun"
    const val KEY_SHOW_SUN_ARC = "showSunArc"
    const val KEY_SHOW_MOON = "showMoon"

    const val DEFAULT_NIGHT = false
    const val DEFAULT_KEEP_SCREEN = true
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
     * Yumuşatma katsayıları: küçük değer sakin ama geç, büyük değer çevik ama
     * oynak. Ortadaki, uygulamanın başından beri kullandığı değerdir.
     */
    val SMOOTHING_ALPHAS = floatArrayOf(0.06f, 0.12f, 0.25f)
    const val DEFAULT_SMOOTHING = 1

    /** NATO mili: tam çember 6400 mil. Topçuluk ve harita işlerinde kullanılır. */
    const val MILS_PER_CIRCLE = 6400f
}
