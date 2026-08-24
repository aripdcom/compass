package com.aripd.compass

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowInsets
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

    /** Ekranda duran diyalog; etkinlik yıkılırken kapatılmalı. */
    private var dialog: AlertDialog? = null

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

        // Android 15'ten itibaren pencere sistem çubuklarının altına çiziliyor;
        // boşluğu kendimiz bırakmazsak ilk satır durum çubuğunun altında kalır.
        // R öncesinde içeriğe sıfır boşluk bildirildiği için bu yol zararsız.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            scroll.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }

        backRow()
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

    /**
     * Geri satırı. Tema `NoActionBar` olduğu için sistem yukarı okunu
     * göstermiyor; `parentActivityName` tanımlı olduğu hâlde görünür bir geri
     * yolu yoktu. Kendi paletiyle çizilen bir satır hem gece modunda doğru
     * renkte kalıyor hem de dokunma hedefi 48dp'yi tutuyor.
     */
    private fun backRow() {
        column.addView(TextView(this).apply {
            text = getString(R.string.settings_back, getString(R.string.app_name))
            setTextColor(palette.textDim)
            textSize = 16f
            minHeight = dp(48)
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        })
    }

    /** Dokununca bir iş yapan satır; anahtar yok, okunacak düğüm satırın kendisi. */
    private fun actionRow(title: String, summary: String?, onClick: () -> Unit) {
        val row = rowContainer()
        val labels = labelColumn(title, summary)
        labels.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        row.isFocusable = true
        row.contentDescription = if (summary == null) title else "$title. $summary"
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.setOnClickListener { onClick() }
        column.addView(row)
    }

    /**
     * Diyaloğu gösterir ve izler. Etkinlik yıkılırken (döndürme, gece moduna
     * geçince gelen `recreate()`) açık kalan diyalog pencereyi sızdırıp
     * logcat'e `WindowLeaked` düşürüyordu.
     */
    private fun show(builder: AlertDialog.Builder) {
        dialog?.dismiss()
        dialog = builder.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        dialog?.dismiss()
        dialog = null
    }

    /** Dokunulamayan bilgi satırı. */
    private fun infoRow(title: String, value: String) {
        val row = rowContainer()
        val labels = labelColumn(title, value)
        labels.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        row.isFocusable = true
        row.contentDescription = "$title. $value"
        row.isClickable = false
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        column.addView(row)
    }

    /** Dokununca tarayıcıda açılan satır. Tarayıcı yoksa sessizce hiçbir şey olmaz. */
    private fun linkRow(title: String, value: String, url: String) {
        val row = rowContainer()
        val labels = labelColumn(title, value)
        labels.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        row.isFocusable = true
        row.contentDescription = "$title. $value"
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: ActivityNotFoundException) {
            }
        }
        column.addView(row)
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
        val title = getString(titleRes)
        val summary = if (summaryRes == 0) null else getString(summaryRes)
        val labels = labelColumn(title, summary)
        val toggle = Switch(this).apply {
            isChecked = prefs().getBoolean(key, default)
            // Ekran okuyucu satırı tek durakta okusun: yazılar anahtarın
            // açıklamasına taşınır, kendileri erişilebilirlik ağacından çıkar.
            // Aksi hâlde her satır üç ayrı durak oluyordu.
            contentDescription = if (summary == null) title else "$title. $summary"
            setOnCheckedChangeListener { _, checked ->
                prefs().edit().putBoolean(key, checked).apply()
                onChange(checked)
            }
        }
        labels.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(toggle)
        row.setOnClickListener { toggle.toggle() }
        column.addView(row)
    }

    private fun choiceRow(titleRes: Int, key: String, default: Int, optionRes: IntArray) {
        val options = optionRes.map { getString(it) }.toTypedArray()
        // Kayıtlı değer sınırlanarak okunur: daha yeni bir sürümden geri
        // yüklenen yedek listede olmayan bir seçenek taşıyabilir ve korumasız
        // indeks ayarlar ekranını daha açılışta çökertirdi. Ana ekran aynı
        // değeri zaten sınırlayarak okuyor (bkz. applySettings'teki smoothing).
        fun selected() = prefs().getInt(key, default).coerceIn(0, options.lastIndex)
        val row = rowContainer()
        val labels = labelColumn(getString(titleRes), options[selected()])
        labels.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        // Seçim satırında anahtar yok; okunacak düğüm satırın kendisi olur.
        row.isFocusable = true
        row.contentDescription = "${getString(titleRes)}. ${options[selected()]}"
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.setOnClickListener {
            show(
                AlertDialog.Builder(this)
                    .setTitle(getString(titleRes))
                    .setSingleChoiceItems(options, selected()) { shown, which ->
                        prefs().edit().putInt(key, which).apply()
                        (labels.getChildAt(1) as TextView).text = options[which]
                        row.contentDescription = "${getString(titleRes)}. ${options[which]}"
                        shown.dismiss()
                    }
            )
        }
        column.addView(row)
    }

    private fun rowContainer(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        // Dokunma alanı en az 48dp: küçük hedefler el titremesinde ıskalanıyor.
        minimumHeight = dp(48)
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

    private fun prefs() = getSharedPreferences("compass", Context.MODE_PRIVATE)

    private companion object {
        const val SOURCE_URL = "https://github.com/aripdcem/compass"
        const val SOURCE_LABEL = "github.com/aripdcem/compass"
    }
}
