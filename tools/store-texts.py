#!/usr/bin/env python3
"""Play mağaza listesindeki uzun açıklamayı yirmi sekiz dilde üretir.

    python3 tools/store-texts.py          # store/uzun-aciklama/ altına yazar

Metin sıfırdan çevrilmiyor, depoda **zaten yayımlanmış** cümlelerden
kuruluyor. Tanıtım sayfasının çevirileri (`docs/assets/i18n.js`) uzun
açıklamanın on bir bölümünün onunu birebir karşılıyor: tagline giriş cümlesi,
f1..f9 özellik bölümleri, secLangs/langsBody dil bölümü, privacy gizlilik
bölümü, heroNote da kapanış satırı.

Sebebi terimler. Denizcilik sözcüklerinin dile göre yerleşik karşılıkları var
ve genel amaçlı bir çevirici onları birebir çevirir: "great circle" Lehçede
Ortodroma, Fransızcada Orthodromie, Yunancada Μέγιστος κύκλος. Mağazada yazan
kelimenin uygulamanın içinde yazanla aynı olması, bu uygulamanın iddia ettiği
özenin göründüğü yer.

Eksik olan tek bölüm deniz mili ve loksodrom: o özellik PR #12'de geldi, site
metnine girmedi. Aşağıdaki NAUTICAL sözlüğü onu taşıyor ve elle yazıldı — ama
terimleri uydurulmadı, `values-*/strings.xml` içindeki onaylı karşılıklardan
alındı (`settings_course_rhumb`, `settings_distance_nautical`).

Çıktılar `store/uzun-aciklama/<dil>.txt`; Console'a kopyalanacak olan onlar.
`tools/check-translations.py` hepsini denetliyor: dil kümesi, Play'in 4000
karakter sınırı ve dini motif kuralı (17. bölüm).
"""

from __future__ import annotations

import html
import json
import pathlib
import re
import subprocess
import unicodedata

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "store" / "uzun-aciklama"

# Sitede İngilizce yok — o, index.html'in kendisinde duruyor (i18n.js'in
# başındaki nota bakın). Bu yüzden iki ayrı yerden okunuyor.
I18N = ROOT / "docs" / "assets" / "i18n.js"
INDEX = ROOT / "docs" / "index.html"

KEYS = ["tagline", "f1t", "f1b", "f2t", "f2b", "f3t", "f3b", "f4t", "f4b",
        "f5t", "f5b", "f6t", "f6b", "f7t", "f7b", "f8t", "f8b", "f9t",
        "secLangs", "langsBody", "privacy", "heroNote"]

# Deniz mili ve loksodrom bölümü: (başlık, gövde). Elle yazıldı; terimler
# values-*/strings.xml'den alındı.
NAUTICAL = {
    'en': (
        'Nautical miles and a steady heading',
        'Distances in metres and kilometres, or metres and nautical miles. Bearings to your own saved points can follow the great circle — the shortest way — or a rhumb line, the single heading you can hold from start to finish. The distance follows whichever you pick.',
    ),
    'bg': (
        'Морски мили и постоянен курс',
        'Разстоянията в метри и километри или в метри и морски мили. Азимутите към собствените ви запазени точки могат да следват големия кръг — най-краткия път — или локсодрома, единствения курс, който можете да държите от начало до край. Разстоянието следва избора ви.',
    ),
    'cs': (
        'Námořní míle a stálý kurz',
        'Vzdálenosti v metrech a kilometrech, nebo v metrech a námořních mílích. Azimuty k vlastním uloženým bodům mohou vést po ortodromě — nejkratší cestě — nebo po loxodromě, jediném kurzu, který lze udržet od začátku do konce. Vzdálenost se počítá podle toho, co zvolíte.',
    ),
    'da': (
        'Sømil og fast kurs',
        'Afstande i meter og kilometer, eller i meter og sømil. Pejlinger til dine egne gemte punkter kan følge storcirklen — den korteste vej — eller en loksodrom, den ene kurs du kan holde fra start til slut. Afstanden følger det, du vælger.',
    ),
    'de': (
        'Seemeilen und ein fester Kurs',
        'Entfernungen in Meter und Kilometer oder Meter und Seemeilen. Peilungen zu Ihren eigenen gespeicherten Punkten können dem Großkreis folgen — dem kürzesten Weg — oder einer Loxodrome, dem einen Kurs, den Sie von Anfang bis Ende halten können. Die Entfernung richtet sich danach, was Sie wählen.',
    ),
    'el': (
        'Ναυτικά μίλια και σταθερή πορεία',
        'Οι αποστάσεις σε μέτρα και χιλιόμετρα ή σε μέτρα και ναυτικά μίλια. Τα αζιμούθια προς τα δικά σας αποθηκευμένα σημεία μπορούν να ακολουθούν τον μέγιστο κύκλο — τη συντομότερη διαδρομή — ή μια λοξοδρομία, τη μοναδική πορεία που μπορείτε να κρατήσετε από την αρχή ως το τέλος. Η απόσταση ακολουθεί αυτό που επιλέγετε.',
    ),
    'es': (
        'Millas náuticas y rumbo constante',
        'Distancias en metros y kilómetros, o en metros y millas náuticas. Los rumbos a sus propios puntos guardados pueden seguir el círculo máximo — el más corto — o la loxodrómica, el único rumbo que puede mantener de principio a fin. La distancia sigue lo que elija.',
    ),
    'et': (
        'Meremiilid ja püsiv kurss',
        'Vahemaad meetrites ja kilomeetrites või meetrites ja meremiilides. Suunad sinu enda salvestatud kohtadeni võivad järgida suurringi — lühimat teed — või loksodroomi, seda ainsat kurssi, mida saab algusest lõpuni hoida. Vahemaa järgib seda, mille valid.',
    ),
    'fi': (
        'Meripeninkulmat ja tasainen kurssi',
        'Etäisyydet metreinä ja kilometreinä tai metreinä ja meripeninkulmina. Suuntimat omiin tallennettuihin pisteisiisi voivat seurata isoympyrää — lyhintä reittiä — tai loksodromia, sitä yhtä kurssia, jonka voi pitää alusta loppuun. Etäisyys määräytyy sen mukaan, kumman valitset.',
    ),
    'fr': (
        'Milles marins et cap constant',
        "Les distances en mètres et kilomètres, ou en mètres et milles marins. Les azimuts vers vos propres points enregistrés peuvent suivre l'orthodromie — le plus court — ou la loxodromie, le seul cap que vous pouvez tenir du début à la fin. La distance suit ce que vous choisissez.",
    ),
    'ga': (
        'Mílte mara agus cúrsa seasta',
        'Faid i méadair agus ciliméadair, nó i méadair agus mílte mara. Is féidir leis na treonna chuig na pointí a shábháil tú féin an ciorcal mór a leanúint — an tslí is giorra — nó lócsadróm, an t-aon chúrsa is féidir a choinneáil ó thús deireadh. Leanann an fhad cibé rud a roghnaíonn tú.',
    ),
    'hr': (
        'Nautičke milje i stalni kurs',
        'Udaljenosti u metrima i kilometrima ili u metrima i nautičkim miljama. Azimuti do vlastitih spremljenih točaka mogu ići ortodromom — najkraćim putem — ili loksodromom, jedinim kursom koji možete držati od početka do kraja. Udaljenost se mjeri prema onome što odaberete.',
    ),
    'hu': (
        'Tengeri mérföld és állandó irány',
        'A távolságok méterben és kilométerben, vagy méterben és tengeri mérföldben. A saját mentett pontjaihoz vezető irányok követhetik a főkört — a legrövidebb utat — vagy egy loxodromát, azt az egyetlen irányt, amelyet elejétől végéig tartani tud. A távolság ahhoz igazodik, amit választ.',
    ),
    'is': (
        'Sjómílur og föst stefna',
        'Vegalengdir í metrum og kílómetrum, eða í metrum og sjómílum. Stefnur á þína eigin vistuðu punkta geta fylgt stórbaug — stystu leið — eða loxodrómu, þeirri einu stefnu sem hægt er að halda frá upphafi til enda. Vegalengdin fylgir því sem þú velur.',
    ),
    'it': (
        'Miglia nautiche e rotta costante',
        "Distanze in metri e chilometri, oppure in metri e miglia nautiche. I rilevamenti verso i punti che avete salvato possono seguire il cerchio massimo — il più breve — o la lossodromia, l'unica rotta che si può tenere dall'inizio alla fine. La distanza segue quello che scegliete.",
    ),
    'lt': (
        'Jūrmylės ir pastovus kursas',
        'Atstumai metrais ir kilometrais arba metrais ir jūrmylėmis. Azimutai iki jūsų pačių išsaugotų taškų gali eiti ortodrome — trumpiausiu keliu — arba loksodrome, tuo vieninteliu kursu, kurio galima laikytis nuo pradžios iki galo. Atstumas skaičiuojamas pagal tai, ką pasirinksite.',
    ),
    'lv': (
        'Jūras jūdzes un nemainīgs kurss',
        'Attālumi metros un kilometros vai metros un jūras jūdzēs. Azimuti uz jūsu pašu saglabātajiem punktiem var sekot lielajam lokam — īsākajam ceļam — vai loksodromai, tam vienīgajam kursam, ko var noturēt no sākuma līdz beigām. Attālums seko tam, ko izvēlaties.',
    ),
    'mt': (
        'Mili nawtiċi u rotta kostanti',
        "Id-distanzi f'metri u kilometri, jew f'metri u mili nawtiċi. Id-direzzjonijiet lejn il-punti li ssalva int stess jistgħu jsegwu ċ-ċirku kbir — l-iqsar triq — jew loksodroma, l-unika rotta li tista' żżomm mill-bidu sal-aħħar. Id-distanza ssegwi dak li tagħżel.",
    ),
    'nb': (
        'Nautiske mil og fast kurs',
        'Avstander i meter og kilometer, eller i meter og nautiske mil. Peilinger til dine egne lagrede punkter kan følge storsirkelen — korteste vei — eller en loksodrom, den ene kursen du kan holde fra start til slutt. Avstanden følger det du velger.',
    ),
    'nl': (
        'Zeemijlen en een vaste koers',
        'Afstanden in meters en kilometers, of in meters en zeemijlen. Peilingen naar uw eigen opgeslagen punten kunnen de grootcirkel volgen — de kortste weg — of een loxodroom, de ene koers die u van begin tot eind kunt aanhouden. De afstand volgt wat u kiest.',
    ),
    'nn': (
        'Nautiske mil og fast kurs',
        'Avstandar i meter og kilometer, eller i meter og nautiske mil. Peilingar til dine eigne lagra punkt kan følgje storsirkelen — kortaste vegen — eller ein loksodrom, den eine kursen du kan halde frå start til slutt. Avstanden følgjer det du vel.',
    ),
    'pl': (
        'Mile morskie i stały kurs',
        'Odległości w metrach i kilometrach albo w metrach i milach morskich. Azymuty do własnych zapisanych punktów mogą biec ortodromą — najkrótszą drogą — albo loksodromą, jedynym kursem, który da się utrzymać od początku do końca. Odległość liczy się zgodnie z wyborem.',
    ),
    'pt': (
        'Milhas náuticas e rumo constante',
        'Distâncias em metros e quilómetros, ou em metros e milhas náuticas. Os rumos para os seus próprios pontos guardados podem seguir o círculo máximo — o mais curto — ou a loxodrómica, o único rumo que se consegue manter do início ao fim. A distância segue o que escolher.',
    ),
    'ro': (
        'Mile marine și cap constant',
        'Distanțele în metri și kilometri sau în metri și mile marine. Azimuturile către propriile puncte salvate pot urma cercul mare — drumul cel mai scurt — sau o loxodromă, singurul cap pe care îl puteți ține de la început până la sfârșit. Distanța urmează ce alegeți.',
    ),
    'sk': (
        'Námorné míle a stály kurz',
        'Vzdialenosti v metroch a kilometroch, alebo v metroch a námorných míľach. Azimuty k vlastným uloženým bodom môžu viesť po ortodróme — najkratšej ceste — alebo po loxodróme, jedinom kurze, ktorý sa dá udržať od začiatku do konca. Vzdialenosť sa počíta podľa toho, čo zvolíte.',
    ),
    'sl': (
        'Navtične milje in stalni kurz',
        'Razdalje v metrih in kilometrih ali v metrih in navtičnih miljah. Azimuti do lastnih shranjenih točk lahko sledijo ortodromi — najkrajši poti — ali loksodromi, edinemu kurzu, ki ga lahko držite od začetka do konca. Razdalja sledi temu, kar izberete.',
    ),
    'sv': (
        'Nautiska mil och fast kurs',
        'Avstånd i meter och kilometer, eller i meter och nautiska mil. Bäringar till dina egna sparade punkter kan följa storcirkeln — den kortaste vägen — eller en loxodrom, den enda kurs du kan hålla från början till slut. Avståndet följer det du väljer.',
    ),
    'tr': (
        'Deniz mili ve sabit pruva',
        'Uzaklıklar metre ve kilometre ya da metre ve deniz mili. Kendi kaydettiğiniz noktalara kerteriz, büyük daireyi — en kısa yolu — ya da loksodromu, baştan sona koruyabileceğiniz tek pruvayı izleyebilir. Mesafe hangisini seçtiyseniz ona göre ölçülür.',
    ),
}


def upper(text: str, locale: str) -> str:
    """Bölüm başlıklarını büyük harfe çevirir. İki dilin kendi kuralı var.

    Türkçede `i`nin büyüğü `İ`, `ı`nınki `I`; Python'ın varsayılanı ikisini de
    `I` yapar ve "IZLEME", "SABIT" gibi yanlış kelimeler çıkar.

    Yunancada tümü büyük yazımda tonos düşer: "Αληθής" → "ΑΛΗΘΗΣ". Python
    tonosu koruduğu için ayrıca temizleniyor. Temizlik yalnızca Yunancada
    yapılır — Fransızcada É anlam taşır, düşürülemez.
    """
    if locale == "tr":
        text = text.replace("i", "İ").replace("ı", "I")
    text = text.upper()
    if locale == "el":
        text = unicodedata.normalize("NFC", "".join(
            c for c in unicodedata.normalize("NFD", text)
            if not unicodedata.combining(c)
        ))
    return text


def read_site() -> dict[str, dict[str, str]]:
    """i18n.js'i Node'a okutup sözlüğü JSON olarak alır.

    Dosya bir JavaScript nesnesi, JSON değil: yorumlar ve tırnaksız anahtarlar
    var. Elle ayrıştırmak yerine onu zaten çalıştırabilen şeye çalıştırılıyor.
    """
    script = (
        'const fs=require("fs");global.window={};'
        f'eval(fs.readFileSync({str(I18N)!r},"utf8"));'
        'console.log(JSON.stringify(window.COMPASS_I18N));'
    )
    data = json.loads(subprocess.run(
        ["node", "-e", script], capture_output=True, text=True, check=True
    ).stdout)
    data["en"] = read_english()
    return data


def read_english() -> dict[str, str]:
    """İngilizce metinleri index.html'deki `data-i18n` etiketlerinden çıkarır."""
    source = INDEX.read_text(encoding="utf-8")
    out = {}
    for key in KEYS:
        match = re.search(
            rf'data-i18n="{key}"[^>]*>(.*?)</(?:h[1-6]|p|li|span|a|div)>',
            source, re.S)
        if match:
            text = html.unescape(re.sub(r"<[^>]+>", "", match.group(1)))
            out[key] = re.sub(r"\s+", " ", text).strip()
    return out


def compose(locale: str, strings: dict[str, str]) -> str:
    """Bir dilin uzun açıklamasını kurar."""
    parts = [strings["tagline"], ""]
    for n in "123456":
        parts += [upper(strings[f"f{n}t"], locale), strings[f"f{n}b"], ""]
    title, body = NAUTICAL[locale]
    parts += [upper(title, locale), body, ""]
    for n in "78":
        parts += [upper(strings[f"f{n}t"], locale), strings[f"f{n}b"], ""]
    parts += [upper(strings["secLangs"], locale), strings["langsBody"], ""]
    # f9b kullanılmıyor: ilk cümlesi kurulum boyutunu veriyor ve o sayı
    # sürümden sürüme kayıyor. privacy aynı şeyi boyutsuz anlatıyor.
    parts += [upper(strings["f9t"], locale), strings["privacy"], "",
              strings["heroNote"], ""]
    parts += ["Source code: github.com/aripdcom/kerteriz",
              "Privacy policy: kerteriz.aripd.com/privacy/"]
    return "\n".join(parts) + "\n"


def main() -> None:
    site = read_site()
    OUT.mkdir(parents=True, exist_ok=True)
    for locale in sorted(NAUTICAL):
        text = compose(locale, site[locale])
        (OUT / f"{locale}.txt").write_text(text, encoding="utf-8")
        print(f"{locale}  {len(text) - 1:>5} karakter")


if __name__ == "__main__":
    main()
