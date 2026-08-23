package com.cem.pusula

/**
 * Kadranda yönü gösterilebilen sabit noktalar.
 *
 * Kâbe'nin etiketi "Kıble", çünkü Türkçede o yönün adı budur; diğerleri kendi
 * adlarıyla anılır. Koordinatlar yapıların kendisine aittir, şehir merkezine
 * değil — birkaç yüz metrelik fark binlerce kilometre öteden bakınca yön olarak
 * saniyeler mertebesinde kalır ama doğrusunu yazmamak için de sebep yok.
 */
data class Place(
    val prefKey: String,
    val labelRes: Int,
    val nameRes: Int,
    val latitude: Double,
    val longitude: Double,
    val defaultVisible: Boolean
)

object Places {
    val ALL = listOf(
        // Anahtar eskiden beri "showQibla"; adı değiştirmek kullanıcının
        // ayarını sıfırlardı, o yüzden olduğu gibi bırakıldı.
        Place("showQibla", R.string.place_kaaba_label, R.string.place_kaaba, 21.4224779, 39.8251832, true),
        Place("showAqsa", R.string.place_aqsa_label, R.string.place_aqsa, 31.7767780, 35.2356630, false),
        Place("showVatican", R.string.place_vatican_label, R.string.place_vatican, 41.9021900, 12.4539300, false)
    )
}

/** Kadrana çizilecek tek bir yazılı işaret. */
data class PlaceMark(val label: String, val bearing: Float)
