package com.cem.pusula

import android.graphics.Color

/**
 * Ekranın renk düzeni. İki hâli var: gündüz ve gece.
 *
 * Gece modunda her şey kırmızıya çekilir. Sebebi göz fizyolojisi: karanlığa
 * uyum sağlamış göz (rodopsin) kırmızı ışıktan neredeyse hiç etkilenmez, ama
 * mavi-yeşil ışık uyumu saniyeler içinde bozar ve yeniden karanlığa alışmak
 * yarım saat sürer. Gece yön bulurken ekrana her bakışta gece görüşünü baştan
 * kaybetmemek için kadran tümüyle kırmızıya çevrilir.
 *
 * Bunun bir bedeli var: gece modunda işaretler renkle ayırt edilemez, çünkü
 * hepsi aynı tonun farklı parlaklıklarıdır. Bu yüzden şekil ayrımı önemli —
 * güneş disk, nokta baklava, manyetik kuzey ile kıble ise yazı.
 */
data class Palette(
    val background: Int,
    val dial: Int,
    val majorTick: Int,
    val minorTick: Int,
    val label: Int,
    val northLabel: Int,
    val needleNorth: Int,
    val needleSouth: Int,
    val levelFill: Int,
    val levelFrame: Int,
    val level: Int,
    val magnetic: Int,
    val qibla: Int,
    val target: Int,
    val waypoint: Int,
    val sun: Int,
    val moon: Int,
    val text: Int,
    val textDim: Int,
    val hint: Int,
    val warning: Int
) {
    companion object {
        val DAY = Palette(
            background = Color.parseColor("#FF101418"),
            dial = Color.parseColor("#33FFFFFF"),
            majorTick = Color.parseColor("#CCFFFFFF"),
            minorTick = Color.parseColor("#55FFFFFF"),
            label = Color.parseColor("#F0F3F6"),
            northLabel = Color.parseColor("#E5484D"),
            needleNorth = Color.parseColor("#E5484D"),
            needleSouth = Color.parseColor("#F0F3F6"),
            levelFill = Color.parseColor("#FF101418"),
            levelFrame = Color.parseColor("#66FFFFFF"),
            level = Color.parseColor("#F0F3F6"),
            magnetic = Color.parseColor("#4C9AFF"),
            qibla = Color.parseColor("#3DD68C"),
            target = Color.parseColor("#F5B841"),
            waypoint = Color.parseColor("#C77DFF"),
            sun = Color.parseColor("#FFD84D"),
            moon = Color.parseColor("#D6DEE8"),
            text = Color.parseColor("#FFFFFFFF"),
            textDim = Color.parseColor("#FF8A94A0"),
            hint = Color.parseColor("#FF6C7683"),
            warning = Color.parseColor("#FFE0A030")
        )

        /**
         * Gece: zemin tam siyah (ekran ne kadar az ışık verirse o kadar iyi),
         * geri kalan her şey kırmızının parlaklık kademeleri.
         */
        val NIGHT = Palette(
            background = Color.parseColor("#FF000000"),
            dial = Color.parseColor("#33FF4A38"),
            majorTick = Color.parseColor("#CCFF5B45"),
            minorTick = Color.parseColor("#55FF5B45"),
            label = Color.parseColor("#FFCF4632"),
            northLabel = Color.parseColor("#FFFF6A50"),
            needleNorth = Color.parseColor("#FFFF3B2F"),
            needleSouth = Color.parseColor("#FF6E2418"),
            levelFill = Color.parseColor("#FF000000"),
            levelFrame = Color.parseColor("#66FF4A38"),
            level = Color.parseColor("#FFFF9A80"),
            magnetic = Color.parseColor("#FF8A2E22"),
            qibla = Color.parseColor("#FFB03828"),
            target = Color.parseColor("#FFFF6A50"),
            waypoint = Color.parseColor("#FF9A3325"),
            sun = Color.parseColor("#FFFF8A70"),
            moon = Color.parseColor("#FFC65A46"),
            text = Color.parseColor("#FFFF5B45"),
            textDim = Color.parseColor("#FFB84A3A"),
            hint = Color.parseColor("#FF8A3A2E"),
            warning = Color.parseColor("#FFFF7A50")
        )

        fun of(night: Boolean): Palette = if (night) NIGHT else DAY
    }
}
