#!/usr/bin/env python3
"""静态回归：卡片表面 / 系统壁纸 / 底栏方向的实现约定。

不是渲染测试：只核对源码里必须存在 / 必须不存在的关键实现。
"""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
theme = (root / 'compose-ui/com/yz8023/purestart/ui/GlassTheme.kt').read_text()
repo = (root / 'compose-ui/com/yz8023/purestart/ui/WallpaperRepository.kt').read_text()
shell = (root / 'compose-ui/com/yz8023/purestart/ui/PureStartShell.kt').read_text()
math = (root / 'compose-ui/com/yz8023/purestart/ui/DragMath.kt').read_text()
settings = (root / 'compose-ui/com/yz8023/purestart/ui/SettingsScreen.kt').read_text()
prefs = (root / 'src/com/yz8023/purestart/Prefs.java').read_text()
manifest_text = (root / 'AndroidManifest.xml').read_text()

# 1) 卡片不得在模糊之后铺满不透明表面（「白底方块」的根因）
assert 'onDrawSurface = { drawRect(surface.copy(alpha = alpha)) }' not in theme
assert 'val tintAlpha = appearance.cardTint.coerceIn(0f, .40f)' in theme

# 2) 卡片「白框框」：不得残留高亮描边与阴影
assert 'shadow = null' in theme, '卡片不应投放阴影'
assert 'val rim = (appearance.border * .45f).coerceIn(0f, .18f)' in theme, '描边必须压淡'
assert 'KEY_CARD_TINT' in prefs

# 3) 普通模式必须是**实心** Material 卡片（对照 yz8023/UI VisualMode.Normal）
assert 'if (appearance.plain)' in theme and '.background(solid)' in theme
assert 'KEY_PLAIN_MODE' in prefs

# 4) 系统壁纸：照参考仓库的做法 —— 声明 MANAGE_EXTERNAL_STORAGE + 双通道读取 + 授权引导
assert 'loadSystemWallpaper' in repo and 'getWallpaperFile' in repo, '需要 drawable + 文件双通道'
assert 'hasAllFilesAccess' in repo and 'ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION' in repo
assert 'systemWallpaperStatus' in repo, '读不到时必须给出可读原因'
assert 'MANAGE_EXTERNAL_STORAGE' in manifest_text, 'Android 14+ 读壁纸必须声明该权限'
assert 'grantLauncher' in settings, '设置页需要一键跳转授权页'

# 5) 底栏拖拽方向必须与页面同向（手指左移 = 前进一页）
assert 'fun slotDelta(' in math
assert 'startPage + slotDelta(' in shell, '底栏必须用统一的 slotDelta 换算'
assert 'travelled / tabWidth' not in shell, '不应再自行推导方向'

# 6) 权限面：只有系统壁纸需要的两个存储权限，不得有 INTERNET / 全盘枚举
manifest = ET.parse(root / 'AndroidManifest.xml').getroot()
ns = '{http://schemas.android.com/apk/res/android}'
permissions = sorted(node.attrib.get(ns + 'name', '').replace('android.permission.', '')
                     for node in manifest.findall('uses-permission'))
assert permissions == ['MANAGE_EXTERNAL_STORAGE', 'READ_EXTERNAL_STORAGE'], \
    '权限面应只有系统壁纸所需的两个存储权限：' + str(permissions)

print('PASS: card surfaces stay translucent; plain mode is solid; system wallpaper follows the reference route')
