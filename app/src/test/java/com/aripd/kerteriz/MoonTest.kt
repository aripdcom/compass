package com.aripd.kerteriz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ayın konumu ve evresi. Ay hesabı hataya çok açık olduğu için testler fiziksel
 * sabitlere dayanır: bunlar uygulamadan bağımsız, ölçülmüş gerçeklerdir.
 */
class MoonTest {

    private val lat = 40.98765
    private val lon = 29.13664
    private val gun = 86_400_000L

    /** JD 2451550,09766 bilinen bir yeni ay anıdır. */
    private val yeniAy = 947_168_437_823L

    /**
     * Epok hatasını yakalayan test. Schlyter'in gün sayısı 31 Aralık 1999
     * 00:00 UT'den başlar; yanlışlıkla JD 2451545,0 (1 Ocak öğlen) kullanmak
     * 1,5 günlük kayma, yani ayın yerinde 18° hata demektir. İlk yazımda tam
     * olarak bu olmuştu ve bu kontrol yakaladı.
     */
    @Test
    fun `bilinen yeni ayda aydinlik sifira yakin`() {
        val position = Moon.position(yeniAy, lat, lon)
        assertTrue("aydınlık ${position.illumination}", position.illumination < 0.02f)
    }

    /** Yeni ayda ay güneşle aynı yöndedir; iki bağımsız hesabın çapraz kontrolü. */
    @Test
    fun `yeni ayda ay ile gunes ayni yonde`() {
        val moon = Moon.position(yeniAy, lat, lon)
        val sun = Sun.position(yeniAy, lat, lon)
        assertTrue(
            "ay ${moon.azimuth}, güneş ${sun.azimuth}",
            Geo.separation(moon.azimuth, sun.azimuth) < 6f
        )
    }

    /** Dolunayda ay güneşin tam karşısındadır. */
    @Test
    fun `dolunayda ay gunesin karsisinda`() {
        val dolunay = yeniAy + (14.765 * gun).toLong()
        val moon = Moon.position(dolunay, lat, lon)
        val sun = Sun.position(dolunay, lat, lon)
        assertTrue("aydınlık ${moon.illumination}", moon.illumination > 0.97f)
        assertEquals(180f, Geo.separation(moon.azimuth, sun.azimuth), 8f)
    }

    /** Sinodik ay 29,53 gün; tek tek aylar 29,3 ile 29,8 arasında oynar. */
    @Test
    fun `sinodik ay yaklasik yirmi dokuz bucuk gun`() {
        val ilk = yeniAyBul(yeniAy + 5 * gun)
        val ikinci = yeniAyBul(ilk + 5 * gun)
        val fark = (ikinci - ilk).toDouble() / gun
        assertTrue("bulunan aralık $fark gün", fark in 29.0..30.1)
    }

    @Test
    fun `aydinlik her zaman sifir ile bir arasinda`() {
        for (step in 0 until 400) {
            val position = Moon.position(yeniAy + step * gun, lat, lon)
            assertTrue(position.illumination in 0f..1f)
            assertTrue(position.elevation in -90f..90f)
            assertTrue(position.azimuth in 0f..360f)
        }
    }

    /** Büyüyen ayda aydınlık artar, küçülende azalır. */
    @Test
    fun `buyuyen ayda aydinlik artar`() {
        val once = Moon.position(yeniAy + 3 * gun, lat, lon)
        val sonra = Moon.position(yeniAy + 5 * gun, lat, lon)
        assertTrue(once.waxing)
        assertTrue(sonra.illumination > once.illumination)
    }

    /** Aydınlığın en küçük olduğu anı arar. */
    private fun yeniAyBul(baslangic: Long): Long {
        var enIyi = baslangic
        var enAz = Float.MAX_VALUE
        for (step in 0 until 40 * 24) {
            val t = baslangic + step * 3_600_000L
            val aydinlik = Moon.position(t, lat, lon).illumination
            if (aydinlik < enAz) {
                enAz = aydinlik
                enIyi = t
            }
        }
        return enIyi
    }
}
