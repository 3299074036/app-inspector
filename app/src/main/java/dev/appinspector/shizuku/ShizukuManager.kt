package dev.appinspector.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

/**
 * Shizuku 集成管理：状态检测、授权、UserService 绑定、pm 命令执行。
 *
 * 调用方流程：
 * 1. [getStatus] 检查状态（未安装 / 未运行 / 未授权 / 就绪）
 * 2. 未授权时调 [requestPermission] 弹窗申请
 * 3. 就绪后调 [setEnabled] / [uninstall] 执行操作
 */
object ShizukuManager {

    const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
    const val PERMISSION_REQUEST_CODE = 1001

    /** Shizuku 可用状态 */
    sealed interface Status {
        /** 未安装 Shizuku App */
        data object NotInstalled : Status
        /** 已安装但服务未运行（未激活或重启后失效） */
        data object NotRunning : Status
        /** 服务运行中，但本应用未获授权 */
        data object NoPermission : Status
        /** 就绪：可执行 pm 操作 */
        data object Ready : Status
    }

    fun getStatus(context: Context): Status {
        val installed = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
        }.isSuccess
        if (!installed) return Status.NotInstalled
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            return Status.NotRunning
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return Status.NoPermission
        }
        return Status.Ready
    }

    /** 弹窗申请 Shizuku 授权，结果通过 listener 回调 */
    fun requestPermission() {
        Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
    }

    fun addPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        Shizuku.addRequestPermissionResultListener(listener)
    }

    fun removePermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        Shizuku.removeRequestPermissionResultListener(listener)
    }

    // ---------- UserService 绑定 ----------

    @Volatile
    private var api: IShizukuApi? = null

    @Volatile
    private var bindContinuation: ((IShizukuApi?) -> Unit)? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val a = service?.let { IShizukuApi.Stub.asInterface(it) }
            api = a
            bindContinuation?.invoke(a)
            bindContinuation = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            api = null
        }
    }

    private fun serviceArgs(context: Context): Shizuku.UserServiceArgs =
        Shizuku.UserServiceArgs(
            ComponentName(context.packageName, ShizukuService::class.java.name)
        )
            .processNameSuffix("shizuku_pm")
            .version(1)

    /**
     * 确保 UserService 已绑定并返回接口；超时或无权限返回 null。
     * 必须在 Shizuku 就绪（[getStatus] == Ready）后调用。
     */
    private suspend fun ensureBound(context: Context): IShizukuApi? {
        api?.let {
            if (runCatching { it.asBinder().pingBinder() }.getOrDefault(false)) return it
            api = null
        }
        return withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine { cont ->
                bindContinuation = { cont.resume(it) }
                cont.invokeOnCancellation { bindContinuation = null }
                Shizuku.bindUserService(serviceArgs(context), connection)
            }
        }
    }

    /** 断开并退出 service 进程 */
    fun disconnect(context: Context) {
        runCatching { api?.destroy() }
        api = null
        runCatching { Shizuku.unbindUserService(serviceArgs(context), connection, true) }
    }

    // ---------- pm 操作 ----------

    private suspend fun execPm(
        context: Context,
        vararg args: String,
    ): Result<String> = execCommand(context, *arrayOf("pm") + args)

    /**
     * 以 shell 身份执行任意命令（终端用）。
     * 注意：仅检查进程退出码，不解析输出中的 Failure。
     */
    suspend fun execCommand(
        context: Context,
        vararg args: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        val service = ensureBound(context)
            ?: return@withContext Result.failure(
                IllegalStateException("Shizuku 服务连接失败，请确认已授权并重试")
            )
        try {
            val output = service.execPm(args)
            val firstLine = output.lineSequence().firstOrNull() ?: ""
            // pm 报错形式多样：Failure [...] / Error: ... / Exception ...
            if (output.contains("Failure", ignoreCase = true) ||
                firstLine.startsWith("Error", ignoreCase = true) ||
                firstLine.contains("Exception", ignoreCase = true)
            ) {
                Result.failure(IllegalStateException(firstLine.ifBlank { output }.take(200)))
            } else {
                Result.success(output.ifBlank { "Success" })
            }
        } catch (t: Exception) {
            api = null
            Result.failure(t)
        }
    }

    /**
     * 终端专用：执行任意 shell 命令行（按空格分词，不支持管道/重定向），
     * 返回原始输出，不做 Failure 判断。
     */
    suspend fun execRaw(
        context: Context,
        command: String,
    ): Result<Pair<Int, String>> = withContext(Dispatchers.IO) {
        val service = ensureBound(context)
            ?: return@withContext Result.failure(
                IllegalStateException("Shizuku 服务连接失败")
            )
        try {
            // 简单分词（不支持引号嵌套，终端场景够用）
            val args = command.trim().split(Regex("\\s+")).toTypedArray()
            if (args.isEmpty() || args[0].isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("空命令"))
            }
            val output = service.execPm(args)
            Result.success(0 to output.ifBlank { "(无输出)" })
        } catch (t: Exception) {
            api = null
            Result.failure(t)
        }
    }

    /**
     * 停用 / 启用应用，对应 `pm disable-user --user 0` / `pm enable --user 0`。
     * 注意：部分系统核心应用 disable-user 会失败（Failure），属正常。
     */
    suspend fun setEnabled(
        context: Context,
        packageName: String,
        enabled: Boolean,
    ): Result<String> {
        val action = if (enabled) "enable" else "disable-user"
        return execPm(context, action, "--user", "0", packageName)
    }

    /**
     * 卸载应用（仅对当前用户），对应 `pm uninstall --user 0`。
     * 系统应用的 APK 保留在系统分区，可通过 `pm install-existing` 恢复。
     */
    suspend fun uninstall(
        context: Context,
        packageName: String,
    ): Result<String> = execPm(context, "uninstall", "--user", "0", packageName)

    /**
     * 恢复已删除的系统应用，对应 `pm install-existing --user 0`。
     * 仅对系统应用有效（APK 保留在系统分区）；用户应用删除后无法恢复。
     */
    suspend fun installExisting(
        context: Context,
        packageName: String,
    ): Result<String> = execPm(context, "install-existing", "--user", "0", packageName)

}
