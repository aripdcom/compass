package com.aripd.compass

import kotlin.math.abs

/**
 * Bir yönün **üzerinden geçildiğini** algılar.
 *
 * Bölge değil geçiş aranır: izlenen yöne göre işaretli fark iki örnek arasında
 * işaret değiştirdiyse o yönün üzerinden geçilmiştir. Bunun sebebi örnekleme
 * hızı — "2° yaklaşınca tık" kuralı 50 Hz'de çalışıyordu ama 16 Hz'de hızlı
 * çevirmede örnekler 5-6° atlıyor ve 4°'lik pencere tümüyle ıskalanabiliyor.
 * Geçiş algılama hızdan bağımsızdır.
 *
 * Tam o yönde durulduğunda gürültü işareti sürekli değiştirebileceği için, bir
 * kez tetikledikten sonra en az [armDegrees] kadar uzaklaşılmadan yeniden
 * tetiklenmez.
 *
 * Ekran koduna dokunmaz: histerezis uygulamanın en incelikli parçası olduğu
 * için JVM testinden koşturulabilir tutuluyor.
 */
class Crossing(private val armDegrees: Float) {

    /** Uygulama açılırken o yöne bakıyorsanız titremesin diye ilk örnek sayılmaz. */
    private var primed = false

    /** Tetiklendikten sonra yeterince uzaklaşılana kadar yeniden tetiklenmez. */
    private var armed = false

    /** Bir önceki örnekteki işaretli fark; işaret değişimi geçiş demektir. */
    private var lastOffset = 0f

    /** İzlenen yönün kimliği; değişince sayaç sıfırdan başlar. */
    private var zone = NO_ZONE

    /** Hiç örnek görülmemiş gibi başa döner; uygulama öne geldiğinde çağrılır. */
    fun reset() {
        primed = false
        zone = NO_ZONE
    }

    /**
     * @param bearing şu an bakılan yön
     * @param reference geçilmesi izlenen yön
     * @param zone izlenen yönün kimliği; başka bir yöne geçildiğini bildirir
     * @return bu örnekte referansın üzerinden geçildiyse true
     */
    fun crossed(bearing: Float, reference: Float, zone: Int = 0): Boolean {
        val offset = Geo.difference(reference, bearing)

        if (!primed || zone != this.zone) {
            primed = true
            this.zone = zone
            armed = abs(offset) > armDegrees
            lastOffset = offset
            return false
        }

        var crossed = false
        if (!armed) {
            if (abs(offset) > armDegrees) armed = true
        } else if ((offset > 0f) != (lastOffset > 0f)) {
            armed = false
            crossed = true
        }
        lastOffset = offset
        return crossed
    }

    private companion object {
        const val NO_ZONE = Int.MIN_VALUE
    }
}
