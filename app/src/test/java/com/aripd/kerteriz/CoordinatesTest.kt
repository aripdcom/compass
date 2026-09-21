package com.aripd.kerteriz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Paylaşılan metinden koordinat okuma. */
class CoordinatesTest {

    private fun assertPoint(latitude: Double, longitude: Double, text: String) {
        val point = Coordinates.parse(text)
        assertNotNull("çözülemedi: $text", point)
        assertEquals(text, latitude, point!!.latitude, 1e-6)
        assertEquals(text, longitude, point.longitude, 1e-6)
    }

    /**
     * En önemlisi: uygulamanın kendi paylaştığı metin geri okunabilmeli.
     * `shareLocation` koordinatı, adı ve OpenStreetMap bağlantısını gönderiyor.
     */
    @Test
    fun `uygulamanin kendi paylasimi geri okunur`() {
        assertPoint(
            41.012345, 29.123456,
            "Nokta 1\n41.012345, 29.123456\n" +
                "https://www.openstreetmap.org/?mlat=41.012345&mlon=29.123456" +
                "#map=17/41.012345/29.123456"
        )
    }

    @Test
    fun `ondalik cift`() {
        assertPoint(41.0, 29.0, "41.0, 29.0")
        assertPoint(41.0, 29.0, "41.0 29.0")
        assertPoint(-33.8688, 151.2093, "-33.8688, 151.2093")
        assertPoint(41.0, 29.0, "41, 29")
    }

    @Test
    fun `geo adresi`() {
        assertPoint(41.0, 29.0, "geo:41.0,29.0")
        assertPoint(41.0, 29.0, "geo:41.0,29.0?z=17")
    }

    /**
     * `geo:0,0?q=...` kalıbında baştaki sıfırlar "konum belirtilmedi" demektir;
     * gerçek koordinat sorgunun içindedir. Elenmezse her paylaşım Gine
     * Körfezi'ne düşerdi.
     */
    @Test
    fun `geo sifir onekli sorgu`() {
        assertPoint(41.0, 29.0, "geo:0,0?q=41.0,29.0")
        assertPoint(41.0, 29.0, "geo:0,0?q=41.0,29.0(Ev)")
        assertPoint(41.0, 29.0, "geo:0.0,0.0?q=41.0,29.0")
    }

    /** Harita bağlantıları: yakınlaştırma sayısı koordinat sanılmamalı. */
    @Test
    fun `harita baglantilari`() {
        assertPoint(41.0, 29.0, "https://www.google.com/maps/@41.0,29.0,17z")
        assertPoint(41.0, 29.0, "https://www.openstreetmap.org/#map=17/41.0/29.0")
        assertPoint(41.0, 29.0, "https://maps.google.com/?q=41.0,29.0")
        assertPoint(41.0, 29.0, "https://www.openstreetmap.org/?mlat=41.0&mlon=29.0")
    }

    @Test
    fun `derece dakika saniye`() {
        assertPoint(41.0084722, 29.1366667, "41°00'30.5\"N 29°08'12\"E")
        assertPoint(41.0084722, 29.1366667, "41°00'30.5\"K 29°08'12\"D")
        assertPoint(-41.0084722, -29.1366667, "41°00'30.5\"S 29°08'12\"W")
    }

    /**
     * Derece işareti olmadan da okunur. Cihazda denerken çıktı:
     * `41 00 30 K 29 08 12 D` serbest sayı taramasına düşüp ilk iki sayıyı
     * alıyor ve sessizce (41,0) veriyordu — Atlantik'te bir nokta.
     */
    @Test
    fun `derece isareti olmadan da okunur`() {
        assertPoint(41.0083333, 29.1366667, "41 00 30 K 29 08 12 D")
        assertPoint(41.0083333, 29.1366667, "41 00 30 N 29 08 12 E")
        assertPoint(41.0, 29.0, "41 K 29 D")
    }

    /** Saniyede ondalık ayraç virgül de olabilir (Türkçe yazım). */
    @Test
    fun `saniyede virgul kabul edilir`() {
        assertPoint(41.0084722, 29.1366667, "41°00'30,5\"K 29°08'12\"D")
    }

    /**
     * Gevşeyen kalıbın iki koruması. Derece bir sayının ortasından başlamamalı
     * ve yarımküre harfi bir kelimenin başı olmamalı; ikisi de yanlış ama
     * geçerli görünen koordinat üretirdi.
     */
    @Test
    fun `gevsek kalip yanlis eslesme uretmez`() {
        // Sayının ortasından: `40,98767° K` içinden `67° K` çıkmamalı.
        assertPoint(40.98767, 29.13664, "40.98767, 29.13664")
        // Kelimenin başı: `9,0 km` içindeki `k` yarımküre değil.
        assertNull(Coordinates.parse("Kamp 275 · 9 km"))
        // Ondalık çift hâlâ ondalık çift olarak okunmalı.
        assertPoint(41.0, 29.0, "41.0, 29.0")
    }

    /** Enlem önce yazılmak zorunda değil; harf hangisi olduğunu söyler. */
    @Test
    fun `derece dakika saniyede sira harften okunur`() {
        assertPoint(41.0, 29.0, "29°00'00\"E 41°00'00\"N")
    }

    /** Aralık dışındaki değerler koordinat değildir. */
    @Test
    fun `gecersiz deger reddedilir`() {
        assertNull(Coordinates.parse("91.0, 29.0"))
        assertNull(Coordinates.parse("41.0, 181.0"))
        assertNull(Coordinates.parse(null))
        assertNull(Coordinates.parse(""))
        assertNull(Coordinates.parse("merhaba"))
        assertNull(Coordinates.parse("41.0"))
    }

    /** `geo:` adresindeki parantezli ad kaydedilecek noktaya önerilir. */
    @Test
    fun `etiket okunur`() {
        assertEquals("Ev", Coordinates.label("geo:0,0?q=41.0,29.0(Ev)"))
        assertEquals("Kamp yeri", Coordinates.label("geo:0,0?q=41.0,29.0(Kamp%20yeri)"))
        assertEquals("Kamp yeri", Coordinates.label("geo:0,0?q=41.0,29.0(Kamp+yeri)"))
        assertNull(Coordinates.label("geo:41.0,29.0"))
        assertNull(Coordinates.label(null))
        assertNull(Coordinates.label(""))
    }

    /**
     * Uygulamanın kendi paylaşımında ad ilk satırdadır. Cihazda denerken çıktı:
     * koordinat geri okunuyordu ama ad kayboluyor ve nokta "Nokta 1" diye
     * kaydediliyordu.
     */
    @Test
    fun `ilk satirdaki ad okunur`() {
        assertEquals(
            "Kamp",
            Coordinates.label(
                "Kamp\n40.995000, 29.030000\n" +
                    "https://www.openstreetmap.org/?mlat=40.995&mlon=29.03"
            )
        )
    }

    /** İlk satır ad değilse alınmaz: koordinatın kendisi, adres ya da uzun metin. */
    @Test
    fun `ad olamayacak ilk satir alinmaz`() {
        assertNull(Coordinates.label("41.0, 29.0"))
        assertNull(Coordinates.label("https://maps.google.com/?q=41.0,29.0"))
        assertNull(Coordinates.label("geo:41.0,29.0\nbir şey"))
        assertNull(Coordinates.label("41°00'30\"K 29°08'12\"D"))
        assertNull(Coordinates.label("x".repeat(80) + "\n41.0, 29.0"))
    }

    /** Sıfır meridyeni ve ekvator geçerli koordinattır, "yok" değil. */
    @Test
    fun `sifir gecerli koordinattir`() {
        assertPoint(0.0, 0.0, "0.0, 0.0")
    }
}
