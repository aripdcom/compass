package com.aripd.kerteriz

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * Ayar ekranlarının ortak iskeleti: kaydırılan tek sütun, palet ve satır
 * kurucuları.
 *
 * Arayüz elle kuruluyor, çünkü projenin hiç dış bağımlılığı yok — dolayısıyla
 * `PreferenceFragmentCompat` de yok. İki ekran (Ayarlar ve Sabit yerler) aynı
 * satırları kullandığı için ortak kısım burada duruyor; ikinci ekran için yüz
 * elli satır kopyalamak, er geç ikisinin ayrışması demekti.
 *
 * Alt sınıf `onCreate` içinde önce `frame()` çağırır, sonra `column`'a satır
 * ekler.
 */
abstract class RowsActivity : Activity() {

    protected lateinit var palette: Palette
    protected lateinit var column: LinearLayout

    /** Ekranda duran diyalog; etkinlik yıkılırken kapatılmalı. */
    private var dialog: AlertDialog? = null

    /** Kaydırılan sütunu kurar ve ekrana yerleştirir. */
    protected fun frame() {
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
    }

    /**
     * Geri satırı. Tema `NoActionBar` olduğu için sistem yukarı okunu
     * göstermiyor; `parentActivityName` tanımlı olduğu hâlde görünür bir geri
     * yolu yoktu. Kendi paletiyle çizilen bir satır hem gece modunda doğru
     * renkte kalıyor hem de dokunma hedefi 48dp'yi tutuyor.
     */
    protected fun backRow(target: String) {
        column.addView(TextView(this).apply {
            text = getString(R.string.settings_back, target)
            setTextColor(palette.textDim)
            textSize = 16f
            minHeight = dp(48)
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            setOnClickListener { finish() }
        })
    }

    /**
     * Özeti sonradan değişebilen satır.
     *
     * `getChildAt(0)` diye ağaca elle dalmak yerine tutamağın kendisi
     * veriliyor: satırın iç yapısı değişince orada sessizce yanlış görünüme
     * `TextView` diye bakılırdı.
     */
    protected class Row(
        private val view: LinearLayout,
        private val summaryView: TextView,
        private val title: String
    ) {
        fun summary(text: String) {
            summaryView.text = text
            summaryView.visibility = View.VISIBLE
            view.contentDescription = "$title. $text"
        }
    }

    /** Dokununca bir iş yapan satır; anahtar yok, okunacak düğüm satırın kendisi. */
    protected fun actionRow(title: String, summary: String?, onClick: () -> Unit): Row {
        val row = rowContainer()
        val labels = labelColumn(title, summary)
        labels.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        row.isFocusable = true
        row.contentDescription = if (summary == null) title else "$title. $summary"
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.setOnClickListener { onClick() }
        column.addView(row)
        return Row(row, labels.getChildAt(1) as TextView, title)
    }

    /**
     * Diyaloğu gösterir ve izler. Etkinlik yıkılırken (döndürme, gece moduna
     * geçince gelen `recreate()`) açık kalan diyalog pencereyi sızdırıp
     * logcat'e `WindowLeaked` düşürüyordu.
     */
    protected fun show(builder: AlertDialog.Builder) {
        dialog?.dismiss()
        dialog = builder.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        dialog?.dismiss()
        dialog = null
    }

    /** Dokunulamayan bilgi satırı. */
    protected fun infoRow(title: String, value: String) {
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
    protected fun linkRow(title: String, value: String, url: String) {
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

    /** Bölümün altına açıklama satırı. Görünürlüğü değişecekse dönen görünüm tutulur. */
    protected fun note(text: String): TextView {
        val view = TextView(this).apply {
            this.text = text
            setTextColor(palette.hint)
            textSize = 13f
            setPadding(0, dp(10), 0, 0)
        }
        column.addView(view)
        return view
    }

    protected fun header(text: String) {
        column.addView(TextView(this).apply {
            this.text = text
            setTextColor(palette.text)
            textSize = 13f
            setPadding(0, dp(20), 0, dp(6))
            letterSpacing = 0.08f
        })
    }

    protected fun switchRow(
        titleRes: Int,
        summaryRes: Int,
        key: String,
        default: Boolean,
        onChange: (Boolean) -> Unit = {}
    ) = switchRow(
        getString(titleRes),
        if (summaryRes == 0) null else getString(summaryRes),
        key, default, onChange
    )

    protected fun switchRow(
        title: String,
        summary: String?,
        key: String,
        default: Boolean,
        onChange: (Boolean) -> Unit = {}
    ) {
        val row = rowContainer()
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

    protected fun choiceRow(titleRes: Int, key: String, default: Int, optionRes: IntArray) {
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

    protected fun rowContainer(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        // Dokunma alanı en az 48dp: küçük hedefler el titremesinde ıskalanıyor.
        minimumHeight = dp(48)
        setPadding(0, dp(12), 0, dp(12))
        isClickable = true
    }

    /** Başlık ve altındaki açıklama; açıklama yoksa yalnızca başlık. */
    protected fun labelColumn(title: String, summary: String?): LinearLayout =
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

    protected fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    protected fun prefs() = getSharedPreferences("kerteriz", Context.MODE_PRIVATE)
}
