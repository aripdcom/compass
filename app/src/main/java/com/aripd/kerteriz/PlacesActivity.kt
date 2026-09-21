package com.aripd.kerteriz

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout

/**
 * Kadranda gösterilecek sabit yerlerin seçildiği ekran.
 *
 * Ayarlarda on bir anahtar satırı olarak durmuyorlar, çünkü liste ancak
 * uzayacak ve ayarların ortasını yutuyordu. Burada kategorili, aranabilir ve
 * hepsi kapalı başlayan bir liste var.
 *
 * Ekran bir şeyi açıkça söylüyor: kadranın kenarında yazılar için üç kademe
 * var ve manyetik kuzey, güneş, ay ile kaydedilen noktalar da aynı halkayı
 * paylaşıyor. Üçten fazla yer seçmek yazıları üst üste bindirir — uyarı
 * seçim üçü geçince beliriyor. Engellenmiyor; kullanıcı ne yaptığını bilerek
 * dördüncüyü açabilmeli.
 */
class PlacesActivity : RowsActivity() {

    /** Listenin kendi kabı: arama değişince yalnızca burası yeniden kuruluyor. */
    private lateinit var list: LinearLayout
    private lateinit var crowded: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        frame()

        backRow(getString(R.string.settings_title))
        header(getString(R.string.places_title))
        searchField()
        note(getString(R.string.places_note))
        crowded = note(getString(R.string.places_crowded))

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(
            list,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        fill("")
        updateCrowded()
    }

    private fun searchField() {
        column.addView(EditText(this).apply {
            hint = getString(R.string.places_search)
            setSingleLine()
            setTextColor(palette.text)
            setHintTextColor(palette.hint)
            textSize = 16f
            minHeight = dp(48)
            // Alt çizgi tema vurgusunu kullanıyor; ekranın geri kalanı paletle
            // çiziliyor, gece modunda ikisi ayrışmasın.
            backgroundTintList = ColorStateList.valueOf(palette.hint)
            contentDescription = getString(R.string.places_search)
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = fill(s?.toString() ?: "")
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            })
        })
    }

    /**
     * Listeyi kurar. Satır kurucuları `column`'a yazdığı için `column` bu süre
     * boyunca listenin kabına bakıyor; aksi hâlde her tuş vuruşunda arama
     * kutusu da yeniden kurulur, odak ve klavye kaybolurdu.
     */
    private fun fill(query: String) {
        list.removeAllViews()
        val outer = column
        column = list
        try {
            val needle = PlaceSearch.fold(query)
            var shown = 0
            for (group in PlaceGroup.values()) {
                // Yerel ad `matches` olmamalı: aynı isimli üye fonksiyon
                // lambdanın içinden çağrılıyor ve gölgelenirdi.
                val found = Places.ALL.filter { it.group == group && matches(it, needle) }
                if (found.isEmpty()) continue
                header(getString(group.titleRes))
                for (place in found) {
                    switchRow(
                        getString(place.nameRes),
                        getString(R.string.places_dial_label, getString(place.labelRes)),
                        place.prefKey,
                        Places.DEFAULT_VISIBLE
                    ) { updateCrowded() }
                    shown++
                }
            }
            if (shown == 0) note(getString(R.string.places_no_match))
        } finally {
            column = outer
        }
    }

    private fun matches(place: Place, needle: String): Boolean =
        PlaceSearch.matches(getString(place.nameRes), getString(place.labelRes), needle)

    private fun updateCrowded() {
        val chosen = Places.ALL.count { prefs().getBoolean(it.prefKey, Places.DEFAULT_VISIBLE) }
        crowded.visibility = if (chosen > Places.COMFORTABLE) View.VISIBLE else View.GONE
    }
}
