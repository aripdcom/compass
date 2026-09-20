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

    python3 tools/check-translations.py

Bir sorun bulursa çıkış kodu 1 olur ve hepsini tek seferde listeler.
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

RES = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res"

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

    if problems:
        print(f"{len(problems)} sorun:", file=sys.stderr)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        return 1

    print(
        f"{len(locales) + 1} dil, {len(base_strings)} metin, "
        f"{len(base_arrays)} dizi — hepsi tutuyor."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
