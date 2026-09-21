package com.aripd.kerteriz

import java.text.Normalizer
import java.util.Locale

/**
 * Yer listesindeki aramanın harf işleri.
 *
 * Ekrandan ayrı tutuldu: burası saf metin işlemi ve JVM testinden koşturulabilir
 * — `RimLayout` ile aynı sebeple.
 */
object PlaceSearch {

    /**
     * Aramayı aksandan ve büyük/küçük harften bağımsız kılar.
     *
     * Küçültme `Locale.ROOT` ile yapılıyor, cihazın diliyle değil. Türkçe
     * yerelinde `I` harfi `ı`ya düşer ve "kashi" yazan "Kashi"yi bulamazdı —
     * yani uygulamanın dilini Türkçe yapmak aramayı bozardı.
     */
    fun fold(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        val mapped = buildString(lower.length) {
            for (ch in lower) {
                val replacement = FOLDED[ch]
                if (replacement != null) append(replacement) else append(ch)
            }
        }
        return MARKS.replace(Normalizer.normalize(mapped, Normalizer.Form.NFD), "")
    }

    /** Boş arama her şeyi eşler; gerisi tam adda ya da kadran etiketinde aranır. */
    fun matches(name: String, label: String, needle: String): Boolean {
        if (needle.isEmpty()) return true
        return fold(name).contains(needle) || fold(label).contains(needle)
    }

    /** Birleştirici işaretler: NFD'den sonra geriye kalan aksanlar. */
    private val MARKS = Regex("\\p{Mn}+")

    /**
     * NFD'nin ayrıştırmadığı harfler. `ö` ya da `ş` ayrışıp aksanını bırakıyor,
     * ama `ø`, `æ`, `ł`, `đ` ve noktasız `ı` tek bir kod noktası — elle
     * karşılıkları veriliyor ki "kabe" yazan "Kâbe"yi, "lodz" yazan "Łódź"ü
     * bulsun.
     */
    private val FOLDED = mapOf(
        'ı' to "i", 'ø' to "o", 'æ' to "ae", 'œ' to "oe",
        'ł' to "l", 'đ' to "d", 'ð' to "d", 'þ' to "th", 'ß' to "ss"
    )
}
