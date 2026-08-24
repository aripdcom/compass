#!/usr/bin/env bash
#
# compileSdk'yı derleme dosyasından okur ve o platformun koşucuda bulunmasını
# sağlar.
#
# Sürüm burada ikinci kez yazılmıyor. GitLab yapılandırmasında imaj etiketi
# (`android-sdk:35`) elle sabitlenmişti ve compileSdk yükseldiğinde onunla
# birlikte güncellenmesi gerekiyordu; unutulduğunda derleme sebepsiz kırılır.
# Tek doğru kaynak app/build.gradle.kts.

set -uo pipefail

compile_sdk=$(grep -oE 'compileSdk[[:space:]]*=[[:space:]]*[0-9]+' app/build.gradle.kts \
  | grep -oE '[0-9]+$' | head -1)

if [ -z "$compile_sdk" ]; then
  echo "::error::app/build.gradle.kts içinde compileSdk okunamadı"
  exit 1
fi
echo "compileSdk = $compile_sdk"

sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
sdkmanager="$sdk_root/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "$sdkmanager" ]; then
  echo "::notice::sdkmanager bulunamadı; koşucunun hazır SDK'sıyla devam ediliyor"
  exit 0
fi

yes | "$sdkmanager" --licenses >/dev/null 2>&1 || true

# Bileşen zaten kuruluysa hızla döner. Kurulamazsa koşu burada kırılmaz:
# eksikse asıl hatayı derleme adımı çok daha anlaşılır biçimde veriyor.
if ! "$sdkmanager" "platforms;android-$compile_sdk" "build-tools;$compile_sdk.0.0" >/dev/null; then
  echo "::warning::platforms;android-$compile_sdk veya build-tools;$compile_sdk.0.0 kurulamadı"
fi
