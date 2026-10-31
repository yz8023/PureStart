#!/usr/bin/env python3
"""Static regression checks only; not an Android or screenshot test."""
from pathlib import Path
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1]
a = '{http://schemas.android.com/apk/res/android}'
app = ET.parse(root / 'AndroidManifest.xml').getroot().find('application')
activity = next(x for x in app.findall('activity') if x.get(a+'name') == '.SettingsActivity')
alias = next(x for x in app.findall('activity-alias') if x.get(a+'name') == '.IconAlias')
assert alias.get(a+'targetActivity') == '.SettingsActivity'
assert any(c.get(a+'name') == 'android.intent.category.LAUNCHER' for c in alias.iter('category'))
assert any(c.get(a+'name') == 'de.robv.android.xposed.category.MODULE_SETTINGS' for c in activity.iter('category'))
assert any(r.get(a+'name') == '.IconControlReceiver' for r in app.findall('receiver'))
shell = (root / 'compose-ui/com/yz8023/purestart/ui/PureStartShell.kt').read_text()
# v15.1.2：页面可直接左右滑动（Pager），底栏胶囊必须画在 dockContainer 之外，
# 否则 drawBackdrop 会把子内容按胶囊形状裁剪（「胶囊被底栏框住」的老问题）。
assert '.dockContainer(dockBackdrop)' in shell and '.dockLens(lensBackdrop, pressProgress)' in shell
assert 'HorizontalPager(' in shell and 'rememberPagerState(pageCount = { 4 })' in shell
assert 'currentPageOffsetFraction' in shell
# 胶囊必须在 dockContainer 之外绘制（否则被底栏裁剪），
# 又必须在图标 Row 之后绘制（否则白色填充盖住图标）。
def index_of(text, needle, start=0):
    pos = text.find(needle, start)
    assert pos >= 0, '找不到 ' + needle
    return pos

assert index_of(shell, '.dockLens(') > index_of(shell, '.dockContainer(dockBackdrop)'), '胶囊必须在底栏容器之外绘制'

# 胶囊调用点在图标层之后；DockItems 的定义（带 RowScope.）在文件末尾，不能作为起点。
dock_row = 0
icon_calls = [i for i in range(len(shell)) if shell.startswith('DockItems(pagerState.currentPage, badge, interactions, true)', i)]
assert icon_calls, '找不到底栏图标层'
assert index_of(shell, '.dockLens(', dock_row) > icon_calls[-1], '玻璃胶囊必须在图标层之后绘制，否则会盖住图标'
assert 'GlassSceneRoot {' in shell and 'currentSceneBackdrop()' in shell, '背景层必须由根节点统一提供'
assert 'settleDockPage(' in shell and 'animateScrollToPage(' in shell
math = (root / 'compose-ui/com/yz8023/purestart/ui/DragMath.kt').read_text()
assert 'fun settleDockPage(' in math and 'fun slotsPerSecond(' in math
home = (root / 'compose-ui/com/yz8023/purestart/ui/HomeScreen.kt').read_text()
assert 'PureStart/issues' in home and '隐藏桌面图标' in home
settings = (root / 'compose-ui/com/yz8023/purestart/ui/SettingsScreen.kt').read_text()
assert 'R.drawable.ic_chevron_down' in settings and 'GlassActionRow("获取更新")' in settings
# v15.1.3：玻璃细则默认折叠；普通模式不得再用 88% 白底铺卡片
assert 'var expanded by rememberSaveable { mutableStateOf(false) }' in settings
theme = (root / 'compose-ui/com/yz8023/purestart/ui/GlassTheme.kt').read_text()
assert 'copy(alpha = .88f)' not in theme.replace(' ', ''), '普通模式卡片不应有 88% 白底'
assert '.background(sheen)' in theme, '普通模式（降级分支）应使用极淡渐变雾面而非实心白底'
assert 'fun BoxScope.WallpaperSurface' in theme and 'private fun BoxScope.AuroraBlobs' in theme, '需要极光与壁纸两种背景实现'
assert 'PickVisualMedia' in settings or 'PickVisualMedia' in (root / 'compose-ui/com/yz8023/purestart/ui/SettingsScreen.kt').read_text(), '应用背景需支持相册选图'
wallpaper = (root / 'compose-ui/com/yz8023/purestart/ui/WallpaperRepository.kt').read_text()
assert 'MODE_SYSTEM' in wallpaper and 'loadSystemBitmap' in wallpaper
assert ET.parse(root / 'res/drawable/ic_chevron_down.xml').getroot().find('path') is not None
print('PASS: static glass/navigation and launcher entry contracts; device behaviour not tested')
