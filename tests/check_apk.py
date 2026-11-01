#!/usr/bin/env python3
"""Check packaging invariants; does not substitute for runtime/device tests."""
import os, subprocess, sys, zipfile, xml.etree.ElementTree as ET
from pathlib import Path
root = Path(__file__).resolve().parents[1]
apk = Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'app/build/outputs/apk/release/app-release.apk'
sdk = os.environ.get('ANDROID_HOME')
if not sdk: raise SystemExit('ANDROID_HOME is required')
ns = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
with zipfile.ZipFile(apk) as z:
    for name in ('java_init.list', 'scope.list'):
        assert z.read('META-INF/xposed/' + name) == (root / 'xposed' / name).read_bytes(), name
    props = dict(line.split('=',1) for line in z.read('META-INF/xposed/module.prop').decode().splitlines() if '=' in line)
    assert props['version'] == 'v' + manifest.attrib[ns + 'versionName']
    assert props['versionCode'] == manifest.attrib[ns + 'versionCode']
    dex = b''.join(z.read(name) for name in z.namelist() if name.startswith('classes') and name.endswith('.dex'))
    # v15.2 第三阶段起 R8 允许混淆模块内部类（只保留真正的入口），因此**不能**再按类名
    # 断言 Prefs / XiaoCanHook 等内部类存在——它们被重命名是预期行为。改为两级断言：
    #   ① 必须按原名存在的入口（libxposed 与清单按名字加载）；
    #   ② 功能标记字符串（偏好键前缀、目标包名），混淆不会改字符串字面量。
    for cls in ('Main', 'SettingsActivity', 'HookStateProvider', 'IconControlReceiver', 'BuildInfo'):
        assert ('Lcom/yz8023/purestart/' + cls + ';').encode() in dex, cls
    for marker in (b'hook_', b'sub_splash_', b'set_liquid_blur', b'plain_mode',
                   b'com.realtech.xiaocan', b'com.netease.cloudmusic',
                   b'com.yz8023.purestart.hookstate'):
        assert marker in dex, '缺少功能标记：%r' % marker
    assert b'Lcom/yz8023/purestart/ui/PrototypeActivity;' not in dex
# 构建机不一定装了 build-tools/35.0.0：按已安装的最高版本找 apksigner。
def find_apksigner(sdk_dir):
    base = Path(sdk_dir) / 'build-tools'
    candidates = []
    for entry in sorted(base.glob('*')):
        binary = entry / 'apksigner'
        if binary.exists():
            comments = [int(p) for p in entry.name.split('.') if p.isdigit()]
            candidates.append((comments, binary))
    if not candidates:
        raise SystemExit('apksigner not found under ' + str(base))
    return max(candidates, key=lambda item: item[0])[1]

subprocess.run([str(find_apksigner(sdk)), 'verify', '--verbose', str(apk)], check=True)
print('PASS: module metadata, entry classes, behaviour markers, and APK signature structure')
print('NOTE: runtime behaviour, correct signing identity, and LSPosed integration require separate validation.')
