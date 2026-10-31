#!/usr/bin/env python3
"""SelfCheckReport 纯函数契约（v15.2 第二阶段）。

自检上报文本来自**目标应用进程**，格式不能假设一定正确：缺字段、多字段、空串、
时间戳非数字都必须安全兜底，且「没上报」与「上报了但 0 项 Hook」必须是两种不同结论
（旧实现把两者混在同一个 when 分支里）。

本文件不依赖 Android 桩：SelfCheckReport 是纯 Kotlin，编译后直接在 JVM 上跑。
"""
from pathlib import Path
import sys
import tempfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
import kotlin_jvm as kj

MAIN = '''import com.yz8023.purestart.repository.SelfCheckReport

private fun check(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

fun main() {
    // 「没上报」≠「上报了 0 项」
    check(SelfCheckReport.parse(null) == null, "null 必须是「未上报」")
    check(SelfCheckReport.parse("") == null, "空串必须是「未上报」")
    check(SelfCheckReport.parse("   ") == null, "空白必须是「未上报」")

    val normal = SelfCheckReport.parse("3|1|hideAd,skip|9.2.1|1730000000000")
    check(normal != null, "正常文本必须解析成功")
    check(normal!!.installed == 3 && normal.missing == 1, "已安装/缺失数量解析错误")
    check(normal.failures == "hideAd,skip" && normal.version == "9.2.1", "字段解析错位")
    check(normal.at == 1730000000000L, "时间戳解析错误")
    check(normal.summary().contains("已安装 3 项 Hook"), "摘要应包含已安装数量")
    check(normal.summary().contains("缺 1"), "摘要应包含缺失数量")
    check(normal.summary().contains("失败: hideAd,skip"), "摘要应包含失败列表")

    // 0 项 Hook 是「未生效」，与「未上报」必须区分开
    val zero = SelfCheckReport.parse("0|0||1.0|0")
    check(zero != null, "0 项也必须解析成报告（不是未上报）")
    check(zero!!.summary().contains("未报告已安装的 Hook"), "0 项应给出未生效结论文案")
    check(!zero.summary().contains("失败"), "空失败列表不应出现「失败」字样")

    // 字段缺失 / 脏数据兜底，绝不抛异常
    val broken = SelfCheckReport.parse("abc|1")
    check(broken != null, "字段缺失也应返回报告")
    check(broken!!.installed == 0 && broken.missing == 1, "非数字字段应兜底为 0")
    check(broken.version == "?" && broken.at == 0L, "缺版本/时间戳应兜底")
    check(SelfCheckReport.parse("1|2|3|4|5|6|7")!!.installed == 1, "多余字段必须忽略")

    // 失败列表过长时必须截断（设置页一行显示不下）
    val longFailures = "x".repeat(200)
    val truncated = SelfCheckReport.parse("1|0|$longFailures|1.0|1")!!
    check(truncated.summary().length < 130, "过长失败列表必须截断")

    println("PASS: SelfCheckReport parses hook reports, distinguishes no-report from zero hooks, and never throws")
}
'''


def main():
    kotlinc = kj.require_kotlinc()
    with tempfile.TemporaryDirectory() as temp:
        base = Path(temp)
        harness = kj.write_sources(base / 'src', {'ReportMain.kt': MAIN})
        classes = base / 'classes'
        classes.mkdir()
        stdlib = kj.kotlin_runtime(kotlinc)
        sources = [kj.MAIN_JAVA / 'com/yz8023/purestart/repository/SelfCheckReport.kt'] + harness
        kj.kotlinc_compile(kotlinc, classes, sources, classpath=[*stdlib])
        kj.run_java('ReportMainKt', [classes, *stdlib])


if __name__ == '__main__':
    main()
