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
assert '.dockContainer(pageBackdrop)' in shell and '.dockLens(lensBackdrop, pressProgress)' in shell
assert 'HorizontalPager(' in shell and 'rememberPagerState(pageCount = { 4 })' in shell
assert 'currentPageOffsetFraction' in shell
assert shell.index('.dockLens(') > shell.index('.dockContainer(pageBackdrop)'), '胶囊必须在容器之外绘制'
assert 'settleDockPage(' in shell and 'animateScrollToPage(' in shell
math = (root / 'compose-ui/com/yz8023/purestart/ui/DragMath.kt').read_text()
assert 'fun settleDockPage(' in math and 'fun slotsPerSecond(' in math
home = (root / 'compose-ui/com/yz8023/purestart/ui/HomeScreen.kt').read_text()
assert 'PureStart/issues' in home and '隐藏桌面图标' in home
settings = (root / 'compose-ui/com/yz8023/purestart/ui/SettingsScreen.kt').read_text()
assert 'R.drawable.ic_chevron_down' in settings and 'GlassActionRow("获取更新")' in settings
assert ET.parse(root / 'res/drawable/ic_chevron_down.xml').getroot().find('path') is not None
print('PASS: static glass/navigation and launcher entry contracts; device behaviour not tested')
