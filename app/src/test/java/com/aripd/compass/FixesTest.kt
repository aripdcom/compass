package com.aripd.compass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Konum düzeltmeleri hakkındaki kararlar. */
class FixesTest {

    private val stale = 120_000L

    /** Belirgin daha yeni fix kazanır: aradan geçen sürede yer değişmiş olabilir. */
    @Test
    fun `belirgin yeni fix hassasiyetten once gelir`() {
        assertTrue(Fixes.isBetter(300_000L, 500f, 0L, 5f, stale))
    }

    /** Belirgin eski fix ne kadar hassas olursa olsun kaybeder. */
    @Test
    fun `belirgin eski fix kaybeder`() {
        assertFalse(Fixes.isBetter(0L, 1f, 300_000L, 500f, stale))
    }

    /** Yaşları yakın olanlarda hata payı küçük olan seçilir. */
    @Test
    fun `yasit fixlerde hassas olan kazanir`() {
        assertTrue(Fixes.isBetter(1_000L, 10f, 0L, 50f, stale))
        assertFalse(Fixes.isBetter(1_000L, 50f, 0L, 10f, stale))
    }

    /** Eşit hassasiyette yeni olan geçer: yerinde duruyorsak da fix tazelensin. */
    @Test
    fun `esit hassasiyette yeni olan gecer`() {
        assertTrue(Fixes.isBetter(1_000L, 10f, 0L, 10f, stale))
    }

    /**
     * Eşik kısa tutulamaz: ağ konumu GPS'ten seyrek geldiği için kısa bir
     * eşikte kaba fix hassas olanı sürekli devirirdi.
     */
    @Test
    fun `esigin hemen altinda hassasiyet karar verir`() {
        assertFalse(Fixes.isBetter(119_000L, 500f, 0L, 5f, stale))
        assertTrue(Fixes.isBetter(121_000L, 500f, 0L, 5f, stale))
    }

    /** "Buradasınız" eşiği hata payını izler ama iki yandan da sınırlanır. */
    @Test
    fun `varis esigi hata payini izler`() {
        assertEquals(18f, Fixes.arrivedWithin(18f, 10f, 25f), 0.001f)
        assertEquals(10f, Fixes.arrivedWithin(2f, 10f, 25f), 0.001f)
        assertEquals(25f, Fixes.arrivedWithin(500f, 10f, 25f), 0.001f)
        assertEquals(10f, Fixes.arrivedWithin(null, 10f, 25f), 0.001f)
    }
}
