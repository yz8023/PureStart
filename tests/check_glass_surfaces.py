#!/usr/bin/env python3
"""静态回归：底栏结构 / 方向 / 普通模式 / 系统壁纸 / 卡片表面。

不是渲染测试：只核对源码里必须存在 / 必须不存在的关键实现。
"""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
theme = (root / 'app/src/main/java/com/yz8023/purestart/ui/GlassTheme.kt').read_text()
repo = (root / 'app/src/main/java/com/yz8023/purestart/ui/WallpaperRepository.kt').read_text()
shell = (root / 'app/src/main/java/com/yz8023/purestart/ui/PureStartShell.kt').read_text()
math = (root / 'app/src/main/java/com/yz8023/purestart/ui/DragMath.kt').read_text()
settings = (root / 'app/src/main/java/com/yz8023/purestart/ui/SettingsScreen.kt').read_text()
prefs = (root / 'app/src/main/java/com/yz8023/purestart/Prefs.java').read_text()
manifest_text = (root / 'app/src/main/AndroidManifest.xml').read_text()

# 1) 卡片不得在模糊之后铺满不透明表面（「白底方块」的根因）
assert 'onDrawSurface = { drawRect(surface.copy(alpha = alpha)) }' not in theme
assert 'val tintAlpha = appearance.cardTint.coerceIn(0f, .40f)' in theme

# 2) 卡片「白框框」：不得残留高亮描边与阴影
assert 'shadow = null' in theme, '卡片不应投放阴影'
assert 'val rim = (appearance.border * .45f).coerceIn(0f, .18f)' in theme, '描边必须压淡'
assert 'KEY_CARD_TINT' in prefs

# 3) 材质只有三档，判定只有一处（v15.2 第二阶段收敛到 GlassTier）：
#    之前 glassPanel / dockContainer / dockLens 各写一遍「普通模式？关磨砂？SDK<31？」，
#    SDK 判定在文件里出现 3 次，漏改一处就会出现「卡片实心、底栏还是玻璃」的割裂。
assert 'private enum class GlassTier { Plain, Fallback, Backdrop }' in theme
assert theme.count('Build.VERSION.SDK_INT < 31') == 1, 'SDK 版本判定必须只在 GlassTier 里写一次'


def body(start, end):
    return theme[theme.index(start):theme.index(end)]


card = body('fun Modifier.glassPanel', 'private fun Modifier.solidSurface')
container = body('fun Modifier.dockContainer', 'fun Modifier.dockLens')
lens = theme[theme.index('fun Modifier.dockLens'):]
# 实心表面统一由 solidSurface 提供（卡片 1dp / 底栏 3dp 只差阴影高度）
assert '.background(MaterialTheme.colorScheme.surface)' in theme, '普通模式的实心表面色'
assert 'GlassTier.Plain -> solidSurface(shape, 1.dp, appearance)' in card, '普通模式卡片必须实心'
assert 'GlassTier.Plain -> solidSurface(shape, 3.dp, appearance)' in container, '普通模式底栏必须实心'
# 降级分支必须是极淡雾面，绝不能是实心白底
assert 'GlassTier.Fallback -> clip(shape).background(sheen)' in card, '降级卡片应为雾面而非白底'
# 只有玻璃档才允许采样背景
assert card.count('drawBackdrop') == 1 and 'GlassTier.Backdrop -> drawBackdrop' in card
assert 'Highlight.Default.copy(alpha = .75f)' in container, '底栏容器用柔和高光而不是硬白边'
assert 'GlassTier.Plain -> clip(shape)' in lens, '普通模式胶囊是实心/同色淡层'
assert 'drawBackdrop' not in lens.split('GlassTier.Fallback')[0], '普通模式胶囊不得采样背景'
assert 'alpha = 1f - p' in lens, '胶囊按压时底色必须按 1-progress 变淡（否则糊住图标）'
assert 'KEY_PLAIN_MODE' in prefs

# 4) 系统壁纸：照参考仓库的做法 —— 声明 MANAGE_EXTERNAL_STORAGE + 双通道读取 + 授权引导
assert 'loadSystemWallpaper' in repo and 'getWallpaperFile' in repo, '需要 drawable + 文件双通道'
assert 'hasAllFilesAccess' in repo and 'ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION' in repo
assert 'systemWallpaperStatus' in repo, '读不到时必须给出可读原因'
assert 'MANAGE_EXTERNAL_STORAGE' in manifest_text, 'Android 14+ 读壁纸必须声明该权限'
assert 'grantLauncher' in settings, '设置页需要一键跳转授权页'

# 5) 底栏方向：只有一套「手指右移 = tab 变大」的约定（对照 LiquidBottomTabs）
assert 'fun tabDelta(' in math and 'fun dragTab(' in math and 'fun capsuleOffset(' in math
assert 'dragTab(anchor, travelled, tabPx)' in shell, '底栏必须按手指位移直接推动胶囊'
assert 'capsuleOffset' in math, '胶囊像素位置必须由 tab 坐标线性导出'
assert 'slotDelta' not in shell and 'slotDelta' not in math, \
    'v15.1.6 的「手指左移 = 槽位 +1」换算必须彻底删除（它就是反向的根源）'
# 胶囊像素位置统一由 DragMath.capsuleOffset 导出（与 DragMathTest 覆盖的公式同一份，
# 避免「测试测的是 A、生产用的是 B」这种假覆盖）
assert 'capsuleOffset(shownTab, tabPx, sideInsetPx)' in shell, '胶囊位移必须由 capsuleOffset 导出（与手指同向）'
assert 'Build.VERSION.SDK_INT < 31' not in shell

# 6) 胶囊不得盖住/糊住图标：图标常驻可见，只有胶囊正下方那一格淡出
assert 'fadedIndex' in shell and 'animateFloatAsState' in shell
assert 'alpha = fade' in shell, '胶囊下方的 tab 必须淡出，其余图标保持可见'
# 胶囊仍然必须在图标层之后绘制，才能「泡」在图标之上
assert shell.index('.dockLens(lensBackdrop') > shell.index('DockItems('), '胶囊应在图标层之后绘制'

# 7) 底栏容器/胶囊不得再用高不透明白边（「白框/糊」的历史根因）
for bad in ['Highlight(width = 1.dp, alpha = if (appearance.dark) .42f else .70f)',
            'alpha = .30f + .30f * p',
            'copy(alpha = (if (appearance.dark) .10f else .12f) + .06f * p)']:
    assert bad not in theme, '残留高不透明白边/白底：' + bad
assert 'Highlight.Default.copy(alpha = .75f)' in theme, '容器高光应与参考实现一致'
assert 'alpha = 1f - p' in theme, '按压时胶囊底色应变淡（参考实现 1 - progress）'

# 8) 权限面：只有系统壁纸需要的两个存储权限，不得有 INTERNET / 全盘枚举
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
ns = '{http://schemas.android.com/apk/res/android}'
permissions = sorted(node.attrib.get(ns + 'name', '').replace('android.permission.', '')
                     for node in manifest.findall('uses-permission'))
assert permissions == ['MANAGE_EXTERNAL_STORAGE', 'READ_EXTERNAL_STORAGE'], \
    '权限面应只有系统壁纸所需的两个存储权限：' + str(permissions)

print('PASS: dock follows the reference geometry; plain mode is solid; cards stay translucent')
