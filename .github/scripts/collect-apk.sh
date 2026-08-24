#!/usr/bin/env bash
#
# Üretilen APK'yı dist/ altına sürümü ve commit'iyle adlandırarak kopyalar,
# yanına SHA-256 özetini yazar.
#
# Adın anlamlı olması işe yarıyor: indirilen dosya "app-debug.apk" diye durunca
# hangi sürüm olduğu ancak kurup Ayarlar'a bakınca anlaşılıyor. Burada ad
# doğrudan söylüyor: compass-4.2-33-debug-1a2b3c4.apk
#
# Kullanım: collect-apk.sh <debug|release>

set -euo pipefail

variant="${1:-debug}"

version_name=$(grep -oE 'versionName[[:space:]]*=[[:space:]]*"[^"]+"' app/build.gradle.kts \
  | head -1 | cut -d'"' -f2)
version_code=$(grep -oE 'versionCode[[:space:]]*=[[:space:]]*[0-9]+' app/build.gradle.kts \
  | grep -oE '[0-9]+$' | head -1)

if [ -z "$version_name" ] || [ -z "$version_code" ]; then
  echo "::error::app/build.gradle.kts içinde versionName/versionCode okunamadı"
  exit 1
fi

short_sha=$(git rev-parse --short=7 HEAD 2>/dev/null || echo "${GITHUB_SHA:0:7}")

source_apk=$(find "app/build/outputs/apk/$variant" -maxdepth 1 -name '*.apk' 2>/dev/null | head -1)
if [ -z "$source_apk" ]; then
  echo "::error::app/build/outputs/apk/$variant altında APK bulunamadı"
  exit 1
fi

# İmzasız release APK'sı "app-release-unsigned.apk" adıyla çıkar. Ad bunu
# saklamamalı: imzasız APK telefona kurulamaz, indiren kişi bunu dosya adından
# görmeli.
case "$(basename "$source_apk")" in
  *unsigned*) label="$variant-imzasiz" ;;
  *)          label="$variant" ;;
esac

name="compass-$version_name-$version_code-$label-$short_sha"
mkdir -p dist
cp "$source_apk" "dist/$name.apk"
( cd dist && sha256sum "$name.apk" > "$name.apk.sha256" )

size=$(du -h "dist/$name.apk" | cut -f1)
digest=$(cut -d' ' -f1 < "dist/$name.apk.sha256")

if [ -n "${GITHUB_OUTPUT:-}" ]; then
  {
    echo "name=$name"
    echo "path=dist/$name.apk"
    echo "version=$version_name"
    echo "code=$version_code"
    echo "signed=$([ "$label" = "$variant" ] && echo true || echo false)"
  } >> "$GITHUB_OUTPUT"
fi

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "### APK: \`$name.apk\`"
    echo
    echo "| | |"
    echo "|---|---|"
    echo "| Sürüm | $version_name ($version_code) |"
    echo "| Tür | $label |"
    echo "| Boyut | $size |"
    echo "| SHA-256 | \`$digest\` |"
  } >> "$GITHUB_STEP_SUMMARY"
fi

echo "$name.apk  ($size)"
