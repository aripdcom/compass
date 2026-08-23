package com.cem.pusula

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * Ayarlar. Arayüz elle kuruluyor: projenin hiç dış bağımlılığı yok, dolayısıyla
 * `PreferenceFragmentCompat` de yok. Satırlar tek tip olduğu için kodla üretmek
 * XML'den hem kısa hem de paleti uygulaması kolay.
 *
 * Bütün değerler ana ekranla aynı `SharedPreferences` dosyasında durur; ana ekran
 * `onResume`'da yeniden okuyup uygular, o yüzden burada bir "kaydet" adımı yok.
 */
class SettingsActivity : Activity() {

    private lateinit var palette: Palette
    private lateinit var column: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        palette = Palette.of(prefs().getBoolean(Prefs.KEY_NIGHT, false))

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(palette.background)
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(28))
        }
        scroll.addView(
            column,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        setContentView(scroll)

        header(getString(R.string.settings_section_display))
        switchRow(
            R.string.settings_night, R.string.settings_night_summary,
            Prefs.KEY_NIGHT, Prefs.DEFAULT_NIGHT
        ) { recreate() }   // palet değişti, ekranı yeniden kur
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
        Places.ALL.forEach { place ->
            switchRow(place.nameRes, 0, place.prefKey, place.defaultVisible)
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
    }

    /** Bölümün altına açıklama satırı. */
    private fun note(text: String) {
        column.addView(TextView(this).apply {
            this.text = text
            setTextColor(palette.hint)
            textSize = 13f
            setPadding(0, dp(10), 0, 0)
        })
    }

    private fun header(text: String) {
        column.addView(TextView(this).apply {
            this.text = text
            setTextColor(palette.text)
            textSize = 13f
            setPadding(0, dp(20), 0, dp(6))
            letterSpacing = 0.08f
        })
    }

    private fun switchRow(
        titleRes: Int,
        summaryRes: Int,
        key: String,
        default: Boolean,
        onChange: (Boolean) -> Unit = {}
    ) {
        val row = rowContainer()
        val labels = labelColumn(getString(titleRes), if (summaryRes == 0) null else getString(summaryRes))
        val toggle = Switch(this).apply {
            isChecked = prefs().getBoolean(key, default)
            setOnCheckedChangeListener { _, checked ->
                prefs().edit().putBoolean(key, checked).apply()
                onChange(checked)
            }
        }
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(toggle)
        row.setOnClickListener { toggle.toggle() }
        column.addView(row)
    }

    private fun choiceRow(titleRes: Int, key: String, default: Int, optionRes: IntArray) {
        val options = optionRes.map { getString(it) }.toTypedArray()
        val row = rowContainer()
        val labels = labelColumn(getString(titleRes), options[prefs().getInt(key, default)])
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(titleRes))
                .setSingleChoiceItems(options, prefs().getInt(key, default)) { dialog, which ->
                    prefs().edit().putInt(key, which).apply()
                    (labels.getChildAt(1) as TextView).text = options[which]
                    dialog.dismiss()
                }
                .show()
        }
        column.addView(row)
    }

    private fun rowContainer(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(12), 0, dp(12))
        isClickable = true
    }

    /** Başlık ve altındaki açıklama; açıklama yoksa yalnızca başlık. */
    private fun labelColumn(title: String, summary: String?): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = title
                setTextColor(palette.text)
                textSize = 16f
            })
            addView(TextView(context).apply {
                text = summary ?: ""
                setTextColor(palette.textDim)
                textSize = 13f
                visibility = if (summary == null) View.GONE else View.VISIBLE
            })
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun prefs() = getSharedPreferences("pusula", Context.MODE_PRIVATE)
}
