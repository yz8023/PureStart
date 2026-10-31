#!/usr/bin/env python3
"""Check packaging invariants; does not substitute for runtime/device tests."""
import os, subprocess, sys, zipfile, xml.etree.ElementTree as ET
from pathlib import Path
root = Path(__file__).resolve().parents[1]
apk = Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'app/build/outputs/apk/release/app-release.apk'
sdk = os.environ.get('ANDROID_HOME')
if not sdk: raise SystemExit('ANDROID_HOME is required')
ns = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root / 'AndroidManifest.xml').getroot()
with zipfile.ZipFile(apk) as z:
    for name in ('java_init.list', 'scope.list'):
        assert z.read('META-INF/xposed/' + name) == (root / 'xposed' / name).read_bytes(), name
    props = dict(line.split('=',1) for line in z.read('META-INF/xposed/module.prop').decode().splitlines() if '=' in line)
    assert props['version'] == 'v' + manifest.attrib[ns + 'versionName']
    assert props['versionCode'] == manifest.attrib[ns + 'versionCode']
    dex = b''.join(z.read(name) for name in z.namelist() if name.startswith('classes') and name.endswith('.dex'))
    assert b'Lcom/yz8023/purestart/SettingsActivity;' in dex
    assert b'Lcom/yz8023/purestart/Main;' in dex
    for cls in ('HookGate', 'HookStateProvider', 'IconControlReceiver', 'Prefs', 'Targets',
                'DexFinder', 'UniversalAdHook', 'XiaoCanHook'):
        assert ('Lcom/yz8023/purestart/' + cls + ';').encode() in dex, cls
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
print('PASS: module metadata, entry classes, and APK signature structure')
print('NOTE: runtime behaviour, correct signing identity, and LSPosed integration require separate validation.')
