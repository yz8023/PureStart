#!/usr/bin/env bash
# Compose build. The existing certificate must be supplied; never generate a key.
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_HOME:?请设置 ANDROID_HOME}"
: "${JAVA_HOME:?请设置 JDK 17 的 JAVA_HOME}"
: "${KEYSTORE_PATH:?必须提供现有签名密钥 KEYSTORE_PATH}"
if [[ ! -f "$KEYSTORE_PATH" ]]; then echo "ERROR: 签名密钥不存在" >&2; exit 1; fi
export PATH="$JAVA_HOME/bin:$PATH"
python3 scripts/check.py
bash gradlew :app:assembleRelease --no-daemon --max-workers=1
