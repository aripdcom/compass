package com.aripd.compass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Açı ve yön hesapları. Referans değerler bağımsız hesapla doğrulanmıştır. */
class GeoTest {

    private val istanbulLat = 40.98770
    private val istanbulLon = 29.13660

    @Test
    fun `kutsal yerlerin yonleri`() {
        // Bu üç değer uygulamanın dışında, ayrı bir uygulamayla hesaplandı.
        assertEquals(152.0f, bearingFrom(21.4224779, 39.8251832), 0.2f)   // Kâbe
        assertEquals(150.1f, bearingFrom(31.7767780, 35.2356630), 0.2f)   // Mescid-i Aksa
        assertEquals(279.7f, bearingFrom(41.9021900, 12.4539300), 0.2f)   // Vatikan
    }

    /**
     * Kıble ile Mescid-i Aksa arasında Türkiye'den bakınca ~2° var. Kadranın
     * etiket birleştirme eşiği (6°) bu gerçeğe dayanıyor; fark büyürse eşik
     * anlamını yitirir.
     */
    @Test
    fun `kible ile aksa birbirine cok yakin`() {
        val kaaba = bearingFrom(21.4224779, 39.8251832)
        val aqsa = bearingFrom(31.7767780, 35.2356630)
        assertTrue(Geo.separation(kaaba, aqsa) < 3f)
    }

    @Test
    fun `ayni boylamda kuzeydeki nokta sifir derece`() {
        assertEquals(0f, bearingFrom(50.0, istanbulLon), 0.01f)
        assertEquals(180f, bearingFrom(10.0, istanbulLon), 0.01f)
    }

    @Test
    fun `ekvatorda doguya bakan nokta doksan derece`() {
        assertEquals(90f, Geo.bearing(0.0, 0.0, 0.0, 10.0), 0.01f)
        assertEquals(270f, Geo.bearing(0.0, 0.0, 0.0, -10.0), 0.01f)
    }

    @Test
    fun `normalize her acyi sifir uc yuz altmis arasina indirir`() {
        assertEquals(10f, Geo.normalize(370f), 0.001f)
        assertEquals(350f, Geo.normalize(-10f), 0.001f)
        assertEquals(0f, Geo.normalize(720f), 0.001f)
    }

    /** Sıfır geçişi: 350°'den 10°'ye dönmek 20° sağadır, 340° sola değil. */
    @Test
    fun `fark sifir gecisinde dogru yonu verir`() {
        assertEquals(20f, Geo.difference(350f, 10f), 0.001f)
        assertEquals(-20f, Geo.difference(10f, 350f), 0.001f)
        assertEquals(0f, Geo.difference(45f, 45f), 0.001f)
        // Tam yarım tur: iki yön de aynı dönüş, uygulama sola diyor.
        assertEquals(-180f, Geo.difference(0f, 180f), 0.001f)
        assertTrue(Geo.difference(0f, 179f) > 0f)
        assertTrue(Geo.difference(0f, 181f) < 0f)
    }

    /** Sayısal ortalama 359 ile 1 için 180 verirdi; doğrusu 0'dır. */
    @Test
    fun `ortalama yon vektoreldir`() {
        assertEquals(0f, Geo.meanBearing(listOf(359f, 1f)), 0.01f)
        assertEquals(151.05f, Geo.meanBearing(listOf(150.1f, 152.0f)), 0.01f)
    }

    @Test
    fun `mil donusumu`() {
        assertEquals(0, Geo.degreesToMils(0f))
        assertEquals(1600, Geo.degreesToMils(90f))
        assertEquals(3200, Geo.degreesToMils(180f))
        assertEquals(2702, Geo.degreesToMils(152f))     // kıble
        assertEquals(0, Geo.degreesToMils(360f))
        assertEquals(90f, Geo.milsToDegrees(1600f), 0.001f)
    }

    /** Manyetik çerçevede işaretler sapma kadar geri alınır, gerçekte alınmaz. */
    @Test
    fun `kadran cercevesi donusumu`() {
        assertEquals(152f, Geo.toDialFrame(152f, 6.4f, useTrueNorth = true), 0.001f)
        assertEquals(145.6f, Geo.toDialFrame(152f, 6.4f, useTrueNorth = false), 0.001f)
        // sapma bilinmiyorsa manyetik çerçevede de değişiklik olmaz
        assertEquals(152f, Geo.toDialFrame(152f, null, useTrueNorth = false), 0.001f)
        // sıfır geçişi
        assertEquals(357.6f, Geo.toDialFrame(4f, 6.4f, useTrueNorth = false), 0.001f)
    }

    private fun bearingFrom(lat: Double, lon: Double) =
        Geo.bearing(istanbulLat, istanbulLon, lat, lon)
}
