#!/usr/bin/env python3
"""PureStart 一致性检查（仿 yz8023/ui check-template.py 的防漂移门禁）。

检查项：
  1. 目标应用清单三处一致：Main.TARGETS / xposed/scope.list / SettingsActivity.APP_DESC；
  2. 版本号一致性：AndroidManifest.xml 与 xposed/module.prop（构建脚本会写回，此处拦截手改漂移）；
  3. 代码中不得再硬编码版本号字符串（版本唯一来源 = Manifest，代码侧走 BuildVersion）；
  4. 签名密钥在位（debug.keystore 或 PURESTART_KEYSTORE）。

用法：python3 scripts/check_consistency.py（构建脚本每次构建前自动运行）。
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def read(p):
    with open(os.path.join(ROOT, p), encoding="utf-8") as f:
        return f.read()


def fail(msg):
    print("  ✗ " + msg)
    return 1


def ok(msg):
    print("  ✓ " + msg)


def main():
    errors = 0

    # ---- 1. 目标清单三处一致 ----
    print("[1] 目标应用清单（Main.TARGETS / scope.list / APP_DESC）")
    main_src = read("src/com/example/l/Main.java")
    targets = re.findall(r'\{\s*"([A-Za-z0-9_.]+)",\s*"', main_src)
    targets = [t for t in targets if "." in t and not t.startswith("com.example")]

    scope = [l.strip() for l in read("xposed/scope.list").splitlines()
             if l.strip() and not l.startswith("#")]

    settings_src = read("src/com/example/l/SettingsActivity.java")
    app_desc = []
    for line in settings_src.splitlines():
        ls = line.strip()
        if ls.startswith("//"):      # 跳过注释示例行
            continue
        m = re.match(r'APP_DESC\.put\("([A-Za-z0-9_.]+)"', ls)
        if m:
            app_desc.append(m.group(1))

    tset, sset, aset = set(targets), set(scope), set(app_desc)
    if tset == sset == aset:
        ok(f"三处一致（{len(tset)} 个目标包）")
    else:
        if tset != sset:
            errors += fail(f"Main.TARGETS 与 scope.list 不一致："
                           f"仅前者有 {sorted(tset - sset)}，仅后者有 {sorted(sset - tset)}")
        if tset != aset:
            errors += fail(f"Main.TARGETS 与 APP_DESC 不一致："
                           f"仅前者有 {sorted(tset - aset)}，仅后者有 {sorted(aset - tset)}")
        if not targets:
            errors += fail("未能从 Main.TARGETS 解析出任何包名（解析式失效？）")

    # ---- 2. 版本号一致性 ----
    print("[2] 版本号（Manifest ↔ module.prop）")
    manifest = read("AndroidManifest.xml")
    vname = re.search(r'android:versionName="([^"]+)"', manifest)
    vcode = re.search(r'android:versionCode="([^"]+)"', manifest)
    prop = read("xposed/module.prop")
    pname = re.search(r'^version=(.+)$', prop, re.M)
    pcode = re.search(r'^versionCode=(\d+)$', prop, re.M)
    if not (vname and vcode and pname and pcode):
        errors += fail("版本字段解析失败（Manifest 或 module.prop 缺字段）")
    elif vname.group(1) != pname.group(1).lstrip("v") or vcode.group(1) != pcode.group(1):
        errors += fail(f"版本不一致：Manifest v{vname.group(1)}/{vcode.group(1)} vs "
                       f"module.prop {pname.group(1)}/{pcode.group(1)}（build.sh 会写回，请先构建一次）")
    else:
        ok(f"v{vname.group(1)} / {vcode.group(1)} 一致")

    # ---- 3. 代码内不得硬编码版本号 ----
    print("[3] 代码内硬编码版本号扫描（应为 0 处，统一走 BuildVersion）")
    hits = []
    for p in ("src/com/example/l/Main.java", "src/com/example/l/SettingsActivity.java",
              "build.sh"):
        for i, line in enumerate(read(p).splitlines(), 1):
            ls = line.strip()
            if ls.startswith(("*", "//", "#")):   # 注释/文档行不拦（只拦代码硬编码）
                continue
            if re.search(r'v?1[45]\.\d+\.\d+', ls) and "sed -n" not in ls and "s/^version" not in ls:
                hits.append(f"{p}:{i}: {ls[:70]}")
    if hits:
        errors += fail("发现硬编码版本号：")
        for h in hits:
            print("      " + h)
    else:
        ok("无硬编码版本号")

    # ---- 4. 签名密钥在位 ----
    print("[4] 签名密钥")
    ks = os.environ.get("PURESTART_KEYSTORE", "debug.keystore")
    if os.path.exists(os.path.join(ROOT, ks)):
        ok(f"{ks} 在位（历代签名一致，覆盖升级可用）")
    else:
        errors += fail(f"签名密钥 {ks} 缺失（构建将拒绝执行，防止静默换签名）")

    print()
    if errors:
        print(f"一致性检查未通过：{errors} 处问题")
        return 1
    print("一致性检查全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
