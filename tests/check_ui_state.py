#!/usr/bin/env python3
"""v15.2 第二阶段守门：界面状态的唯一来源。

背景：迁移期间四个页面各自持有
`mutableIntStateOf + DisposableEffect + repository.addObserver(...)`，
设置页还额外挂了**第二份**直接监听 SharedPreferences 的「双保险」，每个开关/滑杆
又各自 `registerOnSharedPreferenceChangeListener`——同一次偏好变更会被通知 2~3 次，
关闭后还可能收到残留回调，这正是「开关卡死/状态不同步」这一类 bug 的温床。

第二阶段把这些统一收口为：
  - 偏好版本号 → `SettingState.kt#rememberSettingsRevision`（内部只注册一个 Observer）；
  - 读写入口 → `SettingsRepository`（界面层不再出现 `Prefs.sp(` / SharedPreferences 实例）；
  - 复用控件 → `PreferenceControls.kt`；桌面图标状态与弹窗 → `SettingState.kt`。

本门禁静态核对上述收口没有被改回去（不是渲染测试）。
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / 'app/src/main/java/com/yz8023/purestart/ui'
STATE = UI / 'SettingState.kt'
CONTROLS = UI / 'PreferenceControls.kt'

ui_files = sorted(UI.glob('*.kt'))
assert ui_files, '找不到 ui 源码目录'
texts = {p.name: p.read_text(encoding='utf-8') for p in ui_files}


def code_lines(text):
    """去掉注释行，避免「注释里提到过」被误判为违规写法。"""
    out = []
    in_block = False
    for line in text.split('\n'):
        s = line.strip()
        if in_block:
            if '*/' in s:
                in_block = False
            continue
        if s.startswith('/*'):
            if '*/' not in s:
                in_block = True
            continue
        if s.startswith(('//', '*')):
            continue
        out.append(line)
    return out


# 1) 界面层不得直接注册 SharedPreferences 监听
offenders = [name for name, text in texts.items()
             if any('registerOnSharedPreferenceChangeListener' in l for l in code_lines(text))]
assert not offenders, '这些界面文件自己注册了 SharedPreferences 监听：%s' % offenders

# 2) 界面层不得直接拿 SharedPreferences 实例（键表仍在 Prefs，但读写走仓库）
offenders = [name for name, text in texts.items()
             if any('Prefs.sp(' in l for l in code_lines(text))]
assert not offenders, '这些界面文件直接读了 SharedPreferences（应经 SettingsRepository）：%s' % offenders

# 3) Observer 只允许在 SettingState.kt 注册
observers = [name for name, text in texts.items()
             if any('SettingsRepository.Observer' in l for l in code_lines(text))]
assert observers == ['SettingState.kt'], '观察者只允许在 SettingState.kt 注册，实际：%s' % observers
assert any('addObserver(' in l for l in code_lines(texts['SettingState.kt']))
assert any('removeObserver(' in l for l in code_lines(texts['SettingState.kt']))

# 4) 版本号 helper 只有一个实现，页面只能用不能抄
assert 'fun rememberSettingsRevision(' in texts['SettingState.kt']
assert 'fun rememberResumeRevision(' in texts['SettingState.kt']
for name, text in texts.items():
    if name == 'SettingState.kt':
        continue
    for line in code_lines(text):
        assert 'LifecycleEventObserver' not in line, \
            '%s 自己挂了生命周期观察者（应改用 rememberResumeRevision）' % name

# 5) 复用控件与弹窗只有一份定义
assert 'fun PreferenceSwitch(' in texts['PreferenceControls.kt']
assert 'fun PreferenceSlider(' in texts['PreferenceControls.kt']
defined_elsewhere = [name for name, text in texts.items()
                     if name != 'PreferenceControls.kt'
                     and any(l.strip().startswith(('private fun PreferenceSwitch(', 'fun PreferenceSwitch(',
                                                    'private fun PreferenceSlider(', 'fun PreferenceSlider('))
                             for l in text.split('\n'))]
assert not defined_elsewhere, '设置控件被重复定义：%s' % defined_elsewhere
assert 'fun HideIconDialog(' in texts['SettingState.kt']
assert 'fontSize' not in texts['SettingState.kt']
for name, text in texts.items():
    if name == 'SettingState.kt':
        continue
    for line in text.split('\n'):
        assert 'adb shell am broadcast -a com.yz8023.purestart.action.SHOW_ICON' not in line, \
            '%s 复制了隐藏图标的恢复文案（应改用 HideIconDialog）' % name

# 6) 外观参数只在 GlassTheme 里从仓库装配（页目不各自拼 GlassAppearance）
for name, text in texts.items():
    if name == 'GlassTheme.kt':
        continue
    for line in code_lines(text):
        assert 'GlassAppearance(' not in line, '%s 自己构造了 GlassAppearance' % name

print('PASS: UI state has a single source (repository + SettingState), no duplicated controls/dialogs')
