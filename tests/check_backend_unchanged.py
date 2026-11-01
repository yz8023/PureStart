#!/usr/bin/env python3
"""Guard the existing Hook/configuration implementation during UI migration.

Usage: python3 tests/check_backend_unchanged.py [baseline-git-ref]
Default baseline is the commit from which the UI migration started. Checks the
working tree, including staged and unstaged changes, against that commit.
"""
import difflib
import subprocess
import sys
import pathlib
import re
import xml.etree.ElementTree as ET

BASELINE = '3bb6bbd'
# v15.2：源码统一到 AGP 默认布局 app/src/main/java/**（基线提交里还在 src/**）。
# 这里只做「路径搬家」映射，内容仍然逐字节对比：搬家不等于允许改内容。
FILES = [
    ('src/com/yz8023/purestart/DexFinder.java', 'app/src/main/java/com/yz8023/purestart/DexFinder.java'),
    ('src/com/yz8023/purestart/HookGate.java', 'app/src/main/java/com/yz8023/purestart/HookGate.java'),
    ('src/com/yz8023/purestart/HookStateProvider.java',
     'app/src/main/java/com/yz8023/purestart/HookStateProvider.java'),
    ('src/com/yz8023/purestart/IconControlReceiver.java',
     'app/src/main/java/com/yz8023/purestart/IconControlReceiver.java'),
    ('src/com/yz8023/purestart/Targets.java', 'app/src/main/java/com/yz8023/purestart/Targets.java'),
    ('xposed/java_init.list', 'xposed/java_init.list'),
    ('xposed/scope.list', 'xposed/scope.list'),
]
# Prefs.java 单独按「允许新增行」的方式核对：v15.1.2 设置了新增一条界面偏好键
# （页面左右滑动开关），键名必须集中在 Prefs.java。除下列新增行外其余必须与基线一致，
# Hook 相关键与读写方法仍然受保护。
PREFS_BASELINE = 'src/com/yz8023/purestart/Prefs.java'
PREFS_FILE = 'app/src/main/java/com/yz8023/purestart/Prefs.java'
PREFS_ALLOWED_ADDITIONS = {
    '/** 页面左右滑动开关（v15.1.2 新增；默认开） */',
    'public static final String KEY_PAGE_SWIPE = "set_page_swipe";',
    'public static boolean pageSwipeEnabled(Context c) { return sp(c).getBoolean(KEY_PAGE_SWIPE, true); }',
    # v15.1.3：应用背景（极光 / 系统壁纸 / 自定义图片）相关键，均为纯界面偏好，
    # 不与 Hook 键冲突，也不改变既有键的语义与默认值。
    '/** 应用背景来源：0 极光 / 1 系统壁纸 / 2 自定义图片（v15.1.3 新增；默认极光） */',
    'public static final String KEY_WALLPAPER_MODE = "set_wallpaper_mode";',
    '/** 自定义背景图片的私有绝对路径（v15.1.3 新增） */',
    'public static final String KEY_WALLPAPER_PATH = "wallpaper_path";',
    '/** 壁纸压暗强度 0..70（v15.1.3 新增；默认 30） */',
    'public static final String KEY_WALLPAPER_DIM = "wallpaper_dim";',
    '/** 背景来源：0 极光 / 1 系统壁纸 / 2 自定义图片 */',
    'public static int wallpaperMode(Context c) { return (int) clamp(sp(c).getInt(KEY_WALLPAPER_MODE, 0), 0, 2); }',
    'public static void setWallpaperMode(Context c, int v) { sp(c).edit().putInt(KEY_WALLPAPER_MODE, v).apply(); }',
    'public static String wallpaperPath(Context c) { return sp(c).getString(KEY_WALLPAPER_PATH, null); }',
    'public static void setWallpaperPath(Context c, String v) { sp(c).edit().putString(KEY_WALLPAPER_PATH, v).apply(); }',
    '/** 壁纸压暗强度 0..0.7（默认 0.30） */',
    'public static float wallpaperDim(Context c) { return clamp(sp(c).getInt(KEY_WALLPAPER_DIM, 30) / 100f, 0f, .7f); }',
    # v15.1.4：系统壁纸读取状态（仅用于设置页展示原因）
    '/** 系统壁纸读取失败原因（v15.1.4；空 = 正常/未尝试） */',
    'public static final String KEY_WALLPAPER_ERROR = "wallpaper_error";',
    'public static String wallpaperError(Context c) { return sp(c).getString(KEY_WALLPAPER_ERROR, null); }',
    'public static void setWallpaperError(Context c, String v) {',
    'sp(c).edit().putString(KEY_WALLPAPER_ERROR, v).apply();',
    '}',
    # v15.1.5：卡片底色（唯一决定卡片是否发白的界面参数）
    '/** 卡片底色 0..40（v15.1.5；默认 10，可拉到 0 得到完全透明卡片） */',
    'public static final String KEY_CARD_TINT = "card_tint";',
    '/**',
    '* 卡片底色 0..0.40（默认 0.10）。这是**唯一**决定卡片是否「发白」的参数：',
    '* 拉到 0 时卡片只剩一圈极淡的描边，完全透出背景。',
    '*/',
    'public static float cardTint(Context c) { return clamp(sp(c).getInt(KEY_CARD_TINT, 10) / 100f, 0f, .40f); }',
    # v15.1.6：普通模式（实心卡片）开关，纯界面偏好键，同样不改动任何 Hook 键
    '/** 普通模式（v15.1.6）：true = 实心 Material 卡片，不做玻璃/折射/模糊 */',
    'public static final String KEY_PLAIN_MODE = "plain_mode";',
    '* 普通模式（对照 yz8023/UI 的 VisualMode.Normal）：卡片是**实心** Material 表面，',
    '* 不采样背景、不模糊、不折射；深色主题用深色实心、浅色主题用白色实心卡片。',
    'public static boolean plainMode(Context c) { return sp(c).getBoolean(KEY_PLAIN_MODE, false); }',
    'public static void setPlainMode(Context c, boolean v) { sp(c).edit().putBoolean(KEY_PLAIN_MODE, v).apply(); }',
}

def show(ref, path, root):
    """基线里的文件内容；文件在基线里不存在时返回 None。"""
    result = subprocess.run(['git', 'show', ref + ':' + path], cwd=root,
                            capture_output=True, text=True)
    return result.stdout if result.returncode == 0 else None


def unified(base_text, current_text, base_label, current_label):
    return list(difflib.unified_diff(base_text.splitlines(), current_text.splitlines(),
                                     fromfile=base_label, tofile=current_label, lineterm=''))


def main():
    ref = sys.argv[1] if len(sys.argv) > 1 else BASELINE
    root = subprocess.run(['git', 'rev-parse', '--show-toplevel'], check=True,
                          capture_output=True, text=True).stdout.strip()
    failures = 0
    for baseline_path, current_path in FILES:
        baseline_text = show(ref, baseline_path, root)
        current_file = pathlib.Path(root, current_path)
        if baseline_text is None or not current_file.exists():
            print(f'FAIL: missing {baseline_path} in baseline or {current_path} in worktree')
            failures += 1
            continue
        if current_file.read_text() != baseline_text:
            failures += 1
            print(f'FAIL: {current_path} differs from baseline {baseline_path}:')
            for line in unified(baseline_text, current_file.read_text(), baseline_path, current_path):
                print('      ' + line)
    if failures:
        return 1
    prefs_baseline = show(ref, PREFS_BASELINE, root)
    prefs_diff = subprocess.run(
        ['git', 'diff', '--no-ext-diff', ref, '--src-prefix=', '--dst-prefix=', '--', PREFS_BASELINE],
        cwd=root, capture_output=True, text=True)
    if prefs_diff.returncode:
        print('FAIL: could not diff', PREFS_BASELINE)
        return 1
    current_prefs = pathlib.Path(root, PREFS_FILE).read_text()
    prefs_diff_text = '\n'.join(
        line for line in unified(prefs_baseline, current_prefs, PREFS_BASELINE, PREFS_FILE)
        if not line.startswith(('+++', '---', '@@', 'diff ', 'index ')))
    unexpected = []
    for line in prefs_diff_text.splitlines():
        if line.startswith(('+', '-')):
            body = line[1:].strip()
            if body and body not in PREFS_ALLOWED_ADDITIONS:
                unexpected.append(line)
    if unexpected:
        print('FAIL: Prefs.java has changes outside the v15.1.2 UI preference allowlist:')
        for line in unexpected:
            print('      ' + line[:100])
        return 1
    # module.prop version fields are build-derived; all other fields must match.
    baseline = subprocess.run(['git', 'show', ref + ':xposed/module.prop'], cwd=root,
                              capture_output=True, text=True, check=True).stdout
    current = pathlib.Path(root, 'xposed/module.prop').read_text()
    def stable_lines(text):
        return [line for line in text.splitlines() if not re.match(r'^version(?:Code)?=', line)]
    if stable_lines(current) != stable_lines(baseline):
        print('FAIL: non-version module.prop metadata differs from baseline')
        return 1
    manifest = ET.parse(pathlib.Path(root, 'app/src/main/AndroidManifest.xml')).getroot()
    namespace = '{http://schemas.android.com/apk/res/android}'
    values = dict(line.split('=', 1) for line in current.splitlines() if '=' in line)
    if (values.get('version') != 'v' + manifest.attrib[namespace + 'versionName'] or
            values.get('versionCode') != manifest.attrib[namespace + 'versionCode']):
        print('FAIL: module.prop version does not match manifest')
        return 1
    print('PASS: untouched backend matches', ref, '(module.prop version derived from manifest)')
    print('NOTE: Main, UniversalAdHook and XiaoCanHook intentionally changed for diagnostics and gate fixes; runtime not verified.')
    return 0

if __name__ == '__main__':
    sys.exit(main())
