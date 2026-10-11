package dev.appinspector.data

/**
 * ColorOS 停用保护的 14 个包。
 *
 * 背景：这些包的 `pm disable-user --user 0` 会被 ColorOS 静默拒绝
 * （命令不报错但实际未停用），组件级命令则直接抛 SecurityException，
 * shell 身份下无解。实测确认：安装 Google CTS 测试包
 * `com.android.cts.crossprofilepermissioncontrol`（AOSP testkey 签名）
 * 后，系统设置里的「停用」按钮会点亮，再经无障碍服务自动点击即可停用。
 */
object ProtectedApps {
    /** 解锁组件包名（Google CTS 测试包，仅用于解除停用保护，无其他功能） */
    const val UNLOCK_PKG = "com.android.cts.crossprofilepermissioncontrol"

    /** 14 个被保护的包 */
    val PKGS = listOf(
        "com.oplus.uiengine",            // 百变引擎
        "com.oplus.romupdate",           // 更新服务
        "com.coloros.bootreg",           // 开机注册
        "com.coloros.assistantscreen",   // 速览
        "com.heytap.tas",                // 系统卡包
        "com.oplus.cosa",                // 应用增强服务
        "com.oplus.themestore",          // 主题基础服务
        "com.oplus.aiunit",              // AI 服务引擎
        "com.opos.ads",                  // Commercial Service
        "com.coloros.findmyphone",       // 查找
        "com.nearme.instant.platform",   // 快应用服务框架
        "com.heytap.pictorial",          // 乐划锁屏
        "com.heytap.browser",            // 浏览器
        "com.heytap.quicksearchbox",     // 全局搜索
    )
}

