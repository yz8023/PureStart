#!/usr/bin/env bash
# PureStart (ModKit Edition) 构建脚本
# 不使用 Gradle：aapt2 + javac + d8 + zipalign + apksigner
#
# v15.0.7 起的硬性规矩（按外部分析报告 P0）：
#   1. 版本号唯一来源 = AndroidManifest.xml（module.prop / 输出文件名 / BuildVersion.java
#      全部由此派生，不再五处手改）；
#   2. 签名密钥不再自动生成：默认使用仓库内 debug.keystore（保证历代签名一致、可覆盖升级），
#      可用环境变量 PURESTART_KEYSTORE 指定正式密钥；缺失即报错退出（绝不静默换签名）；
#   3. ANDROID_HOME 必须由环境提供（不再内置作者本机路径）；
#   4. 构建前先跑 scripts/check_consistency.py（目标清单三处一致 + 版本一致 + 密钥在位）。
set -e
cd "$(dirname "$0")"

# === 环境检查 ===
if [ -z "$ANDROID_HOME" ]; then
  echo "ERROR: ANDROID_HOME 未设置（需要 platforms/android-35 + build-tools/34.0.0）" >&2
  exit 1
fi
ANDROID_JAR="$ANDROID_HOME/platforms/android-35/android.jar"
BT="$ANDROID_HOME/build-tools/34.0.0"
if [ ! -f "$ANDROID_JAR" ]; then echo "ERROR: 缺少 $ANDROID_JAR" >&2; exit 1; fi
if [ ! -x "$BT/aapt2" ]; then echo "ERROR: 缺少 build-tools/34.0.0（$BT）" >&2; exit 1; fi
AAPT2="$BT/aapt2"; D8="$BT/d8"; ZIPALIGN="$BT/zipalign"; APKSIGNER="$BT/apksigner"
API_JAR="libs/api.jar"

OUT=build
rm -rf $OUT
mkdir -p $OUT/classes $OUT/dex $OUT/gen

echo "[1/5] aapt2 compile + link (生成资源 + R.java)"
$AAPT2 compile --dir res -o $OUT/res.zip
$AAPT2 link -o $OUT/base.apk -I "$ANDROID_JAR" --manifest AndroidManifest.xml -R $OUT/res.zip --auto-add-overlay --java $OUT/gen

# === 版本号：唯一来源 AndroidManifest.xml，经 aapt2 dump badging 读链接产物（读不到即失败） ===
BADGING="$($AAPT2 dump badging $OUT/base.apk 2>/dev/null | head -1)"
VERSION_NAME="$(echo "$BADGING" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")"
VERSION_CODE="$(echo "$BADGING" | sed -n "s/.*versionCode='\([^']*\)'.*/\1/p")"
if [ -z "$VERSION_NAME" ] || [ -z "$VERSION_CODE" ]; then
  echo "ERROR: aapt2 dump badging 未能读出 versionName/versionCode" >&2; exit 1
fi
echo "版本（唯一来源 Manifest，aapt2 dump badging）：v$VERSION_NAME / $VERSION_CODE"

# module.prop 由 Manifest 派生（写回仓库副本保持同步）
sed -i "s/^version=.*/version=v$VERSION_NAME/" xposed/module.prop
sed -i "s/^versionCode=.*/versionCode=$VERSION_CODE/" xposed/module.prop

# === 漂移检查（scripts/check.py；置于 module.prop 写回之后） ===
if command -v python3 >/dev/null 2>&1; then
  python3 scripts/check.py || { echo "ERROR: 一致性检查未通过（见上）" >&2; exit 1; }
fi

echo "[1.5] 生成 BuildInfo.java（构建时间戳；版本号已改为运行时 PackageManager 读取，不再生成 BuildVersion）"
BUILD_TS="$(date '+%Y-%m-%d %H:%M')"
mkdir -p $OUT/gen/com/example/l
cat > $OUT/gen/com/example/l/BuildInfo.java <<EOF
package com.example.l;

/** 由 build.sh 自动生成（每次构建刷新），请勿手改 */
public final class BuildInfo {
    public static final String BUILD_TIME = "$BUILD_TS";
    private BuildInfo() {}
}
EOF

echo "[2/5] javac"
find src $OUT/gen -name "*.java" > $OUT/sources.txt
javac -encoding UTF-8 -source 17 -target 17 -cp "$ANDROID_JAR:$API_JAR" -d $OUT/classes @$OUT/sources.txt

echo "[3/5] d8"
MODCLASSES=$(find $OUT/classes -name "*.class")
$D8 --min-api 26 --output $OUT/dex $MODCLASSES --lib "$ANDROID_JAR"

echo "[4/5] add dex + META-INF/xposed"
cp $OUT/base.apk $OUT/unsigned.apk
( cd $OUT/dex && zip -q ../unsigned.apk classes.dex )
mkdir -p $OUT/xstage/META-INF/xposed
cp xposed/java_init.list $OUT/xstage/META-INF/xposed/java_init.list
cp xposed/module.prop    $OUT/xstage/META-INF/xposed/module.prop
cp xposed/scope.list     $OUT/xstage/META-INF/xposed/scope.list
( cd $OUT/xstage && zip -q -9 -r ../unsigned.apk META-INF )

echo "[5/5] zipalign + sign"
$ZIPALIGN -f -p 4 $OUT/unsigned.apk $OUT/aligned.apk
# P0-2：签名密钥只从环境变量读（KEYSTORE_PATH/KEYSTORE_PASS/KEY_ALIAS/KEY_PASS）。
# 缺失即报错退出，绝不自动生成——换签名 = 无法覆盖升级。
# 本仓库连续发布用的等价调用示例见 README（KEYSTORE_PATH=debug.keystore 等）。
if [ -z "$KEYSTORE_PATH" ] || [ ! -f "$KEYSTORE_PATH" ]; then
  echo "ERROR: 未设置 KEYSTORE_PATH 或文件不存在。" >&2
  echo "  用法：KEYSTORE_PATH=/path/ks.jks KEYSTORE_PASS=*** KEY_ALIAS=*** KEY_PASS=*** bash build.sh" >&2
  echo "  本地调试签名每台机器不同、不可覆盖安装历史版本（见 README）。" >&2
  exit 1
fi
: "${KEYSTORE_PASS:=android}" "${KEY_ALIAS:=androiddebugkey}" "${KEY_PASS:=android}"
OUT_APK="build/PureStart-v${VERSION_NAME}-release.apk"
$APKSIGNER sign --ks "$KEYSTORE_PATH" --ks-pass "pass:$KEYSTORE_PASS" \
  --ks-key-alias "$KEY_ALIAS" --key-pass "pass:$KEY_PASS" --out "$OUT_APK" $OUT/aligned.apk

echo "DONE -> $OUT_APK（版本号/文件名均派生自 Manifest，无第二处手改）"
