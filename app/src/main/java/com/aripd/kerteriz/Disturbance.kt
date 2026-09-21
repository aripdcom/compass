package com.aripd.kerteriz

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Manyetik anomali algılama.
 *
 * Ölçülen alan, o konumda beklenenden belirgin sapıyorsa yakında mıknatıs ya da
 * mıknatıslanmış metal var demektir: pusula sessizce yanlış yön gösterir. Bu en
 * tehlikeli durum, çünkü ekranda hiçbir şey belli olmuyor — cihazın kendi
 * hassasiyet bayrağı da bunu çoğu zaman fark etmez, zira sabit bir bozulma
 * "kararlı" görünür.
 *
 * Üç ayrı gürültü savunması var ve üçü de ölçümden çıktı:
 * - Ölçüm alçak geçiren süzgeçten geçer, tek örnekteki sıçrama uyarı çıkarmaz.
 * - Uyarının açılma ve kapanma eşikleri farklı, sınırda titremesin diye.
 * - Eşiği aşmak tek başına yetmez: telefonu çevirirken kalibrasyon geçici
 *   olarak %20'ye varan sapma üretebiliyor, o yüzden sapmanın kesintisiz
 *   sürmesi aranır.
 *
 * Ekran koduna dokunmaz, JVM testinden koşturulabilir.
 */
class Disturbance {

    /** Yumuşatılmış alan şiddeti (µT). */
    var strength = 0f
        private set

    /** Uyarı açık mı. */
    var disturbed = false
        private set

    /** Uyarı yazısında duran sayı; ölçüm ondan belirgin ayrılınca tazelenir. */
    var shownStrength = -1
        private set

    /** Sapmanın eşiği kesintisiz aştığı ilk an; 0 ise şu anda aşmıyor. */
    private var since = 0L

    /**
     * Arka planda geçen süre kesintisiz gözlem sayılmasın diye `onResume`'da
     * çağrılır.
     */
    fun reset() {
        strength = 0f
        disturbed = false
        shownStrength = -1
        since = 0L
    }

    /**
     * @param magnitude manyetometrenin verdiği toplam alan şiddeti (µT)
     * @param expected o konumda beklenen şiddet; bilinmiyorsa null
     * @return uyarı yazısının tazelenmesi gerekiyorsa true
     */
    fun update(magnitude: Float, expected: Float?, nowMillis: Long): Boolean {
        strength = if (strength == 0f) magnitude else strength + SMOOTHING * (magnitude - strength)

        if (expected == null || expected == 0f) return false
        val deviation = abs(strength - expected) / expected
        val threshold = if (disturbed) CLEAR else WARN

        if (deviation <= threshold) {
            since = 0L
            if (!disturbed) return false
            disturbed = false
            return true
        }

        if (since == 0L) since = nowMillis
        if (!disturbed) {
            if (nowMillis - since < HOLD_MS) return false
            disturbed = true
            shownStrength = strength.roundToInt()
            return true
        }
        // Uyarı çıktıktan sonra da yazıdaki sayı canlı kalsın, ama her örnekte
        // tazelenmesin: 1 µT'lik oynamalar yazıyı saniyede birkaç kez değiştirip
        // göz yorardı, o yüzden ancak 2 µT'yi aşan bir kayma yazıya yansır.
        if (abs(strength.roundToInt() - shownStrength) < SHOWN_STEP) return false
        shownStrength = strength.roundToInt()
        return true
    }

    private companion object {
        /** Alçak geçiren süzgecin katsayısı; manyetometre örnekleri gürültülü. */
        const val SMOOTHING = 0.1f

        // Uyarı bu oranın üstünde çıkar, altındakinde kaybolur.
        const val WARN = 0.25f
        const val CLEAR = 0.15f

        /** Sapmanın uyarı sayılması için kesintisiz sürmesi gereken süre. */
        const val HOLD_MS = 2_500L

        /** Yazıdaki sayının tazelenmesi için gereken kayma (µT). */
        const val SHOWN_STEP = 2
    }
}
