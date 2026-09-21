package com.aripd.kerteriz

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceSearchTest {

    @Test
    fun `aksan aranirken yok sayilir`() {
        assertEquals("kabe", PlaceSearch.fold("Kâbe"))
        assertEquals("kasi", PlaceSearch.fold("Kaşi"))
        assertEquals("bodhgaja", PlaceSearch.fold("Bódhgaja"))
    }

    /**
     * Asıl mesele bu: Türkçe yerelinde `"Kashi".lowercase()` "kashı" verir ve
     * "kashi" yazan kendi listesini bulamazdı. `fold` yereli kullanmadığı için
     * uygulamanın dili aramayı etkilemiyor.
     */
    @Test
    fun `noktali i cihazin diline gore degismez`() {
        assertEquals("kashi", PlaceSearch.fold("KASHI"))
        assertEquals("ise", PlaceSearch.fold("İse"))
        assertEquals("istanbul", PlaceSearch.fold("İstanbul"))
    }

    @Test
    fun `NFD ile ayrismayan harflerin karsiligi var`() {
        assertEquals("oresund", PlaceSearch.fold("Øresund"))
        assertEquals("lodz", PlaceSearch.fold("Łódź"))
        assertEquals("aero", PlaceSearch.fold("Ærø"))
        assertEquals("strasse", PlaceSearch.fold("Straße"))
    }

    @Test
    fun `bos arama her seyi esler`() {
        assertTrue(PlaceSearch.matches("Cape Horn", "Horn", ""))
    }

    @Test
    fun `hem tam ad hem kadran etiketi aranir`() {
        val needle = PlaceSearch.fold("horn")
        assertTrue(PlaceSearch.matches("Cape Horn", "Horn", needle))
        assertTrue(PlaceSearch.matches("Kap Hoorn", "Hoorn", needle))
        assertFalse(PlaceSearch.matches("Point Nemo", "Nemo", needle))
    }

    @Test
    fun `arama sozcuk basina bagli degil`() {
        assertTrue(PlaceSearch.matches("Royal Observatory, Greenwich", "Greenwich", "green"))
        assertTrue(PlaceSearch.matches("Royal Observatory, Greenwich", "Greenwich", "observ"))
    }
}
