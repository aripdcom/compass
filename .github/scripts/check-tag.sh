#!/usr/bin/env bash
#
# Etiketin build.gradle.kts'deki versionName ile tuttuğunu doğrular.
#
# Sürüm numarası iki yerde duruyor: derleme dosyasında ve etikette. Ayrı
# düştüklerinde ortaya "v4.3" diye yayımlanmış ama içinde 4.2 yazan bir APK
# çıkar; bu ancak telefona kurup Ayarlar'a bakınca fark edilir. Yayın burada,
# daha ilk adımda durur.
#
# Etiket iki yoldan gelebilir (bkz. release.yml): itilmiş bir etiketten ya da
# elle tetiklemede kutuya yazılan addan. İkincisinde ad doğrudan elle
# yazıldığı için biçim de burada sınanıyor.
#
# Kullanım: check-tag.sh v4.2

set -euo pipefail

tag="${1:?kullanım: check-tag.sh <etiket>}"

# Etiket itilerek gelindiğinde ad zaten `tags: ['v*']` süzgecinden geçiyor, ama
# elle tetiklemede kutuya ne yazıldığı belli olmaz. "4.4" yazılırsa aşağıdaki
# karşılaştırma versionName ile tutar ve sürüm `v`siz bir etiketle çıkar:
# depodaki diğer etiketlerle uyumsuz olur, o süzgece de bir daha takılmaz.
if [ "${tag#v}" = "$tag" ] || [ -z "${tag#v}" ]; then
  echo "::error::Etiket 'v' ile başlamalı (örn. v4.3); gelen: $tag"
  exit 1
fi

expected="${tag#v}"

actual=$(grep -oE 'versionName[[:space:]]*=[[:space:]]*"[^"]+"' app/build.gradle.kts \
  | head -1 | cut -d'"' -f2)

if [ "$expected" != "$actual" ]; then
  echo "::error::Etiket ($tag) ile versionName ($actual) uyuşmuyor." \
       "app/build.gradle.kts içindeki versionCode/versionName'i güncelleyip etiketi yeniden atın."
  exit 1
fi

echo "Etiket ve versionName uyuşuyor: $actual"
