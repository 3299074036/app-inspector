# App Inspector（应用检查器）

Android 应用信息查看与管理工具：浏览已安装应用、排序筛选、导出应用清单（md/txt/xlsx）、
提取 APK 安装包（含批量提取与分包处理）。

- 包名：`dev.appinspector`　版本：1.0（versionCode 1）
- minSdk 26（Android 8.0）/ targetSdk 35
- 技术栈：Kotlin + Jetpack Compose（Material 3）

## 功能

- 应用列表：默认显示用户应用，右上角 ⋮ 菜单切换显示系统应用 / 全部应用
- 排序：APK 大小、应用名称、包名、首次安装时间、最后更新时间 × 升序/降序
- 搜索、多选、应用详情页
- 导出：应用名称、包名、版本名、版本号 → md / txt / xlsx（SAF 选择保存位置）
- 提取 APK：单个 / 批量提取，支持 split APK，文件名表达式默认 `{A}_{V}.apk`
- 筛选：全部 / 已停用 / 已删除（已删除基于本地快照对比实现）

## 仓库内容

| 路径 | 说明 |
|---|---|
| `app/` | Android 源码（Kotlin + Compose） |
| `AppInspector-v1.0-debug.apk` | 已签名可直接安装的 debug 包 |
| `docs/IMPLEMENTATION.md` | 实现设计文档（架构、模块、关键流程） |
| `docs/ui-prototype/app-inspector.html` | 可交互 UI 设计原型（浏览器打开） |
| `tools/manual-build/build.sh` | 一键构建脚本（不依赖 Gradle） |
| `tools/manual-build/BUILD_STEPS.md` | 构建步骤详解与排错 |

## 构建

```bash
cd tools/manual-build
./build.sh
```

产物输出到仓库根目录 `AppInspector-v1.0-debug.apk`。
详细步骤与常见问题见 [`tools/manual-build/BUILD_STEPS.md`](tools/manual-build/BUILD_STEPS.md)。

> 说明：本项目也保留了标准 Gradle 工程文件（`settings.gradle.kts` 等），
> 但在某些沙箱环境里 Gradle daemon 的本地 socket 通信会被拦截导致无法构建，
> 此时请使用上面的手工构建流程（已验证一次跑通）。

## 截图

UI 设计以 `docs/ui-prototype/` 中的可交互原型为准。
