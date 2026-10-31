#!/usr/bin/env bash
# Linux x86_64 bootstrap for the experimental Compose build. No signing keys
# are created or configured for release builds.
set -euo pipefail
if [[ "${ACCEPT_ANDROID_SDK_LICENSES:-}" != 1 ]]; then
  echo 'Review Android SDK licences first, then rerun with ACCEPT_ANDROID_SDK_LICENSES=1.' >&2
  echo 'https://developer.android.com/studio/terms' >&2
  exit 1
fi
ROOT="${PURESTART_TOOLS:-$HOME/.cache/purestart-tools}"
JDK="$ROOT/jdk17"; SDK="$ROOT/android-sdk"
mkdir -p "$JDK" "$SDK/cmdline-tools"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
if [[ ! -x "$JDK/bin/java" ]]; then
  curl -fL --retry 2 'https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse' -o "$tmp/jdk.tar.gz"
  tar -xzf "$tmp/jdk.tar.gz" --strip-components=1 -C "$JDK"
fi
if [[ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]]; then
  curl -fL --retry 2 https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o "$tmp/cmdline.zip"
  unzip -q "$tmp/cmdline.zip" -d "$SDK/cmdline-tools"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
fi
export JAVA_HOME="$JDK" ANDROID_HOME="$SDK" PATH="$JDK/bin:$PATH"
# sdkmanager may exit after consuming input; do not treat yes receiving SIGPIPE as failure.
set +o pipefail
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses
set -o pipefail
"$SDK/cmdline-tools/latest/bin/sdkmanager" 'platforms;android-35' 'platforms;android-36' 'build-tools;34.0.0' 'build-tools;35.0.0' 'platform-tools'
printf '\nFor this shell:\nexport JAVA_HOME=%q ANDROID_HOME=%q\nexport PATH="$JAVA_HOME/bin:$PATH"\n' "$JAVA_HOME" "$ANDROID_HOME"
