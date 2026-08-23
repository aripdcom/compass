package com.aripd.compass

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

    /** Sıfır meridyeni ve ekvator geçerli koordinattır, "yok" değil. */
    @Test
    fun `sifir gecerli koordinattir`() {
        assertPoint(0.0, 0.0, "0.0, 0.0")
    }
}
