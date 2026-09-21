package com.aripd.kerteriz

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Manyetik anomali algılama. */
class DisturbanceTest {

    private val expected = 48f

    /** Ölçümü süzgecin oturması için yeterince besler. */
    private fun settle(disturbance: Disturbance, magnitude: Float, from: Long = 0L): Long {
        var now = from
        repeat(80) {
            now += 20L
            disturbance.update(magnitude, expected, now)
        }
        return now
    }

    @Test
    fun `beklenen alanda uyari yok`() {
        val disturbance = Disturbance()
        settle(disturbance, 48f)
        assertFalse(disturbance.disturbed)
    }

    /**
     * Eşiği aşmak tek başına yetmez: telefonu çevirirken kalibrasyon geçici
     * olarak %20'ye varan sapma üretebiliyor. Uyarı ancak sapma kesintisiz
     * sürerse çıkar.
     */
    @Test
    fun `kisa sicrama uyari cikarmaz`() {
        val disturbance = Disturbance()
        var now = settle(disturbance, 48f)
        // İki saniye boyunca bozuk okuma — bekleme süresinden kısa.
        repeat(100) {
            now += 20L
            disturbance.update(90f, expected, now)
        }
        assertFalse("2 saniye eşiğin altında kalmalı", disturbance.disturbed)
    }

    @Test
    fun `surekli sapma uyari cikarir`() {
        val disturbance = Disturbance()
        var now = settle(disturbance, 48f)
        repeat(300) {
            now += 20L
            disturbance.update(90f, expected, now)
        }
        assertTrue(disturbance.disturbed)
        assertTrue("gösterilen değer ölçümü izlemeli", disturbance.shownStrength > 80)
    }

    /**
     * Açılma ve kapanma eşikleri farklı: %25'te çıkan uyarı %15'in altına
     * inmeden kaybolmamalı, yoksa sınırda titrerdi.
     */
    @Test
    fun `esikler arasinda uyari acik kalir`() {
        val disturbance = Disturbance()
        var now = settle(disturbance, 48f)
        repeat(300) {
            now += 20L
            disturbance.update(90f, expected, now)
        }
        assertTrue(disturbance.disturbed)
        // %20 sapma: açılma eşiğinin altında ama kapanma eşiğinin üstünde.
        now = settle(disturbance, expected * 1.2f, now)
        assertTrue("histerezis bandında açık kalmalı", disturbance.disturbed)
        // %5 sapma: kapanma eşiğinin de altında.
        now = settle(disturbance, expected * 1.05f, now)
        assertFalse(disturbance.disturbed)
    }

    /** Beklenen değer bilinmiyorsa karşılaştırma yapılamaz; sessiz kalınır. */
    @Test
    fun `beklenen bilinmiyorsa uyari yok`() {
        val disturbance = Disturbance()
        var now = 0L
        repeat(300) {
            now += 20L
            assertFalse(disturbance.update(200f, null, now))
        }
        assertFalse(disturbance.disturbed)
        assertTrue("ölçüm yine de birikmeli", disturbance.strength > 100f)
    }

    /** `reset` arka planda geçen süreyi kesintisiz gözlem saymaz. */
    @Test
    fun `reset gecmisi siler`() {
        val disturbance = Disturbance()
        var now = settle(disturbance, 48f)
        repeat(100) {
            now += 20L
            disturbance.update(90f, expected, now)
        }
        disturbance.reset()
        assertFalse(disturbance.disturbed)
        // Sayaç sıfırdan başladığı için bekleme süresi baştan işler.
        repeat(50) {
            now += 20L
            disturbance.update(90f, expected, now)
        }
        assertFalse(disturbance.disturbed)
    }

    /** Yazıdaki sayı her örnekte değil, ancak kayda değer bir kaymada tazelenir. */
    @Test
    fun `kucuk oynamalar yaziyi tazelemez`() {
        val disturbance = Disturbance()
        var now = settle(disturbance, 48f)
        repeat(300) {
            now += 20L
            disturbance.update(90f, expected, now)
        }
        val shown = disturbance.shownStrength
        var refreshed = false
        repeat(5) {
            now += 20L
            if (disturbance.update(90.4f, expected, now)) refreshed = true
        }
        assertFalse("1 µT'lik oynama yazıyı değiştirmemeli", refreshed)
        assertTrue(disturbance.shownStrength == shown)
    }
}
