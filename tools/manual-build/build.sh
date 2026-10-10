#!/bin/bash
# App Inspector 一键构建脚本（不依赖 Gradle）
# 用法:  ./build.sh
# 环境变量:
#   MB_WORK      构建工作目录（默认: 脚本目录下的 build-work）
#   ANDROID_HOME Android SDK 目录（默认: $HOME/android-sdk，不存在会自动下载安装）
#   JAVA_HOME    JDK 17+（不设置则自动查找）
#
# 产物: <仓库根目录>/AppInspector-v1.0-debug.apk
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
WORK="${MB_WORK:-$SCRIPT_DIR/build-work}"
TOOLS_DIR="$WORK/tools"
DEPS_DIR="$WORK/deps"
export MB_WORK="$WORK"

KOTLIN_VER="2.0.21"
COMPOSE_PLUGIN_VER="2.0.21"
ANDROID_API="35"
BUILD_TOOLS_VER="35.0.0"
MIN_SDK="26"

log() { echo "[$(date +%H:%M:%S)] $*"; }
die() { echo "ERROR: $*" >&2; exit 1; }

# ---------- 0. 检查基础工具 ----------
for cmd in python3 curl unzip zip; do
  command -v "$cmd" >/dev/null 2>&1 || die "缺少命令: $cmd，请先安装"
done

# ---------- 1. JDK 17+ ----------
if [ -z "${JAVA_HOME:-}" ]; then
  for cand in "$HOME"/tools/jdk-17* /usr/lib/jvm/*17* /usr/lib/jvm/*21* /opt/jdk-17* /opt/java*17*; do
    if [ -x "$cand/bin/java" ]; then JAVA_HOME="$cand"; break; fi
  done
fi
[ -n "${JAVA_HOME:-}" ] || die "找不到 JDK 17+，请设置 JAVA_HOME"
export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"
JAVA_MAJOR=$(java -version 2>&1 | head -1 | grep -oP '(?<=version ")[0-9]+' || echo 0)
[ "$JAVA_MAJOR" -ge 17 ] || die "JDK 版本过低 ($JAVA_MAJOR)，需要 17+"
log "JDK: $JAVA_HOME (java $JAVA_MAJOR)"

# ---------- 2. Android SDK ----------
ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_HOME
BT="$ANDROID_HOME/build-tools/$BUILD_TOOLS_VER"
PLATFORM_JAR="$ANDROID_HOME/platforms/android-$ANDROID_API/android.jar"
if [ ! -x "$BT/aapt2" ] || [ ! -f "$PLATFORM_JAR" ]; then
  log "下载安装 Android SDK（platform android-$ANDROID_API + build-tools $BUILD_TOOLS_VER）..."
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  cd "$ANDROID_HOME/cmdline-tools"
  if [ ! -d latest ]; then
    curl -sSL -o cmdtools.zip \
      https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
    rm -rf latest && mkdir latest && unzip -q cmdtools.zip -d latest_tmp \
      && mv latest_tmp/cmdline-tools/* latest/ && rm -rf latest_tmp cmdtools.zip
  fi
  export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
  mkdir -p "$ANDROID_HOME/licenses"
  printf '\nd56f5187479451eabf01fb78af6dfcb131a6481e2' > "$ANDROID_HOME/licenses/android-sdk-license"
  sdkmanager --install "platforms;android-$ANDROID_API" "build-tools;$BUILD_TOOLS_VER" 2>&1 | tail -2
fi
[ -x "$BT/aapt2" ] || die "aapt2 不可用: $BT"
[ -f "$PLATFORM_JAR" ] || die "android.jar 不可用: $PLATFORM_JAR"
AAPT2="$BT/aapt2"; D8="$BT/d8"; APKSIGNER="$BT/apksigner"; ZIPALIGN="$BT/zipalign"; AAPT="$BT/aapt"
log "Android SDK: $ANDROID_HOME"

mkdir -p "$WORK" "$TOOLS_DIR" "$DEPS_DIR"

# ---------- 3. 下载 Kotlin 编译器（embeddable）与 Compose 插件 ----------
EMBEDDABLE_JAR="$TOOLS_DIR/kotlin-compiler-embeddable.jar"
COMPOSE_PLUGIN_JAR="$TOOLS_DIR/compose-plugin.jar"
TROVE_JAR="$TOOLS_DIR/trove4j.jar"
if [ ! -s "$EMBEDDABLE_JAR" ]; then
  log "下载 kotlin-compiler-embeddable $KOTLIN_VER ..."
  curl -sSL -o "$EMBEDDABLE_JAR" \
    "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-compiler-embeddable/$KOTLIN_VER/kotlin-compiler-embeddable-$KOTLIN_VER.jar"
fi
if [ ! -s "$COMPOSE_PLUGIN_JAR" ]; then
  log "下载 compose compiler plugin $COMPOSE_PLUGIN_VER ..."
  curl -sSL -o "$COMPOSE_PLUGIN_JAR" \
    "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-compose-compiler-plugin-embeddable/$COMPOSE_PLUGIN_VER/kotlin-compose-compiler-plugin-embeddable-$COMPOSE_PLUGIN_VER.jar"
fi
if [ ! -s "$TROVE_JAR" ]; then
  log "下载 trove4j ..."
  curl -sSL -o "$TROVE_JAR" \
    "https://repo1.maven.org/maven2/org/jetbrains/intellij/deps/trove4j/1.0.20200330/trove4j-1.0.20200330.jar"
fi

# ---------- 4. 解析并下载 Maven 依赖 ----------
log "解析 Maven 依赖..."
python3 "$SCRIPT_DIR/resolve.py"

# ---------- 5. 解包 AAR ----------
log "解包 AAR..."
python3 "$SCRIPT_DIR/process_aar.py"

# ---------- 6. 消除 classpath 中的重复类 ----------
log "消除重复类..."
python3 "$SCRIPT_DIR/dedupe_classpath.py"

# ---------- 7. aapt2 compile ----------
log "aapt2 compile 资源..."
rm -rf "$WORK/compiled" && mkdir -p "$WORK/compiled"
"$AAPT2" compile --dir "$REPO_ROOT/app/src/main/res" -o "$WORK/compiled/app-res.zip"
while read -r resdir; do
  [ -n "$resdir" ] || continue
  name=$(echo "$resdir" | sed 's|.*/aar/||; s|/|_|g')
  "$AAPT2" compile --dir "$resdir" -o "$WORK/compiled/$name.zip"
done < "$WORK/resdirs.txt"
log "compiled $(ls "$WORK"/compiled/*.zip | wc -l) 个资源包"

# ---------- 8. aapt2 link ----------
log "aapt2 link..."
rm -rf "$WORK/gen" "$WORK/gen-lib" "$WORK/base.apk" && mkdir -p "$WORK/gen"
RARGS=""
for f in "$WORK"/compiled/*.zip; do RARGS="$RARGS -R $f"; done
# shellcheck disable=SC2086
"$AAPT2" link -o "$WORK/base.apk" \
  -I "$PLATFORM_JAR" \
  --manifest "$REPO_ROOT/app/src/main/AndroidManifest.xml" \
  --min-sdk-version "$MIN_SDK" --target-sdk-version "$ANDROID_API" \
  --auto-add-overlay \
  --java "$WORK/gen" \
  --output-text-symbols "$WORK/R.txt" \
  $RARGS
log "link 完成: $(du -h "$WORK/base.apk" | cut -f1)"

# ---------- 9. 生成库 R 类并 javac ----------
log "生成 R 类..."
python3 "$SCRIPT_DIR/scan_r.py" | grep -v '^android$' > "$WORK/r-packages.txt" || true
python3 "$SCRIPT_DIR/gen_r.py" "$WORK/r-packages.txt"
rm -rf "$WORK/rclasses" && mkdir -p "$WORK/rclasses"
find "$WORK/gen" "$WORK/gen-lib" -name "R.java" > "$WORK/rlist.txt"
javac -d "$WORK/rclasses" @"$WORK/rlist.txt"
log "R 类: $(find "$WORK/rclasses" -name '*.class' | wc -l) 个"

# ---------- 10. kotlinc 编译（含 Compose 插件） ----------
log "kotlinc 编译源码..."
STDLIB=$(ls "$DEPS_DIR"/org.jetbrains.kotlin__kotlin-stdlib__*.jar | head -1)
COROUTINES=$(ls "$DEPS_DIR"/org.jetbrains.kotlinx__kotlinx-coroutines-core-jvm__*.jar | head -1)
ANNOT=$(ls "$DEPS_DIR"/org.jetbrains__annotations__*.jar | head -1)
for j in "$STDLIB" "$COROUTINES" "$ANNOT"; do [ -s "$j" ] || die "缺少编译器依赖: $j"; done
CP="$(paste -sd: "$WORK/classpath.txt")"
CP="$CP:$PLATFORM_JAR:$WORK/rclasses"
rm -rf "$WORK/classes" && mkdir -p "$WORK/classes"
find "$REPO_ROOT/app/src/main/java" -name "*.kt" > "$WORK/ksrc.txt"
java -Xmx2g -cp "$EMBEDDABLE_JAR:$STDLIB:$COROUTINES:$TROVE_JAR:$ANNOT" \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -Xplugin="$COMPOSE_PLUGIN_JAR" \
  -no-stdlib -no-reflect \
  -cp "$CP" \
  -jvm-target 1.8 \
  -d "$WORK/classes" \
  @"$WORK/ksrc.txt" 2>&1 | grep -v "kotlin-home" | head -20 || true
[ "$(find "$WORK/classes" -name '*.class' | wc -l)" -gt 0 ] || die "kotlinc 未产出 class"
log "kotlinc 完成: $(find "$WORK/classes" -name '*.class' | wc -l) 个 class"

# ---------- 11. d8 打 dex ----------
log "d8 打 dex..."
(cd "$WORK/classes" && zip -q -r "$WORK/app-classes.jar" .)
(cd "$WORK/rclasses" && zip -q -r "$WORK/r-classes.jar" .)
rm -rf "$WORK/dex" && mkdir -p "$WORK/dex"
"$D8" --lib "$PLATFORM_JAR" --min-api "$MIN_SDK" --output "$WORK/dex/" \
  $(tr '\n' ' ' < "$WORK/classpath.txt") \
  "$WORK/app-classes.jar" "$WORK/r-classes.jar" 2>&1 | grep -vi "^info" | head -10 || true
ls "$WORK/dex"/classes*.dex >/dev/null || die "d8 未产出 dex"

# ---------- 12. 组包 / 对齐 / 签名 ----------
log "组包签名..."
cp "$WORK/base.apk" "$WORK/app-unsigned.apk"
(cd "$WORK/dex" && zip -q -j "$WORK/app-unsigned.apk" classes*.dex)
"$ZIPALIGN" -f 4 "$WORK/app-unsigned.apk" "$WORK/app-aligned.apk"
KEYSTORE="${DEBUG_KEYSTORE:-$HOME/.android/debug.keystore}"
if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10950 \
    -dname "CN=Android Debug,O=Android,C=US"
fi
OUT_APK="$REPO_ROOT/AppInspector-v1.0-debug.apk"
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT_APK" "$WORK/app-aligned.apk"

# ---------- 13. 校验 ----------
log "校验 APK..."
"$APKSIGNER" verify "$OUT_APK"
"$ZIPALIGN" -c 4 "$OUT_APK"
"$AAPT" dump badging "$OUT_APK" | grep -E "^package|launchable-activity" | head -3

log "构建成功: $OUT_APK ($(du -h "$OUT_APK" | cut -f1))"
