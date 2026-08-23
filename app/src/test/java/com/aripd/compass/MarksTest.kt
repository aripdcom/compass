package com.aripd.compass

import org.junit.Assert.assertEquals
import org.junit.Test

/** Kadran işaretlerinin birleştirilmesi. */
class MarksTest {

    private fun marks(vararg pairs: Pair<String, Float>) =
        pairs.map { PlaceMark(it.first, it.second) }

    /** Uzak işaretler olduğu gibi kalır. */
    @Test
    fun `uzak isaretler birlesmez`() {
        val merged = Marks.mergeNearby(marks("Kıble" to 150f, "Vatikan" to 300f), 6f)
        assertEquals(2, merged.size)
        assertEquals("Kıble", merged[0].label)
        assertEquals(150f, merged[0].bearing, 0.001f)
    }

    /**
     * Türkiye'den bakınca kıble ile Mescid-i Aksa arasında ~2° var; kadranda
     * iki ayrı etiket okunmaz oluyor ve bir bilgi katmıyor.
     */
    @Test
    fun `yakin isaretler tek etikette toplanir`() {
        val merged = Marks.mergeNearby(marks("Kıble" to 152f, "Aksa" to 154f), 6f)
        assertEquals(1, merged.size)
        assertEquals("Kıble·Aksa", merged[0].label)
        assertEquals(153f, merged[0].bearing, 0.01f)
    }

    /** Etiket sırası listedeki sırayla korunur, yönlerine göre değil. */
    @Test
    fun `sira listeden gelir`() {
        val merged = Marks.mergeNearby(marks("Kıble" to 154f, "Aksa" to 152f), 6f)
        assertEquals("Kıble·Aksa", merged[0].label)
    }

    /** Ortalama vektöreldir: 359° ile 1° için 0° verir, 180° değil. */
    @Test
    fun `sifir gecisinde ortalama dogru`() {
        val merged = Marks.mergeNearby(marks("A" to 359f, "B" to 1f), 6f)
        assertEquals(1, merged.size)
        assertEquals(0f, Geo.difference(0f, merged[0].bearing), 0.01f)
    }

    /** Üç ve daha fazlası da tek etikette toplanabilir. */
    @Test
    fun `uc isaret birlesebilir`() {
        val merged = Marks.mergeNearby(marks("A" to 100f, "B" to 102f, "C" to 104f), 6f)
        assertEquals(1, merged.size)
        assertEquals("A·B·C", merged[0].label)
    }

    /**
     * Gruplama ilk işarete göre yapılır: ona uzak olan, aradakine yakın olsa
     * bile ayrı kalır. Zincirleme birleşme kadranın yarısını tek etikete
     * çevirebilirdi.
     */
    @Test
    fun `zincirleme birlesme olmaz`() {
        val merged = Marks.mergeNearby(marks("A" to 100f, "B" to 105f, "C" to 110f), 6f)
        assertEquals(2, merged.size)
        assertEquals("A·B", merged[0].label)
        assertEquals("C", merged[1].label)
    }

    @Test
    fun `bos liste bos doner`() {
        assertEquals(0, Marks.mergeNearby(emptyList(), 6f).size)
    }
}
