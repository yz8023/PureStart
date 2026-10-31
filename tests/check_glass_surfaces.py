#!/usr/bin/env python3
"""静态回归：防止「卡片白底」「系统壁纸取不到」两类问题复发。

不是渲染测试：只核对源码里必须存在 / 必须不存在的关键实现。
"""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
theme = (root / 'compose-ui/com/yz8023/purestart/ui/GlassTheme.kt').read_text()
repo = (root / 'compose-ui/com/yz8023/purestart/ui/WallpaperRepository.kt').read_text()
activity = (root / 'compose-ui/com/yz8023/purestart/SettingsActivity.kt').read_text()
themes = (root / 'res/values/themes.xml').read_text()

# 1) 卡片不得在模糊之后铺满不透明表面（白色底方块的根因）
assert 'onDrawSurface = { drawRect(surface.copy(alpha = alpha)) }' not in theme, \
    '卡片不能在模糊后覆盖 alpha 较高的整块 surface'
assert 'val tintAlpha = appearance.cardTint.coerceIn(0f, .40f)' in theme, \
    '卡片底色必须由「卡片底色」滑杆控制（0 = 完全透明）'
assert 'copy(alpha = .88f)' not in theme.replace(' ', ''), '普通模式不得使用 88% 白底'

# 2) 系统壁纸：API 33+ 不得再调用 getDrawable（必然失败），必须走 FLAG_SHOW_WALLPAPER
assert 'Build.VERSION_CODES.S_V2) return null' in repo, 'Android 13+ 不应再尝试读取壁纸位图'
assert 'FLAG_SHOW_WALLPAPER' in activity, '系统壁纸需要窗口级 FLAG_SHOW_WALLPAPER'
assert 'R.style.AppTheme_Wallpaper' in activity, '系统壁纸需要透明窗口主题'
assert 'windowShowWallpaper' in themes and 'windowIsTranslucent' in themes
assert 'probeSystemWallpaper' in repo and 'KEY_WALLPAPER_ERROR' in (
    root / 'src/com/yz8023/purestart/Prefs.java').read_text(), '需要记录系统壁纸失败原因'

# 2.5) 卡片「白框框」：不得残留高亮描边、阴影与不透明填充
assert 'shadow = null' in theme, '卡片不应投放阴影（浅色背景上的灰白光晕会被当成白框）'
assert 'val rim = (appearance.border * .45f).coerceIn(0f, .18f)' in theme, '描边必须压淡'
assert 'KEY_CARD_TINT' in (root / 'src/com/yz8023/purestart/Prefs.java').read_text(), '需要卡片底色滑杆'

# 2.6) 底栏拖拽必须按「位移 / 单槽宽度」换算，不能用手指位移冒充胶囊位置
assert 'travelled / tabWidth' in theme or True
shell = (root / 'compose-ui/com/yz8023/purestart/ui/PureStartShell.kt').read_text()
assert 'startPage + delta' in shell and 'travelled / tabWidth' in shell, \
    '底栏拖拽必须把位移换算成槽位（否则胶囊偏半槽、跳到错误页）'
assert 'basePosition + slots' not in shell, '不应再用「按下槽位 + 位移」的错误换算'

# 3) Manifest 不因背景功能新增权限（读取壁纸在任何版本都不需要权限）
manifest = ET.parse(root / 'AndroidManifest.xml').getroot()
ns = '{http://schemas.android.com/apk/res/android}'
permissions = [node.attrib.get(ns + 'name', '') for node in manifest.findall('uses-permission')]
assert permissions == [], '背景功能不得引入任何权限：' + str(permissions)

print('PASS: glass surfaces stay translucent; system wallpaper uses window compositing')
