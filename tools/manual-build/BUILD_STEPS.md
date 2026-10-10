# App Inspector 构建步骤（不依赖 Gradle）

本项目使用纯命令行工具链手工构建 APK，不需要 Gradle（在某些受限沙箱环境里
Gradle daemon 的本地 socket 通信会被拦截，此时只能用本流程）。

## 一、前置要求

| 工具 | 版本要求 | 说明 |
|---|---|---|
| JDK | 17+ | `JAVA_HOME` 指向它；未设置时脚本会自动查找 |
| Python 3 | 3.8+ | 跑依赖解析脚本 |
| curl / unzip / zip | — | 下载与解包 |
| 网络 | 可访问 Maven Central、Google Maven、dl.google.com | 下载依赖与 SDK |

Android SDK（platform android-35、build-tools 35.0.0）如果不存在，
`build.sh` 会自动用 `sdkmanager` 下载安装。

## 二、一键构建

```bash
cd tools/manual-build
./build.sh
```

构建产物：`AppInspector-v1.0-debug.apk`（仓库根目录）。

构建中间产物默认放在 `tools/manual-build/build-work/`（已加入 `.gitignore`），
可用 `MB_WORK=/path/to/dir` 环境变量改位置。

## 三、构建流程详解（脚本实际做的事）

### 1. 解析并下载 Maven 依赖（`resolve.py`）

不经过 Gradle，直接解析 POM：
- 以 `androidx.compose:compose-bom:2024.10.00` 为 BOM 管理 Compose 全家桶版本；
- 种子依赖显式 pin 版本（Compose 1.7.4、Material3 1.3.0、core-ktx 1.15.0、
  lifecycle 2.8.6、coroutines 1.9.0、kotlin-stdlib 2.0.21 等）；
- 递归遍历 `compile`/`runtime` 依赖，同一 `(group, artifact)` 只保留最高版本
  （greedy highest-wins，效果等价于 Gradle 的版本冲突解决）；
- 从 Google Maven / Maven Central 下载 aar/jar 到 `deps/`，
  清单写入 `artifacts.txt`。

### 2. 解包 AAR（`process_aar.py`）

- 每个 AAR 解包到 `aar/<group>__<artifact>__<version>/`；
- `classes.jar` 收集为编译 classpath（`classpath.txt`）；
- `res/` 目录收集给 aapt2（`resdirs.txt`）；
- 同一 artifact 同时存在 `foo` 和 `foo-android` 时只取 `-android` 变体
  （避免 -jvm/-android 双变体重叠）。

### 3. 消除重复类（`dedupe_classpath.py`）

不同版本构件可能带同名类（如 `collection-ktx:1.2.0` 与 `collection:1.4.2`、
`lifecycle-viewmodel-ktx:2.6.1` 与 `lifecycle-viewmodel:2.8.6`），d8 不允许重复。
脚本自动：逐个类找最高版本持有者，其余 jar 剥离重复类后重写到 `patched/`，
并更新 `classpath.txt`。结束后重复类必须为 0。

### 4. 编译资源（`aapt2 compile`）

```bash
aapt2 compile --dir app/src/main/res -o compiled/app-res.zip
aapt2 compile --dir <每个 AAR 的 res> -o compiled/<name>.zip   # 共 40+1 个
```

### 5. 链接资源（`aapt2 link`）

```bash
aapt2 link -o base.apk \
  -I <android.jar> \
  --manifest app/src/main/AndroidManifest.xml \
  --min-sdk-version 26 --target-sdk-version 35 \
  --auto-add-overlay \
  --java gen/ \
  --output-text-symbols R.txt \
  -R compiled/*.zip            # 41 个资源包
```

要点：
- `--auto-add-overlay`：库资源里覆盖 framework 资源的 id 需要它，否则报
  `failed to merge resource table`（AGP 默认会传）；
- `--java` 只生成 app 包的 `R.java`；
- 非 namespace 构建下所有资源合并进同一张表，库的 R 类只是“别名”，
  第 6 步据此生成。

### 6. 生成库 R 类

`aapt2 link --java` 只给 app 包生成 `R.java`，但依赖库的 class 文件引用的是
自家包的 R（如 `androidx.core.R$drawable`）。做法：

1. `scan_r.py`：扫描所有 classpath jar 的 class 文件 constant pool，
   找出被引用的 `*/R` 类，得到包列表（如 `androidx.core` 等 11 个）；
2. `gen_r.py`：按 `R.txt` 里的符号与 ID，为每个包生成一份 `R.java`
   （ID 与 app 资源表一致——这正是 AGP 在非 namespace 构建下的行为）；
3. `javac` 把 12 份 `R.java`（11 个库 + 1 个 app）编译成 class。

### 7. 编译 Kotlin 源码（含 Compose）

**关键坑**：发行版 `kotlinc/lib/kotlin-compiler.jar` 里的 `ComponentRegistrar`
接口用的是未重定位的 `com.intellij.*`，与 compose 插件（要重定位版）冲突，
报 `AbstractMethodError`。必须用 Maven 的 `kotlin-compiler-embeddable:2.0.21`：

```bash
java -cp kotlin-compiler-embeddable.jar:kotlin-stdlib.jar:\
kotlinx-coroutines-core-jvm.jar:trove4j.jar:annotations-13.0.jar \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -Xplugin=kotlin-compose-compiler-plugin-embeddable-2.0.21.jar \
  -no-stdlib -no-reflect \
  -cp "<classpath.txt>:<android.jar>:<rclasses>" \
  -jvm-target 1.8 \
  -d classes/ \
  $(find app/src/main/java -name '*.kt')
```

其中 `trove4j`（`org.jetbrains.intellij.deps:trove4j`）和
`annotations-13.0.jar` 是 embeddable 编译器启动时的自身依赖，
缺了会报 `ClassNotFoundException`。

### 8. d8 打 dex

```bash
d8 --lib <android.jar> --min-api 26 --output dex/ \
  $(cat classpath.txt) app-classes.jar r-classes.jar
```

得到 `classes.dex` / `classes2.dex`（multidex，应用类在第二个 dex 里是正常的）。

### 9. 组包、对齐、签名

```bash
cp base.apk app-unsigned.apk
zip -j app-unsigned.apk dex/classes*.dex
zipalign -f 4 app-unsigned.apk app-aligned.apk
# 没有 debug keystore 就生成一个
keytool -genkeypair -keystore ~/.android/debug.keystore -storepass android \
  -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10950 \
  -dname "CN=Android Debug,O=Android,C=US"
apksigner sign --ks ~/.android/debug.keystore \
  --ks-pass pass:android --key-pass pass:android \
  --out AppInspector-v1.0-debug.apk app-aligned.apk
```

注意 `apksigner` 的密码格式是 `--ks-pass pass:android`（空格分隔），
不是 `--ks-pass:android`。

### 10. 校验

```bash
apksigner verify AppInspector-v1.0-debug.apk
zipalign -c 4 AppInspector-v1.0-debug.apk
aapt dump badging AppInspector-v1.0-debug.apk | grep -E "^package|launchable-activity"
```

期望：`Verified`、对齐通过、`package: name='dev.appinspector'
versionCode='1' versionName='1.0'`、`launchable-activity: ...MainActivity`。

## 四、常见问题

| 现象 | 原因 | 解法 |
|---|---|---|
| `failed to merge resource table` / `failed parsing overlays` | 缺 `--auto-add-overlay` | link 命令加上 |
| `resource mipmap/ic_launcher not found` | manifest 引用的资源不存在 | 补齐 `res/mipmap-anydpi-v26/ic_launcher.xml` |
| kotlinc `AbstractMethodError ... ComponentRegistrar` | 用了发行版 kotlin-compiler.jar | 改用 kotlin-compiler-embeddable（见第 7 步） |
| kotlinc `ClassNotFoundException: Intrinsics / CoroutineScope / TObjectHashingStrategy / NotNull` | embeddable 编译器启动 classpath 缺东西 | 补齐 stdlib、coroutines、trove4j、annotations |
| d8 报重复类 | 依赖版本混杂 | 跑 `dedupe_classpath.py`（脚本已自动跑） |
| apksigner `Unsupported option: --ks-pass:android` | 参数格式错 | 用 `--ks-pass pass:android` |

## 五、已知限制

- AAR 自带 manifest 里的 `<provider>` / `<receiver>` 等组件声明不会被合并进最终
  manifest（`aapt2 link` 只接受一份 manifest）。本 App 的代码路径用不到这些组件
  （emoji2、lifecycle-process、profileinstaller 的 provider），不影响运行；
  如果你的项目依赖它们（如 App Startup 初始化），需要自行把这些声明合并进
  `app/src/main/AndroidManifest.xml`。
- 构建产物为 debug 签名包。如需 release 签名，把第 9 步的 keystore 换成你自己的即可。

## 六、从零开始的大致耗时

依赖下载约 3–5 分钟（~200MB），kotlinc 约 1 分钟，d8 约 3–4 分钟，
其余步骤都是秒级。总计约 10 分钟。
