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

    /**
     * Ad da etiket de aranır; kadran etiketi adın içinde geçmeyebilir. Türkçede
     * yerin adı "Kâbe", kadrandaki etiketi "Kıble" — "kible" yazan onu ancak
     * etiketten bulur.
     */
    @Test
    fun `hem tam ad hem kadran etiketi aranir`() {
        assertTrue(PlaceSearch.matches("Cape Horn", "Horn", PlaceSearch.fold("horn")))
        assertTrue(PlaceSearch.matches("Kâbe", "Kıble", PlaceSearch.fold("kible")))
        assertFalse(PlaceSearch.matches("Point Nemo", "Nemo", PlaceSearch.fold("horn")))
    }

    /**
     * Arama alt dizgi araması, benzerlik araması değil — ve bu bilerek böyle.
     * Almancada burnun adı "Kap Hoorn"; çift o yüzünden içinde "horn" geçmiyor,
     * yani Almanca arayüzde "hoorn" yazılır. Bulanık eşleme on bir yerin tek
     * ekrana sığdığı bir listede kazandırdığından çok götürürdü: "horn" yazanın
     * karşısına Hoorn da, Horn da, benzeyen başka bir şey de çıkardı.
     */
    @Test
    fun `arama benzerlige degil alt dizgiye bakar`() {
        assertFalse(PlaceSearch.matches("Kap Hoorn", "Hoorn", PlaceSearch.fold("horn")))
        assertTrue(PlaceSearch.matches("Kap Hoorn", "Hoorn", PlaceSearch.fold("hoorn")))
    }

    @Test
    fun `arama sozcuk basina bagli degil`() {
        assertTrue(PlaceSearch.matches("Royal Observatory, Greenwich", "Greenwich", "green"))
        assertTrue(PlaceSearch.matches("Royal Observatory, Greenwich", "Greenwich", "observ"))
    }
}
