package com.aripd.compass

/** Kadran işaretleri üzerindeki saf işlemler. */
object Marks {

    /**
     * Birbirine çok yakın işaretleri tek etikette toplar.
     *
     * Türkiye'den bakınca kıble ile Mescid-i Aksa arasında ~2° var; kadranda
     * iki ayrı etiket göstermek hem okunmaz oluyor hem de bir bilgi katmıyor,
     * çünkü o çözünürlükte ikisi zaten aynı yön. Kesin dereceler alt satırda
     * yazıyor.
     *
     * Sıra listedeki sırayla korunur (Kâbe, Aksa, Vatikan); birleşen etiket
     * "Kıble·Aksa" diye okunsun diye, yönlerine göre değil.
     */
    fun mergeNearby(marks: List<PlaceMark>, withinDegrees: Float): List<PlaceMark> {
        val remaining = marks.toMutableList()
        val merged = ArrayList<PlaceMark>(marks.size)
        while (remaining.isNotEmpty()) {
            val first = remaining.removeAt(0)
            val group = arrayListOf(first)
            val iterator = remaining.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                if (Geo.separation(candidate.bearing, first.bearing) < withinDegrees) {
                    group.add(candidate)
                    iterator.remove()
                }
            }
            merged.add(
                if (group.size == 1) first
                else PlaceMark(
                    group.joinToString(SEPARATOR) { it.label },
                    Geo.meanBearing(group.map { it.bearing })
                )
            )
        }
        return merged
    }

    private const val SEPARATOR = "·"
}
