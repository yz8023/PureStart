#!/usr/bin/env python3
"""Static regression checks only; not an Android or screenshot test."""
from pathlib import Path
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1]
a = '{http://schemas.android.com/apk/res/android}'
app = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot().find('application')
activity = next(x for x in app.findall('activity') if x.get(a+'name') == '.SettingsActivity')
alias = next(x for x in app.findall('activity-alias') if x.get(a+'name') == '.IconAlias')
assert alias.get(a+'targetActivity') == '.SettingsActivity'
assert any(c.get(a+'name') == 'android.intent.category.LAUNCHER' for c in alias.iter('category'))
assert any(c.get(a+'name') == 'de.robv.android.xposed.category.MODULE_SETTINGS' for c in activity.iter('category'))
assert any(r.get(a+'name') == '.IconControlReceiver' for r in app.findall('receiver'))
shell = (root / 'app/src/main/java/com/yz8023/purestart/ui/PureStartShell.kt').read_text()
# v15.1.7：底栏结构与 yz8023/UI 的 LiquidBottomTabs 对齐（容器 / 图标层 / 胶囊三层），
# 胶囊画在 dockContainer 之外（否则被 drawBackdrop 裁剪），且在图标层之后。
assert '.dockContainer(dockBackdrop)' in shell and '.dockLens(lensBackdrop, pressProgress)' in shell
assert 'HorizontalPager(' in shell and 'rememberPagerState(pageCount = { 4 })' in shell
assert 'settledPage' in shell
def index_of(text, needle, start=0):
    pos = text.find(needle, start)
    assert pos >= 0, '找不到 ' + needle
    return pos

assert index_of(shell, '.dockLens(') > index_of(shell, '.dockContainer(dockBackdrop)'), '胶囊必须在底栏容器之外绘制'

# 图标层（可见的那一份）必须在胶囊之前；透明采样副本在容器内部。
icon_calls = [i for i in range(len(shell)) if shell.startswith('DockItems(', i)]
assert len(icon_calls) >= 2, '需要「可见图标层」与「胶囊采样副本」两份 DockItems'
assert index_of(shell, '.dockLens(') > icon_calls[0], '玻璃胶囊必须在图标层之后绘制，否则会盖住图标'
assert 'GlassSceneRoot {' in shell and 'currentSceneBackdrop()' in shell, '背景层必须由根节点统一提供'
assert 'landingTab(' in shell and 'animateScrollToPage(' in shell
math = (root / 'app/src/main/java/com/yz8023/purestart/ui/DragMath.kt').read_text()
assert 'fun landingTab(' in math and 'fun tabDelta(' in math
home = (root / 'app/src/main/java/com/yz8023/purestart/ui/HomeScreen.kt').read_text()
assert 'PureStart/issues' in home and '隐藏桌面图标' in home
settings = (root / 'app/src/main/java/com/yz8023/purestart/ui/SettingsScreen.kt').read_text()
assert 'R.drawable.ic_chevron_down' in settings and 'GlassActionRow("获取更新")' in settings
# v15.1.3：玻璃细则默认折叠；普通模式不得再用 88% 白底铺卡片
assert 'var expanded by rememberSaveable { mutableStateOf(false) }' in settings
theme = (root / 'app/src/main/java/com/yz8023/purestart/ui/GlassTheme.kt').read_text()
assert 'copy(alpha = .88f)' not in theme.replace(' ', ''), '普通模式卡片不应有 88% 白底'
assert '.background(sheen)' in theme, '普通模式（降级分支）应使用极淡渐变雾面而非实心白底'
assert 'fun BoxScope.WallpaperSurface' in theme and 'private fun BoxScope.AuroraBlobs' in theme, '需要极光与壁纸两种背景实现'
assert 'PickVisualMedia' in settings or 'PickVisualMedia' in (root / 'app/src/main/java/com/yz8023/purestart/ui/SettingsScreen.kt').read_text(), '应用背景需支持相册选图'
wallpaper = (root / 'app/src/main/java/com/yz8023/purestart/ui/WallpaperRepository.kt').read_text()
assert 'MODE_SYSTEM' in wallpaper and 'loadSystemWallpaper' in wallpaper
assert ET.parse(root / 'app/src/main/res/drawable/ic_chevron_down.xml').getroot().find('path') is not None
print('PASS: static glass/navigation and launcher entry contracts; device behaviour not tested')
