#!/usr/bin/env bash
#
# Mağaza ekran görüntülerini telefondan dil dil çeker.
#
#     tools/screenshots.sh                 # en, tr, de
#     tools/screenshots.sh en tr de fr es  # istenen diller
#     tools/screenshots.sh -i en tr        # her dilde ekranı elle kurarak
#
# Neden betik: Play'in mağaza sayfası dil başına ayrı ekran görüntüsü kabul
# ediyor ve uygulama yirmi sekiz dilde. Telefonun sistem dilini yirmi sekiz kez
# değiştirip geri almak yerine Android 13'ün "uygulama başına dil" ayarı
# kullanılıyor: yalnızca bu uygulamanın dili değişiyor, telefonun geri kalanı
# olduğu gibi kalıyor.
#
# Gerekenler:
#   * adb (Android platform-tools) ve USB hata ayıklaması açık bir telefon
#   * Android 13 (API 33) ve üstü — `cmd locale` o sürümde geldi
#   * uygulamanın telefonda kurulu olması
#   * telefonda konumun açık olması; kapalıyken kadranda kıble, güneş ve ay
#     görünmez, yani ekran görüntüsü uygulamayı eksik gösterir
#
# Çıktı: dist/ekran/<dil>-<sahne>.png

set -euo pipefail

PACKAGE="${PACKAGE:-com.aripd.kerteriz}"
OUT="${OUT:-dist/ekran}"
WARMUP="${WARMUP:-4}"      # uygulama açıldıktan sonra beklenen saniye
DEMO="${DEMO:-1}"          # durum çubuğunu düzene sok (saat 12:00, pil dolu)

interactive=0
if [ "${1:-}" = "-i" ]; then
  interactive=1
  shift
fi

locales=("$@")
[ ${#locales[@]} -eq 0 ] && locales=(en tr de)

# Elle kurulan sahneler. Otomatik kipte yalnızca ilki çekilir, çünkü gece modu
# ile ayarlar ekranına geçmek dokunma gerektiriyor ve dokunma yeri ekran
# boyutuna göre değişiyor — oraya "input tap x y" yazmak telefon değişince
# sessizce yanlış yere basardı.
scenes_interactive=(
  "gunduz:Gündüz kadranı — güneş, ay ve kaydedilmiş bir nokta görünsün"
  "gece:Gece modu — siyah zemin, kırmızı kadran"
  "ayarlar:Ayarlar ekranı"
)

# Mağaza görsellerinde sabit yerler kadranda görünmemeli; dini motif mağaza
# tarafında geçmiyor. Bunun için ayrıca bir şey yapmak gerekmiyor: hiçbir yer
# varsayılan açık değil, yani dokunulmadıkça kadran zaten temiz çıkar.
# Sınarken birini açtıysanız Ayarlar → Kadran işaretleri → Sabit yerler'den
# kapatın. Özellik uygulamanın içinde ve README'de yerinde duruyor.

say() { printf '%s\n' "$*" >&2; }
die() { printf 'hata: %s\n' "$*" >&2; exit 1; }

command -v adb >/dev/null || die "adb bulunamadı (Android platform-tools kurulu mu?)"

devices=$(adb devices | awk 'NR>1 && $2=="device" {print $1}')
count=$(printf '%s\n' "$devices" | grep -c . || true)
[ "$count" -eq 0 ] && die "bağlı telefon yok; USB hata ayıklamasını açıp izin verin"
if [ "$count" -gt 1 ] && [ -z "${ANDROID_SERIAL:-}" ]; then
  die "birden çok telefon bağlı; ANDROID_SERIAL ile birini seçin:
$devices"
fi

sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
[ "$sdk" -lt 33 ] && die "telefon Android 13'ten eski (API $sdk); uygulama başına dil ayarı yok"

adb shell pm path "$PACKAGE" >/dev/null 2>&1 || die "$PACKAGE telefonda kurulu değil"

# Başlatılacak etkinliği manifest'ten sor: sınıf adını betiğe gömmek paket adı
# değiştiğinde sessizce kırılırdı.
activity=$(adb shell cmd package resolve-activity --brief "$PACKAGE" | tail -1 | tr -d '\r')
[ -z "$activity" ] && die "$PACKAGE için başlatılacak etkinlik bulunamadı"

# Konum izni verilmemişse kadranda yarısı eksik bir ekran görüntüsü çıkar.
# Vermek zararsız: zaten kullanıcının kendi telefonu ve izin her an geri alınır.
for perm in ACCESS_COARSE_LOCATION ACCESS_FINE_LOCATION; do
  adb shell pm grant "$PACKAGE" "android.permission.$perm" 2>/dev/null || true
done

demo_on() {
  [ "$DEMO" = "1" ] || return 0
  adb shell settings put global sysui_demo_allowed 1 >/dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200 >/dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 >/dev/null
  adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false >/dev/null
}

demo_off() {
  [ "$DEMO" = "1" ] || return 0
  adb shell am broadcast -a com.android.systemui.demo -e command exit >/dev/null 2>&1 || true
}

restore_locale() {
  adb shell cmd locale set-app-locales "$PACKAGE" --user current --locales "" >/dev/null 2>&1 || true
}

# Yarıda kesilse bile telefon düzgün kalsın: demo kipi ve zorlanmış dil geri
# alınır. Bunlar telefonda kalıcı ayarlar, betiğin çöpü olarak bırakılamaz.
trap 'demo_off; restore_locale' EXIT INT TERM

shoot() {
  local file="$1"
  adb exec-out screencap -p > "$file"
  # `screencap -p` bazen boş dosya bırakıyor (ekran kapalıysa, ya da cihaz
  # uyanmadıysa); sessizce geçilmemeli.
  [ -s "$file" ] || die "$file boş çıktı — telefonun ekranı açık mı?"
}

# Play'in ölçüsü: her kenar 320-3840 piksel arasında ve uzun kenar kısa kenarın
# iki katını geçmeyecek. 1080x2400'lük bir telefonun ekranı 2,22 oranıyla bu
# sınırın dışında kalıyor — yani ham ekran görüntüsü olduğu gibi yüklenemiyor.
#
# Düzeltmeyi bu betik yapmıyor, yalnızca haber veriyor. Kırpmak kadranın bir
# kısmını götürürdü; doğrusu yanlara karenin kendi zemin rengini eklemek ve o
# renk sabit yazılamıyor — gündüz karesininki #101418, gece karesininki tam
# siyah, sabit yazılsaydı gecenin iki yanında gri bir çerçeve belirirdi. Kuralın
# tek uygulaması `tools/store-graphics.py` içinde; buradan da bir kopya
# üretilseydi ikisi ayrı düşer ve yanlış olan yüklenirdi.
report_size() {
  local file="$1"
  local size w h
  size=$(python3 - "$file" <<'PY'
import struct, sys
with open(sys.argv[1], 'rb') as f:
    head = f.read(26)
w, h = struct.unpack('>II', head[16:24])
print(w, h)
PY
)
  w=${size% *}; h=${size#* }

  if python3 -c "import sys; sys.exit(0 if max($w,$h) <= 2*min($w,$h) else 1)"; then
    say "    ${w}x${h} — Play ölçüsüne uyuyor"
  else
    say "    ${w}x${h} — oran 2:1'i aşıyor, Play ham hâlini kabul etmez."
    say "      Kare depoya girdikten sonra: python3 tools/store-graphics.py"
  fi
}

mkdir -p "$OUT"
demo_on

for locale in "${locales[@]}"; do
  say "── $locale"
  adb shell cmd locale set-app-locales "$PACKAGE" --user current --locales "$locale" >/dev/null
  adb shell am force-stop "$PACKAGE" >/dev/null
  adb shell am start -n "$activity" >/dev/null
  sleep "$WARMUP"

  if [ "$interactive" = "1" ]; then
    for scene in "${scenes_interactive[@]}"; do
      name="${scene%%:*}"; hint="${scene#*:}"
      printf '  %s — %s. Hazır olunca Enter: ' "$name" "$hint" >&2
      read -r _ </dev/tty
      shoot "$OUT/$locale-$name.png"
      say "  $OUT/$locale-$name.png"
      report_size "$OUT/$locale-$name.png"
    done
  else
    shoot "$OUT/$locale-gunduz.png"
    say "  $OUT/$locale-gunduz.png"
    report_size "$OUT/$locale-gunduz.png"
  fi
done

say ""
say "Bitti: $OUT"
say "Dil ayarı geri alındı, demo kipi kapatıldı."
