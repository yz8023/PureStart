package com.yz8023.purestart.repository

/**
 * Hook 自检回传的解析结果（v15.2 第二阶段：从设置页里抽成纯函数）。
 *
 * 目标应用里 `Main` 通过 `HookStateProvider` 回传一行文本：
 * `ok|miss|fails|version|timestamp`（见 `Prefs.readSelfCheck` / `writeSelfCheck`）。
 * 旧实现把这套解析写在 Composable 里，既不能测，也把「解析失败」和「没上报」混成了
 * 同一条分支。现在解析与文案都由本类负责，界面只负责渲染。
 */
data class SelfCheckReport(
    /** 已安装（成功挂上）的 Hook 数量。0 表示没有任何 Hook 生效。 */
    val installed: Int,
    /** 未命中的 Hook 数量（目标版本差异导致，不代表故障）。 */
    val missing: Int,
    /** 失败的 Hook 名称列表（逗号分隔，可能为空）。 */
    val failures: String,
    /** 目标应用版本号；无法解析时为 "?"。 */
    val version: String,
    /** 回传时间戳（毫秒，0 = 未知）。 */
    val at: Long,
) {
    /** 一行摘要：不含时间（时间由界面按用户时区渲染）。 */
    fun summary(): String {
        val state = if (installed > 0) {
            "已安装 $installed 项 Hook（不代表已拦截）" + if (missing > 0) " · 缺 $missing" else ""
        } else {
            "未报告已安装的 Hook（详见日志）"
        }
        val failText = if (failures.isNotEmpty()) " · 失败: ${failures.take(80)}" else ""
        return state + failText
    }

    companion object {
        /**
         * 解析回传文本。空串 / null → null（界面显示「未上报」）；
         * 字段缺失或非数字按 0 / "?" / 0L 兜底，绝不抛异常
         * （这些数据来自目标进程，不能假设它一定格式正确）。
         */
        fun parse(raw: String?): SelfCheckReport? {
            if (raw.isNullOrBlank()) return null
            val parts = raw.split("|")
            return SelfCheckReport(
                installed = parts.getOrNull(0)?.toIntOrNull() ?: 0,
                missing = parts.getOrNull(1)?.toIntOrNull() ?: 0,
                failures = parts.getOrNull(2).orEmpty(),
                version = parts.getOrNull(3)?.takeIf { it.isNotEmpty() } ?: "?",
                at = parts.getOrNull(4)?.toLongOrNull() ?: 0L,
            )
        }
    }
}
