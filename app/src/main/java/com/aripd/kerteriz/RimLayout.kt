package com.aripd.kerteriz

/**
 * Kadran kenarındaki işaretlere yarıçap dağıtır.
 *
 * Aynı yarıçapta, açısal genişlikleri toplamından yakın duran iki işaret üst üste
 * biner; böyle bir durumda ikincisi bir alt yarıçapa iner. Genişler önce yerleşir,
 * çünkü dar olanlar kalan boşluklara daha kolay sığar.
 *
 * Çizimden ayrı tutulmuştur: burası saf geometri, JVM testinden koşturulabilir.
 *
 * Kare başına koştuğu için tahsis konusunda cimri: çağıran kendi işaret
 * nesnelerini `Placeable` olarak verir (ara liste kurulmaz) ve sonuç dizisini
 * yeniden kullanabilir.
 */
object RimLayout {

    /** Yerleştirilebilen bir işaret: yönü ve merkezden görülen açısal yarı genişliği. */
    interface Placeable {
        val bearing: Float
        val halfWidth: Float
    }

    /** Sade bir `Placeable`; sabit gösterge ve testler için. */
    data class Mark(override val bearing: Float, override val halfWidth: Float) : Placeable

    /**
     * @param fixed yerinden oynatılamayan işaret (ekranın tepesindeki gösterge);
     *              en dış yarıçapa önceden yerleştirilir.
     * @param out sonucun yazılacağı dizi; en az işaret sayısı kadar olmalı ve
     *            daha uzun olabilir (çağıran üst sınırdan ayırıp yeniden
     *            kullanır). Verilmezse yenisi ayrılır.
     * @return her işaret için yarıçap oranı, giriş sırasıyla (`out`'un kendisi).
     */
    fun assign(
        marks: List<Placeable>,
        fixed: Placeable?,
        radii: FloatArray,
        margin: Float,
        out: FloatArray = FloatArray(marks.size)
    ): FloatArray {
        val count = marks.size
        // -1: henüz yerleşmedi. Çakışma sınaması yalnızca yerleşmiş olanlara bakar.
        val levels = IntArray(count) { UNPLACED }

        // Genişten dara ekleme sıralaması. `sortedByDescending` doğru sonucu
        // veriyordu ama indeksleri kutulayıp iki ara liste kuruyordu; burası
        // saniyede yirmi kez koştuğu için sıralama yerinde yapılıyor.
        val order = IntArray(count) { it }
        for (i in 1 until count) {
            val pick = order[i]
            val width = marks[pick].halfWidth
            var j = i - 1
            while (j >= 0 && marks[order[j]].halfWidth < width) {
                order[j + 1] = order[j]
                j--
            }
            order[j + 1] = pick
        }

        for (k in 0 until count) {
            val index = order[k]
            val mark = marks[index]
            var level = radii.lastIndex
            for (candidate in radii.indices) {
                if (!clashes(mark, index, candidate, marks, levels, fixed, margin)) {
                    level = candidate
                    break
                }
            }
            levels[index] = level
            out[index] = radii[level]
        }
        return out
    }

    /** İşaret verilen kademede yerleşmiş bir başkasına değiyor mu? */
    private fun clashes(
        mark: Placeable,
        index: Int,
        level: Int,
        marks: List<Placeable>,
        levels: IntArray,
        fixed: Placeable?,
        margin: Float
    ): Boolean {
        // Sabit gösterge yalnızca en dış kademeyi tutar.
        if (level == 0 && fixed != null && touches(mark, fixed, margin)) return true
        for (other in marks.indices) {
            if (other == index || levels[other] != level) continue
            if (touches(mark, marks[other], margin)) return true
        }
        return false
    }

    private fun touches(a: Placeable, b: Placeable, margin: Float): Boolean =
        Geo.separation(a.bearing, b.bearing) < a.halfWidth + b.halfWidth + margin

    private const val UNPLACED = -1
}
