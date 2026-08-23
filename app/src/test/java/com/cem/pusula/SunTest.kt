package com.cem.pusula

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
        val yaz = Sun.riseSet(1_782_043_200_000L, istanbulLat)!!     // 21 Haziran
        val ekinoks = Sun.riseSet(1_774_008_000_000L, istanbulLat)!! // 20 Mart
        val kis = Sun.riseSet(1_797_854_400_000L, istanbulLat)!!     // 21 Aralık
        assertEquals(57.3f, yaz.rise, 0.6f)
        assertEquals(89.3f, ekinoks.rise, 0.6f)
        assertEquals(121.0f, kis.rise, 0.6f)
        assertEquals(63.7f, kis.rise - yaz.rise, 1.0f)
    }

    @Test
    fun `dogus ve batis kuzey-guney eksenine gore simetrik`() {
        listOf(1_782_043_200_000L, 1_774_008_000_000L, 1_797_854_400_000L).forEach { t ->
            val riseSet = Sun.riseSet(t, istanbulLat)!!
            assertEquals(360f, riseSet.rise + riseSet.set, 0.01f)
        }
    }

    /** Kutup gündüzünde güneş ufku hiç kesmez; yay çizilemez. */
    @Test
    fun `kutupta yaz ve kis icin dogus yok`() {
        assertNull(Sun.riseSet(1_782_043_200_000L, 85.0))
        assertNull(Sun.riseSet(1_797_854_400_000L, 85.0))
        assertNotNull(Sun.riseSet(1_774_008_000_000L, 85.0))
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
