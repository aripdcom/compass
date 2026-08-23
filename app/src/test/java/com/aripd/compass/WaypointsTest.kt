package com.aripd.compass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaypointsTest {

    private val ornek = listOf(
        Waypoint("Araba", 40.98765, 29.13664),
        Waypoint("Kamp", 41.5, -0.25)
    )

    @Test
    fun `yazip okuma ayni listeyi verir`() {
        assertEquals(ornek, Waypoints.decode(Waypoints.encode(ornek)))
    }

    @Test
    fun `bos ve bozuk girdi cokmez`() {
        assertEquals(emptyList<Waypoint>(), Waypoints.decode(null))
        assertEquals(emptyList<Waypoint>(), Waypoints.decode(""))
        assertEquals(emptyList<Waypoint>(), Waypoints.decode("   "))
        assertEquals(emptyList<Waypoint>(), Waypoints.decode("sacma"))
        // Bozuk satir atlanir, digerleri korunur.
        assertEquals(ornek, Waypoints.decode(Waypoints.encode(ornek) + "\nbozuk\u0001abc\u0001def"))
    }

    /** Kullanici adina ayrac ya da satir sonu yazarsa kayit bozulmamali. */
    @Test
    fun `ad temizlenir`() {
        val okunan = Waypoints.decode(Waypoints.encode(listOf(Waypoint("Araba\nEv", 1.0, 2.0))))
        assertEquals(1, okunan.size)
        assertEquals("Araba Ev", okunan[0].name)
        assertEquals(1.0, okunan[0].latitude, 1e-9)
        val ayracli = Waypoints.decode(Waypoints.encode(listOf(Waypoint("A\u0001B", 1.0, 2.0))))
        assertEquals("AB", ayracli[0].name)
    }

    @Test
    fun `cok uzun ad kirpilir`() {
        assertTrue(Waypoints.sanitize("x".repeat(200)).length <= 40)
    }

    @Test
    fun `gecersiz koordinat atilir`() {
        assertEquals(emptyList<Waypoint>(), Waypoints.decode("Yer\u0001120.0\u000129.0"))
        assertEquals(emptyList<Waypoint>(), Waypoints.decode("Yer\u000141.0\u0001200.0"))
    }

    @Test
    fun `yeni ad var olanla cakismaz`() {
        val liste = listOf(Waypoint("Nokta 1", 1.0, 1.0), Waypoint("Nokta 2", 1.0, 1.0))
        assertEquals("Nokta 3", Waypoints.nextName(liste) { "Nokta $it" })
        // Aradaki bosluk kullanilir: "Nokta 3" varsa yeni ad "Nokta 1" olur.
        assertEquals("Nokta 1", Waypoints.nextName(listOf(Waypoint("Nokta 3", 1.0, 1.0))) { "Nokta $it" })
    }

    @Test
    fun `negatif ve ondalikli koordinatlar korunur`() {
        val okunan = Waypoints.decode(Waypoints.encode(ornek))
        assertEquals(-0.25, okunan[1].longitude, 1e-9)
        assertEquals(29.13664, okunan[0].longitude, 1e-9)
    }
}
