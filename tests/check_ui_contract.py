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
# 底栏三层（v15.2 第三阶段）：底板 → 胶囊 → 图标层。
# 关键顺序契约：图标层必须画在**最后**（最上层），胶囊只当液态底 —— 上一版胶囊画在图标之上
# 并放大到 139%，滑动时会把邻格两三个 SVG 压在下面（用户看到的「遮挡 + 模糊」）。
assert '.dockContainer(dockBackdrop)' in shell and '.dockLens(lensBackdrop, pressProgress)' in shell
assert 'HorizontalPager(' in shell and 'rememberPagerState(pageCount = { 4 })' in shell
assert 'settledPage' in shell
def index_of(text, needle, start=0):
    pos = text.find(needle, start)
    assert pos >= 0, '找不到 ' + needle
    return pos

assert index_of(shell, '.dockLens(') > index_of(shell, '.dockContainer(dockBackdrop)'), '胶囊必须画在底板之上'

# 图标层只有一份（采样副本已删除），且在胶囊之后绘制。
occ = [i for i in range(len(shell)) if shell.startswith('DockItems(', i)]
defs = [i for i in occ if 'private fun RowScope.' in shell[max(0, i - 30):i]]
calls = [i for i in occ if i not in defs]
assert len(calls) == 1, '可见图标层只能有一份（v15.2 第三阶段删掉了 alpha=0 的采样副本）'
assert calls[0] > index_of(shell, '.dockLens('), '图标层必须画在胶囊之后：滑动时图标放大且永不被胶囊盖住'
assert 'GlassSceneRoot {' in shell and 'currentSceneBackdrop()' in shell, '背景层必须由根节点统一提供'
assert 'landingTab(' in shell and 'animateScrollToPage(' in shell
assert 'tapTab(' in shell and 'glideSpec' in shell, '轻点必须缓速滑到手指所在的格（tapTab + glideSpec）'

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
# v15.2 第二阶段：页面状态只有一条链 —— 版本号来自 SettingState.kt，页面不再自己注册观察者。
ui = root / 'app/src/main/java/com/yz8023/purestart/ui'
for name in ('HomeScreen.kt', 'AppsScreen.kt', 'SettingsScreen.kt', 'RuleEditor.kt', 'PureStartShell.kt'):
    text = (ui / name).read_text()
    assert 'rememberSettingsRevision(repository)' in text, name + ' 必须使用统一的偏好版本号'
    assert 'SettingsRepository.Observer' not in text, name + ' 不得自己注册观察者'
assert 'SettingsRepository.Observer' in (ui / 'SettingState.kt').read_text(), \
    '观察者只允许在 SettingState.kt 里注册'
state = (ui / 'SettingState.kt').read_text()
assert 'fun HideIconDialog(' in state and 'HIDE_ICON_HINT' in state, '隐藏图标的文案与确认框必须共用一份'
assert 'fun rememberLauncherIconState(' in state
controls = (ui / 'PreferenceControls.kt').read_text()
assert 'fun PreferenceSwitch(' in controls and 'fun PreferenceSlider(' in controls
assert 'repository.putBoolean(key' in controls and 'repository.boolean(key, default)' in controls, \
    '设置控件必须经仓库读写，不得直接碰 SharedPreferences'
print('PASS: static glass/navigation and launcher entry contracts; device behaviour not tested')
