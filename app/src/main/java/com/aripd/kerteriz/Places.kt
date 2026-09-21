package com.aripd.kerteriz

/**
 * Kadranda yönü gösterilebilen sabit noktalar.
 *
 * Hiçbiri varsayılan olarak açık değil. Kadranın kenarında yazılar için üç
 * kademe var (`CompassView.RIM_RADII`) ve manyetik kuzey, güneş, ay ile
 * kaydedilen noktalar da aynı halkayı paylaşıyor; hepsini birden açmak
 * yazıları üst üste bindirir. Liste bu yüzden "seç" listesi, "kapat" listesi
 * değil.
 *
 * Koordinatlar yapıların kendisine aittir, şehir merkezine değil. Kaynağın
 * verdiği hassasiyetin ötesine geçilmiyor: Cape Horn dakika mertebesinde
 * yayımlanıyor (ve zaten bir burun, nokta değil), Point Nemo 0,1' ile
 * hesaplanmış bir nokta — ikisine de uydurma ondalık eklenmedi.
 */
data class Place(
    val prefKey: String,
    val labelRes: Int,
    val nameRes: Int,
    val group: PlaceGroup,
    val latitude: Double,
    val longitude: Double
)

/** Seçim ekranındaki başlıklar. Sıra, listedeki sıradır. */
enum class PlaceGroup(val titleRes: Int) {
    NAVIGATION(R.string.places_group_navigation),
    MARITIME(R.string.places_group_maritime),
    HERITAGE(R.string.places_group_heritage)
}

object Places {

    /**
     * Hiçbiri açık gelmez. Kadranı neyin dolduracağına kullanıcı karar verir;
     * uygulamanın bir yönü öne çıkarması için sebep yok.
     */
    const val DEFAULT_VISIBLE = false

    /** Kadranın yazıları okunur taşıdığı üst sınır; seçim ekranı bunu söyler. */
    const val COMFORTABLE = 3

    val ALL = listOf(
        // Airy Geçiş Dairesi: *tarihî* başlangıç meridyeni. GPS'in sıfır boylamı
        // (IERS Referans Meridyeni) bunun ~102 m doğusundan geçer, o yüzden
        // etiket "0° boylam" demiyor — diyen bir uygulama yanlış söylemiş olurdu.
        // Yön olarak 102 m binlerce kilometre öteden görünmez.
        Place("showGreenwich", R.string.place_greenwich_label, R.string.place_greenwich,
            PlaceGroup.NAVIGATION, 51.4778111, -0.0014750),
        // Okyanusun karadan en uzak noktası; en yakın kara 2688 km.
        Place("showNemo", R.string.place_nemo_label, R.string.place_nemo,
            PlaceGroup.NAVIGATION, -48.876667, -123.393333),

        Place("showHorn", R.string.place_horn_label, R.string.place_horn,
            PlaceGroup.MARITIME, -55.9667, -67.2667),
        // Afrika'nın gerçek güney ucu ve Atlantik/Hint sınırının resmî yeri;
        // Ümit Burnu değil, o daha kuzeybatıda kalır.
        Place("showAgulhas", R.string.place_agulhas_label, R.string.place_agulhas,
            PlaceGroup.MARITIME, -34.832778, 20.003333),

        // Anahtar eskiden beri "showQibla"; adı değiştirmek kullanıcının
        // ayarını sıfırlardı, o yüzden olduğu gibi bırakıldı.
        Place("showQibla", R.string.place_kaaba_label, R.string.place_kaaba,
            PlaceGroup.HERITAGE, 21.4224779, 39.8251832),
        Place("showAqsa", R.string.place_aqsa_label, R.string.place_aqsa,
            PlaceGroup.HERITAGE, 31.7767780, 35.2356630),
        Place("showVatican", R.string.place_vatican_label, R.string.place_vatican,
            PlaceGroup.HERITAGE, 41.9021900, 12.4539300),
        Place("showBodhGaya", R.string.place_bodhgaya_label, R.string.place_bodhgaya,
            PlaceGroup.HERITAGE, 24.695102, 84.991275),
        Place("showAmritsar", R.string.place_amritsar_label, R.string.place_amritsar,
            PlaceGroup.HERITAGE, 31.620132, 74.876091),
        Place("showVaranasi", R.string.place_varanasi_label, R.string.place_varanasi,
            PlaceGroup.HERITAGE, 25.3107750, 83.0106139),
        Place("showIse", R.string.place_ise_label, R.string.place_ise,
            PlaceGroup.HERITAGE, 34.45500, 136.72583)
    )
}

/** Kadrana çizilecek tek bir yazılı işaret. */
data class PlaceMark(val label: String, val bearing: Float)

/**
 * Kadranın kenarına yerleşecek bir işaret: yönü, açısal genişliği ve türü.
 *
 * Değişmez değil, çünkü kare başına yeniden kuruluyor: `CompassView` bir havuz
 * tutup aynı nesneleri yeniden dolduruyor. Saniyede yirmi kez birkaç nesne
 * ayırmak yerine alanları üzerine yazmak çöp üretmiyor.
 */
internal class RimItem : RimLayout.Placeable {
    override var bearing = 0f
    override var halfWidth = 0f
    var kind = 0
    var label: String? = null

    fun set(bearing: Float, halfWidth: Float, kind: Int, label: String?) {
        this.bearing = bearing
        this.halfWidth = halfWidth
        this.kind = kind
        this.label = label
    }
}

internal const val KIND_MAGNETIC = 0
internal const val KIND_PLACE = 1
internal const val KIND_SUN = 2
internal const val KIND_MOON = 3
internal const val KIND_WAYPOINT = 4
