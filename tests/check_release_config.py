#!/usr/bin/env python3
"""v15.2 第三阶段守门：release 体积配置的静态契约。

体积优化的改动大部分**只有在构建后**才能看出效果，但导致「改坏了」的却都是静态可查的
几处配置。本门禁把这几处钉死，避免后续提交悄悄把优化退回去：

  1. R8 不允许退回「整包保留」（`-keep class com.yz8023.purestart.** { *; }`），
     但必须保留真正的入口（libxposed 按类名加载）与清单组件；
  2. 缩窄 keep 的前提是「没有任何一处按类名反射自己的类」——源码里一旦出现
     `Class.forName("com.yz8023.purestart…")` 或 `Class.forName(类名字面量)`，
     混淆就会静默失效，因此直接报错；
  3. `minifyEnabled` / `shrinkResources` 必须开启；打包必须剔除构建期元数据；
  4. `res/` 不得再出现位图（图标已矢量化），且所有 drawable 都必须被引用；
  5. 启动图标必须是矢量 + 自适应图标结构完整。

不是渲染/构建测试；APK 体积对比见 `tests/report_apk_size.py`。
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
GRADLE = (ROOT / 'app/build.gradle.kts').read_text(encoding='utf-8')
PROGUARD = (ROOT / 'app/proguard-rules.pro').read_text(encoding='utf-8')
RES = ROOT / 'app/src/main/res'
MANIFEST = (ROOT / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')


def rule_lines(text):
    return [line.strip() for line in text.split('\n')
            if line.strip() and not line.strip().startswith('#')]


RULES = rule_lines(PROGUARD)

# ---- 1. keep 规则：只保留入口，不许整包保留 ----
blanket = [r for r in RULES if re.match(r'-keep\w*\s+class\s+com\.yz8023\.purestart\.\*\*', r)]
assert not blanket, 'R8 规则退回整包保留了（体积优化的收益会被抵消）：%s' % blanket
for entry in ('com.yz8023.purestart.Main',
              'com.yz8023.purestart.SettingsActivity',
              'com.yz8023.purestart.HookStateProvider',
              'com.yz8023.purestart.IconControlReceiver'):
    assert any(entry in r and r.startswith('-keep') for r in RULES), \
        '缺少入口/组件 keep 规则：%s（缺了会按混淆后的名字加载失败）' % entry
assert any('-dontwarn io.github.libxposed.api' in r for r in RULES), 'compileOnly 的 libxposed API 需要 -dontwarn'
assert any(r.startswith('-keepattributes') for r in RULES), '需要保留注解/内部类属性供 Compose 与回调使用'
assert 'SourceFile' in PROGUARD and 'LineNumberTable' in PROGUARD, '保留行号便于在 LSPosed 日志里定位崩溃'

# ---- 2. 缩窄 keep 的前提：不得按类名反射自己的类 ----
reflection = []
for path in sorted((ROOT / 'app/src/main/java').rglob('*.java')) + sorted((ROOT / 'app/src/main/java').rglob('*.kt')):
    for number, line in enumerate(path.read_text(encoding='utf-8').split('\n'), 1):
        stripped = line.strip()
        if stripped.startswith(('//', '*', '/*')):
            continue
        if 'Class.forName("com.yz8023.purestart' in stripped:
            reflection.append('%s:%d: %s' % (path.relative_to(ROOT), number, stripped[:90]))
assert not reflection, '源码按类名反射了自己的类（混淆后会静默失败）：\n      ' + '\n      '.join(reflection)

# ---- 3. Gradle 侧开关 ----
assert 'isMinifyEnabled = true' in GRADLE, 'release 必须开启 R8'
assert 'isShrinkResources = true' in GRADLE, 'release 必须开启资源裁剪'
for excluded in ('kotlin_module', 'kotlin_builtins', 'DebugProbesKt.bin', '/kotlin/**', '/*.txt'):
    assert excluded in GRADLE, 'packaging 必须剔除构建期元数据：%s' % excluded
assert 'resourceConfigurations' in GRADLE, '需要限制语言资源（丢掉第三方库自带的几十种语言）'
assert 'proguard-android-optimize.txt' in GRADLE, '应使用 optimize 版默认规则模板'

# ---- 4. 资源：不得有位图；drawable 必须被引用 ----
bitmaps = [p for p in RES.rglob('*') if p.suffix.lower() in ('.png', '.jpg', '.jpeg', '.webp', '.gif')]
assert not bitmaps, 'res/ 下不应再有位图（请矢量化）：%s' % [p.name for p in bitmaps]

references = set()
for path in list((ROOT / 'app/src/main/java').rglob('*.java')) + list((ROOT / 'app/src/main/java').rglob('*.kt')):
    references |= set(re.findall(r'R\.drawable\.([a-z0-9_]+)', path.read_text(encoding='utf-8')))
for path in list(RES.rglob('*.xml')) + [ROOT / 'app/src/main/AndroidManifest.xml']:
    text = path.read_text(encoding='utf-8')
    references |= set(re.findall(r'@drawable/([a-z0-9_]+)', text))
    references |= set(re.findall(r'@mipmap/([a-z0-9_]+)', text))

unused = []
for drawable in (RES / 'drawable').glob('*'):
    name = drawable.stem
    if name not in references:
        unused.append(drawable.name)
assert not unused, 'res/drawable 下存在未被引用的资源（构建时会占位、debug 包会真的变大）：%s' % unused

# ---- 5. 启动图标：矢量 + 自适应图标结构 ----
foreground = RES / 'drawable/ic_launcher_foreground.xml'
assert foreground.exists(), '启动图标前景必须是矢量图（ic_launcher_foreground.xml）'
vector = ET.parse(foreground).getroot()
assert vector.tag.endswith('vector'), '前景必须是 <vector>'
assert 'pathData' in foreground.read_text(encoding='utf-8'), '矢量图必须有路径'
assert 'aapt:attr' in foreground.read_text(encoding='utf-8'), '前景需要保留对角渐变填充'
for name in ('ic_launcher.xml', 'ic_launcher_round.xml'):
    root = ET.parse(RES / 'mipmap-anydpi-v26' / name).getroot()
    kinds = {child.tag.split('}')[-1] for child in root}
    assert {'background', 'foreground'} <= kinds, '%s 必须是完整的自适应图标' % name
assert 'android:icon="@mipmap/ic_launcher"' in MANIFEST
assert 'android:roundIcon="@mipmap/ic_launcher_round"' in MANIFEST

print('PASS: release config keeps only real entry points, drops bitmap resources, and prunes build metadata')
print('NOTE: 实际体积变化必须构建后量测：python3 tests/report_apk_size.py <apk> [旧apk]')
