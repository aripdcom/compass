#!/usr/bin/env python3
"""Play mağaza görsellerini üretir: ikon, öne çıkan görsel ve ekran görüntüleri.

    python3 tools/store-graphics.py          # store/ altına yazar

Neden betik: ikisi de uygulamanın kendi geometrisinden ve paletinden türüyor.
Elle çizilse ikon bir gün uygulamanınkinden ayrı düşerdi; burada tek kaynak
`ic_launcher_foreground.xml` ile `Palette.kt` ve değerler buraya kopyalandı.
Bir renk değişirse ikisini birlikte güncellemek gerekiyor.

CI'da koşmaz — Pillow gerekiyor (`pip install pillow`) ve çıktılar depoda
duruyor, yani ancak ikon ya da palet değişince yeniden koşturulur.
"""
import math
import pathlib

from PIL import Image, ImageDraw, ImageFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "store"
FONT = "/mnt/skills/examples/canvas-design/canvas-fonts/InstrumentSans-Bold.ttf"

# Palette.kt, DAY.
BG = (0x10, 0x14, 0x18)
DIAL = (0xFF, 0xFF, 0xFF, 0x33)
MAJOR = (0xFF, 0xFF, 0xFF, 0xCC)
MINOR = (0xFF, 0xFF, 0xFF, 0x55)
NORTH = (0xE5, 0x48, 0x4D)
SOUTH = (0xF0, 0xF3, 0xF6)
HUB = (0x8A, 0x94, 0xA0)
MAGNETIC = (0x4C, 0x9A, 0xFF)
PLACE = (0x3D, 0xD6, 0x8C)
WAYPOINT = (0xC7, 0x7D, 0xFF)
SUN = (0xFF, 0xD8, 0x4D)
MOON = (0xD6, 0xDE, 0xE8)
TEXT = (0xF0, 0xF3, 0xF6)
DIM = (0x8A, 0x94, 0xA0)


def canvas(width, height, scale):
    """Kenarları yumuşasın diye büyük çizilip küçültülüyor; Pillow kenar
    yumuşatma yapmıyor, tek yolu bu."""
    image = Image.new("RGB", (width * scale, height * scale), BG)
    return image, ImageDraw.Draw(image, "RGBA")


def ring(draw, cx, cy, r, colour, width):
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=colour, width=int(round(width)))


def disc(draw, cx, cy, r, colour):
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=colour)


def at(cx, cy, r, degrees):
    """Kadran açısı: 0 yukarı, saat yönünde."""
    a = math.radians(degrees - 90)
    return cx + r * math.cos(a), cy + r * math.sin(a)


def icon(path, size=512, supersample=4):
    """Mağaza ikonu.

    Uyarlanabilir ikonun tuvali 108 birim ama telefonda görünen, ortadaki 72
    birimlik güvenli bölge; dışı maskeyle kırpılıyor. Mağaza ikonu o yüzden 72
    biriminden ölçekleniyor — tuvalin tamamı alınsaydı halka telefondakinden
    belirgin biçimde küçük görünürdü.
    """
    safe = 72
    edge = (108 - safe) / 2
    unit = size * supersample / safe
    image, draw = canvas(size, size, supersample)

    def p(x, y):
        return (x - edge) * unit, (y - edge) * unit

    cx, cy = p(54, 54)
    ring(draw, cx, cy, 24.5 * unit, HUB, 3 * unit)
    draw.polygon([p(54, 32), p(59.4, 54), p(48.6, 54)], fill=NORTH)
    draw.polygon([p(54, 75.8), p(59.4, 54), p(48.6, 54)], fill=SOUTH)
    disc(draw, cx, cy, 2.5 * unit, HUB)
    # Play mağaza ikonu 32 bit PNG istiyor; alfa baştan sona opak, yalnızca
    # kanal var olsun diye. Öne çıkan görsel ile ekran görüntülerinde tersi
    # geçerli, onlar 24 bit kalıyor.
    resized = image.resize((size, size), Image.LANCZOS).convert("RGBA")
    resized.save(path, optimize=True)


def feature(path, width=1024, height=500, supersample=3):
    """Öne çıkan görsel.

    Tek metin marka adı: "Kerteriz" yirmi sekiz dilde aynı, yani tek görsel
    hepsine yetiyor. Kadranda yön harfi de yok — uygulama onları çeviriyor
    (K/D/G/B), görsele konsaydı dile bağlanır ve yirmi sekiz görsel gerekirdi.

    Play bu görseli bazı yerlerde 16:9'a kırpıyor, yani yanlardan 67'şer piksel
    gidebilir. İçerik ona göre ortada tutuldu.
    """
    s = supersample
    image, draw = canvas(width, height, s)
    cx, cy, r = 745 * s, 250 * s, 180 * s

    for degrees in range(0, 360, 5):
        major = degrees % 45 == 0
        inner = r - (26 * s if major else 14 * s)
        draw.line(
            [*at(cx, cy, inner, degrees), *at(cx, cy, r, degrees)],
            fill=MAJOR if major else MINOR,
            width=int(round((3 if major else 2) * s)),
        )

    ring(draw, cx, cy, r, DIAL, 2 * s)
    ring(draw, cx, cy, r * 0.72, DIAL, 2 * s)

    arc = r + 16 * s
    draw.arc([cx - arc, cy - arc, cx + arc, cy + arc], -1, 181, fill=SUN + (0xDD,), width=int(round(4 * s)))

    # Kadranın işaretleri, uygulamadaki renk ve şekilleriyle: uygulamanın ne
    # yaptığını tek bakışta anlatan şey bunlar.
    marks = ((354, MAGNETIC, "tick"), (308, PLACE, "tick"), (109, SUN, "disc"),
             (60, WAYPOINT, "diamond"), (330, MOON, "disc"))
    for degrees, colour, kind in marks:
        x, y = at(cx, cy, r + 16 * s, degrees)
        if kind == "disc":
            disc(draw, x, y, 9 * s, colour)
        elif kind == "diamond":
            d = 9 * s
            draw.polygon([(x, y - d), (x + d, y), (x, y + d), (x - d, y)], fill=colour)
        else:
            draw.line([*at(cx, cy, r + 4 * s, degrees), x, y], fill=colour, width=int(round(6 * s)))

    for colour, base, reach in ((NORTH, 0, 0.80), (SOUTH, 180, 0.74)):
        draw.polygon(
            [at(cx, cy, r * reach, base), at(cx, cy, 15 * s, base + 90), at(cx, cy, 15 * s, base - 90)],
            fill=colour,
        )
    disc(draw, cx, cy, 22 * s, BG)
    ring(draw, cx, cy, 22 * s, HUB, 3 * s)
    disc(draw, cx, cy, 9 * s, SOUTH)

    draw.text((100 * s, 250 * s), "Kerteriz", font=ImageFont.truetype(FONT, int(96 * s)),
              fill=TEXT, anchor="lm")
    draw.line([100 * s, 312 * s, 380 * s, 312 * s], fill=DIM + (0x99,), width=int(round(2 * s)))

    image.resize((width, height), Image.LANCZOS).save(path, optimize=True)


def edge_color(shot):
    """Karenin kendi kenar rengi — dolgu buna uymazsa yanlarda bant görünür.

    Sabit bir renk yazmak yetmiyor: gündüz karesinin zemini #101418, gece
    karesininki tam siyah. Gecenin yanına #101418 konsaydı siyah şeridin
    iki yanında gri bir çerçeve belirirdi, mağaza sayfasında hata gibi durur.
    Kenar sütunları tek renk değilse (ki uygulamanın zemini düz, ama bir gün
    değişebilir) karar vermeye çalışmıyoruz, palet rengine düşüyoruz.
    """
    width, height = shot.size
    found = set()
    for box in ((0, 0, 1, height), (width - 1, 0, width, height)):
        # getcolors sınırı aşılınca None döner: sütun düz değil demektir.
        counted = shot.crop(box).getcolors(maxcolors=2)
        if not counted:
            return BG
        found.update(color for _, color in counted)
    return found.pop() if len(found) == 1 else BG


def padded_shots():
    """Ekran görüntülerinin Play sürümü.

    Play'in ölçüsünde uzun kenar kısa kenarın iki katını geçemiyor; 1080x2400
    bir telefon 2,22 ile sınırın dışında kalıyor. Kırpmak kadranın bir kısmını
    götürürdü, o yüzden yanlara karenin kendi zemin rengi ekleniyor — içerik
    bozulmuyor, yalnızca oran düzeliyor.

    Genişlik: uzun kenarın yarısı (en dar geçerli genişlik), artı sınıra bitişik
    durmamak için kırk piksel. 1080x2400 için 1240, yani oran 1,94.

    Kuralın tek uygulaması burası. `screenshots.sh` bir zamanlar kendi kopyasını
    üretiyordu; ikisinin ayrı düşmesi yanlış olanın yüklenmesi demek olurdu.
    """
    for source in sorted((ROOT / "docs").glob("ekran-*.png")):
        shot = Image.open(source).convert("RGB")
        width, height = shot.size
        if max(width, height) <= 2 * min(width, height):
            continue
        target = (height + 1) // 2 + 40
        frame = Image.new("RGB", (target, height), edge_color(shot))
        frame.paste(shot, ((target - width) // 2, 0))
        frame.save(OUT / f"{source.stem}-play.png", optimize=True)


def main():
    OUT.mkdir(exist_ok=True)
    icon(OUT / "ikon-512.png")
    feature(OUT / "one-cikan-1024x500.png")
    padded_shots()
    for f in sorted(OUT.glob("*.png")):
        with f.open("rb") as fh:
            head = fh.read(26)
        w = int.from_bytes(head[16:20], "big")
        h = int.from_bytes(head[20:24], "big")
        print(f"{f.relative_to(ROOT)}  {w}x{h}  {f.stat().st_size // 1024} KB")


if __name__ == "__main__":
    main()
