package com.aripd.compass

/**
 * Konum düzeltmeleri hakkındaki kararlar. Android'in `Location` sınıfına
 * dokunmaz — yalnızca ondan okunan sayıları alır — böylece JVM testinden
 * koşturulabilir.
 */
object Fixes {

    /**
     * Yeni gelen fix eldekinin yerini almalı mı.
     *
     * Belirgin daha yeni bir fix her zaman kazanır: aradan geçen sürede yer
     * değiştirmiş olabiliriz ve eski bir fix ne kadar hassas olursa olsun
     * yanlış yeri gösterir. Yaşları birbirine yakın olanlarda hata payı küçük
     * olan seçilir.
     *
     * Eşik kısa tutulamaz: ağ konumu GPS'ten seyrek geldiği için kısa bir
     * eşikte kaba fix hassas olanı sürekli devirirdi.
     */
    fun isBetter(
        candidateTime: Long,
        candidateAccuracy: Float,
        currentTime: Long,
        currentAccuracy: Float,
        staleMillis: Long
    ): Boolean {
        val age = candidateTime - currentTime
        if (age > staleMillis) return true
        if (age < -staleMillis) return false
        return candidateAccuracy <= currentAccuracy
    }

    /**
     * "Buradasınız" sayılmak için gereken yakınlık (metre).
     *
     * Konum hatasının altındaki mesafede yön anlamını yitirir: hata çemberinin
     * içinde hangi yöne bakılacağını söylemek uydurma olur. Eşik bu yüzden
     * cihazın bildirdiği hata payına bağlanır, ama iki yandan da sınırlanır —
     * ±2 m'lik bir fix'te iki metreyi bulmayı şart koşmak, ±500 m'lik bir
     * fix'te yarım kilometreyi "burası" saymak kadar yanlış olurdu.
     */
    fun arrivedWithin(accuracy: Float?, minimum: Float, maximum: Float): Float =
        (accuracy ?: minimum).coerceIn(minimum, maximum)
}
