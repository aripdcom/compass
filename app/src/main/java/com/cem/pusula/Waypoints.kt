package com.cem.pusula

/** Kaydedilmiş bir nokta: adı ve koordinatı. */
data class Waypoint(val name: String, val latitude: Double, val longitude: Double)

/**
 * Noktaların saklanması. Biçim bilerek sade: her satır bir nokta, alanlar
 * görünmez bir ayraçla (U+0001) bölünür.
 *
 * JSON kullanılmadı, çünkü `org.json` Android'in kendi sınıfıdır ve JVM
 * testlerinde çalışmaz; bu mantığın sınanabilir kalması biçimin zarafetinden
 * daha değerli. Ayraç ve satır sonu adlardan temizlenir, böylece kullanıcı ne
 * yazarsa yazsın kayıt bozulmaz.
 */
object Waypoints {

    private const val FIELD = '\u0001'
    private const val MAX_NAME = 40

    /** Aynı anda tutulabilecek en fazla nokta; kadran okunur kalsın diye. */
    const val LIMIT = 8

    fun encode(points: List<Waypoint>): String =
        points.joinToString("\n") { "${sanitize(it.name)}$FIELD${it.latitude}$FIELD${it.longitude}" }

    fun decode(text: String?): List<Waypoint> {
        if (text.isNullOrBlank()) return emptyList()
        return text.split("\n").mapNotNull { line ->
            val parts = line.split(FIELD)
            if (parts.size != 3) return@mapNotNull null
            val latitude = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            val longitude = parts[2].toDoubleOrNull() ?: return@mapNotNull null
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return@mapNotNull null
            Waypoint(parts[0], latitude, longitude)
        }
    }

    /** Ayraçları ve satır sonlarını atar, aşırı uzun adı kırpar. */
    fun sanitize(name: String): String =
        name.replace(FIELD.toString(), "")
            .replace("\n", " ")
            .replace("\r", " ")
            .trim()
            .take(MAX_NAME)

    /** Listede olmayan bir sıra numarası bulur: "Nokta 1", "Nokta 2"… */
    fun nextName(existing: List<Waypoint>, pattern: (Int) -> String): String {
        val names = existing.map { it.name }.toSet()
        var index = 1
        while (pattern(index) in names) index++
        return pattern(index)
    }
}
