package com.aripd.kerteriz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesTest {

    /**
     * Ayar anahtarı kullanıcının tercihinin adresi; iki yer aynı anahtarı
     * taşısa biri diğerini açıp kapatırdı ve bu ancak ekranda fark edilirdi.
     */
    @Test
    fun `ayar anahtarlari benzersiz`() {
        val keys = Places.ALL.map { it.prefKey }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `koordinatlar dunyanin icinde`() {
        Places.ALL.forEach { place ->
            assertTrue(place.prefKey, place.latitude in -90.0..90.0)
            assertTrue(place.prefKey, place.longitude in -180.0..180.0)
        }
    }

    /** Aynı noktaya iki ad koymak kadranda üst üste iki etiket demek. */
    @Test
    fun `iki yer ayni noktada degil`() {
        val points = Places.ALL.map { it.latitude to it.longitude }
        assertEquals(points.size, points.toSet().size)
    }

    /**
     * Karar burada sabitleniyor: kadranı neyin dolduracağına kullanıcı karar
     * verir. Bir yerin varsayılan olarak açılması ileride sessizce geri
     * gelmesin.
     */
    @Test
    fun `hicbir yer varsayilan olarak acik degil`() {
        assertFalse(Places.DEFAULT_VISIBLE)
    }

    /** Boş bir kategori seçim ekranında başlığı olup altı boş bir bölüm olurdu. */
    @Test
    fun `her kategoride en az bir yer var`() {
        PlaceGroup.values().forEach { group ->
            assertTrue(group.name, Places.ALL.any { it.group == group })
        }
    }

    /**
     * Kadranın kenarında yazılar için üç kademe var (`CompassView.RIM_RADII`).
     * Seçim ekranındaki uyarı bu sayıya dayanıyor; ikisi ayrışırsa uyarı
     * yanlış yerde çıkar.
     */
    @Test
    fun `rahat sinir kadranin kademe sayisiyla ayni`() {
        assertEquals(3, Places.COMFORTABLE)
    }
}
