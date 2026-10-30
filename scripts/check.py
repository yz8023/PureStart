#!/usr/bin/env python3
"""PureStart 漂移检查门禁（照抄 ui 模板 check-template.py 的结构，纯标准库）。

检查项（提示词 P1-1 规定的三项 + 两项守门）：
  1. 目标 App 清单三处一致：Targets.java 的 LIST / xposed/scope.list /
     SettingsActivity 的 APP_DESC —— 三个包名集合两两 diff，有差即报错并指出差在哪两个文件之间；
  2. 版本号一致性：除 AndroidManifest.xml（唯一权威）外，任何源文件出现字面版本号即报错；
     xposed/module.prop 的 version/versionCode 必须与 Manifest 一致（build.sh 派生写回）；
  3. Main.java 声称的 API 版本必须与 xposed/module.prop 的 targetApiVersion 一致；
  4. authority 一致性：Manifest 的 Provider authority == Main.java 中的 authority 拼接
     （MODULE_PACKAGE + ".hookstate"），供包名迁移（P2-3）前后把关；
  5. UI/Hook 类隔离 + 签名密钥在位（守门项，防历史崩溃回归）。

用法：python3 scripts/check.py；退出码：通过 0、发现漂移 1。
build.sh 在每次构建前自动运行；提交前请手动跑一次。
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

    # ---- 1. 目标清单三处一致（两两 diff，指明文件对） ----
    print("[1] 目标应用清单（Targets.java / scope.list / APP_DESC）")
    targets = [t for t in re.findall(r'\{\s*"([A-Za-z0-9_.]+)",\s*"', read("src/com/example/l/Targets.java"))
               if "." in t and not t.startswith("com.example")]
    scope = [l.strip() for l in read("xposed/scope.list").splitlines()
             if l.strip() and not l.startswith("#")]
    app_desc = []
    for line in read("src/com/example/l/SettingsActivity.java").splitlines():
        ls = line.strip()
        if ls.startswith("//"):
            continue
        m = re.match(r'APP_DESC\.put\("([A-Za-z0-9_.]+)"', ls)
        if m:
            app_desc.append(m.group(1))
    pairs = [("Targets.java", set(targets), "scope.list", set(scope)),
             ("Targets.java", set(targets), "SettingsActivity(APP_DESC)", set(app_desc)),
             ("scope.list", set(scope), "SettingsActivity(APP_DESC)", set(app_desc))]
    consistent = True
    for a, sa, b, sb in pairs:
        if sa != sb:
            consistent = False
            errors += fail(f"{a} 与 {b} 不一致：仅 {a} 有 {sorted(sa - sb)}，仅 {b} 有 {sorted(sb - sb & sa | (sb - sa))}")
    if not targets:
        errors += fail("未能从 Targets.java 解析出任何包名（解析式失效？）")
    if consistent and targets:
        ok(f"三处一致（{len(set(targets))} 个目标包）")

    # ---- 2. 版本号：Manifest 唯一权威 ----
    print("[2] 版本号（Manifest 唯一权威）")
    manifest = read("AndroidManifest.xml")
    vname = re.search(r'android:versionName="([^"]+)"', manifest)
    vcode = re.search(r'android:versionCode="([^"]+)"', manifest)
    prop = read("xposed/module.prop")
    pname = re.search(r"^version=(.+)$", prop, re.M)
    pcode = re.search(r"^versionCode=(\d+)$", prop, re.M)
    if not (vname and vcode and pname and pcode):
        errors += fail("版本字段解析失败（Manifest 或 module.prop 缺字段）")
    else:
        if vname.group(1) != pname.group(1).lstrip("v") or vcode.group(1) != pcode.group(1):
            errors += fail(f"module.prop 与 Manifest 不一致：{pname.group(1)}/{pcode.group(1)} vs "
                           f"v{vname.group(1)}/{vcode.group(1)}（build.sh 会写回，请先构建一次）")
        else:
            ok(f"module.prop 与 Manifest 一致（v{vname.group(1)}/{vcode.group(1)}）")
    # 只拦「本应用版本号」字面量：当前版本精确匹配 + 双位数主版本形态（1x.y.z）。
    # SDK（android-35 / build-tools 34.0.0）、LSPosed（1.9.2+）等外部版本号不拦。
    vpat = re.compile(r"v?" + re.escape(vname.group(1) if vname else "15.0.0")
                      + r"(?![0-9])|" + r"1[0-9]\.\d+\.\d+")
    hits = []
    for dirpath, _, files in os.walk(os.path.join(ROOT, "src")):
        for fn in files:
            if not fn.endswith((".java", ".sh")):
                continue
            fp = os.path.relpath(os.path.join(dirpath, fn), ROOT)
            for i, line in enumerate(read(fp).splitlines(), 1):
                ls = line.strip()
                if ls.startswith(("*", "//", "/*", "#")):
                    continue
                if vpat.search(ls):
                    hits.append(f"{fp}:{i}: {ls[:70]}")
    hits += [f"build.sh:{i}: {l.strip()[:70]}" for i, l in enumerate(read("build.sh").splitlines(), 1)
             if vpat.search(l.strip()) and not l.strip().startswith("#")]
    if hits:
        errors += fail("源文件出现字面版本号（应为 0 处，运行时从 PackageManager 取）：")
        for h in hits:
            print("      " + h)
    else:
        ok("源文件无字面版本号")

    # ---- 3. Main.java 声称的 API 版本 == module.prop targetApiVersion ----
    print("[3] API 版本声明（Main.java ↔ module.prop targetApiVersion）")
    main_src = read("src/com/example/l/Main.java")
    api_claims = set(re.findall(r"API (\d+)", main_src))
    target_api = re.search(r"^targetApiVersion=(\d+)$", prop, re.M)
    if not target_api:
        errors += fail("module.prop 缺 targetApiVersion")
    elif not api_claims:
        ok("Main.java 无 API 版本声明（无声明不检查）")
    elif target_api.group(1) not in api_claims:
        errors += fail(f"Main.java 声称 API {sorted(api_claims)} 与 module.prop "
                       f"targetApiVersion={target_api.group(1)} 不一致")
    else:
        ok(f"API 版本声明一致（{target_api.group(1)}）")

    # ---- 4. authority 一致性（Manifest Provider authority ↔ Main 拼接） ----
    print("[4] Provider authority 一致性（Manifest ↔ Main.MODULE_PACKAGE）")
    m_auth = re.search(r'android:authorities="([^"]+)"', manifest)
    m_pkg = re.search(r'package="([^"]+)"', manifest)
    j_pkg = re.search(r'static final String MODULE_PACKAGE = "([^"]+)"', main_src)
    if not (m_auth and m_pkg and j_pkg):
        errors += fail("authority/package 解析失败（Manifest 或 Main.MODULE_PACKAGE 缺失）")
    else:
        if m_pkg.group(1) != j_pkg.group(1):
            errors += fail(f"Main.MODULE_PACKAGE={j_pkg.group(1)} 与 Manifest package={m_pkg.group(1)} 不一致")
        elif m_auth.group(1) != j_pkg.group(1) + ".hookstate":
            errors += fail(f"Manifest authorities={m_auth.group(1)} 与 {j_pkg.group(1)}.hookstate 不一致")
        else:
            ok(f"authority 一致（{m_auth.group(1)}）")

    # ---- 5. 守门：UI/Hook 隔离 + 密钥在位 ----
    print("[5] UI/Hook 类隔离（UI 进程不得引用 Hook 类）")
    ui_files = ["SettingsActivity.java", "GlassCardView.java", "GlassPrefs.java", "LiquidGlass.java",
                "ThemeDialog.java", "AppDetailDialog.java", "HoldShieldView.java",
                "HookStateProvider.java", "Targets.java", "IconControlReceiver.java"]
    bad = re.compile(r"Main\.TARGETS|Main\.Reporter|XiaoCanHook\.|UniversalAdHook\.|DexFinder\.|"
                     r"HookGate|io\.github\.libxposed")
    violations = []
    for uf in ui_files:
        path = "src/com/example/l/" + uf
        if not os.path.exists(os.path.join(ROOT, path)):
            continue
        for i, line in enumerate(read(path).splitlines(), 1):
            ls = line.strip()
            if ls.startswith(("*", "//", "/*")):
                continue
            if bad.search(ls):
                violations.append(f"{path}:{i}: {ls[:70]}")
    if violations:
        errors += fail("UI 类引用了 Hook 类（会致设置页 NoClassDefFoundError 崩溃）：")
        for v in violations:
            print("      " + v)
    else:
        ok("UI 类与 Hook 类完全隔离")

    print("[6] 签名密钥")
    ks = os.environ.get("KEYSTORE_PATH", "")
    if ks and os.path.exists(os.path.join(ROOT, ks)):
        ok(f"KEYSTORE_PATH={ks} 在位")
    elif os.path.exists(os.path.join(ROOT, "debug.keystore")):
        ok("debug.keystore 在位（连续发布用，见 README 签名说明）")
    else:
        errors += fail("无可用签名密钥（构建将拒绝执行）")

    print()
    if errors:
        print(f"一致性检查未通过：{errors} 处问题")
        return 1
    print("一致性检查全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
