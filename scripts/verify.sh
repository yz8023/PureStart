#!/usr/bin/env bash
# 一次跑完全部门禁：静态契约 + 纯 JVM 契约测试。
# 真机 / LSPosed / 目标应用行为仍需人工验收，本脚本不能替代。
set -uo pipefail
cd "$(dirname "$0")/.."
failed=0
run() { echo "== $*"; "$@" || { echo "   ^^ 失败"; failed=1; }; }

run python3 scripts/check.py
run python3 tests/check_ui_contract.py
run python3 tests/check_glass_surfaces.py
run python3 tests/check_backend_unchanged.py
run python3 tests/test_settings_repository.py
run python3 tests/test_backup_repository.py

if [[ -n "${ANDROID_HOME:-}" ]]; then
  run python3 tests/check_apk.py
else
  echo "== tests/check_apk.py 跳过：未设置 ANDROID_HOME（且需要已构建的 release APK）"
fi

echo
if (( failed )); then
  echo "门禁未通过"
  exit 1
fi
echo "门禁全部通过（静态 + JVM 契约）。真机验收另见 README「验证」一节。"
