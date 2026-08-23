package com.aripd.compass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Güneşin konumu ve doğuş/batış yönleri. */
class SunTest {

    private val istanbulLat = 40.98765
    private val istanbulLon = 29.13664

    /**
     * Bağımsız doğrulama: aynı an için Astronomical Almanac'ın (saat açısını
     * zaman denklemi yerine GMST'den kuran) formülasyonu 162,0° ve 59,3° verdi.
     */
    @Test
    fun `bilinen an icin konum`() {
        val position = Sun.position(1_787_477_349_000L, istanbulLat, istanbulLon)  // 2026-08-23 09:29:09 UTC
        assertEquals(162.0f, position.azimuth, 0.5f)
        assertEquals(59.3f, position.elevation, 0.5f)
    }

    /**
     * "Güneş doğudan doğar" yalnızca ekinokslarda doğrudur ve orada bile tam 90°
     * değil, 89,3°'dir: güneşin merkezi ufkun 0,833° altındayken görünür.
     */
    @Test
    fun `dogus yonu mevsimle 64 derece geziniyor`() {
        val yaz = Sun.riseSet(1_782_043_200_000L, istanbulLat, istanbulLon)!!     // 21 Haziran
        val ekinoks = Sun.riseSet(1_774_008_000_000L, istanbulLat, istanbulLon)!! // 20 Mart
        val kis = Sun.riseSet(1_797_854_400_000L, istanbulLat, istanbulLon)!!     // 21 Aralık
        assertEquals(57.3f, yaz.rise, 0.6f)
        assertEquals(89.3f, ekinoks.rise, 0.6f)
        assertEquals(121.0f, kis.rise, 0.6f)
        assertEquals(63.7f, kis.rise - yaz.rise, 1.0f)
    }

    /**
     * Doğuş ve batış kuzey-güney eksenine göre neredeyse simetriktir ama tam
     * değil: deklinasyon sabah ile akşam arasında değişir, dolayısıyla iki uç
     * birkaç yüzde bir derece kayar. Bu fiziksel bir gerçek, hesap hatası değil.
     */
    @Test
    fun `dogus ve batis kuzey-guney eksenine gore neredeyse simetrik`() {
        listOf(1_782_043_200_000L, 1_774_008_000_000L, 1_797_854_400_000L).forEach { t ->
            val riseSet = Sun.riseSet(t, istanbulLat, istanbulLon)!!
            assertEquals(360f, riseSet.rise + riseSet.set, 0.4f)
        }
    }

    /** Kutup gündüzünde güneş ufku hiç kesmez; yay çizilemez. */
    @Test
    fun `kutupta yaz ve kis icin dogus yok`() {
        assertNull(Sun.riseSet(1_782_043_200_000L, 85.0, istanbulLon))
        assertNull(Sun.riseSet(1_797_854_400_000L, 85.0, istanbulLon))
        assertNotNull(Sun.riseSet(1_774_008_000_000L, 85.0, istanbulLon))
    }

    /** Güneş en yüksek noktasına güneyde ulaşır (kuzey yarımkürede). */
    @Test
    fun `en yuksek anda azimut guneyi gosterir`() {
        var best = Sun.position(1_787_400_000_000L, istanbulLat, istanbulLon)
        var bestTime = 1_787_400_000_000L
        for (step in 0 until 24 * 60) {
            val t = 1_787_400_000_000L + step * 60_000L
            val p = Sun.position(t, istanbulLat, istanbulLon)
            if (p.elevation > best.elevation) {
                best = p
                bestTime = t
            }
        }
        assertEquals(180f, best.azimuth, 1.0f)
        // 23 Ağustos'ta güneşin sapması ~+11,4°, azami yükseklik 90-|41-11,4|
        assertEquals(60.4f, best.elevation, 1.0f)
        assertTrue(bestTime > 0L)
    }

    /**
     * En güçlü kontrol: doğuş ve batış saatleri ile konum hesabı birbirini
     * tutmalı. İkisi ayrı formüllerden gelir — biri saat açısını, diğeri azimutu
     * çözer. Hesaplanan doğuş anında güneşin yüksekliği ufuk tanımına
     * (-0,833°) eşit, azimutu da hesaplanan doğuş yönüne eşit olmalıdır.
     */
    @Test
    fun `dogus ve batis anlari konum hesabiyla tutarli`() {
        listOf(1_782_043_200_000L, 1_774_008_000_000L, 1_797_854_400_000L, 1_787_477_349_000L).forEach { t ->
            val riseSet = Sun.riseSet(t, istanbulLat, istanbulLon)!!
            val atRise = Sun.position(riseSet.riseAt, istanbulLat, istanbulLon)
            val atSet = Sun.position(riseSet.setAt, istanbulLat, istanbulLon)
            assertEquals(-0.833f, atRise.elevation, 0.02f)
            assertEquals(-0.833f, atSet.elevation, 0.02f)
            assertEquals(riseSet.rise, atRise.azimuth, 0.1f)
            assertEquals(riseSet.set, atSet.azimuth, 0.1f)
        }
    }

    /** Gün uzunluğu mevsimle değişir: İstanbul'da yazın ~15, kışın ~9 saat. */
    @Test
    fun `gun uzunlugu mevsimle degisir`() {
        val yaz = Sun.riseSet(1_782_043_200_000L, istanbulLat, istanbulLon)!!
        val kis = Sun.riseSet(1_797_854_400_000L, istanbulLat, istanbulLon)!!
        val yazSaat = (yaz.setAt - yaz.riseAt) / 3_600_000.0
        val kisSaat = (kis.setAt - kis.riseAt) / 3_600_000.0
        assertEquals(15.1, yazSaat, 0.3)
        assertEquals(9.3, kisSaat, 0.3)
        // Ekinoksta gündüz ile gece kabaca eşittir; kırılma yüzünden gündüz biraz uzun.
        val ekinoks = Sun.riseSet(1_774_008_000_000L, istanbulLat, istanbulLon)!!
        assertTrue((ekinoks.setAt - ekinoks.riseAt) / 3_600_000.0 in 12.0..12.3)
    }

    /** Gün boyunca yükseklik hem pozitif hem negatif olmalı: gece ve gündüz var. */
    @Test
    fun `gun icinde hem gunduz hem gece var`() {
        val elevations = (0 until 24).map {
            Sun.position(1_787_400_000_000L + it * 3_600_000L, istanbulLat, istanbulLon).elevation
        }
        assertTrue(elevations.any { it > 0f })
        assertTrue(elevations.any { it < 0f })
        assertTrue(elevations.all { abs(it) <= 90f })
    }
}
