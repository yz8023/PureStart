#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

# === Xposed 模块构建脚本 (CodeForge 生成) ===
# 不使用 Gradle，直接用 javac + d8 + aapt2 + apksigner
# 2026-10-09 修改: aapt2 提前执行并生成 R.java，供 javac 引用 R 资源

ANDROID_JAR="${ANDROID_HOME:-/home/user/android-sdk}/platforms/android-35/android.jar"
BT="${ANDROID_HOME:-/home/user/android-sdk}/build-tools/34.0.0"
AAPT2="$BT/aapt2"
D8="$BT/d8"
ZIPALIGN="$BT/zipalign"
APKSIGNER="$BT/apksigner"
API_JAR="libs/api.jar"

OUT=build
rm -rf $OUT
mkdir -p $OUT/classes $OUT/dex $OUT/gen

echo "[1/5] aapt2 compile + link (生成资源 + R.java)"
$AAPT2 compile --dir res -o $OUT/res.zip
$AAPT2 link -o $OUT/base.apk -I "$ANDROID_JAR" --manifest AndroidManifest.xml -R $OUT/res.zip --auto-add-overlay --java $OUT/gen

echo "[1.5] 生成构建时间 BuildInfo.java（每次构建自动刷新）"
BUILD_TS="$(date '+%Y-%m-%d %H:%M')"
mkdir -p $OUT/gen/com/example/l
cat > $OUT/gen/com/example/l/BuildInfo.java <<EOF
package com.example.l;

/** 由 build.sh 自动生成（每次构建刷新），请勿手改 */
public final class BuildInfo {
    public static final String BUILD_TIME = "$BUILD_TS";
    private BuildInfo() {
    }
}
EOF

echo "[2/5] javac"
find src $OUT/gen -name "*.java" > $OUT/sources.txt
javac -encoding UTF-8 -source 8 -target 8 -cp "$ANDROID_JAR:$API_JAR" -d $OUT/classes @$OUT/sources.txt

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
if [ ! -f debug.keystore ]; then
  keytool -genkeypair -keystore debug.keystore -storepass android -keypass android -alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -keysize 2048 -validity 10000
fi
$APKSIGNER sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android --out $OUT/purestart-modkit-v15.0.1.apk $OUT/aligned.apk

echo "DONE -> $OUT/purestart-modkit-v15.0.1.apk"
cp -f $OUT/purestart-modkit-v15.0.1.apk . 2>/dev/null || true
