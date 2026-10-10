# App Inspector 功能与实现设计（开发蓝图）

> 2026-10-10 设计。用户需求：Android 应用检查器，先做 UI + 实现设计，确认后再开发。
> 配套交互原型：artifact「App Inspector 设计」（可点击演示）。

## 0. 定位与假设

- 个人自用工具，sideload 安装，不上架 Google Play（因此可使用 `QUERY_ALL_PACKAGES` / `MANAGE_EXTERNAL_STORAGE` 等受限权限）。
- 目标设备：OPPO Find X8 / ColorOS 15 / Android 15；`minSdk 26`，`targetSdk 35`。
- 技术栈：Kotlin + Jetpack Compose（Material3），单 Activity；Room 存"快照"，DataStore 存设置。

## 1. 功能清单（对照用户需求）

| # | 需求 | 设计 |
|---|------|------|
| 1 | 默认显示用户应用；右上角 ⋮ → 显示系统应用 → 显示全部 | 主列表默认只显示用户应用；⋮ 菜单第一项为带勾选的「显示系统应用」，勾选后列表变为全部应用（含系统应用，「系统」徽标区分） |
| 2 | 排序功能（图2：按 APK 大小 / 应用名称 / 应用包名 / 首次安装时间 / 最后更新时间） | 排序弹窗：5 个单选项 + 升序/降序列分段按钮，实时作用于当前列表并持久化 |
| 3 | 导出信息：应用名称、包名、版本名称、版本号；md / txt / xlsx | 导出弹窗：字段多选（默认勾选上述 4 项，另可选大小、更新时间、系统标记、状态）、格式单选、范围单选（当前列表 / 全部应用） |
| 4 | 提取安装包 | 列表项「提取」按钮 + 长按多选批量提取；独立「提取设置」页（存放路径、命名表达式 `{A}_{V}.apk`、拆分包处理） |
| 5 | 按已删除、已停用筛选 | 列表顶部 FilterChip：全部 / 已停用 / 已删除；「已删除」基于本地快照对比实现（见 4.3） |

## 2. UI 结构

- **MainScreen**：TopAppBar（标题「应用检查器」+ 搜索图标 + ⋮）→ FilterChip 行（全部 / 已停用 / 已删除）→ LazyColumn 列表 → 多选模式底部操作条（已选 n 项｜提取安装包｜取消）
- **⋮ 溢出菜单**：☑ 显示系统应用 ｜ 排序 ｜ 导出列表 ｜ 批量提取安装包 ｜ 提取设置
- **SortDialog**：5 个 Radio + 升序/降序 SegmentedButton，确定后即时生效
- **ExportDialog**：字段 Checkbox 组、格式 Radio（md/txt/xlsx）、范围 Radio，导出按钮调 SAF 文件选择器
- **ExtractSettingsScreen**（独立页，TopAppBar 带返回）：存放路径行（显示当前路径 + 更改）、命名表达式输入框（实时预览，如 `微信_8.0.60.apk`）+ 占位符图例、开关：包含拆分包（splits）、提取完成通知
- **AppDetailDialog**（点列表项弹出）：全部字段 + 安装包路径 + 拆分包数量 +「提取安装包」「在系统设置中查看」按钮
- 列表项：图标｜应用名（+「系统」/「已停用」徽标）｜包名 · 版本名(版本号) · 大小 · 更新日期；右侧「提取」文字按钮；多选模式下变为 Checkbox

## 3. 数据模型

```kotlin
data class AppEntry(
    val packageName: String,
    val appName: String,
    val versionName: String?,
    val versionCode: Long,
    val apkSize: Long,              // base.apk + 所有 split APK 长度之和
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val isSystem: Boolean,          // (flags and FLAG_SYSTEM) != 0；另记 isUpdatedSystemApp 做展示
    val isEnabled: Boolean,         // 见 4.1
    val sourceDir: String,          // base.apk 路径
    val splitSourceDirs: List<String>,
)
```

## 4. 核心实现

### 4.1 应用列表查询（AppRepository，Dispatchers.IO）

```kotlin
val pf = PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong())
val pkgs = pm.getInstalledPackages(pf)
for (pi in pkgs) {
    val ai = pi.applicationInfo ?: continue
    val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    val versionCode = PackageInfoCompat.getLongVersionCode(pi)
    val apkFiles = listOfNotNull(ai.sourceDir) + (ai.splitSourceDirs ?: emptyList())
    val apkSize = apkFiles.sumOf { File(it).length() }
    val state = pm.getApplicationEnabledSetting(pi.packageName)
    val isEnabled = state != COMPONENT_ENABLED_STATE_DISABLED
        && state != COMPONENT_ENABLED_STATE_DISABLED_USER
        && state != COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
        && ai.enabled
}
```

- 图标：`ai.loadIcon(pm)` 在 IO 线程按需加载 + `LruCache<String, Drawable>`；列表先用首字母占位，加载完刷新。
- 应用名称排序用 `java.text.Collator.getInstance(Locale.CHINA)`，中文拼音友好。
- `versionName` 可能为 null → 展示"未知"，导出留空。
- 已更新的系统应用（`FLAG_UPDATED_SYSTEM_APP`）：仍归入系统应用，详情页标注"（已更新）"。

### 4.2 排序

`Map<SortKey, Comparator<AppEntry>>`，5 个 key × 正/反序。默认：按应用名称升序。用户选择持久化到 DataStore。

### 4.3 「已删除」筛选（快照机制）

Android 没有"已卸载应用"系统 API，用本地快照对比实现：

```kotlin
@Entity(tableName = "snapshots")
data class AppSnapshot(
    @PrimaryKey val packageName: String,
    val appName: String,
    val versionName: String?,
    val versionCode: Long,
    val lastSeen: Long,          // 最近一次见到该包的时间
)
```

- 每次刷新列表时：upsert 当前全部包名并更新 `lastSeen`。
- 「已删除」= 快照中有、当前已安装集合中没有的包；可展示"最后见到：2026-09-28"。
- 设置页提供「清空快照」（从零开始记录）。

### 4.4 导出

- **md**：GFM 表格（表头为所选字段）。
- **txt**：TSV（含表头），记事本直接可读。
- **xlsx**：手写最小 writer，不引入 Apache POI（约 10MB+，太重）。用 `ZipOutputStream` 生成 `[Content_Types].xml` + `xl/workbook.xml` + `xl/worksheets/sheet1.xml`，单元格用 `t="inlineStr"` 免去 sharedStrings。约 120 行代码。
- 落盘：SAF `ACTION_CREATE_DOCUMENT`，无需存储权限。MIME：`text/markdown` / `text/plain` / `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`。
- 范围二选一：当前筛选列表 / 全部应用（不过滤）。

### 4.5 提取安装包

- 复制 `sourceDir`（base.apk）+ `splitSourceDirs` 全部拆分包；普通应用通常只有 base.apk。
- 命名表达式替换：`{A}` 应用名称（做文件名非法字符清洗）、`{V}` 版本名称、`{P}` 应用包名、`{C}` 版本号；拆分包命名为 `{A}_{V}.split_<原split名>.apk`。设置页实时预览。
- 目标目录：首选 SAF `ACTION_OPEN_DOCUMENT_TREE` 拿持久化授权（用户选 `/MT2/apks` 这类目录）；备选 `MANAGE_EXTERNAL_STORAGE` 直接写绝对路径（自用可接受，Play 上架会被拒）。
- 批量提取：前台 Service + 通知显示进度（需 `POST_NOTIFICATIONS`）；单个复制失败跳过并在结果里列出。
- 说明：复制的是系统原包，签名保持不变，安装/校验行为与原包一致；图3 那种"签名校验"开关对应的是"查看 APK 信息"场景，本应用 v1 不做。

## 5. 权限（AndroidManifest）

```xml
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<!-- 可选：直接写自定义绝对路径时 -->
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
```

导出（SAF create document）与目录选择（SAF tree）本身不需要存储权限。

## 6. 边界与注意

- Android 11+ 无 `QUERY_ALL_PACKAGES` 时 `getInstalledPackages` 只能看到少量包 → 必须声明（自用 sideload 无审核问题）。
- 目标 SDK 35 / ColorOS 15（Android 15）：上述 API 均可用，无需适配分区存储即可读 `sourceDir`（系统应用安装目录可读）。
- 搜索：应用名/包名 contains；拼音搜索 v2 可选（TinyPinyin，增加包体积，暂不做）。
- 性能：全量查询放 IO 线程一次完成（200+ 应用约 1s 内）；图标懒加载避免首屏卡顿。

## 7. 里程碑

- **M1**：列表 + 显示系统应用开关 + 搜索 + 排序 + 筛选（已停用）
- **M2**：导出 md/txt/xlsx + 字段/范围选择
- **M3**：APK 提取（单/批量）+ 提取设置页 + 快照/已删除筛选
- **M4**：应用详情弹窗、空状态、深色模式、整体打磨

## 8. 待确认

1. 命名表达式默认值用 `{A}_{V}.apk` 还是 `{P}_{V}.apk`？
2. 「已删除」快照保留多久？（默认永久保留，手动清空）
3. 是否需要"在系统设置中查看"跳转（`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`）？建议有。
