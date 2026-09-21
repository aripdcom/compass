package com.aripd.kerteriz

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle

/**
 * Ayarlar. İskelet ve satır kurucuları `RowsActivity`'de; burada yalnızca
 * ekranın içeriği var.
 *
 * Bütün değerler ana ekranla aynı `SharedPreferences` dosyasında durur; ana
 * ekran `onResume`'da yeniden okuyup uygular, o yüzden burada bir "kaydet"
 * adımı yok.
 */
class SettingsActivity : RowsActivity() {

    /** Sabit yerler satırı: özeti seçim ekranından dönünce tazelenir. */
    private var placesRow: Row? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        frame()

        backRow(getString(R.string.app_name))
        header(getString(R.string.settings_section_display))
        switchRow(
            R.string.settings_night, R.string.settings_night_summary,
            Prefs.KEY_NIGHT, Prefs.DEFAULT_NIGHT
        ) {
            // Elle seçim otomatiği kapatır: aksi hâlde anahtar bir sonraki güneş
            // hesabında kendiliğinden geri dönüp arıza gibi görünürdü.
            prefs().edit().putBoolean(Prefs.KEY_NIGHT_AUTO, false).apply()
            recreate()   // palet değişti, ekranı yeniden kur
        }
        // Burada `recreate()` yok: karar güneşin yerine bağlı ve o bilgi pusula
        // ekranında. Palet, oraya dönüldüğünde yerine oturur.
        switchRow(
            R.string.settings_night_auto, R.string.settings_night_auto_summary,
            Prefs.KEY_NIGHT_AUTO, Prefs.DEFAULT_NIGHT_AUTO
        )
        choiceRow(
            R.string.settings_unit, Prefs.KEY_UNIT, Prefs.DEFAULT_UNIT,
            intArrayOf(R.string.settings_unit_degree, R.string.settings_unit_mil)
        )
        switchRow(
            R.string.settings_keep_screen, R.string.settings_keep_screen_summary,
            Prefs.KEY_KEEP_SCREEN, Prefs.DEFAULT_KEEP_SCREEN
        )
        switchRow(
            R.string.settings_fullscreen, R.string.settings_fullscreen_summary,
            Prefs.KEY_FULLSCREEN, Prefs.DEFAULT_FULLSCREEN
        )

        header(getString(R.string.settings_section_compass))
        switchRow(
            R.string.settings_true_north, R.string.settings_true_north_summary,
            Prefs.KEY_TRUE_NORTH, Prefs.DEFAULT_TRUE_NORTH
        )
        choiceRow(
            R.string.settings_smoothing, Prefs.KEY_SMOOTHING, Prefs.DEFAULT_SMOOTHING,
            intArrayOf(
                R.string.settings_smoothing_calm,
                R.string.settings_smoothing_balanced,
                R.string.settings_smoothing_quick
            )
        )
        switchRow(
            R.string.settings_vibrate, R.string.settings_vibrate_summary,
            Prefs.KEY_VIBRATE, Prefs.DEFAULT_VIBRATE
        )

        header(getString(R.string.settings_section_marks))
        switchRow(
            R.string.settings_show_magnetic, R.string.settings_show_magnetic_summary,
            Prefs.KEY_SHOW_MAGNETIC, Prefs.DEFAULT_SHOW_MAGNETIC
        )
        switchRow(
            R.string.settings_show_level, R.string.settings_show_level_summary,
            Prefs.KEY_SHOW_LEVEL, Prefs.DEFAULT_SHOW_LEVEL
        )
        // On bir yer için on bir anahtar satırı listeyi ekrandan taşırıyordu ve
        // liste ancak uzayacak. Tek satır, arkasında kendi ekranı.
        placesRow = actionRow(getString(R.string.settings_places), placesSummary()) {
            startActivity(Intent(this, PlacesActivity::class.java))
        }
        switchRow(R.string.settings_show_sun, 0, Prefs.KEY_SHOW_SUN, Prefs.DEFAULT_SHOW_SUN)
        switchRow(
            R.string.settings_show_sun_arc, R.string.settings_show_sun_arc_summary,
            Prefs.KEY_SHOW_SUN_ARC, Prefs.DEFAULT_SHOW_SUN_ARC
        )
        switchRow(
            R.string.settings_show_moon, R.string.settings_show_moon_summary,
            Prefs.KEY_SHOW_MOON, Prefs.DEFAULT_SHOW_MOON
        )
        note(getString(R.string.settings_marks_note))

        header(getString(R.string.settings_section_about))
        actionRow(
            getString(R.string.settings_gestures),
            getString(R.string.settings_gestures_summary)
        ) {
            show(
                AlertDialog.Builder(this)
                    .setTitle(R.string.settings_gestures)
                    .setMessage(R.string.settings_gestures_body)
                    .setPositiveButton(android.R.string.ok, null)
            )
        }
        infoRow(getString(R.string.settings_version), versionLabel())
        linkRow(getString(R.string.settings_source), SOURCE_LABEL, SOURCE_URL)
        infoRow(getString(R.string.settings_license), getString(R.string.settings_license_value))
        note(getString(R.string.settings_privacy_note))
    }

    /** Seçim ekranından dönülüyor olabilir; satırın özeti oradaki seçimi yansıtmalı. */
    override fun onResume() {
        super.onResume()
        placesRow?.summary(placesSummary())
    }

    /**
     * Satırın altına seçilenlerin kadrandaki etiketleri yazılır, sayısı değil:
     * "2 seçili" için kullanıcının ekranı açması gerekirdi, "Kıble, Greenwich"
     * için gerekmiyor. Ayrıca sayı çoğul eki isteyen dillerde ayrı bir iş
     * açardı.
     */
    private fun placesSummary(): String {
        val chosen = Places.ALL
            .filter { prefs().getBoolean(it.prefKey, Places.DEFAULT_VISIBLE) }
            .map { getString(it.labelRes) }
        // Ayırıcı kodda: yirmi sekiz dilin hepsi virgül kullanıyor (Latin,
        // Kiril, Yunan), yani çevrilecek bir yanı yok. Arapça gibi başka bir
        // ayırıcı kullanan bir dil eklenirse buraya dönülmeli.
        return if (chosen.isEmpty()) getString(R.string.settings_places_none)
        else chosen.joinToString(", ")
    }

    /**
     * Sürüm paketten okunur, elle yazılmış bir sabitten değil: sürüm yükseltince
     * burayı güncellemeyi unutmak diye bir şey olmasın.
     */
    private fun versionLabel(): String {
        val info = packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return getString(R.string.settings_version_format, info.versionName ?: "", code)
    }

    private companion object {
        const val SOURCE_URL = "https://github.com/aripdcom/kerteriz"
        const val SOURCE_LABEL = "github.com/aripdcom/kerteriz"
    }
}
