package com.aripd.compass

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ana yön ve hedef titreşiminin altındaki geçiş algılama. */
class CrossingTest {

    private fun detector() = Crossing(3f)

    /** İlk örnek hiçbir zaman tetiklemez: uygulama o yöne bakarak açılabilir. */
    @Test
    fun `ilk ornek tetiklemez`() {
        assertFalse(detector().crossed(0f, 0f))
    }

    /** Üzerinden geçmek tetikler; yaklaşmak tek başına yetmez. */
    @Test
    fun `uzerinden gecmek tetikler`() {
        val crossing = detector()
        crossing.crossed(350f, 0f)
        assertFalse("yaklaşmak tetiklememeli", crossing.crossed(357f, 0f))
        assertTrue("geçiş tetiklemeli", crossing.crossed(2f, 0f))
    }

    /**
     * Asıl mesele bu: hızlı çevirmede örnekler pencereyi atlar. 350°'den 10°'ye
     * tek adımda gitmek yine de geçiştir.
     */
    @Test
    fun `hizli cevirmede atlanan ornek geciti kacirmaz`() {
        val crossing = detector()
        crossing.crossed(340f, 0f)
        assertTrue(crossing.crossed(20f, 0f))
    }

    /** Tam o yönde durulduğunda gürültü art arda tetiklememeli. */
    @Test
    fun `yonde dururken gurultu tekrar tetiklemez`() {
        val crossing = detector()
        crossing.crossed(350f, 0f)
        assertTrue(crossing.crossed(1f, 0f))
        // Sıfırın iki yanında salınım: kurma eşiği aşılmadığı için sessiz kalır.
        assertFalse(crossing.crossed(359.5f, 0f))
        assertFalse(crossing.crossed(0.5f, 0f))
        assertFalse(crossing.crossed(359f, 0f))
        assertFalse(crossing.crossed(1f, 0f))
    }

    /** Yeterince uzaklaşınca yeniden kurulur ve sonraki geçiş tetikler. */
    @Test
    fun `uzaklasinca yeniden kurulur`() {
        val crossing = detector()
        crossing.crossed(350f, 0f)
        assertTrue(crossing.crossed(1f, 0f))
        assertFalse("kurma eşiğini aşmak tek başına tetiklemez", crossing.crossed(10f, 0f))
        assertTrue(crossing.crossed(355f, 0f))
    }

    /** Başka bir yöne geçmek sayacı sıfırlar: o yönde henüz bir geçmiş yok. */
    @Test
    fun `bolge degisince sayac sifirlanir`() {
        val crossing = detector()
        crossing.crossed(80f, 90f, zone = 1)
        // Kuzeye atlamak yeni bir bölge; ilk örneği tetiklememeli.
        assertFalse(crossing.crossed(2f, 0f, zone = 0))
        assertFalse(crossing.crossed(1f, 0f, zone = 0))
        // Kurma eşiğinin dışına çıkıp geri geçmek gerekiyor.
        assertFalse(crossing.crossed(350f, 0f, zone = 0))
        assertTrue(crossing.crossed(5f, 0f, zone = 0))
    }

    /** `reset` uygulamayı öne aldığında geçmişi siler. */
    @Test
    fun `reset gecmisi siler`() {
        val crossing = detector()
        crossing.crossed(350f, 0f)
        crossing.reset()
        assertFalse(crossing.crossed(10f, 0f))
    }

    /** Sıfır çevresinde olduğu gibi 180° çevresinde de çalışır. */
    @Test
    fun `yarim turda da gecis gorulur`() {
        val crossing = detector()
        crossing.crossed(174f, 180f)
        assertTrue(crossing.crossed(186f, 180f))
    }
}
