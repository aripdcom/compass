package com.aripd.kerteriz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Açı ve yön hesapları. Referans değerler bağımsız hesapla doğrulanmıştır. */
class GeoTest {

    private val istanbulLat = 40.98770
    private val istanbulLon = 29.13660

    @Test
    fun `kutsal yerlerin yonleri`() {
        // Bu üç değer uygulamanın dışında, ayrı bir uygulamayla hesaplandı.
        assertEquals(152.0f, bearingFrom(21.4224779, 39.8251832), 0.2f)   // Kâbe
        assertEquals(150.1f, bearingFrom(31.7767780, 35.2356630), 0.2f)   // Mescid-i Aksa
        assertEquals(279.7f, bearingFrom(41.9021900, 12.4539300), 0.2f)   // Vatikan
    }

    /**
     * Kıble ile Mescid-i Aksa arasında Türkiye'den bakınca ~2° var. Kadranın
     * etiket birleştirme eşiği (6°) bu gerçeğe dayanıyor; fark büyürse eşik
     * anlamını yitirir.
     */
    @Test
    fun `kible ile aksa birbirine cok yakin`() {
        val kaaba = bearingFrom(21.4224779, 39.8251832)
        val aqsa = bearingFrom(31.7767780, 35.2356630)
        assertTrue(Geo.separation(kaaba, aqsa) < 3f)
    }

    @Test
    fun `ayni boylamda kuzeydeki nokta sifir derece`() {
        assertEquals(0f, bearingFrom(50.0, istanbulLon), 0.01f)
        assertEquals(180f, bearingFrom(10.0, istanbulLon), 0.01f)
    }

    @Test
    fun `ekvatorda doguya bakan nokta doksan derece`() {
        assertEquals(90f, Geo.bearing(0.0, 0.0, 0.0, 10.0), 0.01f)
        assertEquals(270f, Geo.bearing(0.0, 0.0, 0.0, -10.0), 0.01f)
    }

    @Test
    fun `normalize her acyi sifir uc yuz altmis arasina indirir`() {
        assertEquals(10f, Geo.normalize(370f), 0.001f)
        assertEquals(350f, Geo.normalize(-10f), 0.001f)
        assertEquals(0f, Geo.normalize(720f), 0.001f)
    }

    /** Sıfır geçişi: 350°'den 10°'ye dönmek 20° sağadır, 340° sola değil. */
    @Test
    fun `fark sifir gecisinde dogru yonu verir`() {
        assertEquals(20f, Geo.difference(350f, 10f), 0.001f)
        assertEquals(-20f, Geo.difference(10f, 350f), 0.001f)
        assertEquals(0f, Geo.difference(45f, 45f), 0.001f)
        // Tam yarım tur: iki yön de aynı dönüş, uygulama sola diyor.
        assertEquals(-180f, Geo.difference(0f, 180f), 0.001f)
        assertTrue(Geo.difference(0f, 179f) > 0f)
        assertTrue(Geo.difference(0f, 181f) < 0f)
    }

    /** Sayısal ortalama 359 ile 1 için 180 verirdi; doğrusu 0'dır. */
    @Test
    fun `ortalama yon vektoreldir`() {
        assertEquals(0f, Geo.meanBearing(listOf(359f, 1f)), 0.01f)
        assertEquals(151.05f, Geo.meanBearing(listOf(150.1f, 152.0f)), 0.01f)
    }

    @Test
    fun `mil donusumu`() {
        assertEquals(0, Geo.degreesToMils(0f))
        assertEquals(1600, Geo.degreesToMils(90f))
        assertEquals(3200, Geo.degreesToMils(180f))
        assertEquals(2702, Geo.degreesToMils(152f))     // kıble
        assertEquals(0, Geo.degreesToMils(360f))
        assertEquals(90f, Geo.milsToDegrees(1600f), 0.001f)
    }

    /** Manyetik çerçevede işaretler sapma kadar geri alınır, gerçekte alınmaz. */
    @Test
    fun `kadran cercevesi donusumu`() {
        assertEquals(152f, Geo.toDialFrame(152f, 6.4f, useTrueNorth = true), 0.001f)
        assertEquals(145.6f, Geo.toDialFrame(152f, 6.4f, useTrueNorth = false), 0.001f)
        // sapma bilinmiyorsa manyetik çerçevede de değişiklik olmaz
        assertEquals(152f, Geo.toDialFrame(152f, null, useTrueNorth = false), 0.001f)
        // sıfır geçişi
        assertEquals(357.6f, Geo.toDialFrame(4f, 6.4f, useTrueNorth = false), 0.001f)
    }

    /**
     * Loksodrom ile büyük daire aynı şey değil ve fark yazıya girecek kadar
     * büyük. New York'tan Lizbon'a en kısa yol 70°'den çıkar — ama o yol
     * boyunca pruva sürekli döner. Sabit pruva 92°'dir ve varana kadar
     * değişmez. Uygulamanın iki ayrı hesaba ihtiyacı olmasının sebebi bu
     * yirmi iki derece.
     */
    @Test
    fun `loksodrom buyuk daireden ayridir`() {
        val greatCircle = Geo.bearing(40.7143, -74.0060, 38.7223, -9.1393)
        val rhumb = Geo.rhumbBearing(40.7143, -74.0060, 38.7223, -9.1393)
        assertEquals(69.934f, greatCircle, 0.01f)
        assertEquals(92.277f, rhumb, 0.01f)
        assertTrue(Geo.separation(greatCircle, rhumb) > 22f)
    }

    /**
     * Pruva ve yol değerleri uygulamanın dışında doğrulandı: verilen açıyla
     * verilen uzunluk kadar yürünce hedefe metrenin altında bir sapmayla
     * varılıyor (adım adım entegrasyon, formülden bağımsız).
     */
    @Test
    fun `loksodrom pruvasi ve yolu`() {
        assertEquals(155.035f, Geo.rhumbBearing(istanbulLat, istanbulLon, 21.4224779, 39.8251832), 0.01f)
        assertEquals(2392926f, Geo.rhumbDistance(istanbulLat, istanbulLon, 21.4224779, 39.8251832), 1f)
        assertEquals(70.696f, Geo.rhumbBearing(-55.9833, -67.2667, -34.8333, 20.0), 0.01f)
        // Aynı iki nokta arasında en kısa yol 6.721.700 m; sabit pruva 389 km uzun.
        assertEquals(7110396f, Geo.rhumbDistance(-55.9833, -67.2667, -34.8333, 20.0), 1f)
    }

    /**
     * Loksodromun tanımı gereği doğru olması gereken iki şey: yol boyunca açı
     * değişmediği için dönüş pruvası tam 180° farklıdır, ve gidiş ile dönüş
     * aynı uzunluktadır. Büyük dairede ikisi de doğru değil — dönüş açısı
     * gidişin 180 fazlası çıkmaz.
     */
    @Test
    fun `loksodrom karsilikli ve simetriktir`() {
        val there = Geo.rhumbBearing(istanbulLat, istanbulLon, 21.4224779, 39.8251832)
        val back = Geo.rhumbBearing(21.4224779, 39.8251832, istanbulLat, istanbulLon)
        assertEquals(180f, Geo.separation(there, back), 0.001f)
        assertEquals(
            Geo.rhumbDistance(istanbulLat, istanbulLon, 21.4224779, 39.8251832),
            Geo.rhumbDistance(21.4224779, 39.8251832, istanbulLat, istanbulLon),
            0.001f
        )
        // Büyük dairede öyle değil: İstanbul'dan Kâbe'ye 152°, dönüşü 332° değil.
        val greatThere = Geo.bearing(istanbulLat, istanbulLon, 21.4224779, 39.8251832)
        val greatBack = Geo.bearing(21.4224779, 39.8251832, istanbulLat, istanbulLon)
        assertTrue(Geo.separation(greatThere, greatBack) < 179f)
    }

    /**
     * Meridyen ve ekvator hem büyük daire hem loksodromdur; iki hesabın orada
     * örtüşmesi gerekir. Yol da aynı çıkmalı: 40°–50° arası jeodezik uzunluk
     * 1.111.318 m'dir (bağımsız kaynaktan).
     */
    @Test
    fun `meridyen ve ekvatorda iki hesap ortusur`() {
        assertEquals(0f, Geo.rhumbBearing(40.0, 29.0, 50.0, 29.0), 0.001f)
        assertEquals(180f, Geo.rhumbBearing(50.0, 29.0, 40.0, 29.0), 0.001f)
        assertEquals(90f, Geo.rhumbBearing(0.0, 0.0, 0.0, 10.0), 0.001f)
        assertEquals(1111318f, Geo.rhumbDistance(40.0, 29.0, 50.0, 29.0), 1f)
        // Kutuptan kutba yarım meridyen: 20.003.931 m.
        assertEquals(20003931f, Geo.rhumbDistance(90.0, 0.0, -90.0, 0.0), 4f)
    }

    /**
     * Enlem çemberi boyunca loksodrom tam doğu ya da tam batıdır ve meridyen
     * payı sıfır olduğu için hesabın bölme yapan kolu burada çalışmaz. Büyük
     * daire aynı iki nokta arasında başka bir açı verir — 45. paralelde
     * on derece boylam için 86,5°.
     */
    @Test
    fun `paralel boyunca loksodrom dogu batidir`() {
        assertEquals(90f, Geo.rhumbBearing(45.0, 0.0, 45.0, 10.0), 0.001f)
        assertEquals(270f, Geo.rhumbBearing(60.0, 10.0, 60.0, 0.0), 0.001f)
        assertEquals(788468f, Geo.rhumbDistance(45.0, 0.0, 45.0, 10.0), 1f)
        assertEquals(558000f, Geo.rhumbDistance(60.0, 10.0, 60.0, 0.0), 1f)
        assertTrue(Geo.separation(Geo.bearing(45.0, 0.0, 45.0, 10.0), 90f) > 3f)
    }

    /** Tarih çizgisinin iki yanı komşudur: 179°D ile 179°B arası 2°, 358° değil. */
    @Test
    fun `tarih cizgisi kisa yoldan gecilir`() {
        assertEquals(90f, Geo.rhumbBearing(35.0, 179.0, 35.0, -179.0), 0.001f)
        assertEquals(182576f, Geo.rhumbDistance(35.0, 179.0, 35.0, -179.0), 1f)
        assertEquals(14.057f, Geo.rhumbBearing(-10.0, 178.0, 10.0, -177.0), 0.01f)
    }

    /**
     * Kutup, izometrik enlemi sonsuza götürdüğü için hesabın çöktüğü yerdi;
     * kayıt biçimi ±90°'ye izin verdiğine göre oraya bir nokta konabilir.
     * Beklenen: kuzey kutbuna gitmek tam kuzey, güney kutbuna gitmek tam
     * güney, kutuptan çıkmak her yönde güney.
     */
    @Test
    fun `kutupta hesap cokmez`() {
        assertEquals(0f, Geo.rhumbBearing(40.0, 29.0, 90.0, 0.0), 0.001f)
        assertEquals(180f, Geo.rhumbBearing(40.0, 29.0, -90.0, 0.0), 0.001f)
        assertEquals(180f, Geo.rhumbBearing(90.0, 0.0, 40.0, 29.0), 0.001f)
        assertEquals(5572436f, Geo.rhumbDistance(40.0, 29.0, 90.0, 0.0), 1f)
        // Kutupta bütün boylamlar aynı noktadır: aradaki yol sıfır.
        assertEquals(0f, Geo.rhumbDistance(90.0, 0.0, 90.0, 150.0), 0.001f)
        assertEquals(0f, Geo.rhumbDistance(41.0, 29.0, 41.0, 29.0), 0.001f)
    }

    /**
     * Deniz mili 1929'dan beri tam olarak 1852 metre. Sayı gökten inmedi:
     * meridyen boyunca bir dakikalık enlem kadardır. Yer küre olmadığı için
     * bu uzunluk sabit değil — ekvatorda 1843 m, kutupta 1862 m — ve seçilen
     * 1852, tam ortadaki 45. derecenin karşılığı. Test de oradan bakıyor.
     */
    @Test
    fun `deniz mili bir dakikalik enlem kadardir`() {
        assertEquals(1852f, Geo.METERS_PER_NAUTICAL_MILE, 0f)
        val oneMinute = Geo.rhumbDistance(45.0, 0.0, 45.0 + 1.0 / 60.0, 0.0)
        assertEquals(Geo.METERS_PER_NAUTICAL_MILE, oneMinute, 1f)
    }

    private fun bearingFrom(lat: Double, lon: Double) =
        Geo.bearing(istanbulLat, istanbulLon, lat, lon)
}
