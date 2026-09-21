package com.aripd.kerteriz

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * İbrenin yumuşatılması: alçak geçiren süzgeç.
 *
 * İki incelik var.
 *
 * **Açı sin/cos üzerinden yumuşatılır.** Doğrudan dereceyi yumuşatmak 359°'den
 * 0°'ye geçerken ibreyi bütün kadran boyunca geri döndürürdü; vektör olarak
 * yumuşatınca sıçrama diye bir şey kalmıyor.
 *
 * **Katsayı değil süre saklanır.** Katsayının anlamı örnekleme hızına bağlı:
 * aynı 0,12 değeri 50 Hz'de 0,17 saniyelik, 16 Hz'de 0,5 saniyelik gecikme
 * demek. Zaman sabiti saklanıp katsayı her örnekte gerçek aralıktan
 * hesaplanınca, hız değişse de ibrenin hissi sabit kalıyor — ve Android'in
 * istenen sensör hızını bir üst sınır değil bir dilek saydığı düşünülürse hız
 * gerçekten değişiyor.
 *
 * Ekran koduna dokunmaz, JVM testinden koşturulabilir.
 */
class Smoothing(var timeConstant: Float) {

    private var smoothSin = 0f
    private var smoothCos = 1f
    private var primed = false
    private var lastTimestamp = 0L

    /** Yumuşatılmış yön (derece, [0, 360)). */
    var bearing = 0f
        private set

    /** Yumuşatılmış eğim değerleri (derece). */
    var pitch = 0f
        private set
    var roll = 0f
        private set

    /**
     * Uygulama arka plandayken geçen süre örnek aralığı sayılmasın diye
     * `onResume`'da çağrılır. Yumuşatılmış değerler korunur, yalnızca zaman
     * geçmişi silinir: ilk örnekte aralık üst sınıra dayanır ve süzgeç yeni
     * okumaya hızla yaklaşır.
     */
    fun reset() {
        lastTimestamp = 0L
    }

    /**
     * @param azimuthRadians `SensorManager.getOrientation`'ın verdiği ham açı
     * @param timestampNanos sensör olayının anı (ns)
     */
    fun update(azimuthRadians: Float, pitchDegrees: Float, rollDegrees: Float, timestampNanos: Long) {
        val s = sin(azimuthRadians)
        val c = cos(azimuthRadians)
        if (!primed) {
            smoothSin = s
            smoothCos = c
            pitch = pitchDegrees
            roll = rollDegrees
            primed = true
        } else {
            val interval = ((timestampNanos - lastTimestamp) / 1_000_000_000.0)
                .coerceIn(MIN_INTERVAL, MAX_INTERVAL)
            val alpha = (1.0 - exp(-interval / timeConstant)).toFloat()
            smoothSin += alpha * (s - smoothSin)
            smoothCos += alpha * (c - smoothCos)
            pitch += alpha * (pitchDegrees - pitch)
            roll += alpha * (rollDegrees - roll)
        }
        lastTimestamp = timestampNanos
        bearing = Geo.normalize(Math.toDegrees(atan2(smoothSin, smoothCos).toDouble()).toFloat())
    }

    private companion object {
        /**
         * Aralık sınırları. Alt sınır sıfıra bölmeyi ve art arda gelen olayların
         * süzgeci dondurmasını engelliyor; üst sınır duraklamadan sonraki ilk
         * örneğin saatlerce beklemiş gibi görünmesini.
         */
        const val MIN_INTERVAL = 0.002
        const val MAX_INTERVAL = 0.25
    }
}
