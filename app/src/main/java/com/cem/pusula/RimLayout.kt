package com.cem.pusula

/**
 * Kadran kenarındaki işaretlere yarıçap dağıtır.
 *
 * Aynı yarıçapta, açısal genişlikleri toplamından yakın duran iki işaret üst üste
 * biner; böyle bir durumda ikincisi bir alt yarıçapa iner. Genişler önce yerleşir,
 * çünkü dar olanlar kalan boşluklara daha kolay sığar.
 *
 * Çizimden ayrı tutulmuştur: burası saf geometri, JVM testinden koşturulabilir.
 */
object RimLayout {

    /** Yerleştirilecek bir işaret: yönü ve merkezden görülen açısal yarı genişliği. */
    data class Mark(val bearing: Float, val halfWidth: Float)

    /**
     * @param fixed yerinden oynatılamayan işaret (ekranın tepesindeki gösterge);
     *              en dış yarıçapa önceden yerleştirilir.
     * @return her işaret için yarıçap oranı, giriş sırasıyla.
     */
    fun assign(
        marks: List<Mark>,
        fixed: Mark?,
        radii: FloatArray,
        margin: Float
    ): FloatArray {
        val fractions = FloatArray(marks.size)
        val placed = Array(radii.size) { ArrayList<Mark>() }
        fixed?.let { placed[0].add(it) }
        marks.indices.sortedByDescending { marks[it].halfWidth }.forEach { index ->
            val mark = marks[index]
            var level = radii.lastIndex
            for (candidate in radii.indices) {
                val clash = placed[candidate].any { other ->
                    Geo.separation(mark.bearing, other.bearing) < mark.halfWidth + other.halfWidth + margin
                }
                if (!clash) {
                    level = candidate
                    break
                }
            }
            placed[level].add(mark)
            fractions[index] = radii[level]
        }
        return fractions
    }
}
