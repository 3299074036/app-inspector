package dev.appinspector.shizuku

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku UserService：运行在 shell 身份（uid 2000）的独立进程中。
 * 通过 [IShizukuApi] 把 pm 命令的执行能力暴露给 App 进程，
 * 效果等价于 `adb shell pm ...`。
 *
 * 注意：此类由 Shizuku 框架实例化，必须继承 IShizukuApi.Stub。
 */
class ShizukuService : IShizukuApi.Stub() {

    override fun execPm(args: Array<String>): String {
        if (args.isEmpty()) return "Failure: empty command"
        val proc = try {
            Runtime.getRuntime().exec(args)
        } catch (t: Throwable) {
            return "Failure: cannot exec ${args[0]}: ${t.message}"
        }
        val out = StringBuilder()
        try {
            BufferedReader(InputStreamReader(proc.inputStream)).use { r ->
                var line: String?
                while (r.readLine().also { line = it } != null) {
                    out.appendLine(line)
                }
            }
            BufferedReader(InputStreamReader(proc.errorStream)).use { r ->
                var line: String?
                while (r.readLine().also { line = it } != null) {
                    out.appendLine(line)
                }
            }
            val code = proc.waitFor()
            if (code != 0 && out.isBlank()) {
                out.append("Failure: pm exited with code $code")
            }
        } catch (t: Throwable) {
            out.append("Failure: ${t.message}")
        }
        return out.toString().trim().ifBlank { "Success" }
    }

    override fun destroy() {
        // 进程退出后 binder 随即失效，调用方须捕获 DeadObjectException
        Runtime.getRuntime().exit(0)
    }
}
