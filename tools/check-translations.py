#!/usr/bin/env python3
"""Çeviri dosyalarını varsayılanla (values/strings.xml) karşılaştırır.

Uygulama yirmi sekiz dilde ve metinler elle tutuluyor; gözden kaçması en kolay
üç hata şu üçü:

  * eksik ya da fazla anahtar — Android eksik anahtarda varsayılana düşer,
    yani ekranın yarısı bir dilde yarısı İngilizce çıkar;
  * bozuk biçim belirteci — `%1$d` yerine `%1$s` yazıldığında uygulama o
    satırı çizerken çöker, hem de yalnızca o dildeki cihazlarda;
  * dizilerin uzunluğunun tutmaması — on altı kısaltma yerine on beşi olan
    bir dilde pusula kadranı çizilirken dizi taşar.

Üçü de derlemeyi kırmaz, testlerde de görünmez. Bu betik CI'da koşar.

Dördüncü olarak `store/kisa-aciklama.tsv` denetleniyor: mağaza listesindeki
kısa açıklama uygulamanın dışında yaşıyor ama aynı dil kümesini taşımalı ve
Play'in seksen karakter sınırına uymalı. Yeni bir dil eklenip orası
unutulursa mağaza o dilde İngilizce görünür, ve bunu kimse fark etmez.

    python3 tools/check-translations.py

Bir sorun bulursa çıkış kodu 1 olur ve hepsini tek seferde listeler.
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"

# Play'in kısa açıklama sınırı. Console fazlasını kabul etmiyor, ama hatayı
# ancak yapıştırma anında veriyor — yirmi sekiz dili tek tek denemek yerine
# burada sayılıyor.
SHORT_LIMIT = 80
STORE_SHORT = ROOT / "store" / "kisa-aciklama.tsv"

# Uzun açıklamanın sınırı ve yeri (tools/store-texts.py üretiyor).
LONG_LIMIT = 4000
STORE_LONG = ROOT / "store" / "uzun-aciklama"

# Dini motifler mağaza ve site metinlerinde geçmez (README 17). Bu kural bir
# kez çiğnendi: uygulama içi bir ayar notu kıbleden söz ediyordu ve doğrudan
# mağaza ekran görüntüsüne düştü. Metin tarafında aynısı olmasın diye özel
# adlar ve "kıble"nin dillerdeki karşılıkları burada taranıyor. Liste
# uygulamanın ne yaptığını gizlemek için değil: on bir sabit yerin hepsi
# adlarıyla uygulamanın içinde duruyor, dışarıya dönük metin "sabit yerler"
# diyor çünkü terim on birini birden kapsıyor.
RELIGIOUS = re.compile(
    r"k[ıi]ble|qibla|kibla|kiblat|qybl|"
    r"k[âa]be|kaaba|mecca|mekke|"
    r"mescid|masjid|aqsa|aksa|"
    r"vatican|vatikan|vatikaan|vatikanas",
    re.IGNORECASE,
)

# %1$s, %2$d, %% ... — sıraları ve türleri diller arasında birebir aynı olmalı.
FORMAT = re.compile(r"%(?:%|(\d+)\$([a-zA-Z]))")


def specifiers(text: str) -> set[str]:
    """Metindeki biçim belirteçleri: {'1$s', '2$d'}. Yüzde işareti sayılmaz."""
    return {f"{m.group(1)}${m.group(2)}" for m in FORMAT.finditer(text) if m.group(1)}


def read(path: Path) -> tuple[dict[str, str], dict[str, list[str]]]:
    root = ET.parse(path).getroot()
    strings = {
        e.attrib["name"]: "".join(e.itertext())
        for e in root.findall("string")
    }
    arrays = {
        e.attrib["name"]: ["".join(i.itertext()) for i in e.findall("item")]
        for e in root.findall("string-array")
    }
    return strings, arrays


def check_store_short(expected: set[str], problems: list[str]) -> int:
    """Mağaza kısa açıklamaları: diller tutuyor mu, hiçbiri sınırı aşıyor mu.

    Dosya uygulamanın metinlerinden ayrı yaşıyor ama aynı dil kümesini
    taşımalı: yeni bir dil eklenip burası unutulursa mağaza o dilde İngilizce
    görünür ve bunu kimse fark etmez.
    """
    if not STORE_SHORT.exists():
        problems.append(f"{STORE_SHORT.relative_to(ROOT)} yok")
        return 0

    found: set[str] = set()
    for number, line in enumerate(STORE_SHORT.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip() or line.startswith("#"):
            continue
        if "\t" not in line:
            problems.append(f"kisa-aciklama {number}. satırda sekme yok")
            continue
        locale, text = line.split("\t", 1)
        if locale in found:
            problems.append(f"kisa-aciklama: `{locale}` iki kez geçiyor")
        found.add(locale)
        if len(text) > SHORT_LIMIT:
            problems.append(
                f"kisa-aciklama: `{locale}` {len(text)} karakter, sınır {SHORT_LIMIT}"
            )
        if match := RELIGIOUS.search(text):
            problems.append(
                f"kisa-aciklama: `{locale}` dini motif — `{match.group()}` (17. bölüm)"
            )

    for locale in sorted(expected - found):
        problems.append(f"kisa-aciklama: `{locale}` eksik")
    for locale in sorted(found - expected):
        problems.append(f"kisa-aciklama: `{locale}` fazla — uygulamada böyle bir dil yok")
    return len(found)


def check_store_long(expected: set[str], problems: list[str]) -> int:
    """Uzun açıklamalar: diller, Play'in karakter sınırı, dini motif kuralı."""
    if not STORE_LONG.is_dir():
        problems.append(f"{STORE_LONG.relative_to(ROOT)} yok")
        return 0

    found: set[str] = set()
    for path in sorted(STORE_LONG.glob("*.txt")):
        locale = path.stem
        found.add(locale)
        text = path.read_text(encoding="utf-8")
        if len(text) > LONG_LIMIT:
            problems.append(
                f"uzun-aciklama/{locale}: {len(text)} karakter, sınır {LONG_LIMIT}"
            )
        if match := RELIGIOUS.search(text):
            problems.append(
                f"uzun-aciklama/{locale}: dini motif — `{match.group()}` (17. bölüm)"
            )

    for locale in sorted(expected - found):
        problems.append(f"uzun-aciklama: `{locale}` eksik")
    for locale in sorted(found - expected):
        problems.append(f"uzun-aciklama: `{locale}` fazla — uygulamada böyle bir dil yok")
    return len(found)


def main() -> int:
    default = RES / "values" / "strings.xml"
    base_strings, base_arrays = read(default)
    problems: list[str] = []

    locales = sorted(
        p for p in RES.glob("values-*/strings.xml")
        # values-night, values-v31 gibi nitelikler dil değildir.
        if re.fullmatch(r"values-[a-z]{2,3}(-r[A-Z]{2})?", p.parent.name)
    )
    if not locales:
        problems.append("hiç çeviri dosyası bulunamadı")

    for path in locales:
        locale = path.parent.name.removeprefix("values-")
        try:
            strings, arrays = read(path)
        except ET.ParseError as exc:
            problems.append(f"{locale}: XML okunamadı — {exc}")
            continue

        for name in sorted(set(base_strings) - set(strings)):
            problems.append(f"{locale}: eksik metin `{name}`")
        for name in sorted(set(strings) - set(base_strings)):
            problems.append(f"{locale}: varsayılanda olmayan metin `{name}`")
        for name in sorted(set(base_arrays) - set(arrays)):
            problems.append(f"{locale}: eksik dizi `{name}`")
        for name in sorted(set(arrays) - set(base_arrays)):
            problems.append(f"{locale}: varsayılanda olmayan dizi `{name}`")

        for name, text in sorted(strings.items()):
            if name not in base_strings:
                continue
            want, got = specifiers(base_strings[name]), specifiers(text)
            if want != got:
                problems.append(
                    f"{locale}: `{name}` biçim belirteçleri tutmuyor — "
                    f"beklenen {sorted(want) or '—'}, bulunan {sorted(got) or '—'}"
                )
            if not text.strip():
                problems.append(f"{locale}: `{name}` boş")

        for name, items in sorted(arrays.items()):
            if name not in base_arrays:
                continue
            if len(items) != len(base_arrays[name]):
                problems.append(
                    f"{locale}: `{name}` {len(items)} öğe, beklenen "
                    f"{len(base_arrays[name])}"
                )
            for index, item in enumerate(items):
                if not item.strip():
                    problems.append(f"{locale}: `{name}` {index}. öğesi boş")

    # locales_config.xml ile klasörler aynı kümeyi göstermeli: listede olup
    # karşılığı olmayan dil, sistemin dil seçicisinde görünüp İngilizce açılır.
    config = RES / "xml" / "locales_config.xml"
    listed = {
        e.attrib["{http://schemas.android.com/apk/res/android}name"]
        for e in ET.parse(config).getroot().findall("locale")
    }
    present = {"en"} | {p.parent.name.removeprefix("values-") for p in locales}
    for locale in sorted(listed - present):
        problems.append(f"locales_config: `{locale}` listede ama çevirisi yok")
    for locale in sorted(present - listed):
        problems.append(f"locales_config: `{locale}` çevirisi var ama listede yok")

    store_count = check_store_short(present, problems)
    long_count = check_store_long(present, problems)

    if problems:
        print(f"{len(problems)} sorun:", file=sys.stderr)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        return 1

    print(
        f"{len(locales) + 1} dil, {len(base_strings)} metin, "
        f"{len(base_arrays)} dizi, {store_count} kısa ve {long_count} uzun "
        f"mağaza açıklaması — hepsi tutuyor."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
