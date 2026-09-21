package com.aripd.kerteriz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** İbrenin yumuşatılması. */
class SmoothingTest {

    private val quarterTurn = Math.toRadians(90.0).toFloat()

    /** İlk örnek olduğu gibi alınır: açılışta ibrenin sıfırdan sürünmesi olmaz. */
    @Test
    fun `ilk ornek dogrudan gecer`() {
        val smoothing = Smoothing(0.17f)
        smoothing.update(quarterTurn, 5f, -3f, 0L)
        assertEquals(90f, smoothing.bearing, 0.001f)
        assertEquals(5f, smoothing.pitch, 0.001f)
        assertEquals(-3f, smoothing.roll, 0.001f)
    }

    /**
     * Asıl iddia bu: aynı gerçek süre boyunca 50 Hz ve 16 Hz beslemek aynı
     * sonuca varmalı. Katsayı saklansaydı 16 Hz'de üçte bir yol alınırdı.
     */
    @Test
    fun `sonuc ornekleme hizindan bagimsiz`() {
        fun run(hertz: Int): Float {
            val smoothing = Smoothing(0.17f)
            val step = 1_000_000_000L / hertz
            smoothing.update(0f, 0f, 0f, 0L)
            // Bir saniye boyunca hedef 90°'de sabit dursun.
            for (i in 1..hertz) smoothing.update(quarterTurn, 0f, 0f, i * step)
            return smoothing.bearing
        }
        assertEquals(run(50), run(16), 0.5f)
    }

    /**
     * Zaman sabiti kadar sonra sin ve cos bileşenlerinin %63,2'si alınmış olur —
     * alçak geçiren süzgecin tanımı bu.
     *
     * Açının kendisi 90°'nin %63,2'si (56,9°) değil, 59,8° çıkar. Bu bir kusur
     * değil: yumuşatma açıyı değil vektörü kesiyor, kirişte ilerlemek açıda
     * daha hızlı ilerlemek demek. Sıfır geçişini sorunsuz kılan seçimin aynısı.
     */
    @Test
    fun `zaman sabitinde bilesenlerin ucte ikisi alinir`() {
        val smoothing = Smoothing(0.2f)
        smoothing.update(0f, 0f, 0f, 0L)
        val step = 5_000_000L   // 200 Hz, kesikli toplamın hatasını küçültür
        for (i in 1..40) smoothing.update(quarterTurn, 0f, 0f, i * step)
        val fraction = 0.632
        val expected = Math.toDegrees(kotlin.math.atan2(fraction, 1.0 - fraction)).toFloat()
        assertEquals(expected, smoothing.bearing, 1.0f)
        // Sayıyı açıkça da yazıyoruz: formül değişirse fark edilsin.
        assertEquals(59.8f, smoothing.bearing, 1.0f)
    }

    /**
     * 359°'den 1°'ye geçiş kadranın tamamını dolaşmamalı. Dereceyi doğrudan
     * yumuşatmak ibreyi ters yöne 358° döndürürdü; sin/cos üzerinden yapınca
     * ara değerler sıfırın çevresinde kalıyor.
     */
    @Test
    fun `sifir gecisinde geriye donmez`() {
        val smoothing = Smoothing(0.08f)
        smoothing.update(Math.toRadians(359.0).toFloat(), 0f, 0f, 0L)
        val step = 20_000_000L
        for (i in 1..20) {
            smoothing.update(Math.toRadians(1.0).toFloat(), 0f, 0f, i * step)
            val distanceFromZero = Geo.separation(smoothing.bearing, 0f)
            assertTrue("kadranı dolaştı: ${smoothing.bearing}", distanceFromZero <= 2f)
        }
    }

    /** Yavaş ayar hızlıdan geri kalmalı; sıralamanın kendisi anlamlı. */
    @Test
    fun `kucuk zaman sabiti daha hizli yakinsar`() {
        fun after(timeConstant: Float): Float {
            val smoothing = Smoothing(timeConstant)
            smoothing.update(0f, 0f, 0f, 0L)
            for (i in 1..10) smoothing.update(quarterTurn, 0f, 0f, i * 20_000_000L)
            return smoothing.bearing
        }
        val calm = after(0.35f)
        val balanced = after(0.17f)
        val quick = after(0.08f)
        assertTrue(calm < balanced)
        assertTrue(balanced < quick)
    }

    /**
     * Duraklamadan sonra ilk aralık üst sınıra dayanır: saatlerce arka planda
     * kalmış bir örnek süzgeci kilitlememeli.
     */
    @Test
    fun `duraklamadan sonra hizla yakinsar`() {
        val smoothing = Smoothing(0.17f)
        smoothing.update(0f, 0f, 0f, 0L)
        smoothing.reset()
        smoothing.update(quarterTurn, 0f, 0f, 3_600_000_000_000L)
        assertTrue("tek adımda yolun çoğu alınmalı", smoothing.bearing > 60f)
        assertTrue(smoothing.bearing < 90f)
    }

    /** Yön her zaman [0, 360) aralığında yazılır. */
    @Test
    fun `yon normalize edilir`() {
        val smoothing = Smoothing(0.17f)
        smoothing.update(Math.toRadians(-90.0).toFloat(), 0f, 0f, 0L)
        assertEquals(270f, smoothing.bearing, 0.001f)
        assertTrue(abs(smoothing.bearing) < 360f)
    }
}
