#!/usr/bin/env python3
"""PureStart 漂移检查门禁（照抄 ui 模板 check-template.py 的结构，纯标准库）。

检查项（提示词 P1-1 规定的三项 + 两项守门 + v15.2 新布局守门）：
  1. 目标 App 清单三处一致：Targets.java 的 LIST / xposed/scope.list /
     app/src/main/AndroidManifest.xml 的 queries —— 三个包名集合两两 diff；
  2. 版本号一致性：除清单（唯一权威）外，任何源文件出现字面版本号即报错；
     xposed/module.prop 的 version/versionCode 必须与清单一致（构建任务派生写回）；
  3. Main.java 声称的 API 版本必须与 xposed/module.prop 的 targetApiVersion 一致；
  4. authority 一致性：清单 Provider authority == namespace + ".hookstate"
     （v15.2 起包名权威由 `app/build.gradle.kts` 的 namespace 承担，清单不再写 package=）；
  5. UI/Hook 类隔离 + 签名密钥在位（守门项，防历史崩溃回归）；
  6. v15.2 布局守门：UI/repository 层不得残留 Java（第一阶段已全部迁移到 Kotlin），
     且 repository 层不得依赖 Compose（保证 tests/test_*_repository.py 能在纯 JVM 上跑）。

用法：python3 scripts/check.py；退出码：通过 0、发现漂移 1。
build.sh 在每次构建前自动运行；提交前请手动跑一次。
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MANIFEST = "app/src/main/AndroidManifest.xml"
MAIN_JAVA = "app/src/main/java"
PKG_ROOT = "app/src/main/java/com/yz8023/purestart"
GRADLE = "app/build.gradle.kts"


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

    # Targets is the sole UI description source. Verify scope and package visibility
    # against it; this replaces the removed duplicate APP_DESC, not the drift gate.
    print("[1] 目标应用清单（Targets.java / scope.list / Manifest queries）")
    targets = [t for t in re.findall(r'\{\s*"([A-Za-z0-9_.]+)",\s*"', read(PKG_ROOT + "/Targets.java"))
               if "." in t and not t.startswith("com.example")]
    scope = [line.strip() for line in read("xposed/scope.list").splitlines()
             if line.strip() and not line.strip().startswith("#")]
    import xml.etree.ElementTree as ET
    manifest_root = ET.fromstring(read(MANIFEST))
    android_name = "{http://schemas.android.com/apk/res/android}name"
    queries = [node.attrib.get(android_name, "") for node in manifest_root.findall("./queries/package")]
    lists = [("Targets.java", targets), ("scope.list", scope), ("Manifest queries", queries)]
    if not targets:
        errors += fail("未能解析目标应用清单")
    for name, entries in lists:
        if len(entries) != len(set(entries)):
            errors += fail(f"{name} 存在重复包名")
    for i, (a, sa) in enumerate(lists):
        for b, sb in lists[i + 1:]:
            if set(sa) != set(sb):
                errors += fail(f"{a} 与 {b} 不一致：仅 {a} 有 {sorted(set(sa)-set(sb))}，仅 {b} 有 {sorted(set(sb)-set(sa))}")
    if targets and all(set(targets) == set(entries) for _, entries in lists):
        ok(f"三处一致（{len(set(targets))} 个目标包）")

    # ---- 2. 版本号：清单唯一权威 ----
    print("[2] 版本号（Manifest 唯一权威）")
    manifest = read(MANIFEST)
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
                           f"v{vname.group(1)}/{vcode.group(1)}（构建任务会写回，请先构建一次）")
        else:
            ok(f"module.prop 与 Manifest 一致（v{vname.group(1)}/{vcode.group(1)}）")
    if 'package="' in manifest:
        errors += fail("清单不应再写 package=（AGP 8 禁止；包名权威是 build.gradle.kts 的 namespace）")
    else:
        ok("清单未残留 package= 属性（AGP 8 要求）")
    # 只拦「本应用版本号」字面量：当前版本精确匹配 + 双位数主版本形态（1x.y.z）。
    # SDK（android-35 / build-tools 34.0.0）、LSPosed（1.9.2+）等外部版本号不拦。
    vpat = re.compile(r"v?" + re.escape(vname.group(1) if vname else "15.0.0")
                      + r"(?![0-9])|" + r"1[0-9]\.\d+\.\d+")
    hits = []
    for dirpath, _, files in os.walk(os.path.join(ROOT, MAIN_JAVA)):
        for fn in files:
            if not fn.endswith((".java", ".kt", ".sh")):
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
    main_src = read(PKG_ROOT + "/Main.java")
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

    # ---- 4. authority 一致性（Manifest ↔ namespace） ----
    print("[4] Provider authority 一致性（Manifest ↔ build.gradle.kts namespace）")
    m_auth = re.search(r'android:authorities="([^"]+)"', manifest)
    gradle_src = read(GRADLE)
    namespace = re.search(r'namespace\s*=\s*"([^"]+)"', gradle_src)
    application_id = re.search(r'applicationId\s*=\s*"([^"]+)"', gradle_src)
    j_pkg = re.search(r'static final String MODULE_PACKAGE = "([^"]+)"', main_src)
    if not (m_auth and namespace and j_pkg):
        errors += fail("authority/namespace 解析失败（Manifest、build.gradle.kts 或 Main.MODULE_PACKAGE 缺失）")
    else:
        if application_id and application_id.group(1) != namespace.group(1):
            errors += fail(f"applicationId={application_id.group(1)} 与 namespace={namespace.group(1)} 不一致")
        elif j_pkg.group(1) != namespace.group(1):
            errors += fail(f"Main.MODULE_PACKAGE={j_pkg.group(1)} 与 namespace={namespace.group(1)} 不一致")
        elif m_auth.group(1) != namespace.group(1) + ".hookstate":
            errors += fail(f"Manifest authorities={m_auth.group(1)} 与 {namespace.group(1)}.hookstate 不一致")
        else:
            ok(f"authority 一致（{m_auth.group(1)}）")

    # ---- 5. 守门：UI/Hook 隔离 + 密钥在位 ----
    print("[5] UI/Hook 类隔离（UI 进程不得引用 Hook 类）")
    ui_files = []
    for base in (PKG_ROOT + "/ui", PKG_ROOT + "/repository"):
        for dirpath, _, filenames in os.walk(os.path.join(ROOT, base)):
            for filename in filenames:
                if filename.endswith((".java", ".kt")):
                    ui_files.append(os.path.relpath(os.path.join(dirpath, filename), ROOT))
    ui_files += [PKG_ROOT + "/" + f for f in
                 ("Prefs.java", "HookStateProvider.java", "Targets.java", "IconControlReceiver.java")]
    bad = re.compile(r"Main\.TARGETS|Main\.Reporter|XiaoCanHook\.|UniversalAdHook\.|DexFinder\.|"
                     r"HookGate|io\.github\.libxposed")
    violations = []
    for path in ui_files:
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

    # ---- 6. v15.2 布局守门 ----
    print("[6] v15.2 布局：UI/repository 层零 Java，repository 层不依赖 Compose")
    leftover = []
    for base in (PKG_ROOT + "/ui", PKG_ROOT + "/repository"):
        for dirpath, _, filenames in os.walk(os.path.join(ROOT, base)):
            for filename in filenames:
                if filename.endswith(".java"):
                    leftover.append(os.path.relpath(os.path.join(dirpath, filename), ROOT))
    if leftover:
        errors += fail("UI/repository 层仍有 Java 文件（第一阶段要求全部迁移到 Kotlin）：")
        for path in leftover:
            print("      " + path)
    else:
        ok("UI/repository 层零 Java")
    compose_refs = []
    for dirpath, _, filenames in os.walk(os.path.join(ROOT, PKG_ROOT, "repository")):
        for filename in filenames:
            if not filename.endswith(".kt"):
                continue
            path = os.path.relpath(os.path.join(dirpath, filename), ROOT)
            for i, line in enumerate(read(path).splitlines(), 1):
                if line.strip().startswith(("import androidx.compose", "import androidx.activity")):
                    compose_refs.append(f"{path}:{i}: {line.strip()[:70]}")
    if compose_refs:
        errors += fail("repository 层不得依赖 Compose（否则 JVM 契约测试无法运行）：")
        for v in compose_refs:
            print("      " + v)
    else:
        ok("repository 层不依赖 Compose")
    # 源码布局：AGP 默认布局，不再有自定义 sourceSets 目录映射
    stray = [d for d in ("src", "compose-ui", "res") if os.path.isdir(os.path.join(ROOT, d))]
    if stray:
        errors += fail(f"仓库根目录仍有自定义源码目录 {stray}（应统一到 app/src/main/**）")
    else:
        ok("源码统一在 app/src/main/**（AGP 默认布局）")

    print("[7] 签名密钥")
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
