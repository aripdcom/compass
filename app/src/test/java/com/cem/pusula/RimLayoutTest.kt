package com.cem.pusula

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kadran işaretlerinin yarıçap dağıtımı. */
class RimLayoutTest {

    private val radii = floatArrayOf(0.94f, 0.855f, 0.79f)
    private val margin = 1.5f

    @Test
    fun `uzak isaretler ayni yaricapta kalir`() {
        val fractions = RimLayout.assign(
            listOf(RimLayout.Mark(0f, 2f), RimLayout.Mark(180f, 2f)),
            null, radii, margin
        )
        assertEquals(0.94f, fractions[0], 0.001f)
        assertEquals(0.94f, fractions[1], 0.001f)
    }

    @Test
    fun `cakisan isaret bir alt yaricapa iner`() {
        val fractions = RimLayout.assign(
            listOf(RimLayout.Mark(100f, 2f), RimLayout.Mark(101f, 2f)),
            null, radii, margin
        )
        assertTrue(fractions[0] != fractions[1])
    }

    /** Genişler önce yerleşir: sırada sonda olsa bile dış yarıçapı alır. */
    @Test
    fun `genis isaret once yerlesir`() {
        val fractions = RimLayout.assign(
            listOf(RimLayout.Mark(150f, 2f), RimLayout.Mark(151f, 14f)),
            null, radii, margin
        )
        assertEquals(0.94f, fractions[1], 0.001f)
        assertEquals(0.855f, fractions[0], 0.001f)
    }

    /**
     * Ekranın tepesindeki sabit gösterge yerinden oynatılamaz; yakınına düşen
     * işaret onun için de bir alt kademeye inmeli. Ölçülen gerçek durum:
     * "Vatikan" 280°, başlık 284°.
     */
    @Test
    fun `sabit gosterge yakinindaki isareti asagi iter`() {
        val fractions = RimLayout.assign(
            listOf(RimLayout.Mark(280f, 10f)),
            RimLayout.Mark(284f, 1.4f), radii, margin
        )
        assertEquals(0.855f, fractions[0], 0.001f)
    }

    @Test
    fun `sabit gostergeden uzak isaret dis yaricapta kalir`() {
        val fractions = RimLayout.assign(
            listOf(RimLayout.Mark(120f, 10f)),
            RimLayout.Mark(284f, 1.4f), radii, margin
        )
        assertEquals(0.94f, fractions[0], 0.001f)
    }

    /** Kademeler tükenince en içteki paylaşılır; çökmez, yalnızca sıkışır. */
    @Test
    fun `kademeler tukenince en ictekine yigilir`() {
        val marks = (0..4).map { RimLayout.Mark(150f + it, 2f) }
        val fractions = RimLayout.assign(marks, null, radii, margin)
        assertEquals(5, fractions.size)
        assertTrue(fractions.all { it in radii.toList() })
        assertTrue(fractions.count { it == 0.79f } >= 2)
    }

    @Test
    fun `sifir gecisinde de cakisma gorulur`() {
        val fractions = RimLayout.assign(
            listOf(RimLayout.Mark(359f, 3f), RimLayout.Mark(2f, 3f)),
            null, radii, margin
        )
        assertTrue(fractions[0] != fractions[1])
    }
}
